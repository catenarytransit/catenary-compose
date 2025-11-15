package com.catenarymaps.catenary

import androidx.compose.material3.MaterialTheme
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

@Composable
fun TripDataForVehicleOnRouteScreen(
    vehicle: VehiclePosition,
    stops: Map<String, RouteInfoStop>,
    possibleTripUpdates: List<AspenisedTripUpdate>
) {
    var likelyTrip by remember { mutableStateOf<AspenisedTripUpdate?>(null) }

    fun computeLikelyTrip() {
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

    LaunchedEffect(possibleTripUpdates, vehicle) {
        computeLikelyTrip()
    }

    LaunchedEffect(Unit) {
        computeLikelyTrip()
    }


    val nextStopId = likelyTrip?.stop_time_update?.firstOrNull()?.stop_id

    //Text(text = "updates ${possibleTripUpdates.toString()}")


    val stopInfo = nextStopId?.let { stops[it] }

    if (stopInfo != null) {
        Text(
            text = "${stringResource(R.string.next_stop)}: ${stopInfo.name}",
            //maxLines = 1,
            //overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium
        )
    } else {
        //Text(text = "no likely trip")
    }
}
