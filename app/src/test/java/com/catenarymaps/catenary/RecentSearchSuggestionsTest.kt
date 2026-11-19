package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSearchSuggestionsTest {
    private val savedAddress = DirectionsLocation("1604 S Primrose Ave", 33.7421, -117.8842)

    private fun stationHistory(id: String, name: String, address: String? = null) =
        RecentStation(
            kind = RecentStationKind.OSM_ITEM,
            id = id,
            osmClass = "way",
            name = name,
            address = address,
            latitude = savedAddress.lat,
            longitude = savedAddress.lon,
        )

    private fun cypress(name: String, lat: Double = savedAddress.lat, lon: Double = savedAddress.lon): SearchRow {
        return SearchRow.CypressRow(
            CypressFeature(
                type = "Feature",
                geometry = CypressGeometry("Point", listOf(lon, lat)),
                properties = CypressPropertiesV2(
                    id = "123", layer = "address", name = name, confidence = 950.0
                ),
            ),
            2.0,
        )
    }

    @Test
    fun prefixAppearsBeforeRemoteResultsIncludingWhenOnlyFourDigitsTyped() {
        val suggestions = matchingRecentDirections(
            "1604", listOf(savedAddress, DirectionsLocation("Other Place", 34.0, -118.0)),
            emptyList()
        )
        assertEquals(listOf(savedAddress), suggestions)
        assertEquals(listOf(savedAddress), matchingRecentDirections("  1604 s", listOf(savedAddress), emptyList()))
        assertTrue(matchingRecentDirections("", listOf(savedAddress), emptyList()).isEmpty())
    }

    @Test
    fun matchesCaseInsensitiveAddressAndStationHistoryAddressField() {
        val visit = stationHistory("8", "Home", "1604 S Primrose Ave")
        val matched = matchingRecentStationHistory("1604", listOf(visit))
        assertEquals(listOf(visit), matched)
        assertEquals("Home", matchingRecentDirections("1604", emptyList(), listOf(visit)).single().name)
    }

    @Test
    fun historyIsPromotedOnceEvenIfBothStoresContainIt() {
        val visit = stationHistory("8", savedAddress.name)
        assertEquals(
            listOf(savedAddress),
            matchingRecentDirections("1604", listOf(savedAddress), listOf(visit))
        )
    }

    @Test
    fun excludeIncomingDuplicateAndKeepNearbyDistinctAddresses() {
        val matching = cypress("1604 South Primrose Avenue", 33.7421, -117.8842)
        val different = cypress("1606 S Primrose Ave", 33.7422, -117.8842)
        val distantSameName = cypress("1604 S Primrose Ave", 34.1, -118.3)
        assertEquals(
            listOf(different, distantSameName),
            withoutMatchingDirectionsResults(
                listOf(matching, different, distantSameName),
                listOf(savedAddress),
            )
        )
    }

    @Test
    fun differentVisitedPlacesAreNotHiddenByAnAddressOnlyQuery() {
        val match = stationHistory("11", "1604 S Primrose Ave")
        val other = cypress("1606 S Primrose Ave")
        val exact = cypress("1604 S Primrose Ave")
        assertEquals(
            listOf(other),
            withoutMatchingVisitedResults(listOf(exact, other), listOf(match)),
        )
        assertFalse(isMatchingRecentQuery("1604", "1606 S Primrose Ave"))
        assertTrue(isMatchingRecentQuery("PRIM", savedAddress.name))
    }

    @Test
    fun sameNameButDifferentCoordinatesStillRemainsInResults() {
        val sameNameFarAway = cypress(savedAddress.name, 34.0, -118.0)
        assertEquals(
            listOf(sameNameFarAway),
            withoutMatchingDirectionsResults(listOf(sameNameFarAway), listOf(savedAddress))
        )
    }
}
