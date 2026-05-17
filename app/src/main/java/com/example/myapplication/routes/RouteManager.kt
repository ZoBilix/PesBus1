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

data class RouteMappingInfo(
    val display: String,
    val name: String
)

class RouteManager(
    private val context: Context,
    private val mapView: MapView,
    private val routeOverlay: FolderOverlay,
    private val stopsOverlay: FolderOverlay,
    private val busesOverlay: FolderOverlay,
    private val onBusClick: (String, RouteMappingInfo) -> Unit,
    private val onStopClick: (BusStop) -> Unit
) {
    private val client = OkHttpClient()
    private val busMarkers = mutableMapOf<String, Marker>()
    private val iconCache = mutableMapOf<String, BitmapDrawable>()
    private val animators = mutableMapOf<String, ValueAnimator>()
    
    private var routeIdMap = mapOf<String, RouteMappingInfo>()
    private var selectedTechId: String? = null
    private var lastFullBusList: List<Bus> = emptyList()

    fun setRouteMapping(mapping: Map<String, RouteMappingInfo>) {
        this.routeIdMap = mapping
        iconCache.clear() 
    }

    /**
     * Устанавливает фильтр для отображения только одного маршрута
     */
    fun selectRoute(techId: String?) {
        this.selectedTechId = techId
        updateBuses(lastFullBusList)
    }

    private fun getRouteInfo(routeId: String): RouteMappingInfo? {
        return routeIdMap[routeId]
    }

    private fun createBusIcon(routeId: String, heading: Float): BitmapDrawable {
        val info = getRouteInfo(routeId)
        val displayId = info?.display ?: routeId
        val roundedHeading = ((Math.round(heading / 15f) * 15) % 360).toInt()
        val cacheKey = "${displayId}_$roundedHeading"
        
        iconCache[cacheKey]?.let { return it }

        val size = 50 
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = 14f 

        canvas.save()
        canvas.rotate(heading, centerX, centerY)
        
        val circlePath = Path().apply { addCircle(centerX, centerY, radius, Path.Direction.CW) }
        val trianglePath = Path().apply {
            moveTo(centerX - radius * 0.8f, centerY - radius * 0.4f)
            lineTo(centerX, centerY - radius * 1.8f) 
            lineTo(centerX + radius * 0.8f, centerY - radius * 0.4f)
            close()
        }
        
        // Объединяем круг и носик в одну фигуру, чтобы не было "полосок" внутри
        val combinedPath = Path()
        combinedPath.op(circlePath, trianglePath, Path.Op.UNION)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E91E63")
            style = Paint.Style.FILL
        }
        
        canvas.drawPath(combinedPath, bgPaint)
        canvas.restore()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 10f
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val textToDraw = if (displayId.length > 3) displayId.takeLast(2) else displayId
        val bounds = Rect()
        textPaint.getTextBounds(textToDraw, 0, textToDraw.length, bounds)
        canvas.drawText(textToDraw, centerX, centerY + bounds.height() / 2f, textPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        iconCache[cacheKey] = drawable
        return drawable
    }

    fun updateBuses(buses: List<Bus>) {
        this.lastFullBusList = buses
        
        // Фильтрация: если выбран конкретный маршрут, показываем только его
        val visibleBuses = if (selectedTechId != null) {
            buses.filter { it.routeId == selectedTechId }
        } else {
            buses
        }

        val currentBusIds = visibleBuses.map { it.id }.toSet()
        
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

        for (bus in visibleBuses) {
            updateBusMarker(bus)
        }
        mapView.invalidate()
    }

    fun updateBusMarker(bus: Bus) {
        if (bus.lat == 0.0 || bus.lon == 0.0) return

        var marker = busMarkers[bus.id]
        val info = getRouteInfo(bus.routeId)
        val techId = bus.routeId
        
        if (marker == null) {
            marker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    info?.let { onBusClick(techId, it) }
                    true
                }
                position = GeoPoint(bus.lat, bus.lon)
                icon = createBusIcon(bus.routeId, bus.heading)
                busesOverlay.add(this)
            }
            busMarkers[bus.id] = marker
        } else {
            animateMarker(bus.id, marker, marker.position, GeoPoint(bus.lat, bus.lon))
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

    /**
     * Загружает и отображает линию маршрута с Busti.me
     */
    fun loadBustiRoute(busId: String, displayId: String, scope: CoroutineScope) {
        selectRoute(busId) // Оставляем на карте только этот маршрут
        val url = "https://ru.busti.me/ajax/route-line/?bus_id=$busId"
        
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    
                    val allDirectionPoints = mutableListOf<List<GeoPoint>>()
                    val keys = json.keys()
                    while(keys.hasNext()) {
                        val key = keys.next() as String
                        val directionArray = json.optJSONArray(key) ?: continue
                        val points = mutableListOf<GeoPoint>()
                        for (i in 0 until directionArray.length()) {
                            val coord = directionArray.getJSONArray(i)
                            // lon, lat
                            points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                        }
                        if (points.isNotEmpty()) allDirectionPoints.add(points)
                    }

                    launch(Dispatchers.Main) {
                        drawBustiRouteWithDots(allDirectionPoints, displayId)
                    }
                }
            } catch (e: Exception) {
                Log.e("RouteManager", "Error loading busti route: ${e.message}")
            }
        }
    }

    private fun drawBustiRouteWithDots(allDirectionPoints: List<List<GeoPoint>>, name: String) {
        routeOverlay.items.clear()
        stopsOverlay.items.clear() // Убираем другие остановки города
        
        for (points in allDirectionPoints) {
            // Рисуем линию
            val polyline = Polyline(mapView).apply {
                setPoints(points)
                outlinePaint.color = Color.parseColor("#E91E63") 
                outlinePaint.strokeWidth = 6f
            }
            routeOverlay.add(polyline)
            
            // Рисуем точки (остановки)
            for (point in points) {
                val dot = Marker(mapView).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createSmallDotIcon()
                    title = "Точка маршрута №$name"
                }
                stopsOverlay.add(dot)
            }
        }
        
        if (allDirectionPoints.isNotEmpty() && allDirectionPoints[0].isNotEmpty()) {
            mapView.controller.animateTo(allDirectionPoints[0][0])
        }
        mapView.invalidate()
    }

    private fun createSmallDotIcon(): BitmapDrawable {
        val size = 10
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E91E63")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(size/2f, size/2f, size/2f - 1f, paint)
        canvas.drawCircle(size/2f, size/2f, size/2f - 1f, stroke)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun displayStops(stops: List<BusStop>) {
        stopsOverlay.items.clear()
        for (stop in stops) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.latitude, stop.longitude)
                title = stop.name
                icon = ContextCompat.getDrawable(context, R.drawable.ic_bus)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ -> onStopClick(stop); true }
            }
            stopsOverlay.add(marker)
        }
        mapView.invalidate()
    }
}