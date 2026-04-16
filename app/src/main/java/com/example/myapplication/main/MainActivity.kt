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
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import com.google.gson.reflect.TypeToken
import com.example.myapplication.routes.ScheduleManager

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
    private var userLocationOverlay: FolderOverlay? = null // Добавьте это
    private var stopsOverlay: FolderOverlay? = null
    private var routeOverlay: FolderOverlay? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val BASE_URL = "https://bus.api.pespes.online:8443/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Настройка osmdroid
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        // 1. Инициализация View
        toolbar = findViewById(R.id.toolbar)
        mapView = findViewById(R.id.map)
        fabMyLocation = findViewById(R.id.fab_my_location)
        bottomNav = findViewById(R.id.bottom_navigation)

        setSupportActionBar(toolbar)

        // 2. Настройка карты
        setupMap()

        routeManager = RouteManager(
            this,
            mapView,routeOverlay!!,
            stopsOverlay!!
        ) { stop ->
            val stopSheet = StopRoutesBottomSheet(stop) { routeNumber ->
                // 1. Загружаем ВСЕ остановки из вашего JSON
                val allStops = loadStopsFromJson()

                // 2. Находим ВСЕ остановки, через которые проходит выбранный автобус
                val routeStops = allStops.filter { it.routes?.contains(routeNumber) == true }

                if (routeStops.isNotEmpty()) {
                    // 3. Автоматически рисуем путь по этим точкам
                    routeManager.loadRouteWithStops(routeNumber, routeStops, lifecycleScope)
                } else {
                    Toast.makeText(this, "Маршрут $routeNumber не найден в базе остановок", Toast.LENGTH_SHORT).show()
                }
            }
            stopSheet.show(supportFragmentManager, "StopRoutesBottomSheet")
        }

        // 4. Нижняя навигация (Главная, Расписание, Профиль)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_main -> {
                    loadBusStopsOnMap()
                    true
                }
                R.id.nav_schedule -> {
                    val scheduleSheet = ScheduleBottomSheet { routeNumber ->
                        // ТА ЖЕ УНИВЕРСАЛЬНАЯ ЛОГИКА:
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

        // 5. Кнопка геолокации
        fabMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }

        // 6. Запуск сервисов
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        requestLocationPermission()
        loadBusStopsOnMap()
        mapView.post {
            loadAllStops()
        }
    }
    private fun loadAllStops() {
        val stops = loadStopsFromJson()
        if (stops.isNotEmpty()) {
            routeManager.displayStops(stops)
        } else {
            // Если JSON пуст, пробуем загрузить из API (Nearby)
            loadBusStopsOnMap()
        }
    }
    private fun showGeneralSchedule() {
        val scheduleSheet = ScheduleBottomSheet { routeNumber ->
            // Загружаем все данные о расписаниях
            val allSchedules = ScheduleManager(this).loadSchedules()

            // Ищем выбранный маршрут в списке
            val selectedBus = allSchedules.find { it.routeNumber == routeNumber }

            if (selectedBus != null) {
                // Здесь должна быть логика получения точек (координат) для этого маршрута.
                // Пока для примера используем RouteData, но в идеале координаты
                // должны быть в JSON или подгружаться по номеру.
                val stopsPoints = if (routeNumber == "57") {
                    RouteData.getRoute57Stops()
                } else {
                    // Если для других маршрутов пока нет координат, можно вывести уведомление
                    emptyList()
                }

                if (stopsPoints.isNotEmpty()) {
                    routeManager.loadRouteWithStops(routeNumber, stopsPoints, lifecycleScope)
                } else {
                    Toast.makeText(this, "Путь для маршрута $routeNumber еще не настроен", Toast.LENGTH_SHORT).show()
                }
            }
        }
        scheduleSheet.show(supportFragmentManager, "ScheduleBottomSheet")
    }
    private fun loadStopsFromJson(): List<BusStop> {
        return try {
            val jsonString = assets.open("bus_stops.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<BusStop>>() {}.type

            Gson().fromJson(jsonString, listType) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(56.4615, 43.5283)) // Центрируем на ваш район

        stopsOverlay = FolderOverlay().apply { name = "Stops" }
        routeOverlay = FolderOverlay().apply { name = "Routes" }
        userLocationOverlay = FolderOverlay().apply { name = "UserLocation" }

        mapView.overlays.add(routeOverlay)
        mapView.overlays.add(stopsOverlay)
        mapView.overlays.add(userLocationOverlay)
    }

    private fun loadBusStopsOnMap() {
        lifecycleScope.launch {
            try {
                // Загружаем остановки (в радиусе 10км от центра для теста)
                BustimeManager.getNearbyStops(56.4615, 43.5283, 10000).onSuccess { stops ->
                    if (stops.isNotEmpty()) {
                        routeManager.displayStops(stops) // Используем метод из RouteManager
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun showStopInfo(stop: BusStop) {
        val routesText = if (stop.routes.isNotEmpty()) stop.routes.joinToString(", ") else "Нет данных"
        AlertDialog.Builder(this)
            .setTitle(stop.name)
            .setMessage("Маршруты: $routesText")
            .setPositiveButton("OK", null)
            .show()
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


    // --- ГЕОЛОКАЦИЯ ---
    private fun updateMapToUserLocation(location: GeoPoint, accuracy: Float) {
        mapView.controller.animateTo(location)
        mapView.controller.setZoom(16.0) // Раскомментируйте, если хотите авто-зум при каждом обновлении

        addUserLocationMarker(location, accuracy)
    }
    private fun addUserLocationMarker(point: GeoPoint, accuracy: Float) {
        userLocationOverlay?.items?.clear() // Удаляем старую метку перед добавлением новой

        val marker = Marker(mapView).apply {
            position = point
            title = "Вы здесь"
            snippet = "Точность: ${accuracy.toInt()} м"

            // Используем стандартную иконку или свою
            icon = ContextCompat.getDrawable(this@MainActivity, org.osmdroid.library.R.drawable.ic_menu_mylocation)
            icon?.setTint(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        userLocationOverlay?.add(marker)
        mapView.invalidate() // Перерисовать карту
    }
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val userLocation = GeoPoint(location.latitude, location.longitude)
                    // Вызываем метод, который и двигает карту, и рисует/обновляет маркер
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
            // Если разрешение уже есть, пробуем сразу найти пользователя
            moveToCurrentLocation()
        }
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLocation = GeoPoint(it.latitude, it.longitude)
                    // Вызываем updateMapToUserLocation вместо прямого управления mapView,
                    // чтобы отрисовалась синяя иконка
                    updateMapToUserLocation(userLocation, it.accuracy)
                } ?: run {
                    Toast.makeText(this, "Не удалось определить местоположение. Попробуйте включить GPS", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun checkAuthStatus() {
        val username = TokenManager.getUsername(this)
        navUsername.text = username ?: "Гость"
        navEmail.text = if (username != null) "Авторизован" else "Войдите в аккаунт"
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_stops -> loadBusStopsOnMap()
            R.id.nav_logout -> {
                TokenManager.clearToken(this)
                checkAuthStatus()
            }
            R.id.nav_help -> showAboutDialog()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); mapView.onDetach() }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("О приложении")
            .setMessage("🚌 BusMap\nВерсия: 1.0.0")
            .setPositiveButton("OK", null)
            .show()
    }
}