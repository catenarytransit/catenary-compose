package com.catenarymaps.catenary

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerWidthTest {
    @Test
    fun phoneUsesEntireWidth() {
        assertEquals(360.dp, drawerWidthForWindow(360.dp))
        assertEquals(599.dp, drawerWidthForWindow(599.dp))
    }

    @Test
    fun tabletUsesHalfWidth() {
        assertEquals(300.dp, drawerWidthForWindow(600.dp))
        assertEquals(400.dp, drawerWidthForWindow(800.dp))
        assertEquals(600.dp, drawerWidthForWindow(1200.dp))
    }

    @Test
    fun veryWideWindowsDoNotGrowDrawer() {
        assertEquals(600.dp, drawerWidthForWindow(1600.dp))
        assertEquals(600.dp, drawerWidthForWindow(2560.dp))
    }

    @Test
    fun windowResizeRecomputesWidth() {
        assertEquals(600.dp, drawerWidthForWindow(2400.dp))
        assertEquals(550.dp, drawerWidthForWindow(1100.dp))
        assertEquals(500.dp, drawerWidthForWindow(500.dp))
    }
}
