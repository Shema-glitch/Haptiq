package com.haptiq.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.haptiq.app.R

/**
 * Haptiq Typography System — Marketing Site Design System
 *
 * Three font families, each with a clear role:
 *  Space Grotesk  → Display/headlines (600–700 weight)
 *  Inter          → Body/UI text (400–600 weight)
 *  JetBrains Mono → Technical values only (Hz, ms, ×, dB) — NEVER plain labels
 *
 * Hierarchy:
 *  Display  → App wordmark "HAPTIQ", hero empty states
 *  Headline → Now Playing track title, screen titles
 *  Title    → Section headers, artist names
 *  Body     → Track lists, descriptions
 *  Label    → Metadata, timestamps, badges, chips
 */

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

// ─── Font Families ────────────────────────────────────────────

/** Space Grotesk — Display/headlines. Weight 600–700. */
val SpaceGroteskFont = GoogleFont("Space Grotesk")
val SpaceGroteskFamily = FontFamily(
    Font(googleFont = SpaceGroteskFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = SpaceGroteskFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = SpaceGroteskFont, fontProvider = provider, weight = FontWeight.Bold),
)

/** Inter — Body/UI text. Weight 400–600. */
val InterFont = GoogleFont("Inter")
val InterFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Black),
)

/** JetBrains Mono — Technical values only. Never use for plain labels. */
val JetBrainsMonoFont = GoogleFont("JetBrains Mono")
val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = JetBrainsMonoFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMonoFont, fontProvider = provider, weight = FontWeight.Medium),
)

// ─── Typography Scale ─────────────────────────────────────────

val Typography = Typography(
    // ─── Display ─────────────────────────────────────────────
    // Used for: "HAPTIQ" wordmark, hero states — Space Grotesk
    displayLarge = TextStyle(
        fontFamily   = SpaceGroteskFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 48.sp,
        lineHeight   = 56.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily   = SpaceGroteskFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 36.sp,
        lineHeight   = 44.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily   = SpaceGroteskFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 28.sp,
        lineHeight   = 36.sp,
        letterSpacing = 0.sp
    ),

    // ─── Headline ────────────────────────────────────────────
    // Used for: Now Playing track title, screen-level headers — Space Grotesk
    headlineLarge = TextStyle(
        fontFamily   = SpaceGroteskFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily   = SpaceGroteskFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 26.sp,
        lineHeight   = 34.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily   = SpaceGroteskFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
        letterSpacing = 0.sp
    ),

    // ─── Title ───────────────────────────────────────────────
    // Used for: Section headers ("Recently Played"), artist names — Inter
    titleLarge = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 20.sp,
        lineHeight   = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ─── Body ────────────────────────────────────────────────
    // Used for: Track list descriptions, settings subtitles — Inter
    bodyLarge = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // ─── Label ───────────────────────────────────────────────
    // Used for: Song count chips, timestamps, metadata, nav labels — Inter
    labelLarge = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily   = InterFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 11.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp
    ),
)
