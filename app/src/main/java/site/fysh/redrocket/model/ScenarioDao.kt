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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenarios(scenarios: List<Scenario>)

    @Delete
    suspend fun deleteScenario(scenario: Scenario)

    @Query("DELETE FROM scenarios WHERE id IN (:ids)")
    suspend fun deleteScenariosByIds(ids: List<String>)
}
