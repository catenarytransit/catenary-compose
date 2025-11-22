package com.catenarymaps.catenary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.FloatValue
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.expressions.value.TextJustify
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.FeatureCollection


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
fun LiveDotLayers(
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

    val styles = getLiveDotStyle(category, settings, railInFrame, isDark)

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
        textHaloWidth = styles.labelHaloWidth,
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