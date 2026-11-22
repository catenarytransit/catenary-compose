package com.catenarymaps.catenary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/** A time-based, single-leg estimate; transfers reference the next transit leg. */
internal data class JourneyLegNotificationProgress(
    val index: Int,
    val count: Int,
    val leg: DirectionsLeg,
    val percent: Int,
    val upcoming: Boolean
)

private fun progressEpoch(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }

internal fun notificationLegProgress(
    journey: DirectionsItinerary,
    now: Long
): JourneyLegNotificationProgress? {
    val legs = journey.legs
    if (legs.isEmpty()) return null
    val current = legs.indexOfFirst { leg ->
        val arrival = progressEpoch(leg.arrivalCurrent) ?: progressEpoch(leg.endTime)
        arrival == null || now < arrival
    }.let { if (it == -1) legs.lastIndex else it }
    val transferring = legs[current].street && legs.take(current).any { !it.street }
    val next = if (transferring) {
        ((current + 1) until legs.size).firstOrNull { !legs[it].street }
    } else null
    val index = next ?: current
    val leg = legs[index]
    val departure = progressEpoch(leg.departureCurrent) ?: progressEpoch(leg.startTime)
    val arrival = progressEpoch(leg.arrivalCurrent) ?: progressEpoch(leg.endTime)
    val percent = if (departure != null && arrival != null && arrival > departure) {
        (100.0 * (now - departure).toDouble() / (arrival - departure).toDouble())
            .coerceIn(0.0, 100.0).toInt()
    } else if (current == legs.lastIndex && now >= (arrival ?: Long.MAX_VALUE)) 100 else 0
    return JourneyLegNotificationProgress(index, legs.size, leg, percent, next != null || (departure != null && now < departure))
}

/** Same-process cache: use existing Ramonda predictions without another subscription. */
internal object ActiveJourneyLiveState {
    @Volatile private var identity: String? = null
    @Volatile private var live: DirectionsItinerary? = null
    fun update(journeyIdentity: String, journey: DirectionsItinerary) {
        if (ActiveJourneySession.current?.snapshot?.journey?.identity() == journeyIdentity) {
            live = journey
            identity = journeyIdentity
        }
    }
    fun journey(journeyIdentity: String): DirectionsItinerary? =
        live?.takeIf { identity == journeyIdentity }
    fun clear() { identity = null; live = null }
}

/**
 * Only runs for an explicitly started journey. The notification continues to
 * advance from the stored timetable when the user backgrounds the app.
 */
class ActiveJourneyProgressService : Service() {
    companion object {
        private const val TAG = "ActiveJourneyProgress"
        private const val CHANNEL_ID = "active_journey_progress"
        private const val NOTIFICATION_ID = 48213

        fun start(context: Context) {
            val app = context.applicationContext
            try {
                ContextCompat.startForegroundService(app, Intent(app, ActiveJourneyProgressService::class.java))
            } catch (error: RuntimeException) {
                // A background FGS launch can be denied by Android; session and FCM
                // reconciliation remain persisted and the UI can retry on resume.
                Log.w(TAG, "Unable to start user-initiated guidance notification", error)
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, ActiveJourneyProgressService::class.java)
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var refreshJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun channel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.journey_tracking_notification_channel),
                NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.journey_tracking_notification_channel_description)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun buildNotification(active: ActiveJourney, journey: DirectionsItinerary, now: Long): Notification {
        val destination = active.snapshot.destination?.name?.takeIf { it.isNotBlank() }
            ?: journey.legs.lastOrNull()?.to?.name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val progress = notificationLegProgress(journey, now)
        val progressText = progress?.let {
            val leg = it.leg
            val route = if (leg.street) {
                getString(if (leg.walking) R.string.journey_tracking_walk else R.string.journey_tracking_cycle)
            } else journeyServiceName(leg)
            getString(
                if (it.upcoming) R.string.journey_tracking_notification_next_leg
                else R.string.journey_tracking_notification_leg,
                it.index + 1, it.count, route, leg.to?.name.orEmpty()
            )
        } ?: getString(R.string.journey_tracking_arriving, destination)
        val open = Intent(this, MainActivity::class.java).apply {
            putExtra("openActiveJourney", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.journey_tracking_notification_title, destination))
            .setContentText(progressText)
            .setContentIntent(pending)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, progress?.percent ?: 100, false)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ActiveJourneySession.initialize(applicationContext)
        val active = ActiveJourneySession.current ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        channel()
        val identity = active.snapshot.journey.identity()
        val journey = ActiveJourneyLiveState.journey(identity) ?: active.snapshot.journey
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(active, journey, Instant.now().epochSecond),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to show foreground progress notification", error)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                delay(20_000)
                val current = ActiveJourneySession.current
                if (current == null) {
                    stopSelf()
                    break
                }
                val currentIdentity = current.snapshot.journey.identity()
                val latest = ActiveJourneyLiveState.journey(currentIdentity) ?: current.snapshot.journey
                val now = Instant.now().epochSecond
                if (hasArrivedAtJourneyDestination(latest, current.snapshot.destination, now, null)) {
                    ActiveJourneySession.end(currentIdentity)
                    break
                }
                try {
                    NotificationManagerCompat.from(this@ActiveJourneyProgressService).notify(
                        NOTIFICATION_ID, buildNotification(current, latest, now)
                    )
                } catch (error: SecurityException) {
                    // Notifications may be disallowed; do not end the trip or FCM subscription.
                    Log.d(TAG, "Notifications disabled for ongoing journey", error)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
