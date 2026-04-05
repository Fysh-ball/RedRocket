package site.fysh.redrocket.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_phrases")
data class BlockPhrase(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phrase: String
)
