package com.dwm.cockpit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * The dashboard, cut down to what this van actually sends.
 *
 * The getter dump of 2026-07-30 was unambiguous: every profile-indexed reading
 * returns -1 permanently — gear, rpm, coolant, fuel, TPMS, doors, belts, ambient,
 * the lot. Six readings are real: **speed, steering, indicators, headlights,
 * battery voltage and the sixteen parking sensors**, plus reverse from the
 * audio-duck broadcast. Tiles for the rest have been deleted rather than left
 * showing dashes, because a dash the vehicle can never fill is just clutter.
 *
 * If a different van, or a different profile in the head unit's car-select app,
 * starts answering those getters, [CarInfo] still polls a few of them and the
 * tiles can come back. Nothing about the AIDL layer was removed.
 */

private val WARN_C = Color(0xFFFF453A)
private val CAUTION_C = Color(0xFFFF9F0A)
private val OK_C = Color(0xFF34C759)

/**
 * Text with tabular figures. Without `tnum` every digit has its own width, so a
 * speed readout visibly jitters as it counts — the number shifts sideways while
 * standing still. Costs nothing and is the single biggest polish win on screen.
 */
@Composable
fun DwmText(
    text: String,
    size: TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    tabular: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        maxLines = 1,
        style = if (tabular) TextStyle(fontFeatureSettings = "tnum") else TextStyle.Default
    )
}

/* -------------------------------------------------------------------- drive */

@Composable
fun DriveZone(drive: DriveState, vitals: VitalsState, body: BodyState, accent: Color) {
    // Sized off the card, not in fixed dp. The deck is a 13" panel and the fixed
    // sizes this started with left a tiny "0" floating in an acre of grey — the
    // exact wasted space the redesign was supposed to kill.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val speedSp = (h.value * 0.30f).coerceIn(56f, 190f).sp
        val unitSp = (h.value * 0.05f).coerceIn(12f, 30f).sp
        val chipSp = (h.value * 0.032f).coerceIn(9f, 18f).sp

        Column(Modifier.fillMaxSize()) {
            CardLabel("DRIVE")

            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    DwmText(
                        drive.speedKmh?.toString() ?: "—",
                        size = speedSp,
                        color = Color.White,
                        weight = FontWeight.Light,
                        tabular = true
                    )
                    Spacer(Modifier.width(8.dp))
                    DwmText(
                        "km/h", size = unitSp, color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = h * 0.045f)
                    )
                }
                Spacer(Modifier.height(h * 0.05f))
                SteeringDial(vitals.track, accent, Modifier.size(h * 0.30f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StateChip("LIGHTS", body.headlight, OK_C, chipSp)
                StateChip("REVERSE", if (body.reverse) true else null, CAUTION_C, chipSp)
                BatteryChip(vitals.voltage, chipSp)
            }
        }
    }
}

/**
 * Steering as a dial rather than a bar.
 *
 * A flat 5dp line at dead centre is indistinguishable from a flat 5dp line that
 * isn't working, which is precisely how the first build read. An arc with a
 * needle has an obvious rest position and an obvious sweep, so turning the wheel
 * while parked is instant proof the CAN link is alive — and steering is the only
 * live reading on this van that moves with the engine off.
 */
@Composable
private fun SteeringDial(track: Int?, accent: Color, modifier: Modifier) {
    val deg = steeringDegrees(track)
    val frac = (((track ?: 240) - 240) / 240f).coerceIn(-1f, 1f)
    val shown by animateFloatAsState(frac, tween(220), label = "steer")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2f
            val sweep = 240f
            val start = 150f
            // track
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (track != null) {
                // needle arc from centre-top outward, so direction is unmistakable
                val mid = start + sweep / 2f
                drawArc(
                    color = accent,
                    startAngle = if (shown >= 0f) mid else mid + shown * (sweep / 2f),
                    sweepAngle = abs(shown) * (sweep / 2f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DwmText(
                if (deg == null) "—" else "${if (deg > 0) "+" else ""}$deg°",
                size = (maxOf(12f, 16f)).sp,
                color = Color.White.copy(alpha = 0.92f),
                weight = FontWeight.Medium,
                tabular = true
            )
            DwmText("STEERING", size = 8.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

/** Null = the van has never said, which must not look like "off". */
@Composable
private fun StateChip(label: String, on: Boolean?, onColor: Color, size: TextUnit) {
    val fg = when (on) {
        true -> onColor
        false -> Color.White.copy(alpha = 0.32f)
        null -> Color.White.copy(alpha = 0.16f)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (on == true) onColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        DwmText(label, size = size, color = fg, weight = FontWeight.Medium)
    }
}

@Composable
private fun BatteryChip(v: Float?, size: TextUnit) {
    // Below ~12V with the engine off is a battery worth knowing about.
    val c = when {
        v == null -> Color.White.copy(alpha = 0.16f)
        v < 11.8f -> WARN_C
        v < 12.2f -> CAUTION_C
        else -> Color.White.copy(alpha = 0.8f)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        DwmText(fmt(v, "V", 1), size = size, color = c, weight = FontWeight.Medium, tabular = true)
    }
}

/* ----------------------------------------------------------------- parking */

@Composable
fun ParkingZone(body: BodyState, accent: Color) {
    // 16 values present means the sensors are genuinely reporting. All-zero then
    // means "nothing detected", which must read as ALL CLEAR — dim grey pills and
    // no words looked identical to a dead feature, and got reported as one.
    val live = body.radar.size >= 16
    val nearest = body.radar.filter { it > 0 }.minOrNull()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusSp = (maxHeight.value * 0.055f).coerceIn(11f, 26f).sp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CardLabel(if (body.reverse) "PARKING · REVERSING" else "PARKING SENSORS")
                Spacer(Modifier.weight(1f))
                when {
                    !live -> DwmText(
                        "NO SENSOR DATA", size = statusSp,
                        color = Color.White.copy(alpha = 0.3f), weight = FontWeight.Medium
                    )
                    nearest == null -> DwmText(
                        "ALL CLEAR", size = statusSp, color = OK_C, weight = FontWeight.Bold
                    )
                    nearest <= 3 -> DwmText(
                        "STOP", size = statusSp, color = WARN_C, weight = FontWeight.Bold
                    )
                    nearest <= 7 -> DwmText(
                        "CLOSE", size = statusSp, color = CAUTION_C, weight = FontWeight.Bold
                    )
                    else -> DwmText(
                        "CLEAR", size = statusSp, color = OK_C, weight = FontWeight.Bold
                    )
                }
            }
            CarDiagram(body, accent, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** Tiny dim all-caps card heading — the J7 uses these above every card. */
@Composable
fun CardLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        maxLines = 1
    )
}

/** Green receiving · amber bound but silent · grey not bound. The middle one
 *  matters: a van that simply never reports must not look like a failed bind. */
@Composable
fun CanDot(level: Int, demo: Boolean) {
    val c = when {
        demo -> CAUTION_C
        level >= 2 -> OK_C
        level == 1 -> CAUTION_C
        else -> Color.White.copy(alpha = 0.25f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Spacer(Modifier.width(4.dp))
        DwmText(
            if (demo) "DEMO" else "CAN",
            size = 9.sp,
            color = if (level >= 1 || demo) c else Color.White.copy(alpha = 0.4f),
            weight = FontWeight.Medium
        )
    }
}
