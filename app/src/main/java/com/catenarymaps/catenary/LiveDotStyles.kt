package com.catenarymaps.catenary

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.DpOffsetValue
import org.maplibre.compose.expressions.value.DpValue
import org.maplibre.compose.expressions.value.FloatValue
import org.maplibre.compose.expressions.value.ListValue
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.expressions.value.TextUnitValue

data class LiveDotStyle(
    val dotRadius: Expression<DpValue>,
    val dotStrokeWidth: Expression<DpValue>,
    val dotOpacity: Expression<FloatValue>,
    val dotStrokeOpacity: Expression<FloatValue>,
    val bearingIconSize: Expression<FloatValue>,
    val bearingIconOffset: Expression<DpOffsetValue>,
    val bearingShellOpacity: Expression<FloatValue>,
    val bearingFilledOpacity: Expression<FloatValue>,
    val labelTextSize: Expression<TextUnitValue>,
    val labelTextFont: Expression<ListValue<StringValue>>,
    val labelRadialOffset: Expression<TextUnitValue>,
    val labelIgnorePlacementZoom: Double,
    val labelTextOpacity: Expression<FloatValue>,
    val minLayerDotsZoom: Float,
    val minLabelDotsZoom: Float,
    val minBearingZoom: Float
)

fun getLiveDotStyle(category: String, settings: LabelSettings): LiveDotStyle {
    val dotRadius: Expression<DpValue>
    val dotStrokeWidth: Expression<DpValue>
    val dotOpacity: Expression<FloatValue>
    val dotStrokeOpacity: Expression<FloatValue>
    val bearingIconSize: Expression<FloatValue>
    val bearingIconOffset: Expression<DpOffsetValue>
    val bearingShellOpacity: Expression<FloatValue>
    val bearingFilledOpacity: Expression<FloatValue>
    val labelTextSize: Expression<TextUnitValue>
    val labelTextFont: Expression<ListValue<StringValue>>
    val labelRadialOffset: Expression<TextUnitValue>
    val labelIgnorePlacementZoom: Double
    val labelTextOpacity: Expression<FloatValue>
    var minLayerDotsZoom: Float = 0.0F
    var minLabelDotsZoom: Float = 0.0F
    var minBearingZoom: Float = 0.0F

    when (category) {
        "bus" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                7.0 to const(1.2.dp),
                8.0 to const(1.6.dp),
                9.0 to const(1.7.dp),
                10.0 to const(2.0.dp),
                16.0 to const(6.0.dp)
            )
            dotStrokeWidth =
                interpolate(linear(), zoom(), 9.0 to const(0.3.dp), 15.0 to const(1.0.dp))
            dotOpacity = const(0.5f)
            dotStrokeOpacity = interpolate(
                linear(),
                zoom(),
                7.9 to const(0.0f),
                8.0 to const(0.3f),
                9.0 to const(0.5f),
                13.0 to const(0.9f)
            )
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                8.0 to const(0.3f),
                9.0 to const(0.4f),
                12.0 to const(0.5f),
                15.0 to const(0.6f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-10).dp),
                10.0 to offset(0.dp, (-8).dp),
                12.0 to offset(0.dp, (-7).dp),
                13.0 to offset(0.dp, (-6).dp),
                15.0 to offset(0.dp, (-5).dp)
            )
            bearingShellOpacity = interpolate(
                linear(),
                zoom(),
                9.0 to const(0.1f),
                10.0 to const(0.2f),
                12.0 to const(0.2f),
                15.0 to const(0.5f)
            )
            bearingFilledOpacity = const(0.4f)
            labelTextSize = if (settings.headsign) {
                interpolate(
                    type = linear(),
                    input = zoom(),
                    9.0 to const(0.25f.em),
                    11.0 to const(0.3125f.em),
                    13.0 to const(0.5625f.em),
                    15.0 to const(0.6875f.em)
                )
            } else {
                interpolate(
                    type = linear(),
                    input = zoom(),
                    9.0 to const(0.3125f.em),
                    11.0 to const(0.4375f.em),
                    13.0 to const(0.625f.em),
                    15.0 to const(0.8125f.em)
                )
            }
            labelTextFont =
                step(
                    zoom(),
                    const(listOf("Barlow-Medium")),
                    11.0 to const(listOf("Barlow-SemiBold"))
                )
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 10.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                7.9 to const(0.0f),
                8.0 to const(0.9f),
                11.0 to const(0.95f),
                12.0 to const(1.0f)
            )
            minLayerDotsZoom = 9F
            minLabelDotsZoom = 10F
            minBearingZoom = 11F
        }

        "metro" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(3.0.dp),
                8.0 to const(3.0.dp),
                10.0 to const(4.0.dp),
                11.0 to const(6.0.dp),
                16.0 to const(12.0.dp)
            )
            dotStrokeWidth =
                interpolate(linear(), zoom(), 8.0 to const(0.8.dp), 10.0 to const(1.2.dp))
            dotOpacity = interpolate(linear(), zoom(), 7.0 to const(0.5f), 9.0 to const(0.7f))
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                4.0 to const(0.4f),
                6.0 to const(0.5f),
                8.0 to const(0.55f),
                9.0 to const(0.6f),
                11.0 to const(0.7f),
                12.0 to const(0.8f),
                15.0 to const(0.9f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-10).dp),
                10.0 to offset(0.dp, (-8).dp),
                12.0 to offset(0.dp, (-7).dp),
                13.0 to offset(0.dp, (-6).dp),
                15.0 to offset(0.dp, (-5).dp)
            )
            bearingShellOpacity = interpolate(
                linear(),
                zoom(),
                9.8 to const(0.3f),
                11.0 to const(0.4f),
                11.5 to const(0.8f)
            )
            bearingFilledOpacity = const(0.6f)
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(0.3125f.em),
                9.0 to const(0.4375f.em),
                10.0 to const(0.5625f.em),
                11.0 to const(0.6875f.em),
                13.0 to const(0.75f.em)
            )
            labelTextFont = const(listOf("Barlow-Medium"))
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                2.0 to const(0.0f),
                2.5 to const(0.8f),
                10.0 to const(1.0f)
            )
        }

        "tram" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(1.8.dp),
                8.0 to const(2.3.dp),
                10.0 to const(4.0.dp),
                11.0 to const(4.5.dp),
                13.0 to const(6.0.dp),
                15.0 to const(6.0.dp),
                16.0 to const(10.0.dp)
            )
            dotStrokeWidth = interpolate(
                linear(),
                zoom(),
                8.0 to const(0.5.dp),
                9.0 to const(0.6.dp),
                10.0 to const(1.0.dp)
            )
            dotOpacity = interpolate(linear(), zoom(), 7.0 to const(0.5f), 9.0 to const(0.7f))
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                4.0 to const(0.2f),
                6.0 to const(0.3f),
                8.0 to const(0.4f),
                9.0 to const(0.5f),
                11.0 to const(0.6f),
                12.0 to const(0.7f),
                15.0 to const(0.8f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-6).dp),
                10.0 to offset(0.dp, (-5).dp),
                12.0 to offset(0.dp, (-4).dp),
                13.0 to offset(0.dp, (-4).dp),
                15.0 to offset(0.dp, (-4).dp)
            )
            bearingShellOpacity = interpolate(
                linear(),
                zoom(),
                6.0 to const(0.1f),
                9.8 to const(0.3f),
                11.0 to const(0.3f),
                11.5 to const(0.4f),
                12.0 to const(0.5f)
            )
            bearingFilledOpacity = interpolate(
                linear(),
                zoom(),
                6.0 to const(0.2f),
                9.0 to const(0.4f),
                11.0 to const(0.5f),
                13.0 to const(0.6f)
            )
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(0.25f.em),
                9.0 to const(0.375f.em),
                10.0 to const(0.4375f.em),
                11.0 to const(0.5625f.em),
                13.0 to const(0.625f.em),
                15.0 to const(0.875f.em)
            )
            labelTextFont = const(listOf("Barlow-Medium"))
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                2.0 to const(0.0f),
                2.5 to const(0.8f),
                10.0 to const(1.0f)
            )
        }

        "intercityrail" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                1.0 to const(1.0.dp),
                3.0 to const(2.5.dp),
                6.0 to const(2.8.dp),
                8.0 to const(4.0.dp),
                11.0 to const(6.0.dp),
                16.0 to const(10.0.dp)
            )
            dotStrokeWidth = interpolate(
                linear(),
                zoom(),
                3.0 to const(0.6.dp),
                5.0 to const(0.7.dp),
                7.0 to const(0.8.dp)
            )
            dotOpacity = interpolate(
                linear(),
                zoom(),
                4.0 to const(0.4f),
                7.0 to const(0.6f),
                11.0 to const(0.7f)
            )
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                4.0 to const(0.3f),
                6.0 to const(0.4f),
                8.0 to const(0.45f),
                9.0 to const(0.5f),
                11.0 to const(0.6f),
                12.0 to const(0.7f),
                15.0 to const(0.8f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-6).dp),
                10.0 to offset(0.dp, (-5).dp),
                12.0 to offset(0.dp, (-4).dp),
                13.0 to offset(0.dp, (-3).dp),
                15.0 to offset(0.dp, (-2).dp)
            )
            bearingShellOpacity =
                interpolate(linear(), zoom(), 9.0 to const(0.3f), 11.5 to const(0.8f))
            bearingFilledOpacity = const(0.8f)
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                6.0 to const(0.5f.em),
                9.0 to const(0.5f.em),
                11.0 to const(0.6f.em),
                13.0 to const(0.7f.em),
                16.0 to const(0.9.em)
            )
            labelTextFont = step(
                zoom(),
                const(listOf("Barlow-Regular")),
                9.0 to const(listOf("Barlow-Medium"))
            )
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                2.0 to const(0.0f),
                2.5 to const(0.85f),
                13.0 to const(0.95f),
                15.0 to const(0.99f)
            )
        }

        "other" -> {
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                8.0 to const(5.0.dp),
                10.0 to const(6.0.dp),
                16.0 to const(8.0.dp)
            )
            dotStrokeWidth = const(1.0.dp)
            dotOpacity = const(0.5f)
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                4.0 to const(0.1f),
                6.0 to const(0.1f),
                8.0 to const(0.15f),
                9.0 to const(0.18f),
                11.0 to const(0.2f),
                12.0 to const(0.25f),
                15.0 to const(0.5f)
            )
            bearingIconOffset = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to offset(0.dp, (-20).dp),
                13.0 to offset(0.dp, (-20).dp),
                15.0 to offset(0.dp, (-20).dp)
            )
            bearingShellOpacity =
                interpolate(linear(), zoom(), 9.0 to const(0.3f), 11.5 to const(0.8f))
            bearingFilledOpacity = const(0.6f)
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                9.0 to const(0.53125f.em),
                11.0 to const(0.8125f.em),
                13.0 to const(1.0f.em)
            )
            labelTextFont =
                step(
                    zoom(),
                    const(listOf("Barlow-Regular")),
                    11.0 to const(listOf("Barlow-Medium"))
                )
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = interpolate(
                linear(),
                zoom(),
                2.0 to const(0.0f),
                2.5 to const(0.8f),
                10.0 to const(0.8f)
            )
        }

        else -> {
            // Fallback to your original generic values
            dotRadius = interpolate(
                type = linear(),
                input = zoom(),
                6 to const(2.dp),
                10 to const(3.dp),
                14 to const(5.dp),
                18 to const(8.dp)
            )
            dotStrokeWidth = const(1.5.dp)
            dotOpacity = const(1.0f)
            dotStrokeOpacity = const(1.0f)
            bearingIconSize = interpolate(
                type = linear(),
                input = zoom(),
                10 to const(0.7f),
                14 to const(1.0f),
                18 to const(1.2f)
            )
            bearingIconOffset = offset(0.dp, (-20).dp) // A reasonable guess in dp
            bearingShellOpacity = const(1.0f)
            bearingFilledOpacity = const(0.2f)
            labelTextSize = interpolate(
                type = linear(),
                input = zoom(),
                9 to const(0.625f.em),
                14 to const(0.8125f.em)
            )
            labelTextFont = const(listOf("Barlow-SemiBold"))
            labelRadialOffset = const(0.2f.em)
            labelIgnorePlacementZoom = 9.5
            labelTextOpacity = const(1.0f)
        }
    }

    return LiveDotStyle(
        dotRadius = dotRadius,
        dotStrokeWidth = dotStrokeWidth,
        dotOpacity = dotOpacity,
        dotStrokeOpacity = dotStrokeOpacity,
        bearingIconSize = bearingIconSize,
        bearingIconOffset = bearingIconOffset,
        bearingShellOpacity = bearingShellOpacity,
        bearingFilledOpacity = bearingFilledOpacity,
        labelTextSize = labelTextSize,
        labelTextFont = labelTextFont,
        labelRadialOffset = labelRadialOffset,
        labelIgnorePlacementZoom = labelIgnorePlacementZoom,
        labelTextOpacity = labelTextOpacity,
        minLayerDotsZoom = minLayerDotsZoom,
        minLabelDotsZoom = minLabelDotsZoom,
        minBearingZoom = minBearingZoom
    )
}