package com.dwm.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the binary-XML reader against DWM's own release APK, whose manifest we
 * wrote and can therefore assert on exactly.
 *
 * The parser walks hand-computed chunk offsets. Get one wrong and it doesn't
 * throw — it returns confident nonsense, which on the deck would look like "this
 * head unit declares no vehicle actions" and send the whole investigation down a
 * dead end. So the offsets get proven here, on the JVM, first.
 */
class AxmlTest {

    private val apk = File("build/outputs/apk/release/app-release.apk")

    private fun parse() = VehicleProbe.Axml.parseApk(apk.path)

    @Test
    fun `finds every action in our own manifest, attributed to the right component`() {
        assumeTrue("no release APK built yet — run assembleRelease", apk.exists())
        val parsed = requireNotNull(parse()) { "parseApk returned null on a valid APK" }

        // ProfileInstallReceiver is merged in by androidx and is not in our source
        // manifest — its presence, with all four of its actions attributed to it
        // and to nothing else, is the point: the parser reads the *merged*
        // manifest and gets the nesting right on a component we never wrote.
        assertEquals(
            mapOf(
                "activity com.dwm.cockpit.HomeActivity" to
                    listOf("android.intent.action.MAIN"),
                "receiver com.dwm.cockpit.BootReceiver" to
                    listOf("android.intent.action.BOOT_COMPLETED"),
                "service com.dwm.cockpit.DwmNotificationListener" to
                    listOf("android.service.notification.NotificationListenerService"),
                "receiver androidx.profileinstaller.ProfileInstallReceiver" to listOf(
                    "androidx.profileinstaller.action.INSTALL_PROFILE",
                    "androidx.profileinstaller.action.SKIP_FILE",
                    "androidx.profileinstaller.action.SAVE_PROFILE",
                    "androidx.profileinstaller.action.BENCHMARK_OPERATION"
                )
            ),
            parsed.byComponent.mapValues { it.value.toList() }
        )
    }

    /** A component with no intent-filter must not inherit the previous one's
     *  actions — that's the failure mode of tracking nesting by depth. */
    @Test
    fun `components without an intent-filter get no actions`() {
        assumeTrue(apk.exists())
        val parsed = requireNotNull(parse())
        assertTrue(
            "filter-less components leaked actions: ${parsed.byComponent.keys}",
            parsed.byComponent.keys.none {
                it.contains("AppDrawerActivity") || it.contains("OverlayService") ||
                    it.contains("InstallResultReceiver") || it.contains("FileProvider")
            }
        )
    }

    /** The string pool is the part most likely to be silently misread; if the
     *  offsets are wrong the strings come out as mojibake or empty. */
    @Test
    fun `string pool decodes to real names`() {
        assumeTrue(apk.exists())
        val parsed = requireNotNull(parse())
        assertEquals(setOf(
            "android.intent.action.MAIN",
            "android.intent.action.BOOT_COMPLETED",
            "android.service.notification.NotificationListenerService",
            "androidx.profileinstaller.action.INSTALL_PROFILE",
            "androidx.profileinstaller.action.SKIP_FILE",
            "androidx.profileinstaller.action.SAVE_PROFILE",
            "androidx.profileinstaller.action.BENCHMARK_OPERATION"
        ), parsed.allActions.toSet())
    }

    @Test
    fun `malformed input is rejected rather than throwing`() {
        assertEquals(null, VehicleProbe.Axml.parse(ByteArray(0)))
        assertEquals(null, VehicleProbe.Axml.parse(ByteArray(64)))          // zero header
        assertEquals(null, VehicleProbe.Axml.parse("not xml at all".toByteArray()))
    }
}
