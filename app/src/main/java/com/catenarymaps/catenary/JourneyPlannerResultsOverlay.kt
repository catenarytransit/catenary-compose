package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/** Keep results out of the map pane's hit-test bounds in split-screen map mode. */
@Composable
internal fun BoxScope.JourneyPlannerResultsOverlay(
    usingMap: Boolean,
    contentWidthFraction: Float,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        Modifier.align(Alignment.TopStart)
            .fillMaxWidth(if (usingMap) contentWidthFraction else 1f)
            .fillMaxHeight()
            .zIndex(5f)
            .background(TripPlannerColors.background),
        content = content
    )
}
