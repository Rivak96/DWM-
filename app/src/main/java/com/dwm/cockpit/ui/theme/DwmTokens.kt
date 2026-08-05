package com.dwm.cockpit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The theme. Colour, spacing, grid, radius, elevation.
 *
 * **Every colour, size, radius and duration in DWM comes from this file or from
 * [DwmType]/[DwmShapes]/[DwmMotion] beside it. There are no exceptions and no
 * call-site literals.** If a value is needed that is not here, the answer is to add
 * it here deliberately, not to invent one locally — that rule is the only reason the
 * result reads as one system rather than as several screens built on different days.
 *
 * The numbers are stated once, as ARGB [Int]s in [DwmPalette], because DWM has two
 * UI stacks: Compose (home) and View/XML (settings, drawer, editor, overlays). Both
 * derive from the same constants — [DwmColors] for Compose, `Ui.Theme` for the
 * views — so a palette change cannot reach one and miss the other. Before this,
 * three palettes existed simultaneously and `res/values/colors.xml` was still
 * carrying a Tesla preset that had been abandoned two releases earlier.
 *
 * ### Why these values, and why they are not Material defaults
 *
 * This is read through a windscreen, in Trinidad daylight, from about a metre, by
 * someone who should be looking at the road. So:
 *
 * - **Surfaces are near-black, not mid-grey.** The previous scheme put `#3B3E43`
 *   cards on a `#292B2E` field. That eighteen-point step measures fine and collapses
 *   into one flat wash on this deck's cheap IPS, which lifts blacks badly. Three
 *   levels now, each far enough apart to survive the panel.
 * - **Hairlines are pushed harder than looks right on a desk monitor.** [HAIRLINE]
 *   sits at ~1.7:1 against [SURFACE] where the usual divider is nearer 1.1:1. On a
 *   glossy panel in sunlight the softer line simply is not there.
 * - **Nothing is pure white in either variant.** White reflects in the windscreen at
 *   night. [TEXT] tops out at `#E8EBF0` by day and `#C3C9D2` after dark.
 * - **One accent, on one element per screen.** [ACCENT] is the nav rail's travelling
 *   bar and nothing else. [OK]/[WARN]/[CRITICAL] are semantic and are never used for
 *   emphasis, decoration or navigation — an orange pixel means something is wrong
 *   with the vehicle, always.
 *
 * Contrast is not left to judgement: `DwmContrastTest` asserts every text/surface and
 * hairline/surface pair in both variants on every build.
 */

/* ---------------------------------------------------------------- the palette */

/**
 * The source values, as ARGB ints so both UI stacks can read them.
 *
 * Day is the brief's palette with two adjustments, both made because a number was
 * measured rather than eyeballed: [MUTED] went one step lighter so it still clears
 * 4.5:1 against [RAISED] (it was 4.46:1 on the darker value — fine on [SURFACE],
 * marginal on a raised card), and [HAIRLINE] went two steps lighter for the daylight
 * reason above.
 *
 * Night is not a second design. It is the same design with the luminance pulled down
 * and the accent desaturated, so that nothing on the panel is bright enough to sit in
 * the driver's peripheral vision or reflect in the glass. Text and accent are lifted
 * back just far enough to hold 4.5:1 — dimming past legibility is a safety problem,
 * not a style.
 */
object DwmPalette {

    /* ---- day ---- */
    const val BACKGROUND = 0xFF0A0C0F.toInt()
    const val SURFACE    = 0xFF14171C.toInt()
    const val RAISED     = 0xFF1C2027.toInt()
    const val HAIRLINE   = 0xFF39414D.toInt()
    const val TEXT       = 0xFFE8EBF0.toInt()
    const val MUTED      = 0xFF808B9B.toInt()
    /**
     * Ice blue, not the phone-default blue it was (`#4C8FD8`).
     *
     * Two reasons. It reads Ford/Rivian rather than Android, which is the whole
     * point of the exercise; and it holds up against a bright map or a daylight
     * camera feed sitting right beside it, which the old blue did not — that matters
     * now that most of the screen is live content rather than dark cards.
     *
     * 11.2 / 10.2 / 9.3 : 1 against background / surface / raised. Bright, but still
     * below [TEXT] at 13.6:1, so the accent never out-shouts the type.
     */
    const val ACCENT     = 0xFF5FD3E8.toInt()
    const val OK         = 0xFF4FA97C.toInt()
    const val WARN       = 0xFFD9A03C.toInt()

    /**
     * Lifted from `#D9564C`. That value clears 5.0:1 on [BACKGROUND] and 5.3:1 on
     * [SURFACE] — both fine — but only 4.15:1 on [RAISED], and a critical warning
     * appears on a raised overlay panel more often than anywhere else. The one
     * colour that has to survive a glance through a windscreen does not get to be
     * the one colour that fails on the surface it most often sits on.
     */
    const val CRITICAL   = 0xFFE4635A.toInt()

    /* ---- night ---- */
    const val N_BACKGROUND = 0xFF06080A.toInt()
    const val N_SURFACE    = 0xFF0F1216.toInt()
    const val N_RAISED     = 0xFF161A1F.toInt()
    const val N_HAIRLINE   = 0xFF333B47.toInt()
    const val N_TEXT       = 0xFFC3C9D2.toInt()
    const val N_MUTED      = 0xFF7B8695.toInt()
    const val N_ACCENT     = 0xFF4FB8CE.toInt()
    const val N_OK         = 0xFF4A9873.toInt()
    const val N_WARN       = 0xFFAE8030.toInt()

    /**
     * Barely dimmer than the day value, and deliberately so. Everything else on the
     * night palette steps down; a critical alert does not, because "dimmed enough
     * not to reflect in the glass" and "legible when the vehicle is telling you
     * something is wrong" resolve in favour of the second one.
     */
    const val N_CRITICAL   = 0xFFD2665A.toInt()

    /** Pressed-state wash. Alpha over whatever it sits on, so it needs no variant. */
    const val PRESS = 0x1AFFFFFF.toInt()

    /** Behind a modal or a raised overlay. Never a blur — see [DwmElevation]. */
    const val SCRIM = 0xB3000000.toInt()
}

/** The palette as the composables consume it. */
data class DwmColors(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val hairline: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val ok: Color,
    val warn: Color,
    val critical: Color,
    val press: Color,
    val scrim: Color,
    /** True after dark. Read it to *choose* a token, never to hardcode a colour. */
    val night: Boolean
)

private fun c(argb: Int) = Color(argb)

val DwmDayColors = DwmColors(
    background = c(DwmPalette.BACKGROUND),
    surface = c(DwmPalette.SURFACE),
    raised = c(DwmPalette.RAISED),
    hairline = c(DwmPalette.HAIRLINE),
    text = c(DwmPalette.TEXT),
    muted = c(DwmPalette.MUTED),
    accent = c(DwmPalette.ACCENT),
    ok = c(DwmPalette.OK),
    warn = c(DwmPalette.WARN),
    critical = c(DwmPalette.CRITICAL),
    press = c(DwmPalette.PRESS),
    scrim = c(DwmPalette.SCRIM),
    night = false
)

val DwmNightColors = DwmColors(
    background = c(DwmPalette.N_BACKGROUND),
    surface = c(DwmPalette.N_SURFACE),
    raised = c(DwmPalette.N_RAISED),
    hairline = c(DwmPalette.N_HAIRLINE),
    text = c(DwmPalette.N_TEXT),
    muted = c(DwmPalette.N_MUTED),
    accent = c(DwmPalette.N_ACCENT),
    ok = c(DwmPalette.N_OK),
    warn = c(DwmPalette.N_WARN),
    critical = c(DwmPalette.N_CRITICAL),
    press = c(DwmPalette.PRESS),
    scrim = c(DwmPalette.SCRIM),
    night = true
)

/**
 * Not `staticCompositionLocalOf`. The debug tweak panel rewrites the accent live
 * while the user sits in the truck with the engine off, and a static local skips
 * recomposition of everything that reads it — the sliders would appear to do nothing.
 */
val LocalDwmColors = compositionLocalOf { DwmDayColors }

/** `Dwm.colors.muted` at the call site. */
object Dwm {
    val colors: DwmColors
        @Composable @ReadOnlyComposable get() = LocalDwmColors.current
}

/* ------------------------------------------------------------------- spacing */

/**
 * A 4dp scale, and a generous one. Cramped is what reads as cheap.
 *
 * Every gap on the old home screen was its own number — 3, 4, 5, 6, 7, 9, 10, 14,
 * 18dp — which is what makes a layout look assembled even when no single value is
 * wrong. There are eight steps here and nothing may fall between them.
 *
 * These are true dp. `Prefs.uiScale` now defaults to 1.0, so unlike every previous
 * version of this file the numbers below are what actually reaches the glass.
 */
object DwmSpace {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
    val huge: Dp = 64.dp

    /** Card interior. Anything less and a 1600dp canvas reads as a phone blown up. */
    val cardPadding: Dp = xl

    /** Distance from any screen edge to content. Matches [DwmGrid.margin]. */
    val screenMargin: Dp = xxl
}

/* ---------------------------------------------------------------------- grid */

/**
 * Twelve columns across the content width, and everything snaps to them.
 *
 * The old home screen had no grid and six competing ratio systems instead: two
 * hardcoded column weights, a three-branch `when` picking a third weight, a tile
 * size clamped against a magic 168, an icon size at 42% of the smaller edge, and a
 * label at 11% of the height. Nothing shared a rhythm with anything, which is
 * exactly why the right-hand column died and the top row did not line up with the
 * row beneath it.
 *
 * Sizes are computed from the available width rather than baked in, for two reasons:
 * the nav rail's width is a token that may change, and the debug tweak panel's gutter
 * slider has to actually move something.
 *
 * At the panel's 1600dp minus a [DwmSize.railWidth] rail, that resolves to columns of
 * about 98dp on a 122dp pitch.
 */
object DwmGrid {
    const val COLUMNS = 12
    val gutter: Dp = DwmSpace.xl
    val margin: Dp = DwmSpace.xxl

    /** Width of one column once margins and gutters are taken out of [available]. */
    fun columnWidth(available: Dp): Dp =
        (available - margin * 2 - gutter * (COLUMNS - 1)) / COLUMNS

    /** Width of a [span]-column region, gutters between them included. */
    fun span(available: Dp, span: Int): Dp =
        columnWidth(available) * span + gutter * (span - 1)
}

/* -------------------------------------------------------------------- sizing */

/**
 * The few structural sizes that are not spacing, radius or type.
 *
 * [touchTarget] and [touchTargetMoving] are the load-bearing pair. 72dp is the floor
 * for anything tappable; 96dp applies to anything that will be pressed while the
 * truck is moving, where the hand is unsteady and the eyes should be elsewhere. The
 * nav rail is built at 96dp for that reason and not for a visual one.
 */
object DwmSize {
    val touchTarget: Dp = 72.dp
    val touchTargetMoving: Dp = 96.dp

    /**
     * The system bar along the bottom — DWM's navigation, on every screen.
     *
     * It replaced a right-edge rail. The rail was the better ergonomic argument in a
     * right-hand-drive truck, and it was not where the driver wanted the controls;
     * v0.24 had them along the bottom. Where a control lives is the driver's call.
     */
    val systemBar: Dp = 88.dp
    val railIcon: Dp = 32.dp

    /**
     * The travelling accent bar. The only accent-coloured element on a screen.
     *
     * It sits above the active item on the system bar, and under the active tab in
     * Settings — one element, two orientations, once per screen.
     */
    val railBarWidth: Dp = DwmSpace.xs
    val railBarLength: Dp = DwmSpace.xxxl

    /**
     * One app tile, per row. Fixed rather than derived from the tile's width.
     *
     * It was `width * 0.62`, which meant three apps produced a 345dp-tall tile
     * holding a 48dp glyph and one line of text — an acre of empty surface — while
     * twelve apps produced something reasonable. A tile is sized by what is in it,
     * and what is in it does not change with how many there are.
     */
    val tile: Dp = 176.dp

    /** A tile in the in-pane app drawer. Smaller than [tile] because the drawer is
     *  browsed while parked, not hit while moving. */
    val drawerTile: Dp = 120.dp

    /**
     * Album art. Fixed, not derived from the card's height.
     *
     * It was `fillMaxHeight().aspectRatio(1f)`, which meant the art grew with the row
     * and at 260dp took a 220dp square — squeezing the title and artist column to
     * zero width so the track name simply vanished. Artwork is an illustration of
     * what is playing; the title is the information, and the information does not get
     * to lose a fight with the decoration.
     */
    val albumArt: Dp = 112.dp

    val icon: Dp = 24.dp
    val iconLarge: Dp = 32.dp

    /**
     * The glyph on an app tile. Larger than [iconLarge] because a tile is a target
     * hit while moving and the glyph is the whole of what identifies it — but fixed
     * rather than derived from the tile size, so three apps and twelve apps produce
     * the same mark at the same weight instead of one scaling rule quietly
     * generating two different designs.
     */
    val tileGlyph: Dp = 48.dp

    /** Top status strip. */
    val topStrip: Dp = 88.dp

    /**
     * A cockpit pane's header strip.
     *
     * Load-bearing, not trim. A live app window covers its pane exactly and consumes
     * every touch inside it, so a swipe started over a map would go to the map. This
     * strip is the one part of a pane that always belongs to DWM, which makes it the
     * only place the source swipe and the position dots can live.
     */
    val paneHeader: Dp = 48.dp

    /**
     * Vertical room one `Reading` needs — overline, value and breathing space.
     *
     * Used to decide how many readings a column can show. When the camera overlay
     * reserves the top-right corner the vitals column loses half its height, and
     * without this it simply clipped the last two readings mid-glyph. A column that
     * shows fewer things is a design; a column that cuts one in half is a bug.
     */
    val readingBlock: Dp = 88.dp

    /**
     * The media strip, and it is always this tall. An idle music widget used to be
     * one of the largest things on the home screen while holding no information; the
     * fix is not to animate it smaller but to never let it be big. Artwork, title,
     * artist and three transport buttons all fit inside this, so the box is the same
     * height whether something is playing or not and the layout never reflows.
     */
    val mediaStrip: Dp = touchTargetMoving

    /** The one-line vehicle instrument strip: overline plus a 28sp value, padded. */
    val vehicleBar: Dp = 104.dp

    /**
     * Media, vehicle diagram and quick toggles share this row.
     *
     * 260 rather than 200 because the vehicle diagram needs a portrait region to draw
     * a vehicle into. At 200 the drawable area came out wider than it was tall and
     * the truck rendered as a landscape box, which is not what one looks like from
     * above.
     */
    val bottomRow: Dp = 260.dp

    /** Overlay grab handle: a bar, not a glyph, and always visible. */
    val grabWidth: Dp = DwmSpace.xxxl
    val grabHeight: Dp = 6.dp
}

/* ------------------------------------------------------------------- strokes */

object DwmStroke {
    /** Hairlines carry the layer separation on this panel. They are not decoration. */
    val hairline: Dp = 1.dp
}

/* ----------------------------------------------------------------- elevation */

/**
 * Three levels, expressed as tone plus hairline rather than as shadow.
 *
 * Shadow alone does not work here: the panel lifts blacks, so a soft ambient shadow
 * on a near-black field is invisible, and real-time blur — the other usual way to
 * separate a floating layer — needs API 31 while this deck is API 29. Depth is
 * therefore carried by a tonal step and a border, which survive both the panel and
 * the platform. [E2] adds a small ambient shadow on top because View elevation is
 * free at API 29 and it helps when an overlay sits over a bright camera feed.
 *
 * There is no fourth level, and no card ever sits inside another card.
 */
object DwmElevation {
    /** The field. No border, no shadow. */
    val E0: Dp = 0.dp

    /** A card at rest: [DwmColors.surface] plus a hairline. */
    val E1: Dp = 0.dp

    /** Floating over content — every overlay panel, and only those. */
    val E2: Dp = DwmSpace.s
}
