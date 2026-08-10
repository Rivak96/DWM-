package com.dwm.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The camera arbitration, on the JVM, with no camera and no deck.
 *
 * This is the part of the fix that is worth testing properly, and it is testable at all
 * only because [CameraHost] talks to a [CameraHost.Client] interface rather than to
 * `CameraPanel` directly. The real thing it is protecting against is a regression back to
 * the original bug, where two panels opened the same device, the second silently evicted
 * the first, and both looked broken.
 */
class CameraHostTest {

    /** A panel that records what it was told, instead of opening hardware. */
    private class FakePanel : CameraHost.Client {
        var holding = false
        var yields = 0
        var reclaims = 0
        override fun yieldCamera() { holding = false; yields++ }
        override fun reclaimCamera() { holding = true; reclaims++ }
    }

    @Before
    fun clean() = CameraHost.reset()

    @Test
    fun `one device is held by exactly one panel`() {
        val home = FakePanel()
        val overlay = FakePanel()
        CameraHost.setHomeVisible(true)
        CameraHost.register(home, "0", CameraHost.Owner.HOME)
        CameraHost.register(overlay, "0", CameraHost.Owner.OVERLAY)

        assertTrue("the dashboard should win its own camera", home.holding)
        assertFalse("the overlay must stand down on the same camera", overlay.holding)
        assertEquals(1, CameraHost.holderCount())
    }

    @Test
    fun `different cameras both run`() {
        val home = FakePanel()
        val overlay = FakePanel()
        CameraHost.setHomeVisible(true)
        CameraHost.register(home, "0", CameraHost.Owner.HOME)
        CameraHost.register(overlay, "1", CameraHost.Owner.OVERLAY)

        assertTrue(home.holding)
        assertTrue("a different camera is not a conflict", overlay.holding)
        assertEquals(2, CameraHost.holderCount())
    }

    /** The handover the user actually sees: dashboard away, overlay takes the feed. */
    @Test
    fun `overlay takes over when home goes away`() {
        val home = FakePanel()
        val overlay = FakePanel()
        CameraHost.setHomeVisible(true)
        CameraHost.register(home, "0", CameraHost.Owner.HOME)
        CameraHost.register(overlay, "0", CameraHost.Owner.OVERLAY)

        CameraHost.setHomeVisible(false)
        assertFalse("a hidden dashboard must release the device", home.holding)
        assertTrue(overlay.holding)

        CameraHost.setHomeVisible(true)
        assertTrue(home.holding)
        assertFalse(overlay.holding)
    }

    /**
     * `onStop` does not detach an Activity's views, so a backgrounded dashboard is still
     * registered. If it kept the camera the overlay would starve — which is the same
     * fault as the original bug, just pointing the other way.
     */
    @Test
    fun `home cannot hold the camera while hidden`() {
        val home = FakePanel()
        CameraHost.register(home, "0", CameraHost.Owner.HOME)
        assertFalse(home.holding)
        assertEquals(0, CameraHost.holderCount())
    }

    /** Two overlay panels on one id already fought each other, before home was involved. */
    @Test
    fun `two overlays on one camera resolve by registration order`() {
        val first = FakePanel()
        val second = FakePanel()
        CameraHost.register(first, "0", CameraHost.Owner.OVERLAY)
        CameraHost.register(second, "0", CameraHost.Owner.OVERLAY)

        assertTrue(first.holding)
        assertFalse(second.holding)
    }

    @Test
    fun `forgetting the holder hands the device on`() {
        val first = FakePanel()
        val second = FakePanel()
        CameraHost.register(first, "0", CameraHost.Owner.OVERLAY)
        CameraHost.register(second, "0", CameraHost.Owner.OVERLAY)

        CameraHost.forget(first)
        assertTrue("the survivor should pick the camera up", second.holding)
    }

    /** A deck exposing no camera at all must not produce a phantom holder. */
    @Test
    fun `a panel with no device never holds`() {
        val p = FakePanel()
        CameraHost.setHomeVisible(true)
        CameraHost.register(p, null, CameraHost.Owner.HOME)
        assertFalse(p.holding)
        assertEquals(0, CameraHost.holderCount())
    }

    /**
     * `CameraBox` rebuilds its panel on every recomposition, and `CockpitHome` recomposes
     * on every vehicle tick. Re-registering the same panel must not re-send `reclaim` —
     * that would be a steady drip of redundant camera opens on a deck that cannot afford
     * them.
     */
    @Test
    fun `re-registering the same panel sends no extra commands`() {
        val home = FakePanel()
        CameraHost.setHomeVisible(true)
        CameraHost.register(home, "0", CameraHost.Owner.HOME)
        val after = home.reclaims

        repeat(5) { CameraHost.register(home, "0", CameraHost.Owner.HOME) }
        assertEquals("only transitions should be sent", after, home.reclaims)
    }

    /** After losing the device to an outside app, the rightful holder is told again. */
    @Test
    fun `lost device is re-offered to the winner`() {
        val home = FakePanel()
        CameraHost.setHomeVisible(true)
        CameraHost.register(home, "0", CameraHost.Owner.HOME)
        val before = home.reclaims

        CameraHost.lost(home)
        assertTrue(home.holding)
        assertEquals(before + 1, home.reclaims)
    }
}
