package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
            .statusBarsPadding()
            .navigationBarsPadding()
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
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
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
                val isFav = currentSong.id in state.favoriteIds
                IconButton(onClick = { onToggleFavorite(currentSong.id) }, modifier = Modifier.size(ComponentSize.touchTarget)) {
                    Icon(
                        if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        if (isFav) "Remove from favorites" else "Add to favorites",
                        tint = if (isFav) ColorPrimary else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconLarge)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // ─── Seek Slider ────────────────────────────────────
            Slider(
                value = if (isDraggingSlider) sliderValue else state.playbackProgress,
                onValueChange = { isDraggingSlider = true; sliderValue = it },
                onValueChangeFinished = { isDraggingSlider = false; onSeek(sliderValue) },
                colors = SliderDefaults.colors(
                    thumbColor = ColorPrimary,
                    activeTrackColor = ColorPrimary,
                    inactiveTrackColor = ColorOutlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        colors = SliderDefaults.colors(thumbColor = ColorPrimary),
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.currentTimeText, style = MaterialTheme.typography.labelSmall, color = ColorOnSurface60)
                Text(state.remainingTimeText, style = MaterialTheme.typography.labelSmall, color = ColorOnSurface60)
            }

            Spacer(Modifier.weight(0.1f))

            // ─── Transport Controls ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(ComponentSize.touchTarget)) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.isShuffle) ColorPrimary else ColorOnSurface60, modifier = Modifier.size(ComponentSize.iconLarge))
                }

                // Prev / Play / Next
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    IconButton(onClick = onPrevClicked, modifier = Modifier.size(ComponentSize.touchTarget)) {
                        Icon(Icons.Default.SkipPrevious, "Previous", tint = ColorOnSurface, modifier = Modifier.size(36.dp))
                    }

                    // Play/Pause — large circular button
                    Box(
                        modifier = Modifier
                            .size(ComponentSize.playPauseFAB)
                            .shadow(Elevation.medium, CircleShape)
                            .background(ColorPrimary, CircleShape)
                            .clickable(onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play or Pause",
                            tint = ColorOnPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = onNextClicked, modifier = Modifier.size(ComponentSize.touchTarget)) {
                        Icon(Icons.Default.SkipNext, "Next", tint = ColorOnSurface, modifier = Modifier.size(36.dp))
                    }
                }

                // Repeat
                IconButton(onClick = onToggleRepeat, modifier = Modifier.size(ComponentSize.touchTarget)) {
                    Icon(Icons.Default.Repeat, "Repeat", tint = if (state.isRepeat) ColorPrimary else ColorOnSurface60, modifier = Modifier.size(ComponentSize.iconLarge))
                }
            }

            Spacer(Modifier.weight(0.15f))

            // ─── Haptic Toggle Row ──────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).clickable { onToggleHaptics(!state.hapticActive) },
                color = if (state.hapticActive) ColorHapticAccent.copy(alpha = 0.1f) else ColorSurfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(Radius.md)
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
