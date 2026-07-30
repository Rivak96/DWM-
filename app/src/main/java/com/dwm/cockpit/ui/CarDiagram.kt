package com.dwm.cockpit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The car, from above.
 *
 * One drawing carries nine separate signals — four doors, the boot, both belts,
 * the headlights, the indicators, four tyre pressures and sixteen radar sensors —
 * because that is far and away the cheapest way to spend the middle of the screen.
 * Read as a picture it is also the only layout where "which door is open" needs no
 * label at all.
 *
 * Everything degrades to a dim outline when a signal has never reported, so a car
 * that tells us nothing renders as a plain grey car rather than a broken widget.
 */

private val WARN = Color(0xFFFF453A)
private val CAUTION = Color(0xFFFF9F0A)
private val OK = Color(0xFF34C759)

@Composable
fun CarDiagram(body: BodyState, accent: Color, modifier: Modifier = Modifier) {
    // Only run a timer when something is actually blinking.
    val blinking = (body.turnSignal ?: 0) != 0
    var blinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(blinking) {
        if (!blinking) { blinkOn = true; return@LaunchedEffect }
        while (true) { blinkOn = !blinkOn; delay(420) }
    }

    val trace = remember { Path() }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRadar(body.radar, body.reverse)
            if (body.reverse) drawTrace(trace, body.track, accent)
            drawBody(body, accent, blinkOn)
        }

        // Tyre readouts sit outside the wheels. Laid out with weights rather than
        // absolute offsets so they track the drawing at any size or UI scale.
        Row(Modifier.fillMaxSize()) {
            TyreColumn(body.tyre(0), body.tyre(2), Alignment.End, Modifier.weight(1f))
            Spacer(Modifier.weight(1.15f))
            TyreColumn(body.tyre(1), body.tyre(3), Alignment.Start, Modifier.weight(1f))
        }
    }
}

private fun BodyState.tyre(i: Int): TyreState? = tyres.getOrNull(i)

@Composable
private fun TyreColumn(
    front: TyreState?,
    rear: TyreState?,
    align: Alignment.Horizontal,
    modifier: Modifier
) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = align) {
        Spacer(Modifier.weight(0.22f))
        TyreLabel(front, align)
        Spacer(Modifier.weight(0.40f))
        TyreLabel(rear, align)
        Spacer(Modifier.weight(0.26f))
    }
}

@Composable
private fun TyreLabel(t: TyreState?, align: Alignment.Horizontal) {
    val warned = (t?.warn ?: 0) != 0
    Column(horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DwmText(
                t?.label ?: "—",
                size = 8.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
            DwmText(
                "  " + (t?.pressure?.let { "%.0f".format(it) } ?: "—"),
                size = 15.sp,
                color = if (warned) WARN else Color.White.copy(alpha = 0.92f),
                weight = FontWeight.Medium,
                tabular = true
            )
        }
        DwmText(
            when {
                t == null -> ""
                warned -> tyreWarning(t.warn)
                t.temp != null -> "%.0f°".format(t.temp)
                else -> t.pressureUnit ?: ""
            },
            size = 8.sp,
            color = if (warned) WARN.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f)
        )
    }
}

/** Vendor coding: 1 pressure, 2 temperature, 3 sensor. */
private fun tyreWarning(type: Int?): String = when (type) {
    1 -> "PRESSURE"
    2 -> "TEMP"
    3 -> "SENSOR"
    else -> "WARN"
}

/* ------------------------------------------------------------------ drawing */

private fun DrawScope.drawBody(b: BodyState, accent: Color, blinkOn: Boolean) {
    val w = size.width
    val h = size.height
    val bw = w * 0.30f
    val bh = h * 0.60f
    val l = (w - bw) / 2f
    val t = (h - bh) / 2f
    val r = l + bw
    val bottom = t + bh
    val hair = Color.White.copy(alpha = 0.30f)
    val stroke = (w * 0.004f).coerceIn(1.2f, 3f)

    // shell
    drawRoundRect(
        color = hair,
        topLeft = Offset(l, t),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(bw * 0.30f, bw * 0.30f),
        style = Stroke(width = stroke)
    )
    // windscreen + rear glass, purely to make the orientation readable
    val inset = bw * 0.16f
    drawLine(hair, Offset(l + inset, t + bh * 0.22f), Offset(r - inset, t + bh * 0.22f), stroke * 0.8f)
    drawLine(hair, Offset(l + inset, bottom - bh * 0.20f), Offset(r - inset, bottom - bh * 0.20f), stroke * 0.8f)

    // headlights — a soft wash at the nose when they are on
    if (b.headlight == true) {
        drawRoundRect(
            color = Color(0xFFFFF3C4).copy(alpha = 0.16f),
            topLeft = Offset(l, t - h * 0.055f),
            size = Size(bw, h * 0.06f),
            cornerRadius = CornerRadius(bw * 0.2f, bw * 0.2f)
        )
    }

    // doors: two segments a side, plus the boot across the tail
    doorLine(Offset(l, t + bh * 0.26f), Offset(l, t + bh * 0.50f), b.doorLF, stroke)
    doorLine(Offset(l, t + bh * 0.52f), Offset(l, t + bh * 0.76f), b.doorLR, stroke)
    doorLine(Offset(r, t + bh * 0.26f), Offset(r, t + bh * 0.50f), b.doorRF, stroke)
    doorLine(Offset(r, t + bh * 0.52f), Offset(r, t + bh * 0.76f), b.doorRR, stroke)
    doorLine(Offset(l + inset, bottom), Offset(r - inset, bottom), b.boot, stroke)

    // belts: the two front seats
    seat(Offset(l + bw * 0.30f, t + bh * 0.40f), bw * 0.17f, b.beltDriver)
    seat(Offset(l + bw * 0.70f, t + bh * 0.40f), bw * 0.17f, b.beltPassenger)

    // wheels
    val wheelW = bw * 0.11f
    val wheelH = bh * 0.15f
    for (wx in listOf(l - wheelW * 0.5f, r - wheelW * 0.5f)) {
        for (wy in listOf(t + bh * 0.16f, t + bh * 0.68f)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(wx, wy),
                size = Size(wheelW, wheelH),
                cornerRadius = CornerRadius(wheelW * 0.4f, wheelW * 0.4f)
            )
        }
    }

    // indicators
    val turn = b.turnSignal ?: 0
    val leftOn = blinkOn && (turn == 2 || turn == 3)
    val rightOn = blinkOn && (turn == 1 || turn == 3)
    arrow(Offset(l - w * 0.055f, h / 2f), -1f, w * 0.030f, if (leftOn) CAUTION else Color.White.copy(alpha = 0.10f))
    arrow(Offset(r + w * 0.055f, h / 2f), 1f, w * 0.030f, if (rightOn) CAUTION else Color.White.copy(alpha = 0.10f))

    if (b.reverse) {
        drawRoundRect(
            color = accent.copy(alpha = 0.20f),
            topLeft = Offset(l, bottom + h * 0.01f),
            size = Size(bw, h * 0.035f),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }
}

/** Unknown stays a hairline; open is the only thing that shouts. */
private fun DrawScope.doorLine(a: Offset, b: Offset, open: Boolean?, stroke: Float) {
    val color = when (open) {
        true -> WARN
        false -> OK.copy(alpha = 0.45f)
        null -> Color.White.copy(alpha = 0.12f)
    }
    drawLine(color, a, b, if (open == true) stroke * 2.4f else stroke * 1.3f)
}

private fun DrawScope.seat(centre: Offset, r: Float, belted: Boolean?) {
    val color = when (belted) {
        true -> OK.copy(alpha = 0.55f)
        false -> WARN.copy(alpha = 0.85f)
        null -> Color.White.copy(alpha = 0.13f)
    }
    drawRoundRect(
        color = color,
        topLeft = Offset(centre.x - r, centre.y - r),
        size = Size(r * 2, r * 2.3f),
        cornerRadius = CornerRadius(r * 0.5f, r * 0.5f),
        style = Stroke(width = r * 0.34f)
    )
}

private fun DrawScope.arrow(tip: Offset, dir: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(tip.x + dir * s, tip.y)
        lineTo(tip.x - dir * s * 0.35f, tip.y - s * 0.85f)
        lineTo(tip.x - dir * s * 0.35f, tip.y + s * 0.85f)
        close()
    }
    drawPath(p, color)
}

/**
 * Sixteen sensors, front-left then clockwise: 0-3 across the nose, 4-7 down the
 * right flank, 8-11 across the tail right-to-left, 12-15 up the left flank. `0`
 * means nothing detected; `1` is closest and `11` furthest, so the colour ramp
 * runs the opposite way to the number.
 */
private fun DrawScope.drawRadar(radar: List<Int>, reverse: Boolean) {
    if (radar.size < 16) return
    val w = size.width
    val h = size.height
    val bw = w * 0.30f
    val bh = h * 0.60f
    val l = (w - bw) / 2f
    val t = (h - bh) / 2f
    val span = bw * 1.10f
    val x0 = (w - span) / 2f
    val segW = span / 4f
    val gap = segW * 0.12f

    for (i in 0 until 4) {
        // nose
        bar(
            Offset(x0 + i * segW + gap / 2, t - h * 0.115f), Size(segW - gap, h * 0.030f),
            radar[i], false
        )
        // tail — drawn right-to-left, which is what indices 8..11 mean
        bar(
            Offset(x0 + (3 - i) * segW + gap / 2, t + bh + h * 0.085f), Size(segW - gap, h * 0.030f),
            radar[8 + i], reverse
        )
    }
    val sideH = bh * 0.20f
    for (i in 0 until 4) {
        val y = t + bh * 0.12f + i * (sideH * 1.15f)
        bar(Offset(l + bw + w * 0.018f, y), Size(w * 0.011f, sideH), radar[4 + i], false)
        bar(Offset(l - w * 0.029f, t + bh * 0.12f + (3 - i) * (sideH * 1.15f)), Size(w * 0.011f, sideH), radar[12 + i], false)
    }
}

private fun DrawScope.bar(at: Offset, sz: Size, v: Int, emphasise: Boolean) {
    val color = when {
        v <= 0 -> Color.White.copy(alpha = if (emphasise) 0.10f else 0.06f)
        v <= 3 -> WARN
        v <= 7 -> CAUTION
        else -> OK
    }
    val alpha = if (v <= 0) 1f else (0.45f + (12 - v).coerceIn(1, 11) / 11f * 0.55f)
    drawRoundRect(
        color = color.copy(alpha = color.alpha * alpha),
        topLeft = at,
        size = sz,
        cornerRadius = CornerRadius(sz.minDimension * 0.5f, sz.minDimension * 0.5f)
    )
}

/**
 * Reversing guides, bent by the steering trace (0..480, centre 240). Two curves
 * behind the car, the way every factory reversing camera draws them. The [Path]
 * is reused across frames — this runs at 4Hz behind a moving wheel.
 */
private fun DrawScope.drawTrace(path: Path, track: Int?, accent: Color) {
    val t = track ?: return
    val w = size.width
    val h = size.height
    val bw = w * 0.30f
    val bh = h * 0.60f
    val top = (h - bh) / 2f + bh
    val bend = ((t - 240) / 240f).coerceIn(-1f, 1f) * w * 0.10f
    val len = h * 0.16f
    val stroke = (w * 0.005f).coerceIn(1.5f, 3.5f)

    for (side in listOf(-1f, 1f)) {
        val x = w / 2f + side * bw * 0.42f
        path.reset()
        path.moveTo(x, top)
        path.quadraticBezierTo(
            x + bend * 0.5f, top + len * 0.55f,
            x + bend + side * bw * 0.10f, top + len
        )
        drawPath(
            path,
            color = accent.copy(alpha = 0.55f - abs(bend) / w * 0.6f + 0.15f),
            style = Stroke(width = stroke)
        )
    }
}
