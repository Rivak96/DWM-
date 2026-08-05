package com.dwm.cockpit.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType

/**
 * Settings, on the home screen's grid and type scale.
 *
 * ### What this replaces
 *
 * A 185dp sidebar of six low-contrast buttons, a runtime-drawn highlight rect that
 * matched nothing else on the screen, and a scroll pane where two thirds of the page
 * was empty. It was recognisably a settings dump rather than part of a vehicle
 * system.
 *
 * ### The recurring motif
 *
 * The section list is a **horizontal tab row with the accent bar underneath the
 * active tab** — the same bar that sits beside the active item on the nav rail,
 * rotated. That is deliberate: one moving accent bar, appearing once per screen in
 * one of two orientations, is what ties home and settings together as one machine.
 * The rail itself is here too, unchanged, with [Rail.SETTINGS] lit.
 *
 * Groups are laid in **two columns of six**. A single column of eight left the
 * bottom two thirds of the page empty on the short sections, which is the original
 * complaint about this screen; a single column of twelve fixes that but stretches
 * each row to 1440dp, putting a label and its control a hand's width apart. Six
 * columns is the measure at which a row still reads as one thing, and two of them
 * fill the panel.
 */

/** Everything Settings displays. The activity owns it and pushes changes in. */
class SettingsUi(
    val themeMode: Int,
    val uiScale: Float,
    val fontScale: Float,
    val mode: Int,
    val modeHint: String,
    val autoLoad: Boolean,
    val carplay: String,
    val favGrid: Boolean,
    val captionComp: Int,
    val pillRunning: Boolean,
    val panelsRunning: Boolean,
    val overlayEdit: Boolean,
    val muteOverlays: Boolean,
    val vehicleStrip: Boolean,
    val demoData: Boolean,
    val obd: String,
    val camFit: Int,
    val camDayNight: Int,
    val camTrim: String,
    val canStatus: String,
    val canScanLabel: String,
    val updateStatus: String,
    val autoUpdate: Boolean,
    val diagnostics: String,
    val about: String
)

/** Everything Settings can do. All of it stays in `SettingsActivity`. */
class SettingsActions(
    val setThemeMode: (Int) -> Unit = {},
    val setUiScale: (Float) -> Unit = {},
    val setFontScale: (Float) -> Unit = {},
    val setMode: (Int) -> Unit = {},
    val setAutoLoad: (Boolean) -> Unit = {},
    val pickCarplay: () -> Unit = {},
    val setFavGrid: (Boolean) -> Unit = {},
    val manageFavourites: () -> Unit = {},
    val editLayout: () -> Unit = {},
    val setComp: (Int) -> Unit = {},
    val grantOverlay: () -> Unit = {},
    val startPill: () -> Unit = {},
    val stopPill: () -> Unit = {},
    val panelsOn: () -> Unit = {},
    val panelsOff: () -> Unit = {},
    val setOverlayEdit: (Boolean) -> Unit = {},
    val raiseWindows: () -> Unit = {},
    val setMuteOverlays: (Boolean) -> Unit = {},
    val setVehicleStrip: (Boolean) -> Unit = {},
    val setDemoData: (Boolean) -> Unit = {},
    val pickObd: () -> Unit = {},
    val scanCameras: () -> Unit = {},
    val setCamFit: (Int) -> Unit = {},
    val setCamDayNight: (Int) -> Unit = {},
    val nudgeCamTrim: (Int) -> Unit = {},
    val canScan: () -> Unit = {},
    val canSerial: () -> Unit = {},
    val canApk: () -> Unit = {},
    val canDump: () -> Unit = {},
    val notifAccess: () -> Unit = {},
    val openBluetooth: () -> Unit = {},
    val openWifi: () -> Unit = {},
    val openDisplay: () -> Unit = {},
    val openAllSettings: () -> Unit = {},
    val defaultLauncher: () -> Unit = {},
    val checkUpdate: () -> Unit = {},
    val editRepo: () -> Unit = {},
    val setAutoUpdate: (Boolean) -> Unit = {},
    val close: () -> Unit = {}
)

private val SECTIONS = listOf("Display", "Cockpit", "Overlay", "Vehicle", "System", "About")

@Composable
fun SettingsScreen(
    ui: SettingsUi,
    actions: SettingsActions,
    home: HomeActions,
    overlaysOn: Boolean,
    /** Which tab opens. Only the snapshot tests pass this — it is how a golden can
     *  be recorded for a section other than the first. */
    startSection: Int = 0
) {
    val colors = Dwm.colors
    var section by rememberSaveable { mutableIntStateOf(startSection) }

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
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(DwmGrid.margin)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DwmSize.topStrip),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DwmText("Settings", style = DwmType.headline, color = colors.text)
                    Spacer(Modifier.weight(1f))
                    SettingsButton("Close", actions.close)
                }

                SectionTabs(section) { section = it }

                Spacer(Modifier.height(DwmGrid.gutter))

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    TwoColumns(
                        when (section) {
                            0 -> displaySection(ui, actions)
                            1 -> cockpitSection(ui, actions)
                            2 -> overlaySection(ui, actions)
                            3 -> vehicleSection(ui, actions)
                            4 -> systemSection(actions)
                            else -> aboutSection(ui, actions)
                        },
                        available = content
                    )
                    Spacer(Modifier.height(DwmGrid.margin))
                }
            }
        }

        NavRail(
            selected = Rail.SETTINGS,
            overlaysOn = overlaysOn,
            actions = home,
            onHome = actions.close
        )
    }
}

/**
 * The section tabs, with the accent bar beneath the active one.
 *
 * The same element as the rail's bar, turned ninety degrees. It is the only accented
 * thing on this screen, exactly as the rail's bar is the only accented thing on home.
 */
@Composable
private fun SectionTabs(selected: Int, onSelect: (Int) -> Unit) {
    val colors = Dwm.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val each = maxWidth / SECTIONS.size
        val barOffset by animateDpAsState(
            targetValue = each * selected + (each - DwmSize.railBarLength) / 2,
            animationSpec = DwmMotion.baseDp,
            label = "tabBar"
        )

        Column {
            Row(Modifier.fillMaxWidth()) {
                SECTIONS.forEachIndexed { i, title ->
                    Box(
                        Modifier
                            .width(each)
                            .height(DwmSize.touchTarget)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        DwmText(
                            title,
                            style = DwmType.label,
                            color = if (i == selected) colors.text else colors.muted
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(DwmSize.railBarWidth)) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(com.dwm.cockpit.ui.theme.DwmStroke.hairline)
                        .background(colors.hairline)
                )
                Spacer(
                    Modifier
                        .offset(x = barOffset)
                        .size(DwmSize.railBarLength, DwmSize.railBarWidth)
                        .background(colors.accent)
                )
            }
        }
    }
}
