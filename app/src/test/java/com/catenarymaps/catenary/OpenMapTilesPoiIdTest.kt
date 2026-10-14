package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMapTilesPoiIdTest {
    @Test
    fun nodeIdsFromCurrentPoiTiles() {
        assertEquals(
            OsmPoiRef("node", "3513479974"),
            decodeOpenMapTilesPoiId("35134799741")
        )
        assertEquals(
            OsmPoiRef("node", "6776211959"),
            decodeOpenMapTilesPoiId("67762119591")
        )
    }

    @Test
    fun waysAndRelations() {
        assertEquals(OsmPoiRef("way", "98765"), decodeOpenMapTilesPoiId("987652"))
        assertEquals(OsmPoiRef("relation", "12345"), decodeOpenMapTilesPoiId("123453"))
    }

    @Test
    fun rejectsInvalidOrUnrecognizedIds() {
        listOf(null, "", "abc", "49", "0", "-35134799741", "1234567890123456789011")
            .forEach { assertNull(decodeOpenMapTilesPoiId(it)) }
    }
}
