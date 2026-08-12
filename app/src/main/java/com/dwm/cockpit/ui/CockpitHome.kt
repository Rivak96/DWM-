package com.dwm.cockpit.ui

import android.graphics.Rect
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import com.dwm.cockpit.Panel
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmIcons
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType
import kotlin.math.roundToInt

/**
 * The cockpit.
 *
 * ```
 *  ┌───────────────────────────────────────┬─────────────────────────────┐
 *  │                                       │  TPMS · doors · radar       │
 *  │  THE BOX — the app grid, or one live  │  (held for the 360 feed)    │
 *  │  app, at the app's own aspect         │                             │
 *  │                                       │                             │
 *  ├───────────────────────────────────────┤                             │
 *  │  ♪  ▲  ⊕  ▣   favourites dock         │                             │
 *  ├───────────────────────────────────────┼─────────────────────────────┤
 *  │ speed gear coolant volts fuel rpm ··· │                             │
 *  ├───────────────────────────────────────┤  CAMERA  16:9               │
 *  │ 07:04  Home Apps Overlays … ▸ R ● CAN │                             │
 *  └───────────────────────────────────────┴─────────────────────────────┘
 * ```
 *
 * **Nothing spans the full width.** The readings strip and the nav bar are both only as
 * wide as the box, which is the single idea behind this layout: the right rail owns an
 * unbroken column from the top margin to the bottom one, so the camera reaches the floor
 * instead of stopping 200dp short of it. Both bands used to run edge to edge and carry
 * their last items under the rail — the one part of the panel with something better to do
 * with the space.
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
 * ### The clock strip, and why it is gone
 *
 * There was a 48dp full-width strip above the cards carrying a clock, a date and the CAN
 * dot, plus a 12dp spacer under it. Sixty of a thousand dp, spent on three small things
 * that needed none of the width — while the same bar along the bottom had a wide dead
 * margin at each end, because six items do not fill 1600dp. They swapped places. See
 * [SystemBar].
 *
 * ### The budget, at the deck's 1600×1000dp
 *
 * A 12dp frame top and bottom leaves **976dp** for the content row, and both columns get
 * all of it. The left one spends it on four stacked bands; the right one on two:
 *
 * | | left (1047dp) | right (517dp) |
 * |---|---|---|
 * | | box 589 (16:9) | 360 slot 673 |
 * | | dock 159 | |
 * | | readings 104 | camera 291 (16:9) |
 * | | nav bar 88 | |
 *
 * The camera's bottom edge and the nav bar's are the same line. **The camera is 291dp
 * because it is 16:9 against a 517dp column, and that is the only thing that sets it** —
 * it reaches the floor now, but the height it gained went to the 360 slot above it, since
 * a 16:9 card cannot grow downward without growing sideways. Making the *picture* bigger
 * means [DwmGrid.span] going to five columns, which costs the box ~130dp of width. That
 * trade has been offered; four is where it stands.
 *
 * For reference, before any of this: camera 380×214, readings and nav bar both full-bleed
 * at 1568dp, and a 16dp frame.
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
    val grantNotifications: () -> Unit = {},
    /** Long-press on the CAN dot. See `DumpFlow` for why it is reachable from home. */
    val sendDump: () -> Unit = {}
)

@OptIn(ExperimentalFoundationApi::class)
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
    drawnView: (Panel) -> View? = { null },
    /**
     * Label of the app hosted live in the box, or null for the app grid.
     *
     * The box is a *reservation* when this is set: the real app is a freeform window
     * floating above this screen, so what matters here is the rectangle, not the content.
     */
    stage: String? = null,
    /**
     * The shape the box asks for when it holds a live app, as width ÷ height.
     *
     * The app picks the shape and DWM works around it. See `Prefs.stageAspect` — nudgeable,
     * because what shape a given app wants on this ROM is not knowable from here.
     */
    stageAspect: Float = 16f / 9f,
    /** Favourites for the strip under a live app. Empty when the box is the grid. */
    favourites: List<HomeApp> = emptyList(),
    /** The box's rect in screen pixels. Grown by the caption before launch — see
     *  [com.dwm.cockpit.StageHost.launchRectFor]. */
    onStageBounds: (Rect) -> Unit = {}
) {
    val colors = Dwm.colors
    val inspecting = LocalInspectionMode.current
    val rootView = LocalView.current.rootView
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                  // The left column. With a live app it is the box at the app's own shape
                  // plus a favourites strip; with the grid it is one card filling the height.
                  Column(Modifier.weight(1f).fillMaxHeight()) {
                    DwmCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                // A live app gets the shape it wants and DWM works around
                                // it, rather than the app being handed whatever rectangle
                                // was left over. CarPlay is laid out for a screen and does
                                // not adapt to an arbitrary box — squeezed and clipped is
                                // what the owner photographed. The grid, which is DWM's own
                                // and reflows happily, still takes the height.
                                if (stage != null) Modifier.aspectRatio(stageAspect)
                                else Modifier.weight(1f)
                            )
                            .then(
                                // Only a stage needs its rect measured, and only off the
                                // renderer: under LocalInspectionMode there is no window to
                                // take a screen position from, and a golden must never call
                                // back into the activity.
                                if (stage != null && !inspecting) Modifier.onGloballyPositioned {
                                    onStageBounds(it.screenRect(rootView))
                                } else Modifier
                            )
                    ) {
                        if (stage != null) {
                            // Deliberately near-empty. The live window covers this card
                            // entirely, so anything drawn here is invisible in normal use —
                            // it is what shows if the app is slow to appear or refuses to
                            // start, which is why it names the app rather than being blank.
                            // The title and the full-screen control live in StageChrome's
                            // mask, the one surface drawn *above* the window.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                DwmText(
                                    stage,
                                    style = DwmType.label,
                                    color = colors.muted
                                )
                            }
                        } else {
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
                    }

                    // The strip the aspect-locked box frees, and it is not decoration:
                    // with a live app in the box there is otherwise **no way to open
                    // anything** without going through the full-screen drawer. The
                    // favourites band was deleted from this screen once as redundant — it
                    // was, while the box itself was a grid of every app. It is not now.
                    if (stage != null) {
                        Spacer(Modifier.height(DwmGrid.gutter))
                        FavouriteStrip(
                            apps = favourites,
                            onLaunch = onOpenFullscreen,
                            onAppMenu = actions.appMenu,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }

                    // The readings belong to the *left* column, not to the screen.
                    //
                    // This ran the full width and carried its last two readings under the
                    // right-hand rail, which is the one part of the panel that has
                    // somewhere better to put them: cropping it here is what lets the rail
                    // run uninterrupted to the nav bar and the camera come down with it.
                    // The owner pointed at that dead right end directly.
                    //
                    // The cost is real and was taken deliberately — eight readings now
                    // divide ~1023dp instead of ~1536dp, so the strip is dense rather than
                    // airy. It still fits: every value is mono and tabular, so the width is
                    // known and does not move when the CAN bus starts talking. If a reading
                    // ever does clip, drop Boost and Outside — both are permanent em dashes
                    // on this van — rather than shrinking the type.
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

                    // The nav bar is cropped to the box as well, and for the same reason:
                    // it was the last full-width band holding the right rail off the floor.
                    // With both it and the readings inside this column, the rail runs the
                    // entire height of the panel and the camera reaches the bottom.
                    //
                    // It costs the bar width. Six items divide `maxWidth - barEdgeSlot * 2`,
                    // which was ~187dp each across 1568dp and is ~94dp each across 1047 —
                    // clear of [DwmSize.touchTarget] but just under the 96dp
                    // [DwmSize.touchTargetMoving] figure that sized this bar in the first
                    // place. Checked in the goldens rather than predicted. If it ever needs
                    // to give, the honest lever is [DwmSize.barEdgeSlot]: two 240dp slots
                    // are 46% of the cropped bar, and the clock does not need all of it.
                    //
                    // It is no longer full-bleed. The column's bottom padding lifts it
                    // [DwmGrid.gutter] off the screen edge, which is what puts its baseline
                    // on the camera's.
                    Spacer(Modifier.height(DwmGrid.gutter))
                    SystemBar(
                        selected = Bar.HOME,
                        overlaysOn = overlaysOn,
                        actions = actions,
                        onHome = {}
                    ) {
                        val turn = body.turnSignal
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

                        if (body.reverse) {
                            DwmText("R", style = DwmType.value, color = colors.warn)
                            Spacer(Modifier.width(DwmSpace.l))
                        }

                        // Long-press sends a diagnostics dump. Deliberately here and not
                        // only in Settings: the v0.36.0 white screen made Settings and the
                        // drawer untouchable, and that is exactly the state someone needs a
                        // dump from. Tap still opens Settings, so nothing is taken away.
                        CanDot(
                            level = head.canLevel,
                            demo = head.demo,
                            modifier = Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = actions.settings,
                                onLongClick = actions.sendDump
                            )
                        )
                    }
                  }

                    Spacer(Modifier.width(DwmGrid.gutter))

                    // Four columns of twelve, and it runs the full height now that the
                    // readings no longer cut across its foot.
                    //
                    // It was three. The camera is aspect-locked, so the only way it gets
                    // taller is by getting wider — and widening this rail narrows the app
                    // box, which is the trade that was offered and declined in v0.38.0.
                    // What changed is the price: the box is aspect-locked too, so a
                    // narrower box is also a *shorter* one, and the height that frees is
                    // exactly what the vehicle bar now needs on this side. The favourites
                    // dock comes out ahead rather than behind.
                    //
                    // The top slot is held for the 360 bird's-eye feed — see
                    // [VehicleDiagram]. It is the emptiest card on the screen and it is
                    // meant to be; the hardware is not fitted yet.
                    Column(
                        Modifier
                            .width(DwmGrid.span(content, 4))
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

        // Both bands that used to live here — the readings and the nav bar — are inside
        // the left column now. Nothing spans the full width any more, which is the whole
        // point: the right rail owns its strip of the panel from the top margin to the
        // bottom one, and the camera ends where the nav bar ends.
    }
    }
}

/**
 * The dock under a live app — glyphs only, no labels.
 *
 * Not [AppGrid]. The grid is labelled, scrolling and sized for a card that owns the height;
 * this owns whatever the aspect-locked box leaves over, which is about 90dp at 16:9. A
 * label needs ~30dp of that and the glyph needs 48dp, so labelling this row would clip
 * every tile — the same "a scaling rule quietly generating two different designs" that
 * [com.dwm.cockpit.ui.theme.DwmSize.tile] warns about.
 *
 * These are the owner's own favourites and a dock is not a place things are read, it is a
 * place they are hit. The count comes from the width so the row never overflows, and
 * nothing is drawn at all when there is nothing to draw — an empty card saying "No apps"
 * would be worse than the background.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteStrip(
    apps: List<HomeApp>,
    onLaunch: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    modifier: Modifier
) {
    if (apps.isEmpty()) return
    val colors = Dwm.colors

    DwmCard(modifier = modifier, padding = DwmSpace.s) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Below this there is no honest way to draw a 48dp glyph inside a target big
            // enough to hit while moving, so the strip stands down rather than clipping.
            if (maxHeight < DwmSize.touchTarget) return@BoxWithConstraints

            val slots = (maxWidth / DwmSize.touchTargetMoving).toInt().coerceAtLeast(1)
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                apps.take(slots).forEach { app ->
                    Box(
                        Modifier
                            .width(DwmSize.touchTargetMoving)
                            .fillMaxHeight()
                            .clip(DwmShapes.small)
                            .combinedClickable(
                                onClick = { onLaunch(app.pkg) },
                                onLongClick = { onAppMenu(app.pkg) }
                            ),
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
                            DwmText(
                                DwmIcons.monogram(app.label),
                                style = DwmType.title,
                                color = colors.text
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * This composable's bounds in **screen** pixels.
 *
 * `boundsInWindow()` is measured from the window's origin, but a freeform launch rect is
 * absolute, so the window's own position on screen has to be added. The offset is taken
 * from the **root** view rather than the `ComposeView`, so it is the window's origin and
 * not the composition's — using the ComposeView double-counts any inset between them.
 */
private fun LayoutCoordinates.screenRect(root: View): Rect {
    val b = boundsInWindow()
    val loc = IntArray(2)
    root.getLocationOnScreen(loc)
    return Rect(
        loc[0] + b.left.roundToInt(),
        loc[1] + b.top.roundToInt(),
        loc[0] + b.right.roundToInt(),
        loc[1] + b.bottom.roundToInt()
    )
}
