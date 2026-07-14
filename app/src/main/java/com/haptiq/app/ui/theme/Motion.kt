package com.haptiq.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * ── Spring-based motion tokens ───────────────────────────────────────
 * Per the Android 16 Material 3 Expressive doc: "Spring-based physics for
 * all transitions... natural acceleration/deceleration curves." Current
 * screens use `tween(...)` + manual easing curves everywhere (Splash's
 * letter reveal, Permissions' pulse ring, Library's equalizer bars) —
 * those are fine for continuous/looping ambient animation, but anything
 * state-driven (expand/collapse, toggle, navigation, drag-to-dismiss)
 * should use these spring specs instead of tween/easing.
 *
 * Three weights, matching M3 Expressive's motion scheme naming:
 *  - expressive: bouncier, more perceptible overshoot — use for playful/
 *    high-visibility moments (mini player → Now Playing expand, FAB press,
 *    heart/favorite toggle).
 *  - standard: default for most UI transitions (screen/tab switches,
 *    bottom sheet open/close).
 *  - fast: quick, low-overshoot — use for small in-place feedback
 *    (chip selection, switch toggle) where a big bounce would feel noisy.
 */
object HaptiqMotion {
    fun <T> expressiveSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> standardSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> fastSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    /** For ambient/looping animation (shimmer, pulse glow, equalizer bars)
     *  where a spring doesn't apply — kept as tween by design, not a gap. */
    fun ambientTween(durationMillis: Int) = tween<Float>(durationMillis)
}
