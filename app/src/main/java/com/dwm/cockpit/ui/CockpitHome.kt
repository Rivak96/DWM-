package com.dwm.cockpit.ui

import android.graphics.Rect
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.dwm.cockpit.Media
import com.dwm.cockpit.Panel
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The cockpit.
 *
 * ```
 *  ┌──────────────────────────────┬──────────────────────────────────────┐
 *  │  ‹ swipe ›                   ║  ‹ swipe ›                           │
 *  │   PANE 1 — live content      ║   PANE 2 — live content              │
 *  ├──────────────────────────────┴──────────────────────────────────────┤
 *  │  speed  gear  coolant  volts  fuel  rpm  boost  outside             │
 *  ├──────────────┬───────────────────┬──────────────────────────────────┤
 *  │ media 5      │ vehicle 3      │ shortcuts 4                         │
 *  ├──────────────┴───────────────────┴──────────────────────────────────┤
 *  │  Home    Apps    Overlays    Bluetooth    Wi-Fi    Settings         │
 *  └─────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * Navigation sits along the bottom rather than in a right-edge rail. The rail was
 * the better ergonomic argument in a right-hand-drive truck and it was not where the
 * driver wanted the controls — v0.24 had them along the bottom. It costs the panes
 * about 90dp of height and gives them back 96dp of width.
 *
 * ### What this replaced, and why
 *
 * The previous version was a page of buttons: a large card reading "CarPlay", an
 * empty proximity box, four em dashes and "Nothing playing". Every element was
 * *about* content rather than being content, and a screen of correctly-typeset
 * nothing reads as unfinished no matter how good the type is.
 *
 * The top two thirds are now live — a camera feed, the app drawer, a gauge, a web
 * dashboard, or a real third-party app window — and the bottom third is dense
 * instrumentation rather than four half-empty cards. The em-dash no-signal treatment
 * is still exactly right as a *component*; it was wrong as most of a screen.
 *
 * `DWM's own drawn panels render underneath the freeform app windows, which is what
 * lets a camera pane sit beside an app pane with no interaction between them.`
 */

/**
 * A favourite or shortcut.
 *
 * [glyph] is a DWM icon, or `null` for a monogram. There is deliberately **no field
 * for the app's own icon and no field for a tint** — the version before last carried
 * both, sampled the icon's average colour and painted it behind the tile, which is
 * what produced the brown and tan slabs.
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

@Composable
fun CockpitHome(
    panes: List<PaneState>,
    splitFraction: Float,
    favourites: List<HomeApp>,
    allApps: List<HomeApp>,
    overlaysOn: Boolean,
    actions: HomeActions,
    vehicle: VehicleUi = rememberVehicleState(LocalContext.current),
    media: Media.State = Media.State.Idle,
    boost: Float? = null,
    /** Optional wallpaper, already decoded by the activity. */
    wallpaper: android.graphics.Bitmap? = null,
    wallpaperDim: Float = 0.72f,
    /** Open an app inside a pane rather than fullscreen. */
    onLaunchInPane: (pane: Int, pkg: String) -> Unit = { _, _ -> },
    onSwipe: (pane: Int, delta: Int) -> Unit = { _, _ -> },
    onSplitChange: (Float) -> Unit = {},
    onPickSource: (pane: Int) -> Unit = {},
    onPaneBounds: (List<Rect>) -> Unit = {},
    drawnView: (Panel) -> View? = { null }
) {
    val colors = Dwm.colors
    val head by vehicle.head
    val drive by vehicle.drive
    val body by vehicle.body
    val vitals by vehicle.vitals

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // The wallpaper, if there is one, with a dim over it. Cards stay fully
        // opaque on top: every contrast figure in this design was measured against a
        // flat near-black field, and letting a photograph show through a card would
        // make all of those numbers fiction. What it changes is the margins, the
        // gutters and the space around the cards — which is a smaller share of this
        // screen than a wallpaper usually gets.
        if (wallpaper != null) {
            androidx.compose.foundation.Image(
                bitmap = wallpaper.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Spacer(
                Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = wallpaperDim))
            )
        }

    Column(
        Modifier
            .fillMaxSize()
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val content = maxWidth

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(DwmGrid.margin)
                    .padding(bottom = DwmSpace.s)
            ) {
                TopStrip(
                    turn = body.turnSignal,
                    reverse = body.reverse,
                    canLevel = head.canLevel,
                    demo = head.demo,
                    onCan = actions.settings
                )

                Spacer(Modifier.height(DwmSpace.m))

                CockpitPanes(
                    panes = panes,
                    splitFraction = splitFraction,
                    apps = allApps,
                    onLaunch = onLaunchInPane,
                    onAppMenu = actions.appMenu,
                    onSwipe = onSwipe,
                    onSplitChange = onSplitChange,
                    onPick = onPickSource,
                    onPaneBounds = onPaneBounds,
                    drawnView = drawnView,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(Modifier.height(DwmGrid.gutter))

                VehicleBar(
                    drive = drive,
                    vitals = vitals,
                    ambient = head.ambient,
                    boost = boost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DwmSize.vehicleBar)
                )

                Spacer(Modifier.height(DwmGrid.gutter))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DwmSize.bottomRow)
                ) {
                    MediaStrip(
                        state = media,
                        onOpen = actions.launch,
                        onGrantAccess = actions.grantNotifications,
                        modifier = Modifier
                            .width(DwmGrid.span(content, 5))
                            .fillMaxHeight()
                    )
                    Spacer(Modifier.width(DwmGrid.gutter))
                    VehicleDiagram(
                        body = body,
                        modifier = Modifier
                            .width(DwmGrid.span(content, 3))
                            .fillMaxHeight()
                    )
                    Spacer(Modifier.width(DwmGrid.gutter))
                    QuickToggles(
                        apps = favourites,
                        onLaunch = actions.launch,
                        onAppMenu = actions.appMenu,
                        modifier = Modifier
                            .width(DwmGrid.span(content, 4))
                            .fillMaxHeight()
                    )
                }
            }
        }

        SystemBar(
            selected = Bar.HOME,
            overlaysOn = overlaysOn,
            actions = actions,
            onHome = {}
        )
    }
    }
}

/**
 * The top strip — clock, date, and the few things that must be visible at all times.
 *
 * The speed and voltage readouts moved out of here and into the vehicle bar, where
 * they sit with the other six. Two numbers floating in a header while four cards
 * below held nothing was part of why the old screen had no centre of gravity.
 *
 * The clock is deliberately quiet: mono, tabular, muted. The signature is the rail.
 */
@Composable
private fun TopStrip(
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
            .height(DwmSize.paneHeader),
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
