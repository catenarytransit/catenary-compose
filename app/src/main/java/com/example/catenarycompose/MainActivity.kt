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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
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

import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.value.InterpolatableValue
import org.maplibre.compose.expressions.value.InterpolationValue
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.value.TextUnitValue

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
    durationMillis = 300,
    delayMillis = 0,
    easing = EaseOutCirc
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
        rect = rect,
        layerIds = setOf("chateaus_calc")
    )

    val names = features.map { f -> f.properties["chateau"]?.toString() ?: "Unknown" }
    visibleChateaus = names
    Log.d(TAG, "Visible chateaus (${names.size}): ${names.joinToString(limit = 100)}")
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val styleUri =
                if (isSystemInDarkTheme()) "https://maps.catenarymaps.org/dark-style.json"
                else "https://maps.catenarymaps.org/light-style.json"

            CatenaryComposeTheme {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val screenHeightPx = with(density) { maxHeight.toPx() }

                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

                    // State for layers panel visibility
                    var showLayersPanel by remember { mutableStateOf(false) }

                    // Camera
                    val camera = rememberCameraState(
                        firstPosition = CameraPosition(
                            target = Position(-118.250, 34.050),
                            zoom = 6.0
                        )
                    )

                    // Track map size
                    var mapSize by remember { mutableStateOf(IntSize.Zero) }

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
                            )
                        ),
                        zoomRange = 2f..20f,
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
                        }
                    ) {
                        // Source + layers
                        val chateausSource = rememberGeoJsonSource(
                            data = GeoJsonData.Uri("https://birch.catenarymaps.org/getchateaus")
                        )

                        FillLayer(
                            id = "chateaus_calc",
                            source = chateausSource,
                            opacity = const(0.0f)
                        )

                        AddShapes()

                        AddStops()

                    }

                    // Main Draggable Bottom Sheet
                    val sheetModifier = if (isLandscape) {
                        Modifier
                            .fillMaxWidth(0.5f)
                            .align(Alignment.BottomStart)
                    } else {
                        Modifier.fillMaxWidth()
                    }

                    Surface(
                        modifier = sheetModifier
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = draggableState
                                        .requireOffset()
                                        .roundToInt()
                                )
                            }
                            .anchoredDraggable(
                                state = draggableState,
                                orientation = Orientation.Vertical
                            ),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        shadowElevation = 8.dp
                    ) {
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
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )


                            if (CatenaryStack.size == 0) {
                                NearbyDepartures()
                            }
                        }
                    }


                    val layerButtonColor = if (isSystemInDarkTheme()) Color.DarkGray else Color.White
                    val layerButtonContentColor = if (isSystemInDarkTheme()) Color.White else Color.Black

                    var searchQuery by remember { mutableStateOf("") }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter) // center so the search can span full width
                            .windowInsetsPadding(WindowInsets.safeDrawing) // below status bar & cutout

                            .padding(top = 8.dp, start = 4.dp, end = 16.dp), // extra margins
                        horizontalAlignment = Alignment.End
                    ) {



                        SearchBarCatenary(
                            searchQuery = searchQuery,
                            onValueChange = {
                                searchQuery = it
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        // Layers Button (now below the search bar)
                        FloatingActionButton(
                            onClick = { showLayersPanel = !showLayersPanel },
                            modifier = Modifier
                                .width(48.dp)
                                .height(48.dp),
                            shape = CircleShape,
                            containerColor = layerButtonColor,
                            contentColor = layerButtonContentColor
                        ) {
                            Icon(Icons.Filled.Layers, contentDescription = "Toggle Layers", Modifier.width(32.dp))
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
                                        Icon(Icons.Filled.Close, contentDescription = "Close Layers")
                                    }
                                }

                                LayerTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

                                if (selectedTab in listOf("intercityrail", "localrail", "bus", "other")) {
                                    // First row of buttons: shapes/labels/Pairs/etc.
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        // use LayerToggleButton composables here


                                        val currentSettings = layerSettings.value[selectedTab] as? LayerCategorySettings
                                        currentSettings?.let { settings ->
                                            LayerToggleButton(
                                                name = "Shapes",
                                                icon = { Icon(Icons.Default.Route, contentDescription = null) },
                                                isActive = settings.shapes,
                                                onToggle = {
                                                    val updated = settings.copy(shapes = !settings.shapes)
                                                    layerSettings.value = layerSettings.value.toMutableMap().apply { put(selectedTab, updated) }
                                                }
                                            )

                                            LayerToggleButton(
                                                name = "Shape Labels",
                                                icon = { Icon(Icons.Default.Route, contentDescription = null) },
                                                isActive = settings.labelshapes,
                                                onToggle = {
                                                    val updated = settings.copy(labelshapes = !settings.labelshapes)
                                                    layerSettings.value = layerSettings.value.toMutableMap().apply { put(selectedTab, updated) }
                                                }
                                            )
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
    selectedTab: String,
    onTabSelected: (String) -> Unit
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
                    .clickable { onTabSelected(tab) }
            ) {
                Text(
                    text = tab.replaceFirstChar { it.uppercase() },
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun NearbyDepartures(

) {
    // Nearby Departures goes here
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
            type = linear(),
            input = zoom(),
            11 to const(0.9.dp),
            13 to const(2.dp)
        ),
        strokeOpacity = interpolate(
            type = linear(),
            input = zoom(),
            11 to const(0.2f),
            14 to const(0.5f)
        ),
        visible = (layerSettings.value["bus"] as LayerCategorySettings).stops
    )

    SymbolLayer(
        id = LayersPerCategory.Bus.LabelStops,
        source = busStopsSource,
        sourceLayer = "data",
        minZoom = 15f,
        textSize = interpolate(
            type = linear(),
            input = zoom(),
            14 to const(0.5f).em,
            16 to const(0.7f).em
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
            7  to const(0.5.dp),
            10 to const(0.7.dp),
            12 to const(1.0.dp),
            14 to const(2.6.dp),
        ),
        opacity = interpolate(
            type = linear(),
            input = zoom(),
            7  to const(0.08f),
            8  to const(0.10f),
            11 to const(0.30f),
        ),
        minZoom = 8f,
        visible = bus.shapes
    )

    var busTextSize:  Expression<TextUnitValue> = interpolate(
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
        textAllowOverlap   = const(false),

        textColor     = colorBusLineText,
        textHaloColor = colorBusLine,
        textHaloWidth = const(2.dp),
        textHaloBlur  = const(0.dp),
        minZoom = 11f,
        visible = bus.labelshapes
    )

    // Pull per-category settings
    val otherSettings       = layerSettings.value["other"] as LayerCategorySettings
    val intercitySettings   = layerSettings.value["intercityrail"] as LayerCategorySettings
    val localRailSettings   = layerSettings.value["localrail"] as LayerCategorySettings

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
            7  to const(2.dp),
            9  to const(3.dp),
        ),
        opacity = const(1f),
        minZoom = 1f,
        visible = otherSettings.shapes,
        // filter: ! (chateau=='schweiz' && stop_to_stop_generated==true)  && (route_type==6 || route_type==7)
        filter = all(
            all(
                cast_chateau.eq(const("schweiz")),
                get_stop_to_stop_generated.eq(const(true))
            ).not(),
            any(
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
        textFont  = const(listOf("Barlow-Regular")),
        textSize  = interpolate(
            type = linear(),
            input = zoom(),
            3  to const(0.4375f).em, // 7px
            9  to const(0.5625f).em, // 9px
            13 to const(0.6875f).em  //11px
        ),
        textIgnorePlacement = const(false),
        textAllowOverlap    = const(false),
        textColor     = colorText,
        textHaloColor = colorLine,
        textHaloWidth = const(2.dp),
        textHaloBlur  = const(1.dp),
        minZoom = 3f,
        visible = otherSettings.labelshapes,
        // filter: (route_type in 4,6,7) && !(schweiz && stop_to_stop_generated)
        filter = all(
            any(
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(4)),
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(6)),
                get("route_type").cast<NumberValue<EquatableValue>>().eq(const(7))
            ),
            all(
                get("chateau").cast<StringValue>().eq(const("schweiz")),
                (get("stop_to_stop_generated").cast<BooleanValue>().convertToBoolean()).eq(const(true))
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
            6  to const(0.5.dp),
            7  to const(1.0.dp),
            10 to const(1.5.dp),
            14 to const(3.0.dp),
        ),
        opacity = interpolate(
            type = linear(),
            input = zoom(),
            6 to const(0.8f),
            7 to const(0.9f)
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

    val line_opacity_intercity:  Expression<NumberValue<Number>> = switch(
        input = get("stop_to_stop_generated").cast<BooleanValue>().asString(),
        case(
            label = "true",
            output = const(0.2f)
        ),
        fallback = const(0.9f)
    )

    LineLayer(
        id = LayersPerCategory.IntercityRail.Shapes,
        source = intercityRailSource,
        sourceLayer = "data",
        color = colorLine,
        width = interpolate(
            type = linear(),
            input = zoom(),
            3  to const(0.4.dp),
            5  to const(0.7.dp),
            7  to const(1.0.dp),
            9  to const(2.0.dp),
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
        textFont  = step(
            input = zoom(),
            const(listOf("Barlow-Semibold")),
            7.0 to const(listOf("Barlow-Bold"))
        ),
        textSize = interpolate(
            type = linear(),
            input = zoom(),
            3  to const(0.375f).em,  // 6px
            6  to const(0.4375f).em, // 7px
            9  to const(0.5625f).em, // 9px
            13 to const(0.6875f).em  // 11px
        ),
        textIgnorePlacement = const(false),
        textAllowOverlap    = const(false),
        textColor     = colorText,
        textHaloColor = colorLine,
        textHaloWidth = const(1.dp),
        textHaloBlur  = const(1.dp),
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
            6  to const(0.5.dp),
            7  to const(1.0.dp),
            9  to const(2.0.dp),
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
                const("nyct").eq( get("chateau").cast()),
                const(true).eq(get("stop_to_stop_generated").cast())
            )
                .not()
        )
    )

// labelshapes
    SymbolLayer(
        id = LayersPerCategory.Metro.LabelShapes,
        source = localCityRailSource,
        sourceLayer = "data",
        placement = const(SymbolPlacement.Line),
        textField = coalesce(get("route_label").cast(), const("")),
        textFont  = const(listOf("Barlow-Bold")),
        textSize  = interpolate(
            type = linear(),
            input = zoom(),
            3  to const(0.4375f).em, // 7px
            9  to const(0.5625f).em, // 9px
            13 to const(0.6875f).em  // 11px
        ),
        textIgnorePlacement = const(false),
        textAllowOverlap    = const(false),
        textPitchAlignment  =  const(TextPitchAlignment.Viewport),
        // text color: if color == '000000' use white else '#'+text_color
        textColor = colorText,
        textHaloColor = colorLine,
        textHaloWidth = const(1.dp),
        textHaloBlur  = const(1.dp),
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

    val tram_filter:  Expression<BooleanValue> = all(
        any(
            const(0).eq(get("route_type").cast()),
            const(5).eq(get("route_type").cast())
        ),


        all(
            const("nyct").eq( get("chateau").cast()),
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
            6  to const(0.5.dp),
            7  to const(1.0.dp),
            9  to const(2.0.dp),
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
        textFont  = step(
            input = zoom(),
            const(listOf("Barlow-Regular")),
            12.0 to const(listOf("Barlow-Medium"))
        ),
        textSize  = interpolate(
            type = linear(),
            input = zoom(),
            3  to const(0.4375f).em, // 7px
            9  to const(0.5625f).em, // 9px
            13 to const(0.6875f).em  // 11px
        ),
        textIgnorePlacement = const(false),
        textAllowOverlap    = const(false),
        textPitchAlignment  = const(TextPitchAlignment.Viewport),
        textColor     = colorText,
        textHaloColor = colorLine,
        textHaloWidth = const(1.dp),
        textHaloBlur  = const(1.dp),
        minZoom = 6f,
        visible = localRailSettings.labelshapes,
        filter =  tram_filter
    )


}
@Composable
fun LayerToggleButton(
    name: String,
    icon: @Composable () -> Unit,
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
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
            .padding(8.dp)
    )
    {
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
        searchQuery = searchQuery,
        onValueChange = { searchQuery = it }
    )
}


@Composable
fun SearchBarCatenary(searchQuery: String = "",
                      onValueChange: (String) -> Unit = {}) {
    // This box just wraps the background and the OutlinedTextField
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Transparent,
                shape = RoundedCornerShape(100.dp)

            )

    ) {
        // This box works as background
        Box(

            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize() // adding some space to the label
                .padding(vertical = 0.dp)
                .shadow(4.dp, shape = RoundedCornerShape(100.dp))
        )
        TextField(
            value = searchQuery,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 3.5.em,
                baselineShift = BaselineShift(1f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(color = Color.Transparent)
                .padding(vertical = 0.dp, horizontal = 0.dp),
            shape = RoundedCornerShape(100.dp),
            placeholder = { Text("Search here",
                fontSize = 3.5.em,
            ) }
        )
    }


}