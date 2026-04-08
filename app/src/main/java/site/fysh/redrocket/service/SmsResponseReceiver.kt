package site.fysh.redrocket.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import site.fysh.redrocket.BuildConfig
import site.fysh.redrocket.EmergencyApp
import site.fysh.redrocket.model.ResponseRecord
import site.fysh.redrocket.util.RegionSettings
import site.fysh.redrocket.util.normalizePhone
import site.fysh.redrocket.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SmsResponseReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsResponseReceiver"
        private const val CHANNEL_ID = "responses"
        private const val PREFS_NAME = "sms_response_receiver_state"
        private const val KEY_LISTEN_START = "listen_start_time"
        private const val KEY_LISTEN_HOURS = "listen_hours"
        private val notifIdCounter = AtomicInteger(1000)

        /** Default global listening window: 1 hour. Configurable via setListenWindowHours(). */
        @Volatile
        private var globalListenWindowMs: Long = 3_600_000L

        /** Per-contact window: 1 minute after their first response. */
        private const val PER_CONTACT_WINDOW_MS = 60_000L

        /** Timestamp when listening started (0 = not listening). */
        @Volatile
        private var listenStartTime: Long = 0L

        /** Reactive view of listenStartTime - updates on start/stop. */
        private val _listenStartTimeFlow = MutableStateFlow(0L)
        val listenStartTimeFlow: StateFlow<Long> = _listenStartTimeFlow.asStateFlow()

        /** SharedPreferences for persisting listenStartTime across process death. */
        @Volatile
        private var prefs: android.content.SharedPreferences? = null

        /**
         * Must be called once from EmergencyApp.onCreate() to restore persisted state.
         * Safe to call multiple times - subsequent calls are no-ops.
         */
        fun init(context: Context) {
            if (prefs != null) return  // fast path — no lock needed for initial null check
            synchronized(SmsResponseReceiver::class.java) {
                if (prefs != null) return  // re-check under lock: another thread may have initialized first
                val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                // Restore the configured listen duration BEFORE validating the saved timestamp.
                // Without this, a user who set 3 hours would have their active window incorrectly
                // expired on process restart because the hardcoded 1-hour default would be used.
                val configuredHours = p.getInt(KEY_LISTEN_HOURS, 1)
                globalListenWindowMs = configuredHours.coerceIn(1, 24).toLong() * 3_600_000L
                val saved = p.getLong(KEY_LISTEN_START, 0L)
                // Restore only if the saved window is still within the configured listen duration
                if (saved > 0L && System.currentTimeMillis() - saved < globalListenWindowMs) {
                    listenStartTime = saved
                    _listenStartTimeFlow.value = saved
                    Log.i(TAG, "Restored listen window from SharedPreferences (started ${(System.currentTimeMillis() - saved) / 1000}s ago)")
                }
                prefs = p  // assign last so the fast-path null check above remains safe

                // Create the notification channel once here to avoid a Binder IPC call
                // on every incoming SMS response inside showNotification().
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val nm = context.applicationContext
                        .getSystemService(android.app.NotificationManager::class.java)
                    val ch = android.app.NotificationChannel(
                        CHANNEL_ID,
                        "Response Notifications",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Notifications for SMS responses received" }
                    nm?.createNotificationChannel(ch)
                }
            }
        }

        /**
         * Per-contact tracking: normalizedPhone -> timestamp of first response.
         * Once set, the contact has PER_CONTACT_WINDOW_MS to update their answer.
         * After that, all further messages from that contact are ignored.
         * Thread-safe: BroadcastReceiver may be called from multiple Binder threads.
         */
        private val contactFirstResponseTime = ConcurrentHashMap<String, Long>()

        /**
         * Tracks whether a contact's listening window has permanently expired.
         * Once expired, no further processing occurs for that contact.
         */
        private val contactExpired = ConcurrentHashMap.newKeySet<String>()

        /**
         * Call this when messages start sending to begin the 1-hour global listening window.
         * If already listening, refreshes the global window without resetting per-contact state
         * (prevents wiping a contact's 1-minute window during a resend).
         */
        /**
         * Configure the global listening window duration.
         * Called from ViewModel when the user changes the "reply listen hours" setting.
         */
        fun setListenWindowHours(hours: Int) {
            val clamped = hours.coerceIn(1, 24)
            globalListenWindowMs = clamped.toLong() * 3_600_000L
            // Persist so init() can restore the correct window on next process start
            // without waiting for the ViewModel to push the DataStore value.
            prefs?.edit()?.putInt(KEY_LISTEN_HOURS, clamped)?.apply()
            Log.i(TAG, "Global listen window set to ${clamped}h (${globalListenWindowMs}ms)")
        }

        fun startListening() {
            val now = System.currentTimeMillis()
            synchronized(SmsResponseReceiver::class.java) {
                if (!isListening()) {
                    contactFirstResponseTime.clear()
                    contactExpired.clear()
                    Log.i(TAG, "Response listening STARTED (fresh). Global window: ${globalListenWindowMs / 60_000}min")
                } else {
                    Log.i(TAG, "Response listening window REFRESHED (per-contact state preserved)")
                }
                listenStartTime = now
                _listenStartTimeFlow.value = now
                prefs?.edit()?.putLong(KEY_LISTEN_START, now)?.apply()
            }
        }

        fun isListening(): Boolean {
            if (listenStartTime == 0L) return false
            return System.currentTimeMillis() - listenStartTime < globalListenWindowMs
        }

        /** Returns the timestamp when listening started, or 0 if not listening. */
        fun getListenStartTime(): Long = if (isListening()) listenStartTime else 0L

        /** Immediately stops listening for responses. Does not clear existing response records. */
        fun stopListening() {
            listenStartTime = 0L
            _listenStartTimeFlow.value = 0L
            // commit() is intentional here: if the process dies immediately after stopListening(),
            // apply() might not flush before death, causing listening to incorrectly restore on restart.
            prefs?.edit()?.putLong(KEY_LISTEN_START, 0L)?.commit()
            Log.i(TAG, "Response listening STOPPED manually.")
        }

        /**
         * Parses a response code from the SMS body using normalized keyword matching.
         *
         * Normalization: lowercase → remove punctuation (replace with space) → collapse spaces.
         * Priority (highest wins): EMERGENCY (3) > UPDATES (2) > SAFE (1)
         * Returns null if no match - caller should ignore the message safely.
         */
        fun parseResponseCode(body: String): Int? {
            // Exact single-digit codes checked on the raw trimmed body first
            val rawTrimmed = body.trim()
            when (rawTrimmed) {
                "1" -> return 1
                "2" -> return 2
                "3" -> return 3
            }

            // Guard: reject messages containing multiple digits, or any digit not in {1, 2, 3}.
            // If a single valid digit IS present, return it immediately — digit takes priority
            // over keyword matching so "help 1" correctly returns 1, not 3 (URGENT).
            val digitsInRaw = rawTrimmed.filter { it.isDigit() }
            if (digitsInRaw.isNotEmpty()) {
                if (digitsInRaw.length > 1 || digitsInRaw.first() !in listOf('1', '2', '3')) return null
                return digitsInRaw.first().digitToInt()
            }

            // Normalize: lowercase, replace all non-alphanumeric chars with a space, collapse whitespace
            val text = rawTrimmed
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

            if (text.isEmpty()) return null

            // Split into words once for word-boundary checks
            val words = text.split(" ").toSet()

            // Priority 1 - EMERGENCY (3): checked first so it always wins over SAFE/UPDATES
            // NOTE: bare "help" is word-boundary matched (not substring) to avoid false positives
            // from "helpful", "helped", "unhelpful", etc. triggering an automatic 911 reply.
            val isEmergency =
                text.contains("emergency") ||
                text.contains("not safe") ||
                text.contains("danger") ||
                text.contains("urgent") ||
                text.contains("i need help") ||
                text.contains("need help") ||
                text.contains("please help") ||
                "help" in words ||
                "sos" in words ||
                text.contains("s o s") ||
                text.contains("injured") ||
                text.contains("hurt") ||
                text.contains("come now") ||
                text.contains("call 911") ||
                text.contains("need assistance")

            if (isEmergency) return 3

            // Priority 2 - UPDATES (2)
            val isUpdates =
                text.contains("keep me updated") ||
                text.contains("want updates") ||
                text.contains("safe but update") ||
                text.contains("im safe but update") ||
                text.contains("update me") ||
                text.contains("updates") ||
                text.contains("update") ||
                text.contains("need more info") ||
                text.contains("more info") ||
                text.contains("what s happening") ||
                text.contains("whats happening") ||
                text.contains("what happened") ||
                text.contains("inform me")

            if (isUpdates) return 2

            // Priority 3 - SAFE (1)
            // "ok", "fine", "good" require exact message match - too broad as substrings
            val isSafe =
                text.contains("i am safe") ||
                text.contains("im safe") ||
                text.contains("i m safe") ||
                text.contains("all good") ||
                text.contains("im good") ||
                text.contains("i m good") ||
                text.contains("okay") ||
                text.contains("safe") ||
                text == "ok" ||
                text == "fine" ||
                text == "good"

            if (isSafe) return 1

            return null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")

        // Check global listening window FIRST - fast reject if not listening
        if (!isListening()) {
            Log.d(TAG, "Global listening window inactive - ignoring SMS")
            return
        }

        val pending = goAsync()
        val app = context.applicationContext as EmergencyApp

        app.appScope.launch(Dispatchers.IO) {
            try {
                val bundle = intent.extras ?: run {
                    Log.w(TAG, "No extras in intent, ignoring")
                    pending.finish()
                    return@launch
                }

                @Suppress("DEPRECATION")
                val pdus = bundle.get("pdus") as? Array<*> ?: run {
                    Log.w(TAG, "No PDUs found in bundle, ignoring")
                    pending.finish()
                    return@launch
                }

                val format = bundle.getString("format") ?: "3gpp"
                val messages = pdus.mapNotNull { pdu ->
                    try {
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse PDU", e)
                        null
                    }
                }

                val sender = messages.firstOrNull()?.originatingAddress ?: run {
                    Log.w(TAG, "Could not determine sender address, ignoring")
                    pending.finish()
                    return@launch
                }

                val region = RegionSettings.effectiveRegion
                val normalizedSender = normalizePhone(sender, region)
                val db = app.database

                // Fast reject: only process SMS from known scenario recipients
                val allScenarios = withTimeoutOrNull(5_000L) {
                    db.scenarioDao().getAllScenariosOnce()
                } ?: run {
                    Log.w(TAG, "DB timeout loading scenarios - ignoring SMS from $normalizedSender")
                    pending.finish()
                    return@launch
                }
                val knownPhones = allScenarios
                    .flatMap { it.allRecipients() }
                    .map { normalizePhone(it.phoneNumber, region) }
                    .toSet()

                if (normalizedSender !in knownPhones) {
                    Log.d(TAG, "Sender $normalizedSender not in any scenario - ignoring")
                    pending.finish()
                    return@launch
                }

                val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
                Log.d(TAG, "SMS from sender='$sender', body='$body'")

                val responseCode = parseResponseCode(body) ?: run {
                    Log.d(TAG, "Body '$body' is not a recognizable response, ignoring")
                    pending.finish()
                    return@launch
                }

                val now = System.currentTimeMillis()

                // Per-contact window check
                // If contact's window is permanently expired, reject immediately
                if (contactExpired.contains(normalizedSender)) {
                    Log.d(TAG, "Contact $normalizedSender window permanently expired - ignoring")
                    pending.finish()
                    return@launch
                }

                // Atomically record the first response time. putIfAbsent returns null if we
                // just created the entry (first response), or the existing timestamp if already present.
                val existingFirstTime = contactFirstResponseTime.putIfAbsent(normalizedSender, now)
                if (existingFirstTime == null) {
                    Log.i(TAG, "First response from $normalizedSender - 1-minute window started")
                } else {
                    val elapsed = now - existingFirstTime
                    if (elapsed >= PER_CONTACT_WINDOW_MS) {
                        // 1-minute window expired - permanently stop listening to this contact
                        contactExpired.add(normalizedSender)
                        Log.d(TAG, "Contact $normalizedSender 1-minute window expired (${elapsed}ms). Stopped listening.")
                        pending.finish()
                        return@launch
                    }
                    Log.d(TAG, "Contact $normalizedSender updating response within 1-min window (${elapsed}ms)")
                }

                val responseText = when (responseCode) {
                    1 -> "Safe"
                    2 -> "Wants Updates"
                    3 -> "URGENT"
                    else -> "Unknown"
                }

                // Match against ALL scenarios (multi-scenario support)
                Log.d(TAG, "Checking against ${allScenarios.size} scenarios")

                var matched = false
                for (scenario in allScenarios) {
                    for (recipient in scenario.allRecipients()) {
                        val normalizedRecipient = normalizePhone(recipient.phoneNumber, region)
                        if (normalizedRecipient == normalizedSender) {
                            if (BuildConfig.DEBUG) {
                                Log.i(TAG, "MATCH: recipient='${recipient.name}' (${recipient.phoneNumber}), scenario='${scenario.name}', code=$responseCode")
                            } else {
                                Log.i(TAG, "MATCH: scenario='${scenario.name}', code=$responseCode")
                            }

                            val record = ResponseRecord(
                                scenarioId = scenario.id,
                                phoneNumber = recipient.phoneNumber,
                                recipientName = recipient.name,
                                responseCode = responseCode,
                                responseText = responseText,
                                receivedAt = now
                            )
                            db.responseRecordDao().insertResponse(record)

                            val displayName = if (recipient.name.isNotBlank()) recipient.name else "Added Number"
                            AppLogger.log(app.database, app.appScope, "response_received",
                                "Contact '$displayName' replied: $responseText")
                            showNotification(context, displayName, responseText, responseCode)
                            matched = true

                            if (responseCode == 3) {
                                sendAutoReply(context, sender)
                            }
                        }
                    }
                }

                if (!matched) {
                    Log.d(TAG, "No matching recipient found for sender='$normalizedSender'")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS response", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun sendAutoReply(context: Context, destination: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS permission not granted - auto-reply to $destination skipped")
            return
        }
        try {
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            smsManager?.sendTextMessage(
                destination,
                null,
                "Call 911 if it's a life threatening emergency",
                null,
                null
            )
            Log.i(TAG, "Auto-replied to $destination: 'Call 911...'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto-reply to $destination", e)
        }
    }

    private fun showNotification(context: Context, recipientName: String, responseText: String, responseCode: Int) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Channel is created once in init() — no Binder IPC here on every response.
        val priority = if (responseCode == 3) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH
        val notifId = notifIdCounter.getAndIncrement()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Response Received")
            .setContentText("$recipientName: $responseText")
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        nm.notify(notifId, notification)
        Log.d(TAG, "Showed notification for $recipientName: $responseText")
    }
}
