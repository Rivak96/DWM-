package com.dwm.cockpit

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two parts of a dump that can be wrong without anyone noticing: what gets thrown away
 * when it is too big, and what gets through the redaction.
 *
 * The rest of `Diagnostics` is Context-bound string building, which a JVM test can only
 * restate rather than check.
 */
class DiagnosticsTest {

    @Before
    fun setUp() = StageHost.resetForTest()

    @Test
    fun `text under the cap is untouched`() {
        val text = "one\ntwo\nthree\n"
        assertEquals(text, Diagnostics.cap(text, 1024))
    }

    /**
     * The **tail** survives, not the head.
     *
     * A log truncated from the end is a log of the boot sequence, and the thing being
     * diagnosed is always the most recent thing that happened. Getting this backwards would
     * produce dumps that look complete and answer nothing.
     */
    @Test
    fun `an oversized log keeps the newest lines and drops the oldest`() {
        val text = (1..500).joinToString("\n") { "line $it" } + "\n"
        val capped = Diagnostics.cap(text, 200)

        assertTrue("the last line must survive", capped.contains("line 500"))
        assertFalse("the first line must be gone", capped.contains("line 1\n"))
        assertTrue("it must say what it dropped", capped.contains("dropped"))
    }

    /** Cutting mid-line would put a half-record at the top and read as corruption. */
    @Test
    fun `the cap breaks on a line boundary`() {
        val text = (1..500).joinToString("\n") { "line $it" } + "\n"
        val body = Diagnostics.cap(text, 200).lines().drop(1)
        assertTrue(
            "every surviving line must be whole: ${body.first()}",
            body.filter { it.isNotBlank() }.all { it.matches(Regex("""line \d+""")) }
        )
    }

    /**
     * The dump is posted to a gist using a GitHub token, and the dump contains the settings
     * store that the token lives in. It must redact itself.
     *
     * `Prefs.githubToken` deliberately stores under a key containing "token" so that
     * `VehicleProbe.SENSITIVE` matches it. This test is what stops that key being renamed
     * to something tidier — and posting the credential to a public paste.
     */
    @Test
    fun `the github token can never appear in a dump`() {
        val secret = "gho_16C7e42F292c6912E7710c838347Ae178B4a"
        val out = VehicleProbe.redact("github_token", secret)
        assertFalse("the token leaked: $out", out.contains(secret))
        assertTrue(out.contains("redacted"))
    }

    @Test
    fun `ordinary settings are not redacted`() {
        assertEquals("32", VehicleProbe.redact("caption_dp", "32"))
        assertEquals("com.zjinnova.zlink", VehicleProbe.redact("stage_pkg", "com.zjinnova.zlink"))
    }

    /**
     * "The placeholder is showing" has at least five causes and they look identical on the
     * glass. The snapshot has to name which one, or it is not worth collecting.
     */
    @Test
    fun `the stage snapshot distinguishes a launch that is owed from one that happened`() {
        StageHost.setStage("com.zjinnova.zlink", "Tlink5")
        StageHost.setHomeVisible(true)
        StageHost.setBounds(Rect(40, 40, 1400, 800))

        assertTrue(StageHost.snapshot().contains("a launch is owed"))

        StageHost.launchNeeded()
        assertTrue(StageHost.snapshot().contains("com.zjinnova.zlink"))
        assertFalse(StageHost.snapshot().contains("a launch is owed"))
    }

    @Test
    fun `the stage snapshot names an eviction, which is otherwise invisible`() {
        StageHost.setStage("com.zjinnova.zlink", "Tlink5")
        StageHost.setHomeVisible(true)
        StageHost.setBounds(Rect(40, 40, 1400, 800))
        StageHost.evict()

        assertTrue(StageHost.snapshot().contains("evicted      : true"))
    }
}
