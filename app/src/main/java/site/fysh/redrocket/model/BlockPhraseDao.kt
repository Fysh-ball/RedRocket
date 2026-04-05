package site.fysh.redrocket.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockPhraseDao {
    @Query("SELECT * FROM block_phrases ORDER BY phrase ASC")
    fun getAll(): Flow<List<BlockPhrase>>

    @Query("SELECT * FROM block_phrases ORDER BY phrase ASC")
    suspend fun getAllOnce(): List<BlockPhrase>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(phrase: BlockPhrase)

    @Delete
    suspend fun delete(phrase: BlockPhrase)

    @Query("DELETE FROM block_phrases")
    suspend fun deleteAll()
}
