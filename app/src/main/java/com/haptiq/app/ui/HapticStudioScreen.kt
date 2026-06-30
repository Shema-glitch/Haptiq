package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haptiq.app.audio.HapticTuningState
import com.haptiq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticStudioScreen(
    state: HaptiqUiState,
    tuning: HapticTuningState,
    onBackClicked: () -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onPresetChanged: (String) -> Unit,
    onIntensityChanged: (Int) -> Unit,
    onTuningAction: (HaptiqUiAction) -> Unit
) {
    val presets = listOf(
        PresetChipData("deep_bass", "Deep Bass"),
        PresetChipData("punch", "Punch"),
        PresetChipData("concert", "Concert"),
        PresetChipData("soft_pulse", "Soft Pulse")
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .testTag("haptic_studio_screen"),
        containerColor = ColorBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HAPTIQ", style = MaterialTheme.typography.titleLarge, color = ColorPrimary, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("Haptic Studio", style = MaterialTheme.typography.labelSmall, color = ColorOnSurface60)
                    }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section 1: Engine Status Switch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(ColorSurfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = if (state.hapticActive) ColorHapticAccent else ColorOnSurface60,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Engine Status",
                                style = MaterialTheme.typography.titleMedium,
                                color = ColorOnSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.hapticActive) "Haptic feedback active" else "Haptic feedback suspended",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ColorOnSurface60
                            )
                        }
                    }

                    // Custom gold Switch
                    Switch(
                        checked = state.hapticActive,
                        onCheckedChange = onToggleHaptics,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorSurface,
                            checkedTrackColor = ColorHapticAccent,
                            uncheckedThumbColor = ColorOnSurface60,
                            uncheckedTrackColor = ColorSurfaceVariant
                        )
                    )
                }
            }

            // DND Warning Banner — show when DND is active and haptics are enabled
            if (state.isDndActive && state.hapticActive) {
                DndWarningBanner()
            }

            // Section 2: Haptic Presets row of chips
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "HAPTIC PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(presets) { preset ->
                        val isSelected = state.currentPresetId == preset.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPresetChanged(preset.id) },
                            label = {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ColorHapticAccent.copy(alpha = 0.12f),
                                selectedLabelColor = ColorHapticAccent,
                                selectedLeadingIconColor = ColorHapticAccent,
                                containerColor = ColorSurface,
                                labelColor = ColorOnSurface
                            )
                        )
                    }
                }
            }

            // Section 3: Intensity Slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Intensity",
                            style = MaterialTheme.typography.titleMedium,
                            color = ColorOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.intensity}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = ColorHapticAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Horizontal layout with range slider, decrement and increment buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Fix T2: increased from 36dp to 48dp for accessibility compliance
                        IconButton(
                            onClick = { onIntensityChanged((state.intensity - 5).coerceIn(0, 100)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease intensity", tint = ColorOnSurface60)
                        }

                        Slider(
                            value = state.intensity.toFloat(),
                            onValueChange = { onIntensityChanged(it.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorHapticAccent,
                                activeTrackColor = ColorHapticAccent,
                                inactiveTrackColor = ColorOutlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Fix T3: increased from 36dp to 48dp for accessibility compliance
                        IconButton(
                            onClick = { onIntensityChanged((state.intensity + 5).coerceIn(0, 100)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase intensity", tint = ColorOnSurface60)
                        }
                    }
                }
            }

            // Section 4: Real-time Feedback Visualization Canvas
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "REAL-TIME FEEDBACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // A1/A2: Semantic description for TalkBack accessibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorSurfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, ColorOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (state.hapticActive && state.isPlaying) {
                                "Haptic frequency visualization active. ${state.visualizerBands.size} frequency bands showing real-time haptic feedback."
                            } else {
                                "Haptic frequency visualization. ${state.intensity}% intensity. Currently idle."
                            }
                        }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .blur(40.dp)
                            .background(ColorHapticAccent.copy(alpha = 0.1f), CircleShape)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        state.visualizerBands.forEachIndexed { index, energy ->
                            val barScale = if (state.hapticActive && state.isPlaying) energy else 0.15f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(barScale)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(ColorHapticAccent, ColorHapticAccent.copy(alpha = 0.4f))
                                        ),
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                        }
                    }
                }
            }

            // Section 5: Haptic Tuning Dashboard
            TuningDashboardCard(tuning = tuning, onAction = onTuningAction)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TuningDashboardCard(
    tuning: HapticTuningState,
    onAction: (HaptiqUiAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "chevron_rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorOutline, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        // Header row — always visible, tapping it toggles the panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = ColorHapticAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "TUNING DASHBOARD",
                    style = MaterialTheme.typography.labelMedium,
                    color = ColorOnSurface,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = ColorOnSurface60,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation)
            )
        }

        if (expanded) {
            HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.4f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── ENGINE MUTES ─────────────────────────────────────────────────────
                Text(
                    text = "ENGINES",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EngineToggleChip(
                        label = "KICK",
                        icon = Icons.Default.FlashOn,
                        enabled = tuning.isKickEnabled,
                        modifier = Modifier.weight(1f),
                        onToggle = { onAction(HaptiqUiAction.SetKickEnabled(it)) }
                    )
                    EngineToggleChip(
                        label = "BASS",
                        icon = Icons.Default.GraphicEq,
                        enabled = tuning.isBassEnabled,
                        modifier = Modifier.weight(1f),
                        onToggle = { onAction(HaptiqUiAction.SetBassEnabled(it)) }
                    )
                }

                // ── KICK FREQUENCY BINS ──────────────────────────────────────────────
                TuningSliderRow(
                    label = "KICK FREQ MIN BIN",
                    value = tuning.kickFreqMinBin.toFloat(),
                    range = 1f..15f,
                    displayValue = "Bin ${tuning.kickFreqMinBin}  (~${tuning.kickFreqMinBin * 43} Hz)",
                    onValueChange = {
                        onAction(HaptiqUiAction.SetKickFreqRange(
                            it.toInt().coerceIn(1, tuning.kickFreqMaxBin),
                            tuning.kickFreqMaxBin
                        ))
                    }
                )
                TuningSliderRow(
                    label = "KICK FREQ MAX BIN",
                    value = tuning.kickFreqMaxBin.toFloat(),
                    range = 1f..15f,
                    displayValue = "Bin ${tuning.kickFreqMaxBin}  (~${tuning.kickFreqMaxBin * 43} Hz)",
                    onValueChange = {
                        onAction(HaptiqUiAction.SetKickFreqRange(
                            tuning.kickFreqMinBin,
                            it.toInt().coerceAtLeast(tuning.kickFreqMinBin)
                        ))
                    }
                )

                // ── THRESHOLD SLIDERS ────────────────────────────────────────────────
                TuningSliderRow(
                    label = "KICK THRESHOLD",
                    value = tuning.kickThreshold,
                    range = 0.01f..0.50f,
                    displayValue = String.format("%.2f", tuning.kickThreshold),
                    onValueChange = { onAction(HaptiqUiAction.SetKickThreshold(it)) }
                )
                TuningSliderRow(
                    label = "NOISE FLOOR GATE",
                    value = tuning.noiseFloorGate,
                    range = 0.30f..0.95f,
                    displayValue = String.format("%.2f", tuning.noiseFloorGate),
                    onValueChange = { onAction(HaptiqUiAction.SetNoiseFloorGate(it)) }
                )
                TuningSliderRow(
                    label = "SUB DRONE THRESHOLD",
                    value = tuning.subDroneThreshold,
                    range = 0.50f..0.99f,
                    displayValue = String.format("%.2f", tuning.subDroneThreshold),
                    onValueChange = { onAction(HaptiqUiAction.SetSubDroneThreshold(it)) }
                )
            }
        }
    }
}

@Composable
private fun EngineToggleChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val bgColor = if (enabled) ColorHapticAccent else ColorSurface
    val contentColor = if (enabled) ColorOnPrimary else ColorOnSurface60

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.height(24.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = ColorSurface,
                checkedTrackColor = ColorOnPrimary,
                uncheckedThumbColor = ColorOnSurface60,
                uncheckedTrackColor = ColorSurfaceVariant
            )
        )
    }
}

@Composable
private fun TuningSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ColorOnSurface60,
                letterSpacing = 1.sp
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelSmall,
                color = ColorHapticAccent,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ColorHapticAccent,
                activeTrackColor = ColorHapticAccent,
                inactiveTrackColor = ColorOutlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

data class PresetChipData(val id: String, val label: String)

