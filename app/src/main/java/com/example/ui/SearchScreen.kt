package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Song
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: HaptiqUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSongSelected: (List<Song>, Int) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateLibrary: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    var active by remember { mutableStateOf(false) }

    val filteredSongs = remember(state.songs, state.searchQuery) {
        if (state.searchQuery.isEmpty()) {
            emptyList()
        } else {
            state.songs.filter {
                it.title.contains(state.searchQuery, ignoreCase = true) ||
                        it.artist.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("search_screen"),
        containerColor = ColorBackground,
        bottomBar = {
            NavigationBar(
                containerColor = ColorSurface,
                modifier = Modifier.height(72.dp)
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateHome,
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = ColorOnSurface60,
                        unselectedTextColor = ColorOnSurface60,
                        selectedIconColor = ColorOnSurface,
                        selectedTextColor = ColorOnSurface,
                        indicatorColor = ColorSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                    label = { Text("Search", style = MaterialTheme.typography.labelSmall) },
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
                    onClick = onNavigateLibrary,
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
                    onClick = onNavigateSettings,
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChanged,
                onSearch = { active = false },
                active = active,
                onActiveChange = { active = it },
                placeholder = { Text("Search songs, artists...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (active) 0.dp else 16.dp, vertical = 8.dp),
                colors = SearchBarDefaults.colors(
                    containerColor = ColorSurfaceVariant,
                    inputFieldColors = TextFieldDefaults.colors(
                        focusedTextColor = ColorOnSurface,
                        unfocusedTextColor = ColorOnSurface,
                        cursorColor = ColorHapticAccent
                    )
                )
            ) {
                if (filteredSongs.isNotEmpty()) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredSongs) { index, song ->
                            TrackRow(
                                song = song,
                                isCurrentPlaying = state.currentSong?.id == song.id,
                                isPlaying = state.isPlaying && state.currentSong?.id == song.id,
                                onClick = {
                                    onSongSelected(filteredSongs, index)
                                    active = false
                                }
                            )
                        }
                    }
                } else if (state.searchQuery.isEmpty()) {
                    // Recent Searches Empty State
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = ColorOnSurface60
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleMedium,
                            color = ColorOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your recent searches will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorOnSurface60
                        )
                    }
                }
            }

            if (!active && state.searchQuery.isEmpty()) {
                // Background content when SearchBar is not active
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = ColorOnSurface60
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Find your music",
                        style = MaterialTheme.typography.titleLarge,
                        color = ColorOnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!active) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredSongs) { index, song ->
                        TrackRow(
                            song = song,
                            isCurrentPlaying = state.currentSong?.id == song.id,
                            isPlaying = state.isPlaying && state.currentSong?.id == song.id,
                            onClick = {
                                onSongSelected(filteredSongs, index)
                            }
                        )
                    }
                }
            }
        }
    }
}
