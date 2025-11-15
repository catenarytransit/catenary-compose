package com.catenarymaps.catenary

import kotlinx.serialization.Serializable

@Serializable
data class AspenisedTripUpdate(
    val trip: AspenRawTripInfo,
    val vehicle: AspenisedVehicleDescriptor? = null,
    val timestamp: Long? = null,
    val delay: Int? = null,
    val stop_time_update: List<AspenisedStopTimeUpdate> = emptyList(),
    val trip_properties: AspenTripProperties? = null,
    val trip_headsign: String? = null,
    val found_schedule_trip_id: Boolean
)

@Serializable
data class AspenisedVehicleDescriptor(
    val id: String? = null,
    val label: String? = null,
    val license_plate: String? = null,
    val wheelchair_accessible: Int? = null
)

@Serializable
data class AspenisedStopTimeUpdate(
    val stop_sequence: Int? = null,
    val stop_id: String? = null,
    val arrival: AspenStopTimeEvent? = null,
    val departure: AspenStopTimeEvent? = null,
    val departure_occupancy_status: AspenisedOccupancyStatus? = null,
    val schedule_relationship: AspenisedStopTimeScheduleRelationship? = null,
    val stop_time_properties: AspenisedStopTimeProperties? = null,
    val platform_string: String? = null,
    val old_rt_data: Boolean
)

// Placeholder for a type not defined in the request
@Serializable
class AspenisedStopTimeProperties

// Placeholder for a type not defined in the request
@Serializable
class AspenTripProperties

@Serializable
enum class AspenisedOccupancyStatus {
    Empty,
    ManySeatsAvailable,
    FewSeatsAvailable,
    StandingRoomOnly,
    CrushedStandingRoomOnly,
    Full,
    NotAcceptingPassengers,
    NoDataAvailable,
    NotBoardable,
}

@Serializable
enum class AspenisedTripScheduleRelationship {
    Scheduled,
    Added,
    Unscheduled,
    Cancelled,
    Replacement,
    Duplicated,
    Deleted,
}

@Serializable
enum class AspenisedStopTimeScheduleRelationship {
    Scheduled,
    Skipped,
    NoData,
    Unscheduled,
}

@Serializable
data class AspenStopTimeEvent(
    val delay: Int? = null,
    val time: Long? = null,
    val uncertainty: Int? = null
)

@Serializable
data class AspenRawTripInfo(
    val trip_id: String? = null,
    val route_id: String? = null,
    val direction_id: Int? = null,
    val start_time: String? = null,
    val start_date: String? = null, // Using String for NaiveDate for simplicity
    val schedule_relationship: AspenisedTripScheduleRelationship? = null,
    val modified_trip: ModifiedTripSelector? = null
)

@Serializable
data class ModifiedTripSelector(
    val modifications_id: String? = null,
    val affected_trip_id: String? = null
)