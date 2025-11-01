package com.catenarymaps.catenary

import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.value.StringValue

fun interpretLabelsToExpression(
    settings: LabelSettings, usUnits: Boolean
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
        secondRow.add(coalesce(get("speed").cast<StringValue>(), empty))
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