package com.haptiq.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin

/**
 * Processes PCM audio data using a Cooley-Tukey FFT to extract bass frequency energy.
 *
 * Sub-bass: 20-60 Hz
 * Bass: 60-250 Hz
 *
 * Returns normalized 0.0-1.0 bass energy values for haptic mapping.
 * No external dependencies — pure Kotlin implementation.
 */
class FftProcessor(
    private val sampleRate: Int = 44100,
    private val fftSize: Int = 1024
) {
    private val magnitudes = FloatArray(fftSize / 2)

    // Frequency resolution: sampleRate / fftSize
    private val freqResolution = sampleRate.toFloat() / fftSize

    // Band indices for sub-bass (20-60 Hz) and bass (60-250 Hz)
    private val subBassStart = (20 / freqResolution).toInt().coerceAtLeast(1)
    private val subBassEnd = (60 / freqResolution).toInt().coerceAtMost(fftSize / 2 - 1)
    private val bassStart = (60 / freqResolution).toInt().coerceAtLeast(1)
    private val bassEnd = (250 / freqResolution).toInt().coerceAtMost(fftSize / 2 - 1)

    // Pre-computed twiddle factors for FFT
    private val cosTable = FloatArray(fftSize / 2)
    private val sinTable = FloatArray(fftSize / 2)

    init {
        for (i in 0 until fftSize / 2) {
            cosTable[i] = cos(2.0 * PI * i / fftSize).toFloat()
            sinTable[i] = sin(2.0 * PI * i / fftSize).toFloat()
        }
    }

    /**
     * Process PCM float samples and return bass energy.
     * @param samples PCM audio samples (float, -1.0 to 1.0)
     * @return BassEnergy with subBass and bass values normalized to 0.0-1.0
     */
    fun process(samples: FloatArray): BassEnergy {
        // Prepare real and imaginary arrays
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val copySize = minOf(samples.size, fftSize)
        System.arraycopy(samples, 0, real, 0, copySize)

        // Apply Hanning window
        applyHanningWindow(real)

        // Perform FFT
        fft(real, imag)

        // Compute magnitudes
        for (i in 0 until fftSize / 2) {
            magnitudes[i] = hypot(real[i], imag[i])
        }

        // Extract sub-bass energy (20-60 Hz)
        var subBassSum = 0f
        var subBassCount = 0
        for (i in subBassStart..subBassEnd) {
            subBassSum += magnitudes[i]
            subBassCount++
        }
        val subBassRaw = if (subBassCount > 0) subBassSum / subBassCount else 0f

        // Extract bass energy (60-250 Hz)
        var bassSum = 0f
        var bassCount = 0
        for (i in bassStart..bassEnd) {
            bassSum += magnitudes[i]
            bassCount++
        }
        val bassRaw = if (bassCount > 0) bassSum / bassCount else 0f

        // Normalize to 0.0-1.0 using logarithmic scaling
        val subBass = normalizeMagnitude(subBassRaw)
        val bass = normalizeMagnitude(bassRaw)

        // Combined bass energy (weighted: sub-bass contributes more to haptics)
        val combined = (subBass * 0.6f + bass * 0.4f).coerceIn(0f, 1f)

        return BassEnergy(
            subBass = subBass,
            bass = bass,
            combined = combined,
            rawKick = 0f,
            spectrum = FloatArray(10) { 0f },
            dominantPitch = DominantPitch.KICK
        )
    }

    /**
     * Process raw PCM byte array (16-bit signed, little-endian).
     * Used with ExoPlayer's AudioProcessor output.
     */
    fun processPcmBytes(pcmData: ByteArray): BassEnergy {
        val samples = FloatArray(pcmData.size / 2)
        for (i in samples.indices) {
            val lo = pcmData[i * 2].toInt() and 0xFF
            val hi = pcmData[i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo
            samples[i] = sample.toFloat() / 32768f
        }
        return process(samples)
    }

    /**
     * In-place Cooley-Tukey FFT (radix-2, decimation-in-time).
     * Input: real[] and imag[] arrays of length fftSize (must be power of 2).
     * Output: DFT coefficients in real[] and imag[].
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = fftSize
        var j = 0

        // Bit-reversal permutation
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Butterfly computation
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until halfLen) {
                    val idx = k * step
                    val tReal = cosTable[idx] * real[i + k + halfLen] - sinTable[idx] * imag[i + k + halfLen]
                    val tImag = sinTable[idx] * real[i + k + halfLen] + cosTable[idx] * imag[i + k + halfLen]
                    real[i + k + halfLen] = real[i + k] - tReal
                    imag[i + k + halfLen] = imag[i + k] - tImag
                    real[i + k] = real[i + k] + tReal
                    imag[i + k] = imag[i + k] + tImag
                }
                i += len
            }
            len *= 2
        }
    }

    private fun applyHanningWindow(buffer: FloatArray) {
        val n = buffer.size
        for (i in 0 until n) {
            val multiplier = 0.5f * (1 - cos(2.0 * PI * i / (n - 1))).toFloat()
            buffer[i] *= multiplier
        }
    }

    private fun normalizeMagnitude(magnitude: Float): Float {
        if (magnitude <= 0f) return 0f
        // Convert to dB scale, then normalize
        val db = 20 * log10(magnitude.toDouble()).toFloat()
        // Map typical range [-60dB, 0dB] to [0.0, 1.0]
        return ((db + 60f) / 60f).coerceIn(0f, 1f)
    }
}

enum class DominantPitch { SUB, KICK }

data class BassEnergy(
    val subBass: Float,   // 20-60 Hz, normalized 0.0-1.0
    val bass: Float,      // 60-250 Hz, normalized 0.0-1.0
    val combined: Float,  // Weighted combination / kick delta
    val rawKick: Float = 0f,
    val spectrum: FloatArray = FloatArray(10) { 0f },
    val dominantPitch: DominantPitch = DominantPitch.KICK,
    // Live DSP thresholds — passed from UI tuning state via BassAudioProcessor
    val kickThreshold: Float = 0.15f,
    val noiseFloorGate: Float = 0.70f,
    val subDroneThreshold: Float = 0.78f
)
