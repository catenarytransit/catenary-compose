package com.example.catenarycompose

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// CHANGE: Add new imports for animation specs
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.EaseOut
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
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
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.style.BaseStyle

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
            val styleUri = if (isSystemInDarkTheme()) "https://maps.catenarymaps.org/dark-style.json" else "https://maps.catenarymaps.org/light-style.json"

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

                    MaplibreMap(
                        modifier = Modifier.fillMaxSize(),
                        baseStyle = BaseStyle.Uri(styleUri),
                        options = MapOptions (
                            ornamentOptions = OrnamentOptions(
                                isLogoEnabled = false,
                                isAttributionEnabled = true,
                                isCompassEnabled = false,
                                isScaleBarEnabled = false,
                            )
                        )
                    )

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