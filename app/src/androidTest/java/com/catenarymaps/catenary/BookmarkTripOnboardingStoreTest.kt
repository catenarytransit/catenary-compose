package com.catenarymaps.catenary

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkTripOnboardingStoreTest {
    @Test
    fun completingBookmarkOnboardingPersistsAcrossStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "bookmark-trip-onboarding-test"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = BookmarkTripOnboardingStore(context, name)
            assertFalse(store.hasBookmarkedTrip())
            store.markBookmarkedTrip()
            assertTrue(store.hasBookmarkedTrip())
            assertTrue(BookmarkTripOnboardingStore(context, name).hasBookmarkedTrip())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
