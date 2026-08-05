package com.dwm.cockpit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.dwm.cockpit.R

/**
 * Two faces, one scale.
 *
 * ### The faces, and why these two
 *
 * **Space Grotesk** (Florian Karsten) for everything that is words. It is drawn from
 * Space Mono's skeleton, so it shares its proportions and its squared terminals with
 * a monospace — which means it sits beside the numeric face as family rather than as
 * a second opinion. That squared, slightly technical character is what makes a screen
 * read as an instrument instead of a phone launcher. Archivo was the other candidate
 * and sits too close to Roboto, which is the exact thing being avoided; General Sans
 * is well made and completely anonymous.
 *
 * **JetBrains Mono** for every number. Two reasons that matter at a metre through a
 * windscreen: it has an unusually large x-height, so digits carry further than their
 * point size suggests, and its zero is dotted rather than slashed, which stays clean
 * when the panel is resolving badly in sunlight. IBM Plex Mono is warmer and has both
 * a smaller x-height and a fussier `1`.
 *
 * Both are SIL OFL 1.1; the full licences ship in `assets/FONT_LICENSES.txt`.
 *
 * ### Why the files are small
 *
 * The four bundled faces total **147 KB**, not the ~770 KB they arrive as. They are
 * subset to Latin plus the punctuation and symbols DWM can actually render, which
 * takes JetBrains Mono from 270 KB a weight to 35 KB by dropping Cyrillic, Greek and
 * a large programming-ligature set this launcher will never show. Kerning (`GPOS
 * kern`) and tabular figures (`tnum`) are explicitly retained. To regenerate:
 *
 * ```
 * python -m fontTools.subset SpaceGrotesk-Regular.ttf \
 *   --unicodes="U+0020-007E,U+00A0-00FF,U+2010-2015,U+2018-201D,U+2020-2022,\
 *               U+2026,U+2030,U+2039-203A,U+2044,U+20AC,U+2122,U+2190-2193,\
 *               U+2212,U+25A0-25CF,U+2713,U+266B" \
 *   --layout-features='kern,liga,tnum,zero' --name-IDs='*'
 * ```
 *
 * Anything outside that range renders as tofu, so a new glyph means a new subset.
 *
 * ### The scale
 *
 * | token       | size | face    | for |
 * |-------------|------|---------|-----|
 * | [hero]      | 44sp | display | the one large element on a screen |
 * | [display]   | 34sp | display | card headline |
 * | [value]     | 28sp | mono    | any reading meant to be taken at a glance |
 * | [title]     | 24sp | display | card titles |
 * | [body]      | 18sp | display | **floor for anything read while moving** |
 * | [label]     | 16sp | display | controls, tile labels |
 * | [data]      | 16sp | mono    | dense tabular readouts — diagnostics |
 * | [caption]   | 14sp | display | units and timestamps beside a larger value |
 * | [overline]  | 13sp | display | all-caps card headings |
 *
 * [caption] and [overline] sit below the 18sp floor deliberately and are the only
 * things that may. Neither is ever a sentence: [caption] is the `km/h` next to a
 * number you are already looking at, [overline] is a heading you read once. Body text
 * at 14sp is what made the last four builds look like a settings dump, and there is
 * no token here that permits it.
 *
 * These are true sp. `Prefs.uiScale` defaulted to 0.8 until this rebuild, so every
 * previous version of this table was a fiction — the 13sp floor was reaching the eye
 * at 10.4sp.
 */
object DwmType {

    /** Words. */
    val display: FontFamily = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium)
    )

    /**
     * Numbers. Monospaced, so figures are tabular by construction and a readout
     * cannot shuffle sideways as it updates — no `tnum` feature required, unlike
     * [display], whose default figures are proportional across nine different widths.
     */
    val mono: FontFamily = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
    )

    /** Tabular figures for [display]. Mandatory on any [display]-set number. */
    const val TABULAR = "tnum"

    val hero: TextStyle = TextStyle(
        fontFamily = display, fontSize = 44.sp, lineHeight = 48.sp,
        fontWeight = FontWeight.Medium
    )
    val headline: TextStyle = TextStyle(
        fontFamily = display, fontSize = 34.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.Medium
    )
    val value: TextStyle = TextStyle(
        fontFamily = mono, fontSize = 28.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.Medium
    )
    val title: TextStyle = TextStyle(
        fontFamily = display, fontSize = 24.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.Medium
    )
    val body: TextStyle = TextStyle(
        fontFamily = display, fontSize = 18.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.Normal
    )
    val label: TextStyle = TextStyle(
        fontFamily = display, fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    )
    val data: TextStyle = TextStyle(
        fontFamily = mono, fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    )
    val caption: TextStyle = TextStyle(
        fontFamily = display, fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    )

    /**
     * All-caps card headings. The tracking is what stops a small heading reading as
     * body text that failed to grow.
     */
    val overline: TextStyle = TextStyle(
        fontFamily = display, fontSize = 13.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp
    )

    /** The clock. Quiet by intent — the signature is spent on the nav rail. */
    val clock: TextStyle = TextStyle(
        fontFamily = mono, fontSize = 24.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Normal
    )

    /**
     * What a reading looks like when nothing is flowing.
     *
     * An em dash, in the numeric face, at the same size and the same tabular width as
     * a live value. Not an orange NO SENSOR DATA label, not a zero pretending to be a
     * reading, and not a smaller or greyer version of the number — the slot looks
     * deliberately empty rather than broken, and because the placeholder occupies a
     * real digit's width the layout does not move when data starts arriving.
     */
    const val NO_SIGNAL = "—"

    /** Sizes, for the rare case that needs a bare number rather than a style. */
    val bodySize: TextUnit = 18.sp
    val floorSize: TextUnit = 18.sp
}

/**
 * Material's own scale, so anything M3 draws for itself lands on the same faces and
 * rhythm instead of silently falling back to Roboto at Material's sizes.
 */
fun dwmTypography(): Typography =
    Typography(
        displayLarge = DwmType.hero,
        displayMedium = DwmType.hero,
        displaySmall = DwmType.headline,
        headlineLarge = DwmType.headline,
        headlineMedium = DwmType.headline,
        headlineSmall = DwmType.title,
        titleLarge = DwmType.title,
        titleMedium = DwmType.title,
        titleSmall = DwmType.label,
        bodyLarge = DwmType.body,
        bodyMedium = DwmType.body,
        bodySmall = DwmType.label,
        labelLarge = DwmType.label,
        labelMedium = DwmType.label,
        labelSmall = DwmType.overline
    )
