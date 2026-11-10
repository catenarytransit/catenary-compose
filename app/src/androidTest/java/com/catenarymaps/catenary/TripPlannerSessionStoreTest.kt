package com.catenarymaps.catenary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TripPlannerSessionStoreTest {
    @Test fun completedSessionSurvivesANewStoreAndCanBeCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "trip-planner-session-test.json")
        val store = TripPlannerSessionStore(file)
        val session = TripPlannerSession(
            origin = DirectionsLocation("Start", 34.0, -118.0),
            destination = DirectionsLocation("End", 34.1, -118.1),
            options = DirectionsOptions(), arriveBy = false, now = true, time = 1793898000,
            itineraries = listOf(DirectionsItinerary("2026-11-05T17:00:00Z", "2026-11-05T18:00:00Z",
                legs = listOf(DirectionsLeg("BUS")))), details = true
        )
        try {
            store.write(session)
            assertEquals(session, TripPlannerSessionStore(file).read())
            store.write(null)
            assertNull(TripPlannerSessionStore(file).read())
            file.writeText("{broken")
            assertNull(TripPlannerSessionStore(file).read())
            assertFalse(file.exists())
        } finally {
            store.write(null)
        }
    }
}
