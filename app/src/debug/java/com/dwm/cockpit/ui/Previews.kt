package com.dwm.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dwm.cockpit.Media
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmSpace

/**
 * Previews.
 *
 * **The device spec below is a placeholder and should be corrected once.** Open
 * Settings → System on the deck; it now prints a line reading
 * `Preview: spec:width=…dp,height=…dp,dpi=…`. Paste that over [DECK] and every
 * preview here starts matching the real panel, including the effect of
 * `Scale.kt`'s density override. Until then these are the right shapes at roughly
 * the wrong size.
 */
private const val DECK = "spec:width=1280dp,height=720dp,dpi=160"

/* --------------------------------------------------------------- whole screen */

@Preview(name = "Home · driving", device = DECK, showBackground = true)
@Composable
private fun PreviewHomeDriving() {
    DwmPreviewTheme {
        CockpitHome(
            favourites = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleIdle
        )
    }
}

/**
 * Reverse engaged — the left column hands over from music to the sensors.
 *
 * The state worth looking at most often and the hardest to reach on the deck,
 * since reproducing it means actually putting the van in reverse.
 */
@Preview(name = "Home · reversing", device = DECK, showBackground = true)
@Composable
private fun PreviewHomeReversing() {
    DwmPreviewTheme {
        CockpitHome(
            favourites = previewFavourites,
            overlaysOn = false,
            actions = previewActions,
            vehicle = previewVehicle
        )
    }
}

/**
 * The regression test for the design-system rebuild.
 *
 * Selecting Light used to produce white-on-white, because the palette said one
 * thing and roughly forty `Color.White.copy(alpha = …)` call sites said another.
 * If this preview is legible, those are all gone.
 */
@Preview(name = "Home · Light", device = DECK, showBackground = true)
@Composable
private fun PreviewHomeLight() {
    DwmPreviewTheme(colors = LightPreviewColors) {
        CockpitHome(
            favourites = previewFavourites,
            overlaysOn = false,
            actions = previewActions,
            vehicle = previewVehicleIdle
        )
    }
}

/* ------------------------------------------------------------------ fragments */

/** Sensors and the steering-predicted path, at the size the column gives them. */
@Preview(name = "Parking · object behind", widthDp = 300, heightDp = 460)
@Composable
private fun PreviewParkingClose() {
    DwmPreviewTheme {
        Box(Modifier.fillMaxSize().background(Dwm.colors.bg).padding(DwmSpace.m)) {
            FlatCard(Modifier.fillMaxSize()) {
                ParkingDisplay(previewVehicle.body.value, Modifier.fillMaxSize())
            }
        }
    }
}

/** Wheel straight, nothing detected — has to read as working, not as blank. */
@Preview(name = "Parking · all clear", widthDp = 300, heightDp = 460)
@Composable
private fun PreviewParkingClear() {
    DwmPreviewTheme {
        Box(Modifier.fillMaxSize().background(Dwm.colors.bg).padding(DwmSpace.m)) {
            FlatCard(Modifier.fillMaxSize()) {
                ParkingDisplay(previewVehicleIdle.body.value, Modifier.fillMaxSize())
            }
        }
    }
}

@Preview(name = "Media · hero playing", widthDp = 300, heightDp = 460)
@Composable
private fun PreviewMediaHero() {
    DwmPreviewTheme {
        Box(Modifier.fillMaxSize().background(Dwm.colors.bg).padding(DwmSpace.m)) {
            MediaPanel(
                onOpen = {},
                onGrantAccess = {},
                modifier = Modifier.fillMaxSize(),
                initialState = Media.State.Playing(
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

/** Nothing playing, in the tall slot — the state most at risk of reading as an
 *  empty card rather than a deliberate one. */
@Preview(name = "Media · hero idle", widthDp = 300, heightDp = 460)
@Composable
private fun PreviewMediaHeroIdle() {
    DwmPreviewTheme {
        Box(Modifier.fillMaxSize().background(Dwm.colors.bg).padding(DwmSpace.m)) {
            MediaPanel(
                onOpen = {},
                onGrantAccess = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
