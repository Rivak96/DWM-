package com.dwm.cockpit

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The immersive flag set, which is the only part of [DwmActivity] a JVM test can reach —
 * everything else there is a window, a decor view or a handler.
 *
 * Worth pinning anyway. These flags are what keeps the system bars off the cockpit, they are
 * asserted from a 2.5s timer where a wrong value would be re-applied forever, and they now
 * vary by day/night. `Ui.Theme.light` was hardcoded `false` in *both* branches for months and
 * drove five things including dialog styling — a variant flag that never varies hides
 * indefinitely, so the rule here is that adding a variant means adding the test that proves
 * the variants differ.
 */
class DwmActivityTest {

    @Suppress("DEPRECATION")
    private val hiding = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

    @Suppress("DEPRECATION")
    private val layout = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

    /**
     * The bars stay hidden in both variants. Dropping any of these three is the whole bug
     * this was written for, and it would look like a theme problem rather than a flag one.
     */
    @Test
    fun `both variants hide the system bars`() {
        for (night in listOf(true, false)) {
            val flags = DwmActivity.immersiveFlags(night)
            assertEquals(
                "the hiding bits are missing for night=$night",
                hiding, flags and hiding
            )
        }
    }

    /**
     * The LAYOUT_ bits are what put DWM's content *underneath* the bars. Without them a bar
     * appearing resizes the window instead of drawing over it — which would move the box's
     * rect after the stage had already been launched into it.
     */
    @Test
    fun `both variants lay out under the bars`() {
        for (night in listOf(true, false)) {
            val flags = DwmActivity.immersiveFlags(night)
            assertEquals(
                "the layout bits are missing for night=$night",
                layout, flags and layout
            )
        }
    }

    /**
     * Day asks for dark bar icons, night does not.
     *
     * This only shows up when the ROM draws a bar anyway: the bar backgrounds are transparent
     * (`styles.xml`), so its icons land on DWM's own content, and the day background is
     * `#E4E9EF`. Without the light-bar bits, day mode gets white icons on near-white.
     */
    @Test
    @Suppress("DEPRECATION")
    fun `day and night differ, and it is the icon colour that differs`() {
        val day = DwmActivity.immersiveFlags(night = false)
        val night = DwmActivity.immersiveFlags(night = true)

        assertNotEquals("the two variants are identical", day, night)

        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        assertEquals("day must ask for dark bar icons", light, day and light)
        assertEquals("night must not", 0, night and light)
        assertEquals("nothing but the icon bits may differ", light, day xor night)
    }
}
