package com.haptiq.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haptiq.app.ui.theme.*
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.Vibration,
        title = "Music you can touch",
        body = "Haptiq turns your phone into a subwoofer for your hands. It reads the bass in your songs live and drives the vibration motor in sync — every kick and every drop, felt."
    ),
    OnboardingPage(
        icon = Icons.Default.GraphicEq,
        title = "Your own Haptic Studio",
        body = "Every phone motor is different. Tune kicks, sub-bass drones and sensitivity in a live studio until the feel is exactly yours — or let Adaptive mode calibrate itself to the music."
    ),
    OnboardingPage(
        icon = Icons.Default.TouchApp,
        title = "Feel the drop before it lands",
        body = "Scrub the timeline and Haptiq pulses the bass under your finger, so you can find the drop by feel. No other player does this."
    )
)

/**
 * First-run pitch, shown BEFORE any permission dialog. Users who understand what
 * the app is for grant permissions; users ambushed by a system dialog uninstall.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .systemBarsPadding()
            .testTag("onboarding_screen")
    ) {
        // Ambient glow, same language as the permissions card
        Box(
            modifier = Modifier
                .size(300.dp)
                .blur(60.dp)
                .background(ColorHapticAccent.copy(alpha = 0.08f), CircleShape)
                .align(Alignment.TopEnd)
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
                OnboardingPageContent(pages[pageIndex])
            }

            // Page dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(bottom = Spacing.xl)
            ) {
                repeat(pages.size) { i ->
                    val selected = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 400f))
                            .width(if (selected) 20.dp else 8.dp)
                            .height(8.dp)
                            .background(
                                if (selected) ColorPrimary else ColorOutlineVariant,
                                CircleShape
                            )
                    )
                }
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        onDone()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
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
                Text(
                    text = if (isLastPage) "Get started" else "Next",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    // Gentle breathing ring behind the icon — alive, not static
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_pulse")
    val pingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)),
        label = "ping_scale"
    )
    val pingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)),
        label = "ping_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pingScale)
                    .alpha(pingAlpha)
                    .border(1.dp, ColorHapticAccent, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(ColorSurface, CircleShape)
                    .border(1.dp, ColorOutlineVariant.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = ColorHapticAccent,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

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
