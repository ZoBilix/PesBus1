package com.example.pesbuscom.main

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.BusStop
import com.example.pesbuscom.R
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

class StopRoutesBottomSheet : BottomSheetDialogFragment() {

    private var isRouteSelected = false
    private val stop: BusStop? by lazy { 
        arguments?.getSerializable(ARG_STOP) as? BusStop 
    }
    
    private var selectedCalendar = Calendar.getInstance()
    private var tvDate: TextView? = null
    private lateinit var containerLayout: LinearLayout

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
        containerLayout = view.findViewById(R.id.schedule_buttons_container)
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        
        val currentStop = stop ?: run { dismiss(); return null }
        tvTitle.text = currentStop.name

        // Добавляем выбор даты
        val dateLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val dateTv = TextView(requireContext()).apply {
            textSize = 16f
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        tvDate = dateTv
        updateDateText()

        val btnCalendar = ImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_calendar)
            background = null
            setOnClickListener { showDatePicker() }
        }

        dateLayout.addView(dateTv)
        dateLayout.addView(btnCalendar)
        containerLayout.addView(dateLayout)

        loadData()

        return view
    }

    private fun showDatePicker() {
        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            selectedCalendar.set(Calendar.YEAR, year)
            selectedCalendar.set(Calendar.MONTH, month)
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateText()
            loadData()
        }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateText() {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        tvDate?.text = "Дата: ${sdf.format(selectedCalendar.time)}"
    }

    private fun loadData() {
        val currentStop = stop ?: return
        
        // Очищаем список (но оставляем выбор даты)
        val count = containerLayout.childCount
        if (count > 1) {
            containerLayout.removeViews(1, count - 1)
        }

        val progressBar = ProgressBar(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER
                setMargins(0, 50, 0, 0)
            }
        }
        containerLayout.addView(progressBar)

        lifecycleScope.launch {
            val arrivals = withContext(Dispatchers.IO) {
                loadArrivalsTask(currentStop)
            }
            
            if (!isAdded) return@launch
            containerLayout.removeView(progressBar)
            displayArrivals(containerLayout, arrivals)
        }
    }

    private suspend fun loadArrivalsTask(currentStop: BusStop): List<Pair<String, String>> {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val isToday = isSameDay(selectedCalendar, Calendar.getInstance())
        val filterTime = if (isToday) sdf.format(Date()) else "00:00"
        
        val assetManager = try { requireContext().assets } catch (e: Exception) { return emptyList() }
        val arrivals = mutableListOf<Pair<String, String>>()

        try {
            val balahnaDirs = assetManager.list("balahna") ?: emptyArray()
            for (busDir in balahnaDirs) {
                if (!busDir.startsWith("bus_")) continue
                
                val files = assetManager.list("balahna/$busDir") ?: emptyArray()
                for (file in files) {
                    if (!file.endsWith("_arrival_time.json")) continue
                    
                    val dataList = loadArrivalData("balahna/$busDir/$file")
                    val data = dataList?.find { it.stop == currentStop.id || it.stop == currentStop.name }
                    
                    data?.times?.filter { it >= filterTime }?.forEach { time ->
                        val busNumber = busDir.substringAfter("bus_")
                        arrivals.add(time to busNumber)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return arrivals
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun displayArrivals(container: LinearLayout, arrivals: List<Pair<String, String>>) {
        val uniqueArrivals = arrivals.distinctBy { it.first + it.second }.sortedBy { it.first }

        if (uniqueArrivals.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "Рейсов больше нет"
                gravity = Gravity.CENTER
                setPadding(0, 100, 0, 0)
                setTextColor(Color.GRAY)
            })
        } else {
            uniqueArrivals.forEach { (time, busNumber) ->
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

        // Добавляем номер автобуса
        val busText = TextView(context).apply {
            text = busNumber
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setMargins(0, 0, (64 * density).toInt(), 0) // Оставляем место под колокольчик
            }
        }
        frame.addView(busText)

        // Добавляем колокольчик (уведомление)
        val btnNotify = ImageView(context).apply {
            setImageResource(R.drawable.ic_notification)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            layoutParams = FrameLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt()).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setMargins(0, 0, (8 * density).toInt(), 0)
            }
            setOnClickListener {
                showNotificationDialog(time, busNumber)
            }
        }
        frame.addView(btnNotify)

        frame.setOnClickListener { 
            isRouteSelected = true
            setFragmentResult(REQUEST_KEY, Bundle().apply { putString(KEY_ROUTE_NUMBER, busNumber) })
            dismiss()
        }
        
        container.addView(frame)
    }

    private fun showNotificationDialog(time: String, busNumber: String) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("5")
            setSelection(1)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Настройка уведомления")
            .setMessage("За сколько минут уведомить, что автобус $busNumber приедет в $time?")
            .setView(input.apply { 
                setPadding(60, 20, 60, 20)
            })
            .setPositiveButton("Установить") { _, _ ->
                val minsStr = input.text.toString()
                val mins = minsStr.toIntOrNull() ?: 5
                scheduleNotification(time, busNumber, mins)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun scheduleNotification(time: String, busNumber: String, minsBefore: Int) {
        try {
            val busTimeParts = time.split(":")
            val busCalendar = (selectedCalendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, busTimeParts[0].toInt())
                set(Calendar.MINUTE, busTimeParts[1].toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val notificationTime = (busCalendar.clone() as Calendar).apply {
                add(Calendar.MINUTE, -minsBefore)
            }

            val currentTime = Calendar.getInstance()

            if (notificationTime.before(currentTime)) {
                Toast.makeText(requireContext(), "Невозможно установить: время уже прошло", Toast.LENGTH_LONG).show()
                return
            }

            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(requireContext(), NotificationReceiver::class.java).apply {
                putExtra("bus_number", busNumber)
                putExtra("time", time)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                (busNumber.hashCode() + time.hashCode()),
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notificationTime.timeInMillis, pendingIntent)
                } else {
                    // Fallback to inexact if permission not granted
                    alarmManager.set(AlarmManager.RTC_WAKEUP, notificationTime.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notificationTime.timeInMillis, pendingIntent)
            }

            Toast.makeText(requireContext(), "Уведомление установлено за $minsBefore мин.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка при установке уведомления", Toast.LENGTH_SHORT).show()
        }
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
