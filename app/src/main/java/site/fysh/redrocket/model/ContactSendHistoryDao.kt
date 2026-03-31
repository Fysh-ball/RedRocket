package site.fysh.redrocket.model

import androidx.room.*

@Dao
interface ContactSendHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: ContactSendHistory)

    @Query("SELECT * FROM contact_send_history WHERE normalizedPhone = :phone")
    suspend fun getByPhone(phone: String): ContactSendHistory?

    /**
     * Record a send for a contact. Increments count and updates lastSentAt.
     * If contact doesn't exist yet, creates a new entry.
     */
    @Transaction
    suspend fun recordSend(normalizedPhone: String) {
        val existing = getByPhone(normalizedPhone)
        if (existing != null) {
            upsert(existing.copy(
                sendCount = existing.sendCount + 1,
                lastSentAt = System.currentTimeMillis()
            ))
        } else {
            upsert(ContactSendHistory(
                normalizedPhone = normalizedPhone,
                sendCount = 1
            ))
        }
    }

}
