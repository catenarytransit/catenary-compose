package com.catenarymaps.catenary

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleTripViewModelCachedSnapshotTest {
    @Test
    fun cachedInitialTripCanArriveDuringViewModelConstruction() {
        val now = System.currentTimeMillis() / 1000
        val selected = CatenaryStackEnum.SingleTrip(
            chateau_id = "test", trip_id = "trip", route_id = "route",
            start_time = "12:00:00", start_date = "20261119",
            vehicle_id = null, route_type = 3
        )
        val identity = RamondaTripIdentity("test", "trip", "route", "20261119", "12:00:00")
        val initial = TripDataResponse(
            trip_id = "trip", chateau = "test",
            is_cancelled = false, deleted = false,
            stoptimes = listOf(
                TripStoptime(
                    stop_id = "board", longitude = 0.0, latitude = 0.0,
                    gtfs_stop_sequence = 1,
                    scheduled_departure_time_unix_seconds = now - 60
                ),
                TripStoptime(
                    stop_id = "alight", longitude = 0.0, latitude = 0.0,
                    gtfs_stop_sequence = 2,
                    scheduled_arrival_time_unix_seconds = now + 60
                )
            )
        )
        // StateFlow replays this snapshot immediately to a Main.immediate collector,
        // before the ViewModel constructor returns.
        val snapshots = MutableStateFlow(mapOf(identity to RamondaTripSnapshot(initial = initial)))
        val store = ViewModelStore()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                val model = SingleTripViewModel(
                    selected, snapshots,
                    subscribeTrip = {}, unsubscribeTrip = {}
                )
                store.put("cached-trip", model)
                assertFalse(model.isLoading.value)
                assertNull(model.error.value)
                assertEquals("trip", model.tripData.value?.trip_id)
                assertEquals(2, model.stopTimes.value.size)
                assertEquals(0, model.lastInactiveStopIdx.value)
                assertEquals(0, model.movingDotSegmentIdx.value)
                assertTrue(model.movingDotProgress.value > 0f)
                assertTrue(model.movingDotProgress.value < 1f)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test
    fun cachedUpdateIsAppliedAfterInitialTrip() {
        val now = System.currentTimeMillis() / 1000
        val identity = RamondaTripIdentity("test", "trip")
        val selected = CatenaryStackEnum.SingleTrip(
            "test", "trip", null, null, null, null, 3
        )
        val initial = TripDataResponse(
            trip_id = "trip", is_cancelled = false, deleted = false,
            stoptimes = listOf(
                TripStoptime(
                    stop_id = "board", longitude = 0.0, latitude = 0.0,
                    gtfs_stop_sequence = 1,
                    scheduled_departure_time_unix_seconds = now + 120
                )
            )
        )
        val update = TripRtUpdateData(
            trip_id = "trip",
            stoptimes = listOf(
                StopTimeRefresh(
                    stop_id = "board", gtfs_stop_sequence = 1,
                    rt_departure = RtTime(now + 180)
                )
            )
        )
        val snapshots = MutableStateFlow(
            mapOf(identity to RamondaTripSnapshot(initial = initial, latestUpdate = update))
        )
        val store = ViewModelStore()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                val model = SingleTripViewModel(
                    selected, snapshots,
                    subscribeTrip = {}, unsubscribeTrip = {}
                )
                store.put("updated-trip", model)
                assertFalse(model.isLoading.value)
                assertNull(model.error.value)
                assertEquals(now + 180, model.stopTimes.value.single().rtDepartureTime)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
