package com.example.pesbuscom.main

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.pesbuscom.R
import com.example.pesbuscom.routes.BusSchedule
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.*

class BusTimesBottomSheet : BottomSheetDialogFragment() {

    private val bus: BusSchedule? by lazy {
        arguments?.getSerializable(ARG_BUS) as? BusSchedule
    }
    private val times: List<String> by lazy {
        arguments?.getStringArrayList(ARG_TIMES) ?: bus?.schedule ?: emptyList()
    }

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
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.setBackgroundResource(R.drawable.bg_rounded_bottom_sheet)
        
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isFitToContents = false
        behavior.halfExpandedRatio = 0.6f
        behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        behavior.skipCollapsed = true
        behavior.isDraggable = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_bus_times, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val currentBus = bus ?: run { dismiss(); return }

        val dragArea = view.findViewById<View>(R.id.drag_area)
        val bottomSheet = (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val behavior = bottomSheet?.let { BottomSheetBehavior.from(it) }

        dragArea?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                behavior?.isDraggable = true
            }
            false
        }

        behavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState != BottomSheetBehavior.STATE_DRAGGING && newState != BottomSheetBehavior.STATE_SETTLING) {
                    behavior.isDraggable = false
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        view.findViewById<TextView>(R.id.tv_route_title).text = "Маршрут ${currentBus.routeNumber}"
        view.findViewById<TextView>(R.id.tv_route_name).text = currentBus.routeName

        val timesContainer = view.findViewById<LinearLayout>(R.id.times_container)
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        
        times.sortedBy { if (it < currentTime) "24:$it" else "00:$it" }.forEach { time ->
            timesContainer.addView(TextView(requireContext()).apply {
                text = time; textSize = 18f; setPadding(40, 20, 40, 20)
                setTextColor(ContextCompat.getColor(context, if (time >= currentTime && times.filter { it >= currentTime }.minOrNull() == time) android.R.color.holo_green_dark else R.color.blue))
                background = ContextCompat.getDrawable(context, R.drawable.bg_time_chip)
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(15, 0, 15, 0) }
            })
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.returnToMain()
    }
}
