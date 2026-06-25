package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticStudioScreen(
    state: HaptiqUiState,
    onBackClicked: () -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onPresetChanged: (String) -> Unit,
    onIntensityChanged: (Int) -> Unit
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
            .testTag("haptic_studio_screen"),
        containerColor = ColorBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Haptic Studio",
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
                        IconButton(
                            onClick = { onIntensityChanged((state.intensity - 5).coerceIn(0, 100)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", tint = ColorOnSurface60)
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

                        IconButton(
                            onClick = { onIntensityChanged((state.intensity + 5).coerceIn(0, 100)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", tint = ColorOnSurface60)
                        }
                    }
                }
            }

            // Section 4: Real-time Feedback Visualization Canvas
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "REAL-TIME FEEDBACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorSurfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, ColorOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Back glow
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .blur(40.dp)
                            .background(ColorHapticAccent.copy(alpha = 0.1f), CircleShape)
                    )

                    // 12 Vertical bars representing frequencies
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        state.visualizerBands.forEachIndexed { index, energy ->
                            // Scale height by the visualizer frequency
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
        }
    }
}

data class PresetChipData(val id: String, val label: String)
