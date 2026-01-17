// In a new file, e.g., SingleTripViewModel.kt
package com.catenarymaps.catenary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// This class will be the "cleaned" version from your Svelte logic
data class StopTimeCleaned(
        val raw: TripStoptime,
        var rtArrivalTime: Long? = raw.rt_arrival?.time,
        var rtDepartureTime: Long? = raw.rt_departure?.time,
        var strikeDeparture: Boolean = raw.rt_departure?.time != null,
        var strikeArrival: Boolean = raw.rt_arrival?.time != null,
        var rtArrivalDiff: Long? = null,
        var rtDepartureDiff: Long? = null,
        val showBoth: Boolean = false // Logic to calculate this
)

class SingleTripViewModel(private val tripSelected: CatenaryStackEnum.SingleTrip) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _tripData = MutableStateFlow<TripDataResponse?>(null)
    val tripData = _tripData.asStateFlow()

    private val _vehicleData = MutableStateFlow<VehicleRealtimeData?>(null)
    val vehicleData = _vehicleData.asStateFlow()

    // This is your stoptimes_cleaned_dataset
    private val _stopTimes = MutableStateFlow<List<StopTimeCleaned>>(emptyList())
    val stopTimes = _stopTimes.asStateFlow()

    private val _alerts = MutableStateFlow<Map<String, Alert>>(emptyMap())
    val alerts = _alerts.asStateFlow()

    private val _stopIdToAlertIds = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val stopIdToAlertIds = _stopIdToAlertIds.asStateFlow()

    // --- UI State ---
    private val _showPreviousStops = MutableStateFlow(false)
    val showPreviousStops = _showPreviousStops.asStateFlow()

    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    val currentTime = _currentTime.asStateFlow()

    private val _lastInactiveStopIdx = MutableStateFlow(-1)
    val lastInactiveStopIdx = _lastInactiveStopIdx.asStateFlow()

    private val _currentAtStopIdx = MutableStateFlow(-1)
    val currentAtStopIdx = _currentAtStopIdx.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        fetchInitialTripData()
        startTimers()
    }

    private fun fetchInitialTripData() {
        viewModelScope.launch {
            try {

                val encodedChateauId = URLEncoder.encode(tripSelected.chateau_id ?: "", "UTF-8")
                val encodedTripId = URLEncoder.encode(tripSelected.trip_id ?: "", "UTF-8")

                val url =
                        "https://birch.catenarymaps.org/get_trip_information/${encodedChateauId}/?" +
                                if (tripSelected.trip_id != null) "trip_id=${encodedTripId}&"
                                else
                                        "" +
                                                if (tripSelected.start_date != null)
                                                        "start_date=${tripSelected.start_date}&"
                                                else
                                                        "" +
                                                                if (tripSelected.start_time != null)
                                                                        "start_time=${tripSelected.start_time}"
                                                                else ""

                println("fetching url ${url}")

                val response = ktorClient.get(url).body<String>()

                // Handle errors
                val data = json.decodeFromString<TripDataResponse>(response)

                _tripData.value = data
                _alerts.value = data.alert_id_to_alert
                // processStopIdToAlertIds(data.alert_id_to_alert)

                // Process stoptimes into the "cleaned" dataset
                _stopTimes.value = data.stoptimes.map { processStopTime(it) }
                updateStopProgress()

                _isLoading.value = false
                _error.value = null

                // Fetch vehicle info once we have trip data
                fetchVehicleInfo()
            } catch (e: Exception) {
                Log.e("SingleTripViewModel", "Error fetching initial trip: ${e.message}")
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    private fun fetchRealtimeUpdate() {
        if (_isLoading.value) return // Don't fetch if still on initial load
        viewModelScope.launch {
            try {
                val encodedChateauId = URLEncoder.encode(tripSelected.chateau_id ?: "", "UTF-8")
                val encodedTripId = URLEncoder.encode(tripSelected.trip_id ?: "", "UTF-8")

                val url =
                        "https://birch.catenarymaps.org/get_trip_information_rt_update/${encodedChateauId}/?" +
                                "trip_id=${encodedTripId}&" +
                                "start_date=${tripSelected.start_date ?: ""}&" +
                                "start_time=${tripSelected.start_time ?: ""}"

                val rtUpdate = ktorClient.get(url).body<TripRtUpdateResponse>()

                if (rtUpdate.found_data && rtUpdate.data != null) {
                    val newStopTimesMap =
                            rtUpdate.data.stoptimes.associateBy {
                                it.gtfs_stop_sequence ?: it.stop_id
                            }

                    // Merge RT data into existing list
                    _stopTimes.update { currentList ->
                        currentList.map { existingCleanedStop ->
                            val key =
                                    existingCleanedStop.raw.gtfs_stop_sequence
                                            ?: existingCleanedStop.raw.stop_id
                            val newRtData = newStopTimesMap[key]

                            if (newRtData != null) {
                                // This merges new RT data, like in your Svelte logic
                                mergeRtStopTime(existingCleanedStop, newRtData)
                            } else {
                                existingCleanedStop
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SingleTripViewModel", "Error fetching RT update: ${e.message}")
            }
        }
    }

    private fun fetchVehicleInfo() {
        viewModelScope.launch {
            val vehicleId =
                    tripData.value?.vehicle?.label
                            ?: tripData.value?.vehicle?.id ?: tripSelected.vehicle_id

            if (vehicleId.isNullOrBlank()) return@launch

            try {
                val encodedChateauId = URLEncoder.encode(tripSelected.chateau_id ?: "", "UTF-8")
                val url =
                        "https://birch.catenarymaps.org/get_vehicle_information_from_label/${encodedChateauId}/$vehicleId"
                val response = ktorClient.get(url).body<VehicleRealtimeDataResponse>()
                _vehicleData.value = response.data
            } catch (e: Exception) {
                Log.e("SingleTripViewModel", "Error fetching vehicle info: ${e.message}")
            }
        }
    }

    private fun startTimers() {
        // 1-second RT update timer
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                fetchRealtimeUpdate()
                fetchVehicleInfo() // Svelte logic also fetches vehicle rt
            }
        }

        // 30-second full refresh timer
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                fetchInitialTripData() // Refetch everything
            }
        }

        // 100ms current time updater
        viewModelScope.launch {
            while (true) {
                delay(100L)
                _currentTime.value = System.currentTimeMillis()
                updateStopProgress()
            }
        }
    }

    // --- Logic ported from Svelte ---

    private fun processStopTime(stoptime: TripStoptime): StopTimeCleaned {
        // This function replicates the initial processing of stoptimes
        // (calculating diffs, strike-throughs, etc.)
        val cleaned = StopTimeCleaned(raw = stoptime)

        if (cleaned.rtArrivalTime != null && stoptime.scheduled_arrival_time_unix_seconds != null) {
            cleaned.rtArrivalDiff =
                    cleaned.rtArrivalTime!! - stoptime.scheduled_arrival_time_unix_seconds
        }
        if (cleaned.rtDepartureTime != null &&
                        stoptime.scheduled_departure_time_unix_seconds != null
        ) {
            cleaned.rtDepartureDiff =
                    cleaned.rtDepartureTime!! - stoptime.scheduled_departure_time_unix_seconds
        }

        // ... add more logic from your Svelte file (e.g., show_both_departure_and_arrival) ...

        return cleaned
    }

    private fun mergeRtStopTime(existing: StopTimeCleaned, rt: StopTimeRefresh): StopTimeCleaned {
        // This function merges new RT data, like in your Svelte update_realtime_data
        val updated =
                existing.copy(raw = existing.raw.copy(rt_platform_string = rt.rt_platform_string))

        if (rt.rt_arrival?.time != null) {
            updated.rtArrivalTime = rt.rt_arrival.time
            updated.strikeArrival = true
            if (updated.raw.scheduled_arrival_time_unix_seconds != null) {
                updated.rtArrivalDiff =
                        updated.rtArrivalTime!! - updated.raw.scheduled_arrival_time_unix_seconds
            }
        }

        if (rt.rt_departure?.time != null) {
            updated.rtDepartureTime = rt.rt_departure.time
            updated.strikeDeparture = true
            if (updated.raw.scheduled_departure_time_unix_seconds != null) {
                updated.rtDepartureDiff =
                        updated.rtDepartureTime!! -
                                updated.raw.scheduled_departure_time_unix_seconds
            }
        }

        // ... add logic for arrival/departure time conflicts ...

        return updated
    }

    // --- Moving Dot State (Exposed) ---
    private val _movingDotSegmentIdx = MutableStateFlow(-1)
    val movingDotSegmentIdx = _movingDotSegmentIdx.asStateFlow()

    private val _movingDotProgress = MutableStateFlow(0f)
    val movingDotProgress = _movingDotProgress.asStateFlow()

    private fun updateStopProgress() {
        val nowSec = _currentTime.value / 1000
        var lastDepartedIdx = -1
        var currentAtStopIdx = -1

        val stopList = _stopTimes.value

        // Strict RT check: Do we have ANY realtime data?
        val hasAnyRealtime = stopList.any { it.rtArrivalTime != null || it.rtDepartureTime != null }

        stopList.forEachIndexed { i, stoptime ->
            // Logic ported from web frontend + Strict RT Fix
            var arrivalTimeToUse = stoptime.rtArrivalTime
            var departureTimeToUse = stoptime.rtDepartureTime

            // If we have ANY realtime data for the trip, we strictly ignore scheduled times
            // for the purpose of determining active stop / passed stops.
            // UNLESS the specific stop has no RT data, in which case it is implicitly
            // "unknown/future"
            // if we are strictly in RT mode (handled by null check below).
            // Actually, if a stop has NO RT data but the trip HAS RT data,
            // it usually means we haven't reached it yet or it's skipped?
            // "Active stop needs to be based on the realtime data and NOT the schedule data IF
            // there is realtime data."

            // However, we still need fallbacks for processing logic if we are NOT in strict RT
            // mode.

            if (hasAnyRealtime) {
                // Do not fallback to schedule.
                // arrivalTimeToUse and departureTimeToUse remain as is (possibly null).

                // However, we still need to apply the logic for "departed before arrival" fixups
                // just in case RT data is weird
                if (stoptime.raw.scheduled_departure_time_unix_seconds != null &&
                                arrivalTimeToUse != null
                ) {
                    if (stoptime.raw.scheduled_departure_time_unix_seconds < arrivalTimeToUse!!) {
                        departureTimeToUse = arrivalTimeToUse
                    }
                }
            } else {
                // Fallback to schedule if no RT at all
                // Same fixup logic
                if (stoptime.raw.scheduled_departure_time_unix_seconds != null &&
                                arrivalTimeToUse != null
                ) {
                    if (stoptime.raw.scheduled_departure_time_unix_seconds < arrivalTimeToUse!!) {
                        departureTimeToUse = arrivalTimeToUse
                    }
                }
            }

            // Express / Overtake edge case fixup
            if (arrivalTimeToUse != null &&
                            stoptime.raw.scheduled_arrival_time_unix_seconds != null &&
                            stoptime.rtDepartureTime != null
            ) {
                if (stoptime.raw.scheduled_arrival_time_unix_seconds > stoptime.rtDepartureTime!!) {
                    arrivalTimeToUse = stoptime.rtDepartureTime
                }
            }

            // Fallback for missing departure lookup in strict RT mode
            // If we have strict RT, and departure is null, but arrival is NOT null,
            // we should assume departure = arrival (just departed).
            if (hasAnyRealtime && departureTimeToUse == null && arrivalTimeToUse != null) {
                departureTimeToUse = arrivalTimeToUse
            }

            val dep =
                    if (hasAnyRealtime) departureTimeToUse
                    else (departureTimeToUse ?: stoptime.raw.scheduled_departure_time_unix_seconds)
            val arr =
                    if (hasAnyRealtime) arrivalTimeToUse
                    else (arrivalTimeToUse ?: stoptime.raw.scheduled_arrival_time_unix_seconds)

            // If we are in strict RT mode and a stop has NO RT data, it effectively hasn't happened
            // yet (is null).

            val hasDeparted = dep != null && dep <= nowSec
            val hasArrived = arr != null && arr <= nowSec

            if (hasDeparted) {
                lastDepartedIdx = i
            } else if (hasArrived && !hasDeparted && currentAtStopIdx == -1) {
                currentAtStopIdx = i
            }
        }

        _lastInactiveStopIdx.value = lastDepartedIdx
        _currentAtStopIdx.value = currentAtStopIdx

        // --- Moving Dot Calculation (Moved from Screen) ---
        var newMovingDotSegmentIdx = -1
        var newMovingDotProgress = 0f

        val isAtStation = currentAtStopIdx != -1

        if (!isAtStation && lastDepartedIdx != -1 && lastDepartedIdx < stopList.size - 1) {
            val prevStop = stopList[lastDepartedIdx]
            val nextStop = stopList[lastDepartedIdx + 1]

            val hasAnyRealtime =
                    stopList.any { it.rtArrivalTime != null || it.rtDepartureTime != null }

            // Apply same strict RT rule for dot interpolation
            val dep =
                    if (hasAnyRealtime) {
                        prevStop.rtDepartureTime?.toDouble() ?: prevStop.rtArrivalTime?.toDouble()
                    } else {
                        prevStop.rtDepartureTime?.toDouble()
                                ?: prevStop.raw.scheduled_departure_time_unix_seconds?.toDouble()
                                        ?: prevStop.raw.interpolated_stoptime_unix_seconds
                                        ?.toDouble()
                    }

            val arr =
                    if (hasAnyRealtime) {
                        nextStop.rtArrivalTime?.toDouble()
                    } else {
                        nextStop.rtArrivalTime?.toDouble()
                                ?: nextStop.raw.scheduled_arrival_time_unix_seconds?.toDouble()
                                        ?: nextStop.raw.interpolated_stoptime_unix_seconds
                                        ?.toDouble()
                    }

            val nowDouble = nowSec.toDouble()

            if (dep != null && arr != null && arr > dep) {
                newMovingDotSegmentIdx = lastDepartedIdx
                val total = arr - dep
                val elapsed = nowDouble - dep
                newMovingDotProgress = (elapsed / total).coerceIn(0.0, 1.0).toFloat()
            }
        }

        _movingDotSegmentIdx.value = newMovingDotSegmentIdx
        _movingDotProgress.value = newMovingDotProgress
    }

    fun toggleShowPreviousStops() {
        _showPreviousStops.update { !it }
    }

    // Factory to inject the tripSelected parameter
    companion object {
        fun factory(tripSelected: CatenaryStackEnum.SingleTrip): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SingleTripViewModel(tripSelected) as T
                }
            }
        }
    }
}
