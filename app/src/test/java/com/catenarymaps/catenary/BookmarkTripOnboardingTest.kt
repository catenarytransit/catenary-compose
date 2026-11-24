package com.catenarymaps.catenary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkTripOnboardingTest {
    @Test
    fun promptsOnlyForAValidUnsavedJourneyBeforeFirstUse() {
        assertTrue(shouldShowBookmarkTooltip(false, false, false, true))
        assertFalse(shouldShowBookmarkTooltip(true, false, false, true))
        assertFalse(shouldShowBookmarkTooltip(false, true, false, true))
        assertFalse(shouldShowBookmarkTooltip(false, false, true, true))
        assertFalse(shouldShowBookmarkTooltip(false, false, false, false))
    }
}
