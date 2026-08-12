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
 * - **Day is light and night is dark, and they are genuinely different.** They were
 *   not: day was `#0A0C0F` and night `#06080A`, two near-blacks four points apart, so
 *   "Day" only ever meant a very slightly less dim dark. Daylight on a glossy IPS
 *   wants a bright field — a dark screen in sun is a mirror showing you your own
 *   dashboard — and the dark design moved to night where it belongs.
 * - **Three levels, far enough apart to survive the panel.** The deck's cheap IPS
 *   lifts blacks badly and washes out at the top, so neither variant may separate its
 *   surfaces by tone alone at close range. Day steps up toward white
 *   ([BACKGROUND] → [SURFACE] → [RAISED]); night steps up from black. Same logic,
 *   opposite direction — raised is always the level furthest from the field.
 * - **Hairlines are pushed harder than looks right on a desk monitor.** [HAIRLINE]
 *   sits at ~1.6:1 against [BACKGROUND] where the usual divider is nearer 1.1:1. On a
 *   glossy panel in sunlight the softer line simply is not there.
 * - **Nothing bright is pure white at night, and no foreground ever is.** White
 *   reflects in the windscreen after dark. Day may use white *surfaces* because it is
 *   daylight; night tops its text out at `#C3C9D2` and its brightest surface at
 *   `#161A1F`.
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
 * ### Day
 *
 * A real light theme, which it was not before: the old "day" was `#0A0C0F` against a
 * night of `#06080A`, so choosing Day changed almost nothing and the app had one
 * appearance pretending to be two. Every value below was picked against
 * `DwmContrastTest`'s thresholds rather than by eye, and the binding constraint is
 * [RAISED] at pure white — a foreground that clears 7:1 there clears it everywhere,
 * so the numbers are quoted against it.
 *
 * The support colours are all darkened well past their night counterparts, because a
 * mid-tone that reads clearly on near-black is invisible on near-white. The ice blue
 * in particular cannot survive the inversion: `#5FD3E8` manages 1.4:1 on white, so
 * the day accent is the same hue taken down to a teal that holds 6.2:1.
 *
 * ### Night
 *
 * Unchanged, and still the design this app was built around. Luminance pulled down and
 * the accent desaturated, so that nothing on the panel is bright enough to sit in the
 * driver's peripheral vision or reflect in the glass. Text and accent are lifted back
 * just far enough to hold 4.5:1 — dimming past legibility is a safety problem, not a
 * style.
 */
object DwmPalette {

    /* ---- day ---- */

    /** The field. A light grey rather than white, so [SURFACE] cards can sit *on* it
     *  and be seen doing so without a shadow — which this deck cannot draw. */
    const val BACKGROUND = 0xFFE4E9EF.toInt()
    const val SURFACE    = 0xFFF2F5F8.toInt()

    /** Pure white, and the only pure white in the design. It is the top of the day
     *  ramp the way `#161A1F` is the top of night's: the level furthest from the
     *  field, for overlay panels and anything sitting on a card. */
    const val RAISED     = 0xFFFFFFFF.toInt()

    /** 1.61:1 on [BACKGROUND], 1.96:1 on [RAISED]. Deliberately heavier than a
     *  divider normally gets — see the daylight note above. */
    const val HAIRLINE   = 0xFFB0BAC7.toInt()

    /** 18.3:1 on white. Near-black but not black: a true `#000000` on a bright panel
     *  in sun produces the same haloing the near-black surfaces avoid at night. */
    const val TEXT       = 0xFF12151A.toInt()
    const val MUTED      = 0xFF55606E.toInt()

    /**
     * The ice blue, inverted for a light field.
     *
     * Night keeps `#4FB8CE`; that value manages 1.4:1 on white and would be a pale
     * smear on the nav rail by day. This is the same hue pulled down to hold 6.2:1 on
     * [RAISED] and 5.1:1 on [BACKGROUND] — still recognisably the Ford/Rivian teal
     * rather than the phone-default blue this replaced two releases ago, and still
     * below [TEXT] at 18.3:1, so the accent never out-shouts the type.
     */
    const val ACCENT     = 0xFF0A6A7C.toInt()
    const val OK         = 0xFF17683F.toInt()

    /**
     * Amber is the hardest colour to carry onto a light field: it is bright by nature,
     * and the usual `#D9A03C` reads 2.0:1 on white. Taken down to a dark ochre it
     * clears 5.9:1 and still says "amber" beside the green and the red.
     */
    const val WARN       = 0xFF8A5A00.toInt()

    /**
     * 6.5:1 on [RAISED], 5.4:1 on [BACKGROUND]. The night value was chosen because a
     * critical warning appears on a raised overlay more often than anywhere else and
     * must not be the one colour that fails there; the same rule picked this one.
     */
    const val CRITICAL   = 0xFFB3261E.toInt()

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

    /**
     * Pressed-state wash — and it does need a variant now.
     *
     * It was one white-alpha value for both, on the reasoning that alpha over whatever
     * it sits on needs no variant. That held only while both variants were dark. A
     * white wash on a white card is invisible, so day darkens and night lightens.
     */
    const val PRESS   = 0x14000000.toInt()
    const val N_PRESS = 0x1AFFFFFF.toInt()

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
    press = c(DwmPalette.N_PRESS),
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

    /**
     * Card interior, and the frame around every card — see [DwmGrid.margin].
     *
     * `xl` (24dp) → `l` (16dp) → `m` (12dp), and the same sentence caused both moves: the
     * owner, looking at the real deck rather than a desk monitor, said the borders were
     * "a bit too big... we are wasting space". They said it again at 16dp. A 1600dp canvas
     * does not need a phone's breathing room, and on this panel the hairline — pushed to
     * ~1.6:1 for exactly this reason, see [DwmPalette.HAIRLINE] — is what actually
     * separates one surface from the next. The gap was never doing that work.
     *
     * **This token only means anything if call sites use it.** The 16dp step shipped with
     * five of seven `DwmCard` call sites hardcoding `padding = DwmSpace.l`, so the token
     * moved and the screen did not — the reduction reached the app box and nothing else.
     * Every card now passes `cardPadding`; the only deliberate exception is the favourites
     * dock at `s`, which is a row of targets rather than a card of content.
     */
    val cardPadding: Dp = m

    /** Distance from any screen edge to content. Matches [DwmGrid.margin]. */
    val screenMargin: Dp = m
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
 * Sizes are computed from the available width rather than baked in, so changing the frame
 * below re-flows the whole screen instead of leaving one region behind.
 *
 * ### One frame value, and it is small
 *
 * [margin] and [gutter] were 32 and 24, then 16, and are now 12. Three different gaps — at
 * the screen edge, between cards, and inside each one — is a large share of a 1600dp panel
 * spent on air, and the owner looking at the real deck has now said so twice: the borders
 * were "a bit too big... we are wasting space". One value, [DwmSpace.m], for all three, so
 * the frame around a card and the gap between two cards are the same measure and the grid
 * reads as a grid.
 *
 * **The hairline stays.** The instruction was to make the border small, not to remove it,
 * and on this panel the hairline is what carries layer separation at all — see
 * [DwmPalette.HAIRLINE], which is pushed to ~1.6:1 for exactly that reason. Shrinking the
 * gap makes the line matter more, not less.
 *
 * At the panel's 1600dp this resolves to columns of about 120dp on a 132dp pitch, up from
 * 116 on 132 — so the boxes gain width as well as losing padding, again.
 */
object DwmGrid {
    const val COLUMNS = 12
    val gutter: Dp = DwmSpace.m
    val margin: Dp = DwmSpace.m

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
     * The slot at **each** end of the system bar.
     *
     * The clock and date live in the left one and the vehicle's status indicators in the
     * right; the six nav items divide what is left. There is a slot at both ends, and the
     * right one is empty on Settings, because that is what keeps the nav items centred on
     * the panel instead of pushed off-centre by the clock.
     *
     * All of this used to be a full-width strip above the cards, which spent about 90dp of
     * a 1000dp panel carrying a clock, a date and a status dot — the one part of the home
     * screen holding nothing that needed the width. Moving it into the bar's own dead ends
     * costs no height at all and gives the boxes 60dp back.
     *
     * Sized for "07:04  Tue 11 Aug" at [DwmType.clock] plus a [DwmGrid.margin] inset, which
     * is the widest thing either end has to hold.
     */
    val barEdgeSlot: Dp = 240.dp

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

    /*
     * `paneHeader` (48dp) lived here and is gone. It described "the one part of a pane
     * that always belongs to DWM", which is a real thing — but it is `Prefs.captionDp`
     * and `StageChrome`'s header window now, measured against the ROM rather than
     * declared here. Its last caller was the home screen's clock strip, which was not a
     * pane header at all, and a token nothing sizes is how this file drifts.
     */

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
