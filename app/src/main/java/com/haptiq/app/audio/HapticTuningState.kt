package com.haptiq.app.audio

/**
 * Real-time tunable DSP parameters for the haptic engine.
 * Replaces all hardcoded constants in HapticMapper and BassAudioProcessor.
 * Updated via the Haptic Tuning Dashboard without recompilation.
 */
data class HapticTuningState(
    /** Enable/disable the kick transient engine entirely. */
    val isKickEnabled: Boolean = true,
    /** Enable/disable the sub-bass drone engine entirely. */
    val isBassEnabled: Boolean = true,
    /** Kick delta threshold — how sharp a transient must be to trigger. Range: 0.01–0.50 */
    val kickThreshold: Float = 0.15f,
    /** Absolute raw energy floor gate — filters out silence/noise. Range: 0.30–0.95 */
    val noiseFloorGate: Float = 0.70f,
    /** Low FFT bin index for kick/bass detection (inclusive). Range: 1–15 */
    val kickFreqMinBin: Int = 1,
    /** High FFT bin index for kick/bass detection (inclusive). Range: 1–15 */
    val kickFreqMaxBin: Int = 3,
    /** Minimum sustained sub-bass energy to trigger the drone. Range: 0.50–0.99 */
    val subDroneThreshold: Float = 0.78f
)
