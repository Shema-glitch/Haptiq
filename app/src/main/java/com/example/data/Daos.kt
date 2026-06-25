package com.example.data

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
}
