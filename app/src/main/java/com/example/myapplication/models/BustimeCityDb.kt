package com.example.myapplication.models

import com.google.gson.annotations.SerializedName

/**
 * Модель для парсинга "мини-базы" города с Bustime (формат v8-mini)
 */
data class BustimeCityDb(
    @SerializedName("nbusstop")
    val stops: Map<String, List<Any>>, // ID -> [name, lon, lat, slug, place_id, tram_only, unistop]
    
    @SerializedName("route")
    val routes: Map<String, List<Any>>, // RecordID -> [bus_id, busstop_id, direction, order]
    
    @SerializedName("bus")
    val buses: Map<String, List<Any>>   // ID -> [active, name, slug, ...]
)