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
                        .size(44.dp)
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

                // Settings/Studio button in a circular capsule
                IconButton(
                    onClick = onHapticStudioClicked,
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(Elevation.low, CircleShape)
                        .background(ColorSurface, CircleShape)
                        .border(1.dp, ColorOutline, CircleShape)
                ) {
                    Icon(Icons.Default.Settings, "Tuning Studio", tint = ColorOnSurface, modifier = Modifier.size(20.dp))
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
                    .shadow(Elevation.hero, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
            ) {
                ArtworkImage(
                    model = currentSong.artworkUrl,
                    contentDescription = "Album Artwork",
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 64.dp,
                    cornerRadius = 24.dp
                )
            }

            Spacer(Modifier.weight(0.15f))

            // ─── Centered Track Info ────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentSong.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorHapticAccent, // Orange title
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "by ${currentSong.artist}",
                    fontSize = 14.sp,
                    color = ColorOnSurface60,
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
                IconButton(onClick = { onToggleFavorite(currentSong.id) }) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) ColorPrimary else ColorOnSurface60,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                // Save/Download icon
                IconButton(onClick = { /* Save/Download */ }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Download",
                        tint = ColorOnSurface60,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                // Share icon
                IconButton(onClick = { /* Share */ }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = ColorOnSurface60,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.weight(0.2f))

            // ─── Linear Seek Slider ─────────────────────────────
            var sliderValue by remember(state.playbackProgress) { mutableStateOf(state.playbackProgress) }
            var isDraggingSlider by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (isDraggingSlider) sliderValue else state.playbackProgress,
                    onValueChange = { isDraggingSlider = true; sliderValue = it },
                    onValueChangeFinished = { isDraggingSlider = false; onSeek(sliderValue) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = ColorHapticAccent,
                        inactiveTrackColor = ColorOutlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            colors = SliderDefaults.colors(thumbColor = Color.White),
                            modifier = Modifier
                                .size(16.dp)
                                .shadow(Elevation.low, CircleShape)
                                .border(1.dp, ColorOutline, CircleShape)
                        )
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

            Spacer(Modifier.weight(0.3f))

            // ─── Transport Controls ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Queue Button
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = ColorOnSurface60,
                        modifier = Modifier.size(24.dp)
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

                // Play/Pause circular FAB with soft orange shadow
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(Elevation.medium, CircleShape, ambientColor = ColorHapticAccent, spotColor = ColorHapticAccent)
                        .background(ColorHapticAccent, CircleShape)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
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

                // Repeat
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (state.isRepeat) ColorHapticAccent else ColorOnSurface60,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.weight(0.3f))

            // ─── Haptic Toggle Row ──────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable { onToggleHaptics(!state.hapticActive) },
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
                            uncheckedTrackColor = ColorBackground
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
