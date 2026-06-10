package com.example.pesbuscom.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.BusApiService
import com.example.pesbuscom.BustimeManager
import com.example.pesbuscom.BusStop
import com.example.pesbuscom.R
import com.example.pesbuscom.network.BustiClient
import com.example.pesbuscom.help.HelpManager
import com.example.pesbuscom.routes.RouteManager
import com.example.pesbuscom.routes.RouteMappingInfo
import com.example.pesbuscom.routes.ScheduleManager
import com.example.pesbuscom.search.SearchManager
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var toolbar: Toolbar
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var fabToggleBuses: FloatingActionButton
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
    private var routeMapping: Map<String, RouteMappingInfo> = emptyMap()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val CITY_DB_URL = "https://sel.bustm.net/static/other/db/v8-mini/58-12.json"
        private const val MIN_ZOOM_FOR_STOPS = 14.0
        private const val API_KEY = "8FuexJFFJizPEnptwnn9b70y7jc88VZFiOTPVUIE8sE="
        private const val SERVER_URL = "https://top4023177375.mwscdn.ru/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        mapView = findViewById(R.id.map)
        fabMyLocation = findViewById(R.id.fab_my_location)
        fabToggleBuses = findViewById(R.id.fab_toggle_buses)
        bottomNav = findViewById(R.id.bottom_navigation)

        setSupportActionBar(toolbar)
        setupMap()
        
        setupFragmentListeners()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                // Update header logic if necessary for the new server
                if (original.url.host == "top4023177375.mwscdn.ru") {
                    requestBuilder.header("X-API-KEY", API_KEY)
                }
                chain.proceed(requestBuilder.build())
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(SERVER_URL)
            .client(okHttpClient)
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
                showScheduleForRoute(info = routeInfo)
                routeManager.loadBustiRoute(techId, routeInfo.display, lifecycleScope)
            },
            onStopClick = { stop ->
                StopRoutesBottomSheet.newInstance(stop).show(supportFragmentManager, "StopRoutesBottomSheet")
            }
        )

        searchManager = SearchManager(
            context = this,
            routeManager = routeManager,
            mapView = mapView,
            scope = lifecycleScope,
            allStops = { allStopsList },
            routeMapping = { routeMapping },
            currentBuses = { routeManager.getAllBuses() }
        )
        helpManager = HelpManager(this)

        lifecycleScope.launch {
            val mapping = withContext(Dispatchers.IO) { loadRouteMapping() }
            routeMapping = mapping
            routeManager.setRouteMapping(mapping)
            
            loadCityDatabase()
            startBusUpdates()
            
            val stops = withContext(Dispatchers.IO) { loadStopsFromJson() }
            allStopsList = stops
            if (stops.isNotEmpty()) {
                routeManager.displayStops(stops)
            } else {
                loadBusStopsOnMap()
            }
            updateStopsVisibility()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_main -> {
                    returnToMain()
                }
                R.id.nav_schedule -> {
                    ScheduleBottomSheet.newInstance().show(supportFragmentManager, "ScheduleBottomSheet")
                }
                R.id.nav_profile -> {
                    ProfileBottomSheet().show(supportFragmentManager, "ProfileBottomSheet")
                }
            }
            false 
        }

        fabMyLocation.setOnClickListener { moveToCurrentLocation() }
        
        fabToggleBuses.setOnClickListener {
            busesOverlay?.let { overlay ->
                overlay.isEnabled = !overlay.isEnabled
                mapView.invalidate()
                
                // Опционально: менять цвет кнопки в зависимости от состояния
                if (overlay.isEnabled) {
                    fabToggleBuses.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue)
                } else {
                    fabToggleBuses.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
                }
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        requestLocationPermission()

        bustiClient = BustiClient(
            city = "balahna",
            onBusesUpdate = { buses ->
                runOnUiThread {
                    routeManager.updateBuses(buses)
                }
            }
        )
        bustiClient.connect()
    }

    private fun setupFragmentListeners() {
        supportFragmentManager.setFragmentResultListener(StopRoutesBottomSheet.REQUEST_KEY, this) { _, bundle ->
            val routeNumber = bundle.getString(StopRoutesBottomSheet.KEY_ROUTE_NUMBER)
            if (routeNumber != null) {
                val techId = routeMapping.entries.find { it.value.display == routeNumber }?.key
                if (techId != null) {
                    routeManager.loadBustiRoute(techId, routeNumber, lifecycleScope)
                }
            }
        }

        supportFragmentManager.setFragmentResultListener(ScheduleBottomSheet.REQUEST_KEY, this) { _, bundle ->
            val routeNumber = bundle.getString(ScheduleBottomSheet.KEY_ROUTE_NUMBER)
            if (routeNumber != null) {
                val techId = routeMapping.entries.find { it.value.display == routeNumber }?.key
                if (techId != null) {
                    routeManager.loadBustiRoute(techId, routeNumber, lifecycleScope)
                }
            }
        }
    }

    fun returnToMain() {
        routeManager.selectRoute(null)
        routeOverlay?.items?.clear()
        if (allStopsList.isNotEmpty()) routeManager.displayStops(allStopsList)
        mapView.invalidate()
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

    private suspend fun loadRouteMapping(): Map<String, RouteMappingInfo> = withContext(Dispatchers.IO) {
        try {
            val jsonString = assets.open("route_mapping.json").bufferedReader().use { it.readText() }
            val mapType = object : TypeToken<Map<String, RouteMappingInfo>>() {}.type
            Gson().fromJson(jsonString, mapType) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    private fun showScheduleForRoute(info: RouteMappingInfo) {
        lifecycleScope.launch {
            val schedules = withContext(Dispatchers.IO) {
                ScheduleManager(this@MainActivity).loadSchedules()
            }
            val busSchedule = schedules.find { 
                it.routeNumber == info.display && it.routeName == info.name 
            }
            if (busSchedule != null) {
                BusTimesBottomSheet.newInstance(busSchedule).show(supportFragmentManager, "BusTimesBottomSheet")
            }
        }
    }

    private fun startBusUpdates() {
        busUpdateJob?.cancel()
        busUpdateJob = lifecycleScope.launch {
            while (true) {
                try {
                    val buses = busApiService.getBuses()
                    routeManager.updateBuses(buses)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error fetching buses: ${e.message}")
                }
                delay(10000) 
            }
        }
    }

    private suspend fun loadStopsFromJson(): List<BusStop> = withContext(Dispatchers.IO) {
        try {
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
        busesOverlay = FolderOverlay().apply { 
            name = "Buses"
            isEnabled = false // Скрыто по умолчанию при запуске
        }

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

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { 
        super.onDestroy()
        busUpdateJob?.cancel()
        if (::bustiClient.isInitialized) bustiClient.disconnect()
        mapView.onDetach() 
    }
}
