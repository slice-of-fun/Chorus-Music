package pushkar.chorus.music.playback.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import timber.log.Timber
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt


object BeatAnalyzer {

    data class Result(
        val bpm: Float,
        val firstBeatOffsetMs: Long,
        val confidence: Float,
        val mixInPointMs: Long? = null,
        val mixOutPointMs: Long? = null,
        
        val keyPitchClass: Int? = null,
        val keyIsMinor: Boolean? = null,
    )

    private const val TAG = "BeatAnalyzer"

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 512
    private const val MIN_BPM = 60f
    private const val MAX_BPM = 180f

    
    private const val WINDOW_US = 18_000_000L

    
    private const val HEAD_WINDOW_US = 16_000_000L
    private const val TAIL_WINDOW_US = 24_000_000L

    
    private const val MIN_CANONICAL_BPM = 70f
    private const val MAX_CANONICAL_BPM = 140f
    private const val ENERGY_BLOCK_MS = 500
    private const val MAX_INTRO_SKIP_MS = 20_000L
    private const val MAX_OUTRO_CUT_MS = 45_000L

    
    private const val MAX_FETCH_BYTES = 5L * 1024 * 1024

    fun analyzeUri(
        context: Context,
        uri: Uri,
        shouldCancel: () -> Boolean = { false },
    ): Result? =
        analyze(shouldCancel) { extractor -> extractor.setDataSource(context, uri, null) }

    
    class CachedAnalysis(val result: Result?, val complete: Boolean)

    
    @UnstableApi
    fun analyzeStream(
        dataSourceFactory: androidx.media3.datasource.DataSource.Factory,
        mediaId: String,
        tempDir: java.io.File,
        shouldCancel: () -> Boolean = { false },
    ): CachedAnalysis? {
        val tempFile = java.io.File(tempDir, "beat_${mediaId.hashCode()}.tmp")
        try {
            var copied = 0L
            var reachedEnd = false
            val source = dataSourceFactory.createDataSource()
            try {
                source.open(
                    DataSpec.Builder()
                        .setUri(Uri.parse(mediaId))
                        .setKey(mediaId)
                        .build()
                )
                java.io.FileOutputStream(tempFile).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (copied < MAX_FETCH_BYTES) {
                        if (shouldCancel()) {
                            Timber.tag(TAG).d("Stream fetch for %s cancelled at %d bytes", mediaId, copied)
                            return null
                        }
                        val read = source.read(buf, 0, buf.size)
                        if (read == C.RESULT_END_OF_INPUT) {
                            reachedEnd = true
                            break
                        }
                        if (shouldCancel()) {
                            Timber.tag(TAG).d("Stream fetch for %s cancelled at %d bytes", mediaId, copied)
                            return null
                        }
                        out.write(buf, 0, read)
                        copied += read
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("Stream fetch for %s stopped at %d bytes (%s)", mediaId, copied, e.message)
            } finally {
                try {
                    source.close()
                } catch (_: Exception) {
                }
            }

            Timber.tag(TAG).d("Stream fetch for %s: %d bytes, complete=%s", mediaId, copied, reachedEnd)
            if (copied < 256 * 1024) return null

            if (shouldCancel()) return null
            val result = analyze(shouldCancel) { extractor -> extractor.setDataSource(tempFile.absolutePath) }
            return CachedAnalysis(result, reachedEnd)
        } finally {
            tempFile.delete()
        }
    }

    private fun analyze(
        shouldCancel: () -> Boolean = { false },
        setSource: (MediaExtractor) -> Unit,
    ): Result? {
        val extractor = MediaExtractor()
        try {
            if (shouldCancel()) return null
            setSource(extractor)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null

            extractor.selectTrack(trackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            val windowStartUs = max(0L, durationUs / 2 - WINDOW_US / 2)
            extractor.seekTo(windowStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = max(0L, extractor.sampleTime)

            if (shouldCancel()) return null
            val pcm = decodeMono(extractor, format, WINDOW_US, shouldCancel) ?: return null
            if (pcm.samples.size < FFT_SIZE * 8) return null

            if (shouldCancel()) return null
            val key = estimateKey(pcm.samples, pcm.sampleRate)

            if (shouldCancel()) return null
            val flux = spectralFlux(pcm.samples)
            val frameRate = pcm.sampleRate.toFloat() / HOP_SIZE

            val (periodFrames, confidence) = estimateTempoPeriod(flux, frameRate) ?: return null
            val phaseFrames = estimateBeatPhase(flux, periodFrames)

            var periodMs = periodFrames / frameRate * 1000f
            var bpm = 60_000f / periodMs
            
            
            while (bpm < MIN_CANONICAL_BPM) {
                bpm *= 2f
                periodMs /= 2f
            }
            while (bpm >= MAX_CANONICAL_BPM) {
                bpm /= 2f
                periodMs *= 2f
            }
            val windowStartMs = actualStartUs / 1000
            val anchorMs = windowStartMs + (phaseFrames / frameRate * 1000f).roundToLong()
            
            val firstBeatOffsetMs = (anchorMs % periodMs.roundToLong() + periodMs.roundToLong()) %
                periodMs.roundToLong()

            
            
            var mixInPointMs: Long? = null
            if (durationUs > HEAD_WINDOW_US) {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                decodeMono(extractor, format, HEAD_WINDOW_US, shouldCancel)?.let { head ->
                    mixInPointMs = detectMixIn(
                        energyEnvelope(head.samples, head.sampleRate),
                        firstBeatOffsetMs,
                        periodMs,
                    )
                }
            }

            
            
            var mixOutPointMs: Long? = null
            val durationMs = durationUs / 1000
            if (durationUs > TAIL_WINDOW_US) {
                val tailStartUs = durationUs - TAIL_WINDOW_US
                extractor.seekTo(tailStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val tailActualStartMs = max(0L, extractor.sampleTime) / 1000
                decodeMono(extractor, format, TAIL_WINDOW_US, shouldCancel)?.let { tail ->
                    mixOutPointMs = detectMixOut(
                        energyEnvelope(tail.samples, tail.sampleRate),
                        tailActualStartMs,
                        durationMs,
                    )
                }
            }

            return Result(bpm, firstBeatOffsetMs, confidence, mixInPointMs, mixOutPointMs, key?.first, key?.second)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Beat analysis failed")
            return null
        } finally {
            extractor.release()
        }
    }

    private class MonoPcm(val samples: FloatArray, val sampleRate: Int)

    private fun decodeMono(
        extractor: MediaExtractor,
        format: MediaFormat,
        maxDurationUs: Long,
        shouldCancel: () -> Boolean = { false },
    ): MonoPcm? {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        val out = ArrayList<FloatArray>()
        var sampleRate = 0
        var decodedUs = 0L
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()

            while (!outputDone && decodedUs < maxDurationUs) {
                if (shouldCancel()) return null
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (info.size > 0) {
                        val outFormat = codec.outputFormat
                        val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val buf = codec.getOutputBuffer(outIndex)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val shorts = buf.asShortBuffer()
                        val frames = shorts.remaining() / channels
                        val mono = FloatArray(frames)
                        for (f in 0 until frames) {
                            var acc = 0f
                            for (c in 0 until channels) acc += shorts.get(f * channels + c) / 32768f
                            mono[f] = acc / channels
                        }
                        out.add(mono)
                        decodedUs += frames * 1_000_000L / max(1, sampleRate)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Decode failed")
            return null
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }
        if (sampleRate == 0 || out.isEmpty()) return null

        val total = out.sumOf { it.size }
        val samples = FloatArray(total)
        var pos = 0
        for (chunk in out) {
            chunk.copyInto(samples, pos)
            pos += chunk.size
        }
        return MonoPcm(samples, sampleRate)
    }

    
    private fun energyEnvelope(samples: FloatArray, sampleRate: Int): FloatArray {
        val blockSize = (sampleRate * ENERGY_BLOCK_MS / 1000).coerceAtLeast(1)
        val numBlocks = samples.size / blockSize
        val env = FloatArray(numBlocks)
        for (b in 0 until numBlocks) {
            var sum = 0f
            val offset = b * blockSize
            for (i in 0 until blockSize) {
                val s = samples[offset + i]
                sum += s * s
            }
            env[b] = sqrt(sum / blockSize)
        }
        return env
    }

    private fun percentile(values: FloatArray, p: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.size - 1)]
    }

    
    private fun detectMixIn(env: FloatArray, firstBeatOffsetMs: Long, periodMs: Float): Long? {
        if (env.size < 8) return null
        val ref = percentile(env, 0.75f)
        if (ref <= 0f) return null

        var candidateBlock = -1
        for (i in 0 until env.size - 4) {
            if (env[i] >= 0.55f * ref &&
                env[i + 1] >= 0.4f * ref &&
                env[i + 2] >= 0.4f * ref &&
                env[i + 3] >= 0.4f * ref
            ) {
                candidateBlock = i
                break
            }
        }
        if (candidateBlock <= 0) return null 

        val candidateMs = candidateBlock.toLong() * ENERGY_BLOCK_MS
        if (candidateMs > MAX_INTRO_SKIP_MS) return null

        
        val k = kotlin.math.ceil((candidateMs - firstBeatOffsetMs) / periodMs.toDouble()).toLong()
        return (firstBeatOffsetMs + max(0L, k) * periodMs.toDouble()).roundToLong()
    }

    
    private fun detectMixOut(env: FloatArray, windowStartMs: Long, durationMs: Long): Long? {
        if (env.size < 8 || durationMs <= 0) return null
        val ref = percentile(env, 0.75f)
        if (ref <= 0f) return null

        var lastLoudBlock = -1
        for (i in env.indices.reversed()) {
            if (env[i] >= 0.5f * ref) {
                lastLoudBlock = i
                break
            }
        }
        if (lastLoudBlock < 0) return null

        val mixOutMs = windowStartMs + (lastLoudBlock + 1).toLong() * ENERGY_BLOCK_MS
        
        if (durationMs - mixOutMs < 3_000) return null
        
        return max(mixOutMs, durationMs - MAX_OUTRO_CUT_MS)
    }

    
    private fun spectralFlux(samples: FloatArray): FloatArray {
        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / FFT_SIZE).toFloat() }
        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        val bins = FFT_SIZE / 2
        val flux = FloatArray(numFrames)
        val prevMag = FloatArray(bins)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)

        for (frame in 0 until numFrames) {
            val offset = frame * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            var sum = 0f
            for (b in 0 until bins) {
                val mag = ln(1f + 10f * sqrt(re[b] * re[b] + im[b] * im[b]))
                val diff = mag - prevMag[b]
                if (diff > 0) sum += diff
                prevMag[b] = mag
            }
            flux[frame] = sum
        }

        
        val meanWindow = (0.5f * FFT_SIZE / HOP_SIZE * 8).roundToInt().coerceAtLeast(8)
        val detrended = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            val lo = max(0, i - meanWindow)
            val hi = min(numFrames - 1, i + meanWindow)
            var mean = 0f
            for (j in lo..hi) mean += flux[j]
            mean /= hi - lo + 1
            detrended[i] = max(0f, flux[i] - mean)
        }
        return detrended
    }

    
    private fun estimateTempoPeriod(flux: FloatArray, frameRate: Float): Pair<Float, Float>? {
        val minLag = (frameRate * 60f / MAX_BPM).roundToInt()
        val maxLag = (frameRate * 60f / MIN_BPM).roundToInt()
        if (flux.size < maxLag * 2) return null

        val ac = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until flux.size - lag) sum += flux[i] * flux[i + lag]
            ac[lag] = sum / (flux.size - lag)
        }

        var mean = 0f
        for (lag in minLag..maxLag) mean += ac[lag]
        mean /= maxLag - minLag + 1
        if (mean <= 0f) return null

        var bestLag = -1
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            if (lag > minLag && lag < maxLag && (ac[lag] < ac[lag - 1] || ac[lag] < ac[lag + 1])) continue
            var score = ac[lag]
            
            val doubleLag = lag * 2
            if (doubleLag <= maxLag) score += 0.5f * ac[doubleLag]
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        
        val refined = if (bestLag in minLag + 1 until maxLag) {
            val y0 = ac[bestLag - 1]
            val y1 = ac[bestLag]
            val y2 = ac[bestLag + 1]
            val denom = y0 - 2 * y1 + y2
            if (denom != 0f) bestLag + 0.5f * (y0 - y2) / denom else bestLag.toFloat()
        } else bestLag.toFloat()

        
        
        val sortedAc = ac.copyOfRange(minLag, maxLag + 1).sorted()
        val median = sortedAc[sortedAc.size / 2]
        val confidence = if (ac[bestLag] > 0f) ((ac[bestLag] - median) / ac[bestLag]).coerceIn(0f, 1f) else 0f
        return refined to confidence
    }

    
    private val MAJOR_PROFILE = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_PROFILE = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    
    private fun estimateKey(samples: FloatArray, sampleRate: Int): Pair<Int, Boolean>? {
        if (samples.size < FFT_SIZE * 4) return null
        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / FFT_SIZE).toFloat() }
        val hop = FFT_SIZE / 2
        val numFrames = (samples.size - FFT_SIZE) / hop
        if (numFrames < 4) return null

        val chroma = FloatArray(12)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val binHz = sampleRate.toFloat() / FFT_SIZE
        
        val minBin = (65f / binHz).toInt().coerceAtLeast(1)
        val maxBin = (2000f / binHz).toInt().coerceAtMost(FFT_SIZE / 2 - 1)

        for (frame in 0 until numFrames) {
            val offset = frame * hop
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            for (b in minBin..maxBin) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b])
                if (mag <= 0f) continue
                val freq = b * binHz
                val midi = 69.0 + 12.0 * (ln(freq / 440.0) / ln(2.0))
                val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pitchClass] += mag
            }
        }

        val sum = chroma.sum()
        if (sum <= 0f) return null
        for (i in chroma.indices) chroma[i] /= sum

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPitchClass = 0
        var bestIsMinor = false
        for (tonic in 0 until 12) {
            val majorScore = correlateChroma(chroma, MAJOR_PROFILE, tonic)
            if (majorScore > bestScore) {
                bestScore = majorScore; bestPitchClass = tonic; bestIsMinor = false
            }
            val minorScore = correlateChroma(chroma, MINOR_PROFILE, tonic)
            if (minorScore > bestScore) {
                bestScore = minorScore; bestPitchClass = tonic; bestIsMinor = true
            }
        }
        return bestPitchClass to bestIsMinor
    }

    private fun correlateChroma(chroma: FloatArray, profile: FloatArray, tonic: Int): Float {
        var sum = 0f
        for (degree in 0 until 12) sum += chroma[(degree + tonic) % 12] * profile[degree]
        return sum
    }

    
    private fun estimateBeatPhase(flux: FloatArray, periodFrames: Float): Float {
        val period = periodFrames.roundToInt().coerceAtLeast(1)
        var bestPhase = 0
        var bestSum = -1f
        for (phase in 0 until period) {
            var sum = 0f
            var i = phase
            while (i < flux.size) {
                sum += flux[i]
                i += period
            }
            if (sum > bestSum) {
                bestSum = sum
                bestPhase = phase
            }
        }
        return bestPhase.toFloat()
    }

    
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }

}
