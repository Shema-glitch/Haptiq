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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
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

/** How many favorites get the large bento-tile treatment before the list takes over. */
private const val SHOWCASE_COUNT = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: HaptiqUiState,
    onSongSelected: (List<Song>, Int) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    // A–Z so the order is predictable; the header row says so explicitly.
    // (favoriteIds is an unordered Set — "most recently favorited" isn't
    // derivable from UI state today.)
    val favoriteSongs = remember(state.songs, state.favoriteIds) {
        state.songs
            .filter { it.id in state.favoriteIds }
            .sortedBy { it.title.lowercase() }
    }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(favoriteSongs, query) {
        if (query.isBlank()) favoriteSongs
        else favoriteSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }
    // While searching, results render flat (no showcase tiles, no hero) so
    // they're scannable as one set.
    val showcase = remember(filtered, query) {
        if (query.isBlank()) filtered.take(SHOWCASE_COUNT) else emptyList()
    }
    val remaining = remember(filtered, showcase) { filtered.drop(showcase.size) }

    // Tapping an individual favorite keeps the original behavior exactly:
    // play within the full-library queue at that song's library index.
    fun playFromLibrary(song: Song) {
        val songIndex = state.songs.indexOf(song)
        if (songIndex != -1) onSongSelected(state.songs, songIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Layout.screenHorizontalPadding)
            .testTag("favorites_screen")
    ) {
        if (favoriteSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.FavoriteBorder,
                    title = "No favorites yet",
                    subtitle = "Tap the heart icon on any track to add it here"
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // ── Bento hero: play everything you've hearted, as one queue ──
                // Hidden while searching — it acts on ALL favorites, not the results.
                if (query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "favorites_hero") {
                        PlayAllFavoritesTile(
                            songs = favoriteSongs,
                            onPlayAll = { onSongSelected(favoriteSongs, 0) }
                        )
                    }
                }

                // ── Sort/count header — visible ordering, not implied ──
                item(span = { GridItemSpan(maxLineSpan) }, key = "favorites_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            countLabel(filtered.size, "favorite"),
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
                                    contentDescription = if (searchOpen) "Close search" else "Search favorites",
                                    tint = ColorOnSurface60,
                                    modifier = Modifier.size(ComponentSize.iconSmall)
                                )
                            }
                        }
                    }
                }

                // ── Search field — filters by title or artist ──
                if (searchOpen) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "favorites_search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Find in Favorites", color = ColorOnSurface60) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = ColorOnSurface60) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ComponentSize.searchBarHeight)
                                .testTag("favorites_search_bar"),
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
                    item(span = { GridItemSpan(maxLineSpan) }, key = "favorites_no_results") {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No results",
                            subtitle = "Nothing matches \"$query\""
                        )
                    }
                }

                // ── Showcase tiles: first favorites get artwork-forward cards ──
                itemsIndexed(showcase, key = { _, song -> "tile_${song.id}" }) { _, song ->
                    val isCurrent = state.currentSong?.id == song.id
                    FavoriteGridTile(
                        song = song,
                        isCurrentPlaying = isCurrent,
                        isPlaying = state.isPlaying && isCurrent,
                        onClick = { playFromLibrary(song) },
                        onToggleFavorite = { onToggleFavorite(song.id) }
                    )
                }

                // ── The rest as scannable rows, full width ──
                itemsIndexed(
                    remaining,
                    key = { _, song -> "row_${song.id}" },
                    span = { _, _ -> GridItemSpan(maxLineSpan) }
                ) { _, song ->
                    val isCurrent = state.currentSong?.id == song.id
                    TrackRow(
                        song = song,
                        isCurrentPlaying = isCurrent,
                        isPlaying = state.isPlaying && isCurrent,
                        isFavorite = true,
                        onClick = { playFromLibrary(song) },
                        onToggleFavorite = { onToggleFavorite(song.id) }
                    )
                }

                // Breathing room above the MiniPlayer
                item(span = { GridItemSpan(maxLineSpan) }, key = "bottom_spacer") {
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }
        }
    }
}

/**
 * Full-width hero: artwork of the first favorite behind a warm scrim, with a
 * clay play button. No blur — plain gradient scrim only (blur clips to a boxed
 * artifact on the target device).
 */
@Composable
private fun PlayAllFavoritesTile(
    songs: List<Song>,
    onPlayAll: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = HaptiqMotion.fastSpring(),
        label = "play_all_press_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(Elevation.high, RoundedCornerShape(Radius.lg))
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(interactionSource = interaction, indication = null, onClick = onPlayAll)
            .testTag("favorites_play_all")
    ) {
        ArtworkImage(
            model = songs.first().artworkUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            iconSize = ComponentSize.iconHero,
            fallbackLabel = songs.first().title
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ColorBackground.copy(alpha = 0.35f),
                            ColorBackground.copy(alpha = 0.65f),
                            ColorBackground.copy(alpha = 0.94f)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(Layout.cardInternalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "FAVORITES",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorHapticAccent,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "Play all favorites",
                    style = MaterialTheme.typography.titleLarge,
                    color = ColorOnSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    countLabel(songs.size, "track"),
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorOnSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(ComponentSize.touchTarget)
                    .clip(CircleShape)
                    .background(ColorPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play all favorites",
                    tint = ColorOnPrimary,
                    modifier = Modifier.size(ComponentSize.iconLarge)
                )
            }
        }
    }
}

/** Square artwork-forward tile for the showcase row of the favorites bento. */
@Composable
private fun FavoriteGridTile(
    song: Song,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = HaptiqMotion.fastSpring(),
        label = "favorite_tile_press_scale"
    )

    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(Radius.md))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .testTag("favorite_tile_${song.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(Elevation.medium, RoundedCornerShape(Radius.md))
                .clip(RoundedCornerShape(Radius.md))
        ) {
            ArtworkImage(
                model = song.artworkUrl,
                contentDescription = "${song.title} cover",
                modifier = Modifier.fillMaxSize(),
                iconSize = ComponentSize.iconXXL,
                cornerRadius = Radius.md,
                fallbackLabel = song.title
            )
            // Un-favorite affordance — full 48dp touch target, scrim-backed icon
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(ComponentSize.touchTarget)
            ) {
                Box(
                    modifier = Modifier
                        .size(ComponentSize.iconXL)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Remove from favorites",
                        tint = ColorPrimary,
                        modifier = Modifier.size(ComponentSize.iconSmall)
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCurrentPlaying && isPlaying) {
                NowPlayingIndicator(active = true)
                Spacer(Modifier.width(Spacing.xxs))
            }
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelLarge,
                color = ColorOnSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = ColorOnSurface60,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
