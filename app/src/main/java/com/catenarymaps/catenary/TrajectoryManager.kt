package com.catenarymaps.catenary

import android.util.Log
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "TrajectoryManager"

@Serializable
data class TrajectoryStop(
    val name: String? = null,
    val departure: String? = null,
    val arrival: String? = null
)

@Serializable
data class TrajectorySegment(
    val coordinates: Array<DoubleArray>? = null
)

@Serializable
data class TrajectoryItem(
    val trip_id: String? = null,
    val unique_trip_id: String? = null,
    val display_name: String? = null,
    val color: String? = null,
    val route_type: Int? = null,
    val mode: String? = null,
    val chateau_id: String? = null,
    val trip_short_name: String? = null,
    val route_short_name: String? = null,
    val route_long_name: String? = null,
    val route_id: String? = null,
    val text_color: String? = null,
    val start_time: String? = null,
    val start_date: String? = null,
    val stops: List<TrajectoryStop>? = null,
    val segments: List<TrajectorySegment>? = null
)

@Serializable
data class TrajectoryWrapper(
    val content: TrajectoryItem? = null
)

data class ActiveTrajectory(
    val content: List<TrajectoryWrapper>,
    val timestamp: Long,
    val parsedTimes: Map<String, Pair<Long, Long>> = emptyMap(),
    val precomputedProperties: Map<String, JsonObject> = emptyMap()
)

object TrajectoryManager {
    private var lastTrajectorySubTime = 0L
    private var lastTrajectorySubParams = ""
    private var interpolationJob: Job? = null
    private var wsJob: Job? = null

    private val activeTrajectoriesData = mutableMapOf<String, ActiveTrajectory>()
    private val trajectoryAccumulators = mutableMapOf<String, MutableList<TrajectoryWrapper>>()
    private val trajectoryTimestamps = mutableMapOf<String, Long>()

    private val json = Json { ignoreUnknownKeys = true }

    private fun calculateBearing(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val brng = atan2(y, x)
        return (Math.toDegrees(brng) + 360) % 360
    }

    data class InterpolationResult(val coords: List<Double>, val bearing: Double)

    private fun interpolatePositionAndBearing(coordinates: List<DoubleArray>, progress: Double): InterpolationResult? {
        if (coordinates.isEmpty()) return null
        if (coordinates.size == 1) return InterpolationResult(listOf(coordinates[0][0], coordinates[0][1]), 0.0)

        data class SegmentInfo(val length: Double, val start: DoubleArray, val end: DoubleArray)

        val segments = mutableListOf<SegmentInfo>()
        var totalLength = 0.0

        for (i in 0 until coordinates.size - 1) {
            val start = coordinates[i]
            val end = coordinates[i + 1]
            val dx = end[0] - start[0]
            val dy = end[1] - start[1]
            val length = sqrt(dx * dx + dy * dy)
            segments.add(SegmentInfo(length, start, end))
            totalLength += length
        }

        if (totalLength == 0.0) {
            return InterpolationResult(listOf(coordinates[0][0], coordinates[0][1]), 0.0)
        }

        val targetLength = progress * totalLength
        var accumulatedLength = 0.0

        for (segment in segments) {
            if (accumulatedLength + segment.length >= targetLength) {
                val segmentProgress = if (segment.length > 0) (targetLength - accumulatedLength) / segment.length else 0.0
                val lon = segment.start[0] + segmentProgress * (segment.end[0] - segment.start[0])
                val lat = segment.start[1] + segmentProgress * (segment.end[1] - segment.start[1])
                val bearing = calculateBearing(segment.start[0], segment.start[1], segment.end[0], segment.end[1])
                return InterpolationResult(listOf(lon, lat), bearing)
            }
            accumulatedLength += segment.length
        }

        val lastSegment = segments.last()
        val bearing = calculateBearing(lastSegment.start[0], lastSegment.start[1], lastSegment.end[0], lastSegment.end[1])
        return InterpolationResult(listOf(coordinates.last()[0], coordinates.last()[1]), bearing)
    }

    fun fetchTrajectories(
        scope: CoroutineScope,
        zoom: Double,
        settings: AllLayerSettings,
        camera: CameraState
    ) {
        scope.launch {
            val modes = mutableListOf<String>()

            // Don't fetch buses until zoom level >= 9
            if (settings.bus.visiblerealtimedots && zoom >= 9) {
                modes.add("bus")
                modes.add("trolleybus")
            }
            if (settings.intercityrail.visiblerealtimedots && zoom >= 3) {
                modes.add("rail")
            }
            if (settings.localrail.visiblerealtimedots && zoom >= 4) {
                modes.add("tram")
                modes.add("subway")
                modes.add("metro")
                modes.add("funicular")
            }
            if (settings.other.visiblerealtimedots && zoom >= 3) {
                modes.add("ferry")
                modes.add("cable_car")
                modes.add("gondola")
                modes.add("monorail")
            }

            if (modes.isEmpty()) {
                if (lastTrajectorySubParams.isNotEmpty()) {
                    SpruceWebSocket.unsubscribeTrajectories()
                    lastTrajectorySubParams = ""
                }
                return@launch
            }

            val boundaries = getTileBoundaries(camera, 5) // Arbitrary level to get visible bounds
                ?: return@launch
            val visibleBounds = camera.projection?.queryVisibleBoundingBox() ?: return@launch
            
            val bbox = listOf(
                visibleBounds.southwest.longitude,
                visibleBounds.southwest.latitude,
                visibleBounds.northeast.longitude,
                visibleBounds.northeast.latitude
            )

            val paramsStr = bbox.joinToString(",") + "|" + zoom.toInt() + "|" + modes.joinToString(",")
            val now = System.currentTimeMillis()

            if (paramsStr != lastTrajectorySubParams || now - lastTrajectorySubTime > 15000) {
                SpruceWebSocket.subscribeTrajectories(bbox, zoom.toInt(), modes)
                lastTrajectorySubParams = paramsStr
                lastTrajectorySubTime = now
            }
        }
    }

    fun startTrajectoryManager(
        scope: CoroutineScope,
        trajBusDotsSrc: MutableState<GeoJsonSource>,
        trajMetroDotsSrc: MutableState<GeoJsonSource>,
        trajRailDotsSrc: MutableState<GeoJsonSource>,
        trajOtherDotsSrc: MutableState<GeoJsonSource>,
        isDark: Boolean
    ) {
        stopTrajectoryManager()

        wsJob = scope.launch(Dispatchers.IO) {
            SpruceWebSocket.spruceTrajectoryData.collect { msg ->
                if (msg?.type == "buffer" && msg.content != null) {
                    try {
                        val wrappers: List<TrajectoryWrapper> = msg.content
                        val chateau = msg.chateau ?: "unknown"
                        val timestamp = msg.timestamp ?: 0L

                        if (timestamp != trajectoryTimestamps[chateau]) {
                            trajectoryTimestamps[chateau] = timestamp
                            trajectoryAccumulators[chateau] = wrappers.toMutableList()
                        } else {
                            if (trajectoryAccumulators[chateau] == null) {
                                trajectoryAccumulators[chateau] = mutableListOf()
                            }
                            trajectoryAccumulators[chateau]?.addAll(wrappers)
                        }

                        if (msg.total_chunks == 0 || msg.chunk_index == (msg.total_chunks ?: 1) - 1) {
                            val parsedTimes = mutableMapOf<String, Pair<Long, Long>>()
                            val precomputedProperties = mutableMapOf<String, JsonObject>()
                            for (wrapper in (trajectoryAccumulators[chateau] ?: emptyList())) {
                                val traj = wrapper.content ?: continue
                                val uniqueId = traj.unique_trip_id ?: traj.trip_id ?: continue
                                if (!traj.stops.isNullOrEmpty()) {
                                    val depStr = traj.stops.first().departure
                                    val arrStr = traj.stops.last().arrival
                                    val dep = try { if (depStr != null) Instant.parse(depStr).toEpochMilli() else 0L } catch(e:Exception) { 0L }
                                    val arr = try { if (arrStr != null) Instant.parse(arrStr).toEpochMilli() else 0L } catch(e:Exception) { 0L }
                                    parsedTimes[uniqueId] = Pair(dep, arr)
                                }
                                
                                val displayName = traj.route_short_name ?: traj.display_name ?: ""
                                val rawColor = traj.color ?: "#aaaaaa"
                                val (contrastdarkmode, contrastdarkmodebearing) = processColor(rawColor, true)
                                val (contrastlightmode, _) = processColor(rawColor, false)

                                var routeType = 3
                                if (traj.route_type != null) {
                                    routeType = traj.route_type
                                } else {
                                    routeType = when (traj.mode) {
                                        "tram", "cable_car", "funicular" -> 0
                                        "subway", "metro" -> 1
                                        "rail" -> 2
                                        "bus", "trolleybus" -> 3
                                        "ferry" -> 4
                                        else -> 3
                                    }
                                }

                                val props = JsonObject(buildMap<String, JsonElement> {
                                    put("vehicleIdLabel", JsonPrimitive(traj.trip_id ?: ""))
                                    put("speed", JsonPrimitive(""))
                                    put("color", JsonPrimitive(rawColor))
                                    put("chateau", JsonPrimitive(traj.chateau_id ?: ""))
                                    put("route_type", JsonPrimitive(routeType))
                                    put("tripIdLabel", JsonPrimitive(traj.trip_short_name ?: displayName))
                                    // put("has_bearing", JsonPrimitive(true))
                                    put("maptag", JsonPrimitive(displayName))
                                    put("trip_short_name", JsonPrimitive(traj.trip_short_name ?: displayName))
                                    put("route_short_name", JsonPrimitive(traj.route_short_name ?: displayName))
                                    put("route_long_name", JsonPrimitive(traj.route_long_name ?: ""))
                                    put("contrastdarkmode", JsonPrimitive(contrastdarkmode))
                                    put("contrastdarkmodebearing", JsonPrimitive(contrastdarkmodebearing))
                                    put("contrastlightmode", JsonPrimitive(contrastlightmode))
                                    put("routeId", JsonPrimitive(traj.route_id ?: ""))
                                    put("headsign", JsonPrimitive(traj.stops?.lastOrNull()?.name ?: ""))
                                    put("text_color", JsonPrimitive(traj.text_color ?: "#ffffff"))
                                    traj.trip_id?.takeIf { it.isNotBlank() }?.let { put("trip_id", JsonPrimitive(it)) }
                                    traj.start_time?.takeIf { it.isNotBlank() }?.let { put("start_time", JsonPrimitive(it)) }
                                    traj.start_date?.takeIf { it.isNotBlank() }?.let { put("start_date", JsonPrimitive(it)) }
                                    put("crowd_symbol", JsonPrimitive(""))
                                    put("occupancy_status", JsonPrimitive(""))
                                    put("delay_label", JsonPrimitive(""))
                                    put("delay", JsonPrimitive(0))
                                    put("stop_route_type", JsonPrimitive(routeType))
                                })
                                precomputedProperties[uniqueId] = props
                            }
                            
                            activeTrajectoriesData[chateau] = ActiveTrajectory(
                                content = trajectoryAccumulators[chateau] ?: emptyList(),
                                timestamp = trajectoryTimestamps[chateau] ?: 0L,
                                parsedTimes = parsedTimes,
                                precomputedProperties = precomputedProperties
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse trajectory buffer", e)
                    }
                }
            }
        }

        interpolationJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(100)

                val now = System.currentTimeMillis()

                val busesFeatures = mutableListOf<Feature<Point, JsonObject>>()
                val localrailFeatures = mutableListOf<Feature<Point, JsonObject>>()
                val intercityrailFeatures = mutableListOf<Feature<Point, JsonObject>>()
                val otherFeatures = mutableListOf<Feature<Point, JsonObject>>()

                for ((_, activeData) in activeTrajectoriesData) {
                    for (wrapper in activeData.content) {
                        val traj = wrapper.content ?: continue
                        if (traj.stops.isNullOrEmpty() || traj.segments.isNullOrEmpty()) continue

                        val uniqueTripId = traj.unique_trip_id ?: traj.trip_id ?: ""
                        
                        val times = activeData.parsedTimes[uniqueTripId]
                        val departure = times?.first ?: 0L
                        val arrival = times?.second ?: 0L

                        if (departure == 0L || arrival == 0L) continue

                        if (now < departure - 30000 || now > arrival + 30000) {
                            continue
                        }

                        val coordinates = mutableListOf<DoubleArray>()
                        for (segment in traj.segments) {
                            if (segment.coordinates != null) {
                                coordinates.addAll(segment.coordinates)
                            }
                        }

                        if (coordinates.isEmpty()) continue

                        val progress = Math.max(0.0, Math.min(1.0, (now - departure).toDouble() / (arrival - departure).toDouble()))
                        val interpolationResult = interpolatePositionAndBearing(coordinates, progress) ?: continue

                        val coords = interpolationResult.coords
                        val bearing = interpolationResult.bearing
                        
                        val baseProps = activeData.precomputedProperties[uniqueTripId] ?: continue
                        val properties = JsonObject(buildMap<String, JsonElement> {
                            putAll(baseProps)
                            // put("bearing", JsonPrimitive(bearing.toFloat()))
                            put("timestamp", JsonPrimitive(now))
                        })
                        
                        val routeType = baseProps["route_type"]?.jsonPrimitive?.intOrNull ?: 3

                        val feature = Feature(
                            id = kotlinx.serialization.json.JsonPrimitive("trajectory_$uniqueTripId"),
                            geometry = Point(Position(coords[0], coords[1])),
                            properties = properties
                        )

                        when (routeType) {
                            3, 11 -> busesFeatures.add(feature)
                            0, 1, 5, 7 -> localrailFeatures.add(feature)
                            2 -> intercityrailFeatures.add(feature)
                            else -> otherFeatures.add(feature)
                        }
                    }
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    trajBusDotsSrc.value.setData(GeoJsonData.Features(FeatureCollection(busesFeatures)))
                    trajMetroDotsSrc.value.setData(GeoJsonData.Features(FeatureCollection(localrailFeatures)))
                    trajRailDotsSrc.value.setData(GeoJsonData.Features(FeatureCollection(intercityrailFeatures)))
                    trajOtherDotsSrc.value.setData(GeoJsonData.Features(FeatureCollection(otherFeatures)))
                }
            }
        }
    }

    fun stopTrajectoryManager() {
        interpolationJob?.cancel()
        interpolationJob = null
        wsJob?.cancel()
        wsJob = null
        activeTrajectoriesData.clear()
        trajectoryAccumulators.clear()
        trajectoryTimestamps.clear()
        lastTrajectorySubParams = ""
    }
}
