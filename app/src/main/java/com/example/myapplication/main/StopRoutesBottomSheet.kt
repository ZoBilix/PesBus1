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

        // Заголовок с названием остановки
        val header = TextView(requireContext()).apply {
            text = "Остановка: ${stop.name}"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        containerLayout.addView(header, 0)

        val allSchedules = ScheduleManager(requireContext()).loadSchedules()
        val allStops = loadStopsFromJson()
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // Фильтруем расписания: только те, чей номер маршрута прописан у этой остановки
        val filteredSchedules = allSchedules.filter { schedule ->
            stop.routes?.contains(schedule.routeNumber) == true
        }

        if (filteredSchedules.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = "Маршруты не найдены"
                gravity = android.view.Gravity.CENTER
                setPadding(0, 50, 0, 50)
            }
            containerLayout.addView(emptyText)
        }

        filteredSchedules.forEach { bus ->
            // 1. Получаем список всех остановок для конкретного номера маршрута (из JSON)
            val routeStops = allStops.filter { it.routes?.contains(bus.routeNumber) == true }
            val stopIndex = routeStops.indexOfFirst { it.id == stop.id }

            if (stopIndex != -1) {
                // 2. Определяем направление.
                // Если название маршрута начинается с имени ПОСЛЕДНЕЙ остановки в списке — значит едем обратно.
                val lastStopName = routeStops.lastOrNull()?.name ?: ""
                val isBackward = bus.routeName.startsWith(lastStopName, ignoreCase = true)

                // 3. Считаем задержку (индекс):
                // Прямой путь: от 0 до конца.
                // Обратный путь: от конца к 0.
                val actualIndex = if (isBackward) {
                    routeStops.size - 1 - stopIndex
                } else {
                    stopIndex
                }

                val delayMinutes = actualIndex * 2 // 2 минуты на остановку

                // 4. Применяем задержку к расписанию
                val adjustedSchedule = bus.schedule.map { addMinutes(it, delayMinutes) }

                // 5. Находим ближайший рейс
                val nextTime = adjustedSchedule
                    .filter { it >= currentTime }
                    .minOrNull() ?: adjustedSchedule.minOrNull() ?: "--:--"

                // Создаем кнопку для каждого маршрута/направления
                val btn = MaterialButton(requireContext()).apply {
                    // Текст: Номер, конечное направление и время
                    val directionName = bus.routeName.split(" - ").lastOrNull() ?: ""
                    text = "№${bus.routeNumber} до $directionName\nПрибудет в $nextTime"

                    setTextColor(ContextCompat.getColor(context, R.color.purple_500))
                    backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                    strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.purple_500))
                    strokeWidth = (1 * resources.displayMetrics.density).toInt()
                    elevation = 0f
                    stateListAnimator = null
                    setPadding(20, 30, 20, 30)
                    isAllCaps = false

                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 12, 0, 12)
                    }

                    setOnClickListener {
                        val adjustedBus = bus.copy(schedule = adjustedSchedule)
                        showTimeDialog(adjustedBus)
                        onRouteSelected(bus.routeNumber)
                        dismiss()
                    }
                }
                containerLayout.addView(btn)
            }
        }
        return view
    }

    private fun loadStopsFromJson(): List<BusStop> {
        return try {
            val jsonString = requireContext().assets.open("bus_stops.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<BusStop>>() {}.type
            Gson().fromJson(jsonString, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun addMinutes(time: String, minutes: Int): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        return try {
            val date = sdf.parse(time) ?: return time
            calendar.time = date
            calendar.add(Calendar.MINUTE, minutes)
            sdf.format(calendar.time)
        } catch (e: Exception) {
            time
        }
    }

    private fun showTimeDialog(bus: BusSchedule) {
        val timesSheet = BusTimesBottomSheet(bus)
        timesSheet.show(parentFragmentManager, "BusTimesBottomSheet")
    }
}