package com.haptiq.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.currentBackStackEntryAsState
import com.haptiq.app.ui.*

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PERMISSIONS = "permissions"
    const val LIBRARY = "library"
    const val ALBUMS = "albums"
    const val FAVORITES = "favorites"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist_detail"
    const val PLAYER = "player"
    const val HAPTIC_STUDIO = "haptic_studio"
    const val SETTINGS = "settings"
    const val CALIBRATION = "calibration"
}

@Composable
fun HaptiqNavHost(
    navController: NavHostController,
    permissionViewModel: PermissionViewModel,
    onRequestPermission: () -> Unit,
    haptiqViewModel: HaptiqViewModel,
    startDestination: String = Routes.SPLASH
) {
    val haptiqState by haptiqViewModel.uiState.collectAsState()
    val hapticTuning by haptiqViewModel.hapticTuning.collectAsState()
    val permissionState by permissionViewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Screen transition animations.
    // Tabs cross-fade with a barely-there scale settle — sibling screens swap in
    // place. Destination screens (Player, Studio) get their own directional
    // transitions on their composable() entries so motion communicates where the
    // screen came from, not just that it changed.
    val enterTransition: EnterTransition =
        fadeIn(animationSpec = tween(260)) +
            scaleIn(initialScale = 0.97f, animationSpec = tween(260))
    val exitTransition: ExitTransition = fadeOut(animationSpec = tween(200))

    // Shared mini-player + bottom-nav wiring for the routes that sit inside HaptiqScaffold.
    // Every simple route needs the same five pieces of transport state, so this wrapper is
    // the single place that derives them from haptiqState instead of five call sites.
    @Composable
    fun HaptiqScaffoldRoute(
        subtitle: String? = null,
        content: @Composable (PaddingValues) -> Unit
    ) {
        HaptiqScaffold(
            navController = navController,
            currentRoute = currentRoute,
            topBar = {
                // The scaffold owns the app bar: screens with a plain header pass
                // a subtitle; Library passes null because its header carries live
                // actions (search/scan) that belong to its own state.
                if (subtitle != null) {
                    HaptiqScreenHeader(
                        subtitle = subtitle,
                        modifier = androidx.compose.ui.Modifier.padding(
                            horizontal = com.haptiq.app.ui.theme.Layout.screenHorizontalPadding
                        )
                    )
                }
            },
            content = content
        )
    }

    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { enterTransition },
        popExitTransition = { exitTransition }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onTimeout = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.PERMISSIONS) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onAllowClicked = {
                    // Launch the real Android permission dialog
                    onRequestPermission()
                },
                onLaterClicked = {
                    permissionViewModel.handleAction(PermissionUiAction.DenyPermission)
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.PERMISSIONS) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LIBRARY) {
            HaptiqScaffoldRoute { innerPadding ->
                LibraryScreen(
                    state = haptiqState,
                    innerPadding = innerPadding,
                    onSongSelected = { list, index ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                        haptiqViewModel.handleAction(HaptiqUiAction.SetPlayerExpanded(true))
                    },
                    onSearchQueryChanged = { query ->
                        haptiqViewModel.handleAction(HaptiqUiAction.Search(query))
                    },
                    onScanDevice = {
                        haptiqViewModel.handleAction(HaptiqUiAction.ScanDevice)
                    },
                    onHapticStudioClicked = {
                        if (currentRoute != Routes.HAPTIC_STUDIO) {
                            navController.navigate(Routes.HAPTIC_STUDIO) { launchSingleTop = true }
                        }
                    },
                    onToggleFavorite = { songId ->
                        haptiqViewModel.handleAction(HaptiqUiAction.ToggleFavorite(songId))
                    },
                    onSortModeChanged = { mode ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SetSortMode(mode))
                    },
                    onAction = { haptiqViewModel.handleAction(it) }
                )
            }
        }

        composable(Routes.PLAYLISTS) {
            HaptiqScaffoldRoute(subtitle = "Playlists") { _ ->
                PlaylistsScreen(
                    state = haptiqState,
                    onAction = { haptiqViewModel.handleAction(it) },
                    onPlaylistOpened = { id ->
                        haptiqViewModel.handleAction(HaptiqUiAction.OpenPlaylist(id))
                        navController.navigate(Routes.PLAYLIST_DETAIL)
                    }
                )
            }
        }

        composable(
            Routes.PLAYLIST_DETAIL,
            enterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(260)) { it } + fadeOut(tween(260))
            }
        ) {
            PlaylistDetailScreen(
                state = haptiqState,
                onBack = { navController.popBackStack() },
                onSongSelected = { list, index ->
                    haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                    haptiqViewModel.handleAction(HaptiqUiAction.SetPlayerExpanded(true))
                },
                onAction = { haptiqViewModel.handleAction(it) }
            )
        }

        composable(
            Routes.HAPTIC_STUDIO,
            // The Studio is a drill-in detail of the Player — slide in from the
            // trailing edge, back out the same way.
            enterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(300))
            },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(260)) { it } + fadeOut(tween(260))
            }
        ) {
            HapticStudioScreen(
                state = haptiqState,
                tuning = hapticTuning,
                onBackClicked = {
                    navController.popBackStack()
                },
                onToggleHaptics = { active ->
                    haptiqViewModel.handleAction(HaptiqUiAction.ToggleHaptics(active))
                },
                onPresetChanged = { presetId ->
                    haptiqViewModel.handleAction(HaptiqUiAction.ChangePreset(presetId))
                },
                onIntensityChanged = { intensity ->
                    haptiqViewModel.handleAction(HaptiqUiAction.ChangeIntensity(intensity))
                },
                onTuningAction = { action ->
                    haptiqViewModel.handleAction(action)
                }
            )
        }

        composable(Routes.ALBUMS) {
            HaptiqScaffoldRoute(subtitle = "Albums") { innerPadding ->
                AlbumsScreen(
                    state = haptiqState,
                    onSongSelected = { list, index ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                        haptiqViewModel.handleAction(HaptiqUiAction.SetPlayerExpanded(true))
                    },
                    onScanDevice = { haptiqViewModel.handleAction(HaptiqUiAction.ScanDevice) }
                )
            }
        }

        composable(Routes.FAVORITES) {
            HaptiqScaffoldRoute(subtitle = "Favorites") { innerPadding ->
                FavoritesScreen(
                    state = haptiqState,
                    onSongSelected = { list, index ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                        haptiqViewModel.handleAction(HaptiqUiAction.SetPlayerExpanded(true))
                    },
                    onToggleFavorite = { songId ->
                        haptiqViewModel.handleAction(HaptiqUiAction.ToggleFavorite(songId))
                    },
                    onBrowseLibrary = {
                        navController.navigate(Routes.LIBRARY) {
                            popUpTo(Routes.LIBRARY) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(Routes.SETTINGS) {
            HaptiqScaffoldRoute(subtitle = "Settings") { innerPadding ->
                SettingsScreen(
                    state = haptiqState,
                    innerPadding = innerPadding,
                    onBatterySaverToggled = { enabled ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SetBatterySaver(enabled))
                    },
                    onAction = { action ->
                        haptiqViewModel.handleAction(action)
                    },
                    onCalibrationClicked = {
                        navController.navigate(Routes.CALIBRATION)
                    }
                )
            }
        }

        composable(Routes.CALIBRATION) {
            HaptiqScaffoldRoute { innerPadding ->
                CalibrationScreen(
                    state = haptiqState,
                    innerPadding = innerPadding,
                    onPlayTestPulse = {
                        haptiqViewModel.handleAction(HaptiqUiAction.RunTestPulse)
                    },
                    onStrengthSelected = { strength ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SelectCalibrationStrength(strength))
                    },
                    onSaveCalibration = {
                        haptiqViewModel.handleAction(HaptiqUiAction.SaveCalibration)
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

        val hasBottomNav = currentRoute in setOf(
            Routes.LIBRARY, Routes.ALBUMS, Routes.FAVORITES,
            Routes.PLAYLISTS, Routes.SETTINGS, Routes.CALIBRATION
        )

        NowPlayingSheet(
            state = haptiqState,
            isExpanded = haptiqState.isPlayerExpanded,
            onExpandedChange = { expanded ->
                haptiqViewModel.handleAction(HaptiqUiAction.SetPlayerExpanded(expanded))
            },
            hasBottomNav = hasBottomNav,
            onTogglePlayPause = {
                haptiqViewModel.handleAction(HaptiqUiAction.TogglePlayPause)
            },
            onNextClicked = {
                haptiqViewModel.handleAction(HaptiqUiAction.PlayNext)
            },
            onPrevClicked = {
                haptiqViewModel.handleAction(HaptiqUiAction.PlayPrevious)
            },
            onDismissPlayback = {
                haptiqViewModel.handleAction(HaptiqUiAction.DismissPlayback)
            },
            onSeek = { progress ->
                haptiqViewModel.handleAction(HaptiqUiAction.Seek(progress))
            },
            onSeekPreview = { progress ->
                haptiqViewModel.handleAction(HaptiqUiAction.SeekPreview(progress))
            },
            onToggleHaptics = { active ->
                haptiqViewModel.handleAction(HaptiqUiAction.ToggleHaptics(active))
            },
            onHapticStudioClicked = {
                // The sheet is a persistent overlay drawn ON TOP of the NavHost, so it
                // must collapse before navigating — otherwise it keeps covering Haptic
                // Studio's own route underneath instead of getting out of the way.
                // launchSingleTop guards against duplicate back-stack entries from a
                // burst of taps before the first navigation lands.
                if (currentRoute != Routes.HAPTIC_STUDIO) {
                    haptiqViewModel.handleAction(HaptiqUiAction.SetPlayerExpanded(false))
                    navController.navigate(Routes.HAPTIC_STUDIO) { launchSingleTop = true }
                }
            },
            onSongSelected = { list, index ->
                haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
            },
            onQueueAction = { action ->
                haptiqViewModel.handleAction(action)
            },
            onRefreshDndStatus = {
                haptiqViewModel.handleAction(HaptiqUiAction.RefreshDndStatus)
            },
            onToggleShuffle = {
                haptiqViewModel.handleAction(HaptiqUiAction.ToggleShuffle)
            },
            onToggleRepeat = {
                haptiqViewModel.handleAction(HaptiqUiAction.ToggleRepeat)
            },
            onToggleFavorite = { songId ->
                haptiqViewModel.handleAction(HaptiqUiAction.ToggleFavorite(songId))
            },
            onSetSpeed = { speed ->
                haptiqViewModel.handleAction(HaptiqUiAction.SetPlaybackSpeed(speed))
            },
            onSetVolume = { fraction ->
                haptiqViewModel.handleAction(HaptiqUiAction.SetVolume(fraction))
            }
        )
    }
}
