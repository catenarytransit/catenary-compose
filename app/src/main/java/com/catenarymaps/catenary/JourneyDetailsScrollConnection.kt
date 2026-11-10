package com.catenarymaps.catenary

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/** React immediately to user direction, including reversal before the list reaches its top. */
internal class JourneyDetailsScrollConnection(
    private val setCollapsed: (Boolean) -> Unit
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Layout changes during the summary animation must not reverse its state.
        // A fling retains the state established by the user's last input.
        if (source == NestedScrollSource.UserInput) {
            if (available.y < 0f) setCollapsed(true)
            else if (available.y > 0f) setCollapsed(false)
        }
        return Offset.Zero
    }
}
