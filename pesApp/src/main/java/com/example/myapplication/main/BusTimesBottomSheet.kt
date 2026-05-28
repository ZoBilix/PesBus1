package com.example.myapplication.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.routes.BusSchedule
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.*

class BusTimesBottomSheet(private val bus: BusSchedule) : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_bus_times, container, false)

        view.findViewById<TextView>(R.id.tv_route_title).text = "Маршрут №${bus.routeNumber}"
        view.findViewById<TextView>(R.id.tv_route_name).text = bus.routeName

        val timesContainer = view.findViewById<LinearLayout>(R.id.times_container)
        timesContainer.removeAllViews()

        // 1. Получаем текущее время системы
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // 2. Сортируем время: сначала те, что БУДУТ сегодня, потом те, что уже прошли (на завтра)
        val sortedSchedule = bus.schedule.sortedBy { time ->
            if (time < currentTime) "24:$time" else "00:$time"
        }

        // 3. Отображаем отсортированное время
        sortedSchedule.forEach { time ->
            val timeView = TextView(requireContext()).apply {
                text = time
                textSize = 18f
                setPadding(40, 20, 40, 20)

                // Выделяем ближайшее время другим цветом (опционально)
                if (time >= currentTime && sortedSchedule.firstOrNull { it >= currentTime } == time) {
                    setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                } else {
                    // Используем синий цвет вместо фиолетового
                    setTextColor(ContextCompat.getColor(context, R.color.blue))
                }

                background = ContextCompat.getDrawable(context, R.drawable.bg_time_chip)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(15, 0, 15, 0)
                layoutParams = params
            }
            timesContainer.addView(timeView)
        }

        view.findViewById<Button>(R.id.btn_close_times).setOnClickListener { dismiss() }

        return view
    }
}
