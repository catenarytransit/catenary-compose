package com.catenarymaps.catenary

import com.catenarymaps.catenary.NavigationControls
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOutCirc
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Train
import io.ktor.client.plugins.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import org.maplibre.compose.expressions.dsl.image
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.catenarycompose.ui.theme.CatenaryComposeTheme
import org.maplibre.spatialk.geojson.Position
import kotlin.math.roundToInt
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.layers.*;
import org.maplibre.compose.sources.rememberVectorSource
import org.maplibre.compose.expressions.value.*;
import org.maplibre.compose.expressions.ast.*;
import org.maplibre.compose.expressions.dsl.em;
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.material3.TextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.zoom
import com.google.android.gms.location.Priority
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.maplibre.spatialk.geojson.Point
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.dsl.contains as dslcontains
import org.maplibre.compose.expressions.value.TextUnitValue
import org.maplibre.compose.map.RenderOptions
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateTo
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.compose.sources.GeoJsonSource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import org.maplibre.compose.expressions.value.EquatableValue
import org.maplibre.compose.expressions.value.NumberValue
import org.maplibre.compose.expressions.dsl.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.compose.enableComposeActionTracking
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.google.android.gms.analytics.GoogleAnalytics
import com.google.android.gms.analytics.Tracker
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.checkerframework.checker.units.qual.min
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.neq
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.util.ClickResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger.Companion.config
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.LineString
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import org.maplibre.compose.expressions.dsl.Feature.has
import org.maplibre.compose.expressions.dsl.condition
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.turf.measurement.distance

fun parseColor(colorString: String?, default: Color = Color.Black): Color {
    return try {
        val cleanColor = colorString?.removePrefix("#") ?: ""
        Color(android.graphics.Color.parseColor("#$cleanColor"))
    } catch (e: Exception) {
        default
    }
}

private const val PREFS_NAME = "catenary_prefs"
private const val K_LAT = "camera_lat"
private const val K_LON = "camera_lon"
private const val K_ZOOM = "camera_zoom"
private const val K_BEAR = "camera_bearing"
private const val K_TILT = "camera_tilt"

private const val K_PIN_ACTIVE = "pin_active"
private const val K_PIN_LAT = "pin_lat"
private const val K_PIN_LON = "pin_lon"

private const val K_DATADOG_CONSENT = "datadog_consent"
private const val K_GA_CONSENT = "ga_consent"

private const val K_SHOW_ZOMBIE_BUSES = "show_zombie_buses"
private const val K_USE_US_UNITS = "use_us_units"

private fun SharedPreferences.putDouble(key: String, value: Double) =
    edit().putLong(key, java.lang.Double.doubleToRawLongBits(value))
        .apply()

private fun SharedPreferences.getDouble(key: String, default: Double = Double.NaN): Double {
    if (!contains(key)) return default
    return java.lang.Double.longBitsToDouble(getLong(key, 0L))
}

@Serializable
data class StopPreviewRequest(
    val chateaus: Map<String, List<String>>
)

@Serializable
data class StopPreviewResponse(
    @EncodeDefault val stops: Map<String, Map<String, StopPreviewDetail>> = emptyMap(),
    @EncodeDefault val routes: Map<String, Map<String, RoutePreviewDetail>> = emptyMap()
)

@Serializable
data class StopPreviewDetail(
    val level_id: String? = null,
    val platform_code: String? = null,
    @EncodeDefault val routes: List<String> = emptyList()
)

@Serializable
data class RoutePreviewDetail(
    val color: String,
    val text_color: String,
    val short_name: String? = null,
    val long_name: String? = null
)

private data class SavedCamera(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double
)

private fun SharedPreferences.readSavedCamera(): SavedCamera? {
    if (!contains(K_LAT) || !contains(K_LON) || !contains(K_ZOOM)) return null
    return SavedCamera(
        lat = getDouble(K_LAT),
        lon = getDouble(K_LON),
        zoom = getDouble(K_ZOOM),
        bearing = getDouble(K_BEAR).takeIf { !it.isNaN() } ?: 0.0,
        tilt = getDouble(K_TILT).takeIf { !it.isNaN() } ?: 0.0
    )
}

private fun SharedPreferences.writeCamera(pos: CameraPosition) {
    putDouble(K_LAT, pos.target.latitude)
    putDouble(K_LON, pos.target.longitude)
    putDouble(K_ZOOM, pos.zoom)
    putDouble(K_BEAR, pos.bearing)
    putDouble(K_TILT, pos.tilt)
}

private fun SharedPreferences.readPinState(): PinState? {
    if (!contains(K_PIN_ACTIVE)) return null
    val active = getBoolean(K_PIN_ACTIVE, false)
    if (!active) return PinState(active = false)

    val lat = getDouble(K_PIN_LAT)
    val lon = getDouble(K_PIN_LON)

    if (lat.isNaN() || lon.isNaN()) return PinState(active = false)

    return PinState(active = true, position = Position(lon, lat))
}

private fun SharedPreferences.writePinState(pin: PinState) {
    edit().apply {
        putBoolean(K_PIN_ACTIVE, pin.active)
        if (pin.active && pin.position != null) {
            putLong(K_PIN_LAT, java.lang.Double.doubleToRawLongBits(pin.position.latitude))
            putLong(K_PIN_LON, java.lang.Double.doubleToRawLongBits(pin.position.longitude))
        }
    }.apply()
}

object LayersPerCategory {
    object Bus {
        const val Shapes = "bus-shapes"
        const val LabelShapes = "bus-labelshapes"
        const val Stops = "bus-stops"
        const val LabelStops = "bus-labelstops"
        const val Livedots = "bus-livedots"
        const val Labeldots = "bus-labeldots"
        const val Pointing = "bus-pointing"
        const val PointingShell = "bus-pointingshell"
    }

    object Other {
        const val Shapes = "other-shapes"
        const val LabelShapes = "other-labelshapes"
        const val FerryShapes = "ferryshapes"
        const val Stops = "other-stops"
        const val LabelStops = "other-labelstops"
        const val Livedots = "other-livedots"
        const val Labeldots = "other-labeldots"
        const val Pointing = "other-pointing"
        const val PointingShell = "other-pointingshell"
    }

    object IntercityRail {
        const val Shapes = "intercityrail-shapes"
        const val LabelShapes = "intercityrail-labelshapes"
        const val Stops = "intercityrail-stops"
        const val LabelStops = "intercityrail-labelstops"
        const val Livedots = "intercityrail-livedots"
        const val Labeldots = "intercityrail-labeldots"
        const val Pointing = "intercityrail-pointing"
        const val PointingShell = "intercityrail-pointingshell"
    }

    object Metro {
        const val Shapes = "metro-shapes"
        const val LabelShapes = "metro-labelshapes"
        const val Stops = "metro-stops"
        const val LabelStops = "metro-labelstops"
        const val Livedots = "metro-livedots"
        const val Labeldots = "metro-labeldots"
        const val Pointing = "metro-pointing"
        const val PointingShell = "metro-pointingshell"
    }

    object Tram {
        const val Shapes = "tram-shapes"
        const val LabelShapes = "tram-labelshapes"
        const val Stops = "tram-stops"
        const val LabelStops = "tram-labelstops"
        const val Livedots = "tram-livedots"
        const val Labeldots = "tram-labeldots"
        const val Pointing = "tram-pointing"
        const val PointingShell = "tram-pointingshell"
    }
}

@Serializable
// Top-level data structure for all layer settings
data class AllLayerSettings(
    var bus: LayerCategorySettings = LayerCategorySettings(),
    var localrail: LayerCategorySettings = LayerCategorySettings(),
    var intercityrail: LayerCategorySettings = LayerCategorySettings(
        labelrealtimedots = LabelSettings(
            trip = true
        )
    ),
    var other: LayerCategorySettings = LayerCategorySettings(),
    var more: MoreSettings = MoreSettings()
) {
    // Helper to get a category by string key, useful for abstracting UI
    operator fun get(key: String): Any? {
        return when (key) {
            "bus" -> bus
            "localrail" -> localrail
            "intercityrail" -> intercityrail
            "other" -> other
            "more" -> more
            else -> null
        }
    }


}

// Read helper
fun AllLayerSettings.category(tab: String): LayerCategorySettings? = when (tab) {
    "bus" -> bus
    "localrail" -> localrail
    "intercityrail" -> intercityrail
    "other" -> other
    else -> null
}

// Write helper
fun AllLayerSettings.updateCategory(
    tab: String,
    transform: (LayerCategorySettings) -> LayerCategorySettings
): AllLayerSettings = when (tab) {
    "bus" -> copy(bus = transform(bus))
    "localrail" -> copy(localrail = transform(localrail))
    "intercityrail" -> copy(intercityrail = transform(intercityrail))
    "other" -> copy(other = transform(other))
    else -> this
}

@Serializable
// Label settings (route/trip/vehicle/etc.)
data class LabelSettings(
    var route: Boolean = true,
    var trip: Boolean = false,
    var vehicle: Boolean = false,
    var headsign: Boolean = false,
    var direction: Boolean = false,
    var speed: Boolean = false,
    var occupancy: Boolean = true,
    var delay: Boolean = true
)

@Serializable
// Main category settings (bus/localrail/intercityrail/other)
data class LayerCategorySettings(
    var visiblerealtimedots: Boolean = true,
    var labelshapes: Boolean = true,
    var stops: Boolean = true,
    var shapes: Boolean = true,
    var labelstops: Boolean = true,
    var labelrealtimedots: LabelSettings = LabelSettings()
)

@Serializable
// Extra "more" settings
data class FoamermodeSettings(
    var infra: Boolean = false,
    var maxspeed: Boolean = false,
    var signalling: Boolean = false,
    var electrification: Boolean = false,
    var gauge: Boolean = false,
    var dummy: Boolean = true
)

@Serializable
data class MoreSettings(
    var foamermode: FoamermodeSettings = FoamermodeSettings(),
    var showstationentrances: Boolean = true,
    var showstationart: Boolean = false,
    var showbikelanes: Boolean = false,
    var showcoords: Boolean = false
)


// Layer Settings Persistence
private const val K_LAYER_SETTINGS = "layer_settings_v1"

private fun SharedPreferences.writeLayerSettings(settings: AllLayerSettings) {
    val json = Json { ignoreUnknownKeys = true }
    try {
        val settingsJson = json.encodeToString(AllLayerSettings.serializer(), settings)
        edit().putString(K_LAYER_SETTINGS, settingsJson).apply()
    } catch (e: Exception) {
        Log.e("LayerSettings", "Failed to serialize and save layer settings", e)
    }
    Log.d("LayerSettings", "Saved layer settings to SharedPreferences.")
}

private fun SharedPreferences.readLayerSettings(): AllLayerSettings? {
    val json = Json { ignoreUnknownKeys = true }
    val settingsJson = getString(K_LAYER_SETTINGS, null) ?: return null
    return try {
        json.decodeFromString<AllLayerSettings>(settingsJson)
    } catch (e: Exception) {
        Log.e("LayerSettings", "Failed to deserialize layer settings", e)
        null
    }
}

val STOP_SOURCES = mapOf(
    "busstops" to "https://birch6.catenarymaps.org/busstops",
    "stationfeatures" to "https://birch7.catenarymaps.org/station_features",
    "railstops" to "https://birch5.catenarymaps.org/railstops",
    "otherstops" to "https://birch8.catenarymaps.org/otherstops"
)

private const val TAG = "CatenaryDebug"
var visibleChateaus: List<String> = emptyList()

var railinframe by mutableStateOf(false)

val easeOutSpec: AnimationSpec<Float> = tween(
    durationMillis = 300, delayMillis = 0, easing = EaseOutCirc
)

enum class SheetSnapPoint { Collapsed, PartiallyExpanded, Expanded }

@Serializable
private data class RailCountResponse(
    @SerialName("intercityrail_shapes") val intercityRailShapes: Int = 0,
    @SerialName("metro_shapes") val metroShapes: Int = 0,
    @SerialName("tram_shapes") val tramShapes: Int = 0
)

private var lastRailQueryTime = 0L

private suspend fun fetchRailCountInBox(bounds: BoundingBox): RailCountResponse? {
    return try {
        val url = "https://birch.catenarymaps.org/countrailinbox?min_y=${bounds.south - 0.03}" +
                "&max_y=${bounds.north + 0.03}&min_x=${bounds.west - 0.03}&max_x=${bounds.east - 0.03}"
        ktorClient.get(url).body<RailCountResponse>()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch rail count in box", e)
        null
    }
}


private fun queryVisibleChateaus(scope: CoroutineScope, camera: CameraState, mapSize: IntSize) {
    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
        val projection = camera.projection ?: return@launch
        if (mapSize.width == 0 || mapSize.height == 0) return@launch

        val density = Resources.getSystem().displayMetrics.density
        val rect = DpRect(
            left = 0.dp,
            top = 0.dp,
            right = (mapSize.width / density).dp,
            bottom = (mapSize.height / density).dp
        )

        val startTime = SystemClock.uptimeMillis()
        val features = projection.queryRenderedFeatures(
            rect = rect, layerIds = setOf("chateaus_calc")
        )
        val queryTime = SystemClock.uptimeMillis() - startTime

        val names = features.map { f ->
            f.properties?.get("chateau")?.toString()?.trimStart('"')?.trimEnd('"') ?: "Unknown"
        }
        visibleChateaus = names
        Log.d(
            TAG,
            "Visible chateaus query took ${queryTime}ms. Found ${names.size}: ${
                names.joinToString(
                    limit = 100
                )
            }"
        )
    }
}

@Serializable
data class VehiclePositionData(
    val latitude: Double, val longitude: Double, val bearing: Float?, val speed: Float?
)

@Serializable
data class VehicleDescriptor(
    val id: String?, val label: String?
)

@Serializable
data class TripDescriptor(
    val trip_id: String?,
    val route_id: String?,
    val trip_headsign: String?,
    val trip_short_name: String?,
    val start_time: String?,
    val start_date: String?,
    val delay: Int?
)

@Serializable
data class VehiclePosition(
    val position: VehiclePositionData?,
    val vehicle: VehicleDescriptor?,
    val trip: TripDescriptor?,
    val route_type: Int,
    val timestamp: Long?,
    val occupancy_status: Int?
)

@Serializable
data class RouteCacheEntry(
    val color: String,
    val text_color: String,
    val short_name: String?,
    val long_name: String?,
    val route_id: String,
    val agency_id: String?,
)

@Serializable
data class CategoryData(
    val vehicle_positions: Map<String, VehiclePosition>?,
    val last_updated_time_ms: Long,
    val hash_of_routes: ULong
)

@Serializable
data class ChateauData(
    val categories: Map<String, CategoryData?>
)

@Serializable
data class BulkRealtimeResponse(
    val chateaus: Map<String, ChateauData>
)

@Serializable
data class TileBounds(
    val min_x: Int,
    val max_x: Int,
    val min_y: Int,
    val max_y: Int
)

@Serializable
data class CategoryParams(
    @EncodeDefault var hash_of_routes: ULong = ULong.MIN_VALUE,
    @EncodeDefault var last_updated_time_ms: Long = 0
)

@Serializable
data class ChateauFetchParams(
    val category_params: Map<String, CategoryParams>
)

@Serializable
data class BulkRealtimeRequest(
    val categories: List<String>, val chateaus: Map<String, ChateauFetchParams>
)// 1. Create the OkHttp dispatcher

val httpDispatcher = Dispatcher().apply {
    maxRequests = Integer.MAX_VALUE
    maxRequestsPerHost = Integer.MAX_VALUE
}

// Ktor HTTP Client (initialize once)
val ktorClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
            encodeDefaults = true
        })
    }

    engine {
        maxConnectionsCount = Int.MAX_VALUE
        endpoint {
            maxConnectionsPerRoute = Int.MAX_VALUE
            pipelineMaxSize = Int.MAX_VALUE
            connectAttempts = 5
        }
    }
}

class MainActivity : ComponentActivity() {


    //private var tracker: Tracker? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // No-op on purpose.
    }

    private val layerSettings = mutableStateOf(AllLayerSettings())


    private fun queryAreAnyRailFeaturesVisible(
        camera: CameraState, mapSize: IntSize, forceRun: Boolean = false, distance_m: Double = 0.0
    ) {
        println("query any rail features attempted")
        val now = SystemClock.uptimeMillis()
        if ((now - lastRailQueryTime < 1000L && forceRun == false) && distance_m < 100000) return
        lastRailQueryTime = now

        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val projection = camera.projection ?: return@launch
            if (mapSize.width == 0 || mapSize.height == 0) return@launch

            val currentBoundingBox = projection.queryVisibleBoundingBox()

            launch(kotlinx.coroutines.Dispatchers.IO) {
                val responseFromRailCounting = fetchRailCountInBox(currentBoundingBox)



                if (responseFromRailCounting != null) {


                    android.os.Handler(Looper.getMainLooper()).post {
                        // This will queue the task to run when the main thread has processed other pending messages.
                        android.os.Handler(Looper.getMainLooper()).post {
                            val density = Resources.getSystem().displayMetrics.density
                            val rect = DpRect(
                                left = 0.dp,
                                top = 0.dp,
                                right = (mapSize.width / density).dp,
                                bottom = (mapSize.height / density).dp
                            )

                            val layerSettingsValue = layerSettings.value
                            val intercityRailShapesCount =
                                if (layerSettingsValue.intercityrail.shapes || layerSettingsValue.intercityrail.labelshapes) (responseFromRailCounting?.intercityRailShapes
                                    ?: 0) else 0
                            val localRailShapesCount =
                                if (layerSettingsValue.localrail.shapes || layerSettingsValue.localrail.labelshapes) {
                                    (responseFromRailCounting?.metroShapes
                                        ?: 0) + (responseFromRailCounting?.tramShapes ?: 0)
                                } else {
                                    0
                                }

                            val metroRailShapesCount =
                                if (layerSettingsValue.localrail.shapes || layerSettingsValue.localrail.labelshapes) {
                                    (responseFromRailCounting?.metroShapes ?: 0)
                                } else {
                                    0
                                }


                            val waitStartTime = SystemClock.uptimeMillis()

                            val queryFeatureDotTimer = SystemClock.uptimeMillis()


                            val featuresDotsCount = projection.queryRenderedFeatures(
                                rect = rect, layerIds = setOf(
                                    //   LayersPerCategory.Tram.Stops,
                                    //   LayersPerCategory.Tram.Livedots,
                                    LayersPerCategory.Metro.Stops,
                                    LayersPerCategory.Metro.Livedots,
                                    LayersPerCategory.IntercityRail.Stops,
                                    LayersPerCategory.IntercityRail.Livedots,
                                )
                            ).size

                            val queryFeatureDotDuration =
                                SystemClock.uptimeMillis() - queryFeatureDotTimer


                            android.os.Handler(Looper.getMainLooper()).post {
                                val handlerWaitTime1 = SystemClock.uptimeMillis() - waitStartTime
                                val queryStartTime1 = SystemClock.uptimeMillis()

                                //                val intercityRailFeaturesCount = projection.queryRenderedFeatures(
                                //                    rect = rect, layerIds = setOf(
                                //                        LayersPerCategory.IntercityRail.Shapes,
                                //                    )
                                //                ).size

                                val queryTime1 = SystemClock.uptimeMillis() - queryStartTime1
                                val waitStartTime2 = SystemClock.uptimeMillis()

                                android.os.Handler(Looper.getMainLooper()).post {
                                    val handlerWaitTime2 =
                                        SystemClock.uptimeMillis() - waitStartTime2
                                    val queryStartTime2 = SystemClock.uptimeMillis()

                                    val queryTime2 = SystemClock.uptimeMillis() - queryStartTime2

                                    val totalCount =
                                        featuresDotsCount + intercityRailShapesCount + localRailShapesCount

                                    Log.d(
                                        TAG,
                                        "after ${queryFeatureDotDuration} ms, total Count of rail items ${totalCount} with dots ${featuresDotsCount} and shapes ${intercityRailShapesCount} & ${metroRailShapesCount}"
                                    )


                                    val manyVisible =
                                        featuresDotsCount >= 100 || intercityRailShapesCount + metroRailShapesCount >= 20
                                    railinframe = manyVisible
                                }
                            }

                        }

                    }
                }
            }


        }
    }

    val applyFilterToLiveDots = mutableStateOf<Expression<BooleanValue>>(const(true))

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val isFetchingRealtimeData = AtomicBoolean(false)

    // Realtime Data State Holders
    // ChateauID -> RouteID -> Cache
    var realtimeVehicleRouteCache =
        mutableStateOf<Map<String, Map<String, RouteCacheEntry>>>(emptyMap())

    var routeCacheAgenciesKnown = mutableStateOf<Map<String, List<String>>>(emptyMap())

    // ChateauID -> Category -> Timestamp
    var realtimeVehicleLocationsLastUpdated =
        mutableStateOf<Map<String, Map<String, Long>>>(emptyMap())

    // category -> chateau -> x -> y -> vehicle_id -> vehicle_data
    var realtimeVehicleLocationsStoreV2 =
        mutableStateOf<Map<String, Map<String, Map<Int, Map<Int, Map<String, VehiclePosition>>>>>>(
            emptyMap()
        )

    // chateau -> category -> TileBounds
    var previousTileBoundariesStore =
        mutableStateOf<Map<String, Map<String, TileBounds>>>(emptyMap())


    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val action: String? = intent?.action
        val initial_uri: Uri? = intent?.data

        println("url data ${initial_uri}")

        val firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // --- Load Pin State ---
        val initialPinState = prefs.readPinState() ?: PinState()

        // --- Load Layer Settings ---
        val initialLayerSettings = prefs.readLayerSettings() ?: AllLayerSettings()

        val initialDatadogConsent = prefs.getBoolean(K_DATADOG_CONSENT, false)
        val trackingConsent =
            if (initialDatadogConsent) TrackingConsent.GRANTED else TrackingConsent.NOT_GRANTED

        val initialGaConsent = prefs.getBoolean(K_GA_CONSENT, true)

        val initialOptOut = !initialGaConsent
        val initialShowZombieBuses = prefs.getBoolean(K_SHOW_ZOMBIE_BUSES, false)
        val initialUsUnits = prefs.getBoolean(K_USE_US_UNITS, false)

        try {

            firebaseAnalytics.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (initialGaConsent) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
                )
            )
            Log.d(TAG, "Initial Google Analytics opt-out state set to: $initialOptOut")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set initial GA opt-out: ${e.message}")
        }

        val applicationId = "5201846b-e68a-4388-a47c-a9508e3f3dc2"
        val clientToken = "pub6a98d8da258f8b43df56ceb1c6203a16"
        val environmentName = "prod"
        val appVariantName = "catenary"

        val configuration = com.datadog.android.core.configuration.Configuration.Builder(
            clientToken = clientToken,
            env = environmentName,
            variant = appVariantName
        )
            .useSite(DatadogSite.US1)
            .build()
        // Initialize with the saved consent state
        Datadog.initialize(this, configuration, trackingConsent)

        val rumConfiguration = RumConfiguration.Builder(applicationId)
            .trackUserInteractions()
            .enableComposeActionTracking()
            .build()
        Rum.enable(rumConfiguration)

        fun fetchLocation(onSuccess: (Double, Double) -> Unit) {
            // Check permission
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
                )
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    onSuccess(it.latitude, it.longitude)
                }
            }
        }

        // Optional: handle permission result
        fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation { lat, lon ->
                    // You could trigger a recomposition by setting state again


                }
            }
        }

        fun pruneStaleChateauData() {
            // Get a snapshot of the currently visible chateaus
            val visibleSet = visibleChateaus.toSet()
            Log.d(TAG, "Pruning data. Keeping ${visibleSet.size} chateaus.")

            // Prune maps keyed by ChateauID
            val currentCache = realtimeVehicleRouteCache.value
            val currentCacheKeys = currentCache.keys
            if (currentCacheKeys.size != visibleSet.size || !currentCacheKeys.containsAll(visibleSet)) {
                realtimeVehicleRouteCache.value = currentCache.filterKeys { it in visibleSet }
            }

            val currentLastUpdated = realtimeVehicleLocationsLastUpdated.value
            val currentLastUpdatedKeys = currentLastUpdated.keys
            if (currentLastUpdatedKeys.size != visibleSet.size || !currentLastUpdatedKeys.containsAll(
                    visibleSet
                )
            ) {
                realtimeVehicleLocationsLastUpdated.value =
                    currentLastUpdated.filterKeys { it in visibleSet }
            }

            val currentAgenciesKnown = routeCacheAgenciesKnown.value
            val currentAgenciesKnownKeys = currentAgenciesKnown.keys
            if (currentAgenciesKnownKeys.size != visibleSet.size || !currentAgenciesKnownKeys.containsAll(
                    visibleSet
                )
            ) {
                routeCacheAgenciesKnown.value =
                    currentAgenciesKnown.filterKeys { it in visibleSet }
            }

            // Prune the 'realtimeVehicleLocations' map (which is keyed by Category first)
            val currentLocationsV2 = realtimeVehicleLocationsStoreV2.value
            // Early exit if no pruning is needed at all.
            if (currentLocationsV2.values.all { chateauMap ->
                    chateauMap.keys.size == visibleSet.size && chateauMap.keys.containsAll(
                        visibleSet
                    )
                }) {
                // All categories already have the correct set of chateaus, nothing to do.
            } else {

                var locationsV2Modified = false
                val newLocationsV2 = currentLocationsV2.toMutableMap()

                currentLocationsV2.forEach { (category, chateauMap) ->
                    if (chateauMap.keys.any { it !in visibleSet }) {
                        newLocationsV2[category] = chateauMap.filterKeys { it in visibleSet }
                        locationsV2Modified = true
                    }
                }

                if (locationsV2Modified) {
                    realtimeVehicleLocationsStoreV2.value = newLocationsV2
                }
            }

            val currentTileBoundaries = previousTileBoundariesStore.value
            val currentTileBoundariesKeys = currentTileBoundaries.keys
            if (currentTileBoundariesKeys.size != visibleSet.size || !currentTileBoundariesKeys.containsAll(
                    visibleSet
                )
            ) {
                previousTileBoundariesStore.value =
                    currentTileBoundaries.filterKeys { it in visibleSet }
            }


        }


        setContent {
            val context = LocalContext.current

            // --- Moved from onCreate ---
            // The state that will hold our layer settings. Initialize with loaded or default.
            val layerSettings = remember { mutableStateOf(initialLayerSettings) }

            // Whenever layerSettings changes, save it to SharedPreferences.
            LaunchedEffect(layerSettings.value) {
                prefs.writeLayerSettings(layerSettings.value)
                //reset the timer for the rail visible settings

                lastRailQueryTime = 0L
            }
            // --- End of move ---


            val scope = rememberCoroutineScope()
            val snackbars = remember { SnackbarHostState() }

            val appUpdateManager =
                remember { AppUpdateManagerFactory.create(this.applicationContext) }


            var stopsToHide by remember { mutableStateOf(emptySet<String>()) }

            val transitShapeSourceRef =
                remember { mutableStateOf<MutableState<GeoJsonSource>?>(null) }
            val transitShapeDetourSourceRef =
                remember { mutableStateOf<MutableState<GeoJsonSource>?>(null) }
            val stopsContextSourceRef =
                remember { mutableStateOf<MutableState<GeoJsonSource>?>(null) }
            val transitShapeForStopSourceRef =
                remember { mutableStateOf<MutableState<GeoJsonSource>?>(null) }
            val majorDotsSourceRef =
                remember { mutableStateOf<MutableState<GeoJsonSource>?>(null) }

            val density = LocalDensity.current
            var pin by remember { mutableStateOf(initialPinState) }

            // Whenever pin state changes, save it to SharedPreferences.
            LaunchedEffect(pin) {
                prefs.writePinState(pin)
            }

            val searchViewModel: SearchViewModel = viewModel()


            var catenaryStack by remember { mutableStateOf(ArrayDeque<CatenaryStackEnum>()) }

            // Handle back button presses
            androidx.activity.compose.BackHandler(enabled = catenaryStack.isNotEmpty()) {
                // This block will be executed when the back button is pressed
                // and the catenaryStack is not empty.
                if (catenaryStack.isNotEmpty()) {
                    val newStack = ArrayDeque(catenaryStack)
                    newStack.removeLast()
                    catenaryStack = newStack
                }
                // By providing this custom handler, we prevent the default back
                // action (like closing the activity) from happening when the
                // stack has items.
            }

            val usePickedLocation = pin.active && pin.position != null
            val pickedPair: Pair<Double, Double>? =
                pin.position?.let { it.latitude to it.longitude }

            var mapSize by remember { mutableStateOf(IntSize.Zero) }

            val (datadogConsent, setDatadogConsent) = remember {
                mutableStateOf(
                    initialDatadogConsent
                )
            }

            // vvv RENAME THIS from onConsentChanged to onDatadogConsentChanged vvv
            val onDatadogConsentChanged: (Boolean) -> Unit = { isChecked ->
                // Update the UI state
                setDatadogConsent(isChecked)

                // Update Datadog SDK
                val newConsent =
                    if (isChecked) TrackingConsent.GRANTED else TrackingConsent.NOT_GRANTED
                Datadog.setTrackingConsent(newConsent)

                // Save to SharedPreferences
                prefs.edit().putBoolean(K_DATADOG_CONSENT, isChecked).apply()
            }

            // vvv ADD THIS NEW STATE AND HANDLER FOR GA vvv
            val (gaConsent, setGaConsent) = remember { mutableStateOf(initialGaConsent) }
            val onGaConsentChanged: (Boolean) -> Unit = { isChecked ->
                // Update the UI state
                setGaConsent(isChecked)

                // Save to SharedPreferences
                prefs.edit().putBoolean(K_GA_CONSENT, isChecked).apply()

                val optOut = !isChecked
                try {
                    GoogleAnalytics.getInstance(this).appOptOut = optOut

                    firebaseAnalytics.setConsent(
                        mapOf(
                            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (isChecked) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                            FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                            FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
                        )
                    )

                    Log.d(TAG, "Google Analytics consent set to: $isChecked (appOptOut: $optOut)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set GA opt-out on toggle: ${e.message}")
                }
            }

            val onConsentChanged: (Boolean) -> Unit = { isChecked ->
                // Update the UI state
                setDatadogConsent(isChecked)

                // Update Datadog SDK
                val newConsent =
                    if (isChecked) TrackingConsent.GRANTED else TrackingConsent.NOT_GRANTED
                Datadog.setTrackingConsent(newConsent)

                // Save to SharedPreferences
                prefs.edit().putBoolean(K_DATADOG_CONSENT, isChecked).apply()
            }

// Button actions (same behavior as your JS app)
            val onMyLocation: () -> Unit = {
                pin = pin.copy(active = false, position = null)
            }

            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val saved = prefs.readSavedCamera()

            // Camera
            // If there's a saved camera, start there. Otherwise, start somewhere neutral;
            // we'll jump to the user's location at zoom=13 as soon as get it.
            val initialCamera = saved?.let {
                CameraPosition(
                    target = Position(it.lon, it.lat),
                    zoom = it.zoom,
                    bearing = it.bearing,
                    tilt = it.tilt,
                    padding = PaddingValues(0.dp)
                )
            } ?: CameraPosition(
                target = Position(-118.250, 34.050), // temporary fallback until get location
                zoom = 6.0,
                padding = PaddingValues(0.dp)
            )

            val camera = rememberCameraState(firstPosition = initialCamera)
            // State for settings from JS
            var showZombieBuses by remember { mutableStateOf(initialShowZombieBuses) }
            var usUnits by remember { mutableStateOf(initialUsUnits) }

            LaunchedEffect(showZombieBuses) {
                prefs.edit().putBoolean(K_SHOW_ZOMBIE_BUSES, showZombieBuses).apply()
            }

            LaunchedEffect(usUnits) {
                prefs.edit().putBoolean(K_USE_US_UNITS, usUnits).apply()
            }
            val isDark = isSystemInDarkTheme()


            // Realtime Fetcher Logic
            val rtScope = rememberCoroutineScope()

            // Periodic fetcher (e.g., every 5 seconds)
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000L) // 1 second refresh interval
                    fetchRealtimeData(
                        scope = rtScope,
                        zoom = camera.position.zoom,
                        settings = layerSettings.value,
                        isFetchingRealtimeData = isFetchingRealtimeData,
                        visibleChateaus = visibleChateaus,
                        realtimeVehicleLocationsLastUpdated = realtimeVehicleLocationsLastUpdated,
                        ktorClient = ktorClient,
                        realtimeVehicleRouteCache = realtimeVehicleRouteCache,
                        routeCacheAgenciesKnown = routeCacheAgenciesKnown,
                        camera = camera,
                        previousTileBoundariesStore = previousTileBoundariesStore,
                        realtimeVehicleLocationsStoreV2 = realtimeVehicleLocationsStoreV2
                    )
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(2_000L) // 2 second prune interval
                    pruneStaleChateauData()
                }
            }


            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

            // Start live location updates and keep currentLocation in sync
            val hasFinePermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            DisposableEffect(hasFinePermission) {
                if (!hasFinePermission) {
                    // Request permission if needed; you can keep your existing request flow
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        1001
                    )
                    onDispose { }
                } else {
                    // Build a request: high accuracy, ~5s desired interval, 2s min, and 5m min distance
                    val request = LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, /* intervalMillis = */ 5_000L
                    )

                        .setMinUpdateIntervalMillis(2_000L)
                        .setMinUpdateDistanceMeters(5f)
                        .build()

                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val loc = result.lastLocation ?: return
                            currentLocation = loc.latitude to loc.longitude
                            // If you want to follow the user, you can animate the camera here (optional):

                            // camera.animateTo(camera.position.copy(target = Position(loc.longitude, loc.latitude)))
                        }
                    }

                    // Start updates on the main looper
                    fusedLocationClient.requestLocationUpdates(
                        request, callback, Looper.getMainLooper()
                    )

                    // Stop updates when this composable leaves composition
                    onDispose {
                        fusedLocationClient.removeLocationUpdates(callback)
                    }
                }
            }

            val onPinDrop: () -> Unit = {
                //val proj = camera.projection

                println("on pin drop triggered")

                if (pin.position == null) {

                    // val centerDp = with(density) {
                    // DpOffset((mapSize.width / 2f).toDp(), (mapSize.height / 2f).toDp())
                    //val pos = proj.positionFromScreenLocation(centerDp)
                    val camerapos = camera.position
                    val pos = Position(
                        latitude = camerapos.target.latitude, longitude = camerapos.target.longitude
                    )
                    pin = PinState(active = true, position = pos)


                } else {


                    pin = pin.copy(active = true, position = pin.position)

                }


            }

            val onCenterPin: () -> Unit = {
                println("on centre pin dropped")

                val camerapos = camera.position
                val pos = Position(
                    latitude = camerapos.target.latitude, longitude = camerapos.target.longitude
                )

                pin = PinState(active = true, position = pos)

            }


            val geoLock = rememberGeoLockController()

            // Launch location fetch once
            LaunchedEffect(Unit) {
                fetchLocation { lat, lon ->
                    currentLocation = lat to lon
                }
            }

            var didInitialFollow by remember { mutableStateOf(false) }
            val hadSavedView = remember { saved != null }

            LaunchedEffect(currentLocation) {
                // Only auto-center if there was NO saved camera view
                if (!hadSavedView && !didInitialFollow && currentLocation != null) {
                    val (lat, lon) = currentLocation!!
                    camera.animateTo(
                        camera.position.copy(
                            target = Position(lon, lat),
                            zoom = 13.0   // 👈 requested default zoom
                        )
                    )
                    didInitialFollow = true
                }
            }

            LaunchedEffect(currentLocation, geoLock.isActive()) {
                val loc = currentLocation ?: return@LaunchedEffect
                if (geoLock.isActive()) {
                    teleportCamera(camera, geoLock, lat = loc.first, lon = loc.second)
                }
            }


            val styleUri =
                if (isSystemInDarkTheme()) "https://maps.catenarymaps.org/dark-style.json"
                else "https://maps.catenarymaps.org/light-style.json"

            var searchQuery by remember { mutableStateOf("") }

            var isSearchFocused by remember { mutableStateOf(false) }

            var lastPosByLock by remember { mutableStateOf<CameraPosition?>(null) }

            val deactivateGeoLock: () -> Unit = { geoLock.deactivate() }

            CatenaryComposeTheme {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Remember the AppUpdateManager to pass to the snackbar action

                    CheckForAppUpdate(
                        onFlexibleUpdateDownloaded = {
                            // Show a snackbar when the update is ready
                            scope.launch {
                                val message = context.getString(R.string.update_downloaded)
                                val actionLabel = context.getString(R.string.restart)
                                val result = snackbars.showSnackbar(
                                    message = message,
                                    actionLabel = actionLabel,
                                    duration = SnackbarDuration.Indefinite
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    // User clicked "RESTART", complete the update
                                    appUpdateManager.completeUpdate()
                                }
                            }
                        }
                    )

                    val screenHeightPx = with(density) { maxHeight.toPx() }


                    val focusManager = LocalFocusManager.current

                    // ✅ Tablet/wide layout breakpoint
                    val isWideLayout = maxWidth >= 600.dp
                    val contentWidthFraction = if (isWideLayout) 0.5f else 1f
                    val searchAlignment =
                        if (isWideLayout) Alignment.TopStart else Alignment.TopCenter
                    val sheetAlignment =
                        if (isWideLayout) Alignment.BottomStart else Alignment.BottomCenter

                    val configuration = LocalConfiguration.current
                    val isLandscape =
                        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    val bottomInset = WindowInsets.safeDrawing.getBottom(density)
                    val collapsedHandleHeight = with(density) { 64.dp.toPx() }
                    val collapsedAnchor = screenHeightPx - collapsedHandleHeight - bottomInset

                    val anchors = DraggableAnchors<SheetSnapPoint> {
                        SheetSnapPoint.Collapsed at collapsedAnchor
                        SheetSnapPoint.PartiallyExpanded at screenHeightPx / 2f - bottomInset
                        SheetSnapPoint.Expanded at with(density) { 60.dp.toPx() }
                    }


                    val draggableState = remember {
                        AnchoredDraggableState(
                            initialValue = SheetSnapPoint.PartiallyExpanded,
                            anchors = anchors,
                            positionalThreshold = { with(density) { 128.dp.toPx() } },
                            velocityThreshold = { with(density) { 128.dp.toPx() } },
                            snapAnimationSpec = easeOutSpec,
                            decayAnimationSpec = splineBasedDecay(density),
                        )
                    }

                    // When screen size / insets change (rotation, etc.), refresh the anchors
                    // so the sheet snaps correctly with the new height.
                    LaunchedEffect(anchors) {
                        draggableState.updateAnchors(anchors)
                    }

                    val sheetIsExpanded by remember {
                        derivedStateOf { draggableState.currentValue == SheetSnapPoint.Expanded }
                    }

                    // State for layers panel visibility
                    var showLayersPanel by remember { mutableStateOf(false) }


                    // Track map size
                    var mapSize by remember { mutableStateOf(IntSize.Zero) }

                    // 1) Helper: are we half or more?
                    val sheetHalfOrMore by remember {
                        derivedStateOf {
                            draggableState.currentValue == SheetSnapPoint.PartiallyExpanded || draggableState.currentValue == SheetSnapPoint.Expanded
                        }
                    }

                    // 2) Compute desired PaddingValues in dp from the current map size
                    val desiredPadding: PaddingValues = run {
                        val density = LocalDensity.current
                        val halfHeightDp = with(density) { (mapSize.height / 2f).toDp() }
                        val halfWidthDp = with(density) { (mapSize.width / 2f).toDp() }

                        when {
                            // Rule A: full-width content + bottom sheet half or more
                            contentWidthFraction == 1f && sheetHalfOrMore -> PaddingValues(bottom = halfHeightDp)

                            // Rule B: 0.5 content width + (search focused OR bottom sheet half or more)
                            contentWidthFraction == 0.5f && (isSearchFocused || sheetHalfOrMore) -> PaddingValues(
                                start = halfWidthDp
                            )

                            else -> PaddingValues(0.dp)
                        }
                    }

                    // 3) Apply padding to the camera when it changes
                    LaunchedEffect(desiredPadding) {


                        geoLock.beginInternalMove()

                        try {
                            teleportCamera(
                                camera,
                                geoLock,
                                lat = camera.position.target.latitude,
                                lon = camera.position.target.longitude,
                                zoom = camera.position.zoom,
                                padding = desiredPadding
                            )
                        } finally {
                            geoLock.endInternalMove()
                        }


                    }

                    /** idle detection state for move/zoom end */
                    var lastCameraPos by remember { mutableStateOf<CameraPosition?>(null) }
                    var lastMoveAt by remember { mutableStateOf(0L) }
                    var lastQueriedPos by remember {
                        mutableStateOf<CameraPosition?>(null)
                    }
                    var lastQueriedPosOfRailChecker by remember {
                        mutableStateOf<CameraPosition?>(null)
                    }
                    val idleDebounceMs = 250L

                    var lastSavedPos by remember { mutableStateOf<CameraPosition?>(null) }
                    var lastSavedAt by remember { mutableStateOf(0L) }
                    val saveThrottleMs = 1500L
                    val prefsMemo = remember { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

                    var lastFetchedAt by remember { mutableStateOf(0L) }
                    val fetchDebounceMs = 500L // Don't fetch more than once per 500ms on move

                    val vehicleLayerIds = remember {
                        setOf(
                            LayersPerCategory.Bus.Livedots,
                            LayersPerCategory.Bus.Labeldots,
                            LayersPerCategory.Other.Livedots,
                            LayersPerCategory.Other.Labeldots,
                            LayersPerCategory.IntercityRail.Livedots,
                            LayersPerCategory.IntercityRail.Labeldots,
                            LayersPerCategory.Metro.Livedots,
                            LayersPerCategory.Metro.Labeldots,
                            LayersPerCategory.Tram.Livedots,
                            LayersPerCategory.Tram.Labeldots,
                        )
                    }
                    val routeLayerIds = remember {
                        setOf(
                            LayersPerCategory.Bus.Shapes,
                            LayersPerCategory.Bus.LabelShapes,
                            LayersPerCategory.Other.Shapes,
                            LayersPerCategory.Other.LabelShapes,
                            LayersPerCategory.Other.FerryShapes,
                            LayersPerCategory.IntercityRail.Shapes,
                            LayersPerCategory.IntercityRail.LabelShapes,
                            LayersPerCategory.Metro.Shapes,
                            LayersPerCategory.Metro.LabelShapes,
                            LayersPerCategory.Tram.Shapes,
                            LayersPerCategory.Tram.LabelShapes
                        )
                    }
                    val stopLayerIds = remember {
                        setOf(
                            LayersPerCategory.Bus.Stops,
                            LayersPerCategory.Bus.LabelStops,
                            LayersPerCategory.Other.Stops,
                            LayersPerCategory.Other.LabelStops,
                            LayersPerCategory.IntercityRail.Stops,
                            LayersPerCategory.IntercityRail.LabelStops,
                            LayersPerCategory.Metro.Stops,
                            LayersPerCategory.Metro.LabelStops,
                            LayersPerCategory.Tram.Stops,
                            LayersPerCategory.Tram.LabelStops
                        )
                    }

                    MaplibreMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { newSize -> mapSize = newSize },
                        baseStyle = BaseStyle.Uri(styleUri),
                        cameraState = camera,
                        onMapClick = { latlng, screenPos ->
                            println("map clicked")

                            val projection = camera.projection ?: run {
                                Log.w(TAG, "Map clicked, but projection is not ready.")
                                return@MaplibreMap ClickResult.Pass
                            }

                            geoLock.deactivate()

                            val clickPaddingDp = 5.dp
                            val clickRect = DpRect(
                                left = screenPos.x - clickPaddingDp,
                                top = screenPos.y - clickPaddingDp,
                                right = screenPos.x + clickPaddingDp,
                                bottom = screenPos.y + clickPaddingDp
                            )

                            // --- Query each layer group separately ---
                            // The returned 'Feature' is spatialk.geojson.Feature
                            val vehicleFeatures =
                                projection.queryRenderedFeatures(clickRect, vehicleLayerIds)
                            val routeFeatures =
                                projection.queryRenderedFeatures(clickRect, routeLayerIds)
                            val stopFeatures =
                                projection.queryRenderedFeatures(clickRect, stopLayerIds)

                            if (vehicleFeatures.isEmpty() && routeFeatures.isEmpty() && stopFeatures.isEmpty()) {
                                Log.d(TAG, "Map click detected, but no features found.")
                                return@MaplibreMap ClickResult.Pass
                            }

                            Log.d(
                                TAG,
                                "Found ${vehicleFeatures.size} vehicles, ${routeFeatures.size} routes, ${stopFeatures.size} stops."
                            )

                            // --- Process each feature list ---
                            val selectionOptions = mutableListOf<MapSelectionOption>()
                            selectionOptions.addAll(processVehicleClicks(vehicleFeatures))
                            selectionOptions.addAll(processRouteClicks(routeFeatures))
                            selectionOptions.addAll(processStopClicks(stopFeatures))

                            // If we found items, update the stack and open the sheet
                            if (selectionOptions.isNotEmpty()) {
                                val newStack = ArrayDeque(catenaryStack)
                                newStack.addLast(
                                    CatenaryStackEnum.MapSelectionScreen(
                                        selectionOptions
                                    )
                                )
                                catenaryStack = newStack // Update state

                                scope.launch {
                                    draggableState.animateTo(SheetSnapPoint.PartiallyExpanded)
                                }
                            }
                            ClickResult.Pass
                        },
                        options = MapOptions(
                            ornamentOptions = OrnamentOptions(
                                isLogoEnabled = false,
                                isAttributionEnabled = true,
                                isCompassEnabled = false,
                                isScaleBarEnabled = false,

                                ), renderOptions = RenderOptions(

                            )
                        ),
                        zoomRange = 2f..20f,
                        pitchRange = 0f..20f,

                        // 2) Map done loading
                        onMapLoadFinished = {
                            queryVisibleChateaus(scope, camera, mapSize)
                            val pos = camera.position

                            readDeeplink(
                                initial_uri,
                                catenaryStack,
                                camera,
                                reassigncatenarystack = { it ->
                                    catenaryStack = it
                                }
                            )

                            val now = SystemClock.uptimeMillis()

                            fetchRealtimeData(
                                scope = rtScope,
                                zoom = pos.zoom,
                                settings = layerSettings.value,
                                isFetchingRealtimeData = isFetchingRealtimeData,
                                visibleChateaus = visibleChateaus,
                                realtimeVehicleLocationsLastUpdated = realtimeVehicleLocationsLastUpdated,
                                ktorClient = ktorClient,
                                realtimeVehicleRouteCache = realtimeVehicleRouteCache,
                                camera = camera,
                                previousTileBoundariesStore = previousTileBoundariesStore,
                                realtimeVehicleLocationsStoreV2 = realtimeVehicleLocationsStoreV2,
                                routeCacheAgenciesKnown = routeCacheAgenciesKnown
                            )
                            queryVisibleChateaus(scope, camera, mapSize)
                            queryAreAnyRailFeaturesVisible(camera, mapSize, forceRun = true)
                            lastFetchedAt = now
                        },
                        // 3) Use onFrame to detect camera idle -> covers move end & zoom end

                        onFrame = {
                            val now = SystemClock.uptimeMillis()
                            val pos = camera.position

                            if (lastCameraPos == null) {

                            }

                            if (lastCameraPos == null || lastCameraPos != pos) {
                                lastCameraPos = pos
                                lastMoveAt = now


                                // Only drop the lock if this wasn't an internal move we initiated.
                                if (!geoLock.isInternalMove() && geoLock.isActive()) {
                                    //println("deactivate camera ${geoLock.isInternalMove()}")

                                    //println("current ${pos.target.latitude} ${pos.target.longitude} last set ${geoLock.getInternalPos()?.latitude} ${geoLock.getInternalPos()?.longitude}")

                                    // Compare floating point numbers with a small tolerance (epsilon)
                                    // to avoid deactivating the lock from tiny precision differences.
                                    val epsilon = 1e-6
                                    val internalPos = geoLock.getInternalPos()
                                    val currentLoc = currentLocation
                                    if (internalPos == null || kotlin.math.abs(pos.target.latitude - internalPos.latitude) > epsilon || kotlin.math.abs(
                                            pos.target.longitude - internalPos.longitude
                                        ) > epsilon
                                    ) {
                                        if (currentLoc == null || kotlin.math.abs(pos.target.latitude - currentLoc.first) > epsilon || kotlin.math.abs(
                                                pos.target.longitude - currentLoc.second
                                            ) > epsilon
                                        ) {
                                            geoLock.deactivate()
                                        }
                                    }
                                }

                                if (geoLock.isActive()) lastPosByLock = pos
                            }

                            if (now - lastMoveAt >= idleDebounceMs) {
                                if (lastQueriedPos != pos) {
                                    // Compute distance moved in metres

                                    //  val startTime = System.nanoTime()
                                    val distanceMovedOfRailChecker =
                                        lastQueriedPosOfRailChecker?.target?.let {
                                            haversineDistance(
                                                it.latitude,
                                                it.longitude,
                                                pos.target.latitude,
                                                pos.target.longitude
                                            )
                                        } ?: 0.0
                                    //val endTime = System.nanoTime()
                                    //val durationMs = (endTime - startTime) / 1_000_000.0
                                    // Log.d(TAG, "Haversine distance calculation took ${String.format("%.3f", durationMs)} ms")

                                    val zoomMovedOfRailChecker = kotlin.math.abs(
                                        pos.zoom - (lastQueriedPosOfRailChecker?.zoom ?: 0.0)
                                    )

                                    Log.d(
                                        TAG, "Map idle." +
                                                " Moved ${
                                                    String.format(
                                                        "%.2f",
                                                        distanceMovedOfRailChecker
                                                    )
                                                }m since last idle."
                                    )

                                    if (camera.projection != null && mapSize != IntSize.Zero) {
                                        queryVisibleChateaus(scope, camera, mapSize)

                                        if (railinframe) {
                                            if (distanceMovedOfRailChecker > 20000 || zoomMovedOfRailChecker > 3) {
                                                if (pos.zoom > 6) {
                                                    queryAreAnyRailFeaturesVisible(
                                                        camera, mapSize,
                                                        distance_m = distanceMovedOfRailChecker
                                                    )
                                                    lastQueriedPosOfRailChecker = pos
                                                }
                                            }
                                        } else {
                                            if (distanceMovedOfRailChecker > 500 || zoomMovedOfRailChecker > 1) {
                                                queryAreAnyRailFeaturesVisible(
                                                    camera, mapSize,
                                                    distance_m = distanceMovedOfRailChecker
                                                )
                                                lastQueriedPosOfRailChecker = pos
                                            }
                                        }


                                        lastQueriedPos = pos

                                        if (now - lastFetchedAt >= fetchDebounceMs) {
                                            fetchRealtimeData(
                                                scope = rtScope,
                                                zoom = pos.zoom,
                                                settings = layerSettings.value,
                                                isFetchingRealtimeData = isFetchingRealtimeData,
                                                visibleChateaus = visibleChateaus,
                                                realtimeVehicleLocationsLastUpdated = realtimeVehicleLocationsLastUpdated,
                                                ktorClient = ktorClient,
                                                realtimeVehicleRouteCache = realtimeVehicleRouteCache,
                                                camera = camera,
                                                previousTileBoundariesStore = previousTileBoundariesStore,
                                                realtimeVehicleLocationsStoreV2 = realtimeVehicleLocationsStoreV2,
                                                routeCacheAgenciesKnown = routeCacheAgenciesKnown
                                            )
                                            lastFetchedAt = now
                                        }
                                    }
                                }

                                // 👇 Persist the camera view while idle (throttled)
                                if ((now - lastSavedAt) >= saveThrottleMs &&
                                    (lastSavedPos == null || lastSavedPos != pos)
                                ) {
                                    prefsMemo.writeCamera(pos)
                                    lastSavedPos = pos
                                    lastSavedAt = now
                                    Log.d(
                                        TAG,
                                        "Saved camera: lat=${pos.target.latitude}, lon=${pos.target.longitude}, zoom=${pos.zoom}"
                                    )
                                }
                            }


                        })

                    {
                        val busDotsSrc: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    "bus_dots",
                                    GeoJsonData.Features(FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }

                        val metroDotsSrc: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    "metro_dots",
                                    GeoJsonData.Features(FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val railDotsSrc: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    "rail_dots",
                                    GeoJsonData.Features(FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val otherDotsSrc: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    "other_dots",
                                    GeoJsonData.Features(FeatureCollection(emptyList<Feature<Point, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }

                        val transitShapeSource: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "transit_shape_context",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList<Feature<LineString, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val transitShapeDetourSource: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "transit_shape_context_detour",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList<Feature<LineString, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val transitShapeForStopSource: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "transit_shape_context_for_stop",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList<Feature<LineString, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val stopsContextSource: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "stops_context",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList<Feature<LineString, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val majorDotsSource: MutableState<GeoJsonSource> = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "majordots_context",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList<Feature<LineString, Map<String, Any>>>())),
                                    GeoJsonOptions()
                                )
                            )
                        }

                        DisposableEffect(stopsContextSource) {
                            stopsContextSourceRef.value = stopsContextSource
                            onDispose {
                                if (stopsContextSourceRef.value === stopsContextSource) {
                                    stopsContextSourceRef.value = null
                                }
                            }
                        }

                        DisposableEffect(majorDotsSource) {
                            majorDotsSourceRef.value = majorDotsSource
                            onDispose {
                                if (majorDotsSourceRef.value === majorDotsSource) {
                                    majorDotsSourceRef.value = null
                                }
                            }
                        }

                        DisposableEffect(transitShapeSource) {
                            transitShapeSourceRef.value = transitShapeSource
                            onDispose {
                                if (transitShapeSourceRef.value === transitShapeSource) {
                                    transitShapeSourceRef.value = null
                                }
                            }
                        }

                        DisposableEffect(transitShapeDetourSource) {
                            transitShapeDetourSourceRef.value = transitShapeDetourSource
                            onDispose {
                                if (transitShapeDetourSourceRef.value === transitShapeDetourSource) {
                                    transitShapeDetourSourceRef.value = null
                                }
                            }
                        }

                        DisposableEffect(transitShapeForStopSource) {
                            transitShapeForStopSourceRef.value = transitShapeForStopSource
                            onDispose {
                                if (transitShapeForStopSourceRef.value === transitShapeForStopSource) {
                                    transitShapeForStopSourceRef.value = null
                                }
                            }
                        }


                        // Source + layers
                        val chateausSource = rememberGeoJsonSource(
                            data = GeoJsonData.Uri("https://birch.catenarymaps.org/getchateaus")
                        )

                        FillLayer(
                            id = "chateaus_calc", source = chateausSource, opacity = const(0.0f)
                        )

                        AddShapes(
                            layerSettings = layerSettings.value,
                            railInFrame = railinframe
                        )

                        AddStops(
                            layerSettings = layerSettings.value
                        )

                        // --- Detour Line ---
                        LineLayer(
                            id = "contextlinebackingdetour",
                            source = transitShapeDetourSource.value,
                            color = const(Color(0xFFFB9CAC)),
                            width = interpolate(
                                linear(),
                                zoom(),
                                7.0 to const(3.dp),
                                14.0 to const(6.dp)
                            ),
                            opacity = const(0.5f),
                            minZoom = 3f
                        )
                        LineLayer(
                            id = "contextlinedetour",
                            source = transitShapeDetourSource.value,
                            color = get("color").cast(),
                            width = interpolate(
                                linear(),
                                zoom(),
                                7.0 to const(3.2.dp),
                                14.0 to const(5.dp)
                            ),
                            dasharray = const(listOf(1f, 2f)),
                            opacity = const(0.9f),
                            minZoom = 3f
                        )

// --- Main Trip Shape Line ---
                        LineLayer(
                            id = "contextlinebacking",
                            source = transitShapeSource.value,
                            color = if (isDark) const(Color(0xFF111133)) else const(Color.White), // Themable
                            width = interpolate(
                                linear(),
                                zoom(),
                                7.0 to const(4.dp),
                                14.0 to const(8.dp)
                            ),
                            opacity = const(0.9f),
                            minZoom = 3f
                        )
                        LineLayer(
                            id = "contextline",
                            source = transitShapeSource.value,
                            color = get("color").cast(),
                            width = interpolate(
                                linear(),
                                zoom(),
                                7.0 to const(3.5.dp),
                                14.0 to const(6.dp)
                            ),
                            minZoom = 3f
                        )

// --- Shape for Stop (if needed) ---
                        LineLayer(
                            id = "contextlinebackingforstop",
                            source = transitShapeForStopSource.value,
                            color = if (isDark) const(Color(0xFF111133)) else const(Color.White),
                            width = interpolate(
                                linear(),
                                zoom(),
                                7.0 to const(4.dp),
                                11.0 to const(5.dp),
                                14.0 to const(7.dp)
                            ),
                            opacity = const(0.8f),
                            minZoom = 3f
                        )
                        LineLayer(
                            id = "contextlineforstop",
                            source = transitShapeForStopSource.value,
                            color = get("color").cast(),
                            width = interpolate(
                                linear(),
                                zoom(),
                                7.0 to const(2.8.dp),
                                11.0 to const(4.dp),
                                14.0 to const(5.dp)
                            ),
                            minZoom = 3f
                        )

                        // --- Context Stops (from stops_context source) ---

                        // TODO: load the 'cancelledstops' image into the map style
                        // Bus Stops
                        CircleLayer(
                            id = "contextbusstops",
                            source = stopsContextSource.value,
                            color = const(Color.White),
                            radius = interpolate(
                                linear(),
                                zoom(),
                                8.0 to const(1.dp),
                                10.0 to const(2.dp),
                                13.0 to const(4.dp)
                            ),
                            strokeColor = const(Color(0xFF1A1A1A)),
                            strokeWidth = step(zoom(), const(1.2.dp), 13.2 to const(1.5.dp)),
                            strokeOpacity = const(0.9f),
                            opacity = interpolate(
                                linear(),
                                zoom(),
                                11.0 to const(0.7f),
                                12.0 to const(1.0f)
                            ),
                            filter = all(
                                get("stop_route_type").cast<NumberValue<EquatableValue>>()
                                    .eq(const(3)),
                                (get("cancelled").cast<BooleanValue>().neq(const(true))
                                        )
                            ),
                            minZoom = 9.5f
                        )
                        SymbolLayer(
                            id = "contextbusstops_label",
                            source = stopsContextSource.value,
                            textField = get("label").cast(),
                            textSize = interpolate(
                                linear(),
                                zoom(),
                                11.0 to const(0.625f.em),
                                14.0 to const(0.8125f.em)
                            ), // 10px -> 13px
                            textFont = step(
                                zoom(),
                                const(listOf("Barlow-Regular")),
                                13.0 to const(listOf("Barlow-Medium"))
                            ),
                            textColor = if (isDark) const(Color.White) else const(Color(0xFF1A1A1A)),
                            textHaloColor = if (isDark) const(Color(0xFF1A1A1A)) else const(
                                Color(
                                    0xFFDADADA
                                )
                            ),
                            textHaloWidth = const(1.dp),
                            textRadialOffset = const(0.3f.em),
                            filter = get("stop_route_type").cast<NumberValue<EquatableValue>>()
                                .eq(const(3)),
                            minZoom = 12.5f
                        )

// Metro Stops
                        CircleLayer(
                            id = "contextmetrostops",
                            source = stopsContextSource.value,
                            color = const(Color.White),
                            radius = interpolate(
                                linear(),
                                zoom(),
                                8.0 to const(1.3.dp),
                                10.0 to const(3.dp),
                                13.0 to const(5.dp)
                            ),
                            strokeColor = const(Color(0xFF1A1A1A)),
                            strokeWidth = step(zoom(), const(1.2.dp), 13.2 to const(1.5.dp)),
                            filter = all(
                                get("stop_route_type").cast<NumberValue<EquatableValue>>()
                                    .neq(const(3)),
                                get("stop_route_type").cast<NumberValue<EquatableValue>>()
                                    .neq(const(2))
                            ),
                            minZoom = 6f
                        )
                        SymbolLayer(
                            id = "contextmetrostops_label",
                            source = stopsContextSource.value,
                            textField = get("label").cast(),
                            textSize = interpolate(
                                linear(),
                                zoom(),
                                6.0 to const(0.28125f.em),
                                8.0 to const(0.5625f.em),
                                9.0 to const(0.75f.em)
                            ), // 4.5px -> 9px -> 12px
                            textFont = const(listOf("Barlow-Medium")),
                            textColor = if (isDark) const(Color.White) else const(Color(0xFF1A1A1A)),
                            textHaloColor = if (isDark) const(Color(0xFF1A1A1A)) else const(
                                Color(
                                    0xFFDADADA
                                )
                            ),
                            textHaloWidth = const(1.dp),
                            textRadialOffset = const(0.2f.em),
                            filter = all(
                                get("stop_route_type").cast<NumberValue<EquatableValue>>()
                                    .neq(const(3)),
                                get("stop_route_type").cast<NumberValue<EquatableValue>>()
                                    .neq(const(2))
                            ),
                            minZoom = 9f
                        )

// Rail Stops
                        CircleLayer(
                            id = "contextrailstops",
                            source = stopsContextSource.value,
                            color = const(Color.White),
                            radius = interpolate(
                                linear(),
                                zoom(),
                                8.0 to const(3.dp),
                                10.0 to const(4.dp),
                                13.0 to const(5.dp)
                            ),
                            strokeColor = const(Color(0xFF1A1A1A)),
                            strokeWidth = step(zoom(), const(1.2.dp), 13.2 to const(1.5.dp)),
                            filter = get("stop_route_type").cast<NumberValue<EquatableValue>>()
                                .eq(const(2)),
                            minZoom = 4f
                        )
                        SymbolLayer(
                            id = "contextrailstops_label",
                            source = stopsContextSource.value,
                            textField = get("label").cast(),
                            textSize = interpolate(
                                linear(),
                                zoom(),
                                4.0 to const(0.5625f.em),
                                6.0 to const(0.625f.em),
                                10.0 to const(0.875f.em)
                            ), // 9px -> 10px -> 14px
                            textFont = step(
                                zoom(),
                                const(listOf("Barlow-Regular")),
                                6.0 to const(listOf("Barlow-Medium"))
                            ),
                            textColor = if (isDark) const(Color.White) else const(Color(0xFF1A1A1A)),
                            textHaloColor = if (isDark) const(Color(0xFF1A1A1A)) else const(
                                Color(
                                    0xFFDADADA
                                )
                            ),
                            textHaloWidth = const(1.dp),
                            textRadialOffset = const(0.2f.em),
                            filter = get("stop_route_type").cast<NumberValue<EquatableValue>>()
                                .eq(const(2)),
                            minZoom = 3f
                        )

                        val locationsV2 = realtimeVehicleLocationsStoreV2.value
                        val cache = realtimeVehicleRouteCache.value

                        AddLiveDots(
                            isDark = isDark,
                            usUnits = usUnits,
                            showZombieBuses = showZombieBuses,
                            layerSettings = layerSettings.value,
                            vehicleLocationsV2 = locationsV2,
                            routeCache = cache,
                            busDotsSrc = busDotsSrc,
                            metroDotsSrc = metroDotsSrc,
                            railDotsSrc = railDotsSrc,
                            otherDotsSrc = otherDotsSrc,
                            railInFrame = railinframe
                        )

                        // Layers for BUS
                        LiveDotLayers(
                            category = "bus",
                            source = busDotsSrc.value,                // <- persistent source
                            settings = layerSettings.value.bus.labelrealtimedots,
                            isVisible = layerSettings.value.bus.visiblerealtimedots,
                            baseFilter = if (showZombieBuses) all(
                                applyFilterToLiveDots.value
                            ) else all(
                                feature.has("trip_id"),
                                get("trip_id").cast<StringValue>().neq(const("")),
                                applyFilterToLiveDots.value
                            ),
                            bearingFilter = all(
                                applyFilterToLiveDots.value,
                                get("has_bearing").cast<BooleanValue>().eq(const(true)),
                                if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.Bus,
                            railInFrame = railinframe
                        )

// Layers for METRO/TRAM share the metro source but are filtered
                        LiveDotLayers(
                            category = "metro",
                            source = metroDotsSrc.value,
                            settings = layerSettings.value.localrail.labelrealtimedots,
                            isVisible = layerSettings.value.localrail.visiblerealtimedots,
                            baseFilter = all(
                                applyFilterToLiveDots.value,
                                any(rtEq(1), rtEq(12)), if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const("")),

                                    )
                            ),
                            bearingFilter = all(
                                applyFilterToLiveDots.value,
                                any(rtEq(1), rtEq(12)),
                                get("has_bearing").cast<BooleanValue>().eq(const(true))
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.Metro,
                            railInFrame = railinframe
                        )

                        LiveDotLayers(
                            category = "tram",
                            source = metroDotsSrc.value,  // re-uses metro source, different filter via layerIdPrefix branch
                            settings = layerSettings.value.localrail.labelrealtimedots,
                            isVisible = layerSettings.value.localrail.visiblerealtimedots,
                            baseFilter = all(
                                applyFilterToLiveDots.value,
                                any(rtEq(0), rtEq(5)), if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            bearingFilter = all(
                                applyFilterToLiveDots.value,
                                any(rtEq(0), rtEq(5)),
                                get("has_bearing").cast<BooleanValue>().eq(const(true))
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.Tram,
                            railInFrame = railinframe
                        )

// Layers for INTERCITY
                        LiveDotLayers(
                            category = "intercityrail",
                            source = railDotsSrc.value,
                            settings = (layerSettings.value["intercityrail"] as LayerCategorySettings).labelrealtimedots,
                            isVisible = (layerSettings.value["intercityrail"] as LayerCategorySettings).visiblerealtimedots,
                            baseFilter = all(
                                applyFilterToLiveDots.value,
                                isIntercity(), if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            bearingFilter = all(
                                applyFilterToLiveDots.value,
                                isIntercity(),
                                get("has_bearing").cast<BooleanValue>().eq(const(true))
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.IntercityRail,
                            railInFrame = railinframe
                        )

// Layers for OTHER
                        LiveDotLayers(
                            category = "other",
                            source = otherDotsSrc.value,
                            settings = (layerSettings.value["other"] as LayerCategorySettings).labelrealtimedots,
                            isVisible = (layerSettings.value["other"] as LayerCategorySettings).visiblerealtimedots,
                            baseFilter = if (showZombieBuses) all(applyFilterToLiveDots.value) else all(
                                applyFilterToLiveDots.value,
                                feature.has("trip_id"),
                                get("trip_id").cast<StringValue>().neq(const(""))
                            ),
                            bearingFilter = all(
                                applyFilterToLiveDots.value,
                                get("has_bearing").cast<BooleanValue>().eq(const(true)),
                                if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.Other,
                            railInFrame = railinframe
                        )


                        // Show a dot for the user's current location
                        if (currentLocation != null) {
                            val (lat, lon) = currentLocation!!

                            val userLocationSource = rememberGeoJsonSource(
                                data = GeoJsonData.Features(
                                    Point(Position(lon, lat))
                                )
                            )

                            CircleLayer(
                                id = "user-location-dot",
                                source = userLocationSource,
                                radius = interpolate(
                                    type = linear(),
                                    input = zoom(),
                                    0 to const(3.dp),
                                    12 to const(6.dp),
                                    15 to const(8.dp)
                                ),
                                color = const(Color(0xFF1D4ED8)),
                                strokeColor = const(Color.White),
                                strokeWidth = const(2.dp),
                                minZoom = 0f,
                                visible = true
                            )
                        }

                        // draggable pin section


                        DraggablePinLayers(pin = pin)


                    }

                    DraggablePinOverlay(
                        camera = camera,
                        mapSize = mapSize,
                        pin = pin,
                        onActivatePin = { pin = pin.copy(active = true) },

                        onDragEndCommit = { newPos ->
                            // Update Compose state
                            pin = pin.copy(position = newPos, active = true)
                        })


                    // Main Draggable Bottom Sheet
                    val sheetModifier =
                        Modifier
                            .fillMaxWidth(contentWidthFraction)
                            .align(Alignment.BottomStart)

                    Surface(
                        modifier = sheetModifier
                            .offset {
                                IntOffset(
                                    x = 0, y = draggableState.requireOffset().roundToInt()
                                )
                            }
                            .anchoredDraggable(
                                state = draggableState, orientation = Orientation.Vertical
                            ),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        shadowElevation = 8.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding( // This padding ensures content doesn't get clipped at the bottom
                                    bottom = with(LocalDensity.current) {


                                        ((draggableState.requireOffset())).toDp()
                                            .coerceAtLeast(0.dp).coerceAtMost(
                                                with(LocalDensity.current) {
                                                    maxHeight.times(0.9f)
                                                })
                                    }),

                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.4f
                                        )
                                    )
                            )


                            if (catenaryStack.isEmpty()) {

                                NearbyDepartures(
                                    userLocation = currentLocation,
                                    pickedLocation = pickedPair,
                                    usePickedLocation = usePickedLocation,
                                    pin = pin,
                                    //usePickedLocation = false,
                                    darkMode = isSystemInDarkTheme(),
                                    onMyLocation = {
                                        // Mimic JS “my_location_press()”: exit pin mode
                                        pin = pin.copy(active = false)
                                    },
                                    onPinDrop = {
                                        onPinDrop()
                                    },
                                    onCenterPin = {
                                        onCenterPin()
                                    },
                                    onTripClick = { r ->
                                        val newStack = ArrayDeque(catenaryStack)
                                        newStack.addLast(
                                            CatenaryStackEnum.SingleTrip(
                                                chateau_id = r.chateauId!!,
                                                trip_id = r.tripId,
                                                route_id = r.routeId,
                                                start_time = null,
                                                start_date = r.startDay,
                                                vehicle_id = null,
                                                route_type = r.routeType
                                            )
                                        )
                                        catenaryStack = newStack
                                    },
                                    onRouteClick = { chateauId, routeId ->
                                        val newStack = ArrayDeque(catenaryStack)
                                        newStack.addLast(
                                            CatenaryStackEnum.RouteStack(
                                                chateau_id = chateauId,
                                                route_id = routeId
                                            )
                                        )
                                        catenaryStack = newStack
                                    }
                                )
                            } else {
                                // Handle other stack states
                                val currentScreen = catenaryStack.last()
                                val onBack: () -> Unit = {
                                    if (catenaryStack.isNotEmpty()) {
                                        val newStack = ArrayDeque(catenaryStack)
                                        newStack.removeLast()
                                        catenaryStack = newStack
                                    }
                                }

                                val onHome: () -> Unit = {
                                    catenaryStack = ArrayDeque()
                                }
                                Column(modifier = Modifier.padding(horizontal = 8.dp)) {


                                    // Render the current stack screen
                                    when (currentScreen) {
                                        is CatenaryStackEnum.SingleTrip -> {
                                            if (transitShapeSourceRef.value != null
                                                && transitShapeDetourSourceRef.value != null &&
                                                stopsContextSourceRef.value != null
                                            ) {

                                                SingleTripInfoScreen(
                                                    tripSelected = currentScreen,
                                                    onStopClick = { stopStack ->
                                                        val newStack = ArrayDeque(catenaryStack)
                                                        newStack.addLast(stopStack)
                                                        catenaryStack = newStack
                                                    },
                                                    onBlockClick = { blockStack ->
                                                        val newStack = ArrayDeque(catenaryStack)
                                                        newStack.addLast(blockStack)
                                                        catenaryStack = newStack
                                                    },
                                                    onRouteClick = { routeStack ->
                                                        val newStack = ArrayDeque(catenaryStack)
                                                        newStack.addLast(routeStack)
                                                        catenaryStack = newStack
                                                    },
                                                    usUnits = usUnits,
                                                    // --- Pass the .value of the sources ---
                                                    transitShapeSource = transitShapeSourceRef.value!!,
                                                    transitShapeDetourSource = transitShapeDetourSourceRef.value!!,
                                                    stopsContextSource = stopsContextSourceRef.value!!,
                                                    majorDotsSource = majorDotsSourceRef.value!!,
                                                    // Pass the state setter
                                                    onSetStopsToHide = { newSet ->
                                                        stopsToHide = newSet
                                                    },
                                                    applyFilterToLiveDots = applyFilterToLiveDots,
                                                    onBack = onBack,
                                                    onHome = onHome
                                                )
                                            }

                                        }

                                        is CatenaryStackEnum.MapSelectionScreen -> {
                                            MapSelectionScreen(
                                                screenData = currentScreen,
                                                onStackPush = { newScreenData ->
                                                    val newStack = ArrayDeque(catenaryStack)
                                                    newStack.addLast(newScreenData)
                                                    catenaryStack = newStack
                                                },
                                                onBack = onBack,
                                                onHome = onHome
                                            )
                                        }

                                        is CatenaryStackEnum.SettingsStack -> {
                                            SettingsScreen(
                                                datadogConsent = datadogConsent,
                                                onDatadogConsentChanged = onDatadogConsentChanged,
                                                gaConsent = gaConsent,
                                                onGaConsentChanged = onGaConsentChanged,
                                                onBack = onBack,
                                                onHome = onHome
                                            )
                                        }

                                        is CatenaryStackEnum.RouteStack -> {
                                            if (transitShapeSourceRef.value != null && stopsContextSourceRef.value != null) {
                                                RouteScreen(
                                                    screenData = currentScreen,
                                                    transitShapeSource = transitShapeSourceRef.value!!,
                                                    stopsContextSource = stopsContextSourceRef.value!!,
                                                    onStopClick = { stopStack ->
                                                        val newStack = ArrayDeque(catenaryStack)
                                                        newStack.addLast(stopStack)
                                                        catenaryStack = newStack
                                                    },
                                                    onTripClick = { tripStack ->
                                                        val newStack = ArrayDeque(catenaryStack)
                                                        newStack.addLast(tripStack)
                                                        catenaryStack = newStack
                                                    },
                                                    onSetStopsToHide = { newSet ->
                                                        stopsToHide = newSet
                                                    },
                                                    camera = camera,
                                                    desiredPadding = desiredPadding,
                                                    onBack = onBack,
                                                    onHome = onHome
                                                )

                                            }
                                        }

                                        is CatenaryStackEnum.StopStack -> {
                                            // Check all 4 refs now
                                            if (transitShapeSourceRef.value != null &&
                                                transitShapeDetourSourceRef.value != null &&
                                                stopsContextSourceRef.value != null &&
                                                transitShapeForStopSourceRef.value != null // <-- Added this check
                                            ) {
                                                StopScreen(
                                                    screenData = currentScreen,
                                                    onTripClick = { tripStack ->
                                                        val newStack = ArrayDeque(catenaryStack)
                                                        newStack.addLast(tripStack)
                                                        catenaryStack = newStack
                                                    },
                                                    // Pass the .value of the sources
                                                    transitShapeForStopSource = transitShapeForStopSourceRef.value!!,
                                                    stopsContextSource = stopsContextSourceRef.value!!,
                                                    transitShapeSource = transitShapeSourceRef.value!!,
                                                    camera = camera,
                                                    onSetStopsToHide = { newSet ->
                                                        stopsToHide = newSet
                                                    },
                                                    geoLock = geoLock,
                                                    onBack = onBack,
                                                    onHome = onHome
                                                )
                                            }
                                        }

                                        is CatenaryStackEnum.BlockStack -> {
                                            BlockScreen(
                                                chateau = currentScreen.chateau_id,
                                                blockId = currentScreen.block_id,
                                                serviceDate = currentScreen.service_date,
                                                catenaryStack = catenaryStack,
                                                onStackChange = { catenaryStack = it },
                                                onBack = onBack,
                                                onHome = onHome
                                            )
                                        }
                                        // TODO: Add 'when' branches for other stack types
                                        // (SingleTrip, RouteStack, etc.)
                                        else -> {
                                            /*
                                            Text(
                                                text = "Clicked on something!",
                                                modifier = Modifier.padding(16.dp)
                                            )
                                             */
                                        }
                                    }
                                }
                            }
                        }
                    }


                    val layerButtonColor =
                        if (isSystemInDarkTheme()) Color.DarkGray else Color.White
                    val layerButtonContentColor =
                        if (isSystemInDarkTheme()) Color.White else Color.Black


                    var searchBarBottomPx by remember { mutableStateOf(0) }

                    AnimatedVisibility(
                        visible = !sheetIsExpanded, // hide when sheet is fully expanded
                        modifier = Modifier
                            .align(searchAlignment)
                            .fillMaxWidth(contentWidthFraction)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                            .zIndex(3f),
                        enter = slideInVertically(initialOffsetY = { -it / 2 }), // subtle drop-in
                        exit = slideOutVertically(targetOffsetY = { -it })      // swipe up & out
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Box(
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    val bottom = coords.positionInRoot().y + coords.size.height
                                    // searchBarBottomPx = bottom.toInt() // (keep if needed)
                                }) {

                                // === UPDATE SearchBarCatenary call ===
                                SearchBarCatenary(
                                    searchQuery = searchQuery,
                                    onValueChange = { newQuery ->
                                        searchQuery = newQuery

                                        // This line should now resolve (Error 1)
                                        searchViewModel.onSearchQueryChanged(
                                            query = newQuery,
                                            userLocation = currentLocation,
                                            mapCenter = camera.position.target,
                                            context = context
                                        )
                                    },
                                    onFocusChange = { isFocused -> isSearchFocused = isFocused },
                                    onSettingsClick = {
                                        val newStack = ArrayDeque(catenaryStack)
                                        newStack.addLast(CatenaryStackEnum.SettingsStack())
                                        catenaryStack = newStack

                                        // Open the sheet to see the settings
                                        scope.launch {
                                            draggableState.animateTo(SheetSnapPoint.PartiallyExpanded)
                                        }
                                        focusManager.clearFocus()
                                    })
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !(sheetIsExpanded && contentWidthFraction == 1f) && !(isSearchFocused && contentWidthFraction == 1f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(
                                top = if (contentWidthFraction == 1.0f) 64.dp else 16.dp,
                                end = 16.dp
                            )
                            .zIndex(3f) // keep above map
                    ) {
                        FloatingActionButton(
                            onClick = { showLayersPanel = !showLayersPanel },
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Icon(
                                Icons.Filled.Layers,
                                contentDescription = "Toggle Layers",
                                Modifier.size(24.dp)
                            )
                        }
                    }

                    //Compass Screen
                    AnimatedVisibility(
                        visible = !(sheetIsExpanded && contentWidthFraction == 1f) && !(isSearchFocused && contentWidthFraction == 1f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(
                                top = if (contentWidthFraction == 1.0f) 110.dp else 64.dp,
                                end = 16.dp
                            )
                            .zIndex(3f) // keep above map
                    ) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    camera.animateTo(
                                        camera.position.copy(
                                            bearing = 0.0
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Image(
                                painter = painterResource(id = if (isDark) R.drawable.compass_dark else R.drawable.compass_light),
                                contentDescription = "Set to North",
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(-camera.position.bearing.toFloat())
                            )
                        }
                    }

                    //Floating geolocation button

                    // Sizing we’ll use
                    val fabSize = 56.dp
                    val fabMargin = 16.dp

// Drawer geometry
                    val sheetOffsetPx =
                        draggableState.requireOffset()            // top Y of the bottom sheet
                    val fabYAboveSheet =
                        with(density) { (sheetOffsetPx - fabSize.toPx() - fabMargin.toPx()).roundToInt() }
                    val fabBottomMarginWhenFull =
                        with(density) { (maxHeight.toPx() - sheetOffsetPx + fabMargin.toPx()).roundToInt() }

                    val fabModifier =
                        if (contentWidthFraction == 1f) {
                            // Full-width sheet -> float *above* the drawer and move with it
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset {
                                    IntOffset(x = -with(density) {
                                        fabMargin.toPx().roundToInt()
                                    }, y = fabYAboveSheet.coerceAtLeast(0))
                                }
                                .zIndex(4f)
                        } else {
                            // Half-width sheet -> keep it in the bottom-right corner (not over the drawer)
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                                .zIndex(4f)
                        }

                    AnimatedVisibility(
                        visible = isWideLayout || !(isSearchFocused || sheetIsExpanded),
                        modifier = fabModifier,
                        enter = slideInVertically(initialOffsetY = { it / 2 }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        FloatingActionButton(
                            onClick = {
                                val loc = currentLocation
                                if (loc == null) {
                                    scope.launch { snackbars.showSnackbar("Location unavailable") }
                                    return@FloatingActionButton
                                }
                                // Activate lock and teleport *now*
                                geoLock.activate()
                                scope.launch {
                                    teleportCamera(
                                        camera = camera,
                                        controller = geoLock,
                                        lat = loc.first,
                                        lon = loc.second,
                                        zoom = 16.0 // pick your desired snap zoom
                                    )
                                }
                                println("Geolock state ${geoLock.isActive()}")
                                // scope.launch { snackbars.showSnackbar("Following your location") }
                            },
                            shape = CircleShape,
                            containerColor = if (geoLock.isActive()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (geoLock.isActive()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = "My Location")
                        }
                    }

                    // The results overlay that shows when the searchbar is focused.
// On tablets (contentWidthFraction < 1f) it covers the left pane,
// on phones it covers the full window.
                    AnimatedVisibility(
                        visible = isSearchFocused,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(2f), // below the bar (z=3), above the map/sheet
                        enter = slideInVertically(initialOffsetY = { -it }),
                        exit = slideOutVertically(targetOffsetY = { -it }),
                    ) {
                        val overlayBase = if (contentWidthFraction < 1f) {
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(contentWidthFraction)
                        } else {
                            Modifier.fillMaxSize()
                        }

                        SearchResultsOverlay(
                            modifier = overlayBase,
                            viewModel = searchViewModel,
                            currentLocation = currentLocation,
                            onNominatimClick = { result ->
                                geoLock.deactivate()

                                val pos = Position(result.lon.toDouble(), result.lat.toDouble())

                                // === FIX for Error 3 ===
                                scope.launch {

                                    geoLock.deactivate()

                                    camera.animateTo(
                                        camera.position.copy(
                                            target = pos,
                                            zoom = 14.0
                                        )
                                    )
                                }
                                // === FIX for Error 4 ===
                                focusManager.clearFocus() // This should now resolve
                            },
                            onRouteClick = { ranking, routeInfo, agency ->
                                geoLock.deactivate()

                                val newStack = ArrayDeque(catenaryStack)
                                newStack.addLast(
                                    CatenaryStackEnum.RouteStack(
                                        chateau_id = routeInfo.chateau,
                                        route_id = routeInfo.routeId
                                    )
                                )
                                catenaryStack = newStack

                                scope.launch {
                                    draggableState.animateTo(SheetSnapPoint.PartiallyExpanded)
                                }
                                focusManager.clearFocus()
                            },
                            onStopClick = { chateau, gtfsId, ranking, stopInfo ->
                                geoLock.deactivate()

                                val pos = Position(stopInfo.point.x, stopInfo.point.y)

                                val newStack = ArrayDeque(catenaryStack)
                                newStack.addLast(
                                    CatenaryStackEnum.StopStack(
                                        chateau_id = chateau,
                                        stop_id = gtfsId
                                    )
                                )
                                catenaryStack = newStack

                                scope.launch {
                                    draggableState.animateTo(SheetSnapPoint.PartiallyExpanded)
                                }
                                focusManager.clearFocus()
                            }
                        )
                    }

                    // Layers Panel
                    AnimatedVisibility(
                        visible = showLayersPanel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(5f), // Ensure it's on top
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {

                        // UPDATED Layers Panel with "More" tab
                        var selectedTab by remember { mutableStateOf("intercityrail") }
                        val tabs =
                            listOf("intercityrail", "localrail", "bus", "other", "more")

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 700.dp),
                            shadowElevation = 8.dp,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .windowInsetsPadding(
                                        WindowInsets(
                                            bottom = WindowInsets.safeContent.getBottom(
                                                density
                                            )
                                        )
                                    )
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringResource(id = R.string.layers),
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    IconButton(onClick = { showLayersPanel = false }) {
                                        Icon(
                                            Icons.Filled.Close, contentDescription = "Close Layers"
                                        )
                                    }
                                }

                                // Tab Row
                                TabRow(selectedTabIndex = tabs.indexOf(selectedTab)) {
                                    tabs.forEachIndexed { index, title ->
                                        val textResId = when (title) {
                                            "intercityrail" -> R.string.heading_intercity_rail
                                            "localrail" -> R.string.heading_local_rail
                                            "bus" -> R.string.heading_bus
                                            "other" -> R.string.heading_other
                                            "more" -> R.string.heading_more
                                            else -> R.string.app_name // Fallback, though should not happen
                                        }
                                        Tab(
                                            selected = selectedTab == title,
                                            onClick = { selectedTab = title },
                                            text = {
                                                Text(
                                                    text = stringResource(textResId),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Content for Vehicle Tabs
                                if (selectedTab in listOf(
                                        "intercityrail", "localrail", "bus", "other"
                                    )
                                ) {
                                    val currentSettings = layerSettings.value.category(selectedTab)
                                    currentSettings?.let { settings ->

                                        // Row 1: Shapes, Labels, Stops, Stop Labels
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            LayerToggleButton(
                                                name = "Shapes",
                                                padding = 0.dp,
                                                icon = {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.routesicon),
                                                        contentDescription = "Shape",
                                                        modifier = Modifier
                                                            .height(48.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                },
                                                isActive = settings.shapes,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(shapes = !cat.shapes)
                                                        }


                                                    queryAreAnyRailFeaturesVisible(
                                                        camera, mapSize,
                                                        forceRun = true
                                                    )
                                                }
                                            )
                                            LayerToggleButton(
                                                name = "Shape Labels",
                                                padding = 0.dp,
                                                icon = {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.labelsicon),
                                                        contentDescription = "Shape Label",
                                                        modifier = Modifier
                                                            .height(48.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                }, // Placeholder
                                                isActive = settings.labelshapes,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(labelshapes = !cat.labelshapes)
                                                        }

                                                    queryAreAnyRailFeaturesVisible(
                                                        camera, mapSize,
                                                        forceRun = true
                                                    )
                                                }
                                            )
                                            LayerToggleButton(
                                                name = "Stops",
                                                padding = 0.dp,
                                                icon = {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.stopsicon),
                                                        contentDescription = "Stops",
                                                        modifier = Modifier
                                                            .height(48.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                },
                                                isActive = settings.stops,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(stops = !cat.stops)
                                                        }

                                                    queryAreAnyRailFeaturesVisible(
                                                        camera, mapSize,
                                                    )
                                                }

                                            )
                                            LayerToggleButton(
                                                name = "Stop Labels",
                                                padding = 0.dp,
                                                icon = {
                                                    Image(
                                                        painter = painterResource(id = if (isDark) R.drawable.dark_stop_name else R.drawable.light_stop_name),
                                                        contentDescription = "Stops Label",
                                                        modifier = Modifier
                                                            .height(48.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                },
                                                isActive = settings.labelstops,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(labelstops = !cat.labelstops)
                                                        }

                                                    queryAreAnyRailFeaturesVisible(
                                                        camera, mapSize,
                                                    )
                                                }
                                            )

                                            LayerToggleButton(
                                                name = "Vehicles",
                                                padding = 0.dp,
                                                icon = {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.vehiclesicon),
                                                        contentDescription = "Stops Label",
                                                        modifier = Modifier
                                                            .height(48.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                },
                                                isActive = settings.visiblerealtimedots,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(visiblerealtimedots = !cat.visiblerealtimedots)
                                                        }

                                                    queryAreAnyRailFeaturesVisible(
                                                        camera, mapSize,
                                                    )
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Vehicle Labels",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Row 3: Label Toggles
                                        val labelSettings = settings.labelrealtimedots

                                        // Row 3: Label Toggles (First row from JS)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.route), // Corresponds to $_('showroute')
                                                icon = Icons.Filled.Route, // Corresponds to symbol="route"
                                                isActive = labelSettings.route,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(
                                                                labelrealtimedots = cat.labelrealtimedots.copy(
                                                                    route = !cat.labelrealtimedots.route
                                                                )
                                                            )
                                                        }
                                                }
                                            )
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.trip), // Corresponds to $_('showtrip')
                                                icon = Icons.Filled.AltRoute, // Corresponds to symbol="mode_of_travel"
                                                isActive = labelSettings.trip,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(
                                                                labelrealtimedots = cat.labelrealtimedots.copy(
                                                                    trip = !cat.labelrealtimedots.trip
                                                                )
                                                            )
                                                        }
                                                }
                                            )
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.vehicle), // Corresponds to $_('showvehicle')
                                                icon = Icons.Filled.Train, // Corresponds to symbol="train"
                                                isActive = labelSettings.vehicle,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(
                                                                labelrealtimedots = cat.labelrealtimedots.copy(
                                                                    vehicle = !cat.labelrealtimedots.vehicle
                                                                )
                                                            )
                                                        }
                                                }

                                            )

                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.headsign), // Corresponds to $_('headsign')
                                                icon = Icons.Filled.SportsScore, // Corresponds to symbol="sports_score"
                                                isActive = labelSettings.headsign,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(
                                                                labelrealtimedots = cat.labelrealtimedots.copy(
                                                                    headsign = !cat.labelrealtimedots.headsign
                                                                )
                                                            )
                                                        }
                                                }
                                            )
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.speed),
                                                icon = Icons.Filled.Speed, // Corresponds to symbol="speed"
                                                isActive = labelSettings.speed,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(
                                                                labelrealtimedots = cat.labelrealtimedots.copy(
                                                                    speed = !cat.labelrealtimedots.speed
                                                                )
                                                            )
                                                        }
                                                }
                                            )
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.occupancy),
                                                icon = Icons.Filled.Group, // Corresponds to symbol="group"
                                                isActive = labelSettings.occupancy,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(
                                                                labelrealtimedots = cat.labelrealtimedots.copy(
                                                                    occupancy = !cat.labelrealtimedots.occupancy
                                                                )
                                                            )
                                                        }
                                                }
                                            )
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.delay), // Corresponds to $_('delay')
                                                icon = Icons.Filled.Timer, // Corresponds to symbol="timer"
                                                isActive = labelSettings.delay,
                                                onToggle = {
                                                    layerSettings.value =
                                                        layerSettings.value.updateCategory(
                                                            selectedTab
                                                        ) { cat ->
                                                            cat.copy(
                                                                labelrealtimedots = cat.labelrealtimedots.copy(
                                                                    delay = !cat.labelrealtimedots.delay
                                                                )
                                                            )
                                                        }
                                                }
                                            )
                                        }


                                    }
                                }

                                // Content for "More" Tab
                                if (selectedTab == "more") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showZombieBuses = !showZombieBuses }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = showZombieBuses,
                                            onCheckedChange = { showZombieBuses = it })
                                        Text(
                                            "Show vehicles without trip info",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { usUnits = !usUnits }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = usUnits,
                                            onCheckedChange = { usUnits = it })
                                        Text(
                                            "Use US Units (mph)",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbars,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(10f)
                            .windowInsetsPadding(
                                WindowInsets(
                                    bottom = WindowInsets.safeContent.getBottom(
                                        density
                                    )
                                )
                            )
                    )
                }
            }
        }
    }

    // Helper to get string property from spatialk.geojson.Feature
    private fun org.maplibre.spatialk.geojson.Feature<*, JsonObject?>.getString(key: String): String? {
        // Check for null primitive or string "null"
        return this.properties?.get(key)?.jsonPrimitive?.content?.takeIf { it != "null" }
    }

    // Helper to get int property from spatialk.geojson.Feature
    private fun org.maplibre.spatialk.geojson.Feature<*, JsonObject?>.getInt(key: String): Int? {
        // Properties are often stored as doubles, so get double and convert to Int
        return this.properties?.get(key)?.jsonPrimitive?.double?.toInt()
    }

    /**
     * Processes a list of clicked features known to be VEHICLES.
     */
    private fun processVehicleClicks(
        features: List<org.maplibre.spatialk.geojson.Feature<
                *, JsonObject?
                >>
    ): List<MapSelectionOption> {
        val selectedVehiclesKeyUnique = mutableSetOf<String>()

        return features.mapNotNull { f ->
            // Use vehicleIdLabel + chateau for uniqueness
            val key = (f.getString("vehicleIdLabel") ?: "") + (f.getString("chateau") ?: "")
            if (key.isBlank() || !selectedVehiclesKeyUnique.add(key)) return@mapNotNull null

            try {
                MapSelectionOption(
                    MapSelectionSelector.VehicleMapSelector(
                        chateau_id = f.getString("chateau") ?: "",
                        vehicle_id = f.getString("vehicleIdLabel"),
                        route_id = f.getString("routeId"),
                        headsign = f.getString("headsign") ?: "",
                        triplabel = f.getString("tripIdLabel"),
                        colour = f.getString("color") ?: "#FFFFFF",
                        route_short_name = f.getString("route_short_name"),
                        route_long_name = f.getString("route_long_name"),
                        route_type = f.getString("routeType")?.toIntOrNull() ?: 3,
                        trip_short_name = f.getString("trip_short_name"),
                        text_colour = f.getString("text_color") ?: "#000000",
                        gtfs_id = f.getString("rt_id") ?: "", // JS maps rt_id to gtfs_id
                        trip_id = f.getString("trip_id"),
                        start_time = f.getString("start_time"),
                        start_date = f.getString("start_date")
                    )
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to parse VehicleMapSelector: ${e.message} for props ${f.properties}"
                )
                null
            }
        }
    }

    /**
     * Processes a list of clicked features known to be ROUTES.
     */
    private fun processRouteClicks(
        features: List<org.maplibre.spatialk.geojson.Feature<*,
                JsonObject?
                >>
    ): List<MapSelectionOption> {
        val selectedRoutesKeyUnique = mutableSetOf<String>()

        return features.mapNotNull { f ->
            // Use chateau + route_label for uniqueness
            val key = (f.getString("chateau") ?: "") + (f.getString("route_label") ?: "")
            if (key.isBlank() || !selectedRoutesKeyUnique.add(key)) return@mapNotNull null

            // JS: x.properties.routes.replace('{', '').replace('}', '').split(',')[0]
            val routesProp = f.getString("routes")?.replace(Regex("[{}]"), "")
            if (routesProp.isNullOrBlank()) return@mapNotNull null

            try {
                MapSelectionOption(
                    MapSelectionSelector.RouteMapSelector(
                        chateau_id = f.getString("chateau") ?: "",
                        route_id = routesProp.split(',').first(),
                        colour = "#${f.getString("color") ?: "FFFFFF"}",
                        name = f.getString("route_label")
                    )
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to parse RouteMapSelector: ${e.message} for props ${f.properties}"
                )
                null
            }
        }
    }

    /**
     * Processes a list of clicked features known to be STOPS.
     */
    private fun processStopClicks(features: List<org.maplibre.spatialk.geojson.Feature<*, JsonObject?>>): List<MapSelectionOption> {
        val selectedStopsKeyUnique = mutableSetOf<String>()

        return features.mapNotNull { f ->
            // Use chateau + gtfs_id for uniqueness
            val key = (f.getString("chateau") ?: "") + (f.getString("gtfs_id") ?: "")
            if (key.isBlank() || !selectedStopsKeyUnique.add(key)) return@mapNotNull null

            try {
                MapSelectionOption(
                    MapSelectionSelector.StopMapSelector(
                        chateau_id = f.getString("chateau") ?: "",
                        stop_id = f.getString("gtfs_id") ?: "", // JS maps gtfs_id to stop_id
                        stop_name = f.getString("displayname") ?: "Unknown Stop"
                    )
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to parse StopMapSelector: ${e.message} for props ${f.properties}"
                )
                null
            }
        }
    }
}


@Composable
fun LayerTabs(
    selectedTab: String, onTabSelected: (String) -> Unit
) {
    val tabs = listOf("intercityrail", "localrail", "bus", "other")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .clickable { onTabSelected(tab) }) {
                Text(
                    text = tab.replaceFirstChar { it.uppercase() },
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun circleInside(dark: Boolean) = if (dark) Color(0xFF1C2636) else Color(0xFFFFFFFF)
private fun circleOutside(dark: Boolean) = if (dark) Color(0xFFFFFFFF) else Color(0xFF1C2636)

/** JS: bus_stop_stop_color(darkMode) */
private fun busStopStrokeColorExpr(dark: Boolean)
        : Expression<ColorValue> {
    return if (dark) {
        // ['step', ['zoom'], '#e0e0e0', 14, '#dddddd']


        step(
            zoom(),
            const(Color(0xFFE0E0E0)),
            *arrayOf(
                0 to const(Color(0xFFE0E0E0)),
                14 to const(Color(0xFFDDDDDD))
            )
        )
    } else {
        const(Color(0xFF333333))
    }
}

private fun rtEq(v: Int) =
    get("route_type").cast<NumberValue<EquatableValue>>().eq(const(v))

private fun isMetro() =
    any(rtEq(1), rtEq(12))

private fun isTram() =
    any(rtEq(0), rtEq(5))

private fun isIntercity() =
    rtEq(2)

@Composable
fun AddStops(
    layerSettings: AllLayerSettings,
) {
    val isDark = isSystemInDarkTheme()
    val cfg = LocalConfiguration.current
    val isTablet = (cfg.screenWidthDp >= 768)

    // Vector sources
    val busStopsSource = rememberVectorSource(uri = STOP_SOURCES.getValue("busstops"))
    val railStopsSource = rememberVectorSource(uri = STOP_SOURCES.getValue("railstops"))
    val otherStopsSource = rememberVectorSource(uri = STOP_SOURCES.getValue("otherstops"))

    // Fonts
    val barlowRegular = const(listOf("Barlow-Regular"))
    val barlowMedium = const(listOf("Barlow-Medium"))
    val barlowBold = const(listOf("Barlow-Bold"))

    // Colors (inside/outside dot)
    val circleInside = if (isDark) Color(0xFF1C2636) else Color(0xFFFFFFFF)
    val circleOutside = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1C2636)

    // JS: bus_stop_stop_color(darkMode) -> step(zoom, ...)
    val busStrokeColorExpr: Expression<ColorValue> = if (isDark) {
        step(
            input = zoom(),
            const(Color(0xFFE0E0E0)),
            14.0 to const(Color(0xFFDDDDDD))
        )
    } else {
        const(Color(0xFF333333))
    }

    // route_type filters
    val rtEq = { v: Int ->
        (any(
            get("route_types").cast<VectorValue<EquatableValue>>().dslcontains(const(v))
        ))
    }

    val childrenRtEq = { v: Int ->
        (any(
            get("children_route_types").cast<VectorValue<EquatableValue>>().dslcontains(const(v))
        ))
    }

    val isMetro = any(
        any(
            (rtEq(1)),
            childrenRtEq(1),
        ),
        (rtEq(12))
    )
    val isTram = all(
        any(any(rtEq(0), childrenRtEq(0)), rtEq(5)),
        isMetro().not()
    )
    val isIntercity = rtEq(2)

    // String pieces for label fields
    val semi = const("; ")
    val empty = const("")
    val level: Expression<StringValue> =
        coalesce(get("level_id").cast<StringValue>(), empty)
    val platform: Expression<StringValue> =
        coalesce(get("platform_code").cast<StringValue>(), empty)

    /* ============================================================
       BUS (busstops)
       ============================================================ */
    CircleLayer(
        id = LayersPerCategory.Bus.Stops,
        source = busStopsSource,
        sourceLayer = "data",
        color = const(Color(0xFF1C2636)),
        radius = interpolate(
            type = linear(),
            input = zoom(),
            11 to const(0.9.dp),
            12 to const(1.2.dp),
            13 to const(2.dp)
        ),
        strokeColor = busStrokeColorExpr,
        strokeWidth = step(
            input = zoom(),
            const(0.8.dp),
            12.0 to const(1.2.dp),
            13.2 to const(1.5.dp)
        ),
        strokeOpacity = step(
            input = zoom(),
            const(0.5f),
            15.0 to const(0.6f)
        ),
        opacity = const(0.1f),
        minZoom = 13f,
        visible = (layerSettings.bus as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.Bus.LabelStops,
        source = busStopsSource,
        sourceLayer = "data",
        textField = get("displayname").cast(),
        textFont = barlowMedium,
        textSize = interpolate(
            type = linear(),
            input = zoom(),
            13 to const(0.4375f).em, // 7px
            15 to const(0.5f).em,    // 8px
            16 to const(0.625f).em   // 10px
        ),
        // radial 0.5 => y offset +0.5em
        textOffset = offset(0.em, 0.5.em),
        textColor = if (isDark) const(Color(0xFFEEE6FE)) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(0.4.dp),
        minZoom = 14.7f,
        visible = (layerSettings.bus as LayerCategorySettings).labelstops
    )

    /* ============================================================
       OTHER (otherstops)
       ============================================================ */
    CircleLayer(
        id = LayersPerCategory.Other.Stops,
        source = otherStopsSource,
        sourceLayer = "data",
        color = const(circleInside),
        radius = interpolate(
            type = linear(),
            input = zoom(),
            8 to const(1.dp),
            12 to const(4.dp),
            15 to const(5.dp)
        ),
        strokeColor = const(circleOutside),
        strokeWidth = step(
            input = zoom(),
            const(1.2.dp),
            13.2 to const(1.5.dp)
        ),
        strokeOpacity = step(
            input = zoom(),
            const(0.5f),
            15.0 to const(0.6f)
        ),
        opacity = interpolate(
            type = linear(),
            input = zoom(),
            10 to const(0.7f),
            16 to const(0.8f)
        ),
        minZoom = 9f,
        visible = (layerSettings.other as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.Other.LabelStops,
        source = otherStopsSource,
        sourceLayer = "data",
        textField = get("displayname").cast(),
        textSize = interpolate(
            type = linear(),
            input = zoom(),
            9 to const(0.375f).em,  // 6px
            15 to const(0.5625f).em, // 9px
            17 to const(0.625f).em   // 10px
        ),
        // radial 1 => y offset +1em
        textOffset = offset(0.em, 1.em),
        textFont = barlowBold,
        textColor = if (isDark) const(Color(0xFFEEE6FE)) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(1.dp),
        minZoom = 9f,
        visible = (layerSettings.other as LayerCategorySettings).labelstops
    )

    /* ============================================================
       METRO (railstops, route_type 1/12)
       ============================================================ */
    CircleLayer(
        id = LayersPerCategory.Metro.Stops,
        source = railStopsSource,
        sourceLayer = "data",
        color = const(circleInside),
        radius = interpolate(
            type = linear(),
            input = zoom(),
            8 to const(0.8.dp),
            12 to const(3.5.dp),
            15 to const(5.dp)
        ),
        strokeColor = const(circleOutside),
        strokeWidth = step(
            input = zoom(),
            const(0.4.dp),
            10.5 to const(0.8.dp),
            11.0 to const(1.2.dp),
            13.2 to const(1.5.dp)
        ),
        strokeOpacity = step(
            input = zoom(),
            const(0.5f),
            15.0 to const(0.6f)
        ),
        opacity = interpolate(
            type = linear(),
            input = zoom(),
            10 to const(0.7f),
            16 to const(0.8f)
        ),
        minZoom = 9f,
        filter = isMetro,
        visible = (layerSettings.localrail as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.Metro.LabelStops,
        source = railStopsSource,
        sourceLayer = "data",
        textField = step(
            input = zoom(),
            fallback = get("name").cast<StringValue>(),
            8 to get("name").cast<StringValue>(),
            13 to get("name").cast<StringValue>() +
                    switch(
                        conditions = arrayOf(
                            condition(
                                has("level_id"),
                                semi + level
                            )
                        ),
                        fallback = const("")
                    )
                    +
                    switch(
                        conditions = arrayOf(
                            condition(
                                has("platform_code"),
                                semi + platform
                            )
                        ),

                        fallback = const("")
                    )
        ),
        textSize = interpolate(
            type = linear(),
            input = zoom(),
            11 to const(0.5f).em,    // 8px
            12 to const(0.625f).em,  // 10px
            14 to const(0.75f).em    // 12px
        ),
        // radial: 7->0.1, 10->0.3, 12->0.6 (maplibre uses em); animate y offset
        textOffset = interpolate(
            type = linear(),
            input = zoom(),
            7 to offset(0.em, 0.10.em),
            10 to offset(0.em, 0.30.em),
            12 to offset(0.em, 0.60.em)
        ),
        textFont = step(
            input = zoom(),
            barlowRegular,
            12.0 to barlowMedium
        ),
        textColor = if (isDark) const(Color.White) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(1.dp), // was 1.dp
        minZoom = 11f,
        filter = isMetro,
        visible = (layerSettings.localrail as LayerCategorySettings).labelstops
    )

    /* ============================================================
        TRAM (railstops, route_type 0/5)
       ============================================================ */
    CircleLayer(
        id = LayersPerCategory.Tram.Stops,
        source = railStopsSource,
        sourceLayer = "data",
        color = const(circleInside),
        radius = interpolate(
            type = linear(),
            input = zoom(),
            9 to const(0.9.dp),
            10 to const(1.dp),
            12 to const(3.dp),
            15 to const(4.dp)
        ),
        strokeColor = const(circleOutside),
        strokeWidth = step(
            input = zoom(),
            const(1.2.dp),
            13.2 to const(1.5.dp)
        ),
        strokeOpacity = step(
            input = zoom(),
            const(0.4f),
            11.0 to const(0.5f),
            15.0 to const(0.6f)
        ),
        opacity = const(0.8f),
        minZoom = 9f,
        filter = isTram,
        visible = (layerSettings.localrail as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.Tram.LabelStops,
        source = railStopsSource,
        sourceLayer = "data",
        textField = step(
            input = zoom(),
            fallback = get("name").cast<StringValue>(),
            8 to get("name").cast<StringValue>(),
            14 to get("name").cast<StringValue>() +
                    switch(
                        conditions = arrayOf(
                            condition(
                                has("level_id"),
                                semi + level
                            )
                        ),
                        fallback = const("")
                    )
                    +
                    switch(
                        conditions = arrayOf(
                            condition(
                                has("platform_code"),
                                semi + platform
                            )
                        ),

                        fallback = const("")
                    )
        ),
        textSize = interpolate(
            type = linear(),
            input = zoom(),
            9 to const(0.4375f).em, // 7px
            11 to const(0.4375f).em, // 7px
            12 to const(0.5625f).em, // 9px
            14 to const(0.625f).em   // 10px
        ),
        // radial: 7->0.2, 10->0.3, 12->0.5
        textOffset = interpolate(
            type = linear(),
            input = zoom(),
            7 to offset(0.em, 0.2.em),
            10 to offset(0.em, 0.3.em),
            12 to offset(0.em, 0.5.em)
        ),
        textFont = step(
            input = zoom(),
            barlowRegular,
            12.0 to barlowMedium
        ),
        textColor = if (isDark) const(Color.White) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color(0xFFFFFFFF)),
        textHaloWidth = const(1.dp), // was 1.dp
        minZoom = 12f,
        filter = isTram,
        visible = (layerSettings.localrail as LayerCategorySettings).labelstops
    )

    /* ============================================================
       INTERCITY (railstops, route_type 2)
       ============================================================ */
    val intercityCircleRadius = interpolate(
        type = linear(),
        input = zoom(),
        7 to const(1.dp),
        8 to const(2.dp),
        9 to const(3.dp),
        12 to const(5.dp),
        15 to const(8.dp)
    )
    val intercityLabelSize = interpolate(
        type = linear(),
        input = zoom(),
        6 to const(0.375f).em, // 6px
        13 to const(0.75f).em   // 12px
    )

    CircleLayer(
        id = LayersPerCategory.IntercityRail.Stops,
        source = railStopsSource,
        sourceLayer = "data",
        color = const(circleInside),
        radius = intercityCircleRadius,
        strokeColor = const(circleOutside),
        strokeWidth = interpolate(
            type = linear(),
            input = zoom(),
            9 to const(1.dp),
            13.2 to const(1.5.dp)
        ),
        strokeOpacity = step(
            input = zoom(),
            const(0.5f),
            15.0 to const(0.6f)
        ),
        opacity = step(
            input = zoom(),
            const(0.6f),
            13.0 to const(0.8f)
        ),
        minZoom = 7.5f,
        filter = isIntercity,
        visible = layerSettings.intercityrail.stops
    )

    SymbolLayer(
        id = LayersPerCategory.IntercityRail.LabelStops,
        source = railStopsSource,
        sourceLayer = "data",
        textField = step(
            input = zoom(),
            fallback = get("name").cast<StringValue>(),
            8 to get("name").cast<StringValue>(),
            13 to get("name").cast<StringValue>() +
                    switch(
                        conditions = arrayOf(
                            condition(
                                has("level_id"),
                                semi + level
                            )
                        ),
                        fallback = const("")
                    )
                    +
                    switch(
                        conditions = arrayOf(
                            condition(
                                has("platform_code"),
                                semi + platform
                            )
                        ),

                        fallback = const("")
                    )
        ),
        textSize = intercityLabelSize,
        // radial 0.2 => y offset +0.2em
        textOffset = offset(0.em, 0.2.em),
        textFont = step(
            input = zoom(),
            barlowRegular,
            10 to barlowMedium
        ),
        textColor = if (isDark) const(Color.White) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(1.dp), // was 1.dp
        minZoom = 8f,
        filter = isIntercity,
        visible = (layerSettings.intercityrail as LayerCategorySettings).labelstops
    )


}

private fun visibilityOf(isOn: Boolean) = isOn

@Composable
fun AddLiveDots(
    isDark: Boolean,
    usUnits: Boolean,
    showZombieBuses: Boolean,
    layerSettings: AllLayerSettings,
    vehicleLocationsV2: Map<String, Map<String, Map<Int, Map<Int, Map<String, VehiclePosition>>>>>,
    routeCache: Map<String, Map<String, RouteCacheEntry>>,
    busDotsSrc: MutableState<GeoJsonSource>,
    metroDotsSrc: MutableState<GeoJsonSource>,
    railDotsSrc: MutableState<GeoJsonSource>,
    otherDotsSrc: MutableState<GeoJsonSource>,
    railInFrame: Boolean
) {
    val scope = rememberCoroutineScope()
    // remember previous references per category to detect changes cheaply.
    // If the fetcher did not touch a category, its inner maps keep the same reference.
    val prevVehicleRefs = remember { mutableStateMapOf<String, Any?>() }

    val previousRouteStructHash = remember { mutableStateOf<Int?>(null) }

    // Build a stable list of route-cache hash for a category across chateaus.
    fun routeRefsFor(): Int {
        return routeCache.values
            .flatMap { it.keys }
            .sorted()
            .hashCode()
    }

    fun categoryChanged(category: String): Boolean {
        val currVehRef = vehicleLocationsV2[category]
        //val currRouteRefs = routeRefsFor()

        val prevVehRef = prevVehicleRefs[category]


        val vehChanged = (prevVehRef !== currVehRef)

        val routeChanged = (previousRouteStructHash.value !== routeRefsFor())


        return vehChanged || routeChanged
    }

    suspend fun updateIfChanged(category: String, sink: MutableState<GeoJsonSource>) {
        scope.launch(kotlinx.coroutines.Dispatchers.Default) { // Launch in a background thread
            if (!categoryChanged(category)) return@launch

            val features = rerenderCategoryLiveDots(
                category = category,
                isDark = isDark,
                usUnits = usUnits,
                vehicleLocationsV2 = vehicleLocationsV2,
                routeCache = routeCache
            )

            // Switch back to main thread to update the GeoJsonSource
            launch(kotlinx.coroutines.Dispatchers.Main) {
                sink.value.setData(GeoJsonData.Features(FeatureCollection(features)))
            }

            // Stamp current references so future comparisons are accurate
            prevVehicleRefs[category] = vehicleLocationsV2[category]
            previousRouteStructHash.value = routeRefsFor()
        }
    }

    // Re-check when backing data *or* styling inputs that affect rendering change.
    LaunchedEffect(vehicleLocationsV2, routeCache, isDark, usUnits) {
        println("Rerender")
        updateIfChanged("bus", busDotsSrc)
        updateIfChanged("metro", metroDotsSrc)
        updateIfChanged("rail", railDotsSrc)
        updateIfChanged("other", otherDotsSrc)
    }
}

@Composable
private fun LiveDotLayers(
    category: String,
    source: GeoJsonSource,
    settings: LabelSettings,
    isVisible: Boolean,
    baseFilter: Expression<BooleanValue>,
    bearingFilter: Expression<BooleanValue>,
    usUnits: Boolean,
    isDark: Boolean,
    layerIdPrefix: Any = LayersPerCategory.Bus, // Default, will be overridden
    railInFrame: Boolean
) {
    val (idDots, idLabels, idPointing, idPointingShell) = when (layerIdPrefix) {
        is LayersPerCategory.Bus -> listOf(
            LayersPerCategory.Bus.Livedots,
            LayersPerCategory.Bus.Labeldots,
            LayersPerCategory.Bus.Pointing,
            LayersPerCategory.Bus.PointingShell
        )

        is LayersPerCategory.Metro -> listOf(
            LayersPerCategory.Metro.Livedots,
            LayersPerCategory.Metro.Labeldots,
            LayersPerCategory.Metro.Pointing,
            LayersPerCategory.Metro.PointingShell
        )

        is LayersPerCategory.Tram -> listOf(
            LayersPerCategory.Tram.Livedots,
            LayersPerCategory.Tram.Labeldots,
            LayersPerCategory.Tram.Pointing,
            LayersPerCategory.Tram.PointingShell
        )

        is LayersPerCategory.IntercityRail -> listOf(
            LayersPerCategory.IntercityRail.Livedots,
            LayersPerCategory.IntercityRail.Labeldots,
            LayersPerCategory.IntercityRail.Pointing,
            LayersPerCategory.IntercityRail.PointingShell
        )

        is LayersPerCategory.Other -> listOf(
            LayersPerCategory.Other.Livedots,
            LayersPerCategory.Other.Labeldots,
            LayersPerCategory.Other.Pointing,
            LayersPerCategory.Other.PointingShell
        )

        else -> return // Should not happen
    }

    val contrastColorProp = if (isDark) "contrastdarkmode" else "contrastlightmode"
    val contrastBearingColorProp = if (isDark) "contrastdarkmodebearing" else "contrastlightmode"
    val vehicleColor = get(contrastColorProp).cast<ColorValue>()
    val bearingColor = get(contrastBearingColorProp).cast<ColorValue>()

    val styles = getLiveDotStyle(category, settings, railInFrame)

    // --- End of Category-Specific Sizing ---


    // Live Dot
    CircleLayer(
        id = idDots,
        source = source,
        color = get("color").cast<ColorValue>(),
        radius = styles.dotRadius,
        strokeColor = if (isDark) const(Color(0xFF2E394B)) else const(Color.White),
        strokeWidth = styles.dotStrokeWidth,
        opacity = styles.dotOpacity,
        strokeOpacity = styles.dotStrokeOpacity,
        filter = baseFilter,
        visible = isVisible,
        minZoom = styles.minLayerDotsZoom
    )

    // Bearing Pointer Shell (Outline)
    SymbolLayer(
        id = idPointingShell,
        source = source,
        iconImage = image(painterResource(R.drawable.pointing_shell)),
        iconColor = if (isDark) const(Color(0xFF1E293B)) else const(Color.White),
        iconSize = styles.bearingIconSize,
        iconRotate = get("bearing").cast<FloatValue>(),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        iconOffset = styles.bearingIconOffset,
        iconOpacity = styles.bearingShellOpacity,
        filter = bearingFilter,
        visible = isVisible,
        minZoom = styles.minBearingZoom
    )

    // Bearing Pointer
    SymbolLayer(
        id = idPointing,
        source = source,
        iconImage = image(
            painterResource(R.drawable.pointing50percent),
            drawAsSdf = true
        ),
        iconOpacity = styles.bearingFilledOpacity,
        iconColor = bearingColor,
        iconSize = styles.bearingIconSize,
        iconRotate = get("bearing").cast<FloatValue>(),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        iconOffset = styles.bearingIconOffset,
        filter = bearingFilter,
        visible = isVisible,
        minZoom = styles.minBearingZoom
    )

    // Label
    SymbolLayer(
        id = idLabels,
        source = source,
        textField = interpretLabelsToExpression(settings, usUnits),
        textFont = styles.labelTextFont,
        textSize = styles.labelTextSize,
        textColor = vehicleColor,
        textHaloColor = if (isDark) const(Color(0xFF1E293B)) else const(Color(0xFFEDEDED)), // JS: #1d1d1d vs #ededed
        textHaloWidth = if (isDark) const(2.4.dp) else const(1.0.dp), // JS: 2.4 vs 1
        textHaloBlur = const(1.0.dp), // JS: 1
        textRadialOffset = styles.labelRadialOffset,
        textAllowOverlap = const(false),
        textIgnorePlacement = step(
            zoom(),
            const(false),
            styles.labelIgnorePlacementZoom to const(true)
        ),
        textOpacity = styles.labelTextOpacity,
        filter = baseFilter,
        visible = isVisible,
        textAnchor = const(SymbolAnchor.Left),
        textJustify = const(TextJustify.Left),
        minZoom = styles.minLabelDotsZoom
    )
}
// Color, String, etc. Helpers

fun fixRouteName(chateauId: String, maptag: String, routeId: String?): String {
    if (chateauId == "mtr" && maptag == "Disneyland Resort") return "DRL"
    if (chateauId == "nyct" && routeId != null) {
        if (routeId.contains("GS")) return "S"
    }
    return maptag
}

fun fixHeadsignText(headsign: String, maptag: String): String {
    if (maptag == "J" || maptag == "Z") {
        if (headsign.contains("Broad St")) return "Broad St"
    }
    return headsign
}
