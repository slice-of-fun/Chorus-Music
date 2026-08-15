package pushkar.chorus.music.spotifyimport.parsers

import pushkar.chorus.music.spotify.Spotify
import pushkar.chorus.music.spotify.SpotifyAuth

class SpotifyLinkParser : UniversalLinkParser {
    private val PLAYLIST_REFERENCE_REGEX = Regex("""playlist[/:]([A-Za-z0-9]+)""")
    private val BARE_ID_REGEX = Regex("""[A-Za-z0-9]{16,}""")

    override suspend fun supports(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        if (PLAYLIST_REFERENCE_REGEX.find(trimmed) != null) return true
        if (trimmed.matches(BARE_ID_REGEX)) return true
        return false
    }

    override suspend fun parse(url: String): UniversalParsedPlaylist {
        val trimmed = url.trim()
        val playlistId = PLAYLIST_REFERENCE_REGEX.find(trimmed)?.groupValues?.get(1)
            ?: if (trimmed.matches(BARE_ID_REGEX)) trimmed else throw IllegalArgumentException("Invalid Spotify playlist link")

        val token = SpotifyAuth.fetchAnonymousToken().getOrThrow().accessToken
        val playlist = Spotify.playlist(playlistId, tokenOverride = token).getOrThrow()
        val limit = 100
        val allTracks = mutableListOf<UniversalParsedTrack>()
        var offset = 0
        while (true) {
            val page = Spotify.playlistTracks(playlistId, limit = limit, offset = offset, tokenOverride = token).getOrThrow()
            
            page.items.forEach { item ->
                val track = item.track
                if (track != null) {
                    val artists = track.artists.joinToString(", ") { it.name }
                    allTracks.add(
                        UniversalParsedTrack(
                            title = track.name,
                            artist = artists,
                            durationMs = track.durationMs?.toLong()
                        )
                    )
                }
            }

            if (page.items.size < limit) break
            offset += limit
        }

        return UniversalParsedPlaylist(
            id = playlistId,
            title = playlist.name,
            subtitle = playlist.owner?.displayName ?: "Spotify",
            trackCount = allTracks.size,
            thumbnailUrl = playlist.images.firstOrNull()?.url,
            serviceUrl = "https://open.spotify.com/playlist/$playlistId",
            tracks = allTracks
        )
    }
}
