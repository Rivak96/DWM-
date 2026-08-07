package com.dwm.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmIcons
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType

/**
 * The stage — the one app the home screen is built around.
 *
 * ### What this replaced, and why
 *
 * The stage used to host the app *live*, as a real task launched into the rect this
 * composable measured, via `ActivityOptions.launchBounds` plus a reflectively-set
 * freeform windowing mode. That is the only way an unprivileged launcher can put
 * another app inside a rectangle, and on the deck it failed in three ways that no
 * amount of tuning could reach, because all three belong to SystemUI rather than to
 * DWM: it drew a caption bar with drag and close handles inside the bounds, the app
 * could be dragged and resized out of position, and it sank behind the launcher on a
 * z-order DWM does not control. The rect itself was never right either — the caption
 * bar was hidden by inflating the bounds by a guessed 32dp, which overhung the vehicle
 * bar when the guess was too big and cropped the app when it was too small.
 *
 * Freeform *is* Android's floating-window mode, so "make it not float" and "keep the
 * live window" were the same request pulling opposite ways. The window lost.
 *
 * So the stage draws the app rather than containing it: a glyph, a name, and a button
 * that opens it the ordinary way with a plain `startActivity`. Nothing here can drift,
 * mis-measure or sink, and — for the first time — the whole home screen renders in a
 * Paparazzi golden, because there is no longer a foreign task in the middle of it that
 * no renderer can see.
 *
 * Getting back is the physical Home button: `HomeActivity` is `singleTask` and holds
 * `CATEGORY_HOME`, so it is always one press away from any fullscreen app.
 *
 * @param app the chosen app, or `null` before one has ever been picked.
 */
@Composable
fun AppStage(
    app: HomeApp?,
    onOpenFullscreen: () -> Unit,
    onPickApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Dwm.colors

    DwmCard(
        modifier = modifier,
        onClick = if (app != null) onOpenFullscreen else onPickApp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardLabel("Home app")
                Spacer(Modifier.weight(1f))
                // The fullscreen control, at the top, where it was asked for. It does
                // the same thing as the button below; a card this size needs a target
                // at the end of a glance as well as one at the end of a reach.
                if (app != null) {
                    IconAction(R.drawable.ic_dwm_expand, "Open fullscreen", onOpenFullscreen)
                }
                IconAction(R.drawable.ic_dwm_apps, "Choose an app", onPickApp)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (app == null) {
                    DwmText("No app chosen", style = DwmType.title, color = colors.muted)
                } else {
                    Box(
                        Modifier.size(DwmSize.stageGlyph),
                        contentAlignment = Alignment.Center
                    ) {
                        if (app.glyph != null) {
                            Icon(
                                painter = painterResource(app.glyph),
                                contentDescription = null,
                                tint = colors.text,
                                modifier = Modifier.size(DwmSize.stageGlyph)
                            )
                        } else {
                            DwmText(
                                DwmIcons.monogram(app.label),
                                style = DwmType.hero,
                                color = colors.text
                            )
                        }
                    }
                    Spacer(Modifier.height(DwmSpace.l))
                    DwmText(
                        app.label,
                        style = DwmType.title,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(DwmSpace.l))

            StageButton(
                text = if (app == null) "Choose an app" else "Open fullscreen",
                onClick = if (app == null) onPickApp else onOpenFullscreen
            )
        }
    }
}

/**
 * One of the two header controls.
 *
 * The tap target is a full [DwmSize.touchTarget] around a [DwmSize.railIcon] glyph —
 * these are pressed at a junction, and the glyph is not the thing being aimed at.
 */
@Composable
private fun IconAction(glyph: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(DwmSize.touchTarget)
            .clip(DwmShapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = label,
            tint = Dwm.colors.text,
            modifier = Modifier.size(DwmSize.railIcon)
        )
    }
}

/**
 * The card's primary action.
 *
 * Deliberately **not** accent-coloured. `DwmTokens` states the rule plainly — ACCENT is
 * the nav rail's travelling bar and nothing else — and a launcher whose biggest button
 * is also its only accent would break it on the first screen anyone sees. The second
 * elevation step plus a hairline is how everything else in DWM reads as raised.
 */
@Composable
private fun StageButton(text: String, onClick: () -> Unit) {
    val colors = Dwm.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(DwmSize.touchTarget)
            .clip(DwmShapes.small)
            .background(colors.raised)
            .border(DwmStroke.hairline, colors.hairline, DwmShapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DwmText(text.uppercase(), style = DwmType.label, color = colors.text)
    }
}
