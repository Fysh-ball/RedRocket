package site.fysh.redrocket.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import site.fysh.redrocket.BuildConfig
import site.fysh.redrocket.queue.AdaptiveSendController
import site.fysh.redrocket.queue.MessageQueueManager
import site.fysh.redrocket.queue.MessageTask
import site.fysh.redrocket.utils.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Sends real SMS messages via Android SmsManager.
 * Only called when debug mode is OFF - routing is handled by EmergencyApp.getActiveSmsProvider().
 */
class SmsSender(
    private val context: Context,
    private val smsManager: SmsManager?,
    private val rateLimiter: RateLimiter,
    private val adaptiveController: AdaptiveSendController,
    private val queueManager: MessageQueueManager
) : SmsProvider {

    private val TAG = "SmsSender"
    private val requestCounter = AtomicInteger(1000)

    override suspend fun send(task: MessageTask): Boolean = withContext(Dispatchers.IO) {
        // Runtime permission check - SEND_SMS must be granted before each attempt.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "[PERMISSION DENIED] SEND_SMS not granted - skipping ${task.recipient.phoneNumber}")
            adaptiveController.reportResult(false)
            return@withContext false
        }

        val sm = smsManager ?: run {
            Log.e(TAG, "[ERROR] SmsManager is null - cannot send to ${task.recipient.phoneNumber}")
            val app = context.applicationContext as? site.fysh.redrocket.EmergencyApp
            if (app != null) {
                site.fysh.redrocket.utils.AppLogger.log(
                    app.database, app.appScope, "sms_manager_null",
                    "SmsManager unavailable - SMS delivery is not possible on this device"
                )
            }
            adaptiveController.reportResult(false)
            return@withContext false
        }

        if (task.message.isEmpty()) {
            Log.w(TAG, "[ABORT] Empty message for ${task.recipient.phoneNumber}")
            return@withContext false
        }

        val timestamp = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        val responseInstructions = "\n\nReply:\n1 = Safe\n2 = Safe, want updates\n3 = EMERGENCY\n\nAUTO MESSAGE SENT AT $timestamp"
        val fullMessage = task.message + responseInstructions

        return@withContext try {
            rateLimiter.waitForNextSlot(adaptiveController.getRequiredDelayMs())

            val parts = sm.divideMessage(fullMessage)
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Sending SMS to ${task.recipient.phoneNumber} (${parts.size} part(s), ${fullMessage.length} chars)")
            }

            val success = sendWithConfirmation(sm, task.recipient.phoneNumber, fullMessage, parts)

            if (success) {
                adaptiveController.reportResult(true)
                queueManager.markSuccess()
            } else {
                adaptiveController.reportResult(false)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception for ${task.recipient.phoneNumber}", e)
            adaptiveController.reportResult(false)
            false
        }
    }

    /**
     * Calls sendTextMessage/sendMultipartTextMessage and suspends until the SMS_SENT
     * PendingIntent fires (max 10 seconds). Returns true if RESULT_OK, false on any error.
     * Timeout after 10 s is treated as failure - triggers Lazarus retry.
     */
    private suspend fun sendWithConfirmation(
        sm: SmsManager,
        phone: String,
        message: String,
        parts: ArrayList<String>
    ): Boolean {
        val callbackId = UUID.randomUUID().toString()

        return try {
            withTimeout(10_000L) {
                suspendCancellableCoroutine { cont ->
                    SmsDeliveryReceiver.pendingSent[callbackId] = { success ->
                        if (cont.isActive) cont.resume(success)
                    }
                    cont.invokeOnCancellation {
                        SmsDeliveryReceiver.pendingSent.remove(callbackId)
                    }

                    val reqCode = requestCounter.getAndIncrement()
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val sentIntent = PendingIntent.getBroadcast(
                        context, reqCode,
                        Intent(context, SmsDeliveryReceiver::class.java).apply {
                            action = SmsDeliveryReceiver.ACTION_SMS_SENT
                            putExtra(SmsDeliveryReceiver.EXTRA_CALLBACK_ID, callbackId)
                        },
                        flags
                    )

                    try {
                        if (parts.size > 1) {
                            // Only first part carries the callback; rest are null
                            val sentIntents = ArrayList<PendingIntent?>().apply {
                                add(sentIntent)
                                repeat(parts.size - 1) { add(null) }
                            }
                            sm.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
                        } else {
                            sm.sendTextMessage(phone, null, message, sentIntent, null)
                        }
                        Log.d(TAG, "sendTextMessage() called for $phone - awaiting SMS_SENT broadcast")
                    } catch (e: Exception) {
                        Log.e(TAG, "sendTextMessage() threw for $phone: ${e.message}")
                        SmsDeliveryReceiver.pendingSent.remove(callbackId)
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            // 10s elapsed with no confirmation - treat as failure so Lazarus can retry
            Log.w(TAG, "SMS_SENT timeout for $phone - treating as failure for retry")
            SmsDeliveryReceiver.pendingSent.remove(callbackId)
            false
        }
    }
}
