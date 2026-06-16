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
import org.maplibre.compose.expressions.dsl.contains as dslcontains
import org.maplibre.compose.expressions.dsl.contains
import org.maplibre.compose.expressions.dsl.em
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.neq
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.EquatableValue
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.expressions.value.TextJustify
import org.maplibre.compose.expressions.value.VectorValue
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.NumberValue
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.rememberVectorSource

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
        val osmStopsSource = rememberVectorSource(uri = STOP_SOURCES.getValue("osmstations"))
        val osmStopsRankedSource = rememberVectorSource(uri = STOP_SOURCES.getValue("osmstationsranked"))
        val stationFeaturesSource =
                rememberVectorSource(uri = STOP_SOURCES.getValue("stationfeatures"))

        // Fonts
        val barlowRegular = const(listOf("Arimo-Regular"))
        val barlowMedium = const(listOf("Arimo-Medium"))
        val barlowBold = const(listOf("Arimo-Bold"))
        val barlowSemiBold = const(listOf("Arimo-SemiBold"))

        // Colors (inside/outside dot)
        val circleInside = if (isDark) Color(0xFF1C2636) else Color(0xFFFFFFFF)
        val circleOutside = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1C2636)

        val ranked3456Inside = if (isDark) Color(0xFFDDDDDD) else Color(0xFF666767)
        val ranked3456Outside = if (isDark) Color(0xFF1C2636) else Color(0xFFFFFFFF)
        val ranked12Inside = circleInside
        val ranked12Outside = if (isDark) Color(0xFFDDDDDD) else Color(0xFF666767)

        val osmSubwayLabelTextColor: Expression<ColorValue> =
                if (isDark) {
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                13 to const(Color(0xFFAAAAAA)),
                                15 to const(Color(0xFFFFFFFF))
                        )
                } else {
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                13 to const(Color(0xFF555555)),
                                15 to const(Color(0xFF000000))
                        )
                }

        val osmTramLabelTextColor: Expression<ColorValue> =
                if (isDark) {
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                14 to const(Color(0xFFAAAAAA)),
                                16 to const(Color(0xFFFFFFFF))
                        )
                } else {
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                14 to const(Color(0xFF555555)),
                                16 to const(Color(0xFF000000))
                        )
                }

        val osmSubwayCircleSize =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        5 to const(0.8.dp),
                        8 to const(1.2.dp),
                        12 to const(2.8.dp),
                        15 to const(4.8.dp)
                )

        val osmTramCircleSize =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        5 to const(0.6.dp),
                        8 to const(1.dp),
                        12 to const(2.dp),
                        15 to const(4.dp)
                )

        val ranked12CircleSize =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        3 to const(1.5.dp),
                        6 to const(2.dp),
                        8 to const(3.dp),
                        12 to const(5.5.dp),
                        15 to const(8.dp)
                )

        val ranked3CircleSize =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        5 to const(1.2.dp),
                        8 to const(2.2.dp),
                        12 to const(4.2.dp),
                        15 to const(7.dp)
                )

        val ranked456CircleSize =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        5 to const(1.dp),
                        8 to const(1.8.dp),
                        12 to const(3.5.dp),
                        15 to const(6.dp)
                )

        val ranked12LabelSize =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        6 to const(0.75f).em, // 12px
                        13 to const(1.0625f).em // 17px
                )

        val ranked3456LabelSize =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        8 to const(0.6875f).em, // 11px
                        13 to const(0.9375f).em // 15px
                )

        // JS: bus_stop_stop_color(darkMode) -> step(zoom, ...)
        val busStrokeColorExpr: Expression<ColorValue> =
                if (isDark) {
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
                (any(get("route_types").cast<VectorValue<EquatableValue>>().dslcontains(const(v))))
        }

        val childrenRtEq = { v: Int ->
                (any(
                        get("children_route_types")
                                .cast<VectorValue<EquatableValue>>()
                                .dslcontains(const(v))
                ))
        }

        val isMetro =
                any(
                        any(
                                (rtEq(1)),
                                childrenRtEq(1),
                        ),
                        has("osm_station_id").not(),
                        (rtEq(12))
                )
        val isTram =
                all(
                        any(any(rtEq(0), childrenRtEq(0)), rtEq(5)),
                        isMetro().not(),
                        has("osm_station_id").not()
                )
        val isIntercity = rtEq(2)

        // String pieces for label fields
        val semi = const("; ")
        val empty = const("")
        val level: Expression<StringValue> = coalesce(get("level_id").cast<StringValue>(), empty)
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
                radius =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                11 to const(0.9.dp),
                                12 to const(1.2.dp),
                                13 to const(2.dp)
                        ),
                strokeColor = busStrokeColorExpr,
                strokeWidth =
                        step(
                                input = zoom(),
                                const(0.8.dp),
                                12.0 to const(1.2.dp),
                                13.2 to const(1.5.dp)
                        ),
                strokeOpacity = step(input = zoom(), const(0.5f), 15.0 to const(0.6f)),
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
                textSize =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                13 to const(0.4375f).em, // 7px
                                15 to const(0.5f).em, // 8px
                                16 to const(0.625f).em // 10px
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
                radius =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                8 to const(1.dp),
                                12 to const(4.dp),
                                15 to const(5.dp)
                        ),
                strokeColor = const(circleOutside),
                strokeWidth = step(input = zoom(), const(1.2.dp), 13.2 to const(1.5.dp)),
                strokeOpacity = step(input = zoom(), const(0.5f), 15.0 to const(0.6f)),
                opacity =
                        interpolate(
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
                textSize =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                9 to const(0.375f).em, // 6px
                                15 to const(0.5625f).em, // 9px
                                17 to const(0.625f).em // 10px
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
                radius =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                8 to const(0.8.dp),
                                12 to const(3.5.dp),
                                15 to const(5.dp)
                        ),
                strokeColor = const(circleOutside),
                strokeWidth =
                        step(
                                input = zoom(),
                                const(0.4.dp),
                                10.5 to const(0.8.dp),
                                11.0 to const(1.2.dp),
                                13.2 to const(1.5.dp)
                        ),
                strokeOpacity = step(input = zoom(), const(0.5f), 15.0 to const(0.6f)),
                opacity =
                        interpolate(
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
                textField =
                        step(
                                input = zoom(),
                                fallback = get("displayname").cast<StringValue>(),
                                8 to get("displayname").cast<StringValue>(),
                                13 to
                                        get("displayname").cast<StringValue>() +
                                                switch(
                                                        conditions =
                                                                arrayOf(
                                                                        condition(
                                                                                has("level_id"),
                                                                                semi + level
                                                                        )
                                                                ),
                                                        fallback = const("")
                                                ) +
                                                switch(
                                                        conditions =
                                                                arrayOf(
                                                                        condition(
                                                                                has(
                                                                                        "platform_code"
                                                                                ),
                                                                                semi + platform
                                                                        )
                                                                ),
                                                        fallback = const("")
                                                )
                        ),
                textSize =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                11 to const(0.5f).em, // 8px
                                12 to const(0.625f).em, // 10px
                                14 to const(0.75f).em, // 12px
                                17 to const(0.875f).em // 14px
                        ),
                // radial: 7->0.1, 10->0.3, 12->0.6 (maplibre uses em); animate y offset
                textOffset =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                7 to offset(0.em, 0.10.em),
                                10 to offset(0.em, 0.30.em),
                                12 to offset(0.em, 0.60.em)
                        ),
                textFont = step(input = zoom(), barlowRegular, 12.0 to barlowMedium),
                textColor = if (isDark) const(Color.White) else const(Color(0xFF2A2A2A)),
                textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color.White),
                textHaloWidth = const(1.dp), // was 1.dp
                minZoom = 12f,
                filter = isMetro,
                textJustify = const(TextJustify.Left),
                textAnchor = const(SymbolAnchor.Left),
                visible = (layerSettings.localrail as LayerCategorySettings).labelstops
        )

        CircleLayer(
                id = LayersPerCategory.Metro.Stops + "_osm",
                source = osmStopsRankedSource,
                sourceLayer = "data",
                color = const(ranked3456Inside),
                radius = osmSubwayCircleSize,
                strokeColor = const(ranked3456Outside),
                strokeWidth =
                        step(
                                input = zoom(),
                                const(1.dp),
                                11.0 to const(1.8.dp),
                                12.0 to const(3.0.dp)
                        ),
                strokeOpacity = const(1f),
                opacity = const(1f),
                minZoom = 10.5f,
                filter =
                        all(
                                has("local_ref").not(),
                                get("station_type").cast<StringValue>().eq(const("station")),
                                get("mode_type").cast<StringValue>().eq(const("subway")),
                                get("number_of_associated_stops").cast<NumberValue<EquatableValue>>().neq(const(0))
                        ),
                visible = (layerSettings.localrail as LayerCategorySettings).stops
        )

        SymbolLayer(
                id = LayersPerCategory.Metro.LabelStops + "_osm",
                source = osmStopsRankedSource,
                sourceLayer = "data",
                textField = get("name").cast(),
                textSize =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                11 to const(0.5f).em, // 8px
                                12 to const(0.625f).em, // 10px
                                14 to const(0.75f).em, // 12px
                                16 to const(0.875f).em // 14px
                        ),
                textOffset =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                7 to offset(0.em, 0.10.em),
                                10 to offset(0.em, 0.30.em),
                                12 to offset(0.em, 0.60.em)
                        ),
                textFont = step(
                        input = zoom(),
                        barlowRegular,
                        12.0 to barlowMedium,
                        15.0 to barlowSemiBold
                ),
                textColor = osmSubwayLabelTextColor,
                textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color.White),
                textHaloWidth = const(1.dp),
                minZoom = 13f,
                filter =
                        all(
                                get("station_type").cast<StringValue>().eq(const("station")),
                                get("mode_type").cast<StringValue>().eq(const("subway")),
                                get("number_of_associated_stops").cast<NumberValue<EquatableValue>>().neq(const(0))
                        ),
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
                radius =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                9 to const(0.9.dp),
                                10 to const(1.dp),
                                12 to const(3.dp),
                                15 to const(4.dp)
                        ),
                strokeColor = const(circleOutside),
                strokeWidth = step(input = zoom(), const(1.2.dp), 13.2 to const(1.5.dp)),
                strokeOpacity =
                        step(input = zoom(), const(0.4f), 11.0 to const(0.5f), 15.0 to const(0.6f)),
                opacity = const(0.8f),
                minZoom = 9f,
                filter = isTram,
                visible = (layerSettings.localrail as LayerCategorySettings).stops
        )

        SymbolLayer(
                id = LayersPerCategory.Tram.LabelStops,
                source = railStopsSource,
                sourceLayer = "data",
                textField =
                        step(
                                input = zoom(),
                                fallback = get("displayname").cast<StringValue>(),
                                8 to get("displayname").cast<StringValue>(),
                                14 to
                                        get("displayname").cast<StringValue>() +
                                                switch(
                                                        conditions =
                                                                arrayOf(
                                                                        condition(
                                                                                has("level_id"),
                                                                                semi + level
                                                                        )
                                                                ),
                                                        fallback = const("")
                                                ) +
                                                switch(
                                                        conditions =
                                                                arrayOf(
                                                                        condition(
                                                                                has(
                                                                                        "platform_code"
                                                                                ),
                                                                                semi + platform
                                                                        )
                                                                ),
                                                        fallback = const("")
                                                )
                        ),
                textSize =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                9 to const(0.4375f).em, // 7px
                                11 to const(0.4375f).em, // 7px
                                12 to const(0.5625f).em, // 9px
                                14 to const(0.625f).em, // 10px
                                17 to const(0.75f).em,
                        ),
                // radial: 7->0.2, 10->0.3, 12->0.5
                textOffset =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                7 to offset(0.em, 0.2.em),
                                10 to offset(0.em, 0.3.em),
                                12 to offset(0.em, 0.5.em)
                        ),
                textFont = step(input = zoom(), barlowRegular, 12.0 to barlowMedium),
                textColor = if (isDark) const(Color.White) else const(Color(0xFF2A2A2A)),
                textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color(0xFFFFFFFF)),
                textHaloWidth = const(1.dp), // was 1.dp
                minZoom = 14f,
                filter = isTram,
                textJustify = const(TextJustify.Left),
                textAnchor = const(SymbolAnchor.Left),
                visible = (layerSettings.localrail as LayerCategorySettings).labelstops
        )

        CircleLayer(
                id = LayersPerCategory.Tram.Stops + "_osm",
                source = osmStopsRankedSource,
                sourceLayer = "data",
                color = const(ranked3456Inside),
                radius = osmTramCircleSize,
                strokeColor = const(ranked3456Outside),
                strokeWidth = step(input = zoom(), const(1.8.dp), 12.0 to const(3.0.dp)),
                strokeOpacity = const(1f),
                opacity = const(1f),
                minZoom = 12f,
                filter =
                        all(
                                has("local_ref").not(),
                                has("parent_osm_id").not(),
                                any(
                                        get("station_type").cast<StringValue>().eq(const("station")),
                                        get("station_type").cast<StringValue>().eq(const("tram_stop")),
                                        get("station_type").cast<StringValue>().eq(const("halt"))
                                ),
                                get("number_of_associated_stops").cast<NumberValue<EquatableValue>>().neq(const(0)),
                                any(
                                        get("mode_type").cast<StringValue>().eq(const("tram")),
                                        get("mode_type").cast<StringValue>().eq(const("light_rail"))
                                )
                        ),
                visible = (layerSettings.localrail as LayerCategorySettings).stops
        )

        SymbolLayer(
                id = LayersPerCategory.Tram.LabelStops + "_osm",
                source = osmStopsRankedSource,
                sourceLayer = "data",
                textField = get("name").cast(),
                textSize =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                9 to const(0.4375f).em, // 7px
                                11 to const(0.4375f).em, // 7px
                                12 to const(0.5625f).em, // 9px
                                14 to const(0.625f).em, // 10px
                                16 to const(0.75f).em, // 12px
                                18 to const(0.875f).em // 14px
                        ),
                textOffset =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                7 to offset(0.em, 0.2.em),
                                10 to offset(0.em, 0.3.em),
                                12 to offset(0.em, 0.5.em)
                        ),
                textFont = step(
                        input = zoom(),
                        barlowRegular,
                        13.0 to barlowMedium,
                        16.0 to barlowSemiBold
                ),
                textColor = osmTramLabelTextColor,
                textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color(0xFFFFFFFF)),
                textHaloWidth = const(1.dp),
                minZoom = 14f,
                filter =
                        all(
                                has("local_ref").not(),
                                has("parent_osm_id").not(),
                                any(
                                        get("station_type").cast<StringValue>().eq(const("station")),
                                        get("station_type").cast<StringValue>().eq(const("tram_stop")),
                                        get("station_type").cast<StringValue>().eq(const("halt"))
                                ),
                                get("number_of_associated_stops").cast<NumberValue<EquatableValue>>().neq(const(0)),
                                any(
                                        get("mode_type").cast<StringValue>().eq(const("tram")),
                                        get("mode_type").cast<StringValue>().eq(const("light_rail"))
                                )
                        ),
                textJustify = const(TextJustify.Left),
                textAnchor = const(SymbolAnchor.Left),
                visible = (layerSettings.localrail as LayerCategorySettings).labelstops
        )

        /* ============================================================
        INTERCITY (railstops, route_type 2)
        ============================================================ */
        val intercityCircleRadius =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        7 to const(1.dp),
                        8 to const(2.dp),
                        9 to const(3.dp),
                        12 to const(5.dp),
                        15 to const(8.dp)
                )
        val intercityLabelSize =
                interpolate(
                        type = linear(),
                        input = zoom(),
                        6 to const(0.625f).em, // 10px
                        13 to const(1.0f).em // 16px
                )

        CircleLayer(
                id = LayersPerCategory.IntercityRail.Stops,
                source = railStopsSource,
                sourceLayer = "data",
                color = const(circleInside),
                radius = intercityCircleRadius,
                strokeColor = const(circleOutside),
                strokeWidth =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                9 to const(1.dp),
                                13.2 to const(1.5.dp)
                        ),
                strokeOpacity = step(input = zoom(), const(0.5f), 15.0 to const(0.6f)),
                opacity = step(input = zoom(), const(0.6f), 13.0 to const(0.8f)),
                minZoom = 7.5f,
                filter = all(isIntercity, has("osm_station_id").not()),
                visible = layerSettings.intercityrail.stops
        )

        SymbolLayer(
                id = LayersPerCategory.IntercityRail.LabelStops,
                source = railStopsSource,
                sourceLayer = "data",
                textField =
                        step(
                                input = zoom(),
                                fallback = get("displayname").cast<StringValue>(),
                                8 to get("displayname").cast<StringValue>(),
                                13 to
                                        get("displayname").cast<StringValue>() +
                                                switch(
                                                        conditions =
                                                                arrayOf(
                                                                        condition(
                                                                                has("level_id"),
                                                                                semi + level
                                                                        )
                                                                ),
                                                        fallback = const("")
                                                ) +
                                                switch(
                                                        conditions =
                                                                arrayOf(
                                                                        condition(
                                                                                has(
                                                                                        "platform_code"
                                                                                ),
                                                                                semi + platform
                                                                        )
                                                                ),
                                                        fallback = const("")
                                                )
                        ),
                textSize = intercityLabelSize,
                // radial 0.2 => y offset +0.2em
                textOffset = offset(0.em, 0.2.em),
                textFont = step(input = zoom(), barlowRegular, 10 to barlowMedium),
                textColor = if (isDark) const(Color.White) else const(Color(0xFF2A2A2A)),
                textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color.White),
                textHaloWidth = const(1.dp), // was 1.dp
                minZoom = 8f,
                textJustify = const(TextJustify.Left),
                textAnchor = const(SymbolAnchor.Left),
                filter = all(isIntercity, has("osm_station_id").not()),
                visible = (layerSettings.intercityrail as LayerCategorySettings).labelstops
        )

        for (i in 6 downTo 1) {
                val isLevel12 = i <= 2
                val minZoomCircle = when (i) {
                        1 -> 4f
                        2 -> 5f
                        3 -> 5f
                        4 -> 6.5f
                        5 -> 7.5f
                        else -> 8f
                }
                val minZoomLabel = when (i) {
                        1 -> 4f
                        2 -> 6.5f
                        3 -> 7f
                        4 -> 8.5f
                        5 -> 9.5f
                        else -> 10f
                }

                CircleLayer(
                        id = "intercityrail-ranked-$i",
                        source = osmStopsRankedSource,
                        sourceLayer = "data",
                        color = const(if (isLevel12) ranked12Inside else ranked3456Inside),
                        radius = when {
                                isLevel12 -> ranked12CircleSize
                                i == 3 -> ranked3CircleSize
                                else -> ranked456CircleSize
                        },
                        strokeColor = const(if (isLevel12) ranked12Outside else ranked3456Outside),
                        strokeWidth = step(input = zoom(), const(1.8.dp), 12.0 to const(2.0.dp)),
                        strokeOpacity = const(1f),
                        opacity = const(1f),
                        minZoom = minZoomCircle,
                        filter =
                                all(
                                        get("importance_level_station").cast<NumberValue<EquatableValue>>().eq(const(i)),
                                        get("rail").cast<BooleanValue>().eq(const(true)),
                                        get("mode_type").cast<StringValue>().neq(const("light_rail")),
                                        get("number_of_associated_stops").cast<NumberValue<EquatableValue>>().neq(const(0))
                                ),
                        visible = layerSettings.intercityrail.stops
                )

                SymbolLayer(
                        id = "intercityrail-ranked-label-$i",
                        source = osmStopsRankedSource,
                        sourceLayer = "data",
                        textField = get("name").cast(),
                        textSize = if (isLevel12) ranked12LabelSize else ranked3456LabelSize,
                        textOffset = offset(0.em, 0.5.em),
                        textFont = barlowBold,
                        textColor = if (isDark) const(Color.White) else const(Color(0xFF2A2A2A)),
                        textHaloColor = if (isDark) const(Color(0xFF0F172A)) else const(Color.White),
                        textHaloWidth = const(1.dp),
                        minZoom = minZoomLabel,
                        filter =
                                all(
                                        get("importance_level_station").cast<NumberValue<EquatableValue>>().eq(const(i)),
                                        get("rail").cast<BooleanValue>().eq(const(true))
                                ),
                        textJustify = const(TextJustify.Left),
                        textAnchor = const(SymbolAnchor.Left),
                        visible = (layerSettings.intercityrail as LayerCategorySettings).labelstops
                )
        }

        SymbolLayer(
                id = "platformlabels_osm_intercity",
                source = osmStopsSource,
                sourceLayer = "data",
                textField =
                        switch(
                                conditions =
                                        arrayOf(
                                                condition(
                                                        has("local_ref").cast(),
                                                        get("local_ref").cast()
                                                ),
                                        ),
                                fallback = get("ref").cast<StringValue>()
                        ),
                textFont =
                        step(input = zoom(), barlowRegular, 10 to barlowMedium, 13 to barlowBold),
                textSize =
                        interpolate(
                                type = linear(),
                                input = zoom(),
                                14 to const(0.25f).em, // 4px
                                15 to const(0.375f).em, // 6px
                                16 to const(0.6f).em,
                                17 to const(0.8f).em,
                                18 to const(1f).em
                        ),
                textHaloWidth = const(10.dp),
                textColor = const(Color(0xFFFFFFFF)),
                textHaloColor = const(Color(0xFF2d327d)),
                textAllowOverlap = const(true),
                textIgnorePlacement = const(true),
                filter = all(get("station_type").cast<StringValue>().eq(const("stop_position"))),
                minZoom = 14.2f,
        )

        // STATION FEATURES

        SymbolLayer(
                id = "stationenter",
                source = stationFeaturesSource,
                sourceLayer = "data",
                iconImage = image(painterResource(R.drawable.station_enter)),
                iconSize =
                        interpolate(
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
                textSize =
                        interpolate(
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
