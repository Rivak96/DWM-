package com.dwm.cockpit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dwm.cockpit.Media
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType
import kotlinx.coroutines.delay

/**
 * Now playing, with real transport controls.
 *
 * Three states, and every one of them has to look deliberate. This project has
 * already shipped screens that turned out to be empty because the data behind them
 * did not exist; a music card that silently shows nothing would be the same
 * mistake in a new place. So: no notification access offers to fix it, nothing
 * playing becomes a launch tile, and only a real session draws the full card.
 *
 * Album art is the only rich imagery this launcher has — everything else is text
 * and vectors — so when the panel is given a tall slot it lays out vertically and
 * gives the artwork the room. Adaptive rather than a `hero` flag, because the same
 * card is also used in short wide slots and should not need to be told which one
 * it is in.
 */
@Composable
fun MediaPanel(
    onOpen: (String) -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    // Only so a @Preview can show the playing state. The poll below overwrites it
    // within a second on a real device, so the default is what actually ships.
    initialState: Media.State = Media.State.Idle
) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    var state by remember { mutableStateOf(initialState) }

    // 1s is plenty for a track change, and keeps binder chatter off the 4Hz
    // vehicle tick. Data-class equality means an unchanged track recomposes nothing.
    //
    // Skipped under inspection. A preview or a Paparazzi render has no notification
    // listener, so the poll immediately answered NoAccess and painted over whatever
    // state the caller passed in — the "now playing" snapshot was quietly rendering
    // the permissions prompt instead.
    LaunchedEffect(inspecting) {
        if (inspecting) return@LaunchedEffect
        while (true) {
            state = Media.state(context)
            delay(1000)
        }
    }

    FlatCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tall = maxHeight > maxWidth
            when (val s = state) {
                is Media.State.NoAccess -> Placeholder(
                    "Allow notification access",
                    "to show what's playing",
                    tall,
                    onClick = onGrantAccess
                )

                is Media.State.Idle -> Placeholder(
                    "Nothing playing",
                    "Tap to open music",
                    tall,
                    onClick = { Media.launchTarget(context)?.let(onOpen) }
                )

                is Media.State.Playing ->
                    if (tall) PlayingTall(s, onOpen) else PlayingWide(s, onOpen)
            }
        }
    }
}

/** Idle and no-access both have to fill the slot without looking like a failure.
 *  A large glyph does that where a line of small grey text does not. */
@Composable
private fun Placeholder(title: String, subtitle: String, tall: Boolean, onClick: () -> Unit) {
    val colors = Dwm.colors
    Column(
        Modifier.fillMaxSize().clickable { onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (tall) Alignment.CenterHorizontally else Alignment.Start
    ) {
        if (tall) {
            DwmText("♫", size = DwmType.display, color = colors.textTertiary)
            Spacer(Modifier.height(DwmSpace.l))
        } else {
            CardLabel("MEDIA")
            Spacer(Modifier.height(DwmSpace.xs))
        }
        DwmText(title, size = DwmType.body, color = colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        DwmText(subtitle, size = DwmType.caption, color = colors.textTertiary)
    }
}

@Composable
private fun PlayingTall(s: Media.State.Playing, onOpen: (String) -> Unit) {
    val colors = Dwm.colors
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CardLabel("NOW PLAYING")
        Spacer(Modifier.height(DwmSpace.m))

        Artwork(
            s,
            Modifier.fillMaxWidth().weight(1f).aspectRatio(1f).clickable { onOpen(s.pkg) }
        )

        Spacer(Modifier.height(DwmSpace.m))
        DwmText(s.title, size = DwmType.body, color = colors.textPrimary, weight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        DwmText(s.artist, size = DwmType.caption, color = colors.textSecondary)

        Spacer(Modifier.height(DwmSpace.m))
        Row(
            horizontalArrangement = Arrangement.spacedBy(DwmSpace.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportButton("⏮", 48.dp) { Media.previous() }
            TransportButton(if (s.playing) "❚❚" else "▶", 60.dp) { Media.playPause() }
            TransportButton("⏭", 48.dp) { Media.next() }
        }
    }
}

@Composable
private fun PlayingWide(s: Media.State.Playing, onOpen: (String) -> Unit) {
    val colors = Dwm.colors
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Artwork(
            s,
            Modifier.fillMaxHeight().aspectRatio(1f).clickable { onOpen(s.pkg) }
        )
        Spacer(Modifier.width(DwmSpace.m))
        Column(Modifier.weight(1f)) {
            DwmText(s.title, size = DwmType.label, color = colors.textPrimary, weight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            DwmText(s.artist, size = DwmType.micro, color = colors.textSecondary)
        }
        Spacer(Modifier.width(DwmSpace.s))
        Row(
            horizontalArrangement = Arrangement.spacedBy(DwmSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportButton("⏮", 42.dp) { Media.previous() }
            TransportButton(if (s.playing) "❚❚" else "▶", 42.dp) { Media.playPause() }
            TransportButton("⏭", 42.dp) { Media.next() }
        }
    }
}

@Composable
private fun Artwork(s: Media.State.Playing, modifier: Modifier) {
    val colors = Dwm.colors
    Box(
        modifier.clip(DwmShapes.medium).background(colors.surfaceLow),
        contentAlignment = Alignment.Center
    ) {
        val art = s.art
        if (art != null) {
            Image(
                art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DwmText("♫", size = DwmType.headline, color = colors.textTertiary)
        }
    }
}

@Composable
private fun TransportButton(glyph: String, size: Dp, onClick: () -> Unit) {
    val colors = Dwm.colors
    Box(
        Modifier
            .size(size)
            .clip(DwmShapes.pill)
            .background(colors.press)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        DwmText(glyph, size = DwmType.body, color = colors.textPrimary)
    }
}
