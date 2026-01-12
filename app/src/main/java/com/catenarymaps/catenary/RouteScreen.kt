package com.catenarymaps.catenary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.net.URLEncoder
import kotlin.collections.emptyList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

@Serializable
data class RouteInfoStop(
        val name: String,
        val code: String? = null,
        val latitude: Double,
        val longitude: Double,
        @SerialName("parent_station") val parentStation: String? = null,
)

@Serializable
data class ConnectingRoute(
        @SerialName("short_name") val shortName: String? = null,
        @SerialName("long_name") val longName: String? = null,
        @SerialName("color") val color: String? = null,
        @SerialName("text_color") val textColor: String? = null,
        @SerialName("route_type") val routeType: Int = 3,
)

@Serializable
data class RouteInfoDirectionPatternDetail(
        val headsign_or_destination: String,
        val gtfs_shape_id: String? = null,
        val direction_pattern_id: String
)

@Serializable
data class RouteInfoDirectionPattern(
        val direction_pattern: DirectionPatternMeta,
        val rows: List<DirectionPatternRow>
)

data class StopConnectionChip(
        val shortName: String?,
        val longName: String?,
        val color: String?,
        val textColor: String?,
        val chateauId: String? = null
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
        val shape_ids: List<String> = emptyList(),
        val stops: Map<String, RouteInfoStop>,
        val bounding_box: RustRect? = null,
        // NEW: transfers stuff
        @SerialName("connecting_routes")
        val connectingRoutes: Map<String, Map<String, ConnectingRoute>>? = null,
        @SerialName("connections_per_stop")
        val connectionsPerStop: Map<String, Map<String, List<String>>>? = null,
)

@Serializable
data class RouteRealtimeResponse(
        val vehicle_positions: Map<String, VehiclePosition> = emptyMap(),
        val last_updated_time_ms: Long,
        val trips_to_trips_compressed: Map<String, ItineraryPattern> = emptyMap(),
        val itinerary_to_direction_id: Map<String, String> = emptyMap(),
        val trip_updates: List<AspenisedTripUpdate> = emptyList()
)

@Serializable data class ItineraryPattern(val itinerary_pattern_id: String)

@Composable
fun RouteScreen(
        screenData: CatenaryStackEnum.RouteStack,
        transitShapeSource: MutableState<GeoJsonSource>,
        stopsContextSource: MutableState<GeoJsonSource>,
        onStopClick: (CatenaryStackEnum.StopStack) -> Unit,
        onTripClick: (CatenaryStackEnum.SingleTrip) -> Unit,
        onSetStopsToHide: (Set<String>) -> Unit,
        camera: CameraState,
        desiredPadding: PaddingValues,
        onBack: () -> Unit,
        onHome: () -> Unit
) {
    var routeInfo by remember { mutableStateOf<RouteInfoResponse?>(null) }
    var routeRealtime by remember { mutableStateOf<RouteRealtimeResponse?>(null) }
    var activeParentId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val loadedShapes = remember { mutableStateMapOf<String, String>() }
    val scope =
            rememberCoroutineScope() // Re-use or create new scope if needed, usually passed one is
    // better but creating one here is fine for this screen

    suspend fun fetchShape(chateauId: String, shapeId: String) {
        if (loadedShapes.contains(shapeId) ||
                        routeInfo?.shapes_polyline?.containsKey(shapeId) == true
        )
                return

        val encodedChateau = URLEncoder.encode(chateauId, "UTF-8")
        val encodedShape = URLEncoder.encode(shapeId, "UTF-8")
        val url =
                "https://birch.catenarymaps.org/get_shape?chateau=${encodedChateau}&shape_id=${encodedShape}&format=polyline"

        try {
            val response = ktorClient.get(url).body<JsonObject>()
            // Using JsonPrimitive to safely extract content
            val polyline =
                    (response["polyline"] as? kotlinx.serialization.json.JsonPrimitive)?.content

            if (polyline != null) {
                loadedShapes[shapeId] = polyline
            }
        } catch (e: Exception) {
            println("Error fetching shape $shapeId: $e")
        }
    }

    // Fetch static route info once

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, "RouteScreen")
                // param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
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
                    "https://birch.catenarymaps.org/route_info?chateau=${
                    URLEncoder.encode(
                        screenData.chateau_id,
                        "UTF-8"
                    )
                }" +
                            "&route_id=${
                    URLEncoder.encode(
                        screenData.route_id,
                        "UTF-8"
                    )
                }"
            val response: RouteInfoResponse = ktorClient.get(url).body()
            routeInfo = response

            val currentMapBounds = camera.projection?.queryVisibleBoundingBox()

            if (currentMapBounds != null) {
                response.bounding_box?.let {
                    val newBoundingBox =
                            BoundingBox(
                                    west = it.min.x.toDouble(),
                                    south = it.min.y.toDouble(),
                                    east = it.max.x.toDouble(),
                                    north = it.max.y.toDouble()
                            )

                    val currentWest = currentMapBounds.west
                    val currentSouth = currentMapBounds.south
                    val currentEast = currentMapBounds.east
                    val currentNorth = currentMapBounds.north

                    val newWest = newBoundingBox.west
                    val newSouth = newBoundingBox.south
                    val newEast = newBoundingBox.east
                    val newNorth = newBoundingBox.north

                    val contained =
                            (currentWest <= newWest &&
                                    currentSouth <= newSouth &&
                                    currentEast >= newEast &&
                                    currentNorth >= newNorth) || camera.position.zoom < 4

                    if (!contained) {
                        camera.animateTo(boundingBox = newBoundingBox, padding = desiredPadding)
                    }
                }
            }

            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseAnalytics.logEvent("view_route") {
                param("route_name", response.short_name ?: response.long_name ?: "Unknown")
                param("route_id", screenData.route_id)
                param("agency_name", response.agency_name)
                param("chateau", screenData.chateau_id)
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
                        "https://birch.catenarymaps.org/get_rt_of_single_route?chateau=${
                        URLEncoder.encode(
                            screenData.chateau_id,
                            "UTF-8"
                        )
                    }&route_id=${
                        URLEncoder.encode(
                            screenData.route_id, "UTF-8"
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

    if (error != null) {
        Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    if (routeInfo == null) {
        Column {
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End
            ) { NavigationControls(onBack = onBack, onHome = onHome) }
            Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        return
    }

    val info = routeInfo!!

    val stopConnections =
            remember(info) {
                val result = mutableMapOf<String, List<StopConnectionChip>>()
                val connectionsPerStop = info.connectionsPerStop ?: emptyMap()
                val connectingRoutes = info.connectingRoutes ?: emptyMap()

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

    // Map: parentId -> list of patterns sharing that parent
    val patternsByParentId =
            remember(info) {
                info.direction_patterns.values.groupBy { pattern ->
                    pattern.direction_pattern.directionPatternIdParents
                            ?: pattern.direction_pattern.directionPatternId
                }
            }

    // Sort parents by the headsign of the first child pattern
    val sortedParentIds =
            remember(patternsByParentId) {
                patternsByParentId.keys.sortedWith(
                        compareBy { parentId ->
                            val refPattern = patternsByParentId[parentId]?.firstOrNull()
                            refPattern?.direction_pattern?.headsignOrDestination ?: ""
                        }
                )
            }

    // Ensure we always have an active parent
    LaunchedEffect(sortedParentIds) {
        if (activeParentId == null && sortedParentIds.isNotEmpty()) {
            activeParentId = sortedParentIds.first()
        }
    }

    val directionIdToParentId =
            remember(info) {
                info.direction_patterns.values.associate { pattern ->
                    val directionId = pattern.direction_pattern.directionPatternId
                    val parentId =
                            pattern.direction_pattern.directionPatternIdParents ?: directionId
                    directionId to parentId
                }
            }

    val rt = routeRealtime

    val vehiclesByDirectionParent =
            remember(rt, directionIdToParentId) {
                rt?.vehicle_positions?.values?.groupBy { vp ->
                    val tripId = vp.trip?.trip_id
                    val itineraryId = rt.trips_to_trips_compressed[tripId]?.itinerary_pattern_id
                    val directionId = rt.itinerary_to_direction_id[itineraryId]
                    directionIdToParentId[directionId]
                }
                        ?: emptyMap()
            }

    val tripUpdatesByTripId =
            remember(rt) { rt?.trip_updates?.groupBy { it.trip.trip_id ?: "" } ?: emptyMap() }

    // Update map when active pattern changes
    // We add loadedShapes.size to keys to re-trigger when new shapes arrive
    LaunchedEffect(activeParentId, routeInfo, loadedShapes.size) {
        val info = routeInfo ?: return@LaunchedEffect
        val parentId = activeParentId ?: return@LaunchedEffect

        val patterns = patternsByParentId[parentId].orEmpty()
        val pattern = patterns.firstOrNull() ?: return@LaunchedEffect

        // 1. Update route shape on map
        val shapeId = pattern.direction_pattern.gtfsShapeId
        val polylineString = info.shapes_polyline[shapeId] ?: loadedShapes[shapeId]

        if (polylineString != null) {
            val decodedPath = com.google.maps.android.PolyUtil.decode(polylineString)
            val positions = decodedPath.map { Position(it.longitude, it.latitude) }
            val lineString = LineString(positions)
            val feature =
                    Feature(
                            geometry = lineString,
                            properties =
                                    buildJsonObject {
                                        put("color", info.color)
                                        put("text_color", info.text_color)
                                    }
                    )
            transitShapeSource.value.setData(
                    GeoJsonData.Features(FeatureCollection(listOf(feature)))
            )
        } else {
            if (shapeId != null) {
                // Trigger lazy load
                launch { fetchShape(screenData.chateau_id, shapeId) }
            }
            transitShapeSource.value.setData(
                    GeoJsonData.Features(
                            FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
                    )
            )
        }

        // 2. Update stops on map
        val stopFeatures =
                pattern.rows.mapNotNull { stopTime ->
                    info.stops[stopTime.stopId]?.let { stopDetails ->
                        Feature(
                                geometry =
                                        Point(
                                                Position(
                                                        stopDetails.longitude,
                                                        stopDetails.latitude
                                                )
                                        ),
                                properties =
                                        buildJsonObject {
                                            put("label", stopDetails.name)
                                            put("stop_id", stopTime.stopId)
                                            put("chateau", screenData.chateau_id)
                                            put("stop_route_type", info.route_type)
                                        }
                        )
                    }
                }
        stopsContextSource.value.setData(GeoJsonData.Features(FeatureCollection(stopFeatures)))

        // 3. Hide these stops from the main layers
        onSetStopsToHide(pattern.rows.map { it.stopId }.toSet())
    }

    val activePattern =
            activeParentId?.let { parentId -> patternsByParentId[parentId]?.firstOrNull() }
    val activeStops = activePattern?.rows

    LazyColumn(
            modifier =
                    Modifier.padding(horizontal = 8.dp)
                            .windowInsetsPadding(
                                    WindowInsets(
                                            bottom =
                                                    WindowInsets.safeDrawing.getBottom(
                                                            density = LocalDensity.current
                                                    )
                                    )
                            )
    ) {
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
                    chateauId = screenData.chateau_id,
                    onRouteClick = {},
                    controls = { NavigationControls(onBack = onBack, onHome = onHome) }
            )
            if (info.alert_id_to_alert.isNotEmpty()) {
                val json = Json { ignoreUnknownKeys = true }
                val alerts =
                        info.alert_id_to_alert.mapValues { (_, value) ->
                            json.decodeFromJsonElement(Alert.serializer(), value)
                        }
                var alertsExpanded by remember { mutableStateOf(true) }
                AlertsBox(
                        alerts = alerts,
                        chateau = screenData.chateau_id,
                        expanded = alertsExpanded,
                        onExpandedChange = { alertsExpanded = !alertsExpanded }
                )
            }

            // Spacer(Modifier.height(16.dp))
        }

        // Directions
        item {
            Text(
                    text = stringResource(R.string.directions),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedParentIds.forEach { parentId ->
                    val patterns = patternsByParentId[parentId].orEmpty()
                    if (patterns.isEmpty()) return@forEach

                    val refPattern = patterns.first()
                    val headsign = refPattern.direction_pattern.headsignOrDestination
                    val stopCount = refPattern.rows.size
                    val isActive = parentId == activeParentId

                    val vehicleCount = vehiclesByDirectionParent[parentId]?.size ?: 0

                    Card(
                            modifier =
                                    Modifier.fillMaxWidth().clickable { activeParentId = parentId },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    if (isActive)
                                                            MaterialTheme.colorScheme
                                                                    .primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant
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
                                Text(
                                        text = "$stopCount stops",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (vehicleCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                        modifier =
                                                Modifier.size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme.primary
                                                        ),
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

        // Active Vehicles
        val activeVehicles = vehiclesByDirectionParent[activeParentId]

        if (!activeVehicles.isNullOrEmpty()) {
            item {
                Text(
                        text = stringResource(R.string.vehicles),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // item {
            //    Text("debug ${tripUpdatesByTripId.toString()}")
            // }

            items(activeVehicles) { vehicle ->
                Card(
                        modifier =
                                Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable {
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
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.secondaryContainer
                                )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                                "Vehicle ${vehicle.vehicle?.label ?: vehicle.vehicle?.id ?: "Unknown"}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                        )
                        // vehicle.trip?.trip_headsign?.let { Text(stringResource(id =
                        // R.string.route_screen_to, it)) }

                        // New Trip Data and Occupancy Info
                        val possibleUpdates =
                                tripUpdatesByTripId[vehicle.trip?.trip_id] ?: emptyList()

                        TripDataForVehicleOnRouteScreen(
                                vehicle = vehicle,
                                stops = info.stops,
                                possibleTripUpdates = possibleUpdates,
                        ) // This composable also contains text, but let's shrink the top level
                        // first.
                        OccupancyStatusText(
                                occupancyStatus = vehicle.occupancy_status,
                                style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // Stops List

        if (!activeStops.isNullOrEmpty()) {
            item {
                Text(
                        text = stringResource(R.string.stops),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 0.dp)
                )
            }
            itemsIndexed(activeStops) { index, stopTime ->
                val stopDetails = info.stops[stopTime.stopId]
                if (stopDetails != null) {
                    Row(
                            modifier =
                                    Modifier.height(IntrinsicSize.Min).fillMaxWidth().clickable {
                                        onStopClick(
                                                CatenaryStackEnum.StopStack(
                                                        chateau_id = screenData.chateau_id,
                                                        stop_id = stopTime.stopId
                                                )
                                        )
                                    },
                            verticalAlignment = Alignment.Top
                    ) {
                        RouteStopProgressIndicator(
                                color = parseColor(info.color),
                                isFirst = index == 0,
                                isLast = index == activeStops.lastIndex,
                                modifier = Modifier.width(12.dp).fillMaxHeight()
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row() {
                                Text(stopDetails.name, fontWeight = FontWeight.Medium)

                                Spacer(Modifier.width(4.dp))
                                stopDetails.code?.let {
                                    Text(
                                            "$it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.alignByBaseline()
                                    )
                                }
                            }

                            // --- NEW: transfers
                            val parentId = stopDetails.parentStation
                            val connectionKey =
                                    when {
                                        parentId != null && stopConnections.containsKey(parentId) ->
                                                parentId
                                        stopConnections.containsKey(stopTime.stopId) ->
                                                stopTime.stopId
                                        else -> null
                                    }

                            if (connectionKey != null) {
                                val connections = stopConnections[connectionKey].orEmpty()
                                FlowRow(
                                        modifier = Modifier.padding(top = 0.dp),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) { connections.forEach { conn -> TransferRouteChip(conn) } }
                            }

                            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth())
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
        drawCircle(color = circleColor, radius = canvasWidth / 2, center = center)
        drawCircle(
                color = strokeColor,
                radius = canvasWidth / 2,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = canvasWidth / 4)
        )
    }
}

@Composable
fun TransferRouteChip(conn: StopConnectionChip, modifier: Modifier = Modifier) {
    val isRatp = RatpUtils.isIdfmChateau(conn.chateauId) && RatpUtils.isRatpRoute(conn.shortName)
    val isMta =
            MtaSubwayUtils.MTA_CHATEAU_ID == conn.chateauId &&
                    !conn.shortName.isNullOrEmpty() &&
                    MtaSubwayUtils.isSubwayRouteId(conn.shortName!!)

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
                    modifier = modifier.height(20.dp).padding(horizontal = 2.dp)
            )
            return
        }
    } else if (isMta) {
        val mtaColor = MtaSubwayUtils.getMtaSubwayColor(conn.shortName!!)
        val symbolShortName = MtaSubwayUtils.getMtaSymbolShortName(conn.shortName)
        Box(
                modifier =
                        modifier.size(20.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(mtaColor),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text = symbolShortName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    val bgColor = remember(conn.color) { parseColor(conn.color ?: "#cccccc") }
    val fgColor = remember(conn.textColor) { parseColor(conn.textColor ?: "#000000") }

    Box(
            modifier =
                    modifier.clip(RoundedCornerShape(4.dp))
                            .background(bgColor)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Text(
                text = conn.shortName
                                ?: conn.longName?.replace(" Line", "") // same trick as JS
                                 ?: "",
                color = fgColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}
