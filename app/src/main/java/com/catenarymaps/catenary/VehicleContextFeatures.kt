package com.catenarymaps.catenary

import androidx.compose.runtime.MutableState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.neq
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

private const val DEFAULT_ROUTE_COLOR = "#808080"
private const val DEFAULT_TEXT_COLOR = "#000000"

private fun normalizeHexColor(value: String?, fallback: String = DEFAULT_ROUTE_COLOR): String {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return fallback
    return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
}

private fun normalizeRouteId(routeId: String?): String? =
    routeId?.trim('"')?.takeIf { it.isNotBlank() }

private fun contrastTextColorForLightMode(textColor: String): String =
    if (textColor.equals("#FFFFFF", ignoreCase = true)) "#111111" else textColor

private fun contrastTextColorForDarkMode(textColor: String): String =
    if (textColor.equals("#000000", ignoreCase = true)) "#FFFFFF" else textColor

private fun normalizeBearing(bearing: Float?): Float? {
    if (bearing == null || bearing.isNaN() || bearing.isInfinite()) return null
    return ((bearing % 360f) + 360f) % 360f
}

private fun buildVehicleContextFeature(
    chateauId: String,
    routeId: String?,
    tripId: String?,
    routeColor: String?,
    routeTextColor: String?,
    routeShortName: String?,
    routeLongName: String?,
    routeType: Int?,
    headsign: String?,
    vehicleLabel: String?,
    vehicleId: String?,
    tripShortName: String?,
    startTime: String?,
    startDate: String?,
    latitude: Double,
    longitude: Double,
    bearing: Float?,
    speed: Float?,
    occupancyStatus: Int?,
    delaySeconds: Int?
): Feature<Point, JsonObject>? {
    if (vehicleLabel.isNullOrBlank() && vehicleId.isNullOrBlank()) return null
    val displayVehicle = vehicleMapVehicleLabel(chateauId, vehicleLabel, vehicleId)
    val normalizedRouteId = normalizeRouteId(routeId)
    val color = normalizeHexColor(routeColor)
    val textColor = normalizeHexColor(routeTextColor, DEFAULT_TEXT_COLOR)
    val lightContrast = contrastTextColorForLightMode(textColor)
    val darkContrast = contrastTextColorForDarkMode(textColor)
    val routeLabels = vehicleMapRouteLabels(
        chateauId, normalizedRouteId, routeShortName, routeLongName, headsign
    )
    val delayLabel = vehicleMapDelayLabel(delaySeconds)
    val tripLabel = vehicleMapTripLabel(tripShortName, tripId)
    val normalizedBearing = normalizeBearing(bearing)

    return Feature(
        geometry = Point(Position(longitude, latitude)),
        properties =
            buildJsonObject {
                put("chateau", chateauId)
                put("trip_id", tripId ?: "")
                put("tripId", tripId ?: "")
                put("tripIdLabel", tripLabel)
                put("rt_id", tripId ?: "")
                put("routeId", normalizedRouteId ?: "")
                put("route_id", normalizedRouteId ?: "")
                put("color", color)
                put("text_color", textColor)
                put("maptag", routeLabels.route)
                put("route_short_name", routeShortName ?: "")
                put("route_long_name", routeLongName ?: "")
                put("contrastlightmode", lightContrast)
                put("contrastdarkmode", darkContrast)
                put("contrastdarkmodebearing", darkContrast)
                put("route_type", routeType ?: 3)
                put("routeType", routeType ?: 3)
                put("headsign", routeLabels.headsign)
                put("vehicleIdLabel", displayVehicle)
                put("vehicle_id", vehicleId ?: vehicleLabel ?: displayVehicle)
                put("trip_short_name", tripShortName ?: "")
                put("start_time", startTime ?: "")
                put("start_date", startDate ?: "")
                normalizedBearing?.let { put("bearing", it) }
                put("has_bearing", normalizedBearing != null)
                put("speed", speed ?: 0f)
                put("speed_mph", vehicleMapSpeedLabel(speed, true))
                put("speed_kmh", vehicleMapSpeedLabel(speed, false))
                put("occupancy_status", occupancyStatus ?: -1)
                put(
                    "crowd_symbol",
                    occupancyStatus?.let { occupancy_to_symbol(it) } ?: ""
                )
                put("delay", delaySeconds ?: 0)
                put("delay_label", delayLabel)
            }
    )
}

fun vehicleContextFeatureFromRouteVehicle(
    chateauId: String,
    routeId: String,
    routeColor: String?,
    routeTextColor: String?,
    routeShortName: String?,
    routeLongName: String?,
    vehicleKey: String,
    vehicle: VehiclePosition
): Feature<Point, JsonObject>? {
    val position = vehicle.position ?: return null
    val trip = vehicle.trip

    return buildVehicleContextFeature(
        chateauId = chateauId,
        routeId = trip?.route_id ?: routeId,
        tripId = trip?.trip_id,
        routeColor = routeColor,
        routeTextColor = routeTextColor,
        routeShortName = routeShortName,
        routeLongName = routeLongName,
        routeType = vehicle.route_type,
        headsign = trip?.trip_headsign,
        vehicleLabel = vehicle.vehicle?.label,
        vehicleId = vehicle.vehicle?.id ?: vehicleKey,
        tripShortName = trip?.trip_short_name,
        startTime = trip?.start_time,
        startDate = trip?.start_date,
        latitude = position.latitude,
        longitude = position.longitude,
        bearing = position.bearing,
        speed = position.speed,
        occupancyStatus = vehicle.occupancy_status,
        delaySeconds = trip?.delay
    )
}

fun vehicleContextFeatureFromSingleTrip(
    chateauId: String,
    tripData: TripDataResponse,
    vehicleData: VehicleRealtimeData
): Feature<Point, JsonObject>? {
    val position = vehicleData.position ?: return null
    val latitude = position.latitude ?: return null
    val longitude = position.longitude ?: return null
    val trip = vehicleData.trip
    val vehicle = vehicleData.vehicle

    return buildVehicleContextFeature(
        chateauId = chateauId,
        routeId = trip?.route_id ?: tripData.route_id,
        tripId = trip?.trip_id ?: tripData.trip_id,
        routeColor = tripData.color,
        routeTextColor = tripData.text_color,
        routeShortName = tripData.route_short_name,
        routeLongName = tripData.route_long_name,
        routeType = tripData.route_type,
        headsign = trip?.trip_headsign ?: tripData.trip_headsign,
        vehicleLabel = vehicle?.label ?: tripData.vehicle?.label,
        vehicleId = vehicle?.id ?: tripData.vehicle?.id,
        tripShortName = trip?.trip_short_name ?: tripData.trip_short_name,
        startTime = trip?.start_time,
        startDate = trip?.start_date,
        latitude = latitude,
        longitude = longitude,
        bearing = position.bearing,
        speed = position.speed,
        occupancyStatus = vehicleData.occupancy_status?.toIntOrNull(),
        delaySeconds = trip?.delay
    )
}

fun setVehicleContextSource(
    source: MutableState<GeoJsonSource>?,
    features: List<Feature<Point, JsonObject>>
) {
    source ?: return
    source.value.setData(GeoJsonData.Features(FeatureCollection(features)))
}

fun resetVehicleContextSource(
    source: MutableState<GeoJsonSource>?,
    applyFilterToLiveDots: MutableState<Expression<BooleanValue>>?
) {
    setVehicleContextSource(source, emptyList())
    applyFilterToLiveDots?.value = const(true)
}

fun hideSelectedTripLiveDotFilter(chateauId: String, tripId: String?): Expression<BooleanValue> {
    val cleanTripId = tripId?.takeIf { it.isNotBlank() } ?: return const(true)

    return any(
        get("chateau").cast<StringValue>().neq(const(chateauId)),
        all(
            get("trip_id").cast<StringValue>().neq(const(cleanTripId)),
            get("tripId").cast<StringValue>().neq(const(cleanTripId))
        )
    )
}

fun hideSelectedRouteLiveDotsFilter(chateauId: String, routeId: String?): Expression<BooleanValue> {
    val cleanRouteId = normalizeRouteId(routeId) ?: return const(true)

    return any(
        get("chateau").cast<StringValue>().neq(const(chateauId)),
        all(
            get("routeId").cast<StringValue>().neq(const(cleanRouteId)),
            get("route_id").cast<StringValue>().neq(const(cleanRouteId))
        )
    )
}
