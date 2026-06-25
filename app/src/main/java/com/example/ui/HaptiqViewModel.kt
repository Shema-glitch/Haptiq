package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HaptiqUiState(
    val songs: List<Song> = emptyList(),
    val recentSongs: List<Song> = emptyList(),
    val searchQuery: String = "",
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val playbackProgress: Float = 0f,
    val currentTimeText: String = "0:00",
    val remainingTimeText: String = "-0:00",
    val hapticActive: Boolean = true,
    val currentPresetId: String = "deep_bass",
    val intensity: Int = 75,
    val batterySaverEnabled: Boolean = false,
    val calibrationMultiplier: Float = 1.0f,
    val visualizerBands: FloatArray = FloatArray(12) { 0.2f },
    // Calibration Wizard states
    val calibrationStep: Int = 1, // 1: Play test pulse, 2: Select strength, 3: Success
    val calibrationStrength: String = "Medium" // "Weak", "Medium", "Strong"
)

sealed interface HaptiqUiAction {
    data class Search(val query: String) : HaptiqUiAction
    data class SelectSong(val songs: List<Song>, val index: Int) : HaptiqUiAction
    object TogglePlayPause : HaptiqUiAction
    data class Seek(val progress: Float) : HaptiqUiAction
    object PlayNext : HaptiqUiAction
    object PlayPrevious : HaptiqUiAction
    object ToggleShuffle : HaptiqUiAction
    object ToggleRepeat : HaptiqUiAction
    data class ToggleHaptics(val active: Boolean) : HaptiqUiAction
    data class ChangePreset(val presetId: String) : HaptiqUiAction
    data class ChangeIntensity(val intensity: Int) : HaptiqUiAction
    data class SetBatterySaver(val enabled: Boolean) : HaptiqUiAction
    // Calibration
    object RunTestPulse : HaptiqUiAction
    data class SelectCalibrationStrength(val strength: String) : HaptiqUiAction
    object SaveCalibration : HaptiqUiAction
    object ResetCalibrationWizard : HaptiqUiAction
}

class HaptiqViewModel(context: Context) : ViewModel() {

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.haptiqDao()

    private val songRepository = SongRepository()
    private val recentSongRepository = RecentSongRepository(dao, songRepository)
    private val presetRepository = PresetRepository(dao)
    private val calibrationRepository = CalibrationRepository(dao)

    private val playerManager = HaptiqPlayerManager.getInstance(context)

    private val _uiState = MutableStateFlow(HaptiqUiState())
    val uiState: StateFlow<HaptiqUiState> = _uiState.asStateFlow()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        // Collect from repositories and playerManager
        viewModelScope.launch {
            songRepository.getAllSongs().collect { songList ->
                _uiState.update { it.copy(songs = songList) }
            }
        }

        viewModelScope.launch {
            recentSongRepository.allRecentSongs.collect { recents ->
                _uiState.update { it.copy(recentSongs = recents) }
            }
        }

        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                _uiState.update { it.copy(currentSong = song) }
                if (song != null) {
                    recentSongRepository.addRecentSong(song.id)
                }
            }
        }

        viewModelScope.launch {
            playerManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        viewModelScope.launch {
            playerManager.playbackProgress.collect { progress ->
                _uiState.update { it.copy(playbackProgress = progress) }
            }
        }

        viewModelScope.launch {
            playerManager.currentTimeText.collect { time ->
                _uiState.update { it.copy(currentTimeText = time) }
            }
        }

        viewModelScope.launch {
            playerManager.remainingTimeText.collect { time ->
                _uiState.update { it.copy(remainingTimeText = time) }
            }
        }

        viewModelScope.launch {
            playerManager.hapticActive.collect { active ->
                _uiState.update { it.copy(hapticActive = active) }
            }
        }

        viewModelScope.launch {
            playerManager.currentPresetId.collect { preset ->
                _uiState.update { it.copy(currentPresetId = preset) }
                // Fetch saved settings for this preset
                presetRepository.getPresetById(preset).collect { savedPreset ->
                    playerManager.updateIntensity(savedPreset.intensity)
                    _uiState.update { it.copy(intensity = savedPreset.intensity) }
                }
            }
        }

        viewModelScope.launch {
            playerManager.intensity.collect { intensity ->
                _uiState.update { it.copy(intensity = intensity) }
            }
        }

        viewModelScope.launch {
            playerManager.batterySaverEnabled.collect { enabled ->
                _uiState.update { it.copy(batterySaverEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            playerManager.visualizerBands.collect { bands ->
                _uiState.update { it.copy(visualizerBands = bands) }
            }
        }

        // Fetch device calibration profile
        viewModelScope.launch {
            val deviceModel = Build.MODEL ?: "UnknownDevice"
            calibrationRepository.getCalibration(deviceModel).collect { profile ->
                _uiState.update { it.copy(calibrationMultiplier = profile.multiplier) }
            }
        }
    }

    fun handleAction(action: HaptiqUiAction) {
        when (action) {
            is HaptiqUiAction.Search -> {
                _uiState.update { it.copy(searchQuery = action.query) }
            }
            is HaptiqUiAction.SelectSong -> {
                playerManager.setSongs(action.songs, action.index)
            }
            is HaptiqUiAction.TogglePlayPause -> {
                playerManager.togglePlayPause()
            }
            is HaptiqUiAction.Seek -> {
                playerManager.seekTo(action.progress)
            }
            is HaptiqUiAction.PlayNext -> {
                playerManager.playNext()
            }
            is HaptiqUiAction.PlayPrevious -> {
                playerManager.playPrevious()
            }
            is HaptiqUiAction.ToggleShuffle -> {
                playerManager.toggleShuffle()
            }
            is HaptiqUiAction.ToggleRepeat -> {
                playerManager.toggleRepeat()
            }
            is HaptiqUiAction.ToggleHaptics -> {
                playerManager.setHapticActive(action.active)
            }
            is HaptiqUiAction.ChangePreset -> {
                playerManager.setPreset(action.presetId)
            }
            is HaptiqUiAction.ChangeIntensity -> {
                playerManager.updateIntensity(action.intensity)
                viewModelScope.launch {
                    presetRepository.savePreset(
                        _uiState.value.currentPresetId,
                        intensity = action.intensity,
                        sensitivity = 75
                    )
                }
            }
            is HaptiqUiAction.SetBatterySaver -> {
                playerManager.setBatterySaver(action.enabled)
            }
            // Calibration wizard steps
            is HaptiqUiAction.RunTestPulse -> {
                playCalibrationPulse()
                _uiState.update { it.copy(calibrationStep = 2) }
            }
            is HaptiqUiAction.SelectCalibrationStrength -> {
                _uiState.update { it.copy(calibrationStrength = action.strength) }
            }
            is HaptiqUiAction.SaveCalibration -> {
                val strength = _uiState.value.calibrationStrength
                val multiplier = when (strength) {
                    "Weak" -> 0.5f
                    "Medium" -> 1.0f
                    "Strong" -> 1.5f
                    else -> 1.0f
                }
                _uiState.update { it.copy(calibrationMultiplier = multiplier, calibrationStep = 3) }
                viewModelScope.launch {
                    val deviceModel = Build.MODEL ?: "UnknownDevice"
                    calibrationRepository.saveCalibration(deviceModel, multiplier)
                }
            }
            is HaptiqUiAction.ResetCalibrationWizard -> {
                _uiState.update { it.copy(calibrationStep = 1, calibrationStrength = "Medium") }
            }
        }
    }

    private fun playCalibrationPulse() {
        try {
            val duration = 80L
            val amplitude = 200
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.stopPlayback()
    }
}
