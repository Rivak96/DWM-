package com.dwm.cockpit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmRadius
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke

/**
 * The card, and most of the reason the rebuilt screen reads as layered.
 *
 * The previous version was a single solid fill of `colorScheme.surface` — no
 * border, no gradient, nothing. That is genuinely the right call on a good panel,
 * and it is what the flat reference screen does. It fails here for a hardware
 * reason: the deck's display lifts blacks badly, so the tonal step between a card
 * and the field behind it washed out and the whole screen became one grey plane.
 *
 * So a card is now separated from its background three ways simultaneously — a
 * larger tonal step in the palette, a vertical gradient within the fill, and a
 * hairline border. On a good screen that reads as subtle depth; on this one it is
 * the difference between seeing panels and seeing a wash. None of it costs a GPU
 * feature: a linear gradient and a 1dp stroke are close to free, which matters
 * because real blur is not an option here at all — `Modifier.blur` needs API 31
 * and the deck is API 29 despite reporting Android 12.
 */
@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    radius: Dp = DwmRadius.m,
    padding: Dp = DwmSpace.m,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = Dwm.colors
    val shape = RoundedCornerShape(radius)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) DwmMotion.PRESS_SCALE else 1f,
        animationSpec = DwmMotion.press,
        label = "cardPress"
    )

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors.cardTop, colors.cardBottom)))
            .border(DwmStroke.hairline, colors.cardBorder, shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(padding),
        content = content
    )
}
