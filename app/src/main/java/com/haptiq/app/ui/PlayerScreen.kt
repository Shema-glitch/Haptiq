package com.haptiq.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

private val SPEED_STEPS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

/** Next value in the speed cycle, wrapping back to the start. */
private fun nextSpeed(current: Float): Float {
    val i = SPEED_STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return SPEED_STEPS[((if (i < 0) 1 else i) + 1) % SPEED_STEPS.size]
}

/** "1×", "1.25×", "0.5×" — no trailing zeros. */
private fun formatSpeed(speed: Float): String {
    val s = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return "${s}×"
}

/**
 * Pure Now Playing content — no drag/dismiss physics of its own. It's hosted inside
 * [NowPlayingSheet], which owns the single drag gesture that morphs between this and
 * the docked mini-player bar; this composable just renders "the expanded state" and
 * calls [onCollapse] when the user taps the chevron (the sheet then animates itself
 * back down to the bar, with real content — the screen underneath — visible through
 * the drag the whole way, instead of this screen managing its own fake dismiss).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: HaptiqUiState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextClicked: () -> Unit,
    onPrevClicked: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekPreview: (Float) -> Unit = {},
    onToggleHaptics: (Boolean) -> Unit,
    onHapticStudioClicked: () -> Unit,
    onSongSelected: (List<Song>, Int) -> Unit,
    onQueueAction: (HaptiqUiAction) -> Unit = {},
    onRefreshDndStatus: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
    onSetVolume: (Float) -> Unit = {}
) {
    val currentSong = state.currentSong ?: return
    val isFav = currentSong.id in state.favoriteIds
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) { onRefreshDndStatus() }

    // NOT remember(state.playbackProgress): keying on live progress reinitialized
    // this every 500ms tick, snapping the thumb away from the finger mid-drag.
    // While not dragging, the displayed value comes from state directly.
    var sliderValue by remember { mutableStateOf(0f) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // DND toast: a floating overlay that appears briefly when the player opens with
    // DND silencing haptics, then fades. Deliberately NOT inline — an inline banner
    // reflowed the artwork and controls downward every time it showed.
    var dndToastVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isDndActive, state.hapticActive) {
        if (state.isDndActive && state.hapticActive) {
            dndToastVisible = true
            kotlinx.coroutines.delay(6000)
            dndToastVisible = false
        } else {
            dndToastVisible = false
        }
    }

    // ── Kick pulse: sharp scale burst when play/pause is pressed ──
    val kickPulse = remember { Animatable(0f) }
    LaunchedEffect(state.isPlaying) {
        if (state.hapticActive) {
            kickPulse.snapTo(0f)
            kickPulse.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        }
    }

    // ── Bass hum: slow continuous breathing glow when haptics are live ──
    val bassHumScale = if (!state.hapticActive || isReducedMotionEnabled()) 1f else {
        val t = rememberInfiniteTransition(label = "bass_hum")
        val s by t.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                tween(3200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "bass_hum_scale"
        )
        s
    }
    val bassHumAlpha = if (!state.hapticActive || isReducedMotionEnabled()) 0.06f else {
        val t = rememberInfiniteTransition(label = "bass_hum")
        val a by t.animateFloat(
            initialValue = 0.04f,
            targetValue = 0.10f,
            animationSpec = infiniteRepeatable(
                tween(3200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "bass_hum_alpha"
        )
        a
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .testTag("player_screen")
    ) {
        val screenHeight = maxHeight
        val isShortScreen = screenHeight < 680.dp

        // Ambient glow behind artwork — kick orange represents the haptic
        // energy radiating from the player. Bass hum: slow continuous breathing
        // scale + alpha when haptics are live. Reduced motion: frozen frame.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isShortScreen) 220.dp else 350.dp)
                .graphicsLayer {
                    scaleX = bassHumScale
                    scaleY = bassHumScale
                    alpha = bassHumAlpha / 0.06f // normalize around the base alpha
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ColorKick.copy(alpha = bassHumAlpha), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Drag handle ────────────────────────────────────
            // Brutalist: thick bar, no rounding.
            Box(
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .width(40.dp)
                    .height(4.dp)
                    .background(ColorOnSurface)
            )

            // ─── Top Bar ────────────────────────────────────────
            // Sharp, minimal — the design system uses hairline dividers, not
            // elevated chrome. Down chevron matches the drag gesture.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.topBarHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brutalist: square button, thick border
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(ComponentSize.touchTarget)
                        .background(ColorSurface)
                        .border(BorderWidth.medium, ColorOnSurface)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Minimize", tint = ColorOnSurface, modifier = Modifier.size(24.dp))
                }

                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60
                )

                // Invisible twin so "Now playing" stays centered.
                Spacer(Modifier.size(ComponentSize.touchTarget))
            }

            Spacer(Modifier.weight(if (isShortScreen) 0.05f else 0.15f))

            // ─── Artwork ────────────────────────────────────────
            // Brutalist: thick black border, square corners.
            val artworkModifier = if (isShortScreen) {
                Modifier.size(180.dp)
            } else {
                Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
            }
            Box(
                modifier = artworkModifier
                    .border(BorderWidth.medium, ColorOnSurface)
            ) {
                Crossfade(
                    targetState = currentSong.artworkUrl,
                    animationSpec = tween(durationMillis = 450),
                    label = "artwork_crossfade"
                ) { artworkUrl ->
                    ArtworkImage(
                        model = artworkUrl,
                        contentDescription = "Album Artwork",
                        modifier = Modifier.fillMaxSize(),
                        iconSize = if (isShortScreen) 48.dp else 64.dp,
                        cornerRadius = Radius.lg,
                        fallbackLabel = currentSong.title
                    )
                }
            }

            Spacer(Modifier.weight(if (isShortScreen) 0.05f else 0.15f))

            // ─── Centered Track Info ────────────────────────────
            // Title in Space Grotesk (headline styles use it), artist in Inter.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentSong.title,
                    style = if (isShortScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    color = ColorOnSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentSong.artist,
                    style = MaterialTheme.typography.titleSmall,
                    color = ColorOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            // ─── Horizontal Options Strip ───────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heart Favorite
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite(currentSong.id)
                }) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) ColorPrimary else ColorOnSurface60,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                // Share icon — hands off to the system share sheet, same as any other player
                IconButton(onClick = {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "${currentSong.title} — ${currentSong.artist}")
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share track"))
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = ColorOnSurface60,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                // Queue — brutalist: square, thick border.
                Surface(
                    onClick = { showQueue = true },
                    shape = RoundedCornerShape(Radius.sm),
                    color = ColorSurface,
                    contentColor = ColorOnSurface,
                    modifier = Modifier
                        .height(36.dp)
                        .border(BorderWidth.medium, ColorOnSurface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Up Next",
                            tint = ColorOnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Up Next",
                            style = MaterialTheme.typography.labelMedium,
                            color = ColorOnSurface
                        )
                    }
                }
            }

            Spacer(Modifier.weight(if (isShortScreen) 0.05f else 0.2f))

            // ─── Waveform Seek Slider ───────────────────────────
            // The track is the song's precomputed bass envelope; dragging the thumb
            // pulses the motor with the intensity under it (haptic seek-preview) —
            // you feel where the drop is before you jump to it.
            Column(modifier = Modifier.fillMaxWidth()) {
                val displayedProgress = if (isDraggingSlider) sliderValue else state.playbackProgress
                val seekEnergy = state.seekEnergy
                Slider(
                    value = displayedProgress,
                    onValueChange = {
                        isDraggingSlider = true
                        sliderValue = it
                        onSeekPreview(it)
                    },
                    onValueChangeFinished = { isDraggingSlider = false; onSeek(sliderValue) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = ColorPrimary,
                        inactiveTrackColor = ColorOutlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    thumb = {
                        // Brutalist: square thumb, thick border.
                        SliderDefaults.Thumb(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            colors = SliderDefaults.colors(thumbColor = Color.White),
                            modifier = Modifier
                                .size(20.dp)
                                .border(BorderWidth.medium, ColorOnSurface)
                        )
                    },
                    track = { sliderState ->
                        if (seekEnergy != null && seekEnergy.isNotEmpty()) {
                            // Amber only while the envelope is live haptic data;
                            // with haptics off it reverts to the neutral brand clay.                                // Kick orange when haptics are live (the waveform IS the
                                // kick energy); neutral when haptics are off.
                                val playedColor = if (state.hapticActive) ColorKick else ColorPrimary
                                val restColor = ColorOutlineVariant
                            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                                val barW = 3.dp.toPx()
                                val gap = 2.dp.toPx()
                                val bars = (size.width / (barW + gap)).toInt().coerceAtLeast(1)
                                val minH = 3.dp.toPx()
                                for (i in 0 until bars) {
                                    val from = i * seekEnergy.size / bars
                                    val to = (((i + 1) * seekEnergy.size / bars).coerceAtLeast(from + 1))
                                        .coerceAtMost(seekEnergy.size)
                                    var e = 0f
                                    for (j in from until to) e += seekEnergy[j]
                                    e /= (to - from)
                                    val h = minH + e * (size.height - minH)
                                    val played = (i + 0.5f) / bars <= displayedProgress
                                    drawRoundRect(
                                        color = if (played) playedColor else restColor,
                                        topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                                        size = Size(barW, h),
                                        cornerRadius = CornerRadius(barW / 2f)
                                    )
                                }
                                // Kick-onset overlay: one vertical tick per AOT-map kick.
                                // Orange = the beat grid will let lookahead pre-fire it there;
                                // faint = off-beat, rejected by the double-check. Visible
                                // whether or not the toggle is on — it's the preview of
                                // exactly where the AOT map would pre-fire.
                                val totalMs = (state.currentSong?.durationSeconds ?: 0) * 1000L
                                if (totalMs > 0 && state.seekKickMarkers.isNotEmpty()) {
                                    state.seekKickMarkers.forEach { marker ->
                                        val x = (marker.positionMs.toFloat() / totalMs) * size.width
                                        drawLine(
                                            color = if (marker.onBeat) ColorKick.copy(alpha = 0.85f)
                                            else ColorOnSurface60.copy(alpha = 0.35f),
                                            start = Offset(x, 0f),
                                            end = Offset(x, size.height),
                                            strokeWidth = if (marker.onBeat) 2.dp.toPx() else 1.dp.toPx()
                                        )
                                    }
                                }
                            }
                        } else if (state.seekEnergyLoading) {
                            // "Rendering waveform" — a travelling shimmer over placeholder
                            // bars so the empty seek bar reads as *working*, not broken.
                            WaveformRenderingShimmer()
                        } else {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = ColorPrimary,
                                    inactiveTrackColor = ColorOutlineVariant
                                )
                            )
                        }
                    }
                )
                // Timestamps in JetBrains Mono — only real numbers get mono.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        state.currentTimeText,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorOnSurface60
                    )
                    Text(
                        state.remainingTimeText,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorOnSurface60
                    )
                }
            }

            Spacer(Modifier.weight(if (isShortScreen) 0.1f else 0.3f))

            // ─── Transport Controls ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        // Clay = interactive/active; amber stays reserved for haptic
                        // signalling only, so a lit transport control never reads as
                        // "haptics are firing".
                        tint = if (state.isShuffle) ColorPrimary else ColorOnSurface60,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Prev
                IconButton(onClick = onPrevClicked) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = ColorOnSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play/Pause FAB — brutalist: square, kick orange, thick border.
                val fabInteraction = remember { MutableInteractionSource() }
                val isFabPressed by fabInteraction.collectIsPressedAsState()
                val fabPressScale by animateFloatAsState(
                    targetValue = if (isFabPressed) 0.92f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "fab_press_scale"
                )
                // Kick pulse: sharp scale burst on play — the signature "thump" moment
                val kickBurstScale = lerp(1f, 1.12f, kickPulse.value)
                val kickBurstAlpha = lerp(0.35f, 0f, kickPulse.value)
                Box(
                    modifier = Modifier
                        .size(if (isShortScreen) 56.dp else 64.dp)
                        .graphicsLayer {
                            val s = fabPressScale * kickBurstScale
                            scaleX = s; scaleY = s
                        }
                        .background(ColorKick)
                        .border(BorderWidth.thick, ColorOnSurface)
                        .clickable(
                            interactionSource = fabInteraction,
                            indication = null,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePlayPause()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = state.isPlaying,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.6f, animationSpec = tween(180)) + fadeIn(tween(140)))
                                .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(140)) + fadeOut(tween(100)))
                        },
                        label = "play_pause_icon"
                    ) { isPlaying ->
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or Pause",
                            tint = ColorOnPrimary,
                            modifier = Modifier.size(if (isShortScreen) 30.dp else 34.dp)
                        )
                    }
                }

                // Next
                IconButton(onClick = onNextClicked) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = ColorOnSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Repeat — cycles off → all → one
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (state.repeatMode == com.haptiq.app.data.RepeatMode.ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = when (state.repeatMode) {
                            com.haptiq.app.data.RepeatMode.OFF -> "Repeat off"
                            com.haptiq.app.data.RepeatMode.ALL -> "Repeat all"
                            com.haptiq.app.data.RepeatMode.ONE -> "Repeat one"
                        },
                        tint = if (state.repeatMode != com.haptiq.app.data.RepeatMode.OFF) {
                            ColorPrimary
                        } else ColorOnSurface60,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ─── Volume + Speed row ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    imageVector = if (state.volume <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Volume",
                    tint = ColorOnSurface60,
                    modifier = Modifier.size(ComponentSize.iconSmall)
                )
                Slider(
                    value = state.volume,
                    onValueChange = onSetVolume,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ColorOnSurface,
                        activeTrackColor = ColorPrimary,
                        inactiveTrackColor = ColorOutlineVariant
                    ),
                    modifier = Modifier.weight(1f).height(24.dp)
                )
                // Speed chip — brutalist: square, thick border, mono text.
                Surface(
                    onClick = { onSetSpeed(nextSpeed(state.playbackSpeed)) },
                    shape = RoundedCornerShape(Radius.sm),
                    color = if (state.playbackSpeed != 1f) ColorKick else ColorSurface,
                    modifier = Modifier
                        .height(32.dp)
                        .border(
                            BorderWidth.medium,
                            ColorOnSurface
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp)
                            .padding(horizontal = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            formatSpeed(state.playbackSpeed),
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.playbackSpeed != 1f) ColorOnPrimary else ColorOnSurface
                        )
                    }
                }
            }

            Spacer(Modifier.weight(if (isShortScreen) 0.1f else 0.3f))

            // ─── Haptic Engine Row ──────────────────────────────
            // Brutalist: thick border, square, teal when active.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderWidth.medium,
                        ColorOnSurface
                    )
                    .clickable(onClick = onHapticStudioClicked),
                color = if (state.hapticActive) ColorBass else ColorSurface,
                shape = RoundedCornerShape(Radius.sm)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Vibration,
                        null,
                        tint = if (state.hapticActive) ColorOnPrimary else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconMedium)
                    )
                    Text(
                        "Haptic Studio",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.hapticActive) ColorOnPrimary else ColorOnSurface60
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        "Open Haptic Studio",
                        tint = if (state.hapticActive) ColorOnPrimary else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconSmall)
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = state.hapticActive,
                        onCheckedChange = onToggleHaptics,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorSurface,
                            checkedTrackColor = ColorOnSurface,
                            uncheckedThumbColor = ColorOnSurface60,
                            uncheckedTrackColor = ColorBackground
                        )
                    )
                }
            }

            Spacer(Modifier.height(if (isShortScreen) Spacing.md else Spacing.xl))
        }

        // ─── Floating DND toast ─────────────────────────────────
        // Overlays the header gap instead of pushing the layout down.
        androidx.compose.animation.AnimatedVisibility(
            visible = dndToastVisible,
            enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = ComponentSize.topBarHeight + Spacing.sm, start = Spacing.md, end = Spacing.md)
        ) {
            DndWarningBanner(onDismiss = { dndToastVisible = false })
        }

        // Queue Bottom Sheet — shows the live play order, editable
        if (showQueue) {
            val queue = state.queue.ifEmpty { state.songs }
            QueueBottomSheet(
                songs = queue,
                currentSong = state.currentSong,
                isPlaying = state.isPlaying,
                onSongSelected = { index ->
                    if (state.queue.isNotEmpty()) {
                        onQueueAction(HaptiqUiAction.PlayQueueIndex(index))
                    } else {
                        onSongSelected(state.songs, index)
                    }
                    showQueue = false
                },
                onPlayNext = { index -> onQueueAction(HaptiqUiAction.PlaySongNext(index)) },
                onMove = { from, to -> onQueueAction(HaptiqUiAction.MoveInQueue(from, to)) },
                onRemove = { index -> onQueueAction(HaptiqUiAction.RemoveFromQueue(index)) },
                onDismiss = { showQueue = false }
            )
        }
    }
}

/**
 * Placeholder for the seek bar while the track's bass envelope is still decoding.
 * A row of low, equal bars with a bright band sweeping left→right — the universal
 * "computing" gesture — so users read it as loading, not a broken/empty slider.
 */
@Composable
private fun WaveformRenderingShimmer() {
    // Reduced motion: a static mid-sweep frame — still reads as "computing",
    // just without the travelling band.
    val sweep = if (isReducedMotionEnabled()) 0.5f else {
        val transition = rememberInfiniteTransition(label = "waveform_shimmer")
        val s by transition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweep"
        )
        s
    }
    val base = ColorOutlineVariant
    val highlight = ColorKick
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val barW = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val bars = (size.width / (barW + gap)).toInt().coerceAtLeast(1)
        val restH = 6.dp.toPx()
        for (i in 0 until bars) {
            val pos = (i + 0.5f) / bars
            // Gaussian-ish falloff around the sweep centre = a soft moving glow
            val d = kotlin.math.abs(pos - sweep)
            val glow = (1f - (d / 0.18f)).coerceIn(0f, 1f)
            val h = restH + glow * (size.height - restH)
            drawRoundRect(
                color = androidx.compose.ui.graphics.lerp(base, highlight, glow),
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}
