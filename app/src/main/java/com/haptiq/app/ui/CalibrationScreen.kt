package com.haptiq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haptiq.app.ui.theme.*

/**
 * Calibration screen — wrapped by [com.haptiq.app.navigation.HaptiqScaffold]
 * for bottom navigation. The bottom nav highlights "Settings" while on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    state: HaptiqUiState,
    innerPadding: PaddingValues,
    onPlayTestPulse: () -> Unit,
    onStrengthSelected: (String) -> Unit,
    onSaveCalibration: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .testTag("calibration_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Top Bar with back navigation
        CenterAlignedTopAppBar(
            title = {
                Text("Device Calibration", style = MaterialTheme.typography.titleLarge)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = ColorBackground,
                titleContentColor = ColorOnSurface,
                navigationIconContentColor = ColorOnSurface
            )
        )

        Text(
            text = "Let's calibrate the haptic engine for your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = ColorOnSurface60,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Step 1: Play Test Pulse
        Button(
            onClick = onPlayTestPulse,
            colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant, contentColor = ColorOnSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.Vibration, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Play Test Pulse")
        }

        // Step 2: Perceived strength
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
                            onClick = { onStrengthSelected(strength) },
                            label = { Text(strength) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ColorHapticAccent,
                                selectedLabelColor = ColorOnBackground,
                                containerColor = ColorSurface,
                                labelColor = ColorOnSurface
                            )
                        )
                    }
                }
            }
        }

        // Step 3: Save Profile
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                onSaveCalibration()
                onBack()
            },
            enabled = state.calibrationStep >= 2,
            colors = ButtonDefaults.buttonColors(containerColor = ColorHapticAccent, contentColor = ColorOnBackground),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Save Profile", fontWeight = FontWeight.Bold)
        }
    }
}
