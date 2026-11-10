package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/** The connections list must leave composition in map mode; the normal drawer handles details. */
@Composable
internal fun BoxScope.JourneyPlannerResultsOverlay(
    usingMap: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    if (usingMap) return
    Box(
        Modifier.align(Alignment.TopStart).fillMaxSize()
            .zIndex(5f)
            .background(TripPlannerColors.background),
        content = content
    )
}
