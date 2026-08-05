package com.dwm.cockpit

import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * Exactly one WebView runs at a time.
 *
 * ### Why this exists
 *
 * Until now DWM had **no WebView lifecycle at all** — a repo-wide search for
 * `onPause` or `pauseTimers` found nothing. A web panel was created and, eventually,
 * destroyed; in between it kept its timers, its JavaScript and any video running
 * whether or not it was on screen, and whether or not the launcher was even in the
 * foreground. On a Unisoc SC9863A with a Mali-G51 and very little RAM, two live
 * WebViews is the difference between a cockpit and a slideshow.
 *
 * It became reachable rather than theoretical when panes arrived: a pane can hold a
 * web dashboard, panes can be swiped, and there can be three of them.
 *
 * ### What it does
 *
 * [resumeOnly] resumes the one WebView that is visible and holds every other at
 * `onPause()` + `pauseTimers()`. A paused WebView keeps its last rendered frame, so a
 * pane swiped away from and back to shows its content immediately rather than
 * reloading — which also saves the data.
 *
 * `pauseTimers()` is documented as global to the whole application, so it is only
 * ever called when *nothing* should be running, and `resumeTimers()` is called for
 * the active one. Getting that pair the wrong way round silently freezes every
 * WebView in the process, including one that is meant to be visible.
 *
 * References are weak. A panel's View can be torn down by a layout change at any
 * moment and this must not be the thing that keeps it alive.
 */
object WebPanelHost {

    private val views = mutableListOf<WeakReference<WebView>>()

    fun register(w: WebView) {
        prune()
        if (views.none { it.get() === w }) views += WeakReference(w)
    }

    /**
     * Resume [active] and pause everything else. Pass `null` to pause all of them —
     * which is what happens when the launcher goes to the background.
     */
    fun resumeOnly(active: WebView?) {
        prune()
        val live = views.mapNotNull { it.get() }
        live.forEach { w ->
            if (w === active) {
                runCatching { w.onResume() }
            } else {
                runCatching { w.onPause() }
            }
        }
        // Timers are process-wide, so they are only stopped when nothing at all
        // should be ticking.
        runCatching {
            if (active != null) active.resumeTimers() else live.firstOrNull()?.pauseTimers()
        }
    }

    fun pauseAll() = resumeOnly(null)

    /** Forget a WebView that has been destroyed by its owner. */
    fun forget(w: WebView) {
        views.removeAll { it.get() == null || it.get() === w }
    }

    private fun prune() {
        views.removeAll { it.get() == null }
    }
}
