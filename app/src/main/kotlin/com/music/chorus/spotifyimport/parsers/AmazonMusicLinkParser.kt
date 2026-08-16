package pushkar.chorus.music.spotifyimport.parsers

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class AmazonMusicLinkParser : UniversalLinkParser {
    override suspend fun supports(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host.contains("music.amazon")
    }

    override suspend fun parse(url: String): UniversalParsedPlaylist = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()

        val title = doc.title().substringBefore(" |").trim()
        val thumbnailUrl = doc.select("meta[property=og:image]").attr("content")
        val playlistId = url.substringAfter("playlists/").substringBefore("?")

        val tracks = mutableListOf<UniversalParsedTrack>()
        val html = doc.html()
        val stateMatch = Regex("""<script type="application/json" id="dmusic-State">(.*?)</script>""").find(html)
        if (stateMatch != null) {
            try {
                val rawJson = stateMatch.groupValues[1]
                val titleMatches = Regex(""""title":"([^"]+)"""").findAll(rawJson)
                val artistMatches = Regex(""""artistName":"([^"]+)"""").findAll(rawJson)
                
                val titles = titleMatches.map { it.groupValues[1] }.toList()
                val artists = artistMatches.map { it.groupValues[1] }.toList()
                
                for (i in 0 until minOf(titles.size, artists.size)) {
                    val trackName = titles[i].trim()
                    if (trackName.isNotBlank() && !tracks.any { it.title == trackName }) {
                        tracks.add(UniversalParsedTrack(title = trackName, artist = artists[i].trim()))
                    }
                }
            } catch (e: Exception) {}
        }
        
        // Fallback generic json search
        if (tracks.isEmpty()) {
            val genericTitles = Regex(""""title":"([^"]+)"""").findAll(html)
            val genericArtists = Regex(""""artistName":"([^"]+)"""").findAll(html)
            val titles = genericTitles.map { it.groupValues[1] }.toList()
            val artists = genericArtists.map { it.groupValues[1] }.toList()
            
            for (i in 0 until minOf(titles.size, artists.size)) {
                val trackName = titles[i].trim()
                if (trackName.isNotBlank() && !tracks.any { it.title == trackName }) {
                    tracks.add(UniversalParsedTrack(title = trackName, artist = artists[i].trim()))
                }
            }
        }

        if (tracks.isEmpty()) {
            throw IllegalArgumentException("Could not extract tracks from Amazon Music playlist.")
        }

        UniversalParsedPlaylist(
            id = playlistId,
            title = title,
            subtitle = "Amazon Music",
            trackCount = tracks.size,
            thumbnailUrl = thumbnailUrl,
            serviceUrl = url,
            tracks = tracks
        )
    }
}
