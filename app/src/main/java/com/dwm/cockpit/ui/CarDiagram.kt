package com.dwm.cockpit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

/**
 * The van, from above, with its parking sensors around it.
 *
 * This drawing used to carry nine signals. The getter dump of 2026-07-30 settled
 * what this vehicle actually provides: every profile-indexed reading returns -1
 * permanently — no doors, no boot, no seatbelts, no tyre pressures, no gear. So
 * the door outlines, seat marks and TPMS corners are gone; drawing them was
 * drawing a promise the van cannot keep.
 *
 * What is left is real and worth the space: sixteen radar sensors, the
 * indicators, the headlights, and the steering trace. Parking is the one job this
 * van gives DWM enough data to do properly, so this is now a parking display.
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
    }
}

/* ------------------------------------------------------------------ drawing */

private fun DrawScope.drawBody(b: BodyState, accent: Color, blinkOn: Boolean) {
    val w = size.width
    val h = size.height
    val bw = w * 0.30f
    val bh = h * 0.56f
    val l = (w - bw) / 2f
    val t = (h - bh) / 2f
    val r = l + bw
    val bottom = t + bh
    val hair = Color.White.copy(alpha = 0.34f)
    val stroke = (w * 0.004f).coerceIn(1.2f, 3f)

    // shell
    drawRoundRect(
        color = hair,
        topLeft = Offset(l, t),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(bw * 0.30f, bw * 0.30f),
        style = Stroke(width = stroke)
    )
    // windscreen + rear glass, so the orientation reads at a glance
    val inset = bw * 0.16f
    drawLine(hair, Offset(l + inset, t + bh * 0.24f), Offset(r - inset, t + bh * 0.24f), stroke * 0.8f)
    drawLine(hair, Offset(l + inset, bottom - bh * 0.22f), Offset(r - inset, bottom - bh * 0.22f), stroke * 0.8f)

    // headlights — two lamps at the nose. The single wide bar this replaced read
    // as a stray beige blob floating above the van rather than as lights.
    if (b.headlight == true) {
        val lampW = bw * 0.26f
        val lampH = h * 0.014f
        for (lx in listOf(l + bw * 0.10f, r - bw * 0.10f - lampW)) {
            drawRoundRect(
                color = Color(0xFFFFF3C4).copy(alpha = 0.85f),
                topLeft = Offset(lx, t - lampH * 0.5f),
                size = Size(lampW, lampH),
                cornerRadius = CornerRadius(lampH, lampH)
            )
        }
    }

    // wheels
    val wheelW = bw * 0.12f
    val wheelH = bh * 0.16f
    for (wx in listOf(l - wheelW * 0.5f, r - wheelW * 0.5f)) {
        for (wy in listOf(t + bh * 0.16f, t + bh * 0.66f)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.20f),
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
    arrow(Offset(l - w * 0.075f, h / 2f), -1f, w * 0.032f, if (leftOn) CAUTION else Color.White.copy(alpha = 0.09f))
    arrow(Offset(r + w * 0.075f, h / 2f), 1f, w * 0.032f, if (rightOn) CAUTION else Color.White.copy(alpha = 0.09f))

    if (b.reverse) {
        drawRoundRect(
            color = accent.copy(alpha = 0.22f),
            topLeft = Offset(l, bottom + h * 0.012f),
            size = Size(bw, h * 0.03f),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }
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
 *
 * The van reports a one-element `[-1]` when the sensors are idle, which is why
 * this needs all sixteen before it will draw anything.
 */
private fun DrawScope.drawRadar(radar: List<Int>, reverse: Boolean) {
    if (radar.size < 16) return
    val w = size.width
    val h = size.height
    val bw = w * 0.30f
    val bh = h * 0.56f
    val l = (w - bw) / 2f
    val t = (h - bh) / 2f
    val span = bw * 1.15f
    val x0 = (w - span) / 2f
    val segW = span / 4f
    val gap = segW * 0.12f

    // Chunky enough to read as instrumentation from the driver's seat rather than
    // as decoration — the thin version was mistaken for a dead feature.
    val endH = h * 0.055f
    for (i in 0 until 4) {
        // nose
        bar(
            Offset(x0 + i * segW + gap / 2, t - h * 0.155f), Size(segW - gap, endH),
            radar[i], false
        )
        // tail — drawn right-to-left, which is what indices 8..11 mean
        bar(
            Offset(x0 + (3 - i) * segW + gap / 2, t + bh + h * 0.10f), Size(segW - gap, endH),
            radar[8 + i], reverse
        )
    }
    val sideH = bh * 0.19f
    val sideW = w * 0.022f
    for (i in 0 until 4) {
        val y = t + bh * 0.10f + i * (sideH * 1.18f)
        bar(Offset(l + bw + w * 0.030f, y), Size(sideW, sideH), radar[4 + i], false)
        bar(
            Offset(l - w * 0.030f - sideW, t + bh * 0.10f + (3 - i) * (sideH * 1.18f)),
            Size(sideW, sideH), radar[12 + i], false
        )
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
 * Reversing guides, bent by the steering trace (0..480, centre 240) — one of the
 * six readings this van genuinely provides, and it moves with the wheel.
 * The [Path] is reused across frames rather than reallocated.
 */
private fun DrawScope.drawTrace(path: Path, track: Int?, accent: Color) {
    val t = track ?: return
    val w = size.width
    val h = size.height
    val bw = w * 0.30f
    val bh = h * 0.56f
    val top = (h - bh) / 2f + bh
    val bend = ((t - 240) / 240f).coerceIn(-1f, 1f) * w * 0.11f
    val len = h * 0.20f
    val stroke = (w * 0.005f).coerceIn(1.5f, 3.5f)

    for (side in listOf(-1f, 1f)) {
        val x = w / 2f + side * bw * 0.42f
        path.reset()
        path.moveTo(x, top)
        path.quadraticBezierTo(
            x + bend * 0.5f, top + len * 0.55f,
            x + bend + side * bw * 0.12f, top + len
        )
        drawPath(path, color = accent.copy(alpha = 0.6f), style = Stroke(width = stroke))
    }
}
