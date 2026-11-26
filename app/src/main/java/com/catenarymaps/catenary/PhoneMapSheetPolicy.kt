package com.catenarymaps.catenary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A phone stays a phone after rotation even when its landscape width
 * exceeds the window breakpoint used for the tablet side drawer.
 */
internal fun usesTwoSheetAnchors(isLandscape: Boolean, smallestWidthDp: Int): Boolean =
    isLandscape && smallestWidthDp < 600

/** Nearby Departures never applies sheet-dependent map camera padding. */
internal fun usesPersistentDrawerPadding(
    nearbyDeparturesVisible: Boolean,
    isPortraitPhone: Boolean,
): Boolean = !nearbyDeparturesVisible && !isPortraitPhone

/**
 * The active journey guidance covers the map regardless of drawer state. When
 * visible, reserve the measured bottom chrome (guidance, navigation and safe
 * area) as one occlusion; do not add it twice to an existing drawer inset.
 * Without active guidance, keep the existing Nearby Departures zero-padding.
 */
internal fun activeJourneyMapPadding(
    cameraPadding: PaddingValues,
    bottomChromeHeight: Dp,
    visible: Boolean,
    layoutDirection: LayoutDirection,
): PaddingValues {
    if (!visible || bottomChromeHeight <= 0.dp) return cameraPadding
    val left = cameraPadding.calculateLeftPadding(layoutDirection)
    val right = cameraPadding.calculateRightPadding(layoutDirection)
    return PaddingValues(
        start = if (layoutDirection == LayoutDirection.Ltr) left else right,
        top = cameraPadding.calculateTopPadding(),
        end = if (layoutDirection == LayoutDirection.Ltr) right else left,
        bottom = maxOf(cameraPadding.calculateBottomPadding(), bottomChromeHeight),
    )
}

/** Lift the map controls only while guidance occupies the bottom edge. */
internal fun activeJourneyControlsClearance(visible: Boolean, bottomChromeHeight: Dp): Dp =
    if (visible) bottomChromeHeight.coerceAtLeast(0.dp) else 0.dp

/**
 * Frame a GPS recenter around the map area not covered by the sheet.
 *
 * This padding is temporary: Nearby Departures and portrait phones intentionally keep
 * their persistent map padding at zero, so the caller restores [cameraPadding] afterward.
 */
internal fun gpsRecenterPadding(
    cameraPadding: PaddingValues,
    mapHeight: Dp,
    sheetTop: Dp,
    sideDrawerWidth: Dp,
    fullWidthSheet: Boolean,
    sideDrawerVisible: Boolean,
    layoutDirection: LayoutDirection,
): PaddingValues {
    if (mapHeight <= 0.dp) return cameraPadding

    val left = cameraPadding.calculateLeftPadding(layoutDirection)
    val right = cameraPadding.calculateRightPadding(layoutDirection)
    val start = if (layoutDirection == LayoutDirection.Ltr) left else right
    val end = if (layoutDirection == LayoutDirection.Ltr) right else left
    val drawerHeight = (mapHeight - sheetTop).coerceIn(0.dp, mapHeight)

    return PaddingValues(
        start = if (!fullWidthSheet && sideDrawerVisible) {
            maxOf(start, sideDrawerWidth)
        } else start,
        top = cameraPadding.calculateTopPadding(),
        end = end,
        bottom = if (fullWidthSheet) {
            maxOf(cameraPadding.calculateBottomPadding(), drawerHeight)
        } else cameraPadding.calculateBottomPadding(),
    )
}

/**
 * Screen point for a new or recentered Nearby Departures pin on a half-open portrait phone.
 * The sheet top already accounts for bottom navigation and insets. This is a projection
 * point, not camera padding; other drawer states keep the existing camera-target behavior.
 */
internal fun nearbyDeparturePinScreenCenter(
    mapWidth: Dp,
    mapHeight: Dp,
    sheetTop: Dp,
    isPortraitPhone: Boolean,
    sheetPartiallyExpanded: Boolean,
): DpOffset? {
    if (!isPortraitPhone || !sheetPartiallyExpanded ||
        mapWidth <= 0.dp || mapHeight <= 0.dp || !sheetTop.value.isFinite()
    ) return null

    return DpOffset(
        x = mapWidth / 2f,
        y = sheetTop.coerceIn(0.dp, mapHeight) / 2f,
    )
}

/** Geographic coordinate to preserve when discarding one-shot camera padding. */
internal fun mapViewportCenter(
    mapWidth: Dp,
    mapHeight: Dp,
    cameraPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): DpOffset {
    val left = cameraPadding.calculateLeftPadding(layoutDirection)
    val right = cameraPadding.calculateRightPadding(layoutDirection)
    val top = cameraPadding.calculateTopPadding()
    val bottom = cameraPadding.calculateBottomPadding()
    return DpOffset(
        x = left + (mapWidth - left - right).coerceAtLeast(0.dp) / 2f,
        y = top + (mapHeight - top - bottom).coerceAtLeast(0.dp) / 2f,
    )
}
