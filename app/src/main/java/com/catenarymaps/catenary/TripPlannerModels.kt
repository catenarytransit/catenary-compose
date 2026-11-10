package com.catenarymaps.catenary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.math.*

@Serializable
data class DirectionsLocation(val name: String, val lat: Double, val lon: Double, val isCurrentLocation: Boolean = false) {
    fun isValid() = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}

@Serializable
data class DirectionsPoint(
    val name: String = "", val lat: Double? = null, val lon: Double? = null,
    val platform: String? = null, val platformCode: String? = null
)
@Serializable
data class DirectionsStop(
    val name: String = "", val arrival: String? = null, val departure: String? = null,
    val scheduledArrival: String? = null, val scheduledDeparture: String? = null
)
@Serializable
data class DirectionsGeometry(val points: String? = null, val precision: Int = 6)
@Serializable
data class DirectionsLeg(
    val mode: String, val duration: Double? = null,
    val startTime: String? = null, val endTime: String? = null,
    val routeColor: String? = null, val routeTextColor: String? = null,
    val routeShortName: String? = null, val routeLongName: String? = null,
    val routeId: String? = null, val routeType: Int? = null, val chateau: String? = null,
    val tripShortName: String? = null,
    val from: DirectionsPoint? = null, val to: DirectionsPoint? = null,
    val fromPlatform: String? = null, val toPlatform: String? = null,
    val intermediateStops: List<DirectionsStop> = emptyList(),
    val legGeometry: DirectionsGeometry? = null,
    val headsign: String? = null
) {
    val street get() = mode.uppercase() in setOf("WALK", "BIKE", "BICYCLE")
    val label get() = routeShortName ?: routeLongName ?: routeId ?: mode
    val seconds get() = duration ?: runCatching {
        java.time.Duration.between(java.time.Instant.parse(startTime), java.time.Instant.parse(endTime)).seconds.toDouble()
    }.getOrDefault(0.0)
}
@Serializable
data class DirectionsItinerary(
    val startTime: String, val endTime: String, val duration: Double? = null,
    val transfers: Int? = null, val legs: List<DirectionsLeg>
) {
    val seconds get() = duration ?: runCatching {
        java.time.Duration.between(java.time.Instant.parse(startTime), java.time.Instant.parse(endTime)).seconds.toDouble()
    }.getOrDefault(0.0)
}
@Serializable
data class DirectionsPlan(
    val itineraries: List<DirectionsItinerary> = emptyList(),
    val direct: List<DirectionsItinerary> = emptyList()
)

@Serializable
data class DirectionsOptions(
    val cycling: Boolean = false, val walkingPace: Float = 840f, val cyclingKmh: Float = 16f,
    val accessSeconds: Float = 900f, val egressSeconds: Float = 900f, val directSeconds: Float = 1800f
)

/** Same request contract and units as catenary-web's directions/edelweiss.ts. */
fun directionsRequest(from: DirectionsLocation, to: DirectionsLocation, time: Long,
                      arriveBy: Boolean, options: DirectionsOptions): JsonObject {
    require(from.isValid() && to.isValid())
    require(options.walkingPace > 0 && options.cyclingKmh > 0)
    fun point(p: DirectionsLocation) = buildJsonObject { put("lat", p.lat); put("lon", p.lon) }
    return buildJsonObject {
        put("from", point(from)); put("to", point(to))
        put(if (arriveBy) "arrival_unix_seconds" else "departure_unix_seconds", time)
        val mode = JsonArray(listOf(JsonPrimitive(if (options.cycling) "BIKE" else "WALK")))
        put("preTransitModes", mode); put("postTransitModes", mode)
        put("pedestrianSpeed", 1000.0 / options.walkingPace)
        put("cyclingSpeed", options.cyclingKmh / 3.6)
        put("maxPreTransitTime", options.accessSeconds.toInt())
        put("maxPostTransitTime", options.egressSeconds.toInt())
        put("maxDirectTime", options.directSeconds.toInt()); put("detailed_legs", true)
    }
}

/** Defensive polyline decoding: malformed or truncated geometry cannot crash the screen. */
fun decodeDirectionsPolyline(encoded: String, precision: Int = 6): List<Pair<Double, Double>> {
    if (precision !in 0..8) return emptyList()
    var index = 0; var lat = 0L; var lon = 0L
    val scale = 10.0.pow(precision)
    fun next(): Long {
        var result = 0L; var shift = 0
        while (true) {
            require(index < encoded.length && shift <= 60)
            val byte = encoded[index++].code - 63
            require(byte in 0..63)
            result = result or ((byte and 31).toLong() shl shift)
            if (byte < 32) break
            shift += 5
        }
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }
    return runCatching {
        buildList {
            while (index < encoded.length) {
                lat += next(); lon += next()
                val point = lat / scale to lon / scale
                require(point.first in -90.0..90.0 && point.second in -180.0..180.0)
                add(point)
            }
        }
    }.getOrDefault(emptyList())
}

private fun lonDelta(a: Double, b: Double) = ((a - b + 540) % 360) - 180
private data class DirectionsShapeProjection(val index: Int, val fraction: Double, val point: Pair<Double, Double>, val distance: Double)

/** Project and trim full GTFS shapes to boarding/alighting stops, including reversed shapes. */
fun directionsCoordinates(leg: DirectionsLeg): List<Pair<Double, Double>> {
    val geometry = leg.legGeometry ?: return emptyList()
    val points = decodeDirectionsPolyline(geometry.points ?: return emptyList(), geometry.precision)
    val from = leg.from; val to = leg.to
    if (leg.street || points.size < 2 || from?.lat == null || from.lon == null || to?.lat == null || to.lon == null) return points
    if (haversineDistance(from.lat, from.lon, to.lat, to.lon) < 20) return points
    fun projections(stop: DirectionsPoint): List<DirectionsShapeProjection> {
        val latitude = stop.lat!!; val longitude = stop.lon!!
        val scale = 111195 * max(0.00001, cos(Math.toRadians(latitude)))
        val candidates = points.zipWithNext().mapIndexedNotNull { i, (a, b) ->
            val ax = lonDelta(a.second, longitude) * scale; val ay = (a.first - latitude) * 111195
            val bx = lonDelta(b.second, longitude) * scale; val by = (b.first - latitude) * 111195
            val dx = bx - ax; val dy = by - ay
            val square = dx * dx + dy * dy
            if (square == 0.0) return@mapIndexedNotNull null
            val fraction = (-(ax * dx + ay * dy) / square).coerceIn(0.0, 1.0)
            val x = ax + fraction * dx; val y = ay + fraction * dy
            val distance = hypot(x, y)
            if (distance > 350) null else DirectionsShapeProjection(i, fraction,
                latitude + y / 111195 to ((longitude + x / scale + 540) % 360 - 180), distance)
        }.sortedBy { it.distance }
        val cutoff = min(350.0, (candidates.firstOrNull()?.distance ?: return emptyList()) + 30)
        return candidates.filter { it.distance <= cutoff }.take(64)
    }
    val starts = projections(from); val ends = projections(to)
    val chosen = starts.flatMap { a -> ends.map { b -> a to b } }
        .filter { (a, b) -> abs(a.index + a.fraction - b.index - b.fraction) >= 1e-8 }
        .minByOrNull { (a, b) -> a.distance + b.distance + if (a.index + a.fraction > b.index + b.fraction) 5 else 0 }
        ?: return points
    val reverse = chosen.first.index + chosen.first.fraction > chosen.second.index + chosen.second.fraction
    val first = if (reverse) chosen.second else chosen.first
    val last = if (reverse) chosen.first else chosen.second
    val clipped = (listOf(first.point) + points.subList(first.index + 1, last.index + 1) + last.point)
        .fold(emptyList<Pair<Double, Double>>()) { acc, p -> if (acc.lastOrNull() == p) acc else acc + p }
    return if (clipped.size < 2) points else if (reverse) clipped.reversed() else clipped
}

fun directionsDistance(leg: DirectionsLeg): Double? {
    val points = directionsCoordinates(leg)
    if (points.size < 2) return null
    return points.zipWithNext().sumOf { (a, b) -> haversineDistance(a.first, a.second, b.first, b.second) }
}

fun SearchRow.directionsLocation(): DirectionsLocation? = when (this) {
    is SearchRow.CypressRow -> item.geometry.coordinates.takeIf { it.size >= 2 }?.let {
        DirectionsLocation(item.properties.name, it[1], it[0])
    }
    is SearchRow.StopRow -> DirectionsLocation(stopInfo.name, stopInfo.point.y, stopInfo.point.x)
    is SearchRow.OsmStationRow -> result.point?.let { DirectionsLocation(result.name ?: "Station", it.y, it.x) }
    is SearchRow.RouteRow -> null
}?.takeIf { it.isValid() }

fun encodeDirectionsPolyline(points: List<Pair<Double, Double>>): String {
    var lat = 0L; var lon = 0L
    return buildString {
        fun appendDelta(delta: Long) {
            var value = if (delta < 0) (delta shl 1).inv() else delta shl 1
            while (value >= 32) { append(((value and 31) or 32).toInt().plus(63).toChar()); value = value shr 5 }
            append((value + 63).toInt().toChar())
        }
        points.forEach { (a, b) ->
            val nextLat = kotlin.math.round(a * 1e6).toLong(); val nextLon = kotlin.math.round(b * 1e6).toLong()
            appendDelta(nextLat - lat); appendDelta(nextLon - lon); lat = nextLat; lon = nextLon
        }
    }
}

/** Decimal coordinates, signed values and cardinal directions; DMS is also accepted. */
fun parseDirectionsCoordinate(raw: String): DirectionsLocation? {
    val text = raw.trim().uppercase().replace('−', '-')
    fun component(value: String, latitude: Boolean): Double? {
        val hemisphere = value.filter { it in "NSEW" }
        if (hemisphere.length > 1 || (latitude && hemisphere.any { it in "EW" }) || (!latitude && hemisphere.any { it in "NS" })) return null
        val cleaned = value.replace(Regex("[NSEW°º′'″\"]"), " ").trim()
        val parts = cleaned.split(Regex("\\s+")).map { it.toDoubleOrNull() ?: return null }
        if (parts.size !in 1..3 || parts.drop(1).any { it < 0 || it >= 60 }) return null
        val magnitude = kotlin.math.abs(parts[0]) + parts.getOrElse(1) { 0.0 } / 60 + parts.getOrElse(2) { 0.0 } / 3600
        val negative = parts[0] < 0 || hemisphere.any { it in "SW" }
        if (parts[0] < 0 && hemisphere.any { it in "NE" }) return null
        return magnitude * if (negative) -1 else 1
    }
    val pair = if (text.contains(',') || text.contains(';')) text.split(Regex("[,;]"))
    else {
        val cardinal = Regex("^(.+?[NS])\\s+(.+?[EW])$").matchEntire(text)
        cardinal?.groupValues?.drop(1) ?: text.split(Regex("\\s+"))
    }
    if (pair.size != 2) return null
    val lat = component(pair[0], true) ?: return null; val lon = component(pair[1], false) ?: return null
    return DirectionsLocation("%.6f, %.6f".format(java.util.Locale.ROOT, lat, lon), lat, lon).takeIf { it.isValid() }
}
