package com.catenarymaps.catenary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.time.Instant
import kotlin.math.abs

/**
 * Journey planning remains Edelweiss-owned. Live trip data is supplied by the
 * existing Ramonda->Aspen subscription, never a second Edelweiss lookup.
 */
internal fun DirectionsLeg.ramondaIdentity(): RamondaTripIdentity? {
    if (street) return null
    val source = chateau?.takeIf(String::isNotBlank) ?: return null
    val id = tripId?.takeIf(String::isNotBlank) ?: return null
    return RamondaTripIdentity(source, id, routeId, tripStartDate, tripStartTime)
}

internal fun DirectionsItinerary.ramondaTrips(): Set<RamondaTripIdentity> =
    legs.mapNotNull(DirectionsLeg::ramondaIdentity).toSet()

@Composable
internal fun rememberLiveJourney(journey: DirectionsItinerary): DirectionsItinerary {
    val subscriptions = journey.ramondaTrips()
    DisposableEffect(subscriptions) {
        subscriptions.forEach(RamondaWebSocket::subscribe)
        onDispose { subscriptions.forEach(RamondaWebSocket::unsubscribe) }
    }
    val snapshots by RamondaWebSocket.tripSnapshots.collectAsState()
    return journey.withRamonda(snapshots)
}

internal fun directionsDelaySeconds(current: String?, scheduled: String?): Long? {
    val baseline = runCatching { Instant.parse(scheduled).epochSecond }.getOrNull() ?: return null
    val actual = runCatching { Instant.parse(current).epochSecond }.getOrNull() ?: return null
    return (actual - baseline).takeIf { it != 0L }
}

internal val DirectionsLeg.departureDelaySeconds: Long?
    get() = directionsDelaySeconds(departureCurrent, departureScheduled)
internal val DirectionsLeg.arrivalDelaySeconds: Long?
    get() = directionsDelaySeconds(arrivalCurrent, arrivalScheduled)

internal fun instantString(seconds: Long?): String? =
    seconds?.let { runCatching { Instant.ofEpochSecond(it).toString() }.getOrNull() }

private fun findCall(
    calls: List<TripStoptime>,
    sequence: Int?,
    stopId: String?,
    reference: String?
): TripStoptime? {
    if (sequence != null) {
        return calls.firstOrNull { it.gtfs_stop_sequence == sequence &&
            (stopId == null || it.stop_id == stopId) }
    }
    val sameStop = calls.filter { stopId != null && it.stop_id == stopId }
    if (sameStop.size == 1) return sameStop.single()
    if (sameStop.size < 2 || reference == null) return null
    val time = runCatching { Instant.parse(reference).epochSecond }.getOrNull() ?: return null
    return sameStop.mapNotNull { call ->
        val scheduled = call.scheduled_departure_time_unix_seconds
            ?: call.scheduled_arrival_time_unix_seconds ?: return@mapNotNull null
        call to abs(scheduled - time)
    }.minByOrNull { it.second }?.first
}

private fun findUpdate(
    updates: List<StopTimeRefresh>,
    sequence: Int?,
    stopId: String?,
    reference: String?
): StopTimeRefresh? {
    if (sequence != null) {
        return updates.firstOrNull { it.gtfs_stop_sequence == sequence &&
            (stopId == null || it.stop_id == stopId) }
    }
    val sameStop = updates.filter { stopId != null && it.stop_id == stopId }
    // A repeated stop ID cannot safely identify a specific loop occurrence.
    return sameStop.singleOrNull()
}

private fun event(
    current: DirectionsEventTime?,
    scheduled: Long?,
    prediction: Long?
): DirectionsEventTime? {
    val scheduledString = instantString(scheduled) ?: current?.timetabledTime
    val predictedString = instantString(prediction) ?: current?.estimatedTime
    if (scheduledString == null && predictedString == null && current?.recordedAtTime == null)
        return current
    return DirectionsEventTime(
        timetabledTime = scheduledString,
        estimatedTime = predictedString,
        recordedAtTime = current?.recordedAtTime
    )
}

/** Do not mutate the saved route or its immutable journey identity. */
internal fun DirectionsItinerary.withRamonda(
    snapshots: Map<RamondaTripIdentity, RamondaTripSnapshot>
): DirectionsItinerary {
    val revised = legs.map { leg ->
        val snapshot = leg.ramondaIdentity()?.let(snapshots::get) ?: return@map leg
        val initial = snapshot.initial
        val updates = snapshot.latestUpdate?.stoptimes.orEmpty()

        fun updateStop(
            original: DirectionsPoint?,
            seq: Int?,
            reference: String?
        ): Triple<TripStoptime?, StopTimeRefresh?, DirectionsPoint?> {
            val scheduled = findCall(initial?.stoptimes.orEmpty(), seq, original?.stopId, reference)
            val updated = findUpdate(updates, seq, original?.stopId, reference)
            val code = updated?.rt_platform_string ?: scheduled?.rt_platform_string
            val location = original?.copy(
                platform = code ?: original.platform,
                estimatedQuay = code ?: original.estimatedQuay,
                plannedQuay = scheduled?.platform_code ?: original.plannedQuay
            )
            return Triple(scheduled, updated, location)
        }
        val (board, boardRt, boardLocation) =
            updateStop(leg.from, leg.fromStopSequence, leg.departureScheduled ?: leg.startTime)
        val (alight, alightRt, alightLocation) =
            updateStop(leg.to, leg.toStopSequence, leg.arrivalScheduled ?: leg.endTime)
        val dep = event(leg.serviceDeparture,
            board?.scheduled_departure_time_unix_seconds,
            boardRt?.rt_departure?.time ?: board?.rt_departure?.time)
        val arr = event(leg.serviceArrival,
            alight?.scheduled_arrival_time_unix_seconds,
            alightRt?.rt_arrival?.time ?: alight?.rt_arrival?.time)
        val revisedStops = leg.intermediateStops.map { stop ->
            val scheduled = findCall(initial?.stoptimes.orEmpty(), null, stop.stopId,
                stop.serviceDeparture?.timetabledTime ?: stop.scheduledDeparture)
            val rt = findUpdate(updates, scheduled?.gtfs_stop_sequence, stop.stopId,
                stop.serviceDeparture?.timetabledTime ?: stop.scheduledDeparture)
            val arrive = event(stop.serviceArrival, scheduled?.scheduled_arrival_time_unix_seconds,
                rt?.rt_arrival?.time ?: scheduled?.rt_arrival?.time)
            val depart = event(stop.serviceDeparture, scheduled?.scheduled_departure_time_unix_seconds,
                rt?.rt_departure?.time ?: scheduled?.rt_departure?.time)
            stop.copy(
                arrival = arrive?.effectiveTime ?: stop.arrival,
                departure = depart?.effectiveTime ?: stop.departure,
                serviceArrival = arrive, serviceDeparture = depart,
                estimatedQuay = rt?.rt_platform_string ?: scheduled?.rt_platform_string ?: stop.estimatedQuay,
                notServicedStop = rt?.schedule_relationship == 1 || stop.notServicedStop
            )
        }
        leg.copy(
            from = boardLocation, to = alightLocation,
            fromPlatform = boardRt?.rt_platform_string ?: board?.rt_platform_string ?: leg.fromPlatform,
            toPlatform = alightRt?.rt_platform_string ?: alight?.rt_platform_string ?: leg.toPlatform,
            startTime = dep?.effectiveTime ?: leg.startTime,
            endTime = arr?.effectiveTime ?: leg.endTime,
            serviceDeparture = dep,
            serviceArrival = arr,
            scheduledStartTime = dep?.timetabledTime ?: leg.scheduledStartTime,
            scheduledEndTime = arr?.timetabledTime ?: leg.scheduledEndTime,
            intermediateStops = revisedStops,
            cancelled = initial?.is_cancelled == true || initial?.deleted == true || leg.cancelled
        )
    }
    val firstTransit = legs.indexOfFirst { !it.street }
    val lastTransit = legs.indexOfLast { !it.street }
    val start = if (firstTransit == 0) revised.firstOrNull()?.startTime ?: startTime else startTime
    val end = if (lastTransit == legs.lastIndex) revised.lastOrNull()?.endTime ?: endTime else endTime
    return copy(
        startTime = start,
        endTime = end,
        // The top-line duration is recomputed for updated station departures.
        duration = null,
        legs = revised
    )
}
