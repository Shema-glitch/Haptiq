package com.haptiq.app.audio

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class HapticMapper {

    private var lastKickTime = 0L
    private var lastDroneTime = 0L

    // Tracks whether a drone was dispatched — used to gate the sidechain cancel
    private var isDronePlaying = false

    companion object {
        const val KICK_COOLDOWN_MS   = 80L
        const val DRONE_LOCKOUT_MS   = 200L
        const val DRONE_COOLDOWN_MS  = 50L

        // Bass amplitude ceiling — always softer than kick so kick wins perceptually
        const val DRONE_MAX_AMP      = 140
        const val DRONE_MIN_AMP      = 60
    }

    fun mapAndFire(
        dominantPitch: DominantPitch,
        subBassEnergy: Float,
        kickDelta: Float,
        rawKick: Float,
        presetId: String,
        intensity: Int,
        calibrationMultiplier: Float,
        batterySaverEnabled: Boolean,
        vibrator: Vibrator?,
        // Live-tuned thresholds — come from BassEnergy (sourced from HapticTuningState via UI)
        kickThreshold: Float = 0.15f,
        noiseFloorGate: Float = 0.70f,
        subDroneThreshold: Float = 0.78f
    ) {
        val now = System.currentTimeMillis()
        var intensityScalar = (intensity / 100f) * calibrationMultiplier
        if (batterySaverEnabled) intensityScalar *= 0.70f

        // ── ENGINE 1: KICK ────────────────────────────────────────────────────────────
        if (kickDelta > kickThreshold && rawKick > noiseFloorGate && (now - lastKickTime > KICK_COOLDOWN_MS)) {

            // SIDECHAIN: cancel the drone only on the transition from drone→kick
            if (isDronePlaying) {
                try { vibrator?.cancel() } catch (_: Exception) {}
                isDronePlaying = false
            }

            val kickScale = (0.70f + kickDelta * 0.30f).coerceIn(0.70f, 1.00f) * intensityScalar
            fireKick(vibrator, kickScale)
            lastKickTime = now
            return
        }

        // ── ENGINE 2: SUB-BASS DRONE ──────────────────────────────────────────────────
        // subBassEnergy is already zeroed out by BassAudioProcessor when isBassEnabled = false
        if (subBassEnergy > subDroneThreshold && (now - lastKickTime > DRONE_LOCKOUT_MS)) {
            if (now - lastDroneTime > DRONE_COOLDOWN_MS) {
                val normalised = ((subBassEnergy - subDroneThreshold) / (1.0f - subDroneThreshold))
                    .coerceIn(0f, 1f)
                val decayed = normalised * normalised
                val droneAmplitude = (DRONE_MIN_AMP + decayed * (DRONE_MAX_AMP - DRONE_MIN_AMP))
                    .toInt().coerceIn(DRONE_MIN_AMP, DRONE_MAX_AMP)
                val droneScale = (droneAmplitude / 255f) * intensityScalar

                fireDrone(vibrator, droneScale)
                isDronePlaying = true
                lastDroneTime = now
            }
        } else {
            isDronePlaying = false
        }
    }

    // ── KICK: PRIMITIVE_CLICK if supported, waveform fallback otherwise ───────────────
    private fun fireKick(vibrator: Vibrator?, scale: Float) {
        if (vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale.coerceIn(0f, 1f))
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (scale * 0.75f).coerceIn(0f, 1f), 15)
                    .compose()
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (scale * 255f).toInt().coerceIn(180, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 25, 10),
                    intArrayOf(0, amplitude, 0),
                    -1
                ))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30L)
            }
        } catch (_: Exception) {}
    }

    // ── DRONE: PRIMITIVE_THUD if supported, waveform fallback otherwise ───────────────
    private fun fireDrone(vibrator: Vibrator?, scale: Float) {
        if (vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scale.coerceIn(0f, 1f))
                    .compose()
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (scale * 255f).toInt().coerceIn(DRONE_MIN_AMP, DRONE_MAX_AMP)
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 35, 15),
                    intArrayOf(0, amplitude, 0),
                    -1
                ))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40L)
            }
        } catch (_: Exception) {}
    }
}
