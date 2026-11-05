package com.catenarymaps.catenary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.painterResource
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/** Native map symbols follow the camera without Compose projection or drag handlers. */
@Composable
fun TripPlannerEndpoints(planner: TripPlannerViewModel) {
    listOf(true to planner.origin, false to planner.destination).forEach { (origin, location) ->
        key(origin) {
          if (location != null) {
            val source = rememberGeoJsonSource(GeoJsonData.Features(Point(Position(location.lon, location.lat))))
            SymbolLayer(id = if (origin) "directions-start" else "directions-end", source = source,
                iconImage = image(painterResource(if (origin) R.drawable.start_point else R.drawable.end_point)),
                iconAllowOverlap = const(true), iconIgnorePlacement = const(true))
          }
        }
    }
}
