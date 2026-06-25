package com.example.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Song
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: HaptiqUiState,
    onSongSelected: (List<Song>, Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextClicked: () -> Unit,
    onPrevClicked: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSettingsClicked: () -> Unit,
    onHapticStudioClicked: () -> Unit,
    onMenuClicked: () -> Unit,
    onMiniPlayerExpanded: () -> Unit
) {
    // Filter songs based on search query
    val filteredSongs = remember(state.songs, state.searchQuery) {
        if (state.searchQuery.isEmpty()) {
            state.songs
        } else {
            state.songs.filter {
                it.title.contains(state.searchQuery, ignoreCase = true) ||
                        it.artist.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_screen"),
        containerColor = ColorBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.titleLarge,
                        color = ColorPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClicked) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = ColorOnSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClicked) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = ColorOnSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ColorBackground
                )
            )
        },
        bottomBar = {
            Column {
                // Persistent Mini Player sits directly above the bottom navigation bar
                MiniPlayer(
                    currentSong = state.currentSong,
                    isPlaying = state.isPlaying,
                    progress = state.playbackProgress,
                    hapticActive = state.hapticActive,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNextClicked,
                    onPrev = onPrevClicked,
                    onExpand = onMiniPlayerExpanded
                )

                // Navigation Bar
                NavigationBar(
                    containerColor = ColorSurface,
                    modifier = Modifier.height(72.dp)
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library", style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = ColorOnSurface60,
                            unselectedTextColor = ColorOnSurface60,
                            selectedIconColor = ColorOnSurface,
                            selectedTextColor = ColorOnSurface,
                            indicatorColor = ColorSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onSettingsClicked,
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = ColorOnSurface60,
                            unselectedTextColor = ColorOnSurface60,
                            selectedIconColor = ColorOnSurface,
                            selectedTextColor = ColorOnSurface,
                            indicatorColor = ColorSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                    Text(
                        text = "Find in Library",
                        color = ColorOnSurface60,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = ColorOnSurface60)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = ColorOnSurface60)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("search_bar"),
                shape = RoundedCornerShape(28.dp),
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

            Spacer(modifier = Modifier.height(24.dp))

            // Horizontal Recently Played list (only shown when search is empty)
            if (state.searchQuery.isEmpty()) {
                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleMedium,
                    color = ColorOnSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    // Show exact recently played items
                    val recents = state.songs.take(3)
                    itemsIndexed(recents) { index, song ->
                        val isActive = index == 0 // First card "Resonance Cascade" is active haptic pulse
                        MediaCard(
                            song = song,
                            isActiveHaptic = isActive,
                            onClick = {
                                val fullSongs = state.songs
                                val songIndex = fullSongs.indexOf(song)
                                if (songIndex != -1) {
                                    onSongSelected(fullSongs, songIndex)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // All Tracks Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.searchQuery.isEmpty()) "All Tracks" else "Search Results",
                    style = MaterialTheme.typography.titleMedium,
                    color = ColorOnSurface,
                    fontWeight = FontWeight.Bold
                )

                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showSortMenu = true }
                    ) {
                        Text(
                            text = "Sort by",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorOnSurface60
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = ColorOnSurface60,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = ColorSurface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Title") },
                            onClick = { showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Artist") },
                            onClick = { showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added") },
                            onClick = { showSortMenu = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vertical list of all tracks
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                filteredSongs.forEachIndexed { index, song ->
                    val isCurrent = state.currentSong?.id == song.id
                    TrackRow(
                        song = song,
                        isCurrentPlaying = isCurrent,
                        isPlaying = state.isPlaying && isCurrent,
                        onClick = {
                            onSongSelected(filteredSongs, index)
                        }
                    )
                }

                if (filteredSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .background(ColorSurfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = ColorOnSurface60
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            Text(
                                text = "Your library is empty. Let's find your music.",
                                style = MaterialTheme.typography.titleLarge,
                                color = ColorOnSurface,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(48.dp))
                            
                            Button(
                                onClick = { /* To be wired if needed, or already scanned */ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ColorHapticAccent,
                                    contentColor = ColorOnBackground
                                ),
                                shape = CircleShape, // Pill shaped
                                modifier = Modifier.height(56.dp).padding(horizontal = 32.dp)
                            ) {
                                Text(
                                    "Scan Device for Audio", 
                                    fontWeight = FontWeight.Bold, 
                                    style = MaterialTheme.typography.labelLarge // Enforcing Inter via Typography
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCard(
    song: Song,
    isActiveHaptic: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "haptic_pulse")
    val pulseBorderWidthFactor by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_border_width"
    )

    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
            .testTag("media_card_${song.id}")
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (isActiveHaptic) pulseBorderWidthFactor.dp else 0.dp,
                    color = if (isActiveHaptic) ColorHapticAccent else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = "${song.title} cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Gold Accent dot on top right
            if (isActiveHaptic) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(8.dp)
                        .background(ColorHapticAccent, CircleShape)
                        .shadow(4.dp, CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            style = MaterialTheme.typography.labelLarge,
            color = ColorOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = ColorOnSurface60,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TrackRow(
    song: Song,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_bars")
    var showDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrentPlaying) ColorHapticAccent.copy(alpha = 0.12f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail artwork
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // If active and playing, show dynamic bouncing frequency bars
            if (isCurrentPlaying && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(16.dp)
                    ) {
                        BouncingEqualizerBar(infiniteTransition, delayMillis = 0, targetHeight = 16)
                        BouncingEqualizerBar(infiniteTransition, delayMillis = 200, targetHeight = 12)
                        BouncingEqualizerBar(infiniteTransition, delayMillis = 400, targetHeight = 14)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Title and Subtitle metadata
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrentPlaying) ColorHapticAccent else ColorOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrentPlaying) ColorHapticAccent.copy(alpha = 0.7f) else ColorOnSurface60,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { showDropdown = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = ColorOnSurface60
                )
            }
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                containerColor = ColorSurface
            ) {
                DropdownMenuItem(text = { Text("Shuffle play") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Start radio") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Play next") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Add album to library") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Download") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Go to artist") }, onClick = { showDropdown = false })
                DropdownMenuItem(text = { Text("Share") }, onClick = { showDropdown = false })
            }
        }
    }
}

@Composable
fun BouncingEqualizerBar(
    transition: InfiniteTransition,
    delayMillis: Int,
    targetHeight: Int
) {
    val barHeight by transition.animateFloat(
        initialValue = 4f,
        targetValue = targetHeight.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar_height"
    )

    Box(
        modifier = Modifier
            .width(3.dp)
            .height(barHeight.dp)
            .background(ColorHapticAccent, RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
    )
}

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
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(ColorSurface)
            .clickable(onClick = onExpand)
            .testTag("mini_player"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song artwork
            AsyncImage(
                model = currentSong.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Metadata with Haptic Indicator
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentSong.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = ColorOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hapticActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ColorHapticAccent.copy(alpha = dotAlpha))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Haptic Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorHapticAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = currentSong.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorOnSurface60,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Transport controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onPrev) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = ColorOnSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(40.dp)
                        .background(ColorSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = ColorOnSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = ColorOnSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 2dp Progress bar aligned perfectly on the bottom edge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(ColorSurfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(ColorHapticAccent)
            )
        }
    }
}
