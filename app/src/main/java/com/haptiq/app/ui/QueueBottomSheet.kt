package com.haptiq.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongSelected: (Int) -> Unit,
    onPlayNext: (Int) -> Unit = {},
    onMove: (Int, Int) -> Unit = { _, _ -> },
    onRemove: (Int) -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        scrimColor = ColorBackground.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xxl)
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge,
                color = ColorOnSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            TrackRow(
                                song = song,
                                isCurrentPlaying = isCurrent,
                                isPlaying = isPlaying && isCurrent,
                                isFavorite = false,
                                onClick = { onSongSelected(index) },
                                onToggleFavorite = {}
                            )
                        }
                        // The now-playing row can't be edited out from under the player
                        if (!isCurrent) {
                            QueueRowMenu(
                                canMoveUp = index > 0,
                                canMoveDown = index < songs.size - 1,
                                onPlayNext = { onPlayNext(index) },
                                onMoveUp = { onMove(index, index - 1) },
                                onMoveDown = { onMove(index, index + 1) },
                                onRemove = { onRemove(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRowMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlayNext: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Queue options",
                tint = ColorOnSurface60,
                modifier = Modifier.size(ComponentSize.iconSmall)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = ColorSurface
        ) {
            DropdownMenuItem(
                text = { Text("Play next", color = ColorOnSurface) },
                leadingIcon = { Icon(Icons.Default.QueuePlayNext, null, tint = ColorOnSurface60) },
                onClick = { expanded = false; onPlayNext() }
            )
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text("Move up", color = ColorOnSurface) },
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, null, tint = ColorOnSurface60) },
                    onClick = { expanded = false; onMoveUp() }
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text("Move down", color = ColorOnSurface) },
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, null, tint = ColorOnSurface60) },
                    onClick = { expanded = false; onMoveDown() }
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from queue", color = ColorOnSurface) },
                leadingIcon = { Icon(Icons.Default.Close, null, tint = ColorOnSurface60) },
                onClick = { expanded = false; onRemove() }
            )
        }
    }
}
