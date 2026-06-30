package com.haptiq.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.haptiq.app.ui.*

object Routes {
    const val SPLASH = "splash"
    const val PERMISSIONS = "permissions"
    const val LIBRARY = "library"
    const val ALBUMS = "albums"
    const val FAVORITES = "favorites"
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

    // Screen transition animations
    val enterTransition: EnterTransition = fadeIn(animationSpec = tween(300))
    val exitTransition: ExitTransition = fadeOut(animationSpec = tween(300))

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
                    navController.navigate(Routes.PERMISSIONS) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
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
            HaptiqScaffold(
                navController = navController,
                currentRoute = currentRoute,
                currentSong = haptiqState.currentSong,
                isPlaying = haptiqState.isPlaying,
                playbackProgress = haptiqState.playbackProgress,
                hapticActive = haptiqState.hapticActive,
                onTogglePlayPause = {
                    haptiqViewModel.handleAction(HaptiqUiAction.TogglePlayPause)
                },
                onNextClicked = {
                    haptiqViewModel.handleAction(HaptiqUiAction.PlayNext)
                },
                onPrevClicked = {
                    haptiqViewModel.handleAction(HaptiqUiAction.PlayPrevious)
                },
                onMiniPlayerExpanded = {
                    navController.navigate(Routes.PLAYER)
                }
            ) { innerPadding ->
                LibraryScreen(
                    state = haptiqState,
                    innerPadding = innerPadding,
                    onSongSelected = { list, index ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                        navController.navigate(Routes.PLAYER)
                    },
                    onSearchQueryChanged = { query ->
                        haptiqViewModel.handleAction(HaptiqUiAction.Search(query))
                    },
                    onScanDevice = {
                        haptiqViewModel.handleAction(HaptiqUiAction.ScanDevice)
                    },
                    onHapticStudioClicked = {
                        navController.navigate(Routes.HAPTIC_STUDIO)
                    },
                    onToggleFavorite = { songId ->
                        haptiqViewModel.handleAction(HaptiqUiAction.ToggleFavorite(songId))
                    }
                )
            }
        }

        composable(Routes.PLAYER) {
            PlayerScreen(
                state = haptiqState,
                onBackClicked = {
                    navController.popBackStack()
                },
                onTogglePlayPause = {
                    haptiqViewModel.handleAction(HaptiqUiAction.TogglePlayPause)
                },
                onNextClicked = {
                    haptiqViewModel.handleAction(HaptiqUiAction.PlayNext)
                },
                onPrevClicked = {
                    haptiqViewModel.handleAction(HaptiqUiAction.PlayPrevious)
                },
                onSeek = { progress ->
                    haptiqViewModel.handleAction(HaptiqUiAction.Seek(progress))
                },
                onToggleHaptics = { active ->
                    haptiqViewModel.handleAction(HaptiqUiAction.ToggleHaptics(active))
                },
                onHapticStudioClicked = {
                    navController.navigate(Routes.HAPTIC_STUDIO)
                },
                onSongSelected = { list, index ->
                    haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
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
                }
            )
        }

        composable(Routes.HAPTIC_STUDIO) {
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
            HaptiqScaffold(
                navController = navController,
                currentRoute = currentRoute,
                currentSong = haptiqState.currentSong,
                isPlaying = haptiqState.isPlaying,
                playbackProgress = haptiqState.playbackProgress,
                hapticActive = haptiqState.hapticActive,
                onTogglePlayPause = { haptiqViewModel.handleAction(HaptiqUiAction.TogglePlayPause) },
                onNextClicked = { haptiqViewModel.handleAction(HaptiqUiAction.PlayNext) },
                onPrevClicked = { haptiqViewModel.handleAction(HaptiqUiAction.PlayPrevious) },
                onMiniPlayerExpanded = { navController.navigate(Routes.PLAYER) }
            ) { innerPadding ->
                AlbumsScreen(
                    state = haptiqState,
                    onSongSelected = { list, index ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                        navController.navigate(Routes.PLAYER)
                    }
                )
            }
        }

        composable(Routes.FAVORITES) {
            HaptiqScaffold(
                navController = navController,
                currentRoute = currentRoute,
                currentSong = haptiqState.currentSong,
                isPlaying = haptiqState.isPlaying,
                playbackProgress = haptiqState.playbackProgress,
                hapticActive = haptiqState.hapticActive,
                onTogglePlayPause = { haptiqViewModel.handleAction(HaptiqUiAction.TogglePlayPause) },
                onNextClicked = { haptiqViewModel.handleAction(HaptiqUiAction.PlayNext) },
                onPrevClicked = { haptiqViewModel.handleAction(HaptiqUiAction.PlayPrevious) },
                onMiniPlayerExpanded = { navController.navigate(Routes.PLAYER) }
            ) { innerPadding ->
                FavoritesScreen(
                    state = haptiqState,
                    onSongSelected = { list, index ->
                        haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                        navController.navigate(Routes.PLAYER)
                    },
                    onToggleFavorite = { songId ->
                        haptiqViewModel.handleAction(HaptiqUiAction.ToggleFavorite(songId))
                    }
                )
            }
        }

        composable(Routes.SETTINGS) {
            HaptiqScaffold(
                navController = navController,
                currentRoute = currentRoute,
                currentSong = haptiqState.currentSong,
                isPlaying = haptiqState.isPlaying,
                playbackProgress = haptiqState.playbackProgress,
                hapticActive = haptiqState.hapticActive,
                onTogglePlayPause = {
                    haptiqViewModel.handleAction(HaptiqUiAction.TogglePlayPause)
                },
                onNextClicked = {
                    haptiqViewModel.handleAction(HaptiqUiAction.PlayNext)
                },
                onPrevClicked = {
                    haptiqViewModel.handleAction(HaptiqUiAction.PlayPrevious)
                },
                onMiniPlayerExpanded = {
                    navController.navigate(Routes.PLAYER)
                }
            ) { innerPadding ->
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
            HaptiqScaffold(
                navController = navController,
                currentRoute = currentRoute,
                currentSong = haptiqState.currentSong,
                isPlaying = haptiqState.isPlaying,
                playbackProgress = haptiqState.playbackProgress,
                hapticActive = haptiqState.hapticActive,
                onTogglePlayPause = {
                    haptiqViewModel.handleAction(HaptiqUiAction.TogglePlayPause)
                },
                onNextClicked = {
                    haptiqViewModel.handleAction(HaptiqUiAction.PlayNext)
                },
                onPrevClicked = {
                    haptiqViewModel.handleAction(HaptiqUiAction.PlayPrevious)
                },
                onMiniPlayerExpanded = {
                    navController.navigate(Routes.PLAYER)
                }
            ) { innerPadding ->
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
}
