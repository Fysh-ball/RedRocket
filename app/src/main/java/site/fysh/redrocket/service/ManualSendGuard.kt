package site.fysh.redrocket.service

import android.util.Log
import site.fysh.redrocket.model.Recipient
import site.fysh.redrocket.model.Scenario
import site.fysh.redrocket.model.ScenarioDao
import site.fysh.redrocket.queue.MessageQueueManager
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Security and safety guard for manual triggers.
 */
class ManualSendGuard(
    private val queueManager: MessageQueueManager,
    private val scenarioDao: ScenarioDao
) {
    private var currentCaptcha: String? = null
    private var countdownJob: Job? = null
    /**
     * Generates a randomized 6-character OTP code containing letters and numbers.
     */
    fun generateNewCaptcha(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val captcha = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        currentCaptcha = captcha
        return captcha
    }

    fun verifyCaptcha(userInput: String): Boolean = userInput.equals(currentCaptcha, ignoreCase = true)

    /**
     * Sends ALL valid, unlocked scenarios simultaneously.
     * Each scenario independently uses its own recipients, message, and ID.
     */
    fun secureSendAll(
        scope: CoroutineScope,
        scenarios: List<Scenario>,
        onCountdownTick: suspend (Int) -> Unit
    ) {
        val validScenarios = scenarios.filter { it.isValid() && !it.isLocked }
        if (validScenarios.isEmpty()) return

        currentCaptcha = null

        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (i in 4 downTo 1) {
                onCountdownTick(i)
                delay(1000)
            }
            // Enqueue ALL valid scenarios inside NonCancellable so that if the ViewModel is
            // destroyed mid-countdown (e.g. app backgrounded), the enqueue still completes.
            // The service will then send even if the UI is gone.
            withContext(NonCancellable) {
                // Lock FIRST, then enqueue only scenarios we successfully locked
                val lockedScenarios = mutableListOf<Scenario>()
                for (scenario in validScenarios) {
                    val rowsUpdated = scenarioDao.lockIfUnlocked(scenario.id)
                    if (rowsUpdated > 0) {
                        lockedScenarios.add(scenario)
                    } else {
                        Log.i("ManualSendGuard", "Scenario '${scenario.name}' already locked by concurrent trigger - skipping")
                    }
                }
                // Then enqueue only successfully locked scenarios
                for (scenario in lockedScenarios) {
                    for (group in scenario.groups) {
                        if (group.recipients.isNotEmpty() && group.message.isNotBlank()) {
                            queueManager.enqueueScenario(group.recipients, group.message, scenario.id)
                        }
                    }
                }
                onCountdownTick(0)
            }
        }
    }

    fun cancelSend() {
        countdownJob?.cancel()
        countdownJob = null
    }
}
