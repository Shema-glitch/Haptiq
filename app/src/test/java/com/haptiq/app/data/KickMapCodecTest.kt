package com.haptiq.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickMapCodecTest {

    private val song = Song(
        id = "42",
        title = "Legacy (ft. \"Pixy\")",
        artist = "Pixy \\ Band",
        durationSeconds = 214,
        artworkUrl = "",
        audioUrl = "file:///legacy.mp3"
    )

    @Test
    fun encodeDecode_roundTrips() {
        val onsets = intArrayOf(400, 900, 1400, 1900, 2400, 2900, 3400)
        val json = KickMapCodec.encode(song, onsets)
        val file = KickMapCodec.decode(json)
        assertTrue("expected a parse, got null", file != null)
        file!!
        assertEquals(1, file.schema)
        assertEquals(song.title, file.title)
        assertEquals(song.artist, file.artist)
        assertEquals(song.durationSeconds, file.durationSeconds)
        assertEquals(onsets.size, file.onsets.size)
        for (i in onsets.indices) assertEquals(onsets[i], file.onsets[i])
    }

    @Test
    fun decode_skipsUnknownFields() {
        // A future schema that ADDS fields still imports today.
        val json = """{"schema":1,"futureField":true,"song":{"title":"T","artist":"A","durationSeconds":10,"extra":42},"onsets":[100,500,900]}"""
        val file = KickMapCodec.decode(json)
        assertTrue(file != null)
        assertEquals(3, file!!.onsets.size)
        assertEquals("T", file.title)
    }

    @Test
    fun decode_rejectsGarbage() {
        assertNull(KickMapCodec.decode("not json at all"))
        assertNull(KickMapCodec.decode(""))
        assertNull(KickMapCodec.decode("""{"schema":1,"song":{},"onsets":[]}"""))
        assertNull(KickMapCodec.decode("""{"schema":2,"song":{},"onsets":[100]}"""))
        assertNull(KickMapCodec.decode("""{"schema":1,"song":{"title":"T""")) // truly truncated
        assertNull(KickMapCodec.decode("""{"schema":1,"song":{"title":"T","artist":"A","durationSeconds":1},"onsets":[abc]}"""))
        assertNull(KickMapCodec.decode("""{"schema":1,"song":{"title":"T","artist":"A","durationSeconds":1},"onsets":[100,}""")) // truncated array
    }

    @Test
    fun decode_minimalSongObject_stillParses() {
        // Missing artist/duration is tolerated — import only needs the onsets.
        val json = """{"schema":1,"song":{"title":"T"},"onsets":[100,500]}"""
        val file = KickMapCodec.decode(json)
        assertTrue(file != null)
        assertEquals("T", file!!.title)
        assertEquals(2, file.onsets.size)
    }

    @Test
    fun decode_handlesEscapesInStrings() {
        val json = """{"schema":1,"song":{"title":"Line1\nLine2 \"quoted\" \\","artist":"A","durationSeconds":1},"onsets":[100]}"""
        val file = KickMapCodec.decode(json)
        assertTrue(file != null)
        assertEquals("Line1\nLine2 \"quoted\" \\", file!!.title)
    }
}
