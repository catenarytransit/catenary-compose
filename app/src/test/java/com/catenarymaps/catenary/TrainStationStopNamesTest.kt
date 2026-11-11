package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainStationStopNamesTest {
    @Test fun shortensOnlyWhenEveryStopInTheRailTripEndsInStation() {
        val names = listOf("Central Station", "Union Station", "Airport Station")
        for (routeType in 0..2) {
            assertTrue(shouldShortenTrainStationStops(routeType, names))
        }
        assertEquals("Central", displayTrainStationStopName(names.first(), true))
        assertEquals("Airport", displayTrainStationStopName(names.last(), true))
    }

    @Test fun keepsMixedNamingAndNonRailTripsUntouched() {
        val names = listOf("Central Station", "Town Hall")
        assertFalse(shouldShortenTrainStationStops(2, names))
        assertFalse(shouldShortenTrainStationStops(3, listOf("Central Station", "Union Station")))
        assertFalse(shouldShortenTrainStationStops(null, listOf("Central Station", "Union Station")))
        assertFalse(shouldShortenTrainStationStops(2, emptyList()))
        assertFalse(shouldShortenTrainStationStops(2, listOf("Central Station", null)))
        assertFalse(shouldShortenTrainStationStops(2, listOf("Central Station", "")))
        assertEquals("Central Station", displayTrainStationStopName("Central Station", false))
    }

    @Test fun matchesOnlyAWholeTrailingWordWithoutErasingTheEntireName() {
        assertTrue(shouldShortenTrainStationStops(1, listOf("Central STATION  ", "Union Station")))
        assertEquals("Central", displayTrainStationStopName("Central STATION  ", true))
        assertFalse(shouldShortenTrainStationStops(2, listOf("Central Station", "Station")))
        assertFalse(shouldShortenTrainStationStops(2, listOf("Central Station", "Substation")))
        assertFalse(shouldShortenTrainStationStops(2, listOf("Central Station", "Station Road")))
    }
}
