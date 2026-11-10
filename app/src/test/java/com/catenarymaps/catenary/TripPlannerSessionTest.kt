package com.catenarymaps.catenary

import org.junit.Assert.*
import org.junit.Test

class TripPlannerSessionTest {
    private val journey = DirectionsItinerary(
        "2026-11-05T17:00:00Z", "2026-11-05T17:30:00Z",
        legs = listOf(DirectionsLeg("BUS", routeId = "42",
            intermediateStops = listOf(DirectionsStop("Transfer")),
            legGeometry = DirectionsGeometry("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)))
    )
    private val saved = TripPlannerSession(
        origin = DirectionsLocation("Home", 34.0, -118.0, true),
        destination = DirectionsLocation("Station", 34.1, -118.1),
        options = DirectionsOptions(cycling = true, cyclingKmh = 20f),
        arriveBy = true, now = false, time = 1793898000,
        itineraries = listOf(journey, journey.copy(legs = listOf(DirectionsLeg("BIKE")))),
        selected = 1, details = true, showTransfers = true
    )

    @Test fun freshViewModelRestoresDetailsAndCompleteResultsWithoutSearching() {
        val decoded = TripPlannerSessionCodec.decode(TripPlannerSessionCodec.encode(saved))
        assertEquals(saved, decoded)
        val planner = TripPlannerViewModel()
        assertTrue(planner.restoreSession(decoded))
        assertEquals(saved, planner.session(true))
        assertTrue(planner.searched)
        assertFalse(planner.loading)
        assertNull(planner.error)
        assertNull(planner.editing)
        assertEquals("Home", planner.originText)
        assertEquals("Station", planner.destinationText)
    }

    @Test fun resultsListRestoresAndDetailsBackKeepsResults() {
        val planner = TripPlannerViewModel()
        planner.restoreSession(saved.copy(details = false))
        assertFalse(planner.details)
        planner.details = true
        planner.selected = 0
        val nextLaunch = TripPlannerViewModel()
        nextLaunch.restoreSession(TripPlannerSessionCodec.decode(TripPlannerSessionCodec.encode(planner.session(true)!!)))
        assertTrue(nextLaunch.details)
        assertEquals(journey, nextLaunch.itineraries[nextLaunch.selected])
        nextLaunch.details = false
        assertEquals(saved.itineraries, nextLaunch.itineraries)
    }

    @Test fun blankOrUnsubmittedPlannerHasNoSession() {
        val planner = TripPlannerViewModel()
        planner.restoreSession(null)
        assertNull(planner.session(true))
        planner.choose(true, saved.origin)
        planner.choose(false, saved.destination)
        assertNull(planner.session(true))
    }

    @Test fun leavingOrEditingRemovesTheSavedSession() {
        val planner = TripPlannerViewModel()
        planner.restoreSession(saved)
        assertNull(planner.session(false))
        planner.edit(true, "")
        assertNull(planner.session(true))
    }

    @Test fun configurationChangeDoesNotReplaceNewerInMemoryState() {
        val planner = TripPlannerViewModel()
        planner.restoreSession(saved)
        planner.details = false
        planner.selected = 0
        assertFalse(planner.restoreSession(saved))
        assertFalse(planner.details)
        assertEquals(0, planner.selected)
    }

    @Test fun malformedEmptyAndUnsupportedCachesAreDiscarded() {
        assertNull(TripPlannerSessionCodec.decode("{"))
        assertNull(TripPlannerSessionCodec.decode("{}"))
        assertNull(saved.copy(version = 2).validated())
        assertNull(saved.copy(itineraries = emptyList()).validated())
        assertNull(saved.copy(origin = saved.origin.copy(lat = 91.0)).validated())
        assertNull(saved.copy(itineraries = listOf(journey.copy(startTime = "invalid"))).validated())
        assertEquals(1, saved.copy(selected = 99).validated()!!.selected)
    }
}
