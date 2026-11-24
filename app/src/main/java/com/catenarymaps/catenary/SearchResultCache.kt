package com.catenarymaps.catenary

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Search ranking is location-biased. Nearby map movement can reuse a snapshot, but a
 * meaningfully different map focus or user location must cause a new search.
 */
internal data class SearchCacheKey(
    val text: String,
    val focusLatitudeCell: Int,
    val focusLongitudeCell: Int,
    val userLatitudeCell: Int?,
    val userLongitudeCell: Int?,
) {
    companion object {
        fun from(
            query: String,
            focusLatitude: Double,
            focusLongitude: Double,
            userLocation: Pair<Double, Double>?,
        ): SearchCacheKey = SearchCacheKey(
            text = query.trim().lowercase(Locale.ROOT),
            focusLatitudeCell = (focusLatitude * 100).roundToInt(),
            focusLongitudeCell = (focusLongitude * 100).roundToInt(),
            userLatitudeCell = userLocation?.first?.times(100)?.roundToInt(),
            userLongitudeCell = userLocation?.second?.times(100)?.roundToInt(),
        )
    }
}

/** Small in-memory LRU for fully completed searches; never persists personal queries to disk. */
internal class CompletedSearchCache<K, V>(
    private val ttlMillis: Long = 120_000L,
    private val maxEntries: Int = 32,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    init {
        require(ttlMillis > 0)
        require(maxEntries > 0)
    }

    private data class Snapshot<V>(val value: V, val createdAt: Long)

    private val entries = LinkedHashMap<K, Snapshot<V>>(16, 0.75f, true)

    @Synchronized
    fun get(key: K): V? {
        val snapshot = entries[key] ?: return null
        if (monotonicMillis() - snapshot.createdAt >= ttlMillis) {
            entries.remove(key)
            return null
        }
        return snapshot.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Snapshot(value, monotonicMillis())
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }
}
