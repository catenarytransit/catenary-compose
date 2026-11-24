package com.catenarymaps.catenary

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Long-lived search preferences, independent of the transient journey session. */
internal interface TripPlannerOptionsStore {
    /** Null means there are no saved preferences yet (including on upgrade). */
    fun read(): DirectionsOptions?
    fun write(options: DirectionsOptions)
}

internal class SharedPreferencesTripPlannerOptionsStore(
    context: Context,
    preferencesName: String = "trip_planner_options"
) : TripPlannerOptionsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName, Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun read(): DirectionsOptions? {
        if (!preferences.contains(KEY)) return null
        // An invalid existing preference falls back to defaults, not an older
        // journey snapshot. Missing fields in older JSON use model defaults.
        return runCatching {
            json.decodeFromString<DirectionsOptions>(checkNotNull(preferences.getString(KEY, null)))
                .takeIf { options ->
                    listOf(options.walkingPace, options.cyclingKmh).all { it.isFinite() && it > 0 } &&
                        listOf(options.accessSeconds, options.egressSeconds, options.directSeconds)
                            .all { it.isFinite() && it >= 0 }
                }
        }.getOrNull() ?: DirectionsOptions()
    }

    override fun write(options: DirectionsOptions) {
        preferences.edit().putString(KEY, json.encodeToString(options)).apply()
    }

    private companion object {
        const val KEY = "options_v1"
    }
}
