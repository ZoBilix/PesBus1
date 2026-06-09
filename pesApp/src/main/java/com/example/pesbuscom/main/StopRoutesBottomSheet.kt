package com.example.pesbuscom.main

import android.annotation.SuppressLint
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class StopRoutesBottomSheet : BottomSheetDialogFragment() {

    private var isRouteSelected = false
    private val stop: BusStop? by lazy { 
        arguments?.getSerializable(ARG_STOP) as? BusStop 
    }

    companion object {
        private const val ARG_STOP = "arg_stop"
        const val REQUEST_KEY = "stop_route_request"
        const val KEY_ROUTE_NUMBER = "route_number"

        fun newInstance(stop: BusStop): StopRoutesBottomSheet {
            return StopRoutesBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_STOP, stop)
                }
            }
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            it.setBackgroundResource(R.drawable.bg_rounded_bottom_sheet)
            val behavior = BottomSheetBehavior.from(it)
            behavior.isFitToContents = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_schedule, container, false)
        val containerLayout = view.findViewById<LinearLayout>(R.id.schedule_buttons_container)
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        
        val currentStop = stop ?: run { dismiss(); return null }
        
        val progressBar = ProgressBar(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER
                setMargins(0, 50, 0, 0)
            }
        }
        containerLayout.addView(progressBar)

        tvTitle.text = currentStop.name

        lifecycleScope.launch {
            val isDev = SettingsManager.getDeveloperModeFlow(requireContext()).first()
            if (isDev) {
                tvTitle.text = "${currentStop.name} (ID: ${currentStop.id})"
            }

            val arrivals = withContext(Dispatchers.IO) {
                loadArrivalsTask(currentStop)
            }
            
            containerLayout.removeView(progressBar)
            displayArrivals(containerLayout, arrivals)
        }

        return view
    }

    private suspend fun loadArrivalsTask(currentStop: BusStop): List<Pair<String, String>> {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val assetManager = requireContext().assets
        val arrivals = mutableListOf<Pair<String, String>>()

        try {
            assetManager.list("")?.forEach { city ->
                assetManager.list(city)?.filter { it.startsWith("bus_") }?.forEach { busDir ->
                    assetManager.list("$city/$busDir")?.filter { it.endsWith("_arrival_time.json") }?.forEach { file ->
                        val dataList = loadArrivalData("$city/$busDir/$file")
                        val data = dataList?.find { it.stop == currentStop.id || it.stop == currentStop.name }
                        data?.times?.filter { it >= currentTime }?.forEach { time ->
                            val busNumber = busDir.substringAfter("bus_").substringBefore("_")
                            arrivals.add(time to busNumber)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return arrivals.sortedBy { it.first }
    }

    private fun displayArrivals(container: LinearLayout, arrivals: List<Pair<String, String>>) {
        container.removeAllViews()

        if (arrivals.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "Рейсов больше нет"
                gravity = Gravity.CENTER
                setPadding(0, 100, 0, 0)
                setTextColor(Color.GRAY)
            })
        } else {
            arrivals.forEach { (time, busNumber) ->
                addArrivalItem(container, time, busNumber)
            }
        }
    }

    private fun addArrivalItem(container: LinearLayout, time: String, busNumber: String) {
        val context = requireContext()
        val density = resources.displayMetrics.density

        val frame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (60 * density).toInt()).apply {
                setMargins(0, 4, 0, 4)
            }
            
            val backgroundShape = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((1 * density).toInt(), Color.parseColor("#F0F0F0"))
                cornerRadius = 12 * density
            }
            
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#F5F5F5")),
                backgroundShape,
                backgroundShape
            )
            isClickable = true
            isFocusable = true
        }

        frame.addView(TextView(context).apply {
            text = time
            textSize = 22f
            setTextColor(ContextCompat.getColor(context, R.color.blue))
            setTypeface(null, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setMargins((24 * density).toInt(), 0, 0, 0)
            }
        })

        frame.addView(TextView(context).apply {
            text = busNumber
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setMargins(0, 0, (24 * density).toInt(), 0)
            }
        })

        frame.setOnClickListener { 
            isRouteSelected = true
            setFragmentResult(REQUEST_KEY, Bundle().apply { putString(KEY_ROUTE_NUMBER, busNumber) })
            dismiss()
        }
        
        container.addView(frame)
    }

    private fun loadArrivalData(path: String): List<StopArrivalTime>? = try {
        Gson().fromJson(requireContext().assets.open(path).bufferedReader().readText(), object : TypeToken<List<StopArrivalTime>>() {}.type)
    } catch (e: Exception) { null }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!isRouteSelected) {
            (activity as? MainActivity)?.returnToMain()
        }
    }

    data class StopArrivalTime(val bus: String?, val stop: String?, val times: List<String>?)
}
