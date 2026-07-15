package com.haptiq.app.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans the device for local audio files.
 *
 * Strategy:
 * 1. Primary: MediaStore query (fast, indexed by Android)
 * 2. Fallback: Direct directory scan (finds files MediaStore may have missed)
 *
 * Reports progress via [onProgress] callback.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MediaStoreScanner"
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "wma", "amr", "aiff"
        )
    }

    /**
     * Scan for all music files on the device.
     *
     * @param onProgress Callback with (scannedSoFar, statusMessage)
     * @return List of Song objects with real metadata
     */
    fun scanForAudio(minDurationSec: Int = 0, onProgress: ((Int, String) -> Unit)? = null): List<Song> {
        onProgress?.invoke(0, "Scanning MediaStore…")

        // Primary: MediaStore query
        var songs = queryMediaStore(requireIsMusic = true, minDurationSec = minDurationSec)

        if (songs.isEmpty()) {
            Log.d(TAG, "IS_MUSIC=1 returned 0 results. Trying without IS_MUSIC filter…")
            onProgress?.invoke(0, "Trying broader scan…")
            songs = queryMediaStore(requireIsMusic = false, minDurationSec = minDurationSec)
        }

        if (songs.isEmpty()) {
            Log.d(TAG, "MediaStore returned 0 results. Scanning directories…")
            onProgress?.invoke(0, "Scanning directories…")
            songs = scanDirectories(onProgress)
        }

        onProgress?.invoke(songs.size, "Found ${songs.size} tracks")
        Log.d(TAG, "Total songs found: ${songs.size}")
        return songs
    }

    /**
     * Query MediaStore for audio files.
     */
    private fun queryMediaStore(requireIsMusic: Boolean, minDurationSec: Int = 0): List<Song> {
        val songs = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = if (requireIsMusic) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else null
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                collection, projection, selection, null, sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown"
                    // Untagged files come back as the literal string "<unknown>"
                    val artist = cursor.getString(artistColumn)
                        ?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                        ?: "Unknown Artist"
                    val durationMs = cursor.getLong(durationColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""
                    val durationSeconds = (durationMs / 1000).toInt()

                    // Skip clips below the user's minimum (voice notes) — always at least 5s
                    if (durationSeconds < maxOf(5, minDurationSec)) continue
                    if (filePath.contains("/Ringtones/", ignoreCase = true)) continue
                    if (filePath.contains("/Notifications/", ignoreCase = true)) continue
                    if (filePath.contains("/Alarms/", ignoreCase = true)) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )

                    songs.add(
                        Song(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            durationSeconds = durationSeconds,
                            artworkUrl = albumArtUri.toString(),
                            audioUrl = contentUri.toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed: ${e.message}")
        }

        Log.d(TAG, "MediaStore query (requireIsMusic=$requireIsMusic) found ${songs.size} songs")
        return songs
    }

    /**
     * Direct directory scan — fallback when MediaStore returns nothing.
     * Walks common music directories and finds audio files by extension.
     */
    private fun scanDirectories(onProgress: ((Int, String) -> Unit)? = null): List<Song> {
        val songs = mutableListOf<Song>()
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val rootDir = Environment.getExternalStorageDirectory()

        val dirsToScan = listOfNotNull(
            musicDir,
            downloadDir,
            File(rootDir, "Music"),
            File(rootDir, "audio"),
            rootDir
        )

        var scanned = 0
        for (dir in dirsToScan) {
            if (dir.exists() && dir.canRead()) {
                onProgress?.invoke(scanned, "Scanning ${dir.name}…")
                scanDirectory(dir, songs, depth = 0, maxDepth = 4) { count ->
                    scanned = count
                    onProgress?.invoke(count, "Found $count tracks…")
                }
            }
        }

        return songs
    }

    /**
     * Recursively scan a directory for audio files.
     */
    private fun scanDirectory(
        dir: File,
        songs: MutableList<Song>,
        depth: Int,
        maxDepth: Int,
        onCount: (Int) -> Unit
    ) {
        if (depth > maxDepth) return
        if (!dir.exists() || !dir.canRead()) return

        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    // Skip hidden directories and system directories
                    if (file.name.startsWith(".")) continue
                    if (file.name == "Android") continue
                    scanDirectory(file, songs, depth + 1, maxDepth, onCount)
                } else if (file.isFile && isAudioFile(file.name) && file.length() > 10_000) {
                    // Extract title from filename. The artist is deliberately NOT the
                    // parent folder name — that surfaced locations like "Download" or
                    // "Telegram" as artist names under song titles in the UI.
                    val title = file.nameWithoutExtension
                    val artist = "Unknown Artist"
                    val id = "file_${file.absolutePath.hashCode()}"

                    // Avoid duplicates
                    if (songs.none { it.id == id }) {
                        songs.add(
                            Song(
                                id = id,
                                title = title,
                                artist = artist,
                                durationSeconds = 0, // Unknown without metadata extraction
                                artworkUrl = "",
                                audioUrl = file.toURI().toString()
                            )
                        )
                        onCount(songs.size)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning ${dir.absolutePath}: ${e.message}")
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }
}
