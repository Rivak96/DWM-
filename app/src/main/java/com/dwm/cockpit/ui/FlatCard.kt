package com.dwm.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A flat card, replacing the frosted `GlassCard` this project used to use.
 *
 * The reference screen the user supplied is flat and dark: solid panels on a
 * near-black field, no translucency, no borders, no sheen. Dropping the frosted
 * treatment also removes a full-screen blur pass from a low-RAM Unisoc chip, so
 * the honest look and the cheap look happen to be the same thing here.
 */
@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    padding: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surface)
            .padding(padding),
        content = content
    )
}
