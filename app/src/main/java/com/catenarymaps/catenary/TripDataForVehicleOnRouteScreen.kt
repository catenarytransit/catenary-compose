package com.catenarymaps.catenary

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StopTimeUpdate(
    val stop_id: String
)

@Serializable
data class TripUpdate(
    val trip: TripDescriptor,
    val stop_time_update: List<StopTimeUpdate> = emptyList()
)

@Composable
fun TripDataForVehicleOnRouteScreen(
    vehicle: VehiclePosition,
    stops: Map<String, RouteInfoStop>,
    possibleTripUpdates: List<TripUpdate>
) {
    var likelyTrip by remember { mutableStateOf<TripUpdate?>(null) }

    LaunchedEffect(possibleTripUpdates, vehicle) {
        if (possibleTripUpdates.size == 1) {
            likelyTrip = possibleTripUpdates[0]
        } else if (possibleTripUpdates.size > 1) {
            val filteredList = possibleTripUpdates.filter { update ->
                val vehicleTrip = vehicle.trip
                val updateTrip = update.trip
                var match = true
                if (vehicleTrip?.start_date != null && updateTrip.start_date != null) {
                    if (updateTrip.start_date.replace("-", "") != vehicleTrip.start_date) {
                        match = false
                    }
                }
                if (vehicleTrip?.start_time != null && updateTrip.start_time != null) {
                    if (updateTrip.start_time != vehicleTrip.start_time) {
                        match = false
                    }
                }
                match
            }
            if (filteredList.isNotEmpty()) {
                likelyTrip = filteredList[0]
            } else {
                // As a fallback if no exact match is found
                likelyTrip = possibleTripUpdates[0]
            }
        } else {
            likelyTrip = null
        }
    }

    likelyTrip?.let { trip ->
        trip.stop_time_update.firstOrNull()?.let { nextStopUpdate ->
            stops[nextStopUpdate.stop_id]?.let { stopInfo ->
                Text(
                    text = "${stringResource(R.string.next_stop)}: ${stopInfo.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
