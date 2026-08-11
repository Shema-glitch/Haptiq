package com.haptiq.app.audio

import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.haptiq.app.BuildConfig

class HapticMapper {

    private var lastKickTime = 0L
    private var lastDroneTime = 0L

    // DEBUG kick-latency instrumentation (KickLatencyTracker): fed the capture/dispatch
    // stamps of every kick so gyro-confirmed motor onset can be measured on-device.
    // Wired by HaptiqPlayerManager; null in release builds (start() no-ops there).
    var latencyTracker: KickLatencyTracker? = null

    // Surface-adaptive kick boost: asks the tracker (gyro-confirmed) how strongly
    // kicks are actually moving the phone and returns a character shift (+1/+2 on
    // damping surfaces, -1 when motion is unusually free). Wired by
    // HaptiqPlayerManager; gated on tuningState.isSurfaceAdaptiveEnabled.
    var surfaceCharacterShift: () -> Int = { 0 }

    // AOT lookahead sets this after each scheduled kick; the live gate stays silent
    // until it passes so one mapped onset isn't felt twice (once pre-fired, once when
    // the live FFT detector catches it ~50ms later). 0L = never suppressing.
    private var suppressLiveKickUntil = 0L

    // Tracks whether a drone was dispatched — used to gate the sidechain cancel
    private var isDronePlaying = false

    // Re-arm gate: a real kick's energy FALLS back down after the hit; a sustained
    // FX swell / riser keeps climbing or holds. After a fire the engine disarms and
    // tracks the peak since the fire — it re-arms when energy drops a real step below
    // that peak, or after a short timeout as a liveness guarantee. (v1.11 compared
    // against a fraction of the noise gate instead; on the dB-compressed normalize
    // scale inter-kick energy never fell that low, so the kick fired once per song
    // and then stayed disarmed forever.)
    private var kickArmed = true
    private var rearmPeak = 0f

    // ── Cached vibrator capabilities ───────────────────────────────────────────────
    // areAllPrimitivesSupported()/areEffectsSupported() are synchronous Binder calls
    // into the system vibrator service. Querying them on the timing-critical path added
    // real latency + jitter to the first fire of a session — they're now queried once
    // per Vibrator instance via prepare() (off the hot path, at player init) and reused,
    // keeping mapAndFire → vibrate() a pure local call.
    private var cachedVibrator: Vibrator? = null
    private var kickPrimitiveMode: KickPrimitiveMode = KickPrimitiveMode.NONE
    // ERM / no-amplitude motors ignore the amplitude byte and render everything at
    // full strength — the drone must fall back to duty-cycle modulation there.
    private var hasAmplitudeControl = false
    // EFFECT_HEAVY_CLICK is the OEM-tuned buzz system UI uses for gesture nav — on
    // budget phones without composition primitives it's the strongest, snappiest
    // effect the motor has, far better than a hand-rolled waveform.
    private var heavyClickSupported = false

    private enum class KickPrimitiveMode { THUD_CLICK, THUD_ONLY, CLICK_ONLY, NONE }

    /**
     * Pre-warm the capability cache off the timing-critical path (player init). The
     * binder queries below cost real ms the first time they run — doing them at startup
     * means the very first kick of a session doesn't pay for them.
     */
    fun prepare(vibrator: Vibrator?) {
        if (vibrator != null) ensureCapabilitiesCached(vibrator)
    }

    private fun ensureCapabilitiesCached(vibrator: Vibrator) {
        if (cachedVibrator === vibrator) return
        cachedVibrator = vibrator
        // Some OEM vibrator services are flaky — never let a query failure take down the
        // callback thread mid-song. Fall back to NONE (waveform/heavy-click rendering).
        try {
            hasAmplitudeControl = vibrator.hasAmplitudeControl()
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
            heavyClickSupported = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    vibrator.areEffectsSupported(VibrationEffect.EFFECT_HEAVY_CLICK)[0] !=
                        Vibrator.VIBRATION_EFFECT_SUPPORT_NO
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> true // no query API; system falls back safely
                else -> false
            }
            // Log.i, not Log.d: Tecno/MTK ROMs set log.tag to I and drop D-level lines.
            Log.i(TAG, "Vibrator capabilities cached: kickMode=$kickPrimitiveMode amplitudeControl=$hasAmplitudeControl heavyClick=$heavyClickSupported")
        } catch (e: Exception) {
            kickPrimitiveMode = KickPrimitiveMode.NONE
            heavyClickSupported = false
            Log.w(TAG, "Vibrator capability query failed, falling back to NONE: ${e.message}")
        }
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

        // Two-frame decay classification (see Haptiq_Study.md): when bass energy is
        // still high shortly after a kick, the low end is one sustained 808 doing both
        // jobs — the punch already fired, so its tail resumes early as damped rumble
        // instead of waiting out the full-contrast lockout reserved for discrete kicks.
        const val TAIL_808_RESUME_MS = 100L
        const val TAIL_808_MIN_ENV   = 0.55f
        const val TAIL_808_DAMP      = 0.65f

        // Re-arm when energy falls below this fraction of its post-fire peak (a kick
        // tail drops this much within a frame or two; a monotonic riser never does)…
        const val KICK_REARM_DROP    = 0.88f
        // …or after this long regardless — the engine can never be locked out.
        const val KICK_REARM_TIMEOUT_MS = 450L

        // How long a lookahead-scheduled kick silences the live engine. Pre-fire happens
        // ~LEAD (50ms) early; live detection of the same onset lands up to ~50ms after the
        // hit. 150ms covers both with margin, and stays well under real-world kick spacing.
        const val SCHEDULED_KICK_SUPPRESS_MS = 150L

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
        kickGain: Float = 1.0f,
        // Smoothed 0..1 bass envelope, song-relative — see BassAudioProcessor.subEnvelope
        subEnvelope: Float = 0f,
        // Frame carried a beater "click" (1.3–5.5kHz burst) — drum kick vs 808 attack
        clickTransient: Boolean = false,
        // Playback position at detection time (ms) — DEBUG instrumentation only, used to
        // correlate fire timestamps with the music when tuning kick timing on-device.
        playbackPositionMs: Long = 0L,
        // Monotonic (elapsedRealtime) FFT frame arrival — DEBUG kick-latency
        // instrumentation only (KickLatencyTracker); 0 when unknown.
        captureAtElapsedMs: Long = 0L,
        // Kick hit character (Snap/Punch/Thump/Heavy) — how long the kick drives
        // the motor as a decaying strike-train.
        kickCharacter: KickCharacter = KickCharacter.PUNCH
    ) {
        if (vibrator != null) ensureCapabilitiesCached(vibrator)

        // Monotonic clock: kick cooldown / re-arm / drone refresh are interval gates —
        // wall-clock jumps (NTP, user time change) must never shorten or extend them.
        val now = SystemClock.elapsedRealtime()
        var intensityScalar = (intensity / 100f) * calibrationMultiplier
        if (batterySaverEnabled) intensityScalar *= 0.70f

        // ── ENGINE 1: KICK ────────────────────────────────────────────────────────────
        // Re-arm: the energy fell a real step off its post-fire peak (kicks always do,
        // risers don't), or the timeout passed — whichever comes first.
        if (!kickArmed) {
            rearmPeak = maxOf(rearmPeak, rawKick)
            if (rawKick < rearmPeak * KICK_REARM_DROP ||
                now - lastKickTime > KICK_REARM_TIMEOUT_MS
            ) {
                kickArmed = true
            }
        }
        if (kickArmed && kickDelta > kickThreshold && rawKick > noiseFloorGate &&
            (now - lastKickTime > KICK_COOLDOWN_MS) && now >= suppressLiveKickUntil
        ) {

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
            // kick full-strength. kickGain is the user's dedicated kick intensity control
            // from the Studio, applied on top and clamped back into primitive range.
            val kickIntensityScalar = intensityScalar.coerceAtLeast(0.88f)
            val kickScale = ((0.92f + kickDelta * 0.08f).coerceIn(0.92f, 1.00f) *
                kickIntensityScalar * kickGain).coerceIn(0f, 1f)
            // Debug-gated: release builds ship with minify OFF, and unstripped Log.d with
            // string formatting on the timing-critical thread stalls kick dispatch when
            // logd throttles or the buffer fills.
            if (BuildConfig.DEBUG) Log.i(TAG, "KICK fire: delta=$kickDelta raw=$rawKick scale=$kickScale click=$clickTransient char=$kickCharacter posMs=$playbackPositionMs vib=${vibrator != null}")
            val dispatchAt = SystemClock.elapsedRealtime()
            fireKick(vibrator, kickScale, clickTransient, kickCharacter.shift(surfaceCharacterShift()))
            latencyTracker?.onKick(captureAtElapsedMs, dispatchAt, playbackPositionMs, "live")
            lastKickTime = now
            kickArmed = false
            rearmPeak = rawKick
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
        // Post-kick arbitration: bass still swelling right after the punch means one
        // 808 doing both jobs — resume its tail early as damped rumble. Bass that
        // dropped with the hit was a discrete kick — hold the full-contrast silence.
        val sinceKick = now - lastKickTime
        val is808Tail = sinceKick in TAIL_808_RESUME_MS until DRONE_LOCKOUT_MS &&
            subEnvelope > TAIL_808_MIN_ENV
        if (subEnvelope > droneFloor && (sinceKick > DRONE_LOCKOUT_MS || is808Tail)) {
            if (now - lastDroneTime > DRONE_REFRESH_MS) {
                val level = ((subEnvelope - droneFloor) / (1f - droneFloor)).coerceIn(0f, 1f)
                val droneAmplitude = (DRONE_MIN_AMP + level * (DRONE_MAX_AMP - DRONE_MIN_AMP))
                    .toInt().coerceIn(DRONE_MIN_AMP, DRONE_MAX_AMP)
                val tailDamp = if (is808Tail) TAIL_808_DAMP else 1f
                val droneScale = (droneAmplitude / 255f) * intensityScalar * bassGain * tailDamp

                if (BuildConfig.DEBUG) Log.i(TAG, "DRONE fire: env=$subEnvelope level=$level tail808=$is808Tail scale=$droneScale posMs=$playbackPositionMs vib=${vibrator != null}")
                fireDrone(vibrator, droneScale)
                isDronePlaying = true
                lastDroneTime = now
            }
        } else {
            isDronePlaying = false
        }
    }

    // ── AOT LOOKAHEAD: scheduled kicks ─────────────────────────────────────────────
    // Precomputed-map kicks (see HaptiqPlayerManager's lookahead scheduler) fire EARLY —
    // before the audio hit lands — so the motor is already spinning when the transient
    // arrives, cancelling the live engine's inherent ~50ms FFT detection lag. Same fireKick
    // path as the live engine; the live gate is silenced for SCHEDULED_KICK_SUPPRESS_MS
    // afterwards so the same onset isn't double-felt. No-ops when a kick just fired (the
    // live cooldown also guards the real onset's own live detection).
    fun scheduleKick(
        vibrator: Vibrator?,
        intensity: Int,
        calibrationMultiplier: Float,
        batterySaverEnabled: Boolean,
        kickGain: Float,
        onsetMs: Long,
        kickCharacter: KickCharacter = KickCharacter.PUNCH
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastKickTime < KICK_COOLDOWN_MS) return
        var intensityScalar = (intensity / 100f) * calibrationMultiplier
        if (batterySaverEnabled) intensityScalar *= 0.70f
        // Same punch-floor as the live path — a mapped onset is a full-strength transient.
        val kickIntensityScalar = intensityScalar.coerceAtLeast(0.88f)
        val scale = (kickIntensityScalar * kickGain).coerceIn(0f, 1f)
        if (BuildConfig.DEBUG) Log.i(TAG, "AOT kick pre-fire: onsetMs=$onsetMs scale=$scale char=$kickCharacter")
        val dispatchAt = SystemClock.elapsedRealtime()
        fireKick(vibrator, scale, clickTransient = true, kickCharacter.shift(surfaceCharacterShift()))
        // No capture stage for pre-fires — dispatch→onset is exactly the lead needed.
        latencyTracker?.onKick(0L, dispatchAt, onsetMs, "aot")
        lastKickTime = now
        suppressLiveKickUntil = now + SCHEDULED_KICK_SUPPRESS_MS
    }

    // ── KICK: cached capability picks the fastest-starting effect this device actually
    // has — THUD+CLICK (deep body + beater snap) when both are real, THUD or CLICK alone
    // when only one is, waveform only as a last resort. No Binder query on the hot path.
    // clickTransient shapes the punch character (study: classify by behavior): a drum
    // kick with an audible beater click gets the CLICK snap layered on; a clickless 808
    // attack renders as pure THUD — deeper, no snap, matching what the ear hears.
    // character is the hit-longevity axis: longer characters layer extra body strikes at
    // offset delays (primitives have vendor-baked decay, so one THUD always reads as a
    // tick; a second/third hit at delay extends the felt tail into a real thump) or use
    // a decaying multi-strike waveform on motors without composition support.
    private fun fireKick(vibrator: Vibrator?, scale: Float, clickTransient: Boolean = true, character: KickCharacter = KickCharacter.PUNCH) {
        if (vibrator == null) return
        val s = scale.coerceIn(0f, 1f)
        try {
            when (kickPrimitiveMode) {
                KickPrimitiveMode.THUD_CLICK -> {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s)
                    if (clickTransient) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s, 5)
                    }
                    var delay = 60
                    for (i in 1 until character.strikes) {
                        composition.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_THUD,
                            s * (1f - 0.15f * i), delay
                        )
                        delay += 70
                    }
                    vibrator.vibrate(composition.compose())
                }
                KickPrimitiveMode.THUD_ONLY -> {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s)
                    var delay = 60
                    for (i in 1 until character.strikes) {
                        composition.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_THUD,
                            s * (1f - 0.15f * i), delay
                        )
                        delay += 70
                    }
                    vibrator.vibrate(composition.compose())
                }
                KickPrimitiveMode.CLICK_ONLY -> {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                    var delay = 25
                    for (i in 1 until character.strikes) {
                        composition.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_CLICK,
                            s * (1f - 0.15f * i), delay
                        )
                        delay += 40
                    }
                    vibrator.vibrate(composition.compose())
                }
                KickPrimitiveMode.NONE -> {
                    // Heavy-click is a fixed OEM effect that can't be extended — it stays
                    // the default for the crisp Snap character, longer characters fall
                    // back to the waveform so the sustain axis still works everywhere.
                    if (heavyClickSupported && character == KickCharacter.SNAP &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ) {
                        // The system gesture-nav buzz: OEM-tuned per motor, so it's as
                        // strong and snappy as this exact phone can physically feel.
                        // Fixed strength by design — a kick should always land hard.
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitude = (s * 255f).toInt().coerceIn(210, 255)
                        val (times, amps) = if (!hasAmplitudeControl) {
                            // True ERM: motor inertia is slow to start (~30-50ms spin-up), so
                            // a single 35ms pulse mostly ends before the mass is moving.
                            // The strike-train keeps the motor spinning through short gaps
                            // and re-energizes it per strike — one long kick, not a train
                            // of separate ticks.
                            KickWaveform.strikeTrain(character, amplitude)
                        } else {
                            KickWaveform.pulseWithTail(character, amplitude)
                        }
                        vibrator.vibrate(VibrationEffect.createWaveform(times, amps, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(35L * character.strikes)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAmplitudeControl) {
                vibrator.vibrate(VibrationEffect.createOneShot(DRONE_HOLD_MS, amplitude))
            } else {
                // hasAmplitudeControl() == false: approximate loudness by DUTY CYCLE —
                // quiet bass holds 40ms of each 60ms refresh window (motor inertia
                // blurs the gap into a softer rumble), loud bass holds through it.
                // Still pass the computed amplitude, NOT DEFAULT_AMPLITUDE: some ROMs
                // (Tecno/MTK legacy vibrator) report no amplitude control while
                // actually honoring it — full-strength duty pulses on those devices
                // turned the drone into a harsh nonstop machine-gun buzz. A motor that
                // truly can't do amplitude just ignores the value, so this is safe on
                // both kinds of hardware.
                val holdMs = 40L + ((amplitude - DRONE_MIN_AMP).toFloat() /
                    (DRONE_MAX_AMP - DRONE_MIN_AMP) * 80f).toLong()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(holdMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(holdMs)
                }
            }
        } catch (_: Exception) {}
    }
}

// ── KICK WAVEFORMS ────────────────────────────────────────────────────────────────
// The decaying strike-train that implements the hit-longevity axis on motors without
// composition primitives. Kept as a pure object so the waveform math is unit-testable.
object KickWaveform {
    private const val STRIKE_MS = 35L
    private const val GAP_MS = 8L

    /**
     * ERM/no-amplitude path: repeated full-strength strikes at decaying amplitude,
     * then a low-amp tail. Each strike re-energizes the still-spinning motor, so the
     * felt hit lasts as long as the character says instead of ending at motor
     * spin-up. The tail (and each strike's decay) means it reads as ONE decaying hit,
     * never a flat hold (a flat hold reads as a buzz).
     */
    fun strikeTrain(character: KickCharacter, amplitude: Int): Pair<LongArray, IntArray> {
        // (delay, strike) pairs, matching the pre-existing waveform convention:
        // a 0-amp delay segment precedes every strike (timings[0] is the initial
        // delay in VibrationEffect.createWaveform).
        val times = ArrayList<Long>()
        val amps = ArrayList<Int>()
        repeat(character.strikes) { i ->
            times.add(if (i == 0) 0L else GAP_MS)
            amps.add(0)
            times.add(STRIKE_MS)
            amps.add((amplitude * (1f - 0.15f * i)).toInt().coerceIn(0, 255))
        }
        if (character.tailMs > 0) {
            times.add(GAP_MS); amps.add(0)
            times.add(character.tailMs.toLong())
            amps.add((amplitude * 0.5f).toInt().coerceIn(0, 255))
        }
        return times.toLongArray() to amps.toIntArray()
    }

    /**
     * Amplitude-control path: one strike plus a decayed tail that extends the felt
     * decay into a thump without ever holding flat.
     */
    fun pulseWithTail(character: KickCharacter, amplitude: Int): Pair<LongArray, IntArray> =
        if (character.tailMs > 0) {
            longArrayOf(0, STRIKE_MS, GAP_MS, character.tailMs.toLong()) to
                intArrayOf(amplitude, amplitude, 0, (amplitude * 0.45f).toInt().coerceIn(0, 255))
        } else {
            longArrayOf(0, STRIKE_MS, GAP_MS, 12L) to
                intArrayOf(amplitude, amplitude, 0, 0)
        }
}
