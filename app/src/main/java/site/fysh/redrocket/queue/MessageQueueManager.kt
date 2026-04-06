package site.fysh.redrocket.queue

import android.util.Log
import site.fysh.redrocket.model.Recipient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList
import java.util.Queue

/**
 * Internal data class representing a single message task.
 * Fully immutable - prevents data races when SmsSender holds a reference
 * while handleFailure/requeueForLazarus create updated copies inside the mutex.
 */
data class MessageTask(
    val recipient: Recipient,
    val message: String,
    val scenarioId: String,
    val retryCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long = 0
)

/**
 * Live per-message status emitted during active sending.
 */
data class MessageStatus(
    val phoneNumber: String,
    val statusText: String  // "Sending…", "Sent ✓", "Failed ✗", "Retrying…"
)

/**
 * Detailed status of the message queues.
 */
data class QueueStatus(
    val primarySize: Int,
    val retrySize: Int,
    val failedCount: Int,
    val successCount: Int,
    val retrySuccessCount: Int,   // messages that succeeded after being in the retry queue
    val totalEnqueued: Int,
    val inFlightCount: Int
) {
    val remaining = primarySize + retrySize + inFlightCount
    val processed = failedCount + successCount
}

/**
 * Thread-safe manager for emergency message queues.
 * Exposes queueStatusFlow: StateFlow<QueueStatus> so observers can react to state
 * changes without polling loops (per AGENTS.md: no polling, use StateFlow collection).
 */
class MessageQueueManager {
    private val TAG = "MessageQueueManager"
    private val mutex = Mutex()
    private val primaryQueue: Queue<MessageTask> = LinkedList()
    private val retryQueue: Queue<MessageTask> = LinkedList()
    private val failedQueue: MutableList<MessageTask> = mutableListOf()
    private var successCount = 0
    private var retrySuccessCount = 0
    private var totalEnqueued = 0
    private var inFlightCount = 0

    // Live queue status StateFlow
    private val _queueStatus = MutableStateFlow(QueueStatus(0, 0, 0, 0, 0, 0, 0))
    val queueStatusFlow: StateFlow<QueueStatus> = _queueStatus.asStateFlow()

    /** Snapshot current state into the flow - must be called inside mutex. */
    private fun emitStatus() {
        _queueStatus.value = QueueStatus(
            primaryQueue.size, retryQueue.size, failedQueue.size,
            successCount, retrySuccessCount, totalEnqueued, inFlightCount
        )
    }

    // Live per-message status
    private val _currentMessageStatus = MutableStateFlow<MessageStatus?>(null)
    val currentMessageStatus: StateFlow<MessageStatus?> = _currentMessageStatus.asStateFlow()

    /** Update the live per-message status (thread-safe, non-suspend). */
    fun updateCurrentMessageStatus(status: MessageStatus?) {
        _currentMessageStatus.value = status
    }

    // Queue operations
    suspend fun enqueueScenario(recipients: List<Recipient>, message: String, scenarioId: String) {
        mutex.withLock {
            if (recipients.isEmpty()) {
                Log.w(TAG, "Attempted to enqueue scenario $scenarioId with NO recipients.")
                return
            }
            Log.i(TAG, "Enqueuing $scenarioId: adding ${recipients.size} recipients to primary queue.")
            recipients.forEach { recipient ->
                primaryQueue.add(MessageTask(recipient, message, scenarioId))
            }
            totalEnqueued += recipients.size
            emitStatus()
        }
    }

    suspend fun nextTask(): MessageTask? = mutex.withLock {
        val task = primaryQueue.poll() ?: retryQueue.poll()
        if (task != null) {
            inFlightCount++
            Log.d(TAG, "Polling next task for ${task.recipient.phoneNumber}. In-flight: $inFlightCount")
            emitStatus()
        }
        task
    }

    /** Polls only from the retry queue (used exclusively by LazarusRetrySystem). */
    suspend fun nextRetryTask(): MessageTask? = mutex.withLock {
        val task = retryQueue.poll()
        if (task != null) {
            inFlightCount++
            Log.d(TAG, "Lazarus polling retry task for ${task.recipient.phoneNumber}. In-flight: $inFlightCount")
            emitStatus()
        }
        task
    }

    suspend fun handleFailure(task: MessageTask) {
        mutex.withLock {
            inFlightCount = (inFlightCount - 1).coerceAtLeast(0)
            val updated = task.copy(
                retryCount = task.retryCount + 1,
                lastAttemptAt = System.currentTimeMillis()
            )
            if (updated.retryCount <= 5) {
                Log.w(TAG, "Task for ${task.recipient.phoneNumber} failed (attempt ${updated.retryCount}). Moving to retry queue.")
                retryQueue.add(updated)
            } else {
                Log.e(TAG, "Task for ${task.recipient.phoneNumber} failed definitively after 5 retries.")
                failedQueue.add(updated)
            }
            emitStatus()
        }
    }

    /**
     * Re-queues a failed task at the end of the retry queue without incrementing retryCount
     * or enforcing limits. Lazarus cycles messages indefinitely until they succeed.
     */
    suspend fun requeueForLazarus(task: MessageTask) = mutex.withLock {
        inFlightCount = (inFlightCount - 1).coerceAtLeast(0)
        retryQueue.add(task.copy(lastAttemptAt = System.currentTimeMillis()))
        Log.d(TAG, "Lazarus re-queued ${task.recipient.phoneNumber}. Retry queue size: ${retryQueue.size}")
        emitStatus()
    }

    suspend fun markSuccess() {
        mutex.withLock {
            inFlightCount = (inFlightCount - 1).coerceAtLeast(0)
            successCount++
            Log.i(TAG, "Task marked as success. Total success: $successCount, In-flight: $inFlightCount")
            emitStatus()
        }
    }

    /** Called by LazarusRetrySystem when a retried message eventually succeeds. */
    suspend fun noteRetrySucceeded() = mutex.withLock {
        retrySuccessCount++
        Log.i(TAG, "Retry success noted. Total retry successes: $retrySuccessCount")
        emitStatus()
    }

    suspend fun clearQueue() {
        mutex.withLock {
            Log.i(TAG, "Clearing all queues.")
            primaryQueue.clear()
            retryQueue.clear()
            failedQueue.clear()
            successCount = 0
            retrySuccessCount = 0
            totalEnqueued = 0
            inFlightCount = 0
            emitStatus()
        }
        updateCurrentMessageStatus(null)
    }

    suspend fun clearStats() {
        mutex.withLock {
            Log.d(TAG, "Clearing success/failure stats.")
            successCount = 0
            retrySuccessCount = 0
            failedQueue.clear()
            totalEnqueued = primaryQueue.size + retryQueue.size + inFlightCount
            emitStatus()
        }
    }

    suspend fun hardReset() = mutex.withLock {
        Log.i(TAG, "Hard reset: clearing all queues and counters.")
        primaryQueue.clear()
        retryQueue.clear()
        failedQueue.clear()
        inFlightCount = 0
        successCount = 0
        retrySuccessCount = 0
        totalEnqueued = 0
        _currentMessageStatus.value = null
        emitStatus()
    }

    suspend fun hasRetries(): Boolean = mutex.withLock {
        retryQueue.isNotEmpty()
    }

    suspend fun isEmpty(): Boolean = mutex.withLock {
        primaryQueue.isEmpty() && retryQueue.isEmpty() && inFlightCount == 0
    }

    suspend fun getDetailedStatus(): QueueStatus = mutex.withLock {
        QueueStatus(
            primaryQueue.size,
            retryQueue.size,
            failedQueue.size,
            successCount,
            retrySuccessCount,
            totalEnqueued,
            inFlightCount
        )
    }
}
