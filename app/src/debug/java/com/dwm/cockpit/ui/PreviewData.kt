package com.dwm.cockpit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import com.dwm.cockpit.R
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
