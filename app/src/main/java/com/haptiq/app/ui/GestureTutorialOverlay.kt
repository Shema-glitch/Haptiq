package com.haptiq.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.haptiq.app.ui.theme.*

/**
 * One-time first-run coach flow shown over the Library once it has songs.
 *
 * Three cards: a celebration of the songs we found, then two animated demos that
 * teach the invisible gestures (swipe-to-queue, and the mini-player skip/dismiss).
 * Every demo is a self-contained mock — it never touches the real list — so the
 * teaching is reliable regardless of what is actually on screen.
 */
@Composable
fun GestureTutorialOverlay(
    songCount: Int,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val lastStep = 2

    // A full-screen Dialog, not an inline Box: rendered inside the library's
    // content area, an inline scrim only darkened the list region and read as a
    // black rectangle clipped between the header and nav bar. The dialog window
    // covers the whole screen, so the scrim is uniform.
    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
    // Full-screen scrim. Tapping the scrim does nothing — users advance with the
    // button or Skip, so a stray tap can't leave them half-taught.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .shadow(Elevation.high, RoundedCornerShape(Radius.xl))
                .clip(RoundedCornerShape(Radius.xl)),
            color = ColorSurface,
            tonalElevation = Elevation.high
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Skip — always available, never trap the user in the tutorial.
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = ColorOnSurface60,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable(onClick = onFinish)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }

                Spacer(Modifier.height(Spacing.sm))

                // Animated demo area — swaps with a slide as the step changes.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            // Unhurried so each card has room to land — the incoming
                            // slide+fade is slower than the outgoing, so steps feel
                            // like they settle rather than snap.
                            (slideInHorizontally(tween(520, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeIn(tween(520))) togetherWith
                                (slideOutHorizontally(tween(360)) { -it / 4 } + fadeOut(tween(280)))
                        },
                        label = "tutorial_demo"
                    ) { s ->
                        when (s) {
                            0 -> CelebrationDemo(songCount)
                            1 -> SwipeToQueueDemo()
                            else -> MiniPlayerDemo()
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                val (title, body) = when (step) {
                    0 -> "You've got great taste 🎧" to
                        "Haptiq found your music. Let's make it something you feel, not just hear."
                    1 -> "Swipe to play next" to
                        "Swipe any song to the right to drop it into the queue — no menus, no long-press."
                    else -> "Control from anywhere" to
                        "On the mini player, swipe left or right to skip. Pull it down to dismiss. In Now Playing, pull down to close."
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = ColorOnSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorOnSurface60,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Spacing.lg))

                // Step dots
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    for (i in 0..lastStep) {
                        val active = i == step
                        val dotWidth by animateDpAsState(if (active) 20.dp else 6.dp, label = "dot")
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(if (active) ColorHapticAccent else ColorOutlineVariant)
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                Button(
                    onClick = { if (step < lastStep) step++ else onFinish() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPrimary,
                        contentColor = ColorOnPrimary
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(vertical = Spacing.sm)
                ) {
                    Text(
                        text = if (step < lastStep) "Next" else "Start listening",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    }
}

/** Step 0: the found-songs count animates up, over a soft pulsing halo. */
@Composable
private fun CelebrationDemo(songCount: Int) {
    val count by animateIntAsState(
        targetValue = songCount,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "count_up"
    )
    val pulse = rememberInfiniteTransition(label = "celebrate_pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(ColorHapticAccent.copy(alpha = 0.10f))
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.displayMedium,
                color = ColorHapticAccent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (songCount == 1) "song ready" else "songs ready",
                style = MaterialTheme.typography.labelMedium,
                color = ColorOnSurface60
            )
        }
    }
}

/** Step 1: a mock row slides right on a loop, revealing the amber "Play next" layer. */
@Composable
private fun SwipeToQueueDemo() {
    val transition = rememberInfiniteTransition(label = "swipe_demo")
    // 0 → 1 → 0 sweep with a hold at the top, so it reads as a deliberate swipe.
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                0f at 0
                0f at 300
                1f at 1100
                1f at 1500
                0f at 2200
            }
        ),
        label = "swipe_t"
    )
    val maxShift = 96.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(Radius.md))
    ) {
        // Amber reveal underneath
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorHapticAccent.copy(alpha = 0.18f))
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(Icons.Default.QueuePlayNext, null, tint = ColorHapticAccent)
            Text("Play next", style = MaterialTheme.typography.labelLarge, color = ColorHapticAccent)
        }
        // The row itself, sliding
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = maxShift.toPx() * t }
                .background(ColorSurfaceVariant)
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(Radius.sm)).background(ColorSurface),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MusicNote, null, tint = ColorOnSurface60, modifier = Modifier.size(ComponentSize.iconSmall)) }
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Box(Modifier.height(10.dp).width(120.dp).clip(CircleShape).background(ColorOnSurface60.copy(alpha = 0.5f)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.height(8.dp).width(72.dp).clip(CircleShape).background(ColorOnSurface60.copy(alpha = 0.3f)))
            }
        }
        // Finger dot tracking the swipe
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { translationX = maxShift.toPx() * t + 20.dp.toPx() }
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}

/** Step 2: a mock mini-player nudges side to side (skip) then dips down (dismiss). */
@Composable
private fun MiniPlayerDemo() {
    val transition = rememberInfiniteTransition(label = "mini_demo")
    val shiftX by transition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                0f at 0
                -1f at 500      // swipe left (skip next)
                0f at 1000
                1f at 1500      // swipe right (previous)
                0f at 2000
                0f at 3000
            }
        ),
        label = "mini_x"
    )
    val shiftY by transition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                0f at 0
                0f at 2000
                1f at 2600      // pull down (dismiss)
                0f at 3000
            }
        ),
        label = "mini_y"
    )
    Box(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = 40.dp.toPx() * shiftX
                    translationY = 34.dp.toPx() * shiftY
                    alpha = 1f - 0.5f * shiftY
                }
                .shadow(Elevation.medium, RoundedCornerShape(Radius.lg))
                .clip(RoundedCornerShape(Radius.lg))
                .background(ColorSurfaceVariant)
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(Radius.sm)).background(ColorSurface),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MusicNote, null, tint = ColorOnSurface60, modifier = Modifier.size(ComponentSize.iconSmall)) }
            Column(Modifier.weight(1f)) {
                Box(Modifier.height(9.dp).width(110.dp).clip(CircleShape).background(ColorOnSurface60.copy(alpha = 0.5f)))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.height(7.dp).width(64.dp).clip(CircleShape).background(ColorOnSurface60.copy(alpha = 0.3f)))
            }
            Icon(Icons.Default.SkipNext, null, tint = ColorOnSurface, modifier = Modifier.size(ComponentSize.iconMedium))
        }
        // Down-hint chevron under the strip, fading in as it dips
        Icon(
            Icons.Default.KeyboardArrowDown,
            null,
            tint = ColorOnSurface60.copy(alpha = (shiftY).coerceIn(0f, 1f)),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
