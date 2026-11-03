package com.catenarymaps.catenary

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.time.Instant

class TripPlannerViewModel : ViewModel() {
    var origin by mutableStateOf<DirectionsLocation?>(null)
        private set
    var destination by mutableStateOf<DirectionsLocation?>(null)
        private set
    var originText by mutableStateOf("")
        private set
    var destinationText by mutableStateOf("")
        private set
    // true = origin, false = destination, null = no endpoint has focus.
    var editing by mutableStateOf<Boolean?>(null)
    // Endpoint being selected with the fixed centre pin, separate from text search.
    var mapPicking by mutableStateOf<Boolean?>(null)
    var options by mutableStateOf(DirectionsOptions())
    var arriveBy by mutableStateOf(false)
    var now by mutableStateOf(true)
    var time by mutableLongStateOf(Instant.now().epochSecond)
    var itineraries by mutableStateOf<List<DirectionsItinerary>>(emptyList())
        private set
    var selected by mutableIntStateOf(0)
    var details by mutableStateOf(false)
    var showTransfers by mutableStateOf(false)
    var loading by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var request: Job? = null
    private var generation = 0
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun invalidate() {
        generation++; request?.cancel(); loading = false; searched = false
        error = null; itineraries = emptyList(); selected = 0; details = false; showTransfers = false
    }
    fun edit(isOrigin: Boolean, text: String) {
        invalidate()
        if (isOrigin) { origin = null; originText = text }
        else { destination = null; destinationText = text }
        editing = isOrigin
    }
    fun choose(isOrigin: Boolean, location: DirectionsLocation) {
        if (!location.isValid()) return
        invalidate()
        if (isOrigin) { origin = location; originText = location.name }
        else { destination = location; destinationText = location.name }
        editing = null
        mapPicking = null
    }
    fun swap() {
        invalidate()
        val old = origin; origin = destination; destination = old
        val text = originText; originText = destinationText; destinationText = text
        editing = null
    }
    fun leave() {
        mapPicking = null
        generation++
        request?.cancel()
        if (loading) searched = false
        loading = false
        editing = null
    }

    fun search(client: HttpClient) {
        val from = origin ?: return
        val to = destination ?: return
        invalidate(); searched = true; loading = true; editing = null
        val token = generation
        val payload = directionsRequest(from, to, if (now) Instant.now().epochSecond else time, arriveBy, options)
        request = viewModelScope.launch {
            try {
                val response = client.post("https://edelweiss.catenarymaps.org/v1/route") {
                    contentType(ContentType.Application.Json); setBody(payload.toString())
                }
                val text = response.bodyAsText()
                if (!response.status.isSuccess()) throw IllegalStateException("Edelweiss HTTP ${response.status.value}: ${text.take(1200)}")
                val plan = withContext(Dispatchers.Default) { json.decodeFromString<DirectionsPlan>(text) }
                if (token == generation) {
                    // Preserve the backend's ranking, appending direct alternatives as the web does.
                    itineraries = (plan.itineraries + plan.direct).filter {
                        runCatching { Instant.parse(it.startTime); Instant.parse(it.endTime) }.isSuccess
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) error = e.message ?: "Unable to plan journey"
            } finally {
                if (token == generation) loading = false
            }
        }
    }
}
