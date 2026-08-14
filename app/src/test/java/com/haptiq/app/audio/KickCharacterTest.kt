package com.haptiq.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KickCharacterTest {

    // ── KickCharacter.shift ────────────────────────────────────────────────

    @Test
    fun `shift moves along the character spectrum`() {
        assertEquals(KickCharacter.PUNCH, KickCharacter.SNAP.shift(1))
        assertEquals(KickCharacter.HEAVY, KickCharacter.THUMP.shift(1))
        assertEquals(KickCharacter.PUNCH, KickCharacter.HEAVY.shift(-2))
    }

    @Test
    fun `shift clamps at both ends`() {
        assertEquals(KickCharacter.HEAVY, KickCharacter.HEAVY.shift(5))
        assertEquals(KickCharacter.SNAP, KickCharacter.SNAP.shift(-5))
        assertEquals(KickCharacter.HEAVY, KickCharacter.SNAP.shift(3))
        assertEquals(KickCharacter.SNAP, KickCharacter.HEAVY.shift(-3))
    }

    @Test
    fun `shift zero is identity`() {
        KickCharacter.entries.forEach { c ->
            assertEquals(c, c.shift(0))
        }
    }

    // ── KickWaveform.strikeTrain (ERM / no amplitude control) ──────────────

    @Test
    fun `snap is a single strike with no tail`() {
        val (times, amps) = KickWaveform.strikeTrain(KickCharacter.SNAP, 255)
        // [0, 35] — one strike only (SNAP has no tail)
        assertEquals(2, times.size)
        assertEquals(2, amps.size)
        assertEquals(0L, times[0])
        assertEquals(35L, times[1])
        assertEquals(255, amps[1])
    }

    @Test
    fun `punch is two strikes with a gap between`() {
        val (times, amps) = KickWaveform.strikeTrain(KickCharacter.PUNCH, 220)
        // [0, 35, 8, 35, 8, tail]
        assertTrue(times.size >= 4)
        assertEquals(0, amps[0])
        assertEquals(220, amps[1])
        assertEquals(0, amps[2])
        // Second strike decays
        assertTrue(amps[3] < amps[1])
    }

    @Test
    fun `heavy has more strikes than thump and both end with a tail`() {
        val thump = KickWaveform.strikeTrain(KickCharacter.THUMP, 255)
        val heavy = KickWaveform.strikeTrain(KickCharacter.HEAVY, 255)
        // More drive segments = more strikes (heavy = 4 strikes + tail, thump = 3 + tail)
        assertTrue(heavy.first.size > thump.first.size)
        // Both end on a non-zero amplitude segment (the tail) — never a flat-off end
        assertTrue(thump.second.last() > 0)
        assertTrue(heavy.second.last() > 0)
    }

    @Test
    fun `strike amplitudes decay monotonically and stay in range`() {
        val (_, amps) = KickWaveform.strikeTrain(KickCharacter.HEAVY, 255)
        var last = 256
        for (a in amps) {
            if (a == 0) continue // gaps are silent by design
            assertTrue("amplitude $a must decay: last=$last", a <= last)
            assertTrue(a in 0..255)
            last = a
        }
    }

    // ── KickWaveform.pulseWithTail (amplitude-control path) ────────────────

    @Test
    fun `pulse with tail extends the decay for longer characters`() {
        val (times, amps) = KickWaveform.pulseWithTail(KickCharacter.THUMP, 255)
        // [0, 35, 8, tail]
        assertEquals(4, times.size)
        assertEquals(60L, times[3]) // THUMP tail
        assertTrue(amps[3] in 1..254) // decayed, non-zero, not full
    }

    @Test
    fun `snap pulse has no sustained tail`() {
        val (_, amps) = KickWaveform.pulseWithTail(KickCharacter.SNAP, 255)
        assertEquals(0, amps[3]) // SNAP: 12ms off, zero amplitude
    }

    // ── Surface feedback bands (KickLatencyTracker) ────────────────────────

    @Test
    fun `damped surfaces shift up`() {
        val tracker = KickLatencyTracker()
        assertEquals(2, tracker.characterShiftForMagnitude(0.8f))
        assertEquals(2, tracker.characterShiftForMagnitude(1.4f))
        assertEquals(1, tracker.characterShiftForMagnitude(1.5f))
        assertEquals(1, tracker.characterShiftForMagnitude(2.9f))
    }

    @Test
    fun `normal and free motion stay or shift down`() {
        val tracker = KickLatencyTracker()
        assertEquals(0, tracker.characterShiftForMagnitude(3.0f))
        assertEquals(0, tracker.characterShiftForMagnitude(4.5f))
        assertEquals(0, tracker.characterShiftForMagnitude(6.0f))
        assertEquals(-1, tracker.characterShiftForMagnitude(6.5f))
    }
}
