// In a new file, e.g., SingleTripViewModel.kt
package com.catenarymaps.catenary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class SingleTripViewModel(
    private val tripSelected: CatenaryStackEnum.SingleTrip,
    private val snapshots: StateFlow<Map<RamondaTripIdentity, RamondaTripSnapshot>> = RamondaWebSocket.tripSnapshots,
    private val subscribeTrip: (RamondaTripIdentity) -> Unit = RamondaWebSocket::subscribe,
    private val unsubscribeTrip: (RamondaTripIdentity) -> Unit = RamondaWebSocket::unsubscribe
) : ViewModel() {

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

    private val _coachSequenceScreenShown = MutableStateFlow(false)
    val coachSequenceScreenShown = _coachSequenceScreenShown.asStateFlow()

    fun toggleCoachSequenceScreen(show: Boolean) {
        _coachSequenceScreenShown.value = show
    }

    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    val currentTime = _currentTime.asStateFlow()

    private val _lastInactiveStopIdx = MutableStateFlow(-1)
    val lastInactiveStopIdx = _lastInactiveStopIdx.asStateFlow()

    private val _currentAtStopIdx = MutableStateFlow(-1)
    val currentAtStopIdx = _currentAtStopIdx.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private val subscription = tripSelected.trip_id?.takeIf { it.isNotBlank() }?.let { tripId ->
        RamondaTripIdentity(
            tripSelected.chateau_id, tripId, tripSelected.route_id,
            tripSelected.start_date, tripSelected.start_time
        )
    }

    // Serialize stop-time merges and progress scans on one background lane.
    // StateFlow is thread-safe; the UI collects its published values on Main.
    private val processingDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var lastAppliedUpdate: TripRtUpdateData? = null
    private val _movingDotSegmentIdx = MutableStateFlow(-1)
    val movingDotSegmentIdx = _movingDotSegmentIdx.asStateFlow()
    private val _movingDotProgress = MutableStateFlow(0f)
    val movingDotProgress = _movingDotProgress.asStateFlow()

    init {
        subscription?.let(subscribeTrip)
        observeWebSocketData()
        startTimers()
    }

    private fun observeWebSocketData() {
        val identity = subscription ?: return
        viewModelScope.launch(processingDispatcher) {
            snapshots.collect { snapshots ->
                val snapshot = snapshots[identity] ?: return@collect
                snapshot.initial?.let { data ->
                    if (data.trip_id == identity.trip_id && data !== _tripData.value) {
                        handleInitialTripData(data)
                        // If an update arrived before initial_trip, reapply it to
                        // the newly hydrated scheduled stop-time list.
                        lastAppliedUpdate = null
                    }
                }
                snapshot.latestUpdate?.let { data ->
                    if (data !== lastAppliedUpdate &&
                        (data.trip_id == null || data.trip_id == identity.trip_id) &&
                        (data.chateau == null || data.chateau == identity.chateau)
                    ) {
                        lastAppliedUpdate = data
                        handleRealtimeUpdate(data)
                    }
                }
            }
        }
    }

    private fun handleInitialTripData(data: TripDataResponse) {
        _tripData.value = data
        _alerts.value = data.alert_id_to_alert
        _stopTimes.value = data.stoptimes.map { processStopTime(it) }
        updateStopProgress()

        _isLoading.value = false
        _error.value = null

        fetchVehicleInfo()
    }

    private fun handleRealtimeUpdate(data: TripRtUpdateData) {
        val newStopTimesMap = data.stoptimes.associateBy { it.gtfs_stop_sequence ?: it.stop_id }

        _stopTimes.update { currentList ->
            currentList.map { existingCleanedStop ->
                val key =
                    existingCleanedStop.raw.gtfs_stop_sequence
                        ?: existingCleanedStop.raw.stop_id
                val newRtData = newStopTimesMap[key]

                if (newRtData != null) {
                    mergeRtStopTime(existingCleanedStop, newRtData)
                } else {
                    existingCleanedStop
                }
            }
        }
        updateStopProgress()
    }

    private fun fetchVehicleInfo() {
        viewModelScope.launch(Dispatchers.Default) {
            val vehicleId =
                tripData.value?.vehicle?.label
                    ?: tripData.value?.vehicle?.id ?: tripSelected.vehicle_id

            if (vehicleId.isNullOrBlank()) return@launch

            try {
                _vehicleData.value = fetchVehicleRealtimeForLabel(
                    tripSelected.chateau_id, vehicleId
                ).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SingleTripViewModel", "Error fetching vehicle info: ${e.message}")
            }
        }
    }

    private fun startTimers() {
        // Vehicle info poller (separate from trip WS)
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                fetchVehicleInfo()
            }
        }

        // 100ms current time updater, serialized with incoming realtime snapshots.
        viewModelScope.launch(processingDispatcher) {
            while (true) {
                delay(100L)
                _currentTime.value = System.currentTimeMillis()
                updateStopProgress()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscription?.let(unsubscribeTrip)
    }

    // --- Logic ported from Svelte ---

    private fun processStopTime(stoptime: TripStoptime): StopTimeCleaned {
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

        return cleaned
    }

    private fun mergeRtStopTime(existing: StopTimeCleaned, rt: StopTimeRefresh): StopTimeCleaned {
        val updated =
            existing.copy(
                raw = existing.raw.copy(
                    rt_platform_string = rt.rt_platform_string,
                    schedule_relationship = rt.schedule_relationship
                        ?: existing.raw.schedule_relationship
                )
            )

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

        return updated
    }

    // --- Moving Dot State ---
    private fun updateStopProgress() {
        val nowSec = _currentTime.value / 1000
        var lastDepartedIdx = -1
        var currentAtStopIdx = -1

        val stopList = _stopTimes.value

        val hasAnyRealtime = stopList.any { it.rtArrivalTime != null || it.rtDepartureTime != null }

        stopList.forEachIndexed { i, stoptime ->
            var arrivalTimeToUse = stoptime.rtArrivalTime
            var departureTimeToUse = stoptime.rtDepartureTime

            if (hasAnyRealtime) {
                if (stoptime.raw.scheduled_departure_time_unix_seconds != null &&
                    arrivalTimeToUse != null
                ) {
                    if (stoptime.raw.scheduled_departure_time_unix_seconds < arrivalTimeToUse) {
                        departureTimeToUse = arrivalTimeToUse
                    }
                }
            } else {
                if (stoptime.raw.scheduled_departure_time_unix_seconds != null &&
                    arrivalTimeToUse != null
                ) {
                    if (stoptime.raw.scheduled_departure_time_unix_seconds < arrivalTimeToUse) {
                        departureTimeToUse = arrivalTimeToUse
                    }
                }
            }

            if (arrivalTimeToUse != null &&
                stoptime.raw.scheduled_arrival_time_unix_seconds != null &&
                stoptime.rtDepartureTime != null
            ) {
                if (stoptime.raw.scheduled_arrival_time_unix_seconds > stoptime.rtDepartureTime!!) {
                    arrivalTimeToUse = stoptime.rtDepartureTime
                }
            }

            if (hasAnyRealtime && departureTimeToUse == null && arrivalTimeToUse != null) {
                departureTimeToUse = arrivalTimeToUse
            }

            val dep =
                if (hasAnyRealtime) departureTimeToUse
                else (departureTimeToUse ?: stoptime.raw.scheduled_departure_time_unix_seconds)
            val arr =
                if (hasAnyRealtime) arrivalTimeToUse
                else (arrivalTimeToUse ?: stoptime.raw.scheduled_arrival_time_unix_seconds)

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

        var newMovingDotSegmentIdx = -1
        var newMovingDotProgress = 0f

        val isAtStation = currentAtStopIdx != -1

        if (!isAtStation && lastDepartedIdx != -1 && lastDepartedIdx < stopList.size - 1) {
            val prevStop = stopList[lastDepartedIdx]
            val nextStop = stopList[lastDepartedIdx + 1]

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
