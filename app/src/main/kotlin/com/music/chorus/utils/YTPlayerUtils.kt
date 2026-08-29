package pushkar.chorus.music.utils

import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import pushkar.chorus.music.utils.PlaybackLogManager
import pushkar.chorus.music.utils.PlaybackLogLevel
import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import pushkar.chorus.music.utils.reportException

import pushkar.chorus.music.constants.AudioQuality
import kotlinx.coroutines.flow.first
import pushkar.chorus.music.utils.cipher.CipherDeobfuscator
import pushkar.chorus.music.utils.YTPlayerUtils.MAIN_CLIENT
import pushkar.chorus.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import pushkar.chorus.music.utils.YTPlayerUtils.validateStatus
import pushkar.chorus.music.utils.potoken.PoTokenGenerator
import pushkar.chorus.music.utils.potoken.PoTokenResult
import pushkar.chorus.music.utils.sabr.EjsNTransformSolver
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

enum class PlaybackEngine { POTOKEN, BRAVEPIPE, AUTO }

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"


    @Volatile
    var playbackEngine: PlaybackEngine = PlaybackEngine.AUTO

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val poTokenGenerator = PoTokenGenerator()


    private const val VALIDATION_CHUNK_LENGTH = 512 * 1024L


    private fun describeStreamUrl(url: String): String =
        try {
            val uri = Uri.parse(url)
            val expire = uri.getQueryParameter("expire")?.toLongOrNull()
            val nowSec = System.currentTimeMillis() / 1000
            buildString {
                append("host=").append(uri.host ?: "?")
                append(" itag=").append(uri.getQueryParameter("itag") ?: "-")
                append(" mime=").append(uri.getQueryParameter("mime") ?: "-")
                append(" c=").append(uri.getQueryParameter("c") ?: "-")
                append(" expire=").append(expire ?: "-")
                if (expire != null) append("(in ").append(expire - nowSec).append("s)")
                append(" hasPot=").append(uri.getQueryParameter("pot") != null)
                append(" nLen=").append(uri.getQueryParameter("n")?.length ?: -1)
                append(" cpn=").append(uri.getQueryParameter("cpn") ?: "-")
                append(" lmt=").append(uri.getQueryParameter("lmt") ?: "-")
                append(" sabr=").append(uri.getQueryParameter("sabr") ?: "-")
                append(" clen=").append(uri.getQueryParameter("clen") ?: "-")
            }
        } catch (e: Exception) {
            "unparseable url (${e.javaClass.simpleName})"
        }


    private fun describeResponse(client: YouTubeClient, response: PlayerResponse?): String =
        try {
            if (response == null) {
                Fix403.kv("client" to client.clientName, "response" to "NULL(requestFailed)")
            } else {
                val adaptive = response.streamingData?.adaptiveFormats.orEmpty()
                val audio = adaptive.filter { it.isAudio }
                Fix403.kv(
                    "client" to client.clientName,
                    "clientVersion" to client.clientVersion,
                    "status" to response.playabilityStatus.status,
                    "reason" to response.playabilityStatus.reason,
                    "hasStreamingData" to (response.streamingData != null),
                    "expiresInSeconds" to response.streamingData?.expiresInSeconds,
                    "adaptiveFormats" to adaptive.size,
                    "audioFormats" to audio.size,
                    "urls" to adaptive.count { !it.url.isNullOrEmpty() },
                    "ciphers" to adaptive.count { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() },

                    "bare" to adaptive.count {
                        it.url.isNullOrEmpty() && it.signatureCipher.isNullOrEmpty() && it.cipher.isNullOrEmpty()
                    },
                    "audioItags" to audio.joinToString("/") { it.itag.toString() }.ifEmpty { "-" },
                    "musicVideoType" to response.videoDetails?.musicVideoType,
                    "title" to response.videoDetails?.title,
                )
            }
        } catch (e: Exception) {
            "describeResponse failed (${e.javaClass.simpleName}: ${e.message})"
        }

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX


    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        ANDROID_VR_1_65_10,
        TVHTML5,
        ANDROID_VR_1_43_32,
        IPADOS,
        IOS,
        WEB_CREATOR
    )
    private val NORMAL_CONTENT_STREAM_START_INDEX: Int = 0


    private val PRIVATE_TRACK_STREAM_START_INDEX: Int =
        STREAM_FALLBACK_CLIENTS.indexOf(TVHTML5).takeIf { it >= 0 } ?: 0

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        val fx = Fix403.nextId("res")
        Timber.tag(TAG).d("=== PLAYER RESPONSE FOR PLAYBACK ===")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Fetching player response", "VideoId: $videoId")
        Timber.tag(TAG).d("videoId: $videoId")
        Timber.tag(TAG).d("playlistId: $playlistId")
        Timber.tag(TAG).d("audioQuality: $audioQuality")


        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true
        Timber.tag(TAG).d("Content type detection (preliminary):")
        Timber.tag(TAG).d("  isUploadedTrack (from playlistId): $isUploadedTrack")

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(TAG).d("Authentication status: ${if (isLoggedIn) "LOGGED_IN" else "ANONYMOUS"}")

        Fix403.i(
            fx, "resolve.begin",
            Fix403.kv(
                "videoId" to videoId,
                "playlistId" to playlistId,
                "quality" to audioQuality,
                "uploadedTrack" to isUploadedTrack,
                "loggedIn" to isLoggedIn,
                "thread" to Thread.currentThread().name,
            ),
        )
        Fix403.i(
            fx, "resolve.session",
            Fix403.kv(
                "cookie" to Fix403.redact(YouTube.cookie),
                "visitorData" to Fix403.redact(YouTube.visitorData),
                "dataSyncId" to Fix403.redact(YouTube.dataSyncId),
                "proxy" to (YouTube.proxy?.toString() ?: "none"),
                "locale" to "${YouTube.locale.hl}/${YouTube.locale.gl}",
            ),
        )


        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).d("Signature timestamp: ${signatureTimestamp.timestamp}")
        Fix403.i(
            fx, "resolve.sts",
            Fix403.kv("sts" to signatureTimestamp.timestamp, "source" to "NewPipeExtractor"),
        )

        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        val mainClientNeedsPoToken = MAIN_CLIENT.useWebPoTokens
        Fix403.i(
            fx, "potoken.decide",
            Fix403.kv(
                "mainClientNeedsPoToken" to mainClientNeedsPoToken,
                "sessionIdSource" to if (isLoggedIn) "dataSyncId" else "visitorData",
                "sessionId" to Fix403.redact(sessionId),
                "sessionIdEmpty" to (sessionId != null && sessionId.isEmpty()),
            ),
        )
        if (mainClientNeedsPoToken && sessionId != null) {
            Timber.tag(logTag).d("Generating PoToken for WEB_REMIX with sessionId")
            try {
                poToken = Fix403.timed(fx, "potoken.generate") {
                    poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                }
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully")
                }
                Fix403.i(
                    fx, "potoken.result",
                    Fix403.kv(
                        "obtained" to (poToken != null),
                        "playerRequestPoToken" to Fix403.redact(poToken?.playerRequestPoToken),
                        "streamingDataPoToken" to Fix403.redact(poToken?.streamingDataPoToken),
                    ),
                )
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
                Fix403.fail(fx, "potoken.generate.failed", e)
            }
        } else {
            Fix403.w(
                fx, "potoken.skipped",
                Fix403.kv("reason" to if (!mainClientNeedsPoToken) "mainClientDoesNotUseIt" else "sessionIdNull"),
            )
        }
        val skipMainClient = mainClientNeedsPoToken && poToken == null
        if (skipMainClient) {
            Timber.tag(TAG).w("PoToken unavailable — skipping MAIN_CLIENT and using fallback chain directly")
            Fix403.w(fx, "mainClient.skipped", Fix403.kv("reason" to "poTokenUnavailable"))
        }


        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        var mainPlayerResponse = Fix403.trapRethrow(fx, "mainClient.player") {
            Fix403.timed(fx, "mainClient.request") {
                YouTube.player(
                    videoId,
                    playlistId,
                    MAIN_CLIENT,
                    signatureTimestamp.timestamp,
                    poToken?.playerRequestPoToken
                ).getOrThrow()
            }
        }
        Fix403.i(fx, "mainClient.response", describeResponse(MAIN_CLIENT, mainPlayerResponse))


        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse.playabilityStatus.status}")
            PlaybackLogManager.log(
                PlaybackLogLevel.DEBUG,
                "Status: ${mainPlayerResponse.playabilityStatus.status}",
                "Reason: ${mainPlayerResponse.playabilityStatus.reason}"
            )
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse.playabilityStatus.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse.videoDetails?.title}, videoId=${mainPlayerResponse.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean


        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "LOGIN_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {

            Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
            Timber.tag(TAG).i("Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }


        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        val retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null


        val currentStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestricted = currentStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "LOGIN_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content is still age-restricted (status: $currentStatus), will try fallback clients")
            Timber.tag(TAG)
                .i("Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }
        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
        val startIndex = when {
            isPrivateTrack -> PRIVATE_TRACK_STREAM_START_INDEX
            isAgeRestricted -> 0
            skipMainClient -> 0
            else -> NORMAL_CONTENT_STREAM_START_INDEX
        }

        val cascade = mutableListOf<String>()
        fun logCascade(outcome: String) = Fix403.i(
            fx, "cascade.$outcome",
            Fix403.kv("videoId" to videoId, "tried" to cascade.size) + " :: " + cascade.joinToString(" | "),
        )

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {

            format = null
            streamUrl = null
            streamExpiresInSeconds = null
            val client: YouTubeClient
            if (clientIndex == -1) {
                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag)
                    .d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")
                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    Timber.tag(logTag)
                        .d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    cascade += "${client.clientName}=SKIP(loginRequired)"
                    Fix403.w(
                        fx,
                        "client.skip",
                        Fix403.kv("client" to client.clientName, "reason" to "loginRequiredButAnonymous")
                    )
                    continue
                }
                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                Fix403.i(
                    fx, "client.request",
                    Fix403.kv(
                        "idx" to "${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}",
                        "client" to client.clientName,
                        "clientVersion" to client.clientVersion,
                        "loginSupported" to client.loginSupported,
                        "useWebPoTokens" to client.useWebPoTokens,
                        "sts" to clientSigTimestamp,
                        "poToken" to Fix403.redact(clientPoToken),
                    ),
                )
                streamPlayerResponse = Fix403.trap(fx, "client.request.${client.clientName}") {
                    Fix403.timed(fx, "client.http.${client.clientName}") {
                        YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken)
                            .onFailure { Fix403.fail(fx, "client.player.failed.${client.clientName}", it) }
                            .getOrNull()
                    }
                }
                Fix403.i(fx, "client.response", describeResponse(client, streamPlayerResponse))
            }
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag)
                    .d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                val responseToUse = if (wasOriginallyAgeRestricted) {
                    Timber.tag(logTag).d("Skipping NewPipe for age-restricted content")
                    streamPlayerResponse
                } else {
                    val newPipeResponse = YouTube.newPipePlayer(videoId, streamPlayerResponse)
                    newPipeResponse ?: streamPlayerResponse
                }
                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                    )
                if (format == null) {
                    Timber.tag(logTag)
                        .d("No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    cascade += "${client.clientName}=NO_FORMAT"
                    Fix403.w(
                        fx, "client.noFormat",
                        Fix403.kv("client" to client.clientName, "quality" to audioQuality) + " " +
                                describeResponse(client, responseToUse),
                    )
                    continue
                }

                Timber.tag(logTag).d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")
                val urlSource = when {
                    !format.url.isNullOrEmpty() -> "FORMAT_URL"
                    !format.signatureCipher.isNullOrEmpty() || !format.cipher.isNullOrEmpty() -> "SIG_CIPHER"
                    else -> "NEWPIPE_OR_NONE"
                }
                streamUrl = Fix403.trap(fx, "findUrl.${client.clientName}") {
                    findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                }
                Fix403.i(
                    fx, "client.url",
                    Fix403.kv(
                        "client" to client.clientName,
                        "itag" to format.itag,
                        "mime" to format.mimeType,
                        "bitrate" to format.bitrate,
                        "urlSource" to urlSource,
                        "resolved" to (streamUrl != null),
                    ) + if (streamUrl != null) " " + describeStreamUrl(streamUrl!!) else "",
                )
                if (streamUrl == null) {
                    Timber.tag(logTag).d("Stream URL not found for format")
                    cascade += "${client.clientName}=NO_URL($urlSource)"
                    Fix403.w(fx, "client.noUrl", Fix403.kv("client" to client.clientName, "urlSource" to urlSource))
                    continue
                }
                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                val isPrivatelyOwnedTrack =
                    streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
                val musicVideoType = streamPlayerResponse.videoDetails?.musicVideoType

                Timber.tag(TAG).d("=== N-TRANSFORM DECISION ===")
                Timber.tag(TAG).d("Content type analysis:")
                Timber.tag(TAG).d("  musicVideoType: $musicVideoType")
                Timber.tag(TAG).d("  isPrivatelyOwnedTrack: $isPrivatelyOwnedTrack")
                Timber.tag(TAG).d("  isUploadedTrack (from playlistId): $isUploadedTrack")
                Timber.tag(TAG).d("  wasOriginallyAgeRestricted: $wasOriginallyAgeRestricted")
                Timber.tag(TAG).d("Client analysis:")
                Timber.tag(TAG).d("  currentClient: ${currentClient.clientName}")
                Timber.tag(TAG).d("  useWebPoTokens: ${currentClient.useWebPoTokens}")
                val needsNTransform = currentClient.useWebPoTokens ||
                        currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5") ||
                        isPrivatelyOwnedTrack
                Timber.tag(TAG).d("N-transform decision:")
                Timber.tag(TAG).d("  needsNTransform: $needsNTransform")
                Timber.tag(TAG).d(
                    "  Reason: useWebPoTokens=${currentClient.useWebPoTokens}, " +
                            "clientInList=${
                                currentClient.clientName in listOf(
                                    "WEB",
                                    "WEB_REMIX",
                                    "WEB_CREATOR",
                                    "TVHTML5"
                                )
                            }, " +
                            "isPrivatelyOwnedTrack=$isPrivatelyOwnedTrack"
                )
                if (needsNTransform) {
                    try {
                        Timber.tag(TAG).d("Applying n-transform to stream URL...")
                        Timber.tag(TAG).d("  Original URL length: ${streamUrl.length}")
                        Timber.tag(TAG).d("  Original URL preview: ${streamUrl.take(100)}...")

                        val originalUrl = streamUrl!!
                        streamUrl = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)

                        Timber.tag(TAG).d("  Transformed URL length: ${streamUrl.length}")
                        Timber.tag(TAG).d("  URL changed: ${originalUrl != streamUrl}")

                        val needsPoToken =
                            (currentClient.useWebPoTokens || isPrivatelyOwnedTrack) && poToken?.streamingDataPoToken != null
                        Timber.tag(TAG).d("PoToken decision:")
                        Timber.tag(TAG).d("  needsPoToken: $needsPoToken")
                        Timber.tag(TAG).d("  hasStreamingDataPoToken: ${poToken?.streamingDataPoToken != null}")

                        if (needsPoToken) {
                            Timber.tag(TAG).d("Appending pot= parameter to stream URL")
                            val separator = if ("?" in streamUrl!!) "&" else "?"
                            streamUrl = "${streamUrl}${separator}pot=${Uri.encode(poToken!!.streamingDataPoToken)}"
                            Timber.tag(TAG).d("  Final URL length (with pot): ${streamUrl.length}")
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "N-transform or pot append failed: ${e.message}")
                        Timber.tag(TAG).e("Stack trace: ${e.stackTraceToString().take(500)}")
                    }
                } else {
                    Timber.tag(TAG).d("Skipping n-transform (not required for this client/content)")
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).d("Stream expiration time not found")
                    cascade += "${client.clientName}=NO_EXPIRE"
                    Fix403.w(
                        fx, "client.noExpire",
                        Fix403.kv(
                            "client" to client.clientName,
                            "hasStreamingData" to (streamPlayerResponse.streamingData != null)
                        ),
                    )
                    continue
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                val isPrivatelyOwned =
                    streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1 || isPrivatelyOwned) {
                    if (isPrivatelyOwned) {
                        Timber.tag(logTag)
                            .d("Skipping validation for privately owned track: ${currentClient.clientName}")
                        println("[PLAYBACK_DEBUG] Using stream without validation for PRIVATELY_OWNED_TRACK")
                    } else {
                        Timber.tag(logTag)
                            .d("Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    }
                    Timber.tag(TAG)
                        .i("Playback: client=${currentClient.clientName}, videoId=$videoId, private=$isPrivatelyOwned")
                    cascade += "${currentClient.clientName}=ACCEPTED(unvalidated)"
                    Fix403.i(
                        fx, "client.accepted",
                        Fix403.kv(
                            "client" to currentClient.clientName,
                            "validated" to false,
                            "why" to if (isPrivatelyOwned) "privatelyOwnedTrack" else "lastFallbackClient",
                            "expiresInSeconds" to streamExpiresInSeconds,
                        ) + " " + describeStreamUrl(streamUrl!!),
                    )
                    logCascade("resolved")
                    break
                }

                if (validateStatus(
                        streamUrl,
                        format.contentLength,
                        Fix403.kv("fx" to fx, "client" to currentClient.clientName, "itag" to format.itag),
                    )
                ) {
                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    cascade += "${currentClient.clientName}=ACCEPTED"
                    Fix403.i(
                        fx, "client.accepted",
                        Fix403.kv(
                            "client" to currentClient.clientName,
                            "validated" to true,
                            "expiresInSeconds" to streamExpiresInSeconds,
                        ) + " " + describeStreamUrl(streamUrl!!),
                    )
                    logCascade("resolved")
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")
                    cascade += "${currentClient.clientName}=REJECTED(validate)"
                }
            } else {
                Timber.tag(logTag)
                    .d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
                cascade += "${client.clientName}=NOT_OK(${streamPlayerResponse?.playabilityStatus?.status ?: "null"})"
                Fix403.w(
                    fx, "client.notOk",
                    Fix403.kv(
                        "client" to client.clientName,
                        "status" to streamPlayerResponse?.playabilityStatus?.status,
                        "reason" to streamPlayerResponse?.playabilityStatus?.reason,
                    ),
                )
            }
        }
        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            logCascade("exhausted")
            Fix403.e(fx, "resolve.failed", Fix403.kv("videoId" to videoId, "why" to "badStreamPlayerResponse"))
            throw Exception("Bad stream player response")
        }
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            logCascade("exhausted")
            Fix403.e(
                fx, "resolve.failed",
                Fix403.kv(
                    "videoId" to videoId,
                    "why" to "playabilityNotOk",
                    "status" to streamPlayerResponse.playabilityStatus.status,
                    "reason" to errorReason,
                ),
            )
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            logCascade("exhausted")
            Fix403.e(fx, "resolve.failed", Fix403.kv("videoId" to videoId, "why" to "missingExpireTime"))
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            logCascade("exhausted")
            Fix403.e(fx, "resolve.failed", Fix403.kv("videoId" to videoId, "why" to "noFormat"))
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            logCascade("exhausted")
            Fix403.e(fx, "resolve.failed", Fix403.kv("videoId" to videoId, "why" to "noStreamUrl"))
            throw Exception("Could not find stream url")
        }
        Fix403.i(
            fx, "resolve.success",
            Fix403.kv("videoId" to videoId, "itag" to format.itag, "expiresInSeconds" to streamExpiresInSeconds) +
                    " " + describeStreamUrl(streamUrl!!),
        )

        Timber.tag(logTag)
            .d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println(
                "[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${
                    streamUrl.take(
                        100
                    )
                }..."
            )
        }
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }.onFailure { e ->
        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
        Fix403.fail(
            Fix403.nextId("resolve-fail"), "resolve.exception", e,
            Fix403.kv("videoId" to videoId, "playlistId" to playlistId),
        )
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
                it.bitrate * 1 + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0)
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }

    private fun validateStatus(url: String, contentLength: Long? = null, label: String = ""): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val range = if (contentLength != null && contentLength > 0) {
                "bytes=${contentLength - 1}-${contentLength - 1}"
            } else {
                "bytes=0-${VALIDATION_CHUNK_LENGTH - 1}"
            }
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)
                .addHeader("Range", range)

            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.close()
            val code = response.code
            val accepted = response.isSuccessful || code == 405
            when {
                !accepted ->
                    Timber.tag(logTag)
                        .w("Stream URL REJECTED: code=$code range=$range $label ${describeStreamUrl(url)}")

                !response.isSuccessful ->
                    Timber.tag(logTag).w("Stream URL accepted on non-2xx code=$code (HEAD refused) range=$range $label")

                else ->
                    Timber.tag(logTag).d("Stream URL validation: code=$code range=$range accepted $label")
            }
            return accepted
        } catch (e: java.io.IOException) {
            Timber.tag(logTag).w(e, "Stream URL HEAD probe failed (IO); accepting optimistically")
            return true
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
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
                    Timber.tag(TAG).i("Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        val engine = playbackEngine
        Timber.tag(logTag)
            .d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, engine: $engine, skipNewPipe: $skipNewPipe")
        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }
        val useCipher = engine == PlaybackEngine.POTOKEN || engine == PlaybackEngine.AUTO
        if (useCipher) {
            val signatureCipher = format.signatureCipher ?: format.cipher
            if (!signatureCipher.isNullOrEmpty()) {
                Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation (engine=$engine)")
                try {
                    val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
                    if (customDeobfuscatedUrl != null) {
                        Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                        return customDeobfuscatedUrl
                    }
                } catch (e: Exception) {
                    Timber.tag(logTag).e(e, "Custom cipher deobfuscation failed")
                }
                Timber.tag(logTag).d("Custom cipher deobfuscation failed or returned null")
            }
        }

        val useBravePipe = engine == PlaybackEngine.BRAVEPIPE || engine == PlaybackEngine.AUTO
        if (useBravePipe) {
            if (skipNewPipe) {
                Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            } else {
                try {
                    val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
                    if (deobfuscatedUrl != null) {
                        Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
                        return deobfuscatedUrl
                    }
                } catch (e: Exception) {
                    Timber.tag(logTag).e(e, "NewPipe deobfuscation failed")
                }
                Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
                try {
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
                } catch (e: Exception) {
                    Timber.tag(logTag).e(e, "StreamInfo fallback failed")
                }
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}
