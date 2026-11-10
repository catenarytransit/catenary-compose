package com.catenarymaps.catenary

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class DepartureRouteFiltersTest {
    @Test
    fun mixedNamesSortConsistentlyWithoutNumericOverflow() {
        val names = listOf("Airport", "30", "29A", "29", "2", "10", "999999999999999999999")
        assertEquals(
            listOf("2", "10", "29", "29A", "30", "999999999999999999999", "Airport"),
            names.sortedWith(departureRouteNameComparator)
        )
        val samples = names + listOf("", "0", "00", "02", "2A", "2a", "A2", "A10", "-1", "Étoile")
        val cmp = departureRouteNameComparator
        for (a in samples) for (b in samples) {
            assertEquals(cmp.compare(a, b).compareTo(0), -cmp.compare(b, a).compareTo(0))
            for (c in samples) {
                if (cmp.compare(a, b) <= 0 && cmp.compare(b, c) <= 0) {
                    assertTrue(cmp.compare(a, c) <= 0)
                }
            }
        }
        val large = (0..100).flatMap { listOf("$it", "${it}A", "A$it") }
        val expected = large.sortedWith(cmp)
        repeat(20) { assertEquals(expected, large.shuffled(Random(it)).sortedWith(cmp)) }
    }

    @Test
    fun groupsOnlyMatchingNamesAndColoursWithinChateau() {
        val route = StopRouteInfo(color = "#aabbcc", text_color = "FFFFFF", short_name = "29")
        val filters = departureRouteFilters(mapOf(
            "a" to mapOf("one" to route, "two" to route.copy(color = "AABBCC"),
                "three" to route.copy(color = "000000")),
            "b" to mapOf("one" to route)
        ))
        assertEquals(3, filters.size)
        val group = filters.single { "two" in it.routeIds }
        assertEquals(setOf("one", "two"), group.routeIds)
        val selected = setOf(group.key)
        assertTrue(matchesDepartureRouteFilters("a", "one", selected, filters))
        assertTrue(matchesDepartureRouteFilters("a", "two", selected, filters))
        assertFalse(matchesDepartureRouteFilters("a", "three", selected, filters))
        assertFalse(matchesDepartureRouteFilters("b", "one", selected, filters))
        assertTrue(matchesDepartureRouteFilters("b", "one", emptySet(), filters))
        assertTrue(matchesDepartureRouteFilters("b", "one", selected + filters.single { it.key.chateau == "b" }.key, filters))
    }

    @Test
    fun fallsBackToLongNameThenRouteId() {
        val filters = departureRouteFilters(mapOf("a" to mapOf(
            "id" to StopRouteInfo("000000", "FFFFFF", short_name = " ", long_name = "Airport"),
            "29" to StopRouteInfo("000000", "FFFFFF")
        )))
        assertEquals(listOf("29", "Airport"), filters.map { it.key.name })
    }
}
