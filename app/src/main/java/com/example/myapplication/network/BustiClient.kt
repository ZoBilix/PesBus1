package com.example.myapplication.network

import android.util.Log
import com.example.myapplication.models.Bus
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class BustiClient(
    private val city: String = "balahna",
    private val onBusesUpdate: (List<Bus>) -> Unit,
    private val onBusPosition: (Bus) -> Unit,
    private val onConnectionStatus: (Boolean) -> Unit = {}
) {
    private var socket: Socket? = null
    private val gson = Gson()
    
    fun connect() {
        try {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Origin", "https://ru.busti.me")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val options = IO.Options()
            options.path = "/socket.io/"
            options.transports = arrayOf("websocket")
            options.upgrade = true
            options.reconnection = true
            options.webSocketFactory = okHttpClient
            options.callFactory = okHttpClient
            
            socket = IO.socket("https://ru.busti.me", options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("BustiClient", "✅ Соединение установлено")
                onConnectionStatus(true)
                socket?.emit("set_city", city) 
                socket?.emit("get_buses", city)
                socket?.emit("subscribe_updates", true)
            }
            
            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("BustiClient", "❌ Отключено")
                onConnectionStatus(false)
            }

            // Список событий для Балахны (основные маршруты + общие каналы)
            val events = arrayOf("buses", "cars", "c", "v", "430", "203", "17", "7", "11", "102", "57")
            events.forEach { eventName ->
                socket?.on(eventName) { args ->
                    if (args != null && args.isNotEmpty()) {
                        val data = args[0].toString()
                        val buses = parseBustimeData(data, eventName)
                        if (buses.isNotEmpty()) {
                            Log.d("BustiClient", "[$eventName] Получено автобусов: ${buses.size}")
                            onBusesUpdate(buses)
                        }
                    }
                }
            }
            
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("BustiClient", "Ошибка подключения: ${args.getOrNull(0)}")
                onConnectionStatus(false)
            }
            
            socket?.connect()
        } catch (e: Exception) {
            Log.e("BustiClient", "Ошибка: ${e.message}")
        }
    }

    private fun parseBustimeData(json: String, eventName: String? = null): List<Bus> {
        val result = mutableListOf<Bus>()
        try {
            if (json.startsWith("[")) {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    
                    if (item.has("bdata_mode10")) {
                        val mode10 = item.getJSONObject("bdata_mode10")
                        val busIdFromMode = mode10.optString("bus_id")
                        val locations = mode10.optJSONArray("l")
                        if (locations != null) {
                            for (j in 0 until locations.length()) {
                                val loc = locations.getJSONObject(j)
                                // ИСПОЛЬЗУЕМ 'u' как уникальный ID МАШИНЫ
                                val uid = loc.optString("u").ifEmpty { loc.optString("id", busIdFromMode) }
                                if (uid.isEmpty() || uid == "0") continue

                                result.add(Bus(
                                    id = uid,
                                    // Если имя события число (например "430"), используем его как номер маршрута
                                    routeId = eventName?.filter { it.isDigit() } ?: busIdFromMode,
                                    lat = loc.optDouble("y"), // широта
                                    lon = loc.optDouble("x"), // долгота
                                    speed = loc.optInt("s"),
                                    heading = loc.optDouble("h").toFloat(),
                                    lastUpdate = loc.optLong("ts")
                                ))
                            }
                        }
                    } else if (item.has("x") && item.has("y")) {
                        val uid = item.optString("u", item.optString("id", "bus_$i"))
                        result.add(Bus(
                            id = uid,
                            routeId = eventName ?: "Bus",
                            lat = item.optDouble("y"),
                            lon = item.optDouble("x"),
                            speed = item.optInt("s"),
                            heading = item.optDouble("h").toFloat()
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BustiClient", "Ошибка парсинга: ${e.message}")
        }
        return result
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
    }
}
