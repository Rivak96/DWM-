package com.dwm.cockpit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Exactly three radii.
 *
 * There were nine in use before this — 8, 9, 10, 12, 14, 16, 18, 22 and 26dp — spread
 * across a token file, four drawables and three services, none of which knew about
 * the others. The camera overlay was rounded at 12dp, sitting on a canvas card
 * rounded at 18dp, beside a Compose card rounded at 16dp. That disagreement is a
 * large part of why the overlay never looked like part of the system.
 *
 * - [s] — chips, buttons, small controls, the grab handle
 * - [m] — **cards, tiles and every overlay panel.** The workhorse.
 * - [l] — full-bleed hero containers
 *
 * Circular elements use [androidx.compose.foundation.shape.CircleShape]. That is a
 * shape rather than a radius value, so it is not a fourth entry here — flagging it
 * because "exactly three" and "some things are round" have to be reconciled somehow,
 * and this is the reconciliation.
 */
object DwmRadius {
    val s: Dp = 8.dp
    val m: Dp = 16.dp
    val l: Dp = 28.dp
}

object DwmShapes {
    val small: Shape = RoundedCornerShape(DwmRadius.s)
    val medium: Shape = RoundedCornerShape(DwmRadius.m)
    val large: Shape = RoundedCornerShape(DwmRadius.l)
}

/** The same three handed to `MaterialTheme`, so M3's own surfaces agree. */
val DwmMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(DwmRadius.s),
    small = RoundedCornerShape(DwmRadius.s),
    medium = RoundedCornerShape(DwmRadius.m),
    large = RoundedCornerShape(DwmRadius.l),
    extraLarge = RoundedCornerShape(DwmRadius.l)
)
