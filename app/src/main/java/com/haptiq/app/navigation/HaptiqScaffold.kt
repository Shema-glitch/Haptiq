package com.haptiq.app.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.haptiq.app.data.Song
import com.haptiq.app.ui.MiniPlayer
import com.haptiq.app.ui.theme.*

/**
 * Shared scaffold with:
 * - 4-item bottom navigation: Library | Albums | Favorites | Settings
 * - MiniPlayer sits ABOVE the nav bar (inside content, not bottomBar)
 *
 * The MiniPlayer is NOT in bottomBar to avoid doubling the inset padding.
 */
@Composable
fun HaptiqScaffold(
    navController: NavHostController,
    currentRoute: String?,
    currentSong: Song?,
    isPlaying: Boolean,
    playbackProgress: Float,
    hapticActive: Boolean,
    onTogglePlayPause: () -> Unit,
    onNextClicked: () -> Unit,
    onPrevClicked: () -> Unit,
    onMiniPlayerExpanded: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ColorBackground,
        bottomBar = {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = ColorSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.LIBRARY,
                        onClick = {
                            if (currentRoute != Routes.LIBRARY) {
                                navController.navigate(Routes.LIBRARY) {
                                    popUpTo(Routes.LIBRARY) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.LIBRARY) Icons.Default.LibraryMusic else Icons.Outlined.LibraryMusic,
                                contentDescription = "Library"
                            )
                        },
                        label = { Text("Library", style = MaterialTheme.typography.labelSmall) },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ALBUMS,
                        onClick = {
                            if (currentRoute != Routes.ALBUMS) {
                                navController.navigate(Routes.ALBUMS) {
                                    popUpTo(Routes.LIBRARY) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.ALBUMS) Icons.Default.Album else Icons.Outlined.Album,
                                contentDescription = "Albums"
                            )
                        },
                        label = { Text("Albums", style = MaterialTheme.typography.labelSmall) },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.FAVORITES,
                        onClick = {
                            if (currentRoute != Routes.FAVORITES) {
                                navController.navigate(Routes.FAVORITES) {
                                    popUpTo(Routes.LIBRARY) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.FAVORITES) Icons.Default.Favorite else Icons.Outlined.Favorite,
                                contentDescription = "Favorites"
                            )
                        },
                        label = { Text("Favorites", style = MaterialTheme.typography.labelSmall) },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            if (currentRoute != Routes.SETTINGS) {
                                navController.navigate(Routes.SETTINGS) {
                                    popUpTo(Routes.LIBRARY) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.SETTINGS) Icons.Default.Settings else Icons.Outlined.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
                        colors = navItemColors()
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Content fills remaining space
            Box(modifier = Modifier.weight(1f)) {
                content(innerPadding)
            }
            // MiniPlayer sits between content and nav bar
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = playbackProgress,
                hapticActive = hapticActive,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNextClicked,
                onPrev = onPrevClicked,
                onExpand = onMiniPlayerExpanded
            )
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    unselectedIconColor = ColorOnSurface60,
    unselectedTextColor = ColorOnSurface60,
    selectedIconColor = ColorOnPrimary, // Black icon when active
    selectedTextColor = ColorHapticAccent, // Neon-lime text when active
    indicatorColor = ColorHapticAccent // Neon-lime active circular background
)
