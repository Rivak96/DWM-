package com.dwm.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSize
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke
import com.dwm.cockpit.ui.theme.DwmType

/**
 * The Settings control vocabulary — six components covering every control on the
 * screen.
 *
 * Settings was a generic two-pane list: low-contrast type, grey rounded buttons, and
 * a highlight pill with no relationship to anything else on the screen. The cause was
 * that it had no vocabulary — sixty controls each styled at its own call site, then
 * repainted at runtime by a function that walked the view tree remapping colours it
 * happened to recognise. A section header sat at `#8E8E93`, a colour in no palette,
 * so it was never remapped and stayed mid-grey through every theme the app had.
 *
 * These read the same tokens as the home screen and reuse [DwmCard] and [DwmText]
 * directly, which is the only reliable way for two screens to match.
 */

/** A titled block. The section heading is a label above the card, never inside it —
 *  nothing in DWM puts a card inside a card. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        CardLabel(title)
        Spacer(Modifier.height(DwmSpace.m))
        DwmCard(Modifier.fillMaxWidth(), padding = DwmSpace.cardPadding) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DwmSpace.l),
                content = content
            )
        }
    }
}

/**
 * One setting: a label, an optional explanation, and a control on the right.
 *
 * [DwmSize.touchTarget] minimum, always — 72dp. The old rows were 44dp, which is a
 * phone measurement on a panel read at arm's length from a driver's seat.
 */
@Composable
fun SettingsRow(
    label: String,
    hint: String? = null,
    control: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DwmSize.touchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            DwmText(label, style = DwmType.body, color = Dwm.colors.text)
            if (hint != null) {
                // Two lines. Hints carry real explanation — the cockpit-mode one is
                // a sentence and a half — and at one line they were being cut off
                // mid-word, which is how the version string rendered as "v0.25.0…".
                DwmText(hint, style = DwmType.caption, color = Dwm.colors.muted, maxLines = 2)
            }
        }
        Spacer(Modifier.width(DwmSpace.l))
        control()
    }
}

/**
 * A row of mutually exclusive choices — theme, scale, camera fit, cockpit mode.
 *
 * This is the dominant pattern on the screen and was previously a line of loose grey
 * buttons that gave no sign which one was active. The selected option is filled and
 * uses the text colour; the others are outlined and muted. **No accent** — the accent
 * belongs to the nav rail, and one screen gets one accented element.
 */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val colors = Dwm.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DwmSpace.s)
    ) {
        options.forEachIndexed { i, text ->
            val on = i == selected
            Box(
                Modifier
                    .defaultMinSize(minHeight = DwmSize.touchTarget)
                    .clip(DwmShapes.small)
                    .background(if (on) colors.raised else Color.Transparent)
                    .border(
                        DwmStroke.hairline,
                        if (on) colors.text else colors.hairline,
                        DwmShapes.small
                    )
                    .clickable(
                        interactionSource = pressSource(),
                        indication = null
                    ) { onSelect(i) }
                    .padding(horizontal = DwmSpace.xl, vertical = DwmSpace.m),
                contentAlignment = Alignment.Center
            ) {
                DwmText(
                    text,
                    style = DwmType.label,
                    color = if (on) colors.text else colors.muted
                )
            }
        }
    }
}

/** An action. Same shape as a segment so a row of buttons and a row of choices sit
 *  on the same rhythm. */
@Composable
fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false
) {
    val colors = Dwm.colors
    Box(
        modifier
            .defaultMinSize(minHeight = DwmSize.touchTarget)
            .clip(DwmShapes.small)
            .background(if (emphasis) colors.raised else Color.Transparent)
            .border(DwmStroke.hairline, colors.hairline, DwmShapes.small)
            .clickable(
                interactionSource = pressSource(),
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = DwmSpace.xl, vertical = DwmSpace.m),
        contentAlignment = Alignment.Center
    ) {
        DwmText(text, style = DwmType.label, color = colors.text)
    }
}

/**
 * A switch, drawn rather than borrowed.
 *
 * Material's `Switch` is one of the stock components that made the old screen look
 * unstyled, and theming it meant hand-building four `ColorStateList`s from hardcoded
 * greys. This is a track and a knob reading straight from the palette. Off is a
 * hairline outline, on is filled with the text colour — contrast as state, no second
 * colour.
 */
@Composable
fun SettingsSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Dwm.colors
    Box(
        Modifier
            .size(DwmSize.touchTarget, DwmSize.touchTarget)
            .clickable(
                interactionSource = pressSource(),
                indication = null
            ) { onChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(DwmSpace.huge, DwmSpace.xxl)
                .clip(CircleShape)
                .background(if (checked) colors.text else Color.Transparent)
                .border(DwmStroke.hairline, if (checked) colors.text else colors.hairline, CircleShape),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .padding(horizontal = DwmSpace.xs)
                    .size(DwmSpace.xl)
                    .clip(CircleShape)
                    .background(if (checked) colors.background else colors.muted)
            )
        }
    }
}

/**
 * A monospaced data panel — the diagnostics block, and anything else that is a
 * readout rather than prose.
 *
 * The diagnostics content is genuinely the best information on this screen: the SoC,
 * the real `SDK_INT` behind a ROM that lies about it, the panel geometry, the
 * freeform flags, and live CAN state. It was rendered as 11sp monospace on a
 * `#10FFFFFF` wash — invisible in any light theme and reading as leftover debug
 * output. Treated as a deliberate instrument panel instead: raised surface, hairline,
 * the bundled mono face at [DwmType.data], and room to breathe.
 */
@Composable
fun DataPanel(text: String, modifier: Modifier = Modifier) {
    val colors = Dwm.colors
    DwmCard(modifier.fillMaxWidth(), raised = true, padding = DwmSpace.cardPadding) {
        DwmText(
            text,
            style = DwmType.data,
            color = colors.muted,
            maxLines = 64,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Every control here is `indication = null` with its own source; naming it keeps
 *  the call sites readable and avoids shadowing `remember` itself. */
@Composable
private fun pressSource() = remember { MutableInteractionSource() }
