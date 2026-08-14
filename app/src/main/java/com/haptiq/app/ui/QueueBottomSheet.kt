package com.haptiq.app.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*
import kotlin.math.roundToInt

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
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val spacingPx = with(density) { Spacing.xs.toPx() }

    // Local, reorderable mirror of the queue. Dragging edits THIS list for instant
    // feedback (no waiting on the flow round-trip through the player), and the NET move
    // is committed to the real queue once, on drop. Re-synced from the source only when
    // a drag isn't in progress — so drop doesn't briefly snap back to the stale order.
    val local = remember { mutableStateListOf<Song>() }
    var dragItemId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(songs) {
        if (dragItemId == null) {
            local.clear(); local.addAll(songs)
        }
    }

    var dragFromIndex by remember { mutableStateOf(-1) }
    var dragCurrentIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(0f) }

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
                itemsIndexed(local, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    val isDragging = dragItemId == song.id
                    Row(
                        modifier = Modifier
                            .onSizeChanged { if (rowHeightPx == 0f) rowHeightPx = it.height.toFloat() }
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                            .then(
                                if (isDragging) Modifier.shadow(Elevation.high, RoundedCornerShape(Radius.md))
                                else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        // Drag handle — press and drag to reorder. Reordering 100 tracks
                        // by tapping "move up/down" was the pain point this replaces.
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            tint = ColorOnSurface60,
                            modifier = Modifier
                                .size(ComponentSize.iconMedium)
                                .pointerInput(song.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragItemId = song.id
                                            dragFromIndex = local.indexOfFirst { it.id == song.id }
                                            dragCurrentIndex = dragFromIndex
                                            dragOffsetY = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetY += amount.y
                                            val slot = rowHeightPx + spacingPx
                                            if (slot > 0f && dragCurrentIndex >= 0) {
                                                val shift = (dragOffsetY / slot).roundToInt()
                                                val target = (dragCurrentIndex + shift).coerceIn(0, local.lastIndex)
                                                if (target != dragCurrentIndex) {
                                                    local.add(target, local.removeAt(dragCurrentIndex))
                                                    dragOffsetY -= (target - dragCurrentIndex) * slot
                                                    dragCurrentIndex = target
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (dragFromIndex >= 0 && dragFromIndex != dragCurrentIndex) {
                                                onMove(dragFromIndex, dragCurrentIndex)
                                            }
                                            dragItemId = null; dragFromIndex = -1
                                            dragCurrentIndex = -1; dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            dragItemId = null; dragFromIndex = -1
                                            dragCurrentIndex = -1; dragOffsetY = 0f
                                        }
                                    )
                                }
                        )
                        // The now-playing row can't be removed out from under the player
                        if (!isCurrent) {
                            QueueRowMenu(
                                onPlayNext = { onPlayNext(index) },
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
    onPlayNext: () -> Unit,
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
            DropdownMenuItem(
                text = { Text("Remove from queue", color = ColorOnSurface) },
                leadingIcon = { Icon(Icons.Default.Close, null, tint = ColorOnSurface60) },
                onClick = { expanded = false; onRemove() }
            )
        }
    }
}
