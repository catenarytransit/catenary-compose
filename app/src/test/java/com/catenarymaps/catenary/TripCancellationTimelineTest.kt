package com.catenarymaps.catenary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripCancellationTimelineTest {
        private fun classify(vararg stops: Boolean, tripCancelled: Boolean = false) =
                classifyTripCancellation(stops.toList(), tripCancelled)

        @Test fun normalTrip() {
                val result = classify(false, false, false)
                assertEquals(listOf(false, false), result.dashedSegments)
                assertNull(result.exitOnlyStopIndex)
        }
        @Test fun shortenedBeginning() {
                val result = classify(true, true, false, false)
                assertEquals(listOf(true, true, false), result.dashedSegments)
                assertNull(result.exitOnlyStopIndex)
        }
        @Test fun shortenedEnd() {
                val result = classify(false, false, true, true)
                assertEquals(listOf(false, true, true), result.dashedSegments)
                assertEquals(1, result.exitOnlyStopIndex)
        }
        @Test fun bothEnds() {
                val result = classify(true, false, false, true, true)
                assertEquals(listOf(true, false, true, true), result.dashedSegments)
                assertEquals(2, result.exitOnlyStopIndex)
        }
        @Test fun midTripDetourKeepsSolidLine() {
                val result = classify(false, true, true, false)
                assertEquals(listOf(false, true, true, false), result.cancelledStops)
                assertEquals(listOf(false, false, false), result.dashedSegments)
                assertNull(result.exitOnlyStopIndex)
        }
        @Test fun wholeTripCancelled() {
                val result = classify(false, false, false, tripCancelled = true)
                assertEquals(listOf(true, true, true), result.cancelledStops)
                assertEquals(listOf(true, true), result.dashedSegments)
                assertNull(result.exitOnlyStopIndex)
        }
        @Test fun emptyAndFullySkipped() {
                assertEquals(emptyList<Boolean>(), classify().dashedSegments)
                assertNull(classify().exitOnlyStopIndex)
                assertEquals(listOf(true, true), classify(true, true, true).dashedSegments)
                assertNull(classify(true, true, true).exitOnlyStopIndex)
        }
        @Test fun statusChangesUpdateLastServedStop() {
                assertEquals(1, classify(false, false, true).exitOnlyStopIndex)
                assertNull(classify(false, false, false).exitOnlyStopIndex)
        }
}
