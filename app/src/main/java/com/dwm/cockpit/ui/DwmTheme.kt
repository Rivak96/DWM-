package com.dwm.cockpit.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dwm.cockpit.Ui

/** Material 3 theme derived from the active DWM theme preset (Ui.th). The JSON
 *  theme engine will feed these values in a later stage. */
@Composable
fun DwmTheme(context: Context, content: @Composable () -> Unit) {
    val t = Ui.th(context)
    val accent = Color(Ui.accent(context))
    val scheme = if (t.light) {
        lightColorScheme(
            primary = accent,
            background = Color(t.bg),
            surface = Color(t.surface),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(t.text),
            onSurface = Color(t.text)
        )
    } else {
        darkColorScheme(
            primary = accent,
            background = Color(t.bg),
            surface = Color(t.surface),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(t.text),
            onSurface = Color(t.text)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
