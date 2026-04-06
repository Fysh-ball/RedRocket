package site.fysh.redrocket.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks global per-contact send history.
 * Persists even if the scenario is deleted - prevents spam bypass via scenario recreation.
 * The normalized phone number is the primary key (last 10 digits).
 */
@Entity(tableName = "contact_send_history")
data class ContactSendHistory(
    @PrimaryKey
    val normalizedPhone: String,
    val sendCount: Int = 0,
    val lastSentAt: Long = System.currentTimeMillis(),
    val firstSentAt: Long = System.currentTimeMillis()
)
