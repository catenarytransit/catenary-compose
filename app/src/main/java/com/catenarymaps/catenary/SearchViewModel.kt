// SearchViewModel.kt
package com.catenarymaps.catenary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dellisd.spatialk.geojson.Position
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Helper function to calculate distance
fun haversineDistance(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val r = 6371000 // Earth radius in metres
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)

    val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) *
            sin(deltaLambda / 2) * sin(deltaLambda / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return r * c // distance in metres
}

class SearchViewModel : ViewModel() {

    private val _nominatimResults = MutableStateFlow<List<NominatimResult>>(emptyList())
    val nominatimResults = _nominatimResults.asStateFlow()

    private val _catenaryResults = MutableStateFlow<CatenarySearchResponse?>(null)
    val catenaryResults = _catenaryResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var searchJob: Job? = null

    // Setup the JSON parser for Ktor
    private val json = Json { ignoreUnknownKeys = true }

    // Create a reusable Ktor client
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json) // Use kotlinx.serialization
        }
    }

    fun onSearchQueryChanged(
        query: String,
        userLocation: Pair<Double, Double>?,
        mapCenter: Position
    ) {
        searchJob?.cancel() // Cancel previous job
        if (query.isBlank()) {
            _isLoading.value = false
            _nominatimResults.value = emptyList()
            _catenaryResults.value = null
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            delay(300) // Debounce for 300ms

            try {
                // --- Catenary Search API (Ktor) ---
                val catenaryJob = launch {
                    try {
                        var catenaryUrl =
                            "https://birch.catenarymaps.org/text_search_v1?text=$query&map_lat=${mapCenter.latitude}&map_lon=${mapCenter.longitude}&map_z=10"
                        if (userLocation != null) {
                            catenaryUrl += "&user_lat=${userLocation.first}&user_lon=${userLocation.second}"
                        }

                        val response = client.get(catenaryUrl).body<CatenarySearchResponse>()
                        _catenaryResults.value = response
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _catenaryResults.value = null // Clear on error
                    }
                }

                // --- Nominatim API (Ktor) ---
                val nominatimJob = launch {
                    try {
                        val nominatimUrl =
                            "https://nominatim1.catenarymaps.org/search?dedupe=1&q=$query&format=json"

                        // Use Ktor's get with a custom User-Agent header
                        val response = client.get(nominatimUrl) {
                            headers {
                                append("User-Agent", "CatenaryMapsAndroidApp/1.0")
                            }
                        }.body<List<NominatimResult>>()

                        println("Nominatim results: $response")

                        _nominatimResults.value = response.filter {
                            // Filter out stops, as Catenary search handles them
                            it.addressType != "railway" //&& it.category != "bus_stop"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _nominatimResults.value = emptyList() // Clear on error
                    }
                }

                // Wait for both network requests to complete
                catenaryJob.join()
                nominatimJob.join()

            } finally {
                // This now correctly runs *after* both jobs are finished
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close() // Close the client when the ViewModel is destroyed
    }
}