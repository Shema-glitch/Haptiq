package com.haptiq.app.ui

import android.os.VibrationEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.haptiq.app.audio.DeviceVibrator
import com.haptiq.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val hint: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.Vibration,
        title = "Music you can touch",
        body = "Haptiq reads the bass in your songs live and drives your phone's motor in sync. Every kick. Every drop. Felt.",
        hint = "Tap it — feel a beat"
    ),
    OnboardingPage(
        icon = Icons.Default.GraphicEq,
        title = "Your own Haptic Studio",
        body = "Every motor is different. Tune kicks, drones and sensitivity until the feel is exactly yours — or let Adaptive mode calibrate itself.",
        hint = "Tap to feel a kick"
    ),
    OnboardingPage(
        icon = Icons.Default.TouchApp,
        title = "Feel the drop before it lands",
        body = "Scrub the timeline and the bass pulses under your finger. Find the drop by feel — no other player does this.",
        hint = "Tap to feel the drop"
    )
)

/**
 * First-run pitch, shown BEFORE any permission dialog. Users who understand what
 * the app is for grant permissions; users ambushed by a system dialog uninstall.
 *
 * The pitch IS the product demo: every page visual is tappable and answers with
 * a real vibration pattern — the app sells touch, so the onboarding must touch back.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1
    val haptics = LocalHapticFeedback.current

    // Soft tick when a page settles — the swipe itself gets tactile feedback
    LaunchedEffect(pagerState.settledPage) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Ambient glow — a radial gradient that fades to transparent (NOT Modifier.blur:
    // blur clips to rectangular bounds and rendered as a visible box on-device).
    // A slow breathing scale keeps the background alive without stealing attention.
    val ambient = rememberInfiniteTransition(label = "onboarding_ambient")
    val glowScale by ambient.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "glow_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .systemBarsPadding()
            .testTag("onboarding_screen")
    ) {
        // The glow wanders with the pager: each page has its own corner anchor and
        // the position interpolates continuously with the swipe offset, so the
        // background travels WITH the gesture instead of sitting fixed.
        val glowAnchors = listOf(
            1.4f to -1.2f,   // page 0: beyond top-end
            -1.4f to -0.1f,  // page 1: off the start edge, upper middle
            1.4f to 0.6f     // page 2: off the end edge, lower middle
        )
        val pageProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction
        val segment = pageProgress.toInt().coerceIn(0, glowAnchors.size - 2)
        val segmentFraction = (pageProgress - segment).coerceIn(0f, 1f)
        val (fromX, fromY) = glowAnchors[segment]
        val (toX, toY) = glowAnchors[segment + 1]
        val glowBias = BiasAlignment(
            lerp(fromX, toX, segmentFraction),
            lerp(fromY, toY, segmentFraction)
        )
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(glowBias)
                .scale(glowScale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ColorHapticAccent.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Skip — always available, lands on the same permission rationale
        TextButton(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.sm)
                .testTag("onboarding_skip")
        ) {
            Text("Skip", color = ColorOnSurface60, style = MaterialTheme.typography.labelLarge)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                // Parallax + fade driven by how far the page is from center
                val pageOffset = (pagerState.currentPage - pageIndex) +
                    pagerState.currentPageOffsetFraction
                OnboardingPageContent(
                    page = pages[pageIndex],
                    pageIndex = pageIndex,
                    pageOffset = pageOffset
                )
            }

            // Page dots — width and color spring between states
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(bottom = Spacing.xl)
            ) {
                repeat(pages.size) { i ->
                    val selected = pagerState.currentPage == i
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        animationSpec = HaptiqMotion.expressiveSpring(),
                        label = "dot_width"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (selected) ColorPrimary else ColorOutlineVariant,
                        label = "dot_color"
                    )
                    Box(
                        modifier = Modifier
                            .width(dotWidth)
                            .height(8.dp)
                            .background(dotColor, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { scope.launch { pagerState.animateScrollToPage(i, animationSpec = tween(620, easing = FastOutSlowInEasing)) } }
                    )
                }
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isLastPage) {
                        onDone()
                    } else {
                        // Slower, eased page glide so the parallax + traveling glow
                        // read as a deliberate transition, not a hard snap.
                        scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1,
                                animationSpec = tween(620, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .height(ComponentSize.buttonHeight)
                    .testTag("onboarding_next"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorPrimary,
                    contentColor = ColorOnPrimary
                ),
                shape = RoundedCornerShape(Radius.pill)
            ) {
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "cta_label"
                ) { last ->
                    Text(
                        text = if (last) "Get started" else "Next",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    pageIndex: Int,
    pageOffset: Float
) {
    val context = LocalContext.current
    val vibrator = remember { DeviceVibrator.get(context) }
    val scope = rememberCoroutineScope()

    // Tap burst — an extra ring that fires outward when the visual is tapped
    val burst = remember { Animatable(0f) }

    fun feelIt() {
        val effect = when (pageIndex) {
            // A short beat: thump — pause — thump
            0 -> VibrationEffect.createWaveform(
                longArrayOf(0, 45, 110, 45), intArrayOf(0, 255, 0, 180), -1
            )
            // One hard kick
            1 -> VibrationEffect.createWaveform(
                longArrayOf(0, 60), intArrayOf(0, 255), -1
            )
            // A rising drop: soft → hard
            else -> VibrationEffect.createWaveform(
                longArrayOf(0, 30, 60, 40, 60, 70), intArrayOf(0, 80, 0, 160, 0, 255), -1
            )
        }
        try {
            vibrator?.vibrate(effect)
        } catch (_: Exception) { /* some OEM ROMs throw on amplitude control */ }
        scope.launch {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(600, easing = LinearOutSlowInEasing))
        }
    }

    // Fade + drift sideways as the page scrolls out — content moves slower than
    // the pager itself (parallax), which reads as depth instead of a flat slide.
    val clampedOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = pageOffset * size.width * -0.25f
                alpha = lerp(1f, 0.3f, clampedOffset)
                val s = lerp(1f, 0.92f, clampedOffset)
                scaleX = s
                scaleY = s
            }
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = page.hint
                ) { feelIt() },
            contentAlignment = Alignment.Center
        ) {
            when (pageIndex) {
                0 -> PulseVisual(icon = page.icon)
                1 -> StudioVisual()
                else -> WaveformScrubVisual()
            }

            // Burst ring on tap — shared across pages
            if (burst.value > 0f && burst.value < 1f) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(1f + burst.value * 0.9f)
                        .alpha(1f - burst.value)
                        .border(2.dp, ColorHapticAccent, CircleShape)
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = page.hint,
            style = MaterialTheme.typography.labelMedium,
            color = ColorHapticAccent.copy(alpha = 0.8f),
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = ColorOnBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = ColorOnSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

/** Page 1 — the brand icon with two staggered breathing haptic rings. */
@Composable
private fun PulseVisual(icon: ImageVector) {
    val transition = rememberInfiniteTransition(label = "pulse_visual")
    val ringA by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing)),
        label = "ring_a"
    )
    val ringB by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearOutSlowInEasing),
            initialStartOffset = StartOffset(900)
        ),
        label = "ring_b"
    )

    Box(contentAlignment = Alignment.Center) {
        listOf(ringA, ringB).forEach { t ->
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(1f + t * 0.6f)
                    .alpha((1f - t) * 0.5f)
                    .border(1.5.dp, ColorHapticAccent, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(
                        listOf(ColorSurfaceVariant, ColorSurface)
                    ),
                    CircleShape
                )
                .border(1.dp, ColorOutlineVariant.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ColorHapticAccent,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

/** Page 2 — three tuning sliders whose thumbs drift on their own, like a live studio. */
@Composable
private fun StudioVisual() {
    val transition = rememberInfiniteTransition(label = "studio_visual")
    val phases = listOf(
        transition.animateFloat(
            0.25f, 0.85f,
            infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "slider_0"
        ),
        transition.animateFloat(
            0.75f, 0.2f,
            infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "slider_1"
        ),
        transition.animateFloat(
            0.4f, 0.95f,
            infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "slider_2"
        )
    )

    Column(
        modifier = Modifier
            .size(width = 170.dp, height = 130.dp)
            .background(ColorSurface, RoundedCornerShape(Radius.lg))
            .border(1.dp, ColorOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(Radius.lg))
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically)
    ) {
        phases.forEach { phase ->
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                val trackY = size.height / 2f
                drawLine(
                    color = ColorOutlineVariant,
                    start = Offset(0f, trackY),
                    end = Offset(size.width, trackY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val x = size.width * phase.value
                drawLine(
                    color = ColorHapticAccent,
                    start = Offset(0f, trackY),
                    end = Offset(x, trackY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = ColorHapticAccent,
                    radius = 6.dp.toPx(),
                    center = Offset(x, trackY)
                )
            }
        }
    }
}

/** Page 3 — a bass waveform with a playhead sweeping across it, previewing seek-by-feel. */
@Composable
private fun WaveformScrubVisual() {
    val transition = rememberInfiniteTransition(label = "scrub_visual")
    val playhead by transition.animateFloat(
        initialValue = 0.05f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "playhead"
    )
    // Deterministic pseudo-waveform — peaks toward the end so the "drop" reads visually
    val bars = remember {
        List(26) { i ->
            val t = i / 25f
            (0.25f + 0.35f * sin(i * 1.7f).absoluteValue + 0.4f * t).coerceAtMost(1f)
        }
    }

    Canvas(
        modifier = Modifier.size(width = 180.dp, height = 110.dp)
    ) {
        val barW = 3.5.dp.toPx()
        val gap = (size.width - bars.size * barW) / (bars.size - 1)
        bars.forEachIndexed { i, energy ->
            val x = i * (barW + gap)
            val h = energy * size.height * 0.8f
            val played = (i + 0.5f) / bars.size <= playhead
            drawRoundRect(
                color = if (played) ColorHapticAccent else ColorOutlineVariant,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
        // Thumb dot riding the waveform at the playhead
        val thumbX = size.width * playhead
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(thumbX, size.height / 2f)
        )
        drawCircle(
            color = ColorHapticAccent,
            radius = 7.dp.toPx(),
            center = Offset(thumbX, size.height / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}
