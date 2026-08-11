package com.dwm.cockpit

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

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
 */
class StageChrome(
    context: Context,
    private val onFullscreen: () -> Unit
) : StageHost.Chrome {

    /** Covers the resize outset. Outward from the stage rect — see the class comment. */
    private companion object {
        const val FRAME_DP = 16
    }

    private val app = context.applicationContext
    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val panes = ArrayList<View>()
    private var curtainView: View? = null
    private var shown: Rect? = null
    private var shownTitle: String? = null

    override fun showMask(bounds: Rect, title: String) {
        if (bounds.isEmpty) return
        // Idempotent: the box re-reports its rect on every layout pass, and tearing the
        // windows down and rebuilding them each time would flicker.
        if (shown == bounds && shownTitle == title) return
        hideMask()

        val theme = Ui.th(app)
        val caption = Prefs.captionPx(app)
        val frame = Ui.dp(app, FRAME_DP)

        addHeader(theme, bounds, caption, title)
        add(theme.card, bounds.left - frame, bounds.top, frame, bounds.height())
        add(theme.card, bounds.right, bounds.top, frame, bounds.height())
        add(theme.card, bounds.left - frame, bounds.bottom, bounds.width() + frame * 2, frame)

        shown = Rect(bounds)
        shownTitle = title
    }

    override fun hideMask() {
        panes.forEach { v -> runCatching { wm.removeView(v) } }
        panes.clear()
        shown = null
        shownTitle = null
    }

    /** Opaque, and touchable so the app underneath cannot be poked through it. */
    override fun showCurtain() {
        if (curtainView != null) return
        val v = View(app).apply { setBackgroundColor(Ui.th(app).bg) }
        val lp = params(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, 0, 0
        )
        runCatching { wm.addView(v, lp) }.onSuccess { curtainView = v }
    }

    override fun hideCurtain() {
        curtainView?.let { v -> runCatching { wm.removeView(v) } }
        curtainView = null
    }

    private fun addHeader(theme: Ui.Theme, bounds: Rect, height: Int, title: String) {
        if (height <= 0) return
        val pad = Ui.dp(app, 12)
        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.card)
            setPadding(pad, 0, pad, 0)
        }
        row.addView(
            TextView(app).apply {
                text = title
                setTextColor(theme.dim)
                textSize = 13f
                maxLines = 1
            },
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
            .onSuccess { panes.add(row) }
    }

    private fun add(color: Int, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val v = View(app).apply { setBackgroundColor(color) }
        runCatching { wm.addView(v, params(w, h, x, y)) }.onSuccess { panes.add(v) }
    }

    /**
     * `FLAG_NOT_FOCUSABLE` so DWM never steals key input, but **not**
     * `FLAG_NOT_TOUCHABLE` — swallowing the touch is the entire point. Touch pass-through
     * is a property of the window, not of the view, so a plain `View` that handles nothing
     * still blocks the freeform window beneath it.
     */
    private fun params(w: Int, h: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        w, h,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }
}
