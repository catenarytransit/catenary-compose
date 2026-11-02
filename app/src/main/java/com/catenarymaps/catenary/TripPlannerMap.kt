package com.catenarymaps.catenary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.nil
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.MultiPoint
import org.maplibre.spatialk.geojson.Position

/** Dedicated sources disappear immediately when the planner leaves the navigation stack. */
@Composable
fun TripPlannerMap(planner: TripPlannerViewModel) {
    // A separate selected overlay always stays above alternatives, even after switching cards.
    planner.itineraries.forEachIndexed { index, journey ->
        key(index) { JourneyMapLines("alternative-$index", journey, false) }
    }
    planner.itineraries.getOrNull(planner.selected)?.let { journey ->
        JourneyMapLines("selected", journey, true)
    }
    val transit = if (planner.showTransfers) {
        planner.itineraries.getOrNull(planner.selected)?.legs?.filter { !it.street }.orEmpty()
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
private fun JourneyMapLines(id: String, journey: DirectionsItinerary, selected: Boolean) {
    journey.legs.forEachIndexed { index, leg ->
        val points = remember(leg) {
            directionsCoordinates(leg).map { (lat, lon) -> Position(lon, lat) }
        }
        if (points.size >= 2) {
            val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(points)))
            val color = when {
                !selected -> Color(0xFFA8ADB4)
                leg.street -> Color(0xFF64748B)
                else -> parseColor(leg.routeColor, Color(0xFF2563EB))
            }
            LineLayer(
                id = "directions-$id-$index", source = source, color = const(color),
                width = const(if (selected) 5.dp else 3.dp),
                opacity = const(if (selected) 1f else 0.45f),
                dasharray = if (leg.street) const(listOf(0.2f, 2f)) else nil()
            )
        }
    }
}
