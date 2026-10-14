package com.catenarymaps.catenary

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StationHistoryTest {
    @Test
    fun osmItemRoundTripRetainsIdentityAndDetails() {
        val entry = RecentStation(
            kind = RecentStationKind.OSM_ITEM,
            id = "12345",
            name = "City Hall",
            osmClass = "way",
            osmType = "venue",
            address = "1 Main Street",
            names = mapOf("default" to "City Hall", "name:fr" to "Hotel de Ville"),
            latitude = 34.0,
            longitude = -118.0,
        )
        val restored = Json.decodeFromString(
            RecentStation.serializer(),
            Json.encodeToString(RecentStation.serializer(), entry)
        )
        assertEquals(entry, restored)
        assertEquals("osm-item:way:12345", restored.stableKey())
        assertNotEquals(restored.stableKey(), restored.copy(osmClass = "node").stableKey())
        assertNotEquals(restored.stableKey(), RecentStation(
            kind = RecentStationKind.OSM_STATION, id = "12345", name = "City Hall"
        ).stableKey())
    }

    @Test
    fun olderHistoryEntriesStillDecode() {
        val stop = Json.decodeFromString(
            RecentStation.serializer(),
            """{"kind":"GTFS_STOP","id":"42","name":"Main","chateauId":"operator"}"""
        )
        val station = Json.decodeFromString(
            RecentStation.serializer(),
            """{"kind":"OSM_STATION","id":"42","name":"Central"}"""
        )
        assertEquals("stop:operator:42", stop.stableKey())
        assertEquals("osm:42", station.stableKey())
        assertEquals(null, stop.osmClass)
        assertEquals(emptyMap<String, String>(), station.names)
    }
}
