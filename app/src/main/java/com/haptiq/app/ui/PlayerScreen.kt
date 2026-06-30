package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: HaptiqUiState,
    onBackClicked: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextClicked: () -> Unit,
    onPrevClicked: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onHapticStudioClicked: () -> Unit,
    onSongSelected: (List<Song>, Int) -> Unit,
    onRefreshDndStatus: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {}
) {
    val currentSong = state.currentSong ?: return
    val isFav = currentSong.id in state.favoriteIds

    LaunchedEffect(Unit) { onRefreshDndStatus() }

    var sliderValue by remember(state.playbackProgress) { mutableStateOf(state.playbackProgress) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(ColorSurface, ColorBackground),
                    radius = 1200f
                )
            )
            .systemBarsPadding()
            .testTag("player_screen")
    ) {
        // Ambient glow behind artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
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
                modifier = Modifier.fillMaxWidth().height(ComponentSize.topBarHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClicked, modifier = Modifier.size(ComponentSize.touchTarget)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Minimize", tint = ColorOnSurface, modifier = Modifier.size(ComponentSize.iconXL))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", fontSize = 10.sp, color = ColorOnSurface60, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                IconButton(onClick = { showQueue = true }, modifier = Modifier.size(ComponentSize.touchTarget)) {
                    Icon(Icons.Default.QueueMusic, "Queue", tint = ColorOnSurface)
                }
            }

            // DND Warning
            if (state.isDndActive && state.hapticActive) {
                DndWarningBanner()
                Spacer(Modifier.height(Spacing.sm))
            }

            Spacer(Modifier.weight(0.15f))

            // ─── Artwork ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(Elevation.hero, RoundedCornerShape(Radius.lg))
                    .clip(RoundedCornerShape(Radius.lg))
            ) {
                ArtworkImage(
                    model = currentSong.artworkUrl,
                    contentDescription = "Album Artwork",
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 64.dp,
                    cornerRadius = Radius.lg
                )
                // Subtle glare
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)
                            )
                        )
                )
            }

            Spacer(Modifier.weight(0.15f))

            // ─── Track Info + Favorite ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        currentSong.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = ColorOnSurface,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        currentSong.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ColorOnSurface60,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onToggleFavorite(currentSong.id) }, modifier = Modifier.size(ComponentSize.touchTarget)) {
                    Icon(
                        if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        if (isFav) "Remove from favorites" else "Add to favorites",
                        tint = if (isFav) ColorPrimary else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconLarge)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ─── Core Circular Playback Console (TuneHive style) ───
            var isDraggingSlider by remember { mutableStateOf(false) }
            var dragProgress by remember { mutableStateOf(0f) }
            val currentProgress = if (isDraggingSlider) dragProgress else state.playbackProgress

            val infiniteTransition = rememberInfiniteTransition(label = "visualizer_rings")
            val pulseScale1 by infiniteTransition.animateFloat(
                initialValue = 1.0f, targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "pulse1"
            )
            val pulseScale2 by infiniteTransition.animateFloat(
                initialValue = 1.0f, targetValue = 1.12f,
                animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "pulse2"
            )

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDraggingSlider = true },
                            onDragEnd = {
                                isDraggingSlider = false
                                onSeek(dragProgress)
                            },
                            onDragCancel = { isDraggingSlider = false },
                            onDrag = { change, _ ->
                                val size = size
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val touch = change.position

                                val dx = touch.x - center.x
                                val dy = touch.y - center.y
                                val angleRad = kotlin.math.atan2(dy, dx)
                                var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                                if (angleDeg < 0) angleDeg += 360f

                                // Map 135..405 deg to progress 0..1
                                var relativeAngle = angleDeg - 135f
                                if (relativeAngle < 0) relativeAngle += 360f

                                if (relativeAngle <= 270f) {
                                    val newProgress = (relativeAngle / 270f).coerceIn(0f, 1f)
                                    dragProgress = newProgress
                                    change.consume()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background & Active Arcs + Thumb
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidthPx = 6.dp.toPx()
                    val dialRadius = 110.dp.toPx()

                    // 1. Draw outer background track arc (gap at bottom: 45 to 135 deg)
                    drawArc(
                        color = ColorOutlineVariant,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                        size = Size(dialRadius * 2, dialRadius * 2),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )

                    // 2. Draw active progress track arc
                    drawArc(
                        color = ColorHapticAccent,
                        startAngle = 135f,
                        sweepAngle = currentProgress * 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                        size = Size(dialRadius * 2, dialRadius * 2),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )

                    // 3. Draw thumb knob dot
                    val thumbAngleDeg = 135f + currentProgress * 270f
                    val thumbAngleRad = Math.toRadians(thumbAngleDeg.toDouble())
                    val thumbX = center.x + dialRadius * kotlin.math.cos(thumbAngleRad).toFloat()
                    val thumbY = center.y + dialRadius * kotlin.math.sin(thumbAngleRad).toFloat()
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(thumbX, thumbY)
                    )

                    // 4. Draw concentric acoustic visualization circles
                    val scale1 = if (state.isPlaying) pulseScale1 else 1.0f
                    val scale2 = if (state.isPlaying) pulseScale2 else 1.0f

                    drawCircle(
                        color = ColorHapticAccent.copy(alpha = 0.12f),
                        radius = 48.dp.toPx() * scale1,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = ColorHapticAccent.copy(alpha = 0.06f),
                        radius = 60.dp.toPx() * scale2,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Play/Pause circular FAB in the center
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(Elevation.medium, CircleShape)
                        .background(Color.White, CircleShape)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Heart Favorite button above play
                IconButton(
                    onClick = { onToggleFavorite(currentSong.id) },
                    modifier = Modifier.offset(y = (-62).dp)
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) ColorPrimary else ColorOnSurface60,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Time progress text
                Text(
                    text = "${state.currentTimeText} / ${state.remainingTimeText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    modifier = Modifier.offset(y = (-24).dp)
                )

                // Shuffle button (top-left shoulder)
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.offset(x = (-72).dp, y = (-72).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.isShuffle) ColorPrimary else ColorOnSurface60,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Repeat button (top-right shoulder)
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier.offset(x = 72.dp, y = (-72).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (state.isRepeat) ColorPrimary else ColorOnSurface60,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Skip Previous (bottom-left)
                IconButton(
                    onClick = onPrevClicked,
                    modifier = Modifier.offset(x = (-105).dp, y = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = ColorOnSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Skip Next (bottom-right)
                IconButton(
                    onClick = onNextClicked,
                    modifier = Modifier.offset(x = 105.dp, y = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = ColorOnSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ─── Haptic Toggle Row ──────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable { onToggleHaptics(!state.hapticActive) },
                color = if (state.hapticActive) ColorHapticAccent.copy(alpha = 0.1f) else ColorSurfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Vibration,
                        "Haptic",
                        tint = if (state.hapticActive) ColorHapticAccent else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconMedium)
                    )
                    Text(
                        "Haptic Feedback",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.hapticActive) ColorHapticAccent else ColorOnSurface60,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.hapticActive,
                        onCheckedChange = onToggleHaptics,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorSurface,
                            checkedTrackColor = ColorHapticAccent,
                            uncheckedThumbColor = ColorOnSurface60,
                            uncheckedTrackColor = ColorSurfaceVariant
                        )
                    )
                    IconButton(onClick = onHapticStudioClicked, modifier = Modifier.size(ComponentSize.touchTarget)) {
                        Icon(Icons.Default.ChevronRight, "Open Haptic Studio", tint = ColorOnSurface60)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }

        // Queue Bottom Sheet
        if (showQueue) {
            QueueBottomSheet(
                songs = state.songs,
                currentSong = state.currentSong,
                isPlaying = state.isPlaying,
                onSongSelected = { index ->
                    onSongSelected(state.songs, index)
                    showQueue = false
                },
                onDismiss = { showQueue = false }
            )
        }
    }
}
