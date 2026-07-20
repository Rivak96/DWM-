package com.dwm.cockpit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Locale
import kotlin.math.min

/**
 * Instrument-cluster style arc gauge: 270° track, animated accent-coloured value
 * arc, big number in the middle, unit + label underneath. Shows "--" until the
 * first real value arrives.
 */
class GaugeView(context: Context) : View(context) {

    private var label = ""
    private var unit = ""
    private var minV = 0f
    private var maxV = 100f
    private var shown = 0f
    private var hasValue = false
    private var animator: ValueAnimator? = null

    var accentColor: Int = 0xFF3E6AE1.toInt()
        set(v) { field = v; invalidate() }

    /** Theme the gauge: track + value + label colours. */
    fun setPalette(track: Int, value: Int, label: Int) {
        trackPaint.color = track
        valuePaint.color = value
        smallPaint.color = label
        invalidate()
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x1FFFFFFF
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F2F2.toInt()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9A9AA0.toInt()
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }
    private val rect = RectF()

    fun configure(label: String, unit: String, min: Float, max: Float) {
        this.label = label
        this.unit = unit
        this.minV = min
        this.maxV = max
        invalidate()
    }

    fun setValue(v: Float) {
        hasValue = true
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, v).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val s = min(w, h) * 0.92f
        val stroke = s * 0.06f
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        arcPaint.color = accentColor

        val cx = w / 2f
        val cy = h / 2f
        val half = (s - stroke) / 2f
        rect.set(cx - half, cy - half, cx + half, cy + half)

        canvas.drawArc(rect, 135f, 270f, false, trackPaint)
        if (hasValue) {
            val frac = ((shown - minV) / (maxV - minV)).coerceIn(0f, 1f)
            canvas.drawArc(rect, 135f, 270f * frac, false, arcPaint)
        }

        valuePaint.textSize = s * 0.30f
        val valueText = if (hasValue) String.format(Locale.US, "%.0f", shown) else "--"
        canvas.drawText(valueText, cx, cy + valuePaint.textSize * 0.30f, valuePaint)

        smallPaint.textSize = s * 0.085f
        canvas.drawText(unit, cx, cy + s * 0.24f, smallPaint)
        canvas.drawText(label.uppercase(), cx, cy + s * 0.36f, smallPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
