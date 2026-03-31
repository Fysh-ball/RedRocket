package site.fysh.redrocket.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val DEBUG_CHANNEL_ID = "debug_mode_channel"
    private const val DEBUG_NOTIFICATION_ID = 200

    fun ensureDebugChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(DEBUG_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                DEBUG_CHANNEL_ID,
                "Debug Mode Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when debug mode is toggled on or off."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun showDebugModeNotification(context: Context, enabled: Boolean) {
        ensureDebugChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (!enabled) {
            nm.cancel(DEBUG_NOTIFICATION_ID)
        }

        val title = if (enabled) "Red Rocket: Debug Mode ON" else "Red Rocket: Debug Mode OFF"
        val body = if (enabled)
            "Real SMS is disabled. Messages will NOT be sent to real recipients."
        else
            "Real SMS is enabled. Messages will be sent to real recipients."

        val notif = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        nm.notify(DEBUG_NOTIFICATION_ID, notif)
    }
}
