package com.catenarymaps.catenary

import java.time.Instant
import kotlinx.serialization.Serializable

/** One explicitly started journey, independent of the currently selected planner alternative. */
@Serializable
data class ActiveJourney(
    val snapshot: SavedJourney,
    val startedAtEpochSeconds: Long
)

sealed interface JourneyGuidance {
    data class Boarding(val leg: DirectionsLeg, val minutes: Long, val transfer: Boolean) :
        JourneyGuidance

    data class Riding(val stop: String, val minutes: Long) : JourneyGuidance
    data class FinalStreet(val cycling: Boolean, val remainingMeters: Double?) : JourneyGuidance
    data class Arriving(val destination: String) : JourneyGuidance
}

private fun timestamp(raw: String?): Long? = raw?.let {
    runCatching { Instant.parse(it).epochSecond }.getOrNull()
}

private fun minutesRemaining(at: Long?, now: Long): Long =
    at?.let { ((it - now).coerceAtLeast(0L) + 59L) / 60L } ?: 0L

private fun endCoordinates(active: SavedJourney): Pair<Double, Double>? {
    active.destination?.takeIf { it.isValid() }?.let { return it.lat to it.lon }
    val last = active.journey.legs.lastOrNull()?.to ?: return null
    val lat = last.lat ?: return null
    val lon = last.lon ?: return null
    return if (lat.isFinite() && lon.isFinite()) lat to lon else null
}

private fun startCoordinates(active: SavedJourney): Pair<Double, Double>? {
    active.origin?.takeIf { it.isValid() }?.let { return it.lat to it.lon }
    val first = active.journey.legs.firstOrNull()?.from ?: return null
    val lat = first.lat ?: return null
    val lon = first.lon ?: return null
    return if (lat.isFinite() && lon.isFinite()) lat to lon else null
}

/** Prior to departure, only surface guidance when departure is imminent and the user is nearby. */
fun shouldShowActiveJourney(
    active: ActiveJourney, now: Long, position: Pair<Double, Double>?
): Boolean {
    val start = timestamp(active.snapshot.journey.startTime) ?: return false
    if (now >= start) return true // Once underway, moving away from the origin must not hide guidance.
    if (start - now > 3L * 60 * 60) return false
    val origin = startCoordinates(active.snapshot) ?: return false
    val positionSafe = position ?: return false
    return haversineDistance(
        origin.first,
        origin.second,
        positionSafe.first,
        positionSafe.second
    ) <= 5_000.0
}

/** Suppress eligible guidance while a search or journey endpoint field has focus. */
fun shouldDisplayActiveJourneyBar(
    active: ActiveJourney?, now: Long, position: Pair<Double, Double>?,
    searchFocused: Boolean, plannerInputFocused: Boolean
): Boolean = active != null && !searchFocused && !plannerInputFocused &&
        shouldShowActiveJourney(active, now, position)

/** No time-only completion while a usable device position still shows the rider away from the destination. */
fun hasArrivedAtJourneyDestination(
    journey: DirectionsItinerary, destination: DirectionsLocation?,
    now: Long, position: Pair<Double, Double>?
): Boolean {
    val last = journey.legs.lastOrNull() ?: return false
    val arrival = timestamp(last.arrivalCurrent) ?: timestamp(journey.endTime) ?: return false
    // Don't end circular or short journeys just because the rider is at the destination before the last leg.
    if (now < arrival - 180L) return false
    if (position == null) return now >= arrival + 15L * 60L
    val point = destination?.takeIf(DirectionsLocation::isValid)?.let { it.lat to it.lon }
        ?: last.to?.let { stop ->
            val lat = stop.lat
            val lon = stop.lon
            if (lat != null && lon != null) lat to lon else null
        } ?: return false
    return haversineDistance(point.first, point.second, position.first, position.second) <= 75.0
}

/** Pure stage calculation; realtime leg events are supplied via rememberLiveJourney in the bar. */
fun journeyGuidance(
    journey: DirectionsItinerary, now: Long, position: Pair<Double, Double>?,
    destination: DirectionsLocation?
): JourneyGuidance {
    val legs = journey.legs
    if (legs.isEmpty()) return JourneyGuidance.Arriving(destination?.name.orEmpty())
    val lastTransit = legs.indexOfLast { !it.street }
    val current = legs.indexOfFirst { leg ->
        val end = timestamp(leg.arrivalCurrent) ?: timestamp(leg.endTime)
        end == null || now < end
    }
    if (current < 0) return JourneyGuidance.Arriving(
        destination?.name ?: legs.last().to?.name.orEmpty()
    )
    val leg = legs[current]
    if (leg.street) {
        if (current > lastTransit || lastTransit == -1) {
            val end = endCoordinates(SavedJourney(journey, null, destination))
            val remaining = if (end != null && position != null)
                haversineDistance(end.first, end.second, position.first, position.second)
            else null
            return JourneyGuidance.FinalStreet(!leg.walking, remaining)
        }
        val next = legs.drop(current + 1).firstOrNull { !it.street }
        if (next != null) return JourneyGuidance.Boarding(
            next,
            minutesRemaining(timestamp(next.departureCurrent), now), current > 0
        )
    }
    val departure = timestamp(leg.departureCurrent) ?: timestamp(leg.startTime)
    if (!leg.street) {
        return if (departure != null && now >= departure) {
            JourneyGuidance.Riding(
                leg.to?.name.orEmpty(),
                minutesRemaining(timestamp(leg.arrivalCurrent), now)
            )
        } else JourneyGuidance.Boarding(leg, minutesRemaining(departure, now), current > 0)
    }
    return JourneyGuidance.Arriving(destination?.name.orEmpty())
}

fun journeyServiceName(leg: DirectionsLeg): String {
    // National Rail train services outside the existing operator exemptions must not
    // fall back to route names: these represent unrelated trains on the same route.
    if (!NationalRailUtils.showsRouteName(leg.chateau, leg.agencyId)) {
        return leg.tripShortName?.trim()?.takeIf { it.isNotBlank() } ?: leg.mode
    }
    return sequenceOf(
        leg.routeShortName,
        leg.routeLongName,
        leg.tripShortName,
        leg.routeId,
        leg.mode
    )
        .filterNotNull().map(::timelineRouteName).firstOrNull { it.isNotBlank() }.orEmpty()
}

fun journeyPlatform(leg: DirectionsLeg): String? =
    sequenceOf(
        leg.from?.estimatedQuay, leg.fromPlatform, leg.from?.plannedQuay,
        leg.from?.platformCode, leg.from?.platform
    )
        .filterNotNull().firstOrNull { it.isNotBlank() }

