package com.catenarymaps.catenary

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.Condition
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToBoolean
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.em
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.EquatableValue
import org.maplibre.compose.expressions.value.NumberValue
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.expressions.value.SymbolPlacement
import org.maplibre.compose.expressions.value.TextPitchAlignment
import org.maplibre.compose.expressions.value.TextUnitValue
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.rememberVectorSource
import org.maplibre.compose.expressions.dsl.Case
import org.maplibre.compose.expressions.dsl.condition

val SHAPES_SOURCES = mapOf(
    "intercityrailshapes" to "https://birch1.catenarymaps.org/shapes_intercity_rail",
    "localcityrailshapes" to "https://birch2.catenarymaps.org/shapes_local_rail",
    "othershapes" to "https://birch3.catenarymaps.org/shapes_ferry",
    "busshapes" to "https://birch4.catenarymaps.org/shapes_bus"
)

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
            7 to const(0.4.dp),
            10 to const(0.6.dp),
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
        minZoom = 9f,
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
        opacity = switch(
            conditions = arrayOf(
                condition(
                    get_stop_to_stop_generated.eq(const(true)),
                    const(0.2f)
                )
            ),
            fallback = const(1f)
        ),
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
        condition(
            get("stop_to_stop_generated").cast<BooleanValue>(),
            output = const(0.2f)
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