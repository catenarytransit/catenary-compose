package com.catenarymaps.catenary

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM displays notification+data messages automatically when the app is in the
 * background. When in the foreground, render the message ourselves.
 */
class JourneyFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        fun ensureChannel(context: android.content.Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    "journey_changes",
                    "Journey changes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Departure, platform, cancellation and missed-connection alerts"
                }
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        JourneyNotifierSyncWorker.enqueue(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val id = data["eventId"]?.takeIf { it.isNotBlank() } ?: return
        val kind = data["eventType"] ?: return
        val title = message.notification?.title ?: when (kind) {
            "platform_change" -> "Platform change"
            "connection_missed" -> "Connection no longer possible"
            "cancellation" -> "Journey cancelled"
            else -> "Journey update"
        }
        val body = message.notification?.body ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("journey-notifier-events", MODE_PRIVATE)
        val shown = prefs.getString("seen", "").orEmpty().split(",").filter { it.isNotEmpty() }
        if (shown.contains(id)) return
        prefs.edit().putString("seen", (listOf(id) + shown).distinct().take(128).joinToString(","))
            .apply()

        ensureChannel(this)
        val active = ActiveJourneySession.persisted(this)
        val isActive = active != null &&
                data["journeyId"] == JourneyNotifierSyncWorker.stableId(active.snapshot.journey)
        val open = Intent(this, MainActivity::class.java).apply {
            if (isActive) putExtra("openActiveJourney", true)
            else putExtra("openSavedJourneys", true)
            putExtra("journeyId", data["journeyId"])
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, "journey_changes")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify(id.hashCode(), notification)
    }
}
