package com.catenarymaps.catenary

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.Integer.min
import java.util.Locale
import kotlin.math.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Straighten

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
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
private data class TripEntry(
    @SerialName("stop_id") val stopId: String,
    @SerialName("trip_id") val tripId: String,
    val tz: String? = null,
    val platform: String? = null,
    val cancelled: Boolean? = null,
    val deleted: Boolean? = null,
    @SerialName("trip_short_name") val tripShortName: String? = null,
    @SerialName("departure_realtime") val departureRealtime: Long? = null,
    @SerialName("departure_schedule") val departureSchedule: Long? = null
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
        val sortedTrips = trips.sortedBy { t ->
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

private fun logJsonTopLevelShape(raw: String) {
    try {
        val elem: JsonElement = json.parseToJsonElement(raw)
        when (elem) {
            is JsonObject -> {
                val keys = elem.keys.joinToString(", ")
                Log.d(NEARBY_TAG, "Top-level keys: [$keys]")
                // Log a couple of nested shapes if present
                elem["debug"]?.let { Log.d(NEARBY_TAG, "debug: ${it}") }
                elem["departures"]?.let { Log.d(NEARBY_TAG, "departures: ${it::class.simpleName}") }
                elem["stop"]?.let { Log.d(NEARBY_TAG, "stop: ${it::class.simpleName}") }

                elem["departures"]?.let {
                    val depsEl = elem["departures"]
                    if (depsEl is JsonArray && depsEl.isNotEmpty()) {
                        val firstRouteEl = depsEl.first()
                        if (firstRouteEl is JsonObject) {
                            val directionsEl = firstRouteEl["directions"]
                            Log.d(
                                NEARBY_TAG,
                                "first departures[0].directions type = ${directionsEl?.let { it::class.simpleName }}"
                            )

                            if (directionsEl is JsonObject) {
                                val firstDirEntry = directionsEl.entries.firstOrNull()
                                if (firstDirEntry == null) {
                                    Log.w(NEARBY_TAG, "directions is empty")
                                } else {
                                    val (dirKey, dirVal) = firstDirEntry
                                    when (dirVal) {
                                        is JsonObject -> {
                                            // Try decoding as your DirectionGroup {headsign, direction_id, trips}
                                            runCatching {
                                                json.decodeFromJsonElement(
                                                    DirectionGroup.serializer(),
                                                    dirVal
                                                )
                                            }.onSuccess { dg ->
                                                Log.d(
                                                    NEARBY_TAG,
                                                    "Decoded DirectionGroup OK (key=$dirKey): headsign='${dg.headsign}',  trips=${dg.trips.size}"
                                                )
                                            }.onFailure { e ->
                                                Log.e(
                                                    NEARBY_TAG,
                                                    "Failed to decode DirectionGroup (key=$dirKey) as object: ${e.message}"
                                                )
                                                // Optional: log a small preview
                                                Log.e(
                                                    NEARBY_TAG,
                                                    "dirVal preview: ${dirVal.toString().take(600)}"
                                                )
                                            }
                                        }

                                        is JsonArray -> {
                                            // Legacy shape: array of trips. Decode trips to confirm.
                                            runCatching {
                                                json.decodeFromJsonElement(
                                                    ListSerializer(TripEntry.serializer()),
                                                    dirVal
                                                )
                                            }.onSuccess { trips ->
                                                Log.d(
                                                    NEARBY_TAG,
                                                    "directions[$dirKey] is an ARRAY of trips (legacy). trips=${trips.size}"
                                                )
                                                // If you want, synthesize a DirectionGroup-like view for downstream:
                                                // val synthetic = DirectionGroup(headsign = dirKey, trips = trips, directionId = dirKey)
                                            }.onFailure { e ->
                                                Log.e(
                                                    NEARBY_TAG,
                                                    "Failed to decode trips array at directions[$dirKey]: ${e.message}"
                                                )
                                                Log.e(
                                                    NEARBY_TAG,
                                                    "dirVal preview: ${dirVal.toString().take(600)}"
                                                )
                                            }
                                        }

                                        else -> {
                                            Log.w(
                                                NEARBY_TAG,
                                                "directions[$dirKey] unexpected JSON type: ${dirVal::class.simpleName}"
                                            )
                                            Log.w(
                                                NEARBY_TAG,
                                                "dirVal preview: ${dirVal.toString().take(600)}"
                                            )
                                        }
                                    }
                                }
                            } else {
                                Log.w(
                                    NEARBY_TAG,
                                    "departures[0].directions is not an object: ${directionsEl?.let { it::class.simpleName }}"
                                )
                            }
                        } else {
                            Log.w(
                                NEARBY_TAG,
                                "departures[0] is not an object: ${firstRouteEl::class.simpleName}"
                            )
                        }
                    } else {
                        Log.w(
                            NEARBY_TAG,
                            "departures is not a non-empty array (type=${depsEl?.let { it::class.simpleName }})"
                        )
                    }
                }
            }

            else -> Log.d(NEARBY_TAG, "Top-level is not an object: ${elem::class.simpleName}")
        }
    } catch (e: Throwable) {
        Log.e(NEARBY_TAG, "parseToJsonElement failed: ${e.message}")
    }
}

private fun logSerializationError(e: SerializationException, raw: String) {
    // kotlinx.serialization exceptions usually include a path like at path: $.foo[0].bar
    Log.e(NEARBY_TAG, "JSON decode failed: ${e.message}")
    // Log first 2k chars so we don’t spam Logcat
    val head = raw.take(2000)
    Log.e(NEARBY_TAG, "Body head (2k):\n$head")
    // Try to log the top level shape to see where we differ from NearbyResponse
    logJsonTopLevelShape(raw)
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
                logJsonTopLevelShape(rawBody)

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
    onCenterPin: () -> Unit = {}
) {
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

    val sorted = remember(filtered, sortMode, lockedOrigin, stopsTable) {
        when (sortMode) {
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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(bottom = 20.dp)
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
                    .padding(top = 8.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }

        FilterRow(filters = filters, onChange = { filters = it })

        val listScroll = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(listScroll)
            ) {
                if (sorted.isEmpty() && firstLoadComplete && !loading) {
                    Spacer(Modifier.height(16.dp))
                    Text("No departures.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    sorted.forEach { route ->
                        RouteGroupCard(route, stopsTable, darkMode)
                    }
                }
                Spacer(Modifier.height(64.dp))
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onMyLocation,
                modifier = Modifier
                    .size(36.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    .background(
                        if (!currentPickModeIsPin) MaterialTheme.colorScheme.primary.copy(0.2f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) { Icon(Icons.Default.NearMe, contentDescription = "My location") }

            Row(
                modifier = Modifier
                    .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .height(36.dp)
            ) {
                TextButton(
                    onClick = onPinDrop,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (currentPickModeIsPin) MaterialTheme.colorScheme.secondary.copy(
                            0.25f
                        ) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Icon(Icons.Default.PinDrop, contentDescription = "Pin") }
                Divider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )
                TextButton(
                    onClick = onCenterPin,
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Icon(Icons.Default.CenterFocusStrong, contentDescription = "Center") }
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
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Icon(Icons.Default.SortByAlpha, contentDescription = "Sort by Alpha") }

            TextButton(
                onClick = { onSortChange(SortMode.DISTANCE) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (sortMode == SortMode.DISTANCE) MaterialTheme.colorScheme.primary.copy(
                        0.3f
                    ) else Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Icon(Icons.Default.Straighten, contentDescription = "Sort by Distance") }
        }
    }
}

@Composable
private fun FilterRow(
    filters: Filters,
    onChange: (Filters) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip("Intercity Rail", filters.rail) { onChange(filters.copy(rail = !filters.rail)) }
        FilterChip("Local Rail", filters.metro) { onChange(filters.copy(metro = !filters.metro)) }
        FilterChip("Bus", filters.bus) { onChange(filters.copy(bus = !filters.bus)) }
        FilterChip("Other", filters.other) { onChange(filters.copy(other = !filters.other)) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = if (on) 3.dp else 0.dp,
        border = if (on) null else ButtonDefaults.outlinedButtonBorder
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
    darkMode: Boolean
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkMode) 0.5f else 1f)
    val textCol = normalizeHex(route.textColor) ?: MaterialTheme.colorScheme.onSurface
    val lineCol = normalizeHex(route.color) ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp) // space between texts
        ) {
            Text(
                text = route.shortName?.trim().orEmpty(),
                color = if (darkMode) lightenColour(lineCol) else darkenColour(lineCol),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = route.longName?.trim().orEmpty(),
                color = if (darkMode) lightenColour(lineCol) else darkenColour(lineCol),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    visibleTrips.forEach { trip ->
                        TripPill(
                            trip = trip,
                            chateauId = route.chateauId,
                            stopsTable = stopsTable,
                            lineCol = lineCol,
                            textCol = textCol
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
    stopsTable: Map<String, Map<String, StopEntry>>,
    lineCol: Color,
    textCol: Color
) {
    val nowSec = System.currentTimeMillis() / 1000
    val dep = trip.departureRealtime ?: trip.departureSchedule ?: 0L
    val secondsLeft = dep - nowSec
    val stop = stopsTable[chateauId]?.get(trip.stopId)
    val tz = stop?.timezone ?: trip.tz

    Surface(
        onClick = { /* TODO push SingleTrip(...) later */ },
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 76.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (trip.tripShortName != null && trip.tripShortName.isNotBlank()) {
                Text(
                    trip.tripShortName,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(lineCol.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = textCol,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }

            // “in 5 min” / “now”
            DiffTimer(
                diff = secondsLeft.toDouble(),
                showBrackets = false,
                showSeconds = false,
                large = false,
                showDays = false,
                showPlus = false
            )

            // Wall-clock time
            if (tz != null) {
                FormattedTimeText(
                    timezone = tz,
                    timeSeconds = dep,
                    showSeconds = false
                )
            }

            if (trip.cancelled == true) {
                Text("Cancelled", color = Color.Red, style = MaterialTheme.typography.labelSmall)
            } else if (trip.deleted == true) {
                Text("Deleted", color = Color.Red, style = MaterialTheme.typography.labelSmall)
            }

            // If both schedule & realtime are available, show +/- delay
            val sched = trip.departureSchedule
            val rt = trip.departureRealtime
            if (sched != null && rt != null) {
                val diff = rt - sched // seconds
                if (diff != 0L) {
                    val late = diff > 0
                    val mins = abs(diff) / 60
                    Text(
                        (if (late) "+" else "−") + "${mins}m",
                        color = if (late) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Platform/Track
            trip.platform?.let {
                Text(
                    "Plt ${it.replace("Track", "").trim()}",
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

