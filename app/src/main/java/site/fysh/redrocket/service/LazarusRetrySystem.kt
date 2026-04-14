package site.fysh.redrocket.service

import android.util.Log
import site.fysh.redrocket.queue.AdaptiveSendController
import site.fysh.redrocket.util.maskPhone
import site.fysh.redrocket.queue.MessageQueueManager
import site.fysh.redrocket.queue.MessageStatus
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Manages the Lazarus retry logic for failed messages per PROJECT_SPEC.md:
 *
 *  - Pass 1+: retry at 200ms intervals (enforced by RateLimiter inside send())
 *  - Failures go to END of retry queue - no retry-count limit
 *  - After each complete pass: if isKeepTrying=ON, wait with exponential backoff and loop again
 *  - If isKeepTrying=OFF, stop after the first complete pass and move remaining to failed queue
 *  - Circuit breaker: stops after MAX_PASSES or after 5 consecutive zero-progress passes
 *  - On early exit, remaining retries are moved to failedQueue to prevent re-entry
 */
class LazarusRetrySystem(
    private val queueManager: MessageQueueManager,
    private val adaptiveController: AdaptiveSendController,
    private val providerFactory: () -> SmsProvider
) {
    private val TAG = "AdaptiveEngine"

    companion object {
        /** Hard ceiling on total passes to prevent unbounded looping. */
        const val MAX_PASSES = 50
        /** Stop if this many consecutive passes produce zero successful sends. */
        const val MAX_ZERO_PROGRESS_PASSES = 5
        /** Initial inter-pass delay (ms). */
        const val INITIAL_DELAY_MS = 5_000L
        /** Maximum inter-pass delay (ms). */
        const val MAX_DELAY_MS = 60_000L
    }

    suspend fun processRetryQueue() {
        if (!queueManager.hasRetries()) return

        Log.i(TAG, "Lazarus mode activated.")
        adaptiveController.enterLazarusMode()

        var passCount = 0
        var consecutiveZeroProgressPasses = 0
        var currentDelayMs = INITIAL_DELAY_MS

        try {
            var isFirstPass = true

            while (queueManager.hasRetries()) {
                // Between passes: check keep-trying and backoff delay
                if (!isFirstPass) {
                    if (!adaptiveController.isKeepTrying()) {
                        Log.i(TAG, "Lazarus: Keep Trying is OFF - stopping after first pass.")
                        queueManager.moveRetriesToFailed()
                        break
                    }

                    // Circuit breaker: max passes
                    if (passCount >= MAX_PASSES) {
                        Log.w(TAG, "Lazarus: circuit breaker - reached $MAX_PASSES passes. Stopping.")
                        queueManager.moveRetriesToFailed()
                        break
                    }

                    // Circuit breaker: consecutive zero-progress passes
                    if (consecutiveZeroProgressPasses >= MAX_ZERO_PROGRESS_PASSES) {
                        Log.w(TAG, "Lazarus: circuit breaker - $MAX_ZERO_PROGRESS_PASSES consecutive passes with zero progress. Stopping.")
                        queueManager.moveRetriesToFailed()
                        break
                    }

                    Log.d(TAG, "Lazarus: waiting ${currentDelayMs}ms before next pass.")
                    delay(currentDelayMs)
                    // Exponential backoff: 5s, 10s, 20s, 40s, capped at 60s
                    currentDelayMs = min(currentDelayMs * 2, MAX_DELAY_MS)
                }
                isFirstPass = false

                // Snapshot queue size so re-queued failures aren't processed in this pass
                val passSize = queueManager.getDetailedStatus().retrySize
                Log.d(TAG, "Lazarus pass starting: $passSize message(s) in retry queue.")

                var passSuccessCount = 0

                repeat(passSize) {
                    val task = queueManager.nextRetryTask() ?: return@repeat

                    queueManager.updateCurrentMessageStatus(
                        MessageStatus(task.recipient.phoneNumber, "Retrying…")
                    )

                    // send() handles the 200ms rate-limit delay and calls markSuccess() internally
                    val success = providerFactory().send(task)
                    if (success) {
                        passSuccessCount++
                        queueManager.noteRetrySucceeded()
                        queueManager.updateCurrentMessageStatus(
                            MessageStatus(task.recipient.phoneNumber, "Sent ✓")
                        )
                        Log.d(TAG, "Lazarus: delivered to ${maskPhone(task.recipient.phoneNumber)}")
                    } else {
                        // Cycle failed message to end of queue - no limit
                        queueManager.requeueForLazarus(task)
                        queueManager.updateCurrentMessageStatus(
                            MessageStatus(task.recipient.phoneNumber, "Failed ✗")
                        )
                        Log.w(TAG, "Lazarus: failed ${maskPhone(task.recipient.phoneNumber)}, re-queued")
                    }
                }

                passCount++
                adaptiveController.incrementLazarusPass()

                // Track zero-progress passes for circuit breaker
                if (passSuccessCount == 0) {
                    consecutiveZeroProgressPasses++
                } else {
                    consecutiveZeroProgressPasses = 0
                    // Reset backoff on progress
                    currentDelayMs = INITIAL_DELAY_MS
                }

                Log.d(
                    TAG,
                    "Lazarus pass complete. Pass #${adaptiveController.getLazarusPassCount()} done. " +
                        "Successes this pass: $passSuccessCount. " +
                        "Zero-progress streak: $consecutiveZeroProgressPasses. " +
                        "Remaining: ${queueManager.getDetailedStatus().retrySize}"
                )
            }
        } finally {
            adaptiveController.exitLazarusMode()
            queueManager.updateCurrentMessageStatus(null)
        }
    }
}
