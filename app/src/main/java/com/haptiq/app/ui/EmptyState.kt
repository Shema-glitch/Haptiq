package com.haptiq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haptiq.app.ui.theme.*

/**
 * The one empty-state treatment for every list screen (Library, Albums,
 * Favorites, search results). Before this, each screen improvised its own —
 * circle-backed icon on one, raw icon on another, different spacing on a third.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(Spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(120.dp).background(ColorSurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(ComponentSize.iconHero), tint = ColorOnSurface60)
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = ColorOnSurface,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorOnSurface60,
                    textAlign = TextAlign.Center
                )
            }
            content?.invoke(this)
        }
    }
}

/** "1 track" / "12 tracks" — never "1 tracks". */
fun countLabel(count: Int, singular: String): String =
    if (count == 1) "1 $singular" else "$count ${singular}s"
