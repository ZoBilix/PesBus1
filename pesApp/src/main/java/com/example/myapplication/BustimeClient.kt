package com.example.myapplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object BustimeClient {

    private const val CSV_URL = "https://busti.me/api/bustime.csv"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchBusLocations(): Result<List<BusLocation>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CSV_URL)
                .addHeader("User-Agent", "BusMap-Android/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val csv = response.body?.string() ?: ""
                if (csv.isEmpty()) {
                    return@withContext Result.failure(Exception("Пустой ответ"))
                }

                val buses = CsvParser.parseBusLocations(csv)
                Result.success(buses)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}