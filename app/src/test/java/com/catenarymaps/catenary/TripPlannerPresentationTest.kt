package com.catenarymaps.catenary

import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TripPlannerPresentationTest {
    @Test fun dateHeadingAppearsAtLocalMidnightRatherThanUtcMidnight() {
        val zone = ZoneId.of("America/Los_Angeles")
        assertNull(connectionDateHeading("2026-11-10T23:55:00Z", "2026-11-11T00:05:00Z", zone))
        assertEquals(LocalDate.of(2026, 11, 11),
            connectionDateHeading("2026-11-11T07:55:00Z", "2026-11-11T08:05:00Z", zone))
    }

    @Test fun dateHeadingHandlesYearChangesAndMalformedTimes() {
        val utc = ZoneId.of("UTC")
        assertEquals(LocalDate.of(2027, 1, 1),
            connectionDateHeading("2026-12-31T23:59:00Z", "2027-01-01T00:00:00Z", utc))
        assertNull(connectionDateHeading(null, "2027-01-01T00:00:00Z", utc))
        assertNull(connectionDateHeading("bad", "2027-01-01T00:00:00Z", utc))
        assertNull(connectionDateHeading("2026-12-31T23:59:00Z", "bad", utc))
    }

    @Test fun dateHeadingHandlesRepeatedHourAtDaylightSavingChange() {
        assertNull(connectionDateHeading("2026-11-01T08:30:00Z", "2026-11-01T09:30:00Z",
            ZoneId.of("America/Los_Angeles")))
    }

    @Test fun distancesSwitchUnitsAtKilometresAndMiles() {
        assertEquals("999 m", formatDistanceMeters(999.0, false, Locale.US))
        assertEquals("1.0 km", formatDistanceMeters(1000.0, false, Locale.US))
        assertEquals("2.4 km", formatDistanceMeters(2400.0, false, Locale.US))
        assertEquals("100 ft", formatDistanceMeters(30.48, true, Locale.US))
        assertEquals("1.0 mi", formatDistanceMeters(1609.344, true, Locale.US))
        assertEquals("2.0 mi", formatDistanceMeters(3218.688, true, Locale.US))
    }

    private val start = "2026-11-02T17:00:00Z"
    private val board = "2026-11-02T17:10:00Z"
    private val alight = "2026-11-02T17:40:00Z"
    private val end = "2026-11-02T17:50:00Z"
    private val transit = DirectionsLeg("BUS", startTime = board, endTime = alight)

    @Test fun groupsStreetAndTransitTimesInChronologicalOrder() {
        // Unknown walking geometry must not suppress access/egress times.
        val journey = DirectionsItinerary(start, end, legs = listOf(
            DirectionsLeg("WALK", startTime = start, endTime = board), transit,
            DirectionsLeg("BIKE", startTime = alight, endTime = end)))
        val groups = journey.previewTimeGroups()
        assertEquals(listOf(PreviewTimeMarker(start, false), PreviewTimeMarker(board, true)), groups.departure)
        assertEquals(listOf(PreviewTimeMarker(alight, true), PreviewTimeMarker(end, false)), groups.arrival)
        val direct = journey.copy(legs = listOf(DirectionsLeg("WALK"))).previewTimeGroups()
        assertEquals(listOf(PreviewTimeMarker(start, false)), direct.departure)
        assertEquals(listOf(PreviewTimeMarker(end, false)), direct.arrival)
    }

    @Test fun negligibleAccessUsesEndpointTimeAndDoesNotDuplicateTransitTime() {
        val tinyWalk = DirectionsLeg("WALK", legGeometry = DirectionsGeometry(
            encodeDirectionsPolyline(listOf(34.0 to -118.0, 34.00001 to -118.0))))
        val journey = DirectionsItinerary(start, end, legs = listOf(tinyWalk, transit, tinyWalk))
        assertEquals(listOf(PreviewTimeMarker(start, true)), journey.previewTimeGroups().departure)
        assertEquals(listOf(PreviewTimeMarker(end, true)), journey.previewTimeGroups().arrival)
        val sameTimes = journey.copy(startTime = board, endTime = alight,
            legs = listOf(DirectionsLeg("WALK"), transit, DirectionsLeg("WALK"))).previewTimeGroups()
        assertEquals(listOf(PreviewTimeMarker(board, true)), sameTimes.departure)
        assertEquals(listOf(PreviewTimeMarker(alight, true)), sameTimes.arrival)
    }

    @Test fun durationAllocationReservesAnIconForShortLegs() {
        val legs = listOf(DirectionsLeg("WALK", duration = 1.0), DirectionsLeg("RAIL", duration = 7200.0))
        val widths = timelineWeights(legs, 320f)
        assertEquals(48f, widths[0], 0.01f)
        assertEquals(320f, widths.sum(), 0.01f)
        assertEquals(listOf(40f, 40f), timelineWeights(legs, 80f))
        assertTrue(timelineWeights(emptyList(), 320f).isEmpty())
    }
}
