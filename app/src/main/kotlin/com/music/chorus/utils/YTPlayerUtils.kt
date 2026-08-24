package pushkar.chorus.music.utils

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import pushkar.chorus.music.utils.BotDetectionMitigator
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import pushkar.chorus.music.constants.AudioQuality
import pushkar.chorus.music.utils.cipher.CipherDeobfuscator
import pushkar.chorus.music.utils.YTPlayerUtils.MAIN_CLIENT
import pushkar.chorus.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import pushkar.chorus.music.utils.YTPlayerUtils.validateStatus
import pushkar.chorus.music.utils.potoken.PoTokenGenerator
import pushkar.chorus.music.utils.potoken.PoTokenResult
import pushkar.chorus.music.utils.sabr.EjsNTransformSolver
import pushkar.chorus.music.utils.PlaybackLogLevel
import pushkar.chorus.music.utils.PlaybackLogManager
import kotlinx.coroutines.launch
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException
import kotlinx.coroutines.flow.first

import kotlin.coroutines.resume

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Timber.tag(TAG).e(ioe, "Proxy connection failed for URI: $uri")
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()


    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32

    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48,
        WEB_REMIX,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        IOS,
        WEB,
        WEB_CREATOR
    )

    private const val CLIENT_COOLDOWN_MS = 10 * 60 * 1000L

    private val clientCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun reportClientPlaybackFailure(userAgent: String?) {
        if (userAgent.isNullOrBlank()) return
        clientCooldowns[userAgent] = System.currentTimeMillis()
        Timber.tag(logTag).w("Client marked unhealthy for cooldown period: $userAgent")
    }

    private fun isClientCoolingDown(client: YouTubeClient): Boolean {
        val failedAt = clientCooldowns[client.userAgent] ?: return false
        return System.currentTimeMillis() - failedAt < CLIENT_COOLDOWN_MS
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val userAgent: String,
    )

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null,
        knownDurationMs: Long? = null,
        isDownload: Boolean = false
    ): Result<PlaybackData> {
        val showFallbackToast = false

        var hasShownLosslessToast = false
        var hasShownOpusToast = false

        suspend fun tryOpus(): Result<PlaybackData> {
            val firstAttempt = resolvePlaybackData(
                videoId,
                playlistId,
                audioQuality,
                connectivityManager,
                context,
                knownArtist,
                knownTitle
            )
            if (firstAttempt.isFailure && YouTube.cookie == null) {
                Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
                PlaybackLogManager.log(
                    PlaybackLogLevel.BOT,
                    "Playback failed for guest",
                    "Triggering bot detection mitigation (rotating guest session)"
                )
                BotDetectionMitigator.rotateGuestSession()
                val retryResult = resolvePlaybackData(
                    videoId,
                    playlistId,
                    audioQuality,
                    connectivityManager,
                    context,
                    knownArtist,
                    knownTitle
                )
                retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
                return retryResult
            }
            firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return firstAttempt
        }

        suspend fun tryLossless(): Result<PlaybackData> {
            var attemptResult: Result<PlaybackData>? = null
            var lastException: Exception? = null
            try {
                attemptResult = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    val metadata =
                        if (knownTitle == null || knownArtist == null) playerResponseForMetadata(videoId).getOrNull() else null
                    val title = knownTitle ?: metadata?.videoDetails?.title
                    val author = knownArtist ?: metadata?.videoDetails?.author?.replace(" - Topic", "")
                    if (title != null && author != null) {
                        val track = pushkar.chorus.music.utils.LosslessAPI.search(title, author)
                        if (track != null) {
                            val format = com.music.innertube.models.response.PlayerResponse.StreamingData.Format(
                                itag = 0,
                                mimeType = "audio/flac; codecs=\"flac\"",
                                bitrate = 1411000,
                                audioSampleRate = 44100,
                                contentLength = 0L,
                                url = track.url,
                                cipher = null,
                                signatureCipher = null,
                                audioQuality = "LOSSLESS",
                                fps = null,
                                width = null,
                                height = null,
                                quality = "lossless",
                                qualityLabel = null,
                                averageBitrate = null,
                                approxDurationMs = null,
                                audioChannels = null,
                                loudnessDb = null,
                                lastModified = null,
                                audioTrack = null
                            )
                            val resolvedPlaybackData = PlaybackData(
                                audioConfig = null,
                                videoDetails = metadata?.videoDetails,
                                playbackTracking = null,
                                format = format,
                                streamUrl = track.url,
                                streamExpiresInSeconds = 3600,
                                userAgent = com.music.innertube.models.YouTubeClient.WEB_CREATOR.userAgent
                            )
                            return@withTimeoutOrNull Result.success(resolvedPlaybackData)
                        } else {
                            throw Exception("No streamable match resolved on Lossless index")
                        }
                    } else {
                        throw Exception("Missing title or artist for lookup")
                    }
                }
                if (attemptResult == null) {
                    lastException = Exception("Timeout fetching Lossless stream")
                }
            } catch (e: Exception) {
                lastException = e
            }

            return attemptResult ?: Result.failure(lastException ?: Exception("Lossless resolution failed"))
        }

        fun showToastMsg(msg: String) {
            context?.let {
                if (showFallbackToast) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(it, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        return when (audioQuality) {
            AudioQuality.LOSSLESS -> {
                val losslessRes = tryLossless()
                if (losslessRes.isSuccess) return losslessRes

                Timber.tag(TAG).e("Qobuz resolution failed, falling back to YouTube Opus")
                if (!hasShownLosslessToast) {
                    hasShownLosslessToast = true
                    showToastMsg(if (isDownload) "Lossless download unavailable, falling back to Opus" else "Lossless stream unavailable, falling back to Opus")
                }

                tryOpus()
            }

            else -> {
                tryOpus()
            }
        }
    }


    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data", "Video: $videoId")

        val playbackCpn = (1..16).map {
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[kotlin.random.Random.nextInt(64)]
        }.joinToString("")


        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")


        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).d("Signature timestamp: ${signatureTimestamp.timestamp}")


        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            Timber.tag(logTag).d("Generating PoToken for MAIN_CLIENT with sessionId")
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully")
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
            }
        }

        // The web main client is only trustworthy when its PoToken actually generated;
        // guests without one get bot-checked ("Sign in to confirm you're not a bot").
        val potBackedPrimary = !MAIN_CLIENT.useWebPoTokens || poToken != null


        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying ${MAIN_CLIENT.clientName} (Main)")
        var mainPlayerResponse = YouTube.player(
            videoId,
            playlistId,
            MAIN_CLIENT,
            signatureTimestamp.timestamp,
            poToken?.playerRequestPoToken,
            playbackCpn
        ).getOrThrow()


        var metadataResponse: PlayerResponse? = null
        if (isLoggedIn) {
            Timber.tag(logTag).d("Fetching metadata from METADATA_CLIENT (WEB_REMIX) for authenticated tracking")
            try {

                var metaPoToken: PoTokenResult? = null
                val metaSessionId = YouTube.dataSyncId
                if (METADATA_CLIENT.useWebPoTokens && metaSessionId != null) {
                    try {
                        metaPoToken = poTokenGenerator.getWebClientPoToken(videoId, metaSessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Metadata PoToken generation failed")
                    }
                }
                metadataResponse = YouTube.player(
                    videoId, playlistId, METADATA_CLIENT,
                    signatureTimestamp.timestamp, metaPoToken?.playerRequestPoToken
                ).getOrNull()
                Timber.tag(logTag).d("Metadata response obtained: ${metadataResponse?.playabilityStatus?.status}")
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "Failed to fetch metadata from METADATA_CLIENT")
            }
        }


        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean


        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        ) || (mainStatus == "LOGIN_REQUIRED" && mainPlayerResponse.playabilityStatus.reason?.contains(
            "age",
            ignoreCase = true
        ) == true)
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {

            Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
            Log.i(TAG, "Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null)
                .onFailure {
                    // Distinguish thrown request/parse failures from genuine playability
                    // rejections (both otherwise surface as a null response downstream).
                    Timber.tag(logTag).e(it, "player() request FAILED for WEB_CREATOR")
                }.getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }


        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse.videoDetails
        val playbackTracking = metadataResponse?.playbackTracking ?: mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null


        val currentStatus = mainPlayerResponse.playabilityStatus.status
        var isAgeRestricted = currentStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED",
            "UNPLAYABLE",
            "LOGIN_REQUIRED"
        )

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content needs fallback (status: $currentStatus)")
            android.util.Log.i("YTPlayerUtils", "Unplayable content detected: videoId=$videoId, status=$currentStatus")
        }

        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

        val clientsToTry = buildList {
            if (!isPrivateTrack || isLoggedIn) {
                add(
                    when {
                        usedAgeRestrictedClient != null -> usedAgeRestrictedClient
                        // Guests without a PoToken: lead with the best PoToken-free client.
                        !potBackedPrimary && !isLoggedIn ->
                            STREAM_FALLBACK_CLIENTS.first { !it.useWebPoTokens }

                        else -> MAIN_CLIENT
                    }
                )
            }
            val fallbackStart = if (isPrivateTrack) 1 else 0
            addAll(STREAM_FALLBACK_CLIENTS.toList().drop(fallbackStart))
        }
            .distinct()
            .filter { !it.loginRequired || (isLoggedIn && YouTube.cookie != null) }
            .sortedBy { isClientCoolingDown(it) }

        suspend fun tryResolveForClient(client: YouTubeClient): PlaybackData? {
            return kotlinx.coroutines.withTimeout(12_000L) {
                var streamUrl: String? = null
                var format: PlayerResponse.StreamingData.Format? = null

                val clientPoToken = if (client.useWebPoTokens) {
                    if (poToken == null && sessionId != null) {
                        try {
                            poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                        } catch (e: Exception) {
                            null
                        }
                    } else poToken
                } else null

                val clientSigTimestamp =
                    if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp

                val streamPlayerResponse = when {
                    retryMainPlayerResponse != null && client == usedAgeRestrictedClient ->
                        retryMainPlayerResponse

                    client == MAIN_CLIENT && mainPlayerResponse.playabilityStatus.status == "OK" ->
                        mainPlayerResponse

                    else -> YouTube.player(
                        videoId,
                        playlistId,
                        client,
                        clientSigTimestamp,
                        clientPoToken?.playerRequestPoToken,
                        playbackCpn
                    ).getOrNull()
                }

                val responseToUse = streamPlayerResponse?.takeIf {
                    it.playabilityStatus.status == "OK"
                } ?: return@withTimeout null

                format = findFormat(responseToUse, audioQuality, connectivityManager)
                if (format != null) {
                    streamUrl = findUrlOrNull(
                        format,
                        videoId,
                        responseToUse,
                        skipNewPipe = wasOriginallyAgeRestricted
                    )
                }

                if (streamUrl == null || format == null) return@withTimeout null

                if (client.useWebPoTokens) {
                    try {
                        val transformed = EjsNTransformSolver.transformNParamInUrl(streamUrl)
                        if (transformed != streamUrl) streamUrl = transformed
                    } catch (e: Exception) {
                    }
                    if (clientPoToken?.streamingDataPoToken != null) {
                        val separator = if ("?" in streamUrl) "&" else "?"
                        streamUrl =
                            "${streamUrl}${separator}pot=${clientPoToken.streamingDataPoToken}"
                    }
                }

                val resolvedExpiry =
                    responseToUse.streamingData?.expiresInSeconds ?: 21600

                val separator = if ("?" in streamUrl) "&" else "?"
                if (!streamUrl.contains("cpn=")) {
                    streamUrl = "${streamUrl}${separator}cpn=$playbackCpn"
                }

                val isPrivatelyOwned =
                    responseToUse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                var isValid = false
                if (isPrivatelyOwned || (client.useWebPoTokens && clientPoToken?.streamingDataPoToken != null)) {
                    isValid = true
                } else if (validateStatus(streamUrl, client)) {
                    isValid = true
                }

                if (!isValid) return@withTimeout null

                PlaybackData(
                    audioConfig = audioConfig
                        ?: responseToUse.playerConfig?.audioConfig,
                    videoDetails = videoDetails ?: responseToUse.videoDetails,
                    playbackTracking = playbackTracking
                        ?: responseToUse.playbackTracking,
                    format = format,
                    streamUrl = streamUrl,
                    streamExpiresInSeconds = resolvedExpiry,
                    userAgent = client.userAgent
                )
            }
        }

        var finalPlaybackData: PlaybackData? = null

        if (clientsToTry.isNotEmpty()) {
            finalPlaybackData = try {
                tryResolveForClient(clientsToTry.first())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag(logTag).e(e, "Primary client (${clientsToTry.first().clientName}) resolution failed")
                null
            }
        }

        if (finalPlaybackData == null && clientsToTry.size > 1) {
            for (client in clientsToTry.drop(1)) {
                try {
                    val result = tryResolveForClient(client)
                    if (result != null) {
                        finalPlaybackData = result
                        break
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.tag(logTag).e(e, "Fetch failed for fallback client: ${client.clientName}")
                }
            }
        }

        if (finalPlaybackData == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            throw PlaybackException(
                "All fallback clients failed to resolve stream",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        Timber.tag(logTag).d("Successfully obtained playback data from concurrent fetch")
        finalPlaybackData
    }.onFailure { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.tag(logTag).e(e, "Playback resolution failed")
        PlaybackLogManager.log(PlaybackLogLevel.ERROR, "Playback failed", "${e::class.simpleName}: ${e.message}")
    }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag)
            .d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX)
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag)
            .d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.OPUS, AudioQuality.LOSSLESS -> 1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0)
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }

    private suspend fun validateStatus(url: String, client: YouTubeClient): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)
                .header("User-Agent", client.userAgent)

            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            val call = httpClient.newCall(requestBuilder.build())
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : okhttp3.Callback {
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        val isSuccessful = it.isSuccessful
                        Timber.tag(logTag)
                            .d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${it.code})")
                        if (cont.isActive) {
                            cont.resume(isSuccessful)
                        }
                    }
                }

                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
                    if (cont.isActive) {
                        cont.resume(false)
                    }
                }
            })
        }
    }

    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                        error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Log.i(TAG, "Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        Timber.tag(logTag)
            .d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")


        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }


        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }


        if (skipNewPipe) {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            return null
        }


        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }


        Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                return streamUrl
            }


            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}


