package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingSearchRowsTest {
    private fun station(id: Long) = SearchRow.OsmStationRow(
        OsmStationSearchResult(
            osmId = id, name = "Central", modeType = "train", confidence = 100.0
        ), 2.0
    )

    private fun stop(stationId: String?, chateau: String = "schweiz") = SearchRow.StopRow(
        StopRanking(gtfsId = "platform-1", chateau = chateau, score = 1.0),
        StopInfo(
            name = "Central", point = StopPoint(x = 8.5, y = 47.4),
            routes = emptyList(), osmStationId = stationId
        ),
        routes = emptyList(), agencyNames = emptyList(),
        distanceMetres = null, score = 1.0
    )

    @Test
    fun sameOsmStationSuppressesStopsAcrossGtfsFeeds() {
        val osm = station(123L)
        val first = stop("123", "schweiz")
        val second = stop("123", "deutschland")
        assertEquals(listOf(osm), filterRoutingSearchRows(listOf(first, osm, second)))
    }

    @Test
    fun distinctOrUnlinkedStopsRemainSelectable() {
        val osm = station(123L)
        val anotherStationStop = stop("456")
        val unlinked = stop(null)
        assertEquals(
            listOf(osm, anotherStationStop, unlinked),
            filterRoutingSearchRows(listOf(osm, anotherStationStop, unlinked))
        )
    }

    @Test
    fun stopRemainsWhenMatchingStationIsAbsent() {
        val gtfs = stop("123")
        assertEquals(listOf(gtfs), filterRoutingSearchRows(listOf(gtfs)))
    }
}
