package com.catenarymaps.catenary

import java.io.IOException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NearbyDepartureStreamTest {
    private fun departure(time: Long = 100, realtime: Long? = null) = LocalDepartureItem(
        tripId = "loop", serviceDate = "2026-11-10", stopId = "stop",
        departureSchedule = time, departureRealtime = realtime, cancelled = false, lastStop = false
    )
    private fun route(feed: String = "city", headsigns: Map<String, List<LocalDepartureItem>>) = RouteGroupV3(
        chateauId = feed, routeId = "1", routeType = 3, closestDistance = 50.0, headsigns = headsigns
    )
    private fun frame(phase: String, data: NearbyResponseV3, id: String = "request") =
        NearbyDepartureMessage("nearby_departures", id, phase, phase != "priority", data)

    @Test fun combinesDirectionsWithoutDuplicatingRoutesTripsOrLoopVisits() {
        val first = NearbyResponseV3(local = listOf(route(headsigns = mapOf("North" to listOf(departure())))))
        val second = NearbyResponseV3(local = listOf(
            route(headsigns = mapOf("North" to listOf(departure(realtime = 110), departure(200)),
                "South" to listOf(departure(300)))),
            route("other", mapOf("North" to listOf(departure())))
        ))
        val merged = mergeNearbyDepartures(first, second)
        assertEquals(2, merged.local.size)
        val city = merged.local.single { it.chateauId == "city" }
        assertEquals(setOf("North", "South"), city.headsigns.keys)
        assertEquals(listOf(100L, 200L), city.headsigns.getValue("North").map { it.departureSchedule })
        assertEquals(110L, city.headsigns.getValue("North").first().departureRealtime)
        assertEquals(merged, mergeNearbyDepartures(merged, second))
    }

    @Test fun retainsBothPhasesOfRouteAndStopDictionaries() {
        val first = NearbyResponseV3(routes = mapOf("city" to mapOf("1" to RouteInfoExport(routeType = 1))),
            stops = mapOf("city" to mapOf("station" to StopOutputV3("station", "Station", 0.0, 0.0, timezone = "UTC"))))
        val second = NearbyResponseV3(routes = mapOf("city" to mapOf("2" to RouteInfoExport(routeType = 3)),
            "other" to mapOf("1" to RouteInfoExport(routeType = 2))))
        val merged = mergeNearbyDepartures(first, second)
        assertEquals(setOf("1", "2"), merged.routes.getValue("city").keys)
        assertEquals(2, merged.routes.getValue("other").getValue("1").routeType)
        assertEquals(first.stops, merged.stops)
    }

    @Test fun matchesRequestAndCombinesPhasesBeforeCompleting() {
        val state = NearbyDepartureStreamState("request")
        val snapshots = mutableListOf<NearbyResponseV3>()
        val first = NearbyResponseV3(local = listOf(route(headsigns = mapOf("North" to listOf(departure())))))
        val remaining = NearbyResponseV3(local = listOf(route(headsigns = mapOf("South" to listOf(departure(200))))))
        assertFalse(state.accept(frame("full", first, "stale"), snapshots::add))
        assertFalse(state.accept(frame("priority", first), snapshots::add))
        assertFalse(state.accept(frame("priority", first), snapshots::add))
        assertEquals(1, snapshots.size)
        assertTrue(state.accept(frame("remaining", remaining), snapshots::add))
        assertEquals(setOf("North", "South"), snapshots.last().local.single().headsigns.keys)
        assertFalse(state.accept(frame("full", NearbyResponseV3()), snapshots::add))
        assertEquals(2, snapshots.size)
        // A subsequent full request can remove routes and expired trips.
        assertTrue(NearbyDepartureStreamState("refresh").accept(
            frame("full", NearbyResponseV3(), "refresh"), snapshots::add))
        assertTrue(snapshots.last().local.isEmpty())
    }

    @Test fun wireFieldsMatchBackendAndEmptyRemainingStillCompletes() {
        val state = NearbyDepartureStreamState("request")
        val json = Json { ignoreUnknownKeys = true }
        val snapshots = mutableListOf<NearbyResponseV3>()
        for (phase in listOf("priority", "remaining")) {
            val message = json.decodeFromString<NearbyDepartureMessage>("""
                {"type":"nearby_departures","request_id":"request","phase":"$phase",
                 "complete":${phase == "remaining"},"data":{"long_distance":[],"local":[],"routes":{},"stops":{}}}
            """)
            assertEquals(phase == "remaining", state.accept(message, snapshots::add))
        }
        assertEquals(2, snapshots.size)
    }

    @Test fun mergesOsmStationDeparturesAndKeepsDifferentFeeds() {
        fun departure(feed: String) = DepartureItemV3(
            serviceDate = "2026-11-10", headsign = "City", tripId = "t", routeId = "r",
            stopId = "s", cancelled = false, delayed = false, chateauId = feed, lastStop = false,
            scheduledDeparture = 100
        )
        fun station(departures: List<DepartureItemV3>) = StationDepartureGroup(
            stationName = "Central", osmStationId = 123, distanceM = 200.0,
            departures = departures, lat = 1.0, lon = 2.0, timezone = "UTC"
        )
        val first = NearbyResponseV3(longDistance = listOf(station(listOf(departure("one")))))
        val second = NearbyResponseV3(longDistance = listOf(station(listOf(
            departure("one").copy(realtimeDeparture = 110), departure("two")))))
        val merged = mergeNearbyDepartures(first, second)
        assertEquals(1, merged.longDistance.size)
        assertEquals(2, merged.longDistance.single().departures.size)
        assertEquals(110L, merged.longDistance.single().departures.single { it.chateauId == "one" }.realtimeDeparture)
    }

    @Test(expected = IOException::class) fun oldServerErrorsTriggerFallbackImmediately() {
        NearbyDepartureStreamState("request").accept(NearbyDepartureMessage("error", message = "Unknown request")) {}
    }

    @Test(expected = IOException::class) fun errorsDoNotCompleteWithPartialData() {
        val state = NearbyDepartureStreamState("request")
        state.accept(frame("priority", NearbyResponseV3())) {}
        state.accept(NearbyDepartureMessage("nearby_departures_error", "request", message = "Failed")) {}
    }
}
