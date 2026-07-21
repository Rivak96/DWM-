package com.dwm.cockpit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Miniature of the saved cockpit layout for the hero card: the screen as a
 * rounded frame, each panel as a rounded block — fullscreen base tinted accent,
 * windows outlined, drawn panels solid.
 */
class LayoutPreview(context: Context) : View(context) {

    var panels: List<Panel> = emptyList()
        set(v) { field = v; invalidate() }

    private var accent = 0xFF3E6AE1.toInt()
    private var frameColor = 0x22FFFFFF
    private var drawnColor = 0x66FFFFFF
    private var winStroke = 0xAAFFFFFF.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val r = RectF()

    fun setColors(accentColor: Int, theme: Ui.Theme) {
        accent = accentColor
        frameColor = if (theme.light) 0x14000000 else 0x1AFFFFFF
        drawnColor = if (theme.light) 0x33000000 else 0x59FFFFFF
        winStroke = if (theme.light) 0x66000000 else 0x99FFFFFF.toInt()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // fit a 16:9 screen frame in the view
        var fw = w
        var fh = fw * 9f / 16f
        if (fh > h) { fh = h; fw = fh * 16f / 9f }
        val fx = (w - fw) / 2f
        val fy = (h - fh) / 2f
        val rad = min(fw, fh) * 0.06f

        paint.color = frameColor
        r.set(fx, fy, fx + fw, fy + fh)
        canvas.drawRoundRect(r, rad, rad, paint)

        val pad = min(fw, fh) * 0.015f
        for (p in panels) {
            r.set(
                fx + p.l * fw + pad, fy + p.t * fh + pad,
                fx + p.r * fw - pad, fy + p.b * fh - pad
            )
            if (r.width() <= 0 || r.height() <= 0) continue
            when {
                p.isFullscreenApp() -> {
                    paint.color = Ui.withAlpha(accent, 0x59)
                    canvas.drawRoundRect(r, rad, rad, paint)
                    stroke.color = accent
                    stroke.strokeWidth = fh * 0.012f
                    canvas.drawRoundRect(r, rad, rad, stroke)
                }
                p.isWindowedApp() -> {
                    stroke.color = winStroke
                    stroke.strokeWidth = fh * 0.012f
                    canvas.drawRoundRect(r, rad, rad, stroke)
                }
                else -> {
                    paint.color = drawnColor
                    canvas.drawRoundRect(r, rad, rad, paint)
                }
            }
        }
    }
}
