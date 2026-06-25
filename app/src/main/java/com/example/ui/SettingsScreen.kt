package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: HaptiqUiState,
    onBackClicked: () -> Unit,
    onBatterySaverToggled: (Boolean) -> Unit,
    onAction: (HaptiqUiAction) -> Unit
) {
    var showCalibrationSheet by remember { mutableStateOf(false) }

    if (showCalibrationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCalibrationSheet = false },
            containerColor = ColorSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = ColorOnSurface60) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Device Calibration",
                    style = MaterialTheme.typography.titleLarge,
                    color = ColorOnSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Let's calibrate the haptic engine for your device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ColorOnSurface60,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(
                    onClick = { onAction(HaptiqUiAction.RunTestPulse) },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant, contentColor = ColorOnSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Vibration, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play Test Pulse")
                }

                if (state.calibrationStep >= 2) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "How strong was that?",
                            style = MaterialTheme.typography.titleMedium,
                            color = ColorOnSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Weak", "Medium", "Strong").forEach { strength ->
                                FilterChip(
                                    selected = state.calibrationStrength == strength,
                                    onClick = { onAction(HaptiqUiAction.SelectCalibrationStrength(strength)) },
                                    label = { Text(strength) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ColorHapticAccent,
                                        selectedLabelColor = ColorBackground,
                                        containerColor = ColorBackground,
                                        labelColor = ColorOnSurface
                                    )
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onAction(HaptiqUiAction.SaveCalibration)
                        showCalibrationSheet = false
                    },
                    enabled = state.calibrationStep >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorHapticAccent, contentColor = ColorBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Save Profile", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        containerColor = ColorBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = ColorPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back",
                            tint = ColorOnSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ColorBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Preferences section
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        .clip(RoundedCornerShape(12.dp))
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

                    // Interactive calibration row
                    SettingsRow(
                        icon = Icons.Default.Vibration,
                        iconColor = ColorHapticAccent,
                        title = "Haptic Calibration",
                        subtitle = "Multiplier: ${state.calibrationMultiplier}x",
                        onClick = { showCalibrationSheet = true }
                    )
                }
            }

            // Information section
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorSurface)
                ) {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconColor = ColorOnSurface60,
                        title = "About",
                        subtitle = "Haptiq v1.0.0 (MVP)"
                    )
                }
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

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

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ColorOnSurface60,
            modifier = Modifier.size(20.dp)
        )
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

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
