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

    @Query("SELECT * FROM past_alerts ORDER BY triggeredAt DESC LIMIT 1000")
    fun getAllAlerts(): Flow<List<PastAlert>>

    @Query("SELECT * FROM past_alerts ORDER BY triggeredAt DESC LIMIT 1000")
    suspend fun getAllAlertsOnce(): List<PastAlert>

    @Query("DELETE FROM past_alerts")
    suspend fun clearAll()

    /**
     * Bounds the alert log so widening what gets logged cannot bury real alerts.
     *
     * SAFETY: only rows with an EMPTY scenariosTriggered are reachable by this
     * delete. Any non-empty value is structurally immune, not merely retained
     * longer: there is no age or count at which a row that fired a send becomes
     * eligible. That deliberately includes the "[scenario load timed out]"
     * marker, which records a DB failure during a live emergency and is worth
     * more as evidence than the space it costs.
     *
     * Deliberately no string parsing of scenariosTriggered. A predicate like
     * "does not start with a bracket" would make a safety decision depend on a
     * string pattern, and would silently change meaning the day a scenario is
     * named with a leading bracket. Over-retaining is the correct failure
     * direction for a safety log.
     *
     * Age-out rather than count-out, because emergency review happens on human
     * timescales: "did my phone see that alert last month" must be answerable.
     * The floor keeps a quiet phone from emptying its own history.
     *
     * NOTE: this is a write-side bound. It cannot by itself satisfy rule 3's
     * "entries must never disappear from the UI", which is a read-side property
     * of the LIMIT 1000 queries above. That is tracked separately.
     */
    @Query(
        """
        DELETE FROM past_alerts
        WHERE scenariosTriggered = ''
          AND triggeredAt < :cutoffMillis
          AND id NOT IN (
            SELECT id FROM past_alerts
            WHERE scenariosTriggered = ''
            ORDER BY triggeredAt DESC LIMIT :keepNewest
          )
        """
    )
    suspend fun pruneUntriggered(cutoffMillis: Long, keepNewest: Int): Int
}
