package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.maps.android.PolyUtil.encode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

// --- Paging controls ---
private const val OVERLAP_SECONDS = 5 * 60 // 5 min
private const val PAGE_REFRESH_MS = 10000L // 10s
private const val THRESHOLD_HIGH = 150
private const val THRESHOLD_LOW = 40

// --- Data Models for API Response ---
@Serializable
data class StopPrimary(
    val stop_name: String,
    val stop_lon: Double,
    val stop_lat: Double,
    val timezone: String
)

@Serializable
data class StopRouteInfo(
    val color: String,
    val text_color: String,
    val short_name: String? = null,
    val long_name: String? = null,
    val shapes_list: List<String>? = null,
    val route_type: Int? = null,
    val agency_id: String? = null
)

@Serializable
data class AgencyInfo(
    val agency_name: String,
    val agency_url: String? = null,
    val agency_timezone: String? = null,
    val agency_lang: String? = null,
    val agency_phone: String? = null,
    val agency_fare_url: String? = null
)

@Serializable
data class StopEvent(
    val chateau: String,
    val trip_id: String? = null,
    val route_id: String,
    val service_date: String? = null,
    val headsign: String? = null,
    val stop_id: String,
    val scheduled_departure: Long? = null,
    val realtime_departure: Long? = null,
    val scheduled_arrival: Long? = null,
    val realtime_arrival: Long? = null,
    val trip_short_name: String? = null,
    val last_stop: Boolean? = false,
    val platform_string_realtime: String? = null,
    val vehicle_number: String? = null,
    val delay_seconds: Long? = null,
    val trip_cancelled: Boolean,
    val stop_cancelled: Boolean,
    val trip_deleted: Boolean,
    val route_type: Int? = null
)

@Serializable
data class DeparturesAtStopResponse(
    val primary: StopPrimary? = null,
    val routes: Map<String, Map<String, StopRouteInfo>>? = null,
    val shapes: Map<String, Map<String, String>>? = null, // Chateau -> Shape ID -> Polyline
    val events: List<StopEvent>? = null,
    val alerts: Map<String, Map<String, Alert>> = emptyMap(),
    val agencies: Map<String, Map<String, AgencyInfo>>? = null, // Chateau -> Agency ID -> Info
    val stops: List<StopPrimary>? = null
)

// --- Internal State Data Classes ---
private data class StopMeta(
    val primary: StopPrimary?,
    val routes: Map<String, Map<String, StopRouteInfo>>,
    val shapes: Map<String, Map<String, String>>,
    val alerts: Map<String, Map<String, Alert>>,
    val agencies: Map<String, Map<String, AgencyInfo>>,
    val stops: List<StopPrimary>?
)

private data class PageInfo(
    val id: String,
    val startSec: Long,
    val endSec: Long,
    var refreshedAt: Long = 0,
    var loading: Boolean = false,
    var error: String? = null
)

private data class StopEventPageData(
    val event: StopEvent,
    val pageId: String,
    val refreshedAt: Long
)

@Serializable
data class OsmStationLookupResponse(
    val found: Boolean,
    val osm_station_id: String?,
    val osm_station_info: OsmStationInfo?
)

@Serializable
data class OsmStationInfo(val name: String?, val mode_type: String?)

// Main Composable
@Composable
fun StopScreen(
    screenData: CatenaryStackEnum,
    catenaryStack: ArrayDeque<CatenaryStackEnum>,
    reassigncatenarystack: (ArrayDeque<CatenaryStackEnum>) -> Unit,
    ktorClient: HttpClient,
    onTripClick: (CatenaryStackEnum.SingleTrip) -> Unit,
    transitShapeForStopSource: MutableState<GeoJsonSource>,
    stopsContextSource: MutableState<GeoJsonSource>,
    transitShapeSource: MutableState<GeoJsonSource>,
    camera: CameraState,
    onSetStopsToHide: (Set<String>) -> Unit,
    geoLock: GeoLockController,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    // Determine strict types or nulls
    val stopData = screenData as? CatenaryStackEnum.StopStack
    val osmStackData = screenData as? CatenaryStackEnum.OsmStationStack

    // Key generation depends on what we have
    // If pure OSM stack, key is osm_id
    // If stop stack, key is stop_id
    val key = stopData?.stop_id ?: osmStackData?.osm_station_id ?: "unknown"
    val chateauId = stopData?.chateau_id ?: "osm" // default or handled otherwise?

    // We keep 'chateau' just for context if available
    // For OSM stack, we might not have chateau_id easily unless passed, but Svelte uses it for
    // lookup only.
    // In Svelte, OsmStationScreen uses GenericStopScreen directly.

    val scope = rememberCoroutineScope()
    var fetchedInitial by remember { mutableStateOf(false) }

    // State to track if we found an OSM station for this GTFS stop
    // If so, we might want to redirect (as per Svelte logic which replaces the stack item)
    LaunchedEffect(key) {
        if (stopData != null && !fetchedInitial) {
            // Check for OSM station
            try {
                // "https://birch.catenarymaps.org/osm_station_lookup?chateau_id=${chateau}&gtfs_stop_id=${stop_id}"
                val lookupUrl =
                    "https://birch.catenarymaps.org/osm_station_lookup?chateau_id=${stopData.chateau_id}&gtfs_stop_id=${stopData.stop_id}"
                val response: OsmStationLookupResponse = ktorClient.get(lookupUrl).body()

                if (response.found && response.osm_station_id != null) {
                    println(
                        "Found OSM station ${response.osm_station_id} for stop ${stopData.stop_id}, redirecting..."
                    )
                    val newStackItem =
                        CatenaryStackEnum.OsmStationStack(
                            osm_station_id = response.osm_station_id,
                            station_name = response.osm_station_info?.name,
                            mode_type = response.osm_station_info?.mode_type
                        )

                    // Replace current item in stack
                    val newStack = ArrayDeque(catenaryStack)
                    if (newStack.isNotEmpty()) {
                        newStack.removeLast() // pop current
                        newStack.addLast(newStackItem) // push new
                        reassigncatenarystack(newStack)
                    }
                }
            } catch (e: Exception) {
                println("Error checking OSM station: $e")
            }
            fetchedInitial = true
        }
    }

    // If we were just redirected, this Composable might remain active/recomposed with new data?
    // Actually, reassigncatenarystack causes parent to likely recompose generic "StopScreen" with
    // new data if usage matches.
    // Assuming MainActivity handles both StopStack and OsmStationStack by calling StopScreen.

    // ... (rest of logic)
    val lazyListState = rememberLazyListState()

    // --- Helper Functions ---
    fun composeEventKey(ev: StopEvent): String {
        val sched = ev.scheduled_departure ?: ev.scheduled_arrival ?: 0
        return "${ev.chateau}|${ev.trip_id}|${ev.route_id}|${ev.headsign}|${ev.stop_id}|${ev.service_date}|${sched}"
    }

    // --- State ---
    val pages = remember { mutableStateListOf<PageInfo>() }
    val eventIndex = remember { mutableStateMapOf<String, StopEventPageData>() }
    var dataMeta by remember { mutableStateOf<StopMeta?>(null) }
    var currentTime by remember { mutableStateOf(Instant.now().epochSecond) }
    var showPreviousDepartures by remember { mutableStateOf(false) }
    var currentPageHours by remember { mutableStateOf(1) }
    var flyToAlready by remember { mutableStateOf(false) }
    val requestedShapes = remember { mutableStateListOf<String>() }

    // --- Derived State ---
    val mergedEvents by remember {
        derivedStateOf {
            eventIndex.values.map { it.event }.distinctBy { composeEventKey(it) }.sortedBy {
                it.realtime_departure
                    ?: it.realtime_arrival ?: it.scheduled_departure ?: it.scheduled_arrival
                    ?: 0
            }
        }
    }

    val previousCount by remember {
        derivedStateOf {
            mergedEvents.count {
                (it.realtime_departure ?: it.scheduled_departure ?: 0) < (currentTime - 60)
            }
        }
    }

    // Filters
    // Mode = 'rail' | 'metro' | 'bus' | 'other'
    var activeTab by remember(key) { mutableStateOf("rail") }
    var availableModes by remember { mutableStateOf(listOf<String>()) }

    fun getModeForRouteType(routeType: Int): String {
        return when (routeType) {
            3, 11, 700 -> "bus"
            0, 1, 5, 7, 12, 900 -> "metro"
            2, 106, 107, 101, 100, 102, 103 -> "rail"
            else -> "other"
        }
    }

    LaunchedEffect(mergedEvents, dataMeta) {
        val modes = mutableSetOf<String>()
        mergedEvents.forEach { ev ->
            val routeDef = dataMeta?.routes?.get(ev.chateau)?.get(ev.route_id)
            val rType = routeDef?.route_type ?: 3 // default to bus?
            // Wait, StopEvent doesn't have route_type directly unless we join?
            // Actually StopRouteInfo has it. If missing, assume 3?
            // Svelte logic: const rType = routeDef?.route_type ?? ev.route_type ?? 3;
            // Does StopEvent have route_type? No, only StopRouteInfo.
            // But SingleTrip has it? Page logic doesn't join inherently.
            // Let's check StopEvent definition... it DOES NOT have route_type.
            // Actually, `StopScreen.kt` Line 100: `StopRouteInfo` has `route_type`.
            // Line 104 `StopEvent` does not have `route_type`.
            // I should access it via `dataMeta`.
            modes.add(getModeForRouteType(rType))
        }

        val order = listOf("rail", "metro", "bus", "other")
        val newAvailable = order.filter { modes.contains(it) }
        availableModes = newAvailable

        if (newAvailable.isNotEmpty() && !newAvailable.contains(activeTab)) {
            activeTab = newAvailable[0]
        }
    }

    val displayTimezone by remember {
        derivedStateOf {
            dataMeta?.primary?.timezone
                ?: dataMeta?.stops?.firstOrNull()?.timezone
                ?: dataMeta?.agencies
                    ?.values
                    ?.firstOrNull()
                    ?.values
                    ?.firstOrNull()
                    ?.agency_timezone
        }
    }

    val datesToEventsFiltered by remember {
        derivedStateOf {
            val tz =
                displayTimezone?.let {
                    try {
                        ZoneId.of(it)
                    } catch (e: Exception) {
                        ZoneId.systemDefault()
                    }
                }
                    ?: ZoneId.systemDefault()

            mergedEvents
                .filter { event ->
                    val cutoff = if (showPreviousDepartures) 1800 else 60
                    // Filter by time
                    val relevantTime =
                        if (event.last_stop == true)
                            event.realtime_arrival ?: event.scheduled_arrival
                        else event.realtime_departure ?: event.scheduled_departure
                    if ((relevantTime ?: 0) < (currentTime - cutoff)) return@filter false

                    if (event.last_stop == true) {
                        return@filter false
                    }

                    // Filter by mode
                    if (availableModes.size > 1) {
                        val routeDef = dataMeta?.routes?.get(event.chateau)?.get(event.route_id)
                        val rType = routeDef?.route_type ?: 3
                        if (getModeForRouteType(rType) != activeTab) return@filter false
                    }
                    true
                }
                .groupBy { event ->
                    val stamp =
                        (event.realtime_departure
                            ?: event.realtime_arrival ?: event.scheduled_departure
                            ?: event.scheduled_arrival ?: 0) * 1000
                    Instant.ofEpochMilli(stamp).atZone(tz).toLocalDate()
                }
                .toList()
                .sortedBy { it.first }
        }
    }

    fun chooseNextPageHours(count: Int): Int {
        return when {
            count >= THRESHOLD_HIGH -> 2
            count <= THRESHOLD_LOW -> (currentPageHours * 2).coerceAtMost(24)
            else -> currentPageHours
        }
    }

    fun mergePageEvents(pageId: String, data: DeparturesAtStopResponse, refreshedAt: Long) {
        // Merge meta
        // Deep merge Routes
        val mergedRoutes = (dataMeta?.routes ?: emptyMap()).toMutableMap()
        data.routes?.forEach { (chateau, newRoutes) ->
            val existing = mergedRoutes[chateau] ?: emptyMap()
            mergedRoutes[chateau] = existing + newRoutes
        }

        // Deep merge Shapes
        val mergedShapes = (dataMeta?.shapes ?: emptyMap()).toMutableMap()
        data.shapes?.forEach { (chateau, newShapes) ->
            val existing = mergedShapes[chateau] ?: emptyMap()
            mergedShapes[chateau] = existing + newShapes
        }

        // Deep merge Alerts
        val mergedAlerts = (dataMeta?.alerts ?: emptyMap()).toMutableMap()
        data.alerts.forEach { (chateau, newAlerts) ->
            val existing = mergedAlerts[chateau] ?: emptyMap()
            mergedAlerts[chateau] = existing + newAlerts
        }

        // Deep merge Agencies
        val mergedAgencies = (dataMeta?.agencies ?: emptyMap()).toMutableMap()
        data.agencies?.forEach { (chateau, newAgencies) ->
            val existing = mergedAgencies[chateau] ?: emptyMap()
            mergedAgencies[chateau] = existing + newAgencies
        }

        // Handle primary: prioritize non-null
        val currentPrimary = data.primary ?: dataMeta?.primary
        val currentStops = data.stops ?: dataMeta?.stops

        dataMeta =
                StopMeta(
                    currentPrimary,
                    mergedRoutes,
                    mergedShapes,
                    mergedAlerts,
                    mergedAgencies,
                    currentStops
                )

        // Merge events
        data.events?.forEach { ev ->
            val key = composeEventKey(ev)
            val existing = eventIndex[key]
            if (existing == null || refreshedAt > existing.refreshedAt) {
                eventIndex[key] = StopEventPageData(ev, pageId, refreshedAt)
            }
        }
    }

    suspend fun fetchPage(startSec: Long, endSec: Long) {
        val id = "${startSec}_${endSec}"
        val existing = pages.find { it.id == id }
        if (existing != null && existing.loading && existing.error == null) return

        val page = existing ?: PageInfo(id, startSec, endSec, loading = true).also { pages.add(it) }
        page.loading = true
        page.error = null

        val useUrl =
            if (osmStackData != null) {
                "https://birch.catenarymaps.org/departures_at_osm_station?osm_station_id=${key}&start_time=${startSec}&end_time=${endSec}"
            } else {
                "https://birchdeparturesfromstop.catenarymaps.org/departures_at_stop?chateau_id=${chateauId}&stop_id=${key}&start_time=${startSec}&end_time=${endSec}&include_shapes=false"
            }

        var responseString: String? = null
        try {
            responseString = ktorClient.get(useUrl).body<String>()
            val rawData =
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<DeparturesAtStopResponse>(responseString!!)

            // Fix for OSM station data: sometimes it marks everything as last_stop
            // If we have a departure time, it's definitely not a last stop for the purpose of this
            // screen
            val fixedEvents =
                rawData.events?.map { ev ->
                    if (ev.last_stop == true &&
                        (ev.scheduled_departure != null ||
                                ev.realtime_departure != null)
                    ) {
                        ev.copy(last_stop = false)
                    } else {
                        ev
                    }
                }
            val data = rawData.copy(events = fixedEvents)

            val refreshedAt = Instant.now().epochSecond
            mergePageEvents(id, data, refreshedAt)
            currentPageHours = chooseNextPageHours(data.events?.size ?: 0)
        } catch (e: Exception) {
            page.error = e.message

            println("Error fetching page: $e")
            if (responseString != null) {
                println("Raw response body: $responseString")
            }

            page.loading = false
        }
    }

    suspend fun fetchShape(chateauId: String, shapeId: String, routeType: Int? = null) {
        val key = "${chateauId}|${shapeId}"
        if (requestedShapes.contains(key)) return
        requestedShapes.add(key)

        withContext(Dispatchers.Default) {
            val encodedChateau = URLEncoder.encode(chateauId, "UTF-8")
            val encodedShape = URLEncoder.encode(shapeId, "UTF-8")

            // Heuristic: Route Type 2 (Rail) usually has long, complex shapes.
            val isComplexRoute = routeType == 2

            try {
                val finalPolyline: String? =
                    if (isComplexRoute) {
                        val lat = dataMeta?.primary?.stop_lat ?: osmStackData?.lat
                        val lon = dataMeta?.primary?.stop_lon ?: osmStackData?.lon

                        if (lat != null && lon != null) {
                            // println("Fetching complex shape for train route...")
                            try {
                                val cropRadius = 0.1
                                val minX = lon - cropRadius
                                val maxX = lon + cropRadius
                                val minY = lat - cropRadius
                                val maxY = lat + cropRadius

                                coroutineScope {
                                    val highResDeferred = async {
                                        val urlLocal =
                                            "https://birchshapescustom.catenarymaps.org/get_shape?chateau=${encodedChateau}&shape_id=${encodedShape}&format=polyline&simplify=10.0&min_x=$minX&max_x=$maxX&min_y=$minY&max_y=$maxY"
                                        try {
                                            val response =
                                                ktorClient.get(urlLocal).body<JsonObject>()
                                            (response["polyline"] as? JsonPrimitive)?.content
                                        } catch (e: Exception) {
                                            // println("Failed to fetch local shape part: $e")
                                            null
                                        }
                                    }

                                    val lowResDeferred = async {
                                        val urlGlobal =
                                            "https://birchshapescustom.catenarymaps.org/get_shape?chateau=${encodedChateau}&shape_id=${encodedShape}&format=polyline&simplify=100.0"
                                        try {
                                            val response =
                                                ktorClient.get(urlGlobal).body<JsonObject>()
                                            (response["polyline"] as? JsonPrimitive)?.content
                                        } catch (e: Exception) {
                                            // println("Failed to fetch global shape part: $e")
                                            null
                                        }
                                    }

                                    val localPoly = highResDeferred.await()
                                    val globalPoly = lowResDeferred.await()

                                    if (localPoly != null && globalPoly != null) {
                                        splicePolylines(globalPoly, localPoly)
                                    } else {
                                        globalPoly ?: localPoly
                                    }
                                }
                            } catch (e: Exception) {
                                // println("Complex fetch failed, falling back to simple: $e")
                                null
                            }
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                val polylineToCheck =
                    if (finalPolyline == null) {
                        val url =
                            "https://birch.catenarymaps.org/get_shape?chateau=${encodedChateau}&shape_id=${encodedShape}&format=polyline"
                        val response = ktorClient.get(url).body<JsonObject>()
                        (response["polyline"] as? JsonPrimitive)?.content
                    } else {
                        finalPolyline
                    }

                if (polylineToCheck != null) {
                    // Update dataMeta (Thread-safe via Snapshot system, but keep in mind we are on
                    // Default dispatcher)
                    dataMeta?.let { meta ->
                        val currentShapesForChateau = meta.shapes[chateauId] ?: emptyMap()
                        // If we already have this shape (maybe from another simultaneous fetch?),
                        // don't overwrite blindly?
                        // Actually, map + overwrites.
                        val newShapesForChateau =
                            currentShapesForChateau + (shapeId to polylineToCheck)
                        val newShapes = meta.shapes + (chateauId to newShapesForChateau)
                        dataMeta = meta.copy(shapes = newShapes)
                    }
                }
            } catch (e: Exception) {
                // println("Error fetching shape: $e")
                requestedShapes.remove(key)
            }
        }
    }

    suspend fun loadInitialPages() {
        // Clear state
        pages.clear()
        eventIndex.clear()
        dataMeta = null
        flyToAlready = false
        currentPageHours = 1

        val nowSec = Instant.now().epochSecond
        val start = nowSec - 30 * 60 // 30m back
        val end = start + currentPageHours * 3600
        fetchPage(start, end)
    }

    suspend fun loadNextPage() {
        if (pages.isEmpty()) {
            loadInitialPages()
            return
        }
        val maxEnd = pages.maxOfOrNull { it.endSec } ?: (Instant.now().epochSecond + 3600)
        val start = maxEnd - OVERLAP_SECONDS
        val end = start + currentPageHours * 3600
        fetchPage(start, end)
    }

    // --- Effects ---

    // Initial load and reload on input change
    LaunchedEffect(chateauId, key) { loadInitialPages() }

    // Ticking clock
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Instant.now().epochSecond
            delay(500)
        }
    }

    // Page refresh timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(PAGE_REFRESH_MS)
            // Refetch all currently loaded pages
            pages.toList().forEach { page ->
                scope.launch { fetchPage(page.startSec, page.endSec) }
            }
        }
    }

    val isLoading by remember { derivedStateOf { pages.any { it.loading } } }

    // Infinite scroll
    LaunchedEffect(lazyListState, mergedEvents.size, isLoading) {
        // Check if we need to load more because the list is empty or short (bottom visible)
        snapshotFlow { lazyListState.layoutInfo }.collect { layoutInfo ->
            if (pages.none { it.loading }) {
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1

                // Condition 1: List is completely empty
                val isEmpty = totalItems == 0 && mergedEvents.isEmpty()

                // Condition 2: Not enough items to fill the screen (or close to bottom)
                // If totalItems is small, lastVisibleIndex will be totalItems - 1
                val isBottomVisible = totalItems > 0 && lastVisibleIndex >= totalItems - 2

                if (isEmpty || isBottomVisible) {
                    loadNextPage()
                }
            }
        }
    }

    // Map interaction
    LaunchedEffect(dataMeta, osmStackData) {
        val meta = dataMeta
        // Fallback to OSM stack data if primary is not available
        val lat = meta?.primary?.stop_lat ?: osmStackData?.lat
        val lon = meta?.primary?.stop_lon ?: osmStackData?.lon
        val name = meta?.primary?.stop_name ?: osmStackData?.station_name ?: "Station"

        if (lat == null || lon == null) return@LaunchedEffect

        val map = camera

        if (!flyToAlready) {
            scope.launch {
                geoLock.deactivate()

                camera.animateTo(camera.position.copy(target = Position(lon, lat), zoom = 14.0))
            }
            flyToAlready = true
        }

        // Set stop pin
        val stopFeature =
            Feature(
                Point(Position(lon, lat)),
                properties =
                    JsonObject(
                        mapOf(
                            "label" to JsonPrimitive(name),
                            "stop_route_type" to JsonPrimitive(0)
                        )
                    ) // Use 0 for "other" style
            )
        stopsContextSource.value.setData(
            GeoJsonData.Features(FeatureCollection(listOf(stopFeature)))
        )

        // Clear other context lines
        transitShapeSource.value.setData(
            GeoJsonData.Features(
                FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
            )
        )
        onSetStopsToHide(emptySet()) // Clear stop hiding

        // Build and set shape lines for this stop
        // Optimization: Limit shapes to prevent OOM on large stations
        val MAX_SHAPES = 20
        val features = mutableListOf<Feature<LineString, JsonObject>>()

        // Only show shapes for routes that have departures in the current view
        val relevantRouteIds = mergedEvents.map { it.route_id }.toSet()

        var shapeCount = 0
        outerLoop@ for ((chateauId, routes) in (meta?.routes ?: emptyMap())) {
            for ((routeId, route) in routes) {
                if (shapeCount >= MAX_SHAPES) break@outerLoop

                // Skip routes with no current departures
                if (!relevantRouteIds.contains(routeId)) continue

                // Take only the first shape per route to reduce memory usage
                val shapeId = route.shapes_list?.firstOrNull() ?: continue
                val polyline = meta?.shapes?.get(chateauId)?.get(shapeId)

                if (polyline == null) {
                    // Lazy fetch
                    scope.launch { fetchShape(chateauId, shapeId, route.route_type) }
                    continue
                }

                try {
                    val latLngs = com.google.maps.android.PolyUtil.decode(polyline)
                    val positions = latLngs.map { Position(it.longitude, it.latitude) }
                    val lineString = LineString(positions)
                    features.add(
                        Feature(
                            lineString,
                            properties =
                                JsonObject(mapOf("color" to JsonPrimitive(route.color)))
                        )
                    )
                    shapeCount++
                } catch (e: Exception) {
                    // ignore invalid polyline
                }
            }
        }
        transitShapeForStopSource.value.setData(GeoJsonData.Features(FeatureCollection(features)))
    }

    // Cleanup map on exit
    DisposableEffect(Unit) {
        onDispose {
            transitShapeForStopSource.value.setData(
                GeoJsonData.Features(
                    FeatureCollection(emptyList<Feature<LineString, Map<String, Any>>>())
                )
            )
            stopsContextSource.value.setData(
                GeoJsonData.Features(
                    FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
                )
            )
        }
    }

    // --- UI ---
    val meta = dataMeta
    // If no meta AND no valid fallback name/location, show loading
    // But if we have OSM stack data, we can at least show the header + loading
    val hasFallback = osmStackData != null

    if (meta == null && !hasFallback) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.End
            ) { NavigationControls(onBack = onBack, onHome = onHome) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
    } else {
        val timezone = displayTimezone
        val zoneId =
            remember(timezone) {
                try {
                    if (timezone != null) ZoneId.of(timezone) else ZoneId.systemDefault()
                } catch (e: Exception) {
                    ZoneId.systemDefault()
                }
            }
        val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
        val dateHeaderFormatter =
            remember(locale, zoneId) {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(locale)
                    .withZone(zoneId)
            }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                            text = meta?.primary?.stop_name
                                ?: osmStackData?.station_name ?: "Station",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                    )
                    NavigationControls(onBack = onBack, onHome = onHome)
                }
                if (timezone != null) {
                    FormattedTimeText(
                            timezone = zoneId.id,
                            timeSeconds = currentTime,
                            showSeconds = true,
                            // The style from LiveClock is now applied here
                    )
                    Text(
                            text = timezone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(state = lazyListState, modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                // Header
                item {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)) {
                        val alertsExpandedState = remember { mutableStateMapOf<String, Boolean>() }
                        meta?.alerts?.forEach { (chateauId, alertsmap) ->
                            val expanded = alertsExpandedState.getOrPut(chateauId) { true }
                            AlertsBox(
                                alerts = alertsmap,
                                chateau = chateauId,
                                default_tz = zoneId.id,
                                isScrollable = false,
                                expanded = expanded,
                                onExpandedChange = {
                                    alertsExpandedState[chateauId] = !expanded
                                }
                            )
                        }
                    }
                }

                // Tabs for filtering
                if (availableModes.size > 1) {
                    item {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, bottom = 8.dp)
                                    .background(MaterialTheme.colorScheme.background),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableModes.forEach { mode ->
                                val isSelected = activeTab == mode
                                val label =
                                    when (mode) {
                                        "rail" ->
                                            stringResource(R.string.heading_intercity_rail)

                                        "metro" -> stringResource(R.string.heading_local_rail)
                                        "bus" -> stringResource(R.string.heading_bus)
                                        else -> stringResource(R.string.heading_other)
                                    }

                                // Simple Tab Button
                                Column(
                                    modifier =
                                        Modifier
                                            .clickable { activeTab = mode }
                                            .padding(
                                                vertical = 8.dp,
                                                horizontal = 12.dp
                                            ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        style =
                                            MaterialTheme.typography.labelLarge.copy(
                                                fontWeight =
                                                    if (isSelected) FontWeight.Bold
                                                    else FontWeight.Normal,
                                                color =
                                                    if (isSelected)
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary
                                                    else
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onSurfaceVariant
                                            )
                                    )
                                    if (isSelected) {
                                        Box(
                                            Modifier
                                                .height(2.dp)
                                                .width(20.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Previous Departures Toggle
                if (previousCount > 0) {
                    item {
                        TextButton(
                            onClick = { showPreviousDepartures = !showPreviousDepartures },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Icon(
                                imageVector =
                                    if (showPreviousDepartures) Icons.Filled.KeyboardArrowUp
                                    else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(0.dp))
                            Text(
                                text = stringResource(id = R.string.previous_departures),
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                            )
                        }
                    }
                }

                // Departures List
                if (datesToEventsFiltered.isEmpty() && pages.none { it.loading }) {
                    item {
                        Text(
                            text = stringResource(id = R.string.no_departures_found),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    datesToEventsFiltered.forEach { (date, events) ->
                        // Date Header
                        stickyHeader {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = dateHeaderFormatter.format(date),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Event Rows
                        items(events, key = { composeEventKey(it) }) { event ->
                            val routeInfo = meta?.routes?.get(event.chateau)?.get(event.route_id)

                            val rType = routeInfo?.route_type ?: 3
                            val mode = getModeForRouteType(rType)

                            if (activeTab == "rail" && mode == "rail") {
                                StationScreenTrainRow(
                                    event = event,
                                    routeInfo = routeInfo,
                                    agencies = meta?.agencies?.get(event.chateau),
                                    currentTime = currentTime,
                                    zoneId = zoneId,
                                    locale = locale,
                                    showSeconds = false,
                                    useSymbolSign = true,
                                    modifier =
                                        Modifier.clickable {
                                            onTripClick(
                                                CatenaryStackEnum.SingleTrip(
                                                    chateau_id = event.chateau,
                                                    trip_id = event.trip_id,
                                                    route_id = event.route_id,
                                                    start_time = null,
                                                    start_date =
                                                        event.service_date
                                                            ?.replace(
                                                                "-",
                                                                ""
                                                            ),
                                                    vehicle_id = null,
                                                    route_type = null
                                                )
                                            )
                                        }
                                )
                            } else {
                                StopScreenRow(
                                    event = event,
                                    routeInfo = routeInfo,
                                    currentTime = currentTime,
                                    zoneId = zoneId,
                                    locale = locale,
                                    showArrivals = event.last_stop == true,
                                    useSymbolSign = false,
                                    vertical = false,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onTripClick(
                                                    CatenaryStackEnum.SingleTrip(
                                                        chateau_id =
                                                            event.chateau,
                                                        trip_id = event.trip_id,
                                                        route_id =
                                                            event.route_id,
                                                        start_time = null,
                                                        start_date =
                                                            event.service_date
                                                                ?.replace(
                                                                    "-",
                                                                    ""
                                                                ),
                                                        vehicle_id = null,
                                                        route_type =
                                                            null // This
                                                        // will be
                                                        // fetched in
                                                        // SingleTrip
                                                    )
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            HorizontalDivider(
                                color =
                                    MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.3f
                                    )
                            )
                        }
                    }
                }

                // Loading / Load More footer
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pages.any { it.loading }) {
                            CircularProgressIndicator()
                        } else {
                            Button(onClick = { scope.launch { loadNextPage() } }) {
                                Text(stringResource(id = R.string.load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun splicePolylines(globalPoly: String, localPoly: String): String {
    val globalPoints = com.google.maps.android.PolyUtil.decode(globalPoly)
    val localPoints = com.google.maps.android.PolyUtil.decode(localPoly)

    if (globalPoints.isEmpty()) return localPoly
    if (localPoints.isEmpty()) return globalPoly

    val startLocal = localPoints.first()
    val endLocal = localPoints.last()

    // We want to find indices (i, j) in globalPoints such that:
    // 1. i <= j
    // 2. dist(global[i], startLocal) + dist(global[j], endLocal) is minimized.
    //
    // Since globalPoints is usually not huge (< few thousand points), an O(N^2) or even optimized
    // O(N) search is fine.
    // We can do it in O(N) by keeping track of the best 'start' seen so far?
    // Actually no, because the best global start matching local start might be at index 100,
    // while a slightly worse start match is at index 10.
    // If the best end match is at index 20, we MUST pick start=10, end=20.
    // We can iterate j from 0..N. For each j, we want the best i <= j.
    // Let best_start_idx[j] be the index k in 0..j that minimizes dist(global[k], startLocal).
    // We can precompute this or compute on the fly.

    var bestStartIdx = -1
    var bestEndIdx = -1
    var minTotalDist = Double.MAX_VALUE

    // Track the best start index found so far as we iterate
    var currentBestStartIdx = -1
    var currentMinStartDist = Double.MAX_VALUE

    for (j in globalPoints.indices) {
        val p = globalPoints[j]

        // 1. Update best start candidate up to current index j
        val distToStart =
            (p.latitude - startLocal.latitude).let { it * it } +
                    (p.longitude - startLocal.longitude).let { it * it }

        if (distToStart < currentMinStartDist) {
            currentMinStartDist = distToStart
            currentBestStartIdx = j
        }

        // 2. Check if (currentBestStartIdx, j) is the global best pair
        val distToEnd =
            (p.latitude - endLocal.latitude).let { it * it } +
                    (p.longitude - endLocal.longitude).let { it * it }

        val totalDist = currentMinStartDist + distToEnd

        if (totalDist < minTotalDist) {
            minTotalDist = totalDist
            bestStartIdx = currentBestStartIdx
            bestEndIdx = j
        }
    }

    if (bestStartIdx == -1 || bestEndIdx == -1) return globalPoly

    val result = ArrayList<com.google.android.gms.maps.model.LatLng>()
    // Keep global up to start
    result.addAll(globalPoints.subList(0, bestStartIdx))
    // Add local
    result.addAll(localPoints)
    // Keep global from end
    if (bestEndIdx < globalPoints.size - 1) {
        result.addAll(globalPoints.subList(bestEndIdx + 1, globalPoints.size))
    }
    return com.google.maps.android.PolyUtil.encode(result)
}
