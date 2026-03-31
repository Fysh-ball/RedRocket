package site.fysh.redrocket.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a record of every message that triggered the alert system.
 */
@Entity(tableName = "past_alerts")
data class PastAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val messageContent: String,
    val triggeredAt: Long = System.currentTimeMillis(),
    val source: String = "",            // "cell_broadcast", "notification", "manual"
    val scenariosTriggered: String = "" // comma-separated scenario names
)
