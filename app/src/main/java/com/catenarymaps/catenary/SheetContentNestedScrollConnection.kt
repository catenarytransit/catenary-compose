package com.catenarymaps.catenary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Couples the scrollable content of every drawer screen to the common sheet.
 *
 * Finger up: move the sheet to Expanded first, then let the list scroll.
 * Finger down: let the list scroll to its top first, then move the sheet.
 *
 * Children keep their own LazyListState/ScrollState. A nested-scroll connection
 * on the shared sheet Surface is enough for all Compose scrollable descendants.
 * Direct gestures on non-scrollable descendants are still handled by the
 * Surface's anchoredDraggable modifier.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class SheetContentNestedScrollConnection(
    private val state: AnchoredDraggableState<SheetSnapPoint>,
    private val viewportHeightPx: Float,
    private val flingVelocityThresholdPx: Float,
) : NestedScrollConnection {
    private var sheetMovedByNestedScroll = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f) {
            return Offset.Zero
        }
        // Expansion has precedence over scrolling down through the page.
        return consumeSheetDrag(available.y)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) {
            return Offset.Zero
        }
        // A downward drag collapses the sheet only AFTER the child reaches top.
        return consumeSheetDrag(available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (available.y >= 0f ||
            state.requireOffset() <= state.anchors.positionOf(SheetSnapPoint.Expanded) + 0.5f
        ) {
            return Velocity.Zero
        }
        // Transfer an upward fling into the sheet before the child list flings.
        state.settle(available.y)
        sheetMovedByNestedScroll = false
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val carryMomentum = shouldCarryDownwardFling(
            sheetTopPx = state.requireOffset(),
            viewportHeightPx = viewportHeightPx,
            velocityY = available.y,
            thresholdPxPerSecond = flingVelocityThresholdPx,
        )
        val sheetWasDragged = sheetMovedByNestedScroll
        sheetMovedByNestedScroll = false

        if (sheetWasDragged || carryMomentum) {
            // Even a slow gesture should settle cleanly. Only fast flings with
            // sufficient visible sheet area may pass their kinetic energy on.
            state.settle(if (carryMomentum) available.y else 0f)
        }
        return if (carryMomentum) available else Velocity.Zero
    }

    private fun consumeSheetDrag(deltaY: Float): Offset {
        val used = state.dispatchRawDelta(deltaY)
        if (used != 0f) sheetMovedByNestedScroll = true
        return Offset(x = 0f, y = used)
    }
}

/** Visible fraction of the sheet must be >= 25% for a large downward fling. */
internal fun shouldCarryDownwardFling(
    sheetTopPx: Float,
    viewportHeightPx: Float,
    velocityY: Float,
    thresholdPxPerSecond: Float,
): Boolean =
    viewportHeightPx.isFinite() && viewportHeightPx > 0f &&
        sheetTopPx.isFinite() && velocityY.isFinite() &&
        velocityY >= thresholdPxPerSecond &&
        viewportHeightPx - sheetTopPx >= viewportHeightPx * 0.25f
