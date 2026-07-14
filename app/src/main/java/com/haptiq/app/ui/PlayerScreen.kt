package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: HaptiqUiState,
    onBackClicked: () -> Unit,
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
    onToggleFavorite: (String) -> Unit = {}
) {
    val currentSong = state.currentSong ?: return
    val isFav = currentSong.id in state.favoriteIds
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) { onRefreshDndStatus() }

    var sliderValue by remember(state.playbackProgress) { mutableStateOf(state.playbackProgress) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // Sheet physics: the whole screen tracks a downward drag and either commits
    // to dismissing (past the threshold) or springs back. Matches the slide-up
    // entrance — Now Playing behaves like a drawer, not a page.
    val dragOffset = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 160.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffset.value
                alpha = 1f - (dragOffset.value / (dismissThresholdPx * 4f)).coerceIn(0f, 0.25f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        val next = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                        if (next > 0f) change.consume()
                        dragScope.launch { dragOffset.snapTo(next) }
                    },
                    onDragEnd = {
                        if (dragOffset.value > dismissThresholdPx) {
                            onBackClicked()
                        } else {
                            dragScope.launch {
                                dragOffset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                            }
                        }
                    },
                    onDragCancel = {
                        dragScope.launch { dragOffset.animateTo(0f) }
                    }
                )
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(ColorSurface, ColorBackground),
                    radius = 1200f
                )
            )
            .systemBarsPadding()
            .testTag("player_screen")
    ) {
        val screenHeight = maxHeight
        val isShortScreen = screenHeight < 680.dp

        // Ambient glow behind artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isShortScreen) 220.dp else 350.dp)
                .blur(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ColorHapticAccent.copy(alpha = 0.06f), Color.Transparent)
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
            // ─── Top Bar ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.topBarHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button in a circular outline capsule
                IconButton(
                    onClick = onBackClicked,
                    modifier = Modifier
                        .size(ComponentSize.touchTarget)
                        .shadow(Elevation.low, CircleShape)
                        .background(ColorSurface, CircleShape)
                        .border(1.dp, ColorOutline, CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, "Minimize", tint = ColorOnSurface, modifier = Modifier.size(20.dp))
                }

                Text(
                    text = "NOW PLAYING",
                    fontSize = 11.sp,
                    color = ColorOnSurface60,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                // Invisible twin of the back button so "NOW PLAYING" stays centered.
                // Haptic Studio is reached through the haptic pill below — a gear up
                // here read as generic app settings, not the tuning dashboard.
                Spacer(Modifier.size(ComponentSize.touchTarget))
            }

            // DND Warning
            if (state.isDndActive && state.hapticActive) {
                DndWarningBanner()
                Spacer(Modifier.height(Spacing.sm))
            }

            Spacer(Modifier.weight(if (isShortScreen) 0.05f else 0.15f))

            // ─── Artwork ────────────────────────────────────────
            val artworkModifier = if (isShortScreen) {
                Modifier.size(180.dp)
            } else {
                Modifier.fillMaxWidth().aspectRatio(1f)
            }
            Box(
                modifier = artworkModifier
                    .shadow(Elevation.hero, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
            ) {
                ArtworkImage(
                    model = currentSong.artworkUrl,
                    contentDescription = "Album Artwork",
                    modifier = Modifier.fillMaxSize(),
                    iconSize = if (isShortScreen) 48.dp else 64.dp,
                    cornerRadius = 24.dp
                )
            }

            Spacer(Modifier.weight(if (isShortScreen) 0.05f else 0.15f))

            // ─── Centered Track Info ────────────────────────────
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
                // Queue
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = ColorOnSurface60,
                        modifier = Modifier.size(22.dp)
                    )
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
                        SliderDefaults.Thumb(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            colors = SliderDefaults.colors(thumbColor = Color.White),
                            modifier = Modifier
                                .size(20.dp)
                                .shadow(Elevation.medium, CircleShape)
                                .border(2.dp, ColorPrimary, CircleShape)
                        )
                    },
                    track = { sliderState ->
                        if (seekEnergy != null && seekEnergy.isNotEmpty()) {
                            // Amber only while the envelope is live haptic data;
                            // with haptics off it reverts to the neutral brand clay.
                            val playedColor = if (state.hapticActive) ColorHapticAccent else ColorPrimary
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
                            }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(state.currentTimeText, style = MaterialTheme.typography.labelSmall, color = ColorOnSurface60)
                    Text(state.remainingTimeText, style = MaterialTheme.typography.labelSmall, color = ColorOnSurface60)
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
                        tint = if (state.isShuffle) ColorHapticAccent else ColorOnSurface60,
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

                // Play/Pause circular FAB with premium shadow
                Box(
                    modifier = Modifier
                        .size(if (isShortScreen) 60.dp else 68.dp)
                        .shadow(Elevation.high, CircleShape, ambientColor = ColorPrimary, spotColor = ColorPrimary)
                        .background(ColorPrimary, CircleShape)
                        .clickable(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTogglePlayPause()
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause",
                        tint = ColorOnPrimary,
                        modifier = Modifier.size(if (isShortScreen) 30.dp else 34.dp)
                    )
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
                            ColorHapticAccent
                        } else ColorOnSurface60,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.weight(if (isShortScreen) 0.1f else 0.3f))

            // ─── Haptic Engine Row ──────────────────────────────
            // Two controls, two clear jobs: tapping the row opens the Haptic Studio
            // (label + chevron = navigation); the switch — and only the switch —
            // toggles haptics on/off.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(onClick = onHapticStudioClicked),
                color = if (state.hapticActive) ColorHapticAccent.copy(alpha = 0.12f) else ColorOutline.copy(alpha = 0.3f),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Vibration,
                        null,
                        tint = if (state.hapticActive) ColorHapticAccent else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconMedium)
                    )
                    Text(
                        "Haptic Studio",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.hapticActive) ColorHapticAccent else ColorOnSurface60
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        "Open Haptic Studio",
                        tint = if (state.hapticActive) ColorHapticAccent else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconSmall)
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = state.hapticActive,
                        onCheckedChange = onToggleHaptics,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorSurface,
                            checkedTrackColor = ColorHapticAccent,
                            uncheckedThumbColor = ColorOnSurface60,
                            uncheckedTrackColor = ColorBackground
                        )
                    )
                }
            }

            Spacer(Modifier.height(if (isShortScreen) Spacing.md else Spacing.xl))
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
