package com.haptiq.app.audio

import android.media.audiofx.Visualizer
import android.os.Process
import android.util.Log
import com.haptiq.app.audio.HapticTuningState

class BassVisualizer(
    private val fftProcessor: FftProcessor,
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
    private var attachTime = 0L

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
            // Set thread priority once here — the Visualizer callback runs on a dedicated
            // HandlerThread, so this call applies to that thread for the lifetime of the session.
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) } catch (_: Exception) {}
            prevRawKick = 0f
            attachTime = System.currentTimeMillis()
            Log.d(TAG, "Visualizer attached to session $audioSessionId with capture size ${visualizer?.captureSize}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Visualizer: ${e.message}")
        }
    }

    private fun processFft(fft: ByteArray) {
        if (fft.size < 4) return

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
        val kickDelta = if (!t.isKickEnabled || System.currentTimeMillis() - attachTime < 500L) {
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

        // Pass the live tuning thresholds through BassEnergy so HapticMapper
        // can apply the correct gate values without needing its own copy of the state.
        onBassEnergy(
            BassEnergy(
                subBass = if (t.isBassEnabled) spectrum[0] else 0f,
                bass = rawKick,
                combined = kickDelta,
                rawKick = rawKick,
                spectrum = spectrum,
                dominantPitch = dominantPitch,
                kickThreshold = t.kickThreshold,
                noiseFloorGate = t.noiseFloorGate,
                subDroneThreshold = t.subDroneThreshold
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
