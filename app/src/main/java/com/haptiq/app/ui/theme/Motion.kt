package com.haptiq.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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

/**
 * True when the system animation scale is 0 ("Remove animations"). Ambient and
 * hero animation must collapse to static/instant frames in that case — M3
 * Expressive requires honoring the system scale, and infinite loops (pulse
 * glows, drifting gradients, demo loops) are the ones that actually cause
 * motion sickness and needless battery drain.
 */
@Composable
fun isReducedMotionEnabled(): Boolean {
    // ANIMATOR_DURATION_SCALE == 0 is Android's "Remove animations" setting. Read
    // it directly (the compose AccessibilityManager doesn't expose the flag).
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * A [tween] that collapses to instant (0ms) when the system animation scale is
 * 0. Use for state-driven reveals whose timing is not load-bearing — letters,
 * subtitles, page glides — so reduced-motion users get the content without the
 * choreography.
 */
@Composable
fun tweenUnlessReduced(
    durationMillis: Int,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing
): TweenSpec<Float> =
    if (isReducedMotionEnabled()) tween(0) else tween(durationMillis, delayMillis = delayMillis, easing = easing)
