package com.catenarymaps.catenary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DirectionPatternMeta(
    @SerialName("chateau")
    val chateau: String,

    @SerialName("direction_pattern_id")
    val directionPatternId: String,

    @SerialName("headsign_or_destination")
    val headsignOrDestination: String,

    // Option<String>
    @SerialName("gtfs_shape_id")
    val gtfsShapeId: String? = null,

    @SerialName("fake_shape")
    val fakeShape: Boolean,

    @SerialName("onestop_feed_id")
    val onestopFeedId: String,

    @SerialName("attempt_id")
    val attemptId: String,

    // Option<CompactString> -> String?
    @SerialName("route_id")
    val routeId: String? = null,

    // Option<i16> -> Int? (could also be Short?)
    @SerialName("route_type")
    val routeType: Int? = null,

    // Option<bool>
    @SerialName("direction_id")
    val directionId: Boolean? = null,

    // Option<Vec<Option<String>>> -> List<String?>?
    @SerialName("stop_headsigns_unique_list")
    val stopHeadsignsUniqueList: List<String?>? = null,

    // Option<String>
    @SerialName("direction_pattern_id_parents")
    val directionPatternIdParents: String? = null,
)

@Serializable
data class DirectionPatternRow(
    @SerialName("chateau")
    val chateau: String,

    @SerialName("direction_pattern_id")
    val directionPatternId: String,

    // CompactString -> String
    @SerialName("stop_id")
    val stopId: String,

    // u32 -> Int (if you expect very large values, switch to Long)
    @SerialName("stop_sequence")
    val stopSequence: Int,

    // Option<i32> -> Int?
    @SerialName("arrival_time_since_start")
    val arrivalTimeSinceStart: Int? = null,

    @SerialName("departure_time_since_start")
    val departureTimeSinceStart: Int? = null,

    @SerialName("interpolated_time_since_start")
    val interpolatedTimeSinceStart: Int? = null,

    @SerialName("onestop_feed_id")
    val onestopFeedId: String,

    @SerialName("attempt_id")
    val attemptId: String,

    // Option<i16> -> Int? (or Short?)
    @SerialName("stop_headsign_idx")
    val stopHeadsignIdx: Int? = null,
)