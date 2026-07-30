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
import androidx.compose.material3.MaterialTheme
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
 * The three dashboard zones. Every value is drawn whether or not the car has ever
 * reported it — a signal this car does not carry shows "—" and stays on screen,
 * because until DWM has been driven, *which* readings are missing is itself the
 * most useful thing on the display.
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
fun DriveZone(drive: DriveState, accent: Color) {
    Column(Modifier.fillMaxSize()) {
        CardLabel("DRIVE")
        Spacer(Modifier.height(2.dp))

        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                DwmText(
                    drive.speedKmh?.toString() ?: "—",
                    // 46, not 54: three digits plus the unit has to survive the
                    // narrowest this zone gets, and maxLines=1 clips rather than wraps.
                    size = 46.sp,
                    color = Color.White,
                    weight = FontWeight.Light,
                    tabular = true
                )
                Spacer(Modifier.width(5.dp))
                DwmText("km/h", size = 11.sp, color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 10.dp))
            }
            Spacer(Modifier.height(9.dp))
            GearStrip(drive.gear, accent)
        }

        RpmBar(drive.rpm, accent)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StateChip("HOLD", drive.handbrake, WARN_C)
            StateChip("LIGHTS", drive.headlight, OK_C)
        }
    }
}

/** P R N D S, active one lit. Reads faster than a dial and costs a fifth of the
 *  space — the deck is a console screen, not an instrument binnacle. */
@Composable
private fun GearStrip(gear: Int?, accent: Color) {
    val active = gearIndex(gear)
    // Weights, not fixed widths: this zone is a third of a screen whose width we
    // don't know, and a clipped gear strip would be worse than a narrow one.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        GEARS.forEachIndexed { i, g ->
            val on = i == active
            Box(
                Modifier
                    .weight(1f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                DwmText(
                    g,
                    size = 12.sp,
                    color = if (on) Color.White else Color.White.copy(alpha = 0.35f),
                    weight = if (on) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun RpmBar(rpm: Int?, accent: Color) {
    val frac = ((rpm ?: 0) / 7000f).coerceIn(0f, 1f)
    val shown by animateFloatAsState(frac, tween(400), label = "rpm")
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DwmText("RPM", size = 8.sp, color = Color.White.copy(alpha = 0.45f))
            Spacer(Modifier.weight(1f))
            DwmText(rpm?.toString() ?: "—", size = 10.sp,
                color = Color.White.copy(alpha = 0.8f), tabular = true)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier.fillMaxWidth(shown).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (frac > 0.82f) WARN_C else accent)
            )
        }
    }
}

/** Null = the car has never said, which must not look like "off". */
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
        DwmText(if (on == null) "$label —" else label, size = 8.sp, color = fg, weight = FontWeight.Medium)
    }
}

/* ------------------------------------------------------------------- vitals */

@Composable
fun VitalsZone(v: VitalsState, accent: Color) {
    Column(Modifier.fillMaxSize()) {
        CardLabel("VITALS")
        Spacer(Modifier.height(9.dp))

        VitalBar("Coolant", v.coolant, 40f, 120f, fmt(v.coolant, "°", 0),
            if ((v.coolant ?: 0f) > 105f) WARN_C else accent)
        VitalBar("Fuel", v.fuel, 0f, 100f, fmt(v.fuel, "%", 0),
            if ((v.fuel ?: 100f) < 12f) CAUTION_C else accent)
        VitalBar("Battery", v.voltage, 10f, 15f, fmt(v.voltage, "V", 1),
            if (v.voltage != null && v.voltage < 12f) WARN_C else accent)

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DwmText("Steering", size = 9.sp, color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.weight(1f))
            val deg = steeringDegrees(v.track)
            DwmText(
                if (deg == null) "—" else "${if (deg > 0) "+" else ""}$deg°",
                size = 12.sp, color = Color.White.copy(alpha = 0.9f), tabular = true
            )
        }
    }
}

@Composable
private fun VitalBar(
    label: String,
    value: Float?,
    min: Float,
    max: Float,
    text: String,
    color: Color
) {
    val frac = if (value == null) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)
    val shown by animateFloatAsState(frac, tween(500), label = label)
    Column(Modifier.padding(bottom = 9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DwmText(label, size = 9.sp, color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.weight(1f))
            DwmText(
                text, size = 12.sp,
                color = if (value == null) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.92f),
                weight = FontWeight.Medium, tabular = true
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (value != null) {
                Box(
                    Modifier.fillMaxWidth(shown).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ vehicle */

@Composable
fun VehicleZone(body: BodyState, accent: Color) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardLabel(if (body.reverse) "VEHICLE · REVERSING" else "VEHICLE")
            Spacer(Modifier.weight(1f))
            val open = listOfNotNull(
                if (body.doorLF == true) "LF" else null,
                if (body.doorRF == true) "RF" else null,
                if (body.doorLR == true) "LR" else null,
                if (body.doorRR == true) "RR" else null,
                if (body.boot == true) "BOOT" else null
            )
            if (open.isNotEmpty()) {
                DwmText("OPEN: ${open.joinToString(" ")}", size = 9.sp,
                    color = WARN_C, weight = FontWeight.Medium)
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
 *  matters: a car that simply never reports must not look like a failed bind. */
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

/** Says out loud that the blanks are the car's doing, not a hung app. */
@Composable
fun AbsentChip(count: Int) {
    if (count <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        DwmText(
            "$count not on this car",
            size = 9.sp,
            color = Color.White.copy(alpha = 0.42f)
        )
    }
}

@Composable
fun AmbientReadout(head: HeadState) {
    if (head.ambient == null) return
    DwmText(
        fmt(head.ambient, head.ambientUnit ?: "°", 0),
        size = 12.sp,
        color = Color.White.copy(alpha = 0.75f),
        tabular = true
    )
}
