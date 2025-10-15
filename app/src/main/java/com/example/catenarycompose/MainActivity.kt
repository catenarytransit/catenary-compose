package com.example.catenarycompose

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
// CHANGE: Add new imports for animation specs
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.EaseOut
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect // NEW
import androidx.compose.runtime.getValue // NEW
import androidx.compose.runtime.mutableStateOf // NEW
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue // NEW
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset // NEW
import androidx.compose.ui.geometry.Rect // NEW
import androidx.compose.ui.layout.onSizeChanged // NEW
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize // NEW
import androidx.compose.ui.unit.dp
import com.example.catenarycompose.ui.theme.CatenaryComposeTheme
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import kotlin.math.roundToInt
import androidx.compose.animation.core.Spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutCirc
// CHANGE: Import for configuration awareness
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpRect
import io.github.dellisd.spatialk.geojson.Position
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.compose.layers.FillExtrusionLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.sources.rememberGeoJsonSource
// NEW: camera APIs to read the current projection/viewport
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
// NEW: coroutines delay for 1s ticker
import kotlinx.coroutines.delay
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.expressions.dsl.const
import android.content.res.Resources

private const val TAG = "CatenaryDebug"

val easeOutSpec: AnimationSpec<Float> = tween(
    durationMillis = 300, // Specify the duration of the animation
    delayMillis = 0,       // Specify any delay before starting
    easing = EaseOutCirc     // Pass the desired ease-out curve
)

enum class SheetSnapPoint {
    Collapsed,
    PartiallyExpanded,
    Expanded
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

                    // CHANGE: Get screen configuration to determine orientation
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    val anchors = DraggableAnchors<SheetSnapPoint> {
                        SheetSnapPoint.Collapsed at screenHeightPx - with(density) { 64.dp.toPx() }
                        SheetSnapPoint.PartiallyExpanded at screenHeightPx / 2f
                        SheetSnapPoint.Expanded at with(density) { 60.dp.toPx() }
                    }

                    val draggableState: AnchoredDraggableState<SheetSnapPoint> = remember {
                        AnchoredDraggableState<SheetSnapPoint>(
                            initialValue = SheetSnapPoint.Collapsed,
                            anchors = anchors,
                            positionalThreshold = { with(density) { 128.dp.toPx() } },
                            velocityThreshold = { with(density) { 128.dp.toPx() } },
                            // FIX: Provide the required animation spec parameters
                            snapAnimationSpec = easeOutSpec,
                            decayAnimationSpec = splineBasedDecay(density),
                        )
                    }

                    // NEW: camera state so we can query visible features
                    val camera = rememberCameraState(
                        firstPosition = CameraPosition(
                            target = Position(-118.250,34.050), // any default
                            zoom = 6.0
                        )
                    )

                    // NEW: Track the size of the map composable in pixels
                    var mapSize by remember { mutableStateOf(IntSize.Zero) }

                    // Map
                    MaplibreMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { mapSize = it }, // NEW
                        baseStyle = BaseStyle.Uri(styleUri),
                        options = MapOptions(
                            ornamentOptions = OrnamentOptions(
                                isLogoEnabled = false,
                                isAttributionEnabled = true,
                                isCompassEnabled = false,
                                isScaleBarEnabled = false,
                            )
                        ),
                        cameraState = camera // NEW
                    ) {

                        // START: Added GeoJSON source and layer
                        val chateausSource = rememberGeoJsonSource(
                            data = GeoJsonData.Uri("https://birch.catenarymaps.org/getchateaus")
                        )

                        println(chateausSource.toString());


                        FillLayer(
                            id = "chateaus_calc",
                            source = chateausSource,
                            opacity = const(0.0f)
                        )

                        LineLayer(
                            id = "chateaus_calc_line",
                            source = chateausSource,
                            color = const(Color.White),
                            width = const(0.dp),
                        )

                        // END: Added GeoJSON source and layer
                    }


                    // NEW: Poll visible chateaus once/second and print to the console
                    LaunchedEffect(camera, mapSize) {
                        // Wait until we have a non-zero size and a projection
                        while (true) {
                            delay(1000)
                            val projection = camera.projection
                            if (projection == null || mapSize.width == 0 || mapSize.height == 0) continue

                            println("height ${mapSize.height} width ${mapSize.width} density ${Resources.getSystem().displayMetrics.density}")


                            // Build a screen-space rect covering the whole map composable
                            val rect: DpRect = DpRect(
                                left = 0.dp,
                                top = 0.dp,
                                right = (mapSize.width / Resources.getSystem().displayMetrics.density).dp,
                                bottom = (mapSize.height / Resources.getSystem().displayMetrics.density).dp,
                            )

                            // Query rendered features in our chateaus layer within the rect
                            // NOTE: projection.queryRenderedFeatures(rect, layerIds = listOf(...)) is provided by MapLibre Compose. :contentReference[oaicite:1]{index=1}
                            val features = projection.queryRenderedFeatures(
                                rect = rect,
                                layerIds = listOf("chateaus_calc").toSet()
                            )

                            println(features.size)

                            // Try to extract chateau name
                            val names = features.map { f ->
                                val name = f.properties["chateau"]?.toString()
                                name ?: "Unknown"
                            }

                            val msg = "Visible chateaus (${names.size}): ${names.joinToString(limit = 100)}"
                            Log.d(TAG, msg)
                            //println(msg)
                        }
                    }

                    // CHANGE: Conditionally define the modifier based on orientation
                    val sheetModifier = if (isLandscape) {
                        Modifier
                            .fillMaxWidth(0.5f)
                            .align(Alignment.BottomStart)
                    } else {
                        Modifier.fillMaxWidth()
                    }

                    Surface(
                        modifier = sheetModifier // CHANGE: Apply the conditional modifier
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
                            Text("This is the bottom sheet!")
                        }
                    }
                }
            }
        }
    }
}
