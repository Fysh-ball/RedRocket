package site.fysh.redrocket.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a specific emergency messaging configuration.
 * Triggers are global per scenario; recipients and messages are per-Group.
 */
@Entity(tableName = "scenarios")
data class Scenario(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "", // Comma-separated trigger keywords
    val message: String = "",     // Legacy - kept for DB compat; use groups instead
    val recipients: List<Recipient> = emptyList(), // Legacy - kept for DB compat; use groups instead
    val groups: List<Group> = emptyList(),
    val isLocked: Boolean = false,
    val isFavorite: Boolean = false,
    val orderIndex: Int = 0,
    val lastModified: Long = System.currentTimeMillis()
) {
    /**
     * Scenario is valid if it has at least one group with both recipients and a message.
     */
    fun isValid(): Boolean {
        return name.isNotBlank() && groups.any { it.recipients.isNotEmpty() && it.message.isNotBlank() }
    }

    /**
     * Returns all recipients across all groups (for dashboard, response tracking, etc.).
     */
    fun allRecipients(): List<Recipient> = groups.flatMap { it.recipients }
}
