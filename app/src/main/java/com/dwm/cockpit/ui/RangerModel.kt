package com.dwm.cockpit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dwm.cockpit.R

/**
 * The Ranger, as a real render.
 *
 * The first version of this drew the truck by hand in Canvas paths. It was ugly —
 * the user's word, and they were right; I had even flagged the risk and shipped it
 * anyway. Hand-drawn vector was never going to sit next to a photoreal reference
 * without looking like a prototype.
 *
 * So this uses the image instead: the rear 3/4 Ranger cropped out of the reference
 * screen the user supplied, with the overlaid PRND text painted out and the frame
 * trimmed. The tail lights are already lit in the render, so only the states the
 * photo cannot show are drawn on top — reverse lamps and indicators — and those are
 * drawn *beside* the truck rather than pixel-aligned onto it, because aligning
 * overlays to a cropped photo breaks the moment the image is ever swapped.
 *
 * Still no 3D engine: a live glTF renderer on the launcher home screen of a low-RAM
 * Unisoc is the same risk that got Lottie removed from this project, and a single
 * static render costs nothing per frame.
 */
@Composable
fun RangerRear(
    reverse: Boolean,
    headlight: Boolean,
    turnSignal: Int,
    blinkOn: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ranger_rear),
            contentDescription = "Ford Ranger",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
        )
        Canvas(Modifier.fillMaxSize()) {
            drawLamps(reverse, headlight, turnSignal, blinkOn)
        }
    }
}

private val AMBER = Color(0xFFFF9F0A)
private val REVERSE_LAMP = Color(0xFFF2F4F6)

/**
 * Only what the still image cannot say on its own. Every one of these maps to a
 * signal this van genuinely reports — reverse from the confirmed audio-duck
 * broadcast, indicators from `getTurn_Signal`. Nothing here is invented.
 */
private fun DrawScope.drawLamps(
    reverse: Boolean,
    headlight: Boolean,
    turnSignal: Int,
    blinkOn: Boolean
) {
    val w = size.width
    val h = size.height

    // Indicators: chevrons either side of the truck, the way a cluster shows them.
    val leftOn = blinkOn && (turnSignal == 2 || turnSignal == 3)
    val rightOn = blinkOn && (turnSignal == 1 || turnSignal == 3)
    val s = w * 0.045f
    chevron(Offset(w * 0.055f, h * 0.5f), -1f, s, if (leftOn) AMBER else Color.White.copy(alpha = 0.07f))
    chevron(Offset(w * 0.945f, h * 0.5f), 1f, s, if (rightOn) AMBER else Color.White.copy(alpha = 0.07f))

    // Reverse: a wash of white spilling out behind the tailgate.
    if (reverse) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to REVERSE_LAMP.copy(alpha = 0.30f),
                1f to Color.Transparent
            ),
            topLeft = Offset(w * 0.10f, h * 0.72f),
            size = Size(w * 0.62f, h * 0.22f),
            cornerRadius = CornerRadius(h * 0.05f, h * 0.05f)
        )
    }

    // Headlights are on the far side of the truck from this angle; a faint spill
    // past the cab is the only honest way to show them from behind.
    if (headlight) {
        drawRoundRect(
            color = Color(0xFFFFF3C4).copy(alpha = 0.07f),
            topLeft = Offset(w * 0.16f, h * 0.02f),
            size = Size(w * 0.52f, h * 0.05f),
            cornerRadius = CornerRadius(h * 0.03f, h * 0.03f)
        )
    }
}

private fun DrawScope.chevron(tip: Offset, dir: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(tip.x + dir * s, tip.y)
        lineTo(tip.x - dir * s * 0.30f, tip.y - s * 0.9f)
        lineTo(tip.x - dir * s * 0.30f, tip.y + s * 0.9f)
        close()
    }
    drawPath(p, color)
}
