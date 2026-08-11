package com.haptiq.app.data

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.haptiq.app.audio.BassVisualizer
import com.haptiq.app.audio.BassEnergy
import com.haptiq.app.audio.HapticMapper
import com.haptiq.app.audio.HapticTuningState
import com.haptiq.app.audio.KickCharacter
import com.haptiq.app.audio.KickLatencyTracker
import com.haptiq.app.playback.HaptiqPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HaptiqPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStateStore: PlaybackStateStore,
    private val energyMapRepository: EnergyMapRepository
) : PlayerManager {

    companion object {
        // ── AOT lookahead kick scheduling ──────────────────────────────────────────
        // Live FFT detection (Visualizer at ~20Hz) inherently fires kicks 50-70ms after the
        // transient. When enabled (HapticTuningState.isAotLookaheadEnabled, toggled from the
        // Studio), kicks are ALSO pre-fired from a per-track onset map (see
        // TrackEnergyAnalyzer.onsetsMs) LOOKAHEAD_LEAD_MS before the audio hit, so the motor
        // is already moving when the bass lands.
        // How early (ms) a mapped kick fires before its audio position.
        private const val LOOKAHEAD_LEAD_MS = 50L
        // Poll granularity while inside the lead window.
        private const val LOOKAHEAD_POLL_MS = 8L
        // Sleep cap between onsets — keeps the loop idle-friendly on sparse maps.
        private const val LOOKAHEAD_QUIET_SLEEP_MS = 250L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var exoPlayer: ExoPlayer? = null
    private var vibrator: Vibrator? = null

    // Audio processing — created once, re-attached on audio session changes
    private val hapticMapper = HapticMapper()
    private val bassVisualizer = BassVisualizer { bassEnergy ->
        onBassEnergyDetected(bassEnergy)
    }

    // DEBUG kick-latency instrumentation (capture→dispatch→gyro-confirmed motor onset).
    // start() no-ops in release builds, so this is inert outside debug.
    private val kickLatencyTracker = KickLatencyTracker()

    // ── AOT lookahead scheduler state (active only while isAotLookaheadEnabled) ──
    private var lookaheadJob: Job? = null
    private var lookaheadOnsets = IntArray(0)
    // Beat grid for the current song — onsets not near a beat are map false positives
    // (snare thumps, vocal artifacts) and are skipped instead of pre-fired. Empty grid
    // (no steady beat) rejects nothing.
    private var lookaheadBeats = IntArray(0)
    private var lookaheadIdx = 0

    // State flows
    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    override val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentTimeText = MutableStateFlow("0:00")
    override val currentTimeText: StateFlow<String> = _currentTimeText.asStateFlow()

    private val _remainingTimeText = MutableStateFlow("-0:00")
    override val remainingTimeText: StateFlow<String> = _remainingTimeText.asStateFlow()

    private val _hapticActive = MutableStateFlow(true)
    override val hapticActive: StateFlow<Boolean> = _hapticActive.asStateFlow()

    private val _currentPresetId = MutableStateFlow("deep_bass")
    override val currentPresetId: StateFlow<String> = _currentPresetId.asStateFlow()

    private val _intensity = MutableStateFlow(75) // 0 - 100
    override val intensity: StateFlow<Int> = _intensity.asStateFlow()

    private val _kickCharacter = MutableStateFlow(KickCharacter.PUNCH)
    override val kickCharacter: StateFlow<KickCharacter> = _kickCharacter.asStateFlow()

    private val _batterySaverEnabled = MutableStateFlow(false)
    override val batterySaverEnabled: StateFlow<Boolean> = _batterySaverEnabled.asStateFlow()

    // Real-time Visualizer frequencies (10 bands for UI drawing)
    private val _visualizerBands = MutableStateFlow(FloatArray(10) { 0.2f })
    override val visualizerBands: StateFlow<FloatArray> = _visualizerBands.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0)
    override val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var sleepJob: Job? = null

    // Queue management — songsQueue is the live play order; originalQueue remembers
    // the unshuffled order so turning shuffle off restores it exactly.
    private var songsQueue = mutableListOf<Song>()
    private var originalQueue = listOf<Song>()
    private var currentSongIndex = 0

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    override val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // System media-stream volume as a 0..1 fraction. Kept in sync with the hardware
    // volume keys via a ContentObserver so the in-app slider never drifts from reality.
    private val _volume = MutableStateFlow(currentVolumeFraction())
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var lastPositionSaveAt = 0L

    // Kept so destroy() can unregister it — an unregistered ContentObserver leaks the
    // registering Context (here the application Context, so harmless in practice, but
    // destroy() is the designated full-teardown path and should leave nothing dangling).
    private var volumeObserver: android.database.ContentObserver? = null

    // Live DSP tuning state — written from UI thread, read from Visualizer callback thread
    @Volatile private var tuningState = HapticTuningState()

    init {
        initVibrator()
        initExoPlayer()
        registerVolumeObserver()
        hapticMapper.latencyTracker = kickLatencyTracker
        // Surface-adaptive kick boost — reads the gyro-confirmed onset magnitude and
        // steps the kick character up on damping surfaces. Gated off by default until
        // the sensing bands are calibrated on-device (HapticTuningState).
        hapticMapper.surfaceCharacterShift = {
            if (tuningState.isSurfaceAdaptiveEnabled) kickLatencyTracker.surfaceCharacterShift() else 0
        }
        kickLatencyTracker.start(context)
    }

    private fun currentVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    private fun registerVolumeObserver() {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                _volume.value = currentVolumeFraction()
            }
        }
        volumeObserver = observer
        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI, true, observer
        )
    }

    private fun initVibrator() {
        // Validated acquisition with MTK/Tecno dud-vibrator fallback — the previous
        // unvalidated VibratorManager path made song haptics silently dead on those
        // devices while the calibration test pulse (which had the fallback) worked.
        vibrator = com.haptiq.app.audio.DeviceVibrator.get(context)
        // Pre-warm the capability cache (composition primitives / amplitude / heavy-click
        // support) off the hot path — those are synchronous Binder round-trips that would
        // otherwise be paid by the very first kick of the session, on the Visualizer thread.
        hapticMapper.prepare(vibrator)
    }

    private fun initExoPlayer() {
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    Log.d("HaptiqPlayer", "onAudioSessionIdChanged: $audioSessionId")
                    if (audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                        bassVisualizer.attach(audioSessionId)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d("HaptiqPlayer", "onIsPlayingChanged: $isPlaying")
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressUpdate()
                        restartLookahead()
                    } else {
                        stopProgressUpdate()
                        stopLookahead()
                        vibrator?.cancel() // Kill haptics immediately when playback is paused
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    Log.d("HaptiqPlayer", "Position discontinuity: reason=$reason")
                    // Seeks jump the playback clock instantly — the lookahead scheduler must
                    // re-base on the new position or it would pre-fire for onsets already past.
                    restartLookahead()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d("HaptiqPlayer", "onPlaybackStateChanged: $playbackState")
                    when (playbackState) {
                        Player.STATE_READY -> {
                            Log.d("HaptiqPlayer", "STATE_READY")
                            reattachVisualizer("STATE_READY")
                        }
                        Player.STATE_BUFFERING -> Log.d("HaptiqPlayer", "STATE_BUFFERING")
                        Player.STATE_ENDED     -> onSongCompleted()
                        Player.STATE_IDLE      -> Log.d("HaptiqPlayer", "STATE_IDLE")
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("HaptiqPlayer", "Playback error: ${error.message}", error)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    Log.d("HaptiqPlayer", "MediaItem transition: ${mediaItem?.mediaId}")
                    // Native playlist moved to a new item (manual skip OR auto-advance).
                    // Mirror it into our model + now-playing UI so the app, notification,
                    // and haptics all track the track ExoPlayer is actually playing.
                    val idx = exoPlayer?.currentMediaItemIndex ?: return
                    if (idx in songsQueue.indices) {
                        currentSongIndex = idx
                        val song = songsQueue[idx]
                        if (_currentSong.value?.id != song.id) applySongUi(song)
                        saveSession()
                    }
                    // Re-bind the FFT Visualizer to the new track. In the native-playlist
                    // engine prepare() runs ONCE for the whole queue, so STATE_READY does NOT
                    // fire on auto-advance or a seekTo skip — yet the transition can rebuild
                    // the audio renderer, silently killing the old attachment. Without this,
                    // the studio bars and the haptic motor go dead after the first song change.
                    reattachVisualizer("mediaItemTransition")
                    restartLookahead()
                    updateProgressFlows()
                }
            })
        }
        exoPlayer = player
    }

    /**
     * (Re)bind the FFT [BassVisualizer] to the player's current audio session. ExoPlayer keeps
     * a single session id across a playlist, but a track transition can rebuild the underlying
     * audio renderer and silently kill the old attachment. attach() releases the previous
     * Visualizer first, so calling this repeatedly is safe (idempotent).
     */
    private fun reattachVisualizer(source: String) {
        val sessionId = exoPlayer?.audioSessionId ?: return
        if (sessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            Log.d("HaptiqPlayer", "$source: re-attaching Visualizer to session $sessionId")
            bassVisualizer.attach(sessionId)
        }
    }

    private fun onBassEnergyDetected(bassEnergy: BassEnergy) {
        // Update visualizer bands for UI with 10-band spectrum analyzer
        _visualizerBands.value = bassEnergy.spectrum

        // Fire haptic only if active AND actually playing music
        if (_hapticActive.value && _isPlaying.value) {
            hapticMapper.mapAndFire(
                dominantPitch = bassEnergy.dominantPitch,
                subBassEnergy = bassEnergy.subBass,
                kickDelta = bassEnergy.combined,
                rawKick = bassEnergy.rawKick,
                presetId = _currentPresetId.value,
                intensity = _intensity.value,
                calibrationMultiplier = 1.0f,
                batterySaverEnabled = _batterySaverEnabled.value,
                vibrator = vibrator,
                // Live-tuned thresholds from the Tuning Dashboard
                kickThreshold = bassEnergy.kickThreshold,
                noiseFloorGate = bassEnergy.noiseFloorGate,
                subDroneThreshold = bassEnergy.subDroneThreshold,
                bassGain = bassEnergy.bassGain,
                kickGain = bassEnergy.kickGain,
                subEnvelope = bassEnergy.subEnvelope,
                clickTransient = bassEnergy.clickTransient,
                // DEBUG instrumentation: playback position at detection, for correlating fire
                // timestamps with the music when tuning kick timing on-device. Best-effort
                // read from the callback thread (position may lag by a frame — fine for logs).
                playbackPositionMs = runCatching { exoPlayer?.currentPosition ?: 0L }.getOrDefault(0L),
                // DEBUG instrumentation: FFT frame arrival, the "capture" endpoint of the
                // kick-latency measurement (KickLatencyTracker).
                captureAtElapsedMs = bassEnergy.captureAtElapsedMs,
                // Kick hit character (Snap/Punch/Thump/Heavy) — the hit-longevity axis.
                kickCharacter = tuningState.kickCharacter
            )
        }
    }

    // ── AOT lookahead kick scheduling ────────────────────────────────────────────────
    // Pre-fires mapped kicks LOOKAHEAD_LEAD_MS before their audio position so the motor is
    // already spinning when the transient lands (the live FFT engine is inherently late).
    // Safe by construction: it only fires when 0 <= onset - position <= LOOKAHEAD_LEAD_MS at
    // check time, so a stalled position (buffering/pause) can never fire into the void — if
    // an onset passes unmapped, the live engine catches it as usual. Only runs for songs
    // with a stored onset map; everything else keeps the live engine alone.
    private fun restartLookahead() {
        lookaheadJob?.cancel()
        if (!tuningState.isAotLookaheadEnabled) return
        val song = _currentSong.value ?: return
        if (!_isPlaying.value || !_hapticActive.value) return
        lookaheadJob = playerScope.launch {
            val map = withContext(Dispatchers.IO) { energyMapRepository.aotMapFor(song) }
                ?: return@launch
            // The song may have changed while the (potentially seconds-long) analysis ran.
            if (_currentSong.value?.id != song.id || !_isPlaying.value) return@launch
            if (map.onsets.isEmpty()) return@launch
            lookaheadOnsets = map.onsets
            lookaheadBeats = map.beats
            lookaheadIdx = 0
            lookaheadLoop()
        }
    }

    // Extension on CoroutineScope so isActive() works — called from playerScope.launch.
    private suspend fun CoroutineScope.lookaheadLoop() {
        while (isActive) {
            val player = exoPlayer ?: return
            if (!_isPlaying.value || !_hapticActive.value) {
                delay(LOOKAHEAD_QUIET_SLEEP_MS)
                continue
            }
            val posMs = runCatching { player.currentPosition }.getOrDefault(0L)
            // Advance past onsets already behind us (the live engine's job now).
            while (lookaheadIdx < lookaheadOnsets.size && lookaheadOnsets[lookaheadIdx] < posMs) {
                lookaheadIdx++
            }
            if (lookaheadIdx >= lookaheadOnsets.size) return
            val onset = lookaheadOnsets[lookaheadIdx]
            val lead = onset - posMs
            if (lead <= LOOKAHEAD_LEAD_MS) {
                // Beat double-check: skip map false positives (onsets not on the beat
                // grid) — they don't get pre-fired. Live detection is untouched, so a
                // genuinely musical off-beat transient is still felt normally.
                if (com.haptiq.app.audio.TrackEnergyAnalyzer.isOnBeat(onset, lookaheadBeats)) {
                    hapticMapper.scheduleKick(
                        vibrator = vibrator,
                        intensity = _intensity.value,
                        calibrationMultiplier = 1.0f,
                        batterySaverEnabled = _batterySaverEnabled.value,
                        kickGain = tuningState.kickGain,
                        onsetMs = onset.toLong(),
                        kickCharacter = tuningState.kickCharacter
                    )
                }
                lookaheadIdx++
            }
            // Sleep until just before the lead window opens (or poll within it). Wall-clock
            // sleep vs media time drifts at playback speeds != 1.0, but that only shifts the
            // fire a little early/late within the window — the fire condition is re-checked
            // against position, so it can never fire before the audio actually reaches it.
            val sleep = if (lead > LOOKAHEAD_LEAD_MS)
                minOf(lead - LOOKAHEAD_LEAD_MS, LOOKAHEAD_QUIET_SLEEP_MS) else LOOKAHEAD_POLL_MS
            delay(sleep)
        }
    }

    private fun stopLookahead() {
        lookaheadJob?.cancel()
        lookaheadJob = null
        lookaheadOnsets = IntArray(0)
        lookaheadBeats = IntArray(0)
        lookaheadIdx = 0
    }

    override fun setSongs(songs: List<Song>, startIndex: Int) {
        originalQueue = songs.toList()
        if (_isShuffleEnabled.value && songs.isNotEmpty()) {
            // Selected song leads, the rest follow shuffled — no repeats
            val start = songs[startIndex.coerceIn(songs.indices)]
            songsQueue = (listOf(start) + (songs - start).shuffled()).toMutableList()
            currentSongIndex = 0
        } else {
            songsQueue = songs.toMutableList()
            currentSongIndex = startIndex
        }
        publishQueue()
        if (songsQueue.isNotEmpty()) {
            loadQueue(currentSongIndex)
        }
    }

    private fun publishQueue() {
        _queue.value = songsQueue.toList()
    }

    private fun saveSession() {
        if (songsQueue.isEmpty()) return
        playbackStateStore.save(
            PlaybackSnapshot(
                queueIds = songsQueue.map { it.id },
                currentIndex = currentSongIndex,
                positionMs = exoPlayer?.currentPosition ?: 0L,
                shuffle = _isShuffleEnabled.value,
                repeatMode = _repeatMode.value
            )
        )
    }

    override fun restoreSession(library: List<Song>) {
        if (_currentSong.value != null) return // an active session wins over a stored one
        val snapshot = playbackStateStore.load() ?: return
        val byId = library.associateBy { it.id }
        val songs = snapshot.queueIds.mapNotNull { byId[it] }
        if (songs.isEmpty()) return
        // Files may have been deleted since — re-locate the saved track by position, then id
        val savedId = snapshot.queueIds.getOrNull(snapshot.currentIndex)
        val index = songs.indexOfFirst { it.id == savedId }.takeIf { it >= 0 } ?: 0
        originalQueue = songs
        songsQueue = songs.toMutableList()
        currentSongIndex = index
        _isShuffleEnabled.value = snapshot.shuffle
        _repeatMode.value = snapshot.repeatMode
        publishQueue()
        loadQueue(index, startPositionMs = snapshot.positionMs, autoPlay = false)
    }

    override fun setHapticActive(active: Boolean) {
        _hapticActive.value = active
    }

    override fun setPreset(presetId: String) {
        _currentPresetId.value = presetId
        // Each preset is now a kick CHARACTER + intensity pairing — the hit-longevity
        // axis renamed from the old names (Deep Bass/Punch/Concert/Soft Pulse):
        //   THUMP  (deep_bass)   — sustained, low-end hits at moderate intensity
        //   PUNCH  (punch)       — the classic two-strike punch, strongest intensity
        //   HEAVY  (concert)     — maximum presence, longest hit
        //   SNAP   (soft_pulse)  — short, crisp, gentle
        val (intensity, character) = when (presetId) {
            "deep_bass" -> 75 to KickCharacter.THUMP
            "punch" -> 90 to KickCharacter.PUNCH
            "concert" -> 80 to KickCharacter.HEAVY
            "soft_pulse" -> 50 to KickCharacter.SNAP
            else -> 75 to KickCharacter.THUMP
        }
        _intensity.value = intensity
        _kickCharacter.value = character
        // Keep the DSP tuning in sync with the preset so the engine uses the character
        // immediately (the Studio chip and preset chips then agree on one value).
        tuningState = tuningState.copy(kickCharacter = character)
        bassVisualizer.tuning = tuningState
    }

    override fun updateIntensity(value: Int) {
        _intensity.value = value
    }

    override fun setBatterySaver(enabled: Boolean) {
        _batterySaverEnabled.value = enabled
    }

    override fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes > 0) {
            sleepJob = playerScope.launch {
                delay(minutes * 60_000L)
                pauseMedia()
                vibrator?.cancel()
                _sleepTimerMinutes.value = 0
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2f)
        _playbackSpeed.value = s
        exoPlayer?.setPlaybackSpeed(s)
    }

    override fun setVolume(fraction: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        _volume.value = currentVolumeFraction()
    }

    override fun updateTuning(state: HapticTuningState) {
        val wasAotEnabled = tuningState.isAotLookaheadEnabled
        tuningState = state
        _kickCharacter.value = state.kickCharacter
        bassVisualizer.tuning = state
        // The AOT lookahead toggle can flip at runtime from the Studio — start or stop
        // the scheduler on the transition (not on every slider move).
        if (state.isAotLookaheadEnabled != wasAotEnabled) {
            if (state.isAotLookaheadEnabled) restartLookahead() else stopLookahead()
        }
    }

    /**
     * Media3 item for a song. Artwork Uri is null (not an empty-string parse) when the
     * track has no cover, so the notification falls back cleanly.
     */
    private fun mediaItemFor(song: Song): MediaItem {
        val meta = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(song.artworkUrl.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
            .build()
        return MediaItem.Builder()
            .setUri(song.audioUrl)
            .setMediaId(song.id)
            .setMediaMetadata(meta)
            .build()
    }

    private fun exoRepeatMode(mode: RepeatMode): Int = when (mode) {
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }

    /** Push the now-playing UI (title / progress / time) for [song] at [startPositionMs]. */
    private fun applySongUi(song: Song, startPositionMs: Long = 0L) {
        _currentSong.value = song
        val startSecs = (startPositionMs / 1000).toInt()
        _playbackProgress.value = if (song.durationSeconds > 0) {
            (startSecs.toFloat() / song.durationSeconds).coerceIn(0f, 1f)
        } else 0f
        _currentTimeText.value = formatTime(startSecs)
        _remainingTimeText.value = "-${formatTime((song.durationSeconds - startSecs).coerceAtLeast(0))}"
    }

    /**
     * Load the ENTIRE songsQueue into ExoPlayer as one playlist, starting at [startIndex].
     * This is the core of native playback: ExoPlayer auto-advances through the queue by
     * itself, and because it now holds real next/previous items the media notification
     * shows working skip controls. The old model loaded one item at a time, so the player
     * never had a "next" — hence no auto-advance past a single-song context and no skip
     * buttons on the notification.
     *
     * DO NOT call player.stop() — it tears down the audio renderer and kills the
     * Visualizer session; setMediaItems + prepare handles transitions correctly.
     */
    private fun loadQueue(startIndex: Int, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        try {
            if (songsQueue.isEmpty()) return
            val player = exoPlayer ?: run { initExoPlayer(); exoPlayer!! }
            stopProgressUpdate()

            val index = startIndex.coerceIn(0, songsQueue.size - 1)
            currentSongIndex = index
            applySongUi(songsQueue[index], startPositionMs)

            player.setMediaItems(songsQueue.map { mediaItemFor(it) }, index, startPositionMs)
            player.repeatMode = exoRepeatMode(_repeatMode.value)
            player.setPlaybackSpeed(_playbackSpeed.value)
            player.prepare()
            player.playWhenReady = autoPlay
            if (autoPlay) startPlaybackService()
            saveSession()
        } catch (e: Exception) {
            Log.e("HaptiqPlayer", "Error loading queue: ${e.message}", e)
        }
    }

    override fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pauseMedia()
        } else {
            resumeMedia()
        }
    }

    private fun resumeMedia() {
        exoPlayer?.playWhenReady = true
        _isPlaying.value = true
        startProgressUpdate()
        startPlaybackService()
    }

    override fun seekTo(progress: Float) {
        val player = exoPlayer ?: return
        val seekMs = (progress * player.duration).toLong()
        player.seekTo(seekMs)
        updateProgressFlows()
    }

    private fun pauseMedia() {
        exoPlayer?.playWhenReady = false
        _isPlaying.value = false
        stopProgressUpdate()
        // Pause is the natural "user is leaving" moment — pin the resume position
        exoPlayer?.let { playbackStateStore.savePosition(it.currentPosition) }
    }

    override fun playNext() {
        val player = exoPlayer ?: return
        if (songsQueue.isEmpty()) return
        // Manual skips always wrap; onMediaItemTransition syncs currentSong/index.
        val next = (currentSongIndex + 1) % songsQueue.size
        player.seekTo(next, 0L)
        player.playWhenReady = true
        startPlaybackService()
    }

    override fun playPrevious() {
        val player = exoPlayer ?: return
        if (songsQueue.isEmpty()) return
        val prev = if (currentSongIndex - 1 < 0) songsQueue.size - 1 else currentSongIndex - 1
        player.seekTo(prev, 0L)
        player.playWhenReady = true
        startPlaybackService()
    }

    override fun playAt(index: Int) {
        val player = exoPlayer ?: return
        if (index !in songsQueue.indices) return
        player.seekTo(index, 0L)
        player.playWhenReady = true
        startPlaybackService()
    }

    override fun toggleShuffle() {
        val enable = !_isShuffleEnabled.value
        _isShuffleEnabled.value = enable
        val current = songsQueue.getOrNull(currentSongIndex)
        if (enable) {
            if (current != null) {
                songsQueue = (listOf(current) + (songsQueue - current).shuffled()).toMutableList()
                currentSongIndex = 0
            } else {
                songsQueue.shuffle()
            }
        } else {
            // Restore the original order minus anything removed since
            val stillPresent = songsQueue.map { it.id }.toSet()
            songsQueue = originalQueue.filter { it.id in stillPresent }.toMutableList()
            currentSongIndex = songsQueue.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        }
        publishQueue()
        // Rebuild the ExoPlayer playlist to match the new order, keeping the current
        // track playing from where it was so shuffle doesn't restart the song.
        loadQueue(currentSongIndex, startPositionMs = exoPlayer?.currentPosition ?: 0L, autoPlay = _isPlaying.value)
    }

    override fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        // ALL/ONE are now native ExoPlayer loop modes; onSongCompleted only handles the
        // OFF end-of-queue case.
        exoPlayer?.repeatMode = exoRepeatMode(_repeatMode.value)
        saveSession()
    }

    // ── Queue editing ─────────────────────────────────────────
    override fun moveInQueue(from: Int, to: Int) {
        if (from !in songsQueue.indices || to !in songsQueue.indices || from == to) return
        val song = songsQueue.removeAt(from)
        songsQueue.add(to, song)
        // Mirror the move into ExoPlayer; it keeps currentMediaItemIndex pointing at the
        // still-playing track, so we read the authoritative index back from it.
        exoPlayer?.moveMediaItem(from, to)
        currentSongIndex = exoPlayer?.currentMediaItemIndex ?: currentSongIndex
        publishQueue()
        saveSession()
    }

    override fun removeFromQueue(index: Int) {
        if (index !in songsQueue.indices || index == currentSongIndex) return
        songsQueue.removeAt(index)
        exoPlayer?.removeMediaItem(index)
        currentSongIndex = exoPlayer?.currentMediaItemIndex
            ?: (if (index < currentSongIndex) currentSongIndex - 1 else currentSongIndex)
        publishQueue()
        saveSession()
    }

    override fun playSongNext(index: Int) {
        moveInQueue(index, (currentSongIndex + 1).coerceAtMost(songsQueue.size - 1))
    }

    override fun enqueueNext(song: Song) {
        if (songsQueue.isEmpty()) {
            // Nothing playing yet — the swiped song simply becomes the session
            setSongs(listOf(song), 0)
            return
        }
        val existing = songsQueue.indexOfFirst { it.id == song.id }
        if (existing >= 0) {
            if (existing != currentSongIndex) playSongNext(existing)
        } else {
            val insertAt = (currentSongIndex + 1).coerceAtMost(songsQueue.size)
            songsQueue.add(insertAt, song)
            exoPlayer?.addMediaItem(insertAt, mediaItemFor(song))
            publishQueue()
            saveSession()
        }
    }

    private fun onSongCompleted() {
        // With a native playlist ExoPlayer auto-advances and loops (repeat ALL/ONE) on
        // its own, so STATE_ENDED now fires only at the very end of the queue with repeat
        // OFF. Park paused at the start of the last track.
        pauseMedia()
        exoPlayer?.seekTo(currentSongIndex, 0L)
        updateProgressFlows()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = playerScope.launch {
            while (isActive) {
                updateProgressFlows()
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateProgressFlows() {
        val player = exoPlayer ?: return
        if (player.duration > 0) {
            val progress = player.currentPosition.toFloat() / player.duration.toFloat()
            _playbackProgress.value = progress

            // Checkpoint the resume position every ~5s so a swipe-kill loses at most that
            val now = System.currentTimeMillis()
            if (_isPlaying.value && now - lastPositionSaveAt > 5_000L) {
                lastPositionSaveAt = now
                playbackStateStore.savePosition(player.currentPosition)
            }

            val currentSecs = (player.currentPosition / 1000).toInt()
            _currentTimeText.value = formatTime(currentSecs)

            val totalSecs = (player.duration / 1000).toInt()
            val remainingSecs = totalSecs - currentSecs
            _remainingTimeText.value = "-${formatTime(remainingSecs)}"
        }
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }



    override fun stopPlayback() {
        pauseMedia()
        stopLookahead()
        vibrator?.cancel() // Kill haptics immediately when playback is stopped
        // Do not release the player here — it is reused across song changes.
        // Full teardown happens in destroy() called when the singleton is no longer needed.
    }

    override fun dismissPlayback() {
        stopPlayback()
        exoPlayer?.clearMediaItems()
        _currentSong.value = null
        _isPlaying.value = false
        _playbackProgress.value = 0f
        _currentTimeText.value = "0:00"
        _remainingTimeText.value = "-0:00"
        // Without this, restoreSession() resurrects the dismissed track on next launch
        playbackStateStore.clear()
    }

    /**
     * Full teardown — call only when the app is finishing (e.g., process death).
     * In normal use, reuse the singleton player via stopPlayback().
     */
    fun destroy() {
        pauseMedia()
        stopLookahead()
        bassVisualizer.release()
        kickLatencyTracker.stop()
        exoPlayer?.release()
        exoPlayer = null
        volumeObserver?.let { context.contentResolver.unregisterContentObserver(it) }
        volumeObserver = null
        // Cancels progressJob/sleepJob too — without this the SupervisorJob (and any
        // coroutine still parked in a delay()) outlives the player it was driving.
        playerScope.cancel()
    }

    override fun getPlayer(): ExoPlayer? = exoPlayer

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, HaptiqPlaybackService::class.java)
            // Just start the service normally. Since the user is interacting with the app in the foreground,
            // Android allows this. MediaSessionService will automatically call startForeground() once playback begins,
            // preventing the 5-second startForeground timeout crash caused by slow buffering.
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("HaptiqPlayer", "Failed to start HaptiqPlaybackService: ${e.message}")
        }
    }
}
