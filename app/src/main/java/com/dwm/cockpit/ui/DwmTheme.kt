package com.dwm.cockpit.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.dwm.cockpit.Ui
import com.dwm.cockpit.ui.theme.DwmColors
import com.dwm.cockpit.ui.theme.DwmMaterialShapes
import com.dwm.cockpit.ui.theme.LocalDwmColors
import com.dwm.cockpit.ui.theme.StatusDanger
import com.dwm.cockpit.ui.theme.dwmTypography

/**
 * The one place a `Ui.Theme` preset becomes Compose state.
 *
 * `Ui.th()` stays the single source of truth for the palette, which is what lets a
 * single preset restyle both halves of this app: the Compose home reads it through
 * here, and the XML screens — Settings, the app drawer, the layout editor — get the
 * same colours through `Ui.skin()` walking their view trees. Neither of those
 * screens had to be rewritten to follow the new look.
 *
 * The dependency runs one way. `ui.theme` defines the tokens and knows nothing
 * about `Ui`; this file is the adapter between them.
 */
@Composable
fun DwmTheme(context: Context, content: @Composable () -> Unit) {
    val t = Ui.th(context)
    val accent = Color(Ui.accent(context))

    val colors = DwmColors(
        bg = Color(t.bg),
        cardTop = Color(t.cardTop),
        cardBottom = Color(t.cardBottom),
        cardBorder = Color(t.cardBorder),
        // A step above the background but below a card, for the surfaces that hold
        // cards rather than being one — the app grid page, mainly.
        surfaceLow = Color(Ui.blend(t.bg, t.surface, 0.35f)),
        accent = accent,
        textPrimary = Color(t.text),
        textSecondary = Color(t.dim),
        textTertiary = Color(t.textTertiary),
        hairline = Color(t.hairline),
        press = if (t.light) Color(0x14000000) else Color(0x14FFFFFF),
        light = t.light
    )

    val scheme = if (t.light) {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = Color(t.surface),
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceLow,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.cardBorder,
            error = StatusDanger
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = Color(t.surface),
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceLow,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.cardBorder,
            error = StatusDanger
        )
    }

    CompositionLocalProvider(LocalDwmColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = dwmTypography(),
            shapes = DwmMaterialShapes,
            content = content
        )
    }
}
