package com.example.pesbuscom.main

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.R
import com.example.pesbuscom.routes.ScheduleManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleBottomSheet : BottomSheetDialogFragment() {

    private var isRouteSelected = false

    companion object {
        const val REQUEST_KEY = "schedule_request"
        const val KEY_ROUTE_NUMBER = "route_number"

        fun newInstance(): ScheduleBottomSheet {
            return ScheduleBottomSheet()
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.setBackgroundResource(R.drawable.bg_rounded_bottom_sheet)
            val behavior = BottomSheetBehavior.from(it)
            behavior.isFitToContents = false
            behavior.halfExpandedRatio = 0.6f
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_schedule, container, false)
        val containerLayout = view.findViewById<LinearLayout>(R.id.schedule_buttons_container)
        val dragArea = view.findViewById<View>(R.id.drag_area)

        val progressBar = ProgressBar(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.CENTER
                topMargin = 100
            }
        }
        containerLayout.addView(progressBar)

        view.post {
            val bottomSheet = (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                
                dragArea.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        behavior.isDraggable = true
                    }
                    false
                }

                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState != BottomSheetBehavior.STATE_DRAGGING && newState != BottomSheetBehavior.STATE_SETTLING) {
                            behavior.isDraggable = false
                        }
                    }
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {}
                })
            }
        }

        lifecycleScope.launch {
            val schedules = withContext(Dispatchers.IO) {
                ScheduleManager(requireContext()).loadSchedules()
            }
            
            containerLayout.removeView(progressBar)
            
            schedules.forEach { bus ->
                containerLayout.addView(MaterialButton(requireContext()).apply {
                    text = "Маршрут ${bus.routeNumber}\n${bus.routeName}"
                    setTextColor(ContextCompat.getColor(context, R.color.blue))
                    backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                    strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blue))
                    strokeWidth = (1 * resources.displayMetrics.density).toInt()
                    elevation = 0f
                    setPadding(20, 30, 20, 30)
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 12) }
                    setOnClickListener { 
                        isRouteSelected = true
                        BusTimesBottomSheet.newInstance(bus).show(parentFragmentManager, "BusTimesBottomSheet")
                        setFragmentResult(REQUEST_KEY, Bundle().apply { putString(KEY_ROUTE_NUMBER, bus.routeNumber) })
                        dismiss() 
                    }
                })
            }
        }
        
        return view
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!isRouteSelected) {
            (activity as? MainActivity)?.returnToMain()
        }
    }
}
