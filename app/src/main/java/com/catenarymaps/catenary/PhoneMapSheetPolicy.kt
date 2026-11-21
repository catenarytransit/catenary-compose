package com.catenarymaps.catenary

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
