package com.catenarymaps.catenary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDepartureFiltersTest {
    private fun busVisible(filters: NearbyDepartureFilters, routeType: Int = 3) =
        isAllowedByFilter(routeType, filters, false, false, false)

    @Test fun savedRailFilterOnBusOnlyResponseRemainsRecoverable() {
        val saved = NearbyDepartureFilters(rail = true)
        // Reproduces a successful Santa Cruz response with every route filtered out.
        assertFalse(busVisible(saved))
        assertTrue(saved.shouldShowControls(hasNonBusDepartures = false))
        assertTrue(saved.isActive)
        val reset = NearbyDepartureFilters()
        for (type in listOf(3, 11, 700)) assertTrue(busVisible(reset, type))
        assertFalse(reset.isActive)
    }

    @Test fun everySavedSelectionKeepsControlsVisibleWithoutNonBusResults() {
        val selections = listOf(
            NearbyDepartureFilters(rail = true), NearbyDepartureFilters(metro = true),
            NearbyDepartureFilters(tram = true), NearbyDepartureFilters(bus = true),
            NearbyDepartureFilters(other = true), NearbyDepartureFilters(funicular = true),
            NearbyDepartureFilters(monorail = true), NearbyDepartureFilters(cableTram = true)
        )
        selections.forEach {
            assertTrue(it.isActive)
            assertTrue(it.shouldShowControls(hasNonBusDepartures = false))
        }
    }

    @Test fun unfilteredBusOnlyResponseKeepsCompactControls() {
        val filters = NearbyDepartureFilters()
        assertTrue(busVisible(filters))
        assertFalse(filters.shouldShowControls(hasNonBusDepartures = false))
        assertTrue(filters.shouldShowControls(hasNonBusDepartures = true))
    }

    @Test fun togglingBusOnRecoversResultsWithoutClearingOtherSelections() {
        val saved = NearbyDepartureFilters(metro = true, tram = true)
        assertFalse(busVisible(saved))
        assertTrue(busVisible(saved.copy(bus = true)))
        assertTrue(busVisible(saved.copy(metro = false, tram = false)))
    }
}
