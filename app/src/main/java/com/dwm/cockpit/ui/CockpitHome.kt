package com.dwm.cockpit.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType
import com.dwm.cockpit.ui.theme.StatusCaution
import com.dwm.cockpit.ui.theme.StatusOk
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One favourite app. [tint] is the icon's dominant colour, sampled off the main
 * thread when the list is built (`HomeActivity.loadApps`) and used to give each
 * tile its own identity — a grid of grey squares is a file manager, not a
 * launcher.
 */
data class HomeApp(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap?,
    val tint: Color = Color(0xFF4C8DFF)
)

class HomeActions(
    val carplay: () -> Unit,
    val overlayMenu: () -> Unit,
    val bluetooth: () -> Unit,
    val wifi: () -> Unit,
    val apps: () -> Unit,
    val edit: () -> Unit,
    val settings: () -> Unit,
    val reload: () -> Unit,
    val pill: () -> Unit,
    val launch: (String) -> Unit,
    val appMenu: (String) -> Unit,
    val grantNotifications: () -> Unit
)

/**
 * Home.
 *
 * Three bands: a status strip across the top, the working area, and the
 * launcher's own controls along the bottom.
 *
 * **There is no pager.** An earlier version gave each favourite its own full
 * screen page, which photographs well and is hostile in a moving vehicle —
 * reaching the fifth app meant five swipes, each one a glance away from the road.
 * Every app is now on screen at once. That is also why the dot indicator is gone:
 * nothing is hidden, so there is no position to indicate.
 *
 * The left column carries music while driving and hands over to the parking
 * sensors the moment reverse engages, then hands back. It is the same space doing
 * the only two jobs this deck is ever asked for while stationary or moving slowly,
 * and it means neither one is paying rent on screen when it is useless.
 *
 * CarPlay gets its own tile rather than a slot in the bottom bar because that is
 * how this deck is actually driven: CarPlay fullscreen as the base app, with DWM's
 * overlays on top. Home is the place you pass through to get there.
 */
@Composable
fun CockpitHome(
    favourites: List<HomeApp>,
    overlaysOn: Boolean,
    actions: HomeActions,
    // Injected rather than fetched, so a @Preview can hand this screen a van that
    // is doing something. Defaulted so the HomeActivity call site stays simple.
    vehicle: VehicleUi = rememberVehicleState(LocalContext.current)
) {
    val colors = Dwm.colors

    Column(Modifier.fillMaxSize().background(colors.bg).padding(DwmSpace.m)) {

        TopStrip(vehicle, actions)
        Spacer(Modifier.height(DwmSpace.m))

        Row(Modifier.fillMaxWidth().weight(1f)) {

            // ------------------------------------------------ music / reversing
            Box(Modifier.fillMaxHeight().weight(0.38f)) {
                Crossfade(
                    targetState = vehicle.body.value.reverse,
                    animationSpec = DwmMotion.fade,
                    label = "reverseTakeover"
                ) { reversing ->
                    if (reversing) {
                        FlatCard(Modifier.fillMaxSize()) {
                            ParkingDisplay(vehicle.body.value, Modifier.fillMaxSize())
                        }
                    } else {
                        MediaPanel(
                            onOpen = actions.launch,
                            onGrantAccess = actions.grantNotifications,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.width(DwmSpace.m))

            // ------------------------------------------------------------ apps
            Column(Modifier.fillMaxHeight().weight(0.62f)) {
                CarPlayHero(actions.carplay, Modifier.fillMaxWidth().weight(0.28f))
                Spacer(Modifier.height(DwmSpace.m))
                FavouriteGrid(favourites, actions, Modifier.fillMaxWidth().weight(0.72f))
            }
        }

        Spacer(Modifier.height(DwmSpace.m))
        BottomBar(overlaysOn, actions)
    }
}

/* ---------------------------------------------------------------- top strip */

/**
 * Everything the van genuinely reports, in one thin band.
 *
 * Six live signals do not need a column — they needed a line. The previous layout
 * gave them 38% of a 13" screen and then had to invent things to fill it, which is
 * how a truck render nobody wanted ended up on the home screen.
 */
@Composable
private fun TopStrip(vehicle: VehicleUi, actions: HomeActions) {
    val colors = Dwm.colors
    val head = vehicle.head.value
    val drive = vehicle.drive.value
    val body = vehicle.body.value
    val vitals = vehicle.vitals.value

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = remember(now / 60000) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 3600000) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)) }

    // Only run a timer when something is actually blinking.
    val blinking = (body.turnSignal ?: 0) != 0
    var blinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(blinking) {
        if (!blinking) { blinkOn = true; return@LaunchedEffect }
        while (true) { blinkOn = !blinkOn; delay(420) }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

        // Set in the interface's own type rather than an imported logo. A pasted
        // bitmap mark is exactly the mistake the Ranger render made.
        androidx.compose.material3.Text(
            "RANGER",
            style = DwmType.overline,
            color = colors.textSecondary
        )

        Spacer(Modifier.width(DwmSpace.l))
        DwmText(time, size = DwmType.headline, color = colors.textPrimary, weight = FontWeight.Light, tabular = true)
        Spacer(Modifier.width(DwmSpace.s))
        DwmText(date, size = DwmType.caption, color = colors.textSecondary)

        Spacer(Modifier.weight(1f))

        val left = blinkOn && (body.turnSignal == 2 || body.turnSignal == 3)
        val right = blinkOn && (body.turnSignal == 1 || body.turnSignal == 3)
        DwmText("◀", size = DwmType.body, color = if (left) StatusOk else colors.cardBorder)
        Spacer(Modifier.width(DwmSpace.xs))
        DwmText("▶", size = DwmType.body, color = if (right) StatusOk else colors.cardBorder)

        Spacer(Modifier.width(DwmSpace.l))
        if (body.reverse) {
            Box(
                Modifier.clip(DwmShapes.small)
                    .background(StatusCaution.copy(alpha = 0.22f))
                    .padding(horizontal = DwmSpace.s, vertical = 2.dp)
            ) { DwmText("R", size = DwmType.label, color = StatusCaution, weight = FontWeight.Bold) }
            Spacer(Modifier.width(DwmSpace.m))
        }

        Reading(drive.speedKmh?.toString() ?: "—", "km/h")
        Spacer(Modifier.width(DwmSpace.l))
        Reading(fmt(vitals.voltage, "", 1), "volts")

        Spacer(Modifier.width(DwmSpace.l))
        // Tapping the CAN state lands on the diagnostics — the answer to "why is
        // that reading blank" is one tap away rather than a mystery.
        Box(
            Modifier.clip(DwmShapes.small).clickable { actions.settings() }
                .padding(horizontal = DwmSpace.s, vertical = DwmSpace.xs)
        ) { CanDot(head.canLevel, head.demo) }
    }
}

/** A number and its unit, baseline-aligned. */
@Composable
private fun Reading(value: String, unit: String) {
    val colors = Dwm.colors
    Row(verticalAlignment = Alignment.Bottom) {
        DwmText(value, size = DwmType.title, color = colors.textPrimary, weight = FontWeight.Medium, tabular = true)
        Spacer(Modifier.width(3.dp))
        DwmText(unit, size = DwmType.micro, color = colors.textTertiary, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/* ---------------------------------------------------------------- carplay */

@Composable
private fun CarPlayHero(onClick: () -> Unit, modifier: Modifier) {
    val colors = Dwm.colors
    Box(
        modifier
            .clip(DwmShapes.large)
            .background(
                Brush.horizontalGradient(
                    listOf(colors.accent.copy(alpha = 0.26f), colors.accent.copy(alpha = 0.08f))
                )
            )
            .border(DwmStroke.hairline, colors.accent.copy(alpha = 0.40f), DwmShapes.large)
            .clickable { onClick() }
            .padding(horizontal = DwmSpace.xl, vertical = DwmSpace.m),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painterResource(R.drawable.ic_car),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(DwmSpace.l))
            Column {
                DwmText("CarPlay", size = DwmType.headline, color = colors.textPrimary, weight = FontWeight.Light)
                DwmText("Phone projection", size = DwmType.label, color = colors.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(DwmShapes.pill)
                    .background(colors.accent.copy(alpha = 0.22f))
                    .border(DwmStroke.hairline, colors.accent.copy(alpha = 0.5f), DwmShapes.pill)
                    .padding(horizontal = DwmSpace.xl, vertical = DwmSpace.s)
            ) {
                DwmText("OPEN", size = DwmType.label, color = colors.accent, weight = FontWeight.Medium)
            }
        }
    }
}

/* ------------------------------------------------------------- favourites */

/**
 * Every favourite, on screen, always.
 *
 * Built from explicit weighted rows rather than a `LazyVerticalGrid` on purpose:
 * a lazy grid would happily scroll the last row off the bottom, and a row of apps
 * you have to scroll to reach is the same problem as a page you have to swipe to.
 * Twelve slots is `Apps.FAV_SLOTS`, which is exactly three rows of four.
 */
@Composable
private fun FavouriteGrid(apps: List<HomeApp>, actions: HomeActions, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
        for (row in apps.take(12).chunked(4)) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)
            ) {
                for (app in row) {
                    FavouriteTile(app, actions, Modifier.weight(1f).fillMaxHeight())
                }
                // Keep a short last row's tiles the same width as a full one's.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteTile(app: HomeApp, actions: HomeActions, modifier: Modifier) {
    val colors = Dwm.colors
    BoxWithConstraints(
        modifier
            .clip(DwmShapes.medium)
            .background(
                Brush.verticalGradient(
                    listOf(app.tint.copy(alpha = 0.22f), app.tint.copy(alpha = 0.06f))
                )
            )
            .border(DwmStroke.hairline, colors.cardBorder, DwmShapes.medium)
            .combinedClickable(
                onClick = { actions.launch(app.pkg) },
                onLongClick = { actions.appMenu(app.pkg) }
            ),
        contentAlignment = Alignment.Center
    ) {
        val icon = (minOf(maxWidth.value, maxHeight.value) * 0.42f).coerceIn(40f, 96f).dp
        val label = (maxHeight.value * 0.11f)
            .coerceIn(DwmType.micro.value, DwmType.label.value).sp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (app.icon != null) {
                Image(app.icon, contentDescription = app.label, modifier = Modifier.size(icon))
                Spacer(Modifier.height(DwmSpace.s))
            }
            DwmText(app.label, size = label, color = colors.textPrimary)
        }
    }
}

/* --------------------------------------------------------------- bottom bar */

/** The launcher's own controls. CarPlay used to sit here as one icon among eight;
 *  it has its own tile now, because it is the destination rather than a utility. */
@Composable
private fun BottomBar(overlaysOn: Boolean, actions: HomeActions) {
    val colors = Dwm.colors
    val items = listOf(
        BarItem(R.drawable.ic_apps, "Apps", null, actions.apps),
        BarItem(R.drawable.ic_edit, "Cockpit", null, actions.edit),
        BarItem(R.drawable.ic_layers, "Overlays", if (overlaysOn) StatusOk else null, actions.overlayMenu),
        BarItem(R.drawable.ic_pill, "Pill", null, actions.pill),
        BarItem(R.drawable.ic_reload, "Reload", null, actions.reload),
        BarItem(R.drawable.ic_bt, "Bluetooth", null, actions.bluetooth),
        BarItem(R.drawable.ic_wifi, "Wi-Fi", null, actions.wifi),
        BarItem(R.drawable.ic_settings, "Settings", null, actions.settings)
    )
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(DwmStroke.hairline).background(colors.hairline))
        Row(
            Modifier.fillMaxWidth().height(66.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (item in items) BarButton(item)
        }
    }
}

@Composable
private fun RowScope.BarButton(item: BarItem) {
    val colors = Dwm.colors
    val active = item.tint != null
    val tint = item.tint ?: colors.textSecondary
    // The active item carries a tinted pill rather than only a coloured glyph.
    // Colour alone was doing all the work before, and on this panel colour alone
    // is the first thing to wash out.
    val bg by animateFloatAsState(
        targetValue = if (active) 0.16f else 0f,
        animationSpec = DwmMotion.ui,
        label = "barActive"
    )
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .clip(DwmShapes.medium)
            .clickable { item.onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .clip(DwmShapes.pill)
                .background(tint.copy(alpha = bg))
                .padding(horizontal = DwmSpace.m, vertical = DwmSpace.xs),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(item.icon),
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        DwmText(
            item.label,
            size = DwmType.micro,
            color = if (active) tint else colors.textTertiary,
            weight = FontWeight.Medium
        )
    }
}

private class BarItem(
    @DrawableRes val icon: Int,
    val label: String,
    val tint: Color?,
    val onClick: () -> Unit
)
