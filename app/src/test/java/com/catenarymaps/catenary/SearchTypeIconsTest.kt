package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTypeIconsTest {
    @Test fun transitModesUseCopiedSvgIcons() {
        assertEquals(SearchTypeIcons.Tram, SearchTypeIcons.route(0))
        assertEquals(SearchTypeIcons.Metro, SearchTypeIcons.route(1))
        assertEquals(SearchTypeIcons.Train, SearchTypeIcons.route(2))
        assertEquals(SearchTypeIcons.Bus, SearchTypeIcons.route(3))
        assertEquals(SearchTypeIcons.Train, SearchTypeIcons.route(100))
        assertEquals(SearchTypeIcons.Bus, SearchTypeIcons.route(700))
        assertEquals(SearchTypeIcons.Metro, SearchTypeIcons.mode("subway"))
        assertEquals(SearchTypeIcons.Tram, SearchTypeIcons.mode("light_rail"))
        assertEquals(SearchTypeIcons.Location, SearchTypeIcons.route(null))
    }

    @Test fun cypressCategoriesDeterminePlaceIcons() {
        val categories = listOf(
            "tourism:hotel" to SearchTypeIcons.Hotel,
            "amenity:restaurant" to SearchTypeIcons.Restaurant,
            "leisure:park" to SearchTypeIcons.Park,
            "amenity:townhall" to SearchTypeIcons.Government,
            "shop:supermarket" to SearchTypeIcons.Shopping,
            "shop:clothes" to SearchTypeIcons.Shopping,
            "amenity:marketplace" to SearchTypeIcons.Shopping,
            "railway:station" to SearchTypeIcons.Train
        )
        categories.forEach { (category, expected) ->
            assertEquals(category, expected, SearchTypeIcons.place(listOf(category)))
        }
    }

    @Test fun unknownPlacesFallBackToLocationPin() {
        assertEquals(SearchTypeIcons.Location, SearchTypeIcons.place(listOf("amenity:library")))
        assertEquals(SearchTypeIcons.Location, SearchTypeIcons.place(emptyList(), "address"))
    }
}
