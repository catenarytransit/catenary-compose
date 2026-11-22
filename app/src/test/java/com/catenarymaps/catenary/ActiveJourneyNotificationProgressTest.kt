package com.catenarymaps.catenary

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ActiveJourneyNotificationProgressTest {
    private fun epoch(value: String): Long = Instant.parse(value).epochSecond

    @Test fun timeProgressIsClampedWithinEachLeg() {
        val journey = DirectionsItinerary("2026-11-22T10:00:00Z", "2026-11-22T11:00:00Z",
            legs = listOf(DirectionsLeg("BUS", startTime = "2026-11-22T10:00:00Z",
                endTime = "2026-11-22T11:00:00Z")))
        assertEquals(0, notificationLegProgress(journey, epoch("2026-11-22T09:40:00Z"))?.percent)
        assertEquals(50, notificationLegProgress(journey, epoch("2026-11-22T10:30:00Z"))?.percent)
        assertEquals(100, notificationLegProgress(journey, epoch("2026-11-22T11:20:00Z"))?.percent)
    }

    @Test fun transferSelectsNextTransitLegAndStartsAtZero() {
        val journey = DirectionsItinerary("2026-11-22T10:00:00Z", "2026-11-22T11:00:00Z",
            legs = listOf(
                DirectionsLeg("BUS", startTime = "2026-11-22T10:00:00Z", endTime = "2026-11-22T10:20:00Z"),
                DirectionsLeg("WALK", startTime = "2026-11-22T10:20:00Z", endTime = "2026-11-22T10:25:00Z"),
                DirectionsLeg("TRAIN", startTime = "2026-11-22T10:30:00Z", endTime = "2026-11-22T11:00:00Z")
            ))
        val atTransfer = notificationLegProgress(journey, epoch("2026-11-22T10:23:00Z"))!!
        assertEquals(2, atTransfer.index)
        assertEquals(0, atTransfer.percent)
        assertTrue(atTransfer.upcoming)
        val riding = notificationLegProgress(journey, epoch("2026-11-22T10:45:00Z"))!!
        assertEquals(50, riding.percent)
        assertFalse(riding.upcoming)
    }

    @Test fun initialWalkAndFinalWalkAreTheirOwnLegs() {
        val journey = DirectionsItinerary("2026-11-22T10:00:00Z", "2026-11-22T11:00:00Z",
            legs = listOf(
                DirectionsLeg("WALK", startTime = "2026-11-22T10:00:00Z", endTime = "2026-11-22T10:10:00Z"),
                DirectionsLeg("TRAIN", startTime = "2026-11-22T10:15:00Z", endTime = "2026-11-22T10:50:00Z"),
                DirectionsLeg("WALK", startTime = "2026-11-22T10:50:00Z", endTime = "2026-11-22T11:00:00Z")
            ))
        assertEquals(0, notificationLegProgress(journey, epoch("2026-11-22T10:05:00Z"))?.index)
        assertEquals(2, notificationLegProgress(journey, epoch("2026-11-22T10:55:00Z"))?.index)
    }

    @Test fun notifierDeduplicatesBookmarksAndActiveJourney() {
        val journey = DirectionsItinerary("2026-11-22T10:00:00Z", "2026-11-22T11:00:00Z",
            legs = listOf(DirectionsLeg("BUS", startTime = "2026-11-22T10:00:00Z",
                endTime = "2026-11-22T11:00:00Z")), id = "trip")
        val bookmark = SavedJourney(journey, null, null)
        assertEquals(1, journeysForNotifier(listOf(bookmark), bookmark).size)
        assertEquals(1, journeysForNotifier(emptyList(), bookmark).size)
        assertEquals(1, journeysForNotifier(listOf(bookmark), null).size)
        assertTrue(journeysForNotifier(emptyList(), null).isEmpty())
    }
}
