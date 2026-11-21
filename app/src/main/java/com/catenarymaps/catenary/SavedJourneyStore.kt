package com.catenarymaps.catenary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A self-contained snapshot: recalling a saved journey will not require the planner session. */
@Serializable
internal data class SavedJourney(
    val journey: DirectionsItinerary,
    val origin: DirectionsLocation?,
    val destination: DirectionsLocation?,
    val savedAt: Long = System.currentTimeMillis()
)

internal class SavedJourneyStore(context: Context, name: String = "saved-journeys.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {
    private val appContext = context.applicationContext
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE saved_journeys (identity TEXT PRIMARY KEY NOT NULL, saved_at INTEGER NOT NULL, snapshot TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun contains(identity: String): Boolean = readableDatabase.query(
        "saved_journeys", arrayOf("identity"), "identity = ?", arrayOf(identity), null, null, null
    ).use { it.moveToFirst() }

    fun save(snapshot: SavedJourney) {
        val values = ContentValues().apply {
            put("identity", snapshot.journey.identity())
            put("saved_at", snapshot.savedAt)
            put("snapshot", Json.encodeToString(snapshot))
        }
        check(writableDatabase.insertWithOnConflict("saved_journeys", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
        JourneyNotifierSyncWorker.enqueue(appContext)
    }

    fun remove(identity: String) {
        if (writableDatabase.delete("saved_journeys", "identity = ?", arrayOf(identity)) > 0) {
            JourneyNotifierSyncWorker.enqueue(appContext)
        }
    }

    /**
     * Physical retention is intentionally longer than the Trips-screen lifetime:
     * extremely delayed journeys must not be accidentally unregistered by the
     * UI's normal twelve-hour saved-trip cleanup. The server owns final expiry.
     */
    fun readForNotifier(now: java.time.Instant = java.time.Instant.now()): List<SavedJourney> {
        val snapshots = readableDatabase.query(
            "saved_journeys", arrayOf("snapshot"), null, null, null, null, "saved_at DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Json.decodeFromString<SavedJourney>(cursor.getString(0)))
            }
        }
        return snapshots.filter { snapshot ->
            val end = runCatching { java.time.Instant.parse(snapshot.journey.endTime) }.getOrNull()
            val expired = end != null && end.isBefore(now.minusSeconds(30L * 24 * 60 * 60))
            if (expired) writableDatabase.delete("saved_journeys", "identity = ?", arrayOf(snapshot.journey.identity()))
            !expired
        }
    }

    /** Hide old journeys from Trips after 12h, while keeping their monitor identity. */
    fun readAll(now: java.time.Instant = java.time.Instant.now()): List<SavedJourney> =
        readForNotifier(now).filter { snapshot ->
            val end = runCatching { java.time.Instant.parse(snapshot.journey.endTime) }.getOrNull()
            end == null || !end.isBefore(now.minusSeconds(12L * 60 * 60))
        }
}
