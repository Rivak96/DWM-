package com.dwm.cockpit

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * The overlay chrome that makes a freeform window sit still and look built-in.
 *
 * ### What this is for
 *
 * A live app in the home screen's box is a real freeform task (see
 * [LaunchEngine.launchInBox]). Freeform is Android's *floating*-window mode, so the system
 * gives it a caption bar with drag and close handles, and resize handles around its edges.
 * Neither is suppressible: the caption is drawn by the hosted app's own `DecorView` and
 * the handles belong to SystemUI, so nothing DWM can call will remove them.
 *
 * They can, however, be **covered**. `TYPE_APPLICATION_OVERLAY` sits above every app
 * window including freeform ones, and a *touchable* overlay swallows the touch before the
 * window underneath sees it. So:
 *
 * - a **mask** across the top of the stage rect hides the caption bar, and because a
 *   freeform window is dragged *by its caption*, covering it also removes the only grab
 *   handle — the window stops being movable;
 * - a **frame** just outside the other three edges covers the resize outset, so the window
 *   stops being resizable.
 *
 * This is the part v0.29 never had. That attempt tried to dodge the caption by inflating
 * the launch rect by a guessed 32 dp, which overhung the vehicle bar when the guess ran
 * long and cropped the app when it ran short. The guess was the bug; covering is exact,
 * and [Prefs.captionPx] is nudged against the real window rather than assumed.
 *
 * The frame is drawn **outward only**. AOSP puts the freeform resize handles in an outset
 * *outside* the task bounds, so covering outward catches them without stealing a strip of
 * the app's own content — and outward lands on DWM's card padding, painting card colour
 * over card colour, which is invisible.
 *
 * ### The curtain, and why `onPause` must not drive it
 *
 * A freeform window floats above fullscreen activities, so opening [AppDrawerActivity] does
 * **not** cover it — the drawer would come up with a live app punched through it. The
 * [curtain] is a fullscreen opaque overlay that does cover it, raised while another DWM
 * screen is in front.
 *
 * It is driven by the drawer's and Settings' own `onStart`/`onStop`, **never** by
 * `HomeActivity.onPause`. On API 29 multi-window only the focused activity is resumed, so
 * simply *touching the stage app* pauses the home activity — driving the curtain from
 * `onPause` would black out the app the moment you tried to use it.
 *
 * Not a Service: these windows only exist while DWM is on screen, they are owned by the
 * application context so no activity leaks, and a service would add a foreground
 * notification for something with no lifetime of its own.
 */
object StageChrome {

    /** Covers the resize outset. Outward from the stage rect — see the class comment. */
    private const val FRAME_DP = 16

    private val panes = ArrayList<View>()
    private var curtainView: View? = null

    /** The rect the chrome is currently drawn around, so a repeat call can no-op. */
    private var shown: Rect? = null
    private var shownTitle: String? = null

    val isShowing: Boolean get() = shown != null

    private fun wm(c: Context) =
        c.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /**
     * Lay the mask and frame around [bounds] (screen pixels).
     *
     * Idempotent: called again with the same rect it does nothing, which matters because
     * `HomeActivity` re-reports the stage rect on every layout pass.
     */
    fun show(c: Context, bounds: Rect, title: String, onFullscreen: () -> Unit) {
        if (bounds.isEmpty) return
        if (shown == bounds && shownTitle == title) return
        hide(c)

        val app = c.applicationContext
        val theme = Ui.th(app)
        val caption = Prefs.captionPx(app)
        val frame = Ui.dp(app, FRAME_DP)

        // The mask is the one surface drawn *above* the live app, so it is also the only
        // place a control can go — anything placed in the Compose card underneath would be
        // covered by the window it belongs to. So it carries the title and the full-screen
        // button, and the covered system caption is replaced by DWM's own.
        addHeader(app, theme, bounds, caption, title, onFullscreen)
        // Outward frame: left, right, bottom.
        add(app, theme.card, bounds.left - frame, bounds.top, frame, bounds.height())
        add(app, theme.card, bounds.right, bounds.top, frame, bounds.height())
        add(app, theme.card, bounds.left - frame, bounds.bottom, bounds.width() + frame * 2, frame)

        shown = Rect(bounds)
        shownTitle = title
    }

    private fun addHeader(
        c: Context,
        theme: Ui.Theme,
        bounds: Rect,
        height: Int,
        title: String,
        onFullscreen: () -> Unit
    ) {
        if (height <= 0) return
        val pad = Ui.dp(c, 12)
        val row = android.widget.LinearLayout(c).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.card)
            setPadding(pad, 0, pad, 0)
        }
        row.addView(
            android.widget.TextView(c).apply {
                text = title
                setTextColor(theme.dim)
                textSize = 13f
                maxLines = 1
            },
            android.widget.LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            android.widget.TextView(c).apply {
                text = "Full screen"
                setTextColor(theme.text)
                textSize = 13f
                setPadding(pad, pad / 2, pad, pad / 2)
                // The whole strip already swallows touches; this just gives the tap a
                // target and a reason.
                setOnClickListener { onFullscreen() }
            }
        )
        runCatching { wm(c).addView(row, params(bounds.width(), height, bounds.left, bounds.top)) }
            .onSuccess { panes.add(row) }
    }

    fun hide(c: Context) {
        val manager = wm(c)
        panes.forEach { v -> runCatching { manager.removeView(v) } }
        panes.clear()
        shown = null
        shownTitle = null
    }

    /**
     * Raise or drop the fullscreen cover.
     *
     * Opaque, and touchable so the app underneath cannot be poked through it.
     */
    fun curtain(c: Context, on: Boolean) {
        val app = c.applicationContext
        val manager = wm(app)
        if (!on) {
            curtainView?.let { v -> runCatching { manager.removeView(v) } }
            curtainView = null
            return
        }
        if (curtainView != null) return
        val v = View(app).apply { setBackgroundColor(Ui.th(app).bg) }
        val lp = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, 0, 0)
        runCatching { manager.addView(v, lp) }.onSuccess { curtainView = v }
    }

    /** Everything down — chrome and curtain both. */
    fun clear(c: Context) {
        curtain(c, false)
        hide(c)
    }

    private fun add(c: Context, color: Int, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val v = View(c).apply { setBackgroundColor(color) }
        runCatching { wm(c).addView(v, params(w, h, x, y)) }.onSuccess { panes.add(v) }
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
