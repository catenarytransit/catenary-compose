package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catenarymaps.catenary.ui.components.RouteHeading
import com.google.android.gms.maps.model.Polyline
import org.maplibre.spatialk.geojson.Feature
import com.catenarymaps.catenary.Alert
import com.catenarymaps.catenary.AlertsBox
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import kotlin.collections.emptyList

@Serializable
data class RouteInfoStop(
    val name: String,
    val code: String? = null,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class RouteInfoDirectionPatternDetail(
    val headsign_or_destination: String,
    val gtfs_shape_id: String? = null,
    val direction_pattern_id: String
)

@Serializable
data class RouteInfoDirectionPattern(
    val direction_pattern: RouteInfoDirectionPatternDetail,
    val rows: List<RouteInfoStopTime>
)

@Serializable
data class RouteInfoStopTime(
    val stop_id: String
)

@Serializable
data class RouteInfoResponse(
    val color: String,
    val text_color: String,
    val agency_name: String,
    val short_name: String? = null,
    val long_name: String? = null,
    val url: String? = null,
    val route_type: Int,
    val gtfs_desc: String? = null,
    val onestop_feed_id: String,
    val alert_id_to_alert: Map<String, JsonObject> = emptyMap(),
    val direction_patterns: Map<String, RouteInfoDirectionPattern>,
    val shapes_polyline: Map<String, String> = emptyMap(),
    val stops: Map<String, RouteInfoStop>
)

@Serializable
data class RouteRealtimeResponse(
    val vehicle_positions: Map<String, VehiclePosition> = emptyMap(),
    val last_updated_time_ms: Long,
    val trips_to_trips_compressed: Map<String, ItineraryPattern> = emptyMap(),
    val itinerary_to_direction_id: Map<String, String> = emptyMap(),
    val trip_updates: List<JsonObject> = emptyList()
)

@Serializable
data class ItineraryPattern(
    val itinerary_pattern_id: String
)

@Composable
fun RouteScreen(
    screenData: CatenaryStackEnum.RouteStack,
    transitShapeSource: MutableState<GeoJsonSource>,
    stopsContextSource: MutableState<GeoJsonSource>,
    onStopClick: (CatenaryStackEnum.StopStack) -> Unit,
    onTripClick: (CatenaryStackEnum.SingleTrip) -> Unit,
    onSetStopsToHide: (Set<String>) -> Unit
) {
    var routeInfo by remember { mutableStateOf<RouteInfoResponse?>(null) }
    var routeRealtime by remember { mutableStateOf<RouteRealtimeResponse?>(null) }
    var activePatternId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Fetch static route info once

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, "RouteScreen")
                //param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
                param("chateau_id", screenData.chateau_id)
                param("route_id", screenData.route_id)
            }
        } catch (e: Exception) {
            // Log the error or handle it gracefully
            android.util.Log.e("GA", "Failed to log screen view", e)
        }
    }
    LaunchedEffect(screenData) {
        try {
            val url =
                "https://birch.catenarymaps.org/route_info?chateau=${screenData.chateau_id}&route_id=${
                    screenData.route_id.replace(
                        "\"",
                        ""
                    )
                }"
            val response: RouteInfoResponse = ktorClient.get(url).body()
            routeInfo = response

            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseAnalytics.logEvent("view_route") {
                param("route_name", response.short_name ?: response.long_name ?: "Unknown")
                param("route_id", screenData.route_id)
                param("agency_name", response.agency_name)
                param("chateau", screenData.chateau_id)
            }

            // Set initial active pattern
            if (response.direction_patterns.isNotEmpty()) {
                activePatternId = response.direction_patterns.keys.first()
            }
        } catch (e: Exception) {
            error = "Failed to load route details: ${e.message}"
        }
    }

    // Fetch real-time data periodically
    LaunchedEffect(screenData) {
        while (true) {
            try {
                val lastUpdated = routeRealtime?.last_updated_time_ms ?: 0
                val url =
                    "https://birch.catenarymaps.org/get_rt_of_single_route?chateau=${screenData.chateau_id}&route_id=${
                        screenData.route_id.replace(
                            "\"",
                            ""
                        )
                    }&last_updated_time_ms=$lastUpdated"
                val response: RouteRealtimeResponse = ktorClient.get(url).body()
                routeRealtime = response
            } catch (e: Exception) {
                // Don't show error for RT failures, just log it
            }
            delay(5000) // 5-second refresh
        }
    }

    // Update map when active pattern changes
    LaunchedEffect(activePatternId, routeInfo) {
        val info = routeInfo ?: return@LaunchedEffect
        val patternId = activePatternId ?: return@LaunchedEffect
        val pattern = info.direction_patterns[patternId] ?: return@LaunchedEffect

        // 1. Update route shape on map
        val shapeId = pattern.direction_pattern.gtfs_shape_id
        val polylineString = info.shapes_polyline[shapeId]
        if (polylineString != null) {
            val decodedPath = com.google.maps.android.PolyUtil.decode(polylineString)
            val positions = decodedPath.map { Position(it.longitude, it.latitude) }
            val lineString = LineString(positions)
            val feature = Feature(
                geometry = lineString,
                properties = buildJsonObject {
                    put("color", info.color)
                    put("text_color", info.text_color)
                }
            )
            transitShapeSource.value.setData(GeoJsonData.Features(FeatureCollection(listOf(feature))))
        } else {
            transitShapeSource.value.setData(
                GeoJsonData.Features(FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>()))
            )
        }

        // 2. Update stops on map
        val stopFeatures = pattern.rows.mapNotNull { stopTime ->
            info.stops[stopTime.stop_id]?.let { stopDetails ->
                Feature(
                    geometry = Point(Position(stopDetails.longitude, stopDetails.latitude)),
                    properties = buildJsonObject {
                        put("label", stopDetails.name)
                        put("stop_id", stopTime.stop_id)
                        put("chateau", screenData.chateau_id)
                        put("stop_route_type", info.route_type)
                    }
                )
            }
        }
        stopsContextSource.value.setData(GeoJsonData.Features(FeatureCollection(stopFeatures)))

        // 3. Hide these stops from the main layers
        onSetStopsToHide(pattern.rows.map { it.stop_id }.toSet())
    }

    if (error != null) {
        Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    if (routeInfo == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val info = routeInfo!!
    val rt = routeRealtime

    val vehiclesByDirection = remember(rt) {
        rt?.vehicle_positions?.values?.groupBy { vp ->
            val tripId = vp.trip?.trip_id
            val itineraryId = rt.trips_to_trips_compressed[tripId]?.itinerary_pattern_id
            rt.itinerary_to_direction_id[itineraryId]
        } ?: emptyMap()
    }

    val tripUpdatesByTripId = remember(rt) {
        val json = Json { ignoreUnknownKeys = true }
        rt?.trip_updates?.mapNotNull { tripUpdateJson ->
            try {
                json.decodeFromJsonElement(TripUpdate.serializer(), tripUpdateJson)
            } catch (e: Exception) {
                null // Ignore updates that fail to parse
            }
        }?.groupBy { it.trip.trip_id ?: "" } ?: emptyMap()
    }

    LazyColumn(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Header
        item {
            RouteHeading(
                color = info.color,
                textColor = info.text_color,
                routeType = info.route_type,
                agencyName = info.agency_name,
                shortName = info.short_name,
                longName = info.long_name,
                description = info.gtfs_desc,
                isCompact = false,
                onRouteClick = {}
            )
            if (info.alert_id_to_alert.isNotEmpty()) {
                val json = Json { ignoreUnknownKeys = true }
                val alerts = info.alert_id_to_alert.mapValues { (_, value) ->
                    json.decodeFromJsonElement(Alert.serializer(), value)
                }
                AlertsBox(
                    alerts = alerts,
                    chateau = screenData.chateau_id
                )
            }

            //Spacer(Modifier.height(16.dp))
        }

        // Directions
        item {
            Text(
                text = stringResource(R.string.directions),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Group patterns by headsign to handle duplicates
                val patternsByName = info.direction_patterns.values.groupBy { it.direction_pattern.headsign_or_destination }
                patternsByName.entries.sortedBy { it.key }.forEach { (headsign, patterns) ->
                    val showDisambiguation = patterns.size > 1

                    patterns.forEach { pattern ->
                        val patternId = pattern.direction_pattern.direction_pattern_id
                        val isActive = patternId == activePatternId
                        val vehicleCount = vehiclesByDirection[patternId]?.size ?: 0
                        val stopCount = pattern.rows.size

                        val disambiguationText = if (showDisambiguation) {
                            val firstStopId = pattern.rows.firstOrNull()?.stop_id
                            val lastStopId = pattern.rows.lastOrNull()?.stop_id
                            val firstStopName = firstStopId?.let { info.stops[it]?.name }
                            val lastStopName = lastStopId?.let { info.stops[it]?.name }
                            if (firstStopName != null && lastStopName != null) {
                                "$firstStopName → $lastStopName"
                            } else {
                                null
                            }
                        } else {
                            null
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activePatternId = patternId },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = headsign,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    disambiguationText?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "$stopCount stops", // TODO: Use plural string resource
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (vehicleCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = vehicleCount.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Spacer(Modifier.height(24.dp))
        }

        // Active Vehicles
        val activeVehicles = vehiclesByDirection[activePatternId]
        if (!activeVehicles.isNullOrEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.vehicles),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(activeVehicles) { vehicle ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            onTripClick(
                                CatenaryStackEnum.SingleTrip(
                                    chateau_id = screenData.chateau_id,
                                    trip_id = vehicle.trip?.trip_id ?: "",
                                    route_id = vehicle.trip?.route_id,
                                    start_date = vehicle.trip?.start_date,
                                    start_time = vehicle.trip?.start_time,
                                    vehicle_id = vehicle.vehicle?.id,
                                    route_type = vehicle.route_type
                                )
                            )
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Vehicle ${vehicle.vehicle?.label ?: vehicle.vehicle?.id ?: "Unknown"}",
                            fontWeight = FontWeight.Bold
                        )
                        vehicle.trip?.trip_headsign?.let { Text(stringResource(id = R.string.route_screen_to, it)) }

                        // New Trip Data and Occupancy Info
                        val possibleUpdates = tripUpdatesByTripId[vehicle.trip?.trip_id] ?: emptyList()
                        TripDataForVehicleOnRouteScreen(
                            vehicle = vehicle,
                            stops = info.stops,
                            possibleTripUpdates = possibleUpdates
                        )
                        OccupancyStatusText(occupancyStatus = vehicle.occupancy_status)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // Stops List
        val activeStops = info.direction_patterns[activePatternId]?.rows
        if (!activeStops.isNullOrEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.stops),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            itemsIndexed(activeStops) { index, stopTime ->
                val stopDetails = info.stops[stopTime.stop_id]
                if (stopDetails != null) {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .fillMaxWidth()
                            .clickable {
                                onStopClick(
                                    CatenaryStackEnum.StopStack(
                                        chateau_id = screenData.chateau_id,
                                        stop_id = stopTime.stop_id
                                    )
                                )
                            },
                        verticalAlignment = Alignment.Top
                    ) {
                        RouteStopProgressIndicator(
                            color = parseColor(info.color),
                            isFirst = index == 0,
                            isLast = index == activeStops.lastIndex,
                            modifier = Modifier
                                .width(12.dp)
                                .fillMaxHeight()
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(stopDetails.name, fontWeight = FontWeight.Medium)
                            stopDetails.code?.let {
                                Text(
                                    "ID: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteStopProgressIndicator(
    color: Color,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxHeight()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val hCenter = canvasHeight / 2
        val wCenter = canvasWidth / 2

        val boxWidth = canvasWidth / 2

        val circleColor = Color.White
        val strokeColor = color

        // Top box
        if (!isFirst) {
            drawRect(
                color = color,
                topLeft = Offset(wCenter - boxWidth / 2, 0f),
                size = androidx.compose.ui.geometry.Size(boxWidth, hCenter)
            )
        }

        // Bottom box
        if (!isLast) {
            drawRect(
                color = color,
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