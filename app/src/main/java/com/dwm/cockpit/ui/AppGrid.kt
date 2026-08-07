package com.dwm.cockpit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmType

/**
 * What is left of the Compose app grid.
 *
 * `AppGrid` and its `AppTile` lived here and were drawn in exactly one place: the stage,
 * before an app had ever been chosen. The stage is a card for the chosen app now, with
 * its own empty state, so the grid had no caller and went. Browsing every installed app
 * is `AppDrawerActivity`, which is a separate View/`GridView` implementation and never
 * shared any of this.
 *
 * [Unavailable] stays because `CameraBox` still uses it.
 */

/**
 * A chosen source whose view could not be built.
 *
 * Names what it was trying to show rather than pretending nothing was picked. On the
 * deck this is a panel that failed to open; in a golden it is simply the renderer
 * having no camera hardware.
 */
@Composable
fun Unavailable(label: String) {
    val colors = Dwm.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DwmText(label.uppercase(), style = DwmType.overline, color = colors.muted)
    }
}
