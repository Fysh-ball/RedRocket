package site.fysh.redrocket.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import site.fysh.redrocket.EmergencyApp
import site.fysh.redrocket.model.SendState
import site.fysh.redrocket.queue.MessageStatus
import site.fysh.redrocket.queue.QueueStatus
import kotlinx.coroutines.*

class EmergencySendingService : Service() {

    private val TAG = "EmergencySendingService"
    private var serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var processingJob: Job? = null
    @Volatile private var notificationJob: Job? = null

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "emergency_sending_channel"

    companion object {
        const val ACTION_STOP = "site.fysh.redrocket.STOP_SENDING"

        fun startService(context: Context) {
            Log.d("EmergencySendingService", "Requesting service start")
            val intent = Intent(context, EmergencySendingService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, EmergencySendingService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground on ALL code paths before any branching - Android 12+ requirement.
        // A RemoteServiceException crash occurs if startForeground is not called promptly.
        val notification = createNotification("Emergency Message System Active", null, null)
        startForeground(NOTIFICATION_ID, notification)

        // On START_STICKY restart after process death, onDestroy() was called which cancelled
        // serviceScope. Launching on a cancelled scope throws JobCancellationException silently,
        // so recreate the scope before doing any work.
        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            processingJob = null
            notificationJob = null
        }

        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop action received via notification")
            val app = application as EmergencyApp
            serviceScope.launch {
                app.queueManager.clearQueue()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        Log.i(TAG, "onStartCommand: service starting, action=${intent?.action}")
        val app = application as EmergencyApp

        // Log queue state asynchronously to avoid blocking the main thread
        serviceScope.launch {
            val queueSize = app.queueManager.getDetailedStatus()
            Log.i(TAG, "onStartCommand: queue on start - primary=${queueSize.primarySize} retry=${queueSize.retrySize} total=${queueSize.totalEnqueued}")
        }

        if (notificationJob?.isActive != true) {
            notificationJob = serviceScope.launch {
                app.queueManager.queueStatusFlow.collect { status ->
                    val state = app.adaptiveController.currentState.value

                    Log.d(
                        "AdaptiveEngine",
                        "failureCount=${status.failedCount} CurrentMode=$state " +
                            "PrimaryQ=${status.primarySize} RetryQ=${status.retrySize}"
                    )

                    val statusText = when {
                        status.totalEnqueued == 0 -> "Preparing messages…"
                        status.primarySize > 0 -> "Sending messages…"
                        status.retrySize > 0 -> "Lazarus Mode: Retrying failures…"
                        status.processed > 0 && status.remaining == 0 -> "Message delivery complete"
                        else -> "System active"
                    }

                    updateNotification(statusText, status, state)
                }
            }
        }

        if (processingJob?.isActive != true) {
            processingJob = serviceScope.launch {
                processQueue(app)
            }
        }

        return START_STICKY
    }

    private suspend fun processQueue(app: EmergencyApp) {
        // Restore any messages persisted to Room before process death
        app.queueManager.restoreFromDisk()

        // Wait up to 2s for the queue to be populated (guards against start-before-enqueue race)
        var waitMs = 0
        while (waitMs < 2000) {
            val s = app.queueManager.getDetailedStatus()
            if (s.primarySize > 0 || s.retrySize > 0) break
            delay(100)
            waitMs += 100
        }
        val initStatus = app.queueManager.getDetailedStatus()
        Log.i(TAG, "processQueue starting - primary=${initStatus.primarySize} retry=${initStatus.retrySize} total=${initStatus.totalEnqueued} (waited ${waitMs}ms)")
        if (initStatus.primarySize == 0 && initStatus.retrySize == 0 && initStatus.totalEnqueued == 0) {
            Log.w(TAG, "Queue is empty after 2s wait - stopping service")
            stopSelf()
            return
        }
        Log.d(TAG, "Starting queue processing loop")
        while (serviceScope.isActive) {
            val status = app.queueManager.getDetailedStatus()

            when {
                status.primarySize > 0 -> {
                    val task = app.queueManager.nextTask()
                    if (task == null) {
                        // Race: queue was drained between getDetailedStatus() and nextTask().
                        // Yield briefly rather than spinning at full speed.
                        delay(50)
                    } else {
                        app.queueManager.updateCurrentMessageStatus(
                            MessageStatus(task.recipient.phoneNumber, "Sending…")
                        )
                        var taskResolved = false
                        try {
                            val success = app.getActiveSmsProvider().send(task)
                            taskResolved = true
                            if (success) {
                                app.queueManager.updateCurrentMessageStatus(
                                    MessageStatus(task.recipient.phoneNumber, "Sent ✓")
                                )
                            } else {
                                app.queueManager.handleFailure(task)
                                app.queueManager.updateCurrentMessageStatus(
                                    MessageStatus(task.recipient.phoneNumber, "Failed ✗")
                                )
                            }
                        } finally {
                            // On coroutine cancellation, ensure inFlightCount decrements
                            // so the service does not loop forever waiting for remaining == 0
                            if (!taskResolved) {
                                withContext(NonCancellable) {
                                    app.queueManager.handleFailure(task)
                                }
                            }
                        }
                    }
                }

                status.retrySize > 0 -> {
                    // LazarusRetrySystem handles its own full pass-loop and blocks here
                    app.lazarusSystem.processRetryQueue()
                }

                status.totalEnqueued > 0 && status.remaining == 0 -> {
                    Log.i(TAG, "All ${status.totalEnqueued} messages processed. Service stopping.")
                    app.queueManager.updateCurrentMessageStatus(null)

                    // Fire a separate completion notification so the user is notified even when backgrounded
                    val completionText = "${status.successCount} delivered, ${status.failedCount} failed"
                    val openPi = PendingIntent.getActivity(
                        this, 2,  // distinct request code — 0 = foreground open, 1 = stop action
                        Intent(this, site.fysh.redrocket.ui.MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val completionNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Red Rocket: Complete")
                        .setContentText(completionText)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(openPi)
                        .build()
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID + 1, completionNotif)

                    stopSelf()
                    break
                }

                else -> {
                    delay(500)
                }
            }
        }
    }

    private fun createNotification(
        content: String,
        status: QueueStatus?,
        sendState: SendState?
    ): Notification {
        val openIntent = Intent(this, site.fysh.redrocket.ui.MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, EmergencySendingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val isDone = status != null && status.totalEnqueued > 0 && status.remaining == 0

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isDone) "Messages Sent" else "Emergency Message System Active")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(!isDone)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Only show STOP action while actively sending
        if (!isDone) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP SENDING",
                stopPendingIntent
            )
        }

        if (status != null) {
            val total = status.totalEnqueued
            val completed = status.successCount + status.failedCount

            val stateName = when (sendState) {
                SendState.MULTI_THREADED -> "Multi-threaded"
                SendState.SEQUENTIAL -> "Sequential"
                SendState.LAZARUS -> "Lazarus Retry"
                else -> "Active"
            }

            val text = when {
                isDone -> "Complete - ${status.successCount} sent, ${status.failedCount} failed"
                total == 0 -> "Preparing messages…"
                else -> "$completed / $total  •  Mode: $stateName"
            }

            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
            builder.setContentText(text)

            if (total > 0 && !isDone) {
                builder.setProgress(total, completed, false)
            }
        } else {
            builder.setContentText(content)
        }

        return builder.build()
    }

    private fun updateNotification(content: String, status: QueueStatus?, sendState: SendState?) {
        val notification = createNotification(content, status, sendState)
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Emergency Operations",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time status of emergency alerts and message transmission."
            setShowBadge(true)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        // Clear pending SMS callbacks so SmsDeliveryReceiver doesn't hold stale lambdas
        // referencing the now-dead coroutine scope.
        SmsDeliveryReceiver.pendingSent.clear()
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
