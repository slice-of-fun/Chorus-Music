package pushkar.chorus.music.models

import kotlinx.serialization.Serializable

@Serializable
data class SponsorBlockSegment(
    val segment: List<Float>,
    val UUID: String,
    val category: String,
    val actionType: String,
    val locked: Int,
    val votes: Int,
    val description: String
)
