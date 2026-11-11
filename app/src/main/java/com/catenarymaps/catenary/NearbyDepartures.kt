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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import java.lang.Integer.min
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

/* -------------------------------------------------------------------------- */
/*  Data models that match the JSON payload (only the fields we actually use)  */
/* -------------------------------------------------------------------------- */

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
        val rail: Boolean = false,
        val metro: Boolean = false,
        val tram: Boolean = false,
        val bus: Boolean = false,
        val other: Boolean = false,
        val funicular: Boolean = false,
        val monorail: Boolean = false,
        val cableTram: Boolean = false
)

private object FilterPreferences {
        private const val PREFS_NAME = "catenary_filter_prefs"
        private const val KEY_RAIL = "filter_rail"
        private const val KEY_METRO = "filter_metro"
        private const val KEY_TRAM = "filter_tram"
        private const val KEY_BUS = "filter_bus"
        private const val KEY_OTHER = "filter_other"
        private const val KEY_FUNICULAR = "filter_funicular"
        private const val KEY_MONORAIL = "filter_monorail"
        private const val KEY_CABLE_TRAM = "filter_cable_tram"

        fun saveFilters(context: Context, filters: Filters) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                with(prefs.edit()) {
                        putBoolean(KEY_RAIL, filters.rail)
                        putBoolean(KEY_METRO, filters.metro)
                        putBoolean(KEY_TRAM, filters.tram)
                        putBoolean(KEY_BUS, filters.bus)
                        putBoolean(KEY_OTHER, filters.other)
                        putBoolean(KEY_FUNICULAR, filters.funicular)
                        putBoolean(KEY_MONORAIL, filters.monorail)
                        putBoolean(KEY_CABLE_TRAM, filters.cableTram)
                        apply()
                }
        }

        fun loadFilters(context: Context): Filters {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                return Filters(
                        rail = prefs.getBoolean(KEY_RAIL, false),
                        metro = prefs.getBoolean(KEY_METRO, false),
                        tram = prefs.getBoolean(KEY_TRAM, false),
                        bus = prefs.getBoolean(KEY_BUS, false),
                        other = prefs.getBoolean(KEY_OTHER, false),
                        funicular = prefs.getBoolean(KEY_FUNICULAR, false),
                        monorail = prefs.getBoolean(KEY_MONORAIL, false),
                        cableTram = prefs.getBoolean(KEY_CABLE_TRAM, false)
                )
        }
}

// Rough mapping mirroring the JS filter buckets
private fun isAllowedByFilter(
        routeType: Int,
        f: Filters,
        hasFunicular: Boolean,
        hasMonorail: Boolean,
        hasCableTram: Boolean
): Boolean {
        val allDisabled = !f.rail && !f.metro && !f.tram && !f.bus && !f.other &&
                !(hasFunicular && f.funicular) &&
                !(hasMonorail && f.monorail) &&
                !(hasCableTram && f.cableTram)
        if (allDisabled) return true
        return when (routeType) {
                3, 11, 700 -> f.bus
                1 -> f.metro
                0, 900 -> f.tram
                5 -> if (hasCableTram) f.cableTram else f.tram
                7 -> if (hasFunicular) f.funicular else f.metro
                12 -> if (hasMonorail) f.monorail else f.metro
                2, 106, 107, 101, 100, 102, 103 -> f.rail
                4 -> f.other // ferries
                else -> true
        }
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

private suspend fun fetchNearbyHttp(
        lat: Double,
        lon: Double,
        departureTimeSec: Long? = null
): NearbyResponseV3? =
        withContext(Dispatchers.IO) {
                Log.d(
                        NEARBY_TAG,
                        "Fetch nearby departures lat=$lat lon=$lon (thread=${Thread.currentThread().name})"
                )

                val timeParam = departureTimeSec?.let { "&departure_time=$it" } ?: ""
                val url =
                        "https://birch.catenarymaps.org/nearbydeparturesfromcoordsv3?lat=$lat&lon=$lon&limit_per_station=10&limit_per_headsign=20$timeParam"
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
                } catch (e: CancellationException) {
                        throw e
                } catch (t: Exception) {
                        Log.e(
                                NEARBY_TAG,
                                "Network error on ${Thread.currentThread().name}: ${t.message}",
                                t
                        )
                        null
                }
        }

private suspend fun fetchNearby(
        lat: Double,
        lon: Double,
        departureTimeSec: Long?,
        progressive: Boolean,
        onSnapshot: (NearbyResponseV3) -> Unit
): Boolean {
        try {
                kotlinx.coroutines.withTimeout(45_000) {
                        nearbyDepartureSnapshots(lat, lon, departureTimeSec, progressive)
                                .collect { onSnapshot(it) }
                }
                return true
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(NEARBY_TAG, "Nearby WebSocket timed out; retrying HTTP", e)
        } catch (e: CancellationException) {
                throw e
        } catch (e: Exception) {
                Log.w(NEARBY_TAG, "Nearby WebSocket failed; retrying HTTP", e)
        }
        val response = fetchNearbyHttp(lat, lon, departureTimeSec) ?: return false
        onSnapshot(response)
        return true
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
        var realtimeNowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
        var lockToNow by remember { mutableStateOf(true) }

        // Drive "now" when locked; when unlocked, the picker owns nowSec.
        LaunchedEffect(lockToNow) {
                while (true) {
                        if (lockToNow) {
                                nowSec = System.currentTimeMillis() / 1000
                        }
                        delay(1_000)
                }
        }

        // Keep countdown/past-time visuals relative to real time,
        // even when browsing departures for a different selected time.
        LaunchedEffect(Unit) {
                while (true) {
                        realtimeNowSec = System.currentTimeMillis() / 1000
                        delay(1_000)
                }
        }

        val context = LocalContext.current
        var filters by remember { mutableStateOf(FilterPreferences.loadFilters(context)) }

        LaunchedEffect(filters) { FilterPreferences.saveFilters(context, filters) }

        var sortMode by remember { mutableStateOf(SortMode.DISTANCE) }
        var loading by remember { mutableStateOf(false) }
        var offline by remember { mutableStateOf(false) }
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

        // Preserve the first GPS fix to avoid restarting nearby requests on minor GPS drift.
        // Explicit recentering can update it through the My Location button.
        var gpsOrigin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        LaunchedEffect(userLocation) {
                if (gpsOrigin == null && userLocation != null) gpsOrigin = userLocation
        }
        val locationMode = if (usePickedLocation) {
                NearbyDeparturesLocationMode.PINNED_LOCATION
        } else {
                NearbyDeparturesLocationMode.CURRENT_LOCATION
        }
        val lockedOrigin = if (usePickedLocation) pickedLocation else gpsOrigin

        var sorted by remember { mutableStateOf<List<NearbyItem>>(emptyList()) }
        var pollSession by remember { mutableStateOf(0) }
        fun restartPolling() {
                pollSession++
        }

        // When returning from Saved Trips, restore the correct location's data immediately.
        // A changed pin or a different selected time must never display the old response.
        // Use null for the moving 'now' clock so seconds ticking do not invalidate the cache.
        val selectedDepartureTime = if (lockToNow) null else nowSec
        LaunchedEffect(locationMode, lockedOrigin, selectedDepartureTime, pollSession, isAppInForeground) {
                val origin = lockedOrigin
                val cached = origin?.let {
                        nearbyDeparturesMemoryCache.get(locationMode, it, selectedDepartureTime)
                }
                stopsTableLongDistance = cached?.response?.longDistance.orEmpty()
                departureList = cached?.response?.local.orEmpty()
                routesMap = cached?.response?.routes.orEmpty()
                stopsMap = cached?.response?.stops.orEmpty()
                serverMs = cached?.response?.debug?.totalTimeMs
                // Clear the previous location's sorted cards before the derived sort recomputes.
                sorted = emptyList()
                firstAttemptSent = cached != null
                firstLoadComplete = cached != null
                offline = false
                loading = false

                if (!isAppInForeground || origin == null) return@LaunchedEffect

                var progressive = cached == null

                suspend fun doFetch() {
                        loading = true
                        firstAttemptSent = true
                        var latestSnapshot: NearbyResponseV3? = null
                        try {
                                val completed = fetchNearby(
                                        origin.first, origin.second, selectedDepartureTime, progressive
                                ) { response ->
                                        latestSnapshot = response
                                        stopsTableLongDistance = response.longDistance
                                        departureList = response.local
                                        routesMap = response.routes
                                        stopsMap = response.stops
                                        serverMs = response.debug?.totalTimeMs
                                        offline = false
                                }
                                if (completed) {
                                        // Cache only completed refreshes, not a partial WebSocket phase.
                                        latestSnapshot?.let {
                                                nearbyDeparturesMemoryCache.put(
                                                        locationMode, origin, selectedDepartureTime, it
                                                )
                                        }
                                        progressive = false
                                }
                                offline = !completed && latestSnapshot == null && cached == null
                        } finally {
                                loading = false
                                firstLoadComplete = true
                        }
                }

                // Reuse a fresh snapshot on navigation; the normal poll will refresh it.
                if (cached == null) doFetch()
                while (isActive) {
                        delay(10_000)
                        doFetch()
                }
        }

        val hasFunicular = remember(departureList, stopsTableLongDistance, routesMap) {
                departureList.any { it.routeType == 7 } || stopsTableLongDistance.any { group ->
                        group.departures.any { routesMap[it.chateauId]?.get(it.routeId)?.routeType == 7 }
                }
        }

        val hasMonorail = remember(departureList, stopsTableLongDistance, routesMap) {
                departureList.any { it.routeType == 12 } || stopsTableLongDistance.any { group ->
                        group.departures.any { routesMap[it.chateauId]?.get(it.routeId)?.routeType == 12 }
                }
        }

        val hasCableTram = remember(departureList, stopsTableLongDistance, routesMap) {
                departureList.any { it.routeType == 5 } || stopsTableLongDistance.any { group ->
                        group.departures.any { routesMap[it.chateauId]?.get(it.routeId)?.routeType == 5 }
                }
        }

        val hasNonBusDepartures = remember(departureList, stopsTableLongDistance, routesMap) {
                val hasLocalNonBus = departureList.any { rg ->
                        rg.headsigns.isNotEmpty() && rg.routeType != 3 && rg.routeType != 11 && rg.routeType != 700
                }
                val hasLongDistanceNonBus = stopsTableLongDistance.any { group ->
                        group.departures.any { dep ->
                                val rType = routesMap[dep.chateauId]?.get(dep.routeId)?.routeType
                                rType == null || (rType != 3 && rType != 11 && rType != 700)
                        }
                }
                hasLocalNonBus || hasLongDistanceNonBus
        }

        // derived + sorting
        val filteredLocal =
                remember(departureList, filters, hasFunicular, hasMonorail, hasCableTram) {
                        departureList.filter { rg ->
                                rg.headsigns.isNotEmpty() &&
                                        isAllowedByFilter(rg.routeType, filters, hasFunicular, hasMonorail, hasCableTram)
                        }
                }

        val filteredStations =
                remember(stopsTableLongDistance, filters, hasFunicular, hasMonorail, hasCableTram) {
                        stopsTableLongDistance.filter { group ->
                                val allDisabled =
                                        !filters.rail &&
                                                !filters.metro &&
                                                !filters.tram &&
                                                !filters.bus &&
                                                !filters.other &&
                                                !(hasFunicular && filters.funicular) &&
                                                !(hasMonorail && filters.monorail) &&
                                                !(hasCableTram && filters.cableTram)
                                (allDisabled || filters.rail) && group.departures.isNotEmpty()
                        }
                }

        // Derive a reasonable timezone for the nearby view. Prefer the closest
        // long-distance station, then any stop, falling back to device TZ.
        val nearbyTimezoneId by
        remember(stopsTableLongDistance, stopsMap) {
                mutableStateOf(
                        run {
                                val closestStation =
                                        stopsTableLongDistance.minByOrNull { it.distanceM }
                                closestStation?.timezone
                                        ?: stopsMap.values
                                                .firstOrNull()
                                                ?.values
                                                ?.firstOrNull()
                                                ?.timezone
                                        ?: ZoneId.systemDefault().id
                        }
                )
        }

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
                        val hoistedItems = mutableListOf<NearbyItem>()
                        val mixedItems = mutableListOf<NearbyItem>()

                        // Check if the closest station is within the 1000m "hoist" threshold
                        val shouldHoistStation =
                                stationItems.isNotEmpty() &&
                                        stationItems.first().sortDistance < 1000.0

                        if (shouldHoistStation) {
                                hoistedItems.add(stationItems.first())
                                mixedItems.addAll(stationItems.drop(1))
                        } else {
                                mixedItems.addAll(stationItems)
                        }

                        // Hoist Metro (<500m) and Tram (<400m) if in distance sort mode
                        if (sortMode == SortMode.DISTANCE) {
                                localItems.forEach { item ->
                                        val routeType = item.group.routeType
                                        // Match route types used in your filter logic
                                        val isMetro = routeType == 1 || routeType == 7 || routeType == 12 
                                        val isTram = routeType == 0 || routeType == 900 || routeType == 5
                                        
                                        if (isMetro && item.sortDistance < 500.0) {
                                                hoistedItems.add(item)
                                        } else if (isTram && item.sortDistance < 400.0) {
                                                hoistedItems.add(item)
                                        } else {
                                                mixedItems.add(item)
                                        }
                                }
                        } else {
                                mixedItems.addAll(localItems)
                        }

                        // Sort the hoisted items by distance so closer items (like OSM stations) can show above
                        finalList.addAll(hoistedItems.sortedBy { it.sortDistance })

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
                                        gpsOrigin = it
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

                // Time selector between the top options and the departures list.
                Spacer(modifier = Modifier.height(4.dp))
                TimeSelectorButton(
                        epochSeconds = nowSec,
                        timezoneId = nearbyTimezoneId,
                        isNow = lockToNow,
                        onTimeChange = { newEpoch ->
                                nowSec = newEpoch
                                restartPolling()
                        },
                        onIsNowChange = { lock ->
                                lockToNow = lock
                                if (lock) restartPolling()
                        },
                        modifier = Modifier.align(Alignment.Start),
                        labelPrefix = null
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

                if (hasNonBusDepartures) {
                        FilterRow(
                                filters = filters,
                                hasFunicular = hasFunicular,
                                hasMonorail = hasMonorail,
                                hasCableTram = hasCableTram,
                                darkMode = darkMode,
                                onChange = { filters = it }
                        )
                }
                Box(Modifier.fillMaxSize()) {
                        // The drawer owns the bottom inset, including the Explore/Trips bar.
                        LazyColumn(
                                modifier =
                                        Modifier
                                                .fillMaxSize(),
                        ) {
                                if (offline) {
                                        item {
                                                Text(stringResource(R.string.nearby_departures_offline),
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(8.dp))
                                        }
                                }
                                if (sorted.isEmpty() && firstLoadComplete && !loading && !offline) {
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
                                                        val lat =
                                                                item.stopsMap.values.firstOrNull()
                                                                        ?.lat
                                                                        ?: lockedOrigin?.first
                                                                        ?: 0.0
                                                        val lon =
                                                                item.stopsMap.values.firstOrNull()
                                                                        ?.lon
                                                                        ?: lockedOrigin?.second
                                                                        ?: 0.0
                                                        val isEurostyle =
                                                                EurostyleZone.isInside(lat, lon)
                                                        val isSwiss =
                                                                EurostyleZone.isSwitzerland(
                                                                        lat,
                                                                        lon
                                                                )
                                                        RouteGroupCard(
                                                                item.group,
                                                                item.stopsMap,
                                                                darkMode,
                                                                onTripClick,
                                                                onRouteClick,
                                                                onStopClick,
                                                                selectedTimeSec = nowSec,
                                                                realtimeNowSec = realtimeNowSec,
                                                                eurostyle = isEurostyle,
                                                                swiss = isSwiss
                                                        )
                                                }
                                                is NearbyItem.StationGroupItem -> {
                                                        val isEurostyle =
                                                                EurostyleZone.isInside(
                                                                        item.group.lat,
                                                                        item.group.lon
                                                                )
                                                        val isSwiss =
                                                                EurostyleZone.isSwitzerland(
                                                                        item.group.lat,
                                                                        item.group.lon
                                                                )
                                                        StationGroupCard(
                                                                group = item.group,
                                                                routesMap = routesMap,
                                                                selectedTimeSec = nowSec,
                                                                realtimeNowSec = realtimeNowSec,
                                                                onTripClick = onTripClick,
                                                                onStopClick = onStopClick,
                                                                darkMode = darkMode,
                                                                eurostyle = isEurostyle,
                                                                swiss = isSwiss
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
        selectedTimeSec: Long,
        realtimeNowSec: Long,
        onTripClick: (TripClickResponse) -> Unit,
        onStopClick: (chateauId: String, stopId: String) -> Unit,
        darkMode: Boolean,
        eurostyle: Boolean,
        swiss: Boolean
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
                        val departures =
                                group.departures
                                        .asSequence()
                                        .filter { !it.lastStop }
                                        .sortedBy { it.effectiveDepartureSec() ?: Long.MAX_VALUE }
                                        .take(10)
                                        .toList()

                        Column {
                                departures.forEachIndexed { index, dep ->
                                        val routeInfo = routesMap[dep.chateauId]?.get(dep.routeId)
                                        // Construct StopEvent
                                        // Logic mirroring Svelte
                                        // getEffectiveStationRealtimeDeparture
                                        val effectiveRealtimeDeparture =
                                                dep.effectiveRealtimeDepartureSec()

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
                                                        distance_m = group.distanceM,
                                                        final_station_name = dep.finalStationName
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
                                                currentTime = realtimeNowSec,
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
                                                eurostyle = eurostyle,
                                                swiss = swiss,
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
private fun FilterRow(
        filters: Filters,
        hasFunicular: Boolean,
        hasMonorail: Boolean,
        hasCableTram: Boolean,
        darkMode: Boolean,
        onChange: (Filters) -> Unit
) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                FilterChip(
                        stringResource(R.string.heading_intercity_rail),
                        filters.rail,
                        darkMode
                ) {
                        onChange(filters.copy(rail = !filters.rail))
                }
                FilterChip(stringResource(R.string.heading_metro), filters.metro, darkMode) {
                        onChange(filters.copy(metro = !filters.metro))
                }
                FilterChip(stringResource(R.string.heading_tram), filters.tram, darkMode) {
                        onChange(filters.copy(tram = !filters.tram))
                }
                if (hasCableTram) {
                        FilterChip(stringResource(R.string.heading_cable_tram), filters.cableTram, darkMode) {
                                onChange(filters.copy(cableTram = !filters.cableTram))
                        }
                }
                if (hasFunicular) {
                        FilterChip(stringResource(R.string.heading_funicular), filters.funicular, darkMode) {
                                onChange(filters.copy(funicular = !filters.funicular))
                        }
                }
                if (hasMonorail) {
                        FilterChip(stringResource(R.string.heading_monorail), filters.monorail, darkMode) {
                                onChange(filters.copy(monorail = !filters.monorail))
                        }
                }
                FilterChip(stringResource(R.string.heading_bus), filters.bus, darkMode) {
                        onChange(filters.copy(bus = !filters.bus))
                }
                FilterChip(stringResource(R.string.heading_other), filters.other, darkMode) {
                        onChange(filters.copy(other = !filters.other))
                }
        }
        Spacer(Modifier.height(0.dp))
}

@Composable
private fun FilterChip(label: String, on: Boolean, darkMode: Boolean, onClick: () -> Unit) {
        val borderColor = if (on) {
                if (darkMode) Color.White else Color.Black
        } else {
                Color.Gray.copy(alpha = 0.3f)
        }

        Surface(
                onClick = onClick,
                shape = RoundedCornerShape(999.dp),
                tonalElevation = if (on) 4.dp else 16.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                color =
                        if (on) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                        else Color.Transparent
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
        selectedTimeSec: Long,
        realtimeNowSec: Long,
        eurostyle: Boolean,
        swiss: Boolean
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
                                                                .decoderFactory(SvgDecoder.Factory())
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
                                MtaSubwayIcon(
                                        routeId = route.shortName!!,
                                        size = 24.dp,
                                        contentDescription = route.shortName
                                )
                                Spacer(Modifier.width(4.dp))
                        } else {
                                RouteBubble(
                                        chateau = route.chateauId,
                                        shortName = route.shortName,
                                        longName = route.longName,
                                        color = route.color,
                                        textColor = route.textColor,
                                        swiss = swiss
                                )
                        }

                        val displayLongName = route.longName?.trim().orEmpty()
                        if (displayLongName.isNotEmpty() && displayLongName != route.shortName?.trim().orEmpty()) {
                                Text(
                                        text = displayLongName,
                                        color =
                                                if (darkMode) lightenColour(lineCol, minContrast = 4.5)
                                                else darkenColour(lineCol, minContrast = 2.0),
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.titleMedium,
                                        textDecoration = TextDecoration.Underline
                                )
                        }
                }

                // Headsigns (No flattening needed for V3)
                route.headsigns.entries.sortedBy { it.key }.forEach { (headsign, trips) ->
                        val visibleTrips =
                                trips

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
                                                realtimeNowSec = realtimeNowSec
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
        realtimeNowSec: Long
) {
        val dep = trip.departureRealtime ?: trip.departureSchedule ?: 0L
        val secondsLeft = dep - realtimeNowSec
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
                                        SelfUpdatingDiffTimer(
                                                targetTimeSeconds = dep,
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
                                        DelayDiff(
                                                diff = diff,
                                                fontSizeOfPolarity = 12.sp,
                                                use_symbol_sign = true,
                                                hide_min_units = true,
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
