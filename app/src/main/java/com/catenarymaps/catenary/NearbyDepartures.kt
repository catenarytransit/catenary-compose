package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.Integer.min
import java.util.Locale
import kotlin.math.*
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/* -------------------------------------------------------------------------- */
/*  Data models that match the JSON payload (only the fields we actually use)  */
/* -------------------------------------------------------------------------- */
@Serializable
private data class NearbyResponse(
    val stop: Map<String, Map<String, StopEntry>>,
    val departures: List<RouteGroup>,
    val debug: DebugInfo? = null
)

@Serializable
private data class DebugInfo(
    @SerialName("total_time_ms") val totalTimeMs: Long? = null
)

@Serializable
private data class StopEntry(
    val name: String? = null,
    val lat: Double? = null,
    @SerialName("latitude") val latitude: Double? = null,
    val lon: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    val timezone: String? = null
)

@Serializable
private data class RouteGroup(
    @SerialName("chateau_id") val chateauId: String,
    @SerialName("route_id") val routeId: String,
    @SerialName("route_type") val routeType: Int,
    @SerialName("short_name") val shortName: String? = null,
    @SerialName("long_name") val longName: String? = null,
    val color: String? = null,
    @SerialName("text_color") val textColor: String? = null,
    // directions is a map keyed by headsign after consolidation server-side; we normalize on load
    val directions: Map<String, Map<String, DirectionGroup>>
)

@Serializable
private data class DirectionGroup(
    val headsign: String,
    val trips: List<TripEntry>,
)

@Serializable
data class TripEntry(
    @SerialName("stop_id") val stopId: String,
    @SerialName("trip_id") val tripId: String,
    val tz: String? = null,
    val platform: String? = null,
    val cancelled: Boolean? = null,
    val deleted: Boolean? = null,
    @SerialName("trip_short_name") val tripShortName: String? = null,
    @SerialName("departure_realtime") val departureRealtime: Long? = null,
    @SerialName("departure_schedule") val departureSchedule: Long? = null,
    @SerialName("gtfs_schedule_start_day") val startDay: String? = null,
    @SerialName("gtfs_frequency_start_time") val startTime: String? = null
)

data class TripClickResponse(
    val tripId: String? = null,
    val stopId: String? = null,
    val chateauId: String? = null,
    val routeId: String? = null,
    val routeType: Int? = null,
    val startDay: String? = null
)

/* -------------------------------- Utilities ------------------------------- */

private enum class SortMode { ALPHA, DISTANCE }

private data class Filters(
    val rail: Boolean = true,
    val metro: Boolean = true,
    val bus: Boolean = true,
    val other: Boolean = true
)

private fun flattenDirections(
    nested: Map<String, Map<String, DirectionGroup>>
): Map<String, DirectionGroup> {
    // Merge all sub-direction maps into a single map keyed by headsign,
    // concatenating trips, then sort trips by (realtime || schedule).
    val merged = mutableMapOf<String, MutableList<TripEntry>>()

    nested.values.forEach { inner ->
        inner.values.forEach { dg ->
            val key = dg.headsign
            val list = merged.getOrPut(key) { mutableListOf() }
            list += dg.trips
        }
    }

    // Build final map with trips sorted ascending by departure
    return merged.mapValues { (headsign, trips) ->
        val sortedTrips = trips
            .distinctBy { it.tripId + it.startDay + it.startTime }
            .sortedBy { t ->
                t.departureRealtime ?: t.departureSchedule ?: Long.MAX_VALUE
            }
        DirectionGroup(headsign = headsign, trips = sortedTrips)
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val toRad = { d: Double -> d * Math.PI / 180.0 }
    val R = 6371000.0
    val dLat = toRad(lat2 - lat1)
    val dLon = toRad(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(toRad(lat1)) * cos(toRad(lat2)) * sin(dLon / 2).pow(2)
    return 2 * R * asin(sqrt(a))
}

// Rough mapping mirroring the JS filter buckets
private fun isAllowedByFilter(routeType: Int, f: Filters): Boolean = when (routeType) {
    3, 11, 700 -> f.bus
    0, 1, 5, 7, 12, 900 -> f.metro
    2, 106, 107, 101, 100, 102, 103 -> f.rail
    4 -> f.other            // ferries
    else -> true
}

private fun normalizeHex(c: String?): Color? {
    if (c.isNullOrBlank()) return null
    val s = if (c.startsWith("#")) c.drop(1) else c
    return runCatching {
        val v = s.toLong(16).toInt()
        val r = (v shr 16) and 0xFF
        val g = (v shr 8) and 0xFF
        val b = v and 0xFF
        Color(r, g, b)
    }.getOrNull()
}

/* ------------------------------ Networking -------------------------------- */

private val json = Json {
    ignoreUnknownKeys = true
    //isLenient = true
    explicitNulls = true
}

private val httpClient by lazy { OkHttpClient() }

private const val NEARBY_TAG = "NearbyDebug"

private fun peekString(body: ResponseBody?, limit: Long = 200_000): String {
    if (body == null) return "<null body>"
    return try {
        val peeked = body.source().peek()
            .readByteArray(minOf(body.contentLength().takeIf { it >= 0 } ?: limit, limit))
        String(peeked)
    } catch (e: Throwable) {
        "<failed to peek: ${e.message}>"
    }
}


private fun logSerializationError(e: SerializationException, raw: String) {
    // kotlinx.serialization exceptions usually include a path like at path: $.foo[0].bar
    Log.e(NEARBY_TAG, "JSON decode failed: ${e.message}")
    // Log first 2k chars so we don’t spam Logcat
    val head = raw.take(2000)
    Log.e(NEARBY_TAG, "Body head (2k):\n$head")

}

private suspend fun fetchNearby(lat: Double, lon: Double): NearbyResponse? =
    withContext(Dispatchers.IO) {
        Log.d(
            NEARBY_TAG,
            "Fetch nearby departures lat=$lat lon=$lon (thread=${Thread.currentThread().name})"
        )

        val url = "https://birch.catenarymaps.org/nearbydeparturesfromcoordsv2?lat=$lat&lon=$lon"
        val req = Request.Builder().url(url).get().build()

        try {
            httpClient.newCall(req).execute().use { resp ->
                Log.d(NEARBY_TAG, "HTTP ${resp.code} ${resp.message}")

                if (!resp.isSuccessful) {
                    val preview = peekString(resp.body, limit = 4000)
                    Log.e(NEARBY_TAG, "Unsuccessful (${resp.code}). Body preview:\n$preview")
                    return@withContext null
                }

                val rawBody = resp.body?.string()
                if (rawBody.isNullOrEmpty()) {
                    Log.e(NEARBY_TAG, "Empty body")
                    return@withContext null
                }

                if (rawBody.trimStart().startsWith("<")) {
                    Log.e(NEARBY_TAG, "Body looks like HTML (unexpected):\n${rawBody.take(1000)}")
                    return@withContext null
                }

                Log.d(NEARBY_TAG, "Body length: ${rawBody.length} chars")

                withContext(Dispatchers.Default) {
                    try {
                        val parsed = json.decodeFromString(NearbyResponse.serializer(), rawBody)
                        Log.d(
                            NEARBY_TAG,
                            "Decoded OK: departures=${parsed.departures.size}, stops chateaus=${parsed.stop.keys.size}, serverMs=${parsed.debug?.totalTimeMs}"
                        )
                        parsed
                    } catch (se: SerializationException) {
                        logSerializationError(se, rawBody)
                        null
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(NEARBY_TAG, "Network error on ${Thread.currentThread().name}: ${t.message}", t)
            null
        }
    }

/* ----------------------------- Public UI API ------------------------------ */
/**
 * NearbyDepartures screen to place in your home bottom sheet.
 *
 * @param userLocation  current GPS coordinate, or null while waiting
 * @param pickedLocation coordinate for “pin” mode, or null if unused for now
 * @param usePickedLocation if true, query from [pickedLocation]; else from [userLocation]
 */
@Composable
fun NearbyDepartures(
    userLocation: Pair<Double, Double>? = null,
    pickedLocation: Pair<Double, Double>? = null,
    usePickedLocation: Boolean = false,
    pin: PinState? = null,
    darkMode: Boolean = false,
    onMyLocation: () -> Unit = {},
    onPinDrop: () -> Unit = {},
    onCenterPin: () -> Unit = {},
    onTripClick: (TripClickResponse) -> Unit = {},
    onRouteClick: (chateauId: String, routeId: String) -> Unit = { _, _ -> }
) {
    val applicationContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(applicationContext)
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, "NearbyDeparturesScreen")
                //param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
            }

        } catch (e: Exception) {
            // Log the error or handle it gracefully
            android.util.Log.e("GA", "Failed to log screen view", e)
        }
    }

    var filters by remember { mutableStateOf(Filters()) }
    var sortMode by remember { mutableStateOf(SortMode.DISTANCE) }
    var loading by remember { mutableStateOf(false) }
    var firstAttemptSent by remember { mutableStateOf(false) }
    var firstLoadComplete by remember { mutableStateOf(false) }
    var serverMs by remember { mutableStateOf<Long?>(null) }

    var stopsTable by remember { mutableStateOf<Map<String, Map<String, StopEntry>>>(emptyMap()) }
    var departureList by remember { mutableStateOf<List<RouteGroup>>(emptyList()) }

    // NEW: we "lock" the origin after the first fix so GPS drift won't restart fetches.
    var lockedOrigin by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // NEW: bump this to cancel current polling and start a fresh session
    var pollSession by remember { mutableStateOf(0) }
    fun restartPolling() {
        println("Restart polling")
        pollSession++
    }

    // --- LOCK INITIAL ORIGIN ONCE (GPS MODE) ---
    // When we first get a GPS fix and we're NOT using the pin, lock it once.
    LaunchedEffect(userLocation, usePickedLocation) {
        if (!usePickedLocation && lockedOrigin == null && userLocation != null) {
            lockedOrigin = userLocation
            restartPolling()
        }
    }

    // --- UPDATE LOCK WHEN SWITCHING MODES ---
    // If user toggles into pin mode and we have a pin, lock to the pin and restart.
    LaunchedEffect(usePickedLocation) {
        if (usePickedLocation && pickedLocation != null) {
            if (lockedOrigin != pickedLocation) {
                lockedOrigin = pickedLocation
                restartPolling()
            }
        } else if (!usePickedLocation && userLocation != null) {
            // Switching back to GPS: keep current lock unless the user explicitly presses My Location.
            // No action here to avoid auto updates past the first GPS fix.
        }
    }

    // --- PIN MOVES SHOULD CANCEL & REFETCH ---
    // If the pin moves while we are in pin mode, cancel and restart with the new pin.
    LaunchedEffect(pickedLocation) {
        if (usePickedLocation && pickedLocation != null && pickedLocation != lockedOrigin) {
            lockedOrigin = pickedLocation
            restartPolling()
        }
    }

    // --- POLLING LOOP: immediate fetch, then every 10s. Not tied to raw origin updates. ---
    LaunchedEffect(pollSession) {
        val origin = lockedOrigin ?: return@LaunchedEffect
        suspend fun once() {
            loading = true
            firstAttemptSent = true
            val res = fetchNearby(origin.first, origin.second)
            if (res != null) {
                stopsTable = res.stop
                departureList = res.departures
                serverMs = res.debug?.totalTimeMs
                println("nearby took ${res.debug?.totalTimeMs}")
            } else {
                println("nearby returned with nothing")
            }
            loading = false
        }

        // immediate fetch, then every 10 seconds
        once()
        firstLoadComplete = true
        while (currentCoroutineContext().isActive) {
            delay(10_000)
            once()
        }
    }

    // derived + sorting
    val filtered = remember(departureList, filters) {
        departureList.filter { rg ->
            rg.directions.isNotEmpty() && isAllowedByFilter(rg.routeType, filters)
        }
    }

    var sorted by remember { mutableStateOf<List<RouteGroup>>(emptyList()) }

    LaunchedEffect(filtered, sortMode, lockedOrigin, stopsTable) {
        withContext(Dispatchers.Default) {
            val result = when (sortMode) {
                SortMode.ALPHA -> filtered.sortedWith(
                    compareBy(
                        { (it.shortName ?: it.longName ?: "").lowercase(Locale.UK) },
                        { it.routeId }
                    )
                )

                SortMode.DISTANCE -> filtered.sortedWith(
                    compareBy(
                        {
                            val o = lockedOrigin
                            if (o == null) Double.POSITIVE_INFINITY
                            else tryDistanceForRouteGroup(it, o, stopsTable)
                        },
                        { (it.shortName ?: it.longName ?: "").lowercase(Locale.UK) }
                    )
                )
            }
            sorted = result
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        TopRow(
            currentPickModeIsPin = usePickedLocation,
            onMyLocation = {
                // User explicitly requested GPS recenter:
                onMyLocation()
                // If we have a current GPS fix, take it and restart polling.
                userLocation?.let {
                    lockedOrigin = it
                    restartPolling()
                }
            },
            onPinDrop = {
                onPinDrop()
                // Parent will update pickedLocation; our LaunchedEffect(pickedLocation) will handle restart.
            },
            onCenterPin = {
                onCenterPin()
                // No-op here; movement will be observed by pickedLocation change if it happens.
            },
            serverMs = serverMs,
            sortMode = sortMode,
            onSortChange = { sortMode = it },
            darkMode = darkMode
        )

        if (!firstAttemptSent && !usePickedLocation) {
            Text(
                text = "Waiting for GPS…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        } else {
            //Spacer(Modifier.height(8.dp))
        }

        FilterRow(filters = filters, onChange = { filters = it })

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets(
                            bottom = WindowInsets.safeDrawing.getBottom(
                                density = LocalDensity.current
                            )
                        )
                    ),
            ) {
                if (sorted.isEmpty() && firstLoadComplete && !loading) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(id = R.string.nearby_departures_no_departures), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(sorted, key = { it.chateauId + it.routeId }) { route ->
                    RouteGroupCard(route, stopsTable, darkMode, onTripClick, onRouteClick)
                }

            }
        }
    }
}

/* ------------------------------ UI Pieces --------------------------------- */

@Composable
private fun TopRow(
    currentPickModeIsPin: Boolean,
    onMyLocation: () -> Unit,
    onPinDrop: () -> Unit,
    onCenterPin: () -> Unit,
    serverMs: Long?,
    sortMode: SortMode,
    onSortChange: (SortMode) -> Unit,
    darkMode: Boolean
) {
    val primaryButtonColor =
        if (darkMode) Color(0xFF00532F) else Color(0xFF48DC90) // Dark green for dark mode, light green for light mode
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onMyLocation,
                modifier = Modifier
                    .size(36.dp)
                    .border(
                        2.dp,
                        Color(0xFF008744), // oklch(72.3% 0.219 149.579)
                        RoundedCornerShape(8.dp)
                    )
                    .background(
                        if (!currentPickModeIsPin) primaryButtonColor.copy(0.25f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) { Icon(Icons.Default.NearMe, contentDescription = "My location") }

            Row(
                modifier = Modifier
                    .border(2.dp, Color(0xFFC9A2C8), RoundedCornerShape(8.dp)) // purple
                    .clip(RoundedCornerShape(8.dp))
                    .height(36.dp)
            ) {
                TextButton(
                    modifier = Modifier.height(36.dp),
                    onClick = onPinDrop,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (currentPickModeIsPin) Color(0xFFC9A2C8).copy(0.25f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        Icons.Default.PinDrop,
                        contentDescription = "Pin",
                        tint = if (darkMode) Color.White else Color.Black
                    )
                }
                Divider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )
                TextButton(
                    onClick = onCenterPin,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = "Center",
                        tint = if (darkMode) Color.White else Color.Black
                    )
                }
            }
        }

        if (serverMs != null) {
            Text(
                text = "${serverMs} ms",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Sort toggle (A–Z | Distance)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
        ) {
            TextButton(
                onClick = { onSortChange(SortMode.ALPHA) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (sortMode == SortMode.ALPHA) MaterialTheme.colorScheme.primary.copy(
                        0.3f
                    ) else Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Icon(Icons.Default.SortByAlpha, contentDescription = "Sort by Alpha") }

            TextButton(
                onClick = { onSortChange(SortMode.DISTANCE) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (sortMode == SortMode.DISTANCE) MaterialTheme.colorScheme.primary.copy(
                        0.3f
                    ) else Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Icon(Icons.Default.Straighten, contentDescription = "Sort by Distance") }
        }
    }
}

@Composable
private fun FilterRow(
    filters: Filters,
    onChange: (Filters) -> Unit
) {
    //Spacer(Modifier.height(2.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            stringResource(R.string.heading_intercity_rail),
            filters.rail
        ) { onChange(filters.copy(rail = !filters.rail)) }
        FilterChip(
            stringResource(R.string.heading_local_rail),
            filters.metro
        ) { onChange(filters.copy(metro = !filters.metro)) }
        FilterChip(
            stringResource(R.string.heading_bus),
            filters.bus
        ) { onChange(filters.copy(bus = !filters.bus)) }
        FilterChip(stringResource(R.string.heading_other), filters.other) {
            onChange(
                filters.copy(
                    other = !filters.other
                )
            )
        }
    }
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = if (on) 4.dp else 16.dp,
        border = if (on) ButtonDefaults.outlinedButtonBorder else null,
        color = if (on) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RouteGroupCard(
    route: RouteGroup,
    stopsTable: Map<String, Map<String, StopEntry>>,
    darkMode: Boolean,
    onTripClick: (TripClickResponse) -> Unit,
    onRouteClick: (chateauId: String, routeId: String) -> Unit
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (darkMode) 0.5f else 1f)
    val textCol = normalizeHex(route.textColor) ?: MaterialTheme.colorScheme.onSurface
    val lineCol = normalizeHex(route.color) ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.clickable { onRouteClick(route.chateauId, route.routeId) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp) // space between texts
        ) {
            Text(
                text = route.shortName?.trim().orEmpty(),
                color = if (darkMode) lightenColour(lineCol) else darkenColour(
                    lineCol,
                    minContrast = 2.0
                ),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = TextDecoration.Underline
            )

            Text(
                text = route.longName?.trim().orEmpty(),
                color = if (darkMode) lightenColour(lineCol) else darkenColour(
                    lineCol,
                    minContrast = 2.0
                ),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = TextDecoration.Underline
            )
        }

        // Directions
        val flatDirections = remember(route.directions) { flattenDirections(route.directions) }

        flatDirections.entries
            .sortedBy { it.value.headsign }
            .forEach { (_, dir) ->
                val visibleTrips = dir.trips.filter { t ->
                    val dep = t.departureRealtime ?: t.departureSchedule ?: 0L
                    val now = System.currentTimeMillis() / 1000
                    dep in (now - 600)..(now + 64800)
                }

                // println("How many trips are visible ${dir.trips.count()} -> ${visibleTrips.count()}")

                if (visibleTrips.isEmpty()) return@forEach

                Spacer(Modifier.height(4.dp))
                Text(
                    "› ${dir.headsign}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = visibleTrips,
                        // Providing a stable key is crucial for performance and state preservation
                        key = { it.tripId + it.startDay + it.startTime }
                    ) { trip ->
                        TripPill(
                            trip = trip,
                            chateauId = route.chateauId,
                            routeId = route.routeId,
                            stopsTable = stopsTable,
                            lineCol = lineCol,
                            textCol = textCol,
                            onTripClick = onTripClick
                        )
                    }
                }
            }
    }
}

@Composable
private fun TripPill(
    trip: TripEntry,
    chateauId: String,
    routeId: String,
    stopsTable: Map<String, Map<String, StopEntry>>,
    lineCol: Color,
    textCol: Color,
    onTripClick: (TripClickResponse) -> Unit
) {
    val nowSec = System.currentTimeMillis() / 1000
    val dep = trip.departureRealtime ?: trip.departureSchedule ?: 0L
    val secondsLeft = dep - nowSec
    val stop = stopsTable[chateauId]?.get(trip.stopId)
    val tz = stop?.timezone ?: trip.tz
    val isPast = secondsLeft < 0

    val contentAlpha = if (isPast) {
        0.7f
    } else 1.0f

    val hasRealtime = trip.departureRealtime != null
    val baseColor = if (hasRealtime) Color(0x00AB9B) else LocalContentColor.current

    val contentColor = baseColor.copy(alpha = contentAlpha)


    Surface(
        onClick = {

            onTripClick(
                TripClickResponse(
                    tripId = trip.tripId,
                    stopId = trip.stopId,
                    chateauId = chateauId,
                    routeId = routeId,
                    startDay = if (trip.startDay != null) trip.startDay.replace("-", "") else null
                )
            )

        },
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 76.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-4).dp)
        ) {
            if (trip.tripShortName != null && trip.tripShortName.isNotBlank()) {
                Text(
                    trip.tripShortName,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(lineCol)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = textCol,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }

            // “in 5 min” / “now”
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row() {
                    DiffTimer(
                        diff = secondsLeft.toDouble(),
                        showBrackets = false,
                        showSeconds = false,
                        showDays = false,
                        showPlus = false,
                        numSize = 16.sp,
                        unitSize = 14.sp,
                        numberFontWeight = FontWeight.Medium,
                        unitFontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = contentColor.alpha)
                    )

                    if (hasRealtime) {
                        Icon(
                            Icons.Default.RssFeed,
                            modifier = Modifier.size(10.dp),
                            contentDescription = "Realtime",
                            tint = contentColor.copy(alpha = contentColor.alpha)
                        )
                    }
                }


            }

            // Wall-clock time
            if (tz != null) {
                FormattedTimeText(
                    timezone = tz,
                    timeSeconds = dep,
                    showSeconds = false
                )
            }

            if (trip.cancelled == true) {
                Text(stringResource(R.string.cancelled), color = Color.Red, style = MaterialTheme.typography.labelSmall)
            } else if (trip.deleted == true) {
                Text(stringResource(R.string.deleted), color = Color.Red, style = MaterialTheme.typography.labelSmall)
            }

            // If both schedule & realtime are available, show +/- delay
            val sched = trip.departureSchedule
            val rt = trip.departureRealtime
            if (sched != null && rt != null) {
                val diff = rt - sched // seconds
                if (diff != 0L) {
                    DelayDiff(diff = diff,
                        fontSizeOfPolarity = 12.sp
                    )
                }
            }

            // Platform/Track
            trip.platform?.let {
                Text(
                    "${stringResource(R.string.platform)} ${
                        it.replace("Track", "").trim()
                    }",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/* ---------------------------- Distance helper ----------------------------- */

private fun tryDistanceForRouteGroup(
    rg: RouteGroup,
    origin: Pair<Double, Double>,
    stopsTable: Map<String, Map<String, StopEntry>>
): Double {
    val (olat, olon) = origin
    var best = Double.POSITIVE_INFINITY

    // Flatten before iterating
    val flat = flattenDirections(rg.directions)

    // println("for route group ${rg.routeId}")
    for (dir in flat.values) {
        //println("for direction, ${dir.headsign} ${dir.trips.count()}")
        val first = dir.trips.firstOrNull() ?: continue
        val s = stopsTable[rg.chateauId]?.get(first.stopId) ?: continue
        val lat = s.lat ?: s.latitude
        val lon = s.lon ?: s.longitude
        if (lat != null && lon != null) {
            val d = haversineMeters(olat, olon, lat, lon)
            if (d < best) best = d
        }
    }

    //println("Distance is ${best}m")
    return best
}
