package com.dwm.cockpit.ui

import androidx.annotation.DrawableRes
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
import com.dwm.cockpit.Apps
import com.dwm.cockpit.Media
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
import kotlin.math.ceil

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
    // Injected rather than fetched, so a @Preview or a snapshot test can hand this
    // screen a van and a track. Defaulted so the HomeActivity call site stays simple.
    vehicle: VehicleUi = rememberVehicleState(LocalContext.current),
    media: Media.State = Media.State.Idle
) {
    val colors = Dwm.colors

    Column(Modifier.fillMaxSize().background(colors.bg).padding(DwmSpace.m)) {

        TopStrip(vehicle, actions)
        Spacer(Modifier.height(DwmSpace.m))

        Row(Modifier.fillMaxWidth().weight(1f)) {

            // ------------------------------------------------ vehicle / music
            //
            // The proximity display is always up, not only in reverse. Swapping the
            // whole column on gear change meant that for the 99% of the time the van
            // is not reversing, this space was a music card with nothing playing —
            // a third of the screen holding two lines of grey text. Reverse now
            // promotes it rather than summoning it, and the front sensors do fire
            // when creeping forward anyway.
            val reversing = vehicle.body.value.reverse
            Column(Modifier.fillMaxHeight().weight(0.34f)) {
                FlatCard(Modifier.fillMaxWidth().weight(1f)) {
                    ParkingDisplay(vehicle.body.value, Modifier.fillMaxSize())
                }
                if (!reversing) {
                    Spacer(Modifier.height(DwmSpace.m))
                    MediaPanel(
                        onOpen = actions.launch,
                        onGrantAccess = actions.grantNotifications,
                        modifier = Modifier.fillMaxWidth().height(132.dp),
                        initialState = media
                    )
                }
            }

            Spacer(Modifier.width(DwmSpace.m))

            // ------------------------------------------------------------ apps
            Column(Modifier.fillMaxHeight().weight(0.66f)) {
                // Split by how many rows of apps there actually are. A fixed split
                // cannot serve both cases: bound the grid tightly enough for twelve
                // apps and the hero shrinks to a strip when there are three; leave
                // the hero greedy and twelve apps squeeze it to a sliver instead.
                val gridWeight = gridWeightFor(favourites.size)
                CarPlayHero(actions.carplay, Modifier.fillMaxWidth().weight(1f - gridWeight))
                Spacer(Modifier.height(DwmSpace.m))
                FavouriteGrid(favourites, actions, Modifier.fillMaxWidth().weight(gridWeight))
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
            .padding(DwmSpace.l),
        contentAlignment = Alignment.Center
    ) {
        // Centred and sized to the box rather than pinned to the left edge. As a
        // left-aligned row this was a line of small text against an acre of empty
        // blue — the tile was big, and nothing in it was.
        //
        // How much fits depends on how many apps are below: three favourites leave
        // this tile roomy, twelve leave it squat. Sized from the box rather than
        // fixed, because at a fixed size the OPEN pill was simply clipped away.
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val roomy = maxHeight > 190.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_car),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(if (roomy) 64.dp else 40.dp)
                )
                Spacer(Modifier.height(DwmSpace.s))
                DwmText(
                    "CarPlay",
                    size = if (roomy) DwmType.display else DwmType.headline,
                    color = colors.textPrimary,
                    weight = FontWeight.Light
                )
                DwmText("Phone projection", size = DwmType.label, color = colors.textSecondary)
                if (roomy) {
                    Spacer(Modifier.height(DwmSpace.m))
                    Box(
                        Modifier
                            .clip(DwmShapes.pill)
                            .background(colors.accent.copy(alpha = 0.22f))
                            .border(DwmStroke.hairline, colors.accent.copy(alpha = 0.5f), DwmShapes.pill)
                            .padding(horizontal = DwmSpace.xxl, vertical = DwmSpace.s)
                    ) {
                        DwmText("OPEN", size = DwmType.label, color = colors.accent, weight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------- favourites */

/** How much of the app column the grid should claim, by row count. */
private fun gridWeightFor(appCount: Int): Float {
    if (appCount <= 0) return 0.01f
    val cols = minOf(4, appCount)
    return when (ceil(appCount.coerceAtMost(Apps.FAV_SLOTS) / cols.toFloat()).toInt()) {
        1 -> 0.42f
        2 -> 0.58f
        else -> 0.68f
    }
}

/**
 * Every favourite, on screen, always — as square tiles that never stretch.
 *
 * The first version gave each row `weight(1f)` and hardcoded four columns. That is
 * fine with twelve apps and catastrophic with three, which is what this head unit
 * actually exposes: one row taking the entire column height turned three icons into
 * three full-height coloured slabs with a dead fourth slot beside them. It shipped,
 * and it looked exactly as bad as that description.
 *
 * So the tile size is computed to fit both axes and the column count follows the
 * number of apps. Three apps means three columns of squares, not a quarter-empty
 * grid stretched to fill space it does not need.
 */
@Composable
private fun FavouriteGrid(apps: List<HomeApp>, actions: HomeActions, modifier: Modifier) {
    if (apps.isEmpty()) return
    val shown = apps.take(Apps.FAV_SLOTS)
    val cols = minOf(4, shown.size)
    val rows = ceil(shown.size / cols.toFloat()).toInt()

    BoxWithConstraints(modifier) {
        val gap = DwmSpace.s
        val byWidth = (maxWidth - gap * (cols - 1)) / cols
        // Height matters as much as width. Sizing on width alone let twelve apps
        // demand three rows taller than the column, which squeezed the CarPlay
        // tile above down to a blue sliver — the grid quietly ate the hero.
        val byHeight = (maxHeight - gap * (rows - 1)) / rows
        // Cap it too: on a 13" panel an unbounded tile becomes a billboard for a
        // 48dp icon, which is the same failure in the other direction.
        val tile = minOf(byWidth.value, byHeight.value, 168f).dp

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in shown.chunked(cols)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (app in row) FavouriteTile(app, actions, Modifier.size(tile))
                }
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
