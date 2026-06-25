package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SongRepository {
    private val songs = listOf(
        Song(
            id = "1",
            title = "Resonance Cascade",
            artist = "Neural Shift",
            durationSeconds = 302,
            artworkUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDHq_tT-9SIwg_xOTYAO5by3FJ8YRpFH2VYhp5B0v_YTsXwbrfBoKLb4p2I9s-s0QR4pXHuCZULL683uWvShXi6GgAtCFCCL5f90X7uGiH-nGQYPN7g4IjA4Sj98SY-b14qOJxjEjDVf8-ITg2qhK75T-MaYaLaGbko9o95JH9wqwE2yQHxViazabJVtpsQ4xUpbjyuHFdJigjqSR_BIIrvlXY7m7Y3Y4QLA-g0-AG5_nuwdDbtliTu5Z8MeCByqJI_BvfE4aXMNKRk",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            hapticPresetId = "deep_bass"
        ),
        Song(
            id = "2",
            title = "Subterranean",
            artist = "Echolocation",
            durationSeconds = 422,
            artworkUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBa7AhRy9DzqC8q2tzvaCouepKNtDriqjHMvEdT-cwRZq1IxCyUcGZEVWLqzuFtwyrg1-KFyUv6cOPtcdaSerjkM_fxBypkiJpRVJKLEcJnNKCEv--2iaWDq108yTPJi4Zb2fI1FrMl2gPm7NyZYMI_h3-s0ytCTlzeLPy8mnNSZWv-B3l4jHI3TBYdJXA36q-3oY_yVMNpraSQS0ftitYyWeU6QOnnEg6Cl6sgaXJaMScite-Lj8zA0pusVtbDcZacaI-gFGoFLT4B",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            hapticPresetId = "punch"
        ),
        Song(
            id = "3",
            title = "Tactile Memory",
            artist = "The Architects",
            durationSeconds = 345,
            artworkUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAujAtMpfTLuVEuSQPbS4SXfIQGw3pKmyqMUpeaOpKOxyABF5Saw4pUUdKraUPzXIO2pPYS7qHGRMFA5oiq5gJ4xrpxjWPWiRqxIbvOPdpQyVjQ9w7TKdi6E72jg2GEWGTtzTyj9bn_piJLtXjOsWSLvVOx3R6U5IvoCPbGgSM_pX1kPEffe1wKrS-mRy5XtWlnCpHyjld-Uq0lot1eZu7rCcomHzWxy3DJUU8XE_UEJ9Z44ClE6rT6G_muaKg9luBL5vwI7WFqFm1S",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            hapticPresetId = "concert"
        ),
        Song(
            id = "4",
            title = "Frequency Shift",
            artist = "Aura Mechanics",
            durationSeconds = 288,
            artworkUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDYiclGmptxTn1QwBo6c7g00jBhkmzF9F5jDvGyzmuKJzWSUMzTTvwenLqeWLOqD8xZAWvYWa90feY5CJk77TIPuTVJXh5BBiW-FFyT52VSmMpeOkwzQuIcrZI2RJMlDKJ-pVakuuvINs0XCI8rqfnvHtXdkbbqP8PE4Cbr24p592KAJonMa3SkTylb_2RFlpVzQUWSJyGtwCQsIfeQbh7hiNZsD52DaTQT5ZKCyhK4m1Mn2CTb-C2WtN1Ae1QLmB5N9eTmmsC1nWt_",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            hapticPresetId = "soft_pulse"
        ),
        Song(
            id = "5",
            title = "Concrete Echo",
            artist = "The Architects",
            durationSeconds = 310,
            artworkUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuD-3MwT7pEGJxKv8KaCHqX3kFW_CSY2hEGPZCtd2yIVEE4bE_KkE0IooSOFsEdjmevIHntrRbbW6KJ6sKQrbJ0oMShOeCzZGiXRnNweQS9yliz9GMGalB9Umb1tkmF1xb96b_UfeXTsHuYTg0Ha7oF2fqJ0zmmcQ6fKFUBPLzB8fS_nkimU5Q84Dnb_G-_Rtw4sVlNcqP-80JQH3_E20G5_KzIUUNliTH3eCOe03fDI2nBLYzGzorpxmCdliCsRkca-Yb44vgZD-cXK",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            hapticPresetId = "deep_bass"
        ),
        Song(
            id = "6",
            title = "Viscous Flow",
            artist = "Liquid State",
            durationSeconds = 412,
            artworkUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAf53JUtHpwEzKvv6pxC9f6lVECmA9zF3uDlhThIBzdGXPrJYTQLULO41RBPitEgRDiSjbHXcl_ZRa0ATanQpS5Mqwji_R-dIw8G0n7aYpE7lOlVasG2jQPGYwSnRv8d80JqG2LtO0K_P-KtgZVezAeGoOfwgo4stZneX58hyFsTTWlQAtsh3ymdi7qfRoJr1g0mRDir1zUB9PsiwUJVf4wVcghBFY-il09HyBoNeeexkp260WvjyIEyxaVS8V3lRmaLW71xolHt0eM",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            hapticPresetId = "punch"
        ),
        Song(
            id = "7",
            title = "Neon Pulse Iteration",
            artist = "The Synthetics",
            durationSeconds = 240,
            artworkUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAhz4RkW3-MIhZYGswOmrHa0wrqknKS9fgtHOO5z7SoDhSjIavBU4Wq8YGxbusf4WCyODnq-TeWugf-FE1JSSG0qLXSkPMZQ1M2tJHtnidD_TBVopksRWPNkghldPCe0e76jfEM6IsdNw69VHSOqrektg3EU8l9hrZQjHuWX5qcm1iODjAxx5p7LwGCxs2FNJfoL8F_qHPB_QcKpzxq-MeEx-MnbaSXlP23KVqxEyaykmeSaEwCvrHJs4Vi2JsaieqKNLFcO-7JpkN7",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            hapticPresetId = "deep_bass"
        )
    )

    fun getAllSongs(): Flow<List<Song>> = flowOf(songs)

    fun getSongById(id: String): Song? = songs.find { it.id == id }
}

class RecentSongRepository(private val haptiqDao: HaptiqDao, private val songRepository: SongRepository) {
    val allRecentSongs: Flow<List<Song>> = haptiqDao.getAllRecentSongs().map { list ->
        list.mapNotNull { recent -> songRepository.getSongById(recent.songId) }
    }

    suspend fun addRecentSong(songId: String) {
        haptiqDao.insertRecentSong(RecentSongs(songId, System.currentTimeMillis()))
    }

    suspend fun clearAll() {
        haptiqDao.clearRecentSongs()
    }
}

class PresetRepository(private val haptiqDao: HaptiqDao) {
    private val defaultPresets = mapOf(
        "deep_bass" to SavedPresets("deep_bass", intensity = 75, sensitivity = 80),
        "punch" to SavedPresets("punch", intensity = 90, sensitivity = 95),
        "concert" to SavedPresets("concert", intensity = 80, sensitivity = 70),
        "soft_pulse" to SavedPresets("soft_pulse", intensity = 50, sensitivity = 60)
    )

    fun getPresetById(presetId: String): Flow<SavedPresets> {
        return haptiqDao.getPresetById(presetId).map { saved ->
            saved ?: defaultPresets[presetId] ?: SavedPresets(presetId, 75, 75)
        }
    }

    fun getAllPresets(): Flow<List<SavedPresets>> {
        return haptiqDao.getAllPresets().map { savedList ->
            val listMap = savedList.associateBy { it.presetId }
            defaultPresets.keys.map { key -> listMap[key] ?: defaultPresets[key]!! }
        }
    }

    suspend fun savePreset(presetId: String, intensity: Int, sensitivity: Int) {
        haptiqDao.insertPreset(SavedPresets(presetId, intensity, sensitivity))
    }
}

class CalibrationRepository(private val haptiqDao: HaptiqDao) {
    fun getCalibration(deviceModel: String): Flow<CalibrationProfile> {
        return haptiqDao.getCalibration(deviceModel).map { saved ->
            saved ?: CalibrationProfile(deviceModel, multiplier = 1.0f, updatedAt = System.currentTimeMillis())
        }
    }

    suspend fun saveCalibration(deviceModel: String, multiplier: Float) {
        haptiqDao.insertCalibration(CalibrationProfile(deviceModel, multiplier, System.currentTimeMillis()))
    }
}
