package com.catenarymaps.catenary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.em
import org.maplibre.compose.expressions.dsl.nil
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.MultiPoint
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/**
 * A temporary rendering configuration, not a user preference. Keep the user's
 * stop/station visibility while hiding all background transit route geometry,
 * labels, vehicles and trajectory playback in journey details map mode.
 */
internal fun AllLayerSettings.forJourneyDetailsMap(): AllLayerSettings {
    fun LayerCategorySettings.stopsOnly() = copy(
        shapes = false, labelshapes = false,
        visiblerealtimedots = false, labeltrajectories = false
    )
    return copy(
        bus = bus.stopsOnly(),
        localrail = localrail.stopsOnly(),
        intercityrail = intercityrail.stopsOnly(),
        other = other.stopsOnly()
    )
}

/** All planner-owned layers and subscriptions disappear when map details exits. */
@Composable
fun TripPlannerMap(planner: TripPlannerViewModel) {
    // Details should never show the other journey alternatives.
    planner.selectedJourney?.let { journey ->
        key(journey.identity()) {
            JourneyMapLines(journey)
            JourneyMapVehicles(journey)
        }
    }

    val transit = if (planner.showTransfers) {
        planner.selectedJourney?.legs?.filter { !it.street }.orEmpty()
    } else emptyList()
    val transfers = transit.zipWithNext().flatMap { (a, b) -> listOfNotNull(a.to, b.from) }
        .mapNotNull { point ->
            val lat = point.lat
            val lon = point.lon
            if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                lat in -90.0..90.0 && lon in -180.0..180.0) Position(lon, lat) else null
        }.distinct()
    val source = rememberGeoJsonSource(GeoJsonData.Features(MultiPoint(transfers)))
    CircleLayer(
        id = "directions-transfer-points", source = source, radius = const(5.dp),
        color = const(Color.Gray), strokeColor = const(Color.White), strokeWidth = const(2.dp)
    )
}

@Composable
private fun JourneyMapLines(journey: DirectionsItinerary) {
    journey.legs.forEachIndexed { index, leg ->
        val points = remember(leg) {
            directionsCoordinates(leg).map { (lat, lon) -> Position(lon, lat) }
        }
        if (points.size >= 2) {
            val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(points)))
            val color = if (leg.street) TripPlannerColors.streetLine
                else parseColor(leg.routeColor, Color(0xFF2563EB))
            LineLayer(
                id = "directions-selected-$index", source = source, color = const(color),
                width = const(5.dp), opacity = const(1f),
                dasharray = if (leg.walking) const(listOf(2f, 2f)) else nil()
            )
        }
    }
}

private data class JourneyVehicleCandidate(
    val identity: RamondaTripIdentity,
    val trip: TripDataResponse,
    val label: String
)

/** Ramonda supplies trip/vehicle identity; Birch supplies its changing GPS position. */
@Composable
private fun JourneyMapVehicles(journey: DirectionsItinerary) {
    val tripIdentities = journey.ramondaTrips()
    DisposableEffect(tripIdentities) {
        tripIdentities.forEach(RamondaWebSocket::subscribe)
        onDispose { tripIdentities.forEach(RamondaWebSocket::unsubscribe) }
    }

    val snapshots by RamondaWebSocket.tripSnapshots.collectAsState()
    val candidates = tripIdentities.mapNotNull { identity ->
        val trip = snapshots[identity]?.initial ?: return@mapNotNull null
        val label = (trip.vehicle?.label ?: trip.vehicle?.id)
            ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        JourneyVehicleCandidate(identity, trip, label)
    }

    var features by remember { mutableStateOf(emptyList<Feature<Point, JsonObject>>()) }
    val empty = remember { GeoJsonData.Features(FeatureCollection(emptyList<Feature<Point, JsonObject>>())) }
    val source = rememberGeoJsonSource(empty)
    LaunchedEffect(features) { source.setData(GeoJsonData.Features(FeatureCollection(features))) }
    DisposableEffect(source) {
        onDispose { source.setData(empty) }
    }

    // LaunchedEffect is cancelled whenever the journey/map disappears or the
    // Ramonda vehicle label changes. Old polling results cannot leak across trips.
    LaunchedEffect(candidates) {
        features = emptyList()
        if (candidates.isEmpty()) return@LaunchedEffect
        while (true) {
            features = candidates.map { candidate ->
                async {
                    try {
                        val vehicle = fetchVehicleRealtimeForLabel(
                            candidate.identity.chateau, candidate.label
                        ).firstOrNull { it.matchesJourneyTrip(candidate.identity) }
                            ?: return@async null
                        val latitude = vehicle.position?.latitude ?: return@async null
                        val longitude = vehicle.position.longitude ?: return@async null
                        if (!latitude.isFinite() || !longitude.isFinite() ||
                            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                            return@async null
                        }
                        vehicleContextFeatureFromSingleTrip(
                            candidate.identity.chateau, candidate.trip, vehicle
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Missing/temporarily unavailable GPS should leave no stale dot.
                        null
                    }
                }
            }.awaitAll().filterNotNull()
            delay(1_000L)
        }
    }

    CircleLayer(
        id = "directions-selected-vehicles", source = source,
        radius = const(9.dp), color = get("color").cast<ColorValue>(),
        strokeColor = const(Color.White), strokeWidth = const(2.dp)
    )
    SymbolLayer(
        id = "directions-selected-vehicle-labels", source = source,
        textField = get("maptag").cast<StringValue>(),
        textFont = const(listOf("Arimo-SemiBold")),
        textSize = const(0.75f.em), textColor = const(Color.White),
        textHaloColor = const(Color.Black), textHaloWidth = const(1.dp)
    )
}
