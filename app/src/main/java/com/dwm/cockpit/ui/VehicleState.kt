package com.dwm.cockpit.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.dwm.cockpit.CarInfo
import com.dwm.cockpit.Prefs
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The bridge between [CarInfo]'s volatile fields and Compose.
 *
 * [CarInfo] has no observer API on purpose — its callbacks land on binder threads
 * at whatever rate the CAN box chooses, and speed/rpm can push fast. So rather
 * than pipe every callback into recomposition, this samples on a fixed
 * [TICK_MS] ticker and builds immutable snapshots.
 *
 * The snapshots are data classes, and that is the load-bearing detail: assigning
 * a structurally equal value to a `mutableStateOf` is a no-op, so a car sitting
 * still recomposes nothing at all, and a change in speed repaints the DRIVE zone
 * without touching the car diagram. Hence four small states rather than one big
 * one — the split is what keeps the repaint local.
 *
 * `radar` is carried as a `List<Int>`, never the `IntArray` [CarInfo] holds:
 * arrays use identity equality, so an array field would silently defeat all of
 * the above and repaint everything four times a second.
 */
private const val TICK_MS = 250L

/** Nothing reported yet is a real state, distinct from zero, all the way up. */
data class DriveState(
    val gear: Int? = null,
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val handbrake: Boolean? = null,
    val headlight: Boolean? = null
)

data class TyreState(
    val label: String,
    val pressure: Float? = null,
    val pressureUnit: String? = null,
    val temp: Float? = null,
    val warn: Int? = null
)

data class BodyState(
    val doorLF: Boolean? = null,
    val doorRF: Boolean? = null,
    val doorLR: Boolean? = null,
    val doorRR: Boolean? = null,
    val boot: Boolean? = null,
    val beltDriver: Boolean? = null,
    val beltPassenger: Boolean? = null,
    val headlight: Boolean? = null,
    val turnSignal: Int? = null,
    val reverse: Boolean = false,
    val track: Int? = null,
    val tyres: List<TyreState> = emptyList(),
    val radar: List<Int> = emptyList()
)

data class VitalsState(
    val coolant: Float? = null,
    val fuel: Float? = null,
    val voltage: Float? = null,
    val track: Int? = null
)

/**
 * Top-strip state. [canLevel]: 0 not bound · 1 bound but silent · 2 receiving.
 * "Bound but silent" is worth its own colour — it is exactly the state a car that
 * reports nothing will sit in, and it must not look like a failure to connect.
 */
data class HeadState(
    val ambient: Float? = null,
    val ambientUnit: String? = null,
    val canLevel: Int = 0,
    val updates: Int = 0,
    val demo: Boolean = false
)

class VehicleUi(
    val head: State<HeadState>,
    val drive: State<DriveState>,
    val body: State<BodyState>,
    val vitals: State<VitalsState>
)

@Composable
fun rememberVehicleState(context: Context): VehicleUi {
    val head = remember { mutableStateOf(HeadState()) }
    val drive = remember { mutableStateOf(DriveState()) }
    val body = remember { mutableStateOf(BodyState()) }
    val vitals = remember { mutableStateOf(VitalsState()) }

    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        while (true) {
            val demo = Prefs.demoData(context)
            if (demo) {
                val t = (System.currentTimeMillis() - startedAt) / 1000.0
                demoInto(t, head, drive, body, vitals)
            } else {
                liveInto(head, drive, body, vitals)
            }
            delay(TICK_MS)
        }
    }
    return remember { VehicleUi(head, drive, body, vitals) }
}

private fun liveInto(
    head: MutableState<HeadState>,
    drive: MutableState<DriveState>,
    body: MutableState<BodyState>,
    vitals: MutableState<VitalsState>
) {
    head.value = HeadState(
        ambient = CarInfo.ambient,
        ambientUnit = CarInfo.ambientUnit,
        canLevel = when {
            !CarInfo.bound || !CarInfo.connected -> 0
            CarInfo.updates == 0 -> 1
            else -> 2
        },
        updates = CarInfo.updates,
        demo = false
    )
    drive.value = DriveState(
        gear = CarInfo.gear,
        speedKmh = CarInfo.speedKmh,
        rpm = CarInfo.rpm,
        handbrake = CarInfo.handbrake,
        headlight = CarInfo.headlight
    )
    body.value = BodyState(
        doorLF = CarInfo.doorLF,
        doorRF = CarInfo.doorRF,
        doorLR = CarInfo.doorLR,
        doorRR = CarInfo.doorRR,
        boot = CarInfo.boot,
        beltDriver = CarInfo.beltDriver,
        beltPassenger = CarInfo.beltPassenger,
        headlight = CarInfo.headlight,
        turnSignal = CarInfo.turnSignal,
        // Gear is the better answer where the car gives one; onCarReverse is the
        // fallback, and neither is required for the dashboard to be usable.
        reverse = CarInfo.gear == 2 || CarInfo.reverse == true,
        track = CarInfo.track,
        tyres = CarInfo.tyres.map {
            TyreState(it.label, it.pressure, it.pressureUnit, it.temp, it.warnType)
        },
        radar = CarInfo.radar?.toList() ?: emptyList()
    )
    vitals.value = VitalsState(
        coolant = CarInfo.coolant,
        fuel = CarInfo.fuelLevel,
        voltage = CarInfo.voltage,
        track = CarInfo.track
    )
}

/**
 * Invented values for bench work — see [Prefs.demoData]. Deliberately moves, so
 * animation and the "does this jitter?" question can be answered without a car,
 * and deliberately includes one TPMS warning and some radar returns so the
 * failure colours get exercised too.
 */
private fun demoInto(
    t: Double,
    head: MutableState<HeadState>,
    drive: MutableState<DriveState>,
    body: MutableState<BodyState>,
    vitals: MutableState<VitalsState>
) {
    val wave = (sin(t / 6.0) + 1.0) / 2.0            // 0..1, ~38s period
    val speed = (wave * 96).roundToInt()
    val reversing = t.toInt() % 40 in 30..39         // exercise the reverse styling

    head.value = HeadState(ambient = 18.5f, ambientUnit = "°C", canLevel = 2, updates = 999, demo = true)
    drive.value = DriveState(
        gear = if (reversing) 2 else if (speed < 3) 3 else 0,
        speedKmh = if (reversing) 4 else speed,
        rpm = if (reversing) 900 else 850 + (wave * 3400).roundToInt(),
        handbrake = speed < 3 && !reversing,
        headlight = true
    )
    body.value = BodyState(
        doorLF = false, doorRF = t.toInt() % 24 in 18..23, doorLR = false, doorRR = false,
        boot = false,
        beltDriver = true, beltPassenger = false,
        headlight = true,
        turnSignal = when (t.toInt() % 30) { in 6..11 -> 2; in 20..25 -> 1; else -> 0 },
        reverse = reversing,
        track = (240 + sin(t / 3.0) * 150).roundToInt(),
        tyres = listOf(
            TyreState("LF", 32.4f, "psi", 28f, 0),
            TyreState("RF", 33.0f, "psi", 29f, 0),
            TyreState("LR", 24.1f, "psi", 31f, 1),   // one deliberate warning
            TyreState("RR", 32.2f, "psi", 30f, 0)
        ),
        radar = if (reversing)
            listOf(0, 0, 0, 0, 0, 0, 3, 5, 7, 4, 2, 6, 0, 0, 0, 0)
        else List(16) { 0 }
    )
    vitals.value = VitalsState(
        coolant = 88f + (sin(t / 9.0) * 4).toFloat(),
        fuel = 62f - (t / 240).toFloat().coerceAtMost(20f),
        voltage = 14.2f + (sin(t / 5.0) * 0.2).toFloat(),
        track = (240 + sin(t / 3.0) * 150).roundToInt()
    )
}

/** Vendor coding: `0：D 1:N 2:R 3:P 4:S`. */
val GEARS = listOf("P", "R", "N", "D", "S")

/** Index into [GEARS] for a raw vendor gear code, or -1 when unknown. */
fun gearIndex(gear: Int?): Int = when (gear) {
    3 -> 0; 2 -> 1; 1 -> 2; 0 -> 3; 4 -> 4
    else -> -1
}

/** Steering angle in degrees from the vendor's 0..480 trace, centre 240. */
fun steeringDegrees(track: Int?): Int? = track?.let { ((it - 240) * 0.25f).roundToInt() }

/** One decimal, no trailing ".0" noise, and never "-0". */
fun fmt(v: Float?, unit: String = "", decimals: Int = 1): String {
    if (v == null) return "—"
    val r = if (decimals == 0) v.roundToInt().toString() else "%.${decimals}f".format(v)
    val clean = if (abs(v) < 0.05f && r.startsWith("-")) r.drop(1) else r
    return clean + unit
}
