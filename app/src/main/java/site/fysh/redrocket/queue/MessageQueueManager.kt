package site.fysh.redrocket.queue

import android.util.Log
import site.fysh.redrocket.model.PendingMessage
import site.fysh.redrocket.util.maskPhone
import site.fysh.redrocket.model.PendingMessageDao
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
    val lastAttemptAt: Long = 0,
    val persistenceId: Long = 0
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
class MessageQueueManager(private val pendingDao: PendingMessageDao? = null) {
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
        if (recipients.isEmpty()) {
            Log.w(TAG, "Attempted to enqueue scenario $scenarioId with NO recipients.")
            return
        }

        val validRecipients = recipients.filter { it.isValid() }
        if (validRecipients.isEmpty()) {
            Log.w(TAG, "Attempted to enqueue scenario $scenarioId but all recipients are invalid.")
            return
        }
        if (validRecipients.size < recipients.size) {
            Log.w(TAG, "Filtered ${recipients.size - validRecipients.size} invalid recipient(s) from scenario $scenarioId")
        }

        // Persist to Room outside the mutex to avoid holding the lock during I/O
        val dbIds = validRecipients.map { recipient ->
            try {
                pendingDao?.insert(
                    PendingMessage(
                        recipientName = recipient.name,
                        recipientPhone = recipient.phoneNumber,
                        message = message,
                        scenarioId = scenarioId
                    )
                ) ?: 0L
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist pending message for ${maskPhone(recipient.phoneNumber)}", e)
                0L
            }
        }

        mutex.withLock {
            Log.i(TAG, "Enqueuing $scenarioId: adding ${validRecipients.size} recipients to primary queue.")
            validRecipients.forEachIndexed { index, recipient ->
                primaryQueue.add(MessageTask(recipient, message, scenarioId, persistenceId = dbIds[index]))
            }
            totalEnqueued += validRecipients.size
            emitStatus()
        }
    }

    suspend fun nextTask(): MessageTask? = mutex.withLock {
        // Only poll from the primary queue. The retry queue is managed exclusively by
        // LazarusRetrySystem via nextRetryTask(). Falling back to retryQueue here would
        // allow a race (status.primarySize snapshot vs actual poll) to bypass the Lazarus
        // inter-pass delay and "Keep Trying" logic.
        val task = primaryQueue.poll()
        if (task != null) {
            inFlightCount++
            Log.d(TAG, "Polling next task for ${maskPhone(task.recipient.phoneNumber)}. In-flight: $inFlightCount")
            emitStatus()
        }
        task
    }

    /** Polls only from the retry queue (used exclusively by LazarusRetrySystem). */
    suspend fun nextRetryTask(): MessageTask? = mutex.withLock {
        val task = retryQueue.poll()
        if (task != null) {
            inFlightCount++
            Log.d(TAG, "Lazarus polling retry task for ${maskPhone(task.recipient.phoneNumber)}. In-flight: $inFlightCount")
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
                Log.w(TAG, "Task for ${maskPhone(task.recipient.phoneNumber)} failed (attempt ${updated.retryCount}). Moving to retry queue.")
                retryQueue.add(updated)
            } else {
                Log.e(TAG, "Task for ${maskPhone(task.recipient.phoneNumber)} failed definitively after 5 retries.")
                failedQueue.add(updated)
            }
            emitStatus()
        }
        if (task.persistenceId != 0L) {
            try {
                val updated = task.copy(retryCount = task.retryCount + 1)
                if (updated.retryCount <= 5) {
                    pendingDao?.markRetry(task.persistenceId)
                } else {
                    pendingDao?.markFailed(task.persistenceId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update persisted message id=${task.persistenceId}", e)
            }
        }
    }

    /**
     * Re-queues a failed task at the end of the retry queue without incrementing retryCount
     * or enforcing limits. Lazarus cycles messages indefinitely until they succeed.
     */
    suspend fun requeueForLazarus(task: MessageTask) = mutex.withLock {
        inFlightCount = (inFlightCount - 1).coerceAtLeast(0)
        retryQueue.add(task.copy(lastAttemptAt = System.currentTimeMillis()))
        Log.d(TAG, "Lazarus re-queued ${maskPhone(task.recipient.phoneNumber)}. Retry queue size: ${retryQueue.size}")
        emitStatus()
    }

    suspend fun markSuccess(task: MessageTask? = null) {
        mutex.withLock {
            inFlightCount = (inFlightCount - 1).coerceAtLeast(0)
            successCount++
            Log.i(TAG, "Task marked as success. Total success: $successCount, In-flight: $inFlightCount")
            emitStatus()
        }
        if (task != null && task.persistenceId != 0L) {
            try {
                pendingDao?.delete(task.persistenceId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete persisted message id=${task.persistenceId}", e)
            }
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
        try {
            pendingDao?.deleteAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear persisted messages", e)
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

    suspend fun hardReset() {
        mutex.withLock {
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
        try {
            pendingDao?.deleteAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear persisted messages on hard reset", e)
        }
    }

    /**
     * Moves all remaining retry-queue items into the failed queue.
     * Called by LazarusRetrySystem when it exits early (keepTrying=OFF or circuit breaker)
     * so the service's processQueue loop does not re-enter Lazarus.
     */
    suspend fun moveRetriesToFailed() {
        mutex.withLock {
            if (retryQueue.isEmpty()) return
            val count = retryQueue.size
            failedQueue.addAll(retryQueue)
            retryQueue.clear()
            Log.w(TAG, "Moved $count retry item(s) to failed queue.")
            emitStatus()
        }
        try {
            pendingDao?.moveAllRetriesToFailed()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move persisted retries to failed", e)
        }
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

    /**
     * Restores pending and retry messages from Room into the in-memory queues.
     * Called on service restart after process death to recover any unsent messages.
     */
    suspend fun restoreFromDisk() {
        val dao = pendingDao ?: return
        try {
            val pending = dao.getPending()
            val retries = dao.getRetries()
            if (pending.isEmpty() && retries.isEmpty()) return

            mutex.withLock {
                pending.forEach { pm ->
                    primaryQueue.add(
                        MessageTask(
                            recipient = Recipient(pm.recipientName, pm.recipientPhone),
                            message = pm.message,
                            scenarioId = pm.scenarioId,
                            retryCount = pm.retryCount,
                            persistenceId = pm.id
                        )
                    )
                }
                retries.forEach { pm ->
                    retryQueue.add(
                        MessageTask(
                            recipient = Recipient(pm.recipientName, pm.recipientPhone),
                            message = pm.message,
                            scenarioId = pm.scenarioId,
                            retryCount = pm.retryCount,
                            persistenceId = pm.id
                        )
                    )
                }
                totalEnqueued += pending.size + retries.size
                emitStatus()
            }
            Log.i(TAG, "Restored ${pending.size} pending + ${retries.size} retry messages from disk.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore messages from disk", e)
        }
    }
}
