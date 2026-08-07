package com.dwm.cockpit.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmIcons
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType

/**
 * The app grid, and the two small pieces that go with it.
 *
 * These were the reusable third of the deleted `CockpitPanes.kt` — a self-contained
 * grid taking a list of apps and two callbacks, with no knowledge of panes, splits or
 * window rects. It is the only Compose app drawer in the project; `AppDrawerActivity`
 * is a separate View/`GridView` implementation and shares none of this.
 *
 * It is drawn in exactly one place now: the app stage, when no app has been chosen
 * yet. Once a window is on the stage this grid is unreachable from the home screen —
 * a freeform app floats *above* DWM and nothing DWM draws can sit on top of it, which
 * is why swapping apps goes through the fullscreen `AppDrawerActivity` instead.
 */
@Composable
fun AppGrid(
    apps: List<HomeApp>,
    onLaunch: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    onWallpaper: Boolean = false
) {
    val colors = Dwm.colors
    if (apps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DwmText("No apps", style = DwmType.label, color = colors.muted)
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(DwmSpace.l)) {
        // Column count from the space available rather than a fixed number, so the
        // grid works at stage width and would still work in something narrower.
        val cols = ((maxWidth + DwmSpace.m) / (DwmSize.drawerTile + DwmSpace.m))
            .toInt().coerceIn(2, 6)

        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DwmSpace.m)
        ) {
            apps.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.m)) {
                    row.forEach { app ->
                        AppTile(app, onLaunch, onAppMenu, onWallpaper, Modifier.weight(1f))
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun AppTile(
    app: HomeApp,
    onLaunch: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    onWallpaper: Boolean,
    modifier: Modifier
) {
    val colors = Dwm.colors
    Column(
        modifier
            .clip(DwmShapes.small)
            .combinedClickable(
                onClick = { onLaunch(app.pkg) },
                onLongClick = { onAppMenu(app.pkg) }
            )
            .padding(vertical = DwmSpace.m),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(DwmSize.iconLarge), contentAlignment = Alignment.Center) {
            if (app.glyph != null) {
                Icon(
                    painter = painterResource(app.glyph),
                    contentDescription = app.label,
                    tint = colors.text,
                    modifier = Modifier.size(DwmSize.iconLarge)
                )
            } else {
                DwmText(DwmIcons.monogram(app.label), style = DwmType.label, color = colors.text)
            }
        }
        Spacer(Modifier.height(DwmSpace.s))
        DwmText(
            app.label,
            style = DwmType.caption,
            // Full contrast over a photograph. Muted clears only 3.2:1 against the
            // wallpaper's brightest areas; the text colour clears 9.2:1, which is why
            // the picture does not have to be dimmed into invisibility to be safe.
            color = if (onWallpaper) colors.text else colors.muted,
            align = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

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
