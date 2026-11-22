package com.catenarymaps.catenary

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ActiveJourneyGuidanceTest {
    private val departure = "2026-11-22T10:00:00Z"
    private val arrival = "2026-11-22T11:00:00Z"
    private fun epoch(value: String) = Instant.parse(value).epochSecond
    private val origin = DirectionsLocation("Origin", 33.65, -117.75)
    private val destination = DirectionsLocation("Destination", 33.66, -117.76)

    private fun trip(vararg legs: DirectionsLeg) = DirectionsItinerary(
        departure, arrival, legs = legs.toList(), id = "guided"
    )

    @Test fun guidanceOnlyShowsNearbyDuringThreeHourLeadIn() {
        val journey = trip(DirectionsLeg("BUS", startTime = departure, endTime = arrival))
        val active = ActiveJourney(SavedJourney(journey, origin, destination), epoch(departure))
        assertTrue(shouldShowActiveJourney(active, epoch(departure) - 3600, origin.lat to origin.lon))
        assertFalse(shouldShowActiveJourney(active, epoch(departure) - 10801, origin.lat to origin.lon))
        assertFalse(shouldShowActiveJourney(active, epoch(departure) - 60, 40.0 to -120.0))
        assertFalse(shouldShowActiveJourney(active, epoch(departure) - 60, null))
        assertTrue(shouldShowActiveJourney(active, epoch(departure), null))
    }

    @Test fun journeyBarHidesOnlyForFocusedSearchInputs() {
        val active = ActiveJourney(
            SavedJourney(trip(DirectionsLeg("BUS", startTime = departure, endTime = arrival)), origin, destination),
            epoch(departure)
        )
        val now = epoch(departure)
        assertTrue(shouldDisplayActiveJourneyBar(active, now, null, false, false))
        assertFalse(shouldDisplayActiveJourneyBar(active, now, null, true, false))
        assertFalse(shouldDisplayActiveJourneyBar(active, now, null, false, true))
        assertFalse(shouldDisplayActiveJourneyBar(null, now, null, false, false))
        assertFalse(shouldDisplayActiveJourneyBar(active, now - 4 * 3600, null, false, false))
    }

    @Test fun boardingSwitchesToAlightingAfterEffectiveDeparture() {
        val journey = trip(DirectionsLeg("BUS", startTime = departure, endTime = arrival,
            from = DirectionsPoint(name = "Origin"), to = DirectionsPoint(name = "Destination"),
            routeShortName = "42"))
        val before = journeyGuidance(journey, epoch(departure) - 300, null, destination)
        assertTrue(before is JourneyGuidance.Boarding)
        assertEquals(5L, (before as JourneyGuidance.Boarding).minutes)
        val riding = journeyGuidance(journey, epoch(departure) + 300, null, destination)
        assertTrue(riding is JourneyGuidance.Riding)
        assertEquals("Destination", (riding as JourneyGuidance.Riding).stop)
    }

    @Test fun transferShowsNextBoardingNotAlreadyCompletedLeg() {
        val first = DirectionsLeg("BUS", startTime = departure,
            endTime = "2026-11-22T10:20:00Z", to = DirectionsPoint(name = "Change"))
        val walking = DirectionsLeg("WALK", startTime = "2026-11-22T10:20:00Z",
            endTime = "2026-11-22T10:25:00Z")
        val second = DirectionsLeg("TRAIN", startTime = "2026-11-22T10:30:00Z", endTime = arrival,
            from = DirectionsPoint(name = "Change"), to = DirectionsPoint(name = "Destination"))
        val guidance = journeyGuidance(trip(first, walking, second),
            epoch("2026-11-22T10:22:00Z"), null, destination)
        assertTrue(guidance is JourneyGuidance.Boarding)
        assertTrue((guidance as JourneyGuidance.Boarding).transfer)
        assertEquals("Change", guidance.leg.from?.name)
    }

    @Test fun finalWalkReportsRemainingDistanceAndArrivalNeedsLocationOrGrace() {
        val walk = DirectionsLeg("WALK", startTime = departure, endTime = arrival,
            from = DirectionsPoint(name = "Origin"),
            to = DirectionsPoint(name = "Destination", lat = destination.lat, lon = destination.lon))
        val journey = trip(walk)
        val guidance = journeyGuidance(journey, epoch(departure) + 120, origin.lat to origin.lon, destination)
        assertTrue(guidance is JourneyGuidance.FinalStreet)
        assertTrue((guidance as JourneyGuidance.FinalStreet).remainingMeters!! > 100)
        assertFalse(hasArrivedAtJourneyDestination(journey, destination,
            epoch(arrival) - 300, destination.lat to destination.lon))
        assertTrue(hasArrivedAtJourneyDestination(journey, destination,
            epoch(arrival), destination.lat to destination.lon))
        assertFalse(hasArrivedAtJourneyDestination(journey, destination,
            epoch(arrival) + 100, origin.lat to origin.lon))
        assertFalse(hasArrivedAtJourneyDestination(journey, destination,
            epoch(arrival) + 100, null))
        assertTrue(hasArrivedAtJourneyDestination(journey, destination,
            epoch(arrival) + 901, null))
    }
}
