package com.dwm.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmType
import com.dwm.cockpit.ui.theme.StatusCaution
import com.dwm.cockpit.ui.theme.StatusOk

/**
 * Shared text and status primitives.
 *
 * This file used to hold the whole vehicle column — a speed block, two data cards,
 * a steering dial and a tyre diagram. Most of that is gone: the van reports six
 * signals, and six signals fit in the top strip of [CockpitHome]. Steering did not
 * disappear so much as move, from a dial nobody watched into the predicted path
 * curves in [ParkingDisplay], where it is doing an actual job.
 */

/**
 * Text with tabular figures. Without `tnum` every digit has its own width, so a
 * speed readout visibly jitters as it counts — the number shifts sideways while
 * standing still. Costs nothing and is the biggest single polish win on screen.
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
        fontFamily = DwmType.family,
        maxLines = 1,
        // Track titles are longer than any card that holds them. Without this they
        // simply stopped mid-word, which reads as a bug rather than as a title.
        overflow = TextOverflow.Ellipsis,
        style = if (tabular) TextStyle(fontFeatureSettings = DwmType.TABULAR) else TextStyle.Default
    )
}

/** All-caps heading. The letter spacing is what keeps it reading as a label rather
 *  than as body text that failed to grow. */
@Composable
fun CardLabel(text: String) {
    Text(
        text,
        color = Dwm.colors.textTertiary,
        style = DwmType.overline,
        maxLines = 1
    )
}

/** Green receiving · amber bound but silent · grey not bound. The middle one
 *  matters: a van that simply never reports must not look like a failed bind. */
@Composable
fun CanDot(level: Int, demo: Boolean) {
    val colors = Dwm.colors
    val c = when {
        demo -> StatusCaution
        level >= 2 -> StatusOk
        level == 1 -> StatusCaution
        else -> colors.textTertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(c))
        Spacer(Modifier.width(DwmSpace.xs))
        DwmText(
            if (demo) "DEMO" else "CAN",
            size = DwmType.micro,
            color = if (level >= 1 || demo) c else colors.textTertiary,
            weight = FontWeight.Medium
        )
    }
}
