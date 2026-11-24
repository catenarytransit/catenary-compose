package com.catenarymaps.catenary

import java.time.Instant
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class JourneyTripEndpoint(val stopId: String, val sequence: Int? = null, val time: Long)

@Serializable
data class JourneyTripSegment(val from: JourneyTripEndpoint, val to: JourneyTripEndpoint)

/** Separate flags keep the incoming half of boarding and outgoing half of alighting muted. */
data class JourneyStopPresentation(
    val active: Boolean,
    val endpoint: Boolean,
    val incomingActive: Boolean,
    val outgoingActive: Boolean
)

data class JourneyTripRange(val from: Int, val to: Int) {
    fun stop(index: Int) = JourneyStopPresentation(
        active = index in from..to, endpoint = index == from || index == to,
        incomingActive = index > from && index <= to,
        outgoingActive = index >= from && index < to
    )
}

fun DirectionsLeg.singleTripInfo(): CatenaryStackEnum.SingleTrip? {
    if (street) return null
    val source = chateau?.takeIf { it.isNotBlank() } ?: return null
    val trip = tripId?.takeIf { it.isNotBlank() } ?: return null
    val date = tripStartDate?.takeIf { it.isNotBlank() } ?: return null
    val fromId = from?.stopId?.takeIf { it.isNotBlank() } ?: return null
    val toId = to?.stopId?.takeIf { it.isNotBlank() } ?: return null
    val departure = runCatching { Instant.parse(startTime).epochSecond }.getOrNull() ?: return null
    val arrival = runCatching { Instant.parse(endTime).epochSecond }.getOrNull() ?: return null
    return CatenaryStackEnum.SingleTrip(
        source, trip, routeId, tripStartTime, date, null, routeType,
        JourneyTripSegment(
            JourneyTripEndpoint(fromId, fromStopSequence, departure),
            JourneyTripEndpoint(toId, toStopSequence, arrival)
        )
    )
}

fun resolveJourneyTripRange(
    stops: List<TripStoptime>,
    segment: JourneyTripSegment?
): JourneyTripRange? {
    if (segment == null) return null
    fun find(endpoint: JourneyTripEndpoint, arrival: Boolean): Int {
        if (endpoint.sequence != null) {
            val exact =
                stops.indexOfFirst { it.stop_id == endpoint.stopId && it.gtfs_stop_sequence == endpoint.sequence }
            if (exact >= 0) return exact
        }
        return stops.withIndex().filter { it.value.stop_id == endpoint.stopId }
            .mapNotNull { (index, stop) ->
                val time =
                    (if (arrival) stop.scheduled_arrival_time_unix_seconds else stop.scheduled_departure_time_unix_seconds)
                        ?: stop.scheduled_departure_time_unix_seconds
                        ?: stop.scheduled_arrival_time_unix_seconds
                        ?: stop.interpolated_stoptime_unix_seconds ?: return@mapNotNull null
                index to abs(time.toDouble() - endpoint.time.toDouble())
            }.minByOrNull { it.second }?.first ?: -1
    }

    val from = find(segment.from, false)
    val to = find(segment.to, true)
    return if (from >= 0 && to >= from) JourneyTripRange(from, to) else null
}
