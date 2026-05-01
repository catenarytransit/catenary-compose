package com.catenarymaps.catenary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnifiedConsist(
    val global_journey_id: String? = null,
    val groups: List<ConsistGroup> = emptyList(),
    val formation_status: FormationStatus? = null
)

@Serializable
data class ConsistGroup(
    val group_name: String? = null,
    val destination: String? = null,
    val vehicles: List<VehicleElement> = emptyList(),
    val group_orientation: Orientation? = null
)

@Serializable
data class VehicleElement(
    val uic_number: String? = null,
    val label: String? = null,
    val order: Int = 0,
    val position_on_platform: PlatformSpatialPosition? = null,
    val facilities: List<Amenity> = emptyList(),
    val occupancy: SiriOccupancy? = null,
    val passenger_count: Int? = null,
    val passenger_class: PassengerClass? = null,
    val is_locomotive: Boolean? = null,
    val is_revenue: Boolean? = null
)

@Serializable
data class PlatformSpatialPosition(
    val sector: String? = null,
    val start_meters: Float? = null,
    val end_meters: Float? = null
)

@Serializable
data class Amenity(
    val amenity_type: AmenityType? = null,
    val status: AmenityStatus? = null,
    val count: Int? = null
)

@Serializable
enum class AmenityStatus {
    @SerialName("Available")
    Available,
    @SerialName("NotAvailable")
    NotAvailable,
    @SerialName("Unknown")
    Unknown,
    @SerialName("Restricted")
    Restricted
}

@Serializable
enum class AmenityType {
    @SerialName("AIR_CONDITION")
    AirCondition,
    @SerialName("WHEELCHAIR_SPACE")
    WheelchairSpace,
    @SerialName("BIKE_SPACE")
    BikeSpace,
    @SerialName("QUIET_ZONE")
    QuietZone,
    @SerialName("FAMILY_ZONE")
    FamilyZone,
    @SerialName("INFO_POINT")
    InfoPoint,
    @SerialName("DINING_CAR")
    DiningCar,
    @SerialName("TOILET")
    Toilet,
    @SerialName("LOW_FLOOR")
    LowFloor
}

@Serializable
data class AspenisedPlatformInfo(
    val aimed: String? = null,
    val expected: String? = null,
    val platform_sectors: List<PlatformSectorDefinition>? = null,
    val is_changed: Boolean = false
)

@Serializable
data class PlatformSectorDefinition(
    val label: String,
    val start_meters: Float,
    val end_meters: Float
)

@Serializable
enum class FormationStatus {
    @SerialName("MatchesSchedule")
    MatchesSchedule,
    @SerialName("DifferentRearLoad")
    DifferentRearLoad,
    @SerialName("Unknown")
    Unknown
}

@Serializable
enum class Orientation {
    @SerialName("Forwards")
    Forwards,
    @SerialName("Backwards")
    Backwards,
    @SerialName("Unknown")
    Unknown
}

@Serializable
enum class PassengerClass {
    @SerialName("FIRST")
    First,
    @SerialName("SECOND")
    Second,
    @SerialName("UNKNOWN")
    Unknown
}

@Serializable
enum class SiriOccupancy {
    @SerialName("LOW")
    Low,
    @SerialName("MEDIUM")
    Medium,
    @SerialName("HIGH")
    High,
    @SerialName("VERY_HIGH")
    VeryHigh,
    @SerialName("EMPTY")
    Empty,
    @SerialName("STANDING_ROOM_ONLY")
    StandingRoomOnly,
    @SerialName("FULL")
    Full,
    @SerialName("NOT_ACCEPTING_PASSENGERS")
    NotAcceptingPassengers,
    @SerialName("UNKNOWN")
    Unknown
}