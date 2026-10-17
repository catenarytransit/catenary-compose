package com.catenarymaps.catenary

/** Cancelled prefixes/suffixes shorten a trip; interior cancellations are detours. */
internal data class TripCancellationTimeline(
        val cancelledStops: List<Boolean>,
        val dashedSegments: List<Boolean>,
        val exitOnlyStopIndex: Int?
)

internal fun classifyTripCancellation(
        stopCancelled: List<Boolean>,
        tripCancelled: Boolean
): TripCancellationTimeline {
        val cancelled = stopCancelled.map { tripCancelled || it }
        val firstServed = cancelled.indexOfFirst { !it }
        val lastServed = cancelled.indexOfLast { !it }
        val shortened = cancelled.mapIndexed { i, isCancelled ->
                isCancelled && (firstServed < 0 || i < firstServed || i > lastServed)
        }
        val dashed = List((cancelled.size - 1).coerceAtLeast(0)) { i ->
                shortened[i] || shortened[i + 1]
        }
        val exitOnly = if (!tripCancelled && lastServed >= 0 &&
                lastServed < cancelled.lastIndex) lastServed else null
        return TripCancellationTimeline(cancelled, dashed, exitOnly)
}
