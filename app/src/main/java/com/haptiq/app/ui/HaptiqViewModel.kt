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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SortMode(val label: String) {
    TITLE("Title A–Z"),
    ARTIST("Artist A–Z"),
    DURATION("Longest first")
}

/** An installed app able to handle the system audio-effect (equalizer) intent. */
data class EqualizerApp(val label: String, val packageName: String, val activityName: String)

/**
 * Per-song AOT kick-map status, shown in the Studio's lookahead row. Mirrors what the
 * scheduler will actually do: READY songs pre-fire kicks early, NO_MAP songs fall back
 * to live detection, ANALYZING is the transient decode state.
 */
enum class AotMapState { NOT_ENABLED, ANALYZING, READY, NO_MAP }

/** Map state + kick counts — the counts are the on-device sanity check for the onset
 * detector and the beat grid (a heavy track should show most kicks on-beat). */
data class AotMapInfo(
    val state: AotMapState = AotMapState.NOT_ENABLED,
    val onsetCount: Int = 0,
    val onBeatCount: Int = 0,
    /** True when the map came from Teach kicks — hand-validated, nothing filtered. */
    val isUserMap: Boolean = false
)

/** One kick marker on the seek-preview scrubber: where the AOT map detected a kick,
 * and whether the beat grid says lookahead will actually pre-fire it there. */
data class KickMarker(
    val positionMs: Int,
    val onBeat: Boolean
)

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
    // Precomputed 0–1 bass envelope of the current track (null while analyzing or
    // undecodable) — drives the waveform seek bar and its haptic scrub preview
    val seekEnergy: FloatArray? = null,
    // True while the envelope for the current track is being decoded. Lets the seek
    // bar show a "rendering waveform" shimmer instead of looking bugged/empty.
    val seekEnergyLoading: Boolean = false,
    // Calibration Wizard states
    val calibrationStep: Int = 1, // 1: Play test pulse, 2: Select strength, 3: Success
    val calibrationStrength: String = "Medium", // "Weak", "Medium", "Strong"
    // S1/S2: Library scan states
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val scanProgress: Int = 0,
    val scanStatus: String = "",
    // Settings → Clear Cache: brief in-progress flag so the row can show "Clearing…"
    val isClearingCache: Boolean = false,
    // DND detection — haptics are suppressed when DND is active
    val isDndActive: Boolean = false,
    // Equalizer picker: null = hidden; non-null shows the dialog listing installed
    // EQ apps (empty = none installed — dialog explains, instead of a blank screen)
    val equalizerChoices: List<EqualizerApp>? = null,
    // AOT lookahead: whether the current song has a kick map (and how many kicks).
    // NOT_ENABLED when the lookahead toggle is off; resolved per-song otherwise.
    val aotMapInfo: AotMapInfo = AotMapInfo(),
    // Kick-onset overlay for the seek-preview scrubber — where the AOT map detected
    // kicks (and which the beat grid will let pre-fire). Shown whether or not the
    // lookahead toggle is on, so you can preview the map before enabling it.
    val seekKickMarkers: List<KickMarker> = emptyList(),
    // Teach kicks: whether a hand-taught map exists for the current song (shown even
    // when the lookahead toggle is off, so the Clear button is always reachable).
    val hasTaughtMap: Boolean = false,
    // Teach kicks session state: active while the user is tapping along, and the
    // running tap count (drives the live counter on the tap surface).
    val isTeachingKicks: Boolean = false,
    val teachKickCount: Int = 0,
    // Kick-map editor (visual waveform): open flag, the user's pin placements, the
    // auto-detected candidates + beat grid to draw, and whether the map is loading.
    val isKickMapEditorOpen: Boolean = false,
    val kickMapEditorPins: List<Int> = emptyList(),
    val kickMapEditorOnsets: IntArray = IntArray(0),
    val kickMapEditorBeats: IntArray = IntArray(0),
    val kickMapEditorLoading: Boolean = false,
    // Transport state
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f,
    // Live play order (reflects shuffle + queue edits)
    val queue: List<Song> = emptyList(),
    // Favorites
    val favoriteIds: Set<String> = emptySet(),
    // Playlists
    val playlists: List<PlaylistWithCount> = emptyList(),
    val activePlaylistId: Long? = null,
    val activePlaylistSongs: List<Song> = emptyList(),
    // Settings
    val minDurationSec: Int = 30,
    val seekPreviewEnabled: Boolean = true,
    // Now Playing sheet: true = full screen, false = docked mini-player bar. The
    // sheet itself owns the live drag progress between the two (an Animatable, not
    // state — recomposing on every drag pixel would be wasteful); this bool is just
    // where it settles, so other consumers (system back button) agree on the state.
    val isPlayerExpanded: Boolean = false
)

sealed interface HaptiqUiAction {
    data class Search(val query: String) : HaptiqUiAction
    data class SetSortMode(val mode: SortMode) : HaptiqUiAction
    data class SelectSong(val songs: List<Song>, val index: Int) : HaptiqUiAction
    object TogglePlayPause : HaptiqUiAction
    data class Seek(val progress: Float) : HaptiqUiAction
    /** Fired continuously while scrubbing — pulses the bass intensity at that position. */
    data class SeekPreview(val progress: Float) : HaptiqUiAction
    object PlayNext : HaptiqUiAction
    object PlayPrevious : HaptiqUiAction
    /** Mini-player swipe-down: stop and hide playback entirely. */
    object DismissPlayback : HaptiqUiAction
    /** Now Playing sheet settled expanded (true) or collapsed (false). */
    data class SetPlayerExpanded(val expanded: Boolean) : HaptiqUiAction
    object ToggleShuffle : HaptiqUiAction
    object ToggleRepeat : HaptiqUiAction
    // Queue editing
    data class SetPlaybackSpeed(val speed: Float) : HaptiqUiAction
    data class SetVolume(val fraction: Float) : HaptiqUiAction
    data class PlayQueueIndex(val index: Int) : HaptiqUiAction
    data class MoveInQueue(val from: Int, val to: Int) : HaptiqUiAction
    data class RemoveFromQueue(val index: Int) : HaptiqUiAction
    data class PlaySongNext(val index: Int) : HaptiqUiAction
    /** Library swipe: put this song right after the current one. */
    data class EnqueueNext(val song: Song) : HaptiqUiAction
    data class ToggleHaptics(val active: Boolean) : HaptiqUiAction
    data class ChangePreset(val presetId: String) : HaptiqUiAction
    data class ChangeIntensity(val intensity: Int) : HaptiqUiAction
    data class SetBatterySaver(val enabled: Boolean) : HaptiqUiAction
    data class SetSleepTimer(val minutes: Int) : HaptiqUiAction
    // Settings
    data class SetMinDuration(val seconds: Int) : HaptiqUiAction
    data class SetSeekPreviewEnabled(val enabled: Boolean) : HaptiqUiAction
    object ClearRecents : HaptiqUiAction
    object ClearCache : HaptiqUiAction
    object OpenEqualizer : HaptiqUiAction
    data class SelectEqualizer(val app: EqualizerApp) : HaptiqUiAction
    object DismissEqualizerPicker : HaptiqUiAction
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
    data class SetKickGain(val value: Float) : HaptiqUiAction
    data class SetKickCharacter(val character: com.haptiq.app.audio.KickCharacter) : HaptiqUiAction
    data class SetSurfaceAdaptiveEnabled(val enabled: Boolean) : HaptiqUiAction
    data class SetAotLookaheadEnabled(val enabled: Boolean) : HaptiqUiAction
    object ResetTuning : HaptiqUiAction
    // ── Teach kicks: hand-built per-song kick maps (workaround for songs the auto
    // detector/grid reads poorly — tap along and the taps become the AOT map) ──
    object StartTeachKicks : HaptiqUiAction
    object TeachKickTap : HaptiqUiAction
    object SaveTeachKicks : HaptiqUiAction
    object CancelTeachKicks : HaptiqUiAction
    data class ClearTaughtKicks(val songId: String) : HaptiqUiAction
    // Share a taught map as a file (share sheet → another device/app) / import one
    // from a picked file onto the current song.
    data class ExportTaughtKicks(val song: Song) : HaptiqUiAction
    data class ImportTaughtKicks(val uri: android.net.Uri) : HaptiqUiAction
    // ── Kick-map editor: visual waveform pinning (tap peaks to mark kicks) ──
    object OpenKickMapEditor : HaptiqUiAction
    data class ToggleKickPin(val positionMs: Int) : HaptiqUiAction
    object ClearKickPins : HaptiqUiAction
    object SaveKickMapEditor : HaptiqUiAction
    object CancelKickMapEditor : HaptiqUiAction
}

// flatMapLatest (used below for activePlaylistId and currentPresetId switching) is
// still @ExperimentalCoroutinesApi in kotlinx-coroutines 1.10.2. Both usages are the
// textbook case it exists for — swap to a new inner flow and cancel the previous one
// when the key changes — so this is a deliberate, permanent opt-in, not a workaround.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HaptiqViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val recentSongRepository: RecentSongRepository,
    private val presetRepository: PresetRepository,
    private val calibrationRepository: CalibrationRepository,
    private val playlistRepository: PlaylistRepository,
    private val favoritesRepository: FavoritesRepository,
    private val energyMapRepository: EnergyMapRepository,
    private val userSettings: UserSettingsStore,
    private val playerManager: PlayerManager
) : ViewModel() {

    /** Drives which playlist's songs are collected into activePlaylistSongs. */
    private val activePlaylistId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(HaptiqUiState())
    val uiState: StateFlow<HaptiqUiState> = _uiState.asStateFlow()

    /** Separate StateFlow for live DSP tuning — not part of HaptiqUiState to avoid snapshot churn. */
    private val _hapticTuning = MutableStateFlow(HapticTuningState())
    val hapticTuning: StateFlow<HapticTuningState> = _hapticTuning.asStateFlow()

    // ── Teach kicks session state (tap positions collected during Start→Save) ──
    private val teachTaps = ArrayList<Long>()
    private val teachTapsLock = Any()
    // Taps closer than this (ms of playback position) are treated as double-taps.
    private val teachTapGapMs = 120L

    /** Per-song AOT kick-map status — resolved alongside the seek-energy analysis. */
    private val _aotMapInfo = MutableStateFlow(AotMapInfo())
    val aotMapInfo: StateFlow<AotMapInfo> = _aotMapInfo.asStateFlow()

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
        // Presets mutate the manager's tuning directly (setPreset), which the Studio's
        // character chips read via this tuning copy — keep the two in sync. No loop:
        // StateFlow dedupes, and the chip path writes _hapticTuning before updateTuning.
        viewModelScope.launch {
            playerManager.kickCharacter.collect { character ->
                _hapticTuning.update { it.copy(kickCharacter = character) }
            }
        }
        playerManager.batterySaverEnabled.collectIntoState { state, enabled -> state.copy(batterySaverEnabled = enabled) }
        playerManager.sleepTimerMinutes.collectIntoState { state, mins -> state.copy(sleepTimerMinutes = mins) }
        playerManager.queue.collectIntoState { state, q -> state.copy(queue = q) }
        playerManager.isShuffleEnabled.collectIntoState { state, on -> state.copy(isShuffle = on) }
        playerManager.repeatMode.collectIntoState { state, mode -> state.copy(repeatMode = mode) }
        playerManager.playbackSpeed.collectIntoState { state, s -> state.copy(playbackSpeed = s) }
        playerManager.volume.collectIntoState { state, v -> state.copy(volume = v) }
        playlistRepository.allPlaylists.collectIntoState { state, lists -> state.copy(playlists = lists) }
        favoritesRepository.allFavoriteIds.collectIntoState { state, ids -> state.copy(favoriteIds = ids) }
        _aotMapInfo.collectIntoState { state, info -> state.copy(aotMapInfo = info) }

        // Songs of whichever playlist is open — swaps subscriptions when it changes.
        viewModelScope.launch {
            activePlaylistId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else playlistRepository.songsOf(id)
            }.collect { songs ->
                _uiState.update { it.copy(activePlaylistSongs = songs) }
            }
        }
        playerManager.visualizerBands.collectIntoState { state, bands -> state.copy(visualizerBands = bands) }

        // Has side effects (recording history, kicking off track analysis) beyond the
        // state copy, so it stays its own launch.
        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                val songChanged = song?.id != _uiState.value.currentSong?.id
                _uiState.update {
                    it.copy(
                        currentSong = song,
                        seekEnergy = if (songChanged) null else it.seekEnergy,
                        seekEnergyLoading = if (songChanged) song != null else it.seekEnergyLoading,
                        seekKickMarkers = if (songChanged) emptyList() else it.seekKickMarkers
                    )
                }
                // A teach session belongs to one song — swapping tracks drops it (both
                // the old tap-along session and the visual editor).
                if (songChanged) {
                    synchronized(teachTapsLock) { teachTaps.clear() }
                    _uiState.update {
                        it.copy(
                            isTeachingKicks = false,
                            teachKickCount = 0,
                            hasTaughtMap = false,
                            isKickMapEditorOpen = false,
                            kickMapEditorPins = emptyList(),
                            kickMapEditorOnsets = IntArray(0),
                            kickMapEditorBeats = IntArray(0)
                        )
                    }
                }
                if (song != null) {
                    recentSongRepository.addRecentSong(song.id)
                    if (songChanged) loadSeekEnergy(song)
                } else {
                    _aotMapInfo.value = AotMapInfo() // nothing playing → no map
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

        // Hydrate persisted settings into state
        _uiState.update {
            it.copy(
                minDurationSec = userSettings.minDurationSec,
                seekPreviewEnabled = userSettings.seekPreviewEnabled
            )
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
            val startedAt = System.currentTimeMillis()
            val songs = songRepository.scanDevice { count, status ->
                _uiState.update { it.copy(scanProgress = count, scanStatus = status) }
            }
            // Land on a result the user can actually read — a scan that flashes by
            // in 200ms reads as broken, and "Done" says nothing.
            val result = if (songs.isEmpty()) {
                "No songs found — check your Music or Downloads folder"
            } else {
                "Found ${songs.size} ${if (songs.size == 1) "song" else "songs"}"
            }
            _uiState.update { it.copy(scanStatus = result, scanProgress = songs.size) }
            val elapsed = System.currentTimeMillis() - startedAt
            delay((2_000L - elapsed).coerceAtLeast(900L))
            _uiState.update { it.copy(isScanning = false) }
            // Library is known — bring back where the user left off (paused, no autoplay)
            playerManager.restoreSession(songs)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isScanning = false, scanError = e.message ?: "Scan failed. Please try again.")
            }
        }
    }

    /**
     * Check if Do Not Disturb is active.
     * DND suppresses vibration, so haptics won't work.
     *
     * Both signals must agree before we warn. Some OEM ROMs (observed on Tecno
     * Camon CLA5 / HiOS) report currentInterruptionFilter != ALL while
     * zen_mode is 0 — i.e. DND is actually off. A false "DND is blocking
     * haptics" banner that never goes away costs more trust than a missed one.
     */
    fun refreshDndStatus() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = notificationManager.currentInterruptionFilter
        val filterSaysDnd = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        val zenSaysDnd = runCatching {
            Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 0
        }.getOrDefault(true) // unreadable setting → fall back to the filter alone
        _uiState.update { it.copy(isDndActive = filterSaysDnd && zenSaysDnd) }
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
            is HaptiqUiAction.SeekPreview -> {
                previewSeekPulse(action.progress)
            }
            is HaptiqUiAction.PlayNext -> {
                playerManager.playNext()
            }
            is HaptiqUiAction.DismissPlayback -> {
                playerManager.dismissPlayback()
                _uiState.update { it.copy(isPlayerExpanded = false) }
            }
            is HaptiqUiAction.SetPlayerExpanded -> {
                _uiState.update { it.copy(isPlayerExpanded = action.expanded) }
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
            is HaptiqUiAction.SetPlaybackSpeed -> {
                playerManager.setPlaybackSpeed(action.speed)
            }
            is HaptiqUiAction.SetVolume -> {
                playerManager.setVolume(action.fraction)
            }
            is HaptiqUiAction.PlayQueueIndex -> {
                playerManager.playAt(action.index)
            }
            is HaptiqUiAction.MoveInQueue -> {
                playerManager.moveInQueue(action.from, action.to)
            }
            is HaptiqUiAction.RemoveFromQueue -> {
                playerManager.removeFromQueue(action.index)
            }
            is HaptiqUiAction.PlaySongNext -> {
                playerManager.playSongNext(action.index)
            }
            is HaptiqUiAction.EnqueueNext -> {
                playerManager.enqueueNext(action.song)
            }
            is HaptiqUiAction.ToggleFavorite -> {
                // Room is the source of truth; the collector above folds the new
                // set back into state, so no optimistic local mutation needed.
                val isFavorite = action.songId in _uiState.value.favoriteIds
                viewModelScope.launch { favoritesRepository.toggle(action.songId, isFavorite) }
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
            // ── Settings ──────────────────────────────────────────
            is HaptiqUiAction.SetMinDuration -> {
                userSettings.minDurationSec = action.seconds
                _uiState.update { it.copy(minDurationSec = action.seconds) }
                // The filter only applies at scan time, so rescan right away
                viewModelScope.launch { performScan(initialStatus = "Applying filter…") }
            }
            is HaptiqUiAction.SetSeekPreviewEnabled -> {
                userSettings.seekPreviewEnabled = action.enabled
                _uiState.update { it.copy(seekPreviewEnabled = action.enabled) }
            }
            is HaptiqUiAction.ClearRecents -> {
                viewModelScope.launch { recentSongRepository.clearAll() }
            }
            is HaptiqUiAction.ClearCache -> {
                if (!_uiState.value.isClearingCache) {
                    viewModelScope.launch {
                        _uiState.update { it.copy(isClearingCache = true) }
                        songRepository.clearCache()
                        // clearCache() already marks the repo for a fresh load;
                        // performScan republishes _uiState.songs via allSongs collection.
                        performScan(initialStatus = "Rebuilding artwork cache…")
                        _uiState.update { it.copy(isClearingCache = false) }
                    }
                }
            }
            is HaptiqUiAction.OpenEqualizer -> {
                // Never fire the EQ intent blind: some ROMs (Tecno) resolve it to a
                // broken blank panel. List every installed handler and let the user pick.
                val pm = context.packageManager
                val probe = equalizerIntent()
                val handlers = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    pm.queryIntentActivities(
                        probe, android.content.pm.PackageManager.ResolveInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION") pm.queryIntentActivities(probe, 0)
                }
                _uiState.update { s ->
                    s.copy(equalizerChoices = handlers.map { ri ->
                        EqualizerApp(
                            label = ri.loadLabel(pm).toString(),
                            packageName = ri.activityInfo.packageName,
                            activityName = ri.activityInfo.name
                        )
                    })
                }
            }
            is HaptiqUiAction.SelectEqualizer -> {
                _uiState.update { it.copy(equalizerChoices = null) }
                try {
                    context.startActivity(equalizerIntent().apply {
                        setClassName(action.app.packageName, action.app.activityName)
                    })
                } catch (_: Exception) {
                    // Chosen app failed to launch — nothing more we can do
                }
            }
            is HaptiqUiAction.DismissEqualizerPicker -> {
                _uiState.update { it.copy(equalizerChoices = null) }
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
            is HaptiqUiAction.SetKickGain -> {
                _hapticTuning.update { it.copy(kickGain = action.value) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetKickCharacter -> {
                _hapticTuning.update { it.copy(kickCharacter = action.character) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetSurfaceAdaptiveEnabled -> {
                _hapticTuning.update { it.copy(isSurfaceAdaptiveEnabled = action.enabled) }
                playerManager.updateTuning(_hapticTuning.value)
            }
            is HaptiqUiAction.SetAotLookaheadEnabled -> {
                _hapticTuning.update { it.copy(isAotLookaheadEnabled = action.enabled) }
                playerManager.updateTuning(_hapticTuning.value)
                val song = _uiState.value.currentSong
                if (action.enabled && song != null) {
                    // Resolve the current song's map immediately (cheap Room read when the
                    // seek-preview analysis already wrote it; a one-time decode otherwise).
                    viewModelScope.launch {
                        _aotMapInfo.value = AotMapInfo(AotMapState.ANALYZING)
                        val map = energyMapRepository.aotMapFor(song)
                        if (_uiState.value.currentSong?.id == song.id) {
                            _aotMapInfo.value = resolveAotMapInfo(map)
                            _uiState.update { it.copy(hasTaughtMap = map?.isUserMap == true) }
                        }
                    }
                } else if (!action.enabled) {
                    _aotMapInfo.value = AotMapInfo()
                }
            }
            is HaptiqUiAction.StartTeachKicks -> {
                val song = _uiState.value.currentSong ?: return
                synchronized(teachTapsLock) { teachTaps.clear() }
                _uiState.update { it.copy(isTeachingKicks = true, teachKickCount = 0) }
                // Make sure the taps have a map row to land on later (analyze once now,
                // off the UI thread, so the beat/onset ground truth is ready at save).
                viewModelScope.launch { energyMapRepository.autoOnsetsFor(song) }
            }
            is HaptiqUiAction.TeachKickTap -> {
                if (!_uiState.value.isTeachingKicks) return
                val pos = playerManager.getPlayer()?.currentPosition ?: 0L
                if (pos <= 0L) return // no playback position yet — nothing to anchor to
                val accepted = synchronized(teachTapsLock) {
                    val last = teachTaps.lastOrNull()
                    if (last != null && pos - last < teachTapGapMs) false else {
                        teachTaps.add(pos)
                        true
                    }
                }
                if (accepted) _uiState.update { it.copy(teachKickCount = teachTaps.size) }
            }
            is HaptiqUiAction.SaveTeachKicks -> {
                val song = _uiState.value.currentSong ?: return
                val taps = synchronized(teachTapsLock) {
                    teachTaps.map { it.toInt() }.toIntArray().also { teachTaps.clear() }
                }
                _uiState.update { it.copy(isTeachingKicks = false, teachKickCount = 0) }
                if (taps.isEmpty()) return
                viewModelScope.launch {
                    val saved = energyMapRepository.saveUserKicks(song, taps)
                    if (saved.isNotEmpty() && _uiState.value.currentSong?.id == song.id) {
                        // Refresh the overlay + status from the taught map and pick it up
                        // in the scheduler (cheap Room reads — the row already exists).
                        loadSeekEnergy(song)
                        playerManager.refreshAotMap()
                    }
                }
            }
            is HaptiqUiAction.CancelTeachKicks -> {
                synchronized(teachTapsLock) { teachTaps.clear() }
                _uiState.update { it.copy(isTeachingKicks = false, teachKickCount = 0) }
            }
            is HaptiqUiAction.ClearTaughtKicks -> {
                viewModelScope.launch {
                    energyMapRepository.clearUserKicks(action.songId)
                    val song = _uiState.value.currentSong
                    if (song?.id == action.songId) {
                        loadSeekEnergy(song) // auto map (if any) takes back over
                        playerManager.refreshAotMap()
                    }
                }
            }
            is HaptiqUiAction.ExportTaughtKicks -> {
                viewModelScope.launch {
                    val onsets = energyMapRepository.userKicksFor(action.song.id) ?: return@launch
                    exportKickMapFile(action.song, onsets)
                }
            }
            is HaptiqUiAction.OpenKickMapEditor -> {
                val song = _uiState.value.currentSong ?: return
                _uiState.update {
                    it.copy(
                        isKickMapEditorOpen = true,
                        kickMapEditorPins = emptyList(),
                        kickMapEditorOnsets = IntArray(0),
                        kickMapEditorBeats = IntArray(0),
                        kickMapEditorLoading = true
                    )
                }
                viewModelScope.launch {
                    // Raw auto candidates + beat grid (never the taught map — the taught
                    // pins load separately below), then seed pins from the existing map.
                    val auto = energyMapRepository.autoMapFor(song)
                    val taught = energyMapRepository.userKicksFor(song.id)
                    if (_uiState.value.currentSong?.id == song.id) {
                        _uiState.update {
                            it.copy(
                                kickMapEditorOnsets = auto?.onsets ?: IntArray(0),
                                kickMapEditorBeats = auto?.beats ?: IntArray(0),
                                kickMapEditorPins = taught?.toList() ?: emptyList(),
                                kickMapEditorLoading = false
                            )
                        }
                    }
                }
            }
            is HaptiqUiAction.ToggleKickPin -> {
                _uiState.update {
                    val pins = it.kickMapEditorPins.toMutableList()
                    if (pins.contains(action.positionMs)) pins.remove(action.positionMs) else pins.add(action.positionMs)
                    it.copy(kickMapEditorPins = pins.sorted())
                }
            }
            is HaptiqUiAction.ClearKickPins -> {
                _uiState.update { it.copy(kickMapEditorPins = emptyList()) }
            }
            is HaptiqUiAction.SaveKickMapEditor -> {
                val song = _uiState.value.currentSong ?: return
                val pins = _uiState.value.kickMapEditorPins
                _uiState.update { it.copy(isKickMapEditorOpen = false) }
                viewModelScope.launch {
                    if (pins.size < 2) {
                        // Fewer than two pins — there's no map to speak of; treat as a clear.
                        energyMapRepository.clearUserKicks(song.id)
                    } else {
                        energyMapRepository.saveUserKicksRaw(song, pins.toIntArray())
                    }
                    if (_uiState.value.currentSong?.id == song.id) {
                        loadSeekEnergy(song) // overlay + status reflect the new map
                        playerManager.refreshAotMap()
                    }
                }
            }
            is HaptiqUiAction.CancelKickMapEditor -> {
                _uiState.update {
                    it.copy(
                        isKickMapEditorOpen = false,
                        kickMapEditorPins = emptyList(),
                        kickMapEditorOnsets = IntArray(0),
                        kickMapEditorBeats = IntArray(0),
                        kickMapEditorLoading = false
                    )
                }
            }
            is HaptiqUiAction.ImportTaughtKicks -> {
                val song = _uiState.value.currentSong ?: return
                viewModelScope.launch {
                    val json = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(action.uri)
                                ?.bufferedReader()?.use { it.readText() }
                        }.getOrNull()
                    } ?: return@launch
                    val file = KickMapCodec.decode(json) ?: return@launch // junk file → ignore
                    energyMapRepository.saveUserKicksRaw(song, file.onsets)
                    if (_uiState.value.currentSong?.id == song.id) {
                        loadSeekEnergy(song) // overlay + status now show the imported map
                        playerManager.refreshAotMap()
                    }
                }
            }
            is HaptiqUiAction.ResetTuning -> {
                _hapticTuning.update { HapticTuningState() }
                playerManager.updateTuning(_hapticTuning.value)
                _aotMapInfo.value = AotMapInfo() // lookahead resets to OFF with everything else
            }
        }
    }

    /**
     * Stage the taught map as a cache file and open the system share sheet with it.
     * The file is named after the song so the recipient can tell what it is; song IDs
     * differ between devices, so the JSON carries title/artist/duration for humans.
     */
    private fun exportKickMapFile(song: Song, onsets: IntArray) {
        val json = KickMapCodec.encode(song, onsets)
        val dir = java.io.File(context.cacheDir, "maps").apply { mkdirs() }
        val safeName = song.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim()
            .ifEmpty { song.id }
        val file = java.io.File(dir, "$safeName.hqmap")
        runCatching {
            file.writeText(json)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Haptiq kick map: ${song.title}")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                android.content.Intent.createChooser(share, "Share kick map").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /** Fold the onset/beat map into the Studio's displayed status. */
    private fun resolveAotMapInfo(map: AotMapData?): AotMapInfo {
        if (!_hapticTuning.value.isAotLookaheadEnabled) return AotMapInfo()
        if (map == null || map.onsets.isEmpty()) return AotMapInfo(AotMapState.NO_MAP)
        val onBeat = map.onsets.count {
            com.haptiq.app.audio.TrackEnergyAnalyzer.isOnBeat(it, map.beats)
        }
        return AotMapInfo(AotMapState.READY, map.onsets.size, onBeat, map.isUserMap)
    }

    /** Kept per-song so a slow analysis of the previous track can't clobber the new one. */
    private var seekEnergyJob: kotlinx.coroutines.Job? = null

    /** The standard audio-effect control-panel intent, targeted at our audio session. */
    private fun equalizerIntent() = android.content.Intent(
        android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL
    ).apply {
        putExtra(
            android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION,
            playerManager.getPlayer()?.audioSessionId ?: 0
        )
        putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(
            android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE,
            android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
        )
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun loadSeekEnergy(song: Song) {
        seekEnergyJob?.cancel()
        // The single decode pass computes both the seek-preview envelope and the kick-onset
        // map, so the map status resolves right after the envelope is ready.
        if (_hapticTuning.value.isAotLookaheadEnabled) {
            _aotMapInfo.value = AotMapInfo(AotMapState.ANALYZING)
        }
        seekEnergyJob = viewModelScope.launch {
            val energy = energyMapRepository.energyFor(song)
            // Row now exists (with onsets + beats) — a cheap read, no second decode.
            val map = if (energy != null) energyMapRepository.aotMapFor(song) else null
            if (_uiState.value.currentSong?.id == song.id) {
                // Marker overlay rides the same map: one tick per detected kick, flagged by
                // whether the beat grid will let lookahead pre-fire it there. Shown even
                // with the toggle off — it's the preview of what enabling it will do.
                val markers = map?.onsets?.map {
                    KickMarker(
                        positionMs = it,
                        onBeat = com.haptiq.app.audio.TrackEnergyAnalyzer.isOnBeat(it, map.beats)
                    )
                } ?: emptyList()
                _uiState.update {
                    it.copy(
                        seekEnergy = energy,
                        seekEnergyLoading = false,
                        seekKickMarkers = markers,
                        hasTaughtMap = map?.isUserMap == true
                    )
                }
                _aotMapInfo.value = resolveAotMapInfo(map)
            }
        }
    }

    // ── Haptic seek-preview: feel the bass at the scrub position ──────────────
    private var lastPreviewBucket = -1
    private var lastPreviewPulseAt = 0L

    private fun previewSeekPulse(progress: Float) {
        val state = _uiState.value
        val energy = state.seekEnergy ?: return
        if (energy.isEmpty() || !state.hapticActive || !state.seekPreviewEnabled) return
        val v = vibrator ?: return

        val bucket = (progress * (energy.size - 1)).toInt().coerceIn(0, energy.size - 1)
        val now = System.currentTimeMillis()
        // One pulse per envelope bucket, rate-limited so fast flings don't saturate the motor
        if (bucket == lastPreviewBucket || now - lastPreviewPulseAt < 70L) return
        lastPreviewBucket = bucket
        lastPreviewPulseAt = now

        val e = energy[bucket]
        if (e < 0.18f) return // silence stays silent — that contrast is the feature
        val amplitude = (40 + e * e * 215).toInt().coerceIn(1, 255)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(24L, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(24L)
            }
        } catch (_: Exception) {}
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
