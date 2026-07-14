package com.haptiq.app.audio

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
    val subDroneThreshold: Float = 0.78f,
    val bassGain: Float = 1.0f
)
