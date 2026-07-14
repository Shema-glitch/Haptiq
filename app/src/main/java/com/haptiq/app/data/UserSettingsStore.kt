package com.haptiq.app.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User preferences that aren't playback state: library filters and feature toggles.
 */
@Singleton
class UserSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("haptiq_settings", Context.MODE_PRIVATE)

    /** Tracks shorter than this are skipped at scan time — voice notes aren't music. */
    var minDurationSec: Int
        get() = prefs.getInt("minDurationSec", 30)
        set(value) = prefs.edit { putInt("minDurationSec", value) }

    /** Haptic seek-preview pulses while scrubbing the Player timeline. */
    var seekPreviewEnabled: Boolean
        get() = prefs.getBoolean("seekPreviewEnabled", true)
        set(value) = prefs.edit { putBoolean("seekPreviewEnabled", value) }
}
