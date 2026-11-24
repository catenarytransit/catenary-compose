package com.catenarymaps.catenary

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealtimeDelayTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun reportedZeroSecondsIsDistinctFromMissingRealtime() {
        assertEquals(0L, knownRealtimeDelaySeconds(1795113600L, 1795113600L))
        assertEquals(180L, knownRealtimeDelaySeconds(1795113780L, 1795113600L))
        assertEquals(-60L, knownRealtimeDelaySeconds(1795113540L, 1795113600L))
        assertNull(knownRealtimeDelaySeconds(null, 1795113600L))
        assertNull(knownRealtimeDelaySeconds(1795113600L, null))
    }

    @Test
    fun actualRamondaInitialTripFormatPreservesOnTimeTimestamps() {
        // Metrolink 612: the wire payload contains a time but a null delay.
        // The Android RtTime model intentionally ignores GTFS-RT's optional delay.
        val stop = json.decodeFromString<TripStoptime>(
            """{
            "stop_id": "107",
            "name": "L.A. Union Station",
            "longitude": -118.233513, "latitude": 34.055729,
            "gtfs_stop_sequence": 1,
            "scheduled_arrival_time_unix_seconds": 1795113600,
            "scheduled_departure_time_unix_seconds": 1795113600,
            "rt_arrival": {"delay": null, "time": 1795113600, "uncertainty": null},
            "rt_departure": {"delay": null, "time": 1795113600, "uncertainty": null},
            "schedule_relationship": 0
        }"""
        )
        assertEquals(1795113600L, stop.rt_arrival?.time)
        assertEquals(1795113600L, stop.rt_departure?.time)
        assertEquals(
            0L, knownRealtimeDelaySeconds(
                stop.rt_arrival?.time, stop.scheduled_arrival_time_unix_seconds
            )
        )
        assertEquals(
            0L, knownRealtimeDelaySeconds(
                stop.rt_departure?.time, stop.scheduled_departure_time_unix_seconds
            )
        )

        val cleaned = StopTimeCleaned(raw = stop)
        assertEquals(1795113600L, cleaned.rtDepartureTime)
        assertEquals(1795113600L, cleaned.rtArrivalTime)
    }
}
