package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haptiq.app.ui.theme.*

@Composable
fun PermissionsScreen(
    onAllowClicked: () -> Unit,
    onLaterClicked: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
    val pingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ping_scale"
    )
    val pingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ping_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.xl)
            .testTag("permissions_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background glow — radial gradient fading to transparent.
        // (Not Modifier.blur: its default edge treatment clips to rectangular
        // bounds, which rendered as a visible box on-device.)
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ColorHapticAccent.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        // Card Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(1.dp, ColorOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(Radius.lg)),
            colors = CardDefaults.cardColors(containerColor = ColorSurfaceVariant),
            shape = RoundedCornerShape(Radius.lg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon with pulsing boundary
                Box(
                    modifier = Modifier
                        .size(ComponentSize.iconHero)
                        .padding(bottom = Spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing boundary ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pingScale)
                            .alpha(pingAlpha)
                            .border(1.dp, ColorHapticAccent, CircleShape)
                    )

                    // Core Icon Background
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(ColorSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Haptic icon",
                            tint = ColorHapticAccent,
                            modifier = Modifier.size(ComponentSize.iconXL)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Onboarding pitch: say what Haptiq IS before asking for anything.
                Text(
                    text = "Feel your music",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ColorOnBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                Text(
                    text = "Haptiq was built for one idea: music you can touch. It listens to the bass in your songs in real time and drives your phone's vibration motor in sync — every kick and drop, in your hand. To do that, it needs two things:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ColorOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Specific items
                PermissionItem(
                    icon = Icons.Default.AudioFile,
                    title = "Your music library",
                    description = "To find the songs on this device and analyze their bass while they play."
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                PermissionItem(
                    icon = Icons.Default.TouchApp,
                    title = "Audio analysis",
                    description = "Android treats reading the live audio signal as recording — Haptiq only measures bass energy, nothing is saved or sent anywhere."
                )

                Spacer(modifier = Modifier.height(Spacing.xxl))

                // Action Buttons
                // System permission action — uses ColorPrimary, not haptic accent
                Button(
                    onClick = onAllowClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComponentSize.buttonHeight) // meets accessibility touch-target minimum
                        .testTag("allow_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPrimary,
                        contentColor = ColorOnPrimary
                    ),
                    shape = RoundedCornerShape(Radius.pill)
                ) {
                    Text(
                        text = "Allow",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onLaterClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComponentSize.touchTarget)
                        .testTag("later_button"),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ColorOnSurfaceVariant
                    )
                ) {
                    Text(
                        text = "Later",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(Radius.sm))
            .padding(Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ColorPrimary,
            modifier = Modifier
                .size(ComponentSize.iconMedium)
                .padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(Spacing.md))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ColorOnSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = ColorOnSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
