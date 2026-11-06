package com.catenarymaps.catenary

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRouteSortingTest {
    private val comparator = connectionRouteNameComparator

    @Test
    fun mixedNumericAndAlphanumericNamesHaveConsistentOrder() {
        assertEquals(
            listOf("2", "10", "1A"),
            listOf("10", "1A", "2").sortedWith(comparator)
        )
    }

    @Test
    fun sortsNumericNamesByValueAndTextNamesLexically() {
        assertEquals(
            listOf("-2", "02", "2", "10", "", "1A", "2147483648", "A", "B"),
            listOf("B", "10", "2", "A", "", "02", "2147483648", "1A", "-2")
                .sortedWith(comparator)
        )
    }

    @Test
    fun comparatorObeysContractForMixedNames() {
        val names = listOf(
            "", "0", "00", "+0", "-1", "1", "01", "+1", "2", "10", "1A",
            "2147483647", "2147483648", "-2147483648", "-2147483649",
            "A", "B", " 2", "2 ", "Rail"
        )
        for (a in names) {
            assertEquals(0, comparator.compare(a, a))
            for (b in names) {
                assertEquals(
                    comparator.compare(a, b).compareTo(0),
                    -comparator.compare(b, a).compareTo(0)
                )
                for (c in names) {
                    if (comparator.compare(a, b) <= 0 && comparator.compare(b, c) <= 0) {
                        assertTrue(
                            "Inconsistent order: '$a', '$b', '$c'",
                            comparator.compare(a, c) <= 0
                        )
                    }
                }
            }
        }
    }

    @Test
    fun sortsLargeMixedListsWithoutTimSortFailure() {
        val random = Random(4322)
        val names = (0..100).map { it.toString() } +
            (0..100).map { "${it}A" } + listOf("", "02", "2147483648")
        val expected = names.sortedWith(comparator)
        repeat(100) {
            assertEquals(expected, names.shuffled(random).sortedWith(comparator))
        }
    }
}
