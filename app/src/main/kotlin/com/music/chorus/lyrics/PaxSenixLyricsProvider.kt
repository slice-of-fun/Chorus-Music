

package pushkar.chorus.music.lyrics

import android.content.Context
import com.music.paxsenix.Paxsenix
import pushkar.chorus.music.constants.EnablePaxsenixKey
import pushkar.chorus.music.utils.dataStore
import pushkar.chorus.music.utils.get
import timber.log.Timber

object PaxSenixLyricsProvider : LyricsProvider {
    private const val TAG = "PaxSenixProvider"

    override val name = "Paxsenix"

    override fun isEnabled(context: Context): Boolean {
        
        val enabled = context.dataStore[EnablePaxsenixKey] ?: true
        if (enabled) {
            Paxsenix.init(context)
        }
        return enabled
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        Timber.tag(TAG).d("getLyrics: title='$title', artist='$artist', duration=$duration")
        return try {
            Paxsenix.getLyrics(title, artist, duration, album)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception in getLyrics")
            Result.failure(e)
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Timber.tag(TAG).d("getAllLyrics called")
        try {
            Paxsenix.getAllLyrics(title, artist, duration, album, callback)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching lyrics from Paxsenix")
        }
    }
}
