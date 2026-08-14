package com.haptiq.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class KickLatencyTrackerTest {

    private fun stats(vararg values: Float) = KickLatencyTracker.LatencyStats().apply {
        values.forEach { add(it) }
    }

    @Test
    fun `empty stats are all zeros`() {
        val s = KickLatencyTracker.LatencyStats()
        assertEquals(0, s.count)
        assertEquals(0f, s.mean(), 0.001f)
        assertEquals(0f, s.min(), 0.001f)
        assertEquals(0f, s.max(), 0.001f)
        assertEquals(0f, s.percentile(95), 0.001f)
    }

    @Test
    fun `mean min max on known samples`() {
        val s = stats(2f, 4f, 6f, 8f)
        assertEquals(4, s.count)
        assertEquals(5f, s.mean(), 0.001f)
        assertEquals(2f, s.min(), 0.001f)
        assertEquals(8f, s.max(), 0.001f)
    }

    @Test
    fun `percentiles index positionally on sorted samples`() {
        // Sorted: [1,2,3,4,5,6,7,8,9,10]
        val s = stats(10f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f)
        assertEquals(1f, s.percentile(0), 0.001f)
        // p50: 0.5 * (10-1) = 4.5 -> index 4 -> 5
        assertEquals(5f, s.percentile(50), 0.001f)
        // p95: 0.95 * (10-1) = 8.55 -> index 8 -> 9
        assertEquals(9f, s.percentile(95), 0.001f)
        // p100: 0.99... -> index 9 -> 10
        assertEquals(10f, s.percentile(100), 0.001f)
    }

    @Test
    fun `single sample percentile is itself`() {
        val s = stats(3.5f)
        assertEquals(3.5f, s.percentile(50), 0.001f)
        assertEquals(3.5f, s.percentile(95), 0.001f)
    }

    @Test
    fun `samples are capped at max dropping oldest`() {
        val s = KickLatencyTracker.LatencyStats(maxSamples = 5)
        repeat(20) { i -> s.add(i.toFloat()) }
        assertEquals(5, s.count)
        // Oldest five dropped — remaining are 15..19
        assertEquals(15f, s.min(), 0.001f)
        assertEquals(19f, s.max(), 0.001f)
        assertEquals(17f, s.mean(), 0.001f)
    }

    // ── Motor-calibrated AOT lead ───────────────────────────────────────────

    @Test
    fun `motorLeadMs falls back to 50ms before enough onsets are measured`() {
        assertEquals(50L, KickLatencyTracker().motorLeadMs())
        val nearlyEnough = stats(28f, 30f)
        assertEquals(50L, KickLatencyTracker().motorLeadMsFor(nearlyEnough))
    }

    @Test
    fun `motorLeadMs is the p50 dispatch-to-onset latency`() {
        // p50 of [28, 30, 32] → index 1 → 30
        assertEquals(30L, KickLatencyTracker().motorLeadMsFor(stats(28f, 30f, 32f)))
    }

    @Test
    fun `motorLeadMs is clamped to a sane range`() {
        // An unusually fast motor (or a weak onset) can't drive the lead below 20ms.
        assertEquals(20L, KickLatencyTracker().motorLeadMsFor(stats(5f, 10f, 15f)))
        // A slow ERM or a measurement anomaly can't push it past 120ms.
        assertEquals(120L, KickLatencyTracker().motorLeadMsFor(stats(200f, 300f, 400f)))
    }
}
