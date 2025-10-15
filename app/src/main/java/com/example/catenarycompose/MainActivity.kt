package com.example.catenarycompose

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOutCirc
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.maplibre.compose.layers.*
import org.maplibre.compose.expressions.dsl.*

data class VectorSrc(val id: String, val url: String)

val SHAPES_SOURCES = listOf(
    VectorSrc("intercityrailshapes", "https://birch1.catenarymaps.org/shapes_intercity_rail"),
    VectorSrc("localcityrailshapes", "https://birch2.catenarymaps.org/shapes_local_rail"),
    VectorSrc("othershapes", "https://birch3.catenarymaps.org/shapes_ferry"),
    VectorSrc("busshapes", "https://birch4.catenarymaps.org/shapes_bus"),
)

val STOP_SOURCES = listOf(
    VectorSrc("busstops", "https://birch6.catenarymaps.org/busstops"),
    VectorSrc("stationfeatures", "https://birch7.catenarymaps.org/station_features"),
    VectorSrc("railstops", "https://birch5.catenarymaps.org/railstops"),
    VectorSrc("otherstops", "https://birch8.catenarymaps.org/otherstops"),
)

private const val TAG = "CatenaryDebug"
var visibleChateaus: List<String> = emptyList()

val easeOutSpec: AnimationSpec<Float> = tween(
    durationMillis = 300,
    delayMillis = 0,
    easing = EaseOutCirc
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


                    }

                    // Sheet width differs in landscape
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
                                    y = draggableState.requireOffset().roundToInt()
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

@Composable
private fun isWideScreen(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp >= 768
}

