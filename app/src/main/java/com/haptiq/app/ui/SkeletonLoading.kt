package com.haptiq.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.haptiq.app.ui.theme.*

/**
 * Shimmer brush animation for skeleton loading.
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = listOf(
            ColorSurfaceVariant.copy(alpha = 0.6f),
            ColorSurface.copy(alpha = 0.8f),
            ColorSurfaceVariant.copy(alpha = 0.6f)
        ),
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Skeleton placeholder for a track row.
 */
@Composable
fun TrackRowSkeleton() {
    val brush = shimmerBrush()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight)
            .clip(RoundedCornerShape(Radius.sm))
            .background(ColorSurface.copy(alpha = 0.4f))
            .padding(horizontal = Spacing.xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Thumbnail skeleton
        Box(
            modifier = Modifier
                .size(ComponentSize.artworkSmall)
                .clip(RoundedCornerShape(Radius.sm))
                .background(brush)
        )
        Spacer(Modifier.width(Spacing.md))
        // Text skeletons
        Column(Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(Spacing.xxs))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
        // Action skeleton
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

/**
 * Skeleton placeholder for a media card.
 */
@Composable
fun MediaCardSkeleton() {
    val brush = shimmerBrush()
    Column(modifier = Modifier.width(ComponentSize.artworkCard)) {
        Box(
            modifier = Modifier
                .size(ComponentSize.artworkCard)
                .clip(RoundedCornerShape(Radius.md))
                .background(brush)
        )
        Spacer(Modifier.height(Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(Modifier.height(Spacing.xxs))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

/**
 * Full library skeleton — shown while scanning.
 */
@Composable
fun LibrarySkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Layout.screenHorizontalPadding)
    ) {
        // Search bar skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ComponentSize.searchBarHeight)
                .clip(RoundedCornerShape(Radius.pill))
                .background(shimmerBrush())
        )
        Spacer(Modifier.height(Layout.sectionGap))

        // "Recently Played" header skeleton
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush())
        )
        Spacer(Modifier.height(Spacing.sm))

        // Card row skeleton
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(3) { MediaCardSkeleton() }
        }
        Spacer(Modifier.height(Layout.sectionGap))

        // "All Tracks" header skeleton
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush())
        )
        Spacer(Modifier.height(Spacing.sm))

        // Track rows skeleton
        repeat(8) {
            TrackRowSkeleton()
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}
