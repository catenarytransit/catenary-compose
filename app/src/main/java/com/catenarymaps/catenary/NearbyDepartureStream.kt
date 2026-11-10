package com.catenarymaps.catenary

import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Serializable
internal data class NearbyDepartureMessage(
    val type: String,
    @SerialName("request_id") val requestId: String? = null,
    val phase: String? = null,
    val complete: Boolean = false,
    val data: NearbyResponseV3? = null,
    val message: String? = null
)

private fun LocalDepartureItem.identity() =
    listOf(tripId, serviceDate, stopId, departureSchedule, arrivalSchedule)

private fun DepartureItemV3.identity() =
    listOf(chateauId, routeId, tripId, serviceDate, stopId, scheduledDeparture, scheduledArrival)

private fun StationDepartureGroup.identity(): List<Any?> =
    osmStationId?.let { listOf("osm", it) } ?: listOf("station", stationName, lat, lon)

private fun <T> mergeTables(
    first: Map<String, Map<String, T>>, second: Map<String, Map<String, T>>
): Map<String, Map<String, T>> = (first.keys + second.keys).associateWith { chateau ->
    first[chateau].orEmpty() + second[chateau].orEmpty()
}

/** Merge phases by feed and route, preserving directions, loop visits and lookup tables. */
internal fun mergeNearbyDepartures(
    first: NearbyResponseV3, second: NearbyResponseV3
): NearbyResponseV3 {
    val routes = first.local.associateBy { it.chateauId to it.routeId }.toMutableMap()
    for (route in second.local) {
        val key = route.chateauId to route.routeId
        val previous = routes[key]
        routes[key] = if (previous == null) route else route.copy(
            closestDistance = minOf(previous.closestDistance, route.closestDistance),
            headsigns = (previous.headsigns.keys + route.headsigns.keys).associateWith { headsign ->
                (previous.headsigns[headsign].orEmpty().associateBy { it.identity() } +
                    route.headsigns[headsign].orEmpty().associateBy { it.identity() })
                    .values.sortedBy { it.departureRealtime ?: it.departureSchedule ?: Long.MAX_VALUE }
            }
        )
    }
    val stations = first.longDistance.associateBy { it.identity() }.toMutableMap()
    for (station in second.longDistance) {
        val key = station.identity()
        val previous = stations[key]
        stations[key] = if (previous == null) station else station.copy(
            distanceM = minOf(previous.distanceM, station.distanceM),
            departures = (previous.departures.associateBy { it.identity() } +
                station.departures.associateBy { it.identity() }).values
                .sortedBy { it.realtimeDeparture ?: it.scheduledDeparture ?: Long.MAX_VALUE }
        )
    }
    return NearbyResponseV3(
        longDistance = stations.values.sortedBy { it.distanceM },
        local = routes.values.sortedBy { it.closestDistance },
        routes = mergeTables(first.routes, second.routes),
        stops = mergeTables(first.stops, second.stops),
        debug = second.debug ?: first.debug
    )
}

/** Each refresh has its own accumulator; a full frame replaces previous departures. */
internal class NearbyDepartureStreamState(private val requestId: String) {
    private var priority: NearbyResponseV3? = null
    private var finished = false

    fun accept(message: NearbyDepartureMessage, onSnapshot: (NearbyResponseV3) -> Unit): Boolean {
        if (finished) return false
        // Older Ramonda deployments report unknown request types without an ID.
        if (message.type == "error" && message.requestId == null) {
            throw IOException(message.message ?: "Nearby departures unavailable")
        }
        if (message.requestId != requestId) return false
        if (message.type == "nearby_departures_error") throw IOException(message.message ?: "Nearby departures failed")
        if (message.type != "nearby_departures") return false
        val data = message.data ?: throw IOException("Missing nearby departures payload")
        when (message.phase) {
            "priority" -> {
                if (priority != null) return false
                check(!message.complete) { "Priority frame cannot finish the request" }
                priority = data
                onSnapshot(data)
            }
            "remaining" -> {
                check(message.complete) { "Remaining frame must finish the request" }
                onSnapshot(mergeNearbyDepartures(priority ?: throw IOException("Missing priority frame"), data))
                finished = true
            }
            "full" -> {
                check(message.complete) { "Full frame must finish the request" }
                onSnapshot(data)
                finished = true
            }
            else -> throw IOException("Unknown nearby departures phase")
        }
        return finished
    }
}

private val nearbySocketClient = OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .pingInterval(5, TimeUnit.SECONDS)
    .build()

internal fun nearbyDepartureSnapshots(
    lat: Double, lon: Double, departureTime: Long?, progressive: Boolean
): Flow<NearbyResponseV3> = flow {
    val requestId = UUID.randomUUID().toString()
    val json = Json { ignoreUnknownKeys = true }
    val state = NearbyDepartureStreamState(requestId)
    val frames = callbackFlow<String> {
        // A request-scoped socket keeps nearby refreshes independent of trip subscriptions.
        val socket = nearbySocketClient.newWebSocket(
            Request.Builder().url("wss://ramonda.catenarymaps.org/ws/").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val request = buildJsonObject {
                        put("type", "nearby_departures")
                        put("request_id", requestId)
                        put("lat", lat)
                        put("lon", lon)
                        put("limit_per_station", 10)
                        put("limit_per_headsign", 20)
                        put("progressive", progressive)
                        departureTime?.let { put("departure_time", it) }
                    }
                    if (!webSocket.send(request.toString())) close(IOException("Unable to send nearby request"))
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (trySend(text).isFailure) close(IOException("Nearby response buffer full"))
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close(IOException("Nearby socket closed before completion: $code"))
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                    close(IOException("Nearby socket closed before completion: $code"))
                }
            }
        )
        awaitClose { socket.cancel() }
    }
    // Completion cancels callbackFlow and releases its socket immediately.
    frames.first { text ->
        val snapshots = mutableListOf<NearbyResponseV3>()
        val complete = state.accept(json.decodeFromString<NearbyDepartureMessage>(text), snapshots::add)
        snapshots.forEach { emit(it) }
        complete
    }
}.flowOn(Dispatchers.Default)
