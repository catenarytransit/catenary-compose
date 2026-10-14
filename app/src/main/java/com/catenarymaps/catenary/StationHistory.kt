package com.catenarymaps.catenary

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.E
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Local-only, bounded history of GTFS stops, OSM stations, and OSM places. */
@Serializable
enum class RecentStationKind { GTFS_STOP, OSM_STATION, OSM_ITEM }

@Serializable
data class RecentStation(
    val kind: RecentStationKind,
    val id: String,
    val name: String,
    val chateauId: String? = null,
    val modeType: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Required to reopen OSM places; station IDs alone are not sufficient.
    val osmClass: String? = null,
    val osmType: String? = null,
    val address: String? = null,
    val names: Map<String, String> = emptyMap(),
    val visits: Int = 1,
    val lastVisitMs: Long = 0L,
) {
    fun stableKey(): String = when (kind) {
        RecentStationKind.GTFS_STOP -> "stop:${chateauId}:${id}"
        RecentStationKind.OSM_STATION -> "osm:${id}"
        RecentStationKind.OSM_ITEM -> "osm-item:${osmClass}:${id}"
    }
}

/**
 * Similar to catenary-web's stationHistory.ts: deduplicated visits and a
 * frequency/recency ranking with a fourteen-day decay. No remote sync.
 */
class StationHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "station_search_history", Context.MODE_PRIVATE
    )
    private val serializer = ListSerializer(RecentStation.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    // Compose observes this directly, so opening search shows new visits immediately.
    var entries by mutableStateOf(readSaved())
        private set

    fun recordStop(
        chateauId: String,
        stopId: String,
        name: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        if (chateauId.isBlank() || stopId.isBlank() || name.isBlank()) return
        record(RecentStation(
            kind = RecentStationKind.GTFS_STOP,
            id = stopId,
            chateauId = chateauId,
            name = name.trim(),
            latitude = latitude,
            longitude = longitude,
        ))
    }

    fun recordOsmStation(
        osmId: String,
        name: String,
        modeType: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        if (osmId.isBlank() || name.isBlank()) return
        record(RecentStation(
            kind = RecentStationKind.OSM_STATION,
            id = osmId,
            name = name.trim(),
            modeType = modeType,
            latitude = latitude,
            longitude = longitude,
        ))
    }

    /** Record any successfully selected OSM object, independent of its entry point. */
    fun recordOsmItem(item: CatenaryStackEnum.OsmItemStack) {
        if (item.osm_class !in OSM_CLASSES || !OSM_ID_PATTERN.matches(item.osm_id)) return
        val name = item.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: item.names["default"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${item.osm_class.replaceFirstChar { it.titlecase() }} ${item.osm_id}"
        record(RecentStation(
            kind = RecentStationKind.OSM_ITEM,
            id = item.osm_id,
            name = name,
            osmClass = item.osm_class,
            osmType = item.osm_type,
            address = item.address,
            names = item.names,
            latitude = item.lat,
            longitude = item.lon,
        ))
    }

    private fun record(item: RecentStation) {
        val now = System.currentTimeMillis()
        val existing = entries.firstOrNull { it.stableKey() == item.stableKey() }
        if (existing != null && now - existing.lastVisitMs in 0 until VISIT_DEBOUNCE_MS) {
            // A screen can fetch several pages; those are not independent visits.
            return
        }
        val updated = item.copy(
            visits = (existing?.visits ?: 0).coerceAtMost(Int.MAX_VALUE - 1) + 1,
            lastVisitMs = now,
            // Keep known coordinates if the new screen has not loaded any.
            latitude = item.latitude ?: existing?.latitude,
            longitude = item.longitude ?: existing?.longitude,
            modeType = item.modeType ?: existing?.modeType,
            osmType = item.osmType ?: existing?.osmType,
            address = item.address?.takeIf { it.isNotBlank() } ?: existing?.address,
            names = item.names.ifEmpty { existing?.names.orEmpty() },
        )
        val newEntries = (listOf(updated) + entries.filterNot {
            it.stableKey() == updated.stableKey()
        }).take(MAX_ENTRIES)
        entries = newEntries
        prefs.edit().putString(KEY, json.encodeToString(serializer, newEntries)).apply()
    }

    fun topHistory(limit: Int = 10, now: Long = System.currentTimeMillis()): List<RecentStation> =
        entries.sortedByDescending { entry ->
            val ageMs = (now - entry.lastVisitMs).coerceAtLeast(0L)
            ln(E + entry.visits.toDouble()) * exp(-ageMs.toDouble() / DECAY_MS)
        }.take(limit.coerceAtLeast(0))

    private fun readSaved(): List<RecentStation> = try {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        json.decodeFromString(serializer, raw)
            .filter { item ->
                item.id.isNotBlank() && item.name.isNotBlank() && when (item.kind) {
                    RecentStationKind.GTFS_STOP -> !item.chateauId.isNullOrBlank()
                    RecentStationKind.OSM_STATION -> true
                    RecentStationKind.OSM_ITEM ->
                        item.osmClass in OSM_CLASSES && OSM_ID_PATTERN.matches(item.id)
                }
            }
            .distinctBy { it.stableKey() }
            .take(MAX_ENTRIES)
    } catch (_: Exception) {
        // Corrupt or older preferences must never prevent search from opening.
        emptyList()
    }

    private companion object {
        private val OSM_CLASSES = setOf("node", "way", "relation")
        private val OSM_ID_PATTERN = Regex("[1-9][0-9]*")
        const val KEY = "history_v1"
        const val MAX_ENTRIES = 100
        const val VISIT_DEBOUNCE_MS = 30_000L
        const val DECAY_MS = 14L * 24 * 60 * 60 * 1000
    }
}
