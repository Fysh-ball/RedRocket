package site.fysh.redrocket.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import site.fysh.redrocket.R
import site.fysh.redrocket.ui.MainActivity

class EmergencyWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        /**
         * Pushes a status update to all active Red Rocket widgets.
         * Call from [EmergencyApp], [EmergencySendingService], or [SmsResponseReceiver]
         * whenever the app's monitoring state changes.
         */
        fun pushUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, EmergencyWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val isListening = SmsResponseReceiver.isListening()
            val isSending = EmergencySendingService.isRunning

            val statusText = when {
                isSending -> "Sending messages..."
                isListening -> "Listening for replies"
                else -> "Monitoring for alerts"
            }

            val views = RemoteViews(context.packageName, R.layout.widget_emergency)
            views.setTextViewText(R.id.widget_status, statusText)

            // Tap anywhere on the widget to open the app
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, widgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_button, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

    }
}
