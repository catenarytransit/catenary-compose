package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import com.google.maps.android.PolyUtil.decode as decodePolyutil
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datadog.android.core.internal.utils.JsonSerializer.safeMapValuesToJson
import com.google.maps.android.PolyUtil.decode
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import java.time.Instant
import java.net.URLEncoder
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.serialization.json.JsonObject

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
    val shapes_list: List<String>? = null
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
)

@Serializable
data class DeparturesAtStopResponse(
    val primary: StopPrimary? = null,
    val routes: Map<String, Map<String, StopRouteInfo>>? = null,
    val shapes: Map<String, Map<String, String>>? = null, // Chateau -> Shape ID -> Polyline
    val events: List<StopEvent>? = null,
    val alerts: Map<String, Map<String, Alert>> = emptyMap()
)

// --- Internal State Data Classes ---
private data class StopMeta(
    val primary: StopPrimary,
    val routes: Map<String, Map<String, StopRouteInfo>>,
    val shapes: Map<String, Map<String, String>>,
    val alerts: Map<String, Map<String, Alert>>
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

// Main Composable
@Composable
fun StopScreen(
    screenData: CatenaryStackEnum.StopStack,
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
    val chateau = screenData.chateau_id
    val stopId = screenData.stop_id

    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // --- State ---
    val pages = remember { mutableStateListOf<PageInfo>() }
    val eventIndex = remember { mutableStateMapOf<String, StopEventPageData>() }
    var dataMeta by remember { mutableStateOf<StopMeta?>(null) }
    var currentTime by remember { mutableStateOf(Instant.now().epochSecond) }
    var showPreviousDepartures by remember { mutableStateOf(false) }
    var currentPageHours by remember { mutableStateOf(12) }
    var flyToAlready by remember { mutableStateOf(false) }

    // --- Derived State ---
    val mergedEvents by remember {
        derivedStateOf {
            eventIndex.values.map { it.event }.sortedBy {
                it.realtime_departure ?: it.realtime_arrival ?: it.scheduled_departure
                ?: it.scheduled_arrival ?: 0
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

    val datesToEventsFiltered by remember {
        derivedStateOf {
            val tz = dataMeta?.primary?.timezone?.let {
                try {
                    ZoneId.of(it)
                } catch (e: Exception) {
                    ZoneId.systemDefault()
                }
            } ?: ZoneId.systemDefault()

            mergedEvents
                .filter { event ->
                    val cutoff = if (showPreviousDepartures) 1800 else 60
                    (event.realtime_departure ?: event.scheduled_departure
                    ?: 0) >= (currentTime - cutoff)
                }
                .groupBy { event ->
                    val stamp = (event.realtime_departure ?: event.realtime_arrival
                    ?: event.scheduled_departure ?: event.scheduled_arrival ?: 0) * 1000
                    Instant.ofEpochMilli(stamp).atZone(tz).toLocalDate()
                }
                .toList()
                .sortedBy { it.first }
        }
    }

    // --- Helper Functions ---
    fun composeEventKey(ev: StopEvent): String {
        val sched = ev.scheduled_departure ?: ev.scheduled_arrival ?: 0
        return "${ev.chateau}|${ev.trip_id}|${ev.stop_id}|${ev.service_date}|${sched}"
    }

    fun chooseNextPageHours(count: Int): Int {
        return when {
            count >= THRESHOLD_HIGH -> 2
            count <= THRESHOLD_LOW -> (currentPageHours * 2).coerceAtMost(24).coerceAtLeast(12)
            else -> 12
        }
    }

    fun mergePageEvents(pageId: String, data: DeparturesAtStopResponse, refreshedAt: Long) {
        // Merge meta
        if (data.primary != null) {
            val newRoutes = (dataMeta?.routes ?: emptyMap()) + (data.routes ?: emptyMap())
            val newShapes = (dataMeta?.shapes ?: emptyMap()) + (data.shapes ?: emptyMap())
            val newStops = (dataMeta?.alerts ?: emptyMap()) + (data.alerts ?: emptyMap())
            dataMeta = StopMeta(data.primary, newRoutes, newShapes, newStops)
        }

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
        val id = "$startSec-$endSec"
        var page = pages.find { it.id == id }
        if (page == null) {
            page = PageInfo(id, startSec, endSec)
            pages.add(page)
        }
        if (page.loading) return
        page.loading = true

        val encodedStopId = URLEncoder.encode(stopId, "UTF-8")
        val encodedChateau = URLEncoder.encode(chateau, "UTF-8")

        val url =
            "https://birch.catenarymaps.org/departures_at_stop?stop_id=$encodedStopId&chateau_id=$encodedChateau&greater_than_time=$startSec&less_than_time=$endSec"

        try {
            val data = ktorClient.get(url).body<DeparturesAtStopResponse>()

            // I can't think of a better way to check for a null response without making the entire model nullable
            val refreshedAt = Instant.now().toEpochMilli()
            page.refreshedAt = refreshedAt
            page.loading = false

            mergePageEvents(id, data, refreshedAt)
            currentPageHours = chooseNextPageHours(data.events?.size ?: 0)

        } catch (e: Exception) {
            page.error = e.message

            println("Error fetching page: $e")



            page.loading = false
        }
    }

    suspend fun loadInitialPages() {
        // Clear state
        pages.clear()
        eventIndex.clear()
        dataMeta = null
        flyToAlready = false
        currentPageHours = 12

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
    LaunchedEffect(chateau, stopId) {
        loadInitialPages()
    }

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
                scope.launch {
                    fetchPage(page.startSec, page.endSec)
                }
            }
        }
    }

    // Infinite scroll
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo }
            .collect { layoutInfo ->
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                if (lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - 5) {
                    if (pages.none { it.loading }) {
                        loadNextPage()
                    }
                }
            }
    }

    // Map interaction
    LaunchedEffect(dataMeta) {
        val meta = dataMeta ?: return@LaunchedEffect
        val primary = meta.primary
        val map = camera

        if (!flyToAlready) {
            scope.launch {
                geoLock.deactivate()

                camera.animateTo(
                    camera.position.copy(
                        target = Position(primary.stop_lon, primary.stop_lat),
                        zoom = 14.0
                    )
                )
            }
            flyToAlready = true
        }

        // Set stop pin
        val stopFeature = Feature(
            Point(Position(primary.stop_lon, primary.stop_lat)),
            properties = JsonObject(
                mapOf(
                    "label" to JsonPrimitive(primary.stop_name),
                    "stop_route_type" to JsonPrimitive(0)
                )
            ) // Use 0 for "other" style
        )
        stopsContextSource.value.setData(GeoJsonData.Features(FeatureCollection(listOf(stopFeature))))

        // Clear other context lines
        transitShapeSource.value.setData(
            GeoJsonData.Features(
                FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
            )
        )
        onSetStopsToHide(emptySet()) // Clear stop hiding

        // Build and set shape lines for this stop
        val features = mutableListOf<Feature<LineString, JsonObject>>()
        meta.routes.forEach { (chateauId, routes) ->
            routes.forEach { (routeId, route) ->
                route.shapes_list?.forEach { shapeId ->
                    meta.shapes[chateauId]?.get(shapeId)?.let { polyline ->
                        try {
                            val latLngs = com.google.maps.android.PolyUtil.decode(polyline)
                            val positions = latLngs.map { Position(it.longitude, it.latitude) }
                            val lineString = LineString(positions)
                            features.add(
                                Feature(
                                    lineString,
                                    properties = JsonObject(mapOf("color" to JsonPrimitive(route.color)))
                                )
                            )
                        } catch (e: Exception) {
                            // ignore invalid polyline
                        }
                    }
                }
            }
        }
        transitShapeForStopSource.value.setData(GeoJsonData.Features(FeatureCollection(features)))
    }

    // Cleanup map on exit
    DisposableEffect(Unit) {
        onDispose {
            transitShapeForStopSource.value.setData(
                GeoJsonData.Features(FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>()))
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
    if (meta == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        val zoneId = remember(meta.primary.timezone) {
            try {
                ZoneId.of(meta.primary.timezone)
            } catch (e: Exception) {
                ZoneId.systemDefault()
            }
        }
        val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
        val dateHeaderFormatter = remember(locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).withZone(zoneId)
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = meta.primary.stop_name,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        NavigationControls(onBack = onBack, onHome = onHome)
                    }
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = currentTime,
                        showSeconds = true,
                        // The style from LiveClock is now applied here
                    )
                    Text(
                        text = meta.primary.timezone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val alertsExpandedState = remember { mutableStateMapOf<String, Boolean>() }
                    meta.alerts?.forEach { (chateauId, alertsmap) ->
                        val expanded = alertsExpandedState.getOrPut(chateauId) { true }
                        AlertsBox(
                            alerts = alertsmap,
                            chateau = chateauId,
                            default_tz = zoneId.id,
                            isScrollable = false,
                            expanded = expanded,
                            onExpandedChange = { alertsExpandedState[chateauId] = !expanded }
                        )
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
                            imageVector = if (showPreviousDepartures) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(0.dp))
                        Text(
                            text = stringResource(id = R.string.previous_departures),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
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
                            modifier = Modifier
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
                        val routeInfo = meta.routes[event.chateau]?.get(event.route_id)
                        StopScreenRow(
                            event = event,
                            routeInfo = routeInfo,
                            currentTime = currentTime,
                            zoneId = zoneId,
                            locale = locale,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTripClick(
                                        CatenaryStackEnum.SingleTrip(
                                            chateau_id = event.chateau,
                                            trip_id = event.trip_id,
                                            route_id = event.route_id,
                                            start_time = null,
                                            start_date = event.service_date?.replace("-", ""),
                                            vehicle_id = null,
                                            route_type = null // This will be fetched in SingleTrip
                                        )
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
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

@Composable
private fun StopScreenRow(
    event: StopEvent,
    routeInfo: StopRouteInfo?,
    currentTime: Long,
    zoneId: ZoneId,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    val isPast = (event.realtime_departure ?: event.scheduled_departure ?: 0) < (currentTime - 60)

    val departureTimeToShow = event.realtime_departure ?: event.scheduled_departure
    val scheduledTime = event.scheduled_departure ?: event.scheduled_arrival
    val isRealtime = event.realtime_departure != null
    val delaySeconds = event.delay_seconds
    val isCancelled = event.trip_cancelled == true
    val isDeleted = event.trip_deleted == true
    val stopCancelled = event.stop_cancelled == true

    // The main column for the entire row item
    Column(modifier = modifier) {
        // Top part: Route info, headsign, etc.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (routeInfo != null) {
                    Text(
                        text = routeInfo.short_name ?: routeInfo.long_name ?: event.route_id,
                        color = parseColor(routeInfo.text_color, Color.White),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(parseColor(routeInfo.color, Color.Gray))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                if (!event.trip_short_name.isNullOrBlank()) {
                    Text(
                        text = event.trip_short_name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 0.dp)
                    )
                }
            }

            // Vehicle Number
            if (!event.vehicle_number.isNullOrBlank()) {
                Text(
                    text = "• ${event.vehicle_number}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = event.headsign ?: stringResource(id = R.string.no_headsign),
            style = if (isCancelled) MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough) else MaterialTheme.typography.bodyLarge,
            color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )

        // Bottom part: Time information, matching the Svelte layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCancelled) {
                Text(
                    text = "Cancelled",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                val timeToStrike = scheduledTime ?: departureTimeToShow
                if (timeToStrike != null) {
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = timeToStrike,
                        showSeconds = false,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPast) 0.5f else 0.7f),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            } else if (isDeleted) {
                Text(
                    text = "Deleted",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                val timeToStrike = scheduledTime ?: departureTimeToShow
                if (timeToStrike != null) {
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = timeToStrike,
                        showSeconds = false,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPast) 0.5f else 0.7f),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            } else if (stopCancelled) {
                Text(
                    text = "Stop Deleted",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                val timeToStrike = scheduledTime ?: departureTimeToShow
                if (timeToStrike != null) {
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = timeToStrike,
                        showSeconds = false,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPast) 0.5f else 0.7f),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            } else {
                val label =
                    if (event.last_stop == true) stringResource(R.string.arrival) else stringResource(
                        R.string.departure
                    )
                Text(text = stringResource(id = R.string.stop_screen_label, label), style = MaterialTheme.typography.bodyMedium)

                val targetTime = departureTimeToShow ?: scheduledTime
                if (targetTime != null) {
                    DiffTimer(
                        diff = (targetTime - currentTime).toDouble(),
                        showBrackets = false,
                        showSeconds = false,
                        showDays = false,
                        showPlus = false
                    )
                }

                if (isRealtime && scheduledTime != null && departureTimeToShow != null) {
                    Spacer(Modifier.size(4.dp))
                    DelayDiff(diff = departureTimeToShow - scheduledTime, show_seconds = false)
                }

                Spacer(Modifier.weight(1f))

                // Wall clock time display
                val timeColor =
                    if (isRealtime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                val finalColor = if (isPast) timeColor.copy(alpha = 0.7f) else timeColor

                if (isRealtime && scheduledTime != null && departureTimeToShow != null) {
                    if (departureTimeToShow != scheduledTime) {
                        FormattedTimeText(
                            timezone = zoneId.id,
                            timeSeconds = scheduledTime,
                            showSeconds = false,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPast) 0.5f else 1.0f),
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = departureTimeToShow,
                        showSeconds = false,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = finalColor
                    )
                } else if (scheduledTime != null) {
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = scheduledTime,
                        showSeconds = false,
                        style = MaterialTheme.typography.bodyMedium, color = finalColor
                    )
                }
            }
        }
    }
}