package com.example.myapplication.routes

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.animation.LinearInterpolator
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
    private val onBusClick: (String) -> Unit,
    private val onStopClick: (BusStop) -> Unit
) {
    private val client = OkHttpClient()
    private val busMarkers = mutableMapOf<String, Marker>()
    private val iconCache = mutableMapOf<String, BitmapDrawable>()
    private val animators = mutableMapOf<String, ValueAnimator>()
    
    private var routeIdToNumberMap = mapOf<String, String>()

    fun setRouteMapping(mapping: Map<String, String>) {
        this.routeIdToNumberMap = mapping
        iconCache.clear() 
    }

    private fun getDisplayRouteNumber(routeId: String): String {
        return routeIdToNumberMap[routeId] ?: routeId
    }

    /**
     * Создает иконку в виде капли, указывающей направление, без внутренних линий
     */
    private fun createBusIcon(routeId: String, heading: Float): BitmapDrawable {
        val routeNumber = getDisplayRouteNumber(routeId)
        val roundedHeading = ((Math.round(heading / 15f) * 15) % 360).toInt()
        val cacheKey = "${routeNumber}_$roundedHeading"
        
        iconCache[cacheKey]?.let { return it }

        val size = 50 
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = 16f 

        // Рисуем "каплю" (фон), повернутую по вектору движения
        canvas.save()
        canvas.rotate(heading, centerX, centerY)
        
        val path = Path()
        // Объединяем круг и нос в один путь для корректной заливки без "полосок"
        path.addCircle(centerX, centerY, radius, Path.Direction.CW)
        path.moveTo(centerX - radius * 0.8f, centerY - radius * 0.5f)
        path.lineTo(centerX, centerY - radius * 1.8f) 
        path.lineTo(centerX + radius * 0.8f, centerY - radius * 0.5f)
        path.close()

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E91E63")
            style = Paint.Style.FILL
        }
        
        // Убрали borderPaint (белую обводку), чтобы убрать полоски
        canvas.drawPath(path, bgPaint)
        canvas.restore()

        // Рисуем текст (он НЕ поворачивается)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 14f
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val displayId = if (routeNumber.length > 3) routeNumber.takeLast(2) else routeNumber
        val bounds = Rect()
        textPaint.getTextBounds(displayId, 0, displayId.length, bounds)
        canvas.drawText(displayId, centerX, centerY + bounds.height() / 2f, textPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        iconCache[cacheKey] = drawable
        return drawable
    }

    fun updateBuses(buses: List<Bus>) {
        if (buses.isEmpty()) return
        val currentBusIds = buses.map { it.id }.toSet()
        
        val iterator = busMarkers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!currentBusIds.contains(entry.key)) {
                busesOverlay.remove(entry.value)
                animators[entry.key]?.cancel()
                animators.remove(entry.key)
                iterator.remove()
            }
        }

        for (bus in buses) {
            updateBusMarker(bus)
        }
        mapView.invalidate()
    }

    fun updateBusMarker(bus: Bus) {
        if (bus.lat == 0.0 || bus.lon == 0.0) return

        var marker = busMarkers[bus.id]
        val routeNum = getDisplayRouteNumber(bus.routeId)
        
        if (marker == null) {
            marker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    onBusClick(routeNum)
                    true
                }
                position = GeoPoint(bus.lat, bus.lon)
                icon = createBusIcon(bus.routeId, bus.heading)
                busesOverlay.add(this)
            }
            busMarkers[bus.id] = marker
        } else {
            // Анимация перемещения
            animateMarker(bus.id, marker, marker.position, GeoPoint(bus.lat, bus.lon))
            // Обновление иконки (направления)
            marker.icon = createBusIcon(bus.routeId, bus.heading)
        }
    }

    private fun animateMarker(busId: String, marker: Marker, startPos: GeoPoint, endPos: GeoPoint) {
        animators[busId]?.cancel()

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4500 
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val lat = startPos.latitude + (endPos.latitude - startPos.latitude) * fraction
                val lon = startPos.longitude + (endPos.longitude - startPos.longitude) * fraction
                marker.position = GeoPoint(lat, lon)
                mapView.invalidate()
            }
        }
        animators[busId] = animator
        animator.start()
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