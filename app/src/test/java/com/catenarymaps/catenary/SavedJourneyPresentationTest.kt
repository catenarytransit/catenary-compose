package com.catenarymaps.catenary

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SavedJourneyPresentationTest {
    private fun snapshot(id: String, start: String, zone: String = "America/Los_Angeles") = SavedJourney(
        DirectionsItinerary(start, start, legs = listOf(
            DirectionsLeg("BUS", from = DirectionsPoint(timezone = zone))), id = id), null, null)

    @Test fun groupsAllDatesAndSortsConnectionsUsingDepartureTimezone() {
        val first = snapshot("first", "2026-11-11T07:30:00Z") // Nov 10 in Los Angeles
        val second = snapshot("second", "2026-11-11T08:30:00Z")
        val third = snapshot("third", "2026-11-11T09:30:00Z")
        val groups = listOf(third, first, second).groupedByDepartureDate()
        assertEquals(listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11)), groups.keys.toList())
        assertEquals(listOf(first), groups.values.first())
        assertEquals(listOf(second, third), groups.values.last())
    }

    @Test fun comparesEachDepartureInItsOwnTimezoneAndRetainsInvalidDates() {
        val la = snapshot("la", "2026-11-11T07:30:00Z")
        val tokyo = snapshot("tokyo", "2026-11-11T07:00:00Z", "Asia/Tokyo")
        val invalid = snapshot("invalid", "invalid")
        val groups = listOf(invalid, tokyo, la).groupedByDepartureDate()
        assertEquals(listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), null), groups.keys.toList())
        assertEquals(listOf(invalid), groups[null])
    }

    @Test fun namesBothCurrentEndpointsAndPreservesTheCapturedCoordinates() = runBlocking {
        val origin = DirectionsLocation("Current location", 34.0, -118.0, isCurrentLocation = true)
        val destination = DirectionsLocation("Current location", 35.0, -119.0, isCurrentLocation = true)
        val original = snapshot("named", "2026-11-11T08:00:00Z").copy(origin = origin, destination = destination)
        val lookedUp = mutableListOf<DirectionsLocation>()
        val named = original.withNamedCurrentLocations { lookedUp.add(it); " Place ${it.lat} " }
        assertEquals(listOf(origin, destination), lookedUp)
        assertEquals(origin.copy(name = "Place 34.0", isCurrentLocation = false), named.origin)
        assertEquals(destination.copy(name = "Place 35.0", isCurrentLocation = false), named.destination)
        assertEquals(original.journey, named.journey)
        assertTrue(original.origin!!.isCurrentLocation)
    }

    @Test fun lookupMissUsesCoordinatesAndLeavesNamedEndpointsAlone() = runBlocking {
        val fixed = DirectionsLocation("Station", 35.0, -119.0)
        val original = snapshot("fallback", "2026-11-11T08:00:00Z").copy(
            origin = DirectionsLocation("Current location", 34.0, -118.0, isCurrentLocation = true), destination = fixed)
        var calls = 0
        val named = original.withNamedCurrentLocations { calls++; null }
        assertEquals(1, calls)
        assertEquals("34.00000, -118.00000", named.origin?.name)
        assertFalse(named.origin!!.isCurrentLocation)
        assertEquals(fixed, named.destination)
        assertEquals(original.copy(origin = null), original.copy(origin = null).withNamedCurrentLocations { error("Unexpected lookup") })
    }
}
