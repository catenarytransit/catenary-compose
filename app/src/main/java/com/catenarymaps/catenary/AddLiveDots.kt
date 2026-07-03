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
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.neq
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.EquatableValue
import org.maplibre.compose.expressions.value.FloatValue
import org.maplibre.compose.expressions.value.NumberValue
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

        is LayersPerCategory.TrajectoryBus -> listOf(
            LayersPerCategory.TrajectoryBus.Livedots,
            LayersPerCategory.TrajectoryBus.Labeldots,
            LayersPerCategory.TrajectoryBus.Pointing,
            LayersPerCategory.TrajectoryBus.PointingShell
        )

        is LayersPerCategory.Metro -> listOf(
            LayersPerCategory.Metro.Livedots,
            LayersPerCategory.Metro.Labeldots,
            LayersPerCategory.Metro.Pointing,
            LayersPerCategory.Metro.PointingShell
        )

        is LayersPerCategory.TrajectoryMetro -> listOf(
            LayersPerCategory.TrajectoryMetro.Livedots,
            LayersPerCategory.TrajectoryMetro.Labeldots,
            LayersPerCategory.TrajectoryMetro.Pointing,
            LayersPerCategory.TrajectoryMetro.PointingShell
        )

        is LayersPerCategory.Tram -> listOf(
            LayersPerCategory.Tram.Livedots,
            LayersPerCategory.Tram.Labeldots,
            LayersPerCategory.Tram.Pointing,
            LayersPerCategory.Tram.PointingShell
        )

        is LayersPerCategory.TrajectoryTram -> listOf(
            LayersPerCategory.TrajectoryTram.Livedots,
            LayersPerCategory.TrajectoryTram.Labeldots,
            LayersPerCategory.TrajectoryTram.Pointing,
            LayersPerCategory.TrajectoryTram.PointingShell
        )

        is LayersPerCategory.IntercityRail -> listOf(
            LayersPerCategory.IntercityRail.Livedots,
            LayersPerCategory.IntercityRail.Labeldots,
            LayersPerCategory.IntercityRail.Pointing,
            LayersPerCategory.IntercityRail.PointingShell
        )

        is LayersPerCategory.TrajectoryIntercityRail -> listOf(
            LayersPerCategory.TrajectoryIntercityRail.Livedots,
            LayersPerCategory.TrajectoryIntercityRail.Labeldots,
            LayersPerCategory.TrajectoryIntercityRail.Pointing,
            LayersPerCategory.TrajectoryIntercityRail.PointingShell
        )

        is LayersPerCategory.Other -> listOf(
            LayersPerCategory.Other.Livedots,
            LayersPerCategory.Other.Labeldots,
            LayersPerCategory.Other.Pointing,
            LayersPerCategory.Other.PointingShell
        )

        is LayersPerCategory.TrajectoryOther -> listOf(
            LayersPerCategory.TrajectoryOther.Livedots,
            LayersPerCategory.TrajectoryOther.Labeldots,
            LayersPerCategory.TrajectoryOther.Pointing,
            LayersPerCategory.TrajectoryOther.PointingShell
        )

        else -> return // Should not happen
    }

    val contrastColorProp = if (isDark) "contrastdarkmode" else "contrastlightmode"
    val contrastBearingColorProp = if (isDark) "contrastdarkmodebearing" else "contrastlightmode"
    val vehicleColor = get(contrastColorProp).cast<ColorValue>()
    val bearingColor = get(contrastBearingColorProp).cast<ColorValue>()

    val styles = getLiveDotStyle(category, settings, railInFrame, isDark)

    val actualBaseFilter = if (category == "other") {
        all(baseFilter, get("route_type").cast<NumberValue<EquatableValue>>().neq(const(6)))
    } else {
        baseFilter
    }
    val actualBearingFilter = if (category == "other") {
        all(bearingFilter, get("route_type").cast<NumberValue<EquatableValue>>().neq(const(6)))
    } else {
        bearingFilter
    }

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
        filter = actualBaseFilter,
        visible = isVisible,
        minZoom = styles.minLayerDotsZoom
    )

    val isTrajectory = layerIdPrefix is LayersPerCategory.TrajectoryBus ||
        layerIdPrefix is LayersPerCategory.TrajectoryMetro ||
        layerIdPrefix is LayersPerCategory.TrajectoryTram ||
        layerIdPrefix is LayersPerCategory.TrajectoryIntercityRail ||
        layerIdPrefix is LayersPerCategory.TrajectoryOther

    // Bearing Pointer Shell (Outline)
    if (!isTrajectory) {
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
        filter = actualBearingFilter,
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
        filter = actualBearingFilter,
        visible = isVisible,
        minZoom = styles.minBearingZoom
    )
    }

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
        filter = actualBaseFilter,
        visible = isVisible,
        textAnchor = const(SymbolAnchor.Left),
        textJustify = const(TextJustify.Left),
        minZoom = styles.minLabelDotsZoom
    )

    if (category == "other") {
        val tramStyles = getLiveDotStyle("tram", settings, railInFrame, isDark)
        val aerialBaseFilter = all(baseFilter, get("route_type").cast<NumberValue<EquatableValue>>().eq(const(6)))
        val aerialBearingFilter = all(bearingFilter, get("route_type").cast<NumberValue<EquatableValue>>().eq(const(6)))

        CircleLayer(
            id = "${idDots}_aerial",
            source = source,
            color = get("color").cast<ColorValue>(),
            radius = tramStyles.dotRadius,
            strokeColor = if (isDark) const(Color(0xFF2E394B)) else const(Color.White),
            strokeWidth = tramStyles.dotStrokeWidth,
            opacity = tramStyles.dotOpacity,
            strokeOpacity = tramStyles.dotStrokeOpacity,
            filter = aerialBaseFilter,
            visible = isVisible,
            minZoom = tramStyles.minLayerDotsZoom
        )

        if (!isTrajectory) {
            SymbolLayer(
                id = "${idPointingShell}_aerial",
                source = source,
                iconImage = image(painterResource(R.drawable.pointing_shell)),
                iconColor = if (isDark) const(Color(0xFF1E293B)) else const(Color.White),
                iconSize = tramStyles.bearingIconSize,
                iconRotate = get("bearing").cast<FloatValue>(),
                iconRotationAlignment = const(IconRotationAlignment.Map),
                iconAllowOverlap = const(true),
                iconIgnorePlacement = const(true),
                iconOffset = tramStyles.bearingIconOffset,
                iconOpacity = tramStyles.bearingShellOpacity,
                filter = aerialBearingFilter,
                visible = isVisible,
                minZoom = tramStyles.minBearingZoom
            )

            SymbolLayer(
                id = "${idPointing}_aerial",
                source = source,
                iconImage = image(
                    painterResource(R.drawable.pointing50percent),
                    drawAsSdf = true
                ),
                iconOpacity = tramStyles.bearingFilledOpacity,
                iconColor = bearingColor,
                iconSize = tramStyles.bearingIconSize,
                iconRotate = get("bearing").cast<FloatValue>(),
                iconRotationAlignment = const(IconRotationAlignment.Map),
                iconAllowOverlap = const(true),
                iconIgnorePlacement = const(true),
                iconOffset = tramStyles.bearingIconOffset,
                filter = aerialBearingFilter,
                visible = isVisible,
                minZoom = tramStyles.minBearingZoom
            )
        }

        SymbolLayer(
            id = "${idLabels}_aerial",
            source = source,
            textField = interpretLabelsToExpression(settings, usUnits),
            textFont = tramStyles.labelTextFont,
            textSize = tramStyles.labelTextSize,
            textColor = vehicleColor,
            textHaloColor = if (isDark) const(Color(0xFF1E293B)) else const(Color(0xFFEDEDED)),
            textHaloWidth = tramStyles.labelHaloWidth,
            textHaloBlur = const(1.0.dp),
            textRadialOffset = tramStyles.labelRadialOffset,
            textAllowOverlap = const(false),
            textIgnorePlacement = step(
                zoom(),
                const(false),
                tramStyles.labelIgnorePlacementZoom to const(true)
            ),
            textOpacity = tramStyles.labelTextOpacity,
            filter = aerialBaseFilter,
            visible = isVisible,
            textAnchor = const(SymbolAnchor.Left),
            textJustify = const(TextJustify.Left),
            minZoom = tramStyles.minLabelDotsZoom
        )
    }
}

@Composable
fun VehicleContextDotLayers(
    source: GeoJsonSource,
    usUnits: Boolean,
    isDark: Boolean,
    railInFrame: Boolean,
    settings: LabelSettings = LabelSettings(
        route = true,
        trip = true,
        vehicle = true,
        headsign = true,
        occupancy = true,
        delay = true
    ),
    isVisible: Boolean = true,
    idPrefix: String = "vehicle-context"
) {
    val styles = getLiveDotStyle("context", settings, railInFrame, isDark)

    val contrastColorProp = if (isDark) "contrastdarkmode" else "contrastlightmode"
    val contrastBearingColorProp = if (isDark) "contrastdarkmodebearing" else "contrastlightmode"
    val vehicleColor = get(contrastColorProp).cast<ColorValue>()
    val bearingColor = get(contrastBearingColorProp).cast<ColorValue>()
    val baseFilter = const(true)
    val bearingFilter = get("has_bearing").cast<BooleanValue>()

    CircleLayer(
        id = "$idPrefix-livedots",
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

    SymbolLayer(
        id = "$idPrefix-pointingshell",
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

    SymbolLayer(
        id = "$idPrefix-pointing",
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

    SymbolLayer(
        id = "$idPrefix-labeldots",
        source = source,
        textField = interpretLabelsToExpression(settings, usUnits),
        textFont = styles.labelTextFont,
        textSize = styles.labelTextSize,
        textColor = vehicleColor,
        textHaloColor = if (isDark) const(Color(0xFF1E293B)) else const(Color(0xFFEDEDED)),
        textHaloWidth = styles.labelHaloWidth,
        textHaloBlur = const(1.0.dp),
        textRadialOffset = styles.labelRadialOffset,
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
        textOpacity = styles.labelTextOpacity,
        filter = baseFilter,
        visible = isVisible,
        textAnchor = const(SymbolAnchor.Left),
        textJustify = const(TextJustify.Left),
        minZoom = styles.minLabelDotsZoom
    )
}

