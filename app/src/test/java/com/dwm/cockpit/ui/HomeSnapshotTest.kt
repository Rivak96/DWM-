package com.dwm.cockpit.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.dwm.cockpit.Media
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
     * **The state this deck is actually in.** Three launchable apps and no CAN data
     * at all, which is what the truck shows every time it is switched on. If the
     * screen looks unfinished here, the design is wrong.
     */
    @Test
    fun `home as the deck really is - three apps, no CAN signal`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                CockpitHome(
                    favourites = previewDeckApps,
                    overlaysOn = true,
                    actions = previewActions,
                    vehicle = previewVehicleNoSignal
                )
            }
        }
    }

    /** The other end of the range. The grid used to assume this case and break on
     *  the one above. */
    @Test
    fun `home with a full twelve favourites`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                CockpitHome(
                    favourites = previewFavourites,
                    overlaysOn = true,
                    actions = previewActions,
                    vehicle = previewVehicleIdle
                )
            }
        }
    }

    @Test
    fun `home with music playing`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                CockpitHome(
                    favourites = previewDeckApps,
                    overlaysOn = false,
                    actions = previewActions,
                    vehicle = previewVehicleIdle,
                    media = Media.State.Playing(
                        pkg = "com.spotify.music",
                        title = "Everything In Its Right Place",
                        artist = "Radiohead",
                        art = null,
                        playing = true
                    )
                )
            }
        }
    }

    @Test
    fun `home reversing`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                CockpitHome(
                    favourites = previewDeckApps,
                    overlaysOn = false,
                    actions = previewActions,
                    vehicle = previewVehicle
                )
            }
        }
    }

    /** Night. Same layout, dimmed palette, nothing near white. */
    @Test
    fun `home at night`() {
        paparazzi.snapshot {
            DwmPreviewTheme(night = true) {
                CockpitHome(
                    favourites = previewDeckApps,
                    overlaysOn = true,
                    actions = previewActions,
                    vehicle = previewVehicleNoSignal
                )
            }
        }
    }

    /**
     * The camera overlay parked top-right, which is where it actually sits. The
     * reserved rect must push the proximity card down rather than letting the
     * overlay crop it.
     */
    @Test
    fun `home with the camera overlay reserving its corner`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                CockpitHome(
                    favourites = previewDeckApps,
                    overlaysOn = true,
                    actions = previewActions,
                    vehicle = previewVehicleNoSignal,
                    reserved = listOf(ReservedRegion(0.62f, 0.02f, 0.99f, 0.34f))
                )
            }
        }
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
