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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .testTag("settings_screen")
    ) {
        HaptiqScreenHeader(subtitle = "Settings")

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
                    subtitle = "Haptiq v1.0.0 (MVP)"
                )

                HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

                SettingsRow(
                    icon = Icons.Default.Description,
                    iconColor = ColorOnSurface60,
                    title = "Open Source Licenses",
                    subtitle = "View third-party attributions"
                    // NOTE: no onClick wired yet — SettingsRow renders without a
                    // chevron/ripple when onClick is null. Wire this to a real
                    // licenses screen (e.g. androidx OssLicensesMenuActivity or a
                    // custom screen) rather than leaving it silently unclickable.
                )
            }
        }
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
