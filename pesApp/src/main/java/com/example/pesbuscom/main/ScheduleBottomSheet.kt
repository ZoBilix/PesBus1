package com.example.pesbuscom.main

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.BusStop
import com.example.pesbuscom.R
import com.example.pesbuscom.routes.BusSchedule
import com.example.pesbuscom.routes.ScheduleManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleBottomSheet : BottomSheetDialogFragment() {

    private var isRouteSelected = false
    private lateinit var containerLayout: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var progressBar: ProgressBar
    
    private var allStops: List<BusStop> = emptyList()

    companion object {
        const val REQUEST_KEY = "schedule_request"
        const val KEY_ROUTE_NUMBER = "route_number"
        fun newInstance() = ScheduleBottomSheet()
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
        val view = inflater.inflate(R.layout.dialog_schedule, container, false)
        containerLayout = view.findViewById(R.id.schedule_buttons_container)
        tvTitle = view.findViewById(R.id.tv_title)
        
        progressBar = ProgressBar(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { 
                gravity = Gravity.CENTER
                topMargin = 100 
            }
        }

        showCities()
        return view
    }

    private fun showCities() {
        containerLayout.removeAllViews()
        tvTitle.text = "Выберите город"
        
        val cities = listOf("balahna" to "Балахна")
        
        cities.forEach { (id, name) ->
            addItem(name) { showRoutes(id) }
        }
    }

    private fun showRoutes(cityId: String) {
        val safeContext = context ?: return
        containerLayout.removeAllViews()
        if (progressBar.parent == null) containerLayout.addView(progressBar)
        tvTitle.text = "Выберите маршрут"

        lifecycleScope.launch {
            val schedules = withContext(Dispatchers.IO) {
                try {
                    ScheduleManager(safeContext).loadSchedules().filter { it.direction == "A" || it.direction == null }
                } catch (e: Exception) { emptyList<BusSchedule>() }
            }
            
            if (!isAdded) return@launch
            containerLayout.removeView(progressBar)
            
            addItem("← Назад") { showCities() }

            schedules.distinctBy { it.routeNumber }.forEach { bus ->
                addItem("Маршрут ${bus.routeNumber}", bus.routeName) { 
                    showDirections(cityId, bus.routeNumber, bus.routeName) 
                }
            }
        }
    }

    private fun showDirections(cityId: String, routeNum: String, baseName: String) {
        val safeContext = context ?: return
        containerLayout.removeAllViews()
        if (progressBar.parent == null) containerLayout.addView(progressBar)
        tvTitle.text = "Выберите направление"

        lifecycleScope.launch {
            if (allStops.isEmpty()) {
                allStops = withContext(Dispatchers.IO) { loadStops(safeContext) }
            }

            val directions = withContext(Dispatchers.IO) {
                val list = mutableListOf<BusSchedule>()
                val folderPath = "balahna/bus_$routeNum"
                
                val assets = safeContext.assets
                val files = try { assets.list(folderPath) ?: emptyArray() } catch (e: Exception) { emptyArray() }
                
                if (files.contains("a_arrival_time.json")) {
                    getRouteEndpoints(safeContext, "$folderPath/a_arrival_time.json")?.let { (start, end) ->
                        list.add(BusSchedule(routeNum, "$start → $end", direction = "A"))
                    }
                }
                if (files.contains("b_arrival_time.json")) {
                    getRouteEndpoints(safeContext, "$folderPath/b_arrival_time.json")?.let { (start, end) ->
                        list.add(BusSchedule(routeNum, "$start → $end", direction = "B"))
                    }
                }

                if (list.isEmpty()) {
                    try {
                        val allSchedules = ScheduleManager(safeContext).loadSchedules()
                        list.addAll(allSchedules.filter { it.routeNumber == routeNum })
                    } catch (e: Exception) {}
                }
                list
            }

            if (!isAdded) return@launch
            containerLayout.removeView(progressBar)
            addItem("← Назад") { showRoutes(cityId) }

            if (directions.isEmpty()) {
                addItem("Нет данных") {}
            }

            directions.forEach { bus ->
                addItem(bus.routeName) {
                    val fm = parentFragmentManager
                    isRouteSelected = true
                    
                    if (!fm.isStateSaved) {
                        BusTimesBottomSheet.newInstance(bus).show(fm, "BusTimesBottomSheet")
                        fm.setFragmentResult(REQUEST_KEY, Bundle().apply { 
                            putString(KEY_ROUTE_NUMBER, bus.routeNumber) 
                        })
                    }
                    dismiss()
                }
            }
        }
    }

    private suspend fun getRouteEndpoints(ctx: Context, path: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val json = ctx.assets.open(path).bufferedReader().use { it.readText() }
            val data: List<Map<String, Any>> = Gson().fromJson(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            val stopEntries = data.filter { it.containsKey("stop") }
            if (stopEntries.isEmpty()) return@withContext null

            val firstId = stopEntries.first()["stop"] as? String
            val lastId = stopEntries.last()["stop"] as? String

            val startName = allStops.find { it.id == firstId || it.name == firstId }?.name ?: firstId ?: "?"
            val endName = allStops.find { it.id == lastId || it.name == lastId }?.name ?: lastId ?: "?"
            
            Pair(startName, endName)
        } catch (e: Exception) { null }
    }

    private fun addItem(title: String, subtitle: String? = null, onClick: () -> Unit) {
        val safeContext = context ?: return
        val density = resources.displayMetrics.density

        val frame = FrameLayout(safeContext).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8, 0, 8) }
            val shape = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((1 * density).toInt(), Color.parseColor("#F0F0F0"))
                cornerRadius = 12 * density
            }
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#F5F5F5")), shape, shape)
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            setOnClickListener { onClick() }
        }

        val layout = LinearLayout(safeContext).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(TextView(safeContext).apply {
            text = title
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(safeContext, R.color.blue))
        })

        if (subtitle != null) {
            layout.addView(TextView(safeContext).apply {
                text = subtitle
                textSize = 14f
                setTextColor(Color.GRAY)
                setPadding(0, 4, 0, 0)
            })
        }
        
        frame.addView(layout)
        containerLayout.addView(frame)
    }

    private fun loadStops(ctx: Context): List<BusStop> = try {
        val json = ctx.assets.open("bus_stops.json").bufferedReader().use { it.readText() }
        Gson().fromJson(json, object : TypeToken<List<BusStop>>() {}.type)
    } catch (e: Exception) { emptyList<BusStop>() }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!isRouteSelected) (activity as? MainActivity)?.returnToMain()
    }
}
