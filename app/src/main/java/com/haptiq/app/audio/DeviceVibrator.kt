package com.haptiq.app.audio

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Single source for acquiring the device vibrator.
 *
 * On API 31+ the correct API is VibratorManager.defaultVibrator, but several
 * MTK/Tecno/Infinix OEM builds return a dud vibrator from the manager (reports
 * hasVibrator() == false, or silently no-ops) while the legacy VIBRATOR_SERVICE
 * still works. So: try the manager, validate with hasVibrator(), and fall back.
 *
 * Every component that vibrates (playback haptic engine, calibration pulse)
 * must go through this — a validated fallback in one place and not the other
 * is how "test pulse works but songs don't" bugs happen.
 */
object DeviceVibrator {
    fun get(context: Context): Vibrator? {
        val fromManager: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else null

        if (fromManager != null && fromManager.hasVibrator()) return fromManager

        @Suppress("DEPRECATION")
        val legacy = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        return legacy
    }
}
