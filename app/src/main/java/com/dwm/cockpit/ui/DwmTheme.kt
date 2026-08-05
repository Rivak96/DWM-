package com.dwm.cockpit.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
 * There used to be four presets here — Tesla, Midnight, Light and Cockpit — and a
 * Light one in particular cannot coexist with a design whose premise is near-black
 * surfaces and nothing pure white. There is now **one design in two variants**, day
 * and night, and the variant is a fact about the world rather than a preference.
 * See [Ui.night] for how that fact is established.
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
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.text,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.raised,
        onSurfaceVariant = colors.muted,
        outline = colors.hairline,
        outlineVariant = colors.hairline,
        error = colors.critical,
        onError = colors.text,
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
