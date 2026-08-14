package com.haptiq.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
    // A real Scaffold(topBar) — not a top bar as the first item inside the scrollable
    // Column below — so the back button stays reachable even if the step content
    // grows past one screen instead of scrolling away with it. Mirrors the same
    // Scaffold(topBar) structure HapticStudioScreen uses for its own push-screen header.
    Scaffold(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .testTag("calibration_screen"),
        containerColor = ColorBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { HaptiqWordmark(subtitle = "Device Calibration", centered = true) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = ColorOnSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ColorBackground
                )
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .padding(scaffoldPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
        ) {
            Text(
                text = "Let's calibrate the haptic engine for your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = ColorOnSurface60,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Step 1: Play Test Pulse
            val pulseInteraction = remember { MutableInteractionSource() }
            val isPulsePressed by pulseInteraction.collectIsPressedAsState()
            val pulseScale by animateFloatAsState(
                targetValue = if (isPulsePressed) 0.97f else 1f,
                animationSpec = HaptiqMotion.fastSpring(),
                label = "pulse_press_scale"
            )
            Button(
                onClick = onPlayTestPulse,
                interactionSource = pulseInteraction,
                colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant, contentColor = ColorOnSurface),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.buttonHeight)
                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
            ) {
                Icon(Icons.Default.Vibration, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
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
                    Spacer(Modifier.height(Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        listOf("Weak", "Medium", "Strong").forEach { strength ->
                            FilterChip(
                                selected = state.calibrationStrength == strength,
                                onClick = { onStrengthSelected(strength) },
                                label = { Text(strength) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ColorPrimary,
                                    selectedLabelColor = ColorOnPrimary,
                                    containerColor = ColorSurface,
                                    labelColor = ColorOnSurface
                                )
                            )
                        }
                    }
                }
            }

            // Step 3: Save Profile
            Spacer(Modifier.height(Spacing.xxl))
            val saveInteraction = remember { MutableInteractionSource() }
            val isSavePressed by saveInteraction.collectIsPressedAsState()
            val saveScale by animateFloatAsState(
                targetValue = if (isSavePressed) 0.97f else 1f,
                animationSpec = HaptiqMotion.fastSpring(),
                label = "save_press_scale"
            )
            Button(
                onClick = {
                    onSaveCalibration()
                    onBack()
                },
                enabled = state.calibrationStep >= 2,
                interactionSource = saveInteraction,
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.buttonHeight)
                    .graphicsLayer { scaleX = saveScale; scaleY = saveScale }
            ) {
                Text("Save Profile", fontWeight = FontWeight.Bold)
            }
        }
    }
}
