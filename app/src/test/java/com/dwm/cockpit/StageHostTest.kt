package com.dwm.cockpit

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The stage's visibility rules, on the JVM.
 *
 * Every case here is a bug this feature actually had — first a set of ordering faults
 * between two activities issuing orders to the window manager independently, then the three
 * the owner found in the van. Both classes are invisible to a golden and show on the deck
 * only as "it's buggy". Testable at all because [StageHost] talks to a [StageHost.Chrome]
 * rather than to a real `WindowManager` — the same trick `CameraHostTest` uses.
 */
class StageHostTest {

    private class FakeChrome : StageHost.Chrome {
        var mask: Rect? = null
        var maskTitle: String? = null
        /** Ordered log, because *when* each window appears is the thing under test. */
        val events = ArrayList<String>()

        override fun showMask(bounds: Rect, title: String) {
            mask = Rect(bounds); maskTitle = title; events.add("mask+")
        }

        override fun hideMask() {
            mask = null; maskTitle = null; events.add("mask-")
        }
    }

    private val box = Rect(40, 100, 1400, 800)
    private lateinit var chrome: FakeChrome

    @Before
    fun setUp() {
        StageHost.resetForTest()
        chrome = FakeChrome()
        StageHost.attach(chrome)
    }

    /** The ordinary case: home in front with an app configured. */
    private fun goLive() {
        StageHost.setStage("com.zjinnova.zlink", "CarPlay")
        StageHost.setHomeVisible(true)
        StageHost.setBounds(box)
    }

    @Test
    fun `no stage configured draws nothing`() {
        StageHost.setHomeVisible(true)
        StageHost.setBounds(box)
        assertNull(chrome.mask)
    }

    @Test
    fun `home in front with a stage shows the mask at the box`() {
        goLive()
        assertEquals(box, chrome.mask)
        assertEquals("CarPlay", chrome.maskTitle)
    }

    @Test
    fun `leaving home takes the mask down`() {
        goLive()
        StageHost.setHomeVisible(false)
        assertNull(chrome.mask)
    }

    /**
     * Coming back, the mask was once only redrawn from `onGloballyPositioned`, which does
     * not necessarily fire again for an unchanged layout. The window returned with its
     * system caption exposed and draggable.
     */
    @Test
    fun `mask returns from remembered bounds without a new layout pass`() {
        goLive()
        StageHost.setHomeVisible(false)
        StageHost.setHomeVisible(true)   // no setBounds — nothing re-laid out
        assertEquals(box, chrome.mask)
    }

    /** `onGloballyPositioned` fires on every layout pass; each one asks for a launch. */
    @Test
    fun `repeated identical bounds launch exactly once`() {
        goLive()
        assertEquals("com.zjinnova.zlink" to box, StageHost.launchNeeded())
        assertNull(StageHost.launchNeeded())
        StageHost.setBounds(box)
        assertNull(StageHost.launchNeeded())
    }

    /**
     * A window cannot be repositioned any other way, so a box that ends up somewhere else
     * owes a relaunch.
     *
     * v0.37.0 dropped this, keying the launch on the package alone to stop bounds churn
     * relaunching the window. It went too far: the box reports its rect several times while
     * the screen settles — the first pass runs before `goImmersive()` has taken the system
     * bars out of the window — so the **unsettled** rect won permanently and the window was
     * placed against a box that was not where it ended up. The owner saw an empty box with
     * its own placeholder in it.
     *
     * Churn is answered by `HomeActivity` waiting for the layout to stop moving before it
     * asks, which is a question about time rather than about state and does not belong here.
     */
    @Test
    fun `a box that settled somewhere else relaunches, and the mask follows`() {
        goLive()
        StageHost.launchNeeded()
        val moved = Rect(40, 120, 1400, 820)
        StageHost.setBounds(moved)
        assertEquals("com.zjinnova.zlink" to moved, StageHost.launchNeeded())
        assertEquals(moved, chrome.mask)
    }

    /**
     * A committed launch that never went out must not leave the box empty until the user
     * happens to leave home and come back.
     */
    @Test
    fun `a refused launch is owed again`() {
        goLive()
        assertEquals("com.zjinnova.zlink" to box, StageHost.launchNeeded())
        StageHost.launchFailed()
        assertEquals("com.zjinnova.zlink" to box, StageHost.launchNeeded())
    }

    @Test
    fun `nothing launches while home is hidden`() {
        StageHost.setStage("com.zjinnova.zlink", "CarPlay")
        StageHost.setBounds(box)
        assertNull(StageHost.launchNeeded())
    }

    /**
     * Opening another app must take the stage down. Freeform floats above fullscreen, so a
     * stage left drawing would sit on top of the app the user actually asked for.
     */
    @Test
    fun `evicting hides everything and stops relaunching`() {
        goLive()
        StageHost.launchNeeded()
        StageHost.evict()
        assertNull(chrome.mask)
        assertNull(StageHost.launchNeeded())
    }

    /** ...and coming home afterwards puts it back, rather than assuming it is still there. */
    @Test
    fun `returning home after an eviction relaunches into the box`() {
        goLive()
        StageHost.launchNeeded()
        StageHost.evict()
        StageHost.setHomeVisible(false)
        StageHost.setHomeVisible(true)
        assertEquals("com.zjinnova.zlink" to box, StageHost.launchNeeded())
        assertEquals(box, chrome.mask)
    }

    /**
     * The photo of a stage header sitting over an empty card.
     *
     * `setHomeVisible(true)` cleared `evicted` and claimed to be "a fresh start for the
     * stage", but left the launch marked as already done — so nothing put the window back,
     * and the one caller that would have asked does not necessarily fire for an unchanged
     * layout. Note there is no `evict()` here: this is the plain case of leaving home and
     * coming back, where DWM never sent the user anywhere.
     */
    @Test
    fun `returning home relaunches even when nothing evicted the stage`() {
        goLive()
        assertEquals("com.zjinnova.zlink" to box, StageHost.launchNeeded())
        StageHost.setHomeVisible(false)
        StageHost.setHomeVisible(true)
        assertEquals("com.zjinnova.zlink" to box, StageHost.launchNeeded())
    }

    /**
     * Touching the live app pauses `HomeActivity` on API 29 — only the focused activity is
     * resumed in multi-window. The chrome must not react, or the header vanishes the moment
     * you try to use the app. Modelled here as "no signal arrives", which is the contract:
     * home reports from onStart/onStop, never onPause.
     */
    @Test
    fun `mask stays up while the user works in the app`() {
        goLive()
        chrome.events.clear()
        StageHost.setBounds(box)
        assertEquals(box, chrome.mask)
        assertTrue("nothing may be torn down", chrome.events.none { it == "mask-" })
    }

    @Test
    fun `clearing the stage puts the box back to the grid`() {
        goLive()
        StageHost.setStage(null, "")
        assertNull(chrome.mask)
        assertNull(StageHost.launchNeeded())
    }

    @Test
    fun `changing the app relaunches for the new one`() {
        goLive()
        StageHost.launchNeeded()
        StageHost.setStage("com.waze", "Waze")
        assertEquals("com.waze" to box, StageHost.launchNeeded())
        assertEquals("Waze", chrome.maskTitle)
    }

    /**
     * The leak that walled the launcher off from its own overlays.
     *
     * `StageChrome` draws through the application WindowManager, so its windows outlive the
     * activity, and `HomeActivity.stageChrome` is one instance per activity. A recreate
     * built a second chrome and attached it here — and the first one's four opaque,
     * touch-consuming overlays were orphaned with nothing left to remove them, stacked above
     * everything drawn later including the floating camera panel.
     */
    @Test
    fun `attaching a new chrome takes the old one down`() {
        goLive()
        assertEquals(box, chrome.mask)

        val replacement = FakeChrome()
        StageHost.attach(replacement)

        assertNull("the replaced chrome must have been told to hide", chrome.mask)
        assertEquals("and the new one must be drawing", box, replacement.mask)
    }

    /**
     * `recreate()` runs `new.onStart` before `old.onDestroy`, so a dying activity's teardown
     * arrives *after* its replacement has attached. Unguarded, it would strip the live
     * screen's chrome on behalf of a dead one — the same ordering fault three times over in
     * v0.35.0.
     */
    @Test
    fun `a dead screen detaching cannot take down the live one`() {
        goLive()
        val old = chrome
        val replacement = FakeChrome()
        StageHost.attach(replacement)
        replacement.events.clear()

        StageHost.detach(old)

        assertEquals("the live chrome must be untouched", box, replacement.mask)
        assertTrue(replacement.events.isEmpty())
    }

    @Test
    fun `detaching the current chrome takes its windows down`() {
        goLive()
        StageHost.detach(chrome)
        assertNull(chrome.mask)
    }

    @Test
    fun `an empty box never launches`() {
        StageHost.setStage("com.zjinnova.zlink", "CarPlay")
        StageHost.setHomeVisible(true)
        StageHost.setBounds(Rect())
        assertNull(StageHost.launchNeeded())
        assertNull(chrome.mask)
    }
}
