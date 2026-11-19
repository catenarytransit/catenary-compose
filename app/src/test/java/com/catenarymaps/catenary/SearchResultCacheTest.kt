package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResultCacheTest {
    @Test
    fun completedSnapshotIsAvailableWhileBackspacingAndExpires() {
        var time = 1_000L
        val cache = CompletedSearchCache<String, List<String>>(
            ttlMillis = 120_000L, monotonicMillis = { time }
        )
        cache.put("zurich", listOf("Zürich HB", "Zürich Enge"))
        assertEquals(listOf("Zürich HB", "Zürich Enge"), cache.get("zurich"))
        time += 119_999L
        assertEquals(2, cache.get("zurich")?.size)
        time++
        assertNull(cache.get("zurich"))
    }

    @Test
    fun emptyCompletedResultsAreCacheable() {
        val cache = CompletedSearchCache<String, List<String>>()
        cache.put("no-match", emptyList())
        assertEquals(emptyList<String>(), cache.get("no-match"))
    }

    @Test
    fun leastRecentlyUsedEntryIsEvicted() {
        val cache = CompletedSearchCache<String, Int>(maxEntries = 2)
        cache.put("first", 1)
        cache.put("second", 2)
        cache.get("first")
        cache.put("third", 3)
        assertNull(cache.get("second"))
        assertEquals(1, cache.get("first"))
        assertEquals(3, cache.get("third"))
    }

    @Test
    fun keyNormalizesTextButSeparatesDistinctSearchLocations() {
        val first = SearchCacheKey.from("  Zürich HB ", 47.376, 8.541, null)
        assertEquals(first, SearchCacheKey.from("zürich hb", 47.376, 8.541, null))
        assertNotEquals(first, SearchCacheKey.from("Zürich HB", 48.0, 8.541, null))
        assertNotEquals(first, SearchCacheKey.from("Zürich HB", 47.376, 8.541, 47.3 to 8.6))
        assertNotEquals(
            SearchCacheKey.from("Zürich HB", 47.376, 8.541, 47.3 to 8.6),
            SearchCacheKey.from("Zürich HB", 47.376, 8.541, 47.4 to 8.6),
        )
    }
}
