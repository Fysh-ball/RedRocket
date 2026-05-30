package site.fysh.redrocket.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "block_phrases",
    indices = [Index(value = ["phrase"], unique = true)]
)
data class BlockPhrase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phrase: String
)
