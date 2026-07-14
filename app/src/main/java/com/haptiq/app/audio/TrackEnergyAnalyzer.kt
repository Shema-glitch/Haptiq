package com.haptiq.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** Offline bass-energy scan result: one 0–255 sample per [TrackEnergyAnalyzer.FRAME_MS]. */
class TrackEnergyResult(val frames: ByteArray, val durationMs: Long)

/**
 * Decodes a whole track off-thread (MediaExtractor + MediaCodec, no playback) and
 * reduces it to a coarse bass-energy envelope: low-pass the PCM at ~120 Hz, take the
 * RMS of each 250 ms bucket, normalize to 0–255.
 *
 * This is the AOT "haptic map" concept from the old spec, revived for seek-preview:
 * unlike the live Visualizer (which only sees audio at the current play position),
 * this map answers "how hard does the bass hit at ANY position?" — which is exactly
 * what scrubbing needs.
 */
@Singleton
class TrackEnergyAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TrackEnergyAnalyzer"
        const val FRAME_MS = 250L
        private const val LOW_PASS_HZ = 120.0
        private const val TIMEOUT_US = 10_000L
    }

    suspend fun analyze(audioUrl: String): TrackEnergyResult? = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(audioUrl), null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION) / 1000
            } else 0L

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var pcmFloat = false
            var alpha = lowPassAlpha(sampleRate)
            var samplesPerBucket = (sampleRate * FRAME_MS / 1000L).toInt().coerceAtLeast(1)

            val bucketRms = ArrayList<Double>(1024)
            var sumSquares = 0.0
            var sampleCount = 0
            var lowPassed = 0.0

            // Shared per-sample step for both PCM encodings
            fun accept(mono: Double) {
                lowPassed += alpha * (mono - lowPassed)
                sumSquares += lowPassed * lowPassed
                if (++sampleCount >= samplesPerBucket) {
                    bucketRms.add(sqrt(sumSquares / sampleCount))
                    sumSquares = 0.0
                    sampleCount = 0
                }
            }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone && isActive) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    pcmFloat = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    alpha = lowPassAlpha(sampleRate)
                    samplesPerBucket = (sampleRate * FRAME_MS / 1000L).toInt().coerceAtLeast(1)
                } else if (outIdx >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        if (pcmFloat) {
                            val fb = buf.order(ByteOrder.nativeOrder()).asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var mono = 0.0
                                repeat(channels) { mono += fb.get() }
                                accept(mono / channels)
                            }
                        } else {
                            val sb = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var mono = 0.0
                                repeat(channels) { mono += sb.get() / 32768.0 }
                                accept(mono / channels)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (!isActive) return@withContext null
            if (sampleCount > samplesPerBucket / 4) bucketRms.add(sqrt(sumSquares / sampleCount))
            if (bucketRms.isEmpty()) return@withContext null

            // Normalize against the 95th percentile, not the absolute max, so one stray
            // spike doesn't flatten the whole map.
            val sorted = bucketRms.sorted()
            val ref = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
            if (ref <= 0.0) return@withContext null
            val frames = ByteArray(bucketRms.size) { i ->
                ((bucketRms[i] / ref).coerceIn(0.0, 1.0) * 255).toInt().toByte()
            }
            val effectiveDuration = if (durationMs > 0) durationMs else bucketRms.size * FRAME_MS
            Log.d(TAG, "analyzed ${frames.size} frames over ${effectiveDuration}ms")
            TrackEnergyResult(frames, effectiveDuration)
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed: ${e.message}")
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun lowPassAlpha(sampleRate: Int): Double =
        1.0 - exp(-2.0 * PI * LOW_PASS_HZ / sampleRate)
}
