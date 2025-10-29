package com.catenarymaps.catenary

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOutCirc
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.ui.unit.dp
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
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.layers.*;
import org.maplibre.compose.sources.rememberVectorSource
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.compose.expressions.value.*;
import org.maplibre.compose.expressions.ast.*;
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import io.github.dellisd.spatialk.geojson.Point
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.value.InterpolatableValue
import org.maplibre.compose.expressions.value.InterpolationValue
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.value.TextUnitValue
import org.maplibre.compose.map.RenderOptions
import kotlin.time.Duration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import io.github.dellisd.spatialk.geojson.FeatureCollection
import org.maplibre.compose.sources.GeoJsonSource

object LayersPerCategory {
    object Bus {
        const val Shapes = "bus-shapes"
        const val LabelShapes = "bus-labelshapes"

        const val Stops = "busstopscircle"

        const val LabelStops = "busstopslabel"
    }

    object Other {
        const val Shapes = "other-shapes"
        const val LabelShapes = "other-labelshapes"
        const val FerryShapes = "ferryshapes"
    }

    object IntercityRail {
        const val Shapes = "intercityrail-shapes"
        const val LabelShapes = "intercityrail-labelshapes"
    }

    object Metro {
        const val Shapes = "metro-shapes"
        const val LabelShapes = "metro-labelshapes"
    }

    object Tram {
        const val Shapes = "tram-shapes"
        const val LabelShapes = "tram-labelshapes"
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

val SHAPES_SOURCES = mapOf(
    "intercityrailshapes" to "https://birch1.catenarymaps.org/shapes_intercity_rail",
    "localcityrailshapes" to "https://birch2.catenarymaps.org/shapes_local_rail",
    "othershapes" to "https://birch3.catenarymaps.org/shapes_ferry",
    "busshapes" to "https://birch4.catenarymaps.org/shapes_bus"
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

var CatenaryStack: ArrayDeque<CatenaryStackEnum> = ArrayDeque(listOf())

enum class CatenaryStackEnum {

}

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

    val names = features.map { f -> f.properties["chateau"]?.toString() ?: "Unknown" }
    visibleChateaus = names
    Log.d(TAG, "Visible chateaus (${names.size}): ${names.joinToString(limit = 100)}")
}

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient


    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        setContent {
            val pinSourceRef = remember { mutableStateOf<GeoJsonSource?>(null) }
            val density = LocalDensity.current
            var pin by remember { mutableStateOf(PinState(active = false, position = null)) }


            val usePickedLocation = pin.active && pin.position != null
            val pickedPair: Pair<Double, Double>? =
                pin.position?.let { it.latitude to it.longitude }

            var mapSize by remember { mutableStateOf(IntSize.Zero) }

// Button actions (same behavior as your JS app)
            val onMyLocation: () -> Unit = {
                pin = pin.copy(active = false, position = null)
            }

            // Camera
            val camera = rememberCameraState(
                firstPosition = CameraPosition(
                    target = Position(-118.250, 34.050),
                    zoom = 6.0,
                    padding = PaddingValues(0.dp, 0.dp, 0.dp, 0.dp)
                )
            )


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




            // Launch location fetch once
            LaunchedEffect(Unit) {
                fetchLocation { lat, lon ->
                    currentLocation = lat to lon
                }
            }


            val styleUri =
                if (isSystemInDarkTheme()) "https://maps.catenarymaps.org/dark-style.json"
                else "https://maps.catenarymaps.org/light-style.json"

            var searchQuery by remember { mutableStateOf("") }

            var isSearchFocused by remember { mutableStateOf(false) }

            CatenaryComposeTheme {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val screenHeightPx = with(density) { maxHeight.toPx() }

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

                    MaplibreMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { newSize -> mapSize = newSize },
                        baseStyle = BaseStyle.Uri(styleUri),
                        cameraState = camera,
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
                            }

                            // If camera hasn't changed for idleDebounceMs, it's "idle"
                            if (now - lastMoveAt >= idleDebounceMs) {
                                // Only query if we haven't queried this position yet
                                if (lastQueriedPos != pos) {
                                    if (camera.projection != null && mapSize != IntSize.Zero) {
                                        queryVisibleChateaus(camera, mapSize)
                                        lastQueriedPos = pos
                                    }
                                }
                            }
                        }) {
                        // Source + layers
                        val chateausSource = rememberGeoJsonSource(
                            data = GeoJsonData.Uri("https://birch.catenarymaps.org/getchateaus")
                        )

                        FillLayer(
                            id = "chateaus_calc", source = chateausSource, opacity = const(0.0f)
                        )

                        AddShapes()

                        AddStops()

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


                        val pinSource = rememberGeoJsonSource(
                            data = GeoJsonData.Features(FeatureCollection(emptyList()))
                        )

                        DisposableEffect(pinSource) {
                            println("Create new reference to pin")

                            pinSourceRef.value = pinSource
                            onDispose {
                                // break the strong reference so the old map/style can be GC’d
                                if (pinSourceRef.value === pinSource) {
                                    pinSourceRef.value = null
                                }
                            }
                        }

                        DraggablePinLayers(pin = pin, pinSource = pinSource)


                    }



                    DraggablePinOverlay(
                        camera = camera,
                        mapSize = mapSize,
                        pin = pin,
                        onActivatePin = { pin = pin.copy(active = true) },

                        onDragEndCommit = { newPos ->
                            // Update Compose state
                            pin = pin.copy(position = newPos, active = true)
                            // Also update your map source (inside MaplibreMap scope) via your existing plumbing.
                            // e.g.


                            // pinSourceRef.value?.setData(GeoJsonData.Features(Point(newPos)))
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


                            if (CatenaryStack.size == 0) {

                                NearbyDepartures(
                                    userLocation = currentLocation,
                                    pickedLocation = pickedPair,
                                    //usePickedLocation = usePickedLocation,
                                    pin = pin,
                                    usePickedLocation = false,
                                    darkMode = isSystemInDarkTheme(),
                                    onMyLocation = {
                                        // Mimic JS “my_location_press()”: exit pin mode
                                        pin = pin.copy(active = false)
                                        pinSourceRef.value?.setData(
                                            GeoJsonData.Features(FeatureCollection(emptyList()))
                                        )
                                    },
                                    onPinDrop = {
                                        onPinDrop()
                                        pin.position?.let { pos ->
                                            pinSourceRef.value?.setData(
                                                GeoJsonData.Features(
                                                    Point(
                                                        pos
                                                    )
                                                )
                                            )
                                        }
                                    },
                                    onCenterPin = {
                                        onCenterPin()
                                        pin.position?.let { pos ->
                                            pinSourceRef.value?.setData(
                                                GeoJsonData.Features(
                                                    Point(
                                                        pos
                                                    )
                                                )
                                            )
                                        }
                                    })

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
                                    // keep if you still need this; otherwise it can be removed
                                    // searchBarBottomPx = bottom.toInt()
                                }) {
                                SearchBarCatenary(
                                    searchQuery = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    onFocusChange = { isFocused -> isSearchFocused = isFocused })
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !(sheetIsExpanded && contentWidthFraction == 1f),
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
                            containerColor = layerButtonColor,
                            contentColor = layerButtonContentColor
                        ) {
                            Icon(
                                Icons.Filled.Layers,
                                contentDescription = "Toggle Layers",
                                Modifier.size(24.dp)
                            )
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
                        enter = slideInVertically(initialOffsetY = { -it / 2 }),
                        exit = slideOutVertically(targetOffsetY = { -it / 2 }),

                        ) {
                        val overlayBase = if (contentWidthFraction < 1f) {
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(contentWidthFraction)
                        } else {
                            Modifier.fillMaxSize()
                        }

                        Surface(
                            modifier = overlayBase
                                .offset { IntOffset(x = 0, y = 0) }

                                .shadow(8.dp), tonalElevation = 6.dp, shadowElevation = 8.dp) {
                            // Your results go here
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }
                    }

                    // Layers Panel
                    AnimatedVisibility(
                        visible = showLayersPanel,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {

                        var selectedTab by remember { mutableStateOf("intercityrail") }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 300.dp),
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Layers", style = MaterialTheme.typography.headlineSmall)
                                    IconButton(onClick = { showLayersPanel = false }) {
                                        Icon(
                                            Icons.Filled.Close, contentDescription = "Close Layers"
                                        )
                                    }
                                }

                                LayerTabs(
                                    selectedTab = selectedTab, onTabSelected = { selectedTab = it })

                                if (selectedTab in listOf(
                                        "intercityrail", "localrail", "bus", "other"
                                    )
                                ) {
                                    // First row of buttons: shapes/labels/Pairs/etc.
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        // use LayerToggleButton composables here


                                        val currentSettings =
                                            layerSettings.value[selectedTab] as? LayerCategorySettings
                                        currentSettings?.let { settings ->
                                            LayerToggleButton(name = "Shapes", icon = {
                                                Icon(
                                                    Icons.Default.Route,
                                                    contentDescription = null
                                                )
                                            }, isActive = settings.shapes, onToggle = {
                                                val updated =
                                                    settings.copy(shapes = !settings.shapes)
                                                layerSettings.value =
                                                    layerSettings.value.toMutableMap()
                                                        .apply { put(selectedTab, updated) }
                                            })

                                            LayerToggleButton(name = "Shape Labels", icon = {
                                                Icon(
                                                    Icons.Default.Route,
                                                    contentDescription = null
                                                )
                                            }, isActive = settings.labelshapes, onToggle = {
                                                val updated =
                                                    settings.copy(labelshapes = !settings.labelshapes)
                                                layerSettings.value =
                                                    layerSettings.value.toMutableMap()
                                                        .apply { put(selectedTab, updated) }
                                            })
                                            // Repeat for labels, Pairs, vehicles etc.
                                        }

                                    }

                                    // Second row: route/trip/vehicle/headsign/speed/occupancy/delay
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        // more LayerToggleButton composables here
                                    }
                                }
                            }
                        }


                    }
                }
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

@Composable
fun AddStops() {
    val busStopsSource = rememberVectorSource(
        uri = STOP_SOURCES.getValue("busstops")
    )

    val otherStopsSource = rememberVectorSource(
        uri = STOP_SOURCES.getValue("otherstops")
    )

    val railStops = rememberVectorSource(
        uri = STOP_SOURCES.getValue("railstops")
    )

    CircleLayer(
        id = LayersPerCategory.Bus.Stops,
        source = busStopsSource,
        sourceLayer = "data",
        opacity = const(0.1f),
        minZoom = 11f,
        strokeColor = if (isSystemInDarkTheme()) const(Color.White) else const(Color.Black),
        strokeWidth = interpolate(
            type = linear(),
            input = zoom(),
            10 to const(0.8.dp),
            12 to const(1.dp),
            13 to const(1.3.dp),
            15 to const(2.dp)
        ),
        radius = interpolate(
            type = linear(), input = zoom(), 11 to const(0.9.dp), 13 to const(2.dp)
        ),
        strokeOpacity = interpolate(
            type = linear(), input = zoom(), 11 to const(0.2f), 14 to const(0.5f)
        ),
        visible = (layerSettings.value["bus"] as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.Bus.LabelStops,
        source = busStopsSource,
        sourceLayer = "data",
        minZoom = 15f,
        textSize = interpolate(
            type = linear(), input = zoom(), 14 to const(0.5f).em, 16 to const(0.7f).em
        ),
        textField = get("displayname").cast(),
        textFont = const(listOf("Barlow-Medium")),
        visible = (layerSettings.value["bus"] as LayerCategorySettings).labelstops,
        textColor = if (isSystemInDarkTheme()) const(Color.White) else const(Color.Black)
    )


}

@Composable
fun AddShapes() {
    val busShapesSource = rememberVectorSource(
        uri = SHAPES_SOURCES.getValue("busshapes")
    )
    val otherShapesSource = rememberVectorSource(
        uri = SHAPES_SOURCES.getValue("othershapes")
    )
    val intercityRailSource = rememberVectorSource(
        uri = SHAPES_SOURCES.getValue("intercityrailshapes")
    )
    val localCityRailSource = rememberVectorSource(
        uri = SHAPES_SOURCES.getValue("localcityrailshapes")
    )

    val bus = layerSettings.value["bus"] as LayerCategorySettings

    // BUS


// BUS
    var colorBusLine: org.maplibre.compose.expressions.ast.Expression<ColorValue> =
        const("#").plus(get("color").cast()).convertToColor()

    var colorBusLineText: org.maplibre.compose.expressions.ast.Expression<ColorValue> =
        const("#").plus(get("text_color").cast()).convertToColor()


    LineLayer(
        id = LayersPerCategory.Bus.Shapes,
        source = busShapesSource,
        sourceLayer = "data",
        color = colorBusLine,
        width = interpolate(
            type = linear(),
            input = zoom(),
            7 to const(0.5.dp),
            10 to const(0.7.dp),
            12 to const(1.0.dp),
            14 to const(2.6.dp),
        ),
        opacity = interpolate(
            type = linear(),
            input = zoom(),
            7 to const(0.08f),
            8 to const(0.10f),
            11 to const(0.30f),
        ),
        minZoom = 8f,
        visible = bus.shapes
    )

    var busTextSize: Expression<TextUnitValue> = interpolate(
        type = linear(),
        input = zoom(),
        10 to const(0.3125f).em,
        11 to const(0.4375f).em,
        13 to const(0.625f).em,
    );

    /*
    spacing = step(
        input = zoom(),
        // base value, then threshold -> value pairs
        const(200.dp),
        12 to const(100.dp),
        13 to const(100.dp),
        15 to const(120.dp),
        20 to const(150.dp),
    );
     */

    SymbolLayer(
        id = LayersPerCategory.Bus.LabelShapes,
        source = busShapesSource,
        sourceLayer = "data",
        placement = const(SymbolPlacement.Line),
        textField = coalesce(get("route_label").cast(), const("")),
        // ListValue<StringValue>
        textFont = const(listOf("Barlow-Regular")),
        textSize = busTextSize,
        textIgnorePlacement = const(false),
        textAllowOverlap = const(false),

        textColor = colorBusLineText,
        textHaloColor = colorBusLine,
        textHaloWidth = const(2.dp),
        textHaloBlur = const(0.dp),
        minZoom = 11f,
        visible = bus.labelshapes
    )

    // Pull per-category settings
    val otherSettings = layerSettings.value["other"] as LayerCategorySettings
    val intercitySettings = layerSettings.value["intercityrail"] as LayerCategorySettings
    val localRailSettings = layerSettings.value["localrail"] as LayerCategorySettings

// Common color expressions (same style as your bus code)
    val colorLine: org.maplibre.compose.expressions.ast.Expression<ColorValue> =
        const("#").plus(get("color").cast()).convertToColor()
    val colorText: org.maplibre.compose.expressions.ast.Expression<ColorValue> =
        const("#").plus(get("text_color").cast()).convertToColor()

    /* =========================
       OTHER (othershapes)
       ========================= */

    val cast_chateau: Expression<StringValue> = get("chateau").cast()
    val get_stop_to_stop_generated: Expression<BooleanValue> = get("stop_to_stop_generated").cast()

// shapes (routes_type 6/7, excluding schweiz stop_to_stop_generated)
    LineLayer(
        id = LayersPerCategory.Other.Shapes,
        source = otherShapesSource,
        sourceLayer = "data",
        color = colorLine,
        width = interpolate(
            type = linear(),
            input = zoom(),
            7 to const(2.dp),
            9 to const(3.dp),
        ),
        opacity = const(1f),
        minZoom = 1f,
        visible = otherSettings.shapes,
        // filter: ! (chateau=='schweiz' && stop_to_stop_generated==true)  && (route_type==6 || route_type==7)
        filter = all(
            all(
                cast_chateau.eq(const("schweiz")), get_stop_to_stop_generated.eq(const(true))
            ).not(), any(
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(6)),
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(7))
            )
        )
    )

// labelshapes
    SymbolLayer(
        id = LayersPerCategory.Other.LabelShapes,
        source = otherShapesSource,
        sourceLayer = "data",
        placement = const(SymbolPlacement.Line),
        textField = coalesce(get("route_label").cast(), const("")),
        textFont = const(listOf("Barlow-Regular")),
        textSize = interpolate(
            type = linear(), input = zoom(), 3 to const(0.4375f).em, // 7px
            9 to const(0.5625f).em, // 9px
            13 to const(0.6875f).em  //11px
        ),
        textIgnorePlacement = const(false),
        textAllowOverlap = const(false),
        textColor = colorText,
        textHaloColor = colorLine,
        textHaloWidth = const(2.dp),
        textHaloBlur = const(1.dp),
        minZoom = 3f,
        visible = otherSettings.labelshapes,
        // filter: (route_type in 4,6,7) && !(schweiz && stop_to_stop_generated)
        filter = all(
            any(
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(4)),
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(6)),
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(7))
            ), all(
                get("chateau").cast<StringValue>().eq(const("schweiz")),
                (get("stop_to_stop_generated").cast<BooleanValue>().convertToBoolean()).eq(
                    const(
                        true
                    )
                )
            ).not()
        )
    )

// ferry (route_type == 4) with dash

    LineLayer(
        id = LayersPerCategory.Other.FerryShapes,
        source = otherShapesSource,
        sourceLayer = "data",
        color = colorLine,
        width = interpolate(
            type = linear(),
            input = zoom(),
            6 to const(0.5.dp),
            7 to const(1.0.dp),
            10 to const(1.5.dp),
            14 to const(3.0.dp),
        ),
        opacity = interpolate(
            type = linear(), input = zoom(), 6 to const(0.8f), 7 to const(0.9f)
        ),
        minZoom = 3f,
        visible = otherSettings.shapes,
        filter = all(
            get("route_type").cast<NumberValue<EquatableValue>>().eq(const(4))
        ),
        dasharray = const(listOf(1f, 2f))
    )


    /* =========================
       INTERCITY RAIL (intercityrailshapes)
       ========================= */

// shapes

    val line_opacity_intercity: Expression<NumberValue<Number>> = switch(
        input = get("stop_to_stop_generated").cast<BooleanValue>().asString(), case(
            label = "true", output = const(0.2f)
        ), fallback = const(0.9f)
    )

    LineLayer(
        id = LayersPerCategory.IntercityRail.Shapes,
        source = intercityRailSource,
        sourceLayer = "data",
        color = colorLine,
        width = interpolate(
            type = linear(),
            input = zoom(),
            3 to const(0.4.dp),
            5 to const(0.7.dp),
            7 to const(1.0.dp),
            9 to const(2.0.dp),
            11 to const(2.5.dp),
        ),
        opacity = line_opacity_intercity,
        minZoom = 2f,
        visible = intercitySettings.shapes,
        filter = all(
            any(get("route_type").cast<NumberValue<EquatableValue>>().eq(const(2))),
        )
    )


// labelshapes
    SymbolLayer(
        id = LayersPerCategory.IntercityRail.LabelShapes,
        source = intercityRailSource,
        sourceLayer = "data",
        placement = const(SymbolPlacement.Line),
        textField = get("route_label").cast<StringValue>(), // your JS toggles debug; you can replicate if needed
        textFont = step(
            input = zoom(), const(listOf("Barlow-Semibold")), 7.0 to const(listOf("Barlow-Bold"))
        ),
        textSize = interpolate(
            type = linear(), input = zoom(), 3 to const(0.375f).em,  // 6px
            6 to const(0.4375f).em, // 7px
            9 to const(0.5625f).em, // 9px
            13 to const(0.6875f).em  // 11px
        ),
        textIgnorePlacement = const(false),
        textAllowOverlap = const(false),
        textColor = colorText,
        textHaloColor = colorLine,
        textHaloWidth = const(1.dp),
        textHaloBlur = const(1.dp),
        minZoom = 5.5f,
        visible = intercitySettings.labelshapes,
        filter = all(
            any(get("route_type").cast<NumberValue<EquatableValue>>().eq(const(2))),
        )
    )


    /* =========================
       METRO (localcityrailshapes, route_type 1 or 12)
       ========================= */

// shapes


    LineLayer(
        id = LayersPerCategory.Metro.Shapes,
        source = localCityRailSource,
        sourceLayer = "data",
        color = colorLine,
        width = interpolate(
            type = linear(),
            input = zoom(),
            6 to const(0.5.dp),
            7 to const(1.0.dp),
            9 to const(2.0.dp),
        ),
        opacity = const(1f),
        minZoom = 5f,
        visible = localRailSettings.shapes,
        filter = all(
            any(
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(1)),
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(12))
            ),

            all(
                const("nyct").eq(get("chateau").cast()),
                const(true).eq(get("stop_to_stop_generated").cast())
            ).not()
        )
    )

// labelshapes
    SymbolLayer(
        id = LayersPerCategory.Metro.LabelShapes,
        source = localCityRailSource,
        sourceLayer = "data",
        placement = const(SymbolPlacement.Line),
        textField = coalesce(get("route_label").cast(), const("")),
        textFont = const(listOf("Barlow-Bold")),
        textSize = interpolate(
            type = linear(), input = zoom(), 3 to const(0.4375f).em, // 7px
            9 to const(0.5625f).em, // 9px
            13 to const(0.6875f).em  // 11px
        ),
        textIgnorePlacement = const(false),
        textAllowOverlap = const(false),
        textPitchAlignment = const(TextPitchAlignment.Viewport),
        // text color: if color == '000000' use white else '#'+text_color
        textColor = colorText,
        textHaloColor = colorLine,
        textHaloWidth = const(1.dp),
        textHaloBlur = const(1.dp),
        minZoom = 6f,
        visible = localRailSettings.labelshapes,
        filter = all(
            any(
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(1)),
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(12))
            )
        )
    )


    /* =========================
       TRAM (localcityrailshapes, route_type 0 or 5)
       ========================= */

// shapes

    val tram_filter: Expression<BooleanValue> = all(
        any(
            const(0).eq(get("route_type").cast()), const(5).eq(get("route_type").cast())
        ),


        all(
            const("nyct").eq(get("chateau").cast()),
            const(true).eq(get("stop_to_stop_generated").cast())
        ).not(),


        )


    LineLayer(
        id = LayersPerCategory.Tram.Shapes,
        source = localCityRailSource,
        sourceLayer = "data",
        color = colorLine,
        width = interpolate(
            type = linear(),
            input = zoom(),
            6 to const(0.5.dp),
            7 to const(1.0.dp),
            9 to const(2.0.dp),
        ),
        opacity = const(1f),
        minZoom = 5f,
        visible = localRailSettings.shapes,
        filter = tram_filter
    )


// labelshapes
    SymbolLayer(
        id = LayersPerCategory.Tram.LabelShapes,
        source = localCityRailSource,
        sourceLayer = "data",
        placement = const(SymbolPlacement.Line),
        textField = coalesce(get("route_label").cast(), const("")),
        textFont = step(
            input = zoom(), const(listOf("Barlow-Regular")), 12.0 to const(listOf("Barlow-Medium"))
        ),
        textSize = interpolate(
            type = linear(), input = zoom(), 3 to const(0.4375f).em, // 7px
            9 to const(0.5625f).em, // 9px
            13 to const(0.6875f).em  // 11px
        ),
        textIgnorePlacement = const(false),
        textAllowOverlap = const(false),
        textPitchAlignment = const(TextPitchAlignment.Viewport),
        textColor = colorText,
        textHaloColor = colorLine,
        textHaloWidth = const(1.dp),
        textHaloBlur = const(1.dp),
        minZoom = 6f,
        visible = localRailSettings.labelshapes,
        filter = tram_filter
    )


}

@Composable
fun LayerToggleButton(
    name: String, icon: @Composable () -> Unit, isActive: Boolean, onToggle: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onToggle() }
                .padding(8.dp)) {
            icon()

        }

        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
    onFocusChange: (Boolean) -> Unit = {}
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
                Text("Search here", fontSize = 3.5.em, overflow = TextOverflow.Visible)
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
            })
    }


}

