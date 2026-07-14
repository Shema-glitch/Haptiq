package com.haptiq.app.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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

enum class SortMode(val label: String) {
    TITLE("Title A–Z"),
    ARTIST("Artist A–Z"),
    DURATION("Longest first")
}

data class HaptiqUiState(
    val songs: List<Song> = emptyList(),
    val sortMode: SortMode = SortMode.TITLE,
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
    val sleepTimerMinutes: Int = 0,
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
    val favoriteIds: Set<String> = emptySet(),
    // Playlists
    val playlists: List<PlaylistWithCount> = emptyList(),
    val activePlaylistId: Long? = null,
    val activePlaylistSongs: List<Song> = emptyList()
)

sealed interface HaptiqUiAction {
    data class Search(val query: String) : HaptiqUiAction
    data class SetSortMode(val mode: SortMode) : HaptiqUiAction
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
    data class SetSleepTimer(val minutes: Int) : HaptiqUiAction
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
    // Playlists
    data class CreatePlaylist(val name: String, val firstSongId: String? = null) : HaptiqUiAction
    data class RenamePlaylist(val id: Long, val name: String) : HaptiqUiAction
    data class DeletePlaylist(val id: Long) : HaptiqUiAction
    data class AddToPlaylist(val playlistId: Long, val songId: String) : HaptiqUiAction
    data class RemoveFromPlaylist(val playlistId: Long, val songId: String) : HaptiqUiAction
    data class OpenPlaylist(val id: Long) : HaptiqUiAction
    // Haptic Tuning Dashboard
    data class SetAdaptiveEnabled(val enabled: Boolean) : HaptiqUiAction
    data class SetKickEnabled(val enabled: Boolean) : HaptiqUiAction
    data class SetBassEnabled(val enabled: Boolean) : HaptiqUiAction
    data class SetKickThreshold(val value: Float) : HaptiqUiAction
    data class SetNoiseFloorGate(val value: Float) : HaptiqUiAction
    data class SetKickFreqRange(val min: Int, val max: Int) : HaptiqUiAction
    data class SetSubDroneThreshold(val value: Float) : HaptiqUiAction
    data class SetBassFreqRange(val min: Int, val max: Int) : HaptiqUiAction
    data class SetBassGain(val value: Float) : HaptiqUiAction
    object ResetTuning : HaptiqUiAction
}

@HiltViewModel
class HaptiqViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val recentSongRepository: RecentSongRepository,
    private val presetRepository: PresetRepository,
    private val calibrationRepository: CalibrationRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    /** Drives which playlist's songs are collected into activePlaylistSongs. */
    private val activePlaylistId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(HaptiqUiState())
    val uiState: StateFlow<HaptiqUiState> = _uiState.asStateFlow()

    /** Separate StateFlow for live DSP tuning — not part of HaptiqUiState to avoid snapshot churn. */
    private val _hapticTuning = MutableStateFlow(HapticTuningState())
    val hapticTuning: StateFlow<HapticTuningState> = _hapticTuning.asStateFlow()

    private val vibrator: Vibrator? = com.haptiq.app.audio.DeviceVibrator.get(context)

    /**
     * Collects this flow in its own coroutine and folds each value into [_uiState].
     * Kept as one collector per source flow (not merged via combine()) — a previous nested
     * flatMapLatest/collect here caused a leak, so each flow gets an independent subscription.
     */
    private inline fun <T> Flow<T>.collectIntoState(
        crossinline transform: (HaptiqUiState, T) -> HaptiqUiState
    ) {
        viewModelScope.launch {
            collect { value -> _uiState.update { transform(it, value) } }
        }
    }

    init {
        // Hot StateFlow — collected once here, but scanDevice() (below) can push new
        // values into it at any time and this same collector keeps receiving them.
        songRepository.allSongs.collectIntoState { state, songs -> state.copy(songs = songs) }
        recentSongRepository.allRecentSongs.collectIntoState { state, recents -> state.copy(recentSongs = recents) }
        playerManager.isPlaying.collectIntoState { state, playing -> state.copy(isPlaying = playing) }
        playerManager.playbackProgress.collectIntoState { state, progress -> state.copy(playbackProgress = progress) }
        playerManager.currentTimeText.collectIntoState { state, time -> state.copy(currentTimeText = time) }
        playerManager.remainingTimeText.collectIntoState { state, time -> state.copy(remainingTimeText = time) }
        playerManager.hapticActive.collectIntoState { state, active -> state.copy(hapticActive = active) }
        playerManager.currentPresetId.collectIntoState { state, preset -> state.copy(currentPresetId = preset) }
        playerManager.intensity.collectIntoState { state, intensity -> state.copy(intensity = intensity) }
        playerManager.batterySaverEnabled.collectIntoState { state, enabled -> state.copy(batterySaverEnabled = enabled) }
        playerManager.sleepTimerMinutes.collectIntoState { state, mins -> state.copy(sleepTimerMinutes = mins) }
        playlistRepository.allPlaylists.collectIntoState { state, lists -> state.copy(playlists = lists) }

        // Songs of whichever playlist is open — swaps subscriptions when it changes.
        viewModelScope.launch {
            activePlaylistId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else playlistRepository.songsOf(id)
            }.collect { songs ->
                _uiState.update { it.copy(activePlaylistSongs = songs) }
            }
        }
        playerManager.visualizerBands.collectIntoState { state, bands -> state.copy(visualizerBands = bands) }

        // Has a side effect (recording history) beyond the state copy, so it stays its own launch.
        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                _uiState.update { it.copy(currentSong = song) }
                if (song != null) {
                    recentSongRepository.addRecentSong(song.id)
                }
            }
        }

        // Has a side effect (pushing intensity back into the player) beyond the state copy,
        // and re-derives from currentPresetId via flatMapLatest — kept as its own launch
        // (fixes a prior nested-collect leak; do not fold into currentPresetId's collector above).
        viewModelScope.launch {
            playerManager.currentPresetId.flatMapLatest { preset ->
                presetRepository.getPresetById(preset)
            }.collect { savedPreset ->
                playerManager.updateIntensity(savedPreset.intensity)
                _uiState.update { it.copy(intensity = savedPreset.intensity) }
            }
        }

        // Depends on a value (deviceModel) computed once at startup, not itself a flow input.
        val deviceModel = Build.MODEL ?: "UnknownDevice"
        calibrationRepository.getCalibration(deviceModel).collectIntoState { state, profile ->
            state.copy(calibrationMultiplier = profile.multiplier)
        }

        // Check DND status on launch
        refreshDndStatus()

        // Load the library once per process — scanDevice() populates the hot
        // songRepository.allSongs StateFlow collected above, so this doesn't need
        // its own separate state wiring.
        if (!songRepository.isLoaded()) {
            viewModelScope.launch { performScan(initialStatus = "Starting scan…") }
        }
    }

    private suspend fun performScan(initialStatus: String) {
        _uiState.update { it.copy(isScanning = true, scanError = null, scanProgress = 0, scanStatus = initialStatus) }
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
            is HaptiqUiAction.SetSortMode -> {
                _uiState.update { it.copy(sortMode = action.mode) }
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
            is HaptiqUiAction.SetSleepTimer -> {
                playerManager.setSleepTimer(action.minutes)
            }
            // ── Playlists ─────────────────────────────────────────
            is HaptiqUiAction.CreatePlaylist -> {
                if (action.name.isNotBlank()) viewModelScope.launch {
                    val id = playlistRepository.create(action.name)
                    action.firstSongId?.let { playlistRepository.addSong(id, it) }
                }
            }
            is HaptiqUiAction.RenamePlaylist -> {
                if (action.name.isNotBlank()) viewModelScope.launch {
                    playlistRepository.rename(action.id, action.name)
                }
            }
            is HaptiqUiAction.DeletePlaylist -> {
                viewModelScope.launch { playlistRepository.delete(action.id) }
            }
            is HaptiqUiAction.AddToPlaylist -> {
                viewModelScope.launch { playlistRepository.addSong(action.playlistId, action.songId) }
            }
            is HaptiqUiAction.RemoveFromPlaylist -> {
                viewModelScope.launch { playlistRepository.removeSong(action.playlistId, action.songId) }
            }
            is HaptiqUiAction.OpenPlaylist -> {
                activePlaylistId.value = action.id
                _uiState.update { it.copy(activePlaylistId = action.id) }
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
                viewModelScope.launch { performScan(initialStatus = "Starting scan…") }
            }
            // Rescan after runtime permission is granted
            is HaptiqUiAction.RescanLibrary -> {
                viewModelScope.launch { performScan(initialStatus = "Scanning…") }
            }
            // Refresh DND status (user may have toggled it)
            is HaptiqUiAction.RefreshDndStatus -> {
                refreshDndStatus()
            }
            // ── Haptic Tuning Dashboard ───────────────────────────────────────────
            is HaptiqUiAction.SetAdaptiveEnabled -> {
                _hapticTuning.update { it.copy(isAdaptiveEnabled = action.enabled) }
                playerManager.updateTuning(_hapticTuning.value)
            }
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
            is HaptiqUiAction.SetBassFreqRange -> {
                _hapticTuning.update { it.copy(bassFreqMinBin = action.min, bassFreqMaxBin = action.max) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetBassGain -> {
                _hapticTuning.update { it.copy(bassGain = action.value) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.ResetTuning -> {
                _hapticTuning.update { HapticTuningState() }
                playerManager.updateTuning(_hapticTuning.value)
            }
        }
    }

    private fun playCalibrationPulse() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Waveform: wait 0ms, then 80ms buzz at amplitude 255 — more reliable on MTK/Tecno
                val timings    = longArrayOf(0L, 80L, 60L, 60L)
                val amplitudes = intArrayOf(0, 255, 0, 180)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                // Pre-Oreo: simple timed vibration
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0L, 80L, 60L, 60L), -1)
            }
        } catch (e: Exception) {
            // Last-resort: basic one-shot
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(200L)
                }
            } catch (_: Exception) { /* give up gracefully */ }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.stopPlayback()
    }
}
