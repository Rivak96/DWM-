package com.dwm.cockpit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A Ford Ranger seen from behind and slightly above — the pose in the reference
 * screen the user supplied.
 *
 * Drawn entirely in Canvas paths. No bitmap, no glTF, no 3D engine: this runs on
 * the home screen of a launcher on a low-RAM Unisoc SC9863A, and a real-time
 * renderer there is the same class of risk that got Lottie removed from this
 * project. A Sketchfab model was offered and declined for that reason plus an
 * unverifiable licence on a public repo.
 *
 * It is stylised, not photoreal — the honest ceiling of drawing a truck in code.
 * If it ever needs to be a render instead, everything below is replaceable by one
 * `Image()` and the lamps drawn on top, without touching the rest of the screen.
 *
 * Only lamps that map to a real signal are lit: tail lights whenever the deck is
 * awake, reverse lamps on the confirmed reverse broadcast, indicators on
 * `getTurn_Signal`. There is no door or tailgate animation because every door
 * getter on this van returns -1, and inventing one is how the last four releases
 * went wrong.
 */
@Composable
fun RangerRear(
    reverse: Boolean,
    headlight: Boolean,
    turnSignal: Int,
    blinkOn: Boolean,
    modifier: Modifier = Modifier
) {
    // Allocated once and reused — this repaints behind a 4Hz data tick.
    val body = remember { Path() }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawRanger(body, reverse, headlight, turnSignal, blinkOn)
        }
        // Tailgate lettering as real text — path-lettering at this size turns to
        // mush, and the badge is what makes it read as a Ranger rather than a box.
        DwmText(
            "R A N G E R",
            size = (h.value * 0.055f).coerceIn(6f, 13f).sp,
            color = Color.White.copy(alpha = 0.38f),
            weight = FontWeight.Medium,
            modifier = Modifier.offset(y = h * 0.16f)
        )
    }
}

private val TAIL = Color(0xFFE02020)
private val REVERSE_LAMP = Color(0xFFF2F4F6)
private val AMBER = Color(0xFFFF9F0A)

private fun DrawScope.drawRanger(
    path: Path,
    reverse: Boolean,
    headlight: Boolean,
    turnSignal: Int,
    blinkOn: Boolean
) {
    val w = size.width
    val h = size.height
    // Everything below is expressed as a fraction of the box, so the truck scales
    // with the column instead of needing a fixed dp size.
    fun x(f: Float) = w * f
    fun y(f: Float) = h * f

    // ---- contact shadow
    drawOval(
        brush = Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.55f),
            1f to Color.Transparent
        ),
        topLeft = Offset(x(0.10f), y(0.80f)),
        size = Size(x(0.80f), y(0.20f))
    )

    // ---- cab roof, receding away from us (narrower and higher)
    path.reset()
    path.moveTo(x(0.34f), y(0.30f))
    path.lineTo(x(0.66f), y(0.30f))
    path.lineTo(x(0.71f), y(0.45f))
    path.lineTo(x(0.29f), y(0.45f))
    path.close()
    drawPath(
        path,
        brush = Brush.verticalGradient(
            0f to Color(0xFF3A3D42),
            1f to Color(0xFF232629),
            startY = y(0.30f), endY = y(0.45f)
        )
    )
    // rear glass
    path.reset()
    path.moveTo(x(0.37f), y(0.33f))
    path.lineTo(x(0.63f), y(0.33f))
    path.lineTo(x(0.66f), y(0.43f))
    path.lineTo(x(0.34f), y(0.43f))
    path.close()
    drawPath(path, color = Color(0xFF10151A))

    // ---- bed / body sides, flaring out toward us
    path.reset()
    path.moveTo(x(0.29f), y(0.45f))
    path.lineTo(x(0.71f), y(0.45f))
    path.lineTo(x(0.80f), y(0.62f))
    path.lineTo(x(0.20f), y(0.62f))
    path.close()
    drawPath(
        path,
        brush = Brush.verticalGradient(
            0f to Color(0xFF34383D),
            1f to Color(0xFF1E2226),
            startY = y(0.45f), endY = y(0.62f)
        )
    )

    // ---- tailgate: the face pointed at us
    val gateT = y(0.55f)
    val gateB = y(0.76f)
    path.reset()
    path.moveTo(x(0.20f), gateT)
    path.lineTo(x(0.80f), gateT)
    path.lineTo(x(0.82f), gateB)
    path.lineTo(x(0.18f), gateB)
    path.close()
    drawPath(
        path,
        brush = Brush.verticalGradient(
            0f to Color(0xFF2E3237),
            0.5f to Color(0xFF23272B),
            1f to Color(0xFF171A1D),
            startY = gateT, endY = gateB
        )
    )
    // top crease along the tailgate — the line that sells it as sheet metal
    drawLine(
        Color.White.copy(alpha = 0.14f),
        Offset(x(0.21f), gateT + 1f), Offset(x(0.79f), gateT + 1f),
        strokeWidth = (h * 0.006f).coerceAtLeast(1f)
    )

    // ---- tail lights, one each side of the tailgate
    val lampW = x(0.11f)
    val lampH = h * 0.085f
    val lampY = gateT + h * 0.025f
    for (lx in listOf(x(0.205f), x(0.795f) - lampW)) {
        drawRoundRect(
            color = TAIL.copy(alpha = 0.9f),
            topLeft = Offset(lx, lampY),
            size = Size(lampW, lampH),
            cornerRadius = CornerRadius(lampW * 0.28f, lampW * 0.28f)
        )
        // glow
        drawRoundRect(
            color = TAIL.copy(alpha = 0.22f),
            topLeft = Offset(lx - lampW * 0.16f, lampY - lampH * 0.16f),
            size = Size(lampW * 1.32f, lampH * 1.32f),
            cornerRadius = CornerRadius(lampW * 0.4f, lampW * 0.4f)
        )
    }

    // ---- indicators: outer edge of each cluster, only when actually signalling
    val leftOn = blinkOn && (turnSignal == 2 || turnSignal == 3)
    val rightOn = blinkOn && (turnSignal == 1 || turnSignal == 3)
    if (leftOn) lampFlash(Offset(x(0.205f), lampY), Size(lampW, lampH), AMBER)
    if (rightOn) lampFlash(Offset(x(0.795f) - lampW, lampY), Size(lampW, lampH), AMBER)

    // ---- reverse lamps, from the one gear signal this van genuinely reports
    if (reverse) {
        val rw = x(0.075f)
        val rh = h * 0.028f
        val ry = gateB - rh - h * 0.02f
        for (rx in listOf(x(0.335f), x(0.665f) - rw)) {
            drawRoundRect(
                color = REVERSE_LAMP,
                topLeft = Offset(rx, ry),
                size = Size(rw, rh),
                cornerRadius = CornerRadius(rh, rh)
            )
            drawRoundRect(
                color = REVERSE_LAMP.copy(alpha = 0.20f),
                topLeft = Offset(rx - rw * 0.25f, ry - rh * 0.6f),
                size = Size(rw * 1.5f, rh * 2.2f),
                cornerRadius = CornerRadius(rh * 2, rh * 2)
            )
        }
    }

    // ---- rear bumper
    drawRoundRect(
        color = Color(0xFF2A2E33),
        topLeft = Offset(x(0.17f), gateB),
        size = Size(x(0.66f), h * 0.055f),
        cornerRadius = CornerRadius(h * 0.014f, h * 0.014f)
    )

    // ---- wheels, just the visible bottom arcs
    for (wx in listOf(x(0.215f), x(0.785f))) {
        drawRoundRect(
            color = Color(0xFF0E1113),
            topLeft = Offset(wx - x(0.045f), y(0.63f)),
            size = Size(x(0.09f), h * 0.18f),
            cornerRadius = CornerRadius(x(0.03f), x(0.03f))
        )
    }

    // ---- headlight wash spilling past the cab, when the lights are on
    if (headlight) {
        drawRoundRect(
            color = Color(0xFFFFF3C4).copy(alpha = 0.10f),
            topLeft = Offset(x(0.24f), y(0.235f)),
            size = Size(x(0.52f), h * 0.05f),
            cornerRadius = CornerRadius(h * 0.03f, h * 0.03f)
        )
    }

    // ---- body outline, to lift it off a dark background
    path.reset()
    path.moveTo(x(0.34f), y(0.30f))
    path.lineTo(x(0.66f), y(0.30f))
    path.lineTo(x(0.71f), y(0.45f))
    path.lineTo(x(0.80f), y(0.62f))
    path.lineTo(x(0.82f), gateB)
    path.lineTo(x(0.18f), gateB)
    path.lineTo(x(0.20f), y(0.62f))
    path.lineTo(x(0.29f), y(0.45f))
    path.close()
    drawPath(path, color = Color.White.copy(alpha = 0.10f), style = Stroke(width = (h * 0.004f).coerceAtLeast(1f)))
}

private fun DrawScope.lampFlash(at: Offset, sz: Size, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = at,
        size = sz,
        cornerRadius = CornerRadius(sz.width * 0.28f, sz.width * 0.28f)
    )
    drawRoundRect(
        color = color.copy(alpha = 0.25f),
        topLeft = Offset(at.x - sz.width * 0.2f, at.y - sz.height * 0.2f),
        size = Size(sz.width * 1.4f, sz.height * 1.4f),
        cornerRadius = CornerRadius(sz.width * 0.4f, sz.width * 0.4f)
    )
}
