package com.dwm.cockpit.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp

/**
 * Exactly two durations, one curve, and transform or opacity only.
 *
 * This replaces five spring specs. Springs read better in isolation and were the
 * wrong choice here for two reasons. A spring has no duration, so "nothing over
 * 250ms" is not something it can be held to — a soft spring on a value that keeps
 * changing settles whenever it settles. And a spring invites animating whatever feels
 * good, which on this SoC is how a 60fps budget quietly disappears.
 *
 * The rules, and they are not stylistic:
 *
 * - **[FAST] (120ms)** — anything the finger caused. Feedback slower than this reads
 *   as lag on a resistive-feeling panel.
 * - **[BASE] (200ms)** — anything the system caused. The rail's travelling bar, a
 *   crossfade, a state change.
 * - **Nothing else.** No third duration, no per-call-site number.
 * - **`graphicsLayer` transform and opacity only.** Never height, width, padding or
 *   any other property that triggers measure and layout. The Unisoc SC9863A has a
 *   Mali-G51 driving 2.3 million pixels; a relayout every frame is the one thing it
 *   genuinely cannot afford, and it is why the media widget is a fixed-height strip
 *   that crossfades its contents rather than a card that grows.
 * - Frame cost is measured on the deck with `dumpsys gfxinfo … framestats`, not
 *   assumed. Anything over 16.6ms at the 95th percentile gets simplified.
 */
object DwmMotion {

    /** Touch feedback. Must settle before the finger lifts. */
    const val FAST = 120

    /** System-initiated change. Nothing in DWM is slower than this. */
    const val BASE = 200

    /**
     * One curve everywhere: quick to leave, gentle to arrive. A single easing is
     * most of what makes separate screens feel like one machine.
     */
    val easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val fast: TweenSpec<Float> = tween(FAST, easing = easing)
    val base: TweenSpec<Float> = tween(BASE, easing = easing)

    /** [BASE], for `animateDpAsState`. Same curve, same duration, different type. */
    val baseDp: TweenSpec<Dp> = tween(BASE, easing = easing)

    /**
     * How far a pressed surface sinks. A scale, so it runs on `graphicsLayer` and
     * costs no layout pass.
     */
    const val PRESS_SCALE = 0.97f
}
