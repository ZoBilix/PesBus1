package com.example.myapplication

object BustimeManager {

    // ✅ Получить местоположения автобусов (CSV)
    suspend fun getBusLocations(): Result<List<BusLocation>> {
        return BustimeClient.fetchBusLocations()
    }

    // ✅ Получить остановки поблизости
    suspend fun getNearbyStops(
        lat: Double,
        lon: Double,
        radius: Int = 1000
    ): Result<List<BusStop>> {
        // Пока возвращаем демо-данные
        return Result.success(getDemoStops())
    }

    // ✅ Получить расписание для остановки
    suspend fun getScheduleForStop(
        stopId: String,
        radius: Int = 500
    ): Result<BusSchedule> {
        return Result.failure(Exception("API временно недоступен"))
    }

    // ✅ Демо-остановки
    private fun getDemoStops(): List<BusStop> {
        return listOf(
            BusStop(
                id = "stop_1",
                name = "🚏 Красная площадь",
                latitude = 55.753930,
                longitude = 37.620808,
                routes = listOf("1", "5", "10", "25", "А")
            ),
            BusStop(
                id = "stop_2",
                name = "🚏 Театральная",
                latitude = 55.760226,
                longitude = 37.618423,
                routes = listOf("2", "7", "15", "М")
            ),
            BusStop(
                id = "stop_3",
                name = "🚏 Парк Горького",
                latitude = 55.731024,
                longitude = 37.601631,
                routes = listOf("3", "8", "20", "Б")
            ),
            BusStop(
                id = "stop_4",
                name = "🚏 Киевская",
                latitude = 55.743611,
                longitude = 37.573056,
                routes = listOf("4", "9", "17", "В")
            ),
            BusStop(
                id = "stop_5",
                name = "🚏 Белорусская",
                latitude = 55.776111,
                longitude = 37.580278,
                routes = listOf("6", "11", "22", "Г")
            )
        )
    }
}