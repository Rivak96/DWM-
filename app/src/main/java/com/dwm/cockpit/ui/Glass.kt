package com.dwm.cockpit.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Frosted-glass card: translucent surface over the (blurred) background, with a
 *  hairline light border and a faint top sheen — the premium OEM look without
 *  per-frame blur cost. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    padding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(cs.surface.copy(alpha = 0.52f))
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.06f),
                    0.4f to Color.Transparent
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(padding),
        content = content
    )
}

/** Render any launcher icon Drawable into a Compose ImageBitmap. */
fun drawableToImageBitmap(d: Drawable, sizePx: Int): ImageBitmap {
    val s = sizePx.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    d.setBounds(0, 0, s, s)
    d.draw(canvas)
    return bmp.asImageBitmap()
}
