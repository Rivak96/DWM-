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
 * The pieces of the vehicle column.
 *
 * Scoped hard to the six readings this van actually provides — speed, steering,
 * indicators, headlights, battery voltage and the sixteen parking sensors. The
 * reference screen the user supplied also shows tyre pressures, engine temperature
 * and a PRND indicator; all three return -1 on this vehicle permanently, so those
 * card slots carry real data instead of dashes pretending to be readings.
 */

private val WARN_C = Color(0xFFFF453A)
private val CAUTION_C = Color(0xFFFF9F0A)
private val OK_C = Color(0xFF34C759)

/**
 * Text with tabular figures. Without `tnum` every digit has its own width, so a
 * speed readout visibly jitters as it counts — the number shifts sideways while
 * standing still. Costs nothing and is the biggest single polish win on screen.
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

/** Speed, sized off the space it is given rather than a fixed dp — on a 13" panel
 *  a hardcoded size left a tiny number floating in an acre of empty grey. */
@Composable
fun SpeedBlock(drive: DriveState, reverse: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        // Captured before the inner scopes shadow the BoxWithConstraints receiver.
        val h = maxHeight
        val speedSp = (h.value * 0.62f).coerceIn(40f, 150f).sp
        Row(verticalAlignment = Alignment.Bottom) {
            DwmText(
                drive.speedKmh?.toString() ?: "—",
                size = speedSp,
                color = Color.White,
                weight = FontWeight.Light,
                tabular = true
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.padding(bottom = h * 0.10f)) {
                DwmText("km/h", size = 11.sp, color = Color.White.copy(alpha = 0.5f))
                if (reverse) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(CAUTION_C.copy(alpha = 0.22f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        DwmText("R", size = 10.sp, color = CAUTION_C, weight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Parking state in the card slot the reference screen gives to tyre pressures.
 *
 * Sixteen values present means the sensors are genuinely reporting; all-zero then
 * means nothing detected, which must read as ALL CLEAR. Dim pills with no words
 * looked identical to a dead feature and were reported as one.
 */
@Composable
fun ParkingCard(body: BodyState, modifier: Modifier = Modifier) {
    val live = body.radar.size >= 16
    val nearest = body.radar.filter { it > 0 }.minOrNull()
    Column(modifier) {
        CardLabel("PARKING")
        Spacer(Modifier.height(5.dp))
        when {
            !live -> DwmText("NO DATA", size = 13.sp, color = Color.White.copy(alpha = 0.3f), weight = FontWeight.Medium)
            nearest == null -> DwmText("ALL CLEAR", size = 13.sp, color = OK_C, weight = FontWeight.Bold)
            nearest <= 3 -> DwmText("STOP", size = 15.sp, color = WARN_C, weight = FontWeight.Bold)
            nearest <= 7 -> DwmText("CLOSE", size = 14.sp, color = CAUTION_C, weight = FontWeight.Bold)
            else -> DwmText("CLEAR", size = 13.sp, color = OK_C, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        // The four rear sensors as a mini bar — the ones that matter reversing.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until 4) {
                val v = body.radar.getOrNull(8 + i) ?: -1
                val c = when {
                    v <= 0 -> Color.White.copy(alpha = 0.10f)
                    v <= 3 -> WARN_C
                    v <= 7 -> CAUTION_C
                    else -> OK_C
                }
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
            }
        }
    }
}

/** Battery and steering, in the slot the reference screen gives to engine temp. */
@Composable
fun BatterySteerCard(vitals: VitalsState, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        CardLabel("BATTERY")
        Spacer(Modifier.height(5.dp))
        val v = vitals.voltage
        val c = when {
            v == null -> Color.White.copy(alpha = 0.3f)
            v < 11.8f -> WARN_C
            v < 12.2f -> CAUTION_C
            else -> Color.White
        }
        DwmText(fmt(v, "V", 1), size = 17.sp, color = c, weight = FontWeight.Medium, tabular = true)
        Spacer(Modifier.height(8.dp))
        SteeringDial(vitals.track, accent, Modifier.size(46.dp))
    }
}

/**
 * Steering as a dial rather than a bar.
 *
 * A flat line at dead centre is indistinguishable from a flat line that is not
 * working, which is exactly how the first build read. An arc with a needle has an
 * obvious rest position and an obvious sweep — and steering is the only live
 * reading on this van that moves with the engine off, so it doubles as the
 * quickest proof the CAN link is alive: turn the wheel and watch it.
 */
@Composable
fun SteeringDial(track: Int?, accent: Color, modifier: Modifier) {
    val deg = steeringDegrees(track)
    val frac = (((track ?: 240) - 240) / 240f).coerceIn(-1f, 1f)
    val shown by animateFloatAsState(frac, tween(220), label = "steer")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.10f
            val inset = stroke / 2f
            val sweep = 240f
            val start = 150f
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (track != null) {
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
        DwmText(
            if (deg == null) "—" else "${if (deg > 0) "+" else ""}$deg",
            size = 10.sp,
            color = Color.White.copy(alpha = 0.9f),
            weight = FontWeight.Medium,
            tabular = true
        )
    }
}

/** Null = the van has never said, which must not look like "off". */
@Composable
fun StateChip(label: String, on: Boolean?, onColor: Color, size: TextUnit = 8.sp) {
    val fg = when (on) {
        true -> onColor
        false -> Color.White.copy(alpha = 0.32f)
        null -> Color.White.copy(alpha = 0.16f)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (on == true) onColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        DwmText(label, size = size, color = fg, weight = FontWeight.Medium)
    }
}

/** Tiny dim all-caps card heading. */
@Composable
fun CardLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.42f),
        fontSize = 8.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.1.sp,
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
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Spacer(Modifier.width(4.dp))
        DwmText(
            if (demo) "DEMO" else "CAN",
            size = 8.sp,
            color = if (level >= 1 || demo) c else Color.White.copy(alpha = 0.4f),
            weight = FontWeight.Medium
        )
    }
}
