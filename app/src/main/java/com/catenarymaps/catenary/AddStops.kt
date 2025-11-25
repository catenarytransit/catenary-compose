package com.catenarymaps.catenary

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.Feature.has
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.condition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.contains
import org.maplibre.compose.expressions.dsl.em
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.EquatableValue
import org.maplibre.compose.expressions.value.NumberValue
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.expressions.value.TextJustify
import org.maplibre.compose.expressions.value.VectorValue
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.rememberVectorSource
import org.maplibre.compose.expressions.dsl.contains as dslcontains


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
    val stationFeaturesSource = rememberVectorSource(uri = STOP_SOURCES.getValue("stationfeatures"))

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

        textJustify = const(TextJustify.Left),
        textAnchor = const(SymbolAnchor.Left),
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

        textJustify = const(TextJustify.Left),
        textAnchor = const(SymbolAnchor.Left),
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
            fallback = get("displayname").cast<StringValue>(),
            8 to get("displayname").cast<StringValue>(),
            13 to get("displayname").cast<StringValue>() +
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
            14 to const(0.75f).em,    // 12px
            17 to const(0.875f).em    // 14px
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
        textJustify = const(TextJustify.Left),
        textAnchor = const(SymbolAnchor.Left),
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
            fallback = get("displayname").cast<StringValue>(),
            8 to get("displayname").cast<StringValue>(),
            14 to get("displayname").cast<StringValue>() +
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
            14 to const(0.625f).em,   // 10px
            17 to const(0.75f).em,
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
        textJustify = const(TextJustify.Left),
        textAnchor = const(SymbolAnchor.Left),
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
            fallback = get("displayname").cast<StringValue>(),
            8 to get("displayname").cast<StringValue>(),
            13 to get("displayname").cast<StringValue>() +
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
        textJustify = const(TextJustify.Left),
        textAnchor = const(SymbolAnchor.Left),
        filter = isIntercity,
        visible = (layerSettings.intercityrail as LayerCategorySettings).labelstops
    )


    // STATION FEATURES

    SymbolLayer(
        id = "stationenter",
        source = stationFeaturesSource,
        sourceLayer = "data",
        iconImage = image(painterResource(R.drawable.station_enter)),
        iconSize = interpolate(
            type = linear(),
            input = zoom(),
            15 to const(0.1f),
            18 to const(0.2f)
        ),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        minZoom = 15.0F
    )

    SymbolLayer(
        id = "stationentertxt",
        source = stationFeaturesSource,
        sourceLayer = "data",
        textField = get("name").cast<StringValue>(),
        textColor = if (isDark) const(Color(0xFFBAE6FD)) else const(Color(0xFF1D4ED8)),
        textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color(0xFFFFFFFF)),
        textHaloWidth = if (isDark) const(0.4.dp) else const(0.2.dp),
        textSize = interpolate(
            type = linear(),
            input = zoom(),
            16 to const(0.4f).em,
            18 to const(0.8f).em
        ),
        textOffset = offset(1.em, 0.em),
        textJustify = const(TextJustify.Left),
        textAnchor = const(SymbolAnchor.Left),
        textFont = barlowBold,
        minZoom = 17F,
    )


}