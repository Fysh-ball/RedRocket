package site.fysh.redrocket.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios ORDER BY orderIndex ASC, lastModified DESC")
    fun getAllScenarios(): Flow<List<Scenario>>

    @Query("SELECT * FROM scenarios ORDER BY orderIndex ASC")
    suspend fun getAllScenariosOnce(): List<Scenario>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun getScenarioById(id: String): Scenario?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenario(scenario: Scenario)

    /**
     * Atomically locks a scenario only if it is currently unlocked.
     * Returns the number of rows updated (1 = locked successfully, 0 = already locked).
     * Use this instead of insertScenario(copy(isLocked=true)) whenever two trigger paths
     * (BroadcastReceiver + NotificationListener) could fire concurrently for the same scenario.
     */
    @Query("UPDATE scenarios SET isLocked = 1 WHERE id = :id AND isLocked = 0")
    suspend fun lockIfUnlocked(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenarios(scenarios: List<Scenario>)

    @Delete
    suspend fun deleteScenario(scenario: Scenario)

    @Query("DELETE FROM scenarios WHERE id IN (:ids)")
    suspend fun deleteScenariosByIds(ids: List<String>)
}
