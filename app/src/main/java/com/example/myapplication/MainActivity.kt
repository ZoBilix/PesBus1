package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var mapView: MapView
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var navUsername: TextView
    private lateinit var navEmail: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var busApiService: BusApiService

    // Оверлеи для карты
    private var stopsOverlay: FolderOverlay? = null
    private var routeOverlay: FolderOverlay? = null

    // Данные
    private val busStops = mutableListOf<BusStop>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val BASE_URL = "https://bus.api.pespes.online:8443/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Настройка osmdroid
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        // Инициализация Views
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        mapView = findViewById(R.id.map)
        fabMyLocation = findViewById(R.id.fab_my_location)

        // Кнопка геолокации
        fabMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }

        val fabRoute57: FloatingActionButton = findViewById(R.id.fab_route_57)
        fabRoute57.setOnClickListener {
            loadBusRoute("57")
        }
        // Toolbar
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Меню навигации
        navView.setNavigationItemSelectedListener(this)

        // Хедер навигации
        val headerView = navView.getHeaderView(0)
        navUsername = headerView.findViewById(R.id.nav_username)
        navEmail = headerView.findViewById(R.id.nav_email)

        // Карта и API
        setupMap()
        setupApi()

        // Геолокация (только для ручной работы)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        requestLocationPermission()

        // Авторизация
        checkAuthStatus()

        // Загружаем остановки при старте
        loadBusStopsOnMap()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)

        val startPoint = GeoPoint(55.751244, 37.618423) // Москва
        mapView.controller.setCenter(startPoint)

        // Оверлей для остановок
        stopsOverlay = FolderOverlay().apply { name = "Bus Stops" }
        mapView.overlays.add(stopsOverlay)

        // Оверлей для маршрутов
        routeOverlay = FolderOverlay().apply { name = "Bus Routes" }
        mapView.overlays.add(routeOverlay)
    }

    private fun setupApi() {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
        busApiService = retrofit.create(BusApiService::class.java)
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
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            enableMyLocation()
        }
    }

    private fun enableMyLocation() {
        if (!isLocationEnabled()) {
            showLocationSettingsDialog()
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLocation = GeoPoint(it.latitude, it.longitude)
                    updateMapToUserLocation(userLocation, it.accuracy)
                }
            }
        }
    }

    private fun updateMapToUserLocation(location: GeoPoint, accuracy: Float) {
        mapView.controller.animateTo(location)
        mapView.controller.setZoom(16.0)
        addMarker(location, "📍 Вы здесь", "Точность: ${accuracy.toInt()} м")
        Toast.makeText(this, "Местоположение обновлено", Toast.LENGTH_SHORT).show()
    }

    private fun moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Включите геолокацию")
            .setMessage("Для определения местоположения необходимо включить геолокацию. Включить сейчас?")
            .setPositiveButton("Включить") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun addMarker(point: GeoPoint, title: String, snippet: String) {
        val marker = Marker(mapView).apply {
            position = point
            this.title = title
            this.snippet = snippet
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    // ================= ОСТАНОВКИ НА КАРТЕ =================

    private fun loadBusStopsOnMap() {
        val center = mapView.mapCenter as GeoPoint
        val lat = center.latitude
        val lon = center.longitude
        val radius = 5000 // 5 км

        lifecycleScope.launch {
            try {
                // Вызываем ваш сервис
                val result = BustimeManager.getNearbyStops(lat, lon, radius)

                result.onSuccess { stops ->
                    if (stops.isNotEmpty()) {
                        displayStopsOnMap(stops)
                    } else {
                        Toast.makeText(this@MainActivity, "Остановок рядом нет", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure {
                    Toast.makeText(this@MainActivity, "Ошибка загрузки: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun displayStopsOnMap(stops: List<BusStop>) {
        if (stopsOverlay == null) {
            stopsOverlay = FolderOverlay().apply { name = "Bus Stops" }
            mapView.overlays.add(stopsOverlay)
        }

        stopsOverlay?.items?.clear() // Очищаем старые маркеры

        for (stop in stops) {
            // Проверка корректности координат
            if (stop.latitude !in -90.0..90.0 || stop.longitude !in -180.0..180.0) continue

            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.latitude, stop.longitude)
                title = stop.name

                // УСТАНОВКА ВАШЕЙ ИКОНКИ АВТОБУСА
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_bus)
                // Опционально: красим иконку в фиолетовый
                icon?.setTint(ContextCompat.getColor(this@MainActivity, R.color.purple_500))

                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                // Обработка клика
                setOnMarkerClickListener { _, _ ->
                    showStopInfo(stop)
                    true
                }
            }
            stopsOverlay?.add(marker)
        }

        mapView.invalidate() // Перерисовываем карту
    }

    private fun showStopInfo(stop: BusStop) {
        val routesText = if (stop.routes.isNotEmpty()) {stop.routes.joinToString("\n") { "🚌 $it" }
        } else {
            "Нет данных"
        }

        val message = """
            🚏 ${stop.name}
            
            Маршруты:
            $routesText
            
            📍 Координаты: ${String.format("%.4f", stop.latitude)}, ${String.format("%.4f", stop.longitude)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Информация об остановке")
            .setMessage(message)
            .setPositiveButton("🕐 Расписание") { dialog, _ ->
                openStopSchedule(stop)
                dialog.dismiss()
            }
            .setNegativeButton("🗺️ На карте") { dialog, _ ->
                mapView.controller.animateTo(
                    GeoPoint(stop.latitude, stop.longitude),
                    17.0,
                    1000L
                )
                dialog.dismiss()
            }
            .setNeutralButton("Закрыть", null)
            .show()
    }

    private fun openStopSchedule(stop: BusStop) {
        Toast.makeText(this, "Расписание для: ${stop.name}", Toast.LENGTH_SHORT).show()
    }


    private fun loadBusRoute(routeRef: String) {
        Toast.makeText(this, "Загрузка маршрута $routeRef...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Запрос Overpass с "out geom" — координаты приходят сразу внутри путей
                val overpassQuery = """
                [out:json][timeout:25];
                (
                  relation["route"="bus"]["ref"="$routeRef"];
                  rel(around:0)["type"="route_master"]["ref"="$routeRef"];
                  rel(r);
                );
                out geom;
                """.trimIndent()

                val encodedQuery = java.net.URLEncoder.encode(overpassQuery, "UTF-8")
                val url = "https://overpass-api.de/api/interpreter?data=$encodedQuery"

                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    val jsonString = response.body?.string() ?: return@use
                    val json = JSONObject(jsonString)
                    val elements = json.optJSONArray("elements") ?: JSONArray()

                    val allSegments = mutableListOf<List<GeoPoint>>()

                    for (i in 0 until elements.length()) {
                        val element = elements.getJSONObject(i)

                        // Парсим геометрию из участников (members) отношения
                        if (element.has("members")) {
                            val members = element.getJSONArray("members")
                            for (j in 0 until members.length()) {
                                val member = members.getJSONObject(j)
                                if (member.has("geometry")) {
                                    val geometry = member.getJSONArray("geometry")
                                    val segmentPoints = mutableListOf<GeoPoint>()
                                    for (k in 0 until geometry.length()) {
                                        val p = geometry.getJSONObject(k)
                                        segmentPoints.add(GeoPoint(p.getDouble("lat"), p.getDouble("lon")))
                                    }
                                    allSegments.add(segmentPoints)
                                }
                            }
                        }
                    }

                    launch(Dispatchers.Main) {
                        if (allSegments.isNotEmpty()) {
                            drawRouteLine(allSegments, routeRef)
                            Toast.makeText(this@MainActivity, "✅ Маршрут $routeRef загружен", Toast.LENGTH_SHORT).show()
                        } else {
                            addDemoRoute57()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ROUTE_ERR", "Ошибка: ${e.message}")
                launch(Dispatchers.Main) {
                    addDemoRoute57()
                }
            }
        }
    }

    /**
     * Рисует линию маршрута на карте
     */
    private fun drawRouteLine(segments: List<List<GeoPoint>>, routeNumber: String) {
        routeOverlay?.items?.clear()

        for (points in segments) {
            val polyline = Polyline(mapView)
            polyline.setPoints(points)
            polyline.outlinePaint.color = ContextCompat.getColor(this, R.color.purple_500)
            polyline.outlinePaint.strokeWidth = 10f
            polyline.outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            polyline.title = "Автобус $routeNumber"

            routeOverlay?.items?.add(polyline)
        }

        if (segments.isNotEmpty() && segments[0].isNotEmpty()) {
            mapView.controller.animateTo(segments[0][0])
        }
        mapView.invalidate()
    }


    private fun addDemoRoute57() {
        val demoPoints = listOf(
            GeoPoint(56.461537, 43.528394),
            GeoPoint(56.465000, 43.535000)
        )
        // Оборачиваем в список сегментов
        drawRouteLine(listOf(demoPoints), "57 (Демо)")
        Toast.makeText(this, "Показан тестовый маршрут", Toast.LENGTH_SHORT).show()
    }

    // ================= НАВИГАЦИЯ =================

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                Toast.makeText(this, "Главная", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_map -> {
                loadBusStopsOnMap()
                Toast.makeText(this, "Карта", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_stops -> {
                loadBusStopsOnMap()
                Toast.makeText(this, "Остановки загружены", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_routes -> {
                // Загружаем маршрут 57
                loadBusRoute("57")
                Toast.makeText(this, "Загрузка маршрута 57...", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_login -> {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            R.id.nav_register -> {
                val intent = Intent(this, RegisterActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_profile -> {
                Toast.makeText(this, "Профиль", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_logout -> {
                AlertDialog.Builder(this)
                    .setTitle("Выход из аккаунта")
                    .setMessage("Вы уверены?")
                    .setPositiveButton("Да") { _, _ ->
                        TokenManager.clearToken(this)
                        updateAuthUI(isLoggedIn = false)
                        Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.nav_help -> {
                showAboutDialog()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    // ================= АВТОРИЗАЦИЯ =================

    private fun checkAuthStatus() {
        val token = TokenManager.getToken(this)
        val role = TokenManager.getRole(this)
        val username = TokenManager.getUsername(this)

        if (token != null && token.isNotEmpty()) {
            updateAuthUI(isLoggedIn = true, username = username ?: "Пользователь", role = role ?: "user")
        } else {
            updateAuthUI(isLoggedIn = false)
        }
    }

    private fun updateAuthUI(isLoggedIn: Boolean, username: String = "Гость", role: String = "guest") {
        navUsername.text = if (isLoggedIn && role != "guest") username else "Гость"
        navEmail.text = when {
            !isLoggedIn -> "Не авторизован"
            role == "admin" -> "Администратор"
            role == "guest" -> "Гостевой режим"
            else -> "Пользователь"
        }

        navView.menu.apply {
            findItem(R.id.nav_login)?.isVisible = !isLoggedIn || role == "guest"
            findItem(R.id.nav_register)?.isVisible = !isLoggedIn || role == "guest"
            findItem(R.id.nav_profile)?.isVisible = isLoggedIn && role != "guest"
            findItem(R.id.nav_logout)?.isVisible = isLoggedIn && role != "guest"
        }
    }

    // ================= ЖИЗНЕННЫЙ ЦИКЛ =================

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Без разрешения геолокация не будет работать", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }

    private fun showAboutDialog() {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        AlertDialog.Builder(this)
            .setTitle("О приложении")
            .setMessage("🚌 BusMap\n\nСоздано: PesCode\nВерсия: 1.0.0\n© $currentYear")
            .setIcon(R.drawable.ic_info)
            .setPositiveButton("OK", null)
            .show()
    }
}