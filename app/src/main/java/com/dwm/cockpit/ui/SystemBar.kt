package com.dwm.cockpit.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The system bar — DWM's navigation, along the bottom, on every screen.
 *
 * This replaces the right-edge nav rail. The rail was better ergonomics on paper: the
 * truck is right-hand drive, so the right edge is the near edge for the driver. It was
 * not what the driver wanted, and where the controls live is their call rather than a
 * geometry argument — v0.24 had them along the bottom and that is where they belong.
 *
 * ### What it costs, and what it buys
 *
 * A full-width bar takes about 90dp of height that the panes used to have, and gives
 * back 96dp of width they did not. Live content is generally wider than it is tall, so
 * that is close to an even trade, and it is the honest reason the panes did not get
 * dramatically smaller.
 *
 * ### The accent
 *
 * The travelling bar survives, turned ninety degrees: a short accent line above the
 * active item, sliding between positions on the one motion token. It is still the only
 * accent-coloured element on any screen. [Bar.HOME] is a real destination now, so it
 * always has somewhere to rest and always means "you are here".
 *
 * Items are large and unlabelled except for the active one. The bar it replaces had
 * eight items with 10sp labels on a 1600dp panel, which is the opposite failure.
 *
 * ### The two ends
 *
 * Six items across 1600dp left a large dead margin at each end, and the home screen was
 * simultaneously spending a 48dp full-width strip plus its spacer — about 60dp of a
 * 1000dp panel — on a clock, a date and a CAN dot. Those ends are now [DwmSize.barEdgeSlot]
 * slots: the clock on the left, on every screen, and a [status] slot on the right that
 * home fills with the vehicle's indicators and Settings leaves empty.
 *
 * Both ends exist even when only one is used, because that is what keeps the six items
 * centred on the panel rather than shunted right by the width of the clock.
 */
object Bar {
    const val HOME = 0
    const val APPS = 1
    const val OVERLAYS = 2
    const val BLUETOOTH = 3
    const val WIFI = 4
    const val SETTINGS = 5
}

@Composable
fun SystemBar(
    selected: Int,
    overlaysOn: Boolean,
    actions: HomeActions,
    onHome: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Screen-level status, right-aligned in the trailing slot. Home puts the turn signal,
     * reverse and the CAN dot here; Settings has no vehicle state and leaves it empty.
     */
    status: @Composable RowScope.() -> Unit = {}
) {
    val colors = Dwm.colors

    val items = listOf(
        BarItem(R.drawable.ic_dwm_layout, "Home", false, onHome),
        BarItem(R.drawable.ic_dwm_apps, "Apps", false, actions.apps),
        BarItem(R.drawable.ic_dwm_overlays, "Overlays", overlaysOn, actions.overlayMenu),
        BarItem(R.drawable.ic_dwm_bluetooth, "Bluetooth", false, actions.bluetooth),
        BarItem(R.drawable.ic_dwm_wifi, "Wi-Fi", false, actions.wifi),
        BarItem(R.drawable.ic_dwm_settings, "Settings", false, actions.settings)
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(DwmSize.systemBar)
    ) {
        val edge = DwmSize.barEdgeSlot
        val each = (maxWidth - edge * 2) / items.size
        val barOffset by animateDpAsState(
            // Past the leading slot, then across the items. Miss the `edge` term and the
            // accent sits one slot to the left of the item it is meant to be under.
            targetValue = edge + each * selected + (each - DwmSize.railBarLength) / 2,
            animationSpec = DwmMotion.baseDp,
            label = "systemBar"
        )

        Column {
            // The travelling accent, above the active item, over a hairline that
            // separates the bar from the content it belongs to.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DwmSize.railBarWidth)
            ) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(DwmStroke.hairline)
                        .background(colors.hairline)
                )
                Spacer(
                    Modifier
                        .offset(x = barOffset)
                        .size(DwmSize.railBarLength, DwmSize.railBarWidth)
                        .background(colors.accent)
                )
            }

            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                BarClock(
                    Modifier
                        .width(edge)
                        .fillMaxHeight()
                        .padding(start = DwmGrid.margin)
                )
                items.forEachIndexed { i, item ->
                    BarButton(item, active = i == selected, modifier = Modifier.width(each))
                }
                Row(
                    Modifier
                        .width(edge)
                        .fillMaxHeight()
                        .padding(end = DwmGrid.margin),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = status
                )
            }
        }
    }
}

/**
 * The clock, in the bar's leading slot.
 *
 * Deliberately quiet — mono, tabular, muted — because the signature of this screen is the
 * travelling accent, not the time. It reads the same on every screen, which is the point
 * of it living in the shared bar rather than in the home screen's own header.
 */
@Composable
private fun BarClock(modifier: Modifier) {
    val colors = Dwm.colors
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val date = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        DwmText(time.format(now), style = DwmType.clock, color = colors.muted)
        Spacer(Modifier.width(DwmSpace.m))
        DwmText(date.format(now), style = DwmType.caption, color = colors.muted)
    }
}

private class BarItem(
    @DrawableRes val icon: Int,
    val label: String,
    val on: Boolean,
    val onClick: () -> Unit
)

/**
 * One item.
 *
 * Only the active item shows its label. A row of six labels is the tiny-text problem
 * the old bar had; no labels at all leaves "Overlays" unlearnable. One label, always
 * the one you are on, teaches the set by use.
 */
@Composable
private fun BarButton(item: BarItem, active: Boolean, modifier: Modifier) {
    val colors = Dwm.colors
    Column(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = item.onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = item.label,
            // Contrast as state, never a second colour: the accent belongs to the
            // travelling bar, and an "on" toggle must not compete with it.
            tint = if (active || item.on) colors.text else colors.muted,
            modifier = Modifier.size(DwmSize.railIcon)
        )
        if (active) {
            Spacer(Modifier.height(DwmSpace.xs))
            DwmText(item.label, style = DwmType.overline, color = colors.text)
        }
    }
}
