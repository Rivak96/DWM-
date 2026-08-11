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
 * Every case here is a bug that the first cut of this feature actually had. They were all
 * ordering bugs between two activities issuing orders to the window manager independently,
 * which is exactly the class of fault a golden cannot see and the deck shows only as
 * "it's buggy". Testable at all because [StageHost] talks to a [StageHost.Chrome] rather
 * than to a real `WindowManager` — the same trick `CameraHostTest` uses.
 */
class StageHostTest {

    private class FakeChrome : StageHost.Chrome {
        var mask: Rect? = null
        var maskTitle: String? = null
        var curtain = false
        /** Ordered log, because *when* each window appears is the thing under test. */
        val events = ArrayList<String>()

        override fun showMask(bounds: Rect, title: String) {
            mask = Rect(bounds); maskTitle = title; events.add("mask+")
        }

        override fun hideMask() {
            mask = null; maskTitle = null; events.add("mask-")
        }

        override fun showCurtain() {
            curtain = true; events.add("curtain+")
        }

        override fun hideCurtain() {
            curtain = false; events.add("curtain-")
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
        assertTrue(!chrome.curtain)
    }

    @Test
    fun `home in front with a stage shows the mask at the box`() {
        goLive()
        assertEquals(box, chrome.mask)
        assertEquals("CarPlay", chrome.maskTitle)
        assertTrue(!chrome.curtain)
    }

    /**
     * The drawer bug. Android runs `B.onStart` *before* `A.onStop`, so the first cut raised
     * the curtain and then home's stop cleared it — the drawer ran with a live app floating
     * through it.
     */
    @Test
    fun `curtain survives home stopping after the drawer starts`() {
        goLive()
        StageHost.setDwmScreenInFront(true)   // drawer onStart
        StageHost.setHomeVisible(false)       // home onStop, afterwards
        assertTrue("curtain must still be up", chrome.curtain)
        assertNull("mask must be down behind the curtain", chrome.mask)
    }

    /** And there must be no frame where the live window is uncovered between the two. */
    @Test
    fun `curtain goes up before the mask comes down`() {
        goLive()
        chrome.events.clear()
        StageHost.setDwmScreenInFront(true)
        assertEquals(listOf("curtain+", "mask-"), chrome.events)
    }

    /**
     * The other half of the drawer bug: coming back, the mask was only redrawn from
     * `onGloballyPositioned`, which does not necessarily fire again for an unchanged
     * layout. The window returned with its system caption exposed and draggable.
     */
    @Test
    fun `mask returns from remembered bounds without a new layout pass`() {
        goLive()
        StageHost.setDwmScreenInFront(true)
        StageHost.setHomeVisible(false)
        // ...back to home. No setBounds() call — nothing re-laid out. Real lifecycle order
        // again: home's onStart runs before the drawer's onStop.
        StageHost.setHomeVisible(true)
        StageHost.setDwmScreenInFront(false)
        assertEquals(box, chrome.mask)
        assertTrue(!chrome.curtain)
    }

    @Test
    fun `mask comes down only after the curtain is gone`() {
        goLive()
        StageHost.setDwmScreenInFront(true)
        StageHost.setHomeVisible(false)
        chrome.events.clear()
        StageHost.setHomeVisible(true)
        StageHost.setDwmScreenInFront(false)
        assertEquals(listOf("mask+", "curtain-"), chrome.events)
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

    @Test
    fun `a moved box relaunches, because a window cannot be repositioned any other way`() {
        goLive()
        StageHost.launchNeeded()
        val moved = Rect(40, 120, 1400, 820)
        StageHost.setBounds(moved)
        assertEquals("com.zjinnova.zlink" to moved, StageHost.launchNeeded())
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
        assertTrue(!chrome.curtain)
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
        StageHost.setDwmScreenInFront(false)
        assertEquals("com.zjinnova.zlink" to box, StageHost.launchNeeded())
        assertEquals(box, chrome.mask)
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
        val before = chrome.mask
        StageHost.setBounds(box)
        assertEquals(before, chrome.mask)
        assertTrue(!chrome.curtain)
    }

    @Test
    fun `clearing the stage puts the box back to the grid`() {
        goLive()
        StageHost.setStage(null, "")
        assertNull(chrome.mask)
        assertTrue(!chrome.curtain)
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

    @Test
    fun `an empty box never launches`() {
        StageHost.setStage("com.zjinnova.zlink", "CarPlay")
        StageHost.setHomeVisible(true)
        StageHost.setBounds(Rect())
        assertNull(StageHost.launchNeeded())
        assertNull(chrome.mask)
    }
}
