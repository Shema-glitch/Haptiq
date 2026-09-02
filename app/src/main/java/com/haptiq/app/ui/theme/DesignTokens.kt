package com.haptiq.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Haptiq Design Token System — Brutalist
 *
 * All spacing, radius, and layout values are defined here.
 * NEVER use raw dp values in screens — always reference these tokens.
 *
 * Brutalist: zero radius on everything, thick visible borders,
 * no shadows, no soft rounding. Structure IS the decoration.
 */

// ─── Spacing Scale (base-4) ─────────────────────────────────
// Every padding/margin value in the app should reference one of these.
// No arbitrary one-off dp values in composables going forward.
object Spacing {
    val xxs = 4.dp    // Hairline gaps (icon-to-label inside a tight chip)
    val xs = 8.dp     // Tightly related elements (icon + its own label)
    val sm = 12.dp    // Related actions (heart icon to queue-add icon)
    val md = 16.dp    // Standard padding inside cards/rows, internal gaps
    val lg = 24.dp    // Gap between distinct sections on the same screen
    val xl = 32.dp    // Gap between major functional groups
    val xxl = 40.dp   // Empty state hero spacing
    val xxxl = 48.dp  // Empty state text to button
    val huge = 56.dp  // Button height, search bar height
    val giant = 64.dp // Album art thumbnail, FAB size
}

// ─── Corner Radius ─────────────────────────────────────────
// BRUTALIST: zero radius on everything. No rounding, no pills.
// Structure IS the decoration — sharp edges, visible borders.
object Radius {
    val sm = 0.dp     // Everything is square
    val md = 0.dp
    val lg = 0.dp
    val xl = 0.dp
    val pill = 0.dp   // No pill shapes in brutalism
}

// ─── Border Width ──────────────────────────────────────────
// Thick, visible borders are the primary depth/structure signal
// in brutalist design. Replaces shadows entirely.
object BorderWidth {
    val thin = 1.dp     // Dividers between grouped items
    val medium = 2.dp   // Cards, buttons, chips — the default
    val thick = 3.dp    // Emphasized elements, active states
}

// ─── Component Sizes ───────────────────────────────────────
object ComponentSize {
    val touchTarget = 48.dp   // Minimum touch target (accessibility)
    val iconSmall = 20.dp     // Small icons (trailing, inline)
    val iconMedium = 24.dp    // Standard icons (nav bar, list items)
    val iconLarge = 28.dp     // Emphasized icons (transport controls)
    val iconXL = 32.dp        // Hero icons (play/pause, empty state)
    val iconXXL = 48.dp       // Empty state large icon
    val iconHero = 64.dp      // Empty state hero icon

    val artworkSmall = 44.dp  // Track row thumbnail
    val artworkMedium = 48.dp // Mini player artwork
    val artworkCard = 160.dp  // Recently played card artwork

    val buttonHeight = 56.dp  // Standard button height
    val searchBarHeight = 56.dp // Search bar height
    val topBarHeight = 56.dp  // Top app bar height
    val navBarHeight = 72.dp  // Bottom navigation bar height
    val miniPlayerHeight = 72.dp // Mini player height

    val playPauseFAB = 64.dp  // Main play/pause button
    val hapticFAB = 56.dp     // Haptic studio FAB
}

// ─── Elevation ─────────────────────────────────────────────
// Brutalist: no shadows. Depth comes from thick borders only.
object Elevation {
    val none = 0.dp
    val low = 0.dp
    val medium = 0.dp
    val high = 0.dp
    val hero = 0.dp
}

// ─── Layout ────────────────────────────────────────────────
object Layout {
    val screenHorizontalPadding = Spacing.md        // 16dp
    val screenVerticalPadding = Spacing.md           // 16dp
    val contentMaxWidth = 480.dp                      // Max content width for readability
    val sectionGap = Spacing.lg                       // 24dp between sections
    val itemGap = Spacing.xs                          // 8dp between list items
    val cardInternalPadding = Spacing.md              // 16dp inside cards
    val chipGap = Spacing.xs                          // 8dp between chips
}
