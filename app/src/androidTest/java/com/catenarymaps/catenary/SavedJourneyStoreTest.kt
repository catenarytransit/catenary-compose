package com.catenarymaps.catenary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedJourneyStoreTest {
    @Test fun savedSnapshotSurvivesReopenAndIsDeduplicatedAndRemovable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "saved-journeys-test.db"
        context.deleteDatabase(name)
        val journey = DirectionsItinerary("2026-11-09T10:00:00Z", "2026-11-09T11:00:00Z",
            legs = listOf(DirectionsLeg("BUS", routeId = "42", legGeometry = DirectionsGeometry("test"))), id = "saved-trip")
        val snapshot = SavedJourney(journey, DirectionsLocation("Home", 34.0, -118.0),
            DirectionsLocation("Station", 34.1, -118.1), savedAt = 1L)
        try {
            SavedJourneyStore(context, name).use { it.save(snapshot) }
            SavedJourneyStore(context, name).use { store ->
                assertTrue(store.contains(journey.identity()))
                assertEquals(listOf(snapshot), store.readAll(java.time.Instant.parse("2026-11-09T12:00:00Z")))
                val updated = snapshot.copy(savedAt = 2L, journey = journey.copy(duration = 3500.0))
                store.save(updated)
                assertEquals(listOf(updated), store.readAll(java.time.Instant.parse("2026-11-09T12:00:00Z")))
                store.remove(journey.identity())
                assertFalse(store.contains(journey.identity()))
                assertTrue(store.readAll(java.time.Instant.parse("2026-11-09T12:00:00Z")).isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun expiresOnlyAfterTwelveHoursAndPersistsDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "saved-journeys-expiry-test.db"
        context.deleteDatabase(name)
        val arrival = java.time.Instant.parse("2026-11-10T12:00:00Z")
        val journey = DirectionsItinerary("2026-11-10T11:00:00Z", arrival.toString(), legs = emptyList(), id = "expiry")
        val snapshot = SavedJourney(journey, null, null)
        try {
            SavedJourneyStore(context, name).use { store ->
                store.save(snapshot)
                assertEquals(listOf(snapshot), store.readAll(arrival.plusSeconds(12 * 60 * 60)))
                assertTrue(store.readAll(arrival.plusSeconds(12 * 60 * 60 + 1)).isEmpty())
            }
            SavedJourneyStore(context, name).use { store ->
                assertFalse(store.contains(journey.identity()))
                assertTrue(store.readAll(arrival).isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }

}
