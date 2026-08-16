package pushkar.chorus.music.utils

import kotlinx.serialization.Serializable

@Serializable
data class LosslessIndex(val items: List<LosslessTrack> = emptyList())

@Serializable
data class LosslessTrack(val song: String, val artist: String, val url: String)

@Serializable
data class DonationGoal(val current: Int = 0, val target: Int = 100)

object LosslessAPI {
    suspend fun getRecentTracks(limit: Int = 10): List<LosslessTrack> = emptyList()
    suspend fun getTotalTracksCount(): Int = 0
    suspend fun search(queryTitle: String, queryArtist: String): LosslessTrack? = null
    suspend fun getDonationGoal(): DonationGoal = DonationGoal()
}
