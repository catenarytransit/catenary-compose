package com.catenarymaps.catenary

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JourneyDetailsScrollConnectionTest {
    private var fraction = 0f
    private val connection = JourneyDetailsScrollConnection(24f, { 100f }, { fraction }, { fraction = it })
    private fun scroll(y: Float) = connection.onPreScroll(Offset(0f, y), NestedScrollSource.UserInput)

    @Test fun thresholdThenLinearHeightAndClampedConsumption() {
        assertEquals(Offset.Zero, scroll(-20f))
        assertEquals(0f, fraction, 0.001f)
        assertEquals(-6f, scroll(-10f).y, 0.001f)
        assertEquals(0.06f, fraction, 0.001f)
        scroll(-40f)
        assertEquals(0.46f, fraction, 0.001f)
        assertEquals(-54f, scroll(-100f).y, 0.001f)
        assertEquals(1f, fraction, 0.001f)
        assertEquals(Offset.Zero, scroll(-100f))
    }

    @Test fun reversalRequiresThresholdThenExpandsProportionally() {
        scroll(-74f)
        assertEquals(0.5f, fraction, 0.001f)
        scroll(10f)
        assertEquals(0.5f, fraction, 0.001f)
        scroll(34f)
        assertEquals(0.3f, fraction, 0.001f)
        assertEquals(30f, scroll(100f).y, 0.001f)
        assertEquals(0f, fraction, 0.001f)
    }

    @Test fun releaseAtMidpointExpandsAndNewGestureHasThreshold() = runBlocking {
        scroll(-74f)
        connection.onPreScroll(Offset(0f, 100f), NestedScrollSource.SideEffect)
        connection.onPreScroll(Offset(100f, 0f), NestedScrollSource.UserInput)
        assertEquals(0.5f, fraction, 0.001f)
        connection.onPreFling(Velocity(0f, -1000f))
        assertEquals(0f, fraction, 0.001f)
        scroll(-20f)
        assertEquals(0f, fraction, 0.001f)
    }

    @Test fun releasePastMidpointCollapsesEvenWithoutFlingVelocity() = runBlocking {
        scroll(-84f)
        connection.onPreFling(Velocity.Zero)
        assertEquals(1f, fraction, 0.001f)
    }

    @Test fun releaseBelowMidpointExpandsEvenAfterUpwardMotion() = runBlocking {
        scroll(-64f)
        connection.onPreFling(Velocity.Zero)
        assertEquals(0f, fraction, 0.001f)
    }

    @Test fun drawerAndSummaryShareMidpointAndEndpoints() {
        assertEquals(0f, drawerSummaryCollapseFraction(700f, 500f, 100f), 0.001f)
        assertEquals(0f, drawerSummaryCollapseFraction(500f, 500f, 100f), 0.001f)
        assertEquals(0.5f, drawerSummaryCollapseFraction(300f, 500f, 100f), 0.001f)
        assertEquals(1f, drawerSummaryCollapseFraction(100f, 500f, 100f), 0.001f)
        assertEquals(1f, drawerSummaryCollapseFraction(50f, 500f, 100f), 0.001f)
        assertEquals(0f, drawerSummaryCollapseFraction(100f, 100f, 100f), 0.001f)
        assertEquals(0f, summarySettleTarget(drawerSummaryCollapseFraction(301f, 500f, 100f)), 0.001f)
        assertEquals(1f, summarySettleTarget(drawerSummaryCollapseFraction(299f, 500f, 100f)), 0.001f)
    }

    @Test fun settlingReceivesNearestEndpoint() = runBlocking {
        var target = -1f
        val custom = JourneyDetailsScrollConnection(24f, { 100f }, { 0.75f }, {}, { target = it })
        custom.onPreFling(Velocity.Zero)
        assertEquals(1f, target, 0.001f)
    }
}
