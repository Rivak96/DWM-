package com.dwm.cockpit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import com.dwm.cockpit.Panel
import com.dwm.cockpit.PanelType
import com.dwm.cockpit.ui.theme.DwmIcons

/**
 * Fixtures for previews and snapshot tests.
 *
 * These live in `src/debug` rather than `src/main` because shipping
 * `ui-tooling-preview` in release cost about 260 KB for annotations that do nothing
 * on a deck, in a project that has twice cut dependencies to stay near 6 MB.
 */

/**
 * The theme, without a `Context` or a vehicle.
 *
 * [LocalInspectionMode] is provided as `true` on purpose. Studio previews set it;
 * Paparazzi composes for real and does not. [MediaStrip] polls a system service, and
 * off-device that poll answers "no notification access" and paints the permission
 * prompt over whatever state the test passed in — which silently turned every
 * snapshot of a playing track into a snapshot of a permissions error. Any new
 * composable that reads a system service needs the same guard or its snapshot is a
 * lie.
 */
@Composable
fun DwmPreviewTheme(night: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        DwmThemeFrom(night = night, content = content)
    }
}

/* ------------------------------------------------------------------- apps */

/**
 * What this head unit actually exposes. Three apps, and two of them are vendor
 * builds whose package names are guesses — which is the case [DwmIcons.forApp] is
 * built to survive, since it matches on keywords across the package and the label
 * rather than on an exact identifier.
 */
val previewDeckApps: List<HomeApp> = listOf(
    app("com.deck.aux", "AUX"),
    app("com.deck.bluetooth", "Bluetooth"),
    app("com.deck.tpms", "TPMS")
)

/** The other end of the range: a full favourites bar, including two apps with no
 *  custom glyph so the monogram fallback is visible in a golden rather than only in
 *  theory. */
val previewFavourites: List<HomeApp> = listOf(
    app("com.spotify.music", "Spotify"),
    app("com.google.android.apps.maps", "Maps"),
    app("com.waze", "Waze"),
    app("com.android.chrome", "Chrome"),
    app("com.deck.aux", "AUX"),
    app("com.google.android.youtube", "YouTube"),
    app("com.deck.tpms", "TPMS"),
    app("com.deck.bluetooth", "Bluetooth"),
    app("com.android.gallery3d", "Gallery"),
    app("org.prowl.torque", "Torque"),
    app("com.acme.dashcam", "Dashcam"),
    app("com.acme.notes", "Trip Notes")
)

private fun app(pkg: String, label: String) =
    HomeApp(pkg, label, DwmIcons.forApp(pkg, label))

/* ---------------------------------------------------------------- vehicle */

/**
 * **The truck as it is today: no CAN data at all.**
 *
 * This is the fixture that matters most and the one the primary golden uses. There
 * is no signal on this vehicle yet — the CAN service is bound but silent — so every
 * reading is `null` and the radar list is empty rather than sixteen zeros. Sixteen
 * zeros would be a live sensor ring reporting all-clear, which is a different thing
 * and must not be what a disconnected bus looks like.
 *
 * If the screen looks unfinished in this state, the design is wrong, because this is
 * the state the deck is in every time it is switched on.
 */
val previewVehicleNoSignal: VehicleUi = VehicleUi(
    head = mutableStateOf(HeadState(canLevel = 1)),
    drive = mutableStateOf(DriveState()),
    body = mutableStateOf(BodyState()),
    vitals = mutableStateOf(VitalsState())
)

/** CAN alive, vehicle stationary, sensors reporting all clear. */
val previewVehicleIdle: VehicleUi = VehicleUi(
    head = mutableStateOf(HeadState(canLevel = 2, updates = 400)),
    drive = mutableStateOf(DriveState(speedKmh = 0)),
    body = mutableStateOf(BodyState(radar = List(16) { 0 })),
    vitals = mutableStateOf(VitalsState(voltage = 12.4f, coolant = 82f, track = 240))
)

/** Reversing at walking pace with two rear sensors picking something up. */
val previewVehicle: VehicleUi = VehicleUi(
    head = mutableStateOf(HeadState(canLevel = 2, updates = 812)),
    drive = mutableStateOf(DriveState(speedKmh = 4, gear = 2, headlight = true)),
    body = mutableStateOf(
        BodyState(
            headlight = true,
            turnSignal = 1,
            reverse = true,
            track = 300,
            radar = listOf(0, 0, 0, 0, 0, 0, 9, 5, 7, 3, 2, 8, 0, 0, 0, 0)
        )
    ),
    vitals = mutableStateOf(VitalsState(voltage = 12.7f, coolant = 91f, track = 300))
)

val previewActions = HomeActions()

/**
 * Settings as this deck actually reports it: nothing paired, nothing scanned, and a
 * diagnostics block that is real output from the van rather than lorem ipsum. The
 * empty states are the ones worth rendering — they are what the screen shows on a
 * truck that has never had an OBD dongle plugged into it.
 */
val previewSettings = SettingsUi(
    themeMode = 0,
    uiScale = 1.0f,
    fontScale = 1.0f,
    wallpaper = "None",
    wallpaperDim = 0.72f,
    mode = 1,
    modeHint = "Solo + overlays — a fullscreen base app opens on start and every " +
        "gauge, camera and web panel floats on top of it.",
    autoLoad = true,
    carplay = "CarPlay app: not set",
    favGrid = true,
    captionComp = 32,
    pillRunning = false,
    panelsRunning = true,
    overlayEdit = false,
    muteOverlays = true,
    vehicleStrip = false,
    demoData = false,
    obd = "OBD dongle: not set",
    camFit = 0,
    camDayNight = 0,
    camTrim = "0",
    canStatus = "Bound to the vehicle service. No signals have arrived yet.",
    canScanLabel = "Scan vehicle",
    updateStatus = "Installed: v0.25.0\nRepo: Rivak96/DWM-",
    autoUpdate = true,
    diagnostics = """
        Device: SPRD s9863a1h10_Natv
        Android 12 / API 29
        Panel: 1920x1200px @ 192dpi
        Preview: spec:width=1600dp,height=1000dp,dpi=192
        Freeform feature: no
        enable_freeform_support: 0
        Overlay permission: granted
        Memory: 412 MB free of 1876 MB
        Vehicle: gear unknown, 0 km/h
        CAN: bound, 0 updates, 41 signals absent
    """.trimIndent(),
    about = "DWM Cockpit v0.25.0 — your driving window manager.\n" +
        "Panels: apps in freeform windows · AUX camera · web dashboards · " +
        "custom HTML · OBD-II gauges · GPS speed · clock · images."
)

/* -------------------------------------------------------------------- panes */

/**
 * The default cockpit: camera on the left, app drawer on the right.
 *
 * Both are drawn by DWM, so this is what the screen looks like the first time it
 * opens — before an app has been picked, a permission granted or a CAN bus wired up.
 * It is the state that has to look finished, because it is the state the deck is in
 * every single morning.
 */
val previewPanesDefault: List<PaneState> = listOf(
    PaneState(
        listOf(
            Panel(PanelType.CAMERA, 0f, 0f, 1f, 1f, label = "Camera"),
            Panel(PanelType.DRAWER, 0f, 0f, 1f, 1f, label = "Apps")
        ),
        index = 0
    ),
    PaneState(
        listOf(
            Panel(PanelType.DRAWER, 0f, 0f, 1f, 1f, label = "Apps"),
            Panel(PanelType.CLOCK, 0f, 0f, 1f, 1f, label = "Clock")
        ),
        index = 0
    )
)

/**
 * Both panes holding a live app.
 *
 * Renders as two headers over empty bodies, because the windows are separate tasks
 * the renderer cannot see. That is the point of the golden: it proves the chrome and
 * the rects the windows will be launched into.
 */
val previewPanesApps: List<PaneState> = listOf(
    PaneState(
        listOf(
            Panel(PanelType.APP, 0f, 0f, 1f, 1f, pkg = "com.google.android.apps.maps", label = "Maps"),
            Panel(PanelType.CAMERA, 0f, 0f, 1f, 1f, label = "Camera")
        ),
        index = 0
    ),
    PaneState(
        listOf(
            Panel(PanelType.APP, 0f, 0f, 1f, 1f, pkg = "com.spotify.music", label = "Spotify"),
            Panel(PanelType.DRAWER, 0f, 0f, 1f, 1f, label = "Apps")
        ),
        index = 0
    )
)

/** A door ajar, tyres reporting, reversing onto something. */
val previewBodyActive = BodyState(
    doorLR = true,
    reverse = true,
    track = 300,
    tyres = listOf(
        TyreState("LF", pressure = 36f),
        TyreState("RF", pressure = 35f),
        TyreState("LR", pressure = 28f, warn = 1),
        TyreState("RR", pressure = 36f)
    ),
    radar = listOf(0, 0, 0, 0, 0, 0, 9, 5, 7, 3, 2, 8, 0, 0, 0, 0)
)

/* ------------------------------------------------------------ drawn stand-ins */

/**
 * Stand-in Views for DWM-drawn pane sources, **for goldens only**.
 *
 * Off device there is no camera to open and no GPS to read, so `buildPanelView`
 * returns nothing and every drawn pane renders as a label. That makes the primary
 * golden — the one meant to show what the deck looks like every morning — a picture
 * of two empty boxes, which is exactly the failure this rebuild exists to fix.
 *
 * These draw an unmistakably synthetic placeholder: a flat fill and the source name.
 * It is not pretending to be a camera feed. It exists so the golden shows the *shape*
 * of the screen with its panes occupied, and the deck shows what is actually in them.
 */
fun previewDrawnView(context: android.content.Context, p: Panel): android.view.View? =
    when (p.type) {
        PanelType.APP, PanelType.DRAWER -> null
        else -> android.widget.TextView(context).apply {
            text = p.displayLabel().uppercase()
            textSize = 13f
            setTextColor(0xFF7B8695.toInt())
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF1C2027.toInt())
            letterSpacing = 0.12f
        }
    }
