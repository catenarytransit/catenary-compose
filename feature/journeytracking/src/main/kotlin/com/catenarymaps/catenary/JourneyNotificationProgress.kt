package com.catenarymaps.catenary

import java.time.Instant

/** A time-based, single-leg estimate; transfers reference the next transit leg. */
data class JourneyLegNotificationProgress(
    val index: Int,
    val count: Int,
    val leg: DirectionsLeg,
    val percent: Int,
    val upcoming: Boolean
)

private fun progressEpoch(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }

fun notificationLegProgress(
    journey: DirectionsItinerary,
    now: Long
): JourneyLegNotificationProgress? {
    val legs = journey.legs
    if (legs.isEmpty()) return null
    val current = legs.indexOfFirst { leg ->
        val arrival = progressEpoch(leg.arrivalCurrent) ?: progressEpoch(leg.endTime)
        arrival == null || now < arrival
    }.let { if (it == -1) legs.lastIndex else it }
    val transferring = legs[current].street && legs.take(current).any { !it.street }
    val next = if (transferring) {
        ((current + 1) until legs.size).firstOrNull { !legs[it].street }
    } else null
    val index = next ?: current
    val leg = legs[index]
    val departure = progressEpoch(leg.departureCurrent) ?: progressEpoch(leg.startTime)
    val arrival = progressEpoch(leg.arrivalCurrent) ?: progressEpoch(leg.endTime)
    val percent = if (departure != null && arrival != null && arrival > departure) {
        (100.0 * (now - departure).toDouble() / (arrival - departure).toDouble())
            .coerceIn(0.0, 100.0).toInt()
    } else if (current == legs.lastIndex && now >= (arrival ?: Long.MAX_VALUE)) 100 else 0
    return JourneyLegNotificationProgress(
        index,
        legs.size,
        leg,
        percent,
        next != null || (departure != null && now < departure)
    )
}

