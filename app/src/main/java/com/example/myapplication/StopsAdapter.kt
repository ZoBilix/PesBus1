package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class StopsAdapter(
    private val stops: List<BusStop>,
    private val onItemClick: (BusStop) -> Unit
) : RecyclerView.Adapter<StopsAdapter.StopViewHolder>() {

    inner class StopViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val stopName: TextView = itemView.findViewById(R.id.stop_name)
        val stopRoutes: TextView = itemView.findViewById(R.id.stop_routes)
        val stopDistance: TextView = itemView.findViewById(R.id.stop_distance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stop, parent, false)
        return StopViewHolder(view)
    }

    override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
        val stop = stops[position]

        holder.stopName.text = stop.name
        holder.stopRoutes.text = "Маршруты: ${stop.routes.joinToString(", ")}"
        holder.stopDistance.text = "📍 ${stop.id}" // Здесь можно рассчитать расстояние

        holder.cardView.setOnClickListener {
            onItemClick(stop)
        }
    }

    override fun getItemCount() = stops.size
}