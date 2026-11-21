package com.catenarymaps.catenary

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val WIDE_LAYOUT_MIN_WIDTH = 600.dp
internal val MAX_DRAWER_WIDTH = 600.dp

/** The drawer is full width on phones, half width on tablets, and capped on very wide windows. */
internal fun drawerWidthForWindow(windowWidth: Dp): Dp =
    if (windowWidth < WIDE_LAYOUT_MIN_WIDTH) windowWidth
    else (windowWidth / 2f).coerceAtMost(MAX_DRAWER_WIDTH)
