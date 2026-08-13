package com.haptiq.app.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.haptiq.app.BuildConfig
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * DEBUG-only kick latency instrumentation: how long the haptic pipeline really takes,
 * measured on the device instead of guessed.
 *
 * Three timestamps per kick, all on the monotonic clock (SystemClock.elapsedRealtime):
 *  1. CAPTURE — the FFT frame arrives on the Visualizer callback thread
 *     (BassVisualizer.processFft). Only live detection has this stage.
 *  2. DISPATCH — the vibrate() call leaves the app (HapticMapper.fireKick).
 *  3. ONSET   — the motor physically starts moving, detected via the gyroscope:
 *     vibration onset reads as a sharp angular-velocity spike shortly after dispatch.
 *
 * That yields capture→dispatch (DSP + dispatch overhead) and dispatch→onset (motor
 * spin-up — the part no Studio knob can see), plus the audio position each fire
 * happened at, so kick timing can be correlated with the music.
 *
 * AOT pre-fires (HapticMapper.scheduleKick) have no capture stage — for those,
 * dispatch→onset is exactly how early you must pre-fire to land the punch on the hit.
 *
 * Everything is gated behind BuildConfig.DEBUG: release builds never register the
 * gyro listener (battery) and never format a log string on the hot path. All stats
 * live in rolling capped samples, and summary lines print every
 * [SUMMARY_EVERY] confirmed onsets (per-fire lines on the callback thread are exactly
 * what the latency audit removed), plus one final summary on stop().
 */
class KickLatencyTracker {

    companion object {
        private const val TAG = "KickLatency"
        // Only listen for onset this long after dispatch — long enough for slow ERM
        // spin-up (~10-60ms) with margin, short enough that unrelated phone motion
        // after the kick can't false-trigger. Measured on the SENSOR clock (see
        // handleGyroEvent): sensor timestamps on some OEM ROMs (MTK/Tecno) are not on
        // the same base as SystemClock.elapsedRealtime, so any cross-clock comparison
        // silently breaks onset detection on those devices.
        private const val ONSET_WINDOW_MS = 300L
        // Gyro magnitude (rad/s) that counts as motor onset. Vibration onset reads as
        // a sharp rotation spike (typically several rad/s on a phone gyro); resting
        // hand-held is well under 0.5. Conservative by design — a too-high threshold
        // reports nothing (expire line shows the observed max for calibration), never
        // a wrong number.
        private const val ONSET_GYRO_THRESHOLD = 1.2f
        private const val MAX_SAMPLES = 200
        private const val SUMMARY_EVERY = 20
        // Surface feedback needs a few measured onsets before it says anything — a
        // single sample on an unusual surface (brief phone pick-up mid-song) would
        // otherwise skew the whole session.
        private const val MIN_SURFACE_SAMPLES = 8
        // AOT lookahead lead, derived from the measured dispatch→onset motor latency.
        // Until the gyro has confirmed a few onsets, fall back to the old fixed 50ms.
        private const val LEAD_FALLBACK_MS = 50L
        private const val LEAD_MIN_MS = 20L
        private const val LEAD_MAX_MS = 120L
        private const val LEAD_MIN_SAMPLES = 3
    }

    /**
     * Surface-feedback band mapping: how much to step the kick character based on the
     * mean gyro magnitude each kick actually moves the phone. The bands are starting
     * points to be calibrated on-device — the values ship logged on every onset
     * (`gyro=x.xx rad/s`), so the device test produces the real numbers.
     */
    internal fun characterShiftForMagnitude(mag: Float): Int = when {
        mag < 1.5f -> 2   // heavily damped (mattress/couch): step up two characters
        mag < 3.0f -> 1   // damped (table/case): step up one
        mag > 6.0f -> -1  // unusually free motion: one step less presence
        else -> 0
    }

    /** Capped rolling sample collector with the stats a latency report needs. */
    internal class LatencyStats(private val maxSamples: Int = MAX_SAMPLES) {
        private val samples = ArrayList<Float>(maxSamples)

        fun add(ms: Float) {
            if (samples.size >= maxSamples) samples.removeAt(0)
            samples.add(ms)
        }

        val count: Int get() = samples.size
        fun mean(): Float = if (samples.isEmpty()) 0f else samples.sum() / samples.size
        fun min(): Float = samples.minOrNull() ?: 0f
        fun max(): Float = samples.maxOrNull() ?: 0f

        fun percentile(p: Int): Float {
            if (samples.isEmpty()) return 0f
            val sorted = samples.sorted()
            val idx = ((p / 100f) * (sorted.size - 1)).toInt()
            return sorted[idx]
        }

        internal fun clear() = samples.clear()
    }

    private var sensorManager: SensorManager? = null
    private var gyro: Sensor? = null
    private var running = false

    private val liveCaptureToDispatch = LatencyStats()
    private val liveTotal = LatencyStats()          // capture→onset (live only)
    private val onsetAfterDispatch = LatencyStats() // dispatch→onset (live + AOT)
    private val onsetMagnitudes = LatencyStats()    // gyro rad/s at onset — surface feedback
    private var measuredOnsets = 0
    private var aotCount = 0
    private var confirmedOnsets = 0

    // The fire awaiting its motor onset. pendingDispatchAt == 0L means "no pending fire".
    private var pendingCaptureAt = 0L
    private var pendingDispatchAt = 0L
    private var pendingPositionMs = 0L
    private var pendingKind = ""
    // Onset window measured on the SENSOR clock: anchorSensorNanos is the first gyro
    // event after arming, and the window expires ONSET_WINDOW_MS of sensor time later.
    // Relative-only — immune to any mismatch between the sensor clock base and
    // elapsedRealtime (OEMs are not consistent about which base sensor timestamps use).
    private var anchorSensorNanos = 0L
    private var maxMagWhileArmed = 0f
    private val onsetWindowNanos = ONSET_WINDOW_MS * 1_000_000L

    @Synchronized
    fun start(context: Context) {
        if (running || !BuildConfig.DEBUG) return
        running = true
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro == null) {
            Log.w(TAG, "No gyroscope — motor onset can't be measured; capture→dispatch timing still logged")
        } else {
            sensorManager?.registerListener(gyroListener, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
        Log.i(TAG, "Kick latency tracking started (gyro=${gyro != null})")
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(gyroListener)
        sensorManager = null
        gyro = null
        logSummary("final")
        liveCaptureToDispatch.clear()
        liveTotal.clear()
        onsetAfterDispatch.clear()
        onsetMagnitudes.clear()
        measuredOnsets = 0
        aotCount = 0
        confirmedOnsets = 0
        pendingCaptureAt = 0L
        pendingDispatchAt = 0L
        anchorSensorNanos = 0L
        maxMagWhileArmed = 0f
    }

    /**
     * Called from the Visualizer callback thread the moment a kick is dispatched.
     * [captureAtElapsedMs] is the FFT frame arrival time (0 when there is none — AOT
     * pre-fires), [dispatchAtElapsedMs] right before vibrate(), [positionMs] the audio
     * position the fire corresponds to.
     */
    @Synchronized
    fun onKick(
        captureAtElapsedMs: Long,
        dispatchAtElapsedMs: Long,
        positionMs: Long,
        kind: String
    ) {
        if (!running) return
        if (captureAtElapsedMs > 0L && dispatchAtElapsedMs >= captureAtElapsedMs) {
            liveCaptureToDispatch.add((dispatchAtElapsedMs - captureAtElapsedMs).toFloat())
        }
        pendingCaptureAt = captureAtElapsedMs
        pendingDispatchAt = dispatchAtElapsedMs
        pendingPositionMs = positionMs
        pendingKind = kind
        // Re-arm the sensor-clock window. The anchor resets on the next gyro event.
        anchorSensorNanos = 0L
        maxMagWhileArmed = 0f
    }

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
            handleGyroEvent(event.timestamp, event.values)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    @Synchronized
    private fun handleGyroEvent(sensorNanos: Long, values: FloatArray) {
        if (!running || pendingDispatchAt == 0L) return
        val mag = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        maxMagWhileArmed = maxOf(maxMagWhileArmed, mag)
        if (anchorSensorNanos == 0L) {
            // First event after arming anchors the window on the sensor clock — no
            // cross-clock comparison anywhere, so OEM timestamp base differences
            // (MTK/Tecno) can't break the measurement.
            anchorSensorNanos = sensorNanos
            return
        }
        val sinceArmNanos = sensorNanos - anchorSensorNanos
        if (sinceArmNanos < 0L) return // out-of-order event; ignore
        if (sinceArmNanos > onsetWindowNanos) {
            // Window expired with no crossing — log what the motor actually produced
            // so the threshold can be calibrated per device class.
            if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "no onset [${pendingKind}] — max gyro in window=" +
                        String.format(Locale.US, "%.2f", maxMagWhileArmed) + " rad/s (threshold " +
                        String.format(Locale.US, "%.1f", ONSET_GYRO_THRESHOLD) + ") posMs=$pendingPositionMs"
                )
            }
            pendingDispatchAt = 0L
            anchorSensorNanos = 0L
            maxMagWhileArmed = 0f
            return
        }
        if (mag < ONSET_GYRO_THRESHOLD) return

        // Onset. Elapsed-time deltas equal the sensor-clock deltas (both monotonic),
        // so compute dispatch→onset purely from sensor time and only map back to
        // elapsedRealtime via the recorded dispatch stamp.
        val dispatchToOnset = sinceArmNanos / 1_000_000L
        onsetAfterDispatch.add(dispatchToOnset.toFloat())
        onsetMagnitudes.add(mag)
        measuredOnsets++
        val onsetElapsedMs = pendingDispatchAt + dispatchToOnset
        if (pendingCaptureAt > 0L) {
            liveTotal.add((onsetElapsedMs - pendingCaptureAt).toFloat())
        } else {
            aotCount++
        }
        confirmedOnsets++
        if (BuildConfig.DEBUG) {
            val total = if (pendingCaptureAt > 0L)
                String.format(Locale.US, "%.1f", (onsetElapsedMs - pendingCaptureAt).toFloat())
            else "-"
            Log.i(
                TAG,
                "onset [${pendingKind}] dispatch→onset=" +
                    String.format(Locale.US, "%.1f", dispatchToOnset.toFloat()) + "ms gyro=" +
                    String.format(Locale.US, "%.2f", mag) + " cap→onset=${total}ms posMs=$pendingPositionMs n=$confirmedOnsets"
            )
        }
        pendingDispatchAt = 0L // consumed — further spikes in this window are the same kick
        anchorSensorNanos = 0L
        maxMagWhileArmed = 0f
        if (confirmedOnsets % SUMMARY_EVERY == 0) logSummary("rolling")
    }

    /**
     * Surface feedback for HapticMapper: how many character steps up (damping
     * surface) or down (unusually free motion) kicks should be shifted, based on the
     * mean measured onset magnitude. Returns 0 until enough onsets are measured.
     */
    @Synchronized
    fun surfaceCharacterShift(): Int {
        if (measuredOnsets < MIN_SURFACE_SAMPLES) return 0
        return characterShiftForMagnitude(onsetMagnitudes.mean())
    }

    /**
     * Measured dispatch→onset motor latency (p50) — how early the AOT lookahead must
     * pre-fire so the motor peaks at the audio hit. This is the motor-calibration
     * number the old fixed 50ms lead guessed at: an ERM's real spin-up, measured on
     * THIS device while music actually plays. Falls back to 50ms until the gyro
     * confirms enough onsets, clamped to a sane range so one measurement anomaly
     * can't make the lead absurd.
     */
    @Synchronized
    fun motorLeadMs(): Long = motorLeadMsFor(onsetAfterDispatch)

    /** Pure p50→clamped-lead mapping, split out for unit testing. */
    internal fun motorLeadMsFor(stats: LatencyStats): Long {
        if (stats.count < LEAD_MIN_SAMPLES) return LEAD_FALLBACK_MS
        return stats.percentile(50).roundToInt()
            .toLong().coerceIn(LEAD_MIN_MS, LEAD_MAX_MS)
    }

    private fun logSummary(label: String) {
        if (!BuildConfig.DEBUG) return
        val capToDispatch = if (liveCaptureToDispatch.count > 0) {
            String.format(
                Locale.US, "cap→disp mean=%.1f p50=%.1f p95=%.1f (n=%d)",
                liveCaptureToDispatch.mean(), liveCaptureToDispatch.percentile(50),
                liveCaptureToDispatch.percentile(95), liveCaptureToDispatch.count
            )
        } else {
            "cap→disp n=0"
        }
        val dispToOnset = if (onsetAfterDispatch.count > 0) {
            String.format(
                Locale.US, "disp→onset mean=%.1f p50=%.1f p95=%.1f min=%.1f max=%.1f (n=%d)",
                onsetAfterDispatch.mean(), onsetAfterDispatch.percentile(50),
                onsetAfterDispatch.percentile(95), onsetAfterDispatch.min(),
                onsetAfterDispatch.max(), onsetAfterDispatch.count
            )
        } else {
            "disp→onset n=0"
        }
        val totals = if (liveTotal.count > 0) {
            String.format(
                Locale.US, "cap→onset mean=%.1f p95=%.1f (n=%d)",
                liveTotal.mean(), liveTotal.percentile(95), liveTotal.count
            )
        } else {
            "cap→onset n=0"
        }
        Log.i(TAG, "latency $label: $capToDispatch | $dispToOnset | $totals | aot=$aotCount")
    }
}
