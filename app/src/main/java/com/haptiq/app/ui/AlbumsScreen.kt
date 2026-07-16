package com.haptiq.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

/** One artist's collection — the grouping unit this screen renders. */
private data class ArtistCollection(val artist: String, val songs: List<Song>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    state: HaptiqUiState,
    onSongSelected: (List<Song>, Int) -> Unit,
    onScanDevice: () -> Unit = {}
) {
    // Grouped by artist (Song carries no album metadata today) and sorted
    // A–Z so the ordering is predictable; the header row says so explicitly.
    val collections = remember(state.songs) {
        state.songs.groupBy { it.artist }
            .map { (artist, songs) -> ArtistCollection(artist, songs) }
            .sortedBy { it.artist.lowercase() }
    }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(collections, query) {
        if (query.isBlank()) collections
        else collections.filter { c ->
            c.artist.contains(query, ignoreCase = true) ||
                c.songs.any { it.title.contains(query, ignoreCase = true) }
        }
    }
    // Hero = the deepest collection: the one the user most likely came here for.
    // Suppressed while searching — results should be a flat, scannable set.
    val hero = remember(filtered, query) {
        if (query.isBlank()) filtered.maxByOrNull { it.songs.size } else null
    }
    val rest = remember(filtered, hero) { filtered.filterNot { it === hero } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Layout.screenHorizontalPadding)
            .testTag("albums_screen")
    ) {
        if (collections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Album,
                    title = "No albums yet",
                    subtitle = "Scan your device to find music"
                ) {
                    Spacer(Modifier.height(Spacing.lg))
                    Button(
                        onClick = onScanDevice,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimary,
                            contentColor = ColorOnPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm)
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(ComponentSize.iconSmall))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Scan for music")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // ── Sort/count header — makes the ordering visible, not implied ──
                item(span = { GridItemSpan(maxLineSpan) }, key = "albums_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            countLabel(filtered.size, "artist"),
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorOnSurface60
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Sorted A–Z",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorOnSurface60
                            )
                            IconButton(
                                onClick = {
                                    if (searchOpen) query = ""
                                    searchOpen = !searchOpen
                                },
                                modifier = Modifier.size(ComponentSize.touchTarget)
                            ) {
                                Icon(
                                    if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = if (searchOpen) "Close search" else "Search albums",
                                    tint = ColorOnSurface60,
                                    modifier = Modifier.size(ComponentSize.iconSmall)
                                )
                            }
                        }
                    }
                }

                // ── Search field — filters by artist or any song title ──
                if (searchOpen) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "albums_search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Find artists or songs", color = ColorOnSurface60) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = ColorOnSurface60) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ComponentSize.searchBarHeight)
                                .testTag("albums_search_bar"),
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
                }

                // ── No search results ──
                if (filtered.isEmpty() && query.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "albums_no_results") {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No results",
                            subtitle = "Nothing matches \"$query\""
                        )
                    }
                }

                // ── Bento hero: the largest collection spans both columns ──
                if (hero != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hero_${hero.artist}") {
                        val isPlaying = state.isPlaying && state.currentSong?.artist == hero.artist
                        AlbumHeroTile(
                            collection = hero,
                            isPlaying = isPlaying,
                            onClick = { onSongSelected(hero.songs, 0) }
                        )
                    }
                }

                // ── Standard tiles, alphabetical ──
                items(rest, key = { it.artist }) { collection ->
                    val isPlaying = state.isPlaying && state.currentSong?.artist == collection.artist
                    AlbumGridCard(
                        artist = collection.artist,
                        songCount = collection.songs.size,
                        artworkUrl = collection.songs.first().artworkUrl,
                        isPlaying = isPlaying,
                        onClick = { onSongSelected(collection.songs, 0) }
                    )
                }
            }
        }
    }
}

/**
 * Full-width bento hero: artwork-dominant, metadata overlaid on a warm scrim.
 * No blur anywhere — the scrim is a plain vertical gradient (blur renders as a
 * boxed artifact on the target device).
 */
@Composable
private fun AlbumHeroTile(
    collection: ArtistCollection,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = HaptiqMotion.fastSpring(),
        label = "hero_press_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(Elevation.high, RoundedCornerShape(Radius.lg))
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .testTag("albums_hero")
    ) {
        ArtworkImage(
            model = collection.songs.first().artworkUrl,
            contentDescription = "${collection.artist} artwork",
            modifier = Modifier.fillMaxSize(),
            iconSize = ComponentSize.iconHero,
            fallbackLabel = collection.artist
        )
        // Readability scrim — warm charcoal fading up into the artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ColorBackground.copy(alpha = 0.55f),
                            ColorBackground.copy(alpha = 0.92f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Layout.cardInternalPadding)
        ) {
            Text(
                text = "MOST TRACKS",
                style = MaterialTheme.typography.labelSmall,
                color = ColorHapticAccent,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(Spacing.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    NowPlayingIndicator(active = true)
                    Spacer(Modifier.width(Spacing.xxs))
                }
                Text(
                    text = collection.artist,
                    style = MaterialTheme.typography.titleLarge,
                    color = ColorOnSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                countLabel(collection.songs.size, "track"),
                style = MaterialTheme.typography.bodySmall,
                color = ColorOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumGridCard(
    artist: String,
    songCount: Int,
    artworkUrl: String,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = HaptiqMotion.fastSpring(),
        label = "card_press_scale"
    )

    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(Radius.md))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(Elevation.medium, RoundedCornerShape(Radius.md))
                .clip(RoundedCornerShape(Radius.md))
        ) {
            ArtworkImage(
                model = artworkUrl,
                contentDescription = "$artist artwork",
                modifier = Modifier.fillMaxSize(),
                iconSize = ComponentSize.iconXXL,
                cornerRadius = Radius.md,
                fallbackLabel = artist
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isPlaying) {
                NowPlayingIndicator(active = true)
                Spacer(Modifier.width(Spacing.xxs))
            }
            Text(
                text = artist,
                style = MaterialTheme.typography.labelLarge,
                color = ColorOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            countLabel(songCount, "track"),
            style = MaterialTheme.typography.bodySmall,
            color = ColorOnSurface60
        )
    }
}
