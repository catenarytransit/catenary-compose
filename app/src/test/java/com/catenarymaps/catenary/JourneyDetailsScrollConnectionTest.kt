package com.catenarymaps.catenary

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class JourneyDetailsScrollConnectionTest {
    @Test fun downCollapsesAndReversingUpImmediatelyExpands() {
        var collapsed = false
        val connection = JourneyDetailsScrollConnection { collapsed = it }
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput))
        assertTrue(collapsed)
        connection.onPreScroll(Offset(0f, -1f), NestedScrollSource.UserInput)
        assertTrue(collapsed)
        connection.onPreScroll(Offset(0f, 1f), NestedScrollSource.UserInput)
        assertFalse(collapsed)
    }

    @Test fun horizontalInputAndFlingDoNotOverrideLastUserDirection() {
        var collapsed = false
        val connection = JourneyDetailsScrollConnection { collapsed = it }
        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, 20f), NestedScrollSource.SideEffect)
        connection.onPreScroll(Offset(10f, 0f), NestedScrollSource.UserInput)
        connection.onPreScroll(Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(collapsed)
        connection.onPreScroll(Offset(0f, 10f), NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, -20f), NestedScrollSource.SideEffect)
        assertFalse(collapsed)
    }
}
