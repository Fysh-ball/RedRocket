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
import site.fysh.redrocket.util.maskPhone
import site.fysh.redrocket.queue.MessageQueueManager
import site.fysh.redrocket.queue.MessageTask
import site.fysh.redrocket.utils.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Sends real SMS messages via Android SmsManager.
 * Only called when debug mode is OFF - routing is handled by EmergencyApp.getActiveSmsProvider().
 */
class SmsSender(
    private val context: Context,
    private val smsManagerProvider: () -> SmsManager?,
    private val rateLimiter: RateLimiter,
    private val adaptiveController: AdaptiveSendController,
    private val queueManager: MessageQueueManager
) : SmsProvider {

    private val TAG = "SmsSender"
    private val requestCounter = AtomicInteger(1000)
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    override suspend fun send(task: MessageTask): Boolean = withContext(Dispatchers.IO) {
        // Runtime permission check - SEND_SMS must be granted before each attempt.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "[PERMISSION DENIED] SEND_SMS not granted - skipping ${maskPhone(task.recipient.phoneNumber)}")
            adaptiveController.reportResult(false)
            return@withContext false
        }

        val sm = smsManagerProvider() ?: run {
            Log.e(TAG, "[ERROR] SmsManager is null - cannot send to ${maskPhone(task.recipient.phoneNumber)}")
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
            Log.w(TAG, "[ABORT] Empty message for ${maskPhone(task.recipient.phoneNumber)}")
            adaptiveController.reportResult(false)
            return@withContext false
        }

        val timestamp = LocalTime.now().format(timeFormatter)
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
                queueManager.markSuccess(task)
            } else {
                adaptiveController.reportResult(false)
                val lastErr = SmsDeliveryReceiver.lastErrorType
                if (lastErr == SmsManager.RESULT_ERROR_RADIO_OFF
                    || lastErr == SmsManager.RESULT_ERROR_NO_SERVICE
                ) {
                    adaptiveController.reportRadioError()
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception for ${maskPhone(task.recipient.phoneNumber)}", e)
            adaptiveController.reportResult(false)
            false
        }
    }

    /**
     * Calls sendTextMessage/sendMultipartTextMessage and suspends until the SMS_SENT
     * PendingIntent fires for ALL parts (max 30 seconds). Returns true only if every
     * part reports RESULT_OK; any part failure resumes immediately as false.
     * Timeout after 30s is treated as failure so Lazarus can retry.
     */
    private suspend fun sendWithConfirmation(
        sm: SmsManager,
        phone: String,
        message: String,
        parts: ArrayList<String>
    ): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return try {
            withTimeout(30_000L) {
                suspendCancellableCoroutine { cont ->
                    if (parts.size > 1) {
                        // Multipart: track each part with its own callback
                        val groupId = UUID.randomUUID().toString()
                        val totalParts = parts.size
                        val partResults = ConcurrentHashMap<Int, Boolean>()

                        for (i in parts.indices) {
                            val partCallbackId = "${groupId}_part$i"
                            SmsDeliveryReceiver.pendingSent[partCallbackId] = { success ->
                                partResults[i] = success
                                if (!success) {
                                    // Any part failure means overall failure
                                    SmsDeliveryReceiver.pendingSent.keys.removeAll {
                                        it.startsWith(groupId)
                                    }
                                    if (cont.isActive) cont.resume(false)
                                } else if (partResults.size == totalParts) {
                                    // All parts succeeded
                                    if (cont.isActive) cont.resume(true)
                                }
                            }
                        }

                        cont.invokeOnCancellation {
                            SmsDeliveryReceiver.pendingSent.keys.removeAll {
                                it.startsWith(groupId)
                            }
                        }

                        val sentIntents = ArrayList<PendingIntent?>().apply {
                            for (i in parts.indices) {
                                val reqCode = requestCounter.getAndIncrement()
                                add(PendingIntent.getBroadcast(
                                    context, reqCode,
                                    Intent(context, SmsDeliveryReceiver::class.java).apply {
                                        action = SmsDeliveryReceiver.ACTION_SMS_SENT
                                        putExtra(
                                            SmsDeliveryReceiver.EXTRA_CALLBACK_ID,
                                            "${groupId}_part$i"
                                        )
                                    },
                                    flags
                                ))
                            }
                        }

                        val deliveryIntents = ArrayList<PendingIntent?>().apply {
                            for (i in parts.indices) {
                                val deliveryReqCode = requestCounter.getAndIncrement()
                                add(PendingIntent.getBroadcast(
                                    context, deliveryReqCode,
                                    Intent(context, SmsDeliveryReceiver::class.java).apply {
                                        action = SmsDeliveryReceiver.ACTION_SMS_DELIVERED
                                        putExtra(
                                            SmsDeliveryReceiver.EXTRA_CALLBACK_ID,
                                            "${groupId}_delivery$i"
                                        )
                                    },
                                    flags
                                ))
                            }
                        }

                        try {
                            sm.sendMultipartTextMessage(
                                phone, null, parts, sentIntents, deliveryIntents
                            )
                            Log.d(TAG, "sendMultipartTextMessage() called for ${maskPhone(phone)} " +
                                "($totalParts parts) - awaiting SMS_SENT broadcasts")
                        } catch (e: Exception) {
                            Log.e(TAG, "sendMultipartTextMessage() threw for ${maskPhone(phone)}: ${e.message}")
                            SmsDeliveryReceiver.pendingSent.keys.removeAll {
                                it.startsWith(groupId)
                            }
                            if (cont.isActive) cont.resume(false)
                        }
                    } else {
                        // Single part: simple callback
                        val callbackId = UUID.randomUUID().toString()
                        SmsDeliveryReceiver.pendingSent[callbackId] = { success ->
                            if (cont.isActive) cont.resume(success)
                        }
                        cont.invokeOnCancellation {
                            SmsDeliveryReceiver.pendingSent.remove(callbackId)
                        }

                        val reqCode = requestCounter.getAndIncrement()
                        val sentIntent = PendingIntent.getBroadcast(
                            context, reqCode,
                            Intent(context, SmsDeliveryReceiver::class.java).apply {
                                action = SmsDeliveryReceiver.ACTION_SMS_SENT
                                putExtra(SmsDeliveryReceiver.EXTRA_CALLBACK_ID, callbackId)
                            },
                            flags
                        )

                        val deliveryReqCode = requestCounter.getAndIncrement()
                        val deliveryIntent = PendingIntent.getBroadcast(
                            context, deliveryReqCode,
                            Intent(context, SmsDeliveryReceiver::class.java).apply {
                                action = SmsDeliveryReceiver.ACTION_SMS_DELIVERED
                                putExtra(
                                    SmsDeliveryReceiver.EXTRA_CALLBACK_ID,
                                    "${callbackId}_delivery"
                                )
                            },
                            flags
                        )

                        try {
                            sm.sendTextMessage(
                                phone, null, message, sentIntent, deliveryIntent
                            )
                            Log.d(TAG, "sendTextMessage() called for ${maskPhone(phone)} " +
                                "- awaiting SMS_SENT broadcast")
                        } catch (e: Exception) {
                            Log.e(TAG, "sendTextMessage() threw for ${maskPhone(phone)}: ${e.message}")
                            SmsDeliveryReceiver.pendingSent.remove(callbackId)
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            // 30s elapsed with no confirmation - treat as failure so Lazarus can retry
            Log.w(TAG, "SMS_SENT timeout for ${maskPhone(phone)} - treating as failure for retry")
            false
        }
    }
}
