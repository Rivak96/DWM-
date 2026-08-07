package com.dwm.cockpit.ui

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmStroke

/**
 * The stage — where the one app lives.
 *
 * This replaced the two-pane cockpit, which was rejected on the deck as buggy. That
 * was a fair verdict on the architecture rather than the polish: a live app is a real
 * freeform task, and `LaunchEngine.launchWindow` with `NEW_TASK` alone *moves* an
 * existing task rather than duplicating it, so every divider drag, source swipe and
 * split change relaunched a running app. Swiping a pane off an app also parked its
 * window off-screen, a manoeuvre that was never verified on this ROM. One window at
 * one fixed rect has none of those moving parts.
 *
 * The stage draws **nothing** when an app is on it. The window floats above this
 * activity, so anything painted here would simply be hidden — and painting a
 * background behind an opaque window is how you get a visible seam when the window's
 * bounds and the stage's bounds disagree by a pixel.
 *
 * When no app has been chosen it shows the app grid instead. That is safe precisely
 * because there is no window in the way: this state only occurs before the first app
 * has ever been picked, after which [com.dwm.cockpit.Prefs.stageApp] always resolves.
 *
 * @param bounds fires with the stage's rect in **window** coordinates, which is the
 *   coordinate space `ActivityOptions.launchBounds` wants. Reported on every layout
 *   pass; the activity dedupes before relaunching, because relaunching is expensive.
 */
@Composable
fun AppStage(
    occupied: Boolean,
    apps: List<HomeApp>,
    onLaunch: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    onBounds: (Rect) -> Unit,
    onWallpaper: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = Dwm.colors
    Box(
        modifier
            .then(
                // An empty stage is a card so the grid has a surface to sit on. An
                // occupied one is invisible: the window supplies its own everything.
                if (occupied) Modifier
                else Modifier
                    .clip(DwmShapes.medium)
                    .background(if (onWallpaper) Color.Transparent else colors.surface)
                    .border(DwmStroke.hairline, colors.hairline, DwmShapes.medium)
            )
            .onGloballyPositioned { c ->
                val p = c.positionInWindow()
                onBounds(
                    Rect(
                        p.x.toInt(),
                        p.y.toInt(),
                        p.x.toInt() + c.size.width,
                        p.y.toInt() + c.size.height
                    )
                )
            }
    ) {
        if (!occupied) {
            AppGrid(
                apps = apps,
                onLaunch = onLaunch,
                onAppMenu = onAppMenu,
                onWallpaper = onWallpaper
            )
        }
    }
}
