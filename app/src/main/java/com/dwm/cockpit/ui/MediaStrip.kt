package com.dwm.cockpit.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.dwm.cockpit.Media
import com.dwm.cockpit.R
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType
import kotlinx.coroutines.delay

/**
 * Now playing, and it is always a strip.
 *
 * ### The inversion this fixes
 *
 * An idle music widget used to be one of the largest elements on the home screen
 * while holding no information at all: a third of the canvas given over to the words
 * "Nothing playing". The biggest thing on a screen should be the most useful thing
 * on it.
 *
 * ### Why it never resizes
 *
 * The obvious fix is a widget that expands when something starts playing. That would
 * animate height, which triggers measure and layout on every frame of the
 * transition, and on a Mali-G51 driving 2.3 million pixels that is the one thing
 * genuinely worth avoiding. It would also make the whole layout jump the moment music
 * started, while the vehicle is moving.
 *
 * So the box is [DwmSize.mediaStrip] tall in every state, and artwork, title, artist
 * and three transport buttons all fit inside it. Only the *contents* change, by
 * crossfade — opacity alone, no layout pass. Idle is not a smaller version of
 * playing; it is the same strip with less in it.
 */
@Composable
fun MediaStrip(
    state: Media.State,
    onOpen: (String) -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    var live by remember { mutableStateOf(state) }

    // Under Paparazzi and Studio previews there is no notification listener, so a
    // poll here answers NoAccess and paints the permission prompt over whatever
    // state the caller passed in — which silently turned every snapshot of a
    // playing track into a snapshot of a permissions error.
    LaunchedEffect(inspecting) {
        if (inspecting) return@LaunchedEffect
        while (true) {
            live = Media.state(context)
            delay(1000)
        }
    }
    val shown = if (inspecting) state else live

    DwmCard(modifier = modifier, padding = DwmSpace.l) {
        Crossfade(
            targetState = shown is Media.State.Playing,
            animationSpec = DwmMotion.fast,
            label = "media"
        ) { playing ->
            if (playing && shown is Media.State.Playing) {
                Playing(shown, onOpen)
            } else {
                Resting(
                    text = if (shown is Media.State.NoAccess) "Media access off"
                    else "Nothing playing",
                    onClick = if (shown is Media.State.NoAccess) onGrantAccess
                    else ({ onOpen("") })
                )
            }
        }
    }
}

/**
 * The idle strip.
 *
 * Muted, one line, and tappable. It says what it is and takes no more room than that
 * needs — which for most of the time this deck is switched on is the whole design.
 */
@Composable
private fun Resting(text: String, onClick: () -> Unit) {
    val colors = Dwm.colors
    Row(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_dwm_media),
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(DwmSize.icon)
        )
        Spacer(Modifier.width(DwmSpace.l))
        DwmText(text, style = DwmType.body, color = colors.muted)
    }
}

@Composable
private fun Playing(s: Media.State.Playing, onOpen: (String) -> Unit) {
    val colors = Dwm.colors
    Row(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onOpen(s.pkg) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(Modifier.fillMaxHeight().aspectRatio(1f))
        Spacer(Modifier.width(DwmSpace.l))

        Column(Modifier.weight(1f)) {
            DwmText(s.title, style = DwmType.title, color = colors.text)
            DwmText(s.artist, style = DwmType.label, color = colors.muted)
        }

        Spacer(Modifier.width(DwmSpace.l))

        Row(horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)) {
            Transport(R.drawable.ic_dwm_prev, "Previous") { Media.previous() }
            Transport(
                if (s.playing) R.drawable.ic_dwm_pause else R.drawable.ic_dwm_play,
                if (s.playing) "Pause" else "Play"
            ) { Media.playPause() }
            Transport(R.drawable.ic_dwm_next, "Next") { Media.next() }
        }
    }
}

@Composable
private fun Artwork(modifier: Modifier = Modifier) {
    val colors = Dwm.colors
    // Album art is displayed, never sampled. Extracting a colour from it and tinting
    // the surface is the same mistake as extracting one from an app icon.
    Box(
        modifier
            .clip(DwmShapes.small)
            .background(colors.raised),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_dwm_media),
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(DwmSize.icon)
        )
    }
}

/**
 * A transport button at the full moving-touch size. These get pressed on a road.
 */
@Composable
private fun Transport(icon: Int, label: String, onClick: () -> Unit) {
    val colors = Dwm.colors
    Box(
        Modifier
            .size(DwmSize.touchTarget)
            .clip(DwmShapes.small)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = colors.text,
            modifier = Modifier.size(DwmSize.iconLarge)
        )
    }
}
