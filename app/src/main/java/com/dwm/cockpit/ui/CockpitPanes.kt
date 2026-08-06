package com.dwm.cockpit.ui

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.dwm.cockpit.Panel
import com.dwm.cockpit.PanelType
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmIcons
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType

/**
 * The cockpit panes — the part of the screen that holds live content.
 *
 * A pane is a slot, not an app. It carries an ordered list of sources and a swipe on
 * its header cycles them: a camera, the app drawer, a gauge, a web dashboard, a
 * clock, or a live third-party app. One pane full width, two side by side, or three.
 *
 * ### The two kinds of source, and why the header exists
 *
 * **DWM-drawn sources** render inside this activity, through the same
 * `buildPanelView` the dashboard has always used. Swapping between them is free.
 *
 * **A live app** is a separate freeform task floating *above* this activity. DWM
 * cannot draw over it and never receives its touches — which is exactly why each pane
 * has a slim header strip rather than being edge to edge. A horizontal swipe started
 * inside a map would be consumed by the map. The header is DWM's, always visible above
 * the window, and is where the swipe and the source dots live. That constraint shaped
 * the design; it is not decoration.
 *
 * ### Why this is not the pager that was deleted
 *
 * The old one-app-per-page pager was the *only* route to an app, so the fifth app
 * cost five swipes and five glances off the road. Here the swipe changes one region
 * and every destination stays directly reachable from the rail, the quick toggles and
 * the drawer. It is a shortcut, never the only path.
 */

/** One pane: its sources and which one is showing. */
data class PaneState(
    val sources: List<Panel>,
    val index: Int
) {
    val current: Panel? get() = sources.getOrNull(index)
}

@Composable
fun CockpitPanes(
    panes: List<PaneState>,
    splitFraction: Float,
    modifier: Modifier = Modifier,
    /** For a pane showing the app drawer. */
    apps: List<HomeApp> = emptyList(),
    /** Which pane launched, and what. The pane matters: tapping an app in a
     *  pane's drawer opens it *in that pane*, not fullscreen. */
    onLaunch: (pane: Int, pkg: String) -> Unit = { _, _ -> },
    /** Long-press: fullscreen, floating window, or pin. The escape hatch from
     *  "everything opens in a pane". */
    onAppMenu: (String) -> Unit = {},
    onSwipe: (pane: Int, delta: Int) -> Unit = { _, _ -> },
    onSplitChange: (Float) -> Unit = {},
    onPick: (pane: Int) -> Unit = {},
    /** Where each pane's body landed, in window pixels, so live app windows can be
     *  launched into it. Reported on every layout pass. */
    onPaneBounds: (List<Rect>) -> Unit = {},
    /** Builds the View for a DWM-drawn source. Supplied by the activity, which owns
     *  the gauges, WebViews and camera. Null for [PanelType.APP]. */
    drawnView: (Panel) -> View? = { null }
) {
    var drag by remember { mutableFloatStateOf(0f) }

    // The row's own width, so a drag can be converted into a fraction of it. Taking
    // the delta relative to the divider's 24dp instead would move the split by a
    // quarter of the screen per finger-width.
    var rowPx by remember { mutableFloatStateOf(0f) }

    // Reported bounds, one per pane, collected as each body is positioned.
    val bounds = remember(panes.size) { arrayOfNulls<Rect>(panes.size) }

    Row(modifier.onGloballyPositioned { rowPx = it.size.width.toFloat() }) {
        panes.forEachIndexed { i, pane ->
            val weight = when {
                panes.size == 1 -> 1f
                panes.size == 2 -> if (i == 0) splitFraction else 1f - splitFraction
                else -> 1f / panes.size
            }

            Pane(
                pane = pane,
                apps = apps,
                onLaunch = { pkg -> onLaunch(i, pkg) },
                onAppMenu = onAppMenu,
                onSwipe = { d -> onSwipe(i, d) },
                onPick = { onPick(i) },
                drawnView = drawnView,
                onBounds = { r ->
                    bounds[i] = r
                    if (bounds.all { it != null }) {
                        onPaneBounds(bounds.filterNotNull())
                    }
                },
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
            )

            if (i < panes.lastIndex) {
                Divider(
                    onDrag = { dx ->
                        if (rowPx > 0f) drag += dx / rowPx
                    },
                    onRelease = {
                        // Resize on release, never during the drag: `launchWindow`
                        // relaunches the task to move it, and doing that per frame
                        // would restart the app dozens of times across one gesture.
                        onSplitChange(splitFraction + drag)
                        drag = 0f
                    }
                )
            }
        }
    }
}

/**
 * The divider.
 *
 * A real gap, not a hairline. Two reasons: a live app window occupies its pane
 * exactly, so anything thinner than a finger cannot be grabbed — the touch has to
 * land on this activity, in the space *between* two windows — and a visible seam is
 * the correct read for a split view rather than a flaw.
 */
@Composable
private fun Divider(onDrag: (Float) -> Unit, onRelease: () -> Unit) {
    val colors = Dwm.colors
    Box(
        Modifier
            .width(DwmGrid.gutter)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                ) { _, dx -> onDrag(dx) }
            },
        contentAlignment = Alignment.Center
    ) {
        Spacer(
            Modifier
                .width(DwmStroke.hairline)
                .fillMaxHeight()
                .background(colors.hairline)
        )
        // The grab handle: a short bar, the same vocabulary as the rail's.
        Spacer(
            Modifier
                .size(DwmSize.railBarWidth, DwmSize.railBarLength)
                .clip(DwmShapes.small)
                .background(colors.muted)
        )
    }
}

@Composable
private fun Pane(
    pane: PaneState,
    apps: List<HomeApp>,
    onLaunch: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    onSwipe: (Int) -> Unit,
    onPick: () -> Unit,
    drawnView: (Panel) -> View?,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Dwm.colors
    val source = pane.current

    Column(
        modifier
            .clip(DwmShapes.medium)
            .background(colors.surface)
            .border(DwmStroke.hairline, colors.hairline, DwmShapes.medium)
    ) {
        PaneHeader(pane = pane, onSwipe = onSwipe, onPick = onPick)

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { c ->
                    val p = c.positionInWindow()
                    onBounds(
                        Rect(
                            p.x.toInt(),
                            p.y.toInt(),
                            p.x.toInt() + c.size.width,
                            p.y.toInt() + c.size.height
                        )
                    )
                }
        ) {
            when {
                source == null -> EmptyPane(onPick)

                // A live app owns this rect as its own window, floating above this
                // activity. Nothing is drawn underneath — anything here would be
                // hidden by the window, and drawing a placeholder that is only ever
                // seen while the app is loading is how you get a flash of the wrong
                // thing on every launch.
                source.type == PanelType.APP -> Box(Modifier.fillMaxSize())

                source.type == PanelType.DRAWER -> PaneDrawer(apps, onLaunch, onAppMenu)

                else -> key(source.type, source.pkg, source.url, source.metric) {
                    val view = drawnView(source)
                    if (view != null) {
                        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                    } else {
                        // A source *is* chosen here — its view just could not be
                        // built. Offering "Choose a source" would be a lie, and off
                        // device it is simply the renderer having no camera to open.
                        Unavailable(source.displayLabel())
                    }
                }
            }
        }
    }
}

/**
 * The pane header: what this pane is showing, where it sits in the list, and the
 * only surface a swipe can start from.
 */
@Composable
private fun PaneHeader(pane: PaneState, onSwipe: (Int) -> Unit, onPick: () -> Unit) {
    val colors = Dwm.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(DwmSize.paneHeader)
            .padding(horizontal = DwmSpace.l)
            .pointerInput(pane.sources.size) {
                detectHorizontalDragGestures { _, dx ->
                    if (dx < -SWIPE_PX) onSwipe(1) else if (dx > SWIPE_PX) onSwipe(-1)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val label = pane.current?.displayLabel() ?: "Empty"
        DwmText(label.uppercase(), style = DwmType.overline, color = colors.muted)

        Spacer(Modifier.weight(1f))

        // Position dots. Only drawn when there is more than one source — a single
        // dot would say "you can swipe" about a pane that cannot.
        if (pane.sources.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
                pane.sources.indices.forEach { i ->
                    Spacer(
                        Modifier
                            .size(DwmSpace.s)
                            .clip(CircleShape)
                            .background(if (i == pane.index) colors.text else colors.hairline)
                    )
                }
            }
            Spacer(Modifier.width(DwmSpace.l))
        }

        Box(
            Modifier
                .size(DwmSize.icon)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dwm_layout),
                contentDescription = "Change what this pane shows",
                tint = colors.muted,
                modifier = Modifier.size(DwmSize.icon)
            )
        }
    }
}

/** How far a drag must travel before it counts as a swipe. */
private const val SWIPE_PX = 24f

/**
 * A chosen source whose view could not be built.
 *
 * Names what it was trying to show rather than pretending nothing was picked. On the
 * deck this is a panel that failed to open; in a golden it is simply the renderer
 * having no camera hardware.
 */
@Composable
private fun Unavailable(label: String) {
    val colors = Dwm.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DwmText(label.uppercase(), style = DwmType.overline, color = colors.muted)
    }
}

/**
 * A pane with nothing assigned.
 *
 * Compact and centred, not a full-bleed empty card. A pane-sized empty box is exactly
 * what made the previous home screen read as unfinished.
 */
@Composable
private fun EmptyPane(onPick: () -> Unit) {
    val colors = Dwm.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(DwmShapes.small)
                .border(DwmStroke.hairline, colors.hairline, DwmShapes.small)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPick
                )
                .padding(horizontal = DwmSpace.xl, vertical = DwmSpace.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dwm_layout),
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(DwmSize.icon)
            )
            Spacer(Modifier.width(DwmSpace.m))
            DwmText("Choose a source", style = DwmType.label, color = colors.muted)
        }
    }
}

/**
 * The app drawer, drawn inside a pane.
 *
 * A pane can be swiped onto this the same way it is swiped onto the camera, so
 * reaching any app never means leaving the cockpit. Same tiles as the favourites
 * band: DWM glyph or a typographic monogram, theme surface, no colour sampled from
 * anything.
 */
@Composable
private fun PaneDrawer(
    apps: List<HomeApp>,
    onLaunch: (String) -> Unit,
    onAppMenu: (String) -> Unit
) {
    val colors = Dwm.colors
    if (apps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DwmText("No apps", style = DwmType.label, color = colors.muted)
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(DwmSpace.l)) {
        // Column count from the space available rather than a fixed number, so the
        // drawer works in a quarter-width pane and a full-width one.
        val cols = ((maxWidth + DwmSpace.m) / (DwmSize.drawerTile + DwmSpace.m))
            .toInt().coerceIn(2, 6)

        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DwmSpace.m)
        ) {
            apps.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.m)) {
                    row.forEach { app -> DrawerTile(app, onLaunch, onAppMenu, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun DrawerTile(
    app: HomeApp,
    onLaunch: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    modifier: Modifier
) {
    val colors = Dwm.colors
    Column(
        modifier
            .clip(DwmShapes.small)
            .combinedClickable(
                onClick = { onLaunch(app.pkg) },
                onLongClick = { onAppMenu(app.pkg) }
            )
            .padding(vertical = DwmSpace.m),
        horizontalAlignment = Alignment.CenterHorizontally
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
        Spacer(Modifier.height(DwmSpace.s))
        DwmText(
            app.label,
            style = DwmType.caption,
            color = colors.muted,
            align = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
