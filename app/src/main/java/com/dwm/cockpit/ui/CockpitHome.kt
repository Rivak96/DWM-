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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.dwm.cockpit.Panel
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmIcons
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
 *  │   THE STAGE — the one app, drawn as a card  │                       │
 *  │   with a fullscreen button                  ├───────────────────────┤
 *  │                                             │  CAMERA  16:9         │
 *  ├─────────────────────────────────────────────┴───────────────────────┤
 *  │  speed  gear  coolant  volts  fuel  rpm  boost  outside             │
 *  ├─────────────────────────────────────────────────────────────────────┤
 *  │  Home    Apps    Overlays    Bluetooth    Wi-Fi    Settings         │
 *  └─────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ### What this replaced, and why
 *
 * Two panes, a media strip and a favourites band — tested on the deck and rejected as
 * "extremely buggy". The verdict was about the architecture, not the finish. Every
 * pane reposition relaunched a real Android task, swiping a pane off an app parked its
 * window off-screen using bounds this ROM was never proven to honour, and two live
 * apps plus a camera sat badly inside the ~600 MB this deck actually has free.
 *
 * So there is one app now, and the space it gave back is filled with things DWM draws
 * itself. Drawn content costs no window, cannot sink behind anything, and cannot be
 * relaunched out from under you.
 *
 * ### The rule that used to shape this layout, and no longer does
 *
 * Until now the stage held a *live* app — a separate freeform task floating above this
 * activity, which DWM could not draw over and never received touches from. That forced
 * everything DWM owns to live strictly *beside* the stage, and forced app-swapping
 * through the fullscreen `AppDrawerActivity`, because a drawer drawn here would have
 * been hidden behind the very window it was meant to replace.
 *
 * That window is gone (see [AppStage] for the three SystemUI behaviours that killed
 * it), so the constraint is gone with it. **DWM now owns every pixel of this screen.**
 * The right-hand column, the vehicle bar and the nav bar stay where they are because
 * that is a good cockpit, not because a foreign window forced them there — and a
 * Compose drawer, a sheet or an overlay panel drawn on top of the stage is now a
 * perfectly ordinary thing to add.
 *
 * Vertical budget at the deck's 1000dp: 88 nav + 104 vehicle bar + spacers leaves 784
 * for the top box, less the 32dp margin and the 48dp top strip → ~692dp of stage.
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
    val grantNotifications: () -> Unit = {}
)

@Composable
fun CockpitHome(
    /** The app on the stage, or null before one has ever been chosen. */
    stageApp: String?,
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
    /** Open the stage app the ordinary way — a plain fullscreen `startActivity`. */
    onOpenFullscreen: (pkg: String) -> Unit = {},
    /** Choose which app the stage shows. Opens the drawer. */
    onPickApp: () -> Unit = {},
    drawnView: (Panel) -> View? = { null }
) {
    val colors = Dwm.colors
    val head by vehicle.head
    val drive by vehicle.drive
    val body by vehicle.body
    val vitals by vehicle.vitals

    // Resolved here rather than in [AppStage] so the stage stays a dumb renderer and
    // the goldens keep working: Paparazzi has no PackageManager, so anything that had
    // to ask one for a label would render empty on the JVM. The fallback also covers
    // the real first frame, before `loadApps` has come back off its thread.
    val stage = remember(stageApp, allApps) {
        stageApp?.let { pkg ->
            allApps.firstOrNull { it.pkg == pkg }
                ?: HomeApp(pkg, pkg.substringAfterLast('.'), DwmIcons.forApp(pkg, pkg))
        }
    }

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
                    AppStage(
                        app = stage,
                        onOpenFullscreen = { stage?.let { onOpenFullscreen(it.pkg) } },
                        onPickApp = onPickApp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

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
