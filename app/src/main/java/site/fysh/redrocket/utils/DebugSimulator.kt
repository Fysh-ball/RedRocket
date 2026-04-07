package site.fysh.redrocket.utils

import android.content.Context
import site.fysh.redrocket.model.Recipient
import site.fysh.redrocket.queue.MessageQueueManager
import site.fysh.redrocket.service.EmergencySendingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Orchestrates simulation runs.
 * AGENTS.md: Debug tools separate from production logic.
 */
class DebugSimulator(
    private val context: Context,
    private val queueManager: MessageQueueManager,
    private val mockSender: MockSmsSender
) {
    private val simulatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cancel() { simulatorScope.cancel() }

    fun runLoadTest(recipientCount: Int, failureRate: Double) {
        mockSender.failureRate = failureRate
        val dummyRecipients = (1..recipientCount).map { i ->
            Recipient("Test User $i", "555${1000 + i}")
        }
        simulatorScope.launch {
            queueManager.clearQueue()
            queueManager.clearStats()
            queueManager.enqueueScenario(dummyRecipients, "MOCK DEBUG MESSAGE", "debug_scenario")
            
            // Start the foreground service to process the queue and show notifications
            EmergencySendingService.startService(context)
        }
    }
}
