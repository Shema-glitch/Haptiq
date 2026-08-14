package com.haptiq.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Fills the `HaptiqShapes` symbol referenced by Theme.kt's
 * `MaterialTheme(shapes = HaptiqShapes, ...)` call. Sized directly off
 * your real Radius scale in DesignTokens.kt — no new values introduced.
 *
 * Note: Radius.pill (56.dp) intentionally equals ComponentSize.buttonHeight
 * / searchBarHeight (56.dp) in your DesignTokens.kt, so any full-height
 * pill-shaped button/search bar becomes a true stadium shape automatically
 * — that's already correct, kept as-is here.
 */
val HaptiqShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/**
 * Expressive one-off shapes for specific components — this is where the
 * "35 new shape options" concept from the Android 16 doc actually shows
 * up: differentiating surface *types* by shape, not just color.
 */
object ExpressiveShapes {
    /** Subtle rounded rectangle for the floating bottom nav — and, per the
     *  Paper Tactile system, also the search bar, so the two persistent
     *  chrome elements read as one shape language instead of a pill fighting
     *  a rounded rect. */
    val navPill = RoundedCornerShape(Radius.xl)

    /** Mini player — rounded only on top so it reads as flush-docked
     *  above the nav pill rather than a floating card colliding with it. */
    val miniPlayerTop = RoundedCornerShape(
        topStart = Radius.lg, topEnd = Radius.lg,
        bottomStart = 0.dp, bottomEnd = 0.dp
    )

    /** Now Playing hero artwork — larger, softer rounding than a track row. */
    val heroArtwork = RoundedCornerShape(Radius.xl)

    /** Cut-corner accent for one signature moment (e.g. Calibration's
     *  test-pulse card) — used sparingly, not app-wide. */
    val cutAccent = CutCornerShape(topStart = Radius.md, bottomEnd = Radius.md)
}
