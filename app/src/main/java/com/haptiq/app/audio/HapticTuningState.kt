package com.haptiq.app.audio

/**
 * Real-time tunable DSP parameters for the haptic engine.
 * Replaces all hardcoded constants in HapticMapper and BassAudioProcessor.
 * Updated via the Haptic Tuning Dashboard without recompilation.
 */
data class HapticTuningState(
    /**
     * Adaptive mode: gate thresholds continuously self-calibrate to the rolling
     * peak energy of the current music instead of using the fixed values below.
     * Fixes per-device/per-genre sensitivity mismatches automatically.
     */
    val isAdaptiveEnabled: Boolean = true,
    /** Enable/disable the kick transient engine entirely. */
    val isKickEnabled: Boolean = true,
    /**
     * Enable/disable the sub-bass drone engine entirely. Defaults OFF — the drone
     * engine caused a real regression (v1.11 stuck-buzz bug) and is being reworked;
     * kick is the one supported engine while bass is retuned. See HapticStudioScreen's
     * "Coming Soon" bass section — the Studio doesn't currently expose a way to turn
     * this back on from the UI, so this default is effectively the only value in play.
     */
    val isBassEnabled: Boolean = false,
    /**
     * AOT lookahead: pre-fire kicks from the per-track onset map (see
     * TrackEnergyAnalyzer.onsetsMs) LOOKAHEAD_LEAD_MS before the audio hit, so the motor
     * is already moving when the bass lands — live FFT detection is inherently ~50-70ms
     * late. Only active while playing songs that have a kick map; everything else keeps
     * live detection. Off by default until the scheduler is validated on-device.
     */
    val isAotLookaheadEnabled: Boolean = false,
    /** Kick delta threshold — how sharp a transient must be to trigger. Range: 0.01–0.50 */
    val kickThreshold: Float = 0.15f,
    /** Output gain multiplier for the kick punch engine. Range: 0.5–2.0 */
    val kickGain: Float = 1.0f,
    /** Absolute raw energy floor gate — filters out silence/noise. Range: 0.30–0.95 */
    val noiseFloorGate: Float = 0.70f,
    /** Low FFT bin index for kick/bass detection (inclusive). Range: 1–15 */
    val kickFreqMinBin: Int = 2,
    /** High FFT bin index for kick/bass detection (inclusive). Range: 1–15 */
    val kickFreqMaxBin: Int = 5,
    /** Minimum sustained sub-bass energy to trigger the drone. Range: 0.50–0.99 */
    val subDroneThreshold: Float = 0.78f,

    // ── NEW BASS SPECIFIC TUNING ──────────────────────────────────────────

    /** Low FFT bin index for sustained bass drone (inclusive). Range: 1–20 */
    val bassFreqMinBin: Int = 1,
    /** High FFT bin index for sustained bass drone (inclusive). Range: 1–20 */
    val bassFreqMaxBin: Int = 2,
    /** Output gain multiplier for the bass drone engine. Range: 0.5–2.0 */
    val bassGain: Float = 1.0f
)
