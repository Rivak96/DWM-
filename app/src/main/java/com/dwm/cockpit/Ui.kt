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
import com.dwm.cockpit.ui.theme.DwmPalette

/**
 * Runtime theme kit. The user picks a theme preset (Tesla gray / Midnight black /
 * Light) and an accent colour; all surfaces, text colours and backgrounds derive
 * from them at runtime. `skin(root)` walks a view tree and restyles the standard
 * widgets, remapping any known palette colour to the active theme.
 */
object Ui {

    data class Accent(val name: String, val color: Int)

    /**
     * One accent, and this list exists only so the Settings screen keeps compiling
     * until it is rebuilt.
     *
     * There were seven, and the screen was worse for every one of them. The home
     * screen was simultaneously showing a green nav highlight, a blue floating
     * button and orange warning badges — three colours competing to be the one that
     * meant something, so none of them did. The rule now is one accent, on one
     * element per screen: the nav rail's travelling bar. Green, amber and red are
     * semantic and belong to the vehicle, never to the interface.
     */
    val ACCENTS = listOf(Accent("Cockpit Blue", DwmPalette.ACCENT))

    /**
     * A theme preset: every colour the UI needs.
     *
     * [cardTop]/[cardBottom] are a gradient pair rather than one flat fill. The
     * deck's panel has a poor black level, and a card that differs from its
     * background by tone alone disappears on it — see the "Cockpit" preset below.
     */
    data class Theme(
        val light: Boolean,
        val bg: Int,
        val surface: Int,
        val surfacePressed: Int,
        val card: Int,
        val cardBorder: Int,
        val text: Int,
        val dim: Int,
        val textTertiary: Int,
        val barBg: Int,
        val hairline: Int,
        val cardTop: Int,
        val cardBottom: Int
    )

    /** Index into this list *is* the stored `Prefs.theme` value. See [night]. */
    val THEMES = listOf("Auto", "Day", "Night")

    /**
     * The palette, for the View half of the app.
     *
     * There were four presets here: Tesla, Midnight, Light and Cockpit. Three of
     * them were abandoned work and the fourth was the only one anyone used, but all
     * four had to keep compiling, so every screen was written against a palette that
     * might be light or dark and consequently committed to neither. A Light preset
     * also flatly contradicts a design premised on near-black surfaces with nothing
     * pure white on them.
     *
     * One design now, in two variants, both defined in [DwmPalette]. The variant is
     * decided by [night], which is a fact about the world rather than a preference.
     *
     * The [Theme] shape is unchanged so the existing view screens keep compiling.
     * Two of its fields no longer mean what their names suggest and are kept only
     * for that reason: [Theme.cardTop] and [Theme.cardBottom] are now the same
     * colour, because the card gradient is gone. It existed to stop a mid-grey card
     * dissolving into a mid-grey field; against a near-black background separated by
     * a 2.3x luminance step and a hairline at 1.7:1, a flat fill reads cleanly and a
     * gradient is just decoration.
     */
    fun th(c: Context): Theme = theme(night(c))

    fun theme(night: Boolean): Theme = if (night) Theme(
        light = false,
        bg = DwmPalette.N_BACKGROUND,
        surface = DwmPalette.N_SURFACE,
        surfacePressed = DwmPalette.N_RAISED,
        card = DwmPalette.N_SURFACE,
        cardBorder = DwmPalette.N_HAIRLINE,
        text = DwmPalette.N_TEXT,
        dim = DwmPalette.N_MUTED,
        textTertiary = DwmPalette.N_MUTED,
        barBg = DwmPalette.N_SURFACE,
        hairline = DwmPalette.N_HAIRLINE,
        cardTop = DwmPalette.N_SURFACE,
        cardBottom = DwmPalette.N_SURFACE
    ) else Theme(
        light = false,
        bg = DwmPalette.BACKGROUND,
        surface = DwmPalette.SURFACE,
        surfacePressed = DwmPalette.RAISED,
        card = DwmPalette.SURFACE,
        cardBorder = DwmPalette.HAIRLINE,
        text = DwmPalette.TEXT,
        dim = DwmPalette.MUTED,
        textTertiary = DwmPalette.MUTED,
        barBg = DwmPalette.SURFACE,
        hairline = DwmPalette.HAIRLINE,
        cardTop = DwmPalette.SURFACE,
        cardBottom = DwmPalette.SURFACE
    )

    /**
     * Day or night.
     *
     * `Prefs.theme` now means 0 auto / 1 day / 2 night rather than a preset index.
     *
     * Auto reads the vehicle. `CarInfo.headlight` is a real signal on this van — the
     * CAN service exposes `getHeadlight` and it answered during the scan — and
     * headlights are a better night detector than either a clock or a light sensor
     * because they are already correct in a tunnel, under a bridge and at dusk, and
     * they cost nothing to read.
     *
     * The fallback when CAN is silent is the clock, not the ambient light sensor.
     * Trinidad sits at about 10.6 degrees north, so sunrise and sunset barely move
     * across the year — a fixed window is accurate to within about half an hour in
     * every month, needs no wake-ups, and cannot flicker under a passing streetlight
     * the way a sensor does.
     */
    fun night(c: Context): Boolean = when (Prefs.theme(c)) {
        1 -> false
        2 -> true
        else -> CarInfo.headlight ?: clockSaysNight()
    }

    private fun clockSaysNight(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 18 || h < 6
    }

    // Every palette colour that may appear on a TextView, so skin() can remap it to
    // the live variant no matter which palette painted the view last. The current
    // values are listed alongside the abandoned ones because a view may have been
    // painted before a day/night switch, and because the layouts still carry
    // hardcoded literals until they are rebuilt.
    //
    // A colour missing from these sets is silently left alone, which is not an
    // obvious failure mode: Settings' section headers were #8E8E93, in neither set,
    // and so stayed mid-grey through every theme the app ever had.
    private val TEXT_COLORS = setOf(
        DwmPalette.TEXT, DwmPalette.N_TEXT,
        0xFFF2F2F2.toInt(), 0xFF171A20.toInt(), 0xFFFFFFFF.toInt()
    )
    private val DIM_COLORS = setOf(
        DwmPalette.MUTED, DwmPalette.N_MUTED,
        0xFF8E8E93.toInt(), 0xFF9A9AA0.toInt(), 0xFFA5A8AD.toInt(),
        0xFF5C5E62.toInt(), 0xFF93A1A6.toInt(), 0xFFA8B0BC.toInt()
    )

    /**
     * The accent, which is a token and not a preference. `Prefs.accent` is no longer
     * consulted — a colour that carries a meaning cannot also be a matter of taste.
     * The debug tweak panel can still move it live; that is a design tool, gated on
     * `BuildConfig.DEBUG`, not a user setting.
     */
    fun accent(c: Context) = if (night(c)) DwmPalette.N_ACCENT else DwmPalette.ACCENT

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

    /**
     * Semantic green, for the View screens.
     *
     * This is a *vehicle* colour and not an interface one. It says a reading is in
     * range or a system is live; it never means "selected", "active" or "on" for
     * anything DWM itself owns. The green pill under the old bottom bar's Overlays
     * icon was exactly that misuse, and it was one of three colours on the home
     * screen all competing to be the meaningful one.
     */
    const val GREEN = DwmPalette.OK

    /** Dashboard tile: filled rounded square with ripple. */
    fun tileBg(c: Context, fill: Int): Drawable {
        val r = dp(c, 18).toFloat()
        return android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(withAlpha(0xFFFFFFFF.toInt(), 0x33)),
            rounded(fill, r, 0, 0),
            rounded(0xFFFFFFFF.toInt(), r, 0, 0)
        )
    }

    /** Big accent-filled primary button (hero "Launch"). */
    fun primaryBtnBg(c: Context): Drawable {
        val a = accent(c)
        val r = dp(c, 14).toFloat()
        return android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(withAlpha(0xFFFFFFFF.toInt(), 0x40)),
            rounded(a, r, 0, 0),
            rounded(0xFFFFFFFF.toInt(), r, 0, 0)
        )
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

    /** Configure a panel WebView. When [mute] is on, autoplay is blocked and any
     *  media is muted on load so DWM panels never grab audio from CarPlay. */
    fun configureWeb(wv: android.webkit.WebView, mute: Boolean) {
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.useWideViewPort = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.mediaPlaybackRequiresUserGesture = mute
        wv.setBackgroundColor(0x00000000)
        wv.webChromeClient = android.webkit.WebChromeClient()
        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                if (mute) view.evaluateJavascript(
                    "document.querySelectorAll('video,audio').forEach(function(m){m.muted=true;try{m.pause()}catch(e){}});",
                    null
                )
            }
        }
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
