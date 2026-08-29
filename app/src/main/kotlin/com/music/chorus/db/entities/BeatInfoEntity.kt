package pushkar.chorus.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beat_info")
data class BeatInfoEntity(
    @PrimaryKey val songId: String,
    val bpm: Float,
    val firstBeatOffsetMs: Long,
    val confidence: Float,
    val analyzedAt: Long = System.currentTimeMillis(),
    
    val mixInPointMs: Long? = null,
    
    val mixOutPointMs: Long? = null,
    
    val keyPitchClass: Int? = null,
    val keyIsMinor: Boolean? = null,
)
