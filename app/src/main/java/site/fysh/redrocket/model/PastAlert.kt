package site.fysh.redrocket.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a record of every message that triggered the alert system.
 */
@Entity(tableName = "past_alerts")
data class PastAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageContent: String,
    val triggeredAt: Long = System.currentTimeMillis(),
    // "alert"          known WEA/EAS package via the notification listener
    // "notification"    emergency-plausible content from a package outside the
    //                   detector list; logged even when it does not trigger
    // "cell_broadcast"  cell broadcast receiver
    val source: String = "",
    val scenariosTriggered: String = "" // comma-separated scenario names
)
