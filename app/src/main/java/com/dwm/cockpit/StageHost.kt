package com.dwm.cockpit

import android.graphics.Rect

/**
 * What the stage should be showing, decided in one place.
 *
 * ### Why this is not just lifecycle callbacks
 *
 * The first cut drew the chrome straight from `HomeActivity`'s lifecycle and had three
 * bugs that all came from the same mistake — two independent activities each issuing
 * orders to the window manager, with Android deciding the order they arrive in.
 *
 * 1. **The curtain was torn down the instant it went up.** Starting the drawer runs
 *    `AppDrawerActivity.onStart` *before* `HomeActivity.onStop`, so the drawer raised the
 *    curtain and home then cleared it — and the drawer ran with a live app floating
 *    through the middle of it.
 * 2. **The mask did not come back.** Home cleared it on `onStop` and only redrew it from
 *    `onGloballyPositioned`, which does not necessarily fire again when an unchanged
 *    layout resumes. The window came back with its system caption exposed and draggable.
 * 3. **Relaunch storms.** Every reported bound triggered a relaunch, and bounds are
 *    reported on every layout pass.
 *
 * So the activities now only report *facts* — home is visible, a DWM screen is in front,
 * the box is here, the stage is this app — and this object derives what should be on
 * screen. Same shape as [CameraHost], for the same reason, and testable for the same
 * reason: it talks to a [Chrome] interface rather than to a `WindowManager`.
 *
 * ### The rules
 *
 * - The **mask** (header over the system caption, frame over the resize outset) shows only
 *   while home is in front and the box has reported a rect.
 * - The **curtain** (fullscreen cover) shows while another DWM screen is in front. A
 *   freeform window floats above fullscreen activities, so without it the drawer and
 *   Settings come up with the live app on top of them.
 * - Both stay down once the stage is [evict]ed, which is what happens when DWM sends the
 *   user into some *other* app.
 */
object StageHost {

    /** The drawing half. Implemented by [StageChrome]; faked in tests. */
    interface Chrome {
        fun showMask(bounds: Rect, title: String)
        fun hideMask()
        fun showCurtain()
        fun hideCurtain()
    }

    private var chrome: Chrome? = null

    private var pkg: String? = null
    private var title: String = ""
    private var bounds: Rect? = null
    private var homeVisible = false
    private var dwmScreenInFront = false

    /**
     * True once DWM has sent the user into a different app.
     *
     * The stage is a real task that DWM does not own and cannot close, so "gone" here
     * means *we stop drawing for it and stop relaunching it*. Getting it out from in front
     * of the app the user actually asked for is [LaunchEngine]'s job — see
     * `HomeActivity.openOther`.
     */
    private var evicted = false

    /** Bounds the window was last actually started for, so layout churn cannot relaunch. */
    private var launchedFor: Rect? = null
    private var launchedPkg: String? = null

    private var maskUp = false
    private var curtainUp = false

    fun attach(c: Chrome) {
        chrome = c
        apply()
    }

    /** Drop everything — the process is going away or the chrome is being replaced. */
    fun detach() {
        chrome?.hideMask()
        chrome?.hideCurtain()
        maskUp = false
        curtainUp = false
        chrome = null
    }

    /** null [p] means the box is the app grid. Changing the app forces a relaunch. */
    fun setStage(p: String?, label: String) {
        if (p != pkg) {
            launchedPkg = null
            launchedFor = null
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

    fun setHomeVisible(visible: Boolean) {
        if (homeVisible == visible) return
        homeVisible = visible
        // Coming back to home is a fresh start for the stage: whatever the user did in
        // between, the window has to be put back in the box.
        if (visible) evicted = false
        apply()
    }

    fun setDwmScreenInFront(inFront: Boolean) {
        if (dwmScreenInFront == inFront) return
        dwmScreenInFront = inFront
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
        launchedFor = null
        apply()
    }

    val stagePkg: String? get() = pkg

    /**
     * The launch this state implies, or null for "nothing to do".
     *
     * Returning a value **commits** to it, so a caller that asks twice does not launch
     * twice. That is what stops the relaunch storm: `onGloballyPositioned` fires on every
     * layout pass and each one asks this question.
     */
    fun launchNeeded(): Pair<String, Rect>? {
        val p = pkg ?: return null
        val b = bounds ?: return null
        if (evicted || !homeVisible || b.isEmpty) return null
        if (launchedPkg == p && launchedFor == b) return null
        launchedPkg = p
        launchedFor = Rect(b)
        return p to Rect(b)
    }

    private fun apply() {
        val c = chrome ?: return
        val live = pkg != null && !evicted

        val box = bounds?.takeIf { !it.isEmpty }
        val wantCurtain = live && dwmScreenInFront
        val wantMask = live && homeVisible && !dwmScreenInFront && box != null

        // Curtain first when raising and last when lowering, so there is never a frame
        // with the live window uncovered between the two.
        if (wantCurtain && !curtainUp) { c.showCurtain(); curtainUp = true }
        if (!wantMask && maskUp) { c.hideMask(); maskUp = false }
        if (wantMask) { c.showMask(box!!, title); maskUp = true }
        if (!wantCurtain && curtainUp) { c.hideCurtain(); curtainUp = false }
    }

    /** Test seam — the object is process-global and JVM tests share a process. */
    internal fun resetForTest() {
        chrome = null
        pkg = null
        title = ""
        bounds = null
        homeVisible = false
        dwmScreenInFront = false
        evicted = false
        launchedFor = null
        launchedPkg = null
        maskUp = false
        curtainUp = false
    }
}
