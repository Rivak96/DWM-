package com.dwm.cockpit.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType

/**
 * The nav rail — DWM's signature, and the one element on every screen.
 *
 * A full-height rail on the right edge, five icons, no labels, and a single accent
 * bar that slides between positions. It is the only accent-coloured element on any
 * screen and the only thing that moves without being touched.
 *
 * **Right edge, not left.** Trinidad drives on the left, so the truck is
 * right-hand-drive and the right edge of a centre-stack panel is the near edge for
 * the driver. Every item is [DwmSize.touchTargetMoving] — 96dp — because these get
 * pressed while the vehicle is moving and the hand is unsteady.
 *
 * The DWM mark at the top is [Rail.HOME]: both the home affordance and the bar's
 * resting position, so the bar always means "you are here" rather than sometimes
 * meaning nothing. It replaced a bottom bar of eight items with 10sp labels on a
 * 1600dp screen; Cockpit-edit, the pill and Reload were all configuration and moved
 * into Settings.
 *
 * **State is shown as contrast, never as a second colour.** A green pill under an
 * active item is how the old home screen ended up with a green highlight, a blue
 * button and an orange badge all competing to be the one that mattered.
 *
 * This lives in its own file because it is shared: the home screen and Settings draw
 * the same rail, differing only in [selected]. That sharing is the point — it is why
 * the two screens read as one machine, and reimplementing it per screen is exactly
 * how they would drift apart.
 */
object Rail {
    const val HOME = 0
    const val APPS = 1
    const val OVERLAYS = 2
    const val BLUETOOTH = 3
    const val WIFI = 4
    const val SETTINGS = 5
}

@Composable
fun NavRail(
    selected: Int,
    overlaysOn: Boolean,
    actions: HomeActions,
    onHome: () -> Unit = {}
) {
    val colors = Dwm.colors

    val items = listOf(
        RailItem(R.drawable.ic_dwm_apps, "Apps", false, actions.apps),
        RailItem(R.drawable.ic_dwm_overlays, "Overlays", overlaysOn, actions.overlayMenu),
        RailItem(R.drawable.ic_dwm_bluetooth, "Bluetooth", false, actions.bluetooth),
        RailItem(R.drawable.ic_dwm_wifi, "Wi-Fi", false, actions.wifi),
        RailItem(R.drawable.ic_dwm_settings, "Settings", false, actions.settings)
    )

    // The six positions are centred as a block, so the rail reads as a considered
    // column rather than a list that ran out. First render had them top-aligned with
    // the bottom half of the rail empty.
    val stack = DwmSize.railItem * (items.size + 1)

    val barOffset by animateDpAsState(
        targetValue = DwmSize.railItem * selected +
            (DwmSize.railItem - DwmSize.railBarLength) / 2,
        animationSpec = DwmMotion.baseDp,
        label = "railBar"
    )

    // Raised, not surface. The rail is chrome rather than content and has to read as
    // a distinct layer at a glance; against a near-black field the base surface step
    // was too quiet to separate it from the canvas at all.
    Box(
        Modifier
            .width(DwmSize.railWidth)
            .fillMaxHeight()
            .background(colors.raised)
    ) {
        // Hairline on the inner edge; the outer edge is the panel bezel.
        Spacer(
            Modifier
                .width(DwmStroke.hairline)
                .fillMaxHeight()
                .background(colors.hairline)
        )

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .height(stack)
        ) {
            Column {
                Box(
                    Modifier
                        .size(DwmSize.railWidth, DwmSize.railItem)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onHome
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    DwmText(
                        "DWM",
                        style = DwmType.overline,
                        color = if (selected == Rail.HOME) colors.text else colors.muted
                    )
                }
                items.forEachIndexed { i, item ->
                    RailButton(item, active = selected == i + 1)
                }
            }

            // The signature: one bar, on the inner edge, beside where you are.
            Spacer(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(y = barOffset)
                    .size(DwmSize.railBarWidth, DwmSize.railBarLength)
                    .background(colors.accent)
            )
        }
    }
}

private class RailItem(
    @DrawableRes val icon: Int,
    val label: String,
    val on: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun RailButton(item: RailItem, active: Boolean) {
    val colors = Dwm.colors
    Box(
        Modifier
            .size(DwmSize.railWidth, DwmSize.railItem)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = item.onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = item.label,
            tint = if (active || item.on) colors.text else colors.muted,
            modifier = Modifier.size(DwmSize.railIcon)
        )
    }
}
