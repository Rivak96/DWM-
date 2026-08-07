package com.dwm.cockpit.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Every foreground in [DwmPalette], against every surface it can land on, in both
 * variants.
 *
 * This exists because a palette is the thing in a design system that drifts most
 * quietly. A colour gets nudged to look better in a screenshot on a bright laptop
 * and nobody finds out it stopped being readable until they are on a road in
 * daylight. Four of the values in this palette were changed by this test rather than
 * by eye — most usefully `critical`, which was fine on the background and the base
 * surface and failed on `raised`, the surface a warning actually appears on.
 *
 * The panel is a glossy IPS read through a windscreen, so the thresholds are AA
 * rather than anything softer, and the hairline threshold is far above what a
 * divider normally gets: Material's is around 1.1:1, and at that value the line
 * simply is not present outdoors here.
 */
class DwmContrastTest {

    private val textMin = 7.0
    private val supportMin = 4.5
    private val hairlineMin = 1.45

    @Test
    fun `day palette is legible on every surface`() = check(
        variant = "day",
        surfaces = listOf(
            "background" to DwmPalette.BACKGROUND,
            "surface" to DwmPalette.SURFACE,
            "raised" to DwmPalette.RAISED
        ),
        text = DwmPalette.TEXT,
        support = listOf(
            "muted" to DwmPalette.MUTED,
            "accent" to DwmPalette.ACCENT,
            "ok" to DwmPalette.OK,
            "warn" to DwmPalette.WARN,
            "critical" to DwmPalette.CRITICAL
        ),
        hairline = DwmPalette.HAIRLINE
    )

    @Test
    fun `night palette is legible on every surface`() = check(
        variant = "night",
        surfaces = listOf(
            "background" to DwmPalette.N_BACKGROUND,
            "surface" to DwmPalette.N_SURFACE,
            "raised" to DwmPalette.N_RAISED
        ),
        text = DwmPalette.N_TEXT,
        support = listOf(
            "muted" to DwmPalette.N_MUTED,
            "accent" to DwmPalette.N_ACCENT,
            "ok" to DwmPalette.N_OK,
            "warn" to DwmPalette.N_WARN,
            "critical" to DwmPalette.N_CRITICAL
        ),
        hairline = DwmPalette.N_HAIRLINE
    )

    /**
     * Nothing in the night variant may be bright, and no foreground in either may be
     * pure white. White reflects in the windscreen after dark, and the palette this
     * replaced reached it about forty times as `Color.White.copy(alpha=…)`.
     *
     * Day surfaces are exempt and [DwmPalette.RAISED] is deliberately `#FFFFFF`: this
     * is the daylight variant, where a dark panel in sun is a mirror. The rule is
     * about night, and about text, which is what this now says.
     */
    @Test
    fun `no foreground is pure white`() {
        listOf(
            "text" to DwmPalette.TEXT,
            "night text" to DwmPalette.N_TEXT,
            "muted" to DwmPalette.MUTED,
            "night muted" to DwmPalette.N_MUTED
        ).forEach { (name, c) ->
            assertTrue("$name is pure white", c != 0xFFFFFFFF.toInt())
            assertTrue("$name is within one step of white", luminance(c) < 0.90)
        }
    }

    /**
     * Night stays dark everywhere. Not one surface may creep up.
     *
     * This used to be `night is dimmer than day` and compared foregrounds too, which
     * only made sense while "day" was also a dark theme — day text was light, so
     * asserting night text was darker still was meaningful. Day is a real light theme
     * now and its text is near-black, so that comparison inverts and says nothing. The
     * safety property it was actually protecting is this one: nothing on the night
     * panel is bright enough to sit in the driver's peripheral vision.
     */
    @Test
    fun `night surfaces stay dark`() {
        listOf(
            "background" to DwmPalette.N_BACKGROUND,
            "surface" to DwmPalette.N_SURFACE,
            "raised" to DwmPalette.N_RAISED
        ).forEach { (name, c) ->
            assertTrue(
                "night $name (${hex(c)}) is too bright for night",
                luminance(c) < 0.05
            )
        }
    }

    /**
     * The two variants are genuinely different, which they were not.
     *
     * Day was `#0A0C0F` against a night of `#06080A` — two near-blacks four points
     * apart — so choosing Day changed nothing anyone could see. This is the assertion
     * that would have caught that.
     */
    @Test
    fun `day is light and night is dark`() {
        assertTrue(
            "day background ${hex(DwmPalette.BACKGROUND)} is not a light field",
            luminance(DwmPalette.BACKGROUND) > 0.50
        )
        assertTrue(
            "night background ${hex(DwmPalette.N_BACKGROUND)} is not a dark field",
            luminance(DwmPalette.N_BACKGROUND) < 0.05
        )
    }

    private fun check(
        variant: String,
        surfaces: List<Pair<String, Int>>,
        text: Int,
        support: List<Pair<String, Int>>,
        hairline: Int
    ) {
        val failures = mutableListOf<String>()

        surfaces.forEach { (sName, s) ->
            assertAtLeast(failures, variant, "text", text, sName, s, textMin)
            support.forEach { (fName, f) ->
                assertAtLeast(failures, variant, fName, f, sName, s, supportMin)
            }
            assertAtLeast(failures, variant, "hairline", hairline, sName, s, hairlineMin)
        }

        assertTrue(
            "\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    private fun assertAtLeast(
        into: MutableList<String>,
        variant: String,
        fgName: String, fg: Int,
        bgName: String, bg: Int,
        min: Double
    ) {
        val r = contrast(fg, bg)
        if (r < min) {
            into += "  $variant: $fgName ${hex(fg)} on $bgName ${hex(bg)} " +
                "= ${"%.2f".format(r)}:1, need ${"%.2f".format(min)}:1"
        }
    }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    /** WCAG 2.1 relative luminance. */
    private fun luminance(argb: Int): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch((argb shr 16) and 0xFF) +
            0.7152 * ch((argb shr 8) and 0xFF) +
            0.0722 * ch(argb and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }
}
