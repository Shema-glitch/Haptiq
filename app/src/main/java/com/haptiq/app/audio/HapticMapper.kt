package com.haptiq.app.audio

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class HapticMapper {

    private var lastKickTime = 0L
    private var lastDroneTime = 0L

    // Tracks whether a drone was dispatched — used to gate the sidechain cancel
    private var isDronePlaying = false

    // ── Cached vibrator capabilities ───────────────────────────────────────────────
    // areAllPrimitivesSupported() is a synchronous Binder call into the system vibrator
    // service. Querying it on every single kick/drone fire added a real-world latency
    // hit (and jitter) to the timing-critical path — this is now queried once per
    // Vibrator instance and reused, keeping mapAndFire → vibrate() a pure local call.
    private var cachedVibrator: Vibrator? = null
    private var kickPrimitiveMode: KickPrimitiveMode = KickPrimitiveMode.NONE

    private enum class KickPrimitiveMode { THUD_CLICK, THUD_ONLY, CLICK_ONLY, NONE }

    private fun ensureCapabilitiesCached(vibrator: Vibrator) {
        if (cachedVibrator === vibrator) return
        cachedVibrator = vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasThud = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
            val hasClick = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            kickPrimitiveMode = when {
                hasThud && hasClick -> KickPrimitiveMode.THUD_CLICK
                hasThud -> KickPrimitiveMode.THUD_ONLY
                hasClick -> KickPrimitiveMode.CLICK_ONLY
                else -> KickPrimitiveMode.NONE
            }
        } else {
            kickPrimitiveMode = KickPrimitiveMode.NONE
        }
        Log.d(TAG, "Vibrator capabilities cached: kickMode=$kickPrimitiveMode")
    }

    companion object {
        private const val TAG = "HapticMapper"
        const val KICK_COOLDOWN_MS   = 80L
        const val DRONE_LOCKOUT_MS   = 230L
        // Each drone "hold" pulse is issued for DRONE_HOLD_MS but refreshed every
        // DRONE_REFRESH_MS while bass stays above threshold — refresh always lands well
        // before the hold expires, so the motor never lets go between calls. That's what
        // turns it into one sustained buzz instead of a train of separate taps.
        const val DRONE_HOLD_MS      = 160L
        const val DRONE_REFRESH_MS   = 60L

        // Bass amplitude ceiling — always softer than kick so kick wins perceptually.
        // Kick now floors near-max scale regardless of the intensity slider (see kick
        // engine above), so this ceiling has real headroom below it to stay felt as
        // "the quieter one" even at full bass energy.
        const val DRONE_MAX_AMP      = 120
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
        subDroneThreshold: Float = 0.78f,
        bassGain: Float = 1.0f,
        // Smoothed 0..1 bass envelope, song-relative — see BassAudioProcessor.subEnvelope
        subEnvelope: Float = 0f
    ) {
        if (vibrator != null) ensureCapabilitiesCached(vibrator)

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

            // Kick is a short, rare event — safe to keep it consistently punchy even when
            // the master intensity slider is turned down for a gentler overall feel. Only
            // the sustained, ever-present drone needs the slider's full range to avoid
            // fatigue; kick riding the same 0.75-default scalar as drone was why a kick
            // firing perfectly on-beat, alone, with no overlap, still felt weaker than the
            // bass — composition-primitive "scale" and drone's raw amplitude are different
            // units, and a low intensityScalar was compounding that gap instead of leaving
            // kick full-strength.
            val kickIntensityScalar = intensityScalar.coerceAtLeast(0.88f)
            val kickScale = (0.92f + kickDelta * 0.08f).coerceIn(0.92f, 1.00f) * kickIntensityScalar
            Log.d(TAG, "KICK fire: delta=$kickDelta raw=$rawKick scale=$kickScale vib=${vibrator != null}")
            fireKick(vibrator, kickScale)
            lastKickTime = now
            return
        }

        // ── ENGINE 2: SUB-BASS DRONE — continuous envelope follower ──────────────────────
        // subEnvelope is already zeroed by BassAudioProcessor when isBassEnabled = false.
        // Unlike the old threshold-cliff + squared curve (which was either fully silent or
        // pinned near the top of a narrow range — felt like a flat "constant buzz"),
        // amplitude here tracks subEnvelope directly and near-linearly: quiet bass buzzes
        // gently, loud bass buzzes strongly, continuously — a speaker wave, not a switch.
        // The Studio's bass-threshold slider still matters, just as the floor below which
        // the drone stays silent, rescaled into a much more permissive range than before.
        val droneFloor = (subDroneThreshold * 0.20f).coerceIn(0.08f, 0.35f)
        if (subEnvelope > droneFloor && (now - lastKickTime > DRONE_LOCKOUT_MS)) {
            if (now - lastDroneTime > DRONE_REFRESH_MS) {
                val level = ((subEnvelope - droneFloor) / (1f - droneFloor)).coerceIn(0f, 1f)
                val droneAmplitude = (DRONE_MIN_AMP + level * (DRONE_MAX_AMP - DRONE_MIN_AMP))
                    .toInt().coerceIn(DRONE_MIN_AMP, DRONE_MAX_AMP)
                val droneScale = (droneAmplitude / 255f) * intensityScalar * bassGain

                Log.d(TAG, "DRONE fire: env=$subEnvelope level=$level scale=$droneScale vib=${vibrator != null}")
                fireDrone(vibrator, droneScale)
                isDronePlaying = true
                lastDroneTime = now
            }
        } else {
            isDronePlaying = false
        }
    }

    // ── KICK: cached capability picks the fastest-starting effect this device actually
    // has — THUD+CLICK (deep body + beater snap) when both are real, THUD or CLICK alone
    // when only one is, waveform only as a last resort. No Binder query on the hot path.
    private fun fireKick(vibrator: Vibrator?, scale: Float) {
        if (vibrator == null) return
        val s = scale.coerceIn(0f, 1f)
        try {
            when (kickPrimitiveMode) {
                KickPrimitiveMode.THUD_CLICK -> vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s, 5)
                        .compose()
                )
                KickPrimitiveMode.THUD_ONLY -> vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s)
                        .compose()
                )
                KickPrimitiveMode.CLICK_ONLY -> vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s * 0.75f, 15)
                        .compose()
                )
                KickPrimitiveMode.NONE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitude = (s * 255f).toInt().coerceIn(210, 255)
                        vibrator.vibrate(VibrationEffect.createWaveform(
                            longArrayOf(0, 35, 10),
                            intArrayOf(0, amplitude, 0),
                            -1
                        ))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(35L)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // ── DRONE: a single continuously-held, amplitude-controlled pulse — NOT a composition
    // primitive. Primitives like PRIMITIVE_THUD are discrete one-shot "moments" with a
    // vendor-baked decay; re-firing one on a cooldown always feels like separate taps, no
    // matter how tight the cooldown. A sustained rumble needs a plain vibrate(duration,
    // amplitude) call that's refreshed (re-issued) before it ends — mapAndFire calls this
    // every DRONE_REFRESH_MS while bass stays above threshold, and each call simply
    // extends the running vibration, so the motor never spins down between calls and it
    // reads as one continuous buzz. When bass drops below threshold, refresh calls stop
    // and the last hold pulse fades out naturally over its remaining duration.
    private fun fireDrone(vibrator: Vibrator?, scale: Float) {
        if (vibrator == null) return
        val s = scale.coerceIn(0f, 1f)
        val amplitude = (s * 255f).toInt().coerceIn(DRONE_MIN_AMP, DRONE_MAX_AMP)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(DRONE_HOLD_MS, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(DRONE_HOLD_MS)
            }
        } catch (_: Exception) {}
    }
}
