package site.fysh.redrocket.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
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

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected - emergency notification detection is disabled")
        serviceScope.cancel()
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

            if (site.fysh.redrocket.BuildConfig.DEBUG) Log.d(TAG, "Received notification from: $packageName | Content: $fullContent")

            // Skip system audio routing notifications - these are not emergency alerts and must
            // never appear in Alert History or trigger detection logic.
            if (fullContent.contains("you're hearing another device", ignoreCase = true) ||
                fullContent.contains("hearing another device", ignoreCase = true)) {
                Log.d(TAG, "Skipping system audio routing notification from $packageName")
                return
            }

            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = powerManager?.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK, "RedRocket:NotificationProcessing"
            )
            wakeLock?.acquire(30_000L)

            serviceScope.launch {
                try {
                    processNotification(packageName, fullContent)
                } finally {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                }
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
        // Path 1: Always check known system WEA/EAS packages (never gated on settings).
        val isKnownEmergencyPackage = EmergencyPackageDetector.isEmergencyAlertPackage(packageName)

        // Path 2: Content-based EAS detection from ANY app's notification.
        // Only active when Global Keyword Detection (wideSpreadEnabled) is ON.
        val wideSpreadOn = withTimeoutOrNull(2_000L) {
            app.settings.wideSpreadEnabled.first()
        } ?: false
        val isContentMatch = wideSpreadOn && looksLikeEASContent(content)

        val isSystemEmergencyAlert = isKnownEmergencyPackage || isContentMatch

        // Hard stop: not a known WEA package AND not a content match (or wide-spread is off).
        if (!isSystemEmergencyAlert) {
            Log.v(TAG, "Non-emergency notification from $packageName - ignored (wideSpread=$wideSpreadOn)")
            return
        }

        if (!site.fysh.redrocket.util.BroadcastDeduplicator.shouldProcess(content)) {
            Log.i(TAG, "Duplicate notification detected within 30s window - skipping")
            return
        }

        // Read sensitivity - fail safely to MEDIUM
        val sensitivityStr = withTimeoutOrNull(3_000L) {
            app.settings.alertSensitivity.first()
        } ?: "MEDIUM"
        val sensitivity = try { AlertSensitivity.valueOf(sensitivityStr) } catch (_: Exception) { AlertSensitivity.MEDIUM }

        // Load user-defined block phrases (any language).
        // Timeout guards against a slow DB stalling the notification processing coroutine.
        val userBlockPhrases = withTimeoutOrNull(5_000L) {
            app.database.blockPhraseDao().getAllOnce().map { it.phrase }
        } ?: emptyList()

        // EAS/WEA notifications are logged unconditionally before any filtering so
        // they always appear in Alert History regardless of scenario configuration.
        // Row ID is kept so triggered scenario names can be back-filled after the loop.
        // Note: isSystemEmergencyAlert is always true here — the !isSystemEmergencyAlert
        // guard at line 119 returns early, so this code is only reached for EAS alerts.
        val easAlertRowId: Long = app.database.pastAlertDao().insertAlertAndGetId(
            PastAlert(
                messageContent = content.ifBlank { "[EAS alert - no text retrieved]" }.take(500),
                source = "alert",
                scenariosTriggered = ""
            )
        )

        // Load ALL scenarios - every scenario actively listens for its trigger words
        val allScenarios = withTimeoutOrNull(5_000L) {
            app.database.scenarioDao().getAllScenariosOnce()
        } ?: run {
            Log.w(TAG, "DB timeout loading scenarios from $packageName - notification ignored")
            // Back-fill the orphaned PastAlert row so it does not appear in Alert History
            // as a ghost entry with no scenario attribution.
            app.database.pastAlertDao().updateScenariosTriggered(
                easAlertRowId, "[scenario load timed out]"
            )
            return
        }
        Log.i(TAG, "Evaluating ${allScenarios.size} scenario(s) against notification from $packageName")

        if (ContextCompat.checkSelfPermission(this@EmergencyNotificationListener, android.Manifest.permission.SEND_SMS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission revoked - cannot process emergency notification")
            return
        }

        val contentLower = FalseAlarmDetector.normalize(content)
        var triggeredCount = 0
        val triggeredNames = mutableListOf<String>()
        val enqueuedPhones = mutableSetOf<String>()

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
                !FalseAlarmDetector.isBlockedDespiteKeywordMatch(content, userBlockPhrases) &&
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
                AppLogger.log(app.database, app.appScope, "scenario_locked_skip",
                    "Scenario '${scenario.name}' trigger skipped — scenario is locked")
                continue
            }

            // Atomically lock the scenario. If another trigger path (e.g. EmergencyBroadcastReceiver)
            // already locked it in a concurrent call, rowsUpdated == 0 and we skip to prevent
            // duplicate sends to all recipients.
            val rowsUpdated = app.database.scenarioDao().lockIfUnlocked(scenario.id)
            if (rowsUpdated == 0) {
                Log.i(TAG, "Scenario '${scenario.name}' already locked by concurrent trigger - duplicate send prevented")
                continue
            }
            Log.i(TAG, "[LOCKOUT] Scenario '${scenario.name}' locked after notification trigger.")

            // Trigger this scenario
            Log.i(TAG, "TRIGGERED scenario '${scenario.name}' - enqueuing ${scenario.allRecipients().size} recipient(s)")
            AppLogger.log(app.database, app.appScope, "scenario_triggered",
                "Scenario '${scenario.name}' triggered by notification from $packageName")

            for (group in scenario.groups) {
                if (group.recipients.isNotEmpty() && group.message.isNotBlank()) {
                    val deduped = group.recipients.filter { r ->
                        val norm = site.fysh.redrocket.util.normalizePhone(r.phoneNumber)
                        enqueuedPhones.add(norm)  // returns false if already in set
                    }
                    if (deduped.isNotEmpty()) {
                        app.queueManager.enqueueScenario(deduped, group.message, scenario.id)
                        AppLogger.log(app.database, app.appScope, "group_processed",
                            "Group '${group.name}' - ${deduped.size} contact(s) queued")
                    }
                }
            }
            triggeredNames.add(scenario.name)
            triggeredCount++
        }

        // Back-fill triggered scenario names into the EAS PastAlert entry logged above.
        if (triggeredCount > 0 && easAlertRowId >= 0) {
            app.database.pastAlertDao().updateScenariosTriggered(
                easAlertRowId, triggeredNames.joinToString(",")
            )
        }

        // (Non-EAS path removed: processNotification() returns early at line 119 for
        // non-emergency packages, so this point is only ever reached for EAS alerts.
        // The back-fill above handles the only logging path needed.)

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
