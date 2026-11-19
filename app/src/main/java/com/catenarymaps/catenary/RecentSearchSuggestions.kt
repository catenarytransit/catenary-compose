package com.catenarymaps.catenary

import java.util.Locale

/**
 * Locally selected places are suggested as soon as text is entered, independently
 * of the (debounced) Cypress/Birch search or its short-lived completed-results cache.
 */
private fun normalizePlaceText(text: String): String {
    val streetWords = mapOf(
        "avenue" to "ave", "street" to "st", "road" to "rd",
        "boulevard" to "blvd", "drive" to "dr", "lane" to "ln",
        "south" to "s", "north" to "n", "east" to "e", "west" to "w",
    )
    return text.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { streetWords[it] ?: it }
}

internal fun isMatchingRecentQuery(query: String, name: String, address: String? = null): Boolean {
    val needle = normalizePlaceText(query)
    if (needle.isEmpty()) return false
    return listOfNotNull(name, address).any { field ->
        val haystack = normalizePlaceText(field)
        haystack.startsWith(needle) || haystack.contains(" $needle")
    }
}

internal fun matchingRecentStationHistory(
    query: String,
    visits: List<RecentStation>,
    limit: Int = 8,
): List<RecentStation> =
    if (query.isBlank()) emptyList()
    else visits.filter { isMatchingRecentQuery(query, it.name, it.address) }
        .take(limit.coerceAtLeast(0))

private fun recentCoordinates(visit: RecentStation): DirectionsLocation? {
    val latitude = visit.latitude ?: return null
    val longitude = visit.longitude ?: return null
    return DirectionsLocation(visit.name, latitude, longitude).takeIf { it.isValid() }
}

internal fun samePreviouslySelectedPlace(first: DirectionsLocation, second: DirectionsLocation): Boolean {
    // Equal names alone would collapse distinct branches or stops in other places.
    return first.isValid() && second.isValid() &&
        normalizePlaceText(first.name) == normalizePlaceText(second.name) &&
        haversineDistance(first.lat, first.lon, second.lat, second.lon) <= 30.0
}

/**
 * Directions history is newest-first, followed by the already frequency/recency-ranked
 * StationHistory. Never require network connectivity to recall either list.
 */
internal fun matchingRecentDirections(
    query: String,
    recent: List<DirectionsLocation>,
    visits: List<RecentStation>,
    limit: Int = 8,
): List<DirectionsLocation> {
    if (query.isBlank()) return emptyList()
    val candidates = recent.asSequence()
        .filter { it.isValid() && isMatchingRecentQuery(query, it.name) }
        .plus(visits.asSequence().mapNotNull { visit ->
            if (isMatchingRecentQuery(query, visit.name, visit.address)) recentCoordinates(visit)
            else null
        })
    val unique = mutableListOf<DirectionsLocation>()
    for (candidate in candidates) {
        if (unique.none { samePreviouslySelectedPlace(it, candidate) }) {
            unique.add(candidate)
            if (unique.size >= limit.coerceAtLeast(0)) break
        }
    }
    return unique.take(limit.coerceAtLeast(0))
}

/** Exclude identical location results from both cached snapshots and live updates. */
internal fun withoutMatchingDirectionsResults(
    rows: List<SearchRow>,
    promoted: List<DirectionsLocation>,
): List<SearchRow> {
    if (promoted.isEmpty()) return rows
    return rows.filterNot { row ->
        val location = row.directionsLocation()
        location != null && promoted.any { samePreviouslySelectedPlace(it, location) }
    }
}

/** In general search, preserve identity when a stop or station has no coordinates. */
internal fun withoutMatchingVisitedResults(
    rows: List<SearchRow>,
    promoted: List<RecentStation>,
): List<SearchRow> {
    if (promoted.isEmpty()) return rows
    return rows.filterNot { row ->
        promoted.any { visit ->
            val sameId = when (row) {
                is SearchRow.StopRow ->
                    visit.kind == RecentStationKind.GTFS_STOP &&
                        row.ranking.chateau == visit.chateauId &&
                        row.ranking.gtfsId == visit.id
                is SearchRow.OsmStationRow ->
                    visit.kind == RecentStationKind.OSM_STATION &&
                        row.result.osmId.toString() == visit.id
                else -> false
            }
            if (sameId) true
            else {
                val place = row.directionsLocation()
                val previous = recentCoordinates(visit)
                if (place == null || previous == null) false
                else samePreviouslySelectedPlace(previous, place) ||
                    (!visit.address.isNullOrBlank() &&
                        samePreviouslySelectedPlace(
                            previous.copy(name = visit.address), place
                        ))
            }
        }
    }
}
