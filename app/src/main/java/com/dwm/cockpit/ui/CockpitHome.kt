package com.dwm.cockpit.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.dwm.cockpit.Apps
import com.dwm.cockpit.Media
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmIcons
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmRadius
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * The home screen.
 *
 * Everything here sits on [DwmGrid]: twelve columns across the content width, one
 * gutter, one margin, nothing between the lines. The version this replaces had no
 * grid and six competing ratio systems instead — two hardcoded column weights, a
 * three-branch `when` picking a third, a tile size clamped against a magic 168, an
 * icon at 42% of the smaller edge and a label at 11% of the height. That is why the
 * right-hand column died and the top row did not align with the row beneath it.
 *
 * Layout, at the panel's 1600x1000dp:
 *
 * ```
 *  +------------------------------------------------------+------+
 *  | top strip - clock, date            speed  volts  CAN |      |
 *  +-----------------------------------+------------------+ nav  |
 *  | CarPlay                    span 8 | proximity span 4 | rail |
 *  +-----------------------------------+------------------+      |
 *  | app tiles                                    span 12 |  96  |
 *  +-----------------------------------+------------------+  dp  |
 *  | media strip                span 8 | vitals    span 4 |      |
 *  +------------------------------------------------------+------+
 * ```
 *
 * The biggest thing on screen is CarPlay, because on a parked truck that is the most
 * useful thing on screen. It used to be an idle music widget saying "Nothing
 * playing".
 */

/**
 * A favourite.
 *
 * [glyph] is a DWM icon, or `null` for a monogram. There is deliberately **no field
 * for the app's own icon and no field for a tint** — the previous version carried
 * both, sampled the icon's average colour and painted it behind the tile, which is
 * what produced the brown and tan slabs. Removing the fields is what stops that being
 * reintroduced by accident.
 */
data class HomeApp(
    val pkg: String,
    val label: String,
    @DrawableRes val glyph: Int?
)

class HomeActions(
    val carplay: () -> Unit = {},
    val overlayMenu: () -> Unit = {},
    val bluetooth: () -> Unit = {},
    val wifi: () -> Unit = {},
    val apps: () -> Unit = {},
    val edit: () -> Unit = {},
    val settings: () -> Unit = {},
    val reload: () -> Unit = {},
    val pill: () -> Unit = {},
    val launch: (String) -> Unit = {},
    val appMenu: (String) -> Unit = {},
    val grantNotifications: () -> Unit = {}
)

/**
 * A region a drawn overlay is occupying, in fractional screen coordinates.
 *
 * An overlay panel is a separate `TYPE_APPLICATION_OVERLAY` window with
 * `FLAG_LAYOUT_NO_LIMITS`. It cannot see this composition and this composition cannot
 * clip it, which is why the camera used to land on the CarPlay card and crop it.
 * Passing the saved rects in — `OverlayPanelsService` already persists them, so this
 * costs no IPC and touches no window-manager code — lets the layout simply not draw
 * underneath them.
 */
data class ReservedRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@Composable
fun CockpitHome(
    favourites: List<HomeApp>,
    overlaysOn: Boolean,
    actions: HomeActions,
    vehicle: VehicleUi = rememberVehicleState(LocalContext.current),
    media: Media.State = Media.State.Idle,
    reserved: List<ReservedRegion> = emptyList()
) {
    val colors = Dwm.colors
    val head by vehicle.head
    val drive by vehicle.drive
    val body by vehicle.body
    val vitals by vehicle.vitals

    Row(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val content = maxWidth
            val tall = maxHeight

            // How far down the right-hand side an overlay reaches. Only the right is
            // considered because that is where a floating panel lands on this layout;
            // handling a left-hand rect that never occurs would be inventing a case.
            val rightBlocked = reserved
                .filter { it.right > 0.55f }
                .maxOfOrNull { it.bottom }
                ?.coerceIn(0f, 0.6f) ?: 0f

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(DwmGrid.margin)
            ) {
                TopStrip(
                    speed = drive.speedKmh,
                    volts = vitals.voltage,
                    turn = body.turnSignal,
                    reverse = body.reverse,
                    canLevel = head.canLevel,
                    demo = head.demo,
                    onCan = actions.settings
                )

                Spacer(Modifier.height(DwmGrid.gutter))

                // 5 / 4 / 3. CarPlay was span 8 and, at this canvas, that produced a
                // 1140dp-wide card holding a glyph and two words — the largest thing
                // on screen by area and nearly the emptiest. Five columns gives the
                // centred stack a portrait box that suits it, and hands the freed
                // width to a vitals column, which is information rather than air.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CarPlayHero(
                        onClick = actions.carplay,
                        modifier = Modifier
                            .width(DwmGrid.span(content, 5))
                            .fillMaxHeight()
                    )
                    Spacer(Modifier.width(DwmGrid.gutter))
                    ProximityCard(
                        body = body,
                        modifier = Modifier
                            .width(DwmGrid.span(content, 4))
                            .fillMaxHeight()
                            .padding(top = tall * rightBlocked)
                    )
                    Spacer(Modifier.width(DwmGrid.gutter))
                    VitalsCard(
                        vitals = vitals,
                        drive = drive,
                        modifier = Modifier
                            .width(DwmGrid.span(content, 3))
                            .fillMaxHeight()
                            .padding(top = tall * rightBlocked)
                    )
                }

                Spacer(Modifier.height(DwmGrid.gutter))

                FavouriteBand(apps = favourites, available = content, actions = actions)

                Spacer(Modifier.height(DwmGrid.gutter))

                MediaStrip(
                    state = media,
                    onOpen = actions.launch,
                    onGrantAccess = actions.grantNotifications,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DwmSize.mediaStrip)
                )
            }
        }

        NavRail(overlaysOn = overlaysOn, actions = actions)
    }
}

/* ------------------------------------------------------------------ nav rail */

/**
 * The signature.
 *
 * A full-height rail on the right edge, five icons, no labels, and one accent bar
 * that slides between positions. It is the only accent-coloured element on the screen
 * and the only thing that moves without being touched.
 *
 * **Right edge, not left.** Trinidad drives on the left, so the truck is
 * right-hand-drive and the right edge of a centre-stack panel is the near edge for
 * the driver. Every item is [DwmSize.touchTargetMoving] — 96dp — because these get
 * pressed while the vehicle is moving and the hand is unsteady.
 *
 * The DWM mark at the top is the home affordance and the bar's resting position, so
 * the bar always means "you are here" rather than sometimes meaning nothing. The
 * bottom bar this replaces had eight items with 10sp labels on a 1600dp screen;
 * Cockpit-edit, the pill and Reload were all configuration and have moved to
 * Settings.
 *
 * **Overlays-on is shown as contrast, not colour.** A second colour for a second
 * meaning is how the old screen ended up with a green highlight, a blue button and an
 * orange badge all competing to be the one that mattered.
 */
@Composable
private fun NavRail(overlaysOn: Boolean, actions: HomeActions) {
    val colors = Dwm.colors

    // 0 is the DWM mark. Home is the only Compose screen so far, so the bar rests
    // here; Settings joins the rail when it is rebuilt.
    val selected = 0

    val items = listOf(
        RailItem(R.drawable.ic_dwm_apps, "Apps", false, actions.apps),
        RailItem(R.drawable.ic_dwm_overlays, "Overlays", overlaysOn, actions.overlayMenu),
        RailItem(R.drawable.ic_dwm_bluetooth, "Bluetooth", false, actions.bluetooth),
        RailItem(R.drawable.ic_dwm_wifi, "Wi-Fi", false, actions.wifi),
        RailItem(R.drawable.ic_dwm_settings, "Settings", false, actions.settings)
    )

    // The six positions — the DWM mark plus five items — are centred as a block, so
    // the rail reads as a considered column rather than a list that ran out. First
    // render had them top-aligned and the bottom half of the rail was empty.
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

        Box(Modifier.align(Alignment.CenterStart).height(stack)) {
            Column {
                Box(
                    Modifier
                        .size(DwmSize.railWidth, DwmSize.railItem)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    DwmText("DWM", style = DwmType.overline, color = colors.text)
                }
                items.forEach { RailButton(it) }
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
private fun RailButton(item: RailItem) {
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
            tint = if (item.on) colors.text else colors.muted,
            modifier = Modifier.size(DwmSize.railIcon)
        )
    }
}

/* ----------------------------------------------------------------- top strip */

/**
 * The clock is deliberately quiet.
 *
 * The instruction was to make it either a real typographic element or genuinely
 * quiet, and not the awkward middle it was in. The signature is spent on the rail,
 * and the rule around a signature is that everything else stays still — so this is
 * the quiet option: mono, tabular, muted, top-left, the same size as a vehicle
 * reading rather than larger than one.
 */
@Composable
private fun TopStrip(
    speed: Int?,
    volts: Float?,
    turn: Int?,
    reverse: Boolean,
    canLevel: Int,
    demo: Boolean,
    onCan: () -> Unit
) {
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

    Row(
        Modifier
            .fillMaxWidth()
            .height(DwmSize.topStrip),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DwmText(time.format(now), style = DwmType.clock, color = colors.muted)
        Spacer(Modifier.width(DwmSpace.m))
        DwmText(date.format(now), style = DwmType.caption, color = colors.muted)

        Spacer(Modifier.weight(1f))

        if (turn != null && turn != 0) {
            Icon(
                painter = painterResource(R.drawable.ic_dwm_chevron),
                contentDescription = "Indicator",
                tint = colors.ok,
                modifier = Modifier
                    .size(DwmSize.icon)
                    .graphicsLayer { rotationZ = if (turn == 1) 180f else 0f }
            )
            Spacer(Modifier.width(DwmSpace.l))
        }

        if (reverse) {
            DwmText("R", style = DwmType.value, color = colors.warn)
            Spacer(Modifier.width(DwmSpace.l))
        }

        ReadingInline(speed?.toString(), "km/h")
        Spacer(Modifier.width(DwmSpace.xl))
        ReadingInline(volts?.let { String.format(Locale.US, "%.1f", it) }, "volts")
        Spacer(Modifier.width(DwmSpace.xl))

        CanDot(
            level = canLevel,
            demo = demo,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCan
            )
        )
    }
}

/* -------------------------------------------------------------------- carplay */

/**
 * The largest element on the screen, because it is the most useful one.
 *
 * No gradient. The old version filled itself with an accent-to-transparent wash and
 * an "OPEN" pill, which made it the second brightest thing on a screen whose accent
 * was supposed to mean navigation. A card, a glyph, a word.
 */
@Composable
private fun CarPlayHero(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Dwm.colors
    DwmCard(modifier = modifier, radius = DwmRadius.l, onClick = onClick) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dwm_carplay),
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(DwmSize.tileGlyph)
            )
            Spacer(Modifier.height(DwmSpace.l))
            DwmText("CarPlay", style = DwmType.hero, color = colors.text)
            Spacer(Modifier.height(DwmSpace.s))
            DwmText("Phone projection", style = DwmType.body, color = colors.muted)
        }
    }
}

/* ---------------------------------------------------------------------- tiles */

/**
 * The favourites band, full width, on the grid.
 *
 * Column count follows the app count up to six, then wraps. This head unit exposes
 * about three launchable apps and the previous grid was written assuming twelve,
 * which is how three icons ended up stranded in the middle of three full-height
 * slabs with a dead fourth column beside them. Tile height comes from tile width by
 * one fixed ratio, so three apps and twelve apps produce nearly the same band depth
 * and the rest of the layout does not jump when a favourite is pinned.
 */
@Composable
private fun FavouriteBand(
    apps: List<HomeApp>,
    available: Dp,
    actions: HomeActions
) {
    val shown = apps.take(Apps.FAV_SLOTS)
    if (shown.isEmpty()) return

    val cols = minOf(shown.size, MAX_TILE_COLUMNS)
    val usable = available - DwmGrid.margin * 2
    val tileW = (usable - DwmGrid.gutter * (cols - 1)) / cols

    Column(verticalArrangement = Arrangement.spacedBy(DwmGrid.gutter)) {
        shown.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(DwmGrid.gutter)) {
                row.forEach { app ->
                    FavouriteTile(
                        app = app,
                        actions = actions,
                        modifier = Modifier.size(tileW, DwmSize.tile)
                    )
                }
            }
        }
    }
}

/** Six across is where a 1600dp panel stops producing comfortable targets. */
private const val MAX_TILE_COLUMNS = 6

/**
 * A tile: theme surface, hairline, a DWM glyph or a monogram, a label.
 *
 * No colour of any kind. The background used to be the arithmetic mean of the app
 * icon's pixels, which is a mud generator by construction — the AUX icon's red,
 * green, yellow and blue RCA plugs average to brown, and the TPMS orange gradient
 * averages to tan.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FavouriteTile(
    app: HomeApp,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val colors = Dwm.colors
    Box(
        modifier
            .clip(DwmShapes.medium)
            .background(colors.surface)
            .border(DwmStroke.hairline, colors.hairline, DwmShapes.medium)
            .combinedClickable(
                onClick = { actions.launch(app.pkg) },
                onLongClick = { actions.appMenu(app.pkg) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(DwmSize.tileGlyph),
                contentAlignment = Alignment.Center
            ) {
                if (app.glyph != null) {
                    Icon(
                        painter = painterResource(app.glyph),
                        contentDescription = app.label,
                        tint = colors.text,
                        modifier = Modifier.size(DwmSize.tileGlyph)
                    )
                } else {
                    // The typographic fallback. Never the app's own icon.
                    DwmText(
                        DwmIcons.monogram(app.label),
                        style = DwmType.title,
                        color = colors.text
                    )
                }
            }
            Spacer(Modifier.height(DwmSpace.m))
            DwmText(
                app.label,
                style = DwmType.label,
                color = colors.muted,
                align = TextAlign.Center
            )
        }
    }
}

/* --------------------------------------------------------------------- vitals */

/**
 * The vitals column.
 *
 * Four readings that this vehicle's CAN service exposes, stacked. Every one of them
 * is an em dash today, and that is the point: the column has to look like a designed
 * instrument with nothing to say rather than like a feature that failed to load. It
 * will not move by a pixel when the bus starts talking, because the placeholder
 * occupies a real digit's width in a monospaced face.
 */
@Composable
private fun VitalsCard(
    vitals: VitalsState,
    drive: DriveState,
    modifier: Modifier = Modifier
) {
    val readings = listOf<Pair<String, Pair<String?, String>>>(
        "Battery" to (vitals.voltage?.let { String.format(Locale.US, "%.1f", it) } to "V"),
        "Coolant" to (vitals.coolant?.let { String.format(Locale.US, "%.0f", it) } to "°C"),
        "Fuel" to (vitals.fuel?.let { String.format(Locale.US, "%.0f", it) } to "%"),
        "Engine" to (drive.rpm?.toString() to "rpm")
    )

    DwmCard(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Show what fits, most useful first. The camera overlay can take half
            // this column's height, and a clipped reading looks broken where a
            // shorter list looks decided.
            val fits = (maxHeight / DwmSize.readingBlock).toInt().coerceIn(1, readings.size)
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                readings.take(fits).forEach { (label, v) ->
                    Reading(label, v.first, v.second)
                }
            }
        }
    }
}
