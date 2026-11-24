package com.catenarymaps.catenary

import org.junit.Assert.*
import org.junit.Test

class TripPlannerMapLayersTest {
    @Test
    fun stopsOnlyMapLeavesPreferencesAndStopVisibilityUntouched() {
        val original = AllLayerSettings(
            bus = LayerCategorySettings(stops = false, labelstops = false),
            localrail = LayerCategorySettings(),
            intercityrail = LayerCategorySettings(),
            other = LayerCategorySettings()
        )
        val map = original.forJourneyDetailsMap()
        for (category in listOf(map.bus, map.localrail, map.intercityrail, map.other)) {
            assertFalse(category.shapes)
            assertFalse(category.labelshapes)
            assertFalse(category.visiblerealtimedots)
            assertFalse(category.labeltrajectories)
        }
        assertFalse(map.bus.stops)
        assertFalse(map.bus.labelstops)
        assertTrue(map.intercityrail.stops)
        assertTrue(map.intercityrail.labelstops)
        assertTrue(original.bus.shapes)
        assertTrue(original.bus.visiblerealtimedots)
        assertEquals(original.more, map.more)
    }

    @Test
    fun reusedVehicleLabelCannotRenderAnotherTrip() {
        val identity = RamondaTripIdentity("agency", "trip-a", "route", "20261120", "08:00:00")
        fun vehicle(tripId: String?, date: String? = "20261120") =
            VehicleRealtimeData(
                trip = VehicleRealtimeTripInfo(
                    trip_id = tripId, route_id = "route", start_date = date, start_time = "08:00:00"
                )
            )
        assertTrue(vehicle("trip-a").matchesJourneyTrip(identity))
        assertFalse(vehicle("trip-b").matchesJourneyTrip(identity))
        assertFalse(vehicle("trip-a", date = "20261119").matchesJourneyTrip(identity))
        assertTrue(VehicleRealtimeData().matchesJourneyTrip(identity))
    }
}
