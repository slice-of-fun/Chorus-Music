package pushkar.chorus.music.spotifyimport.parsers

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class SoundCloudLinkParser : UniversalLinkParser {
    override suspend fun supports(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host.contains("soundcloud.com") && url.contains("/sets/")
    }

    override suspend fun parse(url: String): UniversalParsedPlaylist = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
            .get()

        val title = doc.title().substringBefore(" by").trim()
        val thumbnailUrl = doc.select("meta[property=og:image]").attr("content")
        val playlistId = url.substringAfterLast("/")

        val tracks = mutableListOf<UniversalParsedTrack>()
        
        val trackElements = doc.select("a[itemprop=url]")
        for (elem in trackElements) {
            val trackName = elem.text()
            if (trackName.isNotBlank() && !tracks.any { it.title == trackName }) {
                tracks.add(UniversalParsedTrack(title = trackName, artist = "SoundCloud"))
            }
        }
        
        if (tracks.isEmpty()) {
            val html = doc.html()
            val matches = Regex("""itemprop="url"[^>]*>\s*([^<]+)\s*</a>""").findAll(html)
            for (match in matches) {
                val trackName = match.groupValues[1].trim()
                if (trackName.isNotBlank() && !tracks.any { it.title == trackName }) {
                    tracks.add(UniversalParsedTrack(title = trackName, artist = "SoundCloud"))
                }
            }
        }
        if (tracks.isEmpty()) {
            val html = doc.html()
            val hydrationMatch = Regex("""window\.__sc_hydration\s*=\s*(\[.*?\]);</script>""").find(html)
            if (hydrationMatch != null) {
                val rawJson = hydrationMatch.groupValues[1]
                val titleMatches = Regex(""""title":"([^"]+)"""").findAll(rawJson)
                for (match in titleMatches) {
                    val trackName = match.groupValues[1].trim()
                    if (trackName.isNotBlank() && !tracks.any { it.title == trackName }) {
                        tracks.add(UniversalParsedTrack(title = trackName, artist = "SoundCloud"))
                    }
                }
            }
        }
        
        if (tracks.isEmpty()) {
            throw IllegalArgumentException("Could not extract tracks from SoundCloud playlist.")
        }

        UniversalParsedPlaylist(
            id = playlistId,
            title = title,
            subtitle = "SoundCloud",
            trackCount = tracks.size,
            thumbnailUrl = thumbnailUrl,
            serviceUrl = url,
            tracks = tracks
        )
    }
}
