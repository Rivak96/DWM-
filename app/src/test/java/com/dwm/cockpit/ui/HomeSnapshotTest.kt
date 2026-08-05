package com.dwm.cockpit.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import androidx.compose.runtime.Composable
import com.dwm.cockpit.Media
import com.dwm.cockpit.Panel
import org.junit.Rule
import org.junit.Test

/**
 * Renders the home screen to PNG on the JVM, with no deck involved.
 *
 * This exists because of a specific, repeated failure: every visual change in this
 * project has been judged from a photograph taken after a build, a sideload and a
 * walk out to the van, and the guesses in between were wrong often enough to cost
 * several releases. `@Preview` helped only if someone opened Android Studio.
 * Paparazzi puts the rendered screen in a file, which means it can be looked at
 * before it ships rather than after.
 *
 * Record with `gradlew recordPaparazziDebug`; the PNGs land in
 * `app/src/test/snapshots/images/`.
 */
class HomeSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DECK,
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false,
        // Without this Paparazzi scales every golden down to a 1000px long edge
        // (`ImageUtils.getThumbnailScale`), which is why the first four committed
        // PNGs were 1000x562 rather than the panel size. Judging type on a
        // resampled image is exactly the mistake this test exists to prevent.
        useDeviceResolution = true
    )

    /** The default cockpit: camera left, app drawer right, no CAN data. This is
     *  exactly what the deck shows the first morning, and it has to look finished. */
    @Test
    fun `cockpit default - camera and drawer, no CAN`() = snap {
        CockpitHome(
            panes = previewPanesDefault,
            splitFraction = 0.5f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal,
            drawnView = drawn
        )
    }

    /** Both panes holding a live app. The bodies render empty because the windows
     *  are separate tasks; the golden proves the chrome and the rects. */
    @Test
    fun `cockpit with two live app windows`() = snap {
        CockpitHome(
            panes = previewPanesApps,
            splitFraction = 0.58f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleIdle,
            drawnView = drawn
        )
    }

    @Test
    fun `cockpit reversing`() = snap {
        CockpitHome(
            panes = previewPanesDefault,
            splitFraction = 0.5f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = false,
            actions = previewActions,
            vehicle = previewVehicle,
            drawnView = drawn
        )
    }

    @Test
    fun `cockpit with music playing`() = snap {
        CockpitHome(
            panes = previewPanesDefault,
            splitFraction = 0.5f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = false,
            actions = previewActions,
            vehicle = previewVehicleIdle,
            media = Media.State.Playing(
                pkg = "com.spotify.music",
                title = "Everything In Its Right Place",
                artist = "Radiohead",
                art = null,
                playing = true
            ),
            drawnView = drawn
        )
    }

    @Test
    fun `cockpit at night`() = snap(night = true) {
        CockpitHome(
            panes = previewPanesDefault,
            splitFraction = 0.5f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal,
            drawnView = drawn
        )
    }

    /** One pane, full width — the other supported layout. */
    @Test
    fun `cockpit single pane`() = snap {
        CockpitHome(
            panes = listOf(previewPanesDefault[0]),
            splitFraction = 1f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleIdle,
            drawnView = drawn
        )
    }

    private fun snap(night: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.snapshot { DwmPreviewTheme(night = night) { content() } }
    }

    /** Golden-only stand-ins, so a drawn pane shows as occupied rather than empty. */
    private val drawn: (Panel) -> android.view.View? = { p ->
        previewDrawnView(paparazzi.context, p)
    }

    private companion object {


        /**
         * The real panel: 1920x1200px at 192dpi, which is a 1600x1000dp canvas.
         *
         * This was 1280x720 at 160dpi — a placeholder chosen so dp mapped 1:1 to px,
         * and 320dp narrower than the truth in logical terms. Everything laid out
         * against it was the right shape at the wrong size.
         *
         * `Density.create(192)` rather than a constant: in the layoutlib Paparazzi
         * 1.3.5 pulls, `com.android.resources.Density` is a class, not an enum, and
         * `create()` returns a real instance for any dpi that has no named constant.
         * `xdpi`/`ydpi`/`size`/`ratio` are set explicitly because `PIXEL_5.copy`
         * otherwise leaves a phone's 442dpi and `ScreenSize.NORMAL` behind, which
         * would mis-resolve the moment a size-qualified resource appears.
         *
         * Note this renders at `Prefs.uiScale` = 1.0 by construction — Paparazzi
         * never runs `Scale.wrap`. That is now also the on-device default, so the
         * golden and the deck agree; they did not while the default was 0.8.
         */
        val DECK: DeviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920,
            screenHeight = 1200,
            xdpi = 192,
            ydpi = 192,
            density = Density.create(192),
            orientation = ScreenOrientation.LANDSCAPE,
            size = ScreenSize.XLARGE,
            ratio = ScreenRatio.NOTLONG,
            softButtons = false
        )
    }
}
