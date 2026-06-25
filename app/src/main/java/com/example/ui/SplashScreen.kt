package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ColorBackground
import com.example.ui.theme.ColorHapticAccent
import com.example.ui.theme.ColorOnSurface60
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Navigate after 2 seconds
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }

    // Logo pulsing ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Center wave animation
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Atmospheric gradient glow in background
        Box(
            modifier = Modifier
                .size(400.dp)
                .blur(80.dp)
                .scale(1.2f)
                .background(ColorHapticAccent.copy(alpha = 0.05f), RoundedCornerShape(200.dp))
        )

        // Logo Container
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing Ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .background(ColorHapticAccent.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
            )

            // Dynamic Custom Vector 'H'
            Canvas(modifier = Modifier.size(100.dp)) {
                val strokeWidth = 8.dp.toPx()
                val leftX = 30.dp.toPx()
                val rightX = 70.dp.toPx()
                val startY = 15.dp.toPx()
                val endY = 85.dp.toPx()
                val midY = 50.dp.toPx()

                // Left Pillar
                drawRoundRect(
                    color = ColorHapticAccent,
                    topLeft = androidx.compose.ui.geometry.Offset(leftX - 6.dp.toPx(), startY),
                    size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 70.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                )

                // Right Pillar
                drawRoundRect(
                    color = ColorHapticAccent,
                    topLeft = androidx.compose.ui.geometry.Offset(rightX - 6.dp.toPx(), startY),
                    size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 70.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                )

                // Center connecting wave
                val path = Path().apply {
                    val waveAmplitude = 10.dp.toPx()
                    moveTo(leftX + 6.dp.toPx(), midY)
                    cubicTo(
                        leftX + 20.dp.toPx(), midY - waveAmplitude - waveOffset,
                        rightX - 20.dp.toPx(), midY + waveAmplitude + waveOffset,
                        rightX - 6.dp.toPx(), midY
                    )
                }

                drawPath(
                    path = path,
                    color = ColorHapticAccent,
                    style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }

        // Footer Attribution
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Powered by Haptiq Engine",
                fontSize = 11.sp,
                color = ColorOnSurface60,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
