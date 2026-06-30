package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: HaptiqUiState,
    innerPadding: PaddingValues,
    onSongSelected: (List<Song>, Int) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onScanDevice: () -> Unit,
    onHapticStudioClicked: () -> Unit,
    onToggleFavorite: (String) -> Unit = {}
) {
    val filteredSongs = remember(state.songs, state.searchQuery) {
        if (state.searchQuery.isEmpty()) state.songs
        else state.songs.filter {
            it.title.contains(state.searchQuery, ignoreCase = true) ||
                    it.artist.contains(state.searchQuery, ignoreCase = true)
        }
    }

    // Show skeleton while scanning and no songs loaded yet
    val showSkeleton = state.isScanning && state.songs.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .testTag("library_screen")
    ) {
        // ─── HAPTIQ Top App Bar ─────────────────────────────────
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "HAPTIQ",
                        style = MaterialTheme.typography.titleLarge,
                        color = ColorPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorOnSurface60
                    )
                }
            },
            actions = {
                // Refresh/Scan button
                IconButton(
                    onClick = onScanDevice,
                    enabled = !state.isScanning
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ColorPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Scan for new music",
                            tint = ColorOnSurfaceVariant
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = ColorBackground
            )
        )

        // Scan progress bar
        if (state.isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = ColorPrimary,
                trackColor = ColorSurfaceVariant
            )
            // Scan status text
            if (state.scanStatus.isNotEmpty()) {
                Text(
                    text = state.scanStatus + if (state.scanProgress > 0) " (${state.scanProgress})" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    modifier = Modifier.padding(horizontal = Layout.screenHorizontalPadding, vertical = Spacing.xxs)
                )
            }
        }

        // ─── Content ────────────────────────────────────────────
        if (showSkeleton) {
            // Skeleton loading while first scan is in progress
            LibrarySkeleton()
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Layout.screenHorizontalPadding)
        ) {
            // ─── Inline Search Bar ───────────────────────────────
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Find in Library", color = ColorOnSurface60) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ColorOnSurface60) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, "Clear", tint = ColorOnSurface60)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.searchBarHeight)
                    .testTag("search_bar"),
                shape = RoundedCornerShape(Radius.pill),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ColorSurfaceVariant,
                    unfocusedContainerColor = ColorSurfaceVariant.copy(alpha = 0.5f),
                    focusedBorderColor = ColorHapticAccent,
                    unfocusedBorderColor = ColorOutlineVariant.copy(alpha = 0.3f),
                    cursorColor = ColorHapticAccent,
                    focusedTextColor = ColorOnSurface,
                    unfocusedTextColor = ColorOnSurface
                ),
                singleLine = true
            )

            Spacer(Modifier.height(Layout.sectionGap))

            // ─── Scrollable Content ──────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Recently Played (only when not searching)
                if (state.searchQuery.isEmpty() && state.songs.isNotEmpty()) {
                    item {
                        Text(
                            "Recently Played",
                            style = MaterialTheme.typography.titleMedium,
                            color = ColorOnSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            contentPadding = PaddingValues(bottom = Spacing.xs)
                        ) {
                            val recents = state.songs.take(5)
                            itemsIndexed(recents) { _, song ->
                                val isActive = state.currentSong?.id == song.id
                                MediaCard(
                                    song = song,
                                    isActiveHaptic = isActive && state.isPlaying,
                                    isFavorite = song.id in state.favoriteIds,
                                    onClick = {
                                        val idx = state.songs.indexOf(song)
                                        if (idx != -1) onSongSelected(state.songs, idx)
                                    },
                                    onToggleFavorite = { onToggleFavorite(song.id) }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Spacing.sm)) }
                }

                // All Tracks / Search Results header
                if (filteredSongs.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.searchQuery.isEmpty()) "All Tracks" else "Results for \"${state.searchQuery}\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = ColorOnSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${filteredSongs.size} songs",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorOnSurface60
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                    }
                }

                // Track list
                if (filteredSongs.isNotEmpty()) {
                    itemsIndexed(filteredSongs) { index, song ->
                        val isCurrent = state.currentSong?.id == song.id
                        TrackRow(
                            song = song,
                            isCurrentPlaying = isCurrent,
                            isPlaying = state.isPlaying && isCurrent,
                            isFavorite = song.id in state.favoriteIds,
                            onClick = { onSongSelected(filteredSongs, index) },
                            onToggleFavorite = { onToggleFavorite(song.id) }
                        )
                    }
                } else if (state.songs.isEmpty() && !state.isScanning) {
                    // Empty library — no device songs found
                    item {
                        LibraryEmptyState(
                            isScanning = state.isScanning,
                            scanError = state.scanError,
                            onScanDevice = onScanDevice
                        )
                    }
                } else if (state.searchQuery.isNotEmpty() && filteredSongs.isEmpty()) {
                    // No search results
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.xxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null, Modifier.size(ComponentSize.iconXXL), tint = ColorOnSurface60)
                                Spacer(Modifier.height(Spacing.md))
                                Text("No results", style = MaterialTheme.typography.titleMedium, color = ColorOnSurface)
                            }
                        }
                    }
                }

                // Bottom spacer for breathing room above MiniPlayer
                item { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
        }
    }
}

// ─── Empty State ────────────────────────────────────────────
@Composable
fun LibraryEmptyState(
    isScanning: Boolean,
    scanError: String?,
    onScanDevice: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(Spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(120.dp).background(ColorSurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LibraryMusic, null, Modifier.size(ComponentSize.iconHero), tint = ColorOnSurface60)
            }
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                "Your library is empty",
                style = MaterialTheme.typography.titleLarge,
                color = ColorOnSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Tap the refresh button above to scan your device for music",
                style = MaterialTheme.typography.bodyMedium,
                color = ColorOnSurface60,
                textAlign = TextAlign.Center
            )
            if (scanError != null) {
                Spacer(Modifier.height(Spacing.md))
                Text(scanError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(Spacing.huge))
            Button(
                onClick = onScanDevice,
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.height(ComponentSize.buttonHeight)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(Modifier.size(ComponentSize.iconMedium), color = ColorOnPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Scanning…", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(ComponentSize.iconMedium))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Scan Device", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─── Media Card ─────────────────────────────────────────────
@Composable
fun MediaCard(
    song: Song,
    isActiveHaptic: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "haptic_pulse")
    val pulseBorderWidthFactor by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 2.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse_border_width"
    )

    Column(
        modifier = Modifier
            .width(ComponentSize.artworkCard)
            .clickable(onClick = onClick)
            .testTag("media_card_${song.id}")
    ) {
        Box(
            modifier = Modifier
                .size(ComponentSize.artworkCard)
                .shadow(Elevation.high, RoundedCornerShape(Radius.md))
                .clip(RoundedCornerShape(Radius.md))
                .border(
                    width = if (isActiveHaptic) pulseBorderWidthFactor.dp else 0.dp,
                    color = if (isActiveHaptic) ColorHapticAccent else Color.Transparent,
                    shape = RoundedCornerShape(Radius.md)
                )
        ) {
            ArtworkImage(
                model = song.artworkUrl,
                contentDescription = "${song.title} cover",
                modifier = Modifier.fillMaxSize(),
                iconSize = ComponentSize.iconXXL,
                cornerRadius = Radius.md
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) ColorPrimary else Color.White,
                    modifier = Modifier.size(ComponentSize.iconSmall)
                )
            }
            if (isActiveHaptic) {
                Box(
                    modifier = Modifier
                        .padding(Spacing.xxs)
                        .size(Spacing.xxs)
                        .background(ColorHapticAccent, CircleShape)
                        .shadow(Elevation.low, CircleShape)
                        .align(Alignment.TopStart)
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = ColorHapticAccent, fontWeight = FontWeight.Bold)) {
                    append("| ")
                }
                append(song.title)
            },
            style = MaterialTheme.typography.labelLarge,
            color = ColorOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = ColorOnSurface60, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Track Row ──────────────────────────────────────────────
@Composable
fun TrackRow(
    song: Song,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_bars")
    var showDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (isCurrentPlaying) ColorHapticAccent.copy(alpha = 0.12f) else ColorSurface.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = when {
                    isCurrentPlaying && isPlaying -> "Now playing: ${song.title} by ${song.artist}"
                    isCurrentPlaying -> "Current track: ${song.title}, paused"
                    else -> "${song.title} by ${song.artist}"
                }
            }
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(ComponentSize.artworkSmall).clip(RoundedCornerShape(Radius.sm)), contentAlignment = Alignment.Center) {
            ArtworkImage(song.artworkUrl, null, Modifier.fillMaxSize(), iconSize = ComponentSize.iconSmall, cornerRadius = Radius.sm)
            if (isCurrentPlaying && isPlaying) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(ComponentSize.iconMedium)) {
                        BouncingEqualizerBar(infiniteTransition, 0, 16)
                        BouncingEqualizerBar(infiniteTransition, 200, 12)
                        BouncingEqualizerBar(infiniteTransition, 400, 14)
                    }
                }
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = ColorHapticAccent, fontWeight = FontWeight.Bold)) {
                        append("| ")
                    }
                    append(song.title)
                },
                style = MaterialTheme.typography.labelLarge,
                color = ColorOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = ColorOnSurface60, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(ComponentSize.touchTarget)) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) ColorPrimary else ColorOnSurface60,
                modifier = Modifier.size(ComponentSize.iconMedium)
            )
        }
        Box {
            IconButton(onClick = { showDropdown = true }, modifier = Modifier.size(ComponentSize.touchTarget)) {
                Icon(Icons.Default.MoreVert, "Options", tint = ColorOnSurface60)
            }
            DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, containerColor = ColorSurface) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Go to artist") }, onClick = { showDropdown = false })
            }
        }
    }
}

// ─── Equalizer Bar ──────────────────────────────────────────
@Composable
fun BouncingEqualizerBar(transition: InfiniteTransition, delayMillis: Int, targetHeight: Int) {
    val barHeight by transition.animateFloat(
        initialValue = 4f, targetValue = targetHeight.toFloat(),
        animationSpec = infiniteRepeatable(tween(600, delayMillis = delayMillis, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bar_height"
    )
    Box(Modifier.width(3.dp).height(barHeight.dp).background(ColorHapticAccent, RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)))
}

// ─── Mini Player ────────────────────────────────────────────
@Composable
fun MiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    progress: Float,
    hapticActive: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExpand: () -> Unit
) {
    if (currentSong == null) return

    val infinitePulse = rememberInfiniteTransition(label = "glow_pulse")
    val dotAlpha by infinitePulse.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md))
            .padding(bottom = 6.dp)
            .clickable(onClick = onExpand)
            .testTag("mini_player"),
        color = ColorSurface,
        tonalElevation = Elevation.low
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(ComponentSize.miniPlayerHeight).padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(currentSong.artworkUrl, null, Modifier.size(ComponentSize.artworkMedium), iconSize = ComponentSize.iconMedium, cornerRadius = Radius.sm)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(currentSong.title, style = MaterialTheme.typography.labelLarge, color = ColorOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hapticActive) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(ColorHapticAccent.copy(alpha = dotAlpha)))
                            Spacer(Modifier.width(Spacing.xxs))
                            Text("Haptic Active", style = MaterialTheme.typography.labelSmall, color = ColorHapticAccent, maxLines = 1)
                        } else {
                            Text(currentSong.artist, style = MaterialTheme.typography.labelSmall, color = ColorOnSurface60, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(ComponentSize.touchTarget)) {
                        Icon(Icons.Default.SkipPrevious, "Previous", tint = ColorOnSurface, modifier = Modifier.size(ComponentSize.iconLarge))
                    }
                    IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(ComponentSize.touchTarget).background(ColorSurfaceVariant, CircleShape)) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = ColorOnSurface, modifier = Modifier.size(ComponentSize.iconMedium))
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(ComponentSize.touchTarget)) {
                        Icon(Icons.Default.SkipNext, "Next", tint = ColorOnSurface, modifier = Modifier.size(ComponentSize.iconLarge))
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = ColorHapticAccent,
                trackColor = ColorSurfaceVariant,
            )
        }
    }
}
