package site.fysh.redrocket.model

import androidx.room.*

@Dao
interface ContactSendHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(history: ContactSendHistory)

    @Query("UPDATE contact_send_history SET sendCount = sendCount + 1, lastSentAt = :now WHERE normalizedPhone = :phone")
    suspend fun incrementCount(phone: String, now: Long)

    /**
     * Records a send for a contact atomically using insert-then-increment.
     * insertIfAbsent is a no-op on conflict, then incrementCount always fires —
     * safe under concurrent calls without a read-modify-write race.
     */
    @Transaction
    suspend fun recordSend(normalizedPhone: String) {
        val now = System.currentTimeMillis()
        insertIfAbsent(ContactSendHistory(normalizedPhone = normalizedPhone, sendCount = 0, lastSentAt = now))
        incrementCount(normalizedPhone, now)
    }

}
