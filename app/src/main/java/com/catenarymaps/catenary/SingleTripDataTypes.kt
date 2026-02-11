package com.catenarymaps.catenary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Data classes for get_trip_information ---

@Serializable
data class TripStoptime(
        val name: String? = null,
        val stop_id: String,
        val longitude: Double,
        val latitude: Double,
        val timezone: String? = null,
        val scheduled_arrival_time_unix_seconds: Long? = null,
        val scheduled_departure_time_unix_seconds: Long? = null,
        val interpolated_stoptime_unix_seconds: Long? = null,
        val rt_arrival: RtTime? = null,
        val rt_departure: RtTime? = null,
        val rt_platform_string: String? = null,
        val schedule_relationship: Int? = null, // 1 means cancelled
        val code: String? = null,
        val timepoint: Boolean? = null,
        val replaced_stop: Boolean? = null,
        val gtfs_stop_sequence: Int? = null,
        val show_both_departure_and_arrival: Boolean = false // You'll set this manually
)

@Serializable data class RtTime(val time: Long? = null)

@Serializable data class TripVehicle(val id: String? = null, val label: String? = null)

@Serializable
data class TripAlert(val informed_entity: List<AlertEntity>
// Add other alert properties as needed
)

@Serializable
data class AlertEntity(val stop_id: String? = null
// Add other entity properties as needed
)

@Serializable
data class TripDataResponse(
        val color: String? = null,
        val text_color: String? = null,
        val route_id: String? = null,
        val vehicle: TripVehicle? = null,
        val trip_headsign: String? = null,
        val trip_short_name: String? = null,
        val route_short_name: String? = null,
        val route_long_name: String? = null,
        val route_type: Int? = null,
        val block_id: String? = null,
        val service_date: String? = null,
        val trip_id: String? = null,
        val shape_polyline: String? = null,
        val old_shape_polyline: String? = null,
        val rt_shape: Boolean? = null,
        val tz: String? = null,
        val stoptimes: List<TripStoptime> = emptyList(),
        val alert_id_to_alert: Map<String, Alert> = emptyMap(),
        val is_cancelled: Boolean,
        val deleted: Boolean,
        @SerialName("connecting_routes")
        val connectingRoutes: Map<String, Map<String, ConnectingRoute>>? = null,
        @SerialName("connections_per_stop")
        val connectionsPerStop: Map<String, Map<String, List<String>>>? = null,
)

// --- Data class for get_trip_information_rt_update ---

@Serializable
data class TripRtUpdateResponse(val found_data: Boolean, val data: TripRtUpdateData? = null)

@Serializable
data class TripRtUpdateData(
    val stoptimes: List<StopTimeRefresh> = emptyList(),
    val timestamp: Long? = null,
    val trip_id: String? = null,
    val chateau: String? = null
)

@Serializable
data class StopTimeRefresh(
        val stop_id: String? = null,
        val rt_arrival: RtTime? = null,
        val rt_departure: RtTime? = null,
        val schedule_relationship: Int? = null,
        val gtfs_stop_sequence: Int? = null,
        val rt_platform_string: String? = null,
        val departure_occupancy_status: Int? = null
)

// --- Data class for get_vehicle_information_from_label ---

@Serializable data class VehicleRealtimeDataResponse(val data: List<VehicleRealtimeData>? = null)

@Serializable
data class VehicleRealtimeData(
        val timestamp: Long? = null,
        val position: SingleTripVehiclePosition? = null,
        val occupancy_status: String? = null,
        val occupancy_percentage: Int? = null,
        val vehicle: VehicleRealtimeVehicleInfo? = null,
        val trip: VehicleRealtimeTripInfo? = null
)

@Serializable
data class SingleTripVehiclePosition(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val bearing: Float? = null,
        val odometer: Double? = null,
        val speed: Float? = null
)

@Serializable
data class VehicleRealtimeVehicleInfo(
        val id: String? = null,
        val label: String? = null,
        val license_plate: String? = null,
        val wheelchair_accessible: String? = null
)

@Serializable
data class VehicleRealtimeTripInfo(
        val trip_id: String? = null,
        val trip_headsign: String? = null,
        val route_id: String? = null,
        val trip_short_name: String? = null,
        val direction_id: Int? = null,
        val start_time: String? = null,
        val start_date: String? = null,
        val schedule_relationship: String? = null,
        val delay: Int? = null
)
