package com.catenarymaps.catenary

import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Group using the same departure timezone as the summary clock, including the first date. */
internal fun List<SavedJourney>.groupedByDepartureDate(): Map<LocalDate?, List<SavedJourney>> =
    sortedBy { runCatching { Instant.parse(it.journey.startTime) }.getOrDefault(Instant.MAX) }
        .groupBy { snapshot ->
            runCatching {
                Instant.parse(snapshot.journey.startTime)
                    .atZone(directionsZone(snapshot.journey.departureTimezone)).toLocalDate()
            }.getOrNull()
        }
        .toSortedMap(nullsLast(naturalOrder<LocalDate>()))

/** Completed journeys cannot be newly saved; journeys still in progress can be. */
internal fun DirectionsItinerary.canBeSaved(now: Instant = Instant.now()): Boolean =
    runCatching { Instant.parse(endTime).isAfter(now) }.getOrDefault(false)

/** Resolve the captured endpoints, never the device's location at a later time. */
internal suspend fun SavedJourney.withNamedCurrentLocations(
    reverseName: suspend (DirectionsLocation) -> String?
): SavedJourney {
    suspend fun resolve(location: DirectionsLocation?): DirectionsLocation? {
        if (location == null || !location.isCurrentLocation) return location
        val name = reverseName(location)?.trim()?.takeIf { it.isNotEmpty() }
            ?: String.format(Locale.ROOT, "%.5f, %.5f", location.lat, location.lon)
        return location.copy(name = name, isCurrentLocation = false)
    }
    return copy(origin = resolve(origin), destination = resolve(destination))
}
