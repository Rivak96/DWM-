package com.dwm.cockpit

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

/**
 * Runtime theme kit. The user picks a theme preset (Tesla gray / Midnight black /
 * Light) and an accent colour; all surfaces, text colours and backgrounds derive
 * from them at runtime. `skin(root)` walks a view tree and restyles the standard
 * widgets, remapping any known palette colour to the active theme.
 */
object Ui {

    data class Accent(val name: String, val color: Int)

    val ACCENTS = listOf(
        Accent("Tesla Blue", 0xFF3E6AE1.toInt()),
        Accent("Tesla Red", 0xFFE82127.toInt()),
        Accent("Mono", 0xFFE8E8EA.toInt()),
        Accent("Teal", 0xFF2DD4BF.toInt()),
        Accent("Amber", 0xFFF5A623.toInt()),
        Accent("Violet", 0xFFA78BFA.toInt())
    )

    /** A theme preset: every colour the UI needs. */
    data class Theme(
        val light: Boolean,
        val bg: Int,
        val surface: Int,
        val surfacePressed: Int,
        val card: Int,
        val cardBorder: Int,
        val text: Int,
        val dim: Int,
        val barBg: Int,
        val hairline: Int
    )

    val THEMES = listOf("Tesla", "Midnight", "Light")

    fun th(c: Context): Theme = when (Prefs.theme(c)) {
        1 -> Theme( // Midnight — OLED black
            light = false,
            bg = 0xFF0A0A0C.toInt(),
            surface = 0xFF2A2A2D.toInt(),
            surfacePressed = 0xFF3A3A3E.toInt(),
            card = 0xE61B1B1E.toInt(),
            cardBorder = 0x14FFFFFF,
            text = 0xFFF2F2F2.toInt(),
            dim = 0xFF9A9AA0.toInt(),
            barBg = 0xE0101013.toInt(),
            hairline = 0x1AFFFFFF
        )
        2 -> Theme( // Light — Tesla day mode
            light = true,
            bg = 0xFFF4F5F6.toInt(),
            surface = 0xFFE3E4E7.toInt(),
            surfacePressed = 0xFFD4D6DA.toInt(),
            card = 0xF2FFFFFF.toInt(),
            cardBorder = 0x14000000,
            text = 0xFF171A20.toInt(),
            dim = 0xFF5C5E62.toInt(),
            barBg = 0xF0FFFFFF.toInt(),
            hairline = 0x1F000000
        )
        else -> Theme( // Tesla — charcoal gray (default)
            light = false,
            bg = 0xFF292B2E.toInt(),
            surface = 0xFF3B3E43.toInt(),
            surfacePressed = 0xFF4A4E55.toInt(),
            card = 0xE62F3236.toInt(),
            cardBorder = 0x1FFFFFFF,
            text = 0xFFF2F2F2.toInt(),
            dim = 0xFFA5A8AD.toInt(),
            barBg = 0xE61E2023.toInt(),
            hairline = 0x24FFFFFF
        )
    }

    // Every palette colour that may appear on a TextView, so skin() can remap
    // between themes no matter which theme painted the view last.
    private val TEXT_COLORS = setOf(0xFFF2F2F2.toInt(), 0xFF171A20.toInt())
    private val DIM_COLORS = setOf(
        0xFF9A9AA0.toInt(), 0xFFA5A8AD.toInt(), 0xFF5C5E62.toInt(), 0xFF93A1A6.toInt()
    )

    fun accent(c: Context) = Prefs.accent(c)

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    fun blend(a: Int, b: Int, t: Float): Int {
        val ia = 1f - t
        val r = (Color.red(a) * ia + Color.red(b) * t).toInt()
        val g = (Color.green(a) * ia + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * ia + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun rounded(fill: Int, radius: Float, strokeW: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (strokeW > 0) setStroke(strokeW, stroke)
        }

    /** Tesla-style button: flat neutral surface, rounded, with ripple feedback. */
    fun chipBg(c: Context): Drawable {
        val t = th(c)
        val r = dp(c, 10).toFloat()
        return android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(withAlpha(t.text, 0x2E)),
            rounded(t.surface, r, 0, 0),
            rounded(0xFFFFFFFF.toInt(), r, 0, 0)
        )
    }

    /** Borderless circular ripple for icon buttons and dock icons. */
    fun iconRipple(c: Context): Drawable {
        val t = th(c)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt())
        }
        return android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(withAlpha(t.text, 0x30)), null, mask
        )
    }

    /** Floating translucent cluster behind the top icon row. */
    fun clusterBg(c: Context): Drawable {
        val t = th(c)
        return rounded(t.barBg, dp(c, 14).toFloat(), dp(c, 1), t.hairline)
    }

    /** Flat card with a hairline border, behind every drawn panel. */
    fun cardBg(c: Context): Drawable {
        val t = th(c)
        return rounded(t.card, dp(c, 12).toFloat(), dp(c, 1), t.cardBorder)
    }

    /** Editor slot tile (the editor preview is always dark). */
    fun slotBg(c: Context): Drawable =
        rounded(0xCC232326.toInt(), dp(c, 10).toFloat(), dp(c, 1), 0x33FFFFFF)

    /** Tesla settings-sidebar item: flat when idle, surface when selected. */
    fun navItemBg(c: Context, selected: Boolean): Drawable {
        val t = th(c)
        return if (selected) rounded(t.surface, dp(c, 10).toFloat(), 0, 0)
        else rounded(0x00000000, dp(c, 10).toFloat(), 0, 0)
    }

    /** Overlay-panel drag/resize grip: accent-tinted rounded corner tab so the
     *  touch target is unmistakable. */
    fun gripBg(c: Context, accent: Int, topLeft: Boolean): Drawable {
        val r = dp(c, 12).toFloat()
        val z = 0f
        return GradientDrawable().apply {
            setColor(withAlpha(accent, 0xE6))
            cornerRadii = if (topLeft)
                floatArrayOf(r, r, z, z, r, r, z, z) // round TL + BR
            else
                floatArrayOf(z, z, r, r, z, z, r, r) // round TR + BL
        }
    }

    /** Bottom bar background: flat translucent with a hairline top edge. */
    fun barBg(c: Context): Drawable {
        val t = th(c)
        return object : ColorDrawable(t.barBg) {
            private val line = android.graphics.Paint().apply { color = t.hairline }
            override fun draw(canvas: android.graphics.Canvas) {
                super.draw(canvas)
                canvas.drawRect(
                    bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.top + dp(c, 1).toFloat(), line
                )
            }
        }
    }

    /** Clip a view to a rounded rect (cards, panels, webviews, camera). */
    fun roundify(v: View, radiusDp: Int) {
        val r = dp(v.context, radiusDp).toFloat()
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        v.clipToOutline = true
    }

    /** Paint the window + content root with the theme background. */
    fun themeWindow(a: Activity) {
        val t = th(a)
        a.window.setBackgroundDrawable(ColorDrawable(t.bg))
        val content = a.findViewById<ViewGroup>(android.R.id.content)
        if (content != null && content.childCount > 0) {
            content.getChildAt(0).setBackgroundColor(t.bg)
        }
    }

    /** Restyle Buttons, Switches, and TextViews under [root] for the theme. */
    fun skin(c: Context, root: View) {
        val a = accent(c)
        val t = th(c)
        walk(root) { v ->
            when (v) {
                is Switch -> {
                    v.setTextColor(t.text)
                    val states = arrayOf(
                        intArrayOf(android.R.attr.state_checked), intArrayOf()
                    )
                    v.thumbTintList =
                        ColorStateList(states, intArrayOf(a, if (t.light) 0xFFAEB0B5.toInt() else 0xFF6B6B70.toInt()))
                    v.trackTintList =
                        ColorStateList(states, intArrayOf(withAlpha(a, 0x66), if (t.light) 0x30000000 else 0x30FFFFFF))
                }
                is Button -> {
                    v.background = chipBg(c)
                    v.setTextColor(t.text)
                    v.isAllCaps = false
                    v.textSize = 13f
                    v.typeface = android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.NORMAL
                    )
                }
                is TextView -> {
                    if (v.tag == "accent") {
                        v.setTextColor(t.text)
                        v.typeface = android.graphics.Typeface.create(
                            "sans-serif-medium", android.graphics.Typeface.NORMAL
                        )
                    } else {
                        val cur = v.currentTextColor
                        if (cur in TEXT_COLORS) v.setTextColor(t.text)
                        else if (cur in DIM_COLORS) v.setTextColor(t.dim)
                    }
                }
                else -> {}
            }
        }
    }

    private fun walk(v: View, f: (View) -> Unit) {
        f(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
    }

    /** Dialog builder matching the theme. */
    fun dialog(c: Context): AlertDialog.Builder = AlertDialog.Builder(
        c,
        if (th(c).light) android.R.style.Theme_Material_Light_Dialog_Alert
        else android.R.style.Theme_Material_Dialog_Alert
    )

    /** Procedural wallpapers. Index 0 is theme-flat; 1 Midnight blue; 2 Carbon. */
    fun wallpaperDrawable(c: Context, idx: Int): Drawable {
        val t = th(c)
        return when (idx) {
            1 -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF050B18.toInt(), 0xFF0B1E3A.toInt(), 0xFF123163.toInt())
            )
            2 -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF0A0A0C.toInt(), 0xFF17171B.toInt(), 0xFF0A0A0C.toInt())
            )
            else -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(t.bg, blend(t.bg, if (t.light) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0.18f))
            )
        }
    }
}
