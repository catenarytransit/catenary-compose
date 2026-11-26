package com.catenarymaps.catenary

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test

class RamondaConnectionTest {
    private class FakeSocket(
        private val originalRequest: Request,
        private val listener: WebSocketListener
    ) : WebSocket {
        val sent = mutableListOf<String>()
        var cancelled = false
        override fun request() = originalRequest
        override fun queueSize() = 0L
        override fun send(text: String): Boolean {
            sent.add(text)
            return !cancelled
        }
        override fun send(bytes: ByteString) = !cancelled
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled = true }

        fun open() = listener.onOpen(
            this,
            Response.Builder().request(originalRequest).protocol(Protocol.HTTP_1_1)
                .code(101).message("Switching Protocols").build()
        )

        fun full(id: String) = listener.onMessage(
            this,
            """{"type":"nearby_departures","request_id":"$id","phase":"full","complete":true,"data":{}}"""
        )

        fun fail() = listener.onFailure(this, IOException("disconnected"), null)
    }

    private class FakeFactory : WebSocket.Factory {
        val sockets = mutableListOf<FakeSocket>()
        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
            FakeSocket(request, listener).also(sockets::add)
    }

    private fun payload(id: String) = """{"type":"nearby_departures","request_id":"$id","lat":1,"lon":2}"""

    @Test
    fun consecutiveQueriesReusePrewarmedSocketAlongsideTripSubscriptions() = runBlocking {
        val factory = FakeFactory()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val connection = RamondaConnection(factory, scope)
        try {
            connection.init()
            val socket = factory.sockets.single()
            val first = Channel<NearbyDepartureMessage>(Channel.BUFFERED)
            connection.requestNearby("first", payload("first"), first).join()
            assertTrue(socket.sent.isEmpty())
            socket.open()
            assertEquals(listOf(payload("first")), socket.sent)
            val trip = RamondaTripIdentity("feed", "trip")
            connection.subscribe(trip)
            socket.full("first")
            assertEquals("first", first.tryReceive().getOrThrow().requestId)
            assertTrue(first.tryReceive().isClosed)
            connection.cancelNearby("first").join()

            val second = Channel<NearbyDepartureMessage>(Channel.BUFFERED)
            connection.requestNearby("second", payload("second"), second).join()
            assertEquals(1, factory.sockets.size)
            assertEquals(payload("second"), socket.sent.last())
            socket.full("second")
            assertEquals("second", second.tryReceive().getOrThrow().requestId)
            assertTrue(socket.sent.any { it.contains("subscribe_trip") })
            assertFalse(socket.cancelled)
            connection.unsubscribe(trip)
            assertFalse(socket.cancelled)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun staleFramesAndOldCollectorCancellationCannotAffectReplacement() = runBlocking {
        val factory = FakeFactory()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val connection = RamondaConnection(factory, scope)
        try {
            connection.init()
            val socket = factory.sockets.single()
            socket.open()
            val first = Channel<NearbyDepartureMessage>(Channel.BUFFERED)
            val second = Channel<NearbyDepartureMessage>(Channel.BUFFERED)
            connection.requestNearby("first", payload("first"), first).join()
            connection.requestNearby("second", payload("second"), second).join()
            assertTrue(first.tryReceive().exceptionOrNull() is CancellationException)
            connection.cancelNearby("first").join()
            assertEquals(payload("second"), socket.sent.last())
            socket.full("first")
            assertTrue(second.tryReceive().isFailure)
            connection.cancelNearby("second").join()
            assertEquals("""{"type":"cancel_nearby_departures"}""", socket.sent.last())
            assertFalse(socket.cancelled)

            val third = Channel<NearbyDepartureMessage>(Channel.BUFFERED)
            connection.requestNearby("third", payload("third"), third).join()
            socket.full("second")
            assertTrue(third.tryReceive().isFailure)
            socket.full("third")
            assertEquals("third", third.tryReceive().getOrThrow().requestId)
            assertEquals(1, factory.sockets.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun connectionFailureReleasesCurrentQueryAndNextQueryReconnectsImmediately() = runBlocking {
        val factory = FakeFactory()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val connection = RamondaConnection(factory, scope)
        try {
            connection.init()
            val old = factory.sockets.single()
            old.open()
            val first = Channel<NearbyDepartureMessage>(Channel.BUFFERED)
            connection.requestNearby("first", payload("first"), first).join()
            old.fail()
            assertTrue(first.tryReceive().exceptionOrNull() is IOException)
            val next = Channel<NearbyDepartureMessage>(Channel.BUFFERED)
            connection.requestNearby("next", payload("next"), next).join()
            assertEquals(2, factory.sockets.size)
            val replacement = factory.sockets.last()
            replacement.open()
            old.full("next")
            assertTrue(next.tryReceive().isFailure)
            replacement.full("next")
            assertEquals("next", next.tryReceive().getOrThrow().requestId)
            connection.stopIdleConnection()
            assertTrue(replacement.cancelled)
        } finally {
            scope.cancel()
        }
    }
}
