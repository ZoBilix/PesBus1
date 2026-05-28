package com.example.myapplication.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.myapplication.BusStop
import com.example.myapplication.R
import com.example.myapplication.routes.BusSchedule
import com.example.myapplication.routes.ScheduleManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class StopRoutesBottomSheet(
    private val stop: BusStop,
    private val onRouteSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_schedule, container, false)
        val containerLayout = view.findViewById<LinearLayout>(R.id.schedule_buttons_container)

        val header = TextView(requireContext()).apply {
            text = "Остановка: ${stop.name}"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        containerLayout.addView(header, 0)

        val allSchedules = ScheduleManager(requireContext()).loadSchedules()
        val allStops = loadStopsFromJson()
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val filteredSchedules = allSchedules.filter { schedule ->
            stop.routes?.contains(schedule.routeNumber) == true
        }

        filteredSchedules.forEach { bus ->
            val stopIds = bus.stops ?: emptyList()
            val stopIndex = stopIds.indexOf(stop.id)

            if (stopIndex != -1) {
                // 1. Получаем список названий всех остановок маршрута
                val orderedStops = stopIds.mapNotNull { id -> allStops.find { it.id == id } }

                // ОПРЕДЕЛЯЕМ НАЧАЛО И КОНЕЦ
                val startStopName = orderedStops.firstOrNull()?.name ?: "Начало"
                val endStopName = orderedStops.lastOrNull()?.name ?: "Конец"
                val isLastStop = stopIndex == stopIds.size - 1

                // 2. Расчет расстояния и времени (как было ранее)
                var totalDistanceKm = 0.0
                for (i in 0 until stopIndex) {
                    val s1 = orderedStops[i]
                    val s2 = orderedStops[i+1]
                    totalDistanceKm += calculateDistance(s1.latitude, s1.longitude, s2.latitude, s2.longitude)
                }

                val delayMinutes = (totalDistanceKm * 3.0 + stopIndex * 0.5).toInt()
                val adjustedSchedule = bus.schedule.map { addMinutes(it, delayMinutes) }
                val nextTimeRaw = adjustedSchedule
                    .filter { it >= currentTime }
                    .minOrNull() ?: adjustedSchedule.minOrNull() ?: "--:--"

                val displayInfo = if (nextTimeRaw != "--:--") {
                    val diff = getMinutesUntil(currentTime, nextTimeRaw)
                    val windowStart = addMinutes(nextTimeRaw, -2)
                    val windowEnd = addMinutes(nextTimeRaw, 2)
                    "~$windowStart - $windowEnd (через $diff мин)"
                } else {
                    "--:--"
                }

                val btn = MaterialButton(requireContext()).apply {
                    // ТЕКСТ: Номер маршрута и направление (Откуда -> Куда)
                    val routeTitle = "№${bus.routeNumber}: $startStopName → $endStopName"

                    if (isLastStop) {
                        text = "$routeTitle\nКонечная остановка"
                        alpha = 0.6f // Делаем кнопку полупрозрачной
                    } else {
                        text = "$routeTitle\nПрибытие: $displayInfo"
                    }

                    // Используем синий цвет (blue) вместо фиолетового
                    setTextColor(ContextCompat.getColor(context, R.color.blue))
                    backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                    strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blue))
                    strokeWidth = (1 * resources.displayMetrics.density).toInt()
                    elevation = 0f
                    stateListAnimator = null
                    setPadding(20, 32, 20, 32)
                    isAllCaps = false

                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 12, 0, 12) }

                    setOnClickListener {
                        if (!isLastStop) {
                            showTimeDialog(bus.copy(schedule = adjustedSchedule))
                            onRouteSelected(bus.routeNumber)
                            dismiss()
                        }
                    }
                }
                containerLayout.addView(btn)
            }
        }
        return view
    }

    // --- Вспомогательные методы (calculateDistance, addMinutes и т.д. остаются без изменений) ---
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun getMinutesUntil(current: String, target: String): Int {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return try {
            val d1 = sdf.parse(current)
            val d2 = sdf.parse(target)
            var diff = (d2.time - d1.time) / (1000 * 60)
            if (diff < 0) diff += 1440
            diff.toInt()
        } catch (e: Exception) { 0 }
    }

    private fun addMinutes(time: String, minutes: Int): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance()
        return try {
            cal.time = sdf.parse(time) ?: return time
            cal.add(Calendar.MINUTE, minutes)
            sdf.format(cal.time)
        } catch (e: Exception) { time }
    }

    private fun loadStopsFromJson(): List<BusStop> {
        return try {
            val jsonString = requireContext().assets.open("bus_stops.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<BusStop>>() {}.type
            Gson().fromJson(jsonString, listType) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun showTimeDialog(bus: BusSchedule) {
        BusTimesBottomSheet(bus).show(parentFragmentManager, "BusTimesBottomSheet")
    }
}