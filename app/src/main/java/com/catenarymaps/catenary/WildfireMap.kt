package com.catenarymaps.catenary

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.value.*
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.expressions.dsl.offset

private const val TAG = "WildfireMap"

private const val EVACUATION_CA_FIRE_URL = "https://fireboundscache.catenarymaps.org/data/evac_california.json"
private const val LOS_ANGELES_EVAC_URL = "https://fireboundscache.catenarymaps.org/data/los_angeles_evac.json"
private const val WATCHDUTY_EVENTS_URL = "https://fireboundscache.catenarymaps.org/data/watchduty_events.json"
private const val MODIS_URL = "https://raw.githubusercontent.com/catenarytransit/fire-bounds-cache/refs/heads/main/data/modis.json"
private const val VIIRS_URL = "https://raw.githubusercontent.com/catenarytransit/fire-bounds-cache/refs/heads/main/data/viirs_nw.json"

@Serializable
data class WatchdutyFireData(
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val is_active: Boolean? = null,
    val data: WatchdutyFireDetails? = null,
    val evacuation_orders_arr: List<String>? = null,
    val evacuation_warnings_arr: List<String>? = null
)

@Serializable
data class WatchdutyFireDetails(
    val acreage: Double? = null,
    val containment: Int? = null,
    val manual_deactivation_started: Boolean? = null
)

fun createFirePointFeatures(fires: List<WatchdutyFireData>): FeatureCollection<Point, Map<String, Any>> {
    val features = fires
        .filter { it.is_active == true }
        .filter { it.data?.manual_deactivation_started != true }
        .filter { it.lat != null && it.lng != null }
        .map { fire ->
            val ha = (fire.data?.acreage ?: 0.0) * 0.4046
            Feature(
                geometry = Point(Position(fire.lng!!, fire.lat!!)),
                properties = mapOf(
                    "name" to (fire.name ?: "Unknown Fire"),
                    "acreage" to (fire.data?.acreage?.toString() ?: "0"),
                    "ha" to ha, // Double
                    "ha_rounded" to String.format("%.1f", ha),
                    "containment" to (fire.data?.containment?.toString() ?: "0")
                )
            ) as Feature<Point, Map<String, Any>>
        }
    return FeatureCollection(features)
}

@Composable
fun WildfireMapLayers(darkMode: Boolean = false) {
    val evacuationCaSource = rememberGeoJsonSource(
        data = GeoJsonData.Uri(EVACUATION_CA_FIRE_URL),
        options = GeoJsonOptions()
    )

    val laEvacSource = rememberGeoJsonSource(
        data = GeoJsonData.Uri(LOS_ANGELES_EVAC_URL),
        options = GeoJsonOptions()
    )

    val modisSource = rememberGeoJsonSource(
        data = GeoJsonData.Uri(MODIS_URL),
        options = GeoJsonOptions()
    )

    val viirsSource = rememberGeoJsonSource(
        data = GeoJsonData.Uri(VIIRS_URL),
        options = GeoJsonOptions()
    )

    var fireNamesData by remember { mutableStateOf<FeatureCollection<Point, Map<String, Any>>>(FeatureCollection(emptyList())) }
    
    val fireNamesSource = remember(fireNamesData) {
        GeoJsonSource(
            id = "firenames",
            data = GeoJsonData.Features(fireNamesData),
            options = GeoJsonOptions()
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val watchdutyResponse = ktorClient.get(WATCHDUTY_EVENTS_URL).body<List<WatchdutyFireData>>()
                fireNamesData = createFirePointFeatures(watchdutyResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch wildfire data", e)
            }
            delay(30_000)
        }
    }

    // --- Layers ---

    // Evacuation CA
    FillLayer(
        id = "evacuation_ca_fire_bounds",
        source = evacuationCaSource,
        minZoom = 5f,
        color = const(Color(0xFFDD3300)), 
        opacity = interpolate(
            linear(),
            zoom(),
            9.0 to const(0.3f),
            12.0 to const(0.2f),
            15.0 to const(0.2f),
            16.0 to const(0.15f)
        )
    )

    SymbolLayer(
        id = "evacuation_ca_fire_txt",
        source = evacuationCaSource,
        minZoom = 6f,
        textField = get("STATUS").cast<StringValue>(),
        textSize = interpolate(
            linear(),
            zoom(),
            7.0 to const(0.8f).em, // 8px approx 0.8em if base is 10? Assuming em unit.
            // Original: 8, 13. MapLibre default is usually 16px? 
            // AddShapes uses 0.5em ~ 8px.
            // Let's use em to match consistency.
            9.0 to const(1.3f).em
        ),
        textFont = const(listOf("NotoSans-Bold")),
        textColor = const(if (darkMode) Color(0xFFCCAAAA) else Color(0xFFCC0000))
    )

    // LA Evac
    FillLayer(
        id = "los_angeles_city_fire_evac_bounds",
        source = laEvacSource,
        minZoom = 5f,
        color = const(Color(0xFFDD3300)),
        opacity = interpolate(
            linear(),
            zoom(),
            9.0 to const(0.2f),
            12.0 to const(0.1f),
            15.0 to const(0.1f)
        )
    )

    SymbolLayer(
        id = "los_angeles_city_fire_evac_txt",
        source = laEvacSource,
        minZoom = 6f,
        textField = get("Label").cast<StringValue>(),
        textSize = interpolate(
            linear(),
            zoom(),
            7.0 to const(0.8f).em,
            9.0 to const(1.25f).em
        ),
        textFont = const(listOf("NotoSans-Bold")),
        textColor = const(if (darkMode) Color(0xFFCCAAAA) else Color(0xFFCC0000))
    )

    // Fire Names
    SymbolLayer(
        id = "firenameslabelwd",
        source = fireNamesSource,
        minZoom = 5.5f,
        iconImage = image(painterResource(R.drawable.fire_1f525)), // Image must be registered or available in style
        iconSize = interpolate(
            linear(),
            get("ha").cast<NumberValue<Number>>(),
            0.0 to const(0.03f),
            100.0 to const(0.04f),
            1000.0 to const(0.05f),
            5000.0 to const(0.06f)
        ),
        textField = get("name").cast<StringValue>() + const(" ") + get("ha_rounded").cast<StringValue>() + const("ha"),
        textOffset = offset(
            0.em,
            1.5.em
        ), // Check if DpOffsetValue is correct constructor or if we need offset() DSL
        textAnchor = const(SymbolAnchor.Top),
        textSize = interpolate(
            linear(),
            zoom(),
            6.0 to const(0.6f).em,
            12.0 to const(1.4f).em
        ),
        textFont = const(listOf("NotoSans-Medium")),
        textIgnorePlacement = const(true),
        iconIgnorePlacement = const(true),
        textColor = const(if (darkMode) Color(0xFFFFAAAA) else Color(0xFFAA0000))
    )

    // MODIS
    CircleLayer(
        id = "modis",
        source = modisSource,
        minZoom = 5f,
        color = interpolate(
            linear(),
            get("BRIGHTNESS").cast<NumberValue<Number>>(),
            310.64 to const(Color(0xFFFF751F)),
            508.63 to const(Color(0xFFFF1A1A))
        ),
        opacity = interpolate(
            linear(),
            get("BRIGHTNESS").cast<NumberValue<Number>>(),
            310.64 to const(0.3f),
            508.63 to const(0.5f)
        ),
        radius = interpolate(
            linear(),
            zoom(),
            5.0 to const(1.dp),
            9.0 to const(5.dp),
            12.0 to const(15.dp),
            15.0 to const(22.dp),
            22.0 to const(50.dp)
        )
    )

    // VIIRS
    CircleLayer(
        id = "viirs_nw",
        source = viirsSource,
        minZoom = 5f,
        color = interpolate(
            linear(),
            get("frp").cast<NumberValue<Number>>(),
            3.0 to const(Color(0xFFFF751F)),
            100.0 to const(Color(0xFFFF1A1A))
        ),
        opacity = interpolate(
            linear(),
            get("frp").cast<NumberValue<Number>>(),
            3.0 to const(0.1f),
            10.0 to const(0.3f),
            100.0 to const(0.4f)
        ),
        radius = interpolate(
            linear(),
            zoom(),
            5.0 to const(0.3.dp),
            9.0 to const(1.6.dp),
            12.0 to const(5.dp),
            15.0 to const(13.dp),
            22.0 to const(16.dp)
        )
    )
}
