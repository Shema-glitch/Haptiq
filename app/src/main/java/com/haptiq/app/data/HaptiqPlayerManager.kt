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
import com.haptiq.app.playback.HaptiqPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HaptiqPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStateStore: PlaybackStateStore
) : PlayerManager {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var exoPlayer: ExoPlayer? = null
    private var vibrator: Vibrator? = null

    // Audio processing — created once, re-attached on audio session changes
    private val hapticMapper = HapticMapper()
    private val bassVisualizer = BassVisualizer { bassEnergy ->
        onBassEnergyDetected(bassEnergy)
    }

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

    private var lastPositionSaveAt = 0L

    // Live DSP tuning state — written from UI thread, read from Visualizer callback thread
    @Volatile private var tuningState = HapticTuningState()

    init {
        initVibrator()
        initExoPlayer()
    }

    private fun initVibrator() {
        // Validated acquisition with MTK/Tecno dud-vibrator fallback — the previous
        // unvalidated VibratorManager path made song haptics silently dead on those
        // devices while the calibration test pulse (which had the fallback) worked.
        vibrator = com.haptiq.app.audio.DeviceVibrator.get(context)
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
                    } else {
                        stopProgressUpdate()
                        vibrator?.cancel() // Kill haptics immediately when playback is paused
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d("HaptiqPlayer", "onPlaybackStateChanged: $playbackState")
                    when (playbackState) {
                        Player.STATE_READY -> {
                            Log.d("HaptiqPlayer", "STATE_READY")
                            // Re-attach Visualizer every time a track becomes ready.
                            // This is necessary because player.prepare() may reuse the same
                            // audio session ID (no onAudioSessionIdChanged fires), yet the
                            // underlying audio renderer was rebuilt — the old Visualizer
                            // attachment is silently dead. Re-attaching here is the fix.
                            val sessionId = exoPlayer?.audioSessionId ?: return
                            if (sessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                                Log.d("HaptiqPlayer", "STATE_READY: re-attaching Visualizer to session $sessionId")
                                bassVisualizer.attach(sessionId)
                            }
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
                    updateProgressFlows()
                }
            })
        }
        exoPlayer = player
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
                bassGain = bassEnergy.bassGain
            )
        }
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
            loadSong(songsQueue[currentSongIndex])
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
        exoPlayer?.repeatMode =
            if (snapshot.repeatMode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        publishQueue()
        loadSong(songsQueue[index], autoPlay = false, startPositionMs = snapshot.positionMs)
    }

    override fun setHapticActive(active: Boolean) {
        _hapticActive.value = active
    }

    override fun setPreset(presetId: String) {
        _currentPresetId.value = presetId
        // Update intensity based on default mapping for preset
        when (presetId) {
            "deep_bass" -> _intensity.value = 75
            "punch" -> _intensity.value = 90
            "concert" -> _intensity.value = 80
            "soft_pulse" -> _intensity.value = 50
        }
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

    override fun updateTuning(state: HapticTuningState) {
        tuningState = state
        bassVisualizer.tuning = state
    }

    private fun loadSong(song: Song, autoPlay: Boolean = true, startPositionMs: Long = 0L) {
        try {
            Log.d("HaptiqPlayer", "Loading song: ${song.title} (autoPlay=$autoPlay)")

            val player = exoPlayer ?: run {
                initExoPlayer()
                exoPlayer!!
            }

            // DO NOT call player.stop() here — it tears down the audio renderer
            // and kills the Visualizer's session. ExoPlayer handles track transitions
            // correctly with just setMediaItem() + prepare().
            stopProgressUpdate()

            _currentSong.value = song
            val startSecs = (startPositionMs / 1000).toInt()
            _playbackProgress.value = if (song.durationSeconds > 0) {
                (startSecs.toFloat() / song.durationSeconds).coerceIn(0f, 1f)
            } else 0f
            _currentTimeText.value = formatTime(startSecs)
            _remainingTimeText.value = "-${formatTime((song.durationSeconds - startSecs).coerceAtLeast(0))}"

            val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(android.net.Uri.parse(song.artworkUrl))
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(song.audioUrl)
                .setMediaMetadata(mediaMetadata)
                .build()

            player.setMediaItem(mediaItem, startPositionMs)
            player.prepare()
            player.playWhenReady = autoPlay
            if (autoPlay) startPlaybackService()
            saveSession()
        } catch (e: Exception) {
            Log.e("HaptiqPlayer", "Error loading song: ${e.message}", e)
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
        if (songsQueue.isEmpty()) return
        // Manual skips always wrap — the queue is already in shuffled order when shuffle is on
        currentSongIndex = (currentSongIndex + 1) % songsQueue.size
        loadSong(songsQueue[currentSongIndex])
    }

    override fun playPrevious() {
        if (songsQueue.isEmpty()) return
        currentSongIndex = if (currentSongIndex - 1 < 0) songsQueue.size - 1 else currentSongIndex - 1
        loadSong(songsQueue[currentSongIndex])
    }

    override fun playAt(index: Int) {
        if (index !in songsQueue.indices) return
        currentSongIndex = index
        loadSong(songsQueue[index])
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
        saveSession()
    }

    override fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        exoPlayer?.repeatMode =
            if (_repeatMode.value == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        saveSession()
    }

    // ── Queue editing ─────────────────────────────────────────
    override fun moveInQueue(from: Int, to: Int) {
        if (from !in songsQueue.indices || to !in songsQueue.indices || from == to) return
        val song = songsQueue.removeAt(from)
        songsQueue.add(to, song)
        currentSongIndex = when (currentSongIndex) {
            from -> to
            in (minOf(from, to))..(maxOf(from, to)) ->
                if (from < to) currentSongIndex - 1 else currentSongIndex + 1
            else -> currentSongIndex
        }
        publishQueue()
        saveSession()
    }

    override fun removeFromQueue(index: Int) {
        if (index !in songsQueue.indices || index == currentSongIndex) return
        songsQueue.removeAt(index)
        if (index < currentSongIndex) currentSongIndex--
        publishQueue()
        saveSession()
    }

    override fun playSongNext(index: Int) {
        moveInQueue(index, (currentSongIndex + 1).coerceAtMost(songsQueue.size - 1))
    }

    private fun onSongCompleted() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // ExoPlayer's REPEAT_MODE_ONE normally handles this; kept as a fallback
                exoPlayer?.seekTo(0)
                exoPlayer?.playWhenReady = true
            }
            RepeatMode.ALL -> playNext()
            RepeatMode.OFF -> {
                if (currentSongIndex < songsQueue.size - 1) {
                    playNext()
                } else {
                    // End of queue: stay on the last track, paused at the start
                    pauseMedia()
                    exoPlayer?.seekTo(0)
                    updateProgressFlows()
                }
            }
        }
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
        vibrator?.cancel() // Kill haptics immediately when playback is stopped
        // Do not release the player here — it is reused across song changes.
        // Full teardown happens in destroy() called when the singleton is no longer needed.
    }

    /**
     * Full teardown — call only when the app is finishing (e.g., process death).
     * In normal use, reuse the singleton player via stopPlayback().
     */
    fun destroy() {
        pauseMedia()
        bassVisualizer.release()
        exoPlayer?.release()
        exoPlayer = null
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
