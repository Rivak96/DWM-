package com.dwm.cockpit

import android.graphics.Rect

/**
 * What the stage should be showing, decided in one place.
 *
 * ### Why this is not just lifecycle callbacks
 *
 * The first cut drew the chrome straight from `HomeActivity`'s lifecycle and had three
 * bugs that all came from the same mistake — two independent activities each issuing
 * orders to the window manager, with Android deciding the order they arrive in. So the
 * activities now only report *facts* — home is visible, the box is here, the stage is this
 * app — and this object derives what should be on screen. Same shape as [CameraHost], for
 * the same reason, and testable for the same reason: it talks to a [Chrome] interface
 * rather than to a `WindowManager`.
 *
 * ### The curtain, and why it is gone
 *
 * v0.35.0 covered the live window with a fullscreen `TYPE_APPLICATION_OVERLAY` whenever
 * another DWM screen came forward, because a freeform window floats *above* fullscreen
 * activities and the app drawer would otherwise come up with a live app punched through
 * the middle of it.
 *
 * That window type layers above **every** activity window — including
 * [AppDrawerActivity] and [SettingsActivity], the very screens raising it. On the deck it
 * painted the day background over the whole panel and, being touchable, swallowed every
 * touch including the drawer's own close button; the hardware Home key was the only way
 * out. There is no z-order in which a curtain hides the stage but not the screen it is
 * protecting, so the idea cannot be repaired — only replaced.
 *
 * It is replaced by eviction: DWM relaunches the stage app *without* freeform bounds
 * before starting one of its own screens, which moves that task out of the freeform stack
 * into the ordinary fullscreen one, so the DWM screen lands on top of it normally. Same
 * lever [LaunchEngine.launchFullscreen] already pulled for every other app; DWM's own
 * screens simply were not going through it. See `HomeActivity.openDwmScreen`.
 *
 * ### The rules
 *
 * - The **mask** (header over the system caption, frame over the resize outset) shows
 *   while home is in front and the box has reported a rect.
 * - It stays down once the stage is [evict]ed, which is what happens when DWM sends the
 *   user anywhere else.
 * - A **launch** is owed when the stage app changes or when home comes back — never
 *   because the box moved. See [launchNeeded].
 */
object StageHost {

    /** The drawing half. Implemented by [StageChrome]; faked in tests. */
    interface Chrome {
        fun showMask(bounds: Rect, title: String)
        fun hideMask()
    }

    private var chrome: Chrome? = null

    private var pkg: String? = null
    private var title: String = ""
    private var bounds: Rect? = null
    private var homeVisible = false

    /**
     * True once DWM has sent the user into a different app.
     *
     * The stage is a real task that DWM does not own and cannot close, so "gone" here
     * means *we stop drawing for it and stop relaunching it*. Getting it out from in front
     * of the app the user actually asked for is [LaunchEngine]'s job.
     */
    private var evicted = false

    /**
     * The package the window was last actually started for, or null when a launch is owed.
     *
     * The rect it was started *for* is deliberately not remembered. It used to be, and a
     * changed rect used to force a relaunch on the reasoning that a window cannot be
     * repositioned any other way — but bounds are reported on every layout pass, and
     * `goImmersive()` runs from `onWindowFocusChanged`, so touching the live app and then
     * touching DWM could move the rect by an inset and move it straight back. Each move
     * relaunched the window. That is the "flickers, or kinda resizes then resizes back"
     * reported from the van, and the box does not move on this deck anyway.
     */
    private var launchedPkg: String? = null

    private var maskUp = false

    fun attach(c: Chrome) {
        chrome = c
        apply()
    }

    /** Drop everything — the process is going away or the chrome is being replaced. */
    fun detach() {
        chrome?.hideMask()
        maskUp = false
        chrome = null
    }

    /** null [p] means the box is the app grid. Changing the app forces a relaunch. */
    fun setStage(p: String?, label: String) {
        if (p != pkg) {
            launchedPkg = null
            evicted = false
        }
        pkg = p
        title = label
        apply()
    }

    fun setBounds(r: Rect?) {
        if (bounds == r) return
        bounds = r?.let { Rect(it) }
        apply()
    }

    /**
     * Home's `onStart`/`onStop`, and never `onPause` — see [DwmActivity].
     *
     * Coming back is a fresh start for the stage: whatever the user did in between, the
     * window has to be put back in the box, so this clears [launchedPkg] as well as
     * [evicted]. Only clearing `evicted` was the bug behind the photo of a stage header
     * sitting over an empty card — the launch was never owed again, and the one caller
     * that asks for it (`onGloballyPositioned`) does not necessarily fire when an
     * unchanged layout resumes.
     */
    fun setHomeVisible(visible: Boolean) {
        if (homeVisible == visible) return
        homeVisible = visible
        if (visible) {
            evicted = false
            launchedPkg = null
        }
        apply()
    }

    /**
     * DWM is sending the user somewhere else — stop drawing for the stage.
     *
     * Also clears [launchedPkg], so returning home relaunches into the box rather than
     * assuming the window is still where it was left.
     */
    fun evict() {
        evicted = true
        launchedPkg = null
        apply()
    }

    val stagePkg: String? get() = pkg

    /**
     * The launch this state implies, or null for "nothing to do".
     *
     * Returning a value **commits** to it, so a caller that asks twice does not launch
     * twice. That is what stops the relaunch storm: `onGloballyPositioned` fires on every
     * layout pass and each one asks this question. `HomeActivity.onStart` asks it too,
     * because that pass is not guaranteed to happen at all.
     */
    fun launchNeeded(): Pair<String, Rect>? {
        val p = pkg ?: return null
        val b = bounds ?: return null
        if (evicted || !homeVisible || b.isEmpty) return null
        if (launchedPkg == p) return null
        launchedPkg = p
        return p to Rect(b)
    }

    private fun apply() {
        val c = chrome ?: return
        val live = pkg != null && !evicted
        val box = bounds?.takeIf { !it.isEmpty }
        val wantMask = live && homeVisible && box != null

        if (!wantMask && maskUp) { c.hideMask(); maskUp = false }
        if (wantMask) { c.showMask(box!!, title); maskUp = true }
    }

    /** Test seam — the object is process-global and JVM tests share a process. */
    internal fun resetForTest() {
        chrome = null
        pkg = null
        title = ""
        bounds = null
        homeVisible = false
        evicted = false
        launchedPkg = null
        maskUp = false
    }
}
