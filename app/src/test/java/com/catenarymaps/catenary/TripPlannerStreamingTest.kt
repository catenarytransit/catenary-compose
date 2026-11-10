package com.catenarymaps.catenary

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TripPlannerStreamingTest {
    private fun journey(id: String, hour: Int, mode: String = "BUS") = DirectionsItinerary(
        startTime = "2026-11-09T${hour}:00:00Z", endTime = "2026-11-09T${hour}:30:00Z",
        legs = listOf(DirectionsLeg(mode)), id = id
    )

    @Test fun pagesKeepTheOriginalQueryAndUseWebPageSizes() {
        val base = directionsRequest(DirectionsLocation("A", 34.0, -118.0),
            DirectionsLocation("B", 35.0, -117.0), 1793642246, true, DirectionsOptions(cycling = true))
        val initial = directionsPageQuery(base, ConnectionDirection.BEFORE)
        val earlier = directionsPageQuery(base, ConnectionDirection.BEFORE, "opaque-before")
        val later = directionsPageQuery(base, ConnectionDirection.AFTER, "opaque-after")
        for (query in listOf(initial, earlier, later)) base.forEach { (key, value) -> assertEquals(value, query[key]) }
        assertEquals(8, initial["number_of_results_before"]!!.jsonPrimitive.int)
        assertEquals(4, earlier["number_of_results_before"]!!.jsonPrimitive.int)
        assertEquals(0, earlier["number_of_results_after"]!!.jsonPrimitive.int)
        assertEquals(5, later["number_of_results_after"]!!.jsonPrimitive.int)
        assertEquals("opaque-after", later["page_cursor"]!!.jsonPrimitive.content)
    }

    @Test fun snapshotsReplacePagesAndKeepAllConnectionsChronological() {
        val earlier = journey("earlier", 10)
        val initial = journey("initial", 11)
        val later = journey("later", 12)
        val direct = journey("walk", 10, "WALK")
        val pages = linkedMapOf("first" to DirectionsPlan(listOf(initial), listOf(direct)))
        pages["next"] = DirectionsPlan(listOf(journey("discarded-snapshot", 13)))
        pages["next"] = DirectionsPlan(listOf(later, initial), listOf(direct))
        pages["before"] = DirectionsPlan(listOf(earlier, initial))
        assertEquals(listOf("earlier", "walk", "initial", "later"), mergeDirectionsPages(pages.values.toList()).map { it.id })
    }

    @Test fun streamIgnoresOtherRequestsAndOldSequencesAndAcceptsFinalPage() {
        val state = DirectionsStreamState("request-1")
        val received = mutableListOf<DirectionsPlan>()
        val partial = DirectionsPlan(listOf(journey("partial", 11)))
        val final = DirectionsPlan(listOf(journey("final", 12)), previousPageCursor = "older", nextPageCursor = "newer")
        assertFalse(state.accept(DirectionsStreamMessage("accepted", "request-1"), received::add))
        state.accept(DirectionsStreamMessage("snapshot", "other", 10, final), received::add)
        state.accept(DirectionsStreamMessage("snapshot", "request-1", 2, partial), received::add)
        state.accept(DirectionsStreamMessage("snapshot", "request-1", 1, final), received::add)
        state.accept(DirectionsStreamMessage("snapshot", "request-1", 2, final), received::add)
        assertEquals(listOf(partial), received)
        assertTrue(state.accept(DirectionsStreamMessage("complete", "request-1", response = final), received::add))
        assertEquals(listOf(partial, final), received)
    }

    @Test fun wireTypesDecodeBackendCamelCaseFieldsAndStableIds() {
        val message = Json { ignoreUnknownKeys = true }.decodeFromString<DirectionsStreamMessage>("""
            {"type":"complete","requestId":"r","response":{"itineraries":[
              {"id":"stable","startTime":"2026-11-09T10:00:00Z","endTime":"2026-11-09T10:30:00Z","legs":[]}],
              "previousPageCursor":"before","nextPageCursor":"after","metadata":{}}}
        """)
        assertEquals("stable", message.response!!.itineraries.single().identity())
        assertEquals("before", message.response.previousPageCursor)
        assertEquals("after", message.response.nextPageCursor)
    }

    @Test fun walkingWithoutGeometryUsesValidEndpointsButCyclingNeedsItsShape() {
        val from = DirectionsPoint("A", 34.0, -118.0)
        val to = DirectionsPoint("B", 34.01, -118.01)
        val walk = DirectionsLeg("WALK", from = from, to = to)
        assertEquals(listOf(34.0 to -118.0, 34.01 to -118.01), directionsCoordinates(walk))
        assertNull(directionsDistance(walk))
        assertEquals(directionsCoordinates(walk), directionsCoordinates(walk.copy(mode = "FOOT")))
        assertTrue(directionsCoordinates(walk.copy(mode = "BIKE")).isEmpty())
        assertTrue(directionsCoordinates(walk.copy(to = to.copy(lat = Double.NaN))).isEmpty())
        val shape = encodeDirectionsPolyline(listOf(34.0 to -118.0, 34.02 to -118.02, 34.01 to -118.01))
        assertEquals(3, directionsCoordinates(walk.copy(mode = "BIKE", legGeometry = DirectionsGeometry(shape))).size)
    }
}
