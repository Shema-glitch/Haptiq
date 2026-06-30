package com.haptiq.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.haptiq.app.navigation.HaptiqNavHost
import com.haptiq.app.ui.HaptiqUiAction
import com.haptiq.app.ui.HaptiqViewModel
import com.haptiq.app.ui.PermissionUiAction
import com.haptiq.app.ui.PermissionViewModel
import com.haptiq.app.ui.theme.HaptiqTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "haptiq_prefs"
        private const val KEY_PERMISSION_ASKED = "permission_asked"
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)
        }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun wasPermissionPreviouslyAsked(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSION_ASKED, false)
    }

    private fun markPermissionAsked() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HaptiqTheme {
                val navController = rememberNavController()
                val permissionViewModel = remember { PermissionViewModel() }
                val haptiqViewModel: HaptiqViewModel = hiltViewModel()

                // Determine start destination:
                // If permission was previously asked OR is already granted, skip to library
                val alreadyAsked = wasPermissionPreviouslyAsked()
                val alreadyGranted = hasRequiredPermissions()
                val startDestination = if (alreadyAsked || alreadyGranted) {
                    if (alreadyGranted) {
                        // Trigger a rescan on cold start with permission
                        LaunchedEffect(Unit) {
                            haptiqViewModel.handleAction(HaptiqUiAction.RescanLibrary)
                        }
                    }
                    "library"
                } else {
                    "splash"
                }

                // Permission launcher — fires after the system dialog returns
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    markPermissionAsked()
                    val allGranted = permissions.values.all { it }
                    if (allGranted) {
                        permissionViewModel.handleAction(PermissionUiAction.GrantPermission)
                        haptiqViewModel.handleAction(HaptiqUiAction.RescanLibrary)
                    } else {
                        permissionViewModel.handleAction(PermissionUiAction.DenyPermission)
                    }
                    navController.navigate("library") {
                        popUpTo("permissions") { inclusive = true }
                    }
                }

                HaptiqNavHost(
                    navController = navController,
                    permissionViewModel = permissionViewModel,
                    haptiqViewModel = haptiqViewModel,
                    startDestination = startDestination,
                    onRequestPermission = {
                        val alreadyGrantedNow = hasRequiredPermissions()
                        if (alreadyGrantedNow) {
                            markPermissionAsked()
                            permissionViewModel.handleAction(PermissionUiAction.GrantPermission)
                            haptiqViewModel.handleAction(HaptiqUiAction.RescanLibrary)
                            navController.navigate("library") {
                                popUpTo("permissions") { inclusive = true }
                            }
                        } else {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    }
                )
            }
        }
    }
}
