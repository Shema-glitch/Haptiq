package com.haptiq.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    state: HaptiqUiState,
    onSongSelected: (List<Song>, Int) -> Unit
) {
    val albums = remember(state.songs) {
        state.songs.groupBy { it.artist }
            .map { (artist, songs) -> artist to songs }
            .sortedBy { it.first }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Layout.screenHorizontalPadding)
    ) {
        HaptiqScreenHeader(subtitle = "Albums")

        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Album,
                    title = "No albums yet",
                    subtitle = "Scan your device to find music"
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                items(albums) { (artist, songs) ->
                    val isPlaying = state.isPlaying && state.currentSong?.artist == artist
                    AlbumGridCard(
                        artist = artist,
                        songCount = songs.size,
                        artworkUrl = songs.first().artworkUrl,
                        isPlaying = isPlaying,
                        onClick = { onSongSelected(songs, 0) }
                    )
                }
            }
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
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
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
                cornerRadius = Radius.md
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NowPlayingIndicator(active = isPlaying, height = 14.dp)
            Spacer(Modifier.width(Spacing.xxs))
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
