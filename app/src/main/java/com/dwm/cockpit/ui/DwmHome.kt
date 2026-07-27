package com.dwm.cockpit.ui

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dwm.cockpit.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DwmFav(val pkg: String, val label: String, val icon: ImageBitmap?)

class HomeActions(
    val carplay: () -> Unit,
    val overlays: () -> Unit,
    val bluetooth: () -> Unit,
    val wifi: () -> Unit,
    val apps: () -> Unit,
    val edit: () -> Unit,
    val settings: () -> Unit,
    val reload: () -> Unit,
    val pill: () -> Unit,
    val favTap: (String) -> Unit,
    val favLong: (String) -> Unit,
    val checkUpdate: () -> Unit
)

private val GREEN = Color(0xFF34C759)

/** How many favourites the grid shows: 4 columns x 3 rows. */
const val FAV_SLOTS = 12

/**
 * Launcher home, laid out the way the Jaecoo J7's centre screen is: a thin status
 * strip, content cards floating over a soft wallpaper, and one persistent dock of
 * small line icons along the bottom. Everything is deliberately compact — the deck
 * is a 13" panel, so the win is fitting more on it, not bigger buttons. Physical
 * size is then trimmed globally by the interface-scale preference.
 */
@Composable
fun DwmHome(
    wallpaper: ImageBitmap?,
    favourites: List<DwmFav>,
    overlaysOn: Boolean,
    panelCount: Int,
    modeName: String,
    version: String,
    previewView: View,
    actions: HomeActions
) {
    val cs = MaterialTheme.colorScheme

    // entrance: fade + rise
    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear = true }
    val enterAlpha by animateFloatAsState(if (appear) 1f else 0f, tween(420), label = "enterA")
    val enterY by animateFloatAsState(if (appear) 0f else 28f, tween(420), label = "enterY")

    Box(Modifier.fillMaxSize().background(cs.background)) {
        // blurred wallpaper backdrop (static → cheap) + contrast scrim
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(30.dp)
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))

        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)
                .graphicsLayer { alpha = enterAlpha; translationY = enterY }
        ) {
            StatusStrip(overlaysOn, actions)
            Spacer(Modifier.height(9.dp))

            Row(Modifier.fillMaxWidth().weight(1f)) {
                // COCKPIT — hero with the live layout preview
                GlassCard(Modifier.fillMaxHeight().weight(1.7f), radius = 16.dp, padding = 11.dp) {
                    Hero(panelCount, modeName, previewView, actions)
                }
                Spacer(Modifier.width(9.dp))

                // FAVOURITES — 12 apps instead of 8, at a sane icon size
                GlassCard(Modifier.fillMaxHeight().weight(1.3f), radius = 16.dp, padding = 11.dp) {
                    Favourites(favourites, actions)
                }
                Spacer(Modifier.width(9.dp))

                // STATUS — everything the old tiles duplicated now lives in the dock
                GlassCard(Modifier.fillMaxHeight().weight(0.9f), radius = 16.dp, padding = 11.dp) {
                    Status(overlaysOn, modeName, panelCount, version, actions)
                }
            }

            Spacer(Modifier.height(9.dp))
            Dock(overlaysOn, actions)
        }
    }
}

/* ---------------------------------------------------------------- top strip */

@Composable
private fun StatusStrip(overlaysOn: Boolean, actions: HomeActions) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = remember(now / 60000) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 3600000) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(9.dp))
        Text(date, color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)

        Spacer(Modifier.weight(1f))

        StatusPill(R.drawable.ic_layers, if (overlaysOn) "Overlays on" else "Overlays off",
            if (overlaysOn) GREEN else null, actions.overlays)
        Spacer(Modifier.width(6.dp))
        StatusPill(R.drawable.ic_bt, "Bluetooth", null, actions.bluetooth)
        Spacer(Modifier.width(6.dp))
        StatusPill(R.drawable.ic_wifi, "Wi-Fi", null, actions.wifi)
        Spacer(Modifier.width(6.dp))
        IconChip(R.drawable.ic_reload, actions.reload)
    }
}

@Composable
private fun StatusPill(@DrawableRes icon: Int, label: String, tint: Color?, onClick: () -> Unit) {
    val fg = tint ?: Color.White.copy(alpha = 0.85f)
    Row(
        Modifier
            .height(27.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .clickable { onClick() }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = fg, fontSize = 10.sp)
    }
}

@Composable
private fun IconChip(@DrawableRes icon: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(27.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/* --------------------------------------------------------------------- hero */

@Composable
private fun Hero(panelCount: Int, modeName: String, previewView: View, actions: HomeActions) {
    Column(Modifier.fillMaxSize()) {
        CardLabel("COCKPIT")
        Text(
            if (panelCount == 0) "No layout yet — tap Edit to build it"
            else "$modeName · $panelCount panel${if (panelCount == 1) "" else "s"}",
            color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp
        )
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp, bottom = 9.dp)
                .clip(RoundedCornerShape(11.dp))
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = actions.reload,
                modifier = Modifier.weight(1.5f).height(40.dp),
                shape = RoundedCornerShape(11.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) { Text("Launch cockpit", fontSize = 13.sp) }
            Button(
                onClick = actions.edit,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(11.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    contentColor = Color.White
                )
            ) { Text("Edit", fontSize = 13.sp) }
        }
    }
}

/* -------------------------------------------------------------- favourites */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Favourites(favourites: List<DwmFav>, actions: HomeActions) {
    Column(Modifier.fillMaxSize()) {
        CardLabel("FAVOURITES")
        Spacer(Modifier.height(7.dp))
        val rows = favourites.take(FAV_SLOTS).chunked(4)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (fav in row) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(11.dp))
                                .combinedClickable(
                                    onClick = { actions.favTap(fav.pkg) },
                                    onLongClick = { actions.favLong(fav.pkg) }
                                )
                                .padding(2.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (fav.icon != null) {
                                Image(fav.icon, contentDescription = fav.label, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.height(3.dp))
                            }
                            Text(
                                fav.label, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ status */

@Composable
private fun Status(
    overlaysOn: Boolean,
    modeName: String,
    panelCount: Int,
    version: String,
    actions: HomeActions
) {
    Column(Modifier.fillMaxSize()) {
        CardLabel("STATUS")
        Spacer(Modifier.height(5.dp))
        StatusRow("Mode", modeName, Color.White.copy(alpha = 0.6f), actions.settings)
        StatusRow("Panels", "$panelCount", Color.White.copy(alpha = 0.6f), actions.edit)
        StatusRow("Overlays", if (overlaysOn) "On" else "Off",
            if (overlaysOn) GREEN else Color.White.copy(alpha = 0.6f), actions.overlays)
        StatusRow("Floating pill", "Show", Color.White.copy(alpha = 0.6f), actions.pill)
        StatusRow("Version", "v$version", Color.White.copy(alpha = 0.6f), actions.checkUpdate)
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/* -------------------------------------------------------------------- dock */

/** The J7's persistent bottom bar: one row of small line icons, always reachable,
 *  no duplicate of anything on the cards above. */
@Composable
private fun Dock(overlaysOn: Boolean, actions: HomeActions) {
    val accent = MaterialTheme.colorScheme.primary
    val items = listOf(
        DockItem(R.drawable.ic_car, "CarPlay", accent, actions.carplay),
        DockItem(R.drawable.ic_apps, "Apps", null, actions.apps),
        DockItem(R.drawable.ic_layers, "Overlays", if (overlaysOn) GREEN else null, actions.overlays),
        DockItem(R.drawable.ic_pill, "Pill", null, actions.pill),
        DockItem(R.drawable.ic_bt, "Bluetooth", null, actions.bluetooth),
        DockItem(R.drawable.ic_wifi, "Wi-Fi", null, actions.wifi),
        DockItem(R.drawable.ic_edit, "Edit", null, actions.edit),
        DockItem(R.drawable.ic_settings, "Settings", null, actions.settings)
    )
    GlassCard(Modifier.fillMaxWidth(), radius = 16.dp, padding = 6.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (item in items) {
                Column(
                    Modifier.weight(1f).height(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(item.tint?.copy(alpha = 0.22f) ?: Color.Transparent)
                        .clickable { item.onClick() },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(item.icon), contentDescription = item.label,
                        tint = item.tint ?: Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        color = item.tint ?: Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp, maxLines = 1
                    )
                }
            }
        }
    }
}

private class DockItem(
    @DrawableRes val icon: Int,
    val label: String,
    val tint: Color?,
    val onClick: () -> Unit
)

/* ------------------------------------------------------------------ shared */

/** Tiny dim all-caps card heading — the J7 uses these above every card. */
@Composable
private fun CardLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp
    )
}
