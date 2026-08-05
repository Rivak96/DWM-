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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.dwm.cockpit.ui.theme.Dwm
import com.dwm.cockpit.ui.theme.DwmMotion
import com.dwm.cockpit.ui.theme.DwmRadius
import com.dwm.cockpit.ui.theme.DwmSpace
import com.dwm.cockpit.ui.theme.DwmStroke

/**
 * The card. Every surface in DWM that is not the background is one of these.
 *
 * ### Flat, not gradient
 *
 * This replaces `FlatCard`, which drew a vertical gradient inside every card. That
 * gradient was a fix for a problem that no longer exists: the old palette put a
 * `#3B3E43` card on a `#292B2E` field, an eighteen-point step that collapsed into
 * one wash on this deck's IPS, and the gradient was one of three tricks stacked up
 * to rescue it. The palette is now near-black — `#14171C` on `#0A0C0F` is a 2.3x
 * luminance ratio — and with a hairline at 1.7:1 on top of that, a flat fill reads
 * cleanly. A gradient that is no longer load-bearing is just decoration, and there
 * is none of that here.
 *
 * ### Depth without blur or shadow
 *
 * Real-time blur needs API 31 and the deck is API 29, so glassmorphism was never
 * available; ambient shadow on a near-black field is invisible on a panel that lifts
 * blacks. Depth is therefore a tonal step plus a hairline, which is what
 * `DwmElevation` encodes. [raised] picks the second step, and it is for overlay
 * panels and for content that sits *on* a card — never for a card inside a card,
 * which does not happen anywhere in this app.
 *
 * The press response is a `graphicsLayer` scale, so it costs no measure or layout
 * pass. That matters on a Mali-G51 pushing 2.3 million pixels.
 */
@Composable
fun DwmCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    radius: Dp = DwmRadius.m,
    padding: Dp = DwmSpace.cardPadding,
    border: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = Dwm.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) DwmMotion.PRESS_SCALE else 1f,
        animationSpec = DwmMotion.fast,
        label = "cardPress"
    )

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(radius))
            .background(if (raised) colors.raised else colors.surface)
            .then(
                if (border) Modifier.border(
                    DwmStroke.hairline,
                    colors.hairline,
                    RoundedCornerShape(radius)
                ) else Modifier
            )
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
