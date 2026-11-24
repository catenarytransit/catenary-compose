package com.catenarymaps.catenary

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkTooltipPositionTest {
    @Test
    fun remainsBelowBookmarkWithUpwardPointingCaret() {
        val anchor = IntRect(left = 32, top = 180, right = 76, bottom = 224)
        assertEquals(
            IntOffset(32, 228),
            bookmarkTooltipPosition(anchor, IntSize(440, 900), IntSize(280, 100), 4)
        )
    }

    @Test
    fun doesNotFlipAboveBookmarkNearBottomOfWindow() {
        val anchor = IntRect(left = 32, top = 180, right = 76, bottom = 224)
        assertEquals(
            IntOffset(20, 228),
            bookmarkTooltipPosition(anchor, IntSize(300, 250), IntSize(280, 100), 4)
        )
    }

    @Test
    fun clampsHorizontalPositionToAvailableScreenWidth() {
        val anchor = IntRect(left = 380, top = 60, right = 424, bottom = 104)
        assertEquals(
            IntOffset(160, 108),
            bookmarkTooltipPosition(anchor, IntSize(440, 800), IntSize(280, 100), 4)
        )
        assertEquals(
            IntOffset(0, 108),
            bookmarkTooltipPosition(anchor, IntSize(240, 800), IntSize(280, 100), 4)
        )
    }
}
