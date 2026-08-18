package com.dwm.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two halves of the camera probe that can be silently wrong: the `getprop` parse,
 * and what the hoist matcher considers interesting.
 *
 * Both fail *quietly*. A regex that matches nothing reports "this ROM has no camera
 * properties", which reads like a finding and is actually a broken tool — and it is the
 * one wrong answer that would end the 360 investigation early, because it looks exactly
 * like the clean negative we are half-expecting. The deck cannot be attached to a
 * debugger and has no USB device port, so this is checked here or not at all.
 *
 * The rest of the probe is Context-bound and can only be exercised on the deck.
 */
class CameraProbeTest {

    /** Real `getprop` output shape: `[key]: [value]`, one per line. */
    private val sample = """
        [ro.build.version.release]: [12]
        [ro.build.version.sdk]: [29]
        [ro.product.model]: [TS18]
        [persist.sys.camera.mode]: [MODE_720P_25FPS]
        [persist.sys.video_pal]: [0]
        [ro.vendor.display.resolution]: [1920x1200]
    """.trimIndent()

    @Test
    fun `getprop lines are parsed into key and value`() {
        val p = VehicleProbe.parseProps(sample)
        assertEquals("12", p["ro.build.version.release"])
        assertEquals("MODE_720P_25FPS", p["persist.sys.camera.mode"])
        assertEquals("1920x1200", p["ro.vendor.display.resolution"])
        assertEquals(6, p.size)
    }

    /** Vendor values are not tidy. A value may hold spaces, brackets or nothing at all,
     *  and dropping such a line would drop exactly the kind of vendor string we are here
     *  to find. */
    @Test
    fun `values may contain spaces, brackets and be empty`() {
        val p = VehicleProbe.parseProps(
            """
            [ro.build.description]: [ts18-user 12 SP1A [release-keys]]
            [persist.sys.blank]: []
            [ro.mcu.version]: [Ts18.1.1-100-411-A5489D-250605(FT072)]
            """.trimIndent()
        )
        assertEquals("ts18-user 12 SP1A [release-keys]", p["ro.build.description"])
        assertEquals("", p["persist.sys.blank"])
        assertEquals("Ts18.1.1-100-411-A5489D-250605(FT072)", p["ro.mcu.version"])
    }

    @Test
    fun `lines that are not properties are skipped rather than fatal`() {
        val p = VehicleProbe.parseProps("garbage\n\n[ro.a]: [1]\nnot [a prop]\n")
        assertEquals(mapOf("ro.a" to "1"), p)
    }

    /**
     * **The case the whole probe exists for.**
     *
     * `MODE_720P_25FPS` is a *value*. The key that holds it may carry no camera
     * vocabulary whatsoever, so hoisting on key names alone — the obvious
     * implementation, and the mistake `VENDOR_HINTS` already records making once —
     * would walk straight past the single line that answers the question.
     */
    @Test
    fun `a resolution value is matched even when its key says nothing`() {
        val key = "persist.sys.hw.info3"
        assertFalse(
            "the key alone must be uninteresting, or this test proves nothing",
            VehicleProbe.CAM_HINT.containsMatchIn(key)
        )
        assertTrue(VehicleProbe.looksCameraRelated(key, "MODE_720P_25FPS"))
    }

    /**
     * `pal` is bounded by a letter-only lookaround, not `\b`.
     *
     * `\w` includes the underscore, so `\bpal\b` matches "palette" no better but fails
     * on `video_pal` — the exact key shape a vendor writes. Getting this backwards
     * silently drops analog-format keys while still admitting the noise it was added to
     * exclude.
     */
    @Test
    fun `pal is matched as a format but not inside an ordinary word`() {
        assertTrue(VehicleProbe.looksCameraRelated("persist.sys.cvbs_pal", "1"))
        assertTrue(VehicleProbe.looksCameraRelated("ro.vendor.pal.enable", "1"))
        assertFalse(VehicleProbe.looksCameraRelated("ro.boot.palette", "warm"))
        assertFalse(VehicleProbe.looksCameraRelated("ro.principal.name", "owner"))
    }

    @Test
    fun `the camera vocabulary matches what this deck would actually carry`() {
        for (k in listOf(
            "persist.sys.ahd.format", "ro.vendor.360.enable", "persist.sys.avm_type",
            "ro.camera.decoder", "persist.sys.reverse_source", "ro.vendor.cvbs.mode",
            "persist.sys.rearcam", "ro.panorama.support"
        )) {
            assertTrue("should have matched: $k", VehicleProbe.looksCameraRelated(k, null))
        }
    }

    /** Build metadata is the bulk of the store. If it all matched, the hoist would be
     *  the dump and the section would be useless. */
    @Test
    fun `ordinary build properties are not hoisted`() {
        for (k in listOf(
            "ro.build.version.sdk", "ro.product.model", "persist.sys.locale",
            "ro.boot.serialno", "sys.usb.state"
        )) {
            assertFalse("should not have matched: $k", VehicleProbe.looksCameraRelated(k, null))
        }
    }

    /** A property store leaves the deck in the dump, so it goes through the same
     *  redaction as the settings store rather than a second list that could drift. */
    @Test
    fun `sensitive properties are redacted like settings are`() {
        assertTrue(VehicleProbe.redact("ro.boot.serialno", "A5489D250605").contains("redacted"))
        assertTrue(VehicleProbe.redact("persist.sys.wifi.mac", "aa:bb:cc:dd:ee:ff").contains("redacted"))
        assertEquals("MODE_720P_25FPS", VehicleProbe.redact("persist.sys.camera.mode", "MODE_720P_25FPS"))
    }
}
