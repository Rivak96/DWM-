package com.dwm.cockpit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape

/**
 * Shapes, from the radius scale in [DwmRadius].
 *
 * The home screen previously used ten different corner radii — 2, 4, 6, 7, 8, 10,
 * 12, 14, 16 and 18dp — each chosen at its own call site. Four is enough, and
 * having only four is what makes the rounding read as a decision.
 */
object DwmShapes {
    val small: Shape = RoundedCornerShape(DwmRadius.s)
    val medium: Shape = RoundedCornerShape(DwmRadius.m)
    val large: Shape = RoundedCornerShape(DwmRadius.l)
    val pill: Shape = RoundedCornerShape(DwmRadius.pill)
    val icon: Shape = RoundedCornerShape(DwmRadius.icon)
}

/** The same four, handed to `MaterialTheme` so M3's own surfaces agree. */
val DwmMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(DwmRadius.s),
    small = RoundedCornerShape(DwmRadius.s),
    medium = RoundedCornerShape(DwmRadius.m),
    large = RoundedCornerShape(DwmRadius.l),
    extraLarge = RoundedCornerShape(DwmRadius.l)
)
