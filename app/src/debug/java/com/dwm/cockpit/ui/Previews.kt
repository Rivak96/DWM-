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
 * It was `1280dp x 720dp @ 160dpi`, a placeholder that made every preview the right
 * shape at the wrong size. This must stay in step with `DECK` in `HomeSnapshotTest`
 * — same geometry, two syntaxes, and nothing enforces the agreement but this note.
 *
 * `Prefs.uiScale` is now 1.0, so a preview at this spec and the deck are finally
 * showing the same thing; while the default was 0.8 they never were.
 */
private const val DECK = "spec:width=1600dp,height=1000dp,dpi=192"

@Preview(name = "Home · no CAN signal (the real deck)", device = DECK)
@Composable
private fun PreviewHomeNoSignal() {
    DwmPreviewTheme {
        CockpitHome(
            favourites = previewDeckApps,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal
        )
    }
}

@Preview(name = "Home · twelve favourites", device = DECK)
@Composable
private fun PreviewHomeFull() {
    DwmPreviewTheme {
        CockpitHome(
            favourites = previewFavourites,
            overlaysOn = false,
            actions = previewActions,
            vehicle = previewVehicleIdle
        )
    }
}

@Preview(name = "Home · reversing", device = DECK)
@Composable
private fun PreviewHomeReversing() {
    DwmPreviewTheme {
        CockpitHome(
            favourites = previewDeckApps,
            overlaysOn = false,
            actions = previewActions,
            vehicle = previewVehicle
        )
    }
}

@Preview(name = "Home · night", device = DECK)
@Composable
private fun PreviewHomeNight() {
    DwmPreviewTheme(night = true) {
        CockpitHome(
            favourites = previewDeckApps,
            overlaysOn = true,
            actions = previewActions,
            vehicle = previewVehicleNoSignal
        )
    }
}

/* ------------------------------------------------------------- fragments */

@Preview(name = "Proximity · no signal", widthDp = 380, heightDp = 460)
@Composable
private fun PreviewProximityNoSignal() = Fragment {
    ProximityCard(BodyState(), Modifier.fillMaxSize())
}

@Preview(name = "Proximity · close", widthDp = 380, heightDp = 460)
@Composable
private fun PreviewProximityClose() = Fragment {
    ProximityCard(
        BodyState(
            reverse = true,
            track = 300,
            radar = listOf(0, 0, 0, 0, 0, 0, 9, 5, 7, 3, 2, 8, 0, 0, 0, 0)
        ),
        Modifier.fillMaxSize()
    )
}

@Preview(name = "Media · idle", widthDp = 900, heightDp = 160)
@Composable
private fun PreviewMediaIdle() = Fragment {
    MediaStrip(Media.State.Idle, {}, {}, Modifier.fillMaxSize())
}

@Preview(name = "Media · playing", widthDp = 900, heightDp = 160)
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
