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
        private const val ARTWORK_MAX_DIM = 512
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
            MediaStore.Audio.Media.ALBUM,
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
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
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
                    val album = cursor.getString(albumColumn)
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

                    // Album art is keyed by albumId, which MediaStore SHARES across every
                    // untagged file (they all fall into one "unknown album" bucket whose
                    // album string is often a real-looking name, not "<unknown>"). Trusting
                    // that URI paints ONE song's embedded cover onto all its neighbours —
                    // the "every track shows the same Tate McRae art" bug. The only reliable
                    // per-song source is the file's OWN embedded picture, so prefer that and
                    // fall back to the shared album URI only when a track has no embedded art
                    // (a genuine album where art lives on the album, not each file).
                    val albumTrusted = albumId > 0 &&
                        !album.isNullOrBlank() &&
                        album != MediaStore.UNKNOWN_STRING
                    val embeddedArt = extractEmbeddedArtwork(id.toString()) {
                        it.setDataSource(context, contentUri)
                    }
                    val artworkUrl = embeddedArt.ifEmpty {
                        if (albumTrusted) {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"), albumId
                            ).toString()
                        } else ""
                    }

                    songs.add(
                        Song(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            durationSeconds = durationSeconds,
                            artworkUrl = artworkUrl,
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
                    val id = "file_${file.absolutePath.hashCode()}"

                    // Avoid duplicates
                    if (songs.none { it.id == id }) {
                        // Real tags where they exist; the artist fallback is deliberately
                        // NOT the parent folder name — that surfaced locations like
                        // "Download" or "Telegram" as artist names in the UI.
                        val meta = extractMetadata(id, file)
                        songs.add(
                            Song(
                                id = id,
                                title = meta.title ?: file.nameWithoutExtension,
                                artist = meta.artist ?: "Unknown Artist",
                                durationSeconds = meta.durationSeconds,
                                artworkUrl = meta.artworkUrl,
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

    private data class FileMetadata(
        val title: String?,
        val artist: String?,
        val durationSeconds: Int,
        val artworkUrl: String
    )

    /**
     * Pull real tags AND embedded cover art out of a file found by the directory
     * fallback. Without this, every fallback-scanned track shows 0:00 and no art.
     * Only runs on the fallback path — MediaStore rows already carry duration — so
     * the per-file cost of MediaMetadataRetriever is acceptable, and reusing the one
     * open retriever for art too avoids opening each file twice.
     */
    private fun extractMetadata(songId: String, file: File): FileMetadata {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever
                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            FileMetadata(
                title = retriever
                    .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() },
                artist = retriever
                    .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() },
                durationSeconds = (durationMs / 1000).toInt(),
                artworkUrl = cacheEmbeddedPicture(songId, retriever.embeddedPicture)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Metadata extraction failed for ${file.name}: ${e.message}")
            FileMetadata(null, null, 0, "")
        } finally {
            runCatching { retriever.release() }
        }
    }

    // Where per-song extracted cover art is cached. cacheDir is OS-managed, so it can
    // be reclaimed under pressure; a missing file just re-extracts on the next scan.
    private val artworkCacheDir: File by lazy {
        File(context.cacheDir, "artwork").apply { mkdirs() }
    }

    private fun artworkFileFor(songId: String): File =
        File(artworkCacheDir, "${songId.hashCode()}.jpg")

    /**
     * Wipe every extracted-artwork file. Used by Settings → Clear Cache. Safe to call
     * any time — a missing file just re-extracts (and re-downscales) on the next scan,
     * same as if the OS reclaimed cacheDir under storage pressure.
     */
    fun clearArtworkCache() {
        artworkCacheDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /**
     * Extract a single song's OWN embedded cover, cache it to disk, and return a
     * file:// URI Coil can load. Cached by songId so rescans are free. Returns ""
     * when the track has no embedded art (caller then falls back to a letter tile).
     * Opens its own retriever — used by the MediaStore path, where rows otherwise
     * never touch MediaMetadataRetriever.
     */
    private fun extractEmbeddedArtwork(
        songId: String,
        setSource: (android.media.MediaMetadataRetriever) -> Unit
    ): String {
        artworkFileFor(songId).let { if (it.exists() && it.length() > 0) return Uri.fromFile(it).toString() }
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            setSource(retriever)
            cacheEmbeddedPicture(songId, retriever.embeddedPicture)
        } catch (e: Exception) {
            Log.w(TAG, "Embedded art extraction failed for $songId: ${e.message}")
            ""
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Write already-extracted picture bytes to the cache and return their file URI.
     * Downscales to [ARTWORK_MAX_DIM] first — embedded covers come straight off the
     * original file and can be several MB at full album-art resolution (3000×3000+).
     * Caching that raw meant every scan wrote megabytes per song to disk, and every
     * scroll through the library made Coil decode a full-size bitmap into memory just
     * to draw it at ~56dp — with a large library that's the actual cause of the app
     * "going slow": not the scan itself, but sustained memory pressure/GC from
     * oversized art on every list recompose. A 512px JPEG is visually identical at
     * any on-screen tile size and is roughly 1/10th the bytes.
     */
    private fun cacheEmbeddedPicture(songId: String, picture: ByteArray?): String {
        if (picture == null || picture.isEmpty()) return ""
        val out = artworkFileFor(songId)
        if (out.exists() && out.length() > 0) return Uri.fromFile(out).toString()
        return try {
            out.writeBytes(downscaleArtwork(picture))
            Uri.fromFile(out).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Caching embedded art failed for $songId: ${e.message}")
            ""
        }
    }

    /** Decode with an inSampleSize that lands near [ARTWORK_MAX_DIM] on the long
     *  edge, then re-encode as JPEG. Falls back to the original bytes if decoding
     *  fails for any reason — a slightly-too-large cache file beats no artwork. */
    private fun downscaleArtwork(picture: ByteArray): ByteArray {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            if (longEdge <= 0) return picture
            var sampleSize = 1
            while (longEdge / (sampleSize * 2) >= ARTWORK_MAX_DIM) sampleSize *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size, opts)
                ?: return picture
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Artwork downscale failed, caching original: ${e.message}")
            picture
        }
    }
}
