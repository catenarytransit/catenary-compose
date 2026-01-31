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

private const val TAG = "SpruceWebSocket"

@Serializable
data class MapViewportUpdate(
        @EncodeDefault val type: String = "update_map",
        val chateaus: List<String>,
        val categories: List<String>,
        val bounds_input: BoundsInput
)

@Serializable
data class SubscribeTrip(
        @EncodeDefault val type: String = "subscribe_trip",
        val chateau: String,
        val trip_id: String? = null,
        val route_id: String? = null,
// Add other fields as needed for QueryTripInformationParams
)

@Serializable
data class UnsubscribeTrip(
        @EncodeDefault val type: String = "unsubscribe_trip",
        val chateau: String
)

@Serializable
data class SpruceCommonMessage(
        val type: String,
        val data: JsonElement? = null, // For initial_trip and update_trip
        val chateaus: Map<String, EachChateauResponseV2>? =
                null, // For map_update (top level in legacy/TS sometimes?)
        val map_update: BulkRealtimeResponseV2? = null, // In case it's wrapped
        val message: String? = null // For error
)

object SpruceWebSocket {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // State Flows
    private val _spruceStatus = MutableStateFlow("disconnected")
    val spruceStatus: StateFlow<String> = _spruceStatus.asStateFlow()

    private val _spruceMapData = MutableStateFlow<BulkRealtimeResponseV2?>(null)
    val spruceMapData: StateFlow<BulkRealtimeResponseV2?> = _spruceMapData.asStateFlow()

    private val _spruceTripData = MutableStateFlow<JsonElement?>(null)
    val spruceTripData: StateFlow<JsonElement?> = _spruceTripData.asStateFlow()

    private val _spruceUpdateData = MutableStateFlow<JsonElement?>(null)
    val spruceUpdateData: StateFlow<JsonElement?> = _spruceUpdateData.asStateFlow()

    private val _spruceError = MutableStateFlow<String?>(null)
    val spruceError: StateFlow<String?> = _spruceError.asStateFlow()

    private var activeMapParams: MapViewportUpdate? = null
    // Keep track of active trip subscription if we implement trip stuff later
    // private var activeTripParams: SubscribeTrip? = null

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

                                // Resend active map subscription
                                activeMapParams?.let {
                                    Log.d(TAG, "Resending active map params")
                                    sendMapUpdate(it)
                                }
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                try {
                                    val msg = json.decodeFromString<SpruceCommonMessage>(text)
                                    when (msg.type) {
                                        "initial_trip" -> _spruceTripData.value = msg.data
                                        "update_trip" -> _spruceUpdateData.value = msg.data
                                        "map_update" -> {
                                            // Handle various potential payload locations
                                            if (msg.map_update != null) {
                                                _spruceMapData.value = msg.map_update
                                            } else if (msg.chateaus != null) {
                                                _spruceMapData.value =
                                                        BulkRealtimeResponseV2(
                                                                chateaus = msg.chateaus
                                                        )
                                            }
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

                            override fun onClosing(
                                    webSocket: WebSocket,
                                    code: Int,
                                    reason: String
                            ) {
                                Log.d(TAG, "Spruce WS Closing: $code / $reason")
                                _spruceStatus.value = "disconnected"
                                this@SpruceWebSocket.webSocket = null
                            }

                            override fun onFailure(
                                    webSocket: WebSocket,
                                    t: Throwable,
                                    response: Response?
                            ) {
                                Log.e(TAG, "Spruce WS Failure", t)
                                _spruceStatus.value = "error"
                                this@SpruceWebSocket.webSocket = null

                                // Retry connection after delay
                                scope.launch {
                                    delay(5000)
                                    ensureConnection()
                                }
                            }
                        }
                )
    }

    fun updateMap(categories: List<String>, chateaus: List<String>, boundsInput: BoundsInput) {
        ensureConnection()
        val params =
                MapViewportUpdate(
                        chateaus = chateaus,
                        categories = categories,
                        bounds_input = boundsInput
                )
        activeMapParams = params
        sendMapUpdate(params)
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
}
