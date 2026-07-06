package com.catenarymaps.catenary

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors

private const val TAG = "RamondaWebSocket"

@Serializable
data class SubscribeTrip(
        @EncodeDefault val type: String = "subscribe_trip",
        val chateau: String,
        val trip_id: String,
        val route_id: String? = null,
        val start_date: String? = null,
        val start_time: String? = null
)

@Serializable
data class UnsubscribeTrip(
        @EncodeDefault val type: String = "unsubscribe_trip",
        val chateau: String,
        val trip_id: String? = null,
        val route_id: String? = null,
        val start_date: String? = null,
        val start_time: String? = null
)

@Serializable
data class RamondaCommonMessage(
        val type: String,
        val data: JsonElement? = null, // For initial_trip and update_trip
        val message: String? = null // For error
)

object RamondaWebSocket {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    // State Flows
    private val _ramondaStatus = MutableStateFlow("disconnected")
    val ramondaStatus: StateFlow<String> = _ramondaStatus.asStateFlow()

    private val _ramondaTripData = MutableStateFlow<JsonElement?>(null)
    val ramondaTripData: StateFlow<JsonElement?> = _ramondaTripData.asStateFlow()

    private val _ramondaUpdateData = MutableStateFlow<JsonElement?>(null)
    val ramondaUpdateData: StateFlow<JsonElement?> = _ramondaUpdateData.asStateFlow()

    private val _ramondaError = MutableStateFlow<String?>(null)
    val ramondaError: StateFlow<String?> = _ramondaError.asStateFlow()

    private var activeTripParams: SubscribeTrip? = null

    private var pingJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    fun init() {
        ensureConnection()
    }

    private fun ensureConnection() {
        if (webSocket != null &&
                        (_ramondaStatus.value == "connected" || _ramondaStatus.value == "connecting")
        ) {
            return
        }

        _ramondaStatus.value = "connecting"
        val request = Request.Builder().url("wss://ramonda.catenarymaps.org/ws/").build()

        webSocket =
                client.newWebSocket(
                        request,
                        object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                Log.d(TAG, "Ramonda WS Connected")
                                _ramondaStatus.value = "connected"
                                startPingLoop(webSocket)

                                activeTripParams?.let {
                                    Log.d(TAG, "Resending active trip params")
                                    sendTripSubscription(it)
                                }
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                scope.launch {
                                    try {
                                        val msg = json.decodeFromString<RamondaCommonMessage>(text)
                                        when (msg.type) {
                                            "initial_trip" -> _ramondaTripData.value = msg.data
                                            "update_trip" -> _ramondaUpdateData.value = msg.data
                                            "pong" -> {
                                                Log.d(TAG, "Ramonda WS received pong")
                                            }
                                            "error" -> {
                                                _ramondaError.value = msg.message
                                                Log.e(TAG, "Ramonda WS Error: ${msg.message}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing Ramonda WS message", e)
                                    }
                                }
                            }

                            override fun onClosing(
                                    webSocket: WebSocket,
                                    code: Int,
                                    reason: String
                            ) {
                                Log.d(TAG, "Ramonda WS Closing: $code / $reason")
                                _ramondaStatus.value = "disconnected"
                                this@RamondaWebSocket.webSocket = null
                                pingJob?.cancel()
                                pingJob = null
                            }

                            override fun onFailure(
                                    webSocket: WebSocket,
                                    t: Throwable,
                                    response: Response?
                            ) {
                                Log.e(TAG, "Ramonda WS Failure", t)
                                _ramondaStatus.value = "error"
                                this@RamondaWebSocket.webSocket = null
                                pingJob?.cancel()
                                pingJob = null

                                // Retry connection after delay
                                scope.launch {
                                    delay(5000)
                                    ensureConnection()
                                }
                            }
                        }
                )
    }

    fun subscribeTrip(
        chateau: String,
        tripId: String,
        routeId: String? = null,
        startDate: String? = null,
        startTime: String? = null
    ) {
        ensureConnection()
        val params =
            SubscribeTrip(
                chateau = chateau,
                trip_id = tripId,
                route_id = routeId,
                start_date = startDate,
                start_time = startTime
            )
        activeTripParams = params
        sendTripSubscription(params)
    }

    fun unsubscribeTrip(chateau: String) {
        val paramsToSend = activeTripParams
        activeTripParams = null

        if (_ramondaStatus.value == "connected") {
            try {
                val msg =
                    UnsubscribeTrip(
                        chateau = chateau,
                        trip_id = paramsToSend?.trip_id,
                        route_id = paramsToSend?.route_id,
                        start_date = paramsToSend?.start_date,
                        start_time = paramsToSend?.start_time
                    )
                val text = json.encodeToString(msg)
                webSocket?.send(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending unsubscribe trip", e)
            }
        }
    }

    private fun startPingLoop(ws: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (_ramondaStatus.value == "connected") {
                delay(10000)
                try {
                    ws.send("{\"type\":\"ping\"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending ping", e)
                }
            }
        }
    }

    private fun sendTripSubscription(params: SubscribeTrip) {
        if (_ramondaStatus.value == "connected") {
            try {
                val text = json.encodeToString(params)
                webSocket?.send(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending trip subscription", e)
            }
        }
    }
}
