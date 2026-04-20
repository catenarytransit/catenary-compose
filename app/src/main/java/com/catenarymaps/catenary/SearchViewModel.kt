// SearchViewModel.kt
package com.catenarymaps.catenary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import android.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.maplibre.spatialk.geojson.Position

// Helper function to calculate distance
fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000 // Earth radius in metres
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)

    val a =
            sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                    cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return r * c // distance in metres
}

// Weight knobs for combining different sources into one ranked list.
private const val CYPRESS_WEIGHT: Double = 2.0
private const val ROUTE_WEIGHT: Double = 1.0
private const val STOP_WEIGHT: Double = 1.0
private const val OSM_STATION_WEIGHT: Double = 2.0

// Internal row model that lets us render different result types in one list.
sealed class SearchRow(val weightedScore: Double) {
    class CypressRow(val item: CypressFeature, score: Double) : SearchRow(score)

    class OsmStationRow(val result: OsmStationSearchResult, score: Double) : SearchRow(score)

    class RouteRow(
        val ranking: RouteRanking,
        val routeInfo: RouteInfo,
        val agency: Agency?,
        score: Double
    ) : SearchRow(score)

    class StopRow(
        val ranking: StopRanking,
        val stopInfo: StopInfo,
        val routes: List<RouteInfo>,
        val agencyNames: List<String>,
        val distanceMetres: Double?,
        score: Double
    ) : SearchRow(score)
}

// Helper to convert a 1-based rank position into a 0..1 score (1.0 is best)
private fun rankToUnitScore(index: Int, total: Int): Double {
    if (total <= 0) return 0.0
    // Top item => 1.0; last => 1/total.
    return ((total - index).toDouble() / total).coerceIn(0.0, 1.0)
}

class SearchViewModel : ViewModel() {

    private val _searchRows = MutableStateFlow<List<SearchRow>>(emptyList())
    val searchRows = _searchRows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var searchJob: Job? = null

    // Setup the JSON parser for Ktor
    private val json = Json { ignoreUnknownKeys = true }

    // Create a reusable Ktor client
    private val client =
            HttpClient(Android) {
                install(ContentNegotiation) {
                    json(json) // Use kotlinx.serialization
                }
            }

    fun onSearchQueryChanged(
            query: String,
            userLocation: Pair<Double, Double>?,
            mapCenter: Position,
    ) {
        searchJob?.cancel() // Cancel previous job
        if (query.isBlank()) {
            _isLoading.value = false
            _searchRows.value = emptyList()
            return
        }

        searchJob =
                viewModelScope.launch(Dispatchers.IO) {
                    _isLoading.value = true
                    delay(200) // Debounce for 200ms

                    try {
                        coroutineScope {
                            data class PartialResults(
                                var catenary: CatenarySearchResponse? = null,
                                var cypress: List<CypressFeature> = emptyList(),
                                var osmStations: List<OsmStationSearchResult> = emptyList(),
                            )

                            val partialResults = PartialResults()

                            fun recomputeRows() {
                                _searchRows.value =
                                    buildCombinedRows(
                                        partialResults.catenary,
                                        partialResults.cypress,
                                        partialResults.osmStations,
                                        userLocation,
                                    )
                            }

                            // --- Catenary Search API (Ktor) ---
                            launch {
                                val response =
                                    try {
                                        var catenaryUrl =
                                            "https://birch.catenarymaps.org/text_search_v1?text=$query&map_lat=${mapCenter.latitude}&map_lon=${mapCenter.longitude}&map_z=10"
                                        if (userLocation != null) {
                                            catenaryUrl +=
                                                "&user_lat=${userLocation.first}&user_lon=${userLocation.second}"
                                        }

                                        client.get(catenaryUrl).body<CatenarySearchResponse>()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        null
                                    }

                                partialResults.catenary = response
                                recomputeRows()
                            }

                            // --- Cypress v2 Search API (Ktor) ---
                            launch {
                                val features =
                                    try {
                                        val baseUrl = "https://cypress.catenarymaps.org/v2/search"
                                        val params = mutableListOf(
                                            "text=${query}"
                                        )
                                        // Add focus point if available
                                        if (mapCenter != null) {
                                            params += "focus.point.lat=${mapCenter.latitude}"
                                            params += "focus.point.lon=${mapCenter.longitude}"
                                            params += "focus.point.weight=4"
                                        }
                                        val cypressUrl = baseUrl + "?" + params.joinToString("&")

                                        val response = client.get(cypressUrl).body<CypressResponse>()
                                        response.features
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        emptyList()
                                    }

                                partialResults.cypress = features
                                recomputeRows()
                            }

                            // --- OSM Station Search API (Ktor) ---
                            launch {
                                val stations =
                                    try {
                                        val httpResponse =
                                            client.get(
                                                "https://birch.catenarymaps.org/osm_station_search"
                                            ) {
                                                url {
                                                    parameters.append("text", query)

                                                    // Mirror Svelte behavior: only apply focus when
                                                    // we have a user location, and focus around the
                                                    // current map center.
                                                    if (userLocation != null) {

                                                    }
                                                }
                                            }

                                        val bodyText = httpResponse.bodyAsText()
                                        Log.d(
                                            "SearchViewModel",
                                            "OSM station search raw response: $bodyText"
                                        )

                                        try {
                                            val parsed =
                                                json.decodeFromString<
                                                    OsmStationSearchResponse
                                                >(bodyText)
                                            parsed.results
                                        } catch (e: Exception) {
                                            Log.e(
                                                "SearchViewModel",
                                                "Failed to deserialize OSM station search response",
                                                e
                                            )
                                            Log.e(
                                                "SearchViewModel",
                                                "Offending OSM body: $bodyText"
                                            )
                                            emptyList()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            "SearchViewModel",
                                            "OSM station search request failed",
                                            e
                                        )
                                        emptyList()
                                    }

                                partialResults.osmStations = stations
                                recomputeRows()
                            }
                        }
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

private fun buildCombinedRows(
    catenaryResults: CatenarySearchResponse?,
    cypressResults: List<CypressFeature>,
    osmStationResults: List<OsmStationSearchResult>,
    userLocation: Pair<Double, Double>?,
): List<SearchRow> {
    val rows = mutableListOf<SearchRow>()

    // OSM Stations (limit to 10)
    if (osmStationResults.isNotEmpty()) {
        val total = osmStationResults.size.coerceAtLeast(1)
        osmStationResults.take(10).forEachIndexed { index, station ->
            val base = rankToUnitScore(index, total)
            val score = base * OSM_STATION_WEIGHT
            rows += SearchRow.OsmStationRow(station, score)
        }
    }

    // Cypress (limit to 10)
    if (cypressResults.isNotEmpty()) {
        cypressResults.take(10).forEach { item ->
            val conf = item.properties.confidence ?: 0.0
            val base = (conf / 1000.0).coerceIn(0.0, 1.0)
            val score = base * CYPRESS_WEIGHT
            rows += SearchRow.CypressRow(item, score)
        }
    }

    // Routes (limit to 10)
    val routesSection = catenaryResults?.routesSection
    routesSection?.let { section ->
        val ranked = section.ranking.take(10)
        val total = ranked.size.coerceAtLeast(1)
        ranked.forEachIndexed { index, ranking ->
            val routeInfo = section.routes[ranking.chateau]?.get(ranking.gtfsId)
            val agency = routeInfo?.agencyId?.let { section.agencies[ranking.chateau]?.get(it) }
            if (routeInfo != null) {
                val base = rankToUnitScore(index, total)
                val score = base * ROUTE_WEIGHT
                rows += SearchRow.RouteRow(ranking, routeInfo, agency, score)
            }
        }
    }

    // Stops (limit to 10)
    val stopsSection = catenaryResults?.stopsSection
    stopsSection?.let { section ->
        val ranked = section.ranking.take(10)
        val total = ranked.size.coerceAtLeast(1)
        ranked.forEachIndexed { index, ranking ->
            val stopInfo = section.stops[ranking.chateau]?.get(ranking.gtfsId)
            if (stopInfo != null && stopInfo.parentStation == null) {
                val routes =
                    stopInfo.routes.mapNotNull { routeId ->
                        section.routes[ranking.chateau]?.get(routeId)
                    }

                val agencyNames =
                    routes
                        .mapNotNull { route ->
                            route.agencyId?.let {
                                section.agencies[ranking.chateau]?.get(it)?.agencyName
                            }
                        }
                        .distinct()

                val distanceMetres =
                    userLocation?.let { (userLat, userLon) ->
                        haversineDistance(
                            userLat,
                            userLon,
                            stopInfo.point.y,
                            stopInfo.point.x,
                        )
                    }

                val base = rankToUnitScore(index, total)
                val score = base * STOP_WEIGHT

                rows +=
                    SearchRow.StopRow(
                        ranking = ranking,
                        stopInfo = stopInfo,
                        routes = routes,
                        agencyNames = agencyNames,
                        distanceMetres = distanceMetres,
                        score = score,
                    )
            }
        }
    }

    // Sort across categories by weighted score (descending)
    return rows.sortedByDescending { it.weightedScore }
}

