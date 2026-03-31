package site.fysh.redrocket.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a response from a contact for a specific scenario.
 * The unique index on (scenarioId, phoneNumber) ensures only the latest
 * response per contact per scenario is kept — preventing duplicates.
 */
@Entity(
    tableName = "response_records",
    indices = [Index(value = ["scenarioId", "phoneNumber"], unique = true)]
)
data class ResponseRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scenarioId: String,
    val phoneNumber: String,
    val recipientName: String,
    val responseCode: Int,
    val responseText: String,
    val receivedAt: Long = System.currentTimeMillis()
)
