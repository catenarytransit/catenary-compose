package com.catenarymaps.catenary

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatDistanceMeters(meters: Double, imperial: Boolean, locale: Locale = Locale.getDefault()): String =
    if (imperial) {
        if (meters >= 1609.344) String.format(locale, "%.1f mi", meters / 1609.344)
        else "${(meters / 0.3048).roundToInt()} ft"
    } else {
        if (meters >= 1000) String.format(locale, "%.1f km", meters / 1000)
        else "${meters.roundToInt()} m"
    }

/** Old cached responses may not include timezone metadata. */
internal fun directionsZone(timezone: String?): ZoneId =
    runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }

internal fun directionsTime(value: String?, timezone: String? = null): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        .withZone(directionsZone(timezone)).format(Instant.parse(value))
}.getOrDefault("")

// Access/egress may start or finish away from a stop; summary clocks use the
// first boarding and final alighting stop's zone as requested.
internal val DirectionsItinerary.departureTimezone: String?
    get() = legs.firstOrNull { !it.street }?.from?.timezone ?: legs.firstOrNull()?.from?.timezone
internal val DirectionsItinerary.arrivalTimezone: String?
    get() = legs.lastOrNull { !it.street }?.to?.timezone ?: legs.lastOrNull()?.to?.timezone

internal data class PreviewTimeMarker(val time: String, val endpoint: Boolean, val timezone: String? = null)
internal data class PreviewTimeGroups(val departure: List<PreviewTimeMarker>, val arrival: List<PreviewTimeMarker>)

/** Match the web preview: negligible access/egress uses only the endpoint time. */
internal fun DirectionsItinerary.previewTimeGroups(): PreviewTimeGroups {
    val first = legs.indexOfFirst { !it.street }
    if (first < 0) return PreviewTimeGroups(listOf(PreviewTimeMarker(startTime, true, departureTimezone)), listOf(PreviewTimeMarker(endTime, true, arrivalTimezone)))
    val last = legs.indexOfLast { !it.street }
    fun atStop(streetLegs: List<DirectionsLeg>): Boolean {
        var meters = 0.0
        for (leg in streetLegs) meters += directionsDistance(leg) ?: return false
        return meters < 25
    }
    fun instant(time: String?) = runCatching { Instant.parse(time) }.getOrNull()
    val boarding = legs[first].startTime ?: legs.getOrNull(first - 1)?.endTime
    val alighting = legs[last].endTime ?: legs.getOrNull(last + 1)?.startTime
        ?: endTime.takeIf { last == legs.lastIndex }
    val originAtStop = atStop(legs.take(first))
    val destinationAtStop = atStop(legs.drop(last + 1))
    val showBoarding = !originAtStop && instant(boarding) != null && instant(boarding) != instant(startTime)
    val showAlighting = !destinationAtStop && instant(alighting) != null && instant(alighting) != instant(endTime)
    return PreviewTimeGroups(
        buildList {
            add(PreviewTimeMarker(startTime, true, departureTimezone))
            if (showBoarding) add(PreviewTimeMarker(boarding!!, false, legs[first].from?.timezone))
        },
        buildList {
            if (showAlighting) add(PreviewTimeMarker(alighting!!, false, legs[last].to?.timezone))
            add(PreviewTimeMarker(endTime, true, arrivalTimezone))
        }
    )
}

/** Reserve room for every mode icon before distributing remaining width by duration. */
internal fun timelineWeights(legs: List<DirectionsLeg>, width: Float, minimum: Float = 48f): List<Float> {
    if (legs.isEmpty()) return emptyList()
    val weights = legs.map { it.seconds.takeIf { s -> s.isFinite() && s > 0 }?.toFloat() ?: 1f }
    val pixels = MutableList(legs.size) { 0f }
    val remaining = legs.indices.toMutableSet()
    var available = width.coerceAtLeast(1f)
    val floor = minimum.coerceAtMost(available / legs.size)
    while (remaining.isNotEmpty()) {
        val total = remaining.sumOf { weights[it].toDouble() }.toFloat()
        val small = remaining.filter { available * weights[it] / total < floor }
        if (small.isEmpty()) {
            remaining.forEach { pixels[it] = available * weights[it] / total }
            break
        }
        small.forEach { pixels[it] = floor; remaining.remove(it); available -= floor }
    }
    return pixels.map { it.coerceAtLeast(0.001f) }
}

/** Two sentinel rows precede the cards; load when the edge card enters view. */
internal fun connectionPageAtEdge(direction: Int, first: Int, last: Int, journeyCount: Int): ConnectionDirection? = when {
    journeyCount == 0 -> null
    direction > 0 && first <= 2 -> ConnectionDirection.BEFORE
    direction < 0 && last >= journeyCount + 1 -> ConnectionDirection.AFTER
    else -> null
}

/** Use the same local midnight as the connection preview clocks. */
internal fun connectionDateHeading(
    previousDeparture: String?, departure: String, zone: ZoneId = ZoneId.systemDefault()
): LocalDate? {
    val previous = runCatching { Instant.parse(previousDeparture).atZone(zone).toLocalDate() }.getOrNull() ?: return null
    val date = runCatching { Instant.parse(departure).atZone(zone).toLocalDate() }.getOrNull() ?: return null
    return date.takeIf { it != previous }
}
