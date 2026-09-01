package com.haptiq.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Haptiq Brand Palette — Marketing Site System ─────────────
// Two-engine legend: orange (kick) and teal (bass) are NOT decorative —
// they're used consistently everywhere the app distinguishes the kick
// engine from the bass engine: toggles, sliders, labels, chart lines,
// icons. If a screen shows both engines, split it visually the same
// way, every time.
//
// Background is near-black, not warm charcoal. Text is warm off-white.
// Surface layers are minimal (only 2 levels: bg + surface). Hairline
// dividers replace card shadows.

// ─── Background / Surface Layers ───────────────────────────
val ColorBackground          = Color(0xFF0E0D0C)  // Black (bg) — the page
val ColorSurface             = Color(0xFF161412)  // Black-2 (surface) — cards, sheets
val ColorSurfaceVariant      = Color(0xFF161412)  // Same as surface — no 3rd layer
val ColorSurfaceContainer    = Color(0xFF161412)  // Mini player
val ColorSurfaceContainerHigh= Color(0xFF161412)  // Nav bar
val ColorSurfaceContainerLow = Color(0xFF0E0D0C)  // Subtle sections — same as bg

// ─── Primary (Kick accent — orange) ───────────────────────────
// Maps to ColorPrimary because the kick is the dominant interactive
// element: play buttons, active states, selection, focused elements.
val ColorPrimary             = Color(0xFFFF5A2E)  // Kick accent #FF5A2E
val ColorOnPrimary           = Color(0xFF0E0D0C)  // Dark bg text on kick buttons
val ColorPrimaryContainer    = Color(0xFF7A3520)  // Dimmed kick container
val ColorOnPrimaryContainer  = Color(0xFFF2EEE4)  // Ink text on kick containers

// ─── Haptic Accent (Bass accent — teal) ───────────────────────
// Maps to ColorHapticAccent because bass is the secondary indicator:
// waveform markers, real-time meters, secondary toggles.
val ColorHapticAccent        = Color(0xFF1FB6A6)  // Bass accent #1FB6A6
val ColorHapticAccentDim     = Color(0xFF1B5750)  // Dimmed teal (off state)

// ─── On-Surface / Text ──────────────────────────────────────
val ColorOnBackground        = Color(0xFFF2EEE4)  // Ink — primary text
val ColorOnSurface           = Color(0xFFF2EEE4)  // Ink — primary text
val ColorOnSurfaceVariant    = Color(0xFF9C948A)  // Muted text
val ColorOnSurface60         = Color(0xFF9C948A)  // Muted — same as variant now

// ─── Outline / Borders ──────────────────────────────────────
// Hairline dividers: F2EEE4 at 12% alpha = ~#1F1E1C on #0E0D0C bg
val ColorOutline             = Color(0x1FF2EEE4)  // Hairline dividers (12% alpha)
val ColorOutlineVariant      = Color(0x0FF2EEE4)  // Subtle borders (6% alpha)

// ─── Semantic ───────────────────────────────────────────────
val ColorError               = Color(0xFFD9553D)  // Warm-shifted red — still unambiguous
val ColorOnError             = Color(0xFFFFF8F3)

// ─── Engine-specific (for screens that need explicit distinction) ──
val ColorKick                = Color(0xFFFF5A2E)  // Explicit kick engine color
val ColorBass                = Color(0xFF1FB6A6)  // Explicit bass engine color

// ─── Paper (light bg — for any paper-white sections) ──────────
val ColorPaper               = Color(0xFFF6F1E7)  // Paper light background
