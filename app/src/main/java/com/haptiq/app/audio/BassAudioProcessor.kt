package com.haptiq.app.audio

import android.media.audiofx.Visualizer
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.haptiq.app.BuildConfig
import com.haptiq.app.audio.HapticTuningState

class BassVisualizer(
    private val onBassEnergy: (BassEnergy) -> Unit
) {
    companion object {
        private const val TAG = "BassVisualizer"
        private const val CAPTURE_SIZE = 1024
    }

    private val magnitudeBuffer = FloatArray(CAPTURE_SIZE / 2)
    private var visualizer: Visualizer? = null
    private var isReleased = false

    private var prevRawKick = 0f
    private var prevClickEnergy = 0f
    private var attachTime = 0L

    // The Visualizer capture listener runs on a system callback (binder) thread, NOT the
    // thread that called attach() — so its priority can only be set from inside the
    // callback itself. Set once on the first frame; see processFft().
    private var callbackPrioritySet = false

    // Rolling 1s window stats, logged for on-device threshold calibration
    private var statWindowStart = 0L
    private var statMaxKick = 0f
    private var statMaxSub = 0f
    private var statMaxDelta = 0f
    private var statMaxClick = 0f

    // Adaptive gates: rolling peaks with per-frame exponential decay (~10s memory
    // at ~19Hz capture). Gates track the music's own energy envelope, so a quiet
    // ballad and a heavy drop both trigger correctly on any device.
    private var rollingKickPeak = 0f
    private var rollingSubPeak = 0f
    private val peakDecay = 0.994f       // per-frame decay of the rolling peak
    private val kickGateRatio = 0.62f    // fire kicks above 62% of recent peak
    private val subGateRatio = 0.80f     // sustain drone above 80% of recent peak
    private val minSignal = 0.12f        // absolute floor: silence never triggers
    // Kick's own absolute floor, higher than the shared minSignal. With bass off,
    // kick is the only engine — a loud intro/chorus followed by a quiet bridge left
    // rollingKickPeak elevated for ~10s (peakDecay's memory), so 62% of that stale
    // peak could still sit above minSignal and let room noise/breath-quiet passages
    // false-trigger. This is checked ADDITIONALLY to the ratio gate, never replacing
    // it, so genuinely quiet songs still get a low, adaptive threshold — it just
    // can't go arbitrarily low.
    private val kickSilenceFloor = 0.18f

    // Smoothed continuous bass envelope (0..1) — see the "CONTINUOUS BASS ENVELOPE"
    // block in processFft() for how it's derived and why.
    private var subEnvelopeSmoothed = 0f

    /** Live DSP tuning — written from UI thread, read on Visualizer callback thread. */
    @Volatile var tuning = HapticTuningState()

    fun attach(audioSessionId: Int) {
        if (isReleased) return
        try {
            release()

            visualizer = Visualizer(audioSessionId).apply {
                captureSize = CAPTURE_SIZE.coerceIn(
                    Visualizer.getCaptureSizeRange()[0],
                    Visualizer.getCaptureSizeRange()[1]
                )
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer, waveform: ByteArray, samplingRate: Int) {}
                    override fun onFftDataCapture(vis: Visualizer, fft: ByteArray, samplingRate: Int) {
                        processFft(fft)
                    }
                }, Visualizer.getMaxCaptureRate(), false, true) // Max capture rate for speed

                enabled = true
            }
            // NOTE: thread priority is deliberately NOT set here — attach() runs on the
            // main thread, and the Visualizer callback runs on a separate system thread, so
            // setThreadPriority here would promote the wrong thread. It's set once on the
            // first callback frame in processFft() instead.
            prevRawKick = 0f
            prevClickEnergy = 0f
            // Self-calibration restarts per track. The rolling peaks decay with ~10s memory
            // (peakDecay), so without this a loud song leaves rollingKickPeak/rollingSubPeak
            // elevated and the ADAPTIVE gates stay too high for the first ~10s of the next,
            // quieter song — kicks that should fire get swallowed. Resetting on every attach
            // makes each track transition start from silence and re-converge in a few frames.
            rollingKickPeak = 0f
            rollingSubPeak = 0f
            subEnvelopeSmoothed = 0f
            statWindowStart = 0L
            statMaxKick = 0f; statMaxSub = 0f; statMaxDelta = 0f; statMaxClick = 0f
            attachTime = SystemClock.elapsedRealtime()
            Log.d(TAG, "Visualizer attached to session $audioSessionId with capture size ${visualizer?.captureSize}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Visualizer: ${e.message}")
        }
    }

    private fun processFft(fft: ByteArray) {
        if (fft.size < 4) return

        // DEBUG kick-latency instrumentation: stamp the frame the moment it arrives on
        // the callback thread — the "capture" endpoint of capture→dispatch→motor-onset
        // (KickLatencyTracker). One elapsedRealtime read per frame, zero string work.
        val captureAtElapsedMs = SystemClock.elapsedRealtime()

        // First frame: this IS the timing-critical capture-to-vibrate thread (a system
        // binder thread the framework uses for Visualizer capture callbacks). Promote it
        // once so kick/drone dispatch stays snappy when the device is under load.
        if (!callbackPrioritySet) {
            callbackPrioritySet = true
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) } catch (_: Exception) {}
        }

        val halfN = minOf(fft.size / 2, magnitudeBuffer.size)
        magnitudeBuffer[0] = kotlin.math.abs(fft[0].toFloat())
        for (i in 1 until halfN) {
            val real = fft[i * 2].toFloat()
            val imag = fft[i * 2 + 1].toFloat()
            magnitudeBuffer[i] = kotlin.math.sqrt(real * real + imag * imag)
        }

        // Snapshot the tuning state once so all reads below are consistent on this frame
        val t = tuning

        // ── RAW KICK ENERGY ────────────────────────────────────────────────────────────
        // Average FFT bins across the dynamically tuned frequency range.
        // kickFreqMinBin and kickFreqMaxBin come from the UI sliders (default: 1–3).
        val minBin = t.kickFreqMinBin.coerceIn(1, halfN - 1)
        val maxBin = t.kickFreqMaxBin.coerceIn(minBin, halfN - 1)
        var binSum = 0f
        var binCount = 0
        for (b in minBin..maxBin) {
            binSum += magnitudeBuffer[b]
            binCount++
        }
        val rawKick = normalize(if (binCount > 0) binSum / binCount else 0f)

        // ── KICK DELTA ────────────────────────────────────────────────────────────────
        // Gated by kick engine toggle and 500ms warm-up lockout
        val kickDelta = if (!t.isKickEnabled || SystemClock.elapsedRealtime() - attachTime < 500L) {
            0f
        } else {
            (rawKick - prevRawKick).coerceAtLeast(0f)
        }
        prevRawKick = rawKick

        // Group the 512 useful bins into 10 distinct logarithmic frequency bands for the UI
        val spectrum = FloatArray(10)

        // Helper to average bins in a range (inclusive)
        fun averageBins(start: Int, end: Int): Float {
            var sum = 0f
            var count = 0
            for (i in start..end) {
                if (i < halfN) {
                    sum += magnitudeBuffer[i]
                    count++
                }
            }
            return if (count > 0) normalize(sum / count) else 0f
        }

        // ── LIVE BASS DRONE ENERGY ──────────────────────────────────────────────────
        // Calculate sub-bass energy based on live tuning freq bins
        val bassMinBin = t.bassFreqMinBin.coerceIn(1, halfN - 1)
        val bassMaxBin = t.bassFreqMaxBin.coerceIn(bassMinBin, halfN - 1)
        val tunedSubBass = averageBins(bassMinBin, bassMaxBin)

        // Bar 0 (Sub): Bin 1 (~43 Hz)
        spectrum[0] = normalize(magnitudeBuffer[1])
        // Bar 1 (Kick): Bins 2-3 (~86 - 129 Hz)
        spectrum[1] = averageBins(2, 3)
        // Bar 2 (Low Mid): Bins 4-7 (~172 - 300 Hz)
        spectrum[2] = averageBins(4, 7)
        // Bar 3 (Mid 1): Bins 8-15 (~344 - 645 Hz)
        spectrum[3] = averageBins(8, 15)
        // Bar 4 (Mid 2): Bins 16-31 (~688 - 1.3 kHz)
        spectrum[4] = averageBins(16, 31)
        // Bar 5 (High Mid): Bins 32-63 (~1.3 - 2.7 kHz)
        spectrum[5] = averageBins(32, 63)
        // Bar 6 (High 1): Bins 64-127 (~2.7 - 5.5 kHz)
        spectrum[6] = averageBins(64, 127)
        // Bar 7 (High 2): Bins 128-255 (~5.5 - 11 kHz)
        spectrum[7] = averageBins(128, 255)
        // Bar 8 (Air 1): Bins 256-383 (~11 - 16 kHz)
        spectrum[8] = averageBins(256, 383)
        // Bar 9 (Air 2): Bins 384-511 (~16 - 22 kHz)
        spectrum[9] = averageBins(384, 511)

        // Determine dominant pitch: Sub (Bar 0) vs Kick (Bar 1) energy
        val dominantPitch = if (spectrum[0] > spectrum[1]) DominantPitch.SUB else DominantPitch.KICK

        // ── MOMENTARY CLICK / SPECTRAL BRIGHTNESS ─────────────────────────────────
        // A real kick drum carries a beater "click" — a simultaneous burst in the
        // 1.3–5.5kHz bands — while a pure 808 attack is all sub with no click. This
        // classifies the transient's CHARACTER (drum kick vs 808 attack); it never
        // gates whether the punch fires. Bands are already computed for the UI, so
        // this costs one max + one subtraction per frame.
        val clickEnergy = maxOf(spectrum[5], spectrum[6])
        val clickDelta = (clickEnergy - prevClickEnergy).coerceAtLeast(0f)
        prevClickEnergy = clickEnergy
        val clickTransient = clickDelta > 0.05f

        // 1s-window peak stats — cheap (3 compares/frame + 1 log line/sec) and the only
        // way to calibrate gate thresholds against a real device's FFT magnitude range.
        // Debug-gated: the per-second line is fine, but release builds ship with minify
        // OFF, so unstripped Log.d at ~1 line/sec still churns logd and the callback
        // thread can stall when the log buffer fills — exactly when kick timing matters.
        val now = SystemClock.elapsedRealtime()
        statMaxKick = maxOf(statMaxKick, rawKick)
        statMaxSub = maxOf(statMaxSub, tunedSubBass)
        statMaxDelta = maxOf(statMaxDelta, kickDelta)
        statMaxClick = maxOf(statMaxClick, clickDelta)
        if (BuildConfig.DEBUG && now - statWindowStart >= 1000L) {
            Log.d(TAG, "1s peaks: rawKick=$statMaxKick sub=$statMaxSub delta=$statMaxDelta click=$statMaxClick gates(k=${t.noiseFloorGate} s=${t.subDroneThreshold} d=${t.kickThreshold})")
            statWindowStart = now
            statMaxKick = 0f; statMaxSub = 0f; statMaxDelta = 0f; statMaxClick = 0f
        }

        // ── ADAPTIVE GATES ─────────────────────────────────────────────────
        // Track rolling peaks of the music's own energy; in adaptive mode the
        // gates are ratios of those peaks (clamped to a silence floor) instead
        // of the fixed slider values — self-calibrating per device and genre.
        rollingKickPeak = maxOf(rawKick, rollingKickPeak * peakDecay)
        rollingSubPeak = maxOf(tunedSubBass, rollingSubPeak * peakDecay)
        val effNoiseFloor = if (t.isAdaptiveEnabled)
            (rollingKickPeak * kickGateRatio).coerceAtLeast(kickSilenceFloor) else t.noiseFloorGate
        val effSubDrone = if (t.isAdaptiveEnabled)
            (rollingSubPeak * subGateRatio).coerceAtLeast(minSignal) else t.subDroneThreshold
        val effKickDelta = if (t.isAdaptiveEnabled)
            (rollingKickPeak * 0.05f).coerceAtLeast(0.02f) else t.kickThreshold

        // ── CONTINUOUS BASS ENVELOPE ──────────────────────────────────────────────
        // How loud is bass RIGHT NOW relative to this song's own recent bass peak —
        // self-calibrating per track/device, same philosophy as the adaptive gates
        // above. Feeding this straight to the drone amplitude (instead of a hard
        // on/off gate + squared post-threshold scale) is what turns the haptic into a
        // continuous "speaker wave" that rises and falls with the bassline, rather
        // than a flat buzz that's either fully off or pinned near max.
        val subEnvelopeRaw = (tunedSubBass / rollingSubPeak.coerceAtLeast(minSignal)).coerceIn(0f, 1f)
        // Asymmetric follower: snap up fast on a swell, ease down slower on the way
        // out so it reads as one continuous wave instead of flickering frame to frame.
        subEnvelopeSmoothed += (subEnvelopeRaw - subEnvelopeSmoothed) *
            (if (subEnvelopeRaw > subEnvelopeSmoothed) 0.6f else 0.15f)

        // Pass the live tuning thresholds through BassEnergy so HapticMapper
        // can apply the correct gate values without needing its own copy of the state.
        onBassEnergy(
            BassEnergy(
                subBass = if (t.isBassEnabled) tunedSubBass else 0f,
                bass = rawKick,
                combined = kickDelta,
                rawKick = rawKick,
                spectrum = spectrum,
                dominantPitch = dominantPitch,
                kickThreshold = effKickDelta,
                noiseFloorGate = effNoiseFloor,
                subDroneThreshold = effSubDrone,
                bassGain = t.bassGain,
                kickGain = t.kickGain,
                subEnvelope = if (t.isBassEnabled) subEnvelopeSmoothed else 0f,
                clickTransient = clickTransient,
                captureAtElapsedMs = captureAtElapsedMs
            )
        )
    }

    private fun normalize(magnitude: Float): Float {
        if (magnitude <= 0f) return 0f
        val db = 20 * kotlin.math.log10((magnitude / 181.0f).toDouble()).toFloat()
        return ((db + 60f) / 60f).coerceIn(0f, 1f)
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Visualizer: ${e.message}")
        }
        visualizer = null
    }

    fun detach() {
        release()
        isReleased = true
    }
}
