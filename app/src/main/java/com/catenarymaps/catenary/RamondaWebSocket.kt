package com.catenarymaps.catenary

import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
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

private const val TAG = "RamondaWebSocket"

/** Full GTFS trip instance identity; reused verbatim as the WebSocket subscription key. */
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
    val trip_id: String,
    val route_id: String? = null,
    val start_date: String? = null,
    val start_time: String? = null
)

@Serializable
data class RamondaTripIdentity(
    val chateau: String,
    val trip_id: String,
    val route_id: String? = null,
    val start_date: String? = null,
    val start_time: String? = null
) {
    fun subscribe() = SubscribeTrip(
        chateau = chateau, trip_id = trip_id, route_id = route_id,
        start_date = start_date, start_time = start_time
    )
}

fun SubscribeTrip.identity() =
    RamondaTripIdentity(chateau, trip_id, route_id, start_date, start_time)

@Serializable
data class RamondaCommonMessage(
    val type: String,
    val subscription: RamondaTripIdentity? = null,
    val data: JsonElement? = null,
    val message: String? = null
)

data class RamondaTripSnapshot(
    val initial: TripDataResponse? = null,
    val latestUpdate: TripRtUpdateData? = null,
    val lastError: String? = null
)

/**
 * Counts viewers, not sockets. Pure registry permits deterministic regression
 * tests for overlapping screens and independent unsubscribe operations.
 */
internal class RamondaSubscriptionRegistry {
    private val counts = linkedMapOf<RamondaTripIdentity, Int>()
    fun acquire(identity: RamondaTripIdentity): Boolean {
        val count = counts[identity] ?: 0
        counts[identity] = count + 1
        return count == 0
    }

    fun release(identity: RamondaTripIdentity): Boolean {
        val count = counts[identity] ?: return false
        if (count > 1) {
            counts[identity] = count - 1
            return false
        }
        counts.remove(identity)
        return true
    }

    fun contains(identity: RamondaTripIdentity) = counts.containsKey(identity)
    fun active(): Set<RamondaTripIdentity> = counts.keys.toSet()
    fun isNotEmpty() = counts.isNotEmpty()
}

/**
 * One socket, independently tracked subscriptions, no global "current trip".
 * Every frame is routed by its server-supplied complete subscription identity.
 *
 * The lease count is per screen/viewmodel. A journey and SingleTripScreen can
 * subscribe to the same run without unsubscribing each other. Only the final
 * release sends unsubscribe_trip. All mutations run on a single dispatcher.
 */
object RamondaWebSocket {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .minWebSocketMessageToCompress(0)
        .build()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null
    private var socketGeneration = 0L
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private val subscriptions = RamondaSubscriptionRegistry()
    private val _tripSnapshots =
        MutableStateFlow<Map<RamondaTripIdentity, RamondaTripSnapshot>>(emptyMap())
    val tripSnapshots: StateFlow<Map<RamondaTripIdentity, RamondaTripSnapshot>> =
        _tripSnapshots.asStateFlow()
    private val _ramondaStatus = MutableStateFlow("disconnected")
    val ramondaStatus: StateFlow<String> = _ramondaStatus.asStateFlow()
    private val _ramondaError = MutableStateFlow<String?>(null)
    val ramondaError: StateFlow<String?> = _ramondaError.asStateFlow()

    fun init() {
        scope.launch { ensureConnection() }
    }

    fun subscribeTrip(
        chateau: String, tripId: String, routeId: String? = null,
        startDate: String? = null, startTime: String? = null
    ) = subscribe(RamondaTripIdentity(chateau, tripId, routeId, startDate, startTime))

    fun subscribe(identity: RamondaTripIdentity) {
        require(identity.chateau.isNotBlank() && identity.trip_id.isNotBlank())
        scope.launch {
            if (subscriptions.acquire(identity)) {
                ensureConnection()
                if (_ramondaStatus.value == "connected") sendSubscribe(identity)
            }
        }
    }

    fun unsubscribe(identity: RamondaTripIdentity) {
        scope.launch {
            if (subscriptions.release(identity)) {
                _tripSnapshots.value = _tripSnapshots.value - identity
                if (_ramondaStatus.value == "connected") sendUnsubscribe(identity)
            }
        }
    }

    fun unsubscribeTrip(
        chateau: String, tripId: String, routeId: String? = null,
        startDate: String? = null, startTime: String? = null
    ) = unsubscribe(RamondaTripIdentity(chateau, tripId, routeId, startDate, startTime))

    private fun sendSubscribe(identity: RamondaTripIdentity) {
        webSocket?.send(json.encodeToString(identity.subscribe()))
    }

    private fun sendUnsubscribe(identity: RamondaTripIdentity) {
        webSocket?.send(
            json.encodeToString(
                UnsubscribeTrip(
                    chateau = identity.chateau, trip_id = identity.trip_id,
                    route_id = identity.route_id, start_date = identity.start_date,
                    start_time = identity.start_time
                )
            )
        )
    }

    private fun ensureConnection() {
        if (webSocket != null) return
        _ramondaStatus.value = "connecting"
        val generation = ++socketGeneration
        webSocket = client.newWebSocket(
            Request.Builder().url("wss://ramonda.catenarymaps.org/ws/").build(),
            object : WebSocketListener() {
                override fun onOpen(socket: WebSocket, response: Response) {
                    scope.launch {
                        if (generation != socketGeneration) return@launch
                        _ramondaStatus.value = "connected"
                        subscriptions.active().forEach(::sendSubscribe)
                        pingJob?.cancel()
                        pingJob = scope.launch {
                            while (_ramondaStatus.value == "connected") {
                                delay(10_000)
                                socket.send("{\"type\":\"ping\"}")
                            }
                        }
                    }
                }

                override fun onMessage(socket: WebSocket, text: String) {
                    scope.launch {
                        if (generation != socketGeneration) return@launch
                        try {
                            val frame = json.decodeFromString<RamondaCommonMessage>(text)
                            acceptFrame(frame)
                        } catch (e: Exception) {
                            Log.e(TAG, "Malformed Ramonda frame", e)
                        }
                    }
                }

                override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch { disconnect(generation, "error") }
                }

                override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                    scope.launch { disconnect(generation, "disconnected") }
                }

                override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                    socket.close(code, reason)
                }
            }
        )
    }

    private fun disconnect(generation: Long, status: String) {
        if (generation != socketGeneration) return
        webSocket = null
        pingJob?.cancel()
        pingJob = null
        _ramondaStatus.value = status
        reconnectJob?.cancel()
        if (subscriptions.isNotEmpty()) reconnectJob = scope.launch {
            delay(5_000)
            ensureConnection()
        }
    }

    /** Missing identities are deliberately rejected: guessing is unsafe for multi-trip views. */
    private fun acceptFrame(frame: RamondaCommonMessage) {
        when (frame.type) {
            "initial_trip", "update_trip" -> {
                val identity = frame.subscription ?: return
                if (!subscriptions.contains(identity)) return
                val body = frame.data ?: return
                val existing = _tripSnapshots.value[identity] ?: RamondaTripSnapshot()
                val replacement = when (frame.type) {
                    "initial_trip" -> {
                        val initial =
                            json.decodeFromJsonElement(TripDataResponse.serializer(), body)
                        if ((initial.trip_id != null && initial.trip_id != identity.trip_id) ||
                            (initial.chateau != null && initial.chateau != identity.chateau)
                        ) return
                        existing.copy(
                            initial = initial,
                            // Keep a response that preceded the first initial_trip,
                            // but discard stale RT state when reconnection rehydrates.
                            latestUpdate = if (existing.initial == null) existing.latestUpdate else null,
                            lastError = null
                        )
                    }

                    else -> {
                        val update = json.decodeFromJsonElement(TripRtUpdateData.serializer(), body)
                        if (update.trip_id != null && update.trip_id != identity.trip_id) return
                        if (update.chateau != null && update.chateau != identity.chateau) return
                        existing.copy(latestUpdate = update, lastError = null)
                    }
                }
                _tripSnapshots.value = _tripSnapshots.value + (identity to replacement)
            }

            "error" -> {
                val identity = frame.subscription
                if (identity != null && subscriptions.contains(identity)) {
                    val old = _tripSnapshots.value[identity] ?: RamondaTripSnapshot()
                    _tripSnapshots.value = _tripSnapshots.value +
                            (identity to old.copy(lastError = frame.message))
                } else if (identity == null) {
                    _ramondaError.value = frame.message
                }
            }
        }
    }
}
