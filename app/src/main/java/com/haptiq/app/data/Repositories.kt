package com.haptiq.app.data

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
    private val mediaStoreScanner: MediaStoreScanner
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
        val songs = mediaStoreScanner.scanForAudio(onProgress)
        _songs.value = songs
        hasLoaded = true
        songs
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
