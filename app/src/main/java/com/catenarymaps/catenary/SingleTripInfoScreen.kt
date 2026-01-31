package com.catenarymaps.catenary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.maps.android.PolyUtil.decode as decodePolyutil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SingleTripInfoScreen(
        tripSelected: CatenaryStackEnum.SingleTrip,
        onStopClick: (CatenaryStackEnum.StopStack) -> Unit,
        onBlockClick: (CatenaryStackEnum.BlockStack) -> Unit,
        onRouteClick: (CatenaryStackEnum.RouteStack) -> Unit,
        usUnits: Boolean,
        showSeconds: Boolean,
        // --- Map sources passed from MainActivity ---
        transitShapeSource: MutableState<GeoJsonSource>,
        transitShapeDetourSource: MutableState<GeoJsonSource>,
        stopsContextSource: MutableState<GeoJsonSource>,
        majorDotsSource: MutableState<GeoJsonSource>,
        // --- State to control other map layers ---
        onSetStopsToHide: (Set<String>) -> Unit,
        // state to control the filter
        applyFilterToLiveDots: MutableState<Expression<BooleanValue>>,
        onBack: () -> Unit,
        onHome: () -> Unit
) {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

        LaunchedEffect(Unit) {
                try {
                        val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                                param(FirebaseAnalytics.Param.SCREEN_NAME, "SingleTripInfoScreen")
                                // param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
                                param("chateau_id", tripSelected.chateau_id)
                                param("trip_id", tripSelected.trip_id.toString())
                        }
                } catch (e: Exception) {
                        // Log the error or handle it gracefully
                        android.util.Log.e("GA", "Failed to log screen view", e)
                }
        }

        val viewModel: SingleTripViewModel =
                viewModel(
                        key =
                                "${tripSelected.chateau_id}-${tripSelected.trip_id}-${tripSelected.start_time}-${tripSelected.start_date}",
                        factory = SingleTripViewModel.factory(tripSelected)
                )

        val isLoading by viewModel.isLoading.collectAsState()
        val error by viewModel.error.collectAsState()
        val tripData by viewModel.tripData.collectAsState()
        val stopTimes by viewModel.stopTimes.collectAsState()
        // val showPreviousStops by viewModel.showPreviousStops.collectAsState() // Removed
        val lastInactiveStopIdx by viewModel.lastInactiveStopIdx.collectAsState()

        val vehicleData by viewModel.vehicleData.collectAsState()

        val lazyListState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        // Detect if current stop is below visible area
        val showScrollToCurrentButton by remember {
                derivedStateOf {
                        val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                        val targetIndex = (lastInactiveStopIdx + 1).coerceAtLeast(0)
                        // If valid target and it's greater than the last visible item index
                        // (plus some buffer or check if significantly below?)
                        // "Below the screen" means index > last visible index.
                        if (lastVisibleItem != null && targetIndex > lastVisibleItem.index) {
                                true
                        } else {
                                false
                        }
                }
        }

        val movingDotSegmentIdx by viewModel.movingDotSegmentIdx.collectAsState()
        val movingDotProgress by viewModel.movingDotProgress.collectAsState()
        val currentAtStopIdx by viewModel.currentAtStopIdx.collectAsState()

        var showFloatingControls by remember {
                mutableStateOf(prefs.getBoolean(K_SHOW_FLOATING_CONTROLS, true))
        }

        LaunchedEffect(showFloatingControls) {
                prefs.edit().putBoolean(K_SHOW_FLOATING_CONTROLS, showFloatingControls).apply()
        }
        val pulseIcon = remember { mutableStateOf(false) }
        var alertsExpanded by remember { mutableStateOf(false) }

        // Pulse Logic
        LaunchedEffect(showFloatingControls) {
                if (!showFloatingControls) {
                        pulseIcon.value = true
                        delay(500)
                        pulseIcon.value = false
                }
        }

        // Scroll State
        var hasInitialScrolled by remember { mutableStateOf(false) }

        // Toggles
        var showOriginalTimetable by remember {
                mutableStateOf(prefs.getBoolean(K_SHOW_ORIGINAL_TIMETABLE, false))
        }
        var showCountdown by remember { mutableStateOf(prefs.getBoolean(K_SHOW_COUNTDOWN, true)) }

        LaunchedEffect(showOriginalTimetable) {
                prefs.edit().putBoolean(K_SHOW_ORIGINAL_TIMETABLE, showOriginalTimetable).apply()
        }

        LaunchedEffect(showCountdown) {
                prefs.edit().putBoolean(K_SHOW_COUNTDOWN, showCountdown).apply()
        }

        var showConnections by remember {
                mutableStateOf(prefs.getBoolean("show_connections", false))
        }
        LaunchedEffect(showConnections) {
                prefs.edit().putBoolean("show_connections", showConnections).apply()
        }

        val timeColumnWidth by
                androidx.compose.animation.core.animateDpAsState(
                        targetValue =
                                (if (showSeconds) 80.dp else 66.dp) +
                                        (if (showOriginalTimetable) 55.dp else 0.dp),
                        label = "timeColumnWidth"
                )

        // NEW: build stop -> connection chips map
        val stopConnections =
                remember(tripData) {
                        val result = mutableMapOf<String, List<StopConnectionChip>>()
                        val connectionsPerStop = tripData?.connectionsPerStop ?: emptyMap()
                        val connectingRoutes = tripData?.connectingRoutes ?: emptyMap()

                        for ((stopId, perChateau) in connectionsPerStop) {
                                val connectingRoutesList =
                                        mutableListOf<Triple<ConnectingRoute, String, String>>()
                                for ((chateauId, routeIds) in perChateau) {
                                        val routesForChateau =
                                                connectingRoutes[chateauId] ?: continue
                                        for (routeId in routeIds) {
                                                routesForChateau[routeId]?.let {
                                                        connectingRoutesList.add(
                                                                Triple(it, chateauId, routeId)
                                                        )
                                                }
                                        }
                                }

                                if (connectingRoutesList.isNotEmpty()) {

                                        // Sort connections
                                        val typeOrder =
                                                mapOf(
                                                        2 to 1, // Rail
                                                        1 to 2, // Subway, Metro
                                                        0 to 3, // Tram, Streetcar, Light rail
                                                        4 to 4 // Ferry
                                                )
                                        connectingRoutesList.sortWith(
                                                compareBy<Triple<ConnectingRoute, String, String>> {
                                                        typeOrder[it.first.routeType] ?: 5
                                                }
                                                        .thenComparing { a, b ->
                                                                val aName =
                                                                        a.first.shortName
                                                                                ?: a.first.longName
                                                                                        ?: ""
                                                                val bName =
                                                                        b.first.shortName
                                                                                ?: b.first.longName
                                                                                        ?: ""

                                                                val aNameAsInt = aName.toIntOrNull()
                                                                val bNameAsInt = bName.toIntOrNull()

                                                                if (aNameAsInt != null &&
                                                                                bNameAsInt != null
                                                                ) {
                                                                        aNameAsInt.compareTo(
                                                                                bNameAsInt
                                                                        )
                                                                } else {
                                                                        aName.compareTo(bName)
                                                                }
                                                        }
                                        )

                                        result[stopId] =
                                                connectingRoutesList.map {
                                                        (route, chateauId, routeId) ->
                                                        StopConnectionChip(
                                                                routeId = routeId,
                                                                shortName = route.shortName,
                                                                longName = route.longName,
                                                                color = route.color,
                                                                textColor = route.textColor,
                                                                chateauId = chateauId,
                                                                agencyId = route.agencyId
                                                        )
                                                }
                                }
                        }

                        result
                }

        // --- Map Update Logic ---
        LaunchedEffect(tripData) {
                val data = tripData
                if (data != null) {
                        try {
                                val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
                                val routeName =
                                        (data.route_short_name ?: "") + (data.route_long_name ?: "")

                                firebaseAnalytics.logEvent("view_trip_details") {
                                        param("route_name", routeName)
                                        param("trip_id", tripSelected.trip_id.toString())
                                        param("chateau_id", tripSelected.chateau_id)
                                        if (data.route_id != null) {
                                                param("route_id", data.route_id)
                                        }
                                }
                        } catch (e: Exception) {
                                android.util.Log.e("GA", "Failed to log trip details view", e)
                        }

                        // Update trip shape
                        if (data.shape_polyline != null) {
                                val coordinates =
                                        decodePolyutil(data.shape_polyline).map {
                                                Position(it.longitude, it.latitude)
                                        }
                                val properties = buildJsonObject {
                                        put("color", JsonPrimitive(data.color ?: "#FFFFFF"))
                                        put(
                                                "text_color",
                                                JsonPrimitive(data.text_color ?: "#000000")
                                        )
                                        put(
                                                "route_label",
                                                JsonPrimitive(
                                                        data.route_short_name
                                                                ?: data.route_long_name
                                                )
                                        )
                                }
                                val feature =
                                        Feature(
                                                geometry = LineString(coordinates),
                                                properties = properties
                                        )
                                transitShapeSource.value.setData(
                                        GeoJsonData.Features(
                                                FeatureCollection(
                                                        listOf<Feature<LineString, JsonObject>>(
                                                                feature
                                                        )
                                                )
                                        )
                                )
                        } else {
                                transitShapeSource.value.setData(
                                        GeoJsonData.Features(
                                                FeatureCollection(
                                                        emptyList<
                                                                Feature<Point, Map<String, Any>>>()
                                                )
                                        )
                                )
                        }

                        // Update detour shape
                        if (data.old_shape_polyline != null) {
                                val coordinates =
                                        decodePolyutil(data.old_shape_polyline).map {
                                                Position(it.longitude, it.latitude)
                                        }
                                val properties = buildJsonObject {
                                        put("color", JsonPrimitive(data.color ?: "#FFFFFF"))
                                }
                                val feature =
                                        Feature(
                                                geometry = LineString(coordinates),
                                                properties = properties
                                        )
                                transitShapeDetourSource.value.setData(
                                        GeoJsonData.Features(
                                                FeatureCollection(
                                                        listOf<Feature<LineString, JsonObject>>(
                                                                feature
                                                        )
                                                )
                                        )
                                )
                        } else {
                                transitShapeDetourSource.value.setData(
                                        GeoJsonData.Features(
                                                FeatureCollection(
                                                        emptyList<
                                                                Feature<Point, Map<String, Any>>>()
                                                )
                                        )
                                )
                        }
                }
        }

        LaunchedEffect(stopTimes, tripData) {
                val data = tripData ?: return@LaunchedEffect
                if (stopTimes.isNotEmpty()) {
                        // This is your label_stops_on_map() logic
                        val stopFeatures =
                                stopTimes.mapNotNull { stopTime ->
                                        val properties = buildJsonObject {
                                                put(
                                                        "label",
                                                        JsonPrimitive(stopTime.raw.name)
                                                ) // TODO: Add time formatting and name cleaning
                                                put("stop_id", JsonPrimitive(stopTime.raw.stop_id))
                                                put(
                                                        "chateau",
                                                        JsonPrimitive(tripSelected.chateau_id)
                                                )
                                                put(
                                                        "stop_route_type",
                                                        JsonPrimitive(data.route_type)
                                                )
                                                put(
                                                        "cancelled",
                                                        JsonPrimitive(
                                                                stopTime.raw
                                                                        .schedule_relationship == 1
                                                        )
                                                )
                                        }
                                        Feature(
                                                geometry =
                                                        Point(
                                                                Position(
                                                                        stopTime.raw.longitude,
                                                                        stopTime.raw.latitude
                                                                )
                                                        ),
                                                properties = properties
                                        )
                                }
                        stopsContextSource.value.setData(
                                GeoJsonData.Features(FeatureCollection(stopFeatures))
                        )

                        // This is your stops_to_hide_store logic
                        onSetStopsToHide(stopTimes.map { it.raw.stop_id }.toSet())
                }
        }

        // Clear hidden stops when this screen leaves composition
        DisposableEffect(Unit) {
                onDispose {
                        onSetStopsToHide(emptySet())
                        // Clear context layers
                        transitShapeSource.value.setData(
                                GeoJsonData.Features(
                                        FeatureCollection(
                                                emptyList<Feature<Point, Map<String, Any>>>()
                                        )
                                )
                        )
                        transitShapeDetourSource.value.setData(
                                GeoJsonData.Features(
                                        FeatureCollection(
                                                emptyList<Feature<Point, Map<String, Any>>>()
                                        )
                                )
                        )
                        stopsContextSource.value.setData(
                                GeoJsonData.Features(
                                        FeatureCollection(
                                                emptyList<Feature<Point, Map<String, Any>>>()
                                        )
                                )
                        )
                        majorDotsSource.value.setData(
                                GeoJsonData.Features(
                                        FeatureCollection(
                                                emptyList<Feature<Point, Map<String, Any>>>()
                                        )
                                )
                        )
                }
        }

        // --- UI Rendering ---
        Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                        if (isLoading) {
                                LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                        } else if (error != null) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                        // Header with Navigation Controls
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(bottom = 16.dp),
                                                horizontalArrangement = Arrangement.Start,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                NavigationControls(
                                                        onBack = onBack,
                                                        onHome = onHome,
                                                        onPageInfo = {
                                                                showFloatingControls =
                                                                        !showFloatingControls
                                                        },
                                                        isPageInfoPulse = false
                                                )
                                        }

                                        // Scrollable Error Details
                                        androidx.compose.foundation.text.selection
                                                .SelectionContainer {
                                                        Column(
                                                                modifier =
                                                                        Modifier.weight(1f)
                                                                                .verticalScroll(
                                                                                        rememberScrollState()
                                                                                )
                                                        ) {
                                                                Text(
                                                                        text = "Error",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .headlineSmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        bottom =
                                                                                                8.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text = error!!,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error,
                                                                        fontFamily =
                                                                                androidx.compose.ui
                                                                                        .text.font
                                                                                        .FontFamily
                                                                                        .Monospace
                                                                )
                                                        }
                                                }
                                }
                        } else if (tripData != null) {
                                val data = tripData!!

                                RouteHeading(
                                        color = data.color ?: "#808080",
                                        textColor = data.text_color ?: "#000000",
                                        routeType = data.route_type,
                                        agencyName = null, // Not available in TripDataResponse
                                        shortName = data.route_short_name,
                                        longName = data.route_long_name,
                                        tripShortName = data.trip_short_name,
                                        chateauId = tripSelected.chateau_id,
                                        isCompact = false,
                                        routeClickable = true,
                                        onRouteClick = {
                                                if (data.route_id != null) {
                                                        onRouteClick(
                                                                CatenaryStackEnum.RouteStack(
                                                                        chateau_id =
                                                                                tripSelected
                                                                                        .chateau_id,
                                                                        route_id = data.route_id
                                                                )
                                                        )
                                                }
                                        },
                                        controls = {
                                                NavigationControls(
                                                        onBack = onBack,
                                                        onHome = onHome,
                                                        onPageInfo = {
                                                                showFloatingControls =
                                                                        !showFloatingControls
                                                        },
                                                        isPageInfoPulse = pulseIcon.value
                                                )
                                        },
                                        headsign = data.trip_headsign
                                )

                                // Toggles Row Removed - moved to floating controls

                                if (data.is_cancelled == true) {
                                        Text(
                                                text = "${stringResource(id = R.string.cancelled)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.error
                                        )
                                }

                                if (data.deleted == true) {
                                        Text(
                                                text = "${stringResource(id = R.string.deleted)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.error
                                        )
                                }

                                // Vehicle + Block + Alerts Pill Row
                                CompositionLocalProvider(
                                        LocalMinimumInteractiveComponentEnforcement provides false
                                ) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                                // horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                if (data.vehicle?.label != null ||
                                                                data.vehicle?.id != null
                                                ) {
                                                        Text(
                                                                text =
                                                                        "${stringResource(id = R.string.vehicle)}: ${data.vehicle.label ?: data.vehicle.id}",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )

                                                        Spacer(modifier = Modifier.width(8.dp))
                                                }

                                                // Clickable Block ID (Button Style)
                                                if (data.block_id != null &&
                                                                data.service_date != null
                                                ) {
                                                        androidx.compose.material3.Surface(
                                                                onClick = {
                                                                        onBlockClick(
                                                                                CatenaryStackEnum
                                                                                        .BlockStack(
                                                                                                chateau_id =
                                                                                                        tripSelected
                                                                                                                .chateau_id,
                                                                                                block_id =
                                                                                                        data.block_id,
                                                                                                service_date =
                                                                                                        data.service_date
                                                                                        )
                                                                        )
                                                                },
                                                                shape =
                                                                        RoundedCornerShape(
                                                                                percent = 50
                                                                        ),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                        ) {
                                                                Text(
                                                                        text =
                                                                                "${stringResource(id = R.string.block)} ${data.block_id}",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onPrimaryContainer,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        horizontal =
                                                                                                8.dp,
                                                                                        vertical =
                                                                                                2.dp
                                                                                ),
                                                                        fontWeight = FontWeight.Bold
                                                                )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                // Alerts Pill
                                                if (data.alert_id_to_alert.isNotEmpty()) {
                                                        androidx.compose.material3.Surface(
                                                                onClick = {
                                                                        alertsExpanded =
                                                                                !alertsExpanded
                                                                },
                                                                shape =
                                                                        RoundedCornerShape(
                                                                                percent = 50
                                                                        ),
                                                                color = Color(0xFFF99C24)
                                                        ) {
                                                                Row(
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        horizontal =
                                                                                                8.dp,
                                                                                        vertical =
                                                                                                2.dp
                                                                                )
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Warning,
                                                                                contentDescription =
                                                                                        "Alerts",
                                                                                tint = Color.White,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                12.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                4.dp
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        "${data.alert_id_to_alert.size}",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                color = Color.White,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }

                        if (tripData != null) {
                                val data = tripData!!
                                if (alertsExpanded) {
                                        Dialog(
                                                onDismissRequest = { alertsExpanded = false },
                                                properties =
                                                        DialogProperties(
                                                                usePlatformDefaultWidth = false
                                                        )
                                        ) {
                                                androidx.compose.material3.Surface(
                                                        modifier = Modifier.fillMaxSize(),
                                                        color = MaterialTheme.colorScheme.background
                                                ) {
                                                        Column(modifier = Modifier.fillMaxSize()) {
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .padding(
                                                                                                16.dp
                                                                                        ),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceBetween,
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        stringResource(
                                                                                                R.string
                                                                                                        .service_alerts_header
                                                                                        ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .headlineSmall
                                                                        )
                                                                        IconButton(
                                                                                onClick = {
                                                                                        alertsExpanded =
                                                                                                false
                                                                                }
                                                                        ) {
                                                                                Icon(
                                                                                        imageVector =
                                                                                                Icons.Filled
                                                                                                        .Close,
                                                                                        contentDescription =
                                                                                                "Close"
                                                                                )
                                                                        }
                                                                }
                                                                Column(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .weight(1f)
                                                                                        .verticalScroll(
                                                                                                rememberScrollState()
                                                                                        )
                                                                                        .padding(
                                                                                                horizontal =
                                                                                                        16.dp
                                                                                        )
                                                                ) {
                                                                        AlertsBox(
                                                                                alerts =
                                                                                        data.alert_id_to_alert,
                                                                                default_tz =
                                                                                        data.tz,
                                                                                chateau =
                                                                                        tripSelected
                                                                                                .chateau_id,
                                                                                isScrollable =
                                                                                        false,
                                                                                expanded = true,
                                                                                onExpandedChange = {
                                                                                }
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                32.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }

                                Column() { // Clickable Vehicle Label
                                        if (data.vehicle?.label != null || data.vehicle?.id != null
                                        ) {
                                                VehicleInfo(
                                                        label = data.vehicle.label
                                                                        ?: data.vehicle.id ?: "",
                                                        chateau = tripSelected.chateau_id,
                                                        routeId = data.route_id
                                                )
                                        }

                                        if (vehicleData != null) {
                                                VehicleInfoDetails(
                                                        vehicleData = vehicleData!!,
                                                        usUnits = usUnits
                                                )
                                        }
                                }

                                // Stop List

                                // Auto-scroll logic
                                LaunchedEffect(
                                        stopTimes,
                                        lastInactiveStopIdx,
                                        tripData,
                                        isLoading
                                ) {
                                        if (!isLoading &&
                                                        !hasInitialScrolled &&
                                                        stopTimes.isNotEmpty()
                                        ) {
                                                delay(100)
                                                val targetStopIndex =
                                                        (lastInactiveStopIdx + 1).coerceIn(
                                                                0,
                                                                stopTimes.indices.last
                                                        )
                                                lazyListState.scrollToItem(index = targetStopIndex)
                                                hasInitialScrolled = true
                                        }
                                }

                                LazyColumn(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        // .windowInsetsBottomHeight(WindowInsets(bottom =
                                                        // WindowInsets.safeDrawing.getBottom(density =
                                                        // LocalDensity.current)))
                                                        .windowInsetsPadding(
                                                                WindowInsets(
                                                                        bottom =
                                                                                WindowInsets
                                                                                        .safeDrawing
                                                                                        .getBottom(
                                                                                                density =
                                                                                                        LocalDensity
                                                                                                                .current
                                                                                        )
                                                                )
                                                        ),
                                        state = lazyListState
                                ) {

                                        // item(key = "show-previous-stops") removed

                                        itemsIndexed(stopTimes) { i, stopTime ->
                                                // Always show all stops
                                                // Calculate new state variables
                                                val isInactive = i <= lastInactiveStopIdx
                                                val isBottomSegmentPast = i < lastInactiveStopIdx
                                                val connections =
                                                        stopConnections[stopTime.raw.stop_id]

                                                StopListItem(
                                                        stopTime = stopTime,
                                                        tripColorStr = data.color ?: "#808080",
                                                        isFirst = i == 0,
                                                        isLast = i == stopTimes.lastIndex,
                                                        isInactive = isInactive,
                                                        isBottomSegmentPast = isBottomSegmentPast,
                                                        connections = connections,
                                                        isAtStop = (i == currentAtStopIdx),
                                                        movingDotProgress =
                                                                if (i == movingDotSegmentIdx)
                                                                        movingDotProgress
                                                                else null,
                                                        onStopClick = {
                                                                onStopClick(
                                                                        CatenaryStackEnum.StopStack(
                                                                                chateau_id =
                                                                                        tripSelected
                                                                                                .chateau_id,
                                                                                stop_id =
                                                                                        stopTime.raw
                                                                                                .stop_id
                                                                        )
                                                                )
                                                        },
                                                        showSeconds = showSeconds,
                                                        showOriginalTimetable =
                                                                showOriginalTimetable,
                                                        showCountdown = showCountdown,
                                                        showConnections = showConnections,
                                                        timeColumnWidth = timeColumnWidth
                                                )
                                        }
                                }
                        } // End tripData != null block
                } // End Column

                // Floating Controls - Scroll To Current
                androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToCurrentButton,
                        modifier =
                                Modifier.align(Alignment.BottomEnd)
                                        .padding(
                                                bottom = 80.dp,
                                                end = 16.dp
                                        ), // Check padding vs other controls
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                        FloatingActionButton(
                                onClick = {
                                        coroutineScope.launch {
                                                val targetIndex =
                                                        (lastInactiveStopIdx + 1).coerceIn(
                                                                0,
                                                                stopTimes.indices.last
                                                        )
                                                lazyListState.animateScrollToItem(targetIndex)
                                        }
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                                Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Scroll to current stop"
                                )
                        }
                }

                // Floating Controls - Toggles
                androidx.compose.animation.AnimatedVisibility(
                        visible = showFloatingControls,
                        modifier =
                                Modifier.align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp)
                                        .windowInsetsPadding(
                                                WindowInsets(
                                                        bottom =
                                                                WindowInsets.safeDrawing.getBottom(
                                                                        density =
                                                                                LocalDensity.current
                                                                )
                                                )
                                        ),
                        enter =
                                androidx.compose.animation.fadeIn() +
                                        androidx.compose.animation.slideInVertically { it },
                        exit =
                                androidx.compose.animation.fadeOut() +
                                        androidx.compose.animation.slideOutVertically { it }
                ) {
                        androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                shadowElevation = 4.dp
                        ) {
                                Column(
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 16.dp,
                                                        vertical = 8.dp
                                                ),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                        IconButton(
                                                onClick = { showFloatingControls = false },
                                                modifier = Modifier.size(24.dp).align(Alignment.End)
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = "Close Controls",
                                                        modifier = Modifier.size(16.dp)
                                                )
                                        }
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        androidx.compose.material3.Switch(
                                                                checked = showOriginalTimetable,
                                                                onCheckedChange = {
                                                                        showOriginalTimetable = it
                                                                },
                                                                modifier =
                                                                        Modifier.scale(0.8f)
                                                                                .padding(end = 4.dp)
                                                        )
                                                        Text(
                                                                text =
                                                                        stringResource(
                                                                                R.string
                                                                                        .single_trip_info_screen_original
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall
                                                        )
                                                }
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        androidx.compose.material3.Switch(
                                                                checked = showCountdown,
                                                                onCheckedChange = {
                                                                        showCountdown = it
                                                                },
                                                                modifier =
                                                                        Modifier.scale(0.8f)
                                                                                .padding(end = 4.dp)
                                                        )
                                                        Text(
                                                                text =
                                                                        stringResource(
                                                                                R.string
                                                                                        .single_trip_info_screen_countdown
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall
                                                        )
                                                }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                androidx.compose.material3.Switch(
                                                        checked = showConnections,
                                                        onCheckedChange = { showConnections = it },
                                                        modifier =
                                                                Modifier.scale(0.8f)
                                                                        .padding(end = 4.dp)
                                                )
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        R.string
                                                                                .single_trip_info_screen_connections
                                                                ),
                                                        style = MaterialTheme.typography.labelSmall
                                                )
                                        }
                                }
                        }
                }
        } // End Box
} // End SingleTripInfoScreen

@Composable
fun VehicleInfoDetails(vehicleData: VehicleRealtimeData, usUnits: Boolean) {
        Column(modifier = Modifier.padding(vertical = 0.dp)) {
                // Last updated
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Text(
                                text = stringResource(R.string.lastupdated) + ": ",
                                style = MaterialTheme.typography.labelSmall
                        )
                        var currentTime by remember {
                                mutableStateOf(System.currentTimeMillis() / 1000)
                        }
                        LaunchedEffect(Unit) {
                                while (true) {
                                        delay(1000)
                                        currentTime = System.currentTimeMillis() / 1000
                                }
                        }
                        vehicleData.timestamp?.let {
                                val diff = (it - currentTime).toDouble()
                                DiffTimer(
                                        diff = diff,
                                        showSeconds = true,
                                        showBrackets = false,
                                        numSize = 12.sp,
                                        unitSize = 10.sp
                                )
                        }

                        // Speed
                        vehicleData.position?.speed?.let { speed ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                                text = stringResource(R.string.speed) + ": ",
                                                style = MaterialTheme.typography.labelSmall
                                        )
                                        val speedText =
                                                if (usUnits) {
                                                        "%.2f mph".format(speed * 2.23694)
                                                } else {
                                                        "%.2f km/h".format(speed * 3.6)
                                                }
                                        Text(
                                                text = speedText,
                                                style = MaterialTheme.typography.labelSmall
                                        )
                                }
                        }
                }

                // Occupancy Status
                vehicleData.occupancy_status?.toIntOrNull()?.let { status ->
                        val occupancyColor =
                                when (status) {
                                        3 -> Color(0xFFF9A825) // Amber 600ish
                                        4, 5, 6, 8 -> MaterialTheme.colorScheme.error
                                        else -> Color.Unspecified
                                }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                        text = stringResource(R.string.occupancy_status) + ": ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = occupancyColor
                                )
                                Text(
                                        text = occupancy_to_symbol(status),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        color = occupancyColor
                                )
                                val statusText = occupancyStatusToString(status)
                                Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = occupancyColor
                                )
                        }
                }

                // Occupancy Percentage
                vehicleData.occupancy_percentage?.takeIf { it > 0 }?.let { percentage ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                        text = stringResource(R.string.occupancy_percentage) + ": ",
                                        style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                        text = "$percentage%",
                                        style = MaterialTheme.typography.labelSmall
                                )
                        }
                }
        }
}

@Composable
private fun occupancyStatusToString(status: Int): String {
        return when (status) {
                0 -> stringResource(R.string.occupancy_status_empty)
                1 -> stringResource(R.string.occupancy_status_many_seats_available)
                2 -> stringResource(R.string.occupancy_status_few_seats_available)
                3 -> stringResource(R.string.occupancy_status_standing_room_only)
                4 -> stringResource(R.string.occupancy_status_crushed_standing_room_only)
                5 -> stringResource(R.string.occupancy_status_full)
                6 -> stringResource(R.string.occupancy_status_not_accepting_passengers)
                7 -> stringResource(R.string.occupancy_status_no_data)
                8 -> stringResource(R.string.occupancy_status_not_boardable)
                else -> ""
        }
}

@Composable
fun StopListItem(
        stopTime: StopTimeCleaned,
        tripColorStr: String,
        isFirst: Boolean,
        isLast: Boolean,
        isInactive: Boolean,
        isBottomSegmentPast: Boolean,
        onStopClick: () -> Unit,
        showSeconds: Boolean,
        showOriginalTimetable: Boolean,
        showCountdown: Boolean,
        showConnections: Boolean,
        timeColumnWidth: androidx.compose.ui.unit.Dp,
        connections: List<StopConnectionChip>? = null,
        isAtStop: Boolean = false,
        movingDotProgress: Float? = null,
) {
        val isDark = isSystemInDarkTheme()
        val neutralColor = if (isDark) Color(0xFFDDDDDD) else Color(0xFF333333)
        val tripColor =
                try {
                        Color(android.graphics.Color.parseColor(tripColorStr))
                } catch (e: Exception) {
                        Color.Gray
                }

        val rtArrival = stopTime.rtArrivalTime
        val rtDeparture = stopTime.rtDepartureTime
        val schedArr =
                stopTime.raw.scheduled_arrival_time_unix_seconds
                        ?: stopTime.raw.interpolated_stoptime_unix_seconds
        val schedDep =
                stopTime.raw.scheduled_departure_time_unix_seconds
                        ?: stopTime.raw.interpolated_stoptime_unix_seconds

        val tz = stopTime.raw.timezone ?: java.time.ZoneId.systemDefault().id

        // Check for double time
        val isDoubleTime =
                (schedDep != null && schedArr != null && schedDep != schedArr) ||
                        (rtDeparture != null && rtArrival != null && rtDeparture != rtArrival)

        val aboveContent: List<@Composable () -> Unit> =
                remember(
                        stopTime,
                        isDoubleTime,
                        showSeconds,
                        showOriginalTimetable,
                        showCountdown
                ) {
                        val list = mutableListOf<@Composable () -> Unit>()

                        if (isDoubleTime) {
                                // ARRIVAL (All arrival info goes above)
                                val arrTime = rtArrival ?: schedArr ?: 0L
                                val arrDelay =
                                        if (rtArrival != null && schedArr != null)
                                                rtArrival - schedArr
                                        else 0L
                                val arrHasDelay = arrDelay != 0L

                                list.add {
                                        Row(verticalAlignment = Alignment.Top) {
                                                if (showOriginalTimetable && schedArr != null) {
                                                        Box(
                                                                modifier = Modifier.height(20.dp),
                                                                contentAlignment =
                                                                        Alignment.CenterEnd
                                                        ) {
                                                                FormattedTimeText(
                                                                        timezone = tz,
                                                                        timeSeconds = schedArr,
                                                                        showSeconds = false,
                                                                        color = Color.Gray,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleMedium
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Normal
                                                                                        ),
                                                                        textDecoration =
                                                                                TextDecoration
                                                                                        .LineThrough,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        end = 4.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                        Box(
                                                                modifier = Modifier.height(20.dp),
                                                                contentAlignment =
                                                                        Alignment.BottomEnd
                                                        ) {
                                                                FormattedTimeText(
                                                                        timezone = tz,
                                                                        timeSeconds = arrTime,
                                                                        showSeconds = showSeconds,
                                                                        color =
                                                                                if (isInactive)
                                                                                        Color.Gray
                                                                                else
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurface,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleMedium
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Normal
                                                                                        )
                                                                )
                                                        }
                                                        if (arrHasDelay) {
                                                                DelayDiff(
                                                                        diff = arrDelay,
                                                                        show_seconds = showSeconds,
                                                                        fontSizeOfPolarity = 10.sp,
                                                                        use_symbol_sign = true,
                                                                        modifier = Modifier
                                                                )
                                                        }
                                                        if (showCountdown) {
                                                                Box(modifier = Modifier) {
                                                                        SelfUpdatingDiffTimer(
                                                                                targetTimeSeconds =
                                                                                        arrTime,
                                                                                showBrackets =
                                                                                        false,
                                                                                showSeconds =
                                                                                        showSeconds,
                                                                                numSize = 12.sp,
                                                                                unitSize = 10.sp,
                                                                                bracketSize = 12.sp
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                        list
                }

        val mainContent: (@Composable () -> Unit)? =
                remember(
                        stopTime,
                        isDoubleTime,
                        showSeconds,
                        showOriginalTimetable,
                        showCountdown
                ) {
                        val headerHeightLocal = if (isDoubleTime) 20.dp else 24.dp
                        if (isDoubleTime) {
                                // DEPARTURE
                                val depTime = rtDeparture ?: schedDep ?: 0L
                                val depDelay =
                                        if (rtDeparture != null && schedDep != null)
                                                rtDeparture - schedDep
                                        else 0L
                                val depHasDelay = depDelay != 0L

                                val content: @Composable () -> Unit = {
                                        Row(verticalAlignment = Alignment.Top) {
                                                if (showOriginalTimetable && schedDep != null) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.height(
                                                                                headerHeightLocal
                                                                        ),
                                                                contentAlignment =
                                                                        Alignment.CenterEnd
                                                        ) {
                                                                FormattedTimeText(
                                                                        timezone = tz,
                                                                        timeSeconds = schedDep,
                                                                        showSeconds = false,
                                                                        color = Color.Gray,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleMedium
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Normal
                                                                                        ),
                                                                        textDecoration =
                                                                                TextDecoration
                                                                                        .LineThrough,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        end = 4.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.height(
                                                                                headerHeightLocal
                                                                        ),
                                                                contentAlignment =
                                                                        Alignment.CenterEnd
                                                        ) {
                                                                FormattedTimeText(
                                                                        timezone = tz,
                                                                        timeSeconds = depTime,
                                                                        showSeconds = showSeconds,
                                                                        color =
                                                                                if (isInactive)
                                                                                        Color.Gray
                                                                                else
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurface,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleMedium
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Bold
                                                                                        ),
                                                                        secondsStyle =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleMedium
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Normal
                                                                                        )
                                                                )
                                                        }
                                                        if (depHasDelay) {
                                                                DelayDiff(
                                                                        diff = depDelay,
                                                                        show_seconds = showSeconds,
                                                                        fontSizeOfPolarity = 10.sp,
                                                                        use_symbol_sign = true,
                                                                        modifier =
                                                                                Modifier.offset(
                                                                                        y = (-6).dp
                                                                                )
                                                                )
                                                        }
                                                        if (showCountdown) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                                start =
                                                                                                        4.dp
                                                                                        )
                                                                                        .offset(
                                                                                                y =
                                                                                                        (-8).dp
                                                                                        )
                                                                ) {
                                                                        SelfUpdatingDiffTimer(
                                                                                targetTimeSeconds =
                                                                                        depTime,
                                                                                showBrackets =
                                                                                        false,
                                                                                showSeconds =
                                                                                        showSeconds,
                                                                                numSize = 12.sp,
                                                                                unitSize = 10.sp,
                                                                                bracketSize = 12.sp,
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                                content
                        } else {
                                // UNIFIED
                                val unifiedRt = rtDeparture ?: rtArrival
                                val unifiedSched = schedDep ?: schedArr
                                val timeToShow = unifiedRt ?: unifiedSched

                                val delay =
                                        if (unifiedRt != null && unifiedSched != null)
                                                unifiedRt - unifiedSched
                                        else 0L
                                val hasDelay = delay != 0L

                                if (timeToShow != null) {
                                        val content: @Composable () -> Unit = {
                                                Row(verticalAlignment = Alignment.Top) {
                                                        if (showOriginalTimetable &&
                                                                        unifiedSched != null
                                                        ) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        headerHeightLocal
                                                                                ),
                                                                        contentAlignment =
                                                                                Alignment.CenterEnd
                                                                ) {
                                                                        FormattedTimeText(
                                                                                timezone = tz,
                                                                                timeSeconds =
                                                                                        unifiedSched,
                                                                                showSeconds = false,
                                                                                color = Color.Gray,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleMedium
                                                                                                .copy(
                                                                                                        fontWeight =
                                                                                                                FontWeight
                                                                                                                        .Normal
                                                                                                ),
                                                                                textDecoration =
                                                                                        TextDecoration
                                                                                                .LineThrough,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                end =
                                                                                                        4.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                        Column(
                                                                horizontalAlignment = Alignment.End
                                                        ) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        headerHeightLocal
                                                                                ),
                                                                        contentAlignment =
                                                                                Alignment.CenterEnd
                                                                ) {
                                                                        FormattedTimeText(
                                                                                timezone = tz,
                                                                                timeSeconds =
                                                                                        timeToShow,
                                                                                showSeconds =
                                                                                        showSeconds,
                                                                                color =
                                                                                        if (isInactive
                                                                                        )
                                                                                                Color.Gray
                                                                                        else
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurface,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleMedium
                                                                                                .copy(
                                                                                                        fontWeight =
                                                                                                                FontWeight
                                                                                                                        .Bold
                                                                                                ),
                                                                                secondsStyle =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleMedium
                                                                                                .copy(
                                                                                                        fontWeight =
                                                                                                                FontWeight
                                                                                                                        .Normal
                                                                                                )
                                                                        )
                                                                }
                                                                if (hasDelay) {
                                                                        DelayDiff(
                                                                                diff = delay,
                                                                                show_seconds =
                                                                                        showSeconds,
                                                                                fontSizeOfPolarity =
                                                                                        10.sp,
                                                                                use_symbol_sign =
                                                                                        true,
                                                                                modifier =
                                                                                        Modifier.offset(
                                                                                                y =
                                                                                                        (-4).dp
                                                                                        )
                                                                        )
                                                                }
                                                                if (showCountdown) {
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                        start =
                                                                                                                4.dp
                                                                                                )
                                                                                                .offset(
                                                                                                        y =
                                                                                                                (-8).dp
                                                                                                )
                                                                        ) {
                                                                                SelfUpdatingDiffTimer(
                                                                                        targetTimeSeconds =
                                                                                                timeToShow,
                                                                                        showBrackets =
                                                                                                false,
                                                                                        showSeconds =
                                                                                                showSeconds,
                                                                                        numSize =
                                                                                                12.sp,
                                                                                        unitSize =
                                                                                                10.sp,
                                                                                        bracketSize =
                                                                                                12.sp
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                        content
                                } else {
                                        null
                                }
                        }
                }

        // Define a consistent header height for the "Station Name" row
        val headerHeight = 24.dp

        Column(
                modifier = Modifier.fillMaxWidth().zIndex(if (movingDotProgress != null) 1f else 0f)
        ) {
                // 1. Render Above Rows
                aboveContent.forEachIndexed { index, content ->
                        Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.Bottom
                        ) {
                                // Time
                                Box(
                                        modifier =
                                                Modifier.width(timeColumnWidth)
                                                        .padding(end = 4.dp)
                                                        .padding(
                                                                top =
                                                                        if (index == 0) 12.dp
                                                                        else 0.dp
                                                        ),
                                        contentAlignment = Alignment.CenterEnd
                                ) { content() }

                                // Timeline (Line Only)
                                Box(modifier = Modifier.width(16.dp).fillMaxHeight()) {
                                        if (!isFirst) {
                                                TimelineLine(
                                                        color = neutralColor,
                                                        isPast = isInactive,
                                                        modifier = Modifier.fillMaxSize()
                                                )
                                        }
                                }

                                // Content Spacer
                                Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                        // Empty
                                }
                        }
                }

                // 2. Render Main Row
                val mainRowTopPadding = if (aboveContent.isEmpty()) 12.dp else 0.dp
                Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Top
                ) {
                        // Time
                        // We constrain the time to the header height to align with the dot and name
                        Box(
                                modifier =
                                        Modifier.width(timeColumnWidth)
                                                // .height(headerHeight) // Removed to allow delay
                                                // expansion
                                                .padding(end = 4.dp)
                                                .padding(top = mainRowTopPadding),
                                contentAlignment =
                                        Alignment.TopEnd // Align content to top (time inside)
                        ) { mainContent?.invoke() }

                        // Timeline (Dot + Lines)
                        Box(modifier = Modifier.width(16.dp).fillMaxHeight()) {
                                TripProgressIndicator(
                                        color = tripColor,
                                        neutralColor = neutralColor,
                                        isFirst = isFirst, // Standard Dot Logic
                                        isLast = isLast,
                                        isTopPast = isInactive,
                                        isBottomPast = isBottomSegmentPast,
                                        modifier = Modifier.fillMaxSize(),
                                        // Center dot in the headerHeight
                                        dotOffset = (headerHeight / 2) + mainRowTopPadding,
                                        isAtStop = isAtStop,
                                        movingDotProgress = movingDotProgress
                                )
                        }

                        // Content (Station Name + Platform)
                        Column(
                                modifier =
                                        Modifier.weight(1f)
                                                .padding(start = 8.dp)
                                                .padding(top = mainRowTopPadding),
                                verticalArrangement = Arrangement.Top
                        ) {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .defaultMinSize(minHeight = headerHeight),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        // Station Name
                                        stopTime.raw.name?.let {
                                                Text(
                                                        text = it,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color =
                                                                if (isInactive) Color.Gray
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface,
                                                        fontWeight = FontWeight.Normal,
                                                        modifier =
                                                                Modifier.clickable(
                                                                                onClick =
                                                                                        onStopClick
                                                                        )
                                                                        .weight(
                                                                                1f,
                                                                                fill = false
                                                                        ), // Don't push platform
                                                        // off if name is short
                                                        textDecoration =
                                                                if (isInactive) TextDecoration.None
                                                                else TextDecoration.Underline,
                                                        overflow = TextOverflow.Ellipsis,
                                                        maxLines = 2
                                                )
                                        }

                                        // Platform Info (Right aligned)
                                        if (stopTime.raw.rt_platform_string != null) {
                                                val platformText =
                                                        stopTime.raw
                                                                .rt_platform_string
                                                                .replace(
                                                                        "Track",
                                                                        "",
                                                                        ignoreCase = true
                                                                )
                                                                .replace(
                                                                        "Platform",
                                                                        "",
                                                                        ignoreCase = true
                                                                )
                                                                .trim()

                                                Text(
                                                        text = platformText,
                                                        style =
                                                                MaterialTheme.typography.bodyLarge
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                )
                                        }
                                }

                                if (!connections.isNullOrEmpty() && showConnections) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        SelectionRouteBadges(
                                                routeIds = connections.map { it.routeId },
                                                resolveRouteInfo = { routeId ->
                                                        connections
                                                                .find { it.routeId == routeId }
                                                                ?.let { chip ->
                                                                        ResolvedRouteBadgeInfo(
                                                                                routeId =
                                                                                        chip.routeId,
                                                                                shortName =
                                                                                        chip.shortName,
                                                                                longName =
                                                                                        chip.longName,
                                                                                color = chip.color
                                                                                                ?: "000000",
                                                                                textColor =
                                                                                        chip.textColor
                                                                                                ?: "FFFFFF",
                                                                                agencyId =
                                                                                        chip.agencyId,
                                                                                chateauId =
                                                                                        chip.chateauId
                                                                        )
                                                                }
                                                },
                                                modifier = Modifier.padding(top = 0.dp)
                                        )
                                }
                        }
                }

                // Continuous line/spacer
                if (!isLast) {
                        Row(Modifier.fillMaxWidth().height(4.dp)) {
                                Box(Modifier.width(timeColumnWidth))
                                Box(Modifier.width(16.dp).fillMaxHeight()) {
                                        TimelineLine(
                                                color = neutralColor,
                                                isPast = isBottomSegmentPast,
                                                modifier = Modifier.fillMaxSize()
                                        )
                                }
                        }
                } else {
                        Spacer(modifier = Modifier.height(4.dp))
                }
        }
}

@Composable
fun TimelineLine(color: Color, isPast: Boolean, modifier: Modifier = Modifier) {
        Canvas(modifier = modifier) {
                val wCenter = size.width / 2
                val lineWidth = if (isPast) 1.dp.toPx() else 2.dp.toPx()
                val useColor = if (isPast) color.copy(alpha = 0.5f) else color

                drawRect(
                        color = useColor,
                        topLeft = Offset(wCenter - lineWidth / 2, 0f),
                        size = androidx.compose.ui.geometry.Size(lineWidth, size.height)
                )
        }
}

@Composable
fun TripProgressIndicator(
        color: Color,
        neutralColor: Color,
        isFirst: Boolean,
        isLast: Boolean,
        isTopPast: Boolean,
        isBottomPast: Boolean,
        modifier: Modifier = Modifier,
        dotOffset: androidx.compose.ui.unit.Dp? = null,
        isAtStop: Boolean = false,
        movingDotProgress: Float? = null
) {
        val isDark = isSystemInDarkTheme()
        Canvas(modifier = modifier) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val wCenter = canvasWidth / 2

                // Determine Vertical Center for Dot and Split Point
                val hCenter = if (dotOffset != null) dotOffset.toPx() else canvasHeight / 2

                val activeLineWidth = 2.dp.toPx()
                val pastLineWidth = 1.dp.toPx()

                // Top line
                if (!isFirst) {
                        val topColor =
                                if (isTopPast) neutralColor.copy(alpha = 0.5f) else neutralColor
                        val lineWidth = if (isTopPast) pastLineWidth else activeLineWidth

                        drawRect(
                                color = topColor,
                                topLeft = Offset(wCenter - lineWidth / 2, 0f),
                                size = androidx.compose.ui.geometry.Size(lineWidth, hCenter)
                        )
                }

                // Bottom line
                if (!isLast) {
                        val bottomColor =
                                if (isBottomPast) neutralColor.copy(alpha = 0.5f) else neutralColor
                        val lineWidth = if (isBottomPast) pastLineWidth else activeLineWidth

                        drawRect(
                                color = bottomColor,
                                topLeft = Offset(wCenter - lineWidth / 2, hCenter),
                                size =
                                        androidx.compose.ui.geometry.Size(
                                                lineWidth,
                                                canvasHeight - hCenter
                                        )
                        )
                }

                // Center circle (Dot)
                val dotRadius = 4.dp.toPx()
                val center = Offset(wCenter, hCenter)
                val dotNeutralColor =
                        if (isTopPast) neutralColor.copy(alpha = 0.5f) else neutralColor

                // Always Neutral Circle
                if (isDark) {
                        // Dark mode: Black fill, neutral ring, black border around it
                        val borderStroke = 1.dp.toPx()
                        val ringStroke = 2.dp.toPx()

                        // Draw outer black border
                        drawCircle(
                                color = Color.Black,
                                radius = dotRadius + (borderStroke / 2) + (ringStroke / 2),
                                center = center,
                                style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = borderStroke
                                        )
                        )

                        // Fill black (to cover the line passing through)
                        drawCircle(color = Color.Black, radius = dotRadius, center = center)

                        // Draw neutral ring
                        drawCircle(
                                color = dotNeutralColor,
                                radius = dotRadius,
                                center = center,
                                style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = ringStroke
                                        )
                        )
                } else {
                        // Light mode: White dot, neutral stroke
                        drawCircle(color = Color.White, radius = dotRadius, center = center)
                        drawCircle(
                                color = dotNeutralColor,
                                radius = dotRadius,
                                center = center,
                                style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = 2.dp.toPx()
                                        )
                        )
                }

                // Pulsing logic for "At Stop"
                if (isAtStop) {
                        drawCircle(color = color, radius = dotRadius * 0.8f, center = center)
                }

                // Moving Dot Interpolation
                if (movingDotProgress != null) {
                        // progress is 0.0 to 1.0 representing travel from THIS stop to NEXT stop.
                        // In the UI, the line goes from `hCenter` (this dot) to `size.height +
                        // nextDotOffset?`
                        // But we only render within this item's box.
                        // The line segment for "next" stop starts at hCenter and goes down to
                        // size.height.
                        // Actually, the "Pearl Chain" column in `StopListItem` is
                        // `fillMaxHeight()`.
                        // The next stop's dot is in the NEXT item.
                        // So we need to compute where the dot is relative to THIS item's height.

                        // Svelte logic: top: calc(${movingDotProgress * 100}% + 0.25rem);
                        // This suggests it moves relative to the segment height.
                        // In Compose, `TripProgressIndicator` is inside a Box of height
                        // `IntrinsicSize.Min`
                        // (the row height).
                        // But wait, the row height is just the stop info. The "line" connects
                        // stops.
                        // The `TripProgressIndicator` draws the line from top to bottom of THIS
                        // row.
                        // If the vehicle is between this stop and the next, it should appear on the
                        // bottom half
                        // of the line?
                        // No, strictly speaking:
                        // Stop N (Line spans from Top to Bottom of Stop N row).
                        // Stop N+1 (Line spans from Top to Bottom of Stop N+1 row).
                        // The visual gap between stops is represented by the height of the rows +
                        // any spacers.
                        // Our logic `movingDotSegmentIdx = i` means vehicle is between Stop i and
                        // Stop i+1.
                        // Visually, it should slide from Stop i's dot down to Stop i+1's dot.
                        // BUT `TripProgressIndicator` only knows about its own bounds.
                        // If the row for Stop i is 100px tall, and Stop i+1 is 100px tall.
                        // And Spacer is 0.
                        // Current `TripProgressIndicator` draws:
                        // Top Line: Top to hCenter (Dot)
                        // Bottom Line: hCenter to Bottom.

                        // So if progress = 0 (Departing Stop i), dot is at hCenter.
                        // If progress = 1 (Arriving Stop i+1), dot should be at Stop i+1's hCenter.
                        // Since we clip to `TripProgressIndicator` bounds, we can only draw until
                        // `size.height`.
                        // We can't draw into the next item's canvas easily.

                        // HOWEVER, we are inside a `LazyColumn`.
                        // Svelte implementation uses `absolute` positioning on the " Pearl Chain
                        // Column" which
                        // might span?
                        // checking Svelte again...
                        // It puts the dot in the `td` for the segment.
                        // `style={background-color: ...; top: calc(${moving_dot_progress * 100}% +
                        // 0.25rem);
                        // ...}`
                        // It seems it thinks the segment is just that `td`.
                        // If the `td` height covers the whole gap, then yes.

                        // In Compose:
                        // `TripProgressIndicator` fills the `Timeline (Dot + Lines)` Box which is
                        // `fillMaxHeight()` of the Row.
                        // The Row contains the Stop info.
                        // So the height of `TripProgressIndicator` is roughly the height of the
                        // StopItem.
                        // The distance from Stop i to Stop i+1 is effectively the height of Stop i
                        // (center to
                        // bottom) + height of Stop i+1 (top to center)?
                        // Actually, `StopListItem` is the whole row.
                        // So `TripProgressIndicator` covers the whole row.
                        // Dot is at `dotOffset` (16.dp = 32.dp/2).
                        // `hCenter` is `dotOffset`.
                        // Bottom of this canvas is `size.height`.
                        // The next dot is at `nextItem.hCenter` (which is relative to next item).
                        // So the distance is `(size.height - hCenter) + (nextItem.hCenter)`.
                        // Roughly `size.height`. (Assuming uniform height and centering).

                        // Let's approximate:
                        // We draw the dot at `hCenter + progress * (size.height)`.
                        // This will look like it moves from the stop dot down to the bottom of the
                        // row.
                        // Once it passes the bottom, it's "in" the next row (conceptually).
                        // But simpler: Svelte uses `top: progress * 100%`.
                        // If we assume the visual segment is just this row's representation...
                        // Let's try drawing at `hCenter + movingDotProgress * (size.height -
                        // hCenter +
                        // something?)`
                        // Or just `hCenter + movingDotProgress * (someDist)`.
                        // If we draw strictly within bounds, the dot disappears when it hits
                        // bottom.
                        // But the next one takes over? No, `movingDotSegmentIdx` is only `i`.
                        // So when `i` becomes `i+1`, progress resets to 0 (Departing i+1).
                        // Wait, `lastDepartedIdx` is `i`.
                        // So `progress` 0 -> 1 is explicitly travel from `i` to `i+1`.
                        // If we only draw in `i`, we need to cover the full visual distance to
                        // `i+1`.
                        // Since `LazyColumn` clips items? Actually `Canvas` clips by default? No,
                        // `drawRect`
                        // doesn't clip unless `clipToBounds` modifier is used.
                        // `TripProgressIndicator` logic:
                        // `drawCircle` at `Offset(wCenter, hCenter + movingDotProgress *
                        // (totalDistanceToNextDot))`
                        // `totalDistanceToNextDot` is roughly `size.height` (if rows are equal and
                        // dot is
                        // centered or fixed from top).
                        // Since dot is fixed offset from top (`dotOffset`=16dp), and item height is
                        // variable...
                        // distance = (size.height - dotOffset) + (nextDotOffset).
                        // nextDotOffset is also 16dp.
                        // So distance = size.height.
                        // So `y = hCenter + movingDotProgress * size.height`?
                        // if progress=0, y = hCenter. Correct.
                        // if progress=1, y = hCenter + size.height = 16dp + height.
                        // The next dot is at 16dp from NEXT top.
                        // Start of next top is `size.height` of this one.
                        // So 16dp into next one is `size.height + 16dp`.
                        // So target Y is `size.height + 16dp`.
                        // Current Y is `16dp`.
                        // Delta is `size.height`.
                        // So yes, `y = hCenter + movingDotProgress * size.height`.

                        val targetY = hCenter + movingDotProgress * size.height
                        drawCircle(
                                color = color,
                                radius =
                                        dotRadius * 1.0f, // Make "moving" dot slightly distinctive?
                                // Svelte uses
                                // size similar to normal dot?
                                center = Offset(wCenter, targetY)
                        )
                        // Pulse ring
                        drawCircle(
                                color = color,
                                radius = dotRadius,
                                center = Offset(wCenter, targetY),
                                style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = 2.dp.toPx()
                                        )
                        )
                }
        }
}

@Composable
fun SbbTimeBlock(
        rt: Long?,
        scheduled: Long?,
        tz: String,
        isInactive: Boolean,
        showSeconds: Boolean
) {
        // Logic:
        // If RT is present and differs from Scheduled:
        //   Show Scheduled (small, crossed out)
        //   Show RT (bold)
        //   Show DelayDiff
        // If RT == Scheduled or only Scheduled:
        //   Show Scheduled (bold)

        val hasDelay = (rt != null && scheduled != null && rt != scheduled)
        val diff = if (hasDelay) rt!! - scheduled!! else 0L

        Column(horizontalAlignment = Alignment.End) {
                if (hasDelay && scheduled != null) {
                        // Scheduled Time (Small)
                        FormattedTimeText(
                                timezone = tz,
                                timeSeconds = scheduled,
                                showSeconds = false,
                                color =
                                        if (isInactive) Color.Gray
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                textDecoration = TextDecoration.LineThrough
                        )
                }

                // Realtime or Main Time
                val timeToShow = rt ?: scheduled
                if (timeToShow != null) {
                        val color =
                                if (isInactive) Color.Gray
                                else if (hasDelay) {
                                        // Colorize time based on delay?
                                        // Usually time is black/white, delay is colored.
                                        // But let's follow local convention or SBB.
                                        // SBB uses black for time, red for delay text.
                                        MaterialTheme.colorScheme.onSurface
                                } else {
                                        MaterialTheme.colorScheme.onSurface
                                }

                        FormattedTimeText(
                                timezone = tz,
                                timeSeconds = timeToShow,
                                showSeconds = showSeconds, // User setting applied here
                                color = color,
                                style =
                                        MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                secondsStyle =
                                        MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Normal
                                        )
                        )

                        if (hasDelay) {
                                DelayDiff(
                                        diff = diff,
                                        show_seconds = showSeconds,
                                        fontSizeOfPolarity = 10.sp,
                                        use_symbol_sign = true
                                )
                        }
                }
        }
}
