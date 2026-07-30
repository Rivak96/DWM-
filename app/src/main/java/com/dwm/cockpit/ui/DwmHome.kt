package com.dwm.cockpit.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwm.cockpit.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActions(
    val carplay: () -> Unit,
    val overlays: () -> Unit,
    val bluetooth: () -> Unit,
    val wifi: () -> Unit,
    val apps: () -> Unit,
    val edit: () -> Unit,
    val settings: () -> Unit,
    val reload: () -> Unit,
    val pill: () -> Unit
)

private val GREEN = Color(0xFF34C759)

/**
 * Launcher home — a vehicle dashboard.
 *
 * What used to be here (a cockpit hero with a layout preview, a 4x3 favourites
 * grid and a status card of five label/value rows) was three boxes of chrome on a
 * screen attached to a car that can now tell us its gear, speed, doors, tyres and
 * parking sensors. Those three are gone: layout editing is a dock button, app
 * launching is the Apps drawer and the pill, and the status rows were duplicates
 * of dock buttons or live in Settings. The space they were using is the dashboard.
 *
 * Laid out the way the Jaecoo J7's centre screen is: a thin status strip, content
 * floating over a soft wallpaper, one persistent dock of small line icons. Kept
 * deliberately compact — the deck is a 13" panel, so the win is fitting more on
 * it, not bigger buttons, and [com.dwm.cockpit.Prefs.uiScale] trims it further.
 */
@Composable
fun DwmHome(
    wallpaper: ImageBitmap?,
    overlaysOn: Boolean,
    actions: HomeActions
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val vehicle = rememberVehicleState(context)

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
            StatusStrip(vehicle.head.value, overlaysOn, actions)
            Spacer(Modifier.height(9.dp))

            Row(Modifier.fillMaxWidth().weight(1f)) {
                GlassCard(Modifier.fillMaxHeight().weight(1f), radius = 16.dp, padding = 11.dp) {
                    DriveZone(vehicle.drive.value, cs.primary)
                }
                Spacer(Modifier.width(9.dp))

                GlassCard(Modifier.fillMaxHeight().weight(1.25f), radius = 16.dp, padding = 11.dp) {
                    VehicleZone(vehicle.body.value, cs.primary)
                }
                Spacer(Modifier.width(9.dp))

                GlassCard(Modifier.fillMaxHeight().weight(1f), radius = 16.dp, padding = 11.dp) {
                    VitalsZone(vehicle.vitals.value, cs.primary)
                }
            }

            Spacer(Modifier.height(9.dp))
            Dock(overlaysOn, actions)
        }
    }
}

/* ---------------------------------------------------------------- top strip */

@Composable
private fun StatusStrip(head: HeadState, overlaysOn: Boolean, actions: HomeActions) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = remember(now / 60000) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 3600000) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(9.dp))
        Text(date, color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)
        Spacer(Modifier.width(12.dp))
        AmbientReadout(head)

        Spacer(Modifier.weight(1f))

        // Tapping the CAN state goes where the diagnostics are, so "why is
        // everything a dash?" is one tap from its answer.
        Box(Modifier.clip(RoundedCornerShape(9.dp)).clickable { actions.settings() }
            .padding(horizontal = 7.dp, vertical = 5.dp)) {
            CanDot(head.canLevel, head.demo)
        }
        Spacer(Modifier.width(6.dp))
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

/* -------------------------------------------------------------------- dock */

/** The J7's persistent bottom bar: one row of small line icons, always reachable.
 *  This is now the only navigation on home, so every screen the cards used to
 *  reach has a button here. */
@Composable
private fun Dock(overlaysOn: Boolean, actions: HomeActions) {
    val accent = MaterialTheme.colorScheme.primary
    val items = listOf(
        DockItem(R.drawable.ic_car, "CarPlay", accent, actions.carplay),
        DockItem(R.drawable.ic_apps, "Apps", null, actions.apps),
        DockItem(R.drawable.ic_edit, "Cockpit", null, actions.edit),
        DockItem(R.drawable.ic_layers, "Overlays", if (overlaysOn) GREEN else null, actions.overlays),
        DockItem(R.drawable.ic_pill, "Pill", null, actions.pill),
        DockItem(R.drawable.ic_bt, "Bluetooth", null, actions.bluetooth),
        DockItem(R.drawable.ic_wifi, "Wi-Fi", null, actions.wifi),
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
