package pushkar.chorus.music.spotifyimport.parsers

data class UniversalParsedPlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val trackCount: Int?,
    val thumbnailUrl: String?,
    val serviceUrl: String,
    val tracks: List<UniversalParsedTrack>
)

data class UniversalParsedTrack(
    val title: String,
    val artist: String,
    val durationMs: Long? = null
)

interface UniversalLinkParser {
    suspend fun supports(url: String): Boolean
    suspend fun parse(url: String): UniversalParsedPlaylist
}
