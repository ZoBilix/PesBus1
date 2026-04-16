package com.example.myapplication.routes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

// Модель данных
data class BusSchedule(
    val routeNumber: String,
    val routeName: String,
    val schedule: List<String>
)

class ScheduleManager(private val context: Context) {
    fun loadSchedules(): List<BusSchedule> {
        val jsonString: String
        try {
            jsonString = context.assets.open("bus_schedules.json").bufferedReader().use { it.readText() }
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return emptyList()
        }

        val listType = object : TypeToken<List<BusSchedule>>() {}.type
        return Gson().fromJson(jsonString, listType)
    }
}