package com.haptiq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haptiq.app.ui.theme.*

/**
 * Floating toast shown when Do Not Disturb is silencing haptics.
 *
 * Designed as an overlay, not an inline banner: it carries its own shadow and an
 * opaque surface so it reads as floating above the content, and the caller places
 * it in an overlay slot that never reflows the artwork or transport controls.
 */
@Composable
fun DndWarningBanner(
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Elevation.high, RoundedCornerShape(Radius.lg))
            .clip(RoundedCornerShape(Radius.lg)),
        // Opaque elevated surface, not the translucent SurfaceVariant — a floating
        // toast must fully hide whatever scrolls behind it.
        color = ColorSurfaceContainerHigh,
        tonalElevation = Elevation.high
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(ComponentSize.iconSmall)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Do Not Disturb is on",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Haptics are silenced. Turn off DND to feel vibrations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorOnSurface60
                )
            }

            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(ComponentSize.touchTarget)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = ColorOnSurface60,
                        modifier = Modifier.size(ComponentSize.iconSmall)
                    )
                }
            }
        }
    }
}
