package com.example.myapplication

object CsvParser {

    fun parseBusLocations(csv: String): List<BusLocation> {
        val lines = csv.trim().split("\n")
        val buses = mutableListOf<BusLocation>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // Пропускаем пустые строки и комментарии
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Пропускаем заголовок (первая строка)
            if (index == 0 && trimmed.contains("device", ignoreCase = true)) continue

            BusLocation.fromCsvLine(trimmed)?.let { buses.add(it) }
        }

        return buses
    }
}