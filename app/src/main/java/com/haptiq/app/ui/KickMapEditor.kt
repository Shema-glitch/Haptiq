package com.haptiq.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haptiq.app.audio.TrackEnergyAnalyzer
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*
import coil.compose.AsyncImage
import kotlin.math.roundToInt

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
    onScrub: (Float) -> Unit,
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
                // Pinch zoom: 1× = full-resolution bar spacing, up to 6× for fine placement.
                val zoomState = remember { mutableStateOf(1f) }
                val contentWDp = stepDp * energy.size * zoomState.value
                // Manual scroll position (px). A plain ScrollState would clamp every
                // delta to its maxValue — and maxValue only updates when a real
                // scrollable container owns the state — so pan/zoom/jump all froze at
                // 0. This is clamped by hand against the actual content width instead.
                var scrollPx by remember { mutableStateOf(0f) }
                var viewportWpx by remember { mutableStateOf(0f) }
                val haptics = LocalHapticFeedback.current
                val scrubHitSlopPx = with(LocalDensity.current) { 24.dp.toPx() }
                // pointerInput closures capture their first-frame values; these keep the
                // live progress/callbacks current without restarting an in-progress gesture.
                val currentProgress by rememberUpdatedState(progress)
                val currentOnTogglePin by rememberUpdatedState(onTogglePin)
                val currentOnScrub by rememberUpdatedState(onScrub)

                // ── Overview strip: whole song at a glance; tap to jump, drag to scrub ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = Spacing.md)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val contentW = stepPx * zoomState.value * energy.size
                                scrollPx = ((contentW - viewportWpx).coerceAtLeast(0f)) * fraction
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                // Drag anywhere on the overview scrubs the whole song —
                                // the fastest way to audition a section without playback.
                                currentOnScrub((change.position.x / size.width).coerceIn(0f, 1f))
                                change.consume()
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
                        // Playhead — always drawn so the scrub handle is a discoverable
                        // affordance even before the song has been played.
                        drawLine(ColorPrimary, Offset(progress * size.width, 0f), Offset(progress * size.width, size.height), 2.dp.toPx())
                        // Viewport indicator over the detail's visible window (zoom-aware).
                        val contentWpx = stepPx * energy.size * zoomState.value
                        val viewW = viewportWpx.coerceAtLeast(1f)
                        val indicatorLeft = size.width * (scrollPx / contentWpx)
                        val indicatorWidth = size.width * (viewW / contentWpx)
                        drawRect(
                            color = ColorPrimary.copy(alpha = 0.12f),
                            topLeft = Offset(indicatorLeft, 0f),
                            size = Size(indicatorWidth, size.height)
                        )
                    }
                }

                // ── Detail: full-resolution waveform with one unified gesture handler.
                // Pinch zooms (1–6×) around the pinch point, single-finger drag pans,
                // dragging from the playhead handle scrubs (seek + haptic audition),
                // and a clean tap places/removes a kick pin. Gestures live on the
                // viewport Box (not the translated canvas), so pointer positions are
                // viewport-local and the scroll offset is added explicitly — no
                // dependence on how graphicsLayer translates hit testing. ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .onSizeChanged { viewportWpx = it.width.toFloat() }
                        .pointerInput(energy.size, onsets.size) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var isPinch = false
                                var isTap = true
                                var scrubbing = false
                                var totalDrag = 0f
                                var lastDist = 0f
                                var scrollNow = scrollPx
                                val slop = viewConfiguration.touchSlop
                                val contentW = stepPx * zoomState.value * energy.size
                                val maxScroll = (contentW - viewportWpx).coerceAtLeast(0f)
                                val playheadViewportX = currentProgress * contentW - scrollNow
                                val nearPlayhead =
                                    kotlin.math.abs(down.position.x - playheadViewportX) <= scrubHitSlopPx

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }

                                    if (pressed.size >= 2) {
                                        // Pinch: zoom around the centroid, keeping the
                                        // content under the fingers stationary.
                                        isPinch = true
                                        isTap = false
                                        scrubbing = false
                                        val c0 = pressed[0]
                                        val c1 = pressed[1]
                                        val dist = (c1.position - c0.position).getDistance()
                                        val centroidX = (c0.position.x + c1.position.x) / 2f
                                        if (lastDist > 0f && dist > 0f) {
                                            val factor = dist / lastDist
                                            if (factor != 1f) {
                                                val oldContentX = scrollNow + centroidX
                                                zoomState.value = (zoomState.value * factor).coerceIn(1f, 6f)
                                                val newContent = stepPx * zoomState.value * energy.size
                                                val newMax = (newContent - viewportWpx).coerceAtLeast(0f)
                                                scrollNow = (oldContentX * factor - centroidX).coerceIn(0f, newMax)
                                                scrollPx = scrollNow
                                            }
                                        }
                                        lastDist = dist
                                        event.changes.forEach { it.consume() }
                                    } else if (pressed.size == 1) {
                                        val change = pressed[0]
                                        val drag = change.positionChange()
                                        if (isPinch) { isPinch = false; lastDist = 0f }
                                        if (scrubbing) {
                                            val w = stepPx * zoomState.value * energy.size
                                            currentOnScrub(((scrollNow + change.position.x) / w).coerceIn(0f, 1f))
                                            change.consume()
                                        } else if (nearPlayhead) {
                                            // Drag from the playhead handle scrubs (seek +
                                            // haptic audition); a still tap stays a tap.
                                            totalDrag += drag.x
                                            if (!scrubbing && kotlin.math.abs(totalDrag) > slop) scrubbing = true
                                            if (scrubbing) {
                                                isTap = false
                                                val w = stepPx * zoomState.value * energy.size
                                                currentOnScrub(((scrollNow + change.position.x) / w).coerceIn(0f, 1f))
                                                change.consume()
                                            }
                                        } else {
                                            // Plain pan — one finger drags the waveform.
                                            totalDrag += drag.x
                                            if (kotlin.math.abs(totalDrag) > slop && isTap) isTap = false
                                            if (!isTap) {
                                                scrollNow = (scrollNow - drag.x).coerceIn(0f, maxScroll)
                                                scrollPx = scrollNow
                                                change.consume()
                                            }
                                        }
                                    }
                                    if (event.changes.all { !it.pressed }) break
                                }

                                if (isTap && !scrubbing) {
                                    // Clean tap (no drag, no pinch): place/remove a pin,
                                    // snapped to the nearest real transient.
                                    val contentX = down.position.x + scrollNow
                                    val bucket = (contentX / (stepPx * zoomState.value))
                                        .toInt().coerceIn(0, energy.size - 1)
                                    val tapMs = (bucket * frameMs + frameMs / 2).toInt()
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnTogglePin(
                                        TrackEnergyAnalyzer.snapKickPlacement(tapMs, onsets, energy)
                                    )
                                }
                            }
                        }
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(contentWDp)
                            .fillMaxHeight()
                            .graphicsLayer { translationX = -scrollPx }
                    ) {
                        val z = zoomState.value
                        val barWpx = barW.toPx() * z
                        val stepPxZ = stepPx * z
                        val minH = 3.dp.toPx()
                        val baseColor = ColorOutlineVariant.copy(alpha = 0.75f)
                        for (i in energy.indices) {
                            val e = energy[i]
                            val h = minH + e * (size.height - minH)
                            drawRoundRect(
                                color = baseColor,
                                topLeft = Offset(i * stepPxZ, (size.height - h) / 2f),
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
                        // Playhead — live position while playing; static once paused. The
                        // top dot is the scrub handle (24dp hit slop in the gesture handler).
                        val playX = currentProgress * size.width
                        drawLine(ColorPrimary, Offset(playX, 0f), Offset(playX, size.height), 2.dp.toPx())
                        drawCircle(ColorPrimary, 6.dp.toPx(), Offset(playX, 10.dp.toPx()))
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
                        text = "Pinch to zoom · drag the playhead to scrub · tap a peak or use At playhead",
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
                        FilledTonalButton(
                            onClick = {
                                // Place a pin exactly where the playhead is (snapped to the
                                // nearest real transient). Before anything has played the
                                // playhead sits at 0ms, so fall back to the center of the
                                // visible waveform instead of the left edge.
                                val targetMs = if (currentProgress > 0.01f) {
                                    (currentProgress * totalMs).toInt()
                                } else {
                                    val centerContentX = scrollPx + viewportWpx / 2f
                                    val centerBucket = (centerContentX / (stepPx * zoomState.value))
                                        .toInt().coerceIn(0, energy.size - 1)
                                    (centerBucket * frameMs + frameMs / 2).toInt()
                                }
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnTogglePin(
                                    TrackEnergyAnalyzer.snapKickPlacement(targetMs, onsets, energy)
                                )
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xxs))
                            Text("At playhead", style = MaterialTheme.typography.labelLarge)
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
