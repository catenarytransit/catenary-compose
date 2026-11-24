package com.catenarymaps.catenary

import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class JourneyTripInfoTest {
    private val departure = Instant.parse("2026-11-14T09:20:00Z").epochSecond
    private val arrival = departure + 600
    private val leg = DirectionsLeg(
        mode = "BUS",
        chateau = "test",
        tripId = "loop",
        routeId = "1",
        routeType = 3,
        tripStartDate = "20261113",
        tripStartTime = "25:00:00",
        startTime = Instant.ofEpochSecond(departure).toString(),
        endTime = Instant.ofEpochSecond(arrival).toString(),
        from = DirectionsPoint(name = "A", stopId = "a"),
        to = DirectionsPoint(name = "A", stopId = "a"),
        fromStopSequence = 30,
        toStopSequence = 50
    )
    private val segment = leg.singleTripInfo()!!.journeySegment!!
    private fun stop(id: String, sequence: Int, time: Long? = null) = TripStoptime(
        stop_id = id, longitude = 0.0, latitude = 0.0, gtfs_stop_sequence = sequence,
        scheduled_departure_time_unix_seconds = time, scheduled_arrival_time_unix_seconds = time
    )

    private val stops = listOf(
        stop("a", 10, departure - 600), stop("b", 20),
        stop("a", 30, departure), stop("b", 40), stop("a", 50, arrival), stop("c", 60)
    )

    @Test
    fun opensExactTripInstanceAcrossMidnight() {
        val trip = leg.singleTripInfo()!!
        assertEquals("test", trip.chateau_id)
        assertEquals("loop", trip.trip_id)
        assertEquals("1", trip.route_id)
        assertEquals("20261113", trip.start_date)
        assertEquals("25:00:00", trip.start_time)
        assertEquals(3, trip.route_type)
    }

    @Test
    fun repeatedStopVisitsUseSequenceAndInclusiveRange() {
        assertEquals(JourneyTripRange(2, 4), resolveJourneyTripRange(stops, segment))
    }

    @Test
    fun missingSequencesUseScheduledTimesForLoopVisits() {
        assertEquals(
            JourneyTripRange(2, 4),
            resolveJourneyTripRange(stops.map { it.copy(gtfs_stop_sequence = null) }, segment)
        )
    }

    @Test
    fun unresolvedAndReversedSelectionsDoNotHighlight() {
        assertNull(resolveJourneyTripRange(stops, null))
        assertNull(
            resolveJourneyTripRange(
                stops,
                segment.copy(from = segment.from.copy(stopId = "missing"))
            )
        )
        assertNull(resolveJourneyTripRange(stops, JourneyTripSegment(segment.to, segment.from)))
    }

    @Test
    fun boardingAndAlightingSplitConnectorContrastAtThePearl() {
        val range = JourneyTripRange(2, 4)
        assertEquals(JourneyStopPresentation(false, false, false, false), range.stop(1))
        assertEquals(JourneyStopPresentation(true, true, false, true), range.stop(2))
        assertEquals(JourneyStopPresentation(true, false, true, true), range.stop(3))
        assertEquals(JourneyStopPresentation(true, true, true, false), range.stop(4))
        assertEquals(JourneyStopPresentation(false, false, false, false), range.stop(5))
    }

    @Test
    fun missingTripMetadataAndStreetLegsDoNotOfferNavigation() {
        assertNull(leg.copy(tripId = null).singleTripInfo())
        assertNull(leg.copy(tripStartDate = null).singleTripInfo())
        assertNull(leg.copy(mode = "WALK").singleTripInfo())
        assertNull(leg.copy(startTime = "invalid").singleTripInfo())
    }

    @Test
    fun plannerAndNavigationContextSurviveSerialization() {
        val restoredLeg = Json.decodeFromString<DirectionsLeg>(Json.encodeToString(leg))
        assertEquals(leg.singleTripInfo(), restoredLeg.singleTripInfo())
        val trip: CatenaryStackEnum = leg.singleTripInfo()!!
        assertEquals(trip, Json.decodeFromString<CatenaryStackEnum>(Json.encodeToString(trip)))
    }

    @Test
    fun olderCachedLegsRemainReadableAndOrdinaryTripsHaveNoSelection() {
        val olderLeg = Json.decodeFromString<DirectionsLeg>("""{"mode":"BUS"}""")
        assertNull(olderLeg.singleTripInfo())
        val olderTrip =
            Json.decodeFromString<CatenaryStackEnum.SingleTrip>("""{"chateau_id":"test","trip_id":"trip","route_id":null,"start_time":null,"start_date":null,"vehicle_id":null,"route_type":3}""")
        assertNull(olderTrip.journeySegment)
    }
}
