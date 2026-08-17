

package com.music.chorus.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pushkar.chorus.music.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

object DiscordAssetRegistrar {
    private const val TAG = "DiscordAssetRegistrar"
    private const val API_BASE = "https://discord.com/api/v10"

    private val client = OkHttpClient()
    private val cache = ConcurrentHashMap<String, String>()
    private val mutex = Mutex()

    suspend fun resolveImage(
        accessToken: String,
        imageUrl: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (imageUrl == null) return@withContext null

            val parsed = parseImageType(imageUrl)
            return@withContext parsed.value
        }

    suspend fun resolveImages(
        accessToken: String,
        largeImage: String?,
        smallImage: String?,
    ): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            val largeType = largeImage?.let { parseImageType(it) }
            val smallType = smallImage?.let { parseImageType(it) }

            val resolvedLarge = largeType?.value
            val resolvedSmall = smallType?.value

            resolvedLarge to resolvedSmall
        }

    fun clearCache() {
        cache.clear()
    }

    private sealed class ImageType {
        abstract val value: String

        data class Snowflake(
            override val value: String,
        ) : ImageType()

        data class MpPrefix(
            override val value: String,
        ) : ImageType()

        data class DiscordCdn(
            override val value: String,
        ) : ImageType()

        data class ExternalUrl(
            override val value: String,
        ) : ImageType()

        data class Raw(
            override val value: String,
        ) : ImageType()
    }

    private fun parseImageType(image: String): ImageType {
        if (Regex("^[0-9]{17,19}$").matches(image)) {
            return ImageType.Snowflake(image)
        }
        if (listOf("mp:", "youtube:", "spotify:", "twitch:").any { image.startsWith(it) }) {
            return ImageType.MpPrefix(image)
        }
        if (image.startsWith("external/")) {
            return ImageType.MpPrefix("mp:$image")
        }
        val isValidUrl =
            try {
                val uri = URI(image)
                uri.scheme == "http" || uri.scheme == "https"
            } catch (_: Exception) {
                false
            }
        if (!isValidUrl) {
            return ImageType.Raw(image)
        }
        val isDiscordCdn =
            listOf(
                "https://cdn.discordapp.com/",
                "http://cdn.discordapp.com/",
                "https://media.discordapp.net/",
                "http://media.discordapp.net/",
            ).any { image.startsWith(it) }

        if (isDiscordCdn) {
            var result =
                image
                    .replace("https://cdn.discordapp.com/", "mp:")
                    .replace("http://cdn.discordapp.com/", "mp:")
                    .replace("https://media.discordapp.net/", "mp:")
                    .replace("http://media.discordapp.net/", "mp:")
            return ImageType.DiscordCdn(result)
        }
        return ImageType.ExternalUrl(image)
    }

}
