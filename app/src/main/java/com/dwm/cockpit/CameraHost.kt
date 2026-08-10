package com.dwm.cockpit

import android.util.Log
import java.lang.ref.WeakReference

/**
 * One live feed per physical camera, decided in one place.
 *
 * ### Why this exists
 *
 * Camera2 opens a device **exclusively**, and DWM had two consumers that could both want
 * the same one: the dashboard's [CameraPanel] inside `CameraBox`, and any number of
 * camera panels built by `OverlayPanelsService`. There was no arbitration of any kind.
 *
 * The failure was worse than a race, because losing the device was silent. The old
 * `onDisconnected` was `cam.close(); device = null` — no status, no log, no retry, no
 * `AvailabilityCallback`. So the second open evicted the first, the evicted panel went
 * permanently black with `status.text` still cleared from its own `onOpened`, and nothing
 * short of destroying the View ever recovered it. Both feeds looked broken; only one
 * actually was.
 *
 * ### The rule
 *
 * While the dashboard is in front it wins its own camera, and an overlay panel on that
 * *same* device stands down and says so. An overlay on a *different* camera is untouched
 * and keeps running — the whole point of being able to see two feeds at once. When the
 * dashboard goes away its panels release, and the overlay takes over within a frame.
 *
 * Panels whose owner is [Owner.HOME] are ineligible while home is hidden. That is
 * load-bearing rather than tidiness: `onStop` does not detach an Activity's views, so a
 * backgrounded dashboard would otherwise sit on the camera forever and starve the overlay
 * it was meant to hand over to.
 *
 * ### Shape
 *
 * Modelled on [WebPanelHost] — a process-global object holding weak references, written
 * into by both the activity and the service. Two differences worth knowing. A WebView is
 * its own resource, so `WebPanelHost` never has to ask what a panel is competing *for*;
 * a camera panel competes for a resolved device id, which is why [CameraIds] exists. And
 * this one only sends transitions: [arbitrate] runs on every register, forget and
 * visibility flip, and `CameraBox` re-registers constantly as `CockpitHome` recomposes,
 * so re-telling a holder to hold would be a steady drip of redundant camera opens.
 */
object CameraHost {

    private const val TAG = "DWM"

    /**
     * Logging must never be the thing that breaks arbitration.
     *
     * It also makes this object unit-testable as-is: on the JVM `android.util.Log` has no
     * native backing and throws `UnsatisfiedLinkError`, which would otherwise force
     * `unitTests.isReturnDefaultValues = true` on the whole module — turning every
     * unmocked Android call across the entire suite into a silent default instead of a
     * loud failure. Too high a price for a log line.
     */
    private fun log(msg: String, warn: Boolean = false) {
        runCatching { if (warn) Log.w(TAG, msg) else Log.i(TAG, msg) }
    }

    enum class Owner { HOME, OVERLAY }

    /** What the host can do to a panel. An interface so the arbitration is testable off-device. */
    interface Client {
        /** Stand down: release the device and say why. */
        fun yieldCamera()
        /** Take the device. Safe to call when already holding. */
        fun reclaimCamera()
    }

    private class Entry(
        val ref: WeakReference<Client>,
        val deviceId: String?,
        val owner: Owner,
        val seq: Long
    )

    private val entries = mutableListOf<Entry>()
    private val holders = mutableSetOf<Client>()
    private var homeVisible = false
    private var counter = 0L

    /**
     * Announce a panel and the device it resolved to.
     *
     * Called from `onAttachedToWindow`, never from a constructor — `CameraBox` builds a
     * fresh `CameraPanel` on every recomposition of `CockpitHome` and attaches only the
     * first, so constructor registration would fill this list with orphans that never
     * draw and never detach.
     *
     * A null [deviceId] means the deck exposes no camera; such a panel is tracked (so it
     * can still be told things) but can never win.
     */
    @Synchronized
    fun register(client: Client, deviceId: String?, owner: Owner) {
        prune()
        if (entries.none { it.ref.get() === client }) {
            entries += Entry(WeakReference(client), deviceId, owner, counter++)
            log("camera: register $owner on device ${deviceId ?: "none"}")
        }
        arbitrate()
    }

    /** Called from `onDetachedFromWindow`. */
    @Synchronized
    fun forget(client: Client) {
        entries.removeAll { it.ref.get() == null || it.ref.get() === client }
        holders.remove(client)
        arbitrate()
    }

    /** Driven from `HomeActivity.onStart`/`onStop`, beside the existing `WebPanelHost` calls. */
    @Synchronized
    fun setHomeVisible(visible: Boolean) {
        if (homeVisible == visible) return
        homeVisible = visible
        log("camera: home ${if (visible) "visible" else "hidden"}")
        arbitrate()
    }

    /**
     * A panel was disconnected — something took its device.
     *
     * Usually another app entirely, since our own evictions are prevented by this class.
     * Drop it from the holder set so the next pass genuinely re-sends `reclaimCamera()`
     * rather than assuming it still holds, then re-run. There is no retry loop here: the
     * panel's own open does not retry either, so this cannot thrash.
     */
    @Synchronized
    fun lost(client: Client) {
        log("camera: lost the device", warn = true)
        holders.remove(client)
        arbitrate()
    }

    /** Test seam — reset between cases. */
    @Synchronized
    internal fun reset() {
        entries.clear()
        holders.clear()
        homeVisible = false
        counter = 0L
    }

    /** Test seam — who currently holds a device. */
    @Synchronized
    internal fun holderCount(): Int = holders.size

    /**
     * Recompute the winner for every device and send only the changes.
     *
     * Winner per device: the dashboard if it is showing, otherwise the
     * earliest-registered eligible panel. Registration order is the tie-break so that two
     * overlay panels aimed at one camera resolve deterministically instead of alternating
     * — that pair already conflicted with each other before this class existed, and gets
     * fixed here for free.
     */
    private fun arbitrate() {
        prune()

        val eligible = entries.filter {
            it.deviceId != null && (it.owner == Owner.OVERLAY || homeVisible)
        }

        val winners = eligible
            .groupBy { it.deviceId }
            .values
            .mapNotNull { forDevice ->
                forDevice.minWithOrNull(
                    compareBy({ if (it.owner == Owner.HOME) 0 else 1 }, { it.seq })
                )
            }
            .mapNotNull { it.ref.get() }
            .toSet()

        entries.mapNotNull { it.ref.get() }.forEach { client ->
            val shouldHold = client in winners
            val doesHold = client in holders
            when {
                shouldHold && !doesHold -> {
                    holders += client
                    runCatching { client.reclaimCamera() }
                }
                !shouldHold && doesHold -> {
                    holders -= client
                    runCatching { client.yieldCamera() }
                }
            }
        }
    }

    private fun prune() {
        entries.removeAll { it.ref.get() == null }
        holders.removeAll { c -> entries.none { it.ref.get() === c } }
    }
}
