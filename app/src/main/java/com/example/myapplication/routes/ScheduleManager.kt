package com.example.myapplication.routes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

/**
 * Модель данных расписания.
 * @param routeNumber Номер маршрута (например, "57")
 * @param routeName Полное название направления (например, "ПМК - ОЛИМПИЙСКАЯ")
 * @param schedule Список времен отправления с конечной точки
 * @param stops Список ID остановок в порядке их следования для данного направления
 */
data class BusSchedule(
    val routeNumber: String,
    val routeName: String,
    val schedule: List<String>,
    val stops: List<String>? = null
)

class ScheduleManager(private val context: Context) {

    /**
     * Загружает все расписания из файла assets/bus_schedules.json
     */
    fun loadSchedules(): List<BusSchedule> {
        val jsonString: String
        try {
            // Читаем JSON из папки assets
            jsonString = context.assets.open("bus_schedules.json")
                .bufferedReader()
                .use { it.readText() }
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return emptyList()
        }

        // Парсим JSON в список объектов BusSchedule
        val listType = object : TypeToken<List<BusSchedule>>() {}.type
        return try {
            Gson().fromJson(jsonString, listType) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}