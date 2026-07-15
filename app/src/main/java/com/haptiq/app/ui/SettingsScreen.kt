package com.haptiq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haptiq.app.ui.theme.*

/**
 * Settings screen — wrapped by [HaptiqScaffold] for bottom navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: HaptiqUiState,
    innerPadding: PaddingValues,
    onBatterySaverToggled: (Boolean) -> Unit,
    onAction: (HaptiqUiAction) -> Unit,
    onCalibrationClicked: () -> Unit
) {
    var showLicenses by remember { mutableStateOf(false) }
    // No .padding(innerPadding) here: HaptiqScaffold already consumes it on the
    // column that hosts this screen. Applying it again doubled the bottom inset
    // (nav bar + mini player height), leaving a dead cutout under the content.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .testTag("settings_screen")
    ) {
        // Preferences section
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = "PREFERENCES",
                style = MaterialTheme.typography.labelSmall,
                color = ColorOnSurface60,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(ColorSurface)
            ) {
                // Battery Saver mapping
                SettingsRowWithSwitch(
                    icon = Icons.Default.BatterySaver,
                    iconColor = ColorHapticAccent,
                    title = "Haptic Battery Saver",
                    subtitle = if (state.batterySaverEnabled) "Saves 30% haptic energy" else "Standard mode",
                    checked = state.batterySaverEnabled,
                    onCheckedChange = onBatterySaverToggled
                )

                HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

                // Sleep timer — tap cycles Off → 15 → 30 → 60 → Off
                SettingsRow(
                    icon = Icons.Default.Bedtime,
                    iconColor = ColorHapticAccent,
                    title = "Sleep Timer",
                    subtitle = if (state.sleepTimerMinutes > 0) "Stops in ${state.sleepTimerMinutes} min" else "Off",
                    onClick = {
                        val next = when (state.sleepTimerMinutes) {
                            0 -> 15; 15 -> 30; 30 -> 60; else -> 0
                        }
                        onAction(HaptiqUiAction.SetSleepTimer(next))
                    }
                )

                HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

                // D4: Calibration row — navigates to Routes.CALIBRATION
                SettingsRow(
                    icon = Icons.Default.Vibration,
                    iconColor = ColorHapticAccent,
                    title = "Haptic Calibration",
                    subtitle = "Multiplier: ${state.calibrationMultiplier}x",
                    onClick = onCalibrationClicked
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Playback section
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "PLAYBACK",
                style = MaterialTheme.typography.labelSmall,
                color = ColorOnSurface60,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(ColorSurface)
            ) {
                SettingsRowWithSwitch(
                    icon = Icons.Default.TouchApp,
                    iconColor = ColorHapticAccent,
                    title = "Haptic Seek Preview",
                    subtitle = "Feel the bass under your finger while scrubbing",
                    checked = state.seekPreviewEnabled,
                    onCheckedChange = { onAction(HaptiqUiAction.SetSeekPreviewEnabled(it)) }
                )

                HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

                SettingsRow(
                    icon = Icons.Default.Equalizer,
                    iconColor = ColorOnSurface60,
                    title = "Equalizer",
                    subtitle = "Open the system equalizer",
                    onClick = { onAction(HaptiqUiAction.OpenEqualizer) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Library section
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "LIBRARY",
                style = MaterialTheme.typography.labelSmall,
                color = ColorOnSurface60,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(ColorSurface)
            ) {
                // Cycles Off → 15s → 30s → 60s, like the sleep timer
                SettingsRow(
                    icon = Icons.Default.Timer,
                    iconColor = ColorOnSurface60,
                    title = "Skip Short Audio",
                    subtitle = if (state.minDurationSec > 5) {
                        "Hiding tracks under ${state.minDurationSec}s (voice notes, clips)"
                    } else "Off — all audio shows in the library",
                    onClick = {
                        val next = when (state.minDurationSec) {
                            0, 5 -> 15; 15 -> 30; 30 -> 60; else -> 0
                        }
                        onAction(HaptiqUiAction.SetMinDuration(next))
                    }
                )

                HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

                SettingsRow(
                    icon = Icons.Default.Refresh,
                    iconColor = ColorOnSurface60,
                    title = "Rescan Library",
                    subtitle = if (state.isScanning) state.scanStatus
                    else "${state.songs.size} songs indexed",
                    onClick = { if (!state.isScanning) onAction(HaptiqUiAction.RescanLibrary) }
                )

                HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

                SettingsRow(
                    icon = Icons.Default.History,
                    iconColor = ColorOnSurface60,
                    title = "Clear Recently Played",
                    subtitle = "Empties the history row on the Library tab",
                    onClick = { onAction(HaptiqUiAction.ClearRecents) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Information section
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = "INFORMATION",
                style = MaterialTheme.typography.labelSmall,
                color = ColorOnSurface60,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(ColorSurface)
            ) {
                SettingsRow(
                    icon = Icons.Default.Info,
                    iconColor = ColorOnSurface60,
                    title = "About",
                    subtitle = "Haptiq ${com.haptiq.app.BuildConfig.RELEASE_CODENAME} " +
                        "v${com.haptiq.app.BuildConfig.VERSION_NAME} — " +
                        "${com.haptiq.app.BuildConfig.BUILD_CODENAME} @ ${com.haptiq.app.BuildConfig.GIT_SHA}"
                )

                HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

                SettingsRow(
                    icon = Icons.Default.Description,
                    iconColor = ColorOnSurface60,
                    title = "Open Source Licenses",
                    subtitle = "View third-party attributions",
                    onClick = { showLicenses = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xxl))
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            containerColor = ColorSurface,
            title = { Text("Open Source Licenses", color = ColorOnSurface) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        "Jetpack Compose — Apache 2.0",
                        "AndroidX Media3 (ExoPlayer) — Apache 2.0",
                        "AndroidX Room — Apache 2.0",
                        "Dagger Hilt — Apache 2.0",
                        "Coil — Apache 2.0",
                        "Kotlin Coroutines — Apache 2.0"
                    ).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorOnSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text("Close", color = ColorPrimary)
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(ComponentSize.iconMedium)
        )

        Spacer(modifier = Modifier.width(Spacing.md))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ColorOnSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorOnSurface60
                )
            }
        }

        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorOnSurface60,
                modifier = Modifier.size(ComponentSize.iconSmall)
            )
        }
    }
}

@Composable
fun SettingsRowWithSwitch(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(ComponentSize.iconMedium)
        )

        Spacer(modifier = Modifier.width(Spacing.md))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ColorOnSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorOnSurface60
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ColorSurface,
                checkedTrackColor = ColorHapticAccent,
                uncheckedThumbColor = ColorOnSurface60,
                uncheckedTrackColor = ColorSurfaceVariant
            )
        )
    }
}
