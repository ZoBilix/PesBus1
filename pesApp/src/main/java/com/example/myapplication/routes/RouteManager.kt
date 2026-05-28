package com.example.myapplication.routes

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
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
import com.example.myapplication.models.BustimeCityDb
import kotlin.math.round

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
    private var stopIcon: BitmapDrawable? = null
    
    private var routeIdMap = mapOf<String, RouteMappingInfo>()
    private var selectedTechId: String? = null
    private var lastFullBusList: List<Bus> = emptyList()
    private var cityDb: BustimeCityDb? = null

    fun setRouteMapping(mapping: Map<String, RouteMappingInfo>) {
        this.routeIdMap = mapping
        iconCache.clear() 
    }

    fun setCityDb(db: BustimeCityDb) {
        this.cityDb = db
    }

    private fun getStopIcon(): BitmapDrawable {
        if (stopIcon == null) {
            val density = context.resources.displayMetrics.density
            val size = (18 * density).toInt() // Размер иконки остановки (18dp)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_bus)
            drawable?.setBounds(0, 0, size, size)
            drawable?.draw(canvas)
            stopIcon = bitmap.toDrawable(context.resources)
        }
        return stopIcon!!
    }

    fun selectRoute(techId: String?) {
        this.selectedTechId = techId
        updateBuses(lastFullBusList)
    }

    fun getAllBuses(): List<Bus> = lastFullBusList

    private fun getRouteInfo(routeId: String): RouteMappingInfo? = routeIdMap[routeId]

    private fun createBusIcon(routeId: String, heading: Float): BitmapDrawable {
        val info = getRouteInfo(routeId)
        val displayId = info?.display ?: routeId
        val roundedHeading = (round(heading / 15f) * 15).toInt() % 360
        val cacheKey = "${displayId}_$roundedHeading"
        
        iconCache[cacheKey]?.let { return it }

        val size = 50 
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = 14f 

        bitmap.applyCanvas {
            withRotation(heading, centerX, centerY) {
                val circlePath = Path().apply { addCircle(centerX, centerY, radius, Path.Direction.CW) }
                val trianglePath = Path().apply {
                    moveTo(centerX - radius * 0.8f, centerY - radius * 0.4f)
                    lineTo(centerX, centerY - radius * 1.8f) 
                    lineTo(centerX + radius * 0.8f, centerY - radius * 0.4f)
                    close()
                }
                val combinedPath = Path()
                combinedPath.op(circlePath, trianglePath, Path.Op.UNION)

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = "#E91E63".toColorInt()
                    style = Paint.Style.FILL
                }
                drawPath(combinedPath, bgPaint)
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 10f
                color = Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
            }
            val textToDraw = if (displayId.length > 3) displayId.takeLast(2) else displayId
            val bounds = Rect()
            textPaint.getTextBounds(textToDraw, 0, textToDraw.length, bounds)
            drawText(textToDraw, centerX, centerY + bounds.height() / 2f, textPaint)
        }

        val drawable = bitmap.toDrawable(context.resources)
        iconCache[cacheKey] = drawable
        return drawable
    }

    fun updateBuses(buses: List<Bus>) {
        this.lastFullBusList = buses
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

    fun loadBustiRoute(busId: String, displayId: String, scope: CoroutineScope) {
        selectRoute(busId)
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
                        val points = List(directionArray.length()) { i ->
                            val coord = directionArray.getJSONArray(i)
                            GeoPoint(coord.getDouble(1), coord.getDouble(0))
                        }
                        if (points.isNotEmpty()) allDirectionPoints.add(points)
                    }

                    val dbStops = findStopsForBus(busId)

                    launch(Dispatchers.Main) {
                        drawBustiRouteWithStops(allDirectionPoints, dbStops, displayId)
                    }
                }
            } catch (e: Exception) {
                Log.e("RouteManager", "Error loading busti route: ${e.message}")
            }
        }
    }

    private fun findStopsForBus(busId: String): List<BusStop> {
        val db = cityDb ?: return emptyList()
        val busIdDouble = busId.toDoubleOrNull() ?: return emptyList()
        
        return db.routes.values
            .filter { it.size >= 2 && (it[0] as? Number)?.toDouble() == busIdDouble }
            .mapNotNull { routeEntry ->
                val stopId = (routeEntry[1] as? Number)?.toInt()?.toString() ?: return@mapNotNull null
                val stopData = db.stops[stopId] ?: return@mapNotNull null
                if (stopData.size < 3) return@mapNotNull null
                
                BusStop(
                    id = stopId,
                    name = stopData[0] as String,
                    latitude = (stopData[2] as Number).toDouble(),
                    longitude = (stopData[1] as Number).toDouble(),
                    routes = emptyList()
                )
            }
            .distinctBy { it.id }
    }

    private fun drawBustiRouteWithStops(allDirectionPoints: List<List<GeoPoint>>, dbStops: List<BusStop>, name: String) {
        routeOverlay.items.clear()
        stopsOverlay.items.clear()
        
        for (points in allDirectionPoints) {
            val polyline = Polyline(mapView).apply {
                setPoints(points)
                outlinePaint.color = "#E91E63".toColorInt() 
                outlinePaint.strokeWidth = 6f
                title = "Маршрут №$name"
            }
            routeOverlay.add(polyline)
        }
        
        for (stop in dbStops) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.latitude, stop.longitude)
                title = stop.name
                icon = getStopIcon()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    onStopClick(stop)
                    true
                }
            }
            stopsOverlay.add(marker)
        }
        
        if (allDirectionPoints.isNotEmpty() && allDirectionPoints[0].isNotEmpty()) {
            mapView.controller.animateTo(allDirectionPoints[0][0])
        }
        mapView.invalidate()
    }

    fun displayStops(stops: List<BusStop>) {
        stopsOverlay.items.clear()
        val icon = getStopIcon()
        for (stop in stops) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.latitude, stop.longitude)
                title = stop.name
                this.icon = icon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ -> onStopClick(stop); true }
            }
            stopsOverlay.add(marker)
        }
        mapView.invalidate()
    }
}
