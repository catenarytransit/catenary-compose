package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.ast.FunctionCall
import org.maplibre.compose.expressions.dsl.const

class VehicleMapLabelsTest {
    @Test
    fun routeAliasesAndFallbacksMatchRegularDots() {
        assertEquals("A", vehicleMapRouteLabels("metro~losangeles", null, "Metro A Line", null, null).route)
        assertEquals("SB", vehicleMapRouteLabels("test", null, null, "Metrolink San Bernardino Line", null).route)
        assertEquals("42", vehicleMapRouteLabels("test", "internal-id", "42", "Long name", null).route)
        assertEquals("", vehicleMapRouteLabels("test", "internal-id", null, null, null).route)
    }

    @Test
    fun contextualVehicleFieldsUseTheRegularDisplayFormat() {
        assertEquals("123", vehicleMapVehicleLabel("test", "ineo-bus:123", null))
        assertEquals("456", vehicleMapVehicleLabel("new-south-wales", "A to B", "456"))
        assertEquals("7", vehicleMapTripLabel("7", "internal_trip"))
        assertEquals("123", vehicleMapTripLabel(null, "line_123x"))
        assertEquals("", vehicleMapTripLabel(null, "internal-id"))
        assertEquals("", vehicleMapSpeedLabel(null, false))
        assertTrue(vehicleMapSpeedLabel(10f, true).endsWith(" mph"))
        assertTrue(vehicleMapSpeedLabel(10f, false).endsWith(" km/h"))
        assertEquals("", vehicleMapDelayLabel(null))
        assertEquals("+0m", vehicleMapDelayLabel(30))
        assertEquals("-1h1m", vehicleMapDelayLabel(-3660))
    }

    private fun fields(expression: Expression<*>): Set<String> = buildSet {
        expression.visit { node ->
            if (node is FunctionCall && node.name == "get") {
                for (field in listOf("maptag", "tripIdLabel", "vehicleIdLabel", "headsign",
                    "speed", "speed_mph", "speed_kmh", "crowd_symbol", "delay_label")) {
                    if (node.args.firstOrNull() == const(field)) add(field)
                }
            }
        }
    }

    @Test
    fun mixedContextSelectsEachModesOwnSettingsAndSpeedUnit() {
        val none = LabelSettings(route = false, occupancy = false, delay = false)
        val settings = AllLayerSettings(
            bus = LayerCategorySettings(labelrealtimedots = none.copy(vehicle = true)),
            localrail = LayerCategorySettings(labelrealtimedots = none.copy(headsign = true)),
            intercityrail = LayerCategorySettings(labelrealtimedots = none.copy(trip = true)),
            other = LayerCategorySettings(labelrealtimedots = none.copy(speed = true))
        )
        val expression = contextVehicleLabelExpression(settings, true) as FunctionCall
        assertEquals("match", expression.name)
        assertEquals(setOf("vehicleIdLabel"), fields(expression.args[2]))
        assertEquals(setOf("headsign"), fields(expression.args[4]))
        assertEquals(setOf("tripIdLabel"), fields(expression.args[6]))
        assertEquals(setOf("speed_mph"), fields(expression.args[7]))
        assertEquals(setOf("speed_kmh"), fields((contextVehicleLabelExpression(settings, false) as FunctionCall).args[7]))
        assertEquals(setOf("speed"), fields(interpretLabelsToExpression(none.copy(speed = true), false)))
    }
}
