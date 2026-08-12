package com.dwm.cockpit

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.dwm.cockpit.ui.theme.DwmPalette

/**
 * The overlay chrome that makes a freeform window sit still and look built-in.
 *
 * The drawing half of [StageHost] — *what* to show is decided there, this only puts it on
 * screen. Anything that looks like policy belongs in the host, which is testable.
 *
 * ### What this is for
 *
 * A live app in the home screen's box is a real freeform task (see
 * [LaunchEngine.launchInBox]). Freeform is Android's *floating*-window mode, so the system
 * gives it a caption bar with drag and close handles, and resize handles around its edges.
 * Neither is suppressible: the caption is drawn by the hosted app's own `DecorView` and
 * the handles belong to SystemUI, so nothing DWM can call removes them.
 *
 * They can be **covered**. `TYPE_APPLICATION_OVERLAY` sits above every app window
 * including freeform ones, and a *touchable* overlay swallows the touch before the window
 * underneath sees it. So:
 *
 * - a **header** across the top of the stage rect hides the caption, and because a
 *   freeform window is dragged *by its caption*, covering it also removes the only grab
 *   handle — the window stops being movable;
 * - a **frame** just outside the other three edges covers the resize outset, so the window
 *   stops being resizable.
 *
 * This is the part v0.29 never had. That attempt dodged the caption by inflating the launch
 * rect by a guessed 32 dp, which overhung the vehicle bar when the guess ran long and
 * cropped the app when it ran short. The guess was the bug; covering is exact, and
 * [Prefs.captionDp] is nudged against the real window rather than assumed.
 *
 * The frame is drawn **outward only**. AOSP puts the freeform resize handles in an outset
 * *outside* the task bounds, so covering outward catches them without stealing a strip of
 * the app's own content — and outward lands on DWM's card padding, painting card colour
 * over card colour, which is invisible.
 *
 * The header is also the only place a control can go: anything drawn in the Compose card
 * is *underneath* the window it belongs to. So it carries the title and the full-screen
 * button, and the system's caption is replaced rather than merely hidden.
 *
 * ### The one thing this must not grow back
 *
 * There was a fullscreen "curtain" here as well, raised whenever another DWM screen came
 * forward. `TYPE_APPLICATION_OVERLAY` is above *every* activity window, DWM's own
 * included, so it covered the app drawer it was meant to protect and ate its touches with
 * it. [StageHost]'s header explains what replaced it. Nothing in this class may go
 * fullscreen.
 */
class StageChrome(
    context: Context,
    private val onFullscreen: () -> Unit
) : StageHost.Chrome {

    private companion object {
        /** Covers the resize outset. Outward from the stage rect — see the class comment. */
        const val FRAME_DP = 16

        /** Thick enough to read in a photograph taken through a windscreen at night. */
        const val OUTLINE_DP = 2
    }

    private val app = context.applicationContext
    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var header: View? = null
    private var titleView: TextView? = null

    /** Left, right and bottom, in that order. Held so they can be moved, not rebuilt. */
    private val edges = ArrayList<View>()

    /** The two diagnostic rectangles. Null unless [Prefs.stageOutline] is on. */
    private var outlineBox: View? = null
    private var outlineLaunch: View? = null

    private var shown: Rect? = null
    private var shownTitle: String? = null

    /**
     * Put the mask over [bounds], or move it if it is already up.
     *
     * Idempotent, because the box re-reports its rect on every layout pass. **Moving**
     * rather than rebuilding matters as well: removing four overlay windows and adding
     * four more flashes the header and the frame, and the rect can be reported many times
     * a second while the user is working in the app.
     */
    override fun showMask(bounds: Rect, title: String) {
        if (bounds.isEmpty) return
        if (shown == bounds && shownTitle == title) return

        val caption = Prefs.captionPx(app)
        val frame = Ui.dp(app, FRAME_DP)

        // A caption height of zero means no header window at all, so the set of windows
        // itself changes when the user nudges it through zero — the only case that has to
        // rebuild rather than move.
        val intact = edges.size == 3 && (caption > 0) == (header != null)
        if (intact) {
            titleView?.text = title
            header?.let { move(it, bounds.left, bounds.top, bounds.width(), caption) }
            move(edges[0], bounds.left - frame, bounds.top, frame, bounds.height())
            move(edges[1], bounds.right, bounds.top, frame, bounds.height())
            move(edges[2], bounds.left - frame, bounds.bottom, bounds.width() + frame * 2, frame)
        } else {
            hideMask()
            val theme = Ui.th(app)
            addHeader(theme, bounds, caption, title)
            add(theme.card, bounds.left - frame, bounds.top, frame, bounds.height())
            add(theme.card, bounds.right, bounds.top, frame, bounds.height())
            add(theme.card, bounds.left - frame, bounds.bottom, bounds.width() + frame * 2, frame)
        }

        // Last, so it stacks above the mask — overlays layer in the order they are added,
        // and the point of the outline is to be visible over everything.
        updateOutlines(bounds, caption)

        shown = Rect(bounds)
        shownTitle = title
    }

    /**
     * The ruler: draw what DWM *asked for*, over what the ROM actually did.
     *
     * Android gives an unprivileged app no way to read another app's window frame, so when
     * the owner reports that the live window "overlaps the right column" there is no way to
     * learn by how much — or whether the ROM honoured the request at all. Seven releases
     * have aimed fixes at a frame nobody has measured.
     *
     * Two rectangles, because the interesting thing is the gap between them and the app:
     *
     * - **accent** — the box, where the app's *content* is meant to end up;
     * - **warn** — the rect actually handed to `setLaunchBounds`, which is the box grown
     *   upward by the caption ([StageHost.launchRectFor]).
     *
     * One photograph then carries all three frames — asked, intended, actual — and every
     * offset can be read straight off it. Off by default; this is scaffolding, not design,
     * which is why it may use the semantic colours without breaking the one-accent rule.
     */
    private fun updateOutlines(bounds: Rect, caption: Int) {
        if (!Prefs.stageOutline(app)) {
            hideOutlines()
            return
        }
        val night = Ui.night(app)
        val boxColor = if (night) DwmPalette.N_ACCENT else DwmPalette.ACCENT
        val launchColor = if (night) DwmPalette.N_WARN else DwmPalette.WARN
        val launch = StageHost.launchRectFor(bounds, caption)

        outlineBox = outline(outlineBox, bounds, boxColor)
        outlineLaunch = outline(outlineLaunch, launch, launchColor)
    }

    /**
     * One hollow rectangle, moved rather than rebuilt when it already exists.
     *
     * `TRANSLUCENT` and **`FLAG_NOT_TOUCHABLE`** — the opposite of the mask, and
     * deliberately so. The mask exists to swallow touches; this exists to be looked at, and
     * a diagnostic that stole input from the app it is measuring would change the thing it
     * is meant to observe.
     */
    private fun outline(existing: View?, r: Rect, color: Int): View? {
        if (r.isEmpty) return existing
        val lp = params(
            r.width(), r.height(), r.left, r.top,
            format = PixelFormat.TRANSLUCENT,
            extraFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        existing?.let { v ->
            runCatching { wm.updateViewLayout(v, lp) }.onSuccess { return v }
        }
        val v = View(app).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(Ui.dp(app, OUTLINE_DP), color)
            }
        }
        return runCatching { wm.addView(v, lp); v }.getOrNull()
    }

    private fun hideOutlines() {
        outlineBox?.let { v -> runCatching { wm.removeView(v) } }
        outlineLaunch?.let { v -> runCatching { wm.removeView(v) } }
        outlineBox = null
        outlineLaunch = null
    }

    override fun hideMask() {
        header?.let { v -> runCatching { wm.removeView(v) } }
        edges.forEach { v -> runCatching { wm.removeView(v) } }
        hideOutlines()
        header = null
        titleView = null
        edges.clear()
        shown = null
        shownTitle = null
    }

    private fun addHeader(theme: Ui.Theme, bounds: Rect, height: Int, title: String) {
        if (height <= 0) return
        val pad = Ui.dp(app, 12)
        val label = TextView(app).apply {
            text = title
            setTextColor(theme.dim)
            textSize = 13f
            maxLines = 1
        }
        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.card)
            setPadding(pad, 0, pad, 0)
        }
        row.addView(
            label,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            TextView(app).apply {
                text = "Full screen"
                setTextColor(theme.text)
                textSize = 13f
                setPadding(pad, pad / 2, pad, pad / 2)
                setOnClickListener { onFullscreen() }
            }
        )
        runCatching { wm.addView(row, params(bounds.width(), height, bounds.left, bounds.top)) }
            .onSuccess { header = row; titleView = label }
    }

    private fun add(color: Int, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val v = View(app).apply { setBackgroundColor(color) }
        runCatching { wm.addView(v, params(w, h, x, y)) }.onSuccess { edges.add(v) }
    }

    private fun move(v: View, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        runCatching { wm.updateViewLayout(v, params(w, h, x, y)) }
    }

    /**
     * `FLAG_NOT_FOCUSABLE` so DWM never steals key input, but **not**
     * `FLAG_NOT_TOUCHABLE` — swallowing the touch is the entire point. Touch pass-through
     * is a property of the window, not of the view, so a plain `View` that handles nothing
     * still blocks the freeform window beneath it.
     */
    private fun params(
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        format: Int = PixelFormat.OPAQUE,
        extraFlags: Int = 0
    ) = WindowManager.LayoutParams(
        w, h,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            extraFlags,
        format
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }
}
