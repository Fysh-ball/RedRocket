package site.fysh.redrocket.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PastAlertDao {
    @Insert
    suspend fun insertAlert(alert: PastAlert)

    /** Inserts an alert and returns the auto-generated row ID for subsequent updates. */
    @Insert
    suspend fun insertAlertAndGetId(alert: PastAlert): Long

    /** Back-fills the triggered scenario names after evaluation completes. */
    @Query("UPDATE past_alerts SET scenariosTriggered = :names WHERE id = :id")
    suspend fun updateScenariosTriggered(id: Long, names: String)

    @Query("SELECT * FROM past_alerts ORDER BY triggeredAt DESC")
    fun getAllAlerts(): Flow<List<PastAlert>>

    @Query("SELECT * FROM past_alerts ORDER BY triggeredAt DESC")
    suspend fun getAllAlertsOnce(): List<PastAlert>

    @Query("DELETE FROM past_alerts")
    suspend fun clearAll()
}
