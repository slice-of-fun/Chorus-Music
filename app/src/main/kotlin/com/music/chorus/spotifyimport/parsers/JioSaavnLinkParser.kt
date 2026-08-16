package pushkar.chorus.music.spotifyimport.parsers

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class JioSaavnLinkParser : UniversalLinkParser {
    override suspend fun supports(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host.contains("jiosaavn.com")
    }

    override suspend fun parse(url: String): UniversalParsedPlaylist = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()

        val title = doc.title().substringBefore(" - ").trim()
        val thumbnailUrl = doc.select("meta[property=og:image]").attr("content")
        val playlistId = url.substringAfterLast("/")

        val tracks = mutableListOf<UniversalParsedTrack>()
        
        val songElements = doc.select(".song-wrap .title a")
        for (elem in songElements) {
            val trackName = elem.text()
            val artistElem = elem.parent()?.parent()?.select(".subtitle a")?.text() ?: "Unknown Artist"
            if (trackName.isNotBlank()) {
                tracks.add(UniversalParsedTrack(title = trackName, artist = artistElem))
            }
        }
        
        if (tracks.isEmpty()) {
            val listItems = doc.select("ol li .content")
            for (item in listItems) {
                val trackName = item.select("h4").text()
                val artistName = item.select("p").text()
                if (trackName.isNotBlank()) {
                    tracks.add(UniversalParsedTrack(title = trackName, artist = artistName))
                }
            }
        }
        
        if (tracks.isEmpty()) {
            val songLinks = doc.select("a[href*=/song/]")
            for (link in songLinks) {
                val trackName = link.text()
                if (trackName.isNotBlank() && trackName.length > 2 && !trackName.equals("Play", ignoreCase = true)) {
                    tracks.add(UniversalParsedTrack(title = trackName, artist = "Unknown Artist"))
                }
            }
        }
        
        val distinctTracks = tracks.distinctBy { it.title }.toMutableList()
        tracks.clear()
        tracks.addAll(distinctTracks)

        if (tracks.isEmpty()) {
            throw IllegalArgumentException("Could not extract tracks from JioSaavn playlist.")
        }

        UniversalParsedPlaylist(
            id = playlistId,
            title = title,
            subtitle = "JioSaavn",
            trackCount = tracks.size,
            thumbnailUrl = thumbnailUrl,
            serviceUrl = url,
            tracks = tracks
        )
    }
}
