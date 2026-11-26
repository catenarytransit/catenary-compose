package com.catenarymaps.catenary

import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.FloatValue
import org.maplibre.compose.expressions.value.StringValue

fun interpretLabelsToExpression(
    settings: LabelSettings, usUnits: Boolean, speedProperty: String = "speed"
): Expression<StringValue> {
    val parts = mutableListOf<Expression<StringValue>>()
    val newline = const("\n")
    val empty = const("")

    if (settings.route) {
        parts.add(coalesce(get("maptag").cast<StringValue>(), empty))
    }
    if (settings.trip) {
        if (parts.isNotEmpty()) parts.add(const(" "))
        parts.add(coalesce(get("tripIdLabel").cast<StringValue>(), empty))
    }
    if (settings.vehicle) {
        if (parts.isNotEmpty()) parts.add(const(" "))
        parts.add(coalesce(get("vehicleIdLabel").cast<StringValue>(), empty))
    }

    // Add newline if any of the first row are present and any of the second row are present
    val secondRow = mutableListOf<Expression<StringValue>>()
    if (settings.headsign) {
        secondRow.add(coalesce(get("headsign").cast<StringValue>(), empty))
    }
    if (settings.speed) {
        if (secondRow.isNotEmpty()) secondRow.add(const(" "))
        secondRow.add(coalesce(get(speedProperty).cast<StringValue>(), empty))
    }
    if (settings.occupancy) {
        if (secondRow.isNotEmpty()) secondRow.add(const(" "))
        secondRow.add(coalesce(get("crowd_symbol").cast<StringValue>(), empty))
    }
    if (settings.delay) {
        if (secondRow.isNotEmpty()) secondRow.add(const(" "))
        secondRow.add(coalesce(get("delay_label").cast<StringValue>(), empty))
    }

    if (parts.isNotEmpty() && secondRow.isNotEmpty()) {
        parts.add(newline)
        parts.addAll(secondRow)
    } else if (secondRow.isNotEmpty()) {
        parts.addAll(secondRow)
    }

    if (parts.isEmpty()) return const("")

    var finalExpression: Expression<StringValue> = parts.first()
    if (parts.size > 1) {
        for (i in 1 until parts.size) {
            finalExpression = finalExpression.plus(parts[i])
        }
    }
    return finalExpression
}

/**
 * Context sources mix transport modes, unlike the regular category sources.
 * Use the same saved label settings and expression for each mode, without
 * changing whether the selected vehicle itself remains visible.
 */
internal fun contextVehicleLabelExpression(
    settings: AllLayerSettings,
    usUnits: Boolean
): Expression<StringValue> {
    val speedProperty = if (usUnits) "speed_mph" else "speed_kmh"
    fun label(category: LayerCategorySettings) =
        interpretLabelsToExpression(category.labelrealtimedots, usUnits, speedProperty)

    // Match Spruce's normal realtime categories; also accept extended coach
    // types from a selected trip's static GTFS metadata.
    return switch(
        input = get("route_type").cast<FloatValue>(),
        case(label = listOf(3, 11) + (200..209), output = label(settings.bus)),
        case(label = listOf(0, 1, 5, 7, 12), output = label(settings.localrail)),
        case(label = 2, output = label(settings.intercityrail)),
        fallback = label(settings.other)
    )
}
