package com.example.myapplication.models

import com.google.gson.annotations.SerializedName

data class Bus(
    @SerializedName("id", alternate = ["i", "bus_id", "u"]) 
    val id: String,
    
    @SerializedName("route_id", alternate = ["r", "route_name", "r_id"]) 
    val routeId: String,
    
    @SerializedName("lat", alternate = ["y", "latitude"]) 
    val lat: Double,
    
    @SerializedName("lon", alternate = ["x", "longitude"]) 
    val lon: Double,
    
    @SerializedName("speed", alternate = ["s"]) 
    val speed: Int = 0,

    @SerializedName("heading", alternate = ["h", "angle"])
    val heading: Float = 0f,

    @SerializedName("last_update", alternate = ["t", "time", "ts"]) 
    val lastUpdate: Long = 0L
)