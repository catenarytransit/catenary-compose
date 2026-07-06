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

private const val TAG = "SpruceWebSocket"

@Serializable
data class MapViewportUpdate(
        @EncodeDefault val type: String = "subscribe_map_v2",
        val categories: List<String>,
        val bounds_input: BoundsInput
)



@Serializable
data class SubscribeTrajectories(
        @EncodeDefault val type: String = "subscribe_trajectories",
        val bbox: List<Double>,
        val zoom: Int,
        val modes: List<String>,
        //val precision: Int? = null,
        @EncodeDefault val client_reference: String = "trajectories_layer"
)

@Serializable
data class UnsubscribeTrajectories(
        @EncodeDefault val type: String = "unsubscribe_trajectories"
)

@Serializable
data class SpruceCommonMessage(
        val type: String,
        val data: JsonElement? = null, // For initial_trip and update_trip
        val chateaus: Map<String, EachChateauResponseV2>? =
                null, // For map_update (top level in legacy/TS sometimes?)
        val map_update: BulkRealtimeResponseV2? = null, // In case it's wrapped
        val message: String? = null, // For error
        val content: List<TrajectoryWrapper>? = null, // For buffer (trajectories)
        val timestamp: Long? = null,
        val chateau: String? = null,
        val total_chunks: Int? = null,
        val chunk_index: Int? = null
)

object SpruceWebSocket {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    // State Flows
    private val _spruceStatus = MutableStateFlow("disconnected")
    val spruceStatus: StateFlow<String> = _spruceStatus.asStateFlow()

    private val _spruceMapData = MutableStateFlow<BulkRealtimeResponseV2?>(null)
    val spruceMapData: StateFlow<BulkRealtimeResponseV2?> = _spruceMapData.asStateFlow()



    private val _spruceError = MutableStateFlow<String?>(null)
    val spruceError: StateFlow<String?> = _spruceError.asStateFlow()

    private val _spruceTrajectoryData = MutableStateFlow<SpruceCommonMessage?>(null)
    val spruceTrajectoryData: StateFlow<SpruceCommonMessage?> = _spruceTrajectoryData.asStateFlow()

    private var activeMapParams: MapViewportUpdate? = null
    // Keep track of active trip subscription if we implement trip stuff later

    private var activeTrajectoryParams: SubscribeTrajectories? = null

    private var pingJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    fun init() {
        ensureConnection()
    }

    private fun ensureConnection() {
        if (webSocket != null &&
                        (_spruceStatus.value == "connected" || _spruceStatus.value == "connecting")
        ) {
            return
        }

        _spruceStatus.value = "connecting"
        val request = Request.Builder().url("wss://spruce.catenarymaps.org/ws/").build()

        webSocket =
                client.newWebSocket(
                        request,
                        object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                Log.d(TAG, "Spruce WS Connected")
                                _spruceStatus.value = "connected"
                                startPingLoop(webSocket)

                                // Resend active map subscription
                                activeMapParams?.let {
                                    Log.d(TAG, "Resending active map params")
                                    sendMapUpdate(it)
                                }



                                activeTrajectoryParams?.let {
                                    Log.d(TAG, "Resending active trajectory params")
                                    sendTrajectorySubscription(it)
                                }
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                scope.launch {
                                    try {
                                        val msg = json.decodeFromString<SpruceCommonMessage>(text)
                                        when (msg.type) {

                                            "buffer" -> {
                                                //Log.d(TAG, "Spruce WS Trajectory buffer received, text size: ${text.length}")
                                                _spruceTrajectoryData.value = msg
                                            }
                                            "map_update" -> {
                                                // Handle various potential payload locations
                                                if (msg.map_update != null) {
                                                    Log.d(TAG, "Spruce WS Map update received via map_update field, chateaus count: ${msg.map_update.chateaus.size}")
                                                    _spruceMapData.value = msg.map_update
                                                } else if (msg.chateaus != null) {
                                                    Log.d(TAG, "Spruce WS Map update received via chateaus field, chateaus count: ${msg.chateaus.size}")
                                                    _spruceMapData.value =
                                                            BulkRealtimeResponseV2(
                                                                    chateaus = msg.chateaus
                                                            )
                                                }
                                            }
                                            "pong" -> {
                                                 Log.d(TAG, "Spruce WS received pong")
                                            }
                                            "error" -> {
                                                _spruceError.value = msg.message
                                                Log.e(TAG, "Spruce WS Error: ${msg.message}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing Spruce WS message", e)
                                    }
                                }
                            }

                            override fun onClosing(
                                    webSocket: WebSocket,
                                    code: Int,
                                    reason: String
                            ) {
                                Log.d(TAG, "Spruce WS Closing: $code / $reason")
                                _spruceStatus.value = "disconnected"
                                this@SpruceWebSocket.webSocket = null
                                pingJob?.cancel()
                                pingJob = null
                            }

                            override fun onFailure(
                                    webSocket: WebSocket,
                                    t: Throwable,
                                    response: Response?
                            ) {
                                Log.e(TAG, "Spruce WS Failure", t)
                                _spruceStatus.value = "error"
                                this@SpruceWebSocket.webSocket = null
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

    fun updateMap(categories: List<String>, boundsInput: BoundsInput) {
        ensureConnection()
        val params =
                MapViewportUpdate(
                        categories = categories,
                        bounds_input = boundsInput
                )
        activeMapParams = params
        sendMapUpdate(params)
    }

    fun subscribeTrajectories(bbox: List<Double>, zoom: Int, modes: List<String>) {
        ensureConnection()
        val params = SubscribeTrajectories(bbox = bbox, zoom = zoom, modes = modes)
        activeTrajectoryParams = params
        sendTrajectorySubscription(params)
    }

    fun unsubscribeTrajectories() {
        activeTrajectoryParams = null
        if (_spruceStatus.value == "connected") {
            try {
                val msg = UnsubscribeTrajectories()
                val text = json.encodeToString(msg)
                webSocket?.send(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending unsubscribe trajectories", e)
            }
        }
    }



    private fun sendMapUpdate(params: MapViewportUpdate) {
        if (_spruceStatus.value == "connected") {
            try {
                val text = json.encodeToString(params)
                webSocket?.send(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending map update", e)
            }
        }
    }



    private fun sendTrajectorySubscription(params: SubscribeTrajectories) {
        if (_spruceStatus.value == "connected") {
            try {
                val text = json.encodeToString(params)
                webSocket?.send(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending trajectory subscription", e)
            }
        }
    }

    private fun startPingLoop(ws: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (_spruceStatus.value == "connected") {
                delay(10000)
                try {
                    ws.send("{\"type\":\"ping\"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending ping", e)
                }
            }
        }
    }
}
