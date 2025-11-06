// In a new file, e.g., SingleTripInfoScreen.kt
package com.catenarymaps.catenary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(stopTimes) { i, stopTime ->
                    if (showPreviousStops || i > lastInactiveStopIdx) {
                        StopListItem(
                            stopTime = stopTime,
                            tripColorStr = data.color ?: "#808080",
                            isFirst = i == 0,
                            isLast = i == stopTimes.lastIndex,
                            isInactive = i <= lastInactiveStopIdx,
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
    onStopClick: () -> Unit
) {
    val tripColor = try {
        Color(android.graphics.Color.parseColor(tripColorStr))
    } catch (e: Exception) {
        Color.Gray
    }
    val inactiveColor = Color.Gray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 1. Progress Bar (port of the logic)
        TripProgressIndicator(
            color = if (isInactive) inactiveColor else tripColor,
            isFirst = isFirst,
            isLast = isLast,
            isInactive = isInactive,
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
        )

        Spacer(Modifier.width(16.dp))

        // 2. Stop Info
        Column {
            Text(
                text = stopTime.raw.name, // TODO: Port fixStationName
                style = MaterialTheme.typography.bodyLarge,
                color = if (isInactive) Color.Gray else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onStopClick)
            )

            // TODO: Port your StopTimeNumber.svelte component here
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxHeight()) {
        val w = size.width
        val h = size.height

        val circleColor = if (isInactive) color else Color.White
        val strokeColor = if (isInactive) Color.DarkGray else color

        // Top line
        if (!isFirst) {
            drawLine(
                color = color,
                start = Offset(w / 2, 0f),
                end = Offset(w / 2, h / 2),
                strokeWidth = w / 2
            )
        }

        // Bottom line
        if (!isLast) {
            drawLine(
                color = color,
                start = Offset(w / 2, h / 2),
                end = Offset(w / 2, h),
                strokeWidth = w / 2
            )
        }

        // Center circle
        drawCircle(
            color = circleColor,
            radius = w / 2,
            center = center
        )
        drawCircle(
            color = strokeColor,
            radius = w / 2,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w / 4)
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
            DelayChip(diffSecs = rt - scheduled, showSeconds = showSeconds, muted = isInactive)
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
                        // strike-through scheduled
                        Text(
                            text = "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                        // Render scheduled time
                        FormattedTimeText(
                            timezone = tz,
                            timeSeconds = scheduled,
                            showSeconds = showSeconds
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    // RT time highlighted
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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
            DelayChip(diffSecs = rt - scheduled, showSeconds = showSeconds, muted = isInactive)
        }

        // Right: clocks
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rt != null) {
                if (scheduled != null && rt == scheduled) {
                    Text("🎯", modifier = Modifier.padding(end = 4.dp))
                }
                if (scheduled != null && rt != scheduled) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                    FormattedTimeText(
                        timezone = tz,
                        timeSeconds = scheduled,
                        showSeconds = showSeconds
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
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
