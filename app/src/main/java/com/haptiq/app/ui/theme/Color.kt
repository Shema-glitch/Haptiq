package com.haptiq.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Haptiq Brand Palette — "Paper Tactile" ─────────────────
// One accent family, not two competing ones: everything warm-clay,
// differentiated by lightness/saturation rather than hue-clash.
// Primary accent : Clay / Terracotta   (#C77B4F)
// Haptic accent  : Warm Amber          (#E0A458) — same family, lighter
// Background base: Warm Charcoal       (#211D1A) — never pure black
//
// This exists because "haptic" means touch — a cold navy/neon-violet
// scheme (the previous palette) reads like every other dark music app.
// Warm, low-saturation neutrals + a single earthy accent read as
// considered and tactile instead of generic.

// ─── Background / Surface Layers ───────────────────────────
val ColorBackground          = Color(0xFF211D1A)  // Warm charcoal, not pure black
val ColorSurface             = Color(0xFF2B2521)  // Card surface
val ColorSurfaceVariant      = Color(0xFF362F29)  // Elevated cards
val ColorSurfaceContainer    = Color(0xFF29241F)  // Mini player
val ColorSurfaceContainerHigh= Color(0xFF332C26)  // Nav bar
val ColorSurfaceContainerLow = Color(0xFF1C1815)  // Subtle sections

// ─── Primary (Clay / Terracotta) ─────────────────────────────
val ColorPrimary             = Color(0xFFC77B4F)  // Clay
val ColorOnPrimary           = Color(0xFF2B211A)  // Dark warm ink on clay
val ColorPrimaryContainer    = Color(0xFF4A3324)  // Deep clay container
val ColorOnPrimaryContainer  = Color(0xFFF0C9A8)  // Light clay text

// ─── Haptic Accent (Amber — for haptic-specific UI elements) ─
// Same warm family as Primary, distinguished by lightness only —
// so haptic-tuning UI reads as "related to" the brand, not a clashing
// second accent competing for attention.
val ColorHapticAccent        = Color(0xFFE0A458)  // Warm amber
val ColorHapticAccentDim     = Color(0xFF8C6A3E)  // Dimmed amber (off state)

// ─── On-Surface / Text ──────────────────────────────────────
val ColorOnBackground        = Color(0xFFF4ECE3)  // Warm off-white text
val ColorOnSurface           = Color(0xFFF4ECE3)  // Primary text
val ColorOnSurfaceVariant    = Color(0xFFB7ACA0)  // Secondary text
val ColorOnSurface60         = Color(0xFF857A6D)  // Muted/tertiary text

// ─── Outline / Borders ──────────────────────────────────────
val ColorOutline             = Color(0xFF4A4038)  // Visible borders
val ColorOutlineVariant      = Color(0xFF322B25)  // Subtle borders

// ─── Semantic ───────────────────────────────────────────────
val ColorError               = Color(0xFFD9553D)  // Warm-shifted red — still unambiguous
val ColorOnError             = Color(0xFFFFF8F3)
