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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

/**
 * The Ranger with its parking sensors arranged around it.
 *
 * In a 3/4 view the sixteen sensors can no longer sit where they physically are —
 * that was the accepted trade for the render looking like a truck rather than a
 * rounded rectangle. They become banks of arcs instead: four behind, four in
 * front, and the eight flank sensors as slimmer arcs down each side. The rear bank
 * gets the most room, because reversing is the case that matters and the only one
 * confirmed working on this van.
 *
 * The van reports a one-element `[-1]` when the sensors are idle, so nothing is
 * drawn until all sixteen values are present — which is also what makes "sensors
 * connected but nothing detected" distinguishable from "no sensor data at all".
 */

private val WARN = Color(0xFFFF453A)
private val CAUTION = Color(0xFFFF9F0A)
private val OK = Color(0xFF34C759)

@Composable
fun VehicleView(body: BodyState, modifier: Modifier = Modifier) {
    // Only run a timer when something is actually blinking.
    val blinking = (body.turnSignal ?: 0) != 0
    var blinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(blinking) {
        if (!blinking) { blinkOn = true; return@LaunchedEffect }
        while (true) { blinkOn = !blinkOn; delay(420) }
    }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) { drawSensors(body.radar, body.reverse) }
        RangerRear(
            reverse = body.reverse,
            headlight = body.headlight == true,
            turnSignal = body.turnSignal ?: 0,
            blinkOn = blinkOn,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Sixteen sensors, front-left then clockwise: 0-3 nose, 4-7 right flank, 8-11 tail
 * right-to-left, 12-15 left flank. `0` means nothing detected; `1` is closest and
 * `11` furthest, so the colour ramp runs opposite to the number.
 */
private fun DrawScope.drawSensors(radar: List<Int>, reverse: Boolean) {
    if (radar.size < 16) return
    val w = size.width
    val h = size.height

    // rear bank — the wide fan below the tailgate, biggest because it matters most
    val rearSpan = w * 0.74f
    val rearX = (w - rearSpan) / 2f
    val segW = rearSpan / 4f
    val gap = segW * 0.10f
    for (i in 0 until 4) {
        // indices 8..11 run right-to-left, so mirror them into screen order
        arcBar(
            Offset(rearX + (3 - i) * segW + gap / 2, h * 0.885f),
            Size(segW - gap, h * 0.045f),
            radar[8 + i],
            reverse
        )
    }

    // front bank — narrower and dimmer; it sits beyond the cab, further from us
    val frontSpan = w * 0.46f
    val frontX = (w - frontSpan) / 2f
    val fSeg = frontSpan / 4f
    for (i in 0 until 4) {
        arcBar(
            Offset(frontX + i * fSeg + fSeg * 0.06f, h * 0.10f),
            Size(fSeg * 0.88f, h * 0.026f),
            radar[i],
            false
        )
    }

    // flanks — thin verticals hugging each side
    val sideH = h * 0.10f
    for (i in 0 until 4) {
        val y = h * 0.36f + i * (sideH * 1.12f)
        arcBar(Offset(w * 0.90f, y), Size(w * 0.035f, sideH), radar[4 + i], false)
        arcBar(Offset(w * 0.075f, h * 0.36f + (3 - i) * (sideH * 1.12f)), Size(w * 0.035f, sideH), radar[12 + i], false)
    }
}

private fun DrawScope.arcBar(at: Offset, sz: Size, v: Int, emphasise: Boolean) {
    val color = when {
        v <= 0 -> Color.White.copy(alpha = if (emphasise) 0.13f else 0.07f)
        v <= 3 -> WARN
        v <= 7 -> CAUTION
        else -> OK
    }
    val alpha = if (v <= 0) 1f else (0.45f + (12 - v).coerceIn(1, 11) / 11f * 0.55f)
    val r = sz.minDimension / 2f
    drawRoundRect(
        color = color.copy(alpha = color.alpha * alpha),
        topLeft = at,
        size = sz,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
    )
    // a faint outer ring on a live return, so a close object reads as a pulse
    if (v in 1..3) {
        drawRoundRect(
            color = color.copy(alpha = 0.20f),
            topLeft = Offset(at.x - r * 0.5f, at.y - r * 0.5f),
            size = Size(sz.width + r, sz.height + r),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 1.5f, r * 1.5f),
            style = Stroke(width = r * 0.5f)
        )
    }
}
