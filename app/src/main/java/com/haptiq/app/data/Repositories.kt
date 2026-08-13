package com.haptiq.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreScanner: MediaStoreScanner,
    private val userSettings: UserSettingsStore
) {
    // Hot, always-current — unlike a one-shot cold Flow, every collector sees every
    // future scanDevice() result too, not just whatever was true at first collection.
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _songs.asStateFlow()

    private var hasLoaded = false

    fun isLoaded(): Boolean = hasLoaded

    fun getSongById(id: String): Song? {
        return _songs.value.find { it.id == id } ?: demoSongs.find { it.id == id }
    }

    /** Force the next scanDevice() to be treated as a fresh load. */
    fun rescan() {
        hasLoaded = false
    }

    /**
     * Scan the device for audio files and update [allSongs].
     * Runs on IO — MediaStore queries are blocking and must never run on Main.
     * Returns the scanned songs, or throws on failure.
     */
    suspend fun scanDevice(onProgress: ((Int, String) -> Unit)? = null): List<Song> = withContext(Dispatchers.IO) {
        val songs = mediaStoreScanner.scanForAudio(userSettings.minDurationSec, onProgress)
        _songs.value = songs
        hasLoaded = true
        songs
    }

    /**
     * Wipes every disk cache the library depends on — extracted artwork, Coil's
     * image cache — and forces a fresh scan. Coil's own disk+memory caches are
     * cleared too, not just the artwork source files: Coil keys entries by the
     * file:// URI, so a stale in-memory bitmap would otherwise keep showing until
     * evicted even after the source file was deleted and re-extracted.
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        mediaStoreScanner.clearArtworkCache()
        val loader = coil.Coil.imageLoader(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        rescan()
    }
}

class RecentSongRepository @Inject constructor(
    private val haptiqDao: HaptiqDao,
    private val songRepository: SongRepository
) {
    val allRecentSongs: Flow<List<Song>> = haptiqDao.getAllRecentSongs().map { list ->
        list.mapNotNull { recent -> songRepository.getSongById(recent.songId) }
    }

    suspend fun addRecentSong(songId: String) {
        haptiqDao.insertRecentSong(RecentSongs(songId, System.currentTimeMillis()))
    }

    suspend fun clearAll() {
        haptiqDao.clearRecentSongs()
    }
}

class PresetRepository @Inject constructor(private val haptiqDao: HaptiqDao) {
    private val defaultPresets = mapOf(
        "deep_bass" to SavedPresets("deep_bass", intensity = 75, sensitivity = 80),
        "punch" to SavedPresets("punch", intensity = 90, sensitivity = 95),
        "concert" to SavedPresets("concert", intensity = 80, sensitivity = 70),
        "soft_pulse" to SavedPresets("soft_pulse", intensity = 50, sensitivity = 60)
    )

    fun getPresetById(presetId: String): Flow<SavedPresets> {
        return haptiqDao.getPresetById(presetId).map { saved ->
            saved ?: defaultPresets[presetId] ?: SavedPresets(presetId, 75, 75)
        }
    }

    fun getAllPresets(): Flow<List<SavedPresets>> {
        return haptiqDao.getAllPresets().map { savedList ->
            val listMap = savedList.associateBy { it.presetId }
            defaultPresets.keys.map { key -> listMap[key] ?: defaultPresets[key]!! }
        }
    }

    suspend fun savePreset(presetId: String, intensity: Int, sensitivity: Int) {
        haptiqDao.insertPreset(SavedPresets(presetId, intensity, sensitivity))
    }
}

class PlaylistRepository @Inject constructor(
    private val haptiqDao: HaptiqDao,
    private val songRepository: SongRepository
) {
    val allPlaylists: Flow<List<PlaylistWithCount>> = haptiqDao.getAllPlaylists()

    /** Songs of a playlist, resolved against the scanned library, in stored order. */
    fun songsOf(playlistId: Long): Flow<List<Song>> =
        haptiqDao.getPlaylistSongs(playlistId).map { entries ->
            entries.mapNotNull { songRepository.getSongById(it.songId) }
        }

    suspend fun create(name: String): Long =
        haptiqDao.insertPlaylist(Playlist(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = haptiqDao.renamePlaylist(id, name.trim())

    suspend fun delete(id: Long) {
        haptiqDao.clearPlaylistSongs(id)
        haptiqDao.deletePlaylist(id)
    }

    suspend fun addSong(playlistId: Long, songId: String) {
        val position = haptiqDao.playlistSize(playlistId)
        haptiqDao.insertPlaylistSong(
            PlaylistSong(playlistId, songId, position, System.currentTimeMillis())
        )
    }

    suspend fun removeSong(playlistId: Long, songId: String) =
        haptiqDao.removePlaylistSong(playlistId, songId)
}

class FavoritesRepository @Inject constructor(private val haptiqDao: HaptiqDao) {
    val allFavoriteIds: Flow<Set<String>> = haptiqDao.getAllFavoriteIds().map { it.toSet() }

    suspend fun toggle(songId: String, isCurrentlyFavorite: Boolean) {
        if (isCurrentlyFavorite) {
            haptiqDao.deleteFavorite(songId)
        } else {
            haptiqDao.insertFavorite(FavoriteSong(songId, System.currentTimeMillis()))
        }
    }
}

/**
 * Kick-onset + beat-grid data for AOT lookahead — both come from the same decode pass.
 * [isUserMap] marks a user-taught map (Teach kicks): those are hand-validated, so the
 * scheduler pre-fires every onset without the beat-grid rejection (empty [beats]).
 */
data class AotMapData(
    val onsets: IntArray,
    val beats: IntArray,
    val isUserMap: Boolean = false
)

@Singleton
class EnergyMapRepository @Inject constructor(
    private val haptiqDao: HaptiqDao,
    private val analyzer: com.haptiq.app.audio.TrackEnergyAnalyzer
) {
    /**
     * Normalized 0–1 bass envelope for a song. Served from Room when the track has
     * been analyzed before; otherwise decoded + analyzed now and cached. Null when
     * the file can't be decoded — callers treat that as "no seek-preview".
     */
    /** A fresh analysis result wrapped as a persisted row, preserving any taught map. */
    private suspend fun insertAnalysis(song: Song, result: com.haptiq.app.audio.TrackEnergyResult) {
        val existing = haptiqDao.getEnergyMap(song.id)
        haptiqDao.insertEnergyMap(
            TrackEnergyMap(
                song.id, result.durationMs, result.frames,
                com.haptiq.app.audio.TrackEnergyAnalyzer.encodeOnsets(result.onsetsMs),
                com.haptiq.app.audio.TrackEnergyAnalyzer.encodeOnsets(result.beatsMs),
                // A re-analysis must never wipe a taught map — carry it over.
                userOnsets = existing?.userOnsets,
                System.currentTimeMillis()
            )
        )
    }

    suspend fun energyFor(song: Song): FloatArray? {
        val row = haptiqDao.getEnergyMap(song.id)
        val frames = row?.frames ?: run {
            val result = analyzer.analyze(song.audioUrl) ?: return null
            // analyze() computes frames, kick onsets AND the beat grid in one pass — store
            // all three so neither the AOT scheduler nor the seek preview needs a second decode.
            insertAnalysis(song, result)
            result.frames
        }
        if (frames.isEmpty()) return null
        return FloatArray(frames.size) { (frames[it].toInt() and 0xFF) / 255f }
    }

    /**
     * Kick onsets + beat grid for AOT lookahead, in one read. Null only when the file
     * can't be decoded at all. A row missing either field (written by an older build)
     * is re-analyzed once and re-stored with both.
     */
    suspend fun aotMapFor(song: Song): AotMapData? {
        val row = haptiqDao.getEnergyMap(song.id)
        // A taught map wins: the user hand-validated these kicks, so every one is
        // pre-fired — no beat-grid rejection (empty beats → isOnBeat accepts all).
        if (row?.userOnsets != null) {
            val user = com.haptiq.app.audio.TrackEnergyAnalyzer.decodeOnsets(row.userOnsets)
            return AotMapData(user, IntArray(0), isUserMap = true)
        }
        val storedOnsets = row?.onsets
        val storedBeats = row?.beats
        if (storedOnsets != null && storedBeats != null) {
            return AotMapData(
                com.haptiq.app.audio.TrackEnergyAnalyzer.decodeOnsets(storedOnsets),
                com.haptiq.app.audio.TrackEnergyAnalyzer.decodeOnsets(storedBeats)
            )
        }
        val result = analyzer.analyze(song.audioUrl) ?: return null
        insertAnalysis(song, result)
        return AotMapData(result.onsetsMs, result.beatsMs)
    }

    /**
     * The song's auto-detected onsets (analyzing once if no row exists yet) — the
     * ground-truth transient times used to clean the user's taps. Empty when the file
     * can't be decoded or the detector found nothing.
     */
    suspend fun autoOnsetsFor(song: Song): IntArray {
        val row = haptiqDao.getEnergyMap(song.id)
        if (row?.onsets != null) return com.haptiq.app.audio.TrackEnergyAnalyzer.decodeOnsets(row.onsets)
        if (row?.frames != null) return IntArray(0) // analyzed, genuinely no onsets
        val result = analyzer.analyze(song.audioUrl) ?: return IntArray(0)
        insertAnalysis(song, result)
        return result.onsetsMs
    }

    /**
     * Save a user-taught kick map for [song]. Ensures a row exists (analyzing once if
     * needed) so the UPDATE has a target, then stores the cleaned taps. The taught map
     * immediately overrides the auto map for lookahead and the seek-bar overlay.
     */
    suspend fun saveUserKicks(song: Song, tapsMs: IntArray): IntArray {
        val auto = autoOnsetsFor(song)
        val map = com.haptiq.app.audio.TrackEnergyAnalyzer.buildUserMap(tapsMs, auto)
        if (map.isEmpty()) return map
        if (haptiqDao.getEnergyMap(song.id) == null) {
            val result = analyzer.analyze(song.audioUrl)
            if (result != null) insertAnalysis(song, result)
        }
        haptiqDao.setUserOnsets(song.id, com.haptiq.app.audio.TrackEnergyAnalyzer.encodeOnsets(map))
        return map
    }

    /** Remove the taught map; the auto map (if any) takes back over. */
    suspend fun clearUserKicks(songId: String) {
        haptiqDao.setUserOnsets(songId, null)
    }

    /** True when [songId] currently has a taught map. */
    suspend fun hasUserMap(songId: String): Boolean =
        haptiqDao.getEnergyMap(songId)?.userOnsets != null
}

class CalibrationRepository @Inject constructor(private val haptiqDao: HaptiqDao) {
    fun getCalibration(deviceModel: String): Flow<CalibrationProfile> {
        return haptiqDao.getCalibration(deviceModel).map { saved ->
            saved ?: CalibrationProfile(deviceModel, multiplier = 1.0f, updatedAt = System.currentTimeMillis())
        }
    }

    suspend fun saveCalibration(deviceModel: String, multiplier: Float) {
        haptiqDao.insertCalibration(CalibrationProfile(deviceModel, multiplier, System.currentTimeMillis()))
    }
}
