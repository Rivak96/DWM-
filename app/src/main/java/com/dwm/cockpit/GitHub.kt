package com.dwm.cockpit

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sign in to GitHub on a device with no keyboard, and post a diagnostics gist.
 *
 * ### Why the device flow, and why there is no secret in this file
 *
 * DWM's repo is public and every release APK is a public asset, so **anything embedded in
 * the binary is extractable**. That rules out shipping a token, and it rules out the
 * ordinary OAuth web flow, which needs a client *secret*.
 *
 * GitHub's device flow needs only a client **ID**, which is public by design — the docs say
 * plainly that "the `client_secret` is not needed for the device flow". The deck shows an
 * eight-character code, the owner types it into `github.com/login/device` on their phone,
 * and the token comes back to the deck and stays there. Nothing worth stealing ever ships.
 *
 * It also happens to be the only sane way to authenticate on this hardware: the alternative
 * is typing a 93-character token onto a car touchscreen with a windscreen in the way.
 *
 * ### Scope
 *
 * `gist`, and nothing else. It cannot touch the DWM repo, cannot read private repos, and is
 * revocable from the owner's GitHub settings at any time without touching the deck.
 */
object GitHub {

    /**
     * The OAuth app's client ID. **Public by design** — see the class comment.
     *
     * Registered at github.com/settings/developers → New OAuth App on the owner's account,
     * scoped to `gist` and nothing else. This is a client *ID*, not a secret: GitHub's docs
     * state plainly that "the `client_secret` is not needed for the device flow", and
     * `GitHubTest` fails the build if a field with "secret" in its name ever appears here.
     *
     * **"Enable Device Flow" must stay ticked** on the app's settings page. It is off by
     * default and turning it off does not break anything until the very last step — the deck
     * still shows the user a code, and the code is simply never accepted. Verified working
     * before this shipped by POSTing to `login/device/code` and getting a real `user_code`
     * back; if sign-in ever starts failing at the phone, re-run that check first.
     *
     * Blank for eleven releases, which meant the Settings → About "Sign in" button could
     * only ever produce "No GitHub client ID is built into this version yet" — dead UI that
     * read as a broken dump button. [signIn] still guards for blank, because a future fork
     * without its own OAuth app should degrade to the paste rather than to a network error.
     */
    const val CLIENT_ID = "Ov23li9H6NMRyQH4lbA9"

    private const val UA = "DWM-Cockpit"

    /**
     * Lazy, and it has to be: `Looper.getMainLooper()` cannot run on a JVM test, so building
     * this eagerly made the whole object fail to initialise and took every parser test with
     * it — `ExceptionInInitializerError`, from a line that has nothing to do with parsing.
     * The parsers are pure so that they can be tested; a field that touches the framework at
     * class-init undoes that from the other end.
     */
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /* ------------------------------------------------------------ device flow */

    /** Step one's answer: what to show the user, and how to poll for the rest. */
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int
    )

    /**
     * Step three's answer.
     *
     * Four of these five are *not* errors in the HTTP sense — GitHub answers 200 with an
     * `error` field while the user is still on their phone. Treating a 200 as success is
     * the obvious bug here, so the states are named and [parsePoll] is a pure function with
     * a test for each one.
     */
    sealed class Poll {
        data class Token(val accessToken: String) : Poll()
        /** The user has not finished yet. Keep polling at the current interval. */
        object Pending : Poll()
        /** Polling too fast. GitHub's rule is to add five seconds to the interval. */
        object SlowDown : Poll()
        data class Failed(val reason: String) : Poll()
    }

    fun parseDeviceCode(json: String): DeviceCode {
        val o = JSONObject(json)
        return DeviceCode(
            deviceCode = o.getString("device_code"),
            userCode = o.getString("user_code"),
            verificationUri = o.optString("verification_uri", "https://github.com/login/device"),
            // Defaults matter: a missing interval polled at zero would get us rate-limited
            // into a slow_down loop, which looks exactly like "sign-in is broken".
            intervalSeconds = o.optInt("interval", 5).coerceAtLeast(1),
            expiresInSeconds = o.optInt("expires_in", 900)
        )
    }

    fun parsePoll(json: String): Poll {
        val o = JSONObject(json)
        o.optString("access_token", "").takeIf { it.isNotBlank() }?.let { return Poll.Token(it) }
        return when (val err = o.optString("error", "")) {
            "authorization_pending" -> Poll.Pending
            "slow_down" -> Poll.SlowDown
            "expired_token" -> Poll.Failed("The code expired. Try again.")
            "access_denied" -> Poll.Failed("Sign-in was refused on the phone.")
            "" -> Poll.Failed("GitHub sent no token and no error.")
            else -> Poll.Failed(o.optString("error_description", err))
        }
    }

    /**
     * Run the whole flow on a background thread.
     *
     * [onCode] fires once with the code to put on screen; [onDone] fires once at the end
     * with the account name or an error. Both on the main thread.
     */
    fun signIn(
        c: Context,
        onCode: (DeviceCode) -> Unit,
        onDone: (Result<String>) -> Unit
    ) {
        if (CLIENT_ID.isBlank()) {
            onDone(Result.failure(IllegalStateException(
                "No GitHub client ID is built into this version yet."
            )))
            return
        }
        Thread {
            val result = runCatching {
                val code = parseDeviceCode(
                    post(
                        "https://github.com/login/device/code",
                        "client_id=$CLIENT_ID&scope=gist"
                    )
                )
                main.post { onCode(code) }

                var interval = code.intervalSeconds
                val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L)
                    val body = post(
                        "https://github.com/login/oauth/access_token",
                        "client_id=$CLIENT_ID&device_code=${code.deviceCode}" +
                            "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    )
                    when (val poll = parsePoll(body)) {
                        is Poll.Token -> {
                            val login = whoAmI(poll.accessToken)
                            Prefs.setGithub(c, poll.accessToken, login)
                            return@runCatching login
                        }
                        Poll.Pending -> Unit
                        Poll.SlowDown -> interval += 5
                        is Poll.Failed -> throw IllegalStateException(poll.reason)
                    }
                }
                throw IllegalStateException("The code expired. Try again.")
            }
            main.post { onDone(result) }
        }.start()
    }

    fun signOut(c: Context) = Prefs.setGithub(c, null, null)

    private fun whoAmI(token: String): String =
        JSONObject(get("https://api.github.com/user", token)).optString("login", "GitHub")

    /* ------------------------------------------------------------------ gists */

    /**
     * Post [text] as a **secret** gist and return its URL.
     *
     * Secret rather than public: unlisted, not indexed and not on the owner's profile. It
     * is still readable by anyone holding the URL, which is why the dump goes through
     * `VehicleProbe.redact` first and why the confirm dialog says where it is going.
     */
    fun postGist(c: Context, name: String, description: String, text: String): String {
        val token = Prefs.githubToken(c) ?: throw IllegalStateException("Not signed in to GitHub.")
        val body = JSONObject().apply {
            put("description", description)
            put("public", false)
            put("files", JSONObject().put(name, JSONObject().put("content", text)))
        }.toString()
        val res = post("https://api.github.com/gists", body, token, "application/json")
        return JSONObject(res).optString("html_url", "(gist created)")
    }

    /**
     * The no-account fallback, for when GitHub is not set up or the token has been revoked.
     *
     * `dpaste.com` rather than `0x0.st`: the latter blocks automated clients outright, which
     * would have been discovered in the van rather than here. dpaste asks for a User-Agent
     * and no more than one request a second, both of which this satisfies.
     */
    fun postPaste(text: String): String =
        post("https://dpaste.com/api/v2/", "content=" + urlEncode(text) + "&expiry_days=30").trim()

    /* ------------------------------------------------------------------- http */

    private fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private fun post(
        url: String,
        body: String,
        token: String? = null,
        contentType: String = "application/x-www-form-urlencoded"
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", contentType)
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        return read(conn)
    }

    private fun get(url: String, token: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        return read(conn)
    }

    /**
     * GitHub puts the useful part of a failure in the *body*, not the status line, so the
     * error stream is read and included. "HTTP 401" alone would send the next investigation
     * looking at the network when the answer is "Bad credentials".
     */
    private fun read(conn: HttpURLConnection): String {
        val code = conn.responseCode
        if (code !in 200..299) {
            val detail = runCatching {
                conn.errorStream?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.take(300).orEmpty()
            conn.disconnect()
            throw IllegalStateException("HTTP $code${if (detail.isBlank()) "" else " — $detail"}")
        }
        return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
