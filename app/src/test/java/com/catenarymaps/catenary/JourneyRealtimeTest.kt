package com.catenarymaps.catenary

import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class JourneyRealtimeTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val date = "20261119"
    private val chateau = "chateau"
    private val depart = "2026-11-19T12:00:00Z"
    private val arrive = "2026-11-19T12:30:00Z"

    private fun leg(date: String = this.date) = DirectionsLeg(
        mode = "BUS", chateau = chateau, tripId = "shared-trip",
        tripStartDate = date, tripStartTime = "25:00:00", routeId = "R",
        fromStopSequence = 5, toStopSequence = 9,
        from = DirectionsPoint(stopId = "origin", timezone = "UTC"),
        to = DirectionsPoint(stopId = "destination", timezone = "UTC"),
        startTime = depart, endTime = arrive,
        serviceDeparture = DirectionsEventTime(timetabledTime = depart),
        serviceArrival = DirectionsEventTime(timetabledTime = arrive)
    )

    @Test fun v2OjpTimesAndQuaysDeserializeWithoutV1EventFields() {
        val leg = json.decodeFromString<DirectionsLeg>("""{
            "mode":"BUS","startTime":"2026-11-19T12:04:00Z",
            "ServiceDeparture":{"TimetabledTime":"2026-11-19T12:00:00Z",
                "EstimatedTime":"2026-11-19T12:04:00Z"},
            "ServiceArrival":{"RecordedAtTime":"2026-11-19T12:30:30Z"},
            "from":{"stopId":"origin","PlannedQuay":"4","EstimatedQuay":"5"}
        }""")
        assertEquals(240L, leg.departureDelaySeconds)
        assertEquals("5", leg.from?.estimatedQuay)
        assertEquals("4", leg.from?.plannedQuay)
        assertEquals("2026-11-19T12:30:30Z", leg.serviceArrival?.recordedAtTime)
    }

    @Test fun sameTripIdInDifferentServiceDaysNeverSharesRealtime() {
        val first = leg()
        val second = leg("20261120")
        val old = first.ramondaIdentity()!!
        val other = second.ramondaIdentity()!!
        assertNotEquals(old, other)
        val updated = TripRtUpdateData(
            trip_id = "shared-trip", chateau = chateau,
            stoptimes = listOf(StopTimeRefresh(
                stop_id = "origin", gtfs_stop_sequence = 5,
                rt_departure = RtTime(Instant.parse(depart).epochSecond + 120),
                rt_platform_string = "7"
            ))
        )
        val snapshots = mapOf(old to RamondaTripSnapshot(latestUpdate = updated))
        val tripA = DirectionsItinerary(depart, arrive, legs = listOf(first)).withRamonda(snapshots)
        val tripB = DirectionsItinerary(depart, arrive, legs = listOf(second)).withRamonda(snapshots)
        assertEquals(120L, tripA.legs[0].departureDelaySeconds)
        assertEquals("7", tripA.legs[0].from?.estimatedQuay)
        assertNull(tripB.legs[0].departureDelaySeconds)
        assertNull(tripB.legs[0].from?.estimatedQuay)
    }

    @Test fun updatesMatchExactSequenceNotOnlyRepeatedStopIds() {
        val start = leg().copy(from = DirectionsPoint(stopId = "loop"),
            to = DirectionsPoint(stopId = "loop"))
        val update = TripRtUpdateData(stoptimes = listOf(
            StopTimeRefresh(stop_id = "loop", gtfs_stop_sequence = 9,
                rt_arrival = RtTime(Instant.parse(arrive).epochSecond + 60)),
            StopTimeRefresh(stop_id = "loop", gtfs_stop_sequence = 5,
                rt_departure = RtTime(Instant.parse(depart).epochSecond + 240))
        ))
        val result = DirectionsItinerary(depart, arrive, legs = listOf(start))
            .withRamonda(mapOf(start.ramondaIdentity()!! to RamondaTripSnapshot(latestUpdate = update)))
        assertEquals(240L, result.legs[0].departureDelaySeconds)
        assertEquals(60L, result.legs[0].arrivalDelaySeconds)
    }

    @Test fun zeroDelayNotShownAndSignedMinutesRemainCorrect() {
        assertNull(directionsDelaySeconds(depart, depart))
        assertEquals(-120L, directionsDelaySeconds(
            "2026-11-19T11:58:00Z", depart))
        assertEquals(60L, directionsDelaySeconds(
            "2026-11-19T12:01:00Z", depart))
        assertNull(directionsDelaySeconds("broken", depart))
    }

    @Test fun subscriptionPayloadRoundTripsWithCompleteIdentity() {
        val id = leg().ramondaIdentity()!!
        val subscribe = id.subscribe()
        val encoded = json.encodeToString(SubscribeTrip.serializer(), subscribe)
        val restored = json.decodeFromString<SubscribeTrip>(encoded)
        assertEquals(id, restored.identity())
        assertTrue(encoded.contains("\"trip_id\":\"shared-trip\""))
        assertTrue(encoded.contains("\"start_date\":\"20261119\""))
        assertTrue(encoded.contains("\"start_time\":\"25:00:00\""))
    }
}
