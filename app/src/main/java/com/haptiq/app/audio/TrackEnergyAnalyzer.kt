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
class TrackEnergyResult(
    val frames: ByteArray,
    val durationMs: Long,
    /** Kick-attack times (ms into the track) for AOT lookahead scheduling. Empty = no onsets. */
    val onsetsMs: IntArray = IntArray(0),
    /** Beat-grid times (ms into the track). Empty = no steady beat detected (ballad, rubato). */
    val beatsMs: IntArray = IntArray(0)
)

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

        // ── AOT kick-onset detection ─────────────────────────────────────────────
        // The 250ms seek-preview buckets are too coarse to time a kick (one hit lands
        // inside a single bucket and is gone by the next). Lookahead scheduling needs
        // ATTACK positions, so a second, faster envelope — 40ms RMS of the same 120Hz
        // low-pass — is tracked in parallel in the same decode pass. A kick onset is a
        // sharp rise from a quiet level: ratio-gated (current window ≥ ATTACK_RATIO ×
        // previous) with a "quiet-before" floor derived from the track's own p95, and a
        // minimum inter-onset gap so one transient can't double-report across windows.
        private const val SHORT_MS = 40L
        private const val ATTACK_RATIO = 2.0
        private const val ONSET_FLOOR_RATIO = 0.35
        private const val ONSET_GAP_MS = 60L

        // ── Beat-grid detection ──────────────────────────────────────────────────
        // In the target genres the kick IS the beat, so a global autocorrelation of the
        // 40ms onset-strength envelope finds the dominant beat period; a phase histogram
        // then aligns the grid where onset energy accumulates. The lookahead scheduler
        // uses the grid to REJECT map false positives — onsets that don't land on a beat
        // (snare thumps, vocal artifacts with bass content). No steady beat (ballad,
        // rubato) → empty grid → nothing is rejected, raw onset behavior stays.
        private const val BEAT_MIN_LAG = 8     // 8 × 40ms = 320ms → 187 BPM max
        private const val BEAT_MAX_LAG = 30    // 30 × 40ms = 1200ms → 50 BPM min
        // A constant envelope has a flat autocorrelation; the chosen lag must stand out
        // this much above the mean, or the signal has no periodic beat at all.
        private const val BEAT_PEAK_RATIO = 1.3
        // Reject a half-tempo grid: if doubling the tempo correlates almost as well,
        // prefer the faster grid — a half-tempo grid would wrongly reject every other
        // real kick (which IS the dangerous failure mode for beat-validated onsets).
        private const val BEAT_HALF_TEMPO_RATIO = 0.85
        // How close an onset must be to a beat for lookahead to trust it (ms). Shared by
        // the scheduler's double-check and the Studio's on-beat count.
        const val BEAT_MATCH_TOLERANCE_MS = 90L

        /** Encode onset/beat times (ms) as 4-byte big-endian ints for Room storage. */
        fun encodeOnsets(timesMs: IntArray): ByteArray {
            val buf = java.nio.ByteBuffer.allocate(timesMs.size * 4)
            timesMs.forEach { buf.putInt(it) }
            return buf.array()
        }

        /** Decode onset/beat times from [encodeOnsets]'s format. Empty input → empty array. */
        fun decodeOnsets(bytes: ByteArray): IntArray {
            if (bytes.isEmpty()) return IntArray(0)
            val buf = java.nio.ByteBuffer.wrap(bytes)
            val out = IntArray(bytes.size / 4)
            var i = 0
            while (buf.remaining() >= 4) out[i++] = buf.int
            return out.copyOf(i)
        }

        /**
         * Detect a global beat grid from the 40ms short-RMS envelope. Pure DSP — unit-testable.
         * Empty result = no steady beat (the caller keeps raw onsets, rejecting nothing).
         */
        fun detectBeats(shortRms: List<Float>, durationMs: Long, windowMs: Long): IntArray {
            val n = shortRms.size
            if (n < BEAT_MAX_LAG + 4) return IntArray(0)

            // Onset strength = positive derivative of the envelope; a kick is a rise.
            val strength = FloatArray(n)
            var energy = 0.0
            for (i in 1 until n) {
                strength[i] = maxOf(0f, shortRms[i] - shortRms[i - 1])
                energy += strength[i] * strength[i]
            }
            if (energy <= 0.0) return IntArray(0)

            // Autocorrelation of the strength signal over candidate beat periods.
            val corr = DoubleArray(BEAT_MAX_LAG + 1)
            var bestLag = -1
            var bestCorr = 0.0
            var corrSum = 0.0
            for (lag in BEAT_MIN_LAG..BEAT_MAX_LAG) {
                var s = 0.0
                for (i in 0 until n - lag) s += strength[i] * strength[i + lag]
                corr[lag] = s / (n - lag)
                corrSum += corr[lag]
                if (corr[lag] > bestCorr) {
                    bestCorr = corr[lag]
                    bestLag = lag
                }
            }
            if (bestLag < 0 || bestCorr <= 0.0) return IntArray(0)
            // Flat/aperiodic signals: the peak must stand out above the mean correlation.
            val meanCorr = corrSum / (BEAT_MAX_LAG - BEAT_MIN_LAG + 1)
            if (bestCorr <= meanCorr * BEAT_PEAK_RATIO) return IntArray(0)
            // Half-tempo guard: prefer the faster grid while it correlates almost as well.
            while (bestLag % 2 == 0 && bestLag / 2 >= BEAT_MIN_LAG &&
                corr[bestLag / 2] >= corr[bestLag] * BEAT_HALF_TEMPO_RATIO
            ) {
                bestLag /= 2
            }

            // Phase: histogram onset strength by position within the period — the grid
            // locks onto where the transients actually accumulate, not one lucky anchor.
            val phaseScore = DoubleArray(bestLag)
            for (i in 1 until n) phaseScore[i % bestLag] += strength[i]
            var bestPhase = 0
            for (p in 1 until bestLag) if (phaseScore[p] > phaseScore[bestPhase]) bestPhase = p

            val periodMs = (bestLag * windowMs).toInt()
            val beats = ArrayList<Int>((durationMs / periodMs).toInt() + 2)
            var t = (bestPhase * windowMs).toInt()
            while (t <= durationMs) {
                beats.add(t)
                t += periodMs
            }
            return beats.toIntArray()
        }

        /**
         * True when [timeMs] lands within [toleranceMs] of a beat in [beatsMs]. An empty
         * grid (no steady beat) rejects nothing — raw onset behavior is preserved.
         */
        fun isOnBeat(timeMs: Int, beatsMs: IntArray, toleranceMs: Long = BEAT_MATCH_TOLERANCE_MS): Boolean {
            if (beatsMs.isEmpty()) return true
            var lo = 0
            var hi = beatsMs.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (beatsMs[mid] < timeMs) lo = mid + 1 else hi = mid
            }
            val dist = minOf(
                kotlin.math.abs(beatsMs[lo] - timeMs),
                if (lo > 0) kotlin.math.abs(beatsMs[lo - 1] - timeMs) else Int.MAX_VALUE
            )
            return dist <= toleranceMs
        }
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
            var shortSamplesPerBucket = (sampleRate * SHORT_MS / 1000L).toInt().coerceAtLeast(1)

            val bucketRms = ArrayList<Double>(1024)
            var sumSquares = 0.0
            var sampleCount = 0
            var lowPassed = 0.0

            // AOT kick-onset envelope: 40ms RMS windows over the same low-pass.
            var shortSumSquares = 0.0
            var shortSampleCount = 0
            var prevShortRms = 0.0
            var totalSamples = 0L
            val shortRmsList = ArrayList<Float>(4096)
            val candidateTimeMs = ArrayList<Int>(256)
            val candidateRms = ArrayList<Float>(256)
            val candidatePrevRms = ArrayList<Float>(256)

            // Shared per-sample step for both PCM encodings
            fun accept(mono: Double) {
                lowPassed += alpha * (mono - lowPassed)
                sumSquares += lowPassed * lowPassed
                if (++sampleCount >= samplesPerBucket) {
                    bucketRms.add(sqrt(sumSquares / sampleCount))
                    sumSquares = 0.0
                    sampleCount = 0
                }
                // Short envelope runs in the same pass — cost is one extra square + compare
                // per sample, dwarfed by the codec work itself.
                totalSamples++
                shortSumSquares += lowPassed * lowPassed
                if (++shortSampleCount >= shortSamplesPerBucket) {
                    val rms = sqrt(shortSumSquares / shortSampleCount)
                    shortRmsList.add(rms.toFloat())
                    if (prevShortRms > 0.0 && rms >= prevShortRms * ATTACK_RATIO) {
                        val endMs = (totalSamples * 1000L / sampleRate).toInt()
                        candidateTimeMs.add((endMs - (SHORT_MS / 2).toInt()).coerceAtLeast(0))
                        candidateRms.add(rms.toFloat())
                        candidatePrevRms.add(prevShortRms.toFloat())
                    }
                    prevShortRms = rms
                    shortSumSquares = 0.0
                    shortSampleCount = 0
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
                    shortSamplesPerBucket = (sampleRate * SHORT_MS / 1000L).toInt().coerceAtLeast(1)
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

            // Post-filter onset candidates: keep only attacks that rose from a genuinely
            // quiet level (previous window below the track's own p95 × ONSET_FLOOR_RATIO)
            // AND reached a real level themselves (at least that same floor) — a sustained
            // swell rises across many windows but never from quiet, so it drops out here —
            // and enforce the minimum gap so one transient can't double-report.
            val onsetsMs = if (candidateTimeMs.isEmpty()) IntArray(0) else {
                val sorted = shortRmsList.sorted()
                val p95 = sorted[sorted.size * 95 / 100]
                val quietFloor = (p95 * ONSET_FLOOR_RATIO).coerceAtLeast(1e-4)
                val result = ArrayList<Int>(candidateTimeMs.size)
                var lastOnset = -ONSET_GAP_MS.toInt()
                for (i in candidateTimeMs.indices) {
                    val t = candidateTimeMs[i]
                    if (candidatePrevRms[i] < quietFloor && candidateRms[i] >= quietFloor &&
                        t - lastOnset >= ONSET_GAP_MS
                    ) {
                        result.add(t)
                        lastOnset = t
                    }
                }
                result.toIntArray()
            }

            // Normalize against the 95th percentile, not the absolute max, so one stray
            // spike doesn't flatten the whole map.
            val sorted = bucketRms.sorted()
            val ref = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
            if (ref <= 0.0) return@withContext null
            val frames = ByteArray(bucketRms.size) { i ->
                ((bucketRms[i] / ref).coerceIn(0.0, 1.0) * 255).toInt().toByte()
            }
            val effectiveDuration = if (durationMs > 0) durationMs else bucketRms.size * FRAME_MS
            // Global beat grid from the same 40ms envelope — used by lookahead to reject
            // off-beat onset false positives. Empty for tracks without a steady beat.
            val beatsMs = detectBeats(shortRmsList, effectiveDuration, SHORT_MS)
            Log.d(TAG, "analyzed ${frames.size} frames over ${effectiveDuration}ms, " +
                "${onsetsMs.size} kick onsets, ${beatsMs.size} beats")
            TrackEnergyResult(frames, effectiveDuration, onsetsMs, beatsMs)
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
