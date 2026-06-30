package com.haptiq.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HaptiqDao {
    @Query("SELECT * FROM recent_songs ORDER BY lastPlayedAt DESC")
    fun getAllRecentSongs(): Flow<List<RecentSongs>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSong(song: RecentSongs)

    @Query("DELETE FROM recent_songs")
    suspend fun clearRecentSongs()

    @Query("SELECT * FROM saved_presets WHERE presetId = :id")
    fun getPresetById(id: String): Flow<SavedPresets?>

    @Query("SELECT * FROM saved_presets")
    fun getAllPresets(): Flow<List<SavedPresets>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: SavedPresets)

    @Query("SELECT * FROM calibration_profile WHERE deviceModel = :deviceModel")
    fun getCalibration(deviceModel: String): Flow<CalibrationProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(calibration: CalibrationProfile)

    // Haptic Track Map (AOT Engine)
    @Query("SELECT * FROM haptic_track_map WHERE songId = :songId")
    suspend fun getHapticTrackMap(songId: String): HapticTrackMap?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHapticTrackMap(trackMap: HapticTrackMap)

    @Query("SELECT * FROM haptic_track_map WHERE isProcessed = 0")
    suspend fun getUnprocessedTracks(): List<HapticTrackMap>

    @Query("DELETE FROM haptic_track_map WHERE songId = :songId")
    suspend fun deleteHapticTrackMap(songId: String)
}
