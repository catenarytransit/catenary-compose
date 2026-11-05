package com.catenarymaps.catenary

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** One completed search, including the exact itinerary being viewed. */
@Serializable
internal data class TripPlannerSession(
    val version: Int = 1,
    val origin: DirectionsLocation,
    val destination: DirectionsLocation,
    val options: DirectionsOptions,
    val arriveBy: Boolean,
    val now: Boolean,
    val time: Long,
    val itineraries: List<DirectionsItinerary>,
    val selected: Int = 0,
    val details: Boolean = false,
    val showTransfers: Boolean = false
) {
    fun validated(): TripPlannerSession? {
        if (version != 1 || !origin.isValid() || !destination.isValid() || itineraries.isEmpty()) return null
        if (listOf(options.walkingPace, options.cyclingKmh).any { !it.isFinite() || it <= 0 } ||
            listOf(options.accessSeconds, options.egressSeconds, options.directSeconds).any { !it.isFinite() || it < 0 }) return null
        if (itineraries.any { journey ->
            runCatching { Instant.parse(journey.startTime); Instant.parse(journey.endTime) }.isFailure
        }) return null
        return copy(selected = selected.coerceIn(itineraries.indices))
    }
}

internal object TripPlannerSessionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(session: TripPlannerSession): String = json.encodeToString(session)
    fun decode(text: String): TripPlannerSession? =
        runCatching { json.decodeFromString<TripPlannerSession>(text).validated() }.getOrNull()
}
