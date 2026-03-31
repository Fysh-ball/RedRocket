package site.fysh.redrocket.model

import java.util.UUID

/**
 * A group within a Scenario. Each group has its own recipients and message.
 * Triggers are defined at the Scenario level and are shared across all groups.
 */
data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val recipients: List<Recipient> = emptyList(),
    val message: String = "",
    val isFavorite: Boolean = false
)
