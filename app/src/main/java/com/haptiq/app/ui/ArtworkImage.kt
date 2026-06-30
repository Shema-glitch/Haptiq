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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.haptiq.app.ui.theme.ColorOnSurface60
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
    cornerRadius: Dp = 0.dp
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(ColorSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // Placeholder icon — always present behind the image
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = ColorOnSurface60,
            modifier = Modifier.size(iconSize)
        )

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

