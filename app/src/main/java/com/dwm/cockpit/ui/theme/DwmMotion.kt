package com.dwm.cockpit.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp

/**
 * Motion, as a small fixed vocabulary.
 *
 * Before this file the entire app contained one animation — a 220ms `tween` on the
 * steering dial — across roughly 8,600 lines. Everything else changed value by
 * simply being a different number on the next frame. A cockpit where nothing ever
 * moves reads as a screenshot of an interface rather than an interface, and that
 * was a real part of "it feels basic".
 *
 * Springs rather than tweens throughout. A tween has to be given a duration, which
 * means guessing one per call site and getting a different guess each time; a
 * spring is defined by how it feels and stays consistent wherever it is used. This
 * is the same idea as Material 3 Expressive's motion schemes, expressed with the
 * `spring()` that has always been in Compose — the deck is API 29 and this project
 * is deliberately not chasing a dependency bump to get the newer names for it.
 */
object DwmMotion {

    /** Needles, arcs and bars tracking a live vehicle reading. Slow and slightly
     *  soft, so a jittering CAN value does not turn into a twitching gauge. */
    val gauge: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)

    /** General interface state — fades, dot indicators, colour changes. */
    val ui: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** Touch feedback. Has to settle before the finger lifts or it feels laggy. */
    val press: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

    /** Things arriving on screen — a hair of overshoot, deliberately. */
    val enter: SpringSpec<Float> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

    /** Sizes, for `animateDpAsState`. */
    val size: SpringSpec<Dp> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    /**
     * Swapping one panel for another — the reverse-gear takeover, mainly.
     *
     * The one place a tween is right rather than a spring: a crossfade has no
     * physical thing being moved, so overshoot would only look like a glitch.
     */
    val fade: TweenSpec<Float> = tween(240)

    /** How far a card sinks when pressed. */
    const val PRESS_SCALE = 0.97f
}
