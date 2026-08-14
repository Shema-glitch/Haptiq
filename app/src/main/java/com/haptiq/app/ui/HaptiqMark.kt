package com.haptiq.app.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Haptiq mark — the same three-facet amber diamond as the launcher icon and
 * the landing page. Shared so in-app surfaces (splash, About) stay in lockstep
 * with the branding.
 */
val HaptiqDiamondVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "HaptiqDiamond",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = 108f,
        viewportHeight = 108f
    ).apply {
        // Top facet (lightest amber).
        path(fill = SolidColor(Color(0xFFD98E62))) {
            moveTo(54f, 23.9f)
            lineTo(80.6f, 39.3f)
            lineTo(54f, 54.7f)
            lineTo(27.4f, 39.3f)
            close()
        }
        // Left facet.
        path(fill = SolidColor(Color(0xFFC77B4F))) {
            moveTo(27.4f, 39.3f)
            lineTo(54f, 54.7f)
            lineTo(54f, 84.1f)
            lineTo(27.4f, 68.7f)
            close()
        }
        // Right facet (deepest amber).
        path(fill = SolidColor(Color(0xFF8F5432))) {
            moveTo(80.6f, 39.3f)
            lineTo(54f, 54.7f)
            lineTo(54f, 84.1f)
            lineTo(80.6f, 68.7f)
            close()
        }
    }.build()
}

@Composable
fun HaptiqDiamondMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    // tint = Unspecified so the vector's own facet colors render instead of
    // being flattened to a single tint.
    Icon(
        imageVector = HaptiqDiamondVector,
        contentDescription = contentDescription,
        tint = Color.Unspecified,
        modifier = modifier
    )
}
