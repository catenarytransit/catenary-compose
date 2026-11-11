package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NearbyDepartureKeyTest {
    private fun departure(
        tripId: String = "2555_0369",
        serviceDate: String = "2026-11-11",
        stopId: String = "stop",
        departure: Long? = 1794398160,
        arrival: Long? = null,
        realtime: Long? = null
    ) = LocalDepartureItem(
        tripId = tripId,
        serviceDate = serviceDate,
        stopId = stopId,
        departureSchedule = departure,
        arrivalSchedule = arrival,
        departureRealtime = realtime,
        cancelled = false,
        lastStop = false
    )

    @Test fun distinctStopVisitsHaveDistinctKeys() {
        val first = departure()
        assertNotEquals(first.stableDepartureKey(), first.copy(stopId = "another").stableDepartureKey())
        assertNotEquals(first.stableDepartureKey(), first.copy(serviceDate = "2026-11-12").stableDepartureKey())
        assertNotEquals(first.stableDepartureKey(), first.copy(departureSchedule = 1794398260).stableDepartureKey())
        assertNotEquals(first.stableDepartureKey(), first.copy(arrivalSchedule = 1794398000).stableDepartureKey())
    }

    @Test fun realtimeUpdatesDoNotChangeKeys() {
        val first = departure()
        assertEquals(first.stableDepartureKey(), first.copy(departureRealtime = 1794398250).stableDepartureKey())
    }

    @Test fun concatenatedTripIdsAndTimesCannotCollide() {
        val first = departure(tripId = "2555_0369", departure = 1794398160)
        val second = departure(tripId = "2555_036917", departure = 94398160)
        assertEquals(first.tripId + first.departureSchedule, second.tripId + second.departureSchedule)
        assertNotEquals(first.stableDepartureKey(), second.stableDepartureKey())
    }

    @Test fun identicalRecordsMayBeDeduplicatedWithoutRemovingLoopVisits() {
        val first = departure()
        val repeated = first.copy(departureRealtime = 1794398250)
        val laterVisit = first.copy(departureSchedule = 1794398460)
        val visible = listOf(first, repeated, laterVisit).distinctBy { it.stableDepartureKey() }
        assertEquals(2, visible.size)
        assertEquals(listOf(first, laterVisit), visible)
    }

    @Test fun nullableTimesAreUnambiguous() {
        assertNotEquals(departure(departure = null).stableDepartureKey(), departure(departure = 0).stableDepartureKey())
        assertNotEquals(departure(tripId = "1:2").stableDepartureKey(), departure(tripId = "1", stopId = "2:stop").stableDepartureKey())
    }
}
