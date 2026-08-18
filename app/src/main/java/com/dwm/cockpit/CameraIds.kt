package com.dwm.cockpit

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size

/**
 * Which camera device a panel would actually take.
 *
 * This lived inside `CameraPanel.openCamera()`, which meant the answer only existed
 * *after* the device had been opened — far too late for anything to arbitrate on. Two
 * panels could not be compared until they had already fought over the hardware.
 *
 * That mattered because the two stored preferences do not look alike even when they
 * resolve to the same camera. The dashboard passes [Prefs.camId], which is `null` on
 * every deck that has never used Settings' camera picker; the overlay's layout editor
 * defaults its field to the string `"0"`. Comparing those two says "different"; opening
 * them says otherwise, and the second open silently evicts the first.
 *
 * So resolution is a pure function of the preference and the device list, callable
 * before anything is opened. [CameraHost] compares the results; [CameraPanel] opens the
 * one it is given. One source of truth for a question that used to have two answers.
 */
object CameraIds {

    /**
     * Every exposed device as `id to label`, for pickers and the scan report.
     *
     * Listing does **not** need the CAMERA permission — only opening does — so this is
     * safe to call before any grant.
     */
    fun list(c: Context): List<Pair<String, String>> {
        val mgr = c.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        val ids = runCatching { mgr.cameraIdList }.getOrDefault(emptyArray())
        return ids.map { id ->
            val facing = runCatching {
                mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            }.getOrNull()
            id to when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "?"
            }
        }
    }

    /**
     * Every preview size [id] advertises, largest first. Empty if the device is gone or
     * says nothing.
     *
     * This is a characteristics read, so like [list] it needs no CAMERA permission and no
     * open device — which is the whole reason it belongs here rather than staying buried
     * in `CameraPanel.choosePreviewSize`, where the answer only existed for the winner and
     * was thrown away immediately.
     *
     * It is reported in the dump. The 360 investigation spent four releases on a 720P/1080P
     * mismatch while nothing in DWM had ever asked an input what it actually offers, and
     * `VehicleProbe.cameraInputs` printing this is how that question finally gets answered
     * without a shell.
     */
    fun sizes(c: Context, id: String): List<Size> {
        val mgr = c.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        val map = runCatching {
            mgr.getCameraCharacteristics(id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        }.getOrNull() ?: return emptyList()
        return runCatching { map.getOutputSizes(SurfaceTexture::class.java)?.toList() }
            .getOrNull().orEmpty()
            .sortedByDescending { it.width.toLong() * it.height }
    }

    /**
     * The device id a panel configured with [pref] would open, or `null` if this deck
     * exposes no Camera2 device at all.
     *
     * A configured id that is not in `cameraIdList` falls through to the auto-pick
     * rather than failing — the behaviour `openCamera` has always had. It is worth
     * knowing that this is why two panels with *different* configured ids can still
     * collide: if neither id exists, both auto-pick the same device.
     *
     * Auto order is EXTERNAL, then BACK, then whatever is first. A wired analog input
     * on this deck usually reports as EXTERNAL, which is the feed worth having.
     */
    fun resolve(c: Context, pref: String?): String? {
        val mgr = c.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
        val ids = runCatching { mgr.cameraIdList }.getOrDefault(emptyArray())
        if (ids.isEmpty()) return null

        pref?.takeIf { it in ids }?.let { return it }

        fun facing(cid: String) = runCatching {
            mgr.getCameraCharacteristics(cid).get(CameraCharacteristics.LENS_FACING)
        }.getOrNull()

        return ids.firstOrNull { facing(it) == CameraCharacteristics.LENS_FACING_EXTERNAL }
            ?: ids.firstOrNull { facing(it) == CameraCharacteristics.LENS_FACING_BACK }
            ?: ids.first()
    }
}
