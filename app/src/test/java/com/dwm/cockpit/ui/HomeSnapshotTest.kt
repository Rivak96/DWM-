package com.dwm.cockpit.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
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
        showSystemUi = false
    )

    /**
     * The case that actually broke: this head unit exposes only three launchable
     * apps. The favourites grid was written assuming twelve, and with one row the
     * row's `weight(1f)` let it swallow the entire column — three icons stranded in
     * the middle of three full-height coloured slabs.
     */
    @Test
    fun `home with the three apps this deck really has`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                CockpitHome(
                    favourites = previewFavourites.take(3),
                    overlaysOn = true,
                    actions = previewActions,
                    vehicle = previewVehicleIdle
                )
            }
        }
    }

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

    /** Nothing playing, which is how the deck sits most of the time. */
    @Test
    fun `home with music playing`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                CockpitHome(
                    favourites = previewFavourites.take(3),
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
                    favourites = previewFavourites.take(3),
                    overlaysOn = false,
                    actions = previewActions,
                    vehicle = previewVehicle
                )
            }
        }
    }

    private companion object {
        /**
         * Landscape at 160dpi so dp maps 1:1 to px and the numbers in the token
         * file can be read straight off the image. Replace with the real panel
         * geometry once Settings → System has been read on the deck.
         */
        val DECK: DeviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1280,
            screenHeight = 720,
            density = Density.MEDIUM,
            orientation = ScreenOrientation.LANDSCAPE,
            softButtons = false
        )
    }
}
