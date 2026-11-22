package com.catenarymaps.catenary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMapSheetPolicyTest {
    @Test fun rotatedPhoneUsesTwoAnchors() {
        assertTrue(usesTwoSheetAnchors(isLandscape = true, smallestWidthDp = 393))
        assertFalse(usesTwoSheetAnchors(isLandscape = false, smallestWidthDp = 393))
    }

    @Test fun tabletsKeepMiddleAnchorInBothOrientations() {
        assertFalse(usesTwoSheetAnchors(isLandscape = true, smallestWidthDp = 600))
        assertFalse(usesTwoSheetAnchors(isLandscape = true, smallestWidthDp = 800))
    }

    @Test fun nearbyDeparturesAlwaysUsesZeroPersistentPadding() {
        assertFalse(usesPersistentDrawerPadding(nearbyDeparturesVisible = true, isPortraitPhone = true))
        assertFalse(usesPersistentDrawerPadding(nearbyDeparturesVisible = true, isPortraitPhone = false))
    }

    @Test fun portraitPhoneOnlyUsesTemporarySelectionPadding() {
        assertFalse(usesPersistentDrawerPadding(nearbyDeparturesVisible = false, isPortraitPhone = true))
        assertTrue(usesPersistentDrawerPadding(nearbyDeparturesVisible = false, isPortraitPhone = false))
    }

    @Test fun gpsRecenterUsesSpaceAbovePortraitPhoneDrawer() {
        val padding = gpsRecenterPadding(
            cameraPadding = PaddingValues(0.dp),
            mapHeight = 800.dp,
            sheetTop = 360.dp,
            sideDrawerWidth = 0.dp,
            fullWidthSheet = true,
            sideDrawerVisible = true,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(440.dp, padding.calculateBottomPadding())
        assertEquals(180.dp, mapViewportCenter(400.dp, 800.dp, padding, LayoutDirection.Ltr).y)
    }

    @Test fun gpsRecenterMovesCenterAwayFromTabletSideDrawer() {
        val padding = gpsRecenterPadding(
            cameraPadding = PaddingValues(0.dp),
            mapHeight = 800.dp,
            sheetTop = 240.dp,
            sideDrawerWidth = 300.dp,
            fullWidthSheet = false,
            sideDrawerVisible = true,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(300.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(350.dp, mapViewportCenter(400.dp, 800.dp, padding, LayoutDirection.Ltr).x)
        assertEquals(0.dp, padding.calculateBottomPadding())
    }

    @Test fun gpsRecenterPreservesExistingPaddingWhenNothingIsObscured() {
        val existing = PaddingValues(start = 24.dp, top = 12.dp, end = 8.dp, bottom = 16.dp)
        val padding = gpsRecenterPadding(
            cameraPadding = existing,
            mapHeight = 600.dp,
            sheetTop = 600.dp,
            sideDrawerWidth = 300.dp,
            fullWidthSheet = false,
            sideDrawerVisible = false,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(existing, padding)
        assertEquals(308.dp, mapViewportCenter(600.dp, 600.dp, padding, LayoutDirection.Ltr).x)
        assertEquals(298.dp, mapViewportCenter(600.dp, 600.dp, padding, LayoutDirection.Ltr).y)
    }
    @Test fun activeJourneyPaddingReservesChromeWithoutDoubleCountingSheet() {
        val base = PaddingValues(start = 24.dp, top = 12.dp, end = 8.dp, bottom = 110.dp)
        val active = activeJourneyMapPadding(base, 72.dp, true, LayoutDirection.Ltr)
        assertEquals(110.dp, active.calculateBottomPadding())
        assertEquals(24.dp, active.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(8.dp, active.calculateRightPadding(LayoutDirection.Ltr))

        val largerChrome = activeJourneyMapPadding(PaddingValues(0.dp), 100.dp, true, LayoutDirection.Ltr)
        assertEquals(100.dp, largerChrome.calculateBottomPadding())
        assertEquals(100.dp, activeJourneyControlsClearance(true, 100.dp))
    }

    @Test fun hidingJourneyRestoresExistingNearbyPaddingAndControlPositions() {
        val base = PaddingValues(0.dp)
        assertEquals(base, activeJourneyMapPadding(base, 96.dp, false, LayoutDirection.Ltr))
        assertEquals(0.dp, activeJourneyControlsClearance(false, 96.dp))
    }

    @Test fun gpsRecenterRespectsActiveJourneyOcclusion() {
        val active = activeJourneyMapPadding(PaddingValues(0.dp), 96.dp, true, LayoutDirection.Ltr)
        val padding = gpsRecenterPadding(
            cameraPadding = active, mapHeight = 800.dp, sheetTop = 740.dp,
            sideDrawerWidth = 0.dp, fullWidthSheet = true,
            sideDrawerVisible = false, layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(96.dp, padding.calculateBottomPadding())
        assertEquals(352.dp, mapViewportCenter(400.dp, 800.dp, padding, LayoutDirection.Ltr).y)
    }

}
