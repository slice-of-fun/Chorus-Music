package pushkar.chorus.music.spotifyimport.parsers

import com.music.innertube.YouTube
import com.music.innertube.utils.completed
import android.net.Uri

class YouTubeLinkParser : UniversalLinkParser {
    override suspend fun supports(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host.contains("youtube.com") || host.contains("youtu.be")
    }

    override suspend fun parse(url: String): UniversalParsedPlaylist {
        val uri = Uri.parse(url)
        val playlistId = uri.getQueryParameter("list")
            ?: throw IllegalArgumentException("Could not extract playlist ID from YouTube link")

        val playlistPage = YouTube.playlist(playlistId).getOrThrow()

        val tracks = playlistPage.songs.mapNotNull { item ->
            val title = item.title
            val artist = item.artists.joinToString(", ") { it.name }
            if (title.isNotBlank()) {
                UniversalParsedTrack(
                    title = title,
                    artist = artist,
                    durationMs = item.duration?.toLong()?.times(1000L) 
                )
            } else null
        }

        return UniversalParsedPlaylist(
            id = playlistId,
            title = playlistPage.playlist.title,
            subtitle = playlistPage.playlist.author?.name ?: "YouTube",
            trackCount = tracks.size,
            thumbnailUrl = playlistPage.playlist.thumbnail,
            serviceUrl = url,
            tracks = tracks
        )
    }
}
