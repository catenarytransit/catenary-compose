package com.catenarymaps.catenary

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TripPlannerOptionsStoreTest {
    @Test
    fun preferencesRoundTripResetAndRecoverFromMalformedData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "trip-planner-options-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        fun store() = SharedPreferencesTripPlannerOptionsStore(context, name)
        val options = DirectionsOptions(
            cycling = true, walkingPace = 690f, cyclingKmh = 25f,
            accessSeconds = 1200f, egressSeconds = 1800f, directSeconds = 2400f,
            filterTransportModes = false,
            transportModes = setOf(PublicTransportMode.TRAIN, PublicTransportMode.COACH)
        )
        try {
            assertNull(store().read())
            store().write(options)
            assertEquals(options, store().read())
            val empty = options.copy(filterTransportModes = true, transportModes = emptySet())
            store().write(empty)
            assertEquals(empty, store().read())
            store().write(DirectionsOptions())
            assertEquals(DirectionsOptions(), store().read())
            preferences.edit().putString("options_v1", "{broken").commit()
            assertEquals(DirectionsOptions(), store().read())
            preferences.edit().putString("options_v1", """{"cycling":true,"futureField":1}""")
                .commit()
            assertEquals(DirectionsOptions(cycling = true), store().read())
            preferences.edit().putString("options_v1", """{"walkingPace":0}""").commit()
            assertEquals(DirectionsOptions(), store().read())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
