package site.fysh.redrocket.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ResponseRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponse(record: ResponseRecord)

    @Query("SELECT * FROM response_records WHERE scenarioId = :scenarioId ORDER BY receivedAt DESC")
    fun getResponsesForScenario(scenarioId: String): Flow<List<ResponseRecord>>

    @Query("SELECT * FROM response_records WHERE scenarioId = :scenarioId ORDER BY receivedAt DESC")
    fun getLatestResponsePerRecipient(scenarioId: String): Flow<List<ResponseRecord>>

    @Query("SELECT * FROM response_records ORDER BY receivedAt DESC")
    fun getAllLatestResponses(): Flow<List<ResponseRecord>>

    @Query("DELETE FROM response_records WHERE scenarioId = :scenarioId")
    suspend fun clearResponsesForScenario(scenarioId: String)

    @Query("DELETE FROM response_records")
    suspend fun clearAllResponses()
}
