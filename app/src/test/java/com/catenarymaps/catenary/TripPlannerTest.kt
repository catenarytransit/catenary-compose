package com.catenarymaps.catenary

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TripPlannerTest {
    private val from = DirectionsLocation("Start", 34.0, -118.0)
    private val to = DirectionsLocation("Finish", 35.0, -117.0)

    @Test fun locationActionsPreserveTheOtherEndpointAndFocusIt() {
        val current = DirectionsLocation("Current location", 34.1, -118.1, isCurrentLocation = true)
        for (isOrigin in listOf(true, false)) {
            val planner = TripPlannerViewModel()
            planner.choose(!isOrigin, to)
            planner.chooseFromLocationAction(isOrigin, from, current)
            assertEquals(from, if (isOrigin) planner.origin else planner.destination)
            assertEquals(to, if (isOrigin) planner.destination else planner.origin)
            assertEquals(!isOrigin, planner.editing)
        }
    }

    @Test fun locationActionsPreserveOppositeEndpointWithoutNearbyCurrentLocation() {
        val far = DirectionsLocation("Current location", 48.9, 2.3, isCurrentLocation = true)
        val invalid = DirectionsLocation("Current location", Double.NaN, 0.0, isCurrentLocation = true)
        for (current in listOf(far, invalid, null)) {
            for (isOrigin in listOf(true, false)) {
                val planner = TripPlannerViewModel()
                planner.choose(!isOrigin, to)
                planner.chooseFromLocationAction(isOrigin, from, current)
                assertEquals(from, if (isOrigin) planner.origin else planner.destination)
                assertEquals(to, if (isOrigin) planner.destination else planner.origin)
            }
        }
    }

    @Test fun freshEntryUsesCurrentOriginAndFocusesDestination() {
        val planner = TripPlannerViewModel()
        val current = from.copy(isCurrentLocation = true)
        planner.open(current)
        assertEquals(current, planner.origin)
        assertNull(planner.destination)
        assertEquals(false, planner.editing)
    }

    @Test fun delayedCurrentLocationDoesNotOverwriteAnOriginTheUserEdited() {
        val planner = TripPlannerViewModel()
        planner.open(null)
        planner.resolveCurrentOrigin(from.copy(isCurrentLocation = true))
        assertEquals(from.copy(isCurrentLocation = true), planner.origin)
        val edited = TripPlannerViewModel()
        edited.open(null)
        edited.edit(true, "My origin")
        edited.resolveCurrentOrigin(from)
        assertNull(edited.origin)
        assertEquals("My origin", edited.originText)
    }

    @Test fun entryPreservesExistingEndpointsAndUnresolvedText() {
        val planner = TripPlannerViewModel()
        planner.edit(true, "Typed origin")
        planner.open(from)
        assertEquals("Typed origin", planner.originText)
        assertNull(planner.origin)
        planner.choose(true, from)
        planner.choose(false, to)
        planner.open(from.copy(isCurrentLocation = true))
        assertEquals(from, planner.origin)
        assertEquals(to, planner.destination)
        assertNull(planner.editing)
    }

    @Test fun selectionFocusesTheOtherMissingEndpointAndClearRemovesCoordinates() {
        val planner = TripPlannerViewModel()
        planner.choose(true, from)
        assertEquals(false, planner.editing)
        planner.choose(false, to)
        assertNull(planner.editing)
        planner.edit(true, "")
        assertNull(planner.origin)
        assertEquals("", planner.originText)
        assertEquals(to, planner.destination)
        assertEquals(true, planner.editing)
    }

    @Test fun pagingStartsOnTheEdgeCardWithoutAnExtraSentinelSwipe() {
        assertEquals(ConnectionDirection.BEFORE, connectionPageAtEdge(1, 2, 5, 10))
        assertEquals(ConnectionDirection.AFTER, connectionPageAtEdge(-1, 8, 11, 10))
        assertNull(connectionPageAtEdge(-1, 2, 5, 10))
        assertNull(connectionPageAtEdge(0, 2, 11, 10))
        assertNull(connectionPageAtEdge(1, 0, 0, 0))
    }

    @Test fun requestUsesExclusiveTimeAndSiUnits() {
        val depart = directionsRequest(from, to, 1793642246, false, DirectionsOptions())
        assertEquals(1793642246L, depart["departure_unix_seconds"]!!.jsonPrimitive.long)
        assertFalse(depart.containsKey("arrival_unix_seconds"))
        assertEquals(1000.0 / 840, depart["pedestrianSpeed"]!!.jsonPrimitive.double, 1e-8)
        assertEquals(16.0 / 3.6, depart["cyclingSpeed"]!!.jsonPrimitive.double, 1e-6)
        assertEquals("WALK", depart["preTransitModes"]!!.jsonArray[0].jsonPrimitive.content)
        val arrive = directionsRequest(from, to, 1793642246, true,
            DirectionsOptions(cycling = true, walkingPace = 600f, cyclingKmh = 21.6f,
                accessSeconds = 720f, egressSeconds = 1320f, directSeconds = 2400f))
        assertFalse(arrive.containsKey("departure_unix_seconds"))
        assertTrue(arrive.containsKey("arrival_unix_seconds"))
        assertEquals("BIKE", arrive["postTransitModes"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals(6.0, arrive["cyclingSpeed"]!!.jsonPrimitive.double, 1e-6)
        assertEquals(720, arrive["maxPreTransitTime"]!!.jsonPrimitive.int)
        assertEquals(1320, arrive["maxPostTransitTime"]!!.jsonPrimitive.int)
        assertEquals(2400, arrive["maxDirectTime"]!!.jsonPrimitive.int)
        assertTrue(arrive["detailed_legs"]!!.jsonPrimitive.boolean)
    }

    @Test fun cyclingBudgetsStayWithinEdelweissContract() {
        val request = directionsRequest(from, to, 1793642246, false,
            DirectionsOptions(cycling = true, accessSeconds = 7200f,
                egressSeconds = 0f, directSeconds = 14400f))
        assertEquals(3600, request["maxPreTransitTime"]!!.jsonPrimitive.int)
        assertEquals(1, request["maxPostTransitTime"]!!.jsonPrimitive.int)
        assertEquals(3600, request["maxDirectTime"]!!.jsonPrimitive.int)
        assertEquals("BIKE", request["preTransitModes"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test fun acceptsSignedCardinalAndDmsCoordinates() {
        for (text in listOf("34.5, -118.25", "34.5 N, 118.25 W", "34°30′0″N 118°15′0″W", "34.5 -118.25")) {
            val coordinate = parseDirectionsCoordinate(text)
            assertNotNull(text, coordinate)
            assertEquals(34.5, coordinate!!.lat, 1e-8)
            assertEquals(-118.25, coordinate.lon, 1e-8)
        }
        for (text in listOf("91, 0", "0, 181", "NaN, 0", "34, -118 N", "34°99′N 118°W", "-34 N, 118 W")) {
            assertNull(text, parseDirectionsCoordinate(text))
        }
    }

    @Test fun decodesPrecisionAndRejectsMalformedShapes() {
        val standard = decodeDirectionsPolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(3, standard.size)
        assertEquals(38.5, standard[0].first, 1e-6)
        assertEquals(-120.2, standard[0].second, 1e-6)
        assertTrue(decodeDirectionsPolyline("_").isEmpty())
        assertTrue(decodeDirectionsPolyline("~~~~~~~").isEmpty())
        assertTrue(decodeDirectionsPolyline("abc", -1).isEmpty())
        val points = listOf(34.0 to -118.0, 34.001234 to -118.009876)
        assertEquals(points, decodeDirectionsPolyline(encodeDirectionsPolyline(points)))
    }

    @Test fun clipsFullTripGeometryInBothDirections() {
        val points = listOf(34.0 to -118.0, 34.0 to -117.99, 34.0 to -117.98, 34.0 to -117.97)
        val leg = DirectionsLeg(mode = "RAIL", from = DirectionsPoint(lat = 34.0, lon = -117.987),
            to = DirectionsPoint(lat = 34.0, lon = -117.974), legGeometry = DirectionsGeometry(encodeDirectionsPolyline(points)))
        val clipped = directionsCoordinates(leg)
        assertEquals(3, clipped.size)
        assertEquals(-117.987, clipped.first().second, 1e-6)
        assertEquals(-117.974, clipped.last().second, 1e-6)
        assertEquals(clipped.reversed(), directionsCoordinates(leg.copy(from = leg.to, to = leg.from)))
    }

    @Test fun appendsDirectJourneysAfterBackendRankedTransit() {
        val json = Json { ignoreUnknownKeys = true }
        val plan = json.decodeFromString<DirectionsPlan>("""{
            "itineraries":[{"startTime":"2026-11-02T17:00:00Z","endTime":"2026-11-02T17:30:00Z","legs":[{"mode":"BUS","routeShortName":"42"}]}],
            "direct":[{"startTime":"2026-11-02T17:00:00Z","endTime":"2026-11-02T17:20:00Z","legs":[{"mode":"WALK"}]}]
        }""")
        assertEquals(listOf("BUS", "WALK"), (plan.itineraries + plan.direct).map { it.legs.first().mode })
    }
}
