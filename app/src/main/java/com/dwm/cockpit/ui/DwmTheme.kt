package com.dwm.cockpit.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.dwm.cockpit.Ui
import com.dwm.cockpit.ui.theme.DwmDayColors
import com.dwm.cockpit.ui.theme.DwmMaterialShapes
import com.dwm.cockpit.ui.theme.DwmNightColors
import com.dwm.cockpit.ui.theme.LocalDwmColors
import com.dwm.cockpit.ui.theme.dwmTypography

/**
 * Where the palette becomes Compose state.
 *
 * There used to be four presets here — Tesla, Midnight, Light and Cockpit. There is
 * now **one design in two variants**, day and night, and the variant is a fact about
 * the world rather than a preference. See [Ui.night] for how that fact is established.
 *
 * Day is a genuinely light theme, which it was not until v0.32.0: both variants used
 * to be near-black four points apart, so picking Day changed nothing anyone could see.
 *
 * The dependency still runs one way: `ui.theme` holds the numbers and knows nothing
 * about `Ui`; this file and `Ui.th()` are the two adapters that read them, one for
 * each half of the app.
 */
@Composable
fun DwmTheme(context: Context, content: @Composable () -> Unit) {
    DwmThemeFrom(night = Ui.night(context), content = content)
}

/**
 * The variant-explicit form. Previews, snapshot tests and the debug tweak panel use
 * this so they can render a variant without a `Context` or a vehicle attached.
 */
@Composable
fun DwmThemeFrom(night: Boolean, content: @Composable () -> Unit) {
    val colors = if (night) DwmNightColors else DwmDayColors

    // M3's scheme is filled in so that any Material component drawing itself lands on
    // the same palette instead of falling back to purple. Nothing in DWM should be
    // reaching for these rather than Dwm.colors, but an unstyled stock component is
    // one of the failure modes this rebuild exists to eliminate, and the cheapest
    // insurance is for the fallback to be right too.
    //
    // The light/dark builder matters beyond the values: M3 derives elevation overlays
    // and a few defaults from which one it is, so a light palette poured into
    // `darkColorScheme` gets dark-mode tinting applied to white surfaces.
    val base = if (night) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.accent,
        onPrimary = if (night) colors.text else colors.raised,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.raised,
        onSurfaceVariant = colors.muted,
        outline = colors.hairline,
        outlineVariant = colors.hairline,
        error = colors.critical,
        onError = if (night) colors.text else colors.raised,
        scrim = colors.scrim
    )

    CompositionLocalProvider(LocalDwmColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = dwmTypography(),
            shapes = DwmMaterialShapes,
            content = content
        )
    }
}
