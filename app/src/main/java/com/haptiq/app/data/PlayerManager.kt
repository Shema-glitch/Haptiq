package com.haptiq.app.data

import com.haptiq.app.audio.HapticTuningState
import com.haptiq.app.audio.KickCharacter
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
    /** The active kick hit-character — set by presets and the Studio chip, in sync with tuning. */
    val kickCharacter: StateFlow<KickCharacter>
    val batterySaverEnabled: StateFlow<Boolean>
    val visualizerBands: StateFlow<FloatArray>
    /** Remaining sleep-timer allocation in minutes; 0 = off. */
    val sleepTimerMinutes: StateFlow<Int>
    /** The live play order — reflects shuffle and queue edits. */
    val queue: StateFlow<List<Song>>
    val isShuffleEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<RepeatMode>
    /** Playback speed multiplier (0.5–2.0; 1.0 = normal). */
    val playbackSpeed: StateFlow<Float>
    /** System media volume as a 0..1 fraction. */
    val volume: StateFlow<Float>

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
    /** Set playback speed (0.5–2.0). */
    fun setPlaybackSpeed(speed: Float)
    /** Set system media volume from a 0..1 fraction. */
    fun setVolume(fraction: Float)
    // ── Queue editing ──
    fun moveInQueue(from: Int, to: Int)
    fun removeFromQueue(index: Int)
    /** Move the song at [index] to right after the current one. */
    fun playSongNext(index: Int)
    /** Insert a song (e.g. from a library swipe) right after the current one. */
    fun enqueueNext(song: Song)
    /** Reload the last session's queue/track/position, paused. */
    fun restoreSession(library: List<Song>)
    fun stopPlayback()
    /** Mini-player swipe-down: stop, clear now-playing, and forget the saved session. */
    fun dismissPlayback()
    fun updateTuning(state: HapticTuningState)
    /** Re-read the current song's AOT map (e.g. after a taught map is saved/cleared). */
    fun refreshAotMap()
    fun getPlayer(): ExoPlayer?
}
