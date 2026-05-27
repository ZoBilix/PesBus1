package com.example.myapplication.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import com.google.gson.Gson
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.BusApiService
import com.example.myapplication.BusStop
import com.example.myapplication.BustimeManager
import com.example.myapplication.R
import com.example.myapplication.TokenManager
import com.example.myapplication.routes.RouteManager
import com.example.myapplication.routes.RouteMappingInfo
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
import com.example.myapplication.search.SearchManager
import com.example.myapplication.help.HelpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent

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
    
    private lateinit var searchManager: SearchManager
    private lateinit var helpManager: HelpManager
    
    private var userLocationOverlay: FolderOverlay? = null
    private var stopsOverlay: FolderOverlay? = null
    private var routeOverlay: FolderOverlay? = null
    private var busesOverlay: FolderOverlay? = null
    
    private var busUpdateJob: Job? = null
    private var allStopsList: List<BusStop> = emptyList()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val BUS_API_URL = "https://top4023177375.mwscdn.ru/api/buses"
        private const val CITY_DB_URL = "https://sel.bustm.net/static/other/db/v8-mini/58-12.json"
        private const val MIN_ZOOM_FOR_STOPS = 14.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        mapView = findViewById(R.id.map)
        fabMyLocation = findViewById(R.id.fab_my_location)
        bottomNav = findViewById(R.id.bottom_navigation)

        setSupportActionBar(toolbar)
        setupMap()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://top4023177375.mwscdn.ru/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        busApiService = retrofit.create(BusApiService::class.java)

        routeManager = RouteManager(
            context = this,
            mapView = mapView,
            routeOverlay = routeOverlay!!,
            stopsOverlay = stopsOverlay!!,
            busesOverlay = busesOverlay!!,
            onBusClick = { techId, routeInfo ->
                showScheduleForRoute(routeInfo)
                routeManager.loadBustiRoute(techId, routeInfo.display, lifecycleScope)
            },
            onStopClick = { stop ->
                val stopSheet = StopRoutesBottomSheet(stop) { routeNumber ->
                    val mapping = loadRouteMapping()
                    val techId = mapping.entries.find { it.value.display == routeNumber }?.key
                    if (techId != null) {
                        routeManager.loadBustiRoute(techId, routeNumber, lifecycleScope)
                    }
                }
                stopSheet.show(supportFragmentManager, "StopRoutesBottomSheet")
            }
        )

        // Инициализация менеджеров
        searchManager = SearchManager(
            context = this,
            routeManager = routeManager,
            mapView = mapView,
            scope = lifecycleScope,
            allStops = { allStopsList },
            routeMapping = { loadRouteMapping() },
            currentBuses = { routeManager.getAllBuses() }
        )
        helpManager = HelpManager(this)

        routeManager.setRouteMapping(loadRouteMapping())
        loadCityDatabase()
        startBusUpdates()
        setupWebSocket()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_main -> {
                    routeManager.selectRoute(null)
                    routeOverlay?.items?.clear()
                    loadAllStops()
                    mapView.invalidate()
                    true
                }
                R.id.nav_schedule -> {
                    val scheduleSheet = ScheduleBottomSheet { routeNumber ->
                        val mapping = loadRouteMapping()
                        val techId = mapping.entries.find { it.value.display == routeNumber }?.key
                        if (techId != null) {
                            routeManager.loadBustiRoute(techId, routeNumber, lifecycleScope)
                        }
                    }
                    scheduleSheet.show(supportFragmentManager, "ScheduleBottomSheet")
                    true
                }
                R.id.nav_profile -> {
                    ProfileBottomSheet().show(supportFragmentManager, "ProfileBottomSheet")
                    true
                }
                else -> false
            }
        }

        fabMyLocation.setOnClickListener { moveToCurrentLocation() }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        requestLocationPermission()
        
        mapView.post { loadAllStops() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        if (searchView != null) {
            searchManager.setupSearchView(searchView)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                helpManager.showInstruction()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadCityDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = busApiService.getCityDb(CITY_DB_URL)
                withContext(Dispatchers.Main) {
                    routeManager.setCityDb(db)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading city database: ${e.message}")
            }
        }
    }

    private fun loadRouteMapping(): Map<String, RouteMappingInfo> {
        return try {
            val jsonString = assets.open("route_mapping.json").bufferedReader().use { it.readText() }
            val mapType = object : TypeToken<Map<String, RouteMappingInfo>>() {}.type
            Gson().fromJson(jsonString, mapType) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    private fun showScheduleForRoute(info: RouteMappingInfo) {
        val busSchedule = ScheduleManager(this).loadSchedules().find { 
            it.routeNumber == info.display && it.routeName == info.name 
        }
        if (busSchedule != null) {
            BusTimesBottomSheet(busSchedule).show(supportFragmentManager, "BusTimesBottomSheet")
        }
    }

    private fun startBusUpdates() {
        busUpdateJob?.cancel()
        busUpdateJob = lifecycleScope.launch {
            while (true) {
                try {
                    val buses = busApiService.getBuses(BUS_API_URL)
                    routeManager.updateBuses(buses)
                } catch (e: Exception) { }
                delay(5000) 
            }
        }
    }

    private fun setupWebSocket() {
        bustiClient = BustiClient(
            onBusesUpdate = { buses -> runOnUiThread { routeManager.updateBuses(buses) } },
            onBusPosition = { bus -> runOnUiThread { routeManager.updateBusMarker(bus); mapView.invalidate() } },
            onConnectionStatus = { }
        )
        bustiClient.connect()
    }
    
    private fun loadAllStops() {
        allStopsList = loadStopsFromJson()
        if (allStopsList.isNotEmpty()) {
            routeManager.displayStops(allStopsList)
        } else {
            loadBusStopsOnMap()
        }
        updateStopsVisibility()
    }
    
    private fun loadStopsFromJson(): List<BusStop> {
        return try {
            val jsonString = assets.open("bus_stops.json").bufferedReader().use { it.readText() }
            Gson().fromJson(jsonString, object : TypeToken<List<BusStop>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
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

        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = false
            override fun onZoom(event: ZoomEvent?): Boolean {
                updateStopsVisibility()
                return true
            }
        })
    }

    private fun updateStopsVisibility() {
        val currentZoom = mapView.zoomLevelDouble
        stopsOverlay?.isEnabled = currentZoom >= MIN_ZOOM_FOR_STOPS
        mapView.invalidate()
    }

    private fun loadBusStopsOnMap() {
        lifecycleScope.launch {
            BustimeManager.getNearbyStops(56.4615, 43.5283, 10000).onSuccess { stops ->
                allStopsList = stops
                if (stops.isNotEmpty()) routeManager.displayStops(stops)
                updateStopsVisibility()
            }
        }
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLocation = GeoPoint(it.latitude, it.longitude)
                    mapView.controller.animateTo(userLocation)
                    addUserLocationMarker(userLocation, it.accuracy)
                }
            }
        }
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
                    addUserLocationMarker(userLocation, location.accuracy)
                }
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else { moveToCurrentLocation() }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = true
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { 
        super.onDestroy()
        busUpdateJob?.cancel()
        bustiClient.disconnect()
        mapView.onDetach() 
    }
}
