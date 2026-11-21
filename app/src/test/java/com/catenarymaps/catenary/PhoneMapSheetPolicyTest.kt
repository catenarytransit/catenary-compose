package com.catenarymaps.catenary

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
}
