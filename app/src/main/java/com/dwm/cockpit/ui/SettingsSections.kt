package com.dwm.cockpit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmGrid
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType

/**
 * The six Settings sections, as lists of groups.
 *
 * They are lists rather than composables so [TwoColumns] can place them. Each one is
 * a plain function returning `@Composable` lambdas: the section decides what is in it
 * and the screen decides where it goes, which is what lets the column count change
 * without editing six sections.
 */

/**
 * Groups laid in two columns, filled alternately.
 *
 * Alternating rather than filling the first column and then the second, so a section
 * with three groups puts two on the left and one on the right instead of stacking
 * everything down one side and leaving the other empty. Each column is six of the
 * twelve.
 */
@Composable
fun TwoColumns(groups: List<@Composable () -> Unit>, available: Dp) {
    val col = DwmGrid.span(available - DwmGrid.margin * 2, 6)
    Row(horizontalArrangement = Arrangement.spacedBy(DwmGrid.gutter)) {
        listOf(0, 1).forEach { side ->
            Column(
                Modifier.width(col),
                verticalArrangement = Arrangement.spacedBy(DwmSpace.xxl)
            ) {
                groups.filterIndexed { i, _ -> i % 2 == side }.forEach { it() }
            }
        }
    }
}

/* The value each option maps to, kept beside the labels so the two cannot drift. */
private val UI_SCALES = listOf(0.7f, 0.8f, 0.9f, 1.0f)
private val TEXT_SCALES = listOf(0.85f, 1.0f, 1.15f)

fun displaySection(ui: SettingsUi, a: SettingsActions): List<@Composable () -> Unit> = listOf(
    {
        SettingsGroup("Theme") {
            SettingsRow(
                "Appearance",
                "Auto follows the headlights, and falls back to the clock when the CAN bus is quiet."
            ) {
                SegmentedChoice(
                    listOf("Auto", "Day", "Night"), ui.themeMode, onSelect = a.setThemeMode
                )
            }
        }
    },
    {
        SettingsGroup("Sizing") {
            SettingsRow(
                "Interface scale",
                "The design is drawn at Stock. Smaller fits more on screen."
            ) {
                SegmentedChoice(
                    listOf("Tiny", "Compact", "Cosy", "Stock"),
                    UI_SCALES.indexOfFirst { it == ui.uiScale }.let { if (it < 0) 3 else it }
                ) { a.setUiScale(UI_SCALES[it]) }
            }
            SettingsRow("Text size") {
                SegmentedChoice(
                    listOf("Compact", "Normal", "Large"),
                    TEXT_SCALES.indexOfFirst { it == ui.fontScale }.let { if (it < 0) 1 else it }
                ) { a.setFontScale(TEXT_SCALES[it]) }
            }
        }
    },
    {
        SettingsGroup("Wallpaper") {
            SettingsRow("Image", ui.wallpaperName) {
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
                    SegmentedChoice(
                        listOf("Ranger", "None"),
                        if (ui.wallpaper == 1) 1 else 0
                    ) { a.setWallpaperMode(if (it == 1) 1 else 0) }
                    SettingsButton("Pick", a.pickWallpaper)
                }
            }
            SettingsRow(
                "Dim",
                "The bundled photo is dark enough to need very little. A brighter picture " +
                    "of your own may need more."
            ) {
                SegmentedChoice(
                    listOf("None", "Light", "Medium", "Heavy"),
                    WALL_DIMS.indexOfFirst { it == ui.wallpaperDim }.let { if (it < 0) 1 else it }
                ) { a.setWallpaperDim(WALL_DIMS[it]) }
            }
        }
    }
)

private val WALL_DIMS = listOf(0f, 0.30f, 0.55f, 0.75f)

fun cockpitSection(ui: SettingsUi, a: SettingsActions): List<@Composable () -> Unit> = listOf(
    {
        SettingsGroup("Cockpit mode") {
            SettingsRow("Mode", ui.modeHint) {
                SegmentedChoice(
                    listOf("Dashboard", "Solo + overlays"), ui.mode, onSelect = a.setMode
                )
            }
            SettingsRow("Open the saved layout at startup") {
                SettingsSwitch(ui.autoLoad, a.setAutoLoad)
            }
        }
    },
    {
        SettingsGroup("Apps") {
            SettingsRow("CarPlay app", ui.carplay) { SettingsButton("Pick", a.pickCarplay) }
            SettingsRow("Show the favourites grid") { SettingsSwitch(ui.favGrid, a.setFavGrid) }
            SettingsRow("Favourites") { SettingsButton("Manage", a.manageFavourites) }
        }
    },
    {
        SettingsGroup("App in the box") {
            SettingsRow("Live app", ui.stageApp) {
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
                    SettingsButton("Pick", a.pickStageApp)
                    SettingsButton("None", a.clearStageApp)
                }
            }
            SettingsRow("Status", ui.stageStatus) {}
            // The one number v0.29 got wrong. It hid the system caption by inflating the
            // launch rect by a hardcoded 32dp, which overhung the vehicle bar when the
            // guess ran long and cropped the app when it ran short. This walks the mask
            // against the real window instead of predicting it.
            SettingsRow(
                "Title bar cover",
                "Raise until the system's own title bar is hidden, lower until the app " +
                    "is not clipped."
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DwmSpace.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DwmText(ui.stageCaption, style = DwmType.body, color = Dwm.colors.muted)
                    SettingsButton("Less", { a.nudgeCaption(-2) })
                    SettingsButton("More", { a.nudgeCaption(2) })
                }
            }
        }
    },
    {
        SettingsGroup("Layout") {
            SettingsRow(
                "Cockpit layout",
                "Choose a template and fill its slots with panels."
            ) {
                SettingsButton("Edit", a.editLayout)
            }
        }
    }
)

fun overlaySection(ui: SettingsUi, a: SettingsActions): List<@Composable () -> Unit> = listOf(
    {
        SettingsGroup("Floating pill") {
            SettingsRow("Permission", "DWM needs the display-over-other-apps permission.") {
                SettingsButton("Grant", a.grantOverlay)
            }
            SettingsRow("Pill", if (ui.pillRunning) "Running" else "Stopped") {
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
                    SettingsButton("Start", a.startPill)
                    SettingsButton("Stop", a.stopPill)
                }
            }
        }
    },
    {
        SettingsGroup("Overlay panels") {
            SettingsRow("Panels over apps", if (ui.panelsRunning) "Showing" else "Hidden") {
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
                    SettingsButton("Show", a.panelsOn)
                    SettingsButton("Hide", a.panelsOff)
                }
            }
            SettingsRow("Edit panel layout", "Shows the move and resize grips on each panel.") {
                SettingsSwitch(ui.overlayEdit, a.setOverlayEdit)
            }
            SettingsRow("Mute web and video panels") {
                SettingsSwitch(ui.muteOverlays, a.setMuteOverlays)
            }
        }
    },
    {
        SettingsGroup("Extras") {
            SettingsRow("Vehicle strip", "A thin always-on speed and gear readout.") {
                SettingsSwitch(ui.vehicleStrip, a.setVehicleStrip)
            }
            SettingsRow(
                "Demo data",
                "Fake vehicle readings, for judging the layout with no CAN bus connected."
            ) {
                SettingsSwitch(ui.demoData, a.setDemoData)
            }
        }
    }
)

fun vehicleSection(ui: SettingsUi, a: SettingsActions): List<@Composable () -> Unit> = listOf(
    {
        SettingsGroup("OBD-II") {
            SettingsRow("Dongle", ui.obd) { SettingsButton("Pick", a.pickObd) }
        }
    },
    {
        SettingsGroup("Cameras") {
            SettingsRow(
                "Dashboard camera",
                ui.camPick + " — an overlay camera panel stands down while the dashboard " +
                    "shows the same camera. Give them different ids to run both at once."
            ) {
                SettingsButton("Choose", a.scanCameras)
            }
            SettingsRow("Picture fit", "Fill crops to the panel. Stretch distorts.") {
                SegmentedChoice(listOf("Fill", "Fit", "Stretch"), ui.camFit, onSelect = a.setCamFit)
            }
            // Four options make this the narrowest text column in the group, so the
            // hint has to be shorter than the others or it ellipsises — the golden
            // caught exactly that.
            SettingsRow(
                "Rotation",
                "Turns the dashboard feed. Overlay panels keep their own."
            ) {
                SegmentedChoice(
                    listOf("0°", "90°", "180°", "270°"), ui.camRotation, onSelect = a.setCamRotation
                )
            }
            SettingsRow("Day / night") {
                SegmentedChoice(
                    listOf("Auto", "Day", "Night"), ui.camDayNight, onSelect = a.setCamDayNight
                )
            }
            SettingsRow("Brightness trim", ui.camTrim) {
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
                    SettingsButton("Darker", { a.nudgeCamTrim(-1) })
                    SettingsButton("Brighter", { a.nudgeCamTrim(1) })
                }
            }
        }
    },
    {
        SettingsGroup("Vehicle data (CAN bus)") {
            SettingsRow("Signal scan", ui.canStatus) {
                SettingsButton(ui.canScanLabel, a.canScan, emphasis = true)
            }
            SettingsRow("Reports") {
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
                    SettingsButton("Serial", a.canSerial)
                    SettingsButton("Export APK", a.canApk)
                    SettingsButton("Dump getters", a.canDump)
                }
            }
        }
    },
    {
        SettingsGroup("TPMS") {
            SettingsRow(
                "Notification access",
                "Lets DWM mirror the alerts the TPMS app posts."
            ) {
                SettingsButton("Allow", a.notifAccess)
            }
        }
    }
)

fun systemSection(a: SettingsActions): List<@Composable () -> Unit> = listOf(
    {
        SettingsGroup("Android settings") {
            SettingsRow("Shortcuts") {
                Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
                    SettingsButton("Bluetooth", a.openBluetooth)
                    SettingsButton("Wi-Fi", a.openWifi)
                    SettingsButton("Display", a.openDisplay)
                }
            }
            SettingsRow("All settings") { SettingsButton("Open", a.openAllSettings) }
        }
    },
    {
        SettingsGroup("Launcher") {
            SettingsRow(
                "Default launcher",
                "Make DWM the screen that opens on start-up."
            ) {
                SettingsButton("Choose", a.defaultLauncher)
            }
        }
    }
)

fun aboutSection(ui: SettingsUi, a: SettingsActions): List<@Composable () -> Unit> = listOf(
    {
        SettingsGroup("Updates") {
            SettingsRow("Version", ui.updateStatus) {
                SettingsButton("Check", a.checkUpdate, emphasis = true)
            }
            SettingsRow("Check automatically") { SettingsSwitch(ui.autoUpdate, a.setAutoUpdate) }
            SettingsRow("Update source") { SettingsButton("Set repo", a.editRepo) }
        }
    },
    {
        // The best information on this screen. It used to render as 11sp monospace on
        // a barely-visible wash and read as leftover debug output; it is an
        // instrument panel now.
        Column(Modifier.fillMaxWidth()) {
            CardLabel("Diagnostics")
            Spacer(Modifier.height(DwmSpace.m))
            DataPanel(ui.diagnostics)
        }
    },
    {
        SettingsGroup("About") {
            DwmText(ui.about, style = DwmType.body, color = Dwm.colors.muted, maxLines = 8)
        }
    }
)
