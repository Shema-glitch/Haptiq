package com.haptiq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haptiq.app.data.PlaylistWithCount
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*

// ─── Playlists tab ──────────────────────────────────────────
@Composable
fun PlaylistsScreen(
    state: HaptiqUiState,
    onAction: (HaptiqUiAction) -> Unit,
    onPlaylistOpened: (Long) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Layout.screenHorizontalPadding)
    ) {
        if (state.playlists.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.PlaylistPlay,
                    title = "No playlists yet",
                    subtitle = "Group the songs you love — tap New Playlist to start"
                ) {
                    Spacer(Modifier.height(Spacing.lg))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                        shape = ExpressiveShapes.navPill,
                        modifier = Modifier.height(ComponentSize.buttonHeight)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("New Playlist", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    countLabel(state.playlists.size, "playlist"),
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60
                )
                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null, tint = ColorPrimary, modifier = Modifier.size(ComponentSize.iconSmall))
                    Spacer(Modifier.width(Spacing.xxs))
                    Text("New", color = ColorPrimary, fontWeight = FontWeight.Bold)
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onPlaylistOpened(playlist.id) },
                        onRename = { renameTarget = playlist },
                        onDelete = { onAction(HaptiqUiAction.DeletePlaylist(playlist.id)) }
                    )
                }
                item { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { onAction(HaptiqUiAction.CreatePlaylist(it)); showCreateDialog = false },
            onDismiss = { showCreateDialog = false }
        )
    }
    renameTarget?.let { target ->
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Rename",
            initialName = target.name,
            onConfirm = { onAction(HaptiqUiAction.RenamePlaylist(target.id, it)); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            model = null,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            cornerRadius = Radius.md,
            fallbackLabel = playlist.name
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = ColorOnSurface, maxLines = 1)
            Text(
                countLabel(playlist.songCount, "song"),
                style = MaterialTheme.typography.bodyMedium,
                color = ColorOnSurface60
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(ComponentSize.touchTarget)) {
                Icon(Icons.Default.MoreVert, "Playlist options", tint = ColorOnSurface60)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = ColorSurfaceVariant) {
                DropdownMenuItem(text = { Text("Rename", color = ColorOnSurface) }, onClick = { showMenu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete", color = ColorError) }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurfaceVariant,
        title = { Text(title, color = ColorOnSurface) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name", color = ColorOnSurface60) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    cursorColor = ColorPrimary,
                    focusedTextColor = ColorOnSurface,
                    unfocusedTextColor = ColorOnSurface
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel, color = ColorPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ColorOnSurface60) }
        }
    )
}

// ─── Add-to-playlist dialog (used from Library rows) ────────
@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<PlaylistWithCount>,
    onAction: (HaptiqUiAction) -> Unit,
    onDismiss: () -> Unit
) {
    var creatingNew by remember { mutableStateOf(false) }
    if (creatingNew) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create & add",
            onConfirm = {
                onAction(HaptiqUiAction.CreatePlaylist(it, firstSongId = song.id))
                onDismiss()
            },
            onDismiss = onDismiss
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurfaceVariant,
        title = { Text("Add to playlist", color = ColorOnSurface) },
        text = {
            LazyColumn {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm))
                            .clickable { creatingNew = true }.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = ColorPrimary)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("New playlist", color = ColorPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm))
                            .clickable {
                                onAction(HaptiqUiAction.AddToPlaylist(playlist.id, song.id))
                                onDismiss()
                            }
                            .padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(playlist.name, color = ColorOnSurface, modifier = Modifier.weight(1f))
                        Text(countLabel(playlist.songCount, "song"), color = ColorOnSurface60, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ColorOnSurface60) }
        }
    )
}

// ─── Playlist detail ────────────────────────────────────────
@Composable
fun PlaylistDetailScreen(
    state: HaptiqUiState,
    onBack: () -> Unit,
    onSongSelected: (List<Song>, Int) -> Unit,
    onAction: (HaptiqUiAction) -> Unit
) {
    val playlist = state.playlists.find { it.id == state.activePlaylistId }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .systemBarsPadding()
            .padding(horizontal = Layout.screenHorizontalPadding)
    ) {
        Row(
            Modifier.fillMaxWidth().height(ComponentSize.topBarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.background(ColorSurface, CircleShape)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ColorOnSurface)
            }
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = ColorOnSurface,
                    maxLines = 1
                )
                Text(
                    countLabel(state.activePlaylistSongs.size, "song"),
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60
                )
            }
        }
        if (state.activePlaylistSongs.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.PlaylistPlay,
                    title = "Nothing here yet",
                    subtitle = "Add songs from the Library using the playlist button on any track"
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                itemsIndexed(state.activePlaylistSongs, key = { _, s -> s.id }) { index, song ->
                    val isCurrent = state.currentSong?.id == song.id
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            TrackRow(
                                song = song,
                                isCurrentPlaying = isCurrent,
                                isPlaying = state.isPlaying && isCurrent,
                                isFavorite = song.id in state.favoriteIds,
                                onClick = { onSongSelected(state.activePlaylistSongs, index) },
                                onToggleFavorite = { onAction(HaptiqUiAction.ToggleFavorite(song.id)) }
                            )
                        }
                        IconButton(
                            onClick = {
                                state.activePlaylistId?.let {
                                    onAction(HaptiqUiAction.RemoveFromPlaylist(it, song.id))
                                }
                            },
                            modifier = Modifier.size(ComponentSize.touchTarget)
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, "Remove from playlist", tint = ColorOnSurface60)
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
    }
}
