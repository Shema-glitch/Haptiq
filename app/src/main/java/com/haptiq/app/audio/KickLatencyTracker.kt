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
        // after the kick can't false-trigger.
        private const val ONSET_WINDOW_MS = 300L
        // Gyro magnitude (rad/s) that counts as motor onset. Vibration onset reads as
        // a sharp rotation spike (typically several rad/s on a phone gyro); resting
        // hand-held is well under 0.5.
        private const val ONSET_GYRO_THRESHOLD = 1.5f
        private const val MAX_SAMPLES = 200
        private const val SUMMARY_EVERY = 20
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
    private var aotCount = 0
    private var confirmedOnsets = 0

    // The fire awaiting its motor onset. pendingDispatchAt == 0L means "no pending fire".
    private var pendingCaptureAt = 0L
    private var pendingDispatchAt = 0L
    private var pendingPositionMs = 0L
    private var pendingKind = ""
    private var armedUntilElapsedMs = 0L

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
        Log.d(TAG, "Kick latency tracking started (gyro=${gyro != null})")
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
        aotCount = 0
        confirmedOnsets = 0
        pendingCaptureAt = 0L
        pendingDispatchAt = 0L
        armedUntilElapsedMs = 0L
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
        armedUntilElapsedMs = dispatchAtElapsedMs + ONSET_WINDOW_MS
    }

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            if (sqrt(x * x + y * y + z * z) < ONSET_GYRO_THRESHOLD) return
            // SensorEvent.timestamp uses the same time base as
            // SystemClock.elapsedRealtimeNanos(), so ms here is comparable to the
            // elapsedRealtime() stamps recorded at capture/dispatch.
            handleOnset(event.timestamp / 1_000_000L)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    @Synchronized
    private fun handleOnset(eventElapsedMs: Long) {
        if (!running || pendingDispatchAt == 0L) return
        if (eventElapsedMs !in pendingDispatchAt..armedUntilElapsedMs) return
        val dispatchToOnset = eventElapsedMs - pendingDispatchAt
        if (dispatchToOnset < 0L) return

        onsetAfterDispatch.add(dispatchToOnset.toFloat())
        if (pendingCaptureAt > 0L) {
            liveTotal.add((eventElapsedMs - pendingCaptureAt).toFloat())
        } else {
            aotCount++
        }
        confirmedOnsets++
        if (BuildConfig.DEBUG) {
            val total = if (pendingCaptureAt > 0L)
                String.format(Locale.US, "%.1f", (eventElapsedMs - pendingCaptureAt).toFloat())
            else "-"
            Log.d(
                TAG,
                "onset [${pendingKind}] dispatch→onset=${String.format(Locale.US, "%.1f", dispatchToOnset.toFloat())}ms " +
                    "cap→onset=${total}ms posMs=$pendingPositionMs n=$confirmedOnsets"
            )
        }
        pendingDispatchAt = 0L // consumed — further spikes in this window are the same kick
        armedUntilElapsedMs = 0L
        if (confirmedOnsets % SUMMARY_EVERY == 0) logSummary("rolling")
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
        Log.d(TAG, "latency $label: $capToDispatch | $dispToOnset | $totals | aot=$aotCount")
    }
}
