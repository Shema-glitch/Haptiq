package com.haptiq.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: HaptiqUiState,
    onSongSelected: (List<Song>, Int) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val favoriteSongs = remember(state.songs, state.favoriteIds) {
        state.songs.filter { it.id in state.favoriteIds }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Layout.screenHorizontalPadding)
    ) {
        // Consistent HAPTIQ app bar with screen name
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HAPTIQ", style = MaterialTheme.typography.titleLarge, color = ColorPrimary, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("Favorites", style = MaterialTheme.typography.labelSmall, color = ColorOnSurface60)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = ColorBackground)
        )

        if (favoriteSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FavoriteBorder, null, Modifier.size(ComponentSize.iconHero), tint = ColorOnSurface60)
                    Spacer(Modifier.height(Spacing.lg))
                    Text("No favorites yet", style = MaterialTheme.typography.titleMedium, color = ColorOnSurface)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Tap the heart icon on any track to add it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorOnSurface60
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                itemsIndexed(favoriteSongs) { index, song ->
                    val isCurrent = state.currentSong?.id == song.id
                    TrackRow(
                        song = song,
                        isCurrentPlaying = isCurrent,
                        isPlaying = state.isPlaying && isCurrent,
                        isFavorite = true,
                        onClick = {
                            val songIndex = state.songs.indexOf(song)
                            if (songIndex != -1) onSongSelected(state.songs, songIndex)
                        },
                        onToggleFavorite = { onToggleFavorite(song.id) }
                    )
                }
            }
        }
    }
}
