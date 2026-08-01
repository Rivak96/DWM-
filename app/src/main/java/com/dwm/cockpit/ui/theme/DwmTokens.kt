package com.dwm.cockpit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colour, spacing and radius tokens.
 *
 * Before this file every one of these values was a literal at the call site —
 * roughly two hundred of them, with the three status colours redeclared in four
 * separate files and `Color.White.copy(alpha = …)` written out about forty times.
 * The hardcoded white is also why picking the Light theme produced white-on-white:
 * the palette said one thing and the call sites said another.
 *
 * Nothing here depends on [com.dwm.cockpit.Ui]. The mapping from a `Ui.Theme`
 * preset into [DwmColors] lives in `ui/DwmTheme.kt`, so the dependency runs one
 * way — Ui knows about tokens, tokens know nothing about Ui.
 */

/* ------------------------------------------------------------------ colours */

/**
 * The palette as the composables consume it.
 *
 * [cardTop]/[cardBottom] are a pair rather than one fill because the deck's panel
 * is a cheap IPS with poor black level. The old scheme put `#3B3E43` cards on a
 * `#292B2E` field — an eighteen-point step that measured fine and vanished
 * completely on the actual screen, which is what made the whole UI read as one
 * flat grey. A card is now separated from the background three ways at once: a
 * larger tonal step, an internal vertical gradient, and a hairline border. Any one
 * of them alone washes out; together they survive.
 */
data class DwmColors(
    val bg: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardBorder: Color,
    val surfaceLow: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,
    val press: Color,
    val light: Boolean
)

/* Semantic status colours. These do not vary by theme — a warning is the same
 * red in every preset — so they are constants rather than DwmColors fields. */
val StatusOk = Color(0xFF2ED573)
val StatusCaution = Color(0xFFFFA726)
val StatusDanger = Color(0xFFFF4757)

/** ARGB mirrors for the View-based screens, so `Ui.GREEN` and [StatusOk] can
 *  never drift apart the way the four Compose copies did. */
const val StatusOkArgb: Int = 0xFF2ED573.toInt()
const val StatusCautionArgb: Int = 0xFFFFA726.toInt()
const val StatusDangerArgb: Int = 0xFFFF4757.toInt()

/** The dominant-colour wash behind a full-page app icon. Raised from 0.26 — at
 *  the old value the page still read as grey with a tint rather than as a surface
 *  belonging to that app. */
const val AppWashAlpha: Float = 0.34f

/** Fallback so previews and any composable outside [DwmTheme] still render. */
val CockpitColors = DwmColors(
    bg = Color(0xFF08090B),
    cardTop = Color(0xFF1D2128),
    cardBottom = Color(0xFF14171C),
    cardBorder = Color(0x1AFFFFFF),
    surfaceLow = Color(0xFF101317),
    accent = Color(0xFF4C8DFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA8B0BC),
    textTertiary = Color(0xFF6C7480),
    hairline = Color(0x1FFFFFFF),
    press = Color(0x14FFFFFF),
    light = false
)

val LocalDwmColors = staticCompositionLocalOf { CockpitColors }

/** `Dwm.colors.textSecondary` at the call site. */
object Dwm {
    val colors: DwmColors
        @Composable @ReadOnlyComposable get() = LocalDwmColors.current
}

/* ------------------------------------------------------------------ spacing */

/**
 * One spacing scale. Every gap in the home screen used to be its own number —
 * 3, 4, 5, 6, 7, 8, 9, 10, 14, 18dp — which is what makes a layout read as
 * assembled rather than designed even when nothing is individually wrong.
 *
 * These are pre-`Scale` values: `Scale.kt` multiplies the whole display density
 * by `Prefs.uiScale` (0.8 by default), so the effective gap is 80% of what is
 * written here.
 */
object DwmSpace {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

/* ------------------------------------------------------------------- radius */

object DwmRadius {
    val s: Dp = 10.dp
    val m: Dp = 16.dp
    val l: Dp = 22.dp
    val pill: Dp = 999.dp

    /** App icon containers. A ~28% corner reads as a squircle at icon sizes
     *  without pulling in `androidx.graphics:graphics-shapes` for real path
     *  morphing — this project has already dropped two libraries over weight on
     *  the Unisoc chip, so a shape that is close enough for free wins. */
    val icon: Dp = 26.dp
}

/* ---------------------------------------------------------------- elevation */

object DwmStroke {
    /** Hairlines are load-bearing on this panel, not decoration — see [DwmColors]. */
    val hairline: Dp = 1.dp
}
