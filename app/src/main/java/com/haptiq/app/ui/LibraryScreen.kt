package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlin.math.abs
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onToggleFavorite: (String) -> Unit = {},
    onSortModeChanged: (SortMode) -> Unit = {},
    onAction: (HaptiqUiAction) -> Unit = {}
) {
    // Which song the add-to-playlist dialog is open for (null = closed)
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    val filteredSongs = remember(state.songs, state.searchQuery, state.sortMode) {
        val matched = if (state.searchQuery.isEmpty()) state.songs
        else state.songs.filter {
            it.title.contains(state.searchQuery, ignoreCase = true) ||
                    it.artist.contains(state.searchQuery, ignoreCase = true)
        }
        when (state.sortMode) {
            SortMode.TITLE -> matched.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> matched.sortedBy { it.artist.lowercase() }
            SortMode.DURATION -> matched.sortedByDescending { it.durationSeconds }
        }
    }
    var showSortMenu by remember { mutableStateOf(false) }

    // Show skeleton while scanning and no songs loaded yet
    val showSkeleton = state.isScanning && state.songs.isEmpty()

    // Search starts collapsed to a header icon; expanding is an explicit act.
    // Stays open while a query is active so results never render "headless".
    var searchExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .testTag("library_screen")
    ) {
        // ─── Header (shared treatment across all tabs) ──────────
        HaptiqScreenHeader(
            subtitle = "Library",
            modifier = Modifier.padding(horizontal = Layout.screenHorizontalPadding),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // Sort menu
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.background(ColorSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Sort tracks", tint = ColorOnSurface)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = ColorSurfaceVariant
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        mode.label,
                                        color = if (mode == state.sortMode) ColorPrimary else ColorOnSurface
                                    )
                                },
                                onClick = {
                                    onSortModeChanged(mode)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
                // Search toggle — collapsing search to an icon keeps the default
                // screen calm; the field slides open only when asked for.
                IconButton(
                    onClick = {
                        if (searchExpanded) onSearchQueryChanged("")
                        searchExpanded = !searchExpanded
                    },
                    modifier = Modifier.background(
                        if (searchExpanded) ColorSurfaceVariant else ColorSurface, CircleShape
                    )
                ) {
                    Icon(
                        if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchExpanded) "Close search" else "Search library",
                        tint = ColorOnSurface
                    )
                }
                // Refresh/Scan button
                IconButton(
                    onClick = onScanDevice,
                    enabled = !state.isScanning,
                    modifier = Modifier.background(ColorSurface, CircleShape)
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
                            tint = ColorOnSurface
                        )
                    }
                }
                }
            }
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
            // ─── Sliding Search Bar ──────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = searchExpanded || state.searchQuery.isNotEmpty(),
                enter = androidx.compose.animation.expandVertically(animationSpec = HaptiqMotion.standardSpring()) +
                    androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() +
                    androidx.compose.animation.fadeOut()
            ) {
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
                shape = ExpressiveShapes.navPill,
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
            }

            Spacer(Modifier.height(Layout.sectionGap))

            // ─── Scrollable Content ──────────────────────────────
            val listState = rememberLazyListState()
            val scrollScope = rememberCoroutineScope()
            val showRecents = state.searchQuery.isEmpty() && state.recentSongs.isNotEmpty()
            // Items that precede the track rows in the LazyColumn — the alphabet
            // rail needs this offset to land scrolls on the right song.
            val headerItemCount = (if (showRecents) 3 else 0) +
                (if (filteredSongs.isNotEmpty()) 1 else 0)
            // First track-list index for each leading letter (A–Z, '#' for digits).
            // LinkedHashMap preserves list order, so keys come out already sorted.
            val letterIndexMap = remember(filteredSongs, state.sortMode) {
                val map = linkedMapOf<Char, Int>()
                filteredSongs.forEachIndexed { index, song ->
                    val source = if (state.sortMode == SortMode.ARTIST) song.artist else song.title
                    val key = source.firstOrNull { !it.isWhitespace() }
                        ?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'
                    if (key !in map) map[key] = index
                }
                map
            }
            val showRail = filteredSongs.size > 50 && state.sortMode != SortMode.DURATION

            // Gestures are now taught by the first-run GestureTutorialOverlay
            // (shown once when the library first has songs), so the old peek-nudge
            // that briefly slid the first row open has been removed.
            val tutorialContext = androidx.compose.ui.platform.LocalContext.current
            var showTutorial by remember {
                mutableStateOf(
                    tutorialContext
                        .getSharedPreferences("haptiq_prefs", Context.MODE_PRIVATE)
                        .getBoolean("gesture_tutorial_shown", false)
                        .not()
                )
            }

            Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Recently Played — real play history from Room, newest first.
                // (Previously rendered state.songs.take(5): the first five scan
                // results, which had nothing to do with what was played.)
                if (showRecents) {
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
                            val recents = state.recentSongs.take(10)
                            itemsIndexed(recents, key = { _, song -> song.id }) { _, song ->
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
                                countLabel(filteredSongs.size, "song"),
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorOnSurface60
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                    }
                }

                // Track list — swipe a row right to queue it as "play next"
                if (filteredSongs.isNotEmpty()) {
                    itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                        val isCurrent = state.currentSong?.id == song.id
                        SwipeToQueueRow(
                            song = song,
                            onEnqueueNext = { onAction(HaptiqUiAction.EnqueueNext(song)) }
                        ) {
                            TrackRow(
                                song = song,
                                isCurrentPlaying = isCurrent,
                                isPlaying = state.isPlaying && isCurrent,
                                isFavorite = song.id in state.favoriteIds,
                                onClick = { onSongSelected(filteredSongs, index) },
                                onToggleFavorite = { onToggleFavorite(song.id) },
                                onAddToPlaylist = { songForPlaylist = song }
                            )
                        }
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
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No results",
                            subtitle = "Nothing matches \"${state.searchQuery}\""
                        )
                    }
                }

                // Bottom spacer for breathing room above MiniPlayer
                item { Spacer(Modifier.height(Spacing.xxl)) }
            }

            // ─── A–Z fast-scroll rail ────────────────────────────
            // Only for big, alphabetically sorted lists — on duration sort the
            // letters would be meaningless jump targets.
            if (showRail) {
                AlphabetRail(
                    letters = letterIndexMap.keys.toList(),
                    onLetterSelected = { letter ->
                        letterIndexMap[letter]?.let { songIndex ->
                            scrollScope.launch {
                                listState.scrollToItem(headerItemCount + songIndex)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            // First-run gesture tutorial — overlays the library once, teaches the
            // swipe/skip/pull-down gestures, and celebrates the songs we found.
            if (showTutorial && state.songs.isNotEmpty()) {
                GestureTutorialOverlay(
                    songCount = state.songs.size,
                    onFinish = {
                        tutorialContext
                            .getSharedPreferences("haptiq_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("gesture_tutorial_shown", true).apply()
                        showTutorial = false
                    }
                )
            }
            }
        }
        }
    }

    songForPlaylist?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = state.playlists,
            onAction = onAction,
            onDismiss = { songForPlaylist = null }
        )
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
                cornerRadius = Radius.md,
                fallbackLabel = song.title
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
        Text(song.title, style = MaterialTheme.typography.labelLarge, color = ColorOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_bars")

    // Flat list rows: only the playing track gets a container. A card+shadow on
    // every row made the list read as a stack of competing surfaces instead of
    // a scannable list, and left the real signal (what's playing) with nothing
    // visually distinct to say it with.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (isCurrentPlaying) ColorSurfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            // Tighter rows: 44dp art + 8dp vertical padding ≈ 60dp tall vs. the old
            // ~80dp, so a long library scrolls in far fewer swipes.
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radius.sm)), contentAlignment = Alignment.Center) {
            ArtworkImage(song.artworkUrl, null, Modifier.fillMaxSize(), iconSize = ComponentSize.iconSmall, cornerRadius = Radius.sm, fallbackLabel = song.title)
            if (isCurrentPlaying && isPlaying) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(18.dp)) {
                        BouncingEqualizerBar(infiniteTransition, 0, 14)
                        BouncingEqualizerBar(infiniteTransition, 200, 10)
                        BouncingEqualizerBar(infiniteTransition, 400, 12)
                    }
                }
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            // The playing row already announces itself via the container highlight and
            // the equalizer overlay on its artwork — a third and fourth signal (clay
            // title + indicator bar that read as a stray "|") were pure noise.
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = ColorOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
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
        if (onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(ComponentSize.touchTarget)) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    "Add to playlist",
                    tint = ColorOnSurface60,
                    modifier = Modifier.size(ComponentSize.iconMedium)
                )
            }
        }
    }
}

// ─── A–Z fast-scroll rail ───────────────────────────────────
/**
 * Vertical letter strip for jumping through a long, sorted list. Tap or drag —
 * each new letter under the finger ticks the haptics and fires [onLetterSelected].
 * Only letters actually present in the list are shown, so every touch lands.
 */
@Composable
private fun AlphabetRail(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var railHeightPx by remember { mutableStateOf(0) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    fun selectAt(y: Float) {
        if (railHeightPx == 0 || letters.isEmpty()) return
        val index = ((y / railHeightPx) * letters.size).toInt().coerceIn(0, letters.size - 1)
        val letter = letters[index]
        if (letter != activeLetter) {
            activeLetter = letter
            haptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
            )
            onLetterSelected(letter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight(0.85f)
            .width(24.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(ColorSurface.copy(alpha = 0.6f))
            .onSizeChanged { railHeightPx = it.height }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { selectAt(it.y) },
                    onVerticalDrag = { change, _ -> selectAt(change.position.y) },
                    onDragEnd = { activeLetter = null },
                    onDragCancel = { activeLetter = null }
                )
            }
            .pointerInput(letters) {
                detectTapGestures(
                    onPress = { offset ->
                        selectAt(offset.y)
                        tryAwaitRelease()
                        activeLetter = null
                    }
                )
            }
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (letter == activeLetter) ColorHapticAccent else ColorOnSurface60
            )
        }
    }
}

// ─── Swipe-to-Queue wrapper ─────────────────────────────────
/**
 * Swipe a row from the left edge to enqueue it right after the current song.
 * The row never dismisses — it springs back, leaving the queue changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToQueueRow(
    song: Song,
    onEnqueueNext: () -> Unit,
    /** Extra X offset (px) for the row content — drives the one-time discovery
     *  nudge that peeks the "Play next" layer without a real swipe. */
    contentOffsetX: () -> Float = { 0f },
    content: @Composable () -> Unit
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // After a successful swipe the card flashes a "Playing next" confirmation over
    // itself, then reverts — so the gesture visibly *did something* rather than just
    // snapping back with no acknowledgement.
    var justQueued by remember { mutableStateOf(false) }
    LaunchedEffect(justQueued) {
        if (justQueued) {
            kotlinx.coroutines.delay(1300)
            justQueued = false
        }
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onEnqueueNext()
                justQueued = true
            }
            false // snap back; the action is queueing, not removing the row
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(ColorHapticAccent.copy(alpha = 0.15f))
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.QueuePlayNext,
                    contentDescription = "Play next",
                    tint = ColorHapticAccent
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "Play next",
                    style = MaterialTheme.typography.labelLarge,
                    color = ColorHapticAccent
                )
            }
        },
        content = {
            // Opaque backing is load-bearing: TrackRow's resting background is
            // transparent, and SwipeToDismissBox always composes backgroundContent
            // behind the row — without this, "Play next" bleeds through under the
            // artwork and title. Opaque here keeps it hidden until actually swiped.
            Box(
                Modifier
                    .graphicsLayer { translationX = contentOffsetX() }
                    .background(ColorBackground)
            ) {
                content()
                // Confirmation overlay: an amber sheet slides in over the card,
                // holds ~1.3s, then fades to reveal the normal row again.
                androidx.compose.animation.AnimatedVisibility(
                    visible = justQueued,
                    enter = androidx.compose.animation.slideInHorizontally { -it } +
                        androidx.compose.animation.fadeIn(tween(180)),
                    exit = androidx.compose.animation.fadeOut(tween(220)),
                    modifier = Modifier.matchParentSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(ColorHapticAccent)
                            .padding(horizontal = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = ColorOnPrimary,
                            modifier = Modifier.size(ComponentSize.iconMedium)
                        )
                        Text(
                            "Playing next",
                            style = MaterialTheme.typography.labelLarge,
                            color = ColorOnPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
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
    onExpand: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    if (currentSong == null) return

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Horizontal swipe = skip track; the strip follows the finger with resistance
    val swipeOffsetX = remember { Animatable(0f) }

    val infinitePulse = rememberInfiniteTransition(label = "glow_pulse")
    val dotAlpha by infinitePulse.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.md, bottom = 4.dp)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(Radius.lg))
            .clip(RoundedCornerShape(Radius.lg))
            .border(width = 1.dp, color = ColorOutlineVariant.copy(alpha = 0.1f), shape = RoundedCornerShape(Radius.lg))
            .clickable(onClick = onExpand)
            // One drag gesture, one dominant axis — never both. Two separate pointerInput
            // blocks (a vertical and a horizontal detector) both received the same pointer
            // stream, so a downward "close" swipe with any sideways drift fired onDismiss
            // AND onNext; onNext then re-queued a track after dismiss cleared it, leaving
            // audio playing with no visible player. Deciding the axis at drag-end from the
            // larger total displacement guarantees exactly one action fires.
            //   vertical: up = open full player, down = dismiss playback
            //   horizontal: left = next, right = previous
            .pointerInput(Unit) {
                val skipThreshold = 88.dp.toPx()
                val dismissThreshold = 64.dp.toPx()
                val expandThreshold = 40.dp.toPx()
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = { totalX = 0f; totalY = 0f },
                    onDrag = { change, dragAmount ->
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        // Follow the finger sideways only while the gesture reads as
                        // horizontal, so a vertical dismiss doesn't shift the strip.
                        if (abs(totalX) > abs(totalY)) {
                            scope.launch { swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount.x * 0.6f) }
                        }
                        change.consume()
                    },
                    onDragCancel = {
                        scope.launch { swipeOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                    },
                    onDragEnd = {
                        if (abs(totalY) > abs(totalX)) {
                            if (totalY < -expandThreshold) {
                                onExpand()
                            } else if (totalY > dismissThreshold) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            }
                        } else {
                            val settled = swipeOffsetX.value
                            if (settled < -skipThreshold) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNext()
                            } else if (settled > skipThreshold) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPrev()
                            }
                        }
                        scope.launch {
                            swipeOffsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
                        }
                    }
                )
            }
            .testTag("mini_player"),
        color = ColorSurface,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.miniPlayerHeight)
                    // Content follows the horizontal swipe; the progress bar below stays put
                    .graphicsLayer { translationX = swipeOffsetX.value }
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    currentSong.artworkUrl,
                    null,
                    Modifier.size(ComponentSize.artworkMedium),
                    iconSize = ComponentSize.iconMedium,
                    cornerRadius = Radius.sm,
                    fallbackLabel = currentSong.title
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        currentSong.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = ColorOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hapticActive) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ColorHapticAccent.copy(alpha = dotAlpha))
                            )
                            Spacer(Modifier.width(Spacing.xxs))
                            Text(
                                "Haptic Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorHapticAccent,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                currentSong.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorOnSurface60,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier.size(ComponentSize.touchTarget)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            "Previous",
                            tint = ColorOnSurface,
                            modifier = Modifier.size(ComponentSize.iconLarge)
                        )
                    }
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(ComponentSize.touchTarget)
                            .clip(CircleShape)
                            .background(ColorSurfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = ColorOnSurface,
                            modifier = Modifier.size(ComponentSize.iconMedium)
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(ComponentSize.touchTarget)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            "Next",
                            tint = ColorOnSurface,
                            modifier = Modifier.size(ComponentSize.iconLarge)
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = ColorHapticAccent.copy(alpha = 0.8f),
                trackColor = ColorSurfaceVariant.copy(alpha = 0.3f),
            )
        }
    }
}
