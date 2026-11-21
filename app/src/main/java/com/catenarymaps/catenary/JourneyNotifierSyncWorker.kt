package com.catenarymaps.catenary

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Reconciles the device's saved journeys with the backend.
 *
 * The server owns realtime evaluation and notification delivery. WorkManager
 * only retries registration/sync; it is never used for time-critical alerts.
 */
internal class JourneyNotifierSyncWorker(
    context: Context, parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val base = BuildConfig.JOURNEY_NOTIFIER_BASE_URL.trimEnd('/')
        if (base.isBlank()) return@withContext Result.success() // Opt-in until deployed.
        try {
            require(base.startsWith("https://")) { "Journey notifier requires HTTPS" }
            val token = Tasks.await(FirebaseMessaging.getInstance().token, 20, TimeUnit.SECONDS)
            val preferences = applicationContext.getSharedPreferences("journey-notifier", Context.MODE_PRIVATE)
            val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
            val deviceId = preferences.getString("device_id", null)
            val secret = preferences.getString("device_secret", null)
            var registeredId = deviceId
            var registeredSecret = secret
            if (registeredId.isNullOrBlank() || registeredSecret.isNullOrBlank()) {
                val body = JSONObject().put("fcmToken", token)
                val response = postJson(http, "$base/v1/devices", body, null, null)
                val registration = JSONObject(response)
                registeredId = registration.getString("deviceId")
                registeredSecret = registration.getString("deviceSecret")
                check(registeredId.isNotBlank() && registeredSecret.isNotBlank())
                // Save both credentials atomically, *before* synchronizing journeys.
                check(preferences.edit().putString("device_id", registeredId)
                    .putString("device_secret", registeredSecret).commit())
            } else if (preferences.getString("fcm_token", null) != token) {
                val body = JSONObject().put("fcmToken", token)
                putJson(http, "$base/v1/devices/$registeredId", body, registeredId, registeredSecret)
            }
            preferences.edit().putString("fcm_token", token).apply()
            val snapshots = SavedJourneyStore(applicationContext).use { it.readForNotifier() }
            val payload = JSONObject().put("journeys", JSONArray().apply {
                snapshots.forEach { snapshot ->
                    journeyBody(snapshot)?.let { put(it) }
                }
            })
            postJson(http, "$base/v1/journeys/sync", payload, registeredId, registeredSecret)
            Result.success()
        } catch (error: Exception) {
            Log.w("JourneyNotifier", "Could not reconcile saved journeys", error)
            Result.retry()
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val UNIQUE_SYNC = "journey-notifier-reconcile"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun enqueue(context: Context) {
            if (BuildConfig.JOURNEY_NOTIFIER_BASE_URL.isBlank()) return
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_SYNC, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<JourneyNotifierSyncWorker>()
                        .setConstraints(network).build())
        }
        fun ensureStarted(context: Context) {
            if (BuildConfig.JOURNEY_NOTIFIER_BASE_URL.isBlank()) return
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "journey-notifier-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<JourneyNotifierSyncWorker>(12, TimeUnit.HOURS)
                    .setConstraints(network).build()
            )
            enqueue(context)
        }

        private fun postJson(http: OkHttpClient, url: String, body: JSONObject, deviceId: String?, secret: String?): String =
            request(http, "POST", url, body, deviceId, secret)

        private fun putJson(http: OkHttpClient, url: String, body: JSONObject, deviceId: String, secret: String) {
            request(http, "PUT", url, body, deviceId, secret)
        }
        private fun request(http: OkHttpClient, method: String, url: String, body: JSONObject, deviceId: String?, secret: String?): String {
            val builder = Request.Builder().url(url).method(method, body.toString().toRequestBody(JSON))
            if (deviceId != null && secret != null) builder.header("X-Device-Id", deviceId).header("Authorization", "Bearer $secret")
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) error("Notifier HTTP ${response.code}")
                return response.body?.string().orEmpty()
            }
        }
        private fun instant(value: String?): Long? =
            runCatching { Instant.parse(value).epochSecond }.getOrNull()

        /** Keep server identifiers short even if Edelweiss returns a synthetic long identity. */
        private fun stableId(journey: DirectionsItinerary): String =
            MessageDigest.getInstance("SHA-256").digest(journey.identity().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun journeyBody(snapshot: SavedJourney): JSONObject? {
            val journey = snapshot.journey
            val scheduledEnd = instant(journey.endTime) ?: return null
            val transit = journey.legs.withIndex().filter { !it.value.street }
            if (transit.isEmpty()) return null
            val legs = JSONArray()
            transit.forEachIndexed { index, current ->
                val leg = current.value
                val date = leg.tripStartDate ?: return null
                val departure = instant(leg.departureScheduled ?: leg.startTime) ?: return null
                val arrival = instant(leg.arrivalScheduled ?: leg.endTime) ?: return null
                val predictedDeparture = instant(leg.departureCurrent) ?: departure
                val predictedArrival = instant(leg.arrivalCurrent) ?: arrival
                val trip = leg.tripId ?: return null
                val chateau = leg.chateau ?: return null
                val from = leg.from ?: return null
                val to = leg.to ?: return null
                var transfer = 120L
                if (index > 0) {
                    val previous = transit[index - 1]
                    val between = journey.legs.subList(previous.index + 1, current.index)
                    val explicitWalk = between.sumOf { (it.duration ?: 0.0).coerceAtLeast(0.0) }
                    if (explicitWalk > 0) transfer = explicitWalk.toLong().coerceIn(0, 3600)
                }
                legs.put(JSONObject().apply {
                    put("chateau", chateau)
                    put("tripId", trip)
                    put("tripStartDate", date)
                    putNullable("tripStartTime", leg.tripStartTime)
                    putNullable("fromStopSequence", leg.fromStopSequence)
                    putNullable("toStopSequence", leg.toStopSequence)
                    putNullable("fromStopId", from.stopId)
                    putNullable("toStopId", to.stopId)
                    put("fromName", from.name)
                    put("toName", to.name)
                    put("routeName", leg.label)
                    putNullable("tripShortName", leg.tripShortName)
                    putNullable("timeZone", from.timezone)
                    put("scheduledDeparture", departure)
                    put("scheduledArrival", arrival)
                    put("expectedDeparture", predictedDeparture)
                    put("expectedArrival", predictedArrival)
                    putNullable("expectedPlatform", from.estimatedQuay ?: leg.fromPlatform ?: from.platform)
                    put("transferSeconds", transfer)
                })
            }
            return JSONObject()
                .put("journeyId", stableId(journey))
                .put("scheduledEnd", scheduledEnd)
                .put("legs", legs)
        }
        private fun JSONObject.putNullable(key: String, value: Any?) {
            put(key, value ?: JSONObject.NULL)
        }
    }
}
