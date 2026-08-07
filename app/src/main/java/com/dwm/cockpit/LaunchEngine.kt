package com.dwm.cockpit

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Launches apps. Fullscreen, and only fullscreen.
 *
 * ### The freeform path, and why it is gone
 *
 * This object used to offer a second mode: `launchWindow`, which started an app into a
 * given [android.graphics.Rect] using `ActivityOptions.launchBounds` plus a reflectively
 * set `setLaunchWindowingMode(WINDOWING_MODE_FREEFORM)`. That is the only way an
 * unprivileged launcher can put another app inside a rectangle — `TaskView`, a trusted
 * `VirtualDisplay` and programmatic split-screen all need signature permissions or adb,
 * which this project has ruled out — and it was how the home screen's stage worked.
 *
 * On the deck it produced three faults, and all three belong to SystemUI rather than to
 * DWM, so none of them could be reached from here:
 *
 * 1. **A caption bar** with drag and close handles, drawn *inside* the requested bounds.
 *    There is no flag to suppress it. It was hidden by inflating the rect by a guessed
 *    32dp (`Prefs.captionComp`), which overhung the vehicle bar when the guess ran long
 *    and cropped the app's own content when it ran short.
 * 2. **Drag and resize**, because a freeform window is a user-movable window by
 *    definition. The app would not stay where it was put.
 * 3. **Sinking behind the launcher**, on a z-order the system owns.
 *
 * Freeform *is* Android's floating-window mode. "Fix the floating" and "keep the live
 * window" were the same request pulling in opposite directions, so the window went: the
 * stage now draws a card for the chosen app and opens it with [launchFullscreen].
 *
 * Nothing in DWM launches an app any other way, and nothing should. A plain
 * `startActivity` with `NEW_TASK` has no bounds to get wrong, no reflection to fail on a
 * ROM that moves, and no window for the system to reposition out from under the user.
 */
object LaunchEngine {

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

    /** NOTE: never add FLAG_ACTIVITY_MULTIPLE_TASK here — it spawns duplicate copies of
     *  the app on every call, which the low-RAM deck then kills ("apps closing by
     *  themselves"). NEW_TASK alone reuses one task, which is also what makes returning
     *  to a running app instant rather than a cold start. */
    fun launchFullscreen(c: Context, pkg: String) {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(i) }
    }

    /**
     * Launch the saved layout's base app.
     *
     * Once upon a time this staggered a fullscreen base app and then a set of freeform
     * windows on top of it, 400ms apart, hence the [Handler]. With the windows gone
     * there is at most one app to open, and the delay survives only because the callers
     * (`onStart` autoload, the reload action) fire during layout.
     */
    fun launchLayout(c: Context, panels: List<Panel>) {
        val base = panels.firstOrNull { it.isFullscreenApp() }?.pkg ?: return
        Handler(Looper.getMainLooper()).post { launchFullscreen(c, base) }
    }
}
