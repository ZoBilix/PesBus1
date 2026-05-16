package com.example.myapplication.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.BusApiService
import com.example.myapplication.BusStop
import com.example.myapplication.BustimeManager
import com.example.myapplication.R
import com.example.myapplication.TokenManager
import com.example.myapplication.routes.RouteData
import com.example.myapplication.routes.RouteManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import com.google.gson.reflect.TypeToken
import com.example.myapplication.routes.ScheduleManager
import com.example.myapplication.network.BustiClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.myapplication.main.BusTimesBottomSheet

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var mapView: MapView
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var navUsername: TextView
    private lateinit var navEmail: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var busApiService: BusApiService
    private lateinit var routeManager: RouteManager
    private lateinit var bustiClient: BustiClient
    
    private var userLocationOverlay: FolderOverlay? = null
    private var stopsOverlay: FolderOverlay? = null
    private var routeOverlay: FolderOverlay? = null
    private var busesOverlay: FolderOverlay? = null
    
    private var busUpdateJob: Job? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val BUS_API_URL = "http://144.31.253.20:3000/api/buses"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        mapView = findViewById(R.id.map)
        fabMyLocation = findViewById(R.id.fab_my_location)
        bottomNav = findViewById(R.id.bottom_navigation)

        setSupportActionBar(toolbar)

        setupMap()
        
        // Инициализация API сервиса
        val retrofit = Retrofit.Builder()
            .baseUrl("http://144.31.253.20:3000/") 
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        busApiService = retrofit.create(BusApiService::class.java)

        routeManager = RouteManager(
            context = this,
            mapView = mapView,
            routeOverlay = routeOverlay!!,
            stopsOverlay = stopsOverlay!!,
            busesOverlay = busesOverlay!!,
            onBusClick = { routeNumber ->
                // При клике на автобус показываем расписание
                showScheduleForRoute(routeNumber)
            },
            onStopClick = { stop ->
                val stopSheet = StopRoutesBottomSheet(stop) { routeNumber ->
                    val allStops = loadStopsFromJson()
                    val routeStops = allStops.filter { it.routes?.contains(routeNumber) == true }

                    if (routeStops.isNotEmpty()) {
                        routeManager.loadRouteWithStops(routeNumber, routeStops, lifecycleScope)
                    } else {
                        Toast.makeText(this, "Маршрут $routeNumber не найден", Toast.LENGTH_SHORT).show()
                    }
                }
                stopSheet.show(supportFragmentManager, "StopRoutesBottomSheet")
            }
        )

        // Загрузка маппинга из JSON
        val routeMapping = loadRouteMapping()
        routeManager.setRouteMapping(routeMapping)

        // Запуск периодического обновления автобусов
        startBusUpdates()
        
        setupWebSocket()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_main -> {
                    routeOverlay?.items?.clear()
                    loadAllStops()
                    mapView.invalidate()
                    true
                }
                R.id.nav_schedule -> {
                    val scheduleSheet = ScheduleBottomSheet { routeNumber ->
                        val allStops = loadStopsFromJson()
                        val routeStops = allStops.filter { it.routes.contains(routeNumber) }
                        if (routeStops.isNotEmpty()) {
                            routeManager.loadRouteWithStops(routeNumber, routeStops, lifecycleScope)
                        }
                    }
                    scheduleSheet.show(supportFragmentManager, "ScheduleBottomSheet")
                    true
                }
                R.id.nav_profile -> {
                    val username = TokenManager.getUsername(this) ?: "Гость"
                    showProfileDialog(username)
                    true
                }
                else -> false
            }
        }

        fabMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        requestLocationPermission()
        
        mapView.post {
            loadAllStops()
        }
    }

    private fun loadRouteMapping(): Map<String, String> {
        return try {
            val jsonString = assets.open("route_mapping.json").bufferedReader().use { it.readText() }
            val mapType = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson(jsonString, mapType) ?: emptyMap()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    private fun showScheduleForRoute(routeNumber: String) {
        val schedules = ScheduleManager(this).loadSchedules()
        val busSchedule = schedules.find { it.routeNumber == routeNumber }
        
        if (busSchedule != null) {
            val timesSheet = BusTimesBottomSheet(busSchedule)
            timesSheet.show(supportFragmentManager, "BusTimesBottomSheet")
        } else {
            Toast.makeText(this, "Расписание для маршрута №$routeNumber не найдено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBusUpdates() {
        busUpdateJob?.cancel()
        busUpdateJob = lifecycleScope.launch {
            while (true) {
                try {
                    val buses = busApiService.getBuses(BUS_API_URL)
                    routeManager.updateBuses(buses)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000) // Обновление каждые 5 секунд
            }
        }
    }

    private fun setupWebSocket() {
        bustiClient = BustiClient(
            onBusesUpdate = { buses ->
                runOnUiThread {
                    routeManager.updateBuses(buses)
                }
            },
            onBusPosition = { bus ->
                runOnUiThread {
                    routeManager.updateBusMarker(bus)
                    mapView.invalidate()
                }
            },
            onConnectionStatus = { isConnected ->
                runOnUiThread {
                    val status = if (isConnected) "Подключено к GPS" else "GPS отключен"
                }
            }
        )
        bustiClient.connect()
    }
    
    private fun loadAllStops() {
        val stops = loadStopsFromJson()
        if (stops.isNotEmpty()) {
            routeManager.displayStops(stops)
        } else {
            loadBusStopsOnMap()
        }
    }
    
    private fun loadStopsFromJson(): List<BusStop> {
        return try {
            val jsonString = assets.open("bus_stops.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<BusStop>>() {}.type
            Gson().fromJson(jsonString, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(56.4615, 43.5283))

        stopsOverlay = FolderOverlay().apply { name = "Stops" }
        routeOverlay = FolderOverlay().apply { name = "Routes" }
        userLocationOverlay = FolderOverlay().apply { name = "UserLocation" }
        busesOverlay = FolderOverlay().apply { name = "Buses" }

        mapView.overlays.add(routeOverlay)
        mapView.overlays.add(stopsOverlay)
        mapView.overlays.add(busesOverlay)
        mapView.overlays.add(userLocationOverlay)
    }

    private fun loadBusStopsOnMap() {
        lifecycleScope.launch {
            try {
                BustimeManager.getNearbyStops(56.4615, 43.5283, 10000).onSuccess { stops ->
                    if (stops.isNotEmpty()) {
                        routeManager.displayStops(stops)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showProfileDialog(username: String) {
        AlertDialog.Builder(this)
            .setTitle("Профиль")
            .setMessage("Вы вошли как: $username")
            .setPositiveButton("Выход") { _, _ ->
                TokenManager.clearToken(this)
                Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun updateMapToUserLocation(location: GeoPoint, accuracy: Float) {
        mapView.controller.animateTo(location)
        addUserLocationMarker(location, accuracy)
    }
    
    private fun addUserLocationMarker(point: GeoPoint, accuracy: Float) {
        userLocationOverlay?.items?.clear()
        val marker = Marker(mapView).apply {
            position = point
            title = "Вы здесь"
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_mark_map)
            icon?.setTint(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        userLocationOverlay?.add(marker)
        mapView.invalidate()
    }
    
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val userLocation = GeoPoint(location.latitude, location.longitude)
                    updateMapToUserLocation(userLocation, location.accuracy)
                }
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            moveToCurrentLocation()
        }
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLocation = GeoPoint(it.latitude, it.longitude)
                    updateMapToUserLocation(userLocation, it.accuracy)
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return true
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { 
        super.onDestroy()
        busUpdateJob?.cancel()
        bustiClient.disconnect()
        mapView.onDetach() 
    }
}