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
}
