package com.catenarymaps.catenary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Query parameters accepted by Birch's /vehicle_history_lookup endpoint. */
@Serializable
data class VehicleHistoryLookupQuery(
        val vehicle: String? = null,
        val chateau: String? = null,
        val route_id: String? = null,
        val unified_agency_id: String? = null,
        val start_date: String? = null,
        val end_date: String? = null
)

/** Query parameters accepted by Birch's /vehicle_history_of_route endpoint. */
@Serializable
data class VehicleHistoryOfRouteQuery(
        val chateau: String? = null,
        val route_id: String? = null,
        val start_date: String? = null,
        val end_date: String? = null
)

@Serializable
data class RouteHistoryRow(
        val operation_date: String,
        val unix_start_time: Long? = null,
        val trip_id: String,
        val route_id: String,
        val trip_short_name: String? = null,
        val direction_headsign: String? = null,
        val block_id: String? = null
)

@Serializable
data class VehicleHistoryOfRouteRow(
        val operation_date: String,
        val vehicle_label: String,
        val trip_id: String,
        val trip_short_name: String? = null,
        val direction_headsign: String? = null,
        val block_id: String? = null
)

/**
 * Kotlin representation of catenary::models::Route as serialized by the Rust endpoint.
 * Numeric PostgreSQL/Rust integer widths are represented as Int/Long for Android JSON decoding.
 */
@Serializable
data class VehicleHistoryRoute(
        val onestop_feed_id: String,
        val attempt_id: String,
        val route_id: String,
        val short_name: String? = null,
        val short_name_translations: JsonElement? = null,
        val long_name: String? = null,
        val long_name_translations: JsonElement? = null,
        val gtfs_desc: String? = null,
        val gtfs_desc_translations: JsonElement? = null,
        val route_type: Int,
        val url: String? = null,
        val url_translations: JsonElement? = null,
        val agency_id: String? = null,
        val gtfs_order: Long? = null,
        val color: String? = null,
        val text_color: String? = null,
        val continuous_pickup: Int,
        val continuous_drop_off: Int,
        val shapes_list: List<String?>? = null,
        val chateau: String
)

@Serializable
data class VehicleHistoryLookupResponse(
        val trip_history: List<RouteHistoryRow>,
        val routes: Map<String, VehicleHistoryRoute>,
        val agency_timezone: String,
        val agency_name: String
)

@Serializable
data class VehicleHistoryOfRouteResponse(
        val trip_history: List<VehicleHistoryOfRouteRow>,
        val agency_timezone: String,
        val agency_name: String
)

@Serializable
data class VehicleHistoryLookupErrorResponse(val error: VehicleHistoryLookupErrorBody)

@Serializable
data class VehicleHistoryLookupErrorBody(val code: String, val message: String)
