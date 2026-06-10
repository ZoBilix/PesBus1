package com.example.pesbuscom.main

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.R
import com.example.pesbuscom.routes.BusSchedule
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class BusTimesBottomSheet : BottomSheetDialogFragment() {

    private val bus: BusSchedule? by lazy { arguments?.getSerializable(ARG_BUS) as? BusSchedule }
    private var times: List<String> = emptyList()

    companion object {
        private const val ARG_BUS = "arg_bus"
        private const val ARG_TIMES = "arg_times"

        fun newInstance(bus: BusSchedule, times: List<String>? = null): BusTimesBottomSheet {
            return BusTimesBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_BUS, bus as java.io.Serializable)
                    times?.let { putStringArrayList(ARG_TIMES, ArrayList(it)) }
                }
            }
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.setBackgroundResource(R.drawable.bg_rounded_bottom_sheet)
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_bus_times, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val safeContext = context ?: return
        val currentBus = bus ?: run { dismiss(); return }
        
        times = arguments?.getStringArrayList(ARG_TIMES) ?: currentBus.schedule ?: emptyList()

        view.findViewById<TextView>(R.id.tv_route_title).text = "Маршрут ${currentBus.routeNumber}"
        view.findViewById<TextView>(R.id.tv_route_name).text = currentBus.routeName

        val timesContainer = view.findViewById<LinearLayout>(R.id.times_container)
        val progressBar = ProgressBar(safeContext).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER }
        }
        
        if (timesContainer != null && progressBar.parent == null) {
            timesContainer.addView(progressBar)
        }

        lifecycleScope.launch {
            if (times.isEmpty()) {
                times = loadTimesForDirection(safeContext, currentBus.routeNumber, currentBus.direction)
            }
            
            if (!isAdded) return@launch
            
            timesContainer?.removeView(progressBar)
            displayTimes(view, timesContainer, times)
        }
    }

    private fun displayTimes(rootView: View, container: LinearLayout?, timesList: List<String>) {
        val safeContext = context ?: return
        if (container == null) return
        
        container.removeAllViews()
        val cleanTimes = timesList.distinct().sorted()
        
        if (cleanTimes.isEmpty()) {
            container.addView(TextView(safeContext).apply { 
                text = "Нет данных"; setPadding(40, 20, 40, 20) 
            })
            return
        }

        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val nextTime = cleanTimes.filter { it >= currentTime }.minOrNull()
        var targetScrollX = 0
        var foundNext = false

        cleanTimes.forEach { time ->
            val isNext = time == nextTime
            val timeView = TextView(safeContext).apply {
                text = time; textSize = 18f; setPadding(40, 20, 40, 20)
                setTextColor(ContextCompat.getColor(safeContext, if (isNext) android.R.color.holo_green_dark else R.color.blue))
                background = ContextCompat.getDrawable(safeContext, R.drawable.bg_time_chip)
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(15, 0, 15, 0) }
                
                setOnClickListener {
                    showNotificationDialog(time, bus?.routeNumber ?: "")
                }
            }
            container.addView(timeView)

            if (isNext) {
                foundNext = true
            } else if (!foundNext) {
                targetScrollX += (100 * resources.displayMetrics.density).toInt() 
            }
        }

        rootView.findViewById<HorizontalScrollView>(R.id.times_scroll_view)?.let { scrollView ->
            scrollView.post {
                if (isAdded) scrollView.smoothScrollTo(targetScrollX, 0)
            }
        }
    }

    private fun showNotificationDialog(time: String, busNumber: String) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("5")
            setSelection(1)
        }
        
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Уведомление")
            .setMessage("За сколько минут уведомить, что автобус $busNumber поедет в $time?")
            .setView(layout)
            .setPositiveButton("Ок") { _, _ ->
                val mins = input.text.toString()
                Toast.makeText(requireContext(), "Напомним за $mins мин.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private suspend fun loadTimesForDirection(ctx: Context, routeNum: String, dir: String?): List<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = if (dir == "B") "b_arrival_time.json" else "a_arrival_time.json"
            val path = "balahna/bus_$routeNum/$fileName"
            val json = ctx.assets.open(path).bufferedReader().use { it.readText() }
            val data: List<StopArrivalTime> = Gson().fromJson(json, object : TypeToken<List<StopArrivalTime>>() {}.type)
            data.firstOrNull { !it.times.isNullOrEmpty() }?.times ?: emptyList<String>()
        } catch (e: Exception) { emptyList<String>() }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.returnToMain()
    }

    private data class StopArrivalTime(val bus: String?, val stop: String?, val times: List<String>?)
}
