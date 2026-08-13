package com.haptiq.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haptiq.app.audio.TrackEnergyAnalyzer
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Full-screen visual kick-map editor — the replacement for blind tap-along teaching.
 * The user SEES the song's bass waveform (the same seekEnergy envelope that powers the
 * scrubber), with every auto-detected transient marked as a candidate and their own
 * placements as amber pins. Tapping a peak pins it (snapped to the nearest real
 * transient — placement is exact, never a human-latency guess); tapping a pin removes
 * it. A playhead tracks live playback so misfires are visually obvious.
 */
@Composable
fun KickMapEditorOverlay(
    song: Song,
    energy: FloatArray?,
    onsets: IntArray,
    beats: IntArray,
    pins: List<Int>,
    loading: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onTogglePin: (Int) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit
) {
    BackHandler(onBack = onCancel)

    Surface(modifier = Modifier.fillMaxSize(), color = ColorBackground) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // ── Header: unmistakable song identity (you never map the wrong track) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Close editor", tint = ColorOnSurfaceVariant)
                }
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(ColorSurfaceVariant)
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = ColorOnSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} · ${formatMs(song.durationSeconds * 1000)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorOnSurface60,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Detected tempo — the rhythm context for placing kicks.
                    val bpm = bpmOf(beats)
                    if (bpm > 0) {
                        Text(
                            text = "~$bpm BPM",
                            style = MaterialTheme.typography.labelMedium,
                            color = ColorPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(ColorPrimary.copy(alpha = 0.12f))
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                    Text(
                        text = "${pins.size} kicks",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pins.isNotEmpty()) ColorHapticAccent else ColorOnSurface60,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (pins.isNotEmpty()) ColorHapticAccent.copy(alpha = 0.12f) else ColorSurfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    )
                }
            }
            HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.4f))

            val frameMs = 250L
            val totalMs = (song.durationSeconds * 1000L).coerceAtLeast(1L)

            if (energy == null) {
                // Envelope still decoding (or the file can't be read) — never a blank box.
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (loading) "Analyzing kicks…" else "Rendering waveform…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorOnSurface60
                    )
                }
            } else {
                val barW = 4.dp
                val gap = 3.dp
                val stepDp = barW + gap
                val stepPx = with(LocalDensity.current) { stepDp.toPx() }
                val totalWDp = stepDp * energy.size
                val scrollState = rememberScrollState()
                val scope = rememberCoroutineScope()

                // ── Overview strip: whole song at a glance; tap to jump the detail ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = Spacing.md)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                scope.launch { scrollState.scrollTo((scrollState.maxValue * fraction).toInt()) }
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val barWpx = 2.dp.toPx()
                        val gapPx = 1.dp.toPx()
                        val bars = (size.width / (barWpx + gapPx)).toInt().coerceAtLeast(1)
                        for (i in 0 until bars) {
                            val from = i * energy.size / bars
                            val to = (((i + 1) * energy.size / bars).coerceAtLeast(from + 1)).coerceAtMost(energy.size)
                            var e = 0f
                            for (j in from until to) e += energy[j]
                            e /= (to - from)
                            val h = 2.dp.toPx() + e * (size.height - 2.dp.toPx())
                            drawRoundRect(
                                color = ColorOutlineVariant.copy(alpha = 0.6f),
                                topLeft = Offset(i * (barWpx + gapPx), (size.height - h) / 2f),
                                size = Size(barWpx, h),
                                cornerRadius = CornerRadius(barWpx / 2f)
                            )
                        }
                        val xFor: (Int) -> Float = { ms -> (ms / totalMs.toFloat()) * size.width }
                        // Beat grid here too — rhythm context at the whole-song scale.
                        beats.forEach { b ->
                            val x = xFor(b)
                            drawLine(ColorOutlineVariant.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        }
                        onsets.forEach {
                            drawLine(ColorOnSurface60.copy(alpha = 0.4f), Offset(xFor(it), 0f), Offset(xFor(it), size.height), 1.dp.toPx())
                        }
                        pins.forEach {
                            drawLine(ColorHapticAccent, Offset(xFor(it), 0f), Offset(xFor(it), size.height), 2.dp.toPx())
                        }
                        if (progress > 0f) {
                            drawLine(ColorPrimary, Offset(progress * size.width, 0f), Offset(progress * size.width, size.height), 2.dp.toPx())
                        }
                        // Viewport indicator over the detail's visible window.
                        val detailWidth = (totalWDp.value - scrollState.maxValue).coerceAtLeast(1f)
                        val indicatorLeft = size.width * (scrollState.value / totalWDp.value)
                        val indicatorWidth = size.width * (detailWidth / totalWDp.value)
                        drawRect(
                            color = ColorPrimary.copy(alpha = 0.12f),
                            topLeft = Offset(indicatorLeft, 0f),
                            size = Size(indicatorWidth, size.height)
                        )
                    }
                }

                // ── Detail: full-resolution waveform, scrollable, tap to place ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .horizontalScroll(scrollState)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(totalWDp)
                            .fillMaxHeight()
                            .pointerInput(energy.size, onsets.size) {
                                detectTapGestures { offset ->
                                    val bucket = (offset.x / stepPx).toInt().coerceIn(0, energy.size - 1)
                                    val tapMs = (bucket * frameMs + frameMs / 2).toInt()
                                    onTogglePin(
                                        TrackEnergyAnalyzer.snapKickPlacement(tapMs, onsets, energy)
                                    )
                                }
                            }
                    ) {
                        val barWpx = barW.toPx()
                        val gapPx = gap.toPx()
                        val minH = 3.dp.toPx()
                        val baseColor = ColorOutlineVariant.copy(alpha = 0.75f)
                        for (i in energy.indices) {
                            val e = energy[i]
                            val h = minH + e * (size.height - minH)
                            drawRoundRect(
                                color = baseColor,
                                topLeft = Offset(i * (barWpx + gapPx), (size.height - h) / 2f),
                                size = Size(barWpx, h),
                                cornerRadius = CornerRadius(barWpx / 2f)
                            )
                        }
                        // Beat grid — rhythm context while placing pins. Visible but
                        // quieter than the onset ticks and never louder than the pins.
                        val xFor: (Int) -> Float = { ms -> (ms / totalMs.toFloat()) * size.width }
                        beats.forEach { b ->
                            val x = xFor(b)
                            drawLine(ColorOutlineVariant.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        }
                        // Auto candidates: short ticks along the top edge.
                        onsets.forEach { o ->
                            val x = xFor(o)
                            drawLine(ColorOnSurface60.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, 12.dp.toPx()), 2.dp.toPx())
                        }
                        // User pins: full-height amber with a cap dot — unmistakable.
                        pins.forEach { p ->
                            val x = xFor(p)
                            drawLine(ColorHapticAccent, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
                            drawCircle(ColorHapticAccent, 4.dp.toPx(), Offset(x, 7.dp.toPx()))
                        }
                        // Playhead — live position while playing; static once paused.
                        if (isPlaying || progress > 0f) {
                            drawLine(ColorPrimary, Offset(progress * size.width, 0f), Offset(progress * size.width, size.height), 2.dp.toPx())
                        }
                    }
                }

                // ── Footer: controls + the one-line interaction hint ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Tap a peak to mark a kick · tap a pin to remove · faint lines are the beat grid",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorOnSurface60
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        IconButton(onClick = onTogglePlay) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = ColorPrimary
                            )
                        }
                        IconButton(onClick = onRestart) {
                            Icon(Icons.Default.Replay, contentDescription = "Restart from beginning", tint = ColorOnSurface60)
                        }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = onClear,
                            enabled = pins.isNotEmpty()
                        ) {
                            Text("Clear all", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = onSave,
                            enabled = pins.size >= 2
                        ) {
                            Text(
                                text = if (pins.size >= 2) "Save (${pins.size} kicks)" else "Save",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun bpmOf(beats: IntArray): Int {
    if (beats.size < 3) return 0
    val gaps = (1 until beats.size).map { beats[it] - beats[it - 1] }.sorted()
    val median = gaps[gaps.size / 2]
    return if (median > 0) (60000.0 / median).roundToInt() else 0
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
