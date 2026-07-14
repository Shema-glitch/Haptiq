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

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSong(
    val playlistId: Long,
    val songId: String,
    val position: Int,
    val addedAt: Long
)

/** Query projection: a playlist plus how many songs it holds. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int
)

/**
 * Precomputed bass-energy envelope for one track (the revived AOT haptic-map concept):
 * one unsigned byte (0–255) per TrackEnergyAnalyzer.FRAME_MS of audio. Consumed by the
 * seek-preview scrubber, which needs energy at arbitrary positions ahead of playback.
 */
@Entity(tableName = "track_energy_maps")
data class TrackEnergyMap(
    @PrimaryKey val songId: String,
    val durationMs: Long,
    val frames: ByteArray,
    val analyzedAt: Long
) {
    override fun equals(other: Any?): Boolean =
        other is TrackEnergyMap && other.songId == songId && other.analyzedAt == analyzedAt

    override fun hashCode(): Int = songId.hashCode() * 31 + analyzedAt.hashCode()
}

@Entity(tableName = "calibration_profile")
data class CalibrationProfile(
    @PrimaryKey val deviceModel: String,
    val multiplier: Float,
    val updatedAt: Long
)
