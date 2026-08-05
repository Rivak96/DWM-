package com.dwm.cockpit.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import org.junit.Rule
import org.junit.Test

/**
 * Settings, rendered at the real panel alongside home.
 *
 * The point of a golden here is not to catch a regression in a settings row. It is
 * that home and settings have to look like one designer made both, and the only way
 * to check that claim is to put the two images side by side — which is impossible
 * while one of the screens can only be seen by walking out to the truck.
 */
class SettingsSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DECK,
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false,
        useDeviceResolution = true
    )

    @Test
    fun `settings display section`() {
        paparazzi.snapshot {
            DwmPreviewTheme { SettingsScreen(previewSettings, SettingsActions(), previewActions, true) }
        }
    }

    @Test
    fun `settings about section with the diagnostics panel`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                SettingsScreen(previewSettings, SettingsActions(), previewActions, true, startSection = 5)
            }
        }
    }

    @Test
    fun `settings vehicle section`() {
        paparazzi.snapshot {
            DwmPreviewTheme {
                SettingsScreen(previewSettings, SettingsActions(), previewActions, true, startSection = 3)
            }
        }
    }

    private companion object {
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
