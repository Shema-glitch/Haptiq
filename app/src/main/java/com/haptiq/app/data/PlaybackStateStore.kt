package com.haptiq.app.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of where playback stood, for resume-on-next-launch. */
data class PlaybackSnapshot(
    val queueIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: RepeatMode
)

/**
 * SharedPreferences-backed persistence of the playback session. Plain prefs, not
 * Room: this is a single mutable snapshot written often (every song change/pause),
 * not queryable data.
 */
@Singleton
class PlaybackStateStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("haptiq_playback", Context.MODE_PRIVATE)

    fun save(snapshot: PlaybackSnapshot) {
        prefs.edit {
            putString("queueIds", snapshot.queueIds.joinToString(","))
            putInt("currentIndex", snapshot.currentIndex)
            putLong("positionMs", snapshot.positionMs)
            putBoolean("shuffle", snapshot.shuffle)
            putString("repeatMode", snapshot.repeatMode.name)
        }
    }

    fun savePosition(positionMs: Long) {
        prefs.edit { putLong("positionMs", positionMs) }
    }

    fun load(): PlaybackSnapshot? {
        val ids = prefs.getString("queueIds", null)?.split(',')?.filter { it.isNotBlank() }
        if (ids.isNullOrEmpty()) return null
        return PlaybackSnapshot(
            queueIds = ids,
            currentIndex = prefs.getInt("currentIndex", 0),
            positionMs = prefs.getLong("positionMs", 0L),
            shuffle = prefs.getBoolean("shuffle", false),
            repeatMode = runCatching {
                RepeatMode.valueOf(prefs.getString("repeatMode", null) ?: "OFF")
            }.getOrDefault(RepeatMode.OFF)
        )
    }
}
