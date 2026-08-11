package com.dwm.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device-flow response parsing, on the JVM.
 *
 * This is tested and the HTTP around it is not, because the HTTP is four lines of
 * `HttpURLConnection` and **the parsing is where this silently breaks**: GitHub answers
 * `200 OK` while the user is still typing the code into their phone, with the real state in
 * an `error` field. Treating a 200 as success is the obvious bug, and it would present as
 * "sign-in never completes" with nothing in the log to say why.
 *
 * Payloads are the ones in GitHub's own documentation.
 */
class GitHubTest {

    @Test
    fun `step one gives the code, the url and the polling interval`() {
        val d = GitHub.parseDeviceCode(
            """
            {
              "device_code": "3584d83530557fdd1f46af8289938c8ef79f9dc5",
              "user_code": "WDJB-MJHT",
              "verification_uri": "https://github.com/login/device",
              "expires_in": 900,
              "interval": 5
            }
            """.trimIndent()
        )
        assertEquals("3584d83530557fdd1f46af8289938c8ef79f9dc5", d.deviceCode)
        assertEquals("WDJB-MJHT", d.userCode)
        assertEquals("https://github.com/login/device", d.verificationUri)
        assertEquals(5, d.intervalSeconds)
        assertEquals(900, d.expiresInSeconds)
    }

    /**
     * A missing interval must never become zero. Polling flat out earns a `slow_down` on
     * every request, which is an infinite loop that looks exactly like a broken sign-in.
     */
    @Test
    fun `a missing interval falls back to something pollable`() {
        val d = GitHub.parseDeviceCode(
            """{"device_code":"x","user_code":"ABCD-EFGH","interval":0}"""
        )
        assertTrue("interval must be at least 1s", d.intervalSeconds >= 1)
        assertEquals("https://github.com/login/device", d.verificationUri)
    }

    @Test
    fun `a token comes back as a token`() {
        val p = GitHub.parsePoll(
            """
            {
              "access_token": "gho_16C7e42F292c6912E7710c838347Ae178B4a",
              "token_type": "bearer",
              "scope": "gist"
            }
            """.trimIndent()
        )
        assertEquals(GitHub.Poll.Token("gho_16C7e42F292c6912E7710c838347Ae178B4a"), p)
    }

    /** The whole reason this is a sealed class: HTTP 200, and not done yet. */
    @Test
    fun `authorization_pending keeps polling`() {
        assertEquals(
            GitHub.Poll.Pending,
            GitHub.parsePoll("""{"error":"authorization_pending"}""")
        )
    }

    @Test
    fun `slow_down is its own state, not a failure`() {
        assertEquals(
            GitHub.Poll.SlowDown,
            GitHub.parsePoll("""{"error":"slow_down","interval":10}""")
        )
    }

    @Test
    fun `an expired code stops, and says so in words`() {
        val p = GitHub.parsePoll("""{"error":"expired_token"}""")
        assertTrue(p is GitHub.Poll.Failed)
        assertTrue((p as GitHub.Poll.Failed).reason.contains("expired", ignoreCase = true))
    }

    @Test
    fun `a refusal on the phone stops`() {
        val p = GitHub.parsePoll("""{"error":"access_denied"}""")
        assertTrue(p is GitHub.Poll.Failed)
    }

    /** Neither a token nor a known error is still a failure — never a silent success. */
    @Test
    fun `an unrecognised answer fails rather than hanging`() {
        assertTrue(GitHub.parsePoll("""{}""") is GitHub.Poll.Failed)
        assertTrue(
            GitHub.parsePoll("""{"error":"something_new","error_description":"nope"}""")
                is GitHub.Poll.Failed
        )
    }

    /**
     * Nothing worth extracting may ship in the binary.
     *
     * The repo is public and every release APK is a public asset, so a client *secret*
     * would be readable by anyone who downloads it. The device flow is used precisely
     * because it needs only the client ID, which is public by design — this test exists so
     * that a future "just add the secret, it's easier" cannot pass unnoticed.
     */
    @Test
    fun `no client secret is compiled in`() {
        val fields = GitHub::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(
            "GitHub must never hold a client secret: $fields",
            fields.none { it.contains("secret") }
        )
    }
}
