package site.fysh.redrocket.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import site.fysh.redrocket.EmergencyApp
import site.fysh.redrocket.model.PastAlert
import site.fysh.redrocket.util.AlertSensitivity
import site.fysh.redrocket.util.FalseAlarmDetector
import site.fysh.redrocket.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives system emergency broadcasts (Cell Broadcasts / WEA / CMAS / ETWS).
 *
 * Multi-scenario: ALL scenarios are evaluated independently against the incoming
 * broadcast. Each scenario that matches (and is valid + unlocked) is enqueued and
 * sent simultaneously. Scenarios that do not match are unaffected.
 *
 * Uses goAsync() so Android keeps the process alive for the full duration of the
 * coroutine - prevents premature process death for manifest-registered receivers.
 */
class EmergencyBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "EmergencyReceiver"

    companion object {
        /** All known intent extra keys OEMs use for the cell broadcast message body. */
        private val MESSAGE_BODY_KEYS = listOf(
            "android.telephony.SmsCbMessage.messageBody",   // AOSP standard
            "messageBody",                                    // Common shorthand
            "message",                                        // Some OEM variants
            "body",                                           // Samsung on older builds
            "cb_message",                                     // MediaTek
            "com.android.cellbroadcastreceiver.CB_MESSAGE",  // AOSP alternate
            "gsm.cb.message",                                 // GSM layer
            "pdu"                                             // Raw PDU fallback (string)
        )
    }

    /**
     * Extracts the cell broadcast message body by trying all known intent extra keys.
     * Returns the first non-blank value, or empty string if none found.
     */
    private fun extractMessageBody(intent: Intent?): String {
        if (intent == null) return ""
        val extras = intent.extras ?: return ""

        for (key in MESSAGE_BODY_KEYS) {
            val value = extras.getString(key)
            if (!value.isNullOrBlank()) {
                Log.d(TAG, "Extracted CB body from key: $key")
                return value
            }
        }

        // Last resort: try to get the SmsCbMessage object and call getMessageBody()
        try {
            val cbMessage = extras.get("message")
            if (cbMessage != null) {
                val method = cbMessage.javaClass.getMethod("getMessageBody")
                val body = method.invoke(cbMessage) as? String
                if (!body.isNullOrBlank()) {
                    Log.d(TAG, "Extracted CB body via SmsCbMessage.getMessageBody()")
                    return body
                }
            }
        } catch (_: Exception) {
            // SmsCbMessage class not accessible or method doesn't exist
        }

        Log.d(TAG, "No message body found in extras keys: ${extras.keySet()}")
        return ""
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received broadcast action: $action")

        val pendingResult = goAsync()
        val app = context.applicationContext as EmergencyApp

        val messageBody: String = runCatching { extractMessageBody(intent) }.getOrElse { "" }
        Log.i(TAG, "Cell broadcast body: ${messageBody.take(100)}")

        app.appScope.launch(Dispatchers.IO) {
            try {
                val allScenarios = app.database.scenarioDao().getAllScenariosOnce()
                Log.i(TAG, "Evaluating ${allScenarios.size} scenario(s) against cell broadcast")
                if (messageBody.isNotBlank()) {
                    AppLogger.log(app.database, app.appScope, "emergency_detected",
                        "Cell broadcast: ${messageBody.take(120)}")
                }

                // Read sensitivity - fail safely to MEDIUM
                val sensitivityStr = app.settings.alertSensitivity.first()
                val sensitivity = try { AlertSensitivity.valueOf(sensitivityStr) } catch (_: Exception) { AlertSensitivity.MEDIUM }

                // Load user-defined block phrases (any language).
                // Timeout guards against a slow DB stalling the goAsync() 10s window.
                val userBlockPhrases = withTimeoutOrNull(5_000L) {
                    app.database.blockPhraseDao().getAllOnce().map { it.phrase }
                } ?: emptyList()

                // Log every cell broadcast BEFORE evaluation (resilience: record even if eval crashes).
                // The row ID is kept so we can back-fill triggered scenario names after the loop.
                val alertRowId = app.database.pastAlertDao().insertAlertAndGetId(
                    PastAlert(
                        messageContent = messageBody.ifBlank { "[Cell broadcast - no text body]" },
                        source = "cell_broadcast",
                        scenariosTriggered = ""
                    )
                )

                var triggeredCount = 0
                val triggeredNames = mutableListOf<String>()
                val bodyLower = messageBody.lowercase()

                for (scenario in allScenarios) {
                    // Skip invalid scenarios (no message or no recipients)
                    if (!scenario.isValid()) {
                        Log.d(TAG, "Scenario '${scenario.name}' skipped - invalid (missing message or recipients)")
                        continue
                    }

                    // Skip locked scenarios
                    if (scenario.isLocked) {
                        Log.i(TAG, "Scenario '${scenario.name}' is LOCKED - trigger silently ignored.")
                        continue
                    }

                    val keywords = scenario.description
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    // TRIGGER DECISION
                    //
                    // The Hard Block ("this is a test / drill") always applies,
                    // regardless of whether keywords are configured.
                    //
                    // - Has keywords: user's keyword is authoritative - if it matches and the
                    //   content is not blocked by a test phrase, trigger.
                    // - No keywords: scenario fires on any real cell broadcast, but content
                    //   quality is checked with FalseAlarmDetector so sensitivity controls
                    //   how broadly it fires (HIGH catches watches/advisories, LOW only
                    //   life-threatening events). Test blocks still apply here.
                    val keywordMatches = keywords.isEmpty() ||
                        keywords.any { kw -> FalseAlarmDetector.keywordMatchesContent(kw, bodyLower) }

                    val shouldTrigger = when {
                        !keywordMatches -> false
                        FalseAlarmDetector.isBlockedDespiteKeywordMatch(messageBody, userBlockPhrases) -> false
                        keywords.isNotEmpty() -> true  // keyword matched + not blocked = trigger
                        else -> FalseAlarmDetector.shouldTrigger(  // no-keyword: use sensitivity
                            messageBody, emptyList(), isTrustedSource = true, sensitivity = sensitivity
                        )
                    }

                    if (!shouldTrigger) {
                        Log.i(TAG, "Scenario '${scenario.name}' suppressed - blocked or below sensitivity threshold")
                        continue
                    }

                    // Trigger this scenario
                    Log.i(TAG, "TRIGGERED scenario '${scenario.name}' - enqueuing ${scenario.allRecipients().size} recipient(s)")
                    AppLogger.log(app.database, app.appScope, "scenario_triggered",
                        "Scenario '${scenario.name}' triggered by cell broadcast")

                    for (group in scenario.groups) {
                        if (group.recipients.isNotEmpty() && group.message.isNotBlank()) {
                            app.queueManager.enqueueScenario(group.recipients, group.message, scenario.id)
                            AppLogger.log(app.database, app.appScope, "group_processed",
                                "Group '${group.name}' - ${group.recipients.size} contact(s) queued")
                        }
                    }

                    // Lock scenario to prevent re-triggering
                    app.database.scenarioDao().insertScenario(scenario.copy(isLocked = true))
                    Log.i(TAG, "[LOCKOUT] Scenario '${scenario.name}' locked after cell broadcast trigger.")
                    triggeredNames.add(scenario.name)
                    triggeredCount++
                }

                // Back-fill triggered scenario names so Alert History can display them
                if (triggeredCount > 0) {
                    app.database.pastAlertDao().updateScenariosTriggered(
                        alertRowId, triggeredNames.joinToString(",")
                    )
                    Log.i(TAG, "$triggeredCount scenario(s) triggered - starting response listener and sending service")
                    SmsResponseReceiver.startListening()
                    EmergencySendingService.startService(context)
                } else {
                    Log.i(TAG, "No scenarios triggered by this cell broadcast")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing cell broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
