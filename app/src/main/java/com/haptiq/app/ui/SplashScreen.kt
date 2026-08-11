package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haptiq.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Splash screen with HAPTIQ text reveal animation.
 *
 * No square backgrounds — clean dark canvas with animated gold text.
 * The text reveals letter by letter, then fades into the app.
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Navigate after animation completes
    LaunchedEffect(Unit) {
        delay(2500)
        onTimeout()
    }

    // Reduced motion: the 2s letter choreography and the breathing glow collapse
    // to a static frame — the wordmark still lands, nothing moves.
    val reduced = isReducedMotionEnabled()

    // Text reveal animation — each letter appears sequentially
    val transition = if (reduced) null else rememberInfiniteTransition(label = "splash")

    // Overall fade in
    val overallAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tweenUnlessReduced(600),
        label = "overall_alpha"
    )

    // Pulse glow behind text
    val pulseScale = if (reduced) 1f else {
        val s by transition!!.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
        s
    }
    val pulseAlpha = if (reduced) 0.08f else {
        val a by transition!!.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        a
    }

    // Letter reveal animation
    val letters = "HAPTIQ"
    val letterAlphas = letters.mapIndexed { index, _ ->
        val delay = index * 150 // 150ms stagger per letter
        val alpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tweenUnlessReduced(400, delayMillis = delay),
            label = "letter_$index"
        )
        alpha
    }

    // Subtitle fade in (appears after all letters)
    val subtitleAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tweenUnlessReduced(600, delayMillis = 1200),
        label = "subtitle_alpha"
    )

    // Scan status fade in
    val scanAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tweenUnlessReduced(400, delayMillis = 1800),
        label = "scan_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient gold glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ColorHapticAccent, Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(overallAlpha)
        ) {
            // HAPTIQ text — letter by letter reveal
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                letters.forEachIndexed { index, letter ->
                    Text(
                        text = letter.toString(),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorPrimary,
                        letterSpacing = 8.sp,
                        modifier = Modifier.alpha(letterAlphas[index])
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // Tagline
            Text(
                text = "Feel the Music",
                style = MaterialTheme.typography.titleMedium,
                color = ColorOnSurface60,
                letterSpacing = 4.sp,
                modifier = Modifier.alpha(subtitleAlpha)
            )

            Spacer(Modifier.height(Spacing.xxxl))

            // Scan status indicator
            Text(
                text = "Preparing your library…",
                style = MaterialTheme.typography.bodySmall,
                color = ColorOnSurface60.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(scanAlpha)
            )
        }
    }
}
