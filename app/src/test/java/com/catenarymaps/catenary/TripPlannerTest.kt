package com.catenarymaps.catenary

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TripPlannerTest {
    private val from = DirectionsLocation("Start", 34.0, -118.0)
    private val to = DirectionsLocation("Finish", 35.0, -117.0)

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
