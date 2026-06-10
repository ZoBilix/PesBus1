package com.example.pesbuscom.routes

import android.content.Context
import com.example.pesbuscom.routes.RouteMappingInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.io.Serializable

data class BusSchedule(
    val routeNumber: String,
    val routeName: String,
    val schedule: List<String> = emptyList(),
    val stops: List<String>? = null,
    val stopSchedules: Map<String, List<String>>? = null,
    val direction: String? = null // "A" или "B"
) : Serializable

class ScheduleManager(private val context: Context) {

    private val gson = Gson()

    fun loadSchedules(): List<BusSchedule> {
        val allSchedules = mutableListOf<BusSchedule>()
        val routeMapping = loadRouteMapping()

        // 1. Сначала сканируем папку balahna (она приоритетнее)
        try {
            val balahnaDirs = context.assets.list("balahna") ?: emptyArray()
            for (dirName in balahnaDirs) {
                if (dirName.startsWith("bus_")) {
                    val routeNum = dirName.substringAfter("bus_")
                    val mapping = routeMapping.values.find { it.display == routeNum }
                    val baseName = mapping?.name ?: "Маршрут $routeNum"

                    val files = context.assets.list("balahna/$dirName") ?: emptyArray()
                    
                    if (files.contains("a_arrival_time.json")) {
                        allSchedules.add(BusSchedule(
                            routeNumber = routeNum,
                            routeName = "$baseName (Туда)",
                            direction = "A"
                        ))
                    }
                    if (files.contains("b_arrival_time.json")) {
                        allSchedules.add(BusSchedule(
                            routeNumber = routeNum,
                            routeName = "$baseName (Обратно)",
                            direction = "B"
                        ))
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 2. Добавляем из bus_schedules.json только те маршруты, которых НЕТ в balahna
        try {
            val jsonString = context.assets.open("bus_schedules.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<BusSchedule>>() {}.type
            val baseSchedules: List<BusSchedule>? = gson.fromJson(jsonString, listType)
            
            baseSchedules?.forEach { baseBus ->
                if (allSchedules.none { it.routeNumber == baseBus.routeNumber }) {
                    allSchedules.add(baseBus)
                }
            }
        } catch (e: Exception) {}

        // 3. Убираем полные дубликаты (на всякий случай) и сортируем
        return allSchedules.distinctBy { it.routeNumber + it.routeName + it.direction }
            .sortedWith(compareBy({ it.routeNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.routeName }))
    }

    private fun loadRouteMapping(): Map<String, RouteMappingInfo> {
        return try {
            val jsonString = context.assets.open("route_mapping.json").bufferedReader().use { it.readText() }
            val mapType = object : TypeToken<Map<String, RouteMappingInfo>>() {}.type
            gson.fromJson(jsonString, mapType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
