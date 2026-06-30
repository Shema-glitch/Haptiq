package com.haptiq.app.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haptiq.app.audio.HapticTuningState
import com.haptiq.app.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val visualizerBands: FloatArray = FloatArray(10) { 0.2f },
    // Calibration Wizard states
    val calibrationStep: Int = 1, // 1: Play test pulse, 2: Select strength, 3: Success
    val calibrationStrength: String = "Medium", // "Weak", "Medium", "Strong"
    // S1/S2: Library scan states
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val scanProgress: Int = 0,
    val scanStatus: String = "",
    // DND detection — haptics are suppressed when DND is active
    val isDndActive: Boolean = false,
    // Transport state
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false,
    // Favorites
    val favoriteIds: Set<String> = emptySet()
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
    // D1: Library scan
    object ScanDevice : HaptiqUiAction
    // Rescan after permission granted
    object RescanLibrary : HaptiqUiAction
    // Refresh DND status
    object RefreshDndStatus : HaptiqUiAction
    // Favorites
    data class ToggleFavorite(val songId: String) : HaptiqUiAction
    // Haptic Tuning Dashboard
    data class SetKickEnabled(val enabled: Boolean) : HaptiqUiAction
    data class SetBassEnabled(val enabled: Boolean) : HaptiqUiAction
    data class SetKickThreshold(val value: Float) : HaptiqUiAction
    data class SetNoiseFloorGate(val value: Float) : HaptiqUiAction
    data class SetKickFreqRange(val min: Int, val max: Int) : HaptiqUiAction
    data class SetSubDroneThreshold(val value: Float) : HaptiqUiAction
}

@HiltViewModel
class HaptiqViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val recentSongRepository: RecentSongRepository,
    private val presetRepository: PresetRepository,
    private val calibrationRepository: CalibrationRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HaptiqUiState())
    val uiState: StateFlow<HaptiqUiState> = _uiState.asStateFlow()

    /** Separate StateFlow for live DSP tuning — not part of HaptiqUiState to avoid snapshot churn. */
    private val _hapticTuning = MutableStateFlow(HapticTuningState())
    val hapticTuning: StateFlow<HapticTuningState> = _hapticTuning.asStateFlow()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        // Collect songs
        viewModelScope.launch {
            songRepository.getAllSongs().collect { songList ->
                _uiState.update { it.copy(songs = songList) }
            }
        }

        // Collect recent songs
        viewModelScope.launch {
            recentSongRepository.allRecentSongs.collect { recents ->
                _uiState.update { it.copy(recentSongs = recents) }
            }
        }

        // Collect current song
        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                _uiState.update { it.copy(currentSong = song) }
                if (song != null) {
                    recentSongRepository.addRecentSong(song.id)
                }
            }
        }

        // Collect isPlaying
        viewModelScope.launch {
            playerManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        // Collect playbackProgress
        viewModelScope.launch {
            playerManager.playbackProgress.collect { progress ->
                _uiState.update { it.copy(playbackProgress = progress) }
            }
        }

        // Collect currentTimeText
        viewModelScope.launch {
            playerManager.currentTimeText.collect { time ->
                _uiState.update { it.copy(currentTimeText = time) }
            }
        }

        // Collect remainingTimeText
        viewModelScope.launch {
            playerManager.remainingTimeText.collect { time ->
                _uiState.update { it.copy(remainingTimeText = time) }
            }
        }

        // Collect hapticActive
        viewModelScope.launch {
            playerManager.hapticActive.collect { active ->
                _uiState.update { it.copy(hapticActive = active) }
            }
        }

        // Collect currentPresetId and fetch saved settings (fixed: no nested collect)
        viewModelScope.launch {
            playerManager.currentPresetId.collect { preset ->
                _uiState.update { it.copy(currentPresetId = preset) }
            }
        }

        // Separate collection for preset intensity (fixes nested collect leak)
        viewModelScope.launch {
            playerManager.currentPresetId.flatMapLatest { preset ->
                presetRepository.getPresetById(preset)
            }.collect { savedPreset ->
                playerManager.updateIntensity(savedPreset.intensity)
                _uiState.update { it.copy(intensity = savedPreset.intensity) }
            }
        }

        // Collect intensity
        viewModelScope.launch {
            playerManager.intensity.collect { intensity ->
                _uiState.update { it.copy(intensity = intensity) }
            }
        }

        // Collect batterySaverEnabled
        viewModelScope.launch {
            playerManager.batterySaverEnabled.collect { enabled ->
                _uiState.update { it.copy(batterySaverEnabled = enabled) }
            }
        }

        // Collect visualizerBands
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

        // Check DND status on launch
        refreshDndStatus()
    }

    /**
     * Check if Do Not Disturb is active.
     * DND suppresses vibration, so haptics won't work.
     */
    fun refreshDndStatus() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            @Suppress("DEPRECATION")
            val zenMode = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
            zenMode != 0
        }
        _uiState.update { it.copy(isDndActive = isDnd) }
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
                _uiState.update { it.copy(isShuffle = !it.isShuffle) }
            }
            is HaptiqUiAction.ToggleRepeat -> {
                playerManager.toggleRepeat()
                _uiState.update { it.copy(isRepeat = !it.isRepeat) }
            }
            is HaptiqUiAction.ToggleFavorite -> {
                val songId = action.songId
                _uiState.update { state ->
                    val newFavorites = if (songId in state.favoriteIds) {
                        state.favoriteIds - songId
                    } else {
                        state.favoriteIds + songId
                    }
                    state.copy(favoriteIds = newFavorites)
                }
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
            // D1: Scan device for audio files
            is HaptiqUiAction.ScanDevice -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isScanning = true, scanError = null, scanProgress = 0, scanStatus = "Starting scan…") }
                    try {
                        songRepository.scanDevice { count, status ->
                            _uiState.update { it.copy(scanProgress = count, scanStatus = status) }
                        }
                        _uiState.update { it.copy(isScanning = false, scanStatus = "Done") }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(isScanning = false, scanError = e.message ?: "Scan failed. Please try again.")
                        }
                    }
                }
            }
            // Rescan after runtime permission is granted
            is HaptiqUiAction.RescanLibrary -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isScanning = true, scanError = null, scanProgress = 0, scanStatus = "Scanning…") }
                    try {
                        songRepository.scanDevice { count, status ->
                            _uiState.update { it.copy(scanProgress = count, scanStatus = status) }
                        }
                        _uiState.update { it.copy(isScanning = false, scanStatus = "Done") }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(isScanning = false, scanError = e.message ?: "Scan failed. Please try again.")
                        }
                    }
                }
            }
            // Refresh DND status (user may have toggled it)
            is HaptiqUiAction.RefreshDndStatus -> {
                refreshDndStatus()
            }
            // ── Haptic Tuning Dashboard ───────────────────────────────────────────
            is HaptiqUiAction.SetKickEnabled -> {
                _hapticTuning.update { it.copy(isKickEnabled = action.enabled) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetBassEnabled -> {
                _hapticTuning.update { it.copy(isBassEnabled = action.enabled) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetKickThreshold -> {
                _hapticTuning.update { it.copy(kickThreshold = action.value) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetNoiseFloorGate -> {
                _hapticTuning.update { it.copy(noiseFloorGate = action.value) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetKickFreqRange -> {
                _hapticTuning.update { it.copy(kickFreqMinBin = action.min, kickFreqMaxBin = action.max) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetSubDroneThreshold -> {
                _hapticTuning.update { it.copy(subDroneThreshold = action.value) }
                playerManager.updateTuning(_hapticTuning.value)
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
