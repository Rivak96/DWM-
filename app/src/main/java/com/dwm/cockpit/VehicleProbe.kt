package com.dwm.cockpit

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Finds the deck's CAN-bus data without root, ADB or Shizuku.
 *
 * There is no standard Android API for this — aftermarket head units aren't
 * Android Automotive, so `android.car`/CarPropertyManager doesn't exist. What the
 * vendor CAN apps DO (confirmed from the reverse-engineered Microntek `CanBusServer`)
 * is publish decoded state two ways that a normal app can read:
 *
 *  1. `Settings.System.putInt(resolver, "com.microntek.hiworld.ari", 0)` — the
 *     settings provider is world-*readable*; only writing needs a permission. So
 *     any key the CAN service stashes there is ours for free.
 *  2. `sendBroadcast(Intent("com.ahucanbus.display").putExtra("text", bytes))` —
 *     a plain implicit broadcast. A runtime-registered receiver still catches
 *     implicit broadcasts on Android 8+ (only manifest ones were restricted).
 *
 * Both are per-unit and undocumented, so we discover them empirically: snapshot
 * the settings store, have the user poke the AC / lights, snapshot again, and diff.
 * Whatever moved is the vehicle data.
 */
object VehicleProbe {

    data class Change(val key: String, val before: String?, val after: String?)

    // ---- settings store ---------------------------------------------------

    /** Every name/value pair in the three settings namespaces. No permission
     *  needed to read; unreadable namespaces are skipped rather than fatal. */
    fun snapshot(c: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val spaces = listOf(
            "system" to Settings.System.CONTENT_URI,
            "global" to Settings.Global.CONTENT_URI,
            "secure" to Settings.Secure.CONTENT_URI
        )
        for ((ns, uri) in spaces) {
            runCatching {
                c.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { cur ->
                    val ni = cur.getColumnIndex("name")
                    val vi = cur.getColumnIndex("value")
                    if (ni < 0 || vi < 0) return@use
                    while (cur.moveToNext()) {
                        val n = cur.getString(ni) ?: continue
                        out["$ns/$n"] = cur.getString(vi) ?: ""
                    }
                }
            }
        }
        return out
    }

    fun diff(before: Map<String, String>, after: Map<String, String>): List<Change> {
        val keys = (before.keys + after.keys).toSortedSet()
        return keys.mapNotNull { k ->
            val b = before[k]
            val a = after[k]
            if (b == a) null else Change(k, b, a)
        }.filterNot { isNoise(it.key) }
    }

    /** Clock ticks, screen state and battery churn constantly and would bury the
     *  handful of keys we actually care about. */
    private fun isNoise(key: String) = NOISE.any { key.contains(it, ignoreCase = true) }

    private val NOISE = listOf(
        "time", "clock", "battery", "screen_state", "boot_count", "brightness",
        "volume_", "last_", "_count", "seq", "uptime", "usage", "sync", "wifi_scan",
        "location_", "device_provisioned", "airplane", "notification_", "ringer"
    )

    // ---- vendor apps ------------------------------------------------------

    /** Packages that look like the deck's CAN/vehicle stack, with anything of
     *  theirs we could talk to. An exported provider with no read permission is
     *  directly queryable. */
    fun vehicleApps(c: Context): List<String> {
        val pm = c.packageManager
        val out = ArrayList<String>()
        val pkgs = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        for (p in pkgs) {
            val name = p.packageName
            if (!VENDOR_HINTS.any { name.contains(it, ignoreCase = true) }) continue
            val sb = StringBuilder(name)
            runCatching {
                val full = pm.getPackageInfo(
                    name,
                    PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES
                )
                full.providers?.forEach { pr ->
                    sb.append("\n    provider ").append(pr.authority)
                        .append(if (pr.exported) " [exported]" else " [private]")
                    pr.readPermission?.let { sb.append(" read=").append(it) }
                }
                full.receivers?.filter { it.exported }?.forEach {
                    sb.append("\n    receiver ").append(it.name.substringAfterLast('.'))
                }
                full.services?.filter { it.exported }?.forEach {
                    sb.append("\n    service ").append(it.name.substringAfterLast('.'))
                }
            }
            out.add(sb.toString())
        }
        return out
    }

    private val VENDOR_HINTS = listOf(
        "canbus", "can_bus", "microntek", "syu", "hzbhd", "txznet", "autochips",
        "zhonghong", "wits", "hct", "fyt", "carsetting", "carservice", "vehicle",
        "aircon", "climate", "hiworld", "raise", "mcu", "dvr", "obd"
    )

    /** Try reading an exported provider straight out. */
    fun probeProvider(c: Context, authority: String): String {
        val uri = Uri.parse("content://$authority")
        return runCatching {
            c.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                val cols = (0 until cur.columnCount).map { cur.getColumnName(it) }
                val sb = StringBuilder("columns: ").append(cols.joinToString())
                var rows = 0
                while (cur.moveToNext() && rows < 20) {
                    sb.append("\n  ")
                    for (i in cols.indices) sb.append(runCatching { cur.getString(i) }.getOrNull()).append(" | ")
                    rows++
                }
                sb.toString()
            } ?: "query returned null"
        }.getOrElse { "not readable: ${it.javaClass.simpleName} ${it.message}" }
    }

    // ---- broadcast sniffer ------------------------------------------------

    /**
     * Listens for the CAN broadcasts these units are known to emit. We can't
     * enumerate every broadcast on the device without root, so this is a candidate
     * list — a hit proves the channel exists and shows us the extras.
     */
    class Sniffer {
        private val hits = LinkedHashMap<String, String>()
        private var receiver: BroadcastReceiver? = null

        fun start(c: Context) {
            if (receiver != null) return
            val r = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val action = intent.action ?: return
                    val sb = StringBuilder()
                    intent.extras?.let { ex ->
                        for (k in ex.keySet()) {
                            val v = runCatching { ex.get(k) }.getOrNull()
                            val shown = when (v) {
                                is ByteArray -> v.joinToString(" ") { b -> "%02X".format(b) }
                                else -> v?.toString()
                            }
                            sb.append("\n    ").append(k).append(" = ").append(shown)
                        }
                    }
                    hits[action] = if (sb.isEmpty()) "(no extras)" else sb.toString()
                }
            }
            val f = IntentFilter().apply { CANDIDATE_ACTIONS.forEach { addAction(it) } }
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    c.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
                else
                    @Suppress("UnspecifiedRegisterReceiverFlag") c.registerReceiver(r, f)
                receiver = r
            }
        }

        fun stop(c: Context) {
            receiver?.let { runCatching { c.unregisterReceiver(it) } }
            receiver = null
        }

        fun report(): String =
            if (hits.isEmpty()) "No CAN broadcasts seen yet.\n\nThis only proves the candidate list missed it — the settings-diff test is the reliable one."
            else hits.entries.joinToString("\n\n") { "${it.key}${it.value}" }
    }

    // ---- report ------------------------------------------------------------

    /**
     * Everything we learned, as one plain-text file the user can hand back.
     *
     * Deliberately NOT a full settings dump — that store holds device identifiers
     * and account/network details, and this file gets uploaded. We include the
     * diff (which is the actual answer), keys whose *names* match vehicle hints,
     * and the app/broadcast scans, with anything identifier-shaped redacted.
     */
    fun buildReport(
        c: Context,
        before: Map<String, String>?,
        after: Map<String, String>,
        sniffer: Sniffer?
    ): String {
        val sb = StringBuilder()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        sb.append("DWM vehicle / CAN scan\n")
        sb.append(stamp).append('\n')
        sb.append("DWM v").append(Updater.currentVersionName(c)).append('\n')
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append("  (").append(Build.DEVICE).append(")\n")
        sb.append("android: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n')
        sb.append("fingerprint: ").append(Build.FINGERPRINT).append("\n\n")

        sb.append("=== 1. SETTINGS THAT CHANGED WHILE YOU POKED THE CAR ===\n")
        sb.append("(this is the important part — these are live vehicle values)\n\n")
        if (before == null) {
            sb.append("No 'before' snapshot — scan was not started properly.\n")
        } else {
            val changes = diff(before, after)
            if (changes.isEmpty()) {
                sb.append("NOTHING CHANGED.\n")
                sb.append("Either the deck keeps CAN state inside its own app, or the\n")
                sb.append("controls you changed don't route through the settings store.\n")
            } else {
                sb.append(changes.size).append(" key(s) changed:\n\n")
                for (ch in changes) {
                    sb.append(ch.key).append('\n')
                    sb.append("   before: ").append(redact(ch.key, ch.before)).append('\n')
                    sb.append("   after:  ").append(redact(ch.key, ch.after)).append('\n')
                }
            }
        }

        sb.append("\n\n=== 2. SETTINGS KEYS THAT LOOK VEHICLE-RELATED ===\n")
        sb.append("(matched by name; current values, whether or not they changed)\n\n")
        val hinted = after.filterKeys { k -> VENDOR_HINTS.any { k.contains(it, ignoreCase = true) } }
        if (hinted.isEmpty()) sb.append("None matched.\n")
        else hinted.forEach { (k, v) -> sb.append(k).append(" = ").append(redact(k, v)).append('\n') }
        sb.append("\n(total keys readable in the settings store: ").append(after.size).append(")\n")

        sb.append("\n\n=== 3. VEHICLE / CAN APPS ON THE DECK ===\n\n")
        val apps = vehicleApps(c)
        if (apps.isEmpty()) sb.append("No packages matched the CAN/vehicle name hints.\n")
        else sb.append(apps.joinToString("\n\n"))
            .append("\n\nAn [exported] provider with no read= permission can be queried directly.\n")

        sb.append("\n\n=== 4. CAN BROADCASTS SEEN DURING THE SCAN ===\n\n")
        sb.append(sniffer?.report() ?: "Sniffer not running.")
        sb.append('\n')
        return sb.toString()
    }

    /** Blank out anything that looks like an identifier or credential — this file
     *  leaves the device. */
    private fun redact(key: String, value: String?): String {
        if (value == null) return "(absent)"
        val k = key.lowercase()
        if (SENSITIVE.any { k.contains(it) }) return "<redacted ${value.length} chars>"
        return if (value.length > 400) value.take(400) + "… <truncated>" else value
    }

    private val SENSITIVE = listOf(
        "android_id", "serial", "imei", "imsi", "mac", "account", "email", "token",
        "password", "passwd", "ssid", "psk", "secret", "gsf", "advertising", "subscriber"
    )

    /**
     * Write the report where the user can actually get at it. On API 29+ that's
     * the shared Downloads collection via MediaStore (no permission needed under
     * scoped storage, and the returned content:// URI is directly shareable).
     */
    fun saveReport(c: Context, text: String): Pair<Uri, String>? {
        val name = "dwm-vehicle-scan-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                c.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                c.contentResolver.update(uri, values, null, null)
                uri to "Downloads/$name"
            }.getOrNull()
        }

        // Pre-Q: app-private external dir, shared through our FileProvider.
        return runCatching {
            val dir = c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: c.filesDir
            val f = File(dir, name)
            f.writeText(text)
            FileProvider.getUriForFile(c, "${c.packageName}.files", f) to f.absolutePath
        }.getOrNull()
    }

    /** Known/likely CAN broadcast actions across the common head-unit platforms. */
    private val CANDIDATE_ACTIONS = listOf(
        "com.ahucanbus.display",
        "com.microntek.canbusdisplay",
        "com.microntek.canbus",
        "com.microntek.bootcheck",
        "com.microntek.CANBUS",
        "com.microntek.aircondition",
        "android.microntek.canbus",
        "com.syu.canbus",
        "com.syu.ms.canbus",
        "com.syu.ipc.canbus",
        "com.hzbhd.canbus",
        "com.hzbhd.canbus.data",
        "com.txznet.canbus",
        "com.autochips.canbus",
        "com.zhonghong.canbus",
        "com.wits.canbus",
        "com.hct.canbus",
        "com.fyt.canbus",
        "com.android.canbus",
        "android.intent.action.CANBUS",
        "android.intent.action.CAN_DATA",
        "com.car.canbus.data",
        "com.canbus.aircondition",
        "com.aircondition.display",
        "com.dvd.aircondition"
    )
}
