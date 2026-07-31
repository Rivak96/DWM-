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

// GlassCard lived here. The SYNC-style home is flat by design — solid panels on a
// near-black field, no translucency, no hairline borders, no sheen — so it was
// replaced by FlatCard, which is both the honest look and the cheaper one.

/** Render any launcher icon Drawable into a Compose ImageBitmap. */
fun drawableToImageBitmap(d: Drawable, sizePx: Int): ImageBitmap {
    val s = sizePx.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    d.setBounds(0, 0, s, s)
    d.draw(canvas)
    return bmp.asImageBitmap()
}
