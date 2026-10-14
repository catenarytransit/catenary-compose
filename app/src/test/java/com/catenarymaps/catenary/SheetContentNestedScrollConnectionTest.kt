package com.catenarymaps.catenary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetContentNestedScrollConnectionTest {
    @Test
    fun quarterOfViewportVisiblePermitsFastDownwardFling() {
        assertTrue(shouldCarryDownwardFling(750f, 1000f, 950f, 900f))
        assertTrue(shouldCarryDownwardFling(500f, 1000f, 950f, 900f))
    }

    @Test
    fun mostlyHiddenSheetDoesNotInheritListFling() {
        assertFalse(shouldCarryDownwardFling(751f, 1000f, 950f, 900f))
        assertFalse(shouldCarryDownwardFling(936f, 1000f, 2000f, 900f))
    }

    @Test
    fun smallOrUpwardFlingDoesNotCloseSheet() {
        assertFalse(shouldCarryDownwardFling(500f, 1000f, 899f, 900f))
        assertFalse(shouldCarryDownwardFling(500f, 1000f, -2000f, 900f))
    }

    @Test
    fun invalidViewportDoesNotCarryVelocity() {
        assertFalse(shouldCarryDownwardFling(0f, 0f, 1500f, 900f))
        assertFalse(shouldCarryDownwardFling(Float.NaN, 1000f, 1500f, 900f))
    }
}
