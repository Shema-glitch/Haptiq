package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

class HaptiqPlayerManager private constructor(private val context: Context) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null

    // State flows
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentTimeText = MutableStateFlow("0:00")
    val currentTimeText: StateFlow<String> = _currentTimeText.asStateFlow()

    private val _remainingTimeText = MutableStateFlow("-0:00")
    val remainingTimeText: StateFlow<String> = _remainingTimeText.asStateFlow()

    private val _hapticActive = MutableStateFlow(true)
    val hapticActive: StateFlow<Boolean> = _hapticActive.asStateFlow()

    private val _currentPresetId = MutableStateFlow("deep_bass")
    val currentPresetId: StateFlow<String> = _currentPresetId.asStateFlow()

    private val _intensity = MutableStateFlow(75) // 0 - 100
    val intensity: StateFlow<Int> = _intensity.asStateFlow()

    private val _batterySaverEnabled = MutableStateFlow(false)
    val batterySaverEnabled: StateFlow<Boolean> = _batterySaverEnabled.asStateFlow()

    // Real-time Visualizer frequencies (12 bands for UI drawing)
    private val _visualizerBands = MutableStateFlow(FloatArray(12) { 0.2f })
    val visualizerBands: StateFlow<FloatArray> = _visualizerBands.asStateFlow()

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var simulatedVisualizerJob: Job? = null

    // Queue management
    private var songsQueue = mutableListOf<Song>()
    private var currentSongIndex = 0
    private var isShuffle = false
    private var isRepeat = false

    // Safety rules helper variables
    private var lastHapticPulseTime = 0L
    private val minHapticCooldownMs = 20L
    private val maxHapticPulseDurationMs = 100L

    init {
        initVibrator()
        setupAudioSession()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun setSongs(songs: List<Song>, startIndex: Int) {
        songsQueue = songs.toMutableList()
        currentSongIndex = startIndex
        if (songsQueue.isNotEmpty()) {
            loadSong(songsQueue[currentSongIndex])
        }
    }

    fun setHapticActive(active: Boolean) {
        _hapticActive.value = active
    }

    fun setPreset(presetId: String) {
        _currentPresetId.value = presetId
        // Update intensity based on default mapping for preset
        when (presetId) {
            "deep_bass" -> _intensity.value = 75
            "punch" -> _intensity.value = 90
            "concert" -> _intensity.value = 80
            "soft_pulse" -> _intensity.value = 50
        }
    }

    fun updateIntensity(value: Int) {
        _intensity.value = value
    }

    fun setBatterySaver(enabled: Boolean) {
        _batterySaverEnabled.value = enabled
    }

    private fun loadSong(song: Song) {
        try {
            stopPlayback()
            _currentSong.value = song
            _playbackProgress.value = 0f
            _currentTimeText.value = "0:00"
            _remainingTimeText.value = "-${formatTime(song.durationSeconds)}"

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(song.audioUrl)
                setOnPreparedListener {
                    // Start playback automatically on prepare if already playing or selected
                    if (_isPlaying.value) {
                        startMedia()
                    }
                }
                setOnCompletionListener {
                    onSongCompleted()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("HaptiqPlayerManager", "Error loading song: ${e.message}")
        }
    }

    private fun startMedia() {
        if (requestAudioFocus()) {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressUpdate()
            setupVisualizer()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            pauseMedia()
        } else {
            startMedia()
        }
    }

    fun seekTo(progress: Float) {
        val player = mediaPlayer ?: return
        val seekMs = (progress * player.duration).toInt()
        player.seekTo(seekMs)
        updateProgressFlows()
    }

    private fun pauseMedia() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        stopProgressUpdate()
        releaseVisualizer()
    }

    fun playNext() {
        if (songsQueue.isEmpty()) return
        if (isShuffle) {
            currentSongIndex = (0 until songsQueue.size).random()
        } else {
            currentSongIndex = (currentSongIndex + 1) % songsQueue.size
        }
        loadSong(songsQueue[currentSongIndex])
        if (_isPlaying.value) {
            startMedia()
        }
    }

    fun playPrevious() {
        if (songsQueue.isEmpty()) return
        if (isShuffle) {
            currentSongIndex = (0 until songsQueue.size).random()
        } else {
            currentSongIndex = if (currentSongIndex - 1 < 0) songsQueue.size - 1 else currentSongIndex - 1
        }
        loadSong(songsQueue[currentSongIndex])
        if (_isPlaying.value) {
            startMedia()
        }
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
    }

    fun toggleRepeat() {
        isRepeat = !isRepeat
        mediaPlayer?.isLooping = isRepeat
    }

    private fun onSongCompleted() {
        if (isRepeat) {
            mediaPlayer?.start()
        } else {
            playNext()
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
        val player = mediaPlayer ?: return
        if (player.duration > 0) {
            val progress = player.currentPosition.toFloat() / player.duration.toFloat()
            _playbackProgress.value = progress

            val currentSecs = player.currentPosition / 1000
            _currentTimeText.value = formatTime(currentSecs)

            val totalSecs = player.duration / 1000
            val remainingSecs = totalSecs - currentSecs
            _remainingTimeText.value = "-${formatTime(remainingSecs)}"
        }
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }

    private fun setupVisualizer() {
        val player = mediaPlayer ?: return
        try {
            releaseVisualizer()
            // Create a Visualizer targeting the mediaPlayer's specific audio session id
            visualizer = Visualizer(player.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0] // standard compact size
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        // Not strictly needed since we use FFT for haptics
                    }

                    override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) {
                            processFft(fft)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            stopSimulatedVisualizer()
        } catch (e: Exception) {
            Log.e("HaptiqPlayerManager", "Visualizer creation failed: ${e.message}, falling back to simulation")
            startSimulatedVisualizer()
        }
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
        } catch (e: Exception) {
            Log.e("HaptiqPlayerManager", "Error releasing visualizer: ${e.message}")
        }
        stopSimulatedVisualizer()
    }

    private fun startSimulatedVisualizer() {
        stopSimulatedVisualizer()
        simulatedVisualizerJob = playerScope.launch {
            while (isActive) {
                if (_isPlaying.value) {
                    val baseArray = FloatArray(12) { index ->
                        // Generate dynamic values between 0.1 and 1.0 based on simple sine models
                        val timeFactor = System.currentTimeMillis() / 250f
                        val offset = index * 0.5f
                        val raw = (Math.sin((timeFactor + offset).toDouble()).toFloat() + 1f) / 2f
                        // Multiply with a baseline for visual activity
                        0.1f + raw * 0.85f
                    }
                    _visualizerBands.value = baseArray

                    // Trigger simulated haptic pulse for fallbacks/emulators
                    val bassEnergy = (baseArray[0] + baseArray[1] + baseArray[2]) / 3f
                    if (bassEnergy > 0.65f) {
                        triggerHapticPulse(bassEnergy)
                    }
                } else {
                    _visualizerBands.value = FloatArray(12) { 0.15f }
                }
                delay(100)
            }
        }
    }

    private fun stopSimulatedVisualizer() {
        simulatedVisualizerJob?.cancel()
        simulatedVisualizerJob = null
    }

    private fun processFft(fft: ByteArray) {
        val bands = FloatArray(12) { 0f }
        val numFrequencies = fft.size / 2

        // Standard FFT layout mapping to 12 distinct visualizer bands
        for (i in 0 until numFrequencies) {
            val rfk = fft[2 * i].toFloat()
            val imk = fft[2 * i + 1].toFloat()
            val magnitude = hypot(rfk, imk)

            // Map standard frequencies to index
            val bandIndex = (i * 12) / numFrequencies
            if (bandIndex in 0..11) {
                bands[bandIndex] = bands[bandIndex] + magnitude
            }
        }

        // Normalize bands to a 0.0 - 1.0 range
        val normalizedBands = FloatArray(12) { index ->
            val scaleFactor = when (index) {
                0, 1 -> 500f // Bass bands need a bit more scaling
                else -> 800f
            }
            val rawValue = (bands[index] / scaleFactor).coerceIn(0.1f, 1.0f)
            rawValue
        }
        _visualizerBands.value = normalizedBands

        // Bass bands are typically 0 and 1
        val bassEnergy = (normalizedBands[0] + normalizedBands[1]) / 2f
        if (bassEnergy > 0.55f) {
            triggerHapticPulse(bassEnergy)
        }
    }

    private fun triggerHapticPulse(energy: Float) {
        if (!_hapticActive.value) return

        val now = System.currentTimeMillis()
        if (now - lastHapticPulseTime < minHapticCooldownMs) {
            return // Enforce Minimum Cooldown: 20ms
        }

        val baseIntensity = _intensity.value / 100f
        var scalar = baseIntensity * energy

        // Battery Saver: Reduce intensity by 30%
        if (_batterySaverEnabled.value) {
            scalar *= 0.70f
        }

        val amplitude = (scalar * 255).toInt().coerceIn(1, 255)

        // Custom Preset mapping for duration and frequency
        val duration = when (_currentPresetId.value) {
            "deep_bass" -> 80L  // Strong amplitude, longer pulses
            "punch" -> 40L      // Short, aggressive pulses
            "concert" -> 60L    // Balanced mid-length pulses
            "soft_pulse" -> 50L  // Gentle pulses
            else -> 60L
        }.coerceAtMost(maxHapticPulseDurationMs) // Maximum Pulse Duration: 100ms

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
            lastHapticPulseTime = now
        } catch (e: Exception) {
            // Silently ignore vibration issues in environments/emulators that don't support it
        }
    }

    private fun setupAudioSession() {
        // Prepare initial settings
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Incoming Call or permanent loss -> Pause Playback -> Suspend Haptics
                pauseMedia()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Notification Interruption -> Duck Audio
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus Regained -> Resume Playback -> Resume Haptics
                mediaPlayer?.setVolume(1.0f, 1.0f)
                startMedia()
            }
        }
    }

    fun stopPlayback() {
        pauseMedia()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        @Volatile
        private var INSTANCE: HaptiqPlayerManager? = null

        fun getInstance(context: Context): HaptiqPlayerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = HaptiqPlayerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
