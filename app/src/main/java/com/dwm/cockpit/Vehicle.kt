package com.dwm.cockpit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * The settings store used to be watched as a second opinion. Scan 3 disproved it
 * and it has been removed — do not put it back. `system/revserse_status` (vendor's
 * spelling) reads **1 while the car is in drive**, and across a scan in which
 * reverse was engaged *and* released the live watcher recorded zero writes to the
 * settings store. So the key does not track the gear; it is far more likely a
 * reverse-camera feature flag left switched on. `SYSTEM_LRREVERSE` sat at 0
 * through the same window. Watching either one could only ever contradict a
 * broadcast that was already right.
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

    fun start(c: Context) {
        startReceiver(c.applicationContext)
    }

    fun stop(c: Context) {
        val app = c.applicationContext
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
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
     * The vendor AIDL's opinion, from [CarInfo]. It outranks the broadcast and is
     * allowed to overwrite it: DO_MUTE only ever meant "the deck ducked its audio
     * and said the reason was reverse", while `onGear_Information` is the gear
     * itself. The broadcast stays because it needs no bind and works even if the
     * CAN service is dead.
     */
    fun onCarInfoReverse(reverse: Boolean, source: String) {
        if (state.reverse == reverse && state.source == source) return
        publish(state.copy(reverse = reverse, source = source, atMs = System.currentTimeMillis()))
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
