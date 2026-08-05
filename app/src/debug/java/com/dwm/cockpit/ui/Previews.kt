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
 * Studio previews.
 *
 * The device spec is the real panel: 1920x1200px at 192dpi is a 1600x1000dp canvas.
 * This must stay in step with `DECK` in the snapshot tests — same geometry, two
 * syntaxes, and nothing enforces the agreement but this note.
 *
 * A pane holding a live app renders empty here, because the app is a separate
 * freeform task that neither Studio nor Paparazzi can see. That is honest rather
 * than a gap: what the golden proves is the chrome and the geometry the window will
 * land in, and only the deck can prove the window lands in it.
 */
private const val DECK = "spec:width=1600dp,height=1000dp,dpi=192"

@Preview(name = "Cockpit · camera + drawer (the default)", device = DECK)
@Composable
private fun PreviewCockpitDefault() {
    DwmPreviewTheme {
        CockpitHome(
            panes = previewPanesDefault,
            splitFraction = 0.5f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal
        )
    }
}

@Preview(name = "Cockpit · live map + camera", device = DECK)
@Composable
private fun PreviewCockpitApps() {
    DwmPreviewTheme {
        CockpitHome(
            panes = previewPanesApps,
            splitFraction = 0.58f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleIdle
        )
    }
}

@Preview(name = "Cockpit · reversing", device = DECK)
@Composable
private fun PreviewCockpitReversing() {
    DwmPreviewTheme {
        CockpitHome(
            panes = previewPanesDefault,
            splitFraction = 0.5f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = false,
            actions = previewActions,
            vehicle = previewVehicle
        )
    }
}

@Preview(name = "Cockpit · night", device = DECK)
@Composable
private fun PreviewCockpitNight() {
    DwmPreviewTheme(night = true) {
        CockpitHome(
            panes = previewPanesDefault,
            splitFraction = 0.5f,
            favourites = previewDeckApps,
            allApps = previewFavourites,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal
        )
    }
}

/* ------------------------------------------------------------- fragments */

@Preview(name = "Vehicle diagram · no signal", widthDp = 380, heightDp = 260)
@Composable
private fun PreviewVehicleNoSignal() = Fragment {
    VehicleDiagram(BodyState(), Modifier.fillMaxSize())
}

@Preview(name = "Vehicle diagram · door open, reversing", widthDp = 380, heightDp = 260)
@Composable
private fun PreviewVehicleActive() = Fragment {
    VehicleDiagram(previewBodyActive, Modifier.fillMaxSize())
}

@Preview(name = "Media · playing", widthDp = 520, heightDp = 220)
@Composable
private fun PreviewMediaPlaying() = Fragment {
    MediaStrip(
        Media.State.Playing(
            pkg = "com.spotify.music",
            title = "Everything In Its Right Place",
            artist = "Radiohead",
            art = null,
            playing = true
        ),
        {}, {}, Modifier.fillMaxSize()
    )
}

@Composable
private fun Fragment(content: @Composable () -> Unit) {
    DwmPreviewTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Dwm.colors.background)
                .padding(DwmSpace.l)
        ) { content() }
    }
}
