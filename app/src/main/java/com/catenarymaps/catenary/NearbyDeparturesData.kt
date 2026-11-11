package com.catenarymaps.catenary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NearbyResponseV3(
        @SerialName("long_distance") val longDistance: List<StationDepartureGroup> = emptyList(),
        val local: List<RouteGroupV3> = emptyList(),
        val routes: Map<String, Map<String, RouteInfoExport>> = emptyMap(),
        val stops: Map<String, Map<String, StopOutputV3>> = emptyMap(),
        val debug: DebugInfo? = null
)

@Serializable
internal data class StationDepartureGroup(
        @SerialName("station_name") val stationName: String,
        @SerialName("osm_station_id") val osmStationId: Long? = null,
        @SerialName("distance_m") val distanceM: Double,
        val departures: List<DepartureItemV3>,
        val lat: Double,
        val lon: Double,
        val timezone: String
)

@Serializable
internal data class DepartureItemV3(
        @SerialName("scheduled_departure") val scheduledDeparture: Long? = null,
        @SerialName("realtime_departure") val realtimeDeparture: Long? = null,
        @SerialName("scheduled_arrival") val scheduledArrival: Long? = null,
        @SerialName("realtime_arrival") val realtimeArrival: Long? = null,
        @SerialName("service_date") val serviceDate: String,
        val headsign: String,
        val platform: String? = null,
        @SerialName("trip_id") val tripId: String,
        @SerialName("trip_short_name") val tripShortName: String? = null,
        @SerialName("route_id") val routeId: String,
        @SerialName("stop_id") val stopId: String,
        val cancelled: Boolean,
        val delayed: Boolean,
        @SerialName("chateau_id") val chateauId: String,
        @SerialName("last_stop") val lastStop: Boolean,
        @SerialName("final_station_name") val finalStationName: String? = null,
)

internal fun DepartureItemV3.effectiveRealtimeDepartureSec(): Long? =
        realtimeDeparture
                ?: realtimeArrival?.takeIf {
                        scheduledDeparture != null && it > scheduledDeparture
                }

internal fun DepartureItemV3.effectiveDepartureSec(): Long? =
        effectiveRealtimeDepartureSec() ?: scheduledDeparture

@Serializable
data class RouteGroupV3(
        @SerialName("chateau_id") val chateauId: String,
        @SerialName("route_id") val routeId: String,
        val color: String? = null,
        @SerialName("text_color") val textColor: String? = null,
        @SerialName("short_name") val shortName: String? = null,
        @SerialName("long_name") val longName: String? = null,
        @SerialName("route_type") val routeType: Int,
        @SerialName("agency_name") val agencyName: String? = null,
        val headsigns: Map<String, List<LocalDepartureItem>> = emptyMap(),
        @SerialName("closest_distance") val closestDistance: Double
)

/**
 * Stable identity for a scheduled visit to a stop. A trip can pass the same stop more
 * than once, and different service days can reuse a trip ID. Realtime updates must not
 * change the key while the list is being recomposed.
 *
 * Length-prefixed fields avoid collisions when IDs contain separators or are adjacent
 * to numeric timestamps. A null timestamp is different from a non-null one.
 */
internal fun LocalDepartureItem.stableDepartureKey(): String =
        listOf(tripId, serviceDate, stopId, departureSchedule, arrivalSchedule)
                .joinToString(separator = "") { value ->
                        value?.toString()?.let { "${it.length}:$it" } ?: "-1:"
                }

@Serializable
data class LocalDepartureItem(
        @SerialName("trip_id") val tripId: String,
        @SerialName("departure_schedule") val departureSchedule: Long? = null,
        @SerialName("departure_realtime") val departureRealtime: Long? = null,
        @SerialName("arrival_schedule") val arrivalSchedule: Long? = null,
        @SerialName("arrival_realtime") val arrivalRealtime: Long? = null,
        @SerialName("stop_id") val stopId: String,
        @SerialName("stop_name") val stopName: String? = null,
        val cancelled: Boolean,
        val platform: String? = null,
        @SerialName("last_stop") val lastStop: Boolean,
        @SerialName("service_date") val serviceDate: String
)

@Serializable
data class RouteInfoExport(
        @SerialName("short_name") val shortName: String? = null,
        @SerialName("long_name") val longName: String? = null,
        @SerialName("agency_name") val agencyName: String? = null,
        val color: String? = null,
        @SerialName("text_color") val textColor: String? = null,
        @SerialName("route_type") val routeType: Int
)

@Serializable
data class StopOutputV3(
        @SerialName("gtfs_id") val gtfsId: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        @SerialName("osm_station_id") val osmStationId: Long? = null,
        val timezone: String
)

@Serializable
internal data class DebugInfo(@SerialName("total_time_ms") val totalTimeMs: Long? = null)

