package com.haptiq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haptiq.app.ui.theme.*

/**
 * Warning banner shown when Do Not Disturb is active.
 *
 * DND suppresses vibration on Android, which means haptic feedback
 * will not work even if the toggle is on. This banner alerts the user.
 */
@Composable
fun DndWarningBanner(
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md)),
        color = ColorSurfaceVariant,
        tonalElevation = Elevation.low
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(ComponentSize.iconMedium)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Do Not Disturb is on",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Haptics are silenced by DND. Turn it off in Settings to feel vibrations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorOnSurface60
                )
            }
        }
    }
}
