package com.haptiq.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Haptiq Shape System — Brutalist
 *
 * Zero radius on everything. No rounding, no pills, no soft edges.
 * Structure IS the decoration — thick borders and sharp corners
 * create the visual hierarchy.
 */
val HaptiqShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/**
 * One-off shapes — all square in brutalist mode.
 */
object ExpressiveShapes {
    val navPill = RoundedCornerShape(Radius.sm)
    val miniPlayerTop = RoundedCornerShape(
        topStart = Radius.md, topEnd = Radius.md,
        bottomStart = 0.dp, bottomEnd = 0.dp
    )
    val heroArtwork = RoundedCornerShape(Radius.lg)
    val cutAccent = CutCornerShape(topStart = Radius.md, bottomEnd = Radius.md)
}
