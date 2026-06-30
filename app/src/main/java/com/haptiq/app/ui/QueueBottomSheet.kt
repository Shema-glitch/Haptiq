package com.haptiq.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        scrimColor = ColorBackground.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge,
                color = ColorOnSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(songs) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TrackRow(
                                song = song,
                                isCurrentPlaying = isCurrent,
                                isPlaying = isPlaying && isCurrent,
                                isFavorite = false,
                                onClick = {
                                    onSongSelected(index)
                                },
                                onToggleFavorite = {}
                            )
                        }
                        // D7: Drag handle — visible but shows "coming soon" Toast
                        IconButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Queue reordering coming soon.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Reorder queue",
                                tint = ColorOnSurface60
                            )
                        }
                    }
                }
            }
        }
    }
}
