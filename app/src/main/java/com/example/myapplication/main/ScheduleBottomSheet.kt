package com.example.myapplication.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.routes.BusSchedule
import com.example.myapplication.routes.ScheduleManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class ScheduleBottomSheet(private val onRouteSelected: (String) -> Unit) : BottomSheetDialogFragment() {

    // Применяем стиль для прозрачного фона, чтобы были видны скругленные углы
    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_schedule, container, false)
        val containerLayout = view.findViewById<LinearLayout>(R.id.schedule_buttons_container)

        val schedules = ScheduleManager(requireContext()).loadSchedules()

        schedules.forEach { bus ->
            // Создаем MaterialButton программно
            val btn = MaterialButton(requireContext()).apply {
                text = "Маршрут №${bus.routeNumber}\n${bus.routeName}"

                // Настройка внешнего вида: используем синий цвет (blue) вместо фиолетового
                setTextColor(ContextCompat.getColor(context, R.color.blue))
                backgroundTintList = ColorStateList.valueOf(Color.WHITE)

                // Обводка синим цветом
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blue))
                strokeWidth = (1 * resources.displayMetrics.density).toInt()

                // Убираем тень (Unelevated)
                elevation = 0f
                stateListAnimator = null

                // Текст по центру и с отступами
                setPadding(20, 30, 20, 30)
                isAllCaps = false

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 12, 0, 12)
                }

                setOnClickListener {
                    showTimeDialog(bus)
                    onRouteSelected(bus.routeNumber) // Рисуем маршрут на карте
                    dismiss()
                }
            }
            containerLayout.addView(btn)
        }
        return view
    }

    private fun showTimeDialog(bus: BusSchedule) {
        val timesSheet = BusTimesBottomSheet(bus)
        timesSheet.show(parentFragmentManager, "BusTimesBottomSheet")
    }
}
