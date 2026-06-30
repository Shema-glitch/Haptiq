package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
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
            .padding(24.dp)
            .testTag("permissions_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .blur(60.dp)
                .background(ColorHapticAccent.copy(alpha = 0.08f), CircleShape)
                .align(Alignment.TopEnd)
        )

        // Card Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(1.dp, ColorOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = ColorSurfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon with pulsing boundary
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 8.dp),
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
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title and Subtitle
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ColorOnBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Haptiq requires access to synchronize high-fidelity vibrations with your music library.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ColorOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Specific items
                PermissionItem(
                    icon = Icons.Default.AudioFile,
                    title = "Music and Audio",
                    description = "Required to read and process audio tracks for haptic generation."
                )

                Spacer(modifier = Modifier.height(16.dp))

                PermissionItem(
                    icon = Icons.Default.TouchApp,
                    title = "Haptic Feedback",
                    description = "Required to control the device's vibration motor for playback."
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                // System permission action — uses ColorPrimary, not haptic accent
                Button(
                    onClick = onAllowClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp) // Fix T: increased from 48dp for better touch target
                        .testTag("allow_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPrimary,
                        contentColor = ColorOnPrimary
                    ),
                    shape = RoundedCornerShape(28.dp)
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
                        .height(48.dp)
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
            .background(ColorSurface, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ColorPrimary,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

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
