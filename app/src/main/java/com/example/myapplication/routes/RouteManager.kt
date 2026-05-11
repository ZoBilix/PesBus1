package com.example.myapplication.routes

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.example.myapplication.BusStop
import com.example.myapplication.R
import com.example.myapplication.models.Bus

class RouteManager(
    private val context: Context,
    private val mapView: MapView,
    private val routeOverlay: FolderOverlay,
    private val stopsOverlay: FolderOverlay,
    private val busesOverlay: FolderOverlay,
    private val onStopClick: (BusStop) -> Unit
) {
    private val client = OkHttpClient()
    private val busMarkers = mutableMapOf<String, Marker>()

    /**
     * Создает иконку с номером маршрута (более яркую и заметную)
     */
    private fun createBusIcon(routeId: String): BitmapDrawable {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 38f
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
        }

        val cleanRoute = routeId.take(4) // Ограничиваем длину текста
        val textBounds = Rect()
        paint.getTextBounds(cleanRoute, 0, cleanRoute.length, textBounds)

        val padding = 18
        val width = textBounds.width() + padding * 2
        val height = textBounds.height() + padding * 2
        val radius = 12f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Фон
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E91E63") // Яркий розово-красный
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 4f, Color.BLACK)
        }
        
        // Обводка
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, backgroundPaint)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, borderPaint)
        canvas.drawText(cleanRoute, width / 2f, height / 2f - textBounds.centerY(), paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Обновляет список автобусов (не удаляя другие маршруты)
     */
    fun updateBuses(buses: List<Bus>) {
        if (buses.isEmpty()) return
        
        Log.d("RouteManager", "Updating ${buses.size} buses")
        for (bus in buses) {
            updateBusMarker(bus)
        }
        mapView.invalidate()
    }

    /**
     * Обновляет или создает маркер конкретного автобуса
     */
    fun updateBusMarker(bus: Bus) {
        if (bus.lat == 0.0 || bus.lon == 0.0) return

        var marker = busMarkers[bus.id]
        
        if (marker == null) {
            marker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createBusIcon(bus.routeId)
                busesOverlay.add(this)
            }
            busMarkers[bus.id] = marker
        }
        
        marker.position = GeoPoint(bus.lat, bus.lon)
        marker.title = "Маршрут ${bus.routeId}"
        marker.snippet = "Скорость: ${bus.speed} км/ч"
        marker.setPanToView(false)
        
        // Обновляем иконку, если номер маршрута изменился
        if (marker.title != "Маршрут ${bus.routeId}") {
            marker.icon = createBusIcon(bus.routeId)
        }
    }

    fun loadRouteWithStops(routeName: String, stops: List<BusStop>, scope: CoroutineScope) {
        val points = stops.map { GeoPoint(it.latitude, it.longitude) }
        val coordinates = points.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "https://router.project-osrm.org/route/v1/driving/$coordinates?overview=full&geometries=geojson"

        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes")

                    if (routes != null && routes.length() > 0) {
                        val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                        val coords = geometry.getJSONArray("coordinates")
                        val roadPoints = mutableListOf<GeoPoint>()

                        for (i in 0 until coords.length()) {
                            val p = coords.getJSONArray(i)
                            roadPoints.add(GeoPoint(p.getDouble(1), p.getDouble(0)))
                        }

                        launch(Dispatchers.Main) {
                            drawRoute(roadPoints, routeName)
                            displayStops(stops)
                            if (roadPoints.isNotEmpty()) {
                                mapView.controller.animateTo(roadPoints[0])
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RouteManager", "Error loading route: ${e.message}")
            }
        }
    }

    private fun drawRoute(points: List<GeoPoint>, name: String) {
        routeOverlay.items.clear()
        val polyline = Polyline(mapView).apply {
            setPoints(points)
            outlinePaint.color = ContextCompat.getColor(context, R.color.purple_500)
            outlinePaint.strokeWidth = 10f
            title = "Маршрут $name"
        }
        routeOverlay.add(polyline)
        mapView.invalidate()
    }

    fun displayStops(stops: List<BusStop>) {
        stopsOverlay.items.clear()
        for (stop in stops) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.latitude, stop.longitude)
                title = stop.name
                icon = ContextCompat.getDrawable(context, R.drawable.ic_bus)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    onStopClick(stop)
                    true
                }
            }
            stopsOverlay.add(marker)
        }
        mapView.invalidate()
    }
}