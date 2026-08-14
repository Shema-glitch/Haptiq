package com.haptiq.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function tests for the AOT kick-onset + beat-grid helpers (no Android deps). */
class TrackEnergyAnalyzerTest {

    // ── Onset/beat encoding round-trips ──────────────────────────────────

    @Test
    fun encodeDecode_roundTrips() {
        val onsets = intArrayOf(123, 4567, 89_000, 240_000)
        val decoded = TrackEnergyAnalyzer.decodeOnsets(TrackEnergyAnalyzer.encodeOnsets(onsets))
        assertArrayEquals(onsets, decoded)
    }

    @Test
    fun encode_empty_staysEmpty() {
        val bytes = TrackEnergyAnalyzer.encodeOnsets(IntArray(0))
        assertEquals(0, bytes.size)
        assertEquals(0, TrackEnergyAnalyzer.decodeOnsets(bytes).size)
    }

    @Test
    fun decode_nullLike_handled() {
        // Empty blob (a "no onsets" row) decodes to an empty list, never throws.
        assertEquals(0, TrackEnergyAnalyzer.decodeOnsets(ByteArray(0)).size)
    }

    @Test
    fun encode_unsortedIsPreserved() {
        // The detector emits onsets in order, but the round-trip must be exact regardless.
        val onsets = intArrayOf(500, 100, 300)
        assertArrayEquals(
            onsets,
            TrackEnergyAnalyzer.decodeOnsets(TrackEnergyAnalyzer.encodeOnsets(onsets))
        )
    }

    // ── Beat-grid detection ──────────────────────────────────────────────

    /** 40ms window envelope with a strong transient every [periodWindows] windows. */
    private fun periodicEnvelope(nWindows: Int, periodWindows: Int): List<Float> {
        return List(nWindows) { i -> if (i % periodWindows == 0) 1.0f else 0.02f }
    }

    @Test
    fun detectBeats_findsPeriodicGrid() {
        val windowMs = 40L
        val periodWindows = 12 // 480ms → 125 BPM
        val n = 300
        val beats = TrackEnergyAnalyzer.detectBeats(
            periodicEnvelope(n, periodWindows), n * windowMs, windowMs
        )
        assertTrue("expected a beat grid, got none", beats.isNotEmpty())
        // Beats are regularly spaced at the detected period.
        val expectedPeriod = 480
        for (i in 1 until beats.size) {
            assertEquals("irregular beat spacing at index $i", expectedPeriod, beats[i] - beats[i - 1])
        }
        // Grid covers the whole track.
        assertTrue(beats.first() < expectedPeriod)
        assertTrue(beats.last() <= n * windowMs)
    }

    @Test
    fun detectBeats_flatSignal_noBeats() {
        val windowMs = 40L
        // No dynamics at all → no onset strength → no beat.
        val flat = List(300) { 0.05f }
        assertEquals(0, TrackEnergyAnalyzer.detectBeats(flat, 300 * windowMs, windowMs).size)
    }

    @Test
    fun detectBeats_monotonicRiser_noBeats() {
        val windowMs = 40L
        // A constant-rate riser has a FLAT autocorrelation — the peak doesn't stand out,
        // so it must NOT produce a beat grid (a grid here would reject real kicks later).
        val riser = List(300) { i -> i * 0.001f }
        assertEquals(0, TrackEnergyAnalyzer.detectBeats(riser, 300 * windowMs, windowMs).size)
    }

    // ── On-beat double-check (the lookahead rejection) ───────────────────

    @Test
    fun isOnBeat_matchesGrid() {
        val beats = intArrayOf(480, 960, 1440, 1920)
        assertTrue(TrackEnergyAnalyzer.isOnBeat(480, beats))
        assertTrue(TrackEnergyAnalyzer.isOnBeat(500, beats))   // 20ms jitter → on
        assertTrue(TrackEnergyAnalyzer.isOnBeat(1420, beats))  // 20ms jitter → on
        assertFalse(TrackEnergyAnalyzer.isOnBeat(700, beats))  // between beats → off
        assertFalse(TrackEnergyAnalyzer.isOnBeat(600, beats))  // 120ms off the grid → off
        // Just inside the 90ms tolerance.
        assertTrue(TrackEnergyAnalyzer.isOnBeat(480 + 90, beats))
        // Just outside it.
        assertFalse(TrackEnergyAnalyzer.isOnBeat(480 + 91, beats))
    }

    @Test
    fun isOnBeat_emptyGridRejectsNothing() {
        // No steady beat (ballad) → nothing is beat-validated, raw behavior preserved.
        assertTrue(TrackEnergyAnalyzer.isOnBeat(1234, IntArray(0)))
    }

    @Test
    fun isOnBeat_edges() {
        val beats = intArrayOf(480, 960)
        // Before the first beat / after the last beat — nearest-beat distance applies.
        assertTrue(TrackEnergyAnalyzer.isOnBeat(0, beats, toleranceMs = 500))
        assertFalse(TrackEnergyAnalyzer.isOnBeat(0, beats, toleranceMs = 100))
        assertFalse(TrackEnergyAnalyzer.isOnBeat(6000, beats, toleranceMs = 1000))
    }

    // ── Beat-grid drift regression (the "4 of 14" failure) ──────────────────

    @Test
    fun refinedPeriodMs_tracksNonIntegerTempo() {
        val windowMs = 40L
        // 174 BPM → 344.8ms period — no 40ms grid multiple matches it exactly.
        val periodMs = 344.8
        val n = 400
        val env = FloatArray(n)
        var t = 0.0
        while (t < n) {
            env[t.toInt().coerceAtMost(n - 1)] = 1.0f
            t += periodMs / windowMs
        }
        // A sparse impulse train makes the raw autocorrelation peak land on the 3×
        // harmonic (1038ms) — the median-gap disambiguation must pull it back to the
        // fundamental. Without onsets, the correlation period is what you get.
        val onsets = IntArray(40) { (it * periodMs).toInt() }
        val refined = TrackEnergyAnalyzer.refinedPeriodMs(env.toList(), windowMs, onsets)
        assertTrue("refined period should be near 344.8, got $refined", refined > 320f && refined < 370f)
        val noOnsets = TrackEnergyAnalyzer.refinedPeriodMs(env.toList(), windowMs)
        assertTrue(
            "correlation-only should still report a beat, got $noOnsets",
            noOnsets >= 320f && noOnsets <= 1200f
        )
    }

    @Test
    fun refineGrid_hugsNonIntegerTempoOnsets() {
        // The 174 BPM signature: detected onsets land on 40ms bucket centers that
        // alternate 320/360ms gaps, and the coarse grid locks to an integer lag
        // (here 320ms). A rigid 320ms grid drifts out of the 90ms tolerance within
        // a few beats — only the first handful are on-beat (the real 4-of-14).
        // The coarse grid is truncated before the tail so the sawtooth can't re-align.
        val onsets = intArrayOf(
            20, 360, 700, 1040, 1380, 1720, 2080, 2400, 2760, 3120, 3440, 3800, 4120, 4480
        )
        val coarse = intArrayOf(20, 340, 660, 980, 1300, 1620, 1940, 2260, 2580, 2900, 3220, 3540)
        val oldOnBeat = onsets.count { TrackEnergyAnalyzer.isOnBeat(it, coarse) }
        assertTrue(
            "coarse grid must reject most non-integer-tempo kicks (got $oldOnBeat/${onsets.size})",
            oldOnBeat < onsets.size / 2
        )
        val refined = TrackEnergyAnalyzer.refineGrid(coarse, onsets, 344.8f, 6000L)
        val newOnBeat = onsets.count { TrackEnergyAnalyzer.isOnBeat(it, refined) }
        assertEquals(
            "refined grid should accept every kick (got $newOnBeat/${onsets.size})",
            onsets.size, newOnBeat
        )
    }

    @Test
    fun refineGrid_sparseCoarseGrid_stillCoversAllKicks() {
        // When the coarse grid is a harmonic of the true tempo it's too sparse — the
        // refined walk must still produce a beat for every kick, not cap at the coarse
        // grid's (wrong) count.
        val onsets = intArrayOf(0, 500, 1000, 1500, 2000, 2500, 3000, 3500, 4000)
        val coarseHarmonic = intArrayOf(0, 1500, 3000) // 3× too sparse
        val refined = TrackEnergyAnalyzer.refineGrid(coarseHarmonic, onsets, 500f, 4200L)
        assertEquals(onsets.size, refined.size)
        val onBeat = onsets.count { TrackEnergyAnalyzer.isOnBeat(it, refined) }
        assertEquals(onsets.size, onBeat)
    }

    @Test
    fun refineGrid_emptyInputs_passthrough() {
        val coarse = intArrayOf(0, 480, 960)
        assertArrayEquals(coarse, TrackEnergyAnalyzer.refineGrid(coarse, IntArray(0), 480f, 2000L))
        assertArrayEquals(IntArray(0), TrackEnergyAnalyzer.refineGrid(IntArray(0), intArrayOf(100), 480f, 2000L))
    }

    // ── User-taught kick map (Teach kicks) ─────────────────────────────────

    @Test
    fun buildUserMap_snapsTapsToTransients() {
        // True kick times (after the 300ms pre-roll so no tap is dropped); the user
        // taps ~150ms late with jitter; the auto detector found the true kicks plus
        // one false positive (a snare at 1150, between two kicks).
        val trueKicks = intArrayOf(400, 900, 1400, 1900, 2400, 2900, 3400)
        val auto = intArrayOf(400, 900, 1150, 1400, 1900, 2400, 2900, 3400) // 1150 is the false positive
        val taps = trueKicks.mapIndexed { i, k -> k + 150 + (i % 3) * 20 - 20 }.toIntArray()
        val map = TrackEnergyAnalyzer.buildUserMap(taps, auto)
        // Every tap snapped to its own kick — the false positive never selected
        // (it's not near any tap), and the output matches the true kicks.
        assertEquals(trueKicks.size, map.size)
        for (i in trueKicks.indices) {
            assertTrue(
                "kick $i should be near ${trueKicks[i]}, got ${map[i]}",
                kotlin.math.abs(map[i] - trueKicks[i]) <= 60
            )
        }
    }

    @Test
    fun buildUserMap_keepsTapsTheDetectorMissed() {
        // The detector missed every other kick (why the user is teaching). Taps at
        // the missed kicks must survive, un-snapped (no transient nearby).
        val auto = intArrayOf(0, 1000, 2000, 3000)
        val taps = intArrayOf(0, 500, 1000, 1500, 2000, 2500, 3000)
        val map = TrackEnergyAnalyzer.buildUserMap(taps, auto)
        assertTrue("missed kicks should be kept (got ${map.joinToString()})", map.contains(500))
        assertTrue(map.contains(1500))
        assertTrue(map.contains(2500))
    }

    @Test
    fun buildUserMap_tooFewTaps_isEmpty() {
        assertEquals(0, TrackEnergyAnalyzer.buildUserMap(intArrayOf(1200), intArrayOf(1200)).size)
        // Pre-roll taps (before 300ms) are dropped.
        assertEquals(0, TrackEnergyAnalyzer.buildUserMap(intArrayOf(100, 150), intArrayOf(100, 150)).size)
    }

    // ── Kick-map editor: snap-to-transient placement ─────────────────────────

    @Test
    fun snapKickPlacement_snapsToNearbyOnset() {
        // A tap within 150ms of an auto onset lands exactly on it (sub-bucket exact).
        val onsets = intArrayOf(500, 1000, 1500)
        val energy = FloatArray(16) { 0.1f } // below the energy floor → onset wins anyway
        assertEquals(1000, TrackEnergyAnalyzer.snapKickPlacement(1050, onsets, energy))
        assertEquals(500, TrackEnergyAnalyzer.snapKickPlacement(480, onsets, energy))
        // Just outside the onset window (300ms away) → falls through to peak/tap logic.
        assertTrue(TrackEnergyAnalyzer.snapKickPlacement(200, onsets, energy) != 500)
    }

    @Test
    fun snapKickPlacement_fallsBackToLocalPeak() {
        // No onset near the tap — the strongest local energy peak wins (bucket center).
        val onsets = intArrayOf(0, 3000, 6000)
        val energy = FloatArray(24) { 0.1f }
        energy[4] = 0.9f // bucket 4 → center 4×250+125 = 1125ms
        assertEquals(1125, TrackEnergyAnalyzer.snapKickPlacement(1200, onsets, energy))
    }

    @Test
    fun snapKickPlacement_flatRegionKeepsTap() {
        val energy = FloatArray(16) { 0.05f }
        assertEquals(2000, TrackEnergyAnalyzer.snapKickPlacement(2000, IntArray(0), energy))
    }

    @Test
    fun snapKickPlacement_clampsToSong() {
        val energy = FloatArray(8) { 0.05f } // 8 × 250ms = 2000ms of audio
        assertEquals(0, TrackEnergyAnalyzer.snapKickPlacement(-999, IntArray(0), energy))
        assertEquals(2000, TrackEnergyAnalyzer.snapKickPlacement(99_999, IntArray(0), energy))
    }
}
