package com.dwm.cockpit.ui

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.dwm.cockpit.Panel
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmShapes
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
 *  ┌─────────────────────────────────────────────┬───────────────────────┐
 *  │                                             │  TPMS · doors · radar │
 *  │   QUICK NAV — the app grid                  │                       │
 *  │   tap to open fullscreen                    ├───────────────────────┤
 *  │                                             │  CAMERA  16:9         │
 *  ├─────────────────────────────────────────────┴───────────────────────┤
 *  │  speed  gear  coolant  volts  fuel  rpm  boost  outside             │
 *  ├─────────────────────────────────────────────────────────────────────┤
 *  │  Home    Apps    Overlays    Bluetooth    Wi-Fi    Settings         │
 *  └─────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ### The app box, and the three things it has been
 *
 * Everything to the right of the box — the vehicle diagram, the camera, the readings
 * and the nav bar — has been stable for several releases. The box itself has not, and
 * its history is the whole design argument of this screen.
 *
 * 1. **Two live panes.** Rejected on the deck as "extremely buggy". Every reposition
 *    relaunched a real Android task, and two live apps plus a camera sat badly inside
 *    the ~600 MB this deck actually has free.
 * 2. **One live app**, launched as a freeform window sized to the box. Freeform is
 *    Android's floating-window mode, so it came with a system caption bar, a window the
 *    user could drag out of position and a z-order that let it sink behind the
 *    launcher — none of them reachable from DWM.
 * 3. **A card for one chosen app.** Solid, but static: a tile that opened something
 *    rather than anything you could use.
 *
 * It is now the app grid, which is the honest version of what the last two were
 * attempting — get into an app in one tap. See [AppGrid] for the stopgap this is, and
 * the vendor service that might yet put a live app back in it.
 *
 * ### The rule that used to shape this layout, and no longer does
 *
 * A live app was a separate task floating above this activity, which DWM could not draw
 * over and never received touches from. That forced everything DWM owns to live strictly
 * *beside* the box, and forced app-swapping through the fullscreen `AppDrawerActivity`.
 *
 * **DWM now owns every pixel of this screen.** The right-hand column, the vehicle bar
 * and the nav bar stay where they are because that is a good cockpit, not because a
 * foreign window forced them there.
 *
 * Vertical budget at the deck's 1000dp: 88 nav + 104 vehicle bar + spacers leaves 784
 * for the top box, less the 32dp margin and the 48dp top strip → ~692dp for the grid.
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
    /** Long-press on an app tile — pin/unpin, app info. */
    val appMenu: (String) -> Unit = {},
    val grantNotifications: () -> Unit = {}
)

@Composable
fun CockpitHome(
    /** The camera for the right-hand column. */
    cameraPanel: Panel,
    allApps: List<HomeApp>,
    overlaysOn: Boolean,
    actions: HomeActions,
    vehicle: VehicleUi = rememberVehicleState(LocalContext.current),
    boost: Float? = null,
    /** Optional wallpaper, already decoded by the activity. */
    wallpaper: android.graphics.Bitmap? = null,
    wallpaperDim: Float = 0.30f,
    /** Open an app the ordinary way — a plain fullscreen `startActivity`. */
    onOpenFullscreen: (pkg: String) -> Unit = {},
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
                    .padding(horizontal = DwmGrid.margin)
                    .padding(top = DwmGrid.margin, bottom = DwmGrid.gutter)
            ) {
                TopStrip(
                    turn = body.turnSignal,
                    reverse = body.reverse,
                    canLevel = head.canLevel,
                    demo = head.demo,
                    onCan = actions.settings
                )

                Spacer(Modifier.height(DwmSpace.m))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    DwmCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CardLabel("Apps")
                                Spacer(Modifier.weight(1f))
                                DwmText(
                                    "All apps",
                                    style = DwmType.label,
                                    color = colors.muted,
                                    modifier = Modifier
                                        .clip(DwmShapes.small)
                                        .clickable(onClick = actions.apps)
                                        .padding(
                                            horizontal = DwmSpace.m,
                                            vertical = DwmSpace.s
                                        )
                                )
                            }

                            Spacer(Modifier.height(DwmSpace.l))

                            AppGrid(
                                apps = allApps,
                                onLaunch = onOpenFullscreen,
                                onAppMenu = actions.appMenu,
                                onWallpaper = false
                            )
                        }
                    }

                    Spacer(Modifier.width(DwmGrid.gutter))

                    // Three columns of twelve — the width the vehicle diagram used to
                    // get along the bottom, now turned on its side. The diagram wants a
                    // portrait region and was being given 159dp of drawing width in a
                    // landscape card; here it gets roughly twice that.
                    Column(
                        Modifier
                            .width(DwmGrid.span(content, 3))
                            .fillMaxHeight()
                    ) {
                        VehicleDiagram(
                            body = body,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        Spacer(Modifier.height(DwmGrid.gutter))

                        // Sized by aspect, not by height. The camera absorbs its own
                        // shape and the diagram takes whatever is left, so adding the
                        // 360 feed later is one more box and no re-layout.
                        CameraBox(
                            panel = cameraPanel,
                            label = "Camera",
                            drawnView = drawnView,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }
                }
            }
        }

        // Outside the margin-padded column so it sits directly on the nav bar, which
        // is where the controls were asked for. It keeps its horizontal margin so it
        // still reads as a card; the nav bar below stays full-bleed.
        VehicleBar(
            drive = drive,
            vitals = vitals,
            ambient = head.ambient,
            boost = boost,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DwmGrid.margin)
                .height(DwmSize.vehicleBar)
        )

        Spacer(Modifier.height(DwmSpace.m))

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
