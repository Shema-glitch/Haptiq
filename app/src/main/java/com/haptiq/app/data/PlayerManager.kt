package com.haptiq.app.data

import com.haptiq.app.audio.HapticTuningState
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ALL, ONE }

interface PlayerManager {
    val currentSong: StateFlow<Song?>
    val isPlaying: StateFlow<Boolean>
    val playbackProgress: StateFlow<Float>
    val currentTimeText: StateFlow<String>
    val remainingTimeText: StateFlow<String>
    val hapticActive: StateFlow<Boolean>
    val currentPresetId: StateFlow<String>
    val intensity: StateFlow<Int>
    val batterySaverEnabled: StateFlow<Boolean>
    val visualizerBands: StateFlow<FloatArray>
    /** Remaining sleep-timer allocation in minutes; 0 = off. */
    val sleepTimerMinutes: StateFlow<Int>
    /** The live play order — reflects shuffle and queue edits. */
    val queue: StateFlow<List<Song>>
    val isShuffleEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<RepeatMode>

    fun setSongs(songs: List<Song>, startIndex: Int)
    fun setHapticActive(active: Boolean)
    fun setPreset(presetId: String)
    fun updateIntensity(value: Int)
    fun setBatterySaver(enabled: Boolean)
    fun setSleepTimer(minutes: Int)
    fun togglePlayPause()
    fun seekTo(progress: Float)
    fun playNext()
    fun playPrevious()
    /** Jump to a song within the existing queue (queue sheet tap). */
    fun playAt(index: Int)
    fun toggleShuffle()
    fun toggleRepeat()
    // ── Queue editing ──
    fun moveInQueue(from: Int, to: Int)
    fun removeFromQueue(index: Int)
    /** Move the song at [index] to right after the current one. */
    fun playSongNext(index: Int)
    /** Reload the last session's queue/track/position, paused. */
    fun restoreSession(library: List<Song>)
    fun stopPlayback()
    fun updateTuning(state: HapticTuningState)
    fun getPlayer(): ExoPlayer?
}
