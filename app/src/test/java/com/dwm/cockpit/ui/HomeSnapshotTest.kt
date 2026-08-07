package com.dwm.cockpit.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import androidx.compose.runtime.Composable
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

    /**
     * The state a fresh install lands in: the app grid, and no CAN data.
     *
     * No CAN is the deck's real state until the engine is running, and on this van it
     * is most readings' permanent state — only voltage, headlight, speed, steering and
     * turn signal ever answer. This is the golden that has to look finished.
     */
    @Test
    fun `cockpit no CAN`() = snap {
        CockpitHome(
            cameraPanel = previewCameraPanel,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal,
            drawnView = drawn
        )
    }

    /**
     * The same screen with the ignition on, so the readings that do work carry values.
     *
     * This golden used to be almost worthless: the app box rendered as a black
     * rectangle, because it held a freeform task no renderer could see, and all it
     * could prove was that the chrome sat outside the rect the window would land in.
     * The box is DWM-drawn now, so the whole home screen is in this PNG — the app grid
     * included, which is the only way to open anything from here.
     */
    @Test
    fun `cockpit with CAN data`() = snap {
        CockpitHome(
            cameraPanel = previewCameraPanel,
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
            cameraPanel = previewCameraPanel,
            allApps = previewFavourites,
            overlaysOn = false,
            actions = previewActions,
            vehicle = previewVehicle,
            drawnView = drawn
        )
    }

    @Test
    fun `cockpit at night`() = snap(night = true) {
        CockpitHome(
            cameraPanel = previewCameraPanel,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal,
            drawnView = drawn
        )
    }

    /**
     * A wallpaper behind the cockpit.
     *
     * **Deliberately a synthetic mid-grey, not the bundled photo.** Two reasons.
     * layoutlib cannot decode WebP, so `decodeResource` returns null here and a
     * golden using the real image would silently show no wallpaper at all — which is
     * exactly what the first version of this test did.
     *
     * More usefully, a flat mid-grey is a far harsher test than the shipped photo.
     * The bundled image measures 0.010 mean luminance; this is roughly fifteen times
     * brighter. If cards stay readable over this, they stay readable over anything a
     * driver is likely to pick. What the golden proves is the compositing: the image
     * draws, the dim applies, and cards stay fully opaque over it.
     */
    @Test
    fun `cockpit over a bright wallpaper`() = snap {
        CockpitHome(
            cameraPanel = previewCameraPanel,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal,
            wallpaper = android.graphics.Bitmap
                .createBitmap(64, 40, android.graphics.Bitmap.Config.ARGB_8888)
                .apply { eraseColor(0xFF6E7683.toInt()) },
            wallpaperDim = 0.30f,
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
