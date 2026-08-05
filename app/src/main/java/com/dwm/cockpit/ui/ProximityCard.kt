package com.dwm.cockpit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmRadius
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType

/**
 * The proximity display.
 *
 * **No vehicle is drawn.** Two attempts at depicting the Ranger were made and both
 * failed — a hand-drawn Canvas truck, then a photograph crop with road and lane
 * markings still in it, upscaled from a 206px source. A neutral footprint is a
 * better spatial reference than a bad picture of the actual truck, and it cannot go
 * out of date.
 *
 * Sensor ring, clockwise from the nose: `0-3` front, `4-7` right flank, `8-11` tail
 * (right to left), `12-15` left flank.
 *
 * ### The no-signal state
 *
 * There is no CAN data on this vehicle yet, so this card renders empty far more
 * often than it renders full, and the empty state is the one that had to be designed
 * first. It is: the footprint, in [Dwm.colors.hairline], with no sensors drawn at
 * all, and the status line reading NO SIGNAL in the same muted overline every other
 * label on the screen uses.
 *
 * Specifically **not** an orange NO SENSOR DATA badge — that was the old behaviour,
 * and a warning colour on a feature that is merely not connected yet trains the eye
 * to ignore the colour that means something is actually wrong. And specifically not
 * sixteen dim pills either: with no words, that looked identical to a dead feature
 * and was reported as one.
 */
@Composable
fun ProximityCard(body: BodyState, modifier: Modifier = Modifier) {
    val colors = Dwm.colors
    val live = body.radar.size >= 16
    val nearest = if (live) body.radar.filter { it > 0 }.minOrNull() else null

    val steer by animateFloatAsState(
        targetValue = (((body.track ?: 240) - 240) / 240f).coerceIn(-1f, 1f),
        animationSpec = DwmMotion.base,
        label = "steer"
    )

    val hairlinePx = with(LocalDensity.current) { DwmStroke.hairline.toPx() }
    val barPx = with(LocalDensity.current) { DwmSpace.s.toPx() }
    val radiusPx = with(LocalDensity.current) { DwmRadius.m.toPx() }

    DwmCard(modifier = modifier, padding = DwmSpace.l) {
        Column(Modifier.fillMaxSize()) {
            CardLabel("Proximity")
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawFootprint(colors.hairline, hairlinePx, radiusPx)
                    if (live) {
                        drawSensors(body.radar, barPx, colors)
                        drawGuides(steer, colors.accent, hairlinePx)
                    }
                }
            }
            Spacer(Modifier.height(DwmSpace.s))
            val (word, tint) = statusWord(live, nearest, colors)
            DwmText(
                word,
                style = DwmType.overline,
                color = tint,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun statusWord(
    live: Boolean,
    nearest: Int?,
    colors: com.dwm.cockpit.ui.theme.DwmColors
): Pair<String, Color> = when {
    !live -> "NO SIGNAL" to colors.muted
    nearest == null -> "ALL CLEAR" to colors.ok
    nearest <= 3 -> "STOP" to colors.critical
    nearest <= 7 -> "CLOSE" to colors.warn
    else -> "CLEAR" to colors.ok
}

/* Geometry as fractions of the canvas.
 *
 * The footprint is taller than it is wide, at roughly 1:1.7, because that is what a
 * vehicle looks like from above and the first render made it very nearly square. It
 * sits slightly high so the space behind the tailgate — the part actually being
 * reversed into — keeps the room, but not as high as it was: with no CAN signal
 * there are no guide curves to fill the lower third, and the card read as top-heavy
 * with a void under it. Empty space is fine; unbalanced empty space is not. */
private const val FX0 = 0.33f
private const val FX1 = 0.67f
private const val FY0 = 0.13f
private const val FY1 = 0.66f
private const val NOSE_Y = 0.085f
private const val TAIL_Y = 0.705f
private const val GUIDE_TOP = 0.77f
private const val GUIDE_BOTTOM = 0.99f

private fun DrawScope.drawFootprint(color: Color, stroke: Float, radius: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * FX0, size.height * FY0),
        size = Size(size.width * (FX1 - FX0), size.height * (FY1 - FY0)),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = stroke)
    )
}

/**
 * Distance as intensity, not as a number.
 *
 * The value is 0-15 with lower meaning closer. Colour ramps through ok, warn and
 * critical, and alpha rises as the obstacle nears, so proximity is legible in
 * peripheral vision without anyone reading a figure while reversing.
 */
private fun DrawScope.drawSensors(
    radar: List<Int>,
    bar: Float,
    colors: com.dwm.cockpit.ui.theme.DwmColors
) {
    val w = size.width
    val h = size.height

    fun tint(v: Int): Color? {
        if (v <= 0) return null
        val c = when {
            v <= 3 -> colors.critical
            v <= 7 -> colors.warn
            else -> colors.ok
        }
        return c.copy(alpha = (1f - (v / 16f)).coerceIn(0.35f, 1f))
    }

    fun pill(cx: Float, cy: Float, horizontal: Boolean, v: Int) {
        val c = tint(v) ?: return
        val long = bar * 2.6f
        val w0 = if (horizontal) long else bar
        val h0 = if (horizontal) bar else long
        drawRoundRect(
            color = c,
            topLeft = Offset(cx - w0 / 2f, cy - h0 / 2f),
            size = Size(w0, h0),
            cornerRadius = CornerRadius(bar / 2f, bar / 2f)
        )
    }

    // Front, 0-3, left to right.
    for (i in 0..3) {
        val t = (i + 0.5f) / 4f
        pill(w * (FX0 + (FX1 - FX0) * t), h * NOSE_Y, true, radar[i])
    }
    // Right flank, 4-7, front to back.
    for (i in 4..7) {
        val t = (i - 4 + 0.5f) / 4f
        pill(w * (FX1 + 0.035f), h * (FY0 + (FY1 - FY0) * t), false, radar[i])
    }
    // Tail, 8-11, right to left — mirrored so the display matches the mirror.
    for (i in 8..11) {
        val t = (11 - i + 0.5f) / 4f
        pill(w * (FX0 + (FX1 - FX0) * t), h * TAIL_Y, true, radar[i])
    }
    // Left flank, 12-15, back to front.
    for (i in 12..15) {
        val t = (15 - i + 0.5f) / 4f
        pill(w * (FX0 - 0.035f), h * (FY0 + (FY1 - FY0) * t), false, radar[i])
    }
}

/**
 * The steering-predicted corridor behind the tailgate.
 *
 * Two edges that widen with distance and swing with the wheel, plus two distance
 * ticks that **span between the edges** rather than floating beside them. The first
 * version drew short stubs that touched neither edge and read as rendering
 * artifacts rather than as a measurement.
 */
private fun DrawScope.drawGuides(steer: Float, accent: Color, stroke: Float) {
    val w = size.width
    val h = size.height
    val top = h * GUIDE_TOP
    val bottom = h * GUIDE_BOTTOM
    val drift = w * 0.20f * steer

    // Edge x at a given fraction down the corridor, as a quadratic in t. Ticks and
    // curves share it so they cannot disagree.
    fun edge(side: Float, t: Float): Float {
        val x0 = w * (0.5f + side * 0.11f)
        val x1 = w * (0.5f + side * 0.26f) + drift
        val ctrl = x0 + drift * 0.4f
        val u = 1f - t
        return u * u * x0 + 2f * u * t * ctrl + t * t * x1
    }

    listOf(-1f, 1f).forEach { side ->
        drawPath(
            path = Path().apply {
                moveTo(edge(side, 0f), top)
                quadraticBezierTo(
                    edge(side, 0.5f), (top + bottom) / 2f,
                    edge(side, 1f), bottom
                )
            },
            color = accent.copy(alpha = 0.75f),
            style = Stroke(width = stroke * 2f)
        )
    }

    // Distance ticks: near and far, each spanning the corridor at that point.
    listOf(0.42f to 0.5f, 0.82f to 0.3f).forEach { (t, alpha) ->
        val y = top + (bottom - top) * t
        drawLine(
            color = accent.copy(alpha = alpha),
            start = Offset(edge(-1f, t), y),
            end = Offset(edge(1f, t), y),
            strokeWidth = stroke * 2f
        )
    }
}
