package com.example.pesbuscom.routes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.io.Serializable

data class BusSchedule(
    val routeNumber: String,
    val routeName: String,
    val schedule: List<String>,
    val stops: List<String>? = null,
    val stopSchedules: Map<String, List<String>>? = null
) : Serializable

class ScheduleManager(private val context: Context) {

    fun loadSchedules(): List<BusSchedule> {
        val jsonString: String
        try {
            jsonString = context.assets.open("bus_schedules.json")
                .bufferedReader()
                .use { it.readText() }
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return emptyList()
        }

        val listType = object : TypeToken<List<BusSchedule>>() {}.type
        return try {
            Gson().fromJson(jsonString, listType) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
