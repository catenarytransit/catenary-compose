package com.catenarymaps.catenary

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

/** A direction threshold filters small movements; beyond it, height follows pixels one for one. */
internal class JourneyDetailsScrollConnection(
    private val thresholdPx: Float,
    private val range: () -> Float,
    private val fraction: () -> Float,
    private val setFraction: (Float) -> Unit,
    private val settle: suspend (Float) -> Unit = { setFraction(it) }
) : NestedScrollConnection {
    private var direction = 0f
    private var pending = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        if (source != NestedScrollSource.UserInput || delta == 0f) return Offset.Zero
        if (sign(delta) != direction) {
            direction = sign(delta)
            pending = 0f
        }
        val beforeThreshold = pending
        pending = (pending + abs(delta)).coerceAtMost(thresholdPx)
        val movement = (abs(delta) - (pending - beforeThreshold)) * direction
        val height = range().coerceAtLeast(1f)
        val before = fraction()
        val after = (before - movement / height).coerceIn(0f, 1f)
        setFraction(after)
        // Spend the gesture on changing header height before scrolling the list further.
        return Offset(0f, (before - after) * height)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        direction = 0f
        pending = 0f
        val current = fraction()
        if (current > 0f && current < 1f) settle(summarySettleTarget(current))
        return Velocity.Zero
    }
}

/** Exactly halfway returns to expanded; crossing halfway commits to collapsed. */
internal fun summarySettleTarget(fraction: Float): Float = if (fraction > 0.5f) 1f else 0f

/** Sharing the drawer's anchors makes its release animation settle the summary as well. */
internal fun drawerSummaryCollapseFraction(offset: Float, half: Float, expanded: Float): Float =
    ((half - offset) / (half - expanded).coerceAtLeast(1f)).coerceIn(0f, 1f)
