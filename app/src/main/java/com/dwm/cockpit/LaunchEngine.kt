package com.dwm.cockpit

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Launches apps — fullscreen, or into a freeform window at given bounds.
 * This deck's ROM supports third-party freeform (confirmed on device), so
 * launchBounds + freeform mode opens apps as floating windows.
 */
object LaunchEngine {
    private const val WINDOWING_MODE_FREEFORM = 5

    fun displaySize(c: Context): Point {
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            Point(dm.widthPixels, dm.heightPixels)
        }
    }

    fun launchFullscreen(c: Context, pkg: String) {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(i) }
    }

    /** NOTE: never add FLAG_ACTIVITY_MULTIPLE_TASK here — it spawns duplicate
     *  copies of the app on every reload, which the low-RAM deck then kills
     *  ("apps closing by themselves") and old fullscreen tasks get reused
     *  ("sometimes opens fullscreen"). NEW_TASK alone reuses one task. */
    fun launchWindow(c: Context, pkg: String, bounds: Rect) {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(i, freeformOptions(compensate(c, bounds)).toBundle()) }
    }

    /** The system draws a caption/title bar INSIDE freeform bounds, shrinking the
     *  app's content area — projection apps (CarPlay) then crop their bottom.
     *  Grow the bounds by the caption height so visible content matches the slot;
     *  if that runs off-screen, shift the window up instead. */
    private fun compensate(c: Context, bounds: Rect): Rect {
        val dp = Prefs.captionComp(c)
        if (dp <= 0) return bounds
        val px = (dp * c.resources.displayMetrics.density).toInt()
        val screenH = displaySize(c).y
        val r = Rect(bounds)
        r.bottom += px
        val overflow = r.bottom - screenH
        if (overflow > 0) {
            r.bottom = screenH
            r.top = (r.top - overflow).coerceAtLeast(0)
        }
        return r
    }

    private fun freeformOptions(bounds: Rect): ActivityOptions {
        val opts = ActivityOptions.makeBasic()
        runCatching { opts.launchBounds = bounds }
        runCatching {
            val m = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode", Int::class.javaPrimitiveType
            )
            m.invoke(opts, WINDOWING_MODE_FREEFORM)
        }
        return opts
    }

    /** Brings every windowed-app panel back above the fullscreen base app —
     *  freeform windows sink behind it whenever the base app is tapped. */
    fun raiseWindows(c: Context, panels: List<Panel>) {
        val wins = panels.filter { it.isWindowedApp() }
        if (wins.isEmpty()) return
        val size = displaySize(c)
        val h = Handler(Looper.getMainLooper())
        wins.forEachIndexed { idx, p ->
            val rect = Rect(
                (p.l * size.x).toInt(), (p.t * size.y).toInt(),
                (p.r * size.x).toInt(), (p.b * size.y).toInt()
            )
            h.postDelayed({ launchWindow(c, p.pkg!!, rect) }, idx * 200L)
        }
    }

    /** Launches the saved layout: fullscreen base apps first (e.g. CarPlay), then
     *  the freeform windows staggered AFTER so they stack on top of the base. */
    fun launchLayout(c: Context, panels: List<Panel>) {
        val h = Handler(Looper.getMainLooper())
        val base = panels.filter { it.isFullscreenApp() }
        base.forEachIndexed { idx, p ->
            h.postDelayed({ launchFullscreen(c, p.pkg!!) }, idx * 300L)
        }
        val wins = panels.filter { it.isWindowedApp() }
        if (wins.isEmpty()) return
        val size = displaySize(c)
        val offset = if (base.isEmpty()) 0L else 900L
        wins.forEachIndexed { idx, p ->
            val rect = Rect(
                (p.l * size.x).toInt(), (p.t * size.y).toInt(),
                (p.r * size.x).toInt(), (p.b * size.y).toInt()
            )
            h.postDelayed({ launchWindow(c, p.pkg!!, rect) }, offset + idx * 400L)
        }
    }
}
