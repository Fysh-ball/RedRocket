package site.fysh.redrocket.utils

import android.util.Log
import site.fysh.redrocket.queue.AdaptiveSendController
import site.fysh.redrocket.queue.MessageQueueManager
import site.fysh.redrocket.queue.MessageTask
import site.fysh.redrocket.service.SmsProvider
import kotlinx.coroutines.delay
import java.util.Random

/**
 * Simulates SMS sending for testing.
 */
class MockSmsSender(
    private val rateLimiter: RateLimiter,
    private val adaptiveController: AdaptiveSendController,
    private val queueManager: MessageQueueManager
) : SmsProvider {
    private val TAG = "MockSmsSender"
    private val random = Random()
    var failureRate: Double = 0.0

    override suspend fun send(task: MessageTask): Boolean {
        val responseInstructions = "\n\nPlease respond with:\n1 = Safe\n2 = Safe, want updates\n3 = NEED HELP URGENT\n\nTHIS IS AN AUTOMATED MESSAGE"
        val fullMessage = task.message + responseInstructions
        Log.d(TAG, "[MOCK] Would send to ${task.recipient.phoneNumber}: ${fullMessage.take(100)}...")

        val adaptiveDelay = adaptiveController.getRequiredDelayMs()
        rateLimiter.waitForNextSlot(adaptiveDelay)

        // Simulate network latency
        delay(100L)

        val maxAttempts = if (adaptiveController.currentState.value == site.fysh.redrocket.model.SendState.SEQUENTIAL) 3 else 1

        // Only the first attempt outcome is reported to the adaptive controller.
        // Retry success must not reward the controller - it earned sequential mode.
        val firstResult = random.nextDouble() >= failureRate
        adaptiveController.reportResult(firstResult)

        if (firstResult) {
            Log.d(TAG, "[MOCK] COMPLETE (Attempt 1): ${task.recipient.phoneNumber}")
            queueManager.markSuccess(task)
            return true
        }

        Log.e(TAG, "[MOCK] FAILED (Attempt 1): ${task.recipient.phoneNumber}")

        // Retry without reporting to adaptive controller
        for (attempt in 2..maxAttempts) {
            delay(200L)
            if (random.nextDouble() >= failureRate) {
                Log.d(TAG, "[MOCK] COMPLETE (Attempt $attempt): ${task.recipient.phoneNumber}")
                queueManager.markSuccess(task)
                return true
            }
            Log.e(TAG, "[MOCK] FAILED (Attempt $attempt): ${task.recipient.phoneNumber}")
        }

        // Caller contract: a `false` return must be matched by exactly one handleFailure() call
        // in EmergencySendingService.processQueue(). MockSmsSender does NOT call markFailure()
        // here because the production SmsSender behaves the same way — both delegate failure
        // bookkeeping to the service loop. Verified parity with SmsSender.send() retry path.
        return false
    }
}
