package com.example.myapplication.routes
import com.example.myapplication.BusStop

object RouteData {
    fun getRoute57Stops(): List<BusStop> = listOf(
        BusStop("57_1", "Улица Горького", 56.461537, 43.528394, listOf("57")),
        BusStop("57_2", "Площадь Свободы", 56.463536, 43.535606, listOf("57")),
        BusStop("57_3", "Парк Культуры", 56.465566, 43.541717, listOf("57")),
        BusStop("57_4", "Конечная", 56.465881, 43.549596, listOf("57"))
    )


}