package com.catenarymaps.catenary

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Called on Dispatchers.IO. Keep route geometry out of Bundles and device backups. */
internal class TripPlannerSessionStore(file: File) {
    private val atomicFile = AtomicFile(file)

    fun read(): TripPlannerSession? = synchronized(lock) {
        try {
            val session = TripPlannerSessionCodec.decode(atomicFile.openRead().bufferedReader().use { it.readText() })
            if (session == null) atomicFile.delete()
            session
        } catch (_: FileNotFoundException) {
            null
        } catch (e: IOException) {
            Log.w("TripPlannerSession", "Unable to read saved journey", e)
            null
        }
    }

    fun write(session: TripPlannerSession?) = synchronized(lock) {
        if (session == null) {
            atomicFile.delete()
            return@synchronized
        }
        val text = TripPlannerSessionCodec.encode(session)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: IOException) {
            atomicFile.failWrite(stream)
            Log.w("TripPlannerSession", "Unable to save journey", e)
        }
    }

    private companion object {
        // Activity recreation can briefly overlap reads and writes from two compositions.
        val lock = Any()
    }
}
