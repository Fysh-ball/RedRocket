package site.fysh.redrocket.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import site.fysh.redrocket.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives SMS_SENT and SMS_DELIVERED system broadcasts for each outgoing message.
 * Correlates results back to in-flight send() calls via a callback map.
 */
class SmsDeliveryReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT = "site.fysh.redrocket.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "site.fysh.redrocket.SMS_DELIVERED"
        const val EXTRA_CALLBACK_ID = "callback_id"

        // Maps callbackId → (success: Boolean) callback registered by SmsSender.send()
        internal val pendingSent = ConcurrentHashMap<String, (Boolean) -> Unit>()

        /** Last error code from a failed SMS_SENT broadcast (0 = no error). */
        @Volatile var lastErrorType: Int = 0
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callbackId = intent.getStringExtra(EXTRA_CALLBACK_ID) ?: return

        when (intent.action) {
            ACTION_SMS_SENT -> {
                val success = resultCode == Activity.RESULT_OK
                val resultName = when (resultCode) {
                    Activity.RESULT_OK -> "OK"
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC_FAILURE"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF"
                    else -> "UNKNOWN($resultCode)"
                }
                if (BuildConfig.DEBUG) {
                    Log.i("SmsDeliveryReceiver", "SMS_SENT $resultName (callbackId=$callbackId)")
                } else if (!success) {
                    Log.w("SmsDeliveryReceiver", "SMS_SENT failed: $resultName")
                }
                if (!success) {
                    lastErrorType = resultCode
                }
                pendingSent.remove(callbackId)?.invoke(success)
            }
            ACTION_SMS_DELIVERED -> {
                if (BuildConfig.DEBUG) {
                    val ok = resultCode == Activity.RESULT_OK
                    Log.i("SmsDeliveryReceiver", "SMS_DELIVERED ${if (ok) "OK" else "FAILED($resultCode)"}")
                }
            }
        }
    }
}
