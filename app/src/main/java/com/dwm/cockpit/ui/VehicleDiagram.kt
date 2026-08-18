package com.dwm.cockpit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmColors
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmRadius
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType
import java.util.Locale

/**
 * The vehicle state diagram — doors, tyres and parking sensors in one schematic.
 *
 * ### This is not the Ranger render, and must not become it
 *
 * Two previous attempts to depict this truck failed and were deleted: a hand-drawn
 * Canvas truck, then a rectangular crop of a photograph with road surface and lane
 * markings still visible in it, upscaled from a 206px source. Neither should return.
 *
 * A schematic is a different object. It is not a picture of a Ranger; it is a
 * top-down state diagram whose only job is to say *which* door is open and *which*
 * corner is low, using position rather than words. Every OEM draws one, and so does
 * the DUDU7 reference. It cannot look dated or wrong the way a rendering can, because
 * it is not claiming to be a likeness.
 *
 * ### Everything on it is real data
 *
 * `BodyState` already carries all of it: four doors plus the boot, a `TyreState` per
 * corner with pressure and warning, the sixteen-sensor radar ring and the steering
 * track. Nothing here is invented, and every element has a designed absent state —
 * a tyre with no reading shows an em dash in the same place the number would be, so
 * the layout is identical before and after the CAN bus wakes up.
 */
@Composable
fun VehicleDiagram(body: BodyState, modifier: Modifier = Modifier) {
    val colors = Dwm.colors
    val liveRadar = body.radar.size >= 16
    val nearest = if (liveRadar) body.radar.filter { it > 0 }.minOrNull() else null

    val steer by animateFloatAsState(
        targetValue = (((body.track ?: 240) - 240) / 240f).coerceIn(-1f, 1f),
        animationSpec = DwmMotion.base,
        label = "steer"
    )

    val d = LocalDensity.current
    val hairline = with(d) { DwmStroke.hairline.toPx() }
    val radius = with(d) { DwmRadius.m.toPx() }
    val pip = with(d) { DwmSpace.s.toPx() }

    DwmCard(modifier = modifier, padding = DwmSpace.cardPadding) {
        Column(Modifier.fillMaxSize()) {
            CardLabel("Vehicle")

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // The drawing is laid out in fractions of a *portrait* region, so it
                // gets one: a centred box no wider than [VEHICLE_ASPECT] of its
                // height. Letting it fill a landscape card made the fractions produce
                // a vehicle wider than it was long.
                val region = minOf(maxWidth, maxHeight * VEHICLE_ASPECT)

                // ...and no *taller* than that region implies either. The card is now
                // a tall one in the right-hand column rather than a short one along
                // the bottom, and a canvas that simply filled the height pushed the
                // tyre figures — which anchor to this box's corners — about 125dp
                // clear of the vehicle they belong to. Bounding the box to the
                // drawing's own proportions keeps them beside their wheels at any
                // card height, which is the whole point of putting them at corners.
                val band = minOf(maxHeight, region / VEHICLE_ASPECT)

                // Bound the *width* the same way [band] bounds the height, and for the
                // same reason. Height alone was enough while this card was 673dp tall:
                // the drawing filled 471 of the 493dp available, so corner-anchored tyre
                // figures landed ~11dp outboard of their wheels and read as attached.
                // With the 360 quad above, the card is 370dp and the drawing narrows to
                // ~210dp — leaving the figures stranded ~140dp out in empty card, which
                // is the same defect the [band] comment describes, arriving on the other
                // axis. [TYRE_SPREAD] is set so this clamps to [maxWidth] whenever the
                // quad is absent, leaving that card exactly as it was.
                val spread = minOf(maxWidth, region * TYRE_SPREAD)

                Box(
                    Modifier
                        .width(spread)
                        .height(band)
                        .align(Alignment.Center)
                ) {
                    Canvas(
                        Modifier
                            .width(region)
                            .fillMaxHeight()
                            .align(Alignment.Center)
                    ) {
                        drawBody(colors, hairline, radius)
                        drawDoors(colors, hairline, body)
                        if (liveRadar) {
                            drawRadar(colors, pip, body.radar)
                            if (body.reverse) drawGuides(colors.accent, hairline, steer)
                        }
                    }

                    // Tyre figures sit across the whole card, outboard of the vehicle,
                    // rather than inside the narrow drawing region — which is both
                    // where there is room for them and where a driver expects them.
                    TyreReadouts(body, Modifier.fillMaxSize())
                }
            }

            Spacer(Modifier.height(DwmSpace.s))
            val (word, tint) = statusWord(colors, body, liveRadar, nearest)
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

/**
 * One line for the whole vehicle, and open doors outrank proximity.
 *
 * A door left ajar is the thing that will still be true in ten minutes; a parking
 * sensor is only relevant for the seconds you are reversing. Ordering them the other
 * way round would hide the persistent fault behind the transient one.
 */
private fun statusWord(
    colors: DwmColors,
    body: BodyState,
    liveRadar: Boolean,
    nearest: Int?
): Pair<String, Color> {
    val open = listOfNotNull(
        body.doorLF?.let { if (it) "LF" else null },
        body.doorRF?.let { if (it) "RF" else null },
        body.doorLR?.let { if (it) "LR" else null },
        body.doorRR?.let { if (it) "RR" else null },
        body.boot?.let { if (it) "BOOT" else null }
    )
    return when {
        open.isNotEmpty() -> "${open.joinToString(" ")} OPEN" to colors.warn
        !liveRadar -> "NO SIGNAL" to colors.muted
        nearest == null -> "ALL CLEAR" to colors.ok
        nearest <= 3 -> "STOP" to colors.critical
        nearest <= 7 -> "CLOSE" to colors.warn
        else -> "CLEAR" to colors.ok
    }
}

/* Geometry as fractions of the canvas. Taller than wide, at roughly 1:1.7, because
 * that is what a vehicle looks like from above. */
/** Portrait: no wider than this fraction of its height. */
private const val VEHICLE_ASPECT = 0.70f

/**
 * How far outboard of the drawing the corner tyre figures may sit, as a multiple of the
 * drawing's width.
 *
 * Sized to leave the *tall* card alone. Without the 360 quad the drawing is ~420dp inside
 * ~493dp of card, so anything at or above 1.18 overshoots, the clamp to `maxWidth` wins,
 * and that layout is untouched — 1.20 with a little margin. It only bites once the card
 * gets short enough for the drawing to narrow, which is exactly when the figures would
 * otherwise drift away from their wheels.
 */
private const val TYRE_SPREAD = 1.20f

private const val BX0 = 0.22f
private const val BX1 = 0.78f
private const val BY0 = 0.05f
private const val BY1 = 0.72f

private fun DrawScope.drawBody(colors: DwmColors, stroke: Float, radius: Float) {
    drawRoundRect(
        color = colors.hairline,
        topLeft = Offset(size.width * BX0, size.height * BY0),
        size = Size(size.width * (BX1 - BX0), size.height * (BY1 - BY0)),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = stroke * 2f)
    )
    // Cabin division, so the schematic reads as facing forward rather than as a box.
    val y = size.height * (BY0 + (BY1 - BY0) * 0.34f)
    drawLine(
        colors.hairline,
        Offset(size.width * BX0, y),
        Offset(size.width * BX1, y),
        strokeWidth = stroke
    )
}

/**
 * Doors as bars on the side they are on.
 *
 * Open is [DwmColors.warn] and drawn outward, closed is a quiet hairline flush to the
 * body, unknown is not drawn at all — a door DWM has never heard about must not look
 * like a door it knows is shut.
 */
private fun DrawScope.drawDoors(colors: DwmColors, stroke: Float, body: BodyState) {
    val w = size.width
    val h = size.height
    val len = h * 0.14f
    val out = w * 0.07f

    fun door(open: Boolean?, left: Boolean, yFrac: Float) {
        if (open == null) return
        val x = if (left) w * BX0 else w * BX1
        val y = h * yFrac
        val tip = if (open) (if (left) x - out else x + out) else x
        drawLine(
            color = if (open) colors.warn else colors.hairline,
            start = Offset(x, y - len / 2f),
            end = Offset(tip, y + len / 2f),
            strokeWidth = stroke * if (open) 3f else 2f
        )
    }

    door(body.doorLF, true, 0.30f)
    door(body.doorRF, false, 0.30f)
    door(body.doorLR, true, 0.55f)
    door(body.doorRR, false, 0.55f)

    body.boot?.let { open ->
        drawLine(
            color = if (open) colors.warn else colors.hairline,
            start = Offset(w * (BX0 + 0.06f), h * (BY1 + 0.03f)),
            end = Offset(w * (BX1 - 0.06f), h * (BY1 + 0.03f)),
            strokeWidth = stroke * if (open) 3f else 2f
        )
    }
}

/** Distance as intensity — colour ramp plus alpha, legible in peripheral vision. */
private fun DrawScope.drawRadar(colors: DwmColors, pip: Float, radar: List<Int>) {
    val w = size.width
    val h = size.height

    fun tint(v: Int): Color? {
        if (v <= 0) return null
        val c = when {
            v <= 3 -> colors.critical
            v <= 7 -> colors.warn
            else -> colors.ok
        }
        return c.copy(alpha = (1f - v / 16f).coerceIn(0.35f, 1f))
    }

    fun pill(cx: Float, cy: Float, horizontal: Boolean, v: Int) {
        val c = tint(v) ?: return
        val long = pip * 2.4f
        val pw = if (horizontal) long else pip
        val ph = if (horizontal) pip else long
        drawRoundRect(
            color = c,
            topLeft = Offset(cx - pw / 2f, cy - ph / 2f),
            size = Size(pw, ph),
            cornerRadius = CornerRadius(pip / 2f, pip / 2f)
        )
    }

    for (i in 0..3) pill(w * (BX0 + (BX1 - BX0) * ((i + 0.5f) / 4f)), h * 0.015f, true, radar[i])
    for (i in 4..7) pill(w * (BX1 + 0.06f), h * (BY0 + (BY1 - BY0) * ((i - 4 + 0.5f) / 4f)), false, radar[i])
    for (i in 8..11) pill(w * (BX0 + (BX1 - BX0) * ((11 - i + 0.5f) / 4f)), h * 0.775f, true, radar[i])
    for (i in 12..15) pill(w * (BX0 - 0.06f), h * (BY0 + (BY1 - BY0) * ((15 - i + 0.5f) / 4f)), false, radar[i])
}

/** Steering-predicted corridor behind the tailgate, shown only while reversing. */
private fun DrawScope.drawGuides(accent: Color, stroke: Float, steer: Float) {
    val w = size.width
    val h = size.height
    val top = h * 0.83f
    val bottom = h * 0.99f
    val drift = w * 0.18f * steer

    fun edge(side: Float, t: Float): Float {
        val x0 = w * (0.5f + side * 0.12f)
        val x1 = w * (0.5f + side * 0.26f) + drift
        val ctrl = x0 + drift * 0.4f
        val u = 1f - t
        return u * u * x0 + 2f * u * t * ctrl + t * t * x1
    }

    listOf(-1f, 1f).forEach { side ->
        drawPath(
            Path().apply {
                moveTo(edge(side, 0f), top)
                quadraticBezierTo(edge(side, 0.5f), (top + bottom) / 2f, edge(side, 1f), bottom)
            },
            color = accent.copy(alpha = 0.75f),
            style = Stroke(width = stroke * 2f)
        )
    }
    val t = 0.6f
    drawLine(
        accent.copy(alpha = 0.4f),
        Offset(edge(-1f, t), top + (bottom - top) * t),
        Offset(edge(1f, t), top + (bottom - top) * t),
        strokeWidth = stroke * 2f
    )
}

/**
 * Tyre pressures, at the corner they belong to.
 *
 * Text rather than Canvas so it uses the real bundled mono face and the same
 * [DwmType.NO_SIGNAL] em dash as every other reading — drawing digits into a Canvas
 * would mean a second, subtly different way of setting a number.
 */
@Composable
private fun TyreReadouts(body: BodyState, modifier: Modifier) {
    val colors = Dwm.colors
    Box(modifier) {
        val corners = listOf(
            0 to androidx.compose.ui.Alignment.TopStart,
            1 to androidx.compose.ui.Alignment.TopEnd,
            2 to androidx.compose.ui.Alignment.BottomStart,
            3 to androidx.compose.ui.Alignment.BottomEnd
        )
        corners.forEach { (i, align) ->
            val t = body.tyres.getOrNull(i)
            val warn = (t?.warn ?: 0) != 0
            DwmText(
                text = t?.pressure?.let { String.format(Locale.US, "%.0f", it) }
                    ?: DwmType.NO_SIGNAL,
                style = DwmType.data,
                color = when {
                    warn -> colors.critical
                    t?.pressure != null -> colors.text
                    else -> colors.muted
                },
                modifier = Modifier.align(align)
            )
        }
    }
}
