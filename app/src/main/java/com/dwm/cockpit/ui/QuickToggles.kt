package com.dwm.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmIcons
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType

/**
 * Quick toggles — the apps reached most often, one press each.
 *
 * **Not a second navigation.** These launch apps; the nav rail moves around DWM. The
 * old home screen blurred the two and ended up with eight items along the bottom that
 * mixed "open Bluetooth settings" with "reload the cockpit", none of them large
 * enough to hit while moving.
 *
 * Each is [DwmSize.touchTargetMoving], drawn with the DWM icon family, falling back to
 * a typographic monogram. No app ever supplies its own artwork here.
 */
@Composable
fun QuickToggles(
    apps: List<HomeApp>,
    onLaunch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Dwm.colors
    DwmCard(modifier = modifier, padding = DwmSpace.l) {
        Row(
            Modifier.fillMaxSize(),
            // Left-aligned, not spread. This deck exposes three launchable apps and
            // SpaceEvenly floated them apart across the whole box; a group that keeps
            // its spacing at any count reads as deliberate at three and at six.
            horizontalArrangement = Arrangement.spacedBy(DwmSpace.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (apps.isEmpty()) {
                DwmText("No shortcuts", style = DwmType.label, color = colors.muted)
            }
            apps.take(MAX).forEach { app ->
                Toggle(app, onLaunch)
            }
        }
    }
}

/** Six is what fits at a moving-hand size across five of twelve columns. */
private const val MAX = 6

@Composable
private fun Toggle(app: HomeApp, onLaunch: (String) -> Unit) {
    val colors = Dwm.colors
    Column(
        Modifier
            .size(DwmSize.touchTargetMoving, DwmSize.touchTargetMoving)
            .clip(DwmShapes.small)
            .background(colors.raised)
            .border(DwmStroke.hairline, colors.hairline, DwmShapes.small)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onLaunch(app.pkg) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
        Spacer(Modifier.height(DwmSpace.xs))
        DwmText(app.label, style = DwmType.caption, color = colors.muted, align = TextAlign.Center)
    }
}
