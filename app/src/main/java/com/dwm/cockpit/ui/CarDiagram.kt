package com.dwm.cockpit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType
import com.dwm.cockpit.ui.theme.StatusCaution
import com.dwm.cockpit.ui.theme.StatusDanger
import com.dwm.cockpit.ui.theme.StatusOk

/**
 * The reversing display: sixteen parking sensors and a steering-predicted path.
 *
 * **No vehicle is drawn.** This replaces a rear-3/4 render of the Ranger that was
 * a rectangular crop of a photograph — road surface, lane markings and a visible
 * crop seam included — upscaled from a 206px source. It never sat right on a flat
 * dark UI, and the version before that was a hand-drawn Canvas truck that was
 * worse. Two attempts at drawing the vehicle failed; the third answer is to stop
 * trying. What is on screen here is only what the van actually measures.
 *
 * Dropping the render also let the sensors go where they physically are. The
 * indices are a clockwise ring — front-left, then round — so a plain top-down
 * footprint maps all sixteen one-to-one. The 3/4 view could not: it had to fake
 * them as banks of arcs sitting beside the truck, which is a diagram of nothing.
 *
 * ```
 *        ▁▁  ▁▁  ▁▁  ▁▁          0-3   nose, dimmer — behind you is what matters
 *      ┌────────────────┐
 *   15 │                │ 4
 *   14 │                │ 5      12-15 left flank · 4-7 right flank
 *   13 │                │ 6
 *   12 │                │ 7
 *      └────────────────┘
 *        ▀▀  ▀▀  ▀▀  ▀▀          8-11  tail, right-to-left, emphasised
 *       ╲              ╱
 *        ╲            ╱          steering-predicted path, from getTrack
 *           ALL CLEAR
 * ```
 *
 * The van reports a one-element `[-1]` when the sensors are idle, so nothing is
 * drawn until all sixteen values are present — which is what keeps "sensors
 * connected, nothing detected" distinguishable from "no sensor data at all".
 */
@Composable
fun ParkingDisplay(body: BodyState, modifier: Modifier = Modifier) {
    val colors = Dwm.colors
    val live = body.radar.size >= 16
    val nearest = if (live) body.radar.filter { it > 0 }.minOrNull() else null

    // Steering is the only reading on this van that moves with the engine off, so
    // the guide path doubles as proof the CAN link is alive: turn the wheel and
    // watch it swing.
    val steer = (((body.track ?: 240) - 240) / 240f).coerceIn(-1f, 1f)
    val shownSteer by animateFloatAsState(steer, DwmMotion.gauge, label = "guide")

    val footprint = colors.hairline
    val idle = colors.cardBorder

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawFootprint(footprint)
            if (live) drawSensors(body.radar, idle)
            drawGuides(shownSteer, colors.accent)
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = DwmSpace.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            val status = parkingWord(live, nearest)
            DwmText(status.first, size = DwmType.title, color = status.second, weight = FontWeight.Bold)
            if (nearest != null) {
                DwmText(
                    "nearest ${nearest}",
                    size = DwmType.micro,
                    color = colors.textTertiary,
                    tabular = true
                )
            }
        }
    }
}

/** Sixteen values present and all zero means nothing detected, which has to read
 *  as ALL CLEAR. Dim pills with no words looked identical to a dead feature and
 *  were reported as one. */
private fun parkingWord(live: Boolean, nearest: Int?): Pair<String, Color> = when {
    !live -> "NO SENSOR DATA" to StatusCaution
    nearest == null -> "ALL CLEAR" to StatusOk
    nearest <= 3 -> "STOP" to StatusDanger
    nearest <= 7 -> "CLOSE" to StatusCaution
    else -> "CLEAR" to StatusOk
}

/* Geometry, as fractions of the canvas. The footprint sits high so the space
 * behind the tailgate — the part you are actually reversing into — gets the room. */
private const val FX0 = 0.30f
private const val FX1 = 0.70f
private const val FY0 = 0.12f
private const val FY1 = 0.52f
private const val NOSE_Y = 0.055f
private const val TAIL_Y = 0.565f
private const val GUIDE_TOP = 0.62f
private const val GUIDE_BOTTOM = 0.84f

/** A neutral outline standing in for the vehicle — spatial reference only, not a
 *  drawing of a truck. Deliberately plain: the last two attempts at depicting the
 *  Ranger both failed, and nothing here needs it to be recognisable. */
private fun DrawScope.drawFootprint(color: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * FX0, h * FY0),
        size = Size(w * (FX1 - FX0), h * (FY1 - FY0)),
        cornerRadius = CornerRadius(w * 0.035f, w * 0.035f),
        style = Stroke(width = w * 0.006f)
    )
}

/**
 * Front-left then clockwise: 0-3 nose, 4-7 right flank, 8-11 tail right-to-left,
 * 12-15 left flank back-to-front. `0` means nothing detected; `1` is closest and
 * `11` furthest, so the colour ramp runs opposite to the number.
 */
private fun DrawScope.drawSensors(radar: List<Int>, idle: Color) {
    val w = size.width
    val h = size.height
    val span = w * (FX1 - FX0)
    val seg = span / 4f
    val gap = seg * 0.16f

    // nose — dimmer and thinner; when reversing it is not what you are watching
    for (i in 0 until 4) {
        bar(
            Offset(w * FX0 + i * seg + gap / 2f, h * NOSE_Y),
            Size(seg - gap, h * 0.020f),
            radar[i], idle, emphasis = false
        )
    }

    // tail — indices run right-to-left, so mirror them into screen order
    for (i in 0 until 4) {
        bar(
            Offset(w * FX0 + (3 - i) * seg + gap / 2f, h * TAIL_Y),
            Size(seg - gap, h * 0.034f),
            radar[8 + i], idle, emphasis = true
        )
    }

    // flanks — thin verticals hugging each side of the footprint
    val vSpan = h * (FY1 - FY0)
    val vSeg = vSpan / 4f
    val vGap = vSeg * 0.18f
    for (i in 0 until 4) {
        bar(
            Offset(w * (FX1 + 0.035f), h * FY0 + i * vSeg + vGap / 2f),
            Size(w * 0.020f, vSeg - vGap),
            radar[4 + i], idle, emphasis = false
        )
        // 12-15 run tail-to-nose, so the last index sits nearest the front
        bar(
            Offset(w * (FX0 - 0.055f), h * FY0 + (3 - i) * vSeg + vGap / 2f),
            Size(w * 0.020f, vSeg - vGap),
            radar[12 + i], idle, emphasis = false
        )
    }
}

private fun DrawScope.bar(at: Offset, sz: Size, v: Int, idle: Color, emphasis: Boolean) {
    val color = when {
        v <= 0 -> if (emphasis) idle else idle.copy(alpha = idle.alpha * 0.6f)
        v <= 3 -> StatusDanger
        v <= 7 -> StatusCaution
        else -> StatusOk
    }
    // Nearer returns burn brighter, so a closing object reads as intensity as well
    // as colour — colour alone is the first thing to wash out on this panel.
    val a = if (v <= 0) 1f else (0.45f + (12 - v).coerceIn(1, 11) / 11f * 0.55f)
    val r = sz.minDimension / 2f
    drawRoundRect(
        color = color.copy(alpha = color.alpha * a),
        topLeft = at,
        size = sz,
        cornerRadius = CornerRadius(r, r)
    )
    if (v in 1..3) {
        drawRoundRect(
            color = color.copy(alpha = 0.22f),
            topLeft = Offset(at.x - r, at.y - r),
            size = Size(sz.width + r * 2f, sz.height + r * 2f),
            cornerRadius = CornerRadius(r * 2f, r * 2f),
            style = Stroke(width = r * 0.6f)
        )
    }
}

/**
 * The predicted path, swung by the steering angle.
 *
 * This is what a real reversing display draws, and it is affordable here for one
 * reason: `getTrack` and the radar are two of the six signals this van genuinely
 * reports. Nothing on this screen is inferred or invented.
 */
private fun DrawScope.drawGuides(steer: Float, accent: Color) {
    val w = size.width
    val h = size.height
    val yTop = h * GUIDE_TOP
    val yBottom = h * GUIDE_BOTTOM
    // Full lock walks the far end of the corridor about a fifth of the width across.
    val drift = steer * w * 0.20f
    val spread = w * 0.06f      // the corridor widens with distance

    for (side in listOf(-1f, 1f)) {
        val x0 = w * (if (side < 0) FX0 else FX1)
        val x1 = x0 + drift + side * spread
        val path = Path().apply {
            moveTo(x0, yTop)
            quadraticTo(x0 + drift * 0.35f, (yTop + yBottom) / 2f, x1, yBottom)
        }
        drawPath(
            path,
            color = accent.copy(alpha = 0.40f),
            style = Stroke(width = w * 0.010f, cap = StrokeCap.Round)
        )
    }

    // Two distance ticks, so the corridor reads as depth rather than as two lines.
    for (t in listOf(0.38f, 0.74f)) {
        val y = yTop + (yBottom - yTop) * t
        val dx = drift * (0.35f + t * 0.65f)
        val half = w * (FX1 - FX0) / 2f + spread * t
        val cx = w * 0.5f + dx
        drawLine(
            color = accent.copy(alpha = 0.16f),
            start = Offset(cx - half, y),
            end = Offset(cx + half, y),
            strokeWidth = w * 0.006f,
            cap = StrokeCap.Round
        )
    }
}
