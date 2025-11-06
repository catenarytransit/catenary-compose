// In a new file, e.g., SingleTripInfoScreen.kt
package com.catenarymaps.catenary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dellisd.spatialk.geojson.FeatureCollection
import io.github.dellisd.spatialk.geojson.LineString
import io.github.dellisd.spatialk.geojson.Position
import kotlinx.serialization.json.JsonElement
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import com.google.maps.android.PolyUtil.decode as decodePolyutil
import io.github.dellisd.spatialk.geojson.Feature
import io.github.dellisd.spatialk.geojson.Point
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.maps.android.PolyUtil.decode as decodePolyutil
import kotlinx.serialization.json.buildJsonObject
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.analytics.GoogleAnalytics
import com.google.android.gms.analytics.HitBuilders
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SingleTripInfoScreen(
    tripSelected: CatenaryStackEnum.SingleTrip,
    onStopClick: (CatenaryStackEnum.StopStack) -> Unit,
    onBlockClick: (CatenaryStackEnum.BlockStack) -> Unit,
    // --- Map sources passed from MainActivity ---
    transitShapeSource: MutableState<GeoJsonSource>,
    transitShapeDetourSource: MutableState<GeoJsonSource>,
    stopsContextSource: MutableState<GeoJsonSource>,
    // --- State to control other map layers ---
    onSetStopsToHide: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, "SingleTripInfoScreen")
                //param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
            }
        } catch (e: Exception) {
            // Log the error or handle it gracefully
            android.util.Log.e("GA", "Failed to log screen view", e)
        }
    }

    val viewModel: SingleTripViewModel = viewModel(
        key = "${tripSelected.chateau_id}-${tripSelected.trip_id}-${tripSelected.start_time}-${tripSelected.start_date}",
        factory = SingleTripViewModel.factory(
            tripSelected
        )
    )

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val tripData by viewModel.tripData.collectAsState()
    val stopTimes by viewModel.stopTimes.collectAsState()
    val showPreviousStops by viewModel.showPreviousStops.collectAsState()
    val lastInactiveStopIdx by viewModel.lastInactiveStopIdx.collectAsState()

    // --- Map Update Logic ---
    LaunchedEffect(tripData) {
        val data = tripData
        if (data != null) {
            // Update trip shape
            if (data.shape_polyline != null) {
                val coordinates =
                    decodePolyutil(data.shape_polyline).map { Position(it.longitude, it.latitude) }
                val properties = buildJsonObject {
                    put("color", JsonPrimitive(data.color ?: "#FFFFFF"))
                    put("text_color", JsonPrimitive(data.text_color ?: "#000000"))
                    put(
                        "route_label",
                        JsonPrimitive(data.route_short_name ?: data.route_long_name)
                    )
                }
                val feature = Feature(geometry = LineString(coordinates), properties = properties)
                transitShapeSource.value.setData(
                    GeoJsonData.Features(
                        FeatureCollection(
                            listOf(
                                feature
                            )
                        )
                    )
                )
            } else {
                transitShapeSource.value.setData(GeoJsonData.Features(FeatureCollection(emptyList())))
            }

            // Update detour shape
            if (data.old_shape_polyline != null) {
                val coordinates = decodePolyutil(data.old_shape_polyline).map {
                    Position(
                        it.longitude,
                        it.latitude
                    )
                }
                val properties = buildJsonObject {
                    put("color", JsonPrimitive(data.color ?: "#FFFFFF"))
                }
                val feature = Feature(geometry = LineString(coordinates), properties = properties)
                transitShapeDetourSource.value.setData(
                    GeoJsonData.Features(
                        FeatureCollection(
                            listOf(
                                feature
                            )
                        )
                    )
                )
            } else {
                transitShapeDetourSource.value.setData(
                    GeoJsonData.Features(
                        FeatureCollection(
                            emptyList()
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
            val stopFeatures = stopTimes.mapNotNull { stopTime ->
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
                    geometry = Point(
                        Position(stopTime.raw.longitude, stopTime.raw.latitude)
                    ), properties = properties
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
            transitShapeSource.value.setData(GeoJsonData.Features(FeatureCollection(emptyList())))
            transitShapeDetourSource.value.setData(GeoJsonData.Features(FeatureCollection(emptyList())))
            stopsContextSource.value.setData(GeoJsonData.Features(FeatureCollection(emptyList())))
        }
    }


    // --- UI Rendering ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        } else if (error != null) {
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        } else if (tripData != null) {
            val data = tripData!!

            // TODO: Port your RouteHeading.svelte component here
            RouteHeadingStub(data)

            // Clickable Block ID
            if (data.block_id != null && data.service_date != null) {
                Text(
                    text = "Block: ${data.block_id}",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
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

            // TODO: Add VehicleInfo.svelte content here
            // TODO: Add AlertBox.svelte content here

            // Show/Hide Previous Stops Button
            if (lastInactiveStopIdx > -1) {
                Button(onClick = { viewModel.toggleShowPreviousStops() }) {
                    Text(if (showPreviousStops) "Hide previous stops" else "Show ${lastInactiveStopIdx + 1} previous stops")
                }
            }

            // Stop List
            val lazyListState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    //.windowInsetsBottomHeight(WindowInsets(bottom = WindowInsets.safeDrawing.getBottom(density = LocalDensity.current)))
                    .windowInsetsPadding(
                        WindowInsets(
                            bottom = WindowInsets.safeDrawing.getBottom(
                                density = LocalDensity.current
                            )
                        )
                    ),
                state = lazyListState
            ) {
                itemsIndexed(stopTimes) { i, stopTime ->
                    if (showPreviousStops || i > lastInactiveStopIdx) {
                        // Calculate new state variables
                        val isInactive = i <= lastInactiveStopIdx
                        val isPreviousInactive = i - 1 == lastInactiveStopIdx

                        StopListItem(
                            stopTime = stopTime,
                            tripColorStr = data.color ?: "#808080",
                            isFirst = i == 0,
                            isLast = i == stopTimes.lastIndex,
                            isInactive = isInactive,
                            isPreviousInactive = isPreviousInactive, // <-- ADD THIS
                            showPreviousStops = showPreviousStops,   // <-- ADD THIS
                            onStopClick = {
                                onStopClick(
                                    CatenaryStackEnum.StopStack(
                                        chateau_id = tripSelected.chateau_id,
                                        stop_id = stopTime.raw.stop_id
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
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
    onStopClick: () -> Unit
) {
    val tripColor = try {
        Color(android.graphics.Color.parseColor(tripColorStr))
    } catch (e: Exception) {
        Color.Gray
    }

    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min) // Ensure Row has a minimum height for fillMaxHeight to work
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalAlignment = Alignment.Top // Align items to the top
    ) {
        // 1. Progress Bar (port of the logic)
        TripProgressIndicator(
            color = tripColor,
            isFirst = isFirst,
            isLast = isLast,
            isInactive = isInactive,
            isPreviousInactive = isPreviousInactive, // <-- ADD THIS
            showPreviousStops = showPreviousStops,   // <-- ADD THIS
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
        )

        Spacer(Modifier.width(8.dp))

        // 2. Stop Info
        Column {
            Text(
                text = stopTime.raw.name, // TODO: Port fixStationName
                style = MaterialTheme.typography.bodyLarge,
                color = if (isInactive) Color.Gray else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onStopClick)
            )

            StopTimeNumber(
                tripTimezone = stopTime.raw.timezone,
                stopTime = stopTime,
                showSeconds = false,
                isInactive = isInactive
            )

            if (stopTime.raw.rt_platform_string != null) {
                Text(
                    text = "Platform: ${stopTime.raw.rt_platform_string}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isInactive) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TripProgressIndicator(
    color: Color,
    isFirst: Boolean,
    isLast: Boolean,
    isInactive: Boolean,
    isPreviousInactive: Boolean, // <-- ADDED
    showPreviousStops: Boolean,  // <-- ADDED
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxHeight()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val hCenter = canvasHeight / 2
        val wCenter = canvasWidth / 2

        val boxWidth = canvasWidth / 2

        print("Trip Progress Indicator w: $boxWidth h: $hCenter")

        val circleColor = if (isInactive) Color.Gray else Color.White
        val strokeColor = if (isInactive) Color.DarkGray else color

        // This is the logic from the JS app. It's the color used for the line segment
        // *above* an inactive stop when previous stops are shown.
        // Faded if showing previous stops, transparent if not
        val fadedColor = if (showPreviousStops) color.copy(alpha = 0.4f) else Color.Transparent

        // Top box
        if (!isFirst) {
            if (isPreviousInactive) {
                // This is the "in-between" stop. Draw gradient.
                drawRect(
                    brush = Brush.verticalGradient(colors = listOf(fadedColor, color)),
                    topLeft = Offset(wCenter - boxWidth / 2, 0f),
                    size = androidx.compose.ui.geometry.Size(boxWidth, hCenter)
                )
            } else {
                // This is a normal solid box
                drawRect(
                    // If this stop is inactive, use the faded color for the line above it.
                    // Otherwise, use the regular color.
                    color = if (isInactive) fadedColor else color,
                    topLeft = Offset(wCenter - boxWidth / 2, 0f),
                    size = androidx.compose.ui.geometry.Size(boxWidth, hCenter)
                )
            }
        }

        // Bottom box
        if (!isLast) {
            drawRect(
                color = if (isInactive) fadedColor else color,
                topLeft = Offset(wCenter - boxWidth / 2, hCenter),
                size = androidx.compose.ui.geometry.Size(boxWidth, canvasHeight - hCenter)
            )
        }

        // Center circle
        drawCircle(
            color = circleColor,
            radius = canvasWidth / 2,
            center = center
        )
        drawCircle(
            color = strokeColor,
            radius = canvasWidth / 2,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = canvasWidth / 4)
        )
    }
}

// --- STUBS for components you need to port ---

@Composable
fun RouteHeadingStub(data: TripDataResponse) {
    // TODO: Implement the full RouteHeading.svelte UI
    Text(
        text = data.route_short_name ?: data.route_long_name ?: "Route",
        style = MaterialTheme.typography.headlineMedium,
        color = try {
            Color(android.graphics.Color.parseColor(data.color))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.onSurface
        }
    )
    Text(
        text = "to ${data.trip_headsign}",
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
fun StopTimeNumber(
    tripTimezone: String?,              // trip_data.tz in Svelte
    stopTime: StopTimeCleaned,          // stoptime in Svelte
    showSeconds: Boolean,
    isInactive: Boolean
) {
    // --- Current time ticker (milliseconds) ---
    val tickMillis = if (showSeconds) 1000L else 30_000L
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tickMillis) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(tickMillis)
        }
    }
    val nowSecs = now / 1000

    // --- Pull fields to mirror Svelte shape ---
    val rtArrival = stopTime.rtArrivalTime
    val rtDeparture = stopTime.rtDepartureTime

    val schedArr = stopTime.raw.scheduled_arrival_time_unix_seconds
        ?: stopTime.raw.interpolated_stoptime_unix_seconds
    val schedDep = stopTime.raw.scheduled_departure_time_unix_seconds
        ?: stopTime.raw.interpolated_stoptime_unix_seconds

    // Shared values (when not showing both)
    val sharedRt = rtDeparture ?: rtArrival
    val sharedSched = stopTime.raw.scheduled_departure_time_unix_seconds
        ?: stopTime.raw.scheduled_arrival_time_unix_seconds
        ?: stopTime.raw.interpolated_stoptime_unix_seconds

    // Svelte's calculate_show_both_departure_and_arrival()
    val showBoth = remember(rtArrival, rtDeparture, schedArr, schedDep) {
        when {
            (schedDep != null && schedArr != null && schedDep != schedArr) -> true
            (rtDeparture != null && rtArrival != null && rtDeparture != rtArrival) -> true
            else -> false
        }
    }

    val baseColor = if (isInactive) Color.Gray else MaterialTheme.colorScheme.onSurface
    val tz = (stopTime.raw.timezone ?: tripTimezone) ?: java.time.ZoneId.systemDefault().id

    if ((sharedSched != null) || (sharedRt != null)) {
        if (showBoth) {
            // ---------------- ARRIVAL ----------------
            TimeRow(
                label = "arrival",
                currentTimeSecs = nowSecs,
                rt = rtArrival,
                scheduled = schedArr,
                tz = tz,
                showSeconds = showSeconds,
                isInactive = isInactive,
                textColor = baseColor
            )
            // ---------------- DEPARTURE --------------
            TimeRow(
                label = "departure",
                currentTimeSecs = nowSecs,
                rt = rtDeparture,
                scheduled = schedDep,
                tz = tz,
                showSeconds = showSeconds,
                isInactive = isInactive,
                textColor = baseColor
            )
        } else {
            // ---------------- UNIFIED -----------------
            UnifiedTimeRow(
                currentTimeSecs = nowSecs,
                rt = sharedRt,
                scheduled = sharedSched,
                tz = tz,
                showSeconds = showSeconds,
                isInactive = isInactive,
                textColor = baseColor
            )
        }
    }
}

@Composable
private fun TimeRow(
    label: String,                   // "arrival" | "departure"
    currentTimeSecs: Long,
    rt: Long?,
    scheduled: Long?,
    tz: String,
    showSeconds: Boolean,
    isInactive: Boolean,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: relative time (uses DiffTimer)
        val target = rt ?: scheduled
        if (target != null) {
            DiffTimer(
                diff = (target - currentTimeSecs).toDouble(),
                showBrackets = false,
                showSeconds = showSeconds,
                showDays = false,
                showPlus = false
            )
        }

        // Middle: delay chip (uses DiffTimer for signed offset)
        if (rt != null && scheduled != null) {
            Spacer(Modifier.width(6.dp))
            DelayDiff(diff = rt - scheduled, show_seconds = showSeconds)
        }

        // Right: label + clocks (uses FormattedTimeText)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))

                if (rt != null) {
                    if (scheduled != null && rt == scheduled) {
                        Text("🎯", modifier = Modifier.padding(end = 4.dp))
                    }
                    if (scheduled != null && rt != scheduled) {
                        Box { // Use Box to apply decoration to the child
                            FormattedTimeText(
                                timezone = tz,
                                timeSeconds = scheduled,
                                showSeconds = showSeconds,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    // RT time highlighted
                    FormattedTimeText(
                        timezone = tz,
                        timeSeconds = rt,
                        showSeconds = showSeconds
                    )
                } else if (scheduled != null) {
                    FormattedTimeText(
                        timezone = tz,
                        timeSeconds = scheduled,
                        showSeconds = showSeconds
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedTimeRow(
    currentTimeSecs: Long,
    rt: Long?,
    scheduled: Long?,
    tz: String,
    showSeconds: Boolean,
    isInactive: Boolean,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: relative diff
        val target = rt ?: scheduled
        if (target != null) {
            DiffTimer(
                diff = (target - currentTimeSecs).toDouble(),
                showBrackets = false,
                showSeconds = showSeconds,
                showDays = false,
                showPlus = false
            )
        }

        // Middle: delay chip
        if (rt != null && scheduled != null) {
            Spacer(Modifier.width(6.dp))
            DelayDiff(diff = rt - scheduled, show_seconds = showSeconds)
        }

        // Right: clocks
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rt != null) {
                if (scheduled != null && rt == scheduled) {
                    Text("🎯", modifier = Modifier.padding(end = 4.dp))
                }
                if (scheduled != null && rt != scheduled) {
                    Box { // Use Box to apply decoration to the child
                        FormattedTimeText(
                            timezone = tz,
                            timeSeconds = scheduled,
                            showSeconds = showSeconds,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough

                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                FormattedTimeText(
                    timezone = tz,
                    timeSeconds = rt,
                    showSeconds = showSeconds
                )
            } else if (scheduled != null) {
                FormattedTimeText(
                    timezone = tz,
                    timeSeconds = scheduled,
                    showSeconds = showSeconds
                )
            }
        }
    }
}

@Composable
private fun DelayChip(diffSecs: Long, showSeconds: Boolean, muted: Boolean) {
    // label (Late / Early / On time)
    val state = when {
        diffSecs > 30 -> "Late"
        diffSecs < -30 -> "Early"
        else -> "On time"
    }

    val bg = when (state) {
        "Late" -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        "Early" -> Color(0xFF1B8A34).copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    }
    val fg = when (state) {
        "Late" -> MaterialTheme.colorScheme.error
        "Early" -> Color(0xFF1B8A34)
        else -> MaterialTheme.colorScheme.secondary
    }

    val alpha = if (muted) 0.6f else 1f
    Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(bg.copy(alpha = bg.alpha * alpha))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(state, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = alpha))
        if (state != "On time") {
            Spacer(Modifier.width(6.dp))
            // Render signed offset with your DiffTimer (shows + / -)
            DiffTimer(
                diff = diffSecs.toDouble(),
                showBrackets = false,
                showSeconds = showSeconds,
                showDays = false,
                showPlus = true
            )
        }
    }
}
