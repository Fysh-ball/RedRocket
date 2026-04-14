package site.fysh.redrocket.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages")
data class PendingMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientName: String,
    val recipientPhone: String,
    val message: String,
    val scenarioId: String,
    val status: String = "PENDING",  // PENDING, RETRY, FAILED
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
