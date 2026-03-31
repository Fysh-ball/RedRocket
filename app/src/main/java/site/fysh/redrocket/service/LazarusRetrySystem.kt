package site.fysh.redrocket.service

import android.util.Log
import site.fysh.redrocket.queue.AdaptiveSendController
import site.fysh.redrocket.queue.MessageQueueManager
import site.fysh.redrocket.queue.MessageStatus
import kotlinx.coroutines.delay

/**
 * Manages the Lazarus retry logic for failed messages per PROJECT_SPEC.md:
 *
 *  - Pass 1+: retry at 200ms intervals (enforced by RateLimiter inside send())
 *  - Failures go to END of retry queue — no retry-count limit
 *  - After each complete pass: if isKeepTrying=ON, wait 5 s and loop again
 *  - If isKeepTrying=OFF, stop after the first complete pass
 *  - Messages are NEVER permanently dropped by Lazarus
 */
class LazarusRetrySystem(
    private val queueManager: MessageQueueManager,
    private val adaptiveController: AdaptiveSendController,
    private val providerFactory: () -> SmsProvider
) {
    private val TAG = "AdaptiveEngine"

    suspend fun processRetryQueue() {
        if (!queueManager.hasRetries()) return

        Log.i(TAG, "Lazarus mode activated.")
        adaptiveController.enterLazarusMode()

        try {
            var isFirstPass = true

            while (queueManager.hasRetries()) {
                // Between passes: check keep-trying and wait 5 s
                if (!isFirstPass) {
                    if (!adaptiveController.isKeepTrying()) {
                        Log.i(TAG, "Lazarus: Keep Trying is OFF — stopping after first pass.")
                        break
                    }
                    Log.d(TAG, "Lazarus: waiting 5 s before next pass.")
                    delay(5000L)
                }
                isFirstPass = false

                // Snapshot queue size so re-queued failures aren't processed in this pass
                val passSize = queueManager.getDetailedStatus().retrySize
                Log.d(TAG, "Lazarus pass starting: $passSize message(s) in retry queue.")

                repeat(passSize) {
                    val task = queueManager.nextRetryTask() ?: return@repeat

                    queueManager.updateCurrentMessageStatus(
                        MessageStatus(task.recipient.phoneNumber, "Retrying…")
                    )

                    // send() handles the 200ms rate-limit delay and calls markSuccess() internally
                    val success = providerFactory().send(task)
                    if (success) {
                        queueManager.noteRetrySucceeded()
                        queueManager.updateCurrentMessageStatus(
                            MessageStatus(task.recipient.phoneNumber, "Sent ✓")
                        )
                        Log.d(TAG, "Lazarus: delivered to ${task.recipient.phoneNumber}")
                    } else {
                        // Cycle failed message to end of queue — no limit
                        queueManager.requeueForLazarus(task)
                        queueManager.updateCurrentMessageStatus(
                            MessageStatus(task.recipient.phoneNumber, "Failed ✗")
                        )
                        Log.w(TAG, "Lazarus: failed ${task.recipient.phoneNumber}, re-queued")
                    }
                }

                adaptiveController.incrementLazarusPass()
                Log.d(
                    TAG,
                    "Lazarus pass complete. Pass #${adaptiveController.getLazarusPassCount()} done. " +
                        "Remaining: ${queueManager.getDetailedStatus().retrySize}"
                )
            }
        } finally {
            adaptiveController.exitLazarusMode()
            queueManager.updateCurrentMessageStatus(null)
        }
    }
}
