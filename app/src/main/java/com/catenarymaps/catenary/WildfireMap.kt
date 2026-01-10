package com.catenarymaps.catenary

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.maplibre.compose.expressions.dsl.concat
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

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
                    "ha" to ha,
                    "ha_rounded" to String.format("%.1f", ha),
                    "containment" to (fire.data?.containment?.toString() ?: "0")
                )
            ) as Feature<Point, Map<String, Any>>
        }
    return FeatureCollection(features)
}

@Composable
fun WildfireMapLayers(darkMode: Boolean = false) {
    // 1. Sources without dynamic data loaded via explicit Ktor calls could be Uri sources,
    // but the original code had setIntervals to refresh them.
    // We can use rememberGeoJsonSource with Uri which might not auto-refresh unless key changes,
    // OR we can manually fetch and update data like the original code did.
    // Given the periodic updates, manual fetch into a state is better for strict parity.

    // However, existing simple sources in MainActivity just use GeoJsonData.Uri.
    // Let's use GeoJsonData.Uri for simpler static-ish sources, and manual for WatchDuty.

    val evacuationCaSource = rememberGeoJsonSource(
        id = "evacuation_ca_fire",
        data = GeoJsonData.Uri(EVACUATION_CA_FIRE_URL),
        options = GeoJsonOptions()
    )

    val laEvacSource = rememberGeoJsonSource(
        id = "los_angeles_city_fire_evac",
        data = GeoJsonData.Uri(LOS_ANGELES_EVAC_URL),
        options = GeoJsonOptions()
    )

    val modisSource = rememberGeoJsonSource(
        id = "modis",
        data = GeoJsonData.Uri(MODIS_URL),
        options = GeoJsonOptions()
    )

    val viirsSource = rememberGeoJsonSource(
        id = "viirs_nw",
        data = GeoJsonData.Uri(VIIRS_URL),
        options = GeoJsonOptions()
    )

    // WatchDuty is dynamic and processed
    var fireNamesData by remember { mutableStateOf<FeatureCollection<Point, Map<String, Any>>>(FeatureCollection(emptyList())) }
    
    // We need to construct the Source object.
    // Since FeatureCollection is immutable property of the source data state usually...
    // referencing usage in MainActivity: mutableStateOf(GeoJsonSource(..., GeoJsonData.Features(...)))
    
    val fireNamesSource = remember(fireNamesData) {
        GeoJsonSource(
            "firenames_wd",
            GeoJsonData.Features(fireNamesData),
            GeoJsonOptions()
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                // Fetch WatchDuty events
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
        fillColor = const(Color(0xFFDD3300)), 
        fillOpacity = interpolate(
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
        textField = get("STATUS"),
        textSize = interpolate(
            linear(),
            zoom(),
            7.0 to const(8f),
            9.0 to const(13f)
        ),
        textFont = const(listOf("NotoSans-Bold")),
        textColor = const(if (darkMode) Color(0xFFCCAAAA) else Color(0xFFCC0000))
    )

    // LA Evac
    FillLayer(
        id = "los_angeles_city_fire_evac_bounds",
        source = laEvacSource,
        minZoom = 5f,
        fillColor = const(Color(0xFFDD3300)),
        fillOpacity = interpolate(
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
        textField = get("Label"),
        textSize = interpolate(
            linear(),
            zoom(),
            7.0 to const(8f),
            9.0 to const(12.5f)
        ),
        textFont = const(listOf("NotoSans-Bold")),
        textColor = const(if (darkMode) Color(0xFFCCAAAA) else Color(0xFFCC0000))
    )

    // Fire Names
    SymbolLayer(
        id = "firenameslabelwd",
        source = fireNamesSource,
        minZoom = 5.5f,
        iconImage = const("fireicon"), // Note: Image must be loaded in the map style or via addImage
        iconSize = interpolate(
            linear(),
            get("ha"),
            0.0 to const(0.03f),
            100.0 to const(0.04f),
            1000.0 to const(0.05f),
            5000.0 to const(0.06f)
        ),
        textField = concat(get("name"), const(" "), get("ha_rounded"), const("ha")),
        textOffset = const(listOf(0f, 1.5f)),
        textAnchor = const("top"),
        textSize = interpolate(
            linear(),
            zoom(),
            6.0 to const(6f),
            12.0 to const(14f)
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
        circleColor = interpolate(
            linear(),
            get("BRIGHTNESS"),
            310.64 to const(Color(0xFFFF751F)),
            508.63 to const(Color(0xFFFF1A1A))
        ),
        circleOpacity = interpolate(
            linear(),
            get("BRIGHTNESS"),
            310.64 to const(0.3f),
            508.63 to const(0.5f)
        ),
        circleRadius = interpolate(
            linear(),
            zoom(),
            5.0 to const(1f),
            9.0 to const(5f),
            12.0 to const(15f),
            15.0 to const(22f),
            22.0 to const(50f)
        )
    )

    // VIIRS
    CircleLayer(
        id = "viirs_nw",
        source = viirsSource,
        minZoom = 5f,
        circleColor = interpolate(
            linear(),
            get("frp"),
            3.0 to const(Color(0xFFFF751F)),
            100.0 to const(Color(0xFFFF1A1A))
        ),
        circleOpacity = interpolate(
            linear(),
            get("frp"),
            3.0 to const(0.1f),
            10.0 to const(0.3f),
            100.0 to const(0.4f)
        ),
        circleRadius = interpolate(
            linear(),
            zoom(),
            5.0 to const(0.3f),
            9.0 to const(1.6f),
            12.0 to const(5f),
            15.0 to const(13f),
            22.0 to const(16f)
        )
    )
}
