# Haptiq Haptic Engine — Developer Handoff

## 1. Context & Recovery
We recovered a development session that froze mid-flight while fixing the real-time haptic feedback engine. The app had a working audio player, but the `Visualizer` and haptic triggering logic were failing to accurately map the audio waveform to physical vibrations.

Our primary goal was to get the Android `Visualizer` to read the audio session correctly and pulse the haptic motor in rhythm with the bass/kick transients, just like a DAW handles frequency layers.

## 2. Issues Diagnosed & Resolved

### A. The "Dead Haptics / Flatline" Bug
**Symptoms:** The haptic motor would vibrate briefly at the start of a song or when seeking, but go completely dead during normal playback.
**Root Causes & Fixes:**
1. **ExoPlayer Lifecycle:** The previous code called `player.stop()` between songs. This destroyed the underlying audio track renderer and invalidated the `Visualizer` session ID, silently breaking the capture.
   * **Fix:** Removed `player.stop()`. We now use `player.setMediaItem()` + `player.prepare()`, and we force-reattach the `Visualizer` when the player enters `STATE_READY`.
2. **Visualizer Math Normalization:** Android's `Visualizer` API returns raw 8-bit bytes (magnitudes in range `[0, 181]`). The previous formula was written expecting float inputs `[0.0, 1.0]`. Because of this, normal music levels clipped to `1.0` instantly. Since the value was pinned at `1.0`, the spectral flux (rate of change) was always `0.0`.
   * **Fix:** Scaled the raw byte magnitudes relative to `181.0f` before calculating the signal energy, restoring dynamic waveform movement.

### B. The "Constant Buzzing / Speaker" Bug
**Symptoms:** After fixing the math, the vibrator started buzzing continuously at a constant rhythm (~8 pulses per second), regardless of the song's tempo. It felt like the motor was trying to act as a speaker coil playing the raw audio.
**Root Causes & Fixes:**
1. **Thresholds too sensitive:** We had lowered `BASS_THRESHOLD` to `0.12f` and increased `ONSET_SCALE` to `8.0f`. This caused the FFT noise floor and background rumble to constantly trigger the haptics. It fired on almost every frame (10 times a second), hitting the `MIN_COOLDOWN_MS` limit of 120ms and creating a constant 8Hz mechanical buzz.
   * **Fix:** Raised `BASS_THRESHOLD` back to `0.25f` and set `ONSET_SCALE` to `6.0f`.

## 3. Current Architecture: Multi-Band DAW Layers
To prevent a sustained 808 drone from masking the sharp attack of a kick drum, we partitioned the FFT data into 3 independent layers in `BassAudioProcessor.kt`. 

With `captureSize = 1024` at `44100Hz`, our frequency resolution is ~43 Hz per bin.
*   **Sub/808 Layer:** Bin 1 (~43 Hz)
*   **Kick Drum Layer:** Bins 2 & 3 (~86–129 Hz)
*   **Upper Bass Layer:** Bins 4 & 5 (~172–215 Hz)

**The Kick Isolation Strategy:**
Currently, to stop the engine from getting confused by total bass energy, we have **strictly isolated the Kick Drum Layer**. 
*   We calculate the spectral flux (onset derivative) for the Kick band ONLY.
*   We completely ignore the Sub-bass and Upper Bass layers when triggering the haptic motor (though they are still passed to the UI for visual animations).
*   If `fluxKick * 6.0f > 0.25f`, a haptic pulse is fired.

## 4. Next Steps for Diagnostics
The real-time `Visualizer` on Android provides extremely low-resolution bass bins (due to the 1024 max capture size limit). If the haptics still feel disconnected or miss beats, it is likely due to the inherent inaccuracy of the Android `Visualizer` API for low-end transient detection.

**Areas to investigate:**
1. Review the `processFft` function in `BassAudioProcessor.kt` and check if the Bins 2 and 3 accurately represent the kicks for the test audio.
2. Consider implementing the **AOT Engine Haptic Map Cache** (detailed in `docs/HAPTIQ_AOT_ENGINE HAPTIC MAP CACHe.md`). Using a background `WorkManager` to pre-compute high-resolution FFT haptic maps via a robust library (like TarsosDSP or FFmpeg) and caching them in Room would completely bypass the real-time constraints of the Android `Visualizer`, reducing runtime CPU usage to 0% and ensuring perfect, sample-accurate haptic timing.

## 5. Key Code Snippets

### A. The Kick Isolation Logic (`BassAudioProcessor.kt`)
This is where we strictly isolate Bins 2 and 3 and drive the flux:
```kotlin
// Android Visualizer FFT layout normalization:
magnitudeBuffer[0] = kotlin.math.abs(fft[0].toFloat())
for (i in 1 until halfN) {
    val real = fft[i * 2].toFloat()
    val imag = fft[i * 2 + 1].toFloat()
    magnitudeBuffer[i] = kotlin.math.sqrt(real * real + imag * imag)
}

// Sub/808 Layer: Bin 1 (~43 Hz)
val rawSub = normalize(magnitudeBuffer[1])

// Kick Drum Layer: Bins 2 & 3 (~86 - 129 Hz)
val rawKick = normalize((magnitudeBuffer[2] + magnitudeBuffer[3]) / 2f)

// Upper Bass Layer: Bins 4 & 5 (~172 - 215 Hz)
val rawUpper = normalize((magnitudeBuffer[4] + magnitudeBuffer[5]) / 2f)

// Independent Spectral Flux Onset Detection for Kick
val smoothedKick = SMOOTH_ALPHA * rawKick + (1f - SMOOTH_ALPHA) * prevSmoothedKick
val fluxKick = (smoothedKick - prevSmoothedKick).coerceAtLeast(0f)
prevSmoothedKick = smoothedKick

// Drive the haptic motor STRICTLY from the Kick band's transients.
val combinedFlux = fluxKick
val onsetSignal = (combinedFlux * ONSET_SCALE).coerceIn(0f, 1f)
```

### B. The Tuned Thresholds (`HapticMapper.kt`)
The thresholds are raised to prevent the engine from firing on background noise:
```kotlin
companion object {
    const val MIN_COOLDOWN_MS = 120L    // ~8 pulses/sec max (feels like bass hits, not buzzing)
    const val MAX_PULSE_DURATION_MS = 80L
    const val BASS_THRESHOLD = 0.25f    // Trigger threshold for onset transients
}
```

### C. The ExoPlayer Lifecycle Fix (`HaptiqPlayerManager.kt`)
Force-reattaching the Visualizer to prevent the audio session from silently freezing:
```kotlin
override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_READY) {
        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
            bassVisualizer.attach(sessionId)
        }
    }
}
```
