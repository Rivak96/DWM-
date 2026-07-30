package com.dwm.cockpit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                    size = 58.sp,
                    color = Color.White,
                    weight = FontWeight.Light,
                    tabular = true
                )
                Spacer(Modifier.width(6.dp))
                DwmText(
                    "km/h", size = 12.sp, color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            SteeringRow(vitals.track, accent)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StateChip("LIGHTS", body.headlight, OK_C)
            StateChip("REVERSE", if (body.reverse) true else null, CAUTION_C)
            BatteryChip(vitals.voltage)
        }
    }
}

/**
 * Steering, from the vendor's 0..480 trace. One of the six live readings, and the
 * only one that moves continuously while parked — which makes it the quickest way
 * to confirm the CAN link is alive: turn the wheel and watch it move.
 */
@Composable
private fun SteeringRow(track: Int?, accent: Color) {
    val deg = steeringDegrees(track)
    val frac = ((track ?: 240) - 240) / 240f
    val shown by animateFloatAsState(frac.coerceIn(-1f, 1f), tween(260), label = "steer")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DwmText("STEERING", size = 8.sp, color = Color.White.copy(alpha = 0.45f))
            Spacer(Modifier.weight(1f))
            DwmText(
                if (deg == null) "—" else "${if (deg > 0) "+" else ""}$deg°",
                size = 13.sp, color = Color.White.copy(alpha = 0.9f), tabular = true
            )
        }
        Spacer(Modifier.height(5.dp))
        // Centre-anchored bar: fills left or right of the middle as the wheel turns.
        Box(
            Modifier.fillMaxWidth().height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                    if (shown < 0f) {
                        Box(
                            Modifier.fillMaxWidth(-shown).fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp)).background(accent)
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (shown > 0f) {
                        Box(
                            Modifier.fillMaxWidth(shown).fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp)).background(accent)
                        )
                    }
                }
            }
        }
    }
}

/** Null = the van has never said, which must not look like "off". */
@Composable
private fun StateChip(label: String, on: Boolean?, onColor: Color) {
    val fg = when (on) {
        true -> onColor
        false -> Color.White.copy(alpha = 0.32f)
        null -> Color.White.copy(alpha = 0.16f)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (on == true) onColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        DwmText(label, size = 8.sp, color = fg, weight = FontWeight.Medium)
    }
}

@Composable
private fun BatteryChip(v: Float?) {
    // Below ~12V with the engine off is a battery worth knowing about.
    val c = when {
        v == null -> Color.White.copy(alpha = 0.16f)
        v < 11.8f -> WARN_C
        v < 12.2f -> CAUTION_C
        else -> Color.White.copy(alpha = 0.75f)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        DwmText(fmt(v, "V", 1), size = 8.sp, color = c, weight = FontWeight.Medium, tabular = true)
    }
}

/* ----------------------------------------------------------------- parking */

@Composable
fun ParkingZone(body: BodyState, accent: Color) {
    val live = body.radar.size >= 16 && body.radar.any { it > 0 }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardLabel(if (body.reverse) "PARKING · REVERSING" else "PARKING SENSORS")
            Spacer(Modifier.weight(1f))
            val nearest = body.radar.filter { it > 0 }.minOrNull()
            if (nearest != null) {
                DwmText(
                    when {
                        nearest <= 3 -> "STOP"
                        nearest <= 7 -> "CLOSE"
                        else -> "CLEAR"
                    },
                    size = 9.sp,
                    color = if (nearest <= 3) WARN_C else if (nearest <= 7) CAUTION_C else OK_C,
                    weight = FontWeight.Medium
                )
            } else if (!live) {
                DwmText("idle", size = 9.sp, color = Color.White.copy(alpha = 0.3f))
            }
        }
        CarDiagram(body, accent, Modifier.fillMaxWidth().weight(1f))
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
