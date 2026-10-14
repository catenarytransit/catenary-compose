package com.catenarymaps.catenary

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/** Independent of the user's persistent, draggable pin. The location is never draggable. */
data class SelectedLocationPin(val key: String, val name: String, val position: Position)

fun selectedStackPinKey(screen: CatenaryStackEnum?): String? = when (screen) {
    is CatenaryStackEnum.StopStack -> "stop:${screen.chateau_id}:${screen.stop_id}"
    is CatenaryStackEnum.OsmStationStack -> "station:${screen.osm_station_id}"
    is CatenaryStackEnum.OsmItemStack -> "osm:${screen.osm_class}:${screen.osm_id}"
    else -> null
}

@Composable
fun SelectedLocationPinLayers(pin: SelectedLocationPin?) {
    val source = remember {
        GeoJsonSource(
            id = "selected-location-source",
            data = GeoJsonData.Features(
                FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())
            ),
            options = GeoJsonOptions()
        )
    }
    LaunchedEffect(pin?.position) {
        source.setData(
            if (pin == null) {
                GeoJsonData.Features(FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>()))
            } else {
                GeoJsonData.Features(Point(pin.position))
            }
        )
    }

    val darkMode = isSystemInDarkTheme()
    val marker = painterResource(R.drawable.selected_location_pin)
    SymbolLayer(
        id = "selected-location-pin",
        source = source,
        visible = pin != null,
        iconImage = image(marker),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true)
    )
    SymbolLayer(
        id = "selected-location-label",
        source = source,
        visible = pin != null,
        textField = format(span(pin?.name ?: "")),
        textSize = const(14.sp),
        textFont = const(listOf("Arimo-Medium")),
        textColor = const(if (darkMode) Color.White else Color.Black),
        textHaloColor = const(if (darkMode) Color.Black else Color.White),
        textHaloWidth = const(2.dp),
        textAnchor = const(SymbolAnchor.Bottom),
        textOffset = offset(0.em, (-2.5).em),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true)
    )
}
