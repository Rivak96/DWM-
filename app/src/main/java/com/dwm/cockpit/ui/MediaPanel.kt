package com.dwm.cockpit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwm.cockpit.Media
import kotlinx.coroutines.delay

/**
 * Now playing, with real transport controls.
 *
 * Three states, and every one of them has to look deliberate. This project has
 * already shipped screens that turned out to be empty because the data behind them
 * did not exist; a music card that silently shows nothing would be the same
 * mistake in a new place. So: no notification access offers to fix it, nothing
 * playing becomes a launch tile, and only a real session draws the full card.
 */
@Composable
fun MediaPanel(
    onOpen: (String) -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<Media.State>(Media.State.Idle) }

    // 1s is plenty for a track change, and keeps binder chatter off the 4Hz
    // vehicle tick. Data-class equality means an unchanged track recomposes nothing.
    LaunchedEffect(Unit) {
        while (true) {
            state = Media.state(context)
            delay(1000)
        }
    }

    FlatCard(modifier, radius = 12.dp, padding = 9.dp) {
        when (val s = state) {
            is Media.State.NoAccess -> Column(
                Modifier.fillMaxSize().clickable { onGrantAccess() },
                verticalArrangement = Arrangement.Center
            ) {
                CardLabel("MEDIA")
                Spacer(Modifier.width(4.dp))
                DwmText("Allow notification access", size = 11.sp, color = Color.White.copy(alpha = 0.9f))
                DwmText("to show what's playing", size = 9.sp, color = Color.White.copy(alpha = 0.45f))
            }

            is Media.State.Idle -> Column(
                Modifier.fillMaxSize().clickable {
                    Media.launchTarget(context)?.let(onOpen)
                },
                verticalArrangement = Arrangement.Center
            ) {
                CardLabel("MEDIA")
                Spacer(Modifier.width(4.dp))
                DwmText("Nothing playing", size = 11.sp, color = Color.White.copy(alpha = 0.85f))
                DwmText("Tap to open music", size = 9.sp, color = Color.White.copy(alpha = 0.45f))
            }

            is Media.State.Playing -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.fillMaxHeight().aspectOrFallback()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onOpen(s.pkg) },
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
                            DwmText("♫", size = 18.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        DwmText(
                            s.title, size = 12.sp, color = Color.White,
                            weight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(2.dp))
                        DwmText(s.artist, size = 10.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                }
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportButton("⏮") { Media.previous() }
                    TransportButton(if (s.playing) "❚❚" else "▶") { Media.playPause() }
                    TransportButton("⏭") { Media.next() }
                }
            }
        }
    }
}

/** Square when there is room; the card is short, so height drives it. */
private fun Modifier.aspectOrFallback(): Modifier = this.then(Modifier.width(44.dp))

@Composable
private fun TransportButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        DwmText(glyph, size = 13.sp, color = Color.White.copy(alpha = 0.92f))
    }
}
