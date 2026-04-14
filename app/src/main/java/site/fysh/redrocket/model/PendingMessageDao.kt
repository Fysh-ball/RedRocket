package site.fysh.redrocket.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingMessageDao {
    @Insert
    suspend fun insert(msg: PendingMessage): Long

    @Insert
    suspend fun insertAll(msgs: List<PendingMessage>)

    @Query("SELECT * FROM pending_messages WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<PendingMessage>

    @Query("SELECT * FROM pending_messages WHERE status = 'RETRY' ORDER BY createdAt ASC")
    suspend fun getRetries(): List<PendingMessage>

    @Query("UPDATE pending_messages SET status = 'RETRY', retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markRetry(id: Long)

    @Query("UPDATE pending_messages SET status = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: Long)

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_messages")
    suspend fun deleteAll()

    @Query("UPDATE pending_messages SET status = 'FAILED' WHERE status = 'RETRY'")
    suspend fun moveAllRetriesToFailed()
}
