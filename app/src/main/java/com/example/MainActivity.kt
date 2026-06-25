package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val permissionViewModel = remember { PermissionViewModel() }
                val haptiqViewModel = remember { HaptiqViewModel(this) }

                val permissionState by permissionViewModel.uiState.collectAsState()
                val haptiqState by haptiqViewModel.uiState.collectAsState()

                var currentScreen by remember { mutableStateOf("splash") }

                // Check permissions helper
                fun checkPermissions() {
                    val hasRecord = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    val hasAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_MEDIA_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    }

                    if (hasRecord && hasAudio) {
                        permissionViewModel.handleAction(PermissionUiAction.GrantPermission)
                        currentScreen = "library"
                    } else {
                        permissionViewModel.handleAction(PermissionUiAction.DenyPermission)
                        currentScreen = "permissions"
                    }
                }

                // Launcher to request runtime permissions
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                    val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false
                    } else {
                        permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
                    }

                    if (recordGranted && audioGranted) {
                        permissionViewModel.handleAction(PermissionUiAction.GrantPermission)
                        currentScreen = "library"
                    } else {
                        permissionViewModel.handleAction(PermissionUiAction.PermanentDenyPermission)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Crossfade(targetState = currentScreen, label = "screen_routing") { screen ->
                        when (screen) {
                            "splash" -> {
                                SplashScreen(
                                    onTimeout = {
                                        checkPermissions()
                                    }
                                )
                            }
                            "permissions" -> {
                                PermissionsScreen(
                                    onAllowClicked = {
                                        val permissionList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            arrayOf(
                                                Manifest.permission.RECORD_AUDIO,
                                                Manifest.permission.READ_MEDIA_AUDIO
                                            )
                                        } else {
                                            arrayOf(
                                                Manifest.permission.RECORD_AUDIO,
                                                Manifest.permission.READ_EXTERNAL_STORAGE
                                            )
                                        }
                                        permissionLauncher.launch(permissionList)
                                    },
                                    onLaterClicked = {
                                        // Allow entry to Library anyway (using high-fidelity mock fallback visualization)
                                        permissionViewModel.handleAction(PermissionUiAction.DenyPermission)
                                        currentScreen = "library"
                                    }
                                )
                            }
                            "library" -> {
                                LibraryScreen(
                                    state = haptiqState,
                                    onSongSelected = { list, index ->
                                        haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                                        currentScreen = "player"
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
                                    onSearchQueryChanged = { query ->
                                        haptiqViewModel.handleAction(HaptiqUiAction.Search(query))
                                    },
                                    onSettingsClicked = {
                                        currentScreen = "settings"
                                    },
                                    onHapticStudioClicked = {
                                        currentScreen = "studio"
                                    },
                                    onMenuClicked = {},
                                    onMiniPlayerExpanded = {
                                        currentScreen = "player"
                                    }
                                )
                            }
                            "player" -> {
                                PlayerScreen(
                                    state = haptiqState,
                                    onBackClicked = {
                                        currentScreen = "library"
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
                                        currentScreen = "studio"
                                    },
                                    onSongSelected = { list, index ->
                                        haptiqViewModel.handleAction(HaptiqUiAction.SelectSong(list, index))
                                    }
                                )
                            }
                            "studio" -> {
                                HapticStudioScreen(
                                    state = haptiqState,
                                    onBackClicked = {
                                        currentScreen = "player"
                                    },
                                    onToggleHaptics = { active ->
                                        haptiqViewModel.handleAction(HaptiqUiAction.ToggleHaptics(active))
                                    },
                                    onPresetChanged = { presetId ->
                                        haptiqViewModel.handleAction(HaptiqUiAction.ChangePreset(presetId))
                                    },
                                    onIntensityChanged = { intensity ->
                                        haptiqViewModel.handleAction(HaptiqUiAction.ChangeIntensity(intensity))
                                    }
                                )
                            }
                            "settings" -> {
                                SettingsScreen(
                                    state = haptiqState,
                                    onBackClicked = {
                                        currentScreen = "library"
                                    },
                                    onBatterySaverToggled = { enabled ->
                                        haptiqViewModel.handleAction(HaptiqUiAction.SetBatterySaver(enabled))
                                    },
                                    onAction = { action ->
                                        haptiqViewModel.handleAction(action)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
