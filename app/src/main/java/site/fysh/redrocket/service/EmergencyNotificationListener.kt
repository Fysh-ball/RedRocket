package site.fysh.redrocket.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import site.fysh.redrocket.EmergencyApp
import site.fysh.redrocket.model.PastAlert
import site.fysh.redrocket.util.AlertSensitivity
import site.fysh.redrocket.util.EmergencyPackageDetector
import site.fysh.redrocket.util.FalseAlarmDetector
import site.fysh.redrocket.utils.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Monitors notifications for emergency triggers.
 *
 * Multi-scenario: every incoming notification is evaluated against ALL scenarios
 * independently. Each scenario with matching keywords (and that is valid + unlocked)
 * is enqueued and sent simultaneously.
 */
class EmergencyNotificationListener : NotificationListenerService() {
    private val TAG = "EmergencyListener"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in notification listener scope", throwable)
    }

    // Var so it can be recreated on rebind - Android may call onListenerConnected()
    // without calling onCreate() again, leaving a cancelled scope from a prior bind.
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private val app by lazy { application as EmergencyApp }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
            Log.i(TAG, "Notification Listener reconnected - scope recreated.")
        } else {
            Log.i(TAG, "Notification Listener connected and active.")
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            val notification = sbn?.notification ?: return
            val packageName = sbn.packageName ?: ""

            // Skip our own notifications to avoid loops
            if (packageName == this.packageName) return


            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            val ticker = notification.tickerText?.toString() ?: ""

            val fullContent = "$title $text $subText $ticker"

            Log.d(TAG, "Received notification from: $packageName | Content: $fullContent")

            // Skip system audio routing notifications - these are not emergency alerts and must
            // never appear in Alert History or trigger detection logic.
            if (fullContent.contains("you're hearing another device", ignoreCase = true) ||
                fullContent.contains("hearing another device", ignoreCase = true)) {
                Log.d(TAG, "Skipping system audio routing notification from $packageName")
                return
            }

            serviceScope.launch {
                processNotification(packageName, fullContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming notification", e)
        }
    }

    /**
     * Content-based EAS fallback: catches WEA/EAS from OEM packages not in the
     * static known-package list. Matches FCC-mandated WEA category names and the
     * generic "Emergency Alert" notification title used across many OEM builds.
     */
    private fun looksLikeEASContent(content: String): Boolean {
        val upper = content.uppercase()
        return upper.contains("WIRELESS EMERGENCY ALERT") ||
               upper.contains("PRESIDENTIAL ALERT") ||
               upper.contains("EXTREME ALERT") ||
               upper.contains("SEVERE ALERT") ||
               upper.contains("AMBER ALERT") ||
               upper.contains("CIVIL EMERGENCY") ||
               upper.contains("NATIONAL EMERGENCY") ||
               upper.contains("EMERGENCY ALERT")
    }

    private suspend fun processNotification(packageName: String, content: String) {
        // Never process notification-based triggers when Red Rocket is not armed.
        // Cell broadcasts (EmergencyBroadcastReceiver) are unaffected.
        if (!app.settings.isArmed.first()) {
            Log.d(TAG, "Not armed - notification from $packageName ignored")
            return
        }

        // Two-path EAS detection: known package OR FCC-mandated content phrases.
        // isEmergencyAlertPackage always checks the full static list first so partial
        // runtime detection can never cause a known WEA package to be missed.
        val isSystemEmergencyAlert = EmergencyPackageDetector.isEmergencyAlertPackage(packageName)
            || looksLikeEASContent(content)

        // Hard stop: notifications from non-emergency apps (YouTube, social media, games, etc.)
        // can never trigger a scenario. Exit before touching the database.
        if (!isSystemEmergencyAlert) {
            Log.v(TAG, "Non-emergency notification from $packageName — ignored")
            return
        }

        // Read sensitivity - fail safely to MEDIUM
        val sensitivityStr = app.settings.alertSensitivity.first()
        val sensitivity = try { AlertSensitivity.valueOf(sensitivityStr) } catch (_: Exception) { AlertSensitivity.MEDIUM }

        // Load user-defined block phrases (any language).
        // Timeout guards against a slow DB stalling the notification processing coroutine.
        val userBlockPhrases = withTimeoutOrNull(5_000L) {
            app.database.blockPhraseDao().getAllOnce().map { it.phrase }
        } ?: emptyList()

        // EAS/WEA notifications are logged unconditionally before any filtering so
        // they always appear in Alert History regardless of scenario configuration.
        // Row ID is kept so triggered scenario names can be back-filled after the loop.
        val easAlertRowId: Long = if (isSystemEmergencyAlert) {
            app.database.pastAlertDao().insertAlertAndGetId(
                PastAlert(
                    messageContent = content.ifBlank { "[EAS alert - no text retrieved]" }.take(500),
                    source = "alert",
                    scenariosTriggered = ""
                )
            )
        } else {
            -1L
        }

        // Load ALL scenarios - every scenario actively listens for its trigger words
        val allScenarios = app.database.scenarioDao().getAllScenariosOnce()
        Log.i(TAG, "Evaluating ${allScenarios.size} scenario(s) against notification from $packageName")

        val contentLower = content.lowercase()
        var triggeredCount = 0
        val triggeredNames = mutableListOf<String>()

        for (scenario in allScenarios) {
            val keywords = scenario.description
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // TRIGGER DECISION
            //
            // Reaching here means the notification passed the isSystemEmergencyAlert gate
            // above — it is from a known OEM emergency package or contains FCC-mandated phrases.
            //
            // - Has keywords: keyword must match and not be blocked.
            // - No keywords: wildcard — FalseAlarmDetector evaluates content quality against
            //   the sensitivity threshold (HIGH = broad, LOW = life-threatening only).
            val isMatched = if (keywords.isEmpty()) {
                FalseAlarmDetector.shouldTrigger(
                    content, emptyList(), isTrustedSource = true, sensitivity = sensitivity
                )
            } else {
                keywords.any { kw -> FalseAlarmDetector.keywordMatchesContent(kw, contentLower) }
                    && !FalseAlarmDetector.isBlockedDespiteKeywordMatch(content, userBlockPhrases)
            }

            if (!isMatched) continue

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

            // Trigger this scenario
            Log.i(TAG, "TRIGGERED scenario '${scenario.name}' - enqueuing ${scenario.allRecipients().size} recipient(s)")
            AppLogger.log(app.database, app.appScope, "scenario_triggered",
                "Scenario '${scenario.name}' triggered by notification from $packageName")

            for (group in scenario.groups) {
                if (group.recipients.isNotEmpty() && group.message.isNotBlank()) {
                    app.queueManager.enqueueScenario(group.recipients, group.message, scenario.id)
                    AppLogger.log(app.database, app.appScope, "group_processed",
                        "Group '${group.name}' - ${group.recipients.size} contact(s) queued")
                }
            }

            // Lock scenario to prevent re-triggering
            app.database.scenarioDao().insertScenario(scenario.copy(isLocked = true))
            Log.i(TAG, "[LOCKOUT] Scenario '${scenario.name}' locked after notification trigger.")
            triggeredNames.add(scenario.name)
            triggeredCount++
        }

        // Back-fill triggered scenario names into the EAS PastAlert entry logged above.
        if (isSystemEmergencyAlert && triggeredCount > 0 && easAlertRowId >= 0) {
            app.database.pastAlertDao().updateScenariosTriggered(
                easAlertRowId, triggeredNames.joinToString(",")
            )
        }

        // Non-EAS notifications only appear in Alert History when they actually caused
        // a trigger. Keyword matches that were blocked or didn't fire are not logged.
        if (!isSystemEmergencyAlert && triggeredCount > 0) {
            app.database.pastAlertDao().insertAlert(
                PastAlert(
                    messageContent = content.take(500),
                    source = "notification",
                    scenariosTriggered = triggeredNames.joinToString(",")
                )
            )
        }

        if (triggeredCount > 0) {
            AppLogger.log(app.database, app.appScope, "emergency_detected",
                "Notification from $packageName matched triggers")
            Log.i(TAG, "$triggeredCount scenario(s) triggered - starting response listener and sending service")
            SmsResponseReceiver.startListening()
            EmergencySendingService.startService(this@EmergencyNotificationListener)
        } else {
            Log.v(TAG, "Notification from $packageName did not match any active triggers.")
        }
    }
}
