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

    // Var so it can be recreated on rebind — Android may call onListenerConnected()
    // without calling onCreate() again, leaving a cancelled scope from a prior bind.
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private val app by lazy { application as EmergencyApp }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
            Log.i(TAG, "Notification Listener reconnected — scope recreated.")
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

            // Skip system audio routing notifications — these are not emergency alerts and must
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
     * Content-based EAS fallback: catches alerts from custom/carrier/OEM packages that
     * are not in EmergencyPackageDetector's known list. WEA message titles follow
     * FCC-mandated category names, so matching them here is high-confidence.
     */
    private fun looksLikeEASContent(content: String): Boolean {
        val upper = content.uppercase()
        return upper.contains("WIRELESS EMERGENCY ALERT") ||
               upper.contains("PRESIDENTIAL ALERT") ||
               upper.contains("EXTREME ALERT") ||
               upper.contains("SEVERE ALERT") ||
               upper.contains("AMBER ALERT") ||
               upper.contains("CIVIL EMERGENCY") ||
               upper.contains("NATIONAL EMERGENCY")
    }

    private suspend fun processNotification(packageName: String, content: String) {
        // Two-path EAS detection: known package OR FCC-mandated content phrases.
        // The content fallback catches alerts from custom OEM/carrier builds whose
        // package names are not in our detection list.
        val isSystemEmergencyAlert = EmergencyPackageDetector.isEmergencyAlertPackage(packageName)
            || looksLikeEASContent(content)

        // Read sensitivity — fail safely to MEDIUM
        val sensitivityStr = app.settings.alertSensitivity.first()
        val sensitivity = try { AlertSensitivity.valueOf(sensitivityStr) } catch (_: Exception) { AlertSensitivity.MEDIUM }

        // SPEC: ALL EAS/WEA notifications logged BEFORE any filtering — unconditionally.
        // Row ID is retained so triggered scenario names can be back-filled after the loop.
        val easAlertRowId: Long = if (isSystemEmergencyAlert) {
            app.database.pastAlertDao().insertAlertAndGetId(
                PastAlert(
                    messageContent = content.ifBlank { "[EAS alert — no text retrieved]" }.take(500),
                    source = "alert",
                    scenariosTriggered = ""
                )
            )
        } else {
            -1L
        }

        // Load ALL scenarios — every scenario actively listens for its trigger words
        val allScenarios = app.database.scenarioDao().getAllScenariosOnce()
        Log.i(TAG, "Evaluating ${allScenarios.size} scenario(s) against notification from $packageName")

        val contentLower = content.lowercase()
        var triggeredCount = 0
        val triggeredNames = mutableListOf<String>()
        // True if ANY scenario's keywords matched the content — used to decide whether
        // to log the notification even when blocked (test phrase / Amber). Mirrors the
        // EAS path which logs unconditionally so users can always see what came in.
        var hadKeywordMatch = false

        for (scenario in allScenarios) {
            val keywords = scenario.description
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // TRIGGER DECISION
            //
            // Amber Block and Hard Block always apply regardless of keyword configuration.
            //
            // - Has keywords: user's keyword is authoritative. Match + not blocked = trigger.
            //   FalseAlarmDetector scoring is bypassed; keywords define exactly what matters.
            // - No keywords: wildcard scenario — only fires on confirmed EAS source, and
            //   content quality is evaluated by FalseAlarmDetector so the sensitivity setting
            //   controls the threshold (HIGH = broad, LOW = life-threatening only).
            val isMatched = if (keywords.isEmpty()) {
                isSystemEmergencyAlert && FalseAlarmDetector.shouldTrigger(
                    content, emptyList(), isTrustedSource = true, sensitivity = sensitivity
                )
            } else {
                val hasKeywordMatch = keywords.any { kw -> FalseAlarmDetector.keywordMatchesContent(kw, contentLower) }
                if (hasKeywordMatch) hadKeywordMatch = true
                hasKeywordMatch && !FalseAlarmDetector.isBlockedDespiteKeywordMatch(content)
            }

            if (!isMatched) continue

            // Skip invalid scenarios (no message or no recipients)
            if (!scenario.isValid()) {
                Log.d(TAG, "Scenario '${scenario.name}' skipped — invalid (missing message or recipients)")
                continue
            }

            // Skip locked scenarios
            if (scenario.isLocked) {
                Log.i(TAG, "Scenario '${scenario.name}' is LOCKED — trigger silently ignored.")
                continue
            }

            // ── Trigger this scenario ──────────────────────────────────────
            Log.i(TAG, "TRIGGERED scenario '${scenario.name}' — enqueuing ${scenario.allRecipients().size} recipient(s)")
            AppLogger.log(app.database, app.appScope, "scenario_triggered",
                "Scenario '${scenario.name}' triggered by notification from $packageName")

            for (group in scenario.groups) {
                if (group.recipients.isNotEmpty() && group.message.isNotBlank()) {
                    app.queueManager.enqueueScenario(group.recipients, group.message, scenario.id)
                    AppLogger.log(app.database, app.appScope, "group_processed",
                        "Group '${group.name}' — ${group.recipients.size} contact(s) queued")
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

        // Log non-EAS notifications to Alert History when keywords matched — regardless of
        // whether a trigger fired. This mirrors the EAS path (logs unconditionally) so users
        // can always see what the system evaluated, including blocked test alerts.
        if (!isSystemEmergencyAlert && (triggeredCount > 0 || hadKeywordMatch)) {
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
            Log.i(TAG, "$triggeredCount scenario(s) triggered — starting response listener and sending service")
            SmsResponseReceiver.startListening()
            EmergencySendingService.startService(this@EmergencyNotificationListener)
        } else {
            Log.v(TAG, "Notification from $packageName did not match any active triggers.")
        }
    }
}
