package com.dwm.cockpit.ui

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    val enterAlpha by animateFloatAsState(if (appear) 1f else 0f, tween(450), label = "enterA")
    val enterY by animateFloatAsState(if (appear) 0f else 36f, tween(450), label = "enterY")

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
            Modifier.fillMaxSize().padding(16.dp)
                .graphicsLayer { alpha = enterAlpha; translationY = enterY }
        ) {
            TopBar(actions)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                // LEFT — action tiles
                Column(Modifier.fillMaxHeight().weight(1f)) { Tiles(overlaysOn, actions) }

                Spacer(Modifier.width(12.dp))

                // CENTRE — cockpit hero
                GlassCard(Modifier.fillMaxHeight().weight(1.4f), padding = 16.dp) {
                    Hero(panelCount, modeName, previewView, actions)
                }

                Spacer(Modifier.width(12.dp))

                // RIGHT — favourites + status
                Column(Modifier.fillMaxHeight().weight(1f)) {
                    GlassCard(Modifier.fillMaxWidth().weight(1f)) {
                        Favourites(favourites, actions)
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        Status(overlaysOn, version, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(actions: HomeActions) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = remember(now / 60000) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 3600000) { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now)) }
    val cs = MaterialTheme.colorScheme

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(time, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(date, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        IconChip(Icons.Rounded.Refresh, actions.reload)
        Spacer(Modifier.width(8.dp))
        IconChip(Icons.Rounded.Edit, actions.edit)
        Spacer(Modifier.width(8.dp))
        IconChip(Icons.Rounded.Settings, actions.settings)
    }
}

@Composable
private fun IconChip(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Tiles(overlaysOn: Boolean, actions: HomeActions) {
    val accent = MaterialTheme.colorScheme.primary
    val rows = listOf(
        listOf(
            Triple(Icons.Rounded.DirectionsCar, "CarPlay", accent) to actions.carplay,
            Triple(Icons.Rounded.Layers, "Overlays", if (overlaysOn) GREEN else null) to actions.overlays
        ),
        listOf(
            Triple(Icons.Rounded.Bluetooth, "Bluetooth", null) to actions.bluetooth,
            Triple(Icons.Rounded.Wifi, "Wi-Fi", null) to actions.wifi
        ),
        listOf(
            Triple(Icons.Rounded.Apps, "Apps", null) to actions.apps,
            Triple(Icons.Rounded.Settings, "Settings", null) to actions.settings
        )
    )
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((spec, click) in row) {
                    Tile(spec.first, spec.second, spec.third, Modifier.weight(1f).fillMaxHeight(), click)
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, fill: Color?, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = fill ?: cs.surface.copy(alpha = 0.5f)
    val fg = if (fill != null) Color.White else Color.White.copy(alpha = 0.92f)
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = fg, fontSize = 11.sp)
    }
}

@Composable
private fun Hero(panelCount: Int, modeName: String, previewView: View, actions: HomeActions) {
    Column(Modifier.fillMaxSize()) {
        Text("Cockpit", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(
            if (panelCount == 0) "No layout yet — tap Edit to build it"
            else "$modeName · $panelCount panel${if (panelCount == 1) "" else "s"}",
            color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp
        )
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = actions.reload,
                modifier = Modifier.weight(1.4f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Launch cockpit", fontSize = 15.sp) }
            Button(
                onClick = actions.edit,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    contentColor = Color.White
                )
            ) { Text("Edit", fontSize = 15.sp) }
        }
    }
}

@Composable
private fun Favourites(favourites: List<DwmFav>, actions: HomeActions) {
    Column(Modifier.fillMaxSize()) {
        Text("FAVOURITES", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        val rows = favourites.take(8).chunked(2)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (fav in row) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { actions.favTap(fav.pkg) }
                                .padding(4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (fav.icon != null) {
                                Image(fav.icon, contentDescription = fav.label, modifier = Modifier.size(44.dp))
                            }
                            Text(
                                fav.label, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Status(overlaysOn: Boolean, version: String, actions: HomeActions) {
    Column(Modifier.fillMaxWidth()) {
        StatusRow("Overlays", if (overlaysOn) "On" else "Off", if (overlaysOn) GREEN else Color.White.copy(alpha = 0.6f), actions.overlays)
        StatusRow("Floating pill", "Show", Color.White.copy(alpha = 0.6f), actions.pill)
        StatusRow("Version", "v$version", Color.White.copy(alpha = 0.6f), actions.checkUpdate)
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
