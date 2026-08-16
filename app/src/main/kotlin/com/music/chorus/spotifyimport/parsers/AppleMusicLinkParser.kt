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
                val trackArray = json.optJSONArray("track") ?: json.optJSONObject("tracks")?.optJSONArray("itemListElement")
                
                if (trackArray != null) {
                    for (i in 0 until trackArray.length()) {
                        val trackObj = trackArray.optJSONObject(i) ?: continue
                        val itemObj = trackObj.optJSONObject("item") ?: trackObj
                        val trackName = itemObj.optString("name")
                        
                        var artistName = "Unknown Artist"
                        val byArtistObj = itemObj.optJSONObject("byArtist")
                        if (byArtistObj != null) {
                            artistName = byArtistObj.optString("name")
                        } else {
                            val byArtistArr = itemObj.optJSONArray("byArtist")
                            if (byArtistArr != null && byArtistArr.length() > 0) {
                                artistName = byArtistArr.optJSONObject(0)?.optString("name") ?: "Unknown Artist"
                            }
                        }
                        
                        if (trackName.isNotBlank()) {
                            tracks.add(
                                UniversalParsedTrack(
                                    title = trackName,
                                    artist = artistName
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }

        if (tracks.isEmpty()) {
            val html = doc.html()
            val serverDataMatch = Regex("""<script type="application/json" id="serialized-server-data">(.*?)</script>""").find(html)
            if (serverDataMatch != null) {
                try {
                    val serverData = JSONArray(serverDataMatch.groupValues[1])
                    val rawJson = serverData.toString()
                    val titleMatches = Regex(""""title":"([^"]+)"""").findAll(rawJson)
                    val artistMatches = Regex(""""artistName":"([^"]+)"""").findAll(rawJson)
                    val titles = titleMatches.map { it.groupValues[1] }.toList()
                    val artists = artistMatches.map { it.groupValues[1] }.toList()
                    
                    for (i in 0 until minOf(titles.size, artists.size)) {
                        tracks.add(UniversalParsedTrack(title = titles[i], artist = artists[i]))
                    }
                } catch (e: Exception) {}
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
