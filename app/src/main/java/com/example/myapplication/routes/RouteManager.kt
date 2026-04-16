package com.example.myapplication.routes

import android.content.Context
import android.widget.Toast
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
class RouteManager(
    private val context: Context,
    private val mapView: MapView,
    private val routeOverlay: FolderOverlay,
    private val stopsOverlay: FolderOverlay,
    private val onStopClick: (BusStop) -> Unit
) {
    private val client = OkHttpClient()

    /**
     * Загружает маршрут по точкам, прокладывая его по дорогам (OSRM)
     */
    fun loadRouteWithStops(routeName: String, stops: List<BusStop>, scope: CoroutineScope) {
        val points = stops.map { GeoPoint(it.latitude, it.longitude) }
        val coordinates = points.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "https://router.project-osrm.org/route/v1/driving/$coordinates?overview=full&geometries=geojson"

        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string() ?: "")
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
                            mapView.controller.animateTo(roadPoints[0])
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка прокладки пути по дорогам", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun drawRoute(points: List<GeoPoint>, name: String) {
        routeOverlay.items.clear()
        val polyline = Polyline(mapView).apply {
            setPoints(points)
            outlinePaint.color = ContextCompat.getColor(context, R.color.purple_500)
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
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