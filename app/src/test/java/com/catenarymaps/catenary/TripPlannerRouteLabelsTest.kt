package com.catenarymaps.catenary

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class TripPlannerRouteLabelsTest {
    @Test fun nationalRailUsesExistingOperatorExceptions() {
        for (agency in listOf("TW", "ME", "LO", "XR", "HX")) {
            assertTrue(NationalRailUtils.showsRouteName("nationalrailuk", agency))
        }
        assertFalse(NationalRailUtils.showsRouteName("nationalrailuk", "GW"))
        assertFalse(NationalRailUtils.showsRouteName("nationalrailuk", null))
        assertTrue(NationalRailUtils.showsRouteName("schweiz", null))
    }

    @Test fun routingAgencyIsDecodedAndOlderSavedLegsRemainReadable() {
        val leg = Json.decodeFromString<DirectionsLeg>("""{"mode":"RAIL","agencyId":"XR"}""")
        assertEquals("XR", leg.agencyId)
        assertNull(Json.decodeFromString<DirectionsLeg>("""{"mode":"RAIL"}""").agencyId)
    }

    @Test fun timelineRemovesOnlyTrailingLineAndBranchWords() {
        assertEquals("Elizabeth", timelineRouteName("Elizabeth Line"))
        assertEquals("Red", timelineRouteName(" Red BRANCH "))
        assertEquals("Red", timelineRouteName("Red Line branch"))
        for (name in listOf("Skyline", "Branchville", "Line A", "Branch Road")) {
            assertEquals(name, timelineRouteName(name))
        }
    }
}
