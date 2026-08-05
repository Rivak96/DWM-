package com.dwm.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType

/**
 * The shared text and status primitives.
 *
 * Everything that puts a character on screen goes through here, which is how the
 * type scale stays a scale rather than a suggestion — there is no `Text` call in
 * DWM that picks its own size.
 */

/**
 * The one text call.
 *
 * Always single-line and always ellipsised, because a cockpit has no reflow: a
 * string that grows by a word must not push the element below it down the screen
 * while the vehicle is moving. Track titles used to stop mid-word for exactly this
 * reason — the fix is an ellipsis, not a wrap.
 */
@Composable
fun DwmText(
    text: String,
    style: TextStyle = DwmType.body,
    color: Color = Dwm.colors.text,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1
) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** All-caps card heading. Read once, then ignored — hence the size and the colour. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    DwmText(
        text = text.uppercase(),
        style = DwmType.overline,
        color = Dwm.colors.muted,
        modifier = modifier
    )
}

/**
 * A vehicle reading, and the way DWM sets every number.
 *
 * This is the whole no-signal design in one composable, and it is the reason the
 * home screen can ship before a single CAN value arrives.
 *
 * - A live value is set in the monospaced face, so figures are tabular and a
 *   readout cannot shuffle sideways as it updates.
 * - **No signal renders as an em dash in the same face at the same size**, in
 *   [Dwm.colors.muted]. Not an orange NO SENSOR DATA label bolted onto an empty box,
 *   not a warning colour, and never a zero — a zero is a reading, and printing one
 *   for a sensor that has never spoken is the difference between an instrument and a
 *   mockup.
 * - The label and the unit stay at their normal contrast either way, so the slot
 *   reads as deliberately empty rather than broken.
 * - Because the placeholder occupies a real digit's width in a monospaced face,
 *   **nothing moves when data starts flowing.** The layout is identical with a dead
 *   CAN bus and a live one.
 */
@Composable
fun Reading(
    label: String,
    value: String?,
    unit: String? = null,
    style: TextStyle = DwmType.value,
    modifier: Modifier = Modifier
) {
    val colors = Dwm.colors
    Column(modifier) {
        CardLabel(label)
        Spacer(Modifier.size(DwmSpace.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            DwmText(
                text = value ?: DwmType.NO_SIGNAL,
                style = style,
                color = if (value == null) colors.muted else colors.text
            )
            if (unit != null) {
                Spacer(Modifier.width(DwmSpace.xs))
                DwmText(
                    text = unit,
                    style = DwmType.caption,
                    color = colors.muted,
                    modifier = Modifier.padding(bottom = DwmSpace.xs)
                )
            }
        }
    }
}

/**
 * The same reading, set on one line, for the top strip.
 *
 * Same rules as [Reading] — mono, tabular, em dash when nothing is flowing — with
 * the label dropped because the unit already says what it is at a glance.
 */
@Composable
fun ReadingInline(
    value: String?,
    unit: String,
    style: TextStyle = DwmType.value,
    modifier: Modifier = Modifier
) {
    val colors = Dwm.colors
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        DwmText(
            text = value ?: DwmType.NO_SIGNAL,
            style = style,
            color = if (value == null) colors.muted else colors.text
        )
        Spacer(Modifier.width(DwmSpace.xs))
        DwmText(
            text = unit,
            style = DwmType.caption,
            color = colors.muted,
            modifier = Modifier.padding(bottom = DwmSpace.xs)
        )
    }
}

/**
 * CAN bus status.
 *
 * A dot and a word, and the dot is the only place [Dwm.colors.ok] appears on the home
 * screen. Green here means the vehicle is talking, not that a button is selected —
 * the previous build used the same green for an active nav item, which is how a
 * status colour stops meaning anything.
 */
@Composable
fun CanDot(level: Int, demo: Boolean, modifier: Modifier = Modifier) {
    val colors = Dwm.colors
    val tint = when {
        demo -> colors.warn
        level >= 2 -> colors.ok
        level == 1 -> colors.warn
        else -> colors.muted
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)
    ) {
        Spacer(
            Modifier
                .size(DwmSpace.s)
                .clip(CircleShape)
                .background(tint)
        )
        DwmText(
            text = if (demo) "DEMO" else "CAN",
            style = DwmType.overline,
            color = tint
        )
    }
}
