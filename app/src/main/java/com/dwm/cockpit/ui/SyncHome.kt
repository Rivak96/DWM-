package com.dwm.cockpit.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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

/** One app on a swipe page or in the grid. */
data class HomeApp(val pkg: String, val label: String, val icon: ImageBitmap?)

class HomeActions(
    val carplay: () -> Unit,
    val overlayMenu: () -> Unit,
    val bluetooth: () -> Unit,
    val wifi: () -> Unit,
    val apps: () -> Unit,
    val edit: () -> Unit,
    val settings: () -> Unit,
    val reload: () -> Unit,
    val pill: () -> Unit,
    val launch: (String) -> Unit,
    val appMenu: (String) -> Unit,
    val grantNotifications: () -> Unit
)

private val GREEN = Color(0xFF34C759)

/**
 * Home, laid out like the Ford SYNC screen the user supplied as a target.
 *
 * A vehicle column down the left — speed, the Ranger with its parking sensors,
 * two small data cards and the music player — and swipeable app pages filling the
 * rest, ending in a grid of everything installed. A flat bar along the bottom.
 *
 * What is *not* here is as deliberate as what is. The reference screen shows tyre
 * pressures, engine temperature, a PRND indicator, outside temperature and a full
 * climate bar; every one of those returns -1 on this van, or does not exist in the
 * CAN interface at all. Those slots carry readings that are real instead. Four
 * releases were spent learning that a tile which can never fill is worse than no
 * tile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SyncHome(
    pages: List<HomeApp>,
    allApps: List<HomeApp>,
    overlaysOn: Boolean,
    actions: HomeActions
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val vehicle = rememberVehicleState(context)

    Row(Modifier.fillMaxSize().background(cs.background).padding(8.dp)) {

        // ---------------------------------------------------------- vehicle
        Column(Modifier.fillMaxHeight().weight(0.38f)) {
            TopStrip(vehicle.head.value, actions)
            Spacer(Modifier.height(4.dp))

            SpeedBlock(
                vehicle.drive.value,
                vehicle.body.value.reverse,
                Modifier.fillMaxWidth().height(78.dp)
            )

            // The truck and its sensors take the slack, so a tall screen gives
            // them the room rather than padding the gaps.
            VehicleView(vehicle.body.value, Modifier.fillMaxWidth().weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FlatCard(Modifier.weight(1f), radius = 12.dp, padding = 9.dp) {
                    ParkingCard(vehicle.body.value, Modifier.fillMaxWidth())
                }
                FlatCard(Modifier.weight(1f), radius = 12.dp, padding = 9.dp) {
                    BatterySteerCard(vehicle.vitals.value, cs.primary, Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(7.dp))
            MediaPanel(
                onOpen = actions.launch,
                onGrantAccess = actions.grantNotifications,
                modifier = Modifier.fillMaxWidth().height(86.dp)
            )
        }

        Spacer(Modifier.width(9.dp))

        // ------------------------------------------------------- app pages
        Column(Modifier.fillMaxHeight().weight(0.62f)) {
            val pageCount = pages.size + 1          // + the grid page
            val pagerState = rememberPagerState(pageCount = { pageCount })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                if (page < pages.size) {
                    BigAppPage(pages[page], actions)
                } else {
                    AppGridPage(allApps, actions)
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { i ->
                    val on = pagerState.currentPage == i
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (on) 7.dp else 5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (on) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.22f))
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            BottomBar(overlaysOn, vehicle.vitals.value, actions)
        }
    }
}

/* ---------------------------------------------------------------- top strip */

@Composable
private fun TopStrip(head: HeadState, actions: HomeActions) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = remember(now / 60000) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 3600000) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(7.dp))
        Text(date, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        Spacer(Modifier.weight(1f))
        // Tapping the CAN state lands on the diagnostics — the answer to "why is
        // that reading blank" is one tap away rather than a mystery.
        Box(
            Modifier.clip(RoundedCornerShape(7.dp)).clickable { actions.settings() }
                .padding(horizontal = 5.dp, vertical = 4.dp)
        ) { CanDot(head.canLevel, head.demo) }
    }
}

/* ---------------------------------------------------------------- app pages */

/** One app, as big as the panel allows. Tap launches it fullscreen — a docked
 *  freeform window was considered and declined: they grab audio focus and sink
 *  behind whatever you touch next. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigAppPage(app: HomeApp, actions: HomeActions) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = { actions.launch(app.pkg) },
                onLongClick = { actions.appMenu(app.pkg) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (app.icon != null) {
                Image(app.icon, contentDescription = app.label, modifier = Modifier.size(96.dp))
                Spacer(Modifier.height(14.dp))
            }
            Text(
                app.label,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridPage(apps: List<HomeApp>, actions: HomeActions) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 78.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(apps, key = { it.pkg }) { app ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { actions.launch(app.pkg) },
                            onLongClick = { actions.appMenu(app.pkg) }
                        )
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (app.icon != null) {
                        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        app.label,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* --------------------------------------------------------------- bottom bar */

/** Flat, full width, hairline top edge, line icons. The reference screen puts
 *  climate here; this van exposes no climate at all, so it carries the launcher's
 *  own controls instead. */
@Composable
private fun BottomBar(overlaysOn: Boolean, vitals: VitalsState, actions: HomeActions) {
    val accent = MaterialTheme.colorScheme.primary
    val items = listOf(
        BarItem(R.drawable.ic_car, "CarPlay", accent, actions.carplay),
        BarItem(R.drawable.ic_apps, "Apps", null, actions.apps),
        BarItem(R.drawable.ic_edit, "Cockpit", null, actions.edit),
        BarItem(R.drawable.ic_layers, "Overlays", if (overlaysOn) GREEN else null, actions.overlayMenu),
        BarItem(R.drawable.ic_pill, "Pill", null, actions.pill),
        BarItem(R.drawable.ic_bt, "Bluetooth", null, actions.bluetooth),
        BarItem(R.drawable.ic_wifi, "Wi-Fi", null, actions.wifi),
        BarItem(R.drawable.ic_settings, "Settings", null, actions.settings)
    )
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Row(
            Modifier.fillMaxWidth().height(50.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (item in items) {
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { item.onClick() },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(item.icon),
                        contentDescription = item.label,
                        tint = item.tint ?: Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        color = item.tint ?: Color.White.copy(alpha = 0.55f),
                        fontSize = 8.sp, maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            DwmText(
                fmt(vitals.voltage, "V", 1),
                size = 11.sp,
                color = Color.White.copy(alpha = 0.55f),
                tabular = true,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}

private class BarItem(
    @DrawableRes val icon: Int,
    val label: String,
    val tint: Color?,
    val onClick: () -> Unit
)
