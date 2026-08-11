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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
                title = { HaptiqWordmark(subtitle = "Haptic Studio", centered = true) },
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
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            // ─── Hero: live visualizer with the engine switch on it ───
            // The one amber moment of the screen. Seeing the music move and
            // flipping the engine happen in the same place — status IS the
            // visualization, so no separate "Engine Status" card needed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    // No clipped card here: the visualizer floats on the screen itself,
                    // with a radial wash that fades to transparent so there's no box edge.
                    .background(
                        Brush.radialGradient(
                            listOf(ColorSurfaceVariant.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (state.hapticActive && state.isPlaying) {
                            "Haptic engine live. Real-time frequency bands are animating."
                        } else if (state.hapticActive) {
                            "Haptic engine on, waiting for playback."
                        } else {
                            "Haptic engine off."
                        }
                    }
            ) {
                // Ambient glow behind the bars — only when alive. Radial gradient,
                // not Modifier.blur (blur clips to rectangular bounds → visible box).
                // Not fixed either: it swells with the music's real energy and
                // drifts slowly side to side, so the hero reads as breathing.
                if (state.hapticActive) {
                    val energy = state.visualizerBands.average().toFloat()
                    val glowScale by animateFloatAsState(
                        targetValue = if (state.isPlaying) 0.85f + energy * 0.7f else 0.85f,
                        animationSpec = HaptiqMotion.standardSpring(),
                        label = "studio_glow_scale"
                    )
                    // Slow lateral drift — the hero reads as breathing. Frozen under
                    // reduced motion; the energy-driven glowScale swell still runs
                    // (it's tied to the music, not ambient decoration).
                    val driftX = if (isReducedMotionEnabled()) 0f else {
                        val glowDrift = rememberInfiniteTransition(label = "studio_glow_drift")
                        val dx by glowDrift.animateFloat(
                            initialValue = -32f,
                            targetValue = 32f,
                            animationSpec = infiniteRepeatable(
                                tween(5200, easing = FastOutSlowInEasing),
                                RepeatMode.Reverse
                            ),
                            label = "studio_glow_drift_x"
                        )
                        dx
                    }
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .align(Alignment.Center)
                            .offset(x = driftX.dp)
                            .scale(glowScale)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        ColorHapticAccent.copy(alpha = 0.14f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                // Frequency bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    state.visualizerBands.forEach { energy ->
                        val barScale = if (state.hapticActive && state.isPlaying) energy.coerceAtLeast(0.06f) else 0.1f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(barScale)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            if (state.hapticActive) ColorHapticAccent else ColorOnSurface60,
                                            (if (state.hapticActive) ColorHapticAccent else ColorOnSurface60).copy(alpha = 0.3f)
                                        )
                                    ),
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                    }
                }
                // Status + engine switch overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (state.hapticActive) ColorHapticAccent else ColorOnSurface60,
                                    CircleShape
                                )
                        )
                        Text(
                            text = when {
                                state.hapticActive && state.isPlaying -> "Engine live"
                                state.hapticActive -> "Engine armed — play a song"
                                else -> "Engine off"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.hapticActive) ColorOnSurface else ColorOnSurface60
                        )
                    }
                    Switch(
                        checked = state.hapticActive,
                        onCheckedChange = onToggleHaptics,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorSurface,
                            checkedTrackColor = ColorPrimary,
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
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "HAPTIC PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.xxs)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
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
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ColorPrimary.copy(alpha = 0.12f),
                                selectedLabelColor = ColorPrimary,
                                selectedLeadingIconColor = ColorPrimary,
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
                shape = RoundedCornerShape(Radius.md)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
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
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Fix T2: increased from 36dp to 48dp for accessibility compliance
                        IconButton(
                            onClick = { onIntensityChanged((state.intensity - 5).coerceIn(0, 100)) },
                            modifier = Modifier.size(ComponentSize.touchTarget)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease intensity", tint = ColorOnSurface60)
                        }

                        Slider(
                            value = state.intensity.toFloat(),
                            onValueChange = { onIntensityChanged(it.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorPrimary,
                                activeTrackColor = ColorPrimary,
                                inactiveTrackColor = ColorOutlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Fix T3: increased from 36dp to 48dp for accessibility compliance
                        IconButton(
                            onClick = { onIntensityChanged((state.intensity + 5).coerceIn(0, 100)) },
                            modifier = Modifier.size(ComponentSize.touchTarget)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase intensity", tint = ColorOnSurface60)
                        }
                    }
                }
            }

            // Section 5: Haptic Tuning Dashboard
            TuningDashboardCard(
                tuning = tuning,
                aotMapInfo = state.aotMapInfo,
                onAction = onTuningAction
            )

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TuningDashboardCard(
    tuning: HapticTuningState,
    aotMapInfo: AotMapInfo,
    onAction: (HaptiqUiAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = HaptiqMotion.fastSpring(),
        label = "chevron_rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorOutline, RoundedCornerShape(Radius.md)),
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(Radius.md)
    ) {
        // Header row — always visible, tapping it toggles the panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .heightIn(min = ComponentSize.touchTarget)
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = ColorHapticAccent,
                    modifier = Modifier.size(ComponentSize.iconSmall)
                )
                Text(
                    text = "TUNING DASHBOARD",
                    style = MaterialTheme.typography.labelMedium,
                    color = ColorOnSurface,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (expanded) {
                    // Was a fixed 32dp height, well under the 48dp touch-target minimum —
                    // let the default TextButton min-height apply instead of overriding it.
                    TextButton(
                        onClick = { onAction(HaptiqUiAction.ResetTuning) },
                        contentPadding = PaddingValues(horizontal = Spacing.sm)
                    ) {
                        Text(
                            "RESET",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorPrimary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = ColorOnSurface60,
                    modifier = Modifier
                        .size(ComponentSize.iconSmall)
                        .rotate(chevronRotation)
                )
            }
        }

        if (expanded) {
            HorizontalDivider(color = ColorOutlineVariant.copy(alpha = 0.4f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {

                // ── ADAPTIVE MODE ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(
                            if (tuning.isAdaptiveEnabled) ColorHapticAccent.copy(alpha = 0.10f)
                            else ColorSurfaceVariant.copy(alpha = 0.4f)
                        )
                        .padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (tuning.isAdaptiveEnabled) ColorHapticAccent else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconSmall)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Adaptive Haptics",
                            style = MaterialTheme.typography.titleSmall,
                            color = ColorOnSurface
                        )
                        Text(
                            if (tuning.isAdaptiveEnabled)
                                "Gates self-calibrate to each song's energy — sliders below are bypassed"
                            else
                                "Manual mode — the threshold sliders below are in control",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorOnSurface60
                        )
                    }
                    Switch(
                        checked = tuning.isAdaptiveEnabled,
                        onCheckedChange = { onAction(HaptiqUiAction.SetAdaptiveEnabled(it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorSurface,
                            checkedTrackColor = ColorPrimary,
                            uncheckedThumbColor = ColorOnSurface60,
                            uncheckedTrackColor = ColorSurfaceVariant
                        )
                    )
                }

                // ── AOT LOOKAHEAD ────────────────────────────────────────────────
                // Pre-fires mapped kicks ~50ms before the audio hit so the motor is
                // already moving when the bass lands (live FFT detection is late by
                // about that much). The per-song status line doubles as the on-device
                // sanity check for the onset detector: a heavy track should show a
                // healthy kick count, a ballad should show "No map".
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(
                            if (tuning.isAotLookaheadEnabled) ColorHapticAccent.copy(alpha = 0.10f)
                            else ColorSurfaceVariant.copy(alpha = 0.4f)
                        )
                        .padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (tuning.isAotLookaheadEnabled) ColorHapticAccent else ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconSmall)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "AOT Lookahead",
                            style = MaterialTheme.typography.titleSmall,
                            color = ColorOnSurface
                        )
                        Text(
                            text = when (aotMapInfo.state) {
                                AotMapState.NOT_ENABLED ->
                                    "Off - kicks fire from live detection (~50ms late)"
                                AotMapState.ANALYZING ->
                                    "Analyzing this song's kicks..."
                                AotMapState.READY ->
                                    "Ready - ${aotMapInfo.onBeatCount} of ${aotMapInfo.onsetCount} kicks on-beat, pre-firing early"
                                AotMapState.NO_MAP ->
                                    "No kick map for this song - live detection only"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorOnSurface60
                        )
                    }
                    Switch(
                        checked = tuning.isAotLookaheadEnabled,
                        onCheckedChange = { onAction(HaptiqUiAction.SetAotLookaheadEnabled(it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorSurface,
                            checkedTrackColor = ColorPrimary,
                            uncheckedThumbColor = ColorOnSurface60,
                            uncheckedTrackColor = ColorSurfaceVariant
                        )
                    )
                }

                // ── ENGINE MUTES ─────────────────────────────────────────────────────
                Text(
                    text = "ENGINES",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                        enabled = false,
                        locked = true,
                        modifier = Modifier.weight(1f),
                        onToggle = {}
                    )
                }

                // ── KICK TUNING ──────────────────────────────────────────────
                Text(
                    text = "KICK TRANSIENTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp
                )
                TuningSliderRow(
                    label = "KICK INTENSITY",
                    value = tuning.kickGain,
                    range = 0.5f..2.0f,
                    displayValue = String.format("%.1fx", tuning.kickGain),
                    description = "Strength of the kick punch. Works in both adaptive and manual mode.",
                    onValueChange = { onAction(HaptiqUiAction.SetKickGain(it)) }
                )
                TuningSliderRow(
                    label = "KICK THRESHOLD",
                    value = tuning.kickThreshold,
                    range = 0.01f..0.50f,
                    displayValue = String.format("%.2f", tuning.kickThreshold),
                    description = "Sensitivity of kick detection. Lower values trigger more easily on subtle beats.",
                    onValueChange = { onAction(HaptiqUiAction.SetKickThreshold(it)) }
                )
                TuningSliderRow(
                    label = "KICK FREQ MIN BIN",
                    value = tuning.kickFreqMinBin.toFloat(),
                    range = 1f..15f,
                    displayValue = "Bin ${tuning.kickFreqMinBin} (~${tuning.kickFreqMinBin * 43}Hz)",
                    description = "Lowest frequency to watch for kick impact.",
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
                    displayValue = "Bin ${tuning.kickFreqMaxBin} (~${tuning.kickFreqMaxBin * 43}Hz)",
                    description = "Highest frequency included in kick transient analysis.",
                    onValueChange = {
                        onAction(HaptiqUiAction.SetKickFreqRange(
                            tuning.kickFreqMinBin,
                            it.toInt().coerceAtLeast(tuning.kickFreqMinBin)
                        ))
                    }
                )

                // ── BASS TUNING ────────────────────────────────────────────────
                // The drone engine is disabled app-wide while it's reworked (it caused
                // the v1.11 stuck-buzz regression) — sliders that tune a dead engine
                // are worse than no sliders, so this section is a placeholder, not a
                // disabled copy of the old controls. Kick is the one supported engine
                // for now.
                Text(
                    text = "BASS DRONE",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(ColorSurface)
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            Icons.Default.Construction,
                            contentDescription = null,
                            tint = ColorOnSurface60,
                            modifier = Modifier.size(ComponentSize.iconSmall)
                        )
                        Text(
                            "Coming back soon",
                            style = MaterialTheme.typography.labelMedium,
                            color = ColorOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "The sustained bass rumble is off while we rebuild it to feel " +
                            "like a real speaker wave instead of a buzz. Kick is carrying " +
                            "the full haptic feel for now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorOnSurface60,
                        lineHeight = 16.sp
                    )
                }

                // ── GLOBAL ────────────────────────────────────────────────
                Text(
                    text = "GLOBAL GATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    letterSpacing = 1.5.sp
                )
                TuningSliderRow(
                    label = "NOISE FLOOR GATE",
                    value = tuning.noiseFloorGate,
                    range = 0.30f..0.95f,
                    displayValue = String.format("%.2f", tuning.noiseFloorGate),
                    description = "Master energy filter. Prevents noise or quiet parts from triggering any haptics.",
                    onValueChange = { onAction(HaptiqUiAction.SetNoiseFloorGate(it)) }
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
    // Locked engines (bass, mid-rework) show a "SOON" badge instead of a live switch
    // and ignore taps — the toggle exists in code but isn't a real control from here.
    locked: Boolean = false,
    onToggle: (Boolean) -> Unit
) {
    val bgColor = if (enabled) ColorHapticAccent else ColorSurface
    val contentColor = if (locked) ColorOnSurface60.copy(alpha = 0.6f) else if (enabled) ColorOnPrimary else ColorOnSurface60

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(bgColor)
            .then(if (locked) Modifier else Modifier.clickable { onToggle(!enabled) })
            // Was ~40dp tall (icon + 10dp*2 padding) — under the 48dp touch-target
            // minimum for an interactive toggle row.
            .heightIn(min = ComponentSize.touchTarget)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(ComponentSize.iconSmall))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        if (locked) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = ColorSurfaceVariant
            ) {
                Text(
                    text = "SOON",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorOnSurface60,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                )
            }
        } else {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.height(Spacing.xl),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ColorSurface,
                    checkedTrackColor = ColorOnPrimary,
                    uncheckedThumbColor = ColorOnSurface60,
                    uncheckedTrackColor = ColorSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun TuningSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    description: String? = null,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ColorOnSurface,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelSmall,
                color = ColorHapticAccent,
                fontWeight = FontWeight.Bold
            )
        }

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorOnSurface60,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ColorPrimary,
                activeTrackColor = ColorPrimary,
                inactiveTrackColor = ColorOutlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

data class PresetChipData(val id: String, val label: String)

