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

    // ── Favorites ────────────────────────────────────────────
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun getAllFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteSong)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun deleteFavorite(songId: String)

    // ── Track energy maps (haptic seek-preview) ─────────────
    @Query("SELECT * FROM track_energy_maps WHERE songId = :songId")
    suspend fun getEnergyMap(songId: String): TrackEnergyMap?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnergyMap(map: TrackEnergyMap)

    /** Write (or clear, with null) the user-taught kick map for a song. */
    @Query("UPDATE track_energy_maps SET userOnsets = :onsets WHERE songId = :songId")
    suspend fun setUserOnsets(songId: String, onsets: ByteArray?)

    // ── Playlists ────────────────────────────────────────────
    @Query(
        """SELECT p.id, p.name, p.createdAt, COUNT(ps.songId) AS songCount
           FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
           GROUP BY p.id ORDER BY p.createdAt DESC"""
    )
    fun getAllPlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistSongs(playlistId: Long): Flow<List<PlaylistSong>>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(entry: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removePlaylistSong(playlistId: Long, songId: String)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun playlistSize(playlistId: Long): Int
}
