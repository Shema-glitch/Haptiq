package com.haptiq.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.haptiq.app.data.Song
import com.haptiq.app.ui.theme.*
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Now Playing as a persistent overlay above whatever tab/route is currently showing,
 * instead of a full-screen nav destination. That distinction is the whole fix: this
 * composable stays mounted alongside the NavHost (see HaptiqNavHost), so the screen
 * behind it during a drag is the ACTUAL library/tab content, already composed and
 * live — not a blank window, which is what happened when Now Playing was its own
 * route and got popped off the back stack while dragging revealed nothing behind it.
 *
 * One Animatable [progress] (0 = docked mini-player bar, 1 = full screen) drives the
 * container's size/position/corners AND a content cross-fade between the compact mini
 * row and the full [PlayerScreen]. A single drag gesture manipulates it directly in
 * both directions — dragging up from the bar expands with 1:1 finger tracking, drag
 * down from full collapses the same way — so "swipe up" is a continuous morph, not a
 * binary trigger that either does nothing or jumps.
 */
@Composable
fun NowPlayingSheet(
    state: HaptiqUiState,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    hasBottomNav: Boolean,
    onTogglePlayPause: () -> Unit,
    onNextClicked: () -> Unit,
    onPrevClicked: () -> Unit,
    onDismissPlayback: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekPreview: (Float) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onHapticStudioClicked: () -> Unit,
    onSongSelected: (List<Song>, Int) -> Unit,
    onQueueAction: (HaptiqUiAction) -> Unit,
    onRefreshDndStatus: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetVolume: (Float) -> Unit
) {
    val currentSong = state.currentSong ?: return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val morphSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)

    val progress = remember { Animatable(if (isExpanded) 1f else 0f) }

    // External state (system back button, tapping the bar) syncs IN, but never fights
    // an in-flight drag — the gesture handler is the only thing allowed to interrupt
    // a running animation with a new one.
    LaunchedEffect(isExpanded) {
        val target = if (isExpanded) 1f else 0f
        if (progress.value != target && !progress.isRunning) {
            progress.animateTo(target, morphSpring)
        }
    }

    BackHandler(enabled = isExpanded) { onExpandedChange(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val miniHeightPx = with(density) { ComponentSize.miniPlayerHeight.toPx() }
        // Px of drag that spans the full 0..1 range — the distance between "docked
        // bar" and "full screen" — so the sheet tracks the finger 1:1, not a fraction
        // of an arbitrary threshold.
        val travelPx = (maxHeightPx - miniHeightPx).coerceAtLeast(1f)

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Matches HaptiqBottomNav's own layout (navBarHeight + its vertical padding)
        // plus the small gap the old in-column MiniPlayer sat above it with, so the
        // docked bar lands in the same place it used to.
        val bottomClearance = if (hasBottomNav) {
            bottomInset + ComponentSize.navBarHeight + Spacing.sm * 2 + Spacing.xs
        } else {
            bottomInset + Spacing.md
        }

        // The settle spring is underdamped (dampingRatio < 1) so a fast fling can
        // briefly overshoot past 0/1 — clamp before feeding any dimension lerp, or a
        // negative corner radius crashes RoundedCornerShape.
        val p = progress.value.coerceIn(0f, 1f)
        val heightDp = lerp(ComponentSize.miniPlayerHeight, maxHeight, p)
        val marginDp = lerp(Spacing.md, 0.dp, p)
        val bottomDp = lerp(bottomClearance, 0.dp, p)
        val cornerDp = lerp(Radius.lg, 0.dp, p)
        val topOffsetDp = maxHeight - bottomDp - heightDp

        // Unclamped drag accumulator in px, seeded from the current progress at drag
        // start. Letting it go negative past the docked floor ("overscroll") is what
        // lets a further downward drag on the bar register as dismiss-playback
        // instead of doing nothing once progress already hit 0.
        var rawDrag by remember { mutableStateOf(progress.value * travelPx) }

        Box(
            modifier = Modifier
                .offset(x = marginDp, y = topOffsetDp)
                .width(maxWidth - marginDp * 2)
                .height(heightDp)
                .shadow(if (p < 0.98f) Elevation.high else 0.dp, RoundedCornerShape(cornerDp))
                .clip(RoundedCornerShape(cornerDp))
                .pointerInput(currentSong.id) {
                    var totalX = 0f
                    var totalY = 0f
                    val velocityTracker = VelocityTracker()
                    // YT Music-style: even a short, quick flick is enough to throw the
                    // sheet open/closed — the settle threshold below is a fallback for
                    // slow drags that stop mid-way, not the primary way to trigger it.
                    val flingVelocityPxPerSec = 700.dp.toPx()
                    val skipThresholdPx = 88.dp.toPx()
                    val dismissOverscrollPx = 90.dp.toPx()
                    detectDragGestures(
                        onDragStart = {
                            totalX = 0f; totalY = 0f
                            velocityTracker.resetTracking()
                            rawDrag = progress.value * travelPx
                        },
                        onDrag = { change, dragAmount ->
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            // Past the full-screen top boundary, resistance builds instead
                            // of a hard dead stop — real things slow down, they don't slam
                            // into walls. Excess beyond travelPx contributes a fraction of
                            // its own movement instead of being thrown away.
                            var raw = rawDrag - dragAmount.y
                            if (raw > travelPx) raw = travelPx + (raw - travelPx) * 0.35f
                            rawDrag = raw
                            scope.launch { progress.snapTo((rawDrag / travelPx).coerceIn(0f, 1f)) }
                            change.consume()
                        },
                        onDragEnd = {
                            val overscroll = -rawDrag // positive once dragged below the docked floor
                            val isTap = abs(totalX) < 8f && abs(totalY) < 8f
                            // Horizontal skip is only unambiguous while fully docked — once
                            // the sheet has started expanding, vertical wins outright.
                            val isHorizontalSkip = progress.value < 0.05f &&
                                abs(totalX) > abs(totalY) * 1.5f && abs(totalX) > skipThresholdPx
                            val velocityY = velocityTracker.calculateVelocity().y
                            when {
                                overscroll > dismissOverscrollPx -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismissPlayback()
                                }
                                isHorizontalSkip -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (totalX < 0) onNextClicked() else onPrevClicked()
                                    scope.launch { progress.animateTo(0f, morphSpring) }
                                }
                                isTap && progress.value < 0.05f -> {
                                    onExpandedChange(true)
                                    scope.launch { progress.animateTo(1f, morphSpring) }
                                }
                                // A quick upward flick opens regardless of how far the
                                // finger actually travelled; a quick downward flick closes.
                                velocityY < -flingVelocityPxPerSec -> {
                                    onExpandedChange(true)
                                    scope.launch { progress.animateTo(1f, morphSpring) }
                                }
                                velocityY > flingVelocityPxPerSec -> {
                                    onExpandedChange(false)
                                    scope.launch { progress.animateTo(0f, morphSpring) }
                                }
                                else -> {
                                    // Slow drag that just stopped: settle toward whichever
                                    // end is closer, with a low bar to favor opening — a
                                    // small deliberate swipe should be enough, not a haul
                                    // to the halfway point of the screen.
                                    val expand = progress.value > 0.2f
                                    onExpandedChange(expand)
                                    scope.launch { progress.animateTo(if (expand) 1f else 0f, morphSpring) }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { progress.animateTo(if (isExpanded) 1f else 0f, morphSpring) }
                        }
                    )
                }
        ) {
            // ── Background cross-fade: flat card while docked, ambient gradient once
            // expanded. Two overlaid layers rather than swapping a single Modifier so
            // the transition itself fades instead of cutting.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ColorSurface.copy(alpha = (1f - p * 1.4f).coerceIn(0f, 1f)))
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(p.coerceIn(0f, 1f))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(ColorSurface, ColorBackground),
                            radius = 1200f
                        )
                    )
            )

            val miniAlpha = ((0.35f - p) / 0.35f).coerceIn(0f, 1f)
            val fullAlpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)

            if (miniAlpha > 0.01f) {
                MiniPlayerContent(
                    modifier = Modifier.alpha(miniAlpha),
                    currentSong = currentSong,
                    isPlaying = state.isPlaying,
                    hapticActive = state.hapticActive,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNextClicked,
                    onPrev = onPrevClicked
                )
            }
            if (fullAlpha > 0.01f) {
                Box(Modifier.alpha(fullAlpha)) {
                    PlayerScreen(
                        state = state,
                        onCollapse = {
                            onExpandedChange(false)
                            scope.launch { progress.animateTo(0f, morphSpring) }
                        },
                        onTogglePlayPause = onTogglePlayPause,
                        onNextClicked = onNextClicked,
                        onPrevClicked = onPrevClicked,
                        onSeek = onSeek,
                        onSeekPreview = onSeekPreview,
                        onToggleHaptics = onToggleHaptics,
                        onHapticStudioClicked = onHapticStudioClicked,
                        onSongSelected = onSongSelected,
                        onQueueAction = onQueueAction,
                        onRefreshDndStatus = onRefreshDndStatus,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        onToggleFavorite = onToggleFavorite,
                        onSetSpeed = onSetSpeed,
                        onSetVolume = onSetVolume
                    )
                }
            }
        }
    }
}

/** Compact docked-bar content — artwork, title/artist or the "Haptic Active" pulse,
 *  and transport mini-buttons. Purely visual; the container above owns all gestures. */
@Composable
private fun MiniPlayerContent(
    modifier: Modifier = Modifier,
    currentSong: Song,
    isPlaying: Boolean,
    hapticActive: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    // The dot pulses to draw the eye while haptics are live; under reduced motion
    // it sits at a fixed mid-pulse alpha — state is the signal, not the motion.
    val dotAlpha = if (isReducedMotionEnabled()) 0.7f else {
        val infinitePulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow_pulse")
        val a by infinitePulse.animateFloat(
            initialValue = 0.4f, targetValue = 1.0f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.EaseInOut),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "dot_alpha"
        )
        a
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.miniPlayerHeight)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            currentSong.artworkUrl,
            null,
            Modifier.size(ComponentSize.artworkMedium),
            iconSize = ComponentSize.iconMedium,
            cornerRadius = Radius.sm,
            fallbackLabel = currentSong.title
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                currentSong.title,
                style = MaterialTheme.typography.labelLarge,
                color = ColorOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hapticActive) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ColorHapticAccent.copy(alpha = dotAlpha))
                    )
                    Spacer(Modifier.width(Spacing.xxs))
                    Text(
                        "Haptic Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorHapticAccent,
                        maxLines = 1
                    )
                } else {
                    Text(
                        currentSong.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorOnSurface60,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(ComponentSize.touchTarget)) {
                Icon(Icons.Default.SkipPrevious, "Previous", tint = ColorOnSurface, modifier = Modifier.size(ComponentSize.iconLarge))
            }
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(ComponentSize.touchTarget)
                    .clip(CircleShape)
                    .background(ColorSurfaceVariant.copy(alpha = 0.5f))
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (androidx.compose.animation.scaleIn(initialScale = 0.6f, animationSpec = androidx.compose.animation.core.tween(160)) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)))
                            .togetherWith(androidx.compose.animation.scaleOut(targetScale = 0.6f, animationSpec = androidx.compose.animation.core.tween(120)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)))
                    },
                    label = "mini_play_pause_icon"
                ) { playing ->
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Play",
                        tint = ColorOnSurface,
                        modifier = Modifier.size(ComponentSize.iconMedium)
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(ComponentSize.touchTarget)) {
                Icon(Icons.Default.SkipNext, "Next", tint = ColorOnSurface, modifier = Modifier.size(ComponentSize.iconLarge))
            }
        }
    }
}
