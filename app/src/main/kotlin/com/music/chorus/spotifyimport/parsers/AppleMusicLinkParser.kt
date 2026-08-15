package pushkar.chorus.music.spotifyimport.parsers

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONArray

class AppleMusicLinkParser : UniversalLinkParser {
    override suspend fun supports(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host.contains("music.apple.com")
    }

    override suspend fun parse(url: String): UniversalParsedPlaylist = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()

        val title = doc.title().substringBefore(" by").trim()
        val thumbnailUrl = doc.select("meta[property=og:image]").attr("content")
        val playlistId = url.substringAfterLast("/")

        val tracks = mutableListOf<UniversalParsedTrack>()
        
        // Try to parse JSON-LD
        val jsonLdElements = doc.select("script[type=application/ld+json]")
        for (elem in jsonLdElements) {
            try {
                val json = JSONObject(elem.data())
                if (json.optString("@type") == "MusicPlaylist") {
                    val trackArray = json.optJSONArray("track")
                    if (trackArray != null) {
                        for (i in 0 until trackArray.length()) {
                            val trackObj = trackArray.optJSONObject(i) ?: continue
                            val trackName = trackObj.optString("name")
                            
                            val byArtistObj = trackObj.optJSONObject("byArtist")
                            var artistName = byArtistObj?.optString("name")
                            if (artistName.isNullOrBlank()) {
                                val byArtistArr = trackObj.optJSONArray("byArtist")
                                if (byArtistArr != null && byArtistArr.length() > 0) {
                                    artistName = byArtistArr.optJSONObject(0)?.optString("name")
                                }
                            }
                            
                            if (trackName.isNotBlank()) {
                                tracks.add(
                                    UniversalParsedTrack(
                                        title = trackName,
                                        artist = artistName ?: "Unknown Artist"
                                    )
                                )
                            }
                        }
                    }
                    break
                }
            } catch (e: Exception) {
                // Ignore parsing errors for individual blocks
            }
        }

        // Fallback: If JSON-LD didn't work, try parsing HTML tracklist
        if (tracks.isEmpty()) {
            val songRows = doc.select(".songs-list-row")
            for (row in songRows) {
                val trackName = row.select(".songs-list-row__song-name").text()
                val artistName = row.select(".songs-list-row__by-line").text()
                if (trackName.isNotBlank()) {
                    tracks.add(UniversalParsedTrack(title = trackName, artist = artistName))
                }
            }
        }
        
        if (tracks.isEmpty()) {
            throw IllegalArgumentException("Could not extract tracks from Apple Music playlist. Ensure the playlist is public.")
        }

        UniversalParsedPlaylist(
            id = playlistId,
            title = title,
            subtitle = "Apple Music",
            trackCount = tracks.size,
            thumbnailUrl = thumbnailUrl,
            serviceUrl = url,
            tracks = tracks
        )
    }
}
