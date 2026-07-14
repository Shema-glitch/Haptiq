package com.haptiq.app.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haptiq.app.ui.theme.ColorHapticAccent

@Composable
fun NowPlayingIndicator(
    active: Boolean = true,
    height: Dp = 14.dp,
    width: Dp = 3.dp
) {
    val transition = rememberInfiniteTransition(label = "now_playing_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "now_playing_alpha"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(
                color = ColorHapticAccent.copy(alpha = if (active) pulseAlpha else 1f),
                shape = RoundedCornerShape(50)
            )
    )
}
