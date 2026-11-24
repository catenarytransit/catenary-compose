package com.catenarymaps.catenary

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant
import java.util.concurrent.TimeUnit

internal enum class ConnectionDirection { BEFORE, AFTER }

/** Wire envelope shared with catenary-web and Edelweiss /v2/route/ws. */
@Serializable
internal data class DirectionsStreamMessage(
    val type: String, val requestId: String? = null, val sequence: Long? = null,
    val response: DirectionsPlan? = null, val message: String? = null
)

internal fun directionsPageQuery(
    base: JsonObject,
    direction: ConnectionDirection,
    cursor: String? = null
) =
    buildJsonObject {
        base.forEach { (key, value) -> put(key, value) }
        cursor?.let { put("page_cursor", it) }
        put(
            "number_of_results_before",
            if (direction == ConnectionDirection.BEFORE) (if (cursor == null) 8 else 3) else 0
        )
        put(
            "number_of_results_after",
            if (direction == ConnectionDirection.AFTER) (if (cursor == null) 8 else 5) else 0
        )
    }

internal fun DirectionsItinerary.identity(): String = id?.takeIf { it.isNotEmpty() }
    ?: buildJsonArray {
        add(startTime); add(endTime)
        add(buildJsonArray {
            legs.forEach { leg ->
                add(buildJsonArray {
                    add(leg.mode); add(leg.startTime); add(leg.endTime); add(leg.routeId)
                    add(leg.from?.name); add(leg.to?.name)
                })
            }
        })
    }.toString()

internal fun mergeDirectionsPages(pages: List<DirectionsPlan>): List<DirectionsItinerary> {
    fun valid(itinerary: DirectionsItinerary) = runCatching {
        Instant.parse(itinerary.startTime); Instant.parse(itinerary.endTime)
    }.isSuccess

    val seen = mutableSetOf<String>()
    val transit = pages.flatMap { it.itineraries }.filter { valid(it) && seen.add(it.identity()) }
        .sortedWith(compareBy({ Instant.parse(it.startTime) }, { Instant.parse(it.endTime) }))
    val direct = pages.flatMap { it.direct }.filter { valid(it) && seen.add(it.identity()) }
    return (transit + direct).sortedWith(
        compareBy(
            { Instant.parse(it.startTime) },
            { Instant.parse(it.endTime) })
    )
}

private class BackendRouteException(message: String) : Exception(message)

/** Ignore unrelated/stale frames; complete always carries the authoritative replacement. */
internal class DirectionsStreamState(private val requestId: String) {
    private var sequence = 0L
    fun accept(message: DirectionsStreamMessage, onPage: (DirectionsPlan) -> Unit): Boolean {
        if (message.requestId != requestId) return false
        when (message.type) {
            "snapshot" -> {
                val next = message.sequence ?: return false
                if (next <= sequence) return false
                sequence = next
                onPage(requireNotNull(message.response))
            }

            "complete" -> {
                onPage(requireNotNull(message.response)); return true
            }

            "error" -> throw BackendRouteException(message.message ?: "Routing failed")
            "cancelled" -> throw BackendRouteException("Routing cancelled")
        }
        return false
    }
}

/** One WebSocket per visible planner, multiplexed by requestId. */
internal class DirectionsSocketSession {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
    private val lock = Any()
    private val pending = mutableMapOf<String, Channel<String>>()
    private var socket: WebSocket? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var owners = 0

    fun acquire() {
        synchronized(lock) {
            owners++
            connectLocked()
        }
    }

    fun release() {
        synchronized(lock) {
            if (owners == 0) return
            owners--
            if (owners == 0) closeLocked()
        }
    }

    fun close() = synchronized(lock) {
        owners = 0
        closeLocked()
    }

    private fun closeLocked() {
        val old = socket
        socket = null
        ready?.cancel()
        ready = null
        pending.values.forEach { it.close() }
        pending.clear()
        old?.close(1000, "Planner closed")
    }

    private fun connectLocked() {
        if (socket != null) return
        val signal = CompletableDeferred<Unit>()
        ready = signal
        socket = client.newWebSocket(
            Request.Builder().url("wss://edelweiss.catenarymaps.org/v2/route/ws").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(lock) {
                        if (socket === webSocket) signal.complete(Unit)
                        else webSocket.close(1000, "Stale connection")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val requestId = runCatching {
                        json.parseToJsonElement(text).jsonObject["requestId"]?.jsonPrimitive?.content
                    }.getOrNull() ?: return
                    synchronized(lock) {
                        if (socket === webSocket) pending[requestId]?.trySend(text)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    fail(webSocket, t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    fail(
                        webSocket,
                        IllegalStateException("Routing WebSocket closed: $code $reason")
                    )
                }
            }
        )
    }

    private fun fail(webSocket: WebSocket, cause: Throwable) {
        synchronized(lock) {
            if (socket !== webSocket) return
            socket = null
            ready?.completeExceptionally(cause)
            ready = null
            pending.values.forEach { it.close(cause) }
            pending.clear()
        }
    }

    suspend fun route(query: JsonObject, requestId: String, onPage: (DirectionsPlan) -> Unit) {
        val messages = Channel<String>(Channel.UNLIMITED)
        val connection: WebSocket
        val opened: CompletableDeferred<Unit>
        synchronized(lock) {
            connectLocked()
            check(pending.put(requestId, messages) == null) { "Duplicate routing requestId" }
            connection = checkNotNull(socket)
            opened = checkNotNull(ready)
        }
        try {
            withTimeout(10_000) { opened.await() }
            check(connection.send(buildJsonObject {
                put("type", "route"); put("requestId", requestId); put("query", query)
            }.toString())) { "Unable to send Edelweiss route request" }
            val stream = DirectionsStreamState(requestId)
            val completed = withTimeoutOrNull(45_000) {
                for (text in messages) {
                    val message = json.decodeFromString<DirectionsStreamMessage>(text)
                    if (stream.accept(message, onPage)) return@withTimeoutOrNull true
                }
                error("Routing WebSocket closed before completion")
            }
            check(completed == true) { "Routing WebSocket timed out" }
        } finally {
            synchronized(lock) { pending.remove(requestId, messages) }
            messages.cancel()
        }
    }
}

/** Try the retained WebSocket first, preserving the existing HTTP fallback. */
internal object DirectionsStreaming {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun load(
        client: HttpClient, session: DirectionsSocketSession, query: JsonObject,
        requestId: String, onPage: (DirectionsPlan) -> Unit
    ) {
        val identified = buildJsonObject {
            query.forEach { (key, value) -> put(key, value) }; put("request_id", requestId)
        }
        try {
            session.route(identified, requestId, onPage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendRouteException) {
            throw e
        } catch (_: Exception) {
            val response = client.post("https://edelweiss.catenarymaps.org/v2/route") {
                contentType(ContentType.Application.Json); setBody(identified.toString())
            }
            check(response.status.isSuccess()) { "Edelweiss HTTP ${response.status.value}" }
            onPage(json.decodeFromString<DirectionsPlan>(response.bodyAsText()))
        }
    }
}
