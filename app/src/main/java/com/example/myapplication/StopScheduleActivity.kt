package com.example.myapplication

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class StopScheduleActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var stopNameText: TextView
    private lateinit var scheduleRecycler: RecyclerView
    private lateinit var loadingIndicator: android.widget.ProgressBar

    private val busesList = mutableListOf<BusArrival>()
    private var stopId: String? = null
    private var stopName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop_schedule)

        toolbar = findViewById(R.id.toolbar)
        stopNameText = findViewById(R.id.stop_name_text)
        scheduleRecycler = findViewById(R.id.schedule_recycler)
        loadingIndicator = findViewById(R.id.loading_indicator)

        // Получаем данные из Intent
        stopId = intent.getStringExtra("stop_id")
        stopName = intent.getStringExtra("stop_name")

        // Toolbar
        toolbar.title = stopName ?: "Остановка"
        toolbar.setNavigationOnClickListener { finish() }

        // Загружаем расписание
        loadSchedule()
    }

    private fun loadSchedule() {
        if (stopId == null) {
            Toast.makeText(this, "Ошибка: ID остановки не указан", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val result = BustimeManager.getScheduleForStop(stopId!!)

            result.onSuccess { schedule ->
                busesList.clear()
                busesList.addAll(schedule.buses)

                stopNameText.text = schedule.stopName

                val adapter = BusScheduleAdapter(busesList)
                scheduleRecycler.layoutManager = LinearLayoutManager(this@StopScheduleActivity)
                scheduleRecycler.adapter = adapter

                loadingIndicator.visibility = android.view.View.GONE
                scheduleRecycler.visibility = android.view.View.VISIBLE
            }.onFailure { error ->
                Toast.makeText(
                    this@StopScheduleActivity,
                    "Ошибка загрузки расписания: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
                loadingIndicator.visibility = android.view.View.GONE
            }
        }
    }
}

// Adapter для расписания
class BusScheduleAdapter(
    private val buses: List<BusArrival>
) : RecyclerView.Adapter<BusScheduleAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val routeName: TextView = itemView.findViewById(R.id.bus_route_name)
        val arrivalTime: TextView = itemView.findViewById(R.id.bus_arrival_time)
        val licensePlate: TextView = itemView.findViewById(R.id.bus_license_plate)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bus_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bus = buses[position]
        holder.routeName.text = "🚌 ${bus.routeName}"
        holder.arrivalTime.text = "⏰ ${bus.arrivalTime} (${bus.estimatedMinutes} мин)"
        holder.licensePlate.text = "🚗 ${bus.licensePlate}"
    }

    override fun getItemCount() = buses.size
}