package com.dwm.cockpit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The type scale — the single biggest reason the launcher looked cheap.
 *
 * The home screen was written with inline literals of 8, 9, 10, 11 and 19sp, and
 * then `Scale.kt` multiplies the entire display density by `Prefs.uiScale`, which
 * defaults to 0.8. So a bottom-bar label specified at 8sp rendered at about 6.4sp
 * on a thirteen-inch panel a metre from the driver. It was not a colour problem or
 * a layout problem; the text was simply too small to read, and small uniform text
 * is what makes an interface look like a settings dump instead of a product.
 *
 * Every size below is therefore given twice: the raw value written in code, and
 * what it actually becomes at the default 0.8 scale.
 *
 * | token     | raw   | effective | replaced |
 * |-----------|-------|-----------|----------|
 * | [speed]   | 120sp | 96sp      | a numeral lost in empty grey |
 * | [display] | 56sp  | 45sp      | — |
 * | [headline]| 34sp  | 27sp      | — |
 * | [title]   | 24sp  | 19sp      | 19sp |
 * | [body]    | 20sp  | 16sp      | 12–13sp |
 * | [label]   | 17sp  | 13.6sp    | 10–11sp |
 * | [caption] | 15sp  | 12sp      | 9sp |
 * | [micro]   | 13sp  | 10.4sp    | 8sp — and this is the floor |
 *
 * `Scale.kt` itself is deliberately left alone. It is applied in four separate
 * `attachBaseContext` overrides and the overlay services depend on its pixel
 * geometry surviving; the correct fix for text that is too small was never a
 * different global multiplier, it was having a type scale at all.
 */
object DwmType {

    /**
     * Swap in a bundled face here and the whole app follows, including the XML
     * screens' Compose siblings. Drop the `.ttf` files into `res/font/` and this
     * becomes:
     *
     * ```
     * val family = FontFamily(
     *     Font(R.font.inter_regular, FontWeight.Normal),
     *     Font(R.font.inter_medium, FontWeight.Medium),
     *     Font(R.font.inter_semibold, FontWeight.SemiBold)
     * )
     * ```
     *
     * Left as the system face for now so the visual rebuild can be judged on its
     * own before an asset is added — three static weights are roughly 250 KB, and
     * this project has twice cut dependencies to keep the APK near 6 MB.
     */
    val family: FontFamily = FontFamily.SansSerif

    val speed: TextUnit = 120.sp
    val display: TextUnit = 56.sp
    val headline: TextUnit = 34.sp
    val title: TextUnit = 24.sp
    val body: TextUnit = 20.sp
    val label: TextUnit = 17.sp
    val caption: TextUnit = 15.sp

    /** Hard floor. Nothing on the home screen may be smaller than this. */
    val micro: TextUnit = 13.sp

    /** Lower bound for the responsive speed numeral. Even on a short panel it
     *  stays the largest thing on screen — that is the point of it. */
    val speedMin: TextUnit = 56.sp

    /** All-caps card headings. The letter spacing is what stops a 13sp caption
     *  reading as body text that failed to grow. */
    val overline: TextStyle = TextStyle(
        fontFamily = family,
        fontSize = micro,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp
    )

    /**
     * Tabular figures. Without `tnum` every digit has its own width, so a speed or
     * voltage readout visibly shifts sideways while the vehicle stands still.
     */
    const val TABULAR = "tnum"
}

/**
 * Material's own scale, so any `Text` that does not go through `DwmText` — and
 * anything M3 draws for itself — lands on the same family and rhythm rather than
 * silently falling back to Roboto at Material's default sizes.
 */
fun dwmTypography(family: FontFamily = DwmType.family): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family, fontSize = DwmType.display),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = d.headlineLarge.copy(fontFamily = family, fontSize = DwmType.headline),
        headlineMedium = d.headlineMedium.copy(fontFamily = family),
        headlineSmall = d.headlineSmall.copy(fontFamily = family),
        titleLarge = d.titleLarge.copy(fontFamily = family, fontSize = DwmType.title),
        titleMedium = d.titleMedium.copy(fontFamily = family),
        titleSmall = d.titleSmall.copy(fontFamily = family),
        bodyLarge = d.bodyLarge.copy(fontFamily = family, fontSize = DwmType.body),
        bodyMedium = d.bodyMedium.copy(fontFamily = family, fontSize = DwmType.label),
        bodySmall = d.bodySmall.copy(fontFamily = family, fontSize = DwmType.caption),
        labelLarge = d.labelLarge.copy(fontFamily = family, fontSize = DwmType.label),
        labelMedium = d.labelMedium.copy(fontFamily = family, fontSize = DwmType.caption),
        labelSmall = d.labelSmall.copy(fontFamily = family, fontSize = DwmType.micro)
    )
}
