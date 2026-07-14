package com.haptiq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.haptiq.app.ui.theme.ColorHapticAccent
import com.haptiq.app.ui.theme.ColorOnSurface
import com.haptiq.app.ui.theme.ColorOnSurface60
import com.haptiq.app.ui.theme.ColorPrimary
import com.haptiq.app.ui.theme.ColorPrimaryContainer
import com.haptiq.app.ui.theme.ColorSurfaceVariant

/**
 * S4: Artwork image with placeholder fallback.
 *
 * Shows a music-note icon on [ColorSurfaceVariant] while loading or on error.
 * Uses explicit Coil [ImageRequest] with per-URL memory and disk cache keys
 * to prevent stale artwork bleed when LazyColumn recycles item views.
 */
@Composable
fun ArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    iconSize: Dp = 24.dp,
    cornerRadius: Dp = 0.dp,
    /** When set, missing artwork shows this text's first letter on a warm
     *  gradient instead of the generic music-note tile. */
    fallbackLabel: String? = null
) {
    val context = LocalContext.current

    // Build a cache-key-aware request so recycled cells always show the correct image
    val cacheKey = model?.toString() ?: ""
    val imageRequest = if (cacheKey.isNotEmpty()) {
        ImageRequest.Builder(context)
            .data(model)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .crossfade(false) // Prevents old image bleeding through during load in recycled cells
            .build()
    } else null

    val letter = fallbackLabel?.trimStart { !it.isLetterOrDigit() }?.firstOrNull()?.uppercaseChar()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (letter != null) {
                    // Deterministic warm gradient per label so the same artist
                    // always gets the same tile — reads as identity, not error.
                    val hues = listOf(ColorPrimary, ColorHapticAccent, ColorPrimaryContainer)
                    val base = hues[(letter.code) % hues.size]
                    Brush.verticalGradient(listOf(base.copy(alpha = 0.75f), base.copy(alpha = 0.35f)))
                } else {
                    Brush.verticalGradient(listOf(ColorSurfaceVariant, ColorSurfaceVariant))
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Placeholder — letter tile when a label is known, icon otherwise
        if (letter != null) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = ColorOnSurface
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = ColorOnSurface60,
                modifier = Modifier.size(iconSize)
            )
        }

        // AsyncImage overlays the placeholder once loaded; null model shows placeholder only
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

