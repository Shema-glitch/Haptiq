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

// ─── Spacing ───────────────────────────────────────────────
object Spacing {
    val xxs = 4.dp    // Icon-to-text micro gap
    val xs = 8.dp     // Tight gap between related items
    val sm = 12.dp    // Chip padding, small gaps
    val md = 16.dp    // Standard content padding, list item padding
    val lg = 20.dp    // Section internal padding
    val xl = 24.dp    // Screen horizontal padding, section gaps
    val xxl = 32.dp   // Large section separator
    val xxxl = 40.dp  // Hero spacing (empty state icon to text)
    val huge = 48.dp  // Empty state text to button
    val massive = 56.dp // Button height, search bar height
    val giant = 64.dp   // Album art thumbnail, FAB size
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
    val screenHorizontalPadding = Spacing.xl        // 24dp
    val screenVerticalPadding = Spacing.md           // 16dp
    val contentMaxWidth = 480.dp                      // Max content width for readability
    val sectionGap = Spacing.xl                       // 24dp between sections
    val itemGap = Spacing.xs                          // 8dp between list items
    val cardInternalPadding = Spacing.md              // 16dp inside cards
    val chipGap = Spacing.xs                          // 8dp between chips
}
