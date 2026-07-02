package com.catenarymaps.catenary

import android.util.Log
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    val coordinates: List<List<Double>>? = null
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
    val timestamp: Long
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
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

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

    private fun interpolatePositionAndBearing(coordinates: List<List<Double>>, progress: Double): InterpolationResult? {
        if (coordinates.isEmpty()) return null
        if (coordinates.size == 1) return InterpolationResult(coordinates[0], 0.0)

        data class SegmentInfo(val length: Double, val start: List<Double>, val end: List<Double>)

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
            return InterpolationResult(coordinates[0], 0.0)
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
        return InterpolationResult(coordinates.last(), bearing)
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
        busDotsSrc: MutableState<GeoJsonSource>,
        metroDotsSrc: MutableState<GeoJsonSource>,
        railDotsSrc: MutableState<GeoJsonSource>,
        otherDotsSrc: MutableState<GeoJsonSource>,
        isDark: Boolean
    ) {
        stopTrajectoryManager()

        wsJob = scope.launch(Dispatchers.IO) {
            SpruceWebSocket.spruceTrajectoryData.collectLatest { msg ->
                if (msg?.type == "buffer" && msg.content != null) {
                    try {
                        val wrappers: List<TrajectoryWrapper> = json.decodeFromJsonElement(msg.content)
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
                            activeTrajectoriesData[chateau] = ActiveTrajectory(
                                content = trajectoryAccumulators[chateau] ?: emptyList(),
                                timestamp = trajectoryTimestamps[chateau] ?: 0L
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
                delay(500)

                val now = System.currentTimeMillis()

                val busesFeatures = mutableListOf<Feature<Point, Map<String, Any>>>()
                val localrailFeatures = mutableListOf<Feature<Point, Map<String, Any>>>()
                val intercityrailFeatures = mutableListOf<Feature<Point, Map<String, Any>>>()
                val otherFeatures = mutableListOf<Feature<Point, Map<String, Any>>>()

                for ((_, activeData) in activeTrajectoriesData) {
                    for (wrapper in activeData.content) {
                        val traj = wrapper.content ?: continue
                        if (traj.stops.isNullOrEmpty() || traj.segments.isNullOrEmpty()) continue

                        val departureStr = traj.stops.first().departure ?: continue
                        val arrivalStr = traj.stops.last().arrival ?: continue

                        val departure = try { dateFormat.parse(departureStr)?.time ?: 0L } catch(e:Exception) { 0L }
                        val arrival = try { dateFormat.parse(arrivalStr)?.time ?: 0L } catch(e:Exception) { 0L }

                        if (departure == 0L || arrival == 0L) continue

                        if (now < departure - 30000 || now > arrival + 30000) {
                            continue
                        }

                        val coordinates = mutableListOf<List<Double>>()
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
                        val tripId = traj.trip_id ?: ""
                        val uniqueTripId = traj.unique_trip_id ?: tripId
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

                        val properties = mapOf<String, Any>(
                            "vehicleIdLabel" to "",
                            "speed" to "",
                            "color" to rawColor,
                            "chateau" to (traj.chateau_id ?: ""),
                            "route_type" to routeType,
                            "tripIdLabel" to (traj.trip_short_name ?: displayName),
                            "bearing" to bearing.toFloat(),
                            "has_bearing" to true,
                            "maptag" to displayName,
                            "trip_short_name" to (traj.trip_short_name ?: displayName),
                            "route_short_name" to (traj.route_short_name ?: displayName),
                            "route_long_name" to (traj.route_long_name ?: ""),
                            "contrastdarkmode" to contrastdarkmode,
                            "contrastdarkmodebearing" to contrastdarkmodebearing,
                            "contrastlightmode" to contrastlightmode,
                            "routeId" to (traj.route_id ?: ""),
                            "headsign" to (traj.stops.last().name ?: ""),
                            "timestamp" to now,
                            "text_color" to (traj.text_color ?: "#ffffff"),
                            "trip_id" to tripId,
                            "start_time" to (traj.start_time ?: departureStr),
                            "start_date" to (traj.start_date ?: ""),
                            "crowd_symbol" to "",
                            "occupancy_status" to "",
                            "delay_label" to "",
                            "delay" to 0,
                            "stop_route_type" to routeType
                        )

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

                launch(Dispatchers.Main) {
                    busDotsSrc.value.setData(GeoJsonData.Features(FeatureCollection(busesFeatures)))
                    metroDotsSrc.value.setData(GeoJsonData.Features(FeatureCollection(localrailFeatures)))
                    railDotsSrc.value.setData(GeoJsonData.Features(FeatureCollection(intercityrailFeatures)))
                    otherDotsSrc.value.setData(GeoJsonData.Features(FeatureCollection(otherFeatures)))
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
