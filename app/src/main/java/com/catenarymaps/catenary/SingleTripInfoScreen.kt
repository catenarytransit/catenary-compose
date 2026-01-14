// In a new file, e.g., SingleTripInfoScreen.kt
package com.catenarymaps.catenary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.maps.android.PolyUtil.decode as decodePolyutil
import kotlinx.coroutines.delay
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
    val showPreviousStops by viewModel.showPreviousStops.collectAsState()
    val lastInactiveStopIdx by viewModel.lastInactiveStopIdx.collectAsState()
    val vehicleData by viewModel.vehicleData.collectAsState()

    // NEW: build stop -> connection chips map
    val stopConnections =
            remember(tripData) {
                val result = mutableMapOf<String, List<StopConnectionChip>>()
                val connectionsPerStop = tripData?.connectionsPerStop ?: emptyMap()
                val connectingRoutes = tripData?.connectingRoutes ?: emptyMap()

                for ((stopId, perChateau) in connectionsPerStop) {
                    val connectingRoutesList = mutableListOf<Pair<ConnectingRoute, String>>()
                    for ((chateauId, routeIds) in perChateau) {
                        val routesForChateau = connectingRoutes[chateauId] ?: continue
                        for (routeId in routeIds) {
                            routesForChateau[routeId]?.let {
                                connectingRoutesList.add(it to chateauId)
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
                                compareBy<Pair<ConnectingRoute, String>> {
                                    typeOrder[it.first.routeType] ?: 5
                                }
                                        .thenComparator { a, b ->
                                            val aName = a.first.shortName ?: a.first.longName ?: ""
                                            val bName = b.first.shortName ?: b.first.longName ?: ""

                                            val aNameAsInt = aName.toIntOrNull()
                                            val bNameAsInt = bName.toIntOrNull()

                                            if (aNameAsInt != null && bNameAsInt != null) {
                                                aNameAsInt.compareTo(bNameAsInt)
                                            } else {
                                                aName.compareTo(bName)
                                            }
                                        }
                        )

                        result[stopId] =
                                connectingRoutesList.map { (route, chateauId) ->
                                    StopConnectionChip(
                                            shortName = route.shortName,
                                            longName = route.longName,
                                            color = route.color,
                                            textColor = route.textColor,
                                            chateauId = chateauId
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
                val routeName = (data.route_short_name ?: "") + (data.route_long_name ?: "")

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
                    put("text_color", JsonPrimitive(data.text_color ?: "#000000"))
                    put("route_label", JsonPrimitive(data.route_short_name ?: data.route_long_name))
                }
                val feature = Feature(geometry = LineString(coordinates), properties = properties)
                transitShapeSource.value.setData(
                        GeoJsonData.Features(FeatureCollection(listOf(feature)))
                )
            } else {
                transitShapeSource.value.setData(
                        GeoJsonData.Features(
                                FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
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
                val feature = Feature(geometry = LineString(coordinates), properties = properties)
                transitShapeDetourSource.value.setData(
                        GeoJsonData.Features(FeatureCollection(listOf(feature)))
                )
            } else {
                transitShapeDetourSource.value.setData(
                        GeoJsonData.Features(
                                FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
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
                            put("chateau", JsonPrimitive(tripSelected.chateau_id))
                            put("stop_route_type", JsonPrimitive(data.route_type))
                            put("cancelled", JsonPrimitive(stopTime.raw.schedule_relationship == 1))
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
            stopsContextSource.value.setData(GeoJsonData.Features(FeatureCollection(stopFeatures)))

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
                            FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
                    )
            )
            transitShapeDetourSource.value.setData(
                    GeoJsonData.Features(
                            FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
                    )
            )
            stopsContextSource.value.setData(
                    GeoJsonData.Features(
                            FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
                    )
            )
            majorDotsSource.value.setData(
                    GeoJsonData.Features(
                            FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
                    )
            )
        }
    }

    // --- UI Rendering ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        } else if (error != null) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
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
                                            chateau_id = tripSelected.chateau_id,
                                            route_id = data.route_id
                                    )
                            )
                        }
                    },
                    controls = { NavigationControls(onBack = onBack, onHome = onHome) },
                    headsign = data.trip_headsign
            )

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

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    // horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                if (data.vehicle?.label != null || data.vehicle?.id != null) {
                    Text(
                            text =
                                    "${stringResource(id = R.string.vehicle)}: ${data.vehicle.label ?: data.vehicle.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    // Spacer(modifier = Modifier.weight(1f)) // push block id to the right
                }

                // Clickable Block ID
                if (data.block_id != null && data.service_date != null) {
                    Text(
                            text = "${stringResource(id = R.string.block)}: ${data.block_id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier =
                                    Modifier.clickable {
                                        onBlockClick(
                                                CatenaryStackEnum.BlockStack(
                                                        chateau_id = tripSelected.chateau_id,
                                                        block_id = data.block_id,
                                                        service_date = data.service_date
                                                )
                                        )
                                    }
                    )
                }
            }

            Column() { // Clickable Vehicle Label
                if (data.vehicle?.label != null || data.vehicle?.id != null) {
                    VehicleInfo(
                            label = data.vehicle.label ?: data.vehicle.id ?: "",
                            chateau = tripSelected.chateau_id,
                            routeId = data.route_id
                    )
                }

                if (vehicleData != null) {
                    VehicleInfoDetails(vehicleData = vehicleData!!, usUnits = usUnits)
                }
            }

            //                    // Show/Hide Previous Stops Button
            //                    if (showPreviousStops) {
            //                        if (lastInactiveStopIdx > -1) {
            //                            Button(onClick = { viewModel.toggleShowPreviousStops() })
            // {
            //                                Text(
            //                                    if (showPreviousStops) stringResource(id =
            // R.string.single_trip_info_screen_hide_previous_stops)
            //                                    else stringResource(
            //                                        id =
            // R.string.single_trip_info_screen_show_previous_stops,
            //                                        lastInactiveStopIdx + 1
            //                                    )
            //                                )
            //                            }
            //                        }
            //                    }

            var alertsExpanded by remember { mutableStateOf(false) }

            // Stop List
            val lazyListState = rememberLazyListState()
            LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // .windowInsetsBottomHeight(WindowInsets(bottom =
                                    // WindowInsets.safeDrawing.getBottom(density =
                                    // LocalDensity.current)))
                                    .windowInsetsPadding(
                                        WindowInsets(
                                            bottom =
                                                WindowInsets.safeDrawing.getBottom(
                                                    density = LocalDensity.current
                                                )
                                        )
                                    ),
                    state = lazyListState
            ) {
                if (data.alert_id_to_alert.isNotEmpty()) {
                    item(key = "alerts-box") {
                        AlertsBox(
                                alerts = data.alert_id_to_alert,
                                default_tz = data.tz,
                                chateau = tripSelected.chateau_id,
                                isScrollable = false,
                                expanded = alertsExpanded,
                                onExpandedChange = { alertsExpanded = !alertsExpanded }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                item(key = "show-previous-stops") {
                    if (lastInactiveStopIdx > -1) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(52.dp))
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .fillMaxHeight()
                            ) {
                                val tripColor =
                                    remember(data.color) {
                                        try {
                                            Color(
                                                android.graphics.Color.parseColor(
                                                    data.color ?: "#808080"
                                                )
                                            )
                                        } catch (e: Exception) {
                                            Color.Gray
                                        }
                                    }

                            }
                            Button(
                                onClick = { viewModel.toggleShowPreviousStops() },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                modifier =
                                    Modifier
                                        .defaultMinSize(
                                            minHeight = 1.dp,
                                            minWidth = 1.dp
                                        )
                                        .padding(
                                            start = 8.dp
                                        ) // remove default button padding
                            ) {
                                Text(
                                    text =
                                        if (showPreviousStops)
                                            "↑ " +
                                                    stringResource(
                                                        id =
                                                            R.string
                                                                .single_trip_info_screen_hide_previous_stops
                                                    )
                                        else
                                            "↓ " +
                                                    stringResource(
                                                        id =
                                                            R.string
                                                                .single_trip_info_screen_show_previous_stops,
                                                        lastInactiveStopIdx + 1
                                                    )
                                )
                            }
                        }
                    }
                }

                itemsIndexed(stopTimes) { i, stopTime ->
                    if (showPreviousStops ||
                                    i > lastInactiveStopIdx ||
                                    (i == stopTimes.lastIndex && stopTimes.isNotEmpty())
                    ) {
                        // Calculate new state variables
                        val isInactive = i <= lastInactiveStopIdx
                        val isPreviousInactive = i - 1 == lastInactiveStopIdx
                        val connections = stopConnections[stopTime.raw.stop_id]

                        StopListItem(
                                stopTime = stopTime,
                                tripColorStr = data.color ?: "#808080",
                                isFirst = i == 0,
                                isLast = i == stopTimes.lastIndex,
                                isInactive = isInactive,
                                isPreviousInactive = isPreviousInactive,
                                showPreviousStops = showPreviousStops,
                                connections = connections,
                                onStopClick = {
                                    onStopClick(
                                            CatenaryStackEnum.StopStack(
                                                    chateau_id = tripSelected.chateau_id,
                                                    stop_id = stopTime.raw.stop_id
                                            )
                                    )
                                },
                            showSeconds = showSeconds
                        )
                    }
                }
            }
        }
    }
}

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
            var currentTime by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
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
                    Text(text = speedText, style = MaterialTheme.typography.labelSmall)
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
                Text(text = "$percentage%", style = MaterialTheme.typography.labelSmall)
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
        isPreviousInactive: Boolean,
        showPreviousStops: Boolean,
        onStopClick: () -> Unit,
        showSeconds: Boolean,
        connections: List<StopConnectionChip>? = null,
) {
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
        remember(stopTime, isDoubleTime, showSeconds) {
            val list = mutableListOf<@Composable () -> Unit>()

            if (isDoubleTime) {
                // ARRIVAL (All arrival info goes above)
                val arrTime = rtArrival ?: schedArr ?: 0L
                val arrDelay =
                    if (rtArrival != null && schedArr != null) rtArrival - schedArr else 0L
                val arrHasDelay = arrDelay != 0L

                list.add {
                    Column(horizontalAlignment = Alignment.End) {
                        FormattedTimeText(
                            timezone = tz,
                            timeSeconds = arrTime,
                            showSeconds = showSeconds,
                            color =
                                if (isInactive) Color.Gray
                                else MaterialTheme.colorScheme.onSurface,
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal
                                ) // Non-bold for arrival, same size as dep
                        )
                        if (arrHasDelay) {
                            DelayDiff(
                                diff = arrDelay,
                                show_seconds = showSeconds,
                                fontSizeOfPolarity = 10.sp,
                                use_symbol_sign = true,
                                modifier = Modifier.offset(y = (-4).dp)
                            )
                        }
                    }
                }
            }
            list
        }

    val mainContent: (@Composable () -> Unit)? =
        remember(stopTime, isDoubleTime, showSeconds) {
            val headerHeightLocal = 32.dp
            if (isDoubleTime) {
                // DEPARTURE
                val depTime = rtDeparture ?: schedDep ?: 0L
                val depDelay =
                    if (rtDeparture != null && schedDep != null) rtDeparture - schedDep
                    else 0L
                val depHasDelay = depDelay != 0L

                {
                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier.height(headerHeightLocal),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            FormattedTimeText(
                                timezone = tz,
                                timeSeconds = depTime,
                                showSeconds = showSeconds,
                                color =
                                    if (isInactive) Color.Gray
                                    else MaterialTheme.colorScheme.onSurface,
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                            )
                        }
                        if (depHasDelay) {
                            DelayDiff(
                                diff = depDelay,
                                show_seconds = showSeconds,
                                fontSizeOfPolarity = 10.sp,
                                use_symbol_sign = true,
                                modifier = Modifier.offset(y = (-4).dp)
                            )
                        }
                    }
                }
            } else {
                // UNIFIED
                val unifiedRt = rtDeparture ?: rtArrival
                val unifiedSched = schedDep ?: schedArr
                val timeToShow = unifiedRt ?: unifiedSched

                val delay =
                    if (unifiedRt != null && unifiedSched != null) unifiedRt - unifiedSched
                    else 0L
                val hasDelay = delay != 0L

                if (timeToShow != null) {
                    {
                        Column(horizontalAlignment = Alignment.End) {
                            Box(
                                modifier = Modifier.height(headerHeightLocal),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                FormattedTimeText(
                                    timezone = tz,
                                    timeSeconds = timeToShow,
                                    showSeconds = showSeconds,
                                    color =
                                        if (isInactive) Color.Gray
                                        else MaterialTheme.colorScheme.onSurface,
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                )
                            }
                            if (hasDelay) {
                                DelayDiff(
                                    diff = delay,
                                    show_seconds = showSeconds,
                                    fontSizeOfPolarity = 10.sp,
                                    use_symbol_sign = true,
                                    modifier = Modifier.offset(y = (-4).dp)
                                )
                            }
                        }
                    }
                } else {
                    null
                }
            }
        }

    // Define a consistent header height for the "Station Name" row
    val headerHeight = 32.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Render Above Rows
        aboveContent.forEach { content ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Bottom
            ) {
                // Time
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .padding(end = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) { content() }

                // Timeline (Line Only)
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                ) {
                    if (!isFirst) {
                        TimelineLine(
                            color = tripColor,
                            isPreviousInactive = isPreviousInactive,
                            showPreviousStops = showPreviousStops,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Content Spacer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    // Empty
                }
            }
        }

        // 2. Render Main Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // Time
            // We constrain the time to the header height to align with the dot and name
            Box(
                modifier =
                    Modifier
                        .width(52.dp)
                        // .height(headerHeight) // Removed to allow delay expansion
                        .padding(end = 4.dp),
                contentAlignment = Alignment.TopEnd // Align content to top (time inside)
            ) { mainContent?.invoke() }

            // Timeline (Dot + Lines)
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight()
            ) {
                TripProgressIndicator(
                    color = tripColor,
                    isFirst = isFirst, // Standard Dot Logic
                    isLast = isLast,
                    isInactive = isInactive,
                    isPreviousInactive = isPreviousInactive,
                    showPreviousStops = showPreviousStops,
                    modifier = Modifier.fillMaxSize(),
                    // Center dot in the headerHeight
                    dotOffset = headerHeight / 2
                )
            }

            // Content (Station Name + Platform)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Normal,
                            modifier =
                                Modifier
                                    .clickable(onClick = onStopClick)
                                    .weight(
                                        1f,
                                        fill = false
                                    ), // Don't push platform off if name is short
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
                                .replace("Track", "", ignoreCase = true)
                                .replace("Platform", "", ignoreCase = true)
                                .trim()

                        Text(
                            text = platformText,
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!connections.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) { connections.forEach { conn -> CompactTransferRouteChip(conn) } }
                }
            }
        }

        // Continuous line/spacer
        if (!isLast) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            ) {
                Box(Modifier.width(52.dp))
                Box(
                    Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                ) {
                    TimelineLine(
                        color = tripColor,
                        isPreviousInactive = false,
                        showPreviousStops = showPreviousStops,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun TimelineLine(
    color: Color,
    isPreviousInactive: Boolean,
    showPreviousStops: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val wCenter = size.width / 2
        val lineWidth = 4.dp.toPx()
        val fadedColor = if (showPreviousStops) color.copy(alpha = 0.4f) else Color.Transparent

        if (isPreviousInactive) {
            drawRect(
                brush = Brush.verticalGradient(colors = listOf(fadedColor, color)),
                topLeft = Offset(wCenter - lineWidth / 2, 0f),
                size = androidx.compose.ui.geometry.Size(lineWidth, size.height)
            )
        } else {
            drawRect(
                color = color,
                topLeft = Offset(wCenter - lineWidth / 2, 0f),
                size = androidx.compose.ui.geometry.Size(lineWidth, size.height)
            )
        }
    }
}

@Composable
fun TripProgressIndicator(
        color: Color,
        isFirst: Boolean,
        isLast: Boolean,
        isInactive: Boolean,
        isPreviousInactive: Boolean,
        showPreviousStops: Boolean,
        modifier: Modifier = Modifier,
        dotOffset: androidx.compose.ui.unit.Dp? = null
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val wCenter = canvasWidth / 2

        // Determine Vertical Center for Dot and Split Point
        val hCenter = if (dotOffset != null) dotOffset.toPx() else canvasHeight / 2

        val lineWidth = 4.dp.toPx()

        val circleColor = if (isInactive) Color.Gray else Color.White
        val strokeColor = if (isInactive) Color.DarkGray else color

        // This is the logic from the JS app. It's the color used for the line segment
        // *above* an inactive stop when previous stops are shown.
        // Faded if showing previous stops, transparent if not
        val fadedColor = if (showPreviousStops) color.copy(alpha = 0.4f) else Color.Transparent

        // Top line
        if (!isFirst) {
            val topColor = color // Always colored strip

            // If we want gradient for isPreviousInactive, we need Brush.verticalGradient
            if (isPreviousInactive) {
                drawRect(
                        brush = Brush.verticalGradient(colors = listOf(fadedColor, color)),
                    topLeft = Offset(wCenter - lineWidth / 2, 0f),
                    size = androidx.compose.ui.geometry.Size(lineWidth, hCenter)
                )
            } else {
                drawRect(
                    color = topColor,
                    topLeft = Offset(wCenter - lineWidth / 2, 0f),
                    size = androidx.compose.ui.geometry.Size(lineWidth, hCenter)
                )
            }
        }

        // Bottom line
        if (!isLast) {
            drawRect(
                color = color, // Always colored strip
                topLeft = Offset(wCenter - lineWidth / 2, hCenter),
                size = androidx.compose.ui.geometry.Size(lineWidth, canvasHeight - hCenter)
            )
        }

        // Center circle (Dot)
        val dotRadius = 4.dp.toPx()
        val center = Offset(wCenter, hCenter)

        if (isInactive) {
            drawCircle(color = Color.Gray, radius = dotRadius, center = center)
        } else {
            // Active dot (White with colored stroke?)
            drawCircle(color = Color.White, radius = dotRadius, center = center)
            drawCircle(
                color = color,
                radius = dotRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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

@Composable
fun CompactTransferRouteChip(conn: StopConnectionChip) {
    // Copied and shrunk from RouteScreen.kt
    // Logic for RATP/MTA icons should ideally be reused but for now we won't import RouteScreen
    // internals if they are private.
    // Assuming TransferRouteChip in RouteScreen.kt uses Utils we can access.
    // Wait, TransferRouteChip was NOT private in RouteScreen.kt but we want a compact version.

    // Note: If TransferRouteChip is public in RouteScreen.kt, we can't easily modify its internals
    // without changing that file.
    // We will reimplement a compact version here.

    val isRatp = RatpUtils.isIdfmChateau(conn.chateauId) && RatpUtils.isRatpRoute(conn.shortName)
    val isMta =
        MtaSubwayUtils.MTA_CHATEAU_ID == conn.chateauId &&
                !conn.shortName.isNullOrEmpty() &&
                MtaSubwayUtils.isSubwayRouteId(conn.shortName!!)

    val modifier = Modifier.height(16.dp) // Smaller height

    if (isRatp) {
        val iconUrl = RatpUtils.getRatpIconUrl(conn.shortName)
        if (iconUrl != null) {
            val context = LocalContext.current
            val imageLoader =
                androidx.compose.runtime.remember(context) {
                    coil.ImageLoader.Builder(context)
                        .components { add(coil.decode.SvgDecoder.Factory()) }
                        .build()
                }
            AsyncImage(
                model = ImageRequest.Builder(context).data(iconUrl).crossfade(true).build(),
                imageLoader = imageLoader,
                contentDescription = conn.shortName,
                modifier = modifier.padding(horizontal = 1.dp)
            )
            return
        }
    } else if (isMta) {
        val iconUrl = MtaSubwayUtils.getMtaIconUrl(conn.shortName!!)
        if (iconUrl != null) {
            val context = LocalContext.current
            val imageLoader =
                androidx.compose.runtime.remember(context) {
                    coil.ImageLoader.Builder(context)
                        .components { add(coil.decode.SvgDecoder.Factory()) }
                        .build()
                }
            AsyncImage(
                model = ImageRequest.Builder(context).data(iconUrl).crossfade(true).build(),
                imageLoader = imageLoader,
                contentDescription = conn.shortName,
                modifier = modifier.padding(horizontal = 1.dp)
            )
            return
        } else {
            // Mta Fallback
            val mtaColor = MtaSubwayUtils.getMtaSubwayColor(conn.shortName!!)
            val symbolShortName = MtaSubwayUtils.getMtaSymbolShortName(conn.shortName)
            Box(
                modifier = modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(mtaColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = symbolShortName,
                    color = Color.White,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return
        }
    }

    val bgColor =
        remember(conn.color) {
            try {
                Color(android.graphics.Color.parseColor(conn.color ?: "#cccccc"))
            } catch (e: Exception) {
                Color.Gray
            }
        }
    val fgColor =
        remember(conn.textColor) {
            try {
                Color(android.graphics.Color.parseColor(conn.textColor ?: "#000000"))
            } catch (e: Exception) {
                Color.Black
            }
            }

    Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(bgColor)
                    .padding(horizontal = 2.dp, vertical = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = conn.shortName ?: conn.longName?.replace(" Line", "") ?: "",
            color = fgColor,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
