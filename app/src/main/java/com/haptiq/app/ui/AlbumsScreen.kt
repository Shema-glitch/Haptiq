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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
        // Consistent HAPTIQ app bar with screen name
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HAPTIQ", style = MaterialTheme.typography.titleLarge, color = ColorPrimary, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("Albums", style = MaterialTheme.typography.labelSmall, color = ColorOnSurface60)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = ColorBackground)
        )

        if (albums.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Album, null, Modifier.size(ComponentSize.iconHero), tint = ColorOnSurface60)
                    Spacer(Modifier.height(Spacing.lg))
                    Text("No albums yet", style = MaterialTheme.typography.titleMedium, color = ColorOnSurface)
                    Text("Scan your device to find music", style = MaterialTheme.typography.bodyMedium, color = ColorOnSurface60)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                items(albums) { (artist, songs) ->
                    AlbumGridCard(
                        artist = artist,
                        songCount = songs.size,
                        artworkUrl = songs.first().artworkUrl,
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
        Text(artist, style = MaterialTheme.typography.labelLarge, color = ColorOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        Text("$songCount tracks", style = MaterialTheme.typography.bodySmall, color = ColorOnSurface60)
    }
}
