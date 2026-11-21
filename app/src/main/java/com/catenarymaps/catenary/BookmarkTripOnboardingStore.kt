package com.catenarymaps.catenary

import android.content.Context

/** Remembers the first successful bookmark independently of saved-journey expiry or removal. */
internal class BookmarkTripOnboardingStore(
    context: Context,
    preferencesName: String = "bookmark_trip_onboarding"
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName, Context.MODE_PRIVATE
    )

    fun hasBookmarkedTrip(): Boolean = preferences.getBoolean("has_bookmarked_trip", false)

    fun markBookmarkedTrip() {
        preferences.edit().putBoolean("has_bookmarked_trip", true).apply()
    }
}

/** Show the prompt only while there is a bookmarkable journey and the feature is unused. */
internal fun shouldShowBookmarkTooltip(
    hasBookmarkedTrip: Boolean,
    isSaved: Boolean,
    isLoading: Boolean,
    canSave: Boolean
): Boolean = !hasBookmarkedTrip && !isSaved && !isLoading && canSave
