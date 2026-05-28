package com.example.myapplication.models

import com.google.gson.annotations.SerializedName

data class Bus(
    @SerializedName("board_uid", alternate = ["id", "i", "bus_id", "u"]) 
    val id: String,
    
    @SerializedName("inner_id", alternate = ["route_id", "r", "route_name", "r_id"]) 
    val routeId: String,
    
    @SerializedName("lat", alternate = ["y", "latitude"]) 
    val lat: Double,
    
    @SerializedName("lon", alternate = ["x", "longitude"]) 
    val lon: Double,
    
    @SerializedName("speed", alternate = ["s"]) 
    val speed: Int = 0,

    @SerializedName("heading", alternate = ["h", "angle"])
    val heading: Float = 0f,

    @SerializedName("timestamp", alternate = ["last_update", "t", "time", "ts"]) 
    val lastUpdate: Long = 0L
)