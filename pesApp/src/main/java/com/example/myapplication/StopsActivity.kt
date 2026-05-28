package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class StopsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var stopsRecycler: RecyclerView
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var loadingIndicator: android.widget.ProgressBar

    private lateinit var adapter: StopsAdapter
    private val stopsList = mutableListOf<BusStop>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stops)

        toolbar = findViewById(R.id.toolbar)
        stopsRecycler = findViewById(R.id.stops_recycler)
        fabMyLocation = findViewById(R.id.fab_my_location)
        loadingIndicator = findViewById(R.id.loading_indicator)

        // Toolbar
        toolbar.setNavigationOnClickListener { finish() }

        // RecyclerView
        adapter = StopsAdapter(stopsList) { stop ->
            openStopSchedule(stop)
        }
        stopsRecycler.layoutManager = LinearLayoutManager(this)
        stopsRecycler.adapter = adapter

        // Загрузка остановок
        loadStops()

        // Кнопка моей локации
        fabMyLocation.setOnClickListener {
            loadNearbyStops()
        }
    }

    private fun loadStops() {
        showLoading(true)

        lifecycleScope.launch {
            // Пример: загружаем остановки поблизости (координаты Минска)
            val result = BustimeManager.getNearbyStops(53.9045, 27.5615, 5000)

            showLoading(false)

            result.onSuccess { stops ->
                stopsList.clear()
                stopsList.addAll(stops)
                adapter.notifyDataSetChanged()
            }.onFailure { error ->
                Toast.makeText(
                    this@StopsActivity,
                    "Ошибка загрузки: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadNearbyStops() {
        // TODO: Получить текущую локацию и загрузить остановки рядом
        Toast.makeText(this, "Загрузка остановок поблизости...", Toast.LENGTH_SHORT).show()
        loadStops()
    }

    private fun openStopSchedule(stop: BusStop) {
        val intent = android.content.Intent(this, StopScheduleActivity::class.java)
        intent.putExtra("stop_id", stop.id)
        intent.putExtra("stop_name", stop.name)
        startActivity(intent)
    }

    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        stopsRecycler.visibility = if (isLoading) View.GONE else View.VISIBLE
    }
}