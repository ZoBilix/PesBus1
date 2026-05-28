package com.example.myapplication.search

import android.content.Context
import android.database.MatrixCursor
import android.provider.BaseColumns
import androidx.appcompat.widget.SearchView
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import com.example.myapplication.BusStop
import com.example.myapplication.routes.RouteManager
import com.example.myapplication.routes.RouteMappingInfo
import com.example.myapplication.models.Bus
import kotlinx.coroutines.CoroutineScope
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.*

class SearchManager(
    private val context: Context,
    private val routeManager: RouteManager,
    private val mapView: MapView,
    private val scope: CoroutineScope,
    private val allStops: () -> List<BusStop>,
    private val routeMapping: () -> Map<String, RouteMappingInfo>,
    private val currentBuses: () -> List<Bus>
) {

    private lateinit var suggestionAdapter: SimpleCursorAdapter

    fun setupSearchView(searchView: SearchView) {
        val from = arrayOf("name")
        val to = intArrayOf(android.R.id.text1)
        
        suggestionAdapter = SimpleCursorAdapter(
            context,
            android.R.layout.simple_list_item_1,
            null,
            from,
            to,
            CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )

        searchView.suggestionsAdapter = suggestionAdapter
        searchView.queryHint = "Маршрут или остановка..."

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    performSearch(query)
                    searchView.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                updateSuggestions(newText)
                return true
            }
        })

        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean = true

            override fun onSuggestionClick(position: Int): Boolean {
                val cursor = suggestionAdapter.cursor
                if (cursor.moveToPosition(position)) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    searchView.setQuery(name, true)
                    performSearch(name)
                }
                return true
            }
        })
    }

    private fun updateSuggestions(query: String?) {
        if (query.isNullOrBlank() || query.length < 1) {
            suggestionAdapter.changeCursor(null)
            return
        }

        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, "name"))
        val mapping = routeMapping()
        val stops = allStops()

        var id = 0
        // Маршруты (номера)
        mapping.values.asSequence()
            .filter { it.display.contains(query, ignoreCase = true) }
            .distinctBy { it.display }
            .take(5)
            .forEach { cursor.addRow(arrayOf(id++, it.display)) }

        // Остановки (названия)
        stops.asSequence()
            .filter { it.name.contains(query, ignoreCase = true) }
            .distinctBy { it.name }
            .take(10)
            .forEach { cursor.addRow(arrayOf(id++, it.name)) }

        suggestionAdapter.changeCursor(cursor)
    }

    private fun performSearch(query: String) {
        val mapping = routeMapping()
        val stops = allStops()
        val buses = currentBuses()
        val mapCenter = mapView.mapCenter as GeoPoint

        // 1. Поиск как номер маршрута
        val routeEntry = mapping.entries.find { it.value.display.equals(query, ignoreCase = true) }
        if (routeEntry != null) {
            val techId = routeEntry.key
            routeManager.loadBustiRoute(techId, routeEntry.value.display, scope)
            
            // Находим ближайший автобус этого маршрута к центру текущего вида карты
            val routeBuses = buses.filter { it.routeId == techId }
            if (routeBuses.isNotEmpty()) {
                val nearestBus = routeBuses.minByOrNull { 
                    calculateDistance(mapCenter.latitude, mapCenter.longitude, it.lat, it.lon) 
                }
                nearestBus?.let {
                    mapView.controller.animateTo(GeoPoint(it.lat, it.lon))
                    mapView.controller.setZoom(17.0)
                }
            }
            return
        }

        // 2. Поиск как название остановки
        val matchedStops = stops.filter { it.name.contains(query, ignoreCase = true) }
        if (matchedStops.isNotEmpty()) {
            val nearestStop = matchedStops.minByOrNull { 
                calculateDistance(mapCenter.latitude, mapCenter.longitude, it.latitude, it.longitude) 
            }
            nearestStop?.let {
                val point = GeoPoint(it.latitude, it.longitude)
                mapView.controller.animateTo(point)
                mapView.controller.setZoom(18.0)
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
