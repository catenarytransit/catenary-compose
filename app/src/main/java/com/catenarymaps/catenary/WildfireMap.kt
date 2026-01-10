package com.catenarymaps.catenary

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.Feature.has
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.gte
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

private const val TAG = "WildfireMap"

// Data URLs
private const val EVACUATION_CA_FIRE_URL = "https://fireboundscache.catenarymaps.org/data/evac_california.json"
private const val LOS_ANGELES_EVAC_URL = "https://fireboundscache.catenarymaps.org/data/los_angeles_evac.json"
private const val WATCHDUTY_EVENTS_URL = "https://fireboundscache.catenarymaps.org/data/watchduty_events.json"

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

@Serializable
data class GeoJsonFeatureCollection(
    val type: String,
    val features: List<GeoJsonFeature>? = null
)

@Serializable
data class GeoJsonFeature(
    val type: String,
    val geometry: GeoJsonGeometry? = null,
    val properties: Map<String, String?>? = null
)

@Serializable
data class GeoJsonGeometry(
    val type: String,
    val coordinates: List<Double>? = null
)

/**
 * Creates fire point features from WatchDuty fire data.
 */
fun createFirePointFeatures(fires: List<WatchdutyFireData>): FeatureCollection {
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
                    "ha" to ha.toString(),
                    "ha_rounded" to String.format("%.1f", ha),
                    "containment" to (fire.data?.containment?.toString() ?: "0")
                )
            )
        }
    return FeatureCollection(features)
}

/**
 * Composable that adds wildfire map layers including evacuation zones and fire points.
 */
@Composable
fun WildfireMapLayers(darkMode: Boolean = false) {
    var evacuationCaData by remember { mutableStateOf<String?>(null) }
    var losAngelesEvacData by remember { mutableStateOf<String?>(null) }
    var fireNamesData by remember { mutableStateOf(FeatureCollection(emptyList())) }

    // Fetch and refresh data periodically
    LaunchedEffect(Unit) {
        while (true) {
            try {
                // Fetch evacuation data
                val evacuationResponse = ktorClient.get(EVACUATION_CA_FIRE_URL).body<String>()
                evacuationCaData = evacuationResponse

                val laEvacResponse = ktorClient.get(LOS_ANGELES_EVAC_URL).body<String>()
                losAngelesEvacData = laEvacResponse

                // Fetch WatchDuty events for fire points
                val watchdutyResponse = ktorClient.get(WATCHDUTY_EVENTS_URL).body<List<WatchdutyFireData>>()
                fireNamesData = createFirePointFeatures(watchdutyResponse)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch wildfire data", e)
            }
            delay(30_000) // Refresh every 30 seconds
        }
    }

    // California evacuation zones source
    evacuationCaData?.let { data ->
        GeoJsonSource(
            id = "evacuation_ca_fire",
            data = GeoJsonData.FromUrl(EVACUATION_CA_FIRE_URL)
        ) {
            // Evacuation fill layer
            FillLayer(
                id = "evacuation_ca_fire_bounds",
                minZoom = 5f,
                fillColor = const(Color(0xFFDD3300)), // Simplified - would need case expression
                fillOpacity = interpolate(
                    linear(),
                    zoom(),
                    9 to const(0.3f),
                    12 to const(0.2f),
                    15 to const(0.2f),
                    16 to const(0.15f)
                )
            )

            // Evacuation text layer
            SymbolLayer(
                id = "evacuation_ca_fire_txt",
                minZoom = 6f,
                textField = get("STATUS"),
                textSize = interpolate(
                    linear(),
                    zoom(),
                    7 to const(8f),
                    9 to const(13f)
                ),
                textFont = listOf("NotoSans-Bold"),
                textColor = const(if (darkMode) Color(0xFFCCAAAA) else Color(0xFFCC0000))
            )
        }
    }

    // Los Angeles evacuation zones source
    losAngelesEvacData?.let { data ->
        GeoJsonSource(
            id = "los_angeles_city_fire_evac",
            data = GeoJsonData.FromUrl(LOS_ANGELES_EVAC_URL)
        ) {
            // LA evacuation fill layer
            FillLayer(
                id = "los_angeles_city_fire_evac_bounds",
                minZoom = 5f,
                fillColor = const(Color(0xFFDD3300)), // Simplified - would need case expression
                fillOpacity = interpolate(
                    linear(),
                    zoom(),
                    9 to const(0.2f),
                    12 to const(0.1f),
                    15 to const(0.1f)
                )
            )

            // LA evacuation text layer
            SymbolLayer(
                id = "los_angeles_city_fire_evac_txt",
                minZoom = 6f,
                textField = get("Label"),
                textSize = interpolate(
                    linear(),
                    zoom(),
                    7 to const(8f),
                    9 to const(12.5f)
                ),
                textFont = listOf("NotoSans-Bold"),
                textColor = const(if (darkMode) Color(0xFFCCAAAA) else Color(0xFFCC0000))
            )
        }
    }

    // Fire names/points from WatchDuty
    GeoJsonSource(
        id = "firenames_wd",
        data = GeoJsonData.FromFeatureCollection(fireNamesData)
    ) {
        SymbolLayer(
            id = "firenameslabelwd",
            minZoom = 5.5f,
            iconImage = const("fireicon"),
            iconSize = interpolate(
                linear(),
                get("ha"),
                0 to const(0.03f),
                100 to const(0.04f),
                1000 to const(0.05f),
                5000 to const(0.06f)
            ),
            textField = get("name") + const(" ") + get("ha_rounded") + const("ha"),
            textOffset = listOf(0f, 1.5f),
            textAnchor = const("top"),
            textSize = interpolate(
                linear(),
                zoom(),
                6 to const(6f),
                12 to const(14f)
            ),
            textFont = listOf("NotoSans-Medium"),
            textIgnorePlacement = true,
            iconIgnorePlacement = true,
            textColor = const(if (darkMode) Color(0xFFFFAAAA) else Color(0xFFAA0000))
        )
    }

    // MODIS thermal hotspots (placeholder source - actual data loading would be similar)
    GeoJsonSource(
        id = "modis",
        data = GeoJsonData.FromUrl("https://raw.githubusercontent.com/catenarytransit/fire-bounds-cache/refs/heads/main/data/modis.json")
    ) {
        CircleLayer(
            id = "modis",
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
                5 to const(1f),
                9 to const(5f),
                12 to const(15f),
                15 to const(40f),
                22 to const(50f)
            )
        )
    }

    // VIIRS thermal hotspots
    GeoJsonSource(
        id = "viirs_nw",
        data = GeoJsonData.FromUrl("https://raw.githubusercontent.com/catenarytransit/fire-bounds-cache/refs/heads/main/data/viirs_nw.json")
    ) {
        CircleLayer(
            id = "viirs_nw",
            minZoom = 5f,
            circleColor = interpolate(
                linear(),
                get("frp"),
                3 to const(Color(0xFFFF751F)),
                100 to const(Color(0xFFFF1A1A))
            ),
            circleOpacity = interpolate(
                linear(),
                get("frp"),
                3 to const(0.1f),
                10 to const(0.3f),
                100 to const(0.4f)
            ),
            circleRadius = interpolate(
                linear(),
                zoom(),
                5 to const(0.3f),
                9 to const(1.6f),
                12 to const(5f),
                15 to const(13f),
                22 to const(16f)
            )
        )
    }
}
