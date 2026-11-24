package com.catenarymaps.catenary

import org.junit.Assert.*
import org.junit.Test

class TripPlannerOptionsTest {
    private class MemoryStore(var saved: DirectionsOptions? = null) : TripPlannerOptionsStore {
        override fun read() = saved
        override fun write(options: DirectionsOptions) { saved = options }
    }

    private val custom = DirectionsOptions(
        cycling = true, walkingPace = 720f, cyclingKmh = 22f,
        accessSeconds = 1500f, egressSeconds = 1800f, directSeconds = 2400f,
        filterTransportModes = true,
        transportModes = setOf(PublicTransportMode.TRAIN, PublicTransportMode.TRAM)
    )

    private fun session(options: DirectionsOptions) = TripPlannerSession(
        origin = DirectionsLocation("Start", 34.0, -118.0),
        destination = DirectionsLocation("End", 34.1, -118.1),
        options = options, arriveBy = false, now = true, time = 1793898000,
        itineraries = listOf(DirectionsItinerary("2026-11-05T17:00:00Z", "2026-11-05T18:00:00Z",
            legs = listOf(DirectionsLeg("BUS"))))
    )

    @Test fun optionsSurviveWithoutACompletedSearchAndResetPersists() {
        val store = MemoryStore()
        val first = TripPlannerViewModel()
        first.restoreSession(null, store)
        assertEquals(DirectionsOptions(), first.options)
        first.updateOptions(custom)
        assertEquals(custom, store.saved)
        assertNull(first.session(true))
        first.leave()

        val second = TripPlannerViewModel()
        second.restoreSession(null, store)
        assertEquals(custom, second.options)
        val disabled = custom.copy(filterTransportModes = false)
        second.updateOptions(disabled)
        val third = TripPlannerViewModel()
        third.restoreSession(null, store)
        assertEquals(disabled, third.options)
        assertEquals(custom.transportModes, third.options.transportModes)
        third.updateOptions(DirectionsOptions())
        val fourth = TripPlannerViewModel()
        fourth.restoreSession(null, store)
        assertEquals(DirectionsOptions(), fourth.options)
        assertEquals(DirectionsOptions(), store.saved)
    }

    @Test fun savedPreferencesTakePrecedenceOverStaleSession() {
        val store = MemoryStore(custom)
        val planner = TripPlannerViewModel()
        planner.restoreSession(session(DirectionsOptions()), store)
        assertEquals(custom, planner.options)
        assertFalse(planner.searched)
        assertNull(planner.session(true))
        assertEquals(custom, store.saved)
    }

    @Test fun matchingSessionStillRestoresAndLegacySessionMigrates() {
        val store = MemoryStore()
        val saved = session(custom)
        val first = TripPlannerViewModel()
        first.restoreSession(saved, store)
        assertEquals(saved, first.session(true))
        assertEquals(custom, store.saved)
        val second = TripPlannerViewModel()
        second.restoreSession(saved, store)
        assertEquals(saved, second.session(true))
        second.updateOptions(DirectionsOptions())
        assertFalse(second.searched)
        assertFalse(second.restoreSession(saved, store))
        assertEquals(DirectionsOptions(), second.options)
        assertEquals(DirectionsOptions(), store.saved)
    }

    @Test fun emptyModeSelectionIsPreserved() {
        val store = MemoryStore()
        val first = TripPlannerViewModel()
        first.restoreSession(null, store)
        val none = custom.copy(transportModes = emptySet())
        first.updateOptions(none)
        val second = TripPlannerViewModel()
        second.restoreSession(null, store)
        assertEquals(none, second.options)
    }
}
