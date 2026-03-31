package site.fysh.redrocket.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT 500")
    fun getRecentLogs(): Flow<List<LogEntry>>

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT 500")
    suspend fun getAllLogsOnce(): List<LogEntry>

    @Insert
    suspend fun insertLog(entry: LogEntry)

    @Query("DELETE FROM app_logs WHERE id NOT IN (SELECT id FROM app_logs ORDER BY timestamp DESC LIMIT 500)")
    suspend fun pruneOldLogs()

    @Query("DELETE FROM app_logs")
    suspend fun clearAll()
}
