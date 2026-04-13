package com.example.myapplication

data class BusStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val routes: List<String>
)

data class BusLocation(
    val deviceId: String,
    val timestamp: String,
    val longitude: Double,
    val latitude: Double,
    val licensePlate: String,
    val routeName: String,
    val speed: Int,
    val wheelchairAccessible: Boolean
) {
    companion object {
        fun fromCsvLine(line: String): BusLocation? {
            val fields = line.split(",").map { it.trim() }
            if (fields.size < 8) return null

            return try {
                BusLocation(
                    deviceId = fields[0],
                    timestamp = fields[1],
                    longitude = fields[2].toDoubleOrNull() ?: return null,
                    latitude = fields[3].toDoubleOrNull() ?: return null,
                    licensePlate = fields[4],
                    routeName = fields[5],
                    speed = fields[6].toIntOrNull() ?: 0,
                    wheelchairAccessible = fields[7] == "1"
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class BusSchedule(
    val stopId: String,
    val stopName: String,
    val buses: List<BusArrival>
)

data class BusArrival(
    val routeName: String,
    val arrivalTime: String,
    val licensePlate: String,
    val estimatedMinutes: Int,
    val wheelchairAccessible: Boolean
)