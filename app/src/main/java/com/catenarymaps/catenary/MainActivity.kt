package com.catenarymaps.catenary

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
import io.github.dellisd.spatialk.geojson.Position
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
import io.github.dellisd.spatialk.geojson.Point
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.dsl.contains as dslcontains
import org.maplibre.compose.expressions.value.TextUnitValue
import org.maplibre.compose.map.RenderOptions
import android.content.SharedPreferences
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateTo
import io.github.dellisd.spatialk.geojson.FeatureCollection
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

private const val PREFS_NAME = "catenary_prefs"
private const val K_LAT = "camera_lat"
private const val K_LON = "camera_lon"
private const val K_ZOOM = "camera_zoom"
private const val K_BEAR = "camera_bearing"
private const val K_TILT = "camera_tilt"

private const val K_DATADOG_CONSENT = "datadog_consent"
private const val K_GA_CONSENT = "ga_consent"

private fun SharedPreferences.putDouble(key: String, value: Double) =
    edit().putLong(key, java.lang.Double.doubleToRawLongBits(value)).apply()

private fun SharedPreferences.getDouble(key: String, default: Double = Double.NaN): Double {
    if (!contains(key)) return default
    return java.lang.Double.longBitsToDouble(getLong(key, 0L))
}

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

// ⬇️ REPLACE your old LayersPerCategory with this one
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

// Main category settings (bus/localrail/intercityrail/other)
data class LayerCategorySettings(
    var visiblerealtimedots: Boolean = true,
    var labelshapes: Boolean = true,
    var stops: Boolean = true,
    var shapes: Boolean = true,
    var labelstops: Boolean = true,
    var labelrealtimedots: LabelSettings = LabelSettings()
)

// Extra "more" settings
data class FoamermodeSettings(
    var infra: Boolean = false,
    var maxspeed: Boolean = false,
    var signalling: Boolean = false,
    var electrification: Boolean = false,
    var gauge: Boolean = false,
    var dummy: Boolean = true
)

data class MoreSettings(
    var foamermode: FoamermodeSettings = FoamermodeSettings(),
    var showstationentrances: Boolean = true,
    var showstationart: Boolean = false,
    var showbikelanes: Boolean = false,
    var showcoords: Boolean = false
)

val layerSettings = mutableStateOf(
    mapOf(
        "bus" to LayerCategorySettings(),
        "localrail" to LayerCategorySettings(),
        "intercityrail" to LayerCategorySettings(labelrealtimedots = LabelSettings(trip = true)),
        "other" to LayerCategorySettings(),
        "more" to MoreSettings()
    )
)

val STOP_SOURCES = mapOf(
    "busstops" to "https://birch6.catenarymaps.org/busstops",
    "stationfeatures" to "https://birch7.catenarymaps.org/station_features",
    "railstops" to "https://birch5.catenarymaps.org/railstops",
    "otherstops" to "https://birch8.catenarymaps.org/otherstops"
)

private const val TAG = "CatenaryDebug"
var visibleChateaus: List<String> = emptyList()

val easeOutSpec: AnimationSpec<Float> = tween(
    durationMillis = 300, delayMillis = 0, easing = EaseOutCirc
)

enum class SheetSnapPoint { Collapsed, PartiallyExpanded, Expanded }

private fun queryVisibleChateaus(camera: CameraState, mapSize: IntSize) {
    val projection = camera.projection ?: return
    if (mapSize.width == 0 || mapSize.height == 0) return

    val density = Resources.getSystem().displayMetrics.density
    val rect = DpRect(
        left = 0.dp,
        top = 0.dp,
        right = (mapSize.width / density).dp,
        bottom = (mapSize.height / density).dp
    )

    val features = projection.queryRenderedFeatures(
        rect = rect, layerIds = setOf("chateaus_calc")
    )

    val names = features.map { f ->
        f.properties["chateau"]?.toString()?.trimStart('"')?.trimEnd('"') ?: "Unknown"
    }
    visibleChateaus = names
    Log.d(TAG, "Visible chateaus (${names.size}): ${names.joinToString(limit = 100)}")
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
    val route_colour: String,
    val route_text_colour: String,
    val route_short_name: String?,
    val route_long_name: String?
)

@Serializable
data class CategoryData(
    val vehicle_positions: Map<String, VehiclePosition>?,
    val vehicle_route_cache: Map<String, RouteCacheEntry>?,
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
)

// Ktor HTTP Client (initialize once)
val ktorClient = HttpClient() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
            encodeDefaults = true
        })
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val isFetchingRealtimeData = AtomicBoolean(false)

    // Realtime Data State Holders
    var realtimeVehicleLocations =
        mutableStateOf<Map<String, Map<String, Map<String, VehiclePosition>>>>(emptyMap())

    // ChateauID -> Category -> RouteID -> Cache
    var realtimeVehicleRouteCache =
        mutableStateOf<Map<String, Map<String, Map<String, RouteCacheEntry>>>>(emptyMap())

    // ChateauID -> Category -> Timestamp
    var realtimeVehicleLocationsLastUpdated =
        mutableStateOf<Map<String, Map<String, Long>>>(emptyMap())

    // ChateauID -> Category -> Hash
    var realtimeVehicleRouteCacheHash =
        mutableStateOf<Map<String, Map<String, ULong>>>(emptyMap())

    private fun fetchRealtimeData(
        scope: CoroutineScope,
        zoom: Double,
        showZombieBuses: Boolean, // Pass state
        settings: Map<String, Any>
    ) {
        if (!isFetchingRealtimeData.compareAndSet(false, true)) {
            Log.d(TAG, "Skipping fetch, another one is already in progress.")
            return
        }

        scope.launch {
            try {
                val categoriesToRequest = mutableListOf<String>()

                val busThreshold = 8
                if ((settings["bus"] as LayerCategorySettings).visiblerealtimedots && zoom >= busThreshold) {
                    categoriesToRequest.add("bus")
                }
                if ((settings["intercityrail"] as LayerCategorySettings).visiblerealtimedots && zoom >= 3) {
                    categoriesToRequest.add("rail")
                }
                if ((settings["localrail"] as LayerCategorySettings).visiblerealtimedots && zoom >= 4) {
                    categoriesToRequest.add("metro")
                }
                if ((settings["other"] as LayerCategorySettings).visiblerealtimedots && zoom >= 3) {
                    categoriesToRequest.add("other")
                }


                if (categoriesToRequest.isEmpty() || visibleChateaus.isEmpty()) {
                    // Don't fetch if no categories are visible or no chateaus are in view
                    return@launch
                }

                // Build chateaus_to_fetch object
                val chateausToFetch = mutableMapOf<String, ChateauFetchParams>()
                val lastUpdatedMap = realtimeVehicleLocationsLastUpdated.value
                val hashCacheMap = realtimeVehicleRouteCacheHash.value

                visibleChateaus.forEach { chateauId ->
                    if (chateauId == "bus~dft~gov~uk") {
                        if (visibleChateaus.contains("sncf") || visibleChateaus.contains("île~de~france~mobilités")) {
                            return@forEach
                        }
                    }

                    //println("Chateau id $chateauId")
                    val categoryParams = mutableMapOf<String, CategoryParams>()
                    val cats = listOf("bus", "rail", "metro", "other")

                    cats.forEach { cat ->
                        val lastUpdated = lastUpdatedMap[chateauId]?.get(cat) ?: 0L
                        val hash = hashCacheMap[chateauId]?.get(cat) ?: ULong.MIN_VALUE
                        categoryParams[cat] = CategoryParams(
                            hash_of_routes = hash,
                            last_updated_time_ms = lastUpdated
                        )

                        //println("last updated for $cat in $chateauId is $lastUpdated")
                    }
                    chateausToFetch[chateauId] =
                        ChateauFetchParams(category_params = categoryParams)
                }

                val requestBody = BulkRealtimeRequest(
                    categories = categoriesToRequest, chateaus = chateausToFetch
                )

                try {

                    // You must run this inside a coroutine scope (e.g., in a suspend function)
                    try {
                        val rawresponse: String =
                            ktorClient.post("https://birch.catenarymaps.org/bulk_realtime_fetch_v1") {
                                contentType(ContentType.Application.Json)
                                setBody(requestBody)
                            }.body()

                        // println("Bulk Fetch: $rawresponse")

                        val json_for_this = Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                            isLenient = true
                            encodeDefaults = true
                        }
                        val response =
                            json_for_this.decodeFromString<BulkRealtimeResponse>(rawresponse)

                        println("Recieved live dots")

                        // Process the response (porting process_realtime_vehicle_locations_v2)
                        val newLocations = realtimeVehicleLocations.value.toMutableMap()
                        val newRouteCache = realtimeVehicleRouteCache.value.toMutableMap()
                        val newLastUpdated =
                            realtimeVehicleLocationsLastUpdated.value.toMutableMap()
                        val newHashCache = realtimeVehicleRouteCacheHash.value.toMutableMap()

                        response.chateaus.forEach { (chateauId, chateauData) ->
                            // println("Processing Chateau $chateauId")
                            chateauData.categories.forEach { (category, categoryData) ->
                                if (categoryData != null) {
                                    // Update Locations
                                    if (categoryData.vehicle_positions != null) {
                                        val chateauLocations =
                                            newLocations.getOrPut(category) { mutableMapOf() }
                                                .toMutableMap()
                                        chateauLocations[chateauId] = categoryData.vehicle_positions
                                        newLocations[category] = chateauLocations
                                        //   println("set value for new vehicle locations for $category with $chateauId and length ${categoryData.vehicle_positions.size}")
                                    }
                                    // Update Route Cache
                                    if (categoryData.vehicle_route_cache != null) {
                                        val chateauCache =
                                            newRouteCache.getOrPut(chateauId) { mutableMapOf() }
                                                .toMutableMap()
                                        chateauCache[category] = categoryData.vehicle_route_cache
                                        newRouteCache[chateauId] = chateauCache
                                    }
                                    // Update Last Updated Time
                                    val chateauLastUpdated =
                                        newLastUpdated.getOrPut(chateauId) { mutableMapOf() }
                                            .toMutableMap()
                                    chateauLastUpdated[category] = categoryData.last_updated_time_ms
                                    newLastUpdated[chateauId] = chateauLastUpdated

                                    // Update Hash
                                    val chateauHash =
                                        newHashCache.getOrPut(chateauId) { mutableMapOf() }
                                            .toMutableMap()
                                    chateauHash[category] = categoryData.hash_of_routes
                                    newHashCache[chateauId] = chateauHash
                                }
                            }
                        }

                        // Atomically update the state variables
                        println("set value for new vehicle locations")
                        realtimeVehicleLocations.value = newLocations
                        realtimeVehicleRouteCache.value = newRouteCache
                        realtimeVehicleLocationsLastUpdated.value = newLastUpdated
                        realtimeVehicleRouteCacheHash.value = newHashCache

                    } catch (e: ClientRequestException) {
                        // This block catches 4xx errors, including your 400 Bad Request
                        val errorBody: String = e.response.body()
                        println("Failed to fetch realtime data (Client Error ${e.response.status}): $errorBody")

                    } catch (e: ServerResponseException) {
                        // This block catches 5xx server errors
                        val errorBody: String = e.response.body()
                        println("Failed to fetch realtime data (Server Error ${e.response.status}): $errorBody")

                    } catch (e: Exception) {
                        // This catches other errors (network connection, DNS, etc.)
                        println("An unexpected error occurred: ${e.message}")
                    }


                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch realtime data: ${e.message}")
                }
            } finally {
                isFetchingRealtimeData.set(false)
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val initialDatadogConsent = prefs.getBoolean(K_DATADOG_CONSENT, false)
        val trackingConsent =
            if (initialDatadogConsent) TrackingConsent.GRANTED else TrackingConsent.NOT_GRANTED

        val initialGaConsent = prefs.getBoolean(K_GA_CONSENT, true)

        val initialOptOut = !initialGaConsent
        try {
            GoogleAnalytics.getInstance(this).appOptOut = initialOptOut
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
            if (currentCache.keys.any { it !in visibleSet }) {
                realtimeVehicleRouteCache.value = currentCache.filterKeys { it in visibleSet }
            }

            val currentLastUpdated = realtimeVehicleLocationsLastUpdated.value
            if (currentLastUpdated.keys.any { it !in visibleSet }) {
                realtimeVehicleLocationsLastUpdated.value =
                    currentLastUpdated.filterKeys { it in visibleSet }
            }

            val currentHash = realtimeVehicleRouteCacheHash.value
            if (currentHash.keys.any { it !in visibleSet }) {
                realtimeVehicleRouteCacheHash.value = currentHash.filterKeys { it in visibleSet }
            }

            // Prune the 'realtimeVehicleLocations' map (which is keyed by Category first)
            val currentLocations = realtimeVehicleLocations.value
            var locationsModified = false
            val newLocations = currentLocations.toMutableMap()

            currentLocations.forEach { (category, chateauMap) ->
                if (chateauMap.keys.any { it !in visibleSet }) {
                    newLocations[category] = chateauMap.filterKeys { it in visibleSet }
                    locationsModified = true
                }
            }

            if (locationsModified) {
                realtimeVehicleLocations.value = newLocations
            }
        }




        setContent {

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

            val density = LocalDensity.current
            var pin by remember { mutableStateOf(PinState(active = false, position = null)) }

            val searchViewModel: SearchViewModel = viewModel()

            var catenaryStack by remember { mutableStateOf(ArrayDeque<CatenaryStackEnum>()) }

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
            var showZombieBuses by remember { mutableStateOf(false) }
            var usUnits by remember { mutableStateOf(false) }
            val isDark = isSystemInDarkTheme()


            // Realtime Fetcher Logic
            val rtScope = rememberCoroutineScope()

            // Periodic fetcher (e.g., every 5 seconds)
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000L) // 1 second refresh interval
                    fetchRealtimeData(
                        rtScope,
                        camera.position.zoom,
                        showZombieBuses, // Pass the state
                        layerSettings.value
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
                                val result = snackbars.showSnackbar(
                                    message = "An update has just been downloaded.",
                                    actionLabel = "RESTART",
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

                    val anchors = DraggableAnchors<SheetSnapPoint> {
                        SheetSnapPoint.Collapsed at screenHeightPx - with(density) { 64.dp.toPx() }
                        SheetSnapPoint.PartiallyExpanded at screenHeightPx / 2f
                        SheetSnapPoint.Expanded at with(density) { 60.dp.toPx() }
                    }


                    val draggableState = remember {
                        AnchoredDraggableState(
                            initialValue = SheetSnapPoint.Collapsed,
                            anchors = anchors,
                            positionalThreshold = { with(density) { 128.dp.toPx() } },
                            velocityThreshold = { with(density) { 128.dp.toPx() } },
                            snapAnimationSpec = easeOutSpec,
                            decayAnimationSpec = splineBasedDecay(density),
                        )
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
                        camera.animateTo(
                            CameraPosition(
                                target = camera.position.target,
                                zoom = camera.position.zoom,
                                bearing = camera.position.bearing,
                                tilt = camera.position.tilt,
                                padding = desiredPadding
                            )
                        )
                    }

                    /** idle detection state for move/zoom end */
                    var lastCameraPos by remember { mutableStateOf<CameraPosition?>(null) }
                    var lastMoveAt by remember { mutableStateOf(0L) }
                    var lastQueriedPos by remember { mutableStateOf<CameraPosition?>(null) }
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
                            val projection = camera.projection ?: run {
                                Log.w(TAG, "Map clicked, but projection is not ready.")
                                return@MaplibreMap ClickResult.Pass
                            }

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
                            queryVisibleChateaus(camera, mapSize)


                        },
                        // 3) Use onFrame to detect camera idle -> covers move end & zoom end

                        onFrame = {
                            val now = SystemClock.uptimeMillis()
                            val pos = camera.position


                            if (lastCameraPos == null || lastCameraPos != pos) {
                                lastCameraPos = pos
                                lastMoveAt = now

                                // Only drop the lock if this wasn't an internal move we initiated.
                                if (!geoLock.isInternalMove() && geoLock.isActive()) {
                                    geoLock.deactivate()
                                }
                                if (geoLock.isActive()) lastPosByLock = pos
                            }

                            if (now - lastMoveAt >= idleDebounceMs) {
                                if (lastQueriedPos != pos) {
                                    if (camera.projection != null && mapSize != IntSize.Zero) {
                                        queryVisibleChateaus(camera, mapSize)
                                        lastQueriedPos = pos

                                        if (now - lastFetchedAt >= fetchDebounceMs) {
                                            fetchRealtimeData(
                                                rtScope,
                                                pos.zoom,
                                                showZombieBuses, // Pass the state
                                                layerSettings.value
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


                        }) {
                        val busDotsSrc = remember {
                            mutableStateOf<GeoJsonSource>(
                                GeoJsonSource(
                                    "bus_dots",
                                    GeoJsonData.Features(FeatureCollection(emptyList())),
                                    GeoJsonOptions()
                                )
                            )
                        }

                        val metroDotsSrc = remember {
                            mutableStateOf<GeoJsonSource>(
                                GeoJsonSource(
                                    "metro_dots",
                                    GeoJsonData.Features(FeatureCollection(emptyList())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val railDotsSrc = remember {
                            mutableStateOf<GeoJsonSource>(
                                GeoJsonSource(
                                    "rail_dots",
                                    GeoJsonData.Features(FeatureCollection(emptyList())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val otherDotsSrc = remember {
                            mutableStateOf<GeoJsonSource>(
                                GeoJsonSource(
                                    "other_dots",
                                    GeoJsonData.Features(FeatureCollection(emptyList())),
                                    GeoJsonOptions()
                                )
                            )
                        }

                        val transitShapeSource = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "transit_shape_context",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val transitShapeDetourSource = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "transit_shape_context_detour",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList())),
                                    GeoJsonOptions()
                                )
                            )
                        }

                        val transitShapeForStopSource = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "transit_shape_context_for_stop",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList())),
                                    GeoJsonOptions()
                                )
                            )
                        }
                        val stopsContextSource = remember {
                            mutableStateOf(
                                GeoJsonSource(
                                    id = "stops_context",
                                    data = GeoJsonData.Features(FeatureCollection(emptyList())),
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



                        // Source + layers
                        val chateausSource = rememberGeoJsonSource(
                            data = GeoJsonData.Uri("https://birch.catenarymaps.org/getchateaus")
                        )

                        FillLayer(
                            id = "chateaus_calc", source = chateausSource, opacity = const(0.0f)
                        )

                        AddShapes()

                        AddStops()

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

                        val locations = realtimeVehicleLocations.value
                        val cache = realtimeVehicleRouteCache.value

                        AddLiveDots(
                            isDark = isDark,
                            usUnits = usUnits,
                            showZombieBuses = showZombieBuses,
                            layerSettings = layerSettings.value,
                            vehicleLocations = locations,
                            routeCache = cache,
                            busDotsSrc = busDotsSrc,
                            metroDotsSrc = metroDotsSrc,
                            railDotsSrc = railDotsSrc,
                            otherDotsSrc = otherDotsSrc
                        )

                        // Layers for BUS
                        LiveDotLayers(
                            category = "bus",
                            source = busDotsSrc.value,                // <- persistent source
                            settings = (layerSettings.value["bus"] as LayerCategorySettings).labelrealtimedots,
                            isVisible = (layerSettings.value["bus"] as LayerCategorySettings).visiblerealtimedots,
                            baseFilter = if (showZombieBuses) all() else all(
                                feature.has("trip_id"),
                                get("trip_id").cast<StringValue>().neq(const(""))
                            ),
                            bearingFilter = all(
                                get("has_bearing").cast<BooleanValue>().eq(const(true)),
                                if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.Bus
                        )

// Layers for METRO/TRAM share the metro source but are filtered
                        LiveDotLayers(
                            category = "metro",
                            source = metroDotsSrc.value,
                            settings = (layerSettings.value["localrail"] as LayerCategorySettings).labelrealtimedots,
                            isVisible = (layerSettings.value["localrail"] as LayerCategorySettings).visiblerealtimedots,
                            baseFilter = all(
                                any(rtEq(1), rtEq(12)), if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            bearingFilter = all(
                                any(rtEq(1), rtEq(12)),
                                get("has_bearing").cast<BooleanValue>().eq(const(true))
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.Metro
                        )

                        LiveDotLayers(
                            category = "tram",
                            source = metroDotsSrc.value,  // re-uses metro source, different filter via layerIdPrefix branch
                            settings = (layerSettings.value["localrail"] as LayerCategorySettings).labelrealtimedots,
                            isVisible = (layerSettings.value["localrail"] as LayerCategorySettings).visiblerealtimedots,
                            baseFilter = all(
                                any(rtEq(0), rtEq(5)), if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            bearingFilter = all(
                                any(rtEq(0), rtEq(5)),
                                get("has_bearing").cast<BooleanValue>().eq(const(true))
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.Tram
                        )

// Layers for INTERCITY
                        LiveDotLayers(
                            category = "intercityrail",
                            source = railDotsSrc.value,
                            settings = (layerSettings.value["intercityrail"] as LayerCategorySettings).labelrealtimedots,
                            isVisible = (layerSettings.value["intercityrail"] as LayerCategorySettings).visiblerealtimedots,
                            baseFilter = all(
                                isIntercity(), if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            bearingFilter = all(
                                isIntercity(),
                                get("has_bearing").cast<BooleanValue>().eq(const(true))
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.IntercityRail
                        )

// Layers for OTHER
                        LiveDotLayers(
                            category = "other",
                            source = otherDotsSrc.value,
                            settings = (layerSettings.value["other"] as LayerCategorySettings).labelrealtimedots,
                            isVisible = (layerSettings.value["other"] as LayerCategorySettings).visiblerealtimedots,
                            baseFilter = if (showZombieBuses) all() else all(
                                feature.has("trip_id"),
                                get("trip_id").cast<StringValue>().neq(const(""))
                            ),
                            bearingFilter = all(
                                get("has_bearing").cast<BooleanValue>().eq(const(true)),
                                if (showZombieBuses) all() else all(
                                    feature.has("trip_id"),
                                    get("trip_id").cast<StringValue>().neq(const(""))
                                )
                            ),
                            usUnits = usUnits,
                            isDark = isDark,
                            layerIdPrefix = LayersPerCategory.Other
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
                                color = if (isSystemInDarkTheme()) const(Color(0xFF4CC9F0)) else const(
                                    Color(0xFF1D4ED8)
                                ),
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
                                .height(maxHeight),
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
                                    onTripClick = {
                                        val newStack = ArrayDeque(catenaryStack)
                                        newStack.addLast(


                                            CatenaryStackEnum.SingleTrip(
                                                chateau_id = it.chateauId!!,
                                                trip_id = it.tripId!!,
                                                route_id = it.routeId,
                                                start_date = it.startDay,
                                                start_time = null,
                                                vehicle_id = null,
                                                route_type = it.routeType
                                            )
                                        )

                                        catenaryStack = newStack

                                    }
                                )

                            } else {
                                // Handle other stack states
                                val currentScreen = catenaryStack.last()
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    // Add a simple back button
                                    IconButton(onClick = {
                                        val newStack = ArrayDeque(catenaryStack)
                                        newStack.removeLastOrNull()
                                        catenaryStack = newStack
                                    }) {
                                        Icon(Icons.Filled.ArrowBack, "Go back")
                                    }

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

                                                    // --- Pass the .value of the sources ---
                                                    transitShapeSource = transitShapeSourceRef.value!!,
                                                    transitShapeDetourSource = transitShapeDetourSourceRef.value!!,
                                                    stopsContextSource = stopsContextSourceRef.value!!,

                                                    // Pass the state setter
                                                    onSetStopsToHide = { newSet ->
                                                        stopsToHide = newSet
                                                    }
                                                )
                                            }

                                        }
                                        is CatenaryStackEnum.MapSelectionScreen -> {
                                            // This is where you would build your list UI
                                            // For now, we just show a summary
                                            Text(
                                                text = "You clicked ${currentScreen.arrayofoptions.size} items. TODO ADD THIS SCREEN",
                                                style = MaterialTheme.typography.headlineSmall,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            // TODO: Create a proper @Composable for MapSelectionScreen
                                            // and render currentScreen.arrayofoptions in a LazyColumn
                                        }
                                        is CatenaryStackEnum.SettingsStack -> {
                                            SettingsScreen(
                                                datadogConsent = datadogConsent,
                                                onDatadogConsentChanged = onDatadogConsentChanged,
                                                gaConsent = gaConsent,
                                                onGaConsentChanged = onGaConsentChanged
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
                                            mapCenter = camera.position.target
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
                                top = if (contentWidthFraction == 1.0f) 72.dp else 16.dp,
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
                            // scope.launch { snackbars.showSnackbar("Following your location") }
                        },
                        modifier = fabModifier,
                        shape = CircleShape,
                        containerColor = if (geoLock.isActive()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (geoLock.isActive()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "My Location")
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
                            onStopClick = { ranking, stopInfo ->
                                geoLock.deactivate()

                                val pos = Position(stopInfo.point.x, stopInfo.point.y)

                                // === FIX for Error 5 ===
                                scope.launch {
                                    camera.animateTo(
                                        camera.position.copy(
                                            target = pos,
                                            zoom = 16.0
                                        )
                                    )
                                }
                                // === FIX for Error 6 ===
                                focusManager.clearFocus() // This should now resolve
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
                                        Tab(
                                            selected = selectedTab == title,
                                            onClick = { selectedTab = title },
                                            text = {
                                                Text(
                                                    text = title.replaceFirstChar { it.uppercase() },
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
                                    val currentSettings =
                                        layerSettings.value[selectedTab] as? LayerCategorySettings
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
                                                    val updated =
                                                        settings.copy(shapes = !settings.shapes)
                                                    layerSettings.value =
                                                        layerSettings.value.toMutableMap().apply {
                                                            put(selectedTab, updated)
                                                        }
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
                                                    val updated =
                                                        settings.copy(labelshapes = !settings.labelshapes)
                                                    layerSettings.value =
                                                        layerSettings.value.toMutableMap().apply {
                                                            put(selectedTab, updated)
                                                        }
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
                                                    val updated =
                                                        settings.copy(stops = !settings.stops)
                                                    layerSettings.value =
                                                        layerSettings.value.toMutableMap().apply {
                                                            put(selectedTab, updated)
                                                        }
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
                                                    val updated =
                                                        settings.copy(labelstops = !settings.labelstops)
                                                    layerSettings.value =
                                                        layerSettings.value.toMutableMap().apply {
                                                            put(selectedTab, updated)
                                                        }
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
                                                    val updated =
                                                        settings.copy(visiblerealtimedots = !settings.visiblerealtimedots)
                                                    layerSettings.value =
                                                        layerSettings.value.toMutableMap().apply {
                                                            put(selectedTab, updated)
                                                        }
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
                                        val updateLayer = { newLabelSettings: LabelSettings ->
                                            val updatedCategorySettings =
                                                settings.copy(labelrealtimedots = newLabelSettings)
                                            layerSettings.value =
                                                layerSettings.value.toMutableMap().apply {
                                                    put(selectedTab, updatedCategorySettings)
                                                }
                                        }

                                        // Row 3: Label Toggles (First row from JS)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            VehicleLabelToggleButton(
                                                name = "Route", // Corresponds to $_('showroute')
                                                icon = Icons.Filled.Route, // Corresponds to symbol="route"
                                                isActive = labelSettings.route,
                                                onToggle = { updateLayer(labelSettings.copy(route = !labelSettings.route)) }
                                            )
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.trip), // Corresponds to $_('showtrip')
                                                icon = Icons.Filled.AltRoute, // Corresponds to symbol="mode_of_travel"
                                                isActive = labelSettings.trip,
                                                onToggle = { updateLayer(labelSettings.copy(trip = !labelSettings.trip)) }
                                            )
                                            VehicleLabelToggleButton(
                                                name = "Vehicle", // Corresponds to $_('showvehicle')
                                                icon = Icons.Filled.Train, // Corresponds to symbol="train"
                                                isActive = labelSettings.vehicle,
                                                onToggle = { updateLayer(labelSettings.copy(vehicle = !labelSettings.vehicle)) }
                                            )

                                            VehicleLabelToggleButton(
                                                name = "Headsign", // Corresponds to $_('headsign')
                                                icon = Icons.Filled.SportsScore, // Corresponds to symbol="sports_score"
                                                isActive = labelSettings.headsign,
                                                onToggle = { updateLayer(labelSettings.copy(headsign = !labelSettings.headsign)) }
                                            )
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.speed),
                                                icon = Icons.Filled.Speed, // Corresponds to symbol="speed"
                                                isActive = labelSettings.speed,
                                                onToggle = { updateLayer(labelSettings.copy(speed = !labelSettings.speed)) }
                                            )
                                            VehicleLabelToggleButton(
                                                name = stringResource(id = R.string.occupancy),
                                                icon = Icons.Filled.Group, // Corresponds to symbol="group"
                                                isActive = labelSettings.occupancy,
                                                onToggle = {
                                                    updateLayer(
                                                        labelSettings.copy(
                                                            occupancy = !labelSettings.occupancy
                                                        )
                                                    )
                                                }
                                            )
                                            VehicleLabelToggleButton(
                                                name = "Delay", // Corresponds to $_('delay')
                                                icon = Icons.Filled.Timer, // Corresponds to symbol="timer"
                                                isActive = labelSettings.delay,
                                                onToggle = { updateLayer(labelSettings.copy(delay = !labelSettings.delay)) }
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
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    // Helper to get string property from spatialk.geojson.Feature
    private fun io.github.dellisd.spatialk.geojson.Feature.getString(key: String): String? {
        // Check for null primitive or string "null"
        return this.properties[key]?.jsonPrimitive?.content?.takeIf { it != "null" }
    }

    // Helper to get int property from spatialk.geojson.Feature
    private fun io.github.dellisd.spatialk.geojson.Feature.getInt(key: String): Int? {
        // Properties are often stored as doubles, so get double and convert to Int
        return this.properties[key]?.jsonPrimitive?.double?.toInt()
    }

    /**
     * Processes a list of clicked features known to be VEHICLES.
     */
    private fun processVehicleClicks(features: List<io.github.dellisd.spatialk.geojson.Feature>): List<MapSelectionOption> {
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
                        triplabel = f.getString("tripIdLabel") ?: "",
                        colour = f.getString("color") ?: "#FFFFFF",
                        route_short_name = f.getString("route_short_name"),
                        route_long_name = f.getString("route_long_name"),
                        route_type = f.getInt("routeType") ?: 0,
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
    private fun processRouteClicks(features: List<io.github.dellisd.spatialk.geojson.Feature>): List<MapSelectionOption> {
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
    private fun processStopClicks(features: List<io.github.dellisd.spatialk.geojson.Feature>): List<MapSelectionOption> {
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
fun AddStops() {
    val dark = isSystemInDarkTheme()
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
    val circleInside = if (dark) Color(0xFF1C2636) else Color(0xFFFFFFFF)
    val circleOutside = if (dark) Color(0xFFFFFFFF) else Color(0xFF1C2636)

    // JS: bus_stop_stop_color(darkMode) -> step(zoom, ...)
    val busStrokeColorExpr: Expression<ColorValue> =
        if (dark) {
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

    val isMetro = all(
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
        minZoom = if (isTablet) 13f else 11.5f,
        visible = (layerSettings.value["bus"] as LayerCategorySettings).stops
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
        textColor = if (dark) const(Color(0xFFEEE6FE)) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (dark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(0.4.dp),
        minZoom = if (isTablet) 14.7f else 13.7f,
        visible = (layerSettings.value["bus"] as LayerCategorySettings).labelstops
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
        visible = (layerSettings.value["localrail"] as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.Metro.LabelStops,
        source = railStopsSource,
        sourceLayer = "data",
        textField = get("displayname").cast<StringValue>() + semi + level + semi + platform,
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
        textColor = if (dark) const(Color.White) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (dark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(1.dp),
        minZoom = 11f,
        filter = isMetro,
        visible = (layerSettings.value["localrail"] as LayerCategorySettings).labelstops
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
        visible = (layerSettings.value["localrail"] as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.Tram.LabelStops,
        source = railStopsSource,
        sourceLayer = "data",
        textField = get("displayname").cast<StringValue>() + semi + level + semi + platform,
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
        textColor = if (dark) const(Color.White) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (dark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(1.dp),
        minZoom = 12f,
        filter = isTram,
        visible = (layerSettings.value["localrail"] as LayerCategorySettings).labelstops
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
        opacity = step(
            input = zoom(),
            const(0.6f),
            13.0 to const(0.8f)
        ),
        minZoom = 7.5f,
        filter = isIntercity,
        visible = (layerSettings.value["intercityrail"] as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.IntercityRail.LabelStops,
        source = railStopsSource,
        sourceLayer = "data",
        textField = get("displayname").cast<StringValue>() + semi + level + semi + platform,
        textSize = intercityLabelSize,
        // radial 0.2 => y offset +0.2em
        textOffset = offset(0.em, 0.2.em),
        textFont = step(
            input = zoom(),
            barlowRegular,
            8.5 to barlowMedium
        ),
        textColor = if (dark) const(Color.White) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (dark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(1.dp),
        minZoom = 8f,
        filter = isIntercity,
        visible = (layerSettings.value["intercityrail"] as LayerCategorySettings).labelstops
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
        visible = (layerSettings.value["other"] as LayerCategorySettings).stops
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
        textColor = if (dark) const(Color(0xFFEEE6FE)) else const(Color(0xFF2A2A2A)),
        textHaloColor = if (dark) const(Color(0xFF0F172A)) else const(Color.White),
        textHaloWidth = const(1.dp),
        minZoom = 9f,
        visible = (layerSettings.value["other"] as LayerCategorySettings).labelstops
    )
}

@Composable
fun LayerToggleButton(
    name: String,
    icon: @Composable () -> Unit,
    isActive: Boolean,
    onToggle: () -> Unit,
    padding: Dp = 4.dp,
    activeBorderWidth: Dp = 2.dp // Parameter for "thick" border
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(4.dp) // This provides spacing between the box and the text
                .border( // --- CHANGE 1: Added border ---
                    width = activeBorderWidth,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .background(
                    // --- CHANGE 2: Removed active state from background ---
                    MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onToggle() }
                .padding(padding) // This is the inner padding for the icon
        ) {
            icon()
        }

        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            // --- CHANGE 3: Removed active state from text color ---
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun visibilityOf(isOn: Boolean) = isOn


@Preview
@Composable
fun SearchBarPreview() {
    var searchQuery = ""



    SearchBarCatenary(
        searchQuery = searchQuery, onValueChange = { searchQuery = it })
}


@Composable
fun SearchBarCatenary(
    searchQuery: String = "",
    onValueChange: (String) -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Transparent, shape = RoundedCornerShape(100.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .padding(vertical = 0.dp)
                .shadow(4.dp, shape = RoundedCornerShape(100.dp))
        )

        TextField(
            value = searchQuery,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 3.5.em, baselineShift = BaselineShift(1f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(color = Color.Transparent)
                .padding(vertical = 0.dp, horizontal = 0.dp)
                .onFocusChanged {
                    isFocused = it.isFocused // 👈 keep local state in sync
                    onFocusChange(it.isFocused)
                },
            shape = RoundedCornerShape(100.dp),
            placeholder = {
                Text(
                    stringResource(id = R.string.searchhere),
                    fontSize = 3.5.em,
                    overflow = TextOverflow.Visible
                )
            },
            leadingIcon = {
                if (isFocused) {
                    IconButton(onClick = { focusManager.clearFocus(force = true) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss search focus")
                    }
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Catenary logo",
                        modifier = Modifier
                            .size(32.dp)
                            .padding(start = 4.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            trailingIcon = {
                if (searchQuery.isEmpty() && !isFocused) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            })
    }


}

@Composable
fun AddLiveDots(
    isDark: Boolean,
    usUnits: Boolean,
    showZombieBuses: Boolean, // (not used here, but keep signature if you need it later)
    layerSettings: Map<String, Any>,
    vehicleLocations: Map<String, Map<String, Map<String, VehiclePosition>>>,
    routeCache: Map<String, Map<String, Map<String, RouteCacheEntry>>>,
    busDotsSrc: MutableState<GeoJsonSource>,
    metroDotsSrc: MutableState<GeoJsonSource>,
    railDotsSrc: MutableState<GeoJsonSource>,
    otherDotsSrc: MutableState<GeoJsonSource>,
) {
    // remember previous references per category to detect changes cheaply.
    // If the fetcher did not touch a category, its inner maps keep the same reference.
    val prevVehicleRefs = remember { mutableStateMapOf<String, Any?>() }
    val prevRouteRefs = remember { mutableStateMapOf<String, List<Any?>>() }

    // Build a stable list of route-cache *references* for a category across chateaus.
    fun routeRefsFor(category: String): List<Any?> {
        // Sort keys to keep order stable across runs.
        val chateauIds = routeCache.keys.sorted()
        return chateauIds.map { cid -> routeCache[cid]?.get(category) }
    }

    fun categoryChanged(category: String): Boolean {
        val currVehRef = vehicleLocations[category]
        val currRouteRefs = routeRefsFor(category)

        val prevVehRef = prevVehicleRefs[category]
        val prevRouteRefList = prevRouteRefs[category]

        val vehChanged = (prevVehRef !== currVehRef)

        val routesChanged = when {
            prevRouteRefList == null -> true
            prevRouteRefList.size != currRouteRefs.size -> true
            else -> prevRouteRefList.zip(currRouteRefs).any { (a, b) -> a !== b }
        }

        return vehChanged || routesChanged
    }

    suspend fun updateIfChanged(category: String, sink: MutableState<GeoJsonSource>) {
        if (!categoryChanged(category)) return

        val features = rerenderCategoryLiveDots(
            category = category,
            isDark = isDark,
            usUnits = usUnits,
            vehicleLocations = vehicleLocations,
            routeCache = routeCache
        )
        sink.value.setData(GeoJsonData.Features(FeatureCollection(features)))

        // Stamp current references so future comparisons are accurate
        prevVehicleRefs[category] = vehicleLocations[category]
        prevRouteRefs[category] = routeRefsFor(category)
    }

    // Re-check when backing data *or* styling inputs that affect rendering change.
    LaunchedEffect(vehicleLocations, routeCache, isDark, usUnits) {
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
    layerIdPrefix: Any = LayersPerCategory.Bus // Default, will be overridden
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

    // --- Start of Category-Specific Sizing ---

    val dotRadius: Expression<DpValue>
    val dotStrokeWidth: Expression<DpValue>
    val dotOpacity: Expression<FloatValue>
    val dotStrokeOpacity: Expression<FloatValue>
    val bearingIconSize: Expression<FloatValue>
    val bearingIconOffset: Expression<DpOffsetValue>
    val bearingShellOpacity: Expression<FloatValue>
    val bearingFilledOpacity: Expression<FloatValue>
    val labelTextSize: Expression<TextUnitValue>
    val labelTextFont: Expression<ListValue<StringValue>>
    val labelRadialOffset: Expression<TextUnitValue>
    val labelVariableAnchor: Expression<ListValue<SymbolAnchor>>
    val labelIgnorePlacementZoom: Double
    val labelTextOpacity: Expression<FloatValue>
    var minLayerDotsZoom: Float = 0.0F
    var minLabelDotsZoom: Float = 0.0F
    var minBearingZoom: Float = 0.0F

    when (category) {
        "bus" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                7.0 to const(1.2.dp),
                8.0 to const(1.6.dp),
                9.0 to const(1.7.dp),
                10.0 to const(2.0.dp),
                16.0 to const(6.0.dp)
            )
            dotStrokeWidth =
                interpolate(linear(), zoom(), 9.0 to const(0.3.dp), 15.0 to const(0.6.dp))
            dotOpacity = const(0.5f)
            dotStrokeOpacity = interpolate(
                linear(),
                zoom(),
                7.9 to const(0.0f),
                8.0 to const(0.3f),
                9.0 to const(0.5f),
                13.0 to const(0.9f)
            )
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                8.0 to const(0.3f),
                9.0 to const(0.4f),
                12.0 to const(0.5f),
                15.0 to const(0.6f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-10).dp),
                10.0 to offset(0.dp, (-8).dp),
                12.0 to offset(0.dp, (-7).dp),
                13.0 to offset(0.dp, (-6).dp),
                15.0 to offset(0.dp, (-5).dp)
            )
            bearingShellOpacity = interpolate(
                linear(),
                zoom(),
                9.0 to const(0.1f),
                10.0 to const(0.2f),
                12.0 to const(0.2f),
                15.0 to const(0.5f)
            )
            bearingFilledOpacity = const(0.4f)
            labelTextSize = if (settings.headsign) {
                interpolate(
                    type = linear(),
                    input = zoom(),
                    9.0 to const(0.25f.em),
                    11.0 to const(0.3125f.em),
                    13.0 to const(0.5625f.em),
                    15.0 to const(0.6875f.em)
                )
            } else {
                interpolate(
                    type = linear(),
                    input = zoom(),
                    9.0 to const(0.3125f.em),
                    11.0 to const(0.4375f.em),
                    13.0 to const(0.625f.em),
                    15.0 to const(0.8125f.em)
                )
            }
            labelTextFont =
                step(
                    zoom(),
                    const(listOf("Barlow-Medium")),
                    11.0 to const(listOf("Barlow-SemiBold"))
                )
            labelRadialOffset = const(0.2f.em)
            // ✅ Corrected value
            labelIgnorePlacementZoom = 10.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                7.9 to const(0.0f),
                8.0 to const(0.9f),
                11.0 to const(0.95f),
                12.0 to const(1.0f)
            )
            minLayerDotsZoom = 9F
            minLabelDotsZoom = 10F
            minBearingZoom = 11F
        }

        "metro" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(3.0.dp),
                8.0 to const(3.0.dp),
                10.0 to const(4.0.dp),
                11.0 to const(6.0.dp),
                16.0 to const(12.0.dp)
            )
            dotStrokeWidth =
                interpolate(linear(), zoom(), 8.0 to const(0.8.dp), 10.0 to const(1.2.dp))
            dotOpacity = interpolate(linear(), zoom(), 7.0 to const(0.5f), 9.0 to const(0.7f))
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                4.0 to const(0.4f),
                6.0 to const(0.5f),
                8.0 to const(0.55f),
                9.0 to const(0.6f),
                11.0 to const(0.7f),
                12.0 to const(0.8f),
                15.0 to const(0.9f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-10).dp),
                10.0 to offset(0.dp, (-8).dp),
                12.0 to offset(0.dp, (-7).dp),
                13.0 to offset(0.dp, (-6).dp),
                15.0 to offset(0.dp, (-5).dp)
            )
            bearingShellOpacity = interpolate(
                linear(),
                zoom(),
                9.8 to const(0.3f),
                11.0 to const(0.4f),
                11.5 to const(0.8f)
            )
            bearingFilledOpacity = const(0.6f)
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(0.3125f.em),
                9.0 to const(0.4375f.em),
                10.0 to const(0.5625f.em),
                11.0 to const(0.6875f.em),
                13.0 to const(0.75f.em)
            )
            labelTextFont = const(listOf("Barlow-Medium"))
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                2.0 to const(0.0f),
                2.5 to const(0.8f),
                10.0 to const(1.0f)
            )
        }

        "tram" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(1.8.dp),
                8.0 to const(2.3.dp),
                10.0 to const(4.0.dp),
                11.0 to const(4.5.dp),
                13.0 to const(6.0.dp),
                15.0 to const(6.0.dp),
                16.0 to const(10.0.dp)
            )
            dotStrokeWidth = interpolate(
                linear(),
                zoom(),
                8.0 to const(0.5.dp),
                9.0 to const(0.6.dp),
                10.0 to const(1.0.dp)
            )
            dotOpacity = interpolate(linear(), zoom(), 7.0 to const(0.5f), 9.0 to const(0.7f))
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                4.0 to const(0.2f),
                6.0 to const(0.3f),
                8.0 to const(0.4f),
                9.0 to const(0.5f),
                11.0 to const(0.6f),
                12.0 to const(0.7f),
                15.0 to const(0.8f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-10).dp),
                10.0 to offset(0.dp, (-8).dp),
                12.0 to offset(0.dp, (-7).dp),
                13.0 to offset(0.dp, (-6).dp),
                15.0 to offset(0.dp, (-5).dp)
            )
            bearingShellOpacity = interpolate(
                linear(),
                zoom(),
                6.0 to const(0.1f),
                9.8 to const(0.3f),
                11.0 to const(0.3f),
                11.5 to const(0.4f),
                12.0 to const(0.5f)
            )
            bearingFilledOpacity = interpolate(
                linear(),
                zoom(),
                6.0 to const(0.2f),
                9.0 to const(0.4f),
                11.0 to const(0.5f),
                13.0 to const(0.6f)
            )
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(0.25f.em),
                9.0 to const(0.375f.em),
                10.0 to const(0.4375f.em),
                11.0 to const(0.5625f.em),
                13.0 to const(0.625f.em),
                15.0 to const(0.875f.em)
            )
            labelTextFont = const(listOf("Barlow-Medium"))
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                2.0 to const(0.0f),
                2.5 to const(0.8f),
                10.0 to const(1.0f)
            )
        }

        "intercityrail" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                1.0 to const(1.0.dp),
                3.0 to const(2.5.dp),
                6.0 to const(2.8.dp),
                8.0 to const(4.0.dp),
                11.0 to const(6.0.dp),
                16.0 to const(10.0.dp)
            )
            dotStrokeWidth = interpolate(
                linear(),
                zoom(),
                3.0 to const(0.6.dp),
                5.0 to const(0.7.dp),
                7.0 to const(0.8.dp)
            )
            dotOpacity = interpolate(
                linear(),
                zoom(),
                4.0 to const(0.4f),
                7.0 to const(0.6f),
                11.0 to const(0.7f)
            )
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                4.0 to const(0.4f),
                6.0 to const(0.5f),
                8.0 to const(0.55f),
                9.0 to const(0.6f),
                11.0 to const(0.7f),
                12.0 to const(0.8f),
                15.0 to const(0.9f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-10).dp),
                10.0 to offset(0.dp, (-8).dp),
                12.0 to offset(0.dp, (-7).dp),
                13.0 to offset(0.dp, (-6).dp),
                15.0 to offset(0.dp, (-5).dp)
            )
            bearingShellOpacity =
                interpolate(linear(), zoom(), 9.0 to const(0.3f), 11.5 to const(0.8f))
            bearingFilledOpacity = const(1.0f)
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(0.5f.em),
                9.0 to const(0.5f.em),
                11.0 to const(0.875f.em),
                13.0 to const(0.9375f.em)
            )
            labelTextFont = const(listOf("Barlow-Medium"))
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                2.0 to const(0.0f),
                2.5 to const(0.8f),
                10.0 to const(1.0f)
            )
        }

        "other" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                8.0 to const(5.0.dp),
                10.0 to const(6.0.dp),
                16.0 to const(10.0.dp)
            )
            dotStrokeWidth = const(1.0.dp)
            dotOpacity = const(0.5f)
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                4.0 to const(0.1f),
                6.0 to const(0.1f),
                8.0 to const(0.15f),
                9.0 to const(0.18f),
                11.0 to const(0.2f),
                12.0 to const(0.25f),
                15.0 to const(0.5f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-20).dp),
                13.0 to offset(0.dp, (-20).dp),
                15.0 to offset(0.dp, (-20).dp)
            )
            bearingShellOpacity =
                interpolate(linear(), zoom(), 9.0 to const(0.3f), 11.5 to const(0.8f))
            bearingFilledOpacity = const(0.6f)
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to const(0.53125f.em),
                11.0 to const(0.8125f.em),
                13.0 to const(1.0f.em)
            )
            labelTextFont =
                step(zoom(), const(listOf("Barlow-Regular")), 9.0 to const(listOf("Barlow-Bold")))
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                2.0 to const(0.0f),
                2.5 to const(0.8f),
                10.0 to const(1.0f)
            )
        }

        else -> {
            // Fallback to your original generic values
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                6 to const(2.dp),
                10 to const(3.dp),
                14 to const(5.dp),
                18 to const(8.dp)
            )
            dotStrokeWidth = const(1.5.dp)
            dotOpacity = const(1.0f)
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                10 to const(0.7f),
                14 to const(1.0f),
                18 to const(1.2f)
            )
            bearingIconOffset = offset(0.dp, (-20).dp) // A reasonable guess in dp
            bearingShellOpacity = const(1.0f)
            bearingFilledOpacity = const(0.2f)
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                9 to const(0.625f.em),
                14 to const(0.8125f.em)
            )
            labelTextFont = const(listOf("Barlow-SemiBold"))
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = const(1.0f)
        }
    }

    // --- End of Category-Specific Sizing ---


    // Live Dot
    CircleLayer(
        id = idDots,
        source = source,
        color = vehicleColor,
        radius = dotRadius,
        strokeColor = if (isDark) const(Color(0xFF1E293B)) else const(Color.White),
        strokeWidth = dotStrokeWidth,
        opacity = dotOpacity,
        strokeOpacity = dotStrokeOpacity,
        filter = baseFilter,
        visible = isVisible,
        minZoom = minLayerDotsZoom
    )

    // Bearing Pointer Shell (Outline)
    SymbolLayer(
        id = idPointingShell,
        source = source,
        iconImage = image(painterResource(R.drawable.pointing_shell)),
        iconColor = if (isDark) const(Color(0xFF1E293B)) else const(Color.White),
        iconSize = bearingIconSize,
        iconRotate = get("bearing").cast<FloatValue>(),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        iconOffset = bearingIconOffset,
        iconOpacity = bearingShellOpacity,
        filter = bearingFilter,
        visible = isVisible,
        minZoom = minBearingZoom
    )

    // Bearing Pointer
    SymbolLayer(
        id = idPointing,
        source = source,
        iconImage = image(
            painterResource(R.drawable.pointing50percent),
            drawAsSdf = true
        ),
        iconOpacity = bearingFilledOpacity,
        iconColor = bearingColor,
        iconSize = bearingIconSize,
        iconRotate = get("bearing").cast<FloatValue>(),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        iconOffset = bearingIconOffset,
        filter = bearingFilter,
        visible = isVisible,
        minZoom = minBearingZoom
    )

    // Label
    SymbolLayer(
        id = idLabels,
        source = source,
        textField = interpretLabelsToExpression(settings, usUnits),
        textFont = labelTextFont,
        textSize = labelTextSize,
        textColor = vehicleColor,
        textHaloColor = if (isDark) const(Color(0xFF1E293B)) else const(Color(0xFFEDEDED)), // JS: #1d1d1d vs #ededed
        textHaloWidth = if (isDark) const(2.4.dp) else const(1.0.dp), // JS: 2.4 vs 1
        textHaloBlur = const(1.0.dp), // JS: 1
        textRadialOffset = labelRadialOffset,
        textAllowOverlap = const(false),
        textIgnorePlacement = step(
            zoom(),
            const(false),
            labelIgnorePlacementZoom to const(true)
        ),
        textOpacity = labelTextOpacity,
        filter = baseFilter,
        visible = isVisible,
        textAnchor = const(SymbolAnchor.Left),
        textJustify = const(TextJustify.Left),
        minZoom = minLabelDotsZoom
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

@Composable
fun VehicleLabelToggleButton(
    name: String,
    icon: ImageVector,
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(IntrinsicSize.Min) // Ensure column shrinks to text
    ) {
        FilledIconToggleButton(
            checked = isActive,
            onCheckedChange = { onToggle() },
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        ) {
            Icon(icon, contentDescription = name)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
