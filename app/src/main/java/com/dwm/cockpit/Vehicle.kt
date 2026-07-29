package com.dwm.cockpit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Live vehicle state — reverse gear, phone call, ignition — from the one channel
 * this deck has actually been caught using.
 *
 * Scan 3 (2026-07-29) is the first of three that ever saw a vendor broadcast land:
 *
 *     com.unisound.intent.action.DO_MUTE     call = 0   reverse = 1
 *     com.unisound.intent.action.DO_UNMUTE   call = 0   reverse = 0
 *
 * That is the deck telling its own apps to duck the audio, and it carries the
 * reason with it: `reverse` goes to 1 as the car is put into reverse and back to 0
 * when it comes out. Real gear state, sent as a plain implicit broadcast, readable
 * with no permission and no AIDL. The two earlier scans, listening on ~50 guessed
 * action names, caught nothing — this only arrived once v0.16 started listening on
 * names read out of the vendor manifests.
 *
 * The settings store is a second opinion here, not the primary source. The deck
 * does keep `system/revserse_status` (vendor's spelling), but v0.16's before/after
 * diff never saw it move: reverse was engaged *and released* inside the scan
 * window, so the two snapshots matched. A transient is invisible to a two-point
 * diff and obvious to an observer, so this watches rather than samples.
 */
object Vehicle {

    data class State(
        /** Car is in reverse. */
        val reverse: Boolean = false,
        /** A phone call is up — the deck mutes for this too. */
        val onCall: Boolean = false,
        /** Ignition/accessory line is live. Assumed true until told otherwise;
         *  DWM is only running because the deck is powered. */
        val accOn: Boolean = true,
        /** What last told us something, for the diagnostics screen. */
        val source: String? = null,
        val atMs: Long = 0L
    )

    @Volatile
    var state = State()
        private set

    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    private var receiver: BroadcastReceiver? = null
    private var observer: ContentObserver? = null

    fun addListener(l: (State) -> Unit) {
        listeners += l
        l(state)
    }

    fun removeListener(l: (State) -> Unit) {
        listeners -= l
    }

    val running: Boolean get() = receiver != null

    /**
     * Actions the deck's own apps filter on, taken from their manifests. DO_MUTE
     * and DO_UNMUTE are the pair confirmed to carry vehicle extras; the ACC and
     * sleep/wake actions come from `com.tw.core`'s BootReceiver and cost nothing
     * to also listen for.
     */
    private val ACTIONS = listOf(
        "com.unisound.intent.action.DO_MUTE",
        "com.unisound.intent.action.DO_UNMUTE",
        "com.unisound.intent.action.ACC_ON",
        "com.unisound.intent.action.ACC_OFF",
        "com.unisound.intent.action.DO_SLEEP",
        "com.unisound.intent.action.DO_WAKEUP",
        "com.unisound.intent.action.DO_SHUTDOWN"
    )

    /** The deck spells it "revserse". Both are watched because a different ROM
     *  build may well fix the typo. */
    private val REVERSE_KEYS = listOf("revserse_status", "reverse_status", "SYSTEM_LRREVERSE")

    fun start(c: Context) {
        val app = c.applicationContext
        startReceiver(app)
        startObserver(app)
    }

    fun stop(c: Context) {
        val app = c.applicationContext
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        observer?.let { runCatching { app.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    private fun startReceiver(app: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val action = intent.action ?: return
                var s = state
                // Only the extras that are present move anything — DO_MUTE fires
                // for calls as well as reverse, and a call must not clear the gear.
                flag(intent, "reverse")?.let { s = s.copy(reverse = it) }
                flag(intent, "call")?.let { s = s.copy(onCall = it) }
                when (action) {
                    "com.unisound.intent.action.ACC_ON",
                    "com.unisound.intent.action.DO_WAKEUP" -> s = s.copy(accOn = true)
                    "com.unisound.intent.action.ACC_OFF",
                    "com.unisound.intent.action.DO_SLEEP",
                    "com.unisound.intent.action.DO_SHUTDOWN" -> s = s.copy(accOn = false)
                }
                publish(s.copy(source = action.substringAfterLast('.'), atMs = System.currentTimeMillis()))
            }
        }
        val f = IntentFilter().apply { ACTIONS.forEach { addAction(it) } }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                app.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
            else
                @Suppress("UnspecifiedRegisterReceiverFlag") app.registerReceiver(r, f)
            receiver = r
        }
    }

    /**
     * Second source: the settings key the deck keeps reverse in. Registered per
     * key rather than on the whole `system` table so the callback isn't woken by
     * every volume tick.
     */
    private fun startObserver(app: Context) {
        if (observer != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange(selfChange, null)
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                val key = uri?.lastPathSegment ?: return
                val v = runCatching { Settings.System.getString(app.contentResolver, key) }.getOrNull()
                val on = truthy(v) ?: return
                if (on == state.reverse) return
                publish(state.copy(reverse = on, source = "settings/$key", atMs = System.currentTimeMillis()))
            }
        }
        runCatching {
            for (k in REVERSE_KEYS) {
                app.contentResolver.registerContentObserver(Settings.System.getUriFor(k), false, o)
            }
            observer = o
        }
    }

    private fun publish(s: State) {
        state = s
        for (l in listeners) runCatching { l(s) }
    }

    /** Vendor extras arrive as int, boolean or string depending on who sent them;
     *  absent means "no opinion", which is not the same as false. */
    private fun flag(intent: Intent, key: String): Boolean? {
        if (!intent.hasExtra(key)) return null
        val v = runCatching { intent.extras?.get(key) }.getOrNull()
        return when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> truthy(v)
            else -> null
        }
    }

    private fun truthy(v: String?): Boolean? = when {
        v == null -> null
        v.equals("true", true) -> true
        v.equals("false", true) -> false
        else -> v.trim().toIntOrNull()?.let { it != 0 }
    }

    /** One line for the diagnostics panel. */
    fun summary(): String {
        val s = state
        if (s.atMs == 0L) return "no vehicle signal yet"
        return "reverse=${if (s.reverse) "YES" else "no"} call=${if (s.onCall) "yes" else "no"} " +
            "acc=${if (s.accOn) "on" else "off"} (via ${s.source})"
    }
}
