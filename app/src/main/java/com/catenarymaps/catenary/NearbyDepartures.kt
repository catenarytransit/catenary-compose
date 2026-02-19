package com.catenarymaps.catenary

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import java.lang.Integer.min
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

/* -------------------------------------------------------------------------- */
/*  Data models that match the JSON payload (only the fields we actually use)  */
/* -------------------------------------------------------------------------- */
@Serializable
private data class NearbyResponseV3(
        @SerialName("long_distance") val longDistance: List<StationDepartureGroup> = emptyList(),
        val local: List<RouteGroupV3> = emptyList(),
        val routes: Map<String, Map<String, RouteInfoExport>> = emptyMap(),
        val stops: Map<String, Map<String, StopOutputV3>> = emptyMap(),
        val debug: DebugInfo? = null
)

@Serializable
private data class StationDepartureGroup(
        @SerialName("station_name") val stationName: String,
        @SerialName("osm_station_id") val osmStationId: Long? = null,
        @SerialName("distance_m") val distanceM: Double,
        val departures: List<DepartureItemV3>,
        val lat: Double,
        val lon: Double,
        val timezone: String
)

@Serializable
private data class DepartureItemV3(
        @SerialName("scheduled_departure") val scheduledDeparture: Long? = null,
        @SerialName("realtime_departure") val realtimeDeparture: Long? = null,
        @SerialName("scheduled_arrival") val scheduledArrival: Long? = null,
        @SerialName("realtime_arrival") val realtimeArrival: Long? = null,
        @SerialName("service_date") val serviceDate: String,
        val headsign: String,
        val platform: String? = null,
        @SerialName("trip_id") val tripId: String,
        @SerialName("trip_short_name") val tripShortName: String? = null,
        @SerialName("route_id") val routeId: String,
        @SerialName("stop_id") val stopId: String,
        val cancelled: Boolean,
        val delayed: Boolean,
        @SerialName("chateau_id") val chateauId: String,
        @SerialName("last_stop") val lastStop: Boolean
)

@Serializable
data class RouteGroupV3(
        @SerialName("chateau_id") val chateauId: String,
        @SerialName("route_id") val routeId: String,
        val color: String? = null,
        @SerialName("text_color") val textColor: String? = null,
        @SerialName("short_name") val shortName: String? = null,
        @SerialName("long_name") val longName: String? = null,
        @SerialName("route_type") val routeType: Int,
        @SerialName("agency_name") val agencyName: String? = null,
        val headsigns: Map<String, List<LocalDepartureItem>> = emptyMap(),
        @SerialName("closest_distance") val closestDistance: Double
)

@Serializable
data class LocalDepartureItem(
        @SerialName("trip_id") val tripId: String,
        @SerialName("departure_schedule") val departureSchedule: Long? = null,
        @SerialName("departure_realtime") val departureRealtime: Long? = null,
        @SerialName("arrival_schedule") val arrivalSchedule: Long? = null,
        @SerialName("arrival_realtime") val arrivalRealtime: Long? = null,
        @SerialName("stop_id") val stopId: String,
        @SerialName("stop_name") val stopName: String? = null,
        val cancelled: Boolean,
        val platform: String? = null,
        @SerialName("last_stop") val lastStop: Boolean,
        @SerialName("service_date") val serviceDate: String
)

@Serializable
data class RouteInfoExport(
        @SerialName("short_name") val shortName: String? = null,
        @SerialName("long_name") val longName: String? = null,
        @SerialName("agency_name") val agencyName: String? = null,
        val color: String? = null,
        @SerialName("text_color") val textColor: String? = null,
        @SerialName("route_type") val routeType: Int
)

@Serializable
data class StopOutputV3(
        @SerialName("gtfs_id") val gtfsId: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        @SerialName("osm_station_id") val osmStationId: Long? = null,
        val timezone: String
)

@Serializable
private data class DebugInfo(@SerialName("total_time_ms") val totalTimeMs: Long? = null)

private sealed interface NearbyItem {
        val sortDistance: Double

        // We KEEP station groups grouped for the card UI.
        data class StationGroupItem(
                val group: StationDepartureGroup,
                override val sortDistance: Double
        ) : NearbyItem

        data class RouteGroupItem(
                val group: RouteGroupV3,
                override val sortDistance: Double,
                val stopsMap: Map<String, StopOutputV3>
        ) : NearbyItem
}

data class TripClickResponse(
        val tripId: String? = null,
        val stopId: String? = null,
        val chateauId: String? = null,
        val routeId: String? = null,
        val routeType: Int? = null,
        val startDay: String? = null
)

/* -------------------------------- Utilities ------------------------------- */

private enum class SortMode {
        ALPHA,
        DISTANCE
}

private data class Filters(
        val rail: Boolean = true,
        val metro: Boolean = true,
        val bus: Boolean = true,
        val other: Boolean = true
)

private object FilterPreferences {
        private const val PREFS_NAME = "catenary_filter_prefs"
        private const val KEY_RAIL = "filter_rail"
        private const val KEY_METRO = "filter_metro"
        private const val KEY_BUS = "filter_bus"
        private const val KEY_OTHER = "filter_other"

        fun saveFilters(context: Context, filters: Filters) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                with(prefs.edit()) {
                        putBoolean(KEY_RAIL, filters.rail)
                        putBoolean(KEY_METRO, filters.metro)
                        putBoolean(KEY_BUS, filters.bus)
                        putBoolean(KEY_OTHER, filters.other)
                        apply()
                }
        }

        fun loadFilters(context: Context): Filters {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                return Filters(
                        rail = prefs.getBoolean(KEY_RAIL, true),
                        metro = prefs.getBoolean(KEY_METRO, true),
                        bus = prefs.getBoolean(KEY_BUS, true),
                        other = prefs.getBoolean(KEY_OTHER, true)
                )
        }
}

// Rough mapping mirroring the JS filter buckets
private fun isAllowedByFilter(routeType: Int, f: Filters): Boolean =
        when (routeType) {
                3, 11, 700 -> f.bus
                0, 1, 5, 7, 12, 900 -> f.metro
                2, 106, 107, 101, 100, 102, 103 -> f.rail
                4 -> f.other // ferries
                else -> true
        }

// Helper functions removed (flattenDirections, haversineMeters, normalizeHex kept if needed)
private fun normalizeHex(c: String?): Color? {
        if (c.isNullOrBlank()) return null
        val s = if (c.startsWith("#")) c.drop(1) else c
        return runCatching {
                        val v = s.toLong(16).toInt()
                        val r = (v shr 16) and 0xFF
                        val g = (v shr 8) and 0xFF
                        val b = v and 0xFF
                        Color(r, g, b)
                }
                .getOrNull()
}

/* ------------------------------ Networking -------------------------------- */

private val json = Json {
        ignoreUnknownKeys = true
        // isLenient = true
        explicitNulls = true
}

private val httpClient by lazy { OkHttpClient() }

private val nearbyClient: OkHttpClient by lazy {
        httpClient
                .newBuilder()
                .callTimeout(45, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
}

private const val NEARBY_TAG = "NearbyDebug"

private fun peekString(body: ResponseBody?, limit: Long = 200_000): String {
        if (body == null) return "<null body>"
        return try {
                val peeked =
                        body.source()
                                .peek()
                                .readByteArray(
                                        minOf(
                                                body.contentLength().takeIf { it >= 0 } ?: limit,
                                                limit
                                        )
                                )
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

private suspend fun fetchNearby(lat: Double, lon: Double): NearbyResponseV3? =
        withContext(Dispatchers.IO) {
                Log.d(
                        NEARBY_TAG,
                        "Fetch nearby departures lat=$lat lon=$lon (thread=${Thread.currentThread().name})"
                )

                val url =
                        "https://birch.catenarymaps.org/nearbydeparturesfromcoordsv3?lat=$lat&lon=$lon&limit_per_station=10&limit_per_headsign=20"
                val req = Request.Builder().url(url).get().build()

                try {
                        nearbyClient.newCall(req).execute().use { resp ->
                                Log.d(NEARBY_TAG, "HTTP ${resp.code} ${resp.message}")

                                if (!resp.isSuccessful) {
                                        val preview = peekString(resp.body, limit = 4000)
                                        Log.e(
                                                NEARBY_TAG,
                                                "Unsuccessful (${resp.code}). Body preview:\n$preview"
                                        )
                                        return@withContext null
                                }

                                val rawBody = resp.body?.string()
                                if (rawBody.isNullOrEmpty()) {
                                        Log.e(NEARBY_TAG, "Empty body")
                                        return@withContext null
                                }

                                if (rawBody.trimStart().startsWith("<")) {
                                        Log.e(
                                                NEARBY_TAG,
                                                "Body looks like HTML (unexpected):\n${
                                                        rawBody.take(
                                                                1000
                                                        )
                                                }"
                                        )
                                        return@withContext null
                                }

                                Log.d(NEARBY_TAG, "Body length: ${rawBody.length} chars")

                                withContext(Dispatchers.Default) {
                                        try {
                                                val parsed =
                                                        json.decodeFromString(
                                                                NearbyResponseV3.serializer(),
                                                                rawBody
                                                        )
                                                Log.d(
                                                        NEARBY_TAG,
                                                        "Decoded OK: local=${parsed.local.size}, long_distance=${parsed.longDistance.size}, serverMs=${parsed.debug?.totalTimeMs}"
                                                )
                                                parsed
                                        } catch (se: SerializationException) {
                                                logSerializationError(se, rawBody)
                                                null
                                        }
                                }
                        }
                } catch (t: Throwable) {
                        Log.e(
                                NEARBY_TAG,
                                "Network error on ${Thread.currentThread().name}: ${t.message}",
                                t
                        )
                        null
                }
        }

/* ----------------------------- Public UI API ------------------------------ */
/**
 * NearbyDepartures screen to place in your home bottom sheet.
 *
 * @param userLocation current GPS coordinate, or null while waiting
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
        onRouteClick: (chateauId: String, routeId: String) -> Unit = { _, _ -> },
        onStopClick: (chateauId: String, stopId: String) -> Unit = { _, _ -> }
) {
        val applicationContext = LocalContext.current.applicationContext
        LaunchedEffect(Unit) {
                try {
                        val firebaseAnalytics = FirebaseAnalytics.getInstance(applicationContext)
                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                                param(FirebaseAnalytics.Param.SCREEN_NAME, "NearbyDeparturesScreen")
                                // param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
                        }
                } catch (e: Exception) {
                        // Log the error or handle it gracefully
                        android.util.Log.e("GA", "Failed to log screen view", e)
                }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        var isAppInForeground by remember { mutableStateOf(true) }

        DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                                Lifecycle.Event.ON_START -> isAppInForeground = true
                                Lifecycle.Event.ON_STOP -> isAppInForeground = false
                                else -> {}
                        }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }

        LaunchedEffect(Unit) {
                while (true) {
                        nowSec = System.currentTimeMillis() / 1000
                        delay(1_000)
                }
        }

        val context = LocalContext.current
        var filters by remember { mutableStateOf(FilterPreferences.loadFilters(context)) }

        LaunchedEffect(filters) { FilterPreferences.saveFilters(context, filters) }

        var sortMode by remember { mutableStateOf(SortMode.DISTANCE) }
        var loading by remember { mutableStateOf(false) }
        var firstAttemptSent by remember { mutableStateOf(false) }
        var firstLoadComplete by remember { mutableStateOf(false) }
        var serverMs by remember { mutableStateOf<Long?>(null) }

        var stopsTableLongDistance by remember {
                mutableStateOf<List<StationDepartureGroup>>(emptyList())
        }
        var departureList by remember { mutableStateOf<List<RouteGroupV3>>(emptyList()) }
        var routesMap by remember {
                mutableStateOf<Map<String, Map<String, RouteInfoExport>>>(emptyMap())
        }
        var stopsMap by remember {
                mutableStateOf<Map<String, Map<String, StopOutputV3>>>(emptyMap())
        }

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
                        // Switching back to GPS: keep current lock unless the user explicitly
                        // presses My
                        // Location.
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
        LaunchedEffect(pollSession, isAppInForeground) {
                val origin = lockedOrigin ?: return@LaunchedEffect
                if (isAppInForeground) {
                        // Function to perform the actual fetch
                        suspend fun doFetch() {
                                loading = true
                                firstAttemptSent = true
                                println("Fetching nearby departures for $origin")
                                val res = fetchNearby(origin.first, origin.second)

                                if (res != null) {
                                        stopsTableLongDistance = res.longDistance
                                        departureList = res.local
                                        routesMap = res.routes
                                        stopsMap = res.stops
                                        serverMs = res.debug?.totalTimeMs
                                        println("nearby took ${res.debug?.totalTimeMs}")
                                } else {
                                        println("nearby returned with nothing")
                                }
                                loading = false
                                firstLoadComplete = true
                        }

                        // 1. Initial fetch immediately
                        doFetch()

                        // 2. Loop every 10s
                        while (isActive) {
                                delay(10_000)
                                doFetch()
                        }
                }
        }

        // derived + sorting
        // derived + sorting
        val filteredLocal =
                remember(departureList, filters) {
                        departureList.filter { rg ->
                                rg.headsigns.isNotEmpty() &&
                                        isAllowedByFilter(rg.routeType, filters)
                        }
                }

        val filteredStations =
                remember(stopsTableLongDistance, filters) {
                        stopsTableLongDistance.filter { group ->
                                // Filter out groups with no relevant departures if rail filter is
                                // off?
                                // But usually LD is rail.
                                // If rail filter is off, do we hide LD? Current logic says yes.
                                filters.rail && group.departures.isNotEmpty()
                        }
                }

        var sorted by remember { mutableStateOf<List<NearbyItem>>(emptyList()) }

        LaunchedEffect(filteredLocal, filteredStations, sortMode, lockedOrigin) {
                val origin = lockedOrigin
                withContext(Dispatchers.Default) {
                        val localItems =
                                filteredLocal.map { rg ->
                                        NearbyItem.RouteGroupItem(
                                                group = rg,
                                                sortDistance = rg.closestDistance,
                                                stopsMap =
                                                        stopsMap.getOrElse(rg.chateauId) {
                                                                emptyMap()
                                                        }
                                        )
                                }

                        val stationItems =
                                filteredStations
                                        .map { group ->
                                                NearbyItem.StationGroupItem(
                                                        group = group,
                                                        sortDistance = group.distanceM
                                                )
                                        }
                                        .sortedBy { it.sortDistance }

                        // Logic mirroring Svelte:
                        // 1. Closest station (if any) at top
                        // 2. Remaining stations mixed with local routes
                        // 3. Mixed list sorted by Pin (TODO: check pin logic) > Alpha/Distance

                        val finalList = mutableListOf<NearbyItem>()
                        val mixedItems = mutableListOf<NearbyItem>()

                        // Check if the closest station is within the 1000m "hoist" threshold
                        val shouldHoist =
                                stationItems.isNotEmpty() && stationItems.first().sortDistance < 1000.0

                        if (shouldHoist) {
                                // Hoist the closest station to the very top
                                finalList.add(stationItems.first())
                                // Add the rest of the stations to the pool to be mixed with local routes
                                mixedItems.addAll(stationItems.drop(1))
                        } else {
                                // If no station is close enough, treat all stations as regular items in the mixed list
                                mixedItems.addAll(stationItems)
                        }

                        mixedItems.addAll(localItems)

                        // Sort mixed
                        val sortedMixed =
                                when (sortMode) {
                                        SortMode.ALPHA ->
                                                mixedItems.sortedWith(
                                                        compareBy { item ->
                                                                val name =
                                                                        when (item) {
                                                                                is NearbyItem.RouteGroupItem ->
                                                                                        (item.group
                                                                                                .shortName
                                                                                                ?: item.group
                                                                                                        .longName
                                                                                                        ?: "")
                                                                                is NearbyItem.StationGroupItem ->
                                                                                        item.group
                                                                                                .stationName
                                                                        }
                                                                name.lowercase(Locale.UK)
                                                        }
                                                )
                                        SortMode.DISTANCE -> mixedItems.sortedBy { it.sortDistance }
                                }

                        finalList.addAll(sortedMixed)

                        sorted =
                                finalList.distinctBy {
                                        when (it) {
                                                is NearbyItem.RouteGroupItem ->
                                                        "R:${it.group.chateauId}:${it.group.routeId}"
                                                is NearbyItem.StationGroupItem ->
                                                        "S:${it.group.stationName}:${it.group.distanceM}" // Unique
                                        // enough for
                                        // UI
                                        }
                                }
                }
        }
        Column(
                modifier =
                        Modifier
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
                                // Parent will update pickedLocation; our
                                // LaunchedEffect(pickedLocation)
                                // will
                                // handle restart.
                        },
                        onCenterPin = {
                                onCenterPin()
                                // No-op here; movement will be observed by pickedLocation change if
                                // it
                                // happens.
                        },
                        serverMs = serverMs,
                        sortMode = sortMode,
                        onSortChange = { sortMode = it },
                        darkMode = darkMode
                )

                if (!firstAttemptSent && !usePickedLocation) {
                        Text(
                                text = stringResource(id = R.string.waiting_for_gps),
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
                        // Spacer(Modifier.height(8.dp))
                }

                FilterRow(filters = filters, onChange = { filters = it })

                Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                                modifier =
                                        Modifier
                                                .fillMaxSize()
                                                .windowInsetsPadding(
                                                        WindowInsets(
                                                                bottom =
                                                                        WindowInsets.safeDrawing
                                                                                .getBottom(
                                                                                        density =
                                                                                                LocalDensity
                                                                                                        .current
                                                                                )
                                                        )
                                                ),
                        ) {
                                if (sorted.isEmpty() && firstLoadComplete && !loading) {
                                        item {
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .nearby_departures_no_departures
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        }
                                }
                                items(
                                        sorted,
                                        key = {
                                                when (it) {
                                                        is NearbyItem.RouteGroupItem ->
                                                                "R:${it.group.chateauId}:${it.group.routeId}"
                                                        is NearbyItem.StationGroupItem ->
                                                                "S:${it.group.stationName}:${it.group.lat}"
                                                }
                                        }
                                ) { item ->
                                        when (item) {
                                                is NearbyItem.RouteGroupItem -> {
                                                        RouteGroupCard(
                                                                item.group,
                                                                item.stopsMap,
                                                                darkMode,
                                                                onTripClick,
                                                                onRouteClick,
                                                                onStopClick,
                                                                nowSec = nowSec
                                                        )
                                                }
                                                is NearbyItem.StationGroupItem -> {
                                                        StationGroupCard(
                                                                group = item.group,
                                                                routesMap = routesMap,
                                                                nowSec = nowSec,
                                                                onTripClick = onTripClick,
                                                                onStopClick = onStopClick,
                                                                darkMode = darkMode
                                                        )
                                                }
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun StationGroupCard(
        group: StationDepartureGroup,
        routesMap: Map<String, Map<String, RouteInfoExport>>,
        nowSec: Long,
        onTripClick: (TripClickResponse) -> Unit,
        onStopClick: (chateauId: String, stopId: String) -> Unit,
        darkMode: Boolean
) {
        Card(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                                alpha = if (darkMode) 0.5f else 1f
                                        )
                        ),
                border =
                        androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                        ),
                shape = RoundedCornerShape(8.dp)
        ) {
                Column(Modifier.padding(8.dp)) {
                        // Header
                        Row(
                                modifier =
                                        Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Column {
                                        Text(
                                                text = group.stationName,
                                                style =
                                                        MaterialTheme.typography.titleMedium.copy(
                                                                fontWeight = FontWeight.Bold
                                                        ),
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                                text = "${group.distanceM.toInt()}m",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                        // Departures
                        // Filter out Last Stop and limit to 10
                        val departures = group.departures.filter { !it.lastStop }.take(10)

                        Column {
                                departures.forEachIndexed { index, dep ->
                                        val routeInfo = routesMap[dep.chateauId]?.get(dep.routeId)
                                        // Construct StopEvent
                                        // Logic mirroring Svelte
                                        // getEffectiveStationRealtimeDeparture
                                        val effectiveRealtimeDeparture =
                                                dep.realtimeDeparture
                                                        ?: if (dep.realtimeArrival != null &&
                                                                        dep.scheduledDeparture !=
                                                                                null &&
                                                                        dep.realtimeArrival >
                                                                                dep.scheduledDeparture
                                                        ) {
                                                                dep.realtimeArrival
                                                        } else {
                                                                null
                                                        }

                                        // Construct StopEvent
                                        val event =
                                                StopEvent(
                                                        chateau = dep.chateauId,
                                                        trip_id = dep.tripId,
                                                        route_id = dep.routeId,
                                                        service_date = dep.serviceDate,
                                                        headsign = dep.headsign,
                                                        stop_id = dep.stopId,
                                                        scheduled_departure =
                                                                dep.scheduledDeparture,
                                                        realtime_departure =
                                                                effectiveRealtimeDeparture,
                                                        scheduled_arrival = dep.scheduledArrival,
                                                        realtime_arrival = dep.realtimeArrival,
                                                        trip_short_name = dep.tripShortName,
                                                        last_stop = dep.lastStop,
                                                        platform_string_realtime = dep.platform,
                                                        delay_seconds = 0L,
                                                        trip_cancelled = dep.cancelled,
                                                        stop_cancelled = false,
                                                        trip_deleted = false,
                                                        route_type = routeInfo?.routeType,
                                                        timezone = group.timezone,
                                                        distance_m = group.distanceM
                                                )

                                        // We need StopRouteInfo? for the row. RouteInfoExport vs
                                        // StopRouteInfo.
                                        // They are slightly different but similar.
                                        // StopRouteInfo from StopScreenRows.kt definition:
                                        // data class StopRouteInfo(val short_name, val long_name,
                                        // val color, val
                                        // text_color, val agency_id, ...)
                                        // RouteInfoExport from NearbyDepartures.kt:
                                        // data class RouteInfoExport(val shortName, val longName,
                                        // val agencyName, val
                                        // color, val textColor, val routeType)

                                        val stopRouteInfo =
                                                if (routeInfo != null) {
                                                        StopRouteInfo(
                                                                short_name = routeInfo.shortName,
                                                                long_name = routeInfo.longName,
                                                                color = routeInfo.color
                                                                                ?: "#000000",
                                                                text_color = routeInfo.textColor
                                                                                ?: "#FFFFFF",
                                                                agency_id =
                                                                        null, // V3 export doesn't
                                                                // strictly have
                                                                // agency_id
                                                                // in this object, maybe agencyName
                                                                route_type = routeInfo.routeType
                                                        )
                                                } else null

                                        StationScreenTrainRowCompact(
                                                event = event,
                                                routeInfo = stopRouteInfo,
                                                agencies = null,
                                                currentTime = nowSec,
                                                zoneId =
                                                        if (group.timezone.isNotBlank())
                                                                runCatching {
                                                                                java.time.ZoneId.of(
                                                                                        group.timezone
                                                                                )
                                                                        }
                                                                        .getOrDefault(
                                                                                java.time.ZoneId
                                                                                        .systemDefault()
                                                                        )
                                                        else java.time.ZoneId.systemDefault(),
                                                locale = Locale.getDefault(),
                                                showSeconds = false,
                                                useSymbolSign = true,
                                                showAgencyName = false,
                                                showTimeDiff = false,
                                                onTripClick = {
                                                        onTripClick(
                                                                TripClickResponse(
                                                                        tripId = dep.tripId,
                                                                        stopId = dep.stopId,
                                                                        chateauId = dep.chateauId,
                                                                        routeId = dep.routeId,
                                                                        routeType =
                                                                                routeInfo
                                                                                        ?.routeType,
                                                                        startDay =
                                                                                dep.serviceDate
                                                                                        .replace(
                                                                                                "-",
                                                                                                ""
                                                                                        )
                                                                )
                                                        )
                                                }
                                        )

                                        if (index < departures.lastIndex) {
                                                Divider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant,
                                                        thickness = 0.5.dp
                                                )
                                        }
                                }
                        }

                        if (departures.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                        onClick = {
                                                val first = departures.first()
                                                onStopClick(first.chateauId, first.stopId)
                                        },
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(vertical = 0.dp)
                                ) {
                                        Text(
                                                text =
                                                        stringResource(
                                                                id = R.string.more_departures
                                                        ),
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                }
                        }
                }
        }
}

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
                if (darkMode) Color(0xFF00532F)
                else Color(0xFF48DC90) // Dark green for dark mode, light green for light mode
        Row(
                Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                                onClick = onMyLocation,
                                modifier =
                                        Modifier
                                                .size(36.dp)
                                                .border(
                                                        2.dp,
                                                        Color(
                                                                0xFF008744
                                                        ), // oklch(72.3% 0.219 149.579)
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .background(
                                                        if (!currentPickModeIsPin)
                                                                primaryButtonColor.copy(0.25f)
                                                        else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                )
                        ) { Icon(Icons.Default.NearMe, contentDescription = "My location") }

                        Row(
                                modifier =
                                        Modifier
                                                .border(
                                                        2.dp,
                                                        Color(0xFFC9A2C8),
                                                        RoundedCornerShape(8.dp)
                                                ) // purple
                                                .clip(RoundedCornerShape(8.dp))
                                                .height(36.dp)
                        ) {
                                TextButton(
                                        modifier = Modifier.height(36.dp),
                                        onClick = onPinDrop,
                                        shape = RoundedCornerShape(0.dp),
                                        colors =
                                                ButtonDefaults.textButtonColors(
                                                        containerColor =
                                                                if (currentPickModeIsPin)
                                                                        Color(0xFFC9A2C8)
                                                                                .copy(0.25f)
                                                                else Color.Transparent
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
                                        color =
                                                MaterialTheme.colorScheme.outline.copy(
                                                        alpha = 0.6f
                                                ),
                                        modifier = Modifier
                                                .fillMaxHeight()
                                                .width(1.dp)
                                )
                                TextButton(
                                        onClick = onCenterPin,
                                        modifier = Modifier.height(36.dp),
                                        shape = RoundedCornerShape(0.dp),
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
                        modifier =
                                Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .height(42.dp)
                                        .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(999.dp)
                                        )
                ) {
                        TextButton(
                                onClick = { onSortChange(SortMode.ALPHA) },
                                colors =
                                        ButtonDefaults.textButtonColors(
                                                containerColor =
                                                        if (sortMode == SortMode.ALPHA)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(0.3f)
                                                        else Color.Transparent
                                        ),
                                modifier =
                                        Modifier
                                                .border(width = 0.dp, color = Color.Transparent)
                                                .fillMaxHeight(),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                                Icon(
                                        Icons.Default.SortByAlpha,
                                        contentDescription = "Sort by Alpha",
                                        Modifier.size(20.dp)
                                )
                        }

                        TextButton(
                                onClick = { onSortChange(SortMode.DISTANCE) },
                                colors =
                                        ButtonDefaults.textButtonColors(
                                                containerColor =
                                                        if (sortMode == SortMode.DISTANCE)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(0.3f)
                                                        else Color.Transparent
                                        ),
                                modifier =
                                        Modifier
                                                .border(width = 0.dp, color = Color.Transparent)
                                                .fillMaxHeight(),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                                Icon(
                                        Icons.Default.Straighten,
                                        contentDescription = "Sort by Distance",
                                        Modifier.size(20.dp)
                                )
                        }
                }
        }
}

@Composable
private fun FilterRow(filters: Filters, onChange: (Filters) -> Unit) {
        // Spacer(Modifier.height(2.dp))
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                FilterChip(stringResource(R.string.heading_intercity_rail), filters.rail) {
                        onChange(filters.copy(rail = !filters.rail))
                }
                FilterChip(stringResource(R.string.heading_local_rail), filters.metro) {
                        onChange(filters.copy(metro = !filters.metro))
                }
                FilterChip(stringResource(R.string.heading_bus), filters.bus) {
                        onChange(filters.copy(bus = !filters.bus))
                }
                FilterChip(stringResource(R.string.heading_other), filters.other) {
                        onChange(filters.copy(other = !filters.other))
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
                color =
                        if (on) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant
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
        route: RouteGroupV3,
        stopsTable: Map<String, StopOutputV3>,
        darkMode: Boolean,
        onTripClick: (TripClickResponse) -> Unit,
        onRouteClick: (chateauId: String, routeId: String) -> Unit,
        onStopClick: (chateauId: String, stopId: String) -> Unit,
        nowSec: Long
) {
        val bg =
                MaterialTheme.colorScheme.surfaceContainerLow.copy(
                        alpha = if (darkMode) 0.5f else 1f
                )
        val textCol = normalizeHex(route.textColor) ?: MaterialTheme.colorScheme.onSurface
        val lineCol = normalizeHex(route.color) ?: MaterialTheme.colorScheme.primary

        Column(
                modifier =
                        Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .padding(8.dp)
        ) {
                Row(
                        modifier =
                                Modifier.clickable { onRouteClick(route.chateauId, route.routeId) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        val isRatp =
                                RatpUtils.isIdfmChateau(route.chateauId) &&
                                        RatpUtils.isRatpRoute(route.shortName)
                        val isMta =
                                MtaSubwayUtils.MTA_CHATEAU_ID == route.chateauId &&
                                        !route.shortName.isNullOrEmpty() &&
                                        MtaSubwayUtils.isSubwayRouteId(route.shortName!!)

                        if (isRatp) {
                                val iconUrl = RatpUtils.getRatpIconUrl(route.shortName)
                                if (iconUrl != null) {
                                        AsyncImage(
                                                model =
                                                        ImageRequest.Builder(LocalContext.current)
                                                                .data(iconUrl)
                                                                .crossfade(true)
                                                                .build(),
                                                contentDescription = route.shortName,
                                                modifier =
                                                        Modifier
                                                                .height(24.dp)
                                                                .padding(end = 4.dp)
                                        )
                                } else {
                                        Text(
                                                text = route.shortName?.trim().orEmpty(),
                                                color =
                                                        if (darkMode)
                                                                lightenColour(
                                                                        lineCol,
                                                                        minContrast = 6.0,
                                                                )
                                                        else
                                                                darkenColour(
                                                                        lineCol,
                                                                        minContrast = 2.0
                                                                ),
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.titleMedium,
                                                textDecoration = TextDecoration.Underline
                                        )
                                }
                        } else if (isMta) {
                                val mtaColor = MtaSubwayUtils.getMtaSubwayColor(route.shortName!!)
                                val symbolShortName =
                                        MtaSubwayUtils.getMtaSymbolShortName(route.shortName)
                                androidx.compose.foundation.layout.Box(
                                        modifier =
                                                Modifier
                                                        .size(24.dp)
                                                        .clip(
                                                                androidx.compose.foundation.shape
                                                                        .CircleShape
                                                        )
                                                        .background(mtaColor),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = symbolShortName,
                                                color = Color.White,
                                                style =
                                                        MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold
                                                        ),
                                                textAlign =
                                                        androidx.compose.ui.text.style.TextAlign
                                                                .Center
                                        )
                                }
                                Spacer(Modifier.width(4.dp))
                        } else {
                                Text(
                                        text = route.shortName?.trim().orEmpty(),
                                        color =
                                                if (darkMode)
                                                        lightenColour(
                                                                lineCol,
                                                                minContrast = 6.0,
                                                        )
                                                else darkenColour(lineCol, minContrast = 2.0),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        textDecoration = TextDecoration.Underline
                                )
                        }

                        Text(
                                text = route.longName?.trim().orEmpty(),
                                color =
                                        if (darkMode) lightenColour(lineCol, minContrast = 4.5)
                                        else darkenColour(lineCol, minContrast = 2.0),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.titleMedium,
                                textDecoration = TextDecoration.Underline
                        )
                }

                // Headsigns (No flattening needed for V3)
                route.headsigns.entries.sortedBy { it.key }.forEach { (headsign, trips) ->
                        val visibleTrips =
                                trips.filter { t ->
                                        val dep = t.departureRealtime ?: t.departureSchedule ?: 0L
                                        val now = System.currentTimeMillis() / 1000
                                        dep in (now - 600)..(now + 64800)
                                }

                        if (visibleTrips.isEmpty()) return@forEach

                        Row(
                                modifier =
                                        Modifier
                                                .padding(start = 2.dp, bottom = 1.dp)
                                                .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier
                                                .size(20.dp)
                                                .offset(y = 0.dp)
                                )
                                Text(
                                        headsign.replace("Underground Station", "")
                                                .replace(" Station", ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                )

                                val firstTrip = trips.firstOrNull()
                                if (firstTrip != null) {
                                        val stopName = stopsTable[firstTrip.stopId]?.name
                                        if (stopName != null) {
                                                Spacer(Modifier.width(4.dp))
                                                Surface(
                                                        shape = RoundedCornerShape(2.dp),
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .surfaceContainerHigh,
                                                        modifier =
                                                                Modifier
                                                                        .offset(y = (-1).dp)
                                                                        .clickable {
                                                                                onStopClick(
                                                                                        route.chateauId,
                                                                                        firstTrip
                                                                                                .stopId
                                                                                )
                                                                        }
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 4.dp,
                                                                                vertical = 2.dp
                                                                        ),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.PinDrop,
                                                                        contentDescription =
                                                                                "Station",
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        12.dp
                                                                                ),
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                                Text(
                                                                        stopName.replace(
                                                                                " Station",
                                                                                ""
                                                                        ),
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        start = 2.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }

                        LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                items(
                                        items = visibleTrips,
                                        key = { it.tripId + it.departureSchedule }
                                ) { trip ->
                                        TripPill(
                                                trip = trip,
                                                chateauId = route.chateauId,
                                                routeId = route.routeId,
                                                stopsTable = stopsTable,
                                                lineCol = lineCol,
                                                textCol = textCol,
                                                onTripClick = onTripClick,
                                                nowSec = nowSec
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun TripPill(
        trip: LocalDepartureItem,
        chateauId: String,
        routeId: String,
        stopsTable: Map<String, StopOutputV3>,
        lineCol: Color,
        textCol: Color,
        onTripClick: (TripClickResponse) -> Unit,
        nowSec: Long
) {
        val dep = trip.departureRealtime ?: trip.departureSchedule ?: 0L
        val secondsLeft = dep - nowSec
        val isPast = secondsLeft < 0

        val contentAlpha = if (isPast) 0.7f else 1.0f

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
                                        startDay = trip.serviceDate
                                )
                        )
                },
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
                Column(
                        modifier =
                                Modifier
                                        .widthIn(min = 76.dp)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((-4).dp)
                ) {
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
                                                color =
                                                        contentColor.copy(
                                                                alpha = contentColor.alpha
                                                        )
                                        )
                                }
                        }

                        // Wall-clock time
                        val stop = stopsTable[trip.stopId]
                        val tz = stop?.timezone
                        if (tz != null) {
                                FormattedTimeText(
                                        timezone = tz,
                                        timeSeconds = dep,
                                        showSeconds = false,
                                )
                        }

                        if (trip.cancelled) {
                                Text(
                                        stringResource(R.string.cancelled),
                                        color = Color.Red,
                                        style = MaterialTheme.typography.labelSmall
                                )
                        }

                        // If both schedule & realtime are available, show +/- delay
                        val sched = trip.departureSchedule
                        val rt = trip.departureRealtime
                        if (sched != null && rt != null) {
                                val diff = rt - sched // seconds
                                if (diff != 0L) {
                                        DelayDiff(diff = diff, fontSizeOfPolarity = 12.sp)
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
