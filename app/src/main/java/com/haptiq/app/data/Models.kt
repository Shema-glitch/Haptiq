package com.haptiq.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val artworkUrl: String,
    val audioUrl: String,
    val hapticPresetId: String = "deep_bass"
)

@Entity(tableName = "recent_songs")
data class RecentSongs(
    @PrimaryKey val songId: String,
    val lastPlayedAt: Long
)

@Entity(tableName = "saved_presets")
data class SavedPresets(
    @PrimaryKey val presetId: String,
    val intensity: Int,
    val sensitivity: Int
)

@Entity(tableName = "calibration_profile")
data class CalibrationProfile(
    @PrimaryKey val deviceModel: String,
    val multiplier: Float,
    val updatedAt: Long
)

/**
 * Pre-computed haptic map for a track.
 * Stores bass intensity values at fixed intervals (every 50ms).
 * During playback, the engine reads the array by timestamp index
 * instead of running real-time FFT — zero math on playback thread.
 */
@Entity(tableName = "haptic_track_map")
data class HapticTrackMap(
    @PrimaryKey val songId: String,
    val durationMs: Long,
    val isProcessed: Boolean = false,
    val hapticFrames: ByteArray? = null // Compressed intensity 0-100 per 50ms interval
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HapticTrackMap) return false
        return songId == other.songId
    }

    override fun hashCode(): Int = songId.hashCode()
}
