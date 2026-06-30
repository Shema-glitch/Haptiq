package com.haptiq.app.data

import com.haptiq.app.audio.HapticTuningState
import kotlinx.coroutines.flow.StateFlow

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

    fun setSongs(songs: List<Song>, startIndex: Int)
    fun setHapticActive(active: Boolean)
    fun setPreset(presetId: String)
    fun updateIntensity(value: Int)
    fun setBatterySaver(enabled: Boolean)
    fun togglePlayPause()
    fun seekTo(progress: Float)
    fun playNext()
    fun playPrevious()
    fun toggleShuffle()
    fun toggleRepeat()
    fun stopPlayback()
    fun updateTuning(state: HapticTuningState)
}
