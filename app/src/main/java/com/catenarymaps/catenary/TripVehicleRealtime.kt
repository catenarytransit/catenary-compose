package com.catenarymaps.catenary

import io.ktor.client.call.body
import io.ktor.client.request.get
import java.net.URLEncoder

/**
 * Same Birch vehicle lookup used by Single Trip. Resolve the vehicle label
 * from Ramonda's trip snapshot before calling this; never guess a vehicle ID.
 */
internal suspend fun fetchVehicleRealtimeForLabel(
    chateau: String,
    vehicleLabel: String
): List<VehicleRealtimeData> {
    fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    val url = "https://birch.catenarymaps.org/get_vehicle_information_from_label/" +
        "${encodeSegment(chateau)}/${encodeSegment(vehicleLabel)}"
    return ktorClient.get(url).body<VehicleRealtimeDataResponse>().data.orEmpty()
}

/** A reused vehicle label must not place a different GTFS trip on the journey map. */
internal fun VehicleRealtimeData.matchesJourneyTrip(identity: RamondaTripIdentity): Boolean {
    val reported = trip ?: return true
    return (reported.trip_id == null || reported.trip_id == identity.trip_id) &&
        (reported.route_id == null || identity.route_id == null || reported.route_id == identity.route_id) &&
        (reported.start_date == null || identity.start_date == null || reported.start_date == identity.start_date) &&
        (reported.start_time == null || identity.start_time == null || reported.start_time == identity.start_time)
}
