package com.dwm.cockpit

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

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

    /**
     * Packages that look like the deck's CAN/vehicle stack, with anything of
     * theirs we could talk to. An exported provider with no read permission is
     * directly queryable.
     *
     * Membership used to be decided by the package *name* alone, and on this deck
     * that returned exactly one app (`com.dofun.carsetting`) while the full dump in
     * the non-AOSP section plainly listed `com.tw.carinfoservice`, `com.syt.tmps`
     * and `com.tw.carchoose`'s MCUService. "carinfoservice" contains none of the
     * hints and neither does "tmps". So the test is now evidence, not spelling: a
     * package qualifies if its name, one of its component class names, or one of
     * the actions its manifest declares mentions something vehicle-shaped.
     */
    fun vehicleApps(c: Context, scan: ManifestScan): List<String> {
        val pm = c.packageManager
        val out = ArrayList<String>()
        val pkgs = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        for (p in pkgs) {
            val name = p.packageName
            if (AOSP_PREFIXES.any { name == it || name.startsWith("$it.") }) continue
            val why = evidence(name, scan)
            if (why.isEmpty()) continue
            val sb = StringBuilder(name)
            sb.append("\n    matched on: ").append(why.joinToString(", "))
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
                // Whether a component *declares* a permission and whether it
                // *enforces* one are different facts, and only the second decides
                // if we can bind. com.tw.carinfoservice declares USE_AIDL; that
                // says nothing about whether CarService is actually behind it.
                full.receivers?.filter { it.exported }?.forEach {
                    sb.append("\n    receiver ").append(it.name.substringAfterLast('.'))
                        .append(guard(it.permission))
                }
                full.services?.filter { it.exported }?.forEach {
                    sb.append("\n    service ").append(it.name.substringAfterLast('.'))
                        .append(guard(it.permission))
                }
            }
            out.add(sb.toString())
        }
        return out
    }

    /** How an exported component is guarded. "UNGUARDED" is the interesting word:
     *  an exported service with no permission attribute can be bound by anyone. */
    private fun guard(permission: String?) =
        if (permission == null) "  [UNGUARDED]" else "  [needs $permission]"

    /**
     * What each vendor-declared permission actually costs us.
     *
     * This is the single fact that decides whether `com.tw.carinfoservice`'s AIDL
     * is reachable, and the scan has never reported it. `normal` is free — declare
     * `<uses-permission>` and the bind just works. `dangerous` needs a runtime
     * prompt but is still gettable. `signature` means only an app signed with the
     * vendor's key gets in and there is no way around it short of a system build,
     * at which point the serial port is the better target.
     */
    fun permissionLevels(c: Context, scan: ManifestScan): String {
        val pm = c.packageManager
        val names = scan.permissions.values.flatten().distinct().sorted()
        if (names.isEmpty()) return "No vendor app declares a custom permission."
        val sb = StringBuilder()
        for (n in names) {
            val info = runCatching { pm.getPermissionInfo(n, 0) }.getOrNull()
            sb.append(n).append("\n    ")
            if (info == null) {
                sb.append("not resolvable (declaring app may be disabled)")
            } else {
                val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.protection
                else @Suppress("DEPRECATION") (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
                sb.append(
                    when (base) {
                        PermissionInfo.PROTECTION_NORMAL -> "normal — GETTABLE, just add <uses-permission>"
                        PermissionInfo.PROTECTION_DANGEROUS -> "dangerous — gettable via a runtime prompt"
                        PermissionInfo.PROTECTION_SIGNATURE -> "signature — vendor-signed apps only, closed to us"
                        else -> "protection level $base"
                    }
                )
                sb.append("\n    held: ").append(
                    if (pm.checkPermission(n, c.packageName) == PackageManager.PERMISSION_GRANTED)
                        "YES — DWM already has this"
                    else "no"
                )
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Why a package was called vehicle-related — shown in the report so a wrong
     *  match is obvious rather than mysterious. Capped; the point is the reason,
     *  not an exhaustive list. */
    private fun evidence(pkg: String, scan: ManifestScan): List<String> {
        val hits = LinkedHashSet<String>()
        if (VENDOR_HINTS.any { pkg.contains(it, ignoreCase = true) }) hits += "package name"
        scan.actionsByComponent[pkg]?.forEach { (component, actions) ->
            if (VENDOR_HINTS.any { component.contains(it, ignoreCase = true) })
                hits += component.substringAfterLast('.')
            actions.filterTo(hits) { a -> VENDOR_HINTS.any { a.contains(it, ignoreCase = true) } }
        }
        return hits.take(8)
    }

    /** Words that mean "this touches the car". Used to flag packages and actions
     *  as worth a look — never to decide what gets scanned in the first place.
     *
     *  Scan 3 flagged 3 keys out of 408 while the raw name list it printed
     *  alongside was full of ones it walked straight past: `DOOR`, `SYSTEM_DOOR2`,
     *  `SHOW_VOLTAGE`, `TEMP_MODE`, `SYSTEM_FRADAR`, `TWCameraBrake`. The list had
     *  "cardoor" and "doorstatus" but not "door". Worse, it had "reverse" and the
     *  deck spells its key `revserse_status` — a vendor typo beat the matcher. So
     *  the hints are now short stems, and anything that looks like a state word in
     *  a settings key earns its place. */
    private val VENDOR_HINTS = listOf(
        "canbus", "can_bus", "carinfo", "car_info", "cardoor", "carchoose",
        "carassistant", "microntek", "syu", "hzbhd", "txznet", "autochips",
        "zhonghong", "wits", "hct", "fyt", "carsetting", "carservice", "vehicle",
        "aircon", "air_condition", "climate", "hiworld", "raise", "mcu", "dvr",
        "obd", "tpms", "tmps", "handbrake", "seatbelt", "odometer",
        "coolant", "steering", "acc_on", "acc_off", "ig_on", "illumination",
        // Stems, so a vendor typo or an unexpected prefix can't dodge the match.
        "reverse", "revserse", "door", "brake", "voltage", "radar", "gear",
        "seat", "temp_mode", "ampvol", "amplifier", "trip", "fuel", "rpm",
        "wheel", "unisound", "dofun", "carplay", "zlink",
        // Surround view. A 12-pin 360 kit stitches four AHD cameras in a module on
        // the mainboard and a pre-installed vendor app draws the result, so the app
        // is already on the deck before the cameras are. These stems are what names
        // it. "360" is broad on purpose — evidence() prints why a package matched,
        // so a wrong hit reads as wrong instead of as a mystery.
        "360", "avm", "panoram", "birdview", "surround", "svm", "aroundview", "ahd"
    )

    /**
     * Installed non-AOSP packages whose *name* carries a [VENDOR_HINTS] stem.
     *
     * Deliberately name-only, unlike [evidence], which also matches components and
     * actions: this feeds a picker dialog, and building a manifest scan of every
     * APK on the deck to populate a list of buttons would be absurd. A vendor app
     * that hides behind an innocent package name won't show up here — it will still
     * show up in the scan report, which is where the looking actually happens.
     */
    fun hintedPackages(c: Context): List<String> {
        val pm = c.packageManager
        val pkgs = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        return pkgs.map { it.packageName }
            .filterNot { name -> AOSP_PREFIXES.any { name == it || name.startsWith("$it.") } }
            .filter { name -> VENDOR_HINTS.any { name.contains(it, ignoreCase = true) } }
            .sorted()
            .take(24)
    }

    /**
     * Every installed package, so the vendor stack can be eyeballed whole.
     * The hint list in [vehicleApps] only catches packages whose *name* says
     * "canbus"/"carsetting"/etc — on this deck that found `com.dofun.carsetting`
     * and missed everything else Dofun ships. This is the safety net.
     */
    fun allPackages(c: Context): String {
        val pm = c.packageManager
        val pkgs = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        val (system, user) = pkgs.map { it to ((it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) }
            .partition { it.second }
        fun fmt(list: List<Pair<android.content.pm.PackageInfo, Boolean>>) =
            list.map { it.first.packageName }.sorted().joinToString("\n")
        return "-- system (${system.size}) --\n${fmt(system)}\n\n-- user (${user.size}) --\n${fmt(user)}"
    }

    /**
     * Full component dump for every package that isn't stock AOSP/Google.
     *
     * This used to expand a hand-written list of vendor prefixes, and that list is
     * exactly how `com.tw.carinfoservice` — the actual CAN service on this deck —
     * got missed: the list said `com.ts`, and "carinfoservice" doesn't contain any
     * of the name hints either. Guessing prefixes is the wrong shape of solution.
     * Invert it: dump everything, minus the platform packages we know are noise.
     */
    fun nonAospDump(c: Context): String {
        val pm = c.packageManager
        val pkgs = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
            .map { it.packageName }
            .filterNot { p -> AOSP_PREFIXES.any { p == it || p.startsWith("$it.") } }
            .sorted()

        val sb = StringBuilder()
        for (p in pkgs) {
            sb.append(p).append('\n')
            runCatching {
                val info = pm.getPackageInfo(
                    p,
                    PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS or
                        PackageManager.GET_SERVICES
                )
                info.providers?.forEach {
                    sb.append("    provider ").append(it.authority)
                        .append(if (it.exported) " [exported]" else " [private]")
                        .append(it.readPermission?.let { r -> " read=$r" } ?: "").append('\n')
                }
                info.receivers?.forEach {
                    sb.append("    receiver ").append(it.name)
                        .append(if (it.exported) " [exported]" else " [private]")
                        .append(if (it.exported) guard(it.permission) else "").append('\n')
                }
                info.services?.forEach {
                    sb.append("    service ").append(it.name)
                        .append(if (it.exported) " [exported]" else " [private]")
                        .append(if (it.exported) guard(it.permission) else "").append('\n')
                }
            }.onFailure { sb.append("    (components unreadable: ").append(it.message).append(")\n") }
            sb.append('\n')
        }
        return sb.toString().ifBlank { "Nothing outside AOSP." }
    }

    private val AOSP_PREFIXES = listOf(
        "android", "com.android", "com.google", "androidx", "org.chromium",
        "com.qualcomm", "com.spreadtrum", "com.unisoc"
    )

    // ---- vendor manifests --------------------------------------------------

    /** Every non-platform action name declared on the deck, and which component
     *  of which package listens for it. */
    data class ManifestScan(
        /** package -> "receiver com.x.Y" -> the actions that component filters on */
        val actionsByComponent: Map<String, Map<String, List<String>>>,
        /** flat union, for [Sniffer] to register on */
        val allActions: Set<String>,
        /** custom `<permission>` declarations — these are what locks us out */
        val permissions: Map<String, List<String>>,
        val packagesRead: Int,
        val packagesFailed: List<String>
    )

    @Volatile private var cachedScan: ManifestScan? = null

    /**
     * Read what the vendor apps actually listen for, instead of guessing.
     *
     * [Sniffer] shipped with a hand-written list of ~50 plausible CAN action names
     * and caught nothing, twice. That was never going to work — the names are
     * per-vendor and undocumented. But they aren't *hidden*: every installed APK is
     * world-readable at `applicationInfo.publicSourceDir`, and the actions its
     * receivers filter on are sitting in its binary AndroidManifest.xml. So open the
     * APK as a zip, parse the manifest, and take the real list. Same inversion that
     * fixed the package dump — stop guessing, go read.
     *
     * Parsing ~40 APKs takes a second or two, so this memoises; call it off the
     * main thread the first time.
     */
    fun manifestScan(c: Context): ManifestScan =
        cachedScan ?: buildManifestScan(c).also { cachedScan = it }

    private fun buildManifestScan(c: Context): ManifestScan {
        val pm = c.packageManager
        val pkgs = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        val byPkg = LinkedHashMap<String, Map<String, List<String>>>()
        val all = LinkedHashSet<String>()
        val perms = LinkedHashMap<String, List<String>>()
        val failed = ArrayList<String>()
        var read = 0

        for (info in pkgs.sortedBy { it.packageName }) {
            val pkg = info.packageName
            if (AOSP_PREFIXES.any { pkg == it || pkg.startsWith("$it.") }) continue
            val apk = info.applicationInfo?.publicSourceDir
            if (apk == null) { failed += "$pkg (no apk path)"; continue }
            val parsed = runCatching { Axml.parseApk(apk) }.getOrNull()
            if (parsed == null) { failed += "$pkg (unreadable)"; continue }
            read++

            val comps = parsed.byComponent
                .mapValues { (_, v) -> v.filterNot(::isPlatformName).distinct() }
                .filterValues { it.isNotEmpty() }
            if (comps.isNotEmpty()) byPkg[pkg] = comps
            all += parsed.allActions.filterNot(::isPlatformName)
            parsed.permissions.filterNot(::isPlatformName)
                .takeIf { it.isNotEmpty() }?.let { perms[pkg] = it }
        }
        return ManifestScan(byPkg, all, perms, read, failed)
    }

    /** The scan as text, vehicle-looking packages hoisted to the top so the
     *  interesting names aren't buried under Netflix's. */
    private fun manifestReport(scan: ManifestScan): String {
        if (scan.actionsByComponent.isEmpty())
            return "No vendor-defined actions found (read ${scan.packagesRead} APK manifest(s))."

        val interesting = { pkg: String, comps: Map<String, List<String>> ->
            VENDOR_HINTS.any { w ->
                pkg.contains(w, true) ||
                    comps.any { (k, v) -> k.contains(w, true) || v.any { it.contains(w, true) } }
            }
        }
        val (hot, rest) = scan.actionsByComponent.entries.partition { interesting(it.key, it.value) }

        val sb = StringBuilder()
        fun emit(entries: List<Map.Entry<String, Map<String, List<String>>>>) {
            for ((pkg, comps) in entries) {
                sb.append(pkg).append('\n')
                scan.permissions[pkg]?.forEach { sb.append("    declares permission ").append(it).append('\n') }
                for ((component, actions) in comps) {
                    sb.append("    ").append(component).append('\n')
                    actions.forEach { sb.append("        ").append(it).append('\n') }
                }
                sb.append('\n')
            }
        }
        sb.append("--- vehicle-looking ---\n\n")
        if (hot.isEmpty()) sb.append("(none)\n\n") else emit(hot)
        sb.append("--- everything else ---\n\n")
        emit(rest)
        sb.append("(read ").append(scan.packagesRead).append(" APK manifest(s)")
        if (scan.packagesFailed.isNotEmpty())
            sb.append("; could not read: ").append(scan.packagesFailed.joinToString())
        sb.append(")\n")
        return sb.toString()
    }

    /** Platform-defined names carry no information about this deck. */
    private fun isPlatformName(n: String) =
        n.startsWith("android.") || n.startsWith("androidx.") ||
            n.startsWith("com.android.") || n.startsWith("com.google.") ||
            n.startsWith("org.chromium.")

    /**
     * Just enough of Android's binary-XML (AXML) format to pull
     * `<action android:name>` out of a packaged manifest: the string pool, and the
     * element tree walked shallowly enough to know which `<receiver>`/`<service>`
     * an `<action>` sits under. Every read is bounds-checked and the whole thing is
     * wrapped in runCatching upstream — a malformed manifest must not take the scan
     * down with it.
     *
     * Internal rather than private so `AxmlTest` can run it against a real APK on
     * the JVM. Hand-computed chunk offsets fail silently — they return plausible
     * garbage rather than throwing — and this ships to a head unit that can't be
     * attached to a debugger, so it gets checked against a manifest whose contents
     * are known before it goes anywhere.
     */
    internal object Axml {

        private const val TYPE_XML = 0x0003
        private const val TYPE_STRING_POOL = 0x0001
        private const val TYPE_START_TAG = 0x0102
        private const val TYPE_END_TAG = 0x0103
        private const val TYPE_STRING = 0x03
        private const val FLAG_UTF8 = 0x0100

        class Parsed {
            val byComponent = LinkedHashMap<String, MutableList<String>>()
            val allActions = LinkedHashSet<String>()
            val permissions = LinkedHashSet<String>()
        }

        fun parseApk(path: String): Parsed? = ZipFile(path).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return null
            zip.getInputStream(entry).use { parse(it.readBytes()) }
        }

        fun parse(data: ByteArray): Parsed? {
            if (data.size < 8) return null
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            if ((bb.short.toInt() and 0xFFFF) != TYPE_XML) return null
            bb.short; bb.int                                    // header size, file size

            var pool: Array<String> = emptyArray()
            val out = Parsed()
            var depth = 0
            var component: String? = null
            var componentDepth = -1

            while (bb.remaining() >= 8) {
                val start = bb.position()
                val type = bb.short.toInt() and 0xFFFF
                val headerSize = bb.short.toInt() and 0xFFFF
                val size = bb.int
                if (size < 8 || start.toLong() + size > data.size) break

                when (type) {
                    TYPE_STRING_POOL -> pool = readPool(data, bb, start, headerSize)

                    TYPE_START_TAG -> {
                        depth++
                        bb.position(start + headerSize)
                        bb.int                                  // namespace
                        val tag = pool.getOrElse(bb.int) { "" }
                        val attrStart = bb.short.toInt() and 0xFFFF
                        val attrSize = (bb.short.toInt() and 0xFFFF).coerceAtLeast(20)
                        val attrCount = bb.short.toInt() and 0xFFFF
                        val name = androidName(
                            data, bb, pool, start + headerSize + attrStart, attrSize, attrCount
                        )
                        if (name != null) when (tag) {
                            "receiver", "service", "activity", "activity-alias", "provider" -> {
                                component = "$tag $name"
                                componentDepth = depth
                            }
                            "action" -> {
                                out.allActions += name
                                component?.let { out.byComponent.getOrPut(it) { ArrayList() } += name }
                            }
                            "permission" -> out.permissions += name
                        }
                    }

                    TYPE_END_TAG -> {
                        if (depth == componentDepth) { component = null; componentDepth = -1 }
                        depth--
                    }
                }
                bb.position(start + size)
            }
            return out
        }

        /** Value of this element's `android:name`, or null if it has none. */
        private fun androidName(
            data: ByteArray, bb: ByteBuffer, pool: Array<String>,
            at: Int, attrSize: Int, count: Int
        ): String? {
            var p = at
            repeat(count) {
                if (p + 20 > data.size) return null
                bb.position(p)
                bb.int                                          // namespace
                val key = pool.getOrElse(bb.int) { "" }
                val raw = bb.int
                bb.short; bb.get()                              // value size, padding
                val valueType = bb.get().toInt() and 0xFF
                val value = bb.int
                if (key == "name") return when {
                    raw in pool.indices -> pool[raw]
                    valueType == TYPE_STRING && value in pool.indices -> pool[value]
                    else -> null
                }
                p += attrSize
            }
            return null
        }

        private fun readPool(data: ByteArray, bb: ByteBuffer, start: Int, headerSize: Int): Array<String> {
            bb.position(start + 8)
            val count = bb.int
            bb.int                                              // style count
            val utf8 = (bb.int and FLAG_UTF8) != 0
            val dataStart = start + bb.int
            if (count <= 0 || count > (1 shl 20)) return emptyArray()
            bb.position(start + headerSize)
            val offsets = IntArray(count) { bb.int }
            return Array(count) { i ->
                runCatching { decode(data, dataStart + offsets[i], utf8) }.getOrDefault("")
            }
        }

        /** Pool strings are length-prefixed with a 1-or-2 unit varint, then either
         *  UTF-8 bytes or UTF-16LE code units depending on the pool's flag. */
        private fun decode(d: ByteArray, at: Int, utf8: Boolean): String {
            var p = at
            fun u8(): Int = d[p++].toInt() and 0xFF
            fun u16(): Int {
                val v = (d[p].toInt() and 0xFF) or ((d[p + 1].toInt() and 0xFF) shl 8)
                p += 2
                return v
            }
            return if (utf8) {
                var chars = u8(); if (chars and 0x80 != 0) chars = ((chars and 0x7F) shl 8) or u8()
                var bytes = u8(); if (bytes and 0x80 != 0) bytes = ((bytes and 0x7F) shl 8) or u8()
                String(d, p, bytes, Charsets.UTF_8)
            } else {
                var units = u16(); if (units and 0x8000 != 0) units = ((units and 0x7FFF) shl 16) or u16()
                String(d, p, units * 2, Charsets.UTF_16LE)
            }
        }
    }

    /**
     * Query every exported provider that doesn't demand a read permission.
     *
     * This is the one channel that hands over live data with no reverse
     * engineering: if the CAN service publishes through a provider we can read,
     * we're done. Even a rejection is informative — "Unknown URI" means the
     * provider is alive and merely wants a path, which is a very different answer
     * from a permission denial.
     */
    fun exportedProviders(c: Context): String {
        val pm = c.packageManager
        val pkgs = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        val sb = StringBuilder()
        var tried = 0
        for (info in pkgs) {
            val pkg = info.packageName
            if (AOSP_PREFIXES.any { pkg == it || pkg.startsWith("$it.") }) continue
            val providers = runCatching {
                pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS).providers
            }.getOrNull() ?: continue
            for (pr in providers) {
                if (!pr.exported || pr.readPermission != null) continue
                val auth = pr.authority ?: continue
                // FileProviders never carry vehicle data and always reject a bare query.
                if (auth.contains("fileprovider", true) || auth.endsWith(".files")) continue
                tried++
                sb.append("content://").append(auth).append("   [").append(pkg).append("]\n")
                sb.append("    ").append(probeProvider(c, auth).replace("\n", "\n    ")).append("\n\n")
            }
        }
        return if (tried == 0) "No exported, permission-free providers outside AOSP."
        else sb.toString()
    }

    /**
     * Read a serial port for a few seconds and hex-dump whatever arrives.
     *
     * DESTRUCTIVE and opt-in only. A UART read consumes bytes — anything we take
     * is data the deck's own CAN service never sees, so its AC/climate display can
     * glitch or freeze while this runs. Bounded hard at [SERIAL_READ_MS] and closed
     * immediately after. Never call this as part of the normal scan.
     */
    fun readSerial(path: String): String {
        val f = File(path)
        if (!f.canRead()) return "$path: not readable"
        return runCatching {
            val bytes = ArrayList<Byte>()
            f.inputStream().use { ins ->
                val deadline = System.currentTimeMillis() + SERIAL_READ_MS
                val buf = ByteArray(256)
                while (System.currentTimeMillis() < deadline && bytes.size < 4096) {
                    val avail = ins.available()
                    if (avail <= 0) { Thread.sleep(50); continue }
                    val n = ins.read(buf, 0, minOf(buf.size, avail))
                    if (n <= 0) break
                    for (i in 0 until n) bytes.add(buf[i])
                }
            }
            if (bytes.isEmpty()) "$path: opened OK, but no data in ${SERIAL_READ_MS}ms"
            else "$path: ${bytes.size} bytes\n" +
                bytes.chunked(16).joinToString("\n") { row ->
                    row.joinToString(" ") { b -> "%02X".format(b) }
                }
        }.getOrElse { "$path: open/read failed — ${it.javaClass.simpleName} ${it.message}" }
    }

    private const val SERIAL_READ_MS = 2500L

    /**
     * Which serial devices we can even *see*. CAN data reaches Android over a UART
     * — `/dev/ttyV0` at 38400 on the MTC-family units — and if a budget ROM leaves
     * it world-readable we can take the raw stream and skip the app layer entirely.
     *
     * This only stats the node (`canRead`), it never opens or reads it. A serial
     * read is destructive: bytes we consume are bytes the deck's own CAN service
     * doesn't get, which would break its AC display. Actually reading is a
     * deliberate second step, not something to do behind the user's back.
     */
    fun serialPorts(): String {
        val names = ArrayList<String>()
        names += listOf("/dev/ttyV0", "/dev/ttyV1", "/dev/ttyV2", "/dev/ttyV3")
        for (i in 0..4) names += "/dev/ttyS$i"
        for (i in 0..4) names += "/dev/ttyHS$i"
        for (i in 0..3) names += "/dev/ttyMT$i"
        for (i in 0..2) names += "/dev/ttyUSB$i"
        names += listOf("/dev/ttyACM0", "/dev/ttysprd0", "/dev/ttysprd1", "/dev/stty_bt")

        val sb = StringBuilder()
        for (n in names) {
            val f = File(n)
            val exists = runCatching { f.exists() }.getOrDefault(false)
            val readable = runCatching { f.canRead() }.getOrDefault(false)
            if (exists || readable) {
                sb.append(n).append("  exists=").append(exists).append(" readable=").append(readable).append('\n')
            }
        }
        // /dev is usually not listable under SELinux, but it costs nothing to ask.
        val listing = runCatching { File("/dev").list()?.filter { it.startsWith("tty") }?.sorted() }.getOrNull()
        sb.append("\n/dev listing: ")
        sb.append(if (listing.isNullOrEmpty()) "not listable (expected — SELinux)" else listing.joinToString(" "))
        return sb.toString().ifBlank { "No candidate serial nodes visible." }
    }

    /**
     * Which v4l2 video devices we can see. This is the escape hatch for the 360
     * camera: its feed reaches Android either as a Camera2 device, or through the
     * vendor's own app, or — if the ROM is careless — as a readable `/dev/video*`
     * node we could take directly. The first is ideal, the third is the fallback,
     * and the second is a dead end for anything that has to float over a fullscreen
     * app, because a freeform window can't outrank one.
     *
     * Stat only, exactly like [serialPorts]. A v4l2 open isn't destructive the way
     * a UART read is, but the normal scan stays side-effect-free regardless.
     */
    fun videoNodes(): String {
        val sb = StringBuilder()
        for (i in 0..9) {
            val n = "/dev/video$i"
            val f = File(n)
            val exists = runCatching { f.exists() }.getOrDefault(false)
            val readable = runCatching { f.canRead() }.getOrDefault(false)
            if (exists || readable) {
                sb.append(n).append("  exists=").append(exists).append(" readable=").append(readable).append('\n')
            }
        }
        val listing = runCatching { File("/dev").list()?.filter { it.startsWith("video") }?.sorted() }.getOrNull()
        sb.append("\n/dev listing: ")
        sb.append(if (listing.isNullOrEmpty()) "not listable (expected — SELinux)" else listing.joinToString(" "))
        return sb.toString().ifBlank { "No video nodes visible." }
    }

    /**
     * Every camera the deck exposes to Camera2, which is the one question that
     * decides whether a feed can be drawn by DWM itself (and so float over
     * everything) or only ever shown by whoever owns the hardware.
     *
     * Listing ids does NOT need the CAMERA permission — only opening a device
     * does — so this runs unconditionally as part of a scan.
     */
    fun cameraInputs(c: Context): List<String> =
        CameraIds.list(c).map { (id, facing) -> "id $id · $facing" }

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

    // ---- vendor AIDL -------------------------------------------------------

    /**
     * Binds the deck's CAN service and reports what its binder gives up.
     *
     * Scan 3 (2026-07-29) settled the question that had blocked this:
     *
     *     com.tw.carinfoservice.permission.USE_AIDL
     *         normal — GETTABLE, just add <uses-permission>
     *     service com.tw.carinfoservice.CarService [exported]  [UNGUARDED]
     *
     * `normal` means the permission costs one manifest line and no prompt, and
     * UNGUARDED means the component never asks for it anyway. So the bind should
     * simply work — this finds out, and names the interface behind it.
     *
     * What it deliberately does NOT do is walk transaction codes. An AIDL stub
     * reads its arguments straight off the parcel, and a parcel we never filled
     * yields 0/null instead of throwing — so poking an unknown code is as likely
     * to call `setSomething(0)` on a live vehicle bus as it is to call a getter.
     * The facts taken here are the ones a binder owes us by contract and cannot
     * misinterpret: its interface descriptor (which names the AIDL, so a decompile
     * of the vendor APK can be matched against it), whether it is alive, and
     * whatever `dump()` volunteers. Argument-level calls wait for the real
     * interface definition — same discipline as [readSerial], which is opt-in for
     * the same reason.
     *
     * Bound at scan *start* and read at scan *finish*: `bindService` is
     * asynchronous while [buildReport] runs on the main thread, so given the whole
     * scan window there is nothing left to wait for.
     */
    class AidlProbe {

        /** [action] is passed through even alongside an explicit component — some
         *  vendor services switch on it inside `onBind`. */
        data class Target(val pkg: String, val cls: String, val action: String?)

        private class Result(val target: Target) {
            var accepted: Boolean? = null
            var refused: String? = null
            @Volatile var connected = false
            @Volatile var afterMs = 0L
            @Volatile var descriptor: String? = null
            @Volatile var binder: IBinder? = null
            @Volatile var nullBinding = false
            @Volatile var disconnected = false
        }

        private val results = ArrayList<Result>()
        private val conns = ArrayList<Pair<Result, ServiceConnection>>()
        private var startedAt = 0L

        /** How many binds are currently held — 0 means not running. */
        val bound: Int get() = conns.size

        fun start(c: Context) {
            if (conns.isNotEmpty()) return
            val app = c.applicationContext
            startedAt = System.currentTimeMillis()
            for (t in TARGETS) {
                val res = Result(t)
                results += res
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                        res.afterMs = System.currentTimeMillis() - startedAt
                        res.connected = true
                        res.binder = b
                        res.descriptor = runCatching { b?.interfaceDescriptor }
                            .getOrElse { "unavailable — ${it.javaClass.simpleName}" }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        res.disconnected = true
                    }

                    override fun onNullBinding(name: ComponentName?) {
                        res.nullBinding = true
                    }
                }
                val intent = Intent().apply {
                    component = ComponentName(t.pkg, t.cls)
                    t.action?.let { action = it }
                }
                runCatching { app.bindService(intent, conn, Context.BIND_AUTO_CREATE) }
                    .onSuccess { ok ->
                        res.accepted = ok
                        // A false return still leaves the connection registered.
                        conns += res to conn
                    }
                    .onFailure { res.refused = "${it.javaClass.simpleName} ${it.message}" }
            }
        }

        fun stop(c: Context) {
            val app = c.applicationContext
            for ((_, conn) in conns) runCatching { app.unbindService(conn) }
            conns.clear()
        }

        fun report(): String {
            if (results.isEmpty()) return "AIDL probe not running."
            val sb = StringBuilder()
            for (r in results) {
                sb.append(r.target.pkg).append('/').append(r.target.cls.removePrefix(r.target.pkg))
                r.target.action?.let { sb.append("\n    action: ").append(it) }
                sb.append("\n    bindService accepted: ").append(
                    when {
                        r.refused != null -> "THREW — ${r.refused}"
                        r.accepted == true -> "true"
                        else -> "false — nothing on the deck matched, or we were denied"
                    }
                )
                when {
                    r.nullBinding ->
                        sb.append("\n    connected: no — onBind() returned null (service is alive but hands out no interface)")
                    !r.connected ->
                        sb.append("\n    connected: NO — never called back in the whole scan window")
                    else -> {
                        sb.append("\n    connected: YES after ").append(r.afterMs).append("ms")
                        sb.append("\n    descriptor: ").append(r.descriptor ?: "(none — a raw Binder, not an AIDL stub)")
                        val b = r.binder
                        if (b != null) {
                            sb.append("\n    alive: ").append(runCatching { b.isBinderAlive }.getOrNull())
                            sb.append("  ping: ").append(runCatching { b.pingBinder() }.getOrNull())
                            sb.append("\n    dump(): ").append(dump(b).replace("\n", "\n        "))
                        }
                        if (r.disconnected) sb.append("\n    NOTE: dropped the connection during the scan")
                    }
                }
                sb.append("\n\n")
            }
            sb.append("A descriptor is the AIDL's fully-qualified name — that is what to look for\n")
            sb.append("when decompiling the vendor APK, and it confirms the bind is real.\n")
            return sb.toString()
        }

        /**
         * `dump()` is the one transaction a binder is contractually allowed to
         * answer with state and nothing else, so it is safe where a blind
         * `transact` is not. Most apps never override it and print nothing; the
         * ones that do tend to print exactly the decoded CAN state we're after.
         */
        private fun dump(b: IBinder): String {
            val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
                ?: return "couldn't open a pipe"
            val out = StringBuilder()
            // Drain the read end while the remote writes — a dump bigger than the
            // pipe buffer would otherwise deadlock against our own blocking call.
            val reader = Thread {
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { ins ->
                        val buf = ByteArray(4096)
                        while (out.length < DUMP_MAX) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            out.append(String(buf, 0, n))
                        }
                    }
                }
            }
            reader.start()
            val err = runCatching { b.dump(pipe[1].fileDescriptor, emptyArray()) }.exceptionOrNull()
            runCatching { pipe[1].close() }
            reader.join(DUMP_WAIT_MS)
            return when {
                err != null -> "refused — ${err.javaClass.simpleName} ${err.message}"
                out.isBlank() -> "nothing printed (the default Binder.dump is a no-op — expected)"
                else -> out.toString().trim()
            }
        }

        private companion object {
            const val DUMP_WAIT_MS = 1500L
            const val DUMP_MAX = 16384

            /**
             * Every exported, unguarded service on this deck that plausibly holds
             * vehicle state, taken from scan 3's component dump. `com.tw.carchoose`'s
             * MCUService is absent on purpose — it is [private] and cannot be bound.
             */
            val TARGETS = listOf(
                Target(
                    "com.tw.carinfoservice",
                    "com.tw.carinfoservice.CarService",
                    "com.tw.carinfoservice.CarService.Bind"
                ),
                // Named "permission", exported and unguarded — reads like the
                // handshake the vendor's own apps use to earn AIDL access.
                Target(
                    "com.tw.carinfoservice",
                    "com.tw.carinfoservice.permission.PermissionService",
                    null
                ),
                Target(
                    "com.dofun.carassistant.car",
                    "com.dofun.carassistant.car.service.CarService",
                    "com.dofun.car.CarService"
                ),
                // TPMS pressures come off a Deelife USB dongle, but the deck ships
                // its own TPMS app with ISSHOW_TPMS=1 — if this binder carries the
                // pressures, it beats reverse-engineering the dongle's bytes.
                Target("com.syt.tmps", "com.syt.tmps.TpmsService", "com.tpms.TpmsService")
            )
        }
    }

    // ---- broadcast sniffer ------------------------------------------------

    /**
     * Listens for the CAN broadcasts this deck emits.
     *
     * We can't enumerate broadcasts as they happen without root, so we have to
     * register for named actions up front. [CANDIDATE_ACTIONS] is the old guessed
     * list, kept only as a backstop; the real list comes from [manifestScan] —
     * every action any vendor app on this device declares a receiver for. If the
     * CAN service broadcasts at all, the action is in that set.
     */
    class Sniffer {
        private val hits = LinkedHashMap<String, String>()
        private var receiver: BroadcastReceiver? = null
        private var registered = 0

        /** Number of actions currently being listened for — 0 means not running. */
        val watching: Int get() = if (receiver == null) 0 else registered

        fun start(c: Context, discovered: Set<String> = emptySet()) {
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
            val actions = LinkedHashSet(CANDIDATE_ACTIONS) + discovered
            registered = actions.size
            val f = IntentFilter().apply { actions.forEach { addAction(it) } }
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

        fun report(): String {
            val header = "(listening on $registered action(s) — every one this deck's apps declare, plus the guessed list)\n\n"
            return header + if (hits.isEmpty())
                "Nothing fired.\n\nWith the real action list in play, this now means one of: the CAN " +
                    "service sends explicitly (component-targeted or LocalBroadcastManager, neither of " +
                    "which a third party can catch), it only broadcasts on change and nothing changed, " +
                    "or it doesn't broadcast at all and hands data over by binding instead."
            else hits.entries.joinToString("\n\n") { "${it.key}${it.value}" }
        }
    }

    // ---- live settings watcher ---------------------------------------------

    /**
     * Records settings changes as they happen instead of comparing two snapshots.
     *
     * The before/after diff has now reported "NOTHING CHANGED" three scans running,
     * and scan 3 proved that answer is partly an artefact of the method: a DO_MUTE
     * broadcast arrived carrying `reverse = 1`, so the car demonstrably went into
     * reverse during the scan — and came back out of it before the second snapshot.
     * `system/revserse_status` could have gone 0 → 1 → 0 in that window and the diff
     * would still see two identical endpoints. Every genuinely interesting vehicle
     * value is a transient like that; a two-point diff is the wrong instrument.
     *
     * A ContentObserver on the three settings tables is woken on each write and gets
     * the URI that changed, so we see the whole sequence with timings. A key that
     * toggles while the user pokes the car *is* the vehicle key — that pattern is
     * the signal, and it's what the diff was throwing away.
     */
    class Watcher {

        data class Event(val atMs: Long, val key: String, val value: String?)

        private val events = ArrayList<Event>()
        private val observers = ArrayList<Pair<Uri, ContentObserver>>()
        private var startedAt = 0L
        private var suppressed = 0

        val running: Boolean get() = observers.isNotEmpty()
        val count: Int get() = synchronized(events) { events.size }

        fun start(c: Context) {
            if (running) return
            startedAt = System.currentTimeMillis()
            val app = c.applicationContext
            val h = Handler(Looper.getMainLooper())
            val spaces = listOf(
                "system" to Settings.System.CONTENT_URI,
                "global" to Settings.Global.CONTENT_URI,
                "secure" to Settings.Secure.CONTENT_URI
            )
            for ((ns, uri) in spaces) {
                val o = object : ContentObserver(h) {
                    override fun onChange(selfChange: Boolean) = onChange(selfChange, null)
                    override fun onChange(selfChange: Boolean, changed: Uri?) {
                        // Pre-16 and some vendor ROMs notify without a URI; then all
                        // we can honestly say is "something in this table moved".
                        val key = changed?.lastPathSegment
                        if (key == null) { record(ns, "$ns/(unnamed)", null); return }
                        val value = runCatching {
                            app.contentResolver.query(
                                uri, arrayOf("name", "value"), "name=?", arrayOf(key), null
                            )?.use { cur -> if (cur.moveToFirst()) cur.getString(1) else null }
                        }.getOrNull()
                        record(ns, "$ns/$key", value)
                    }
                }
                runCatching {
                    app.contentResolver.registerContentObserver(uri, true, o)
                    observers += uri to o
                }
            }
        }

        private fun record(ns: String, key: String, value: String?) {
            if (isNoise(key)) { synchronized(events) { suppressed++ }; return }
            synchronized(events) {
                if (events.size >= MAX_EVENTS) return
                // A key rewritten with the value it already had tells us nothing.
                if (events.lastOrNull { it.key == key }?.value == value) return
                events += Event(System.currentTimeMillis(), key, value)
            }
        }

        fun stop(c: Context) {
            val cr = c.applicationContext.contentResolver
            for ((_, o) in observers) runCatching { cr.unregisterContentObserver(o) }
            observers.clear()
        }

        /**
         * Grouped by key rather than strictly chronological: what identifies a
         * vehicle key is that it *moved repeatedly* while the car was poked, and
         * that is far easier to see with each key's value sequence on one line.
         */
        fun report(): String {
            val snap = synchronized(events) { events.toList() to suppressed }
            val (all, hidden) = snap
            if (!running && all.isEmpty()) return "Watcher never started."
            if (all.isEmpty())
                return "Nothing in the settings store changed while the scan was open " +
                    "($hidden noise write(s) ignored).\n\nThe deck keeps its vehicle state " +
                    "somewhere else — most likely inside com.tw.carinfoservice, handed out " +
                    "over its AIDL bind rather than published."

            val sb = StringBuilder()
            sb.append(all.size).append(" change(s) on ")
                .append(all.map { it.key }.distinct().size).append(" key(s)")
            if (hidden > 0) sb.append(", plus ").append(hidden).append(" ignored as noise")
            sb.append(".\n\n")

            for ((key, evs) in all.groupBy { it.key }.entries.sortedByDescending { it.value.size }) {
                sb.append(key).append("  (").append(evs.size).append("x)\n")
                for (e in evs.take(MAX_PER_KEY)) {
                    sb.append("    +").append("%.1f".format((e.atMs - startedAt) / 1000.0))
                        .append("s  ").append(redact(key, e.value)).append('\n')
                }
                if (evs.size > MAX_PER_KEY) sb.append("    … ").append(evs.size - MAX_PER_KEY).append(" more\n")
            }
            return sb.toString()
        }

        private companion object {
            const val MAX_EVENTS = 2000
            const val MAX_PER_KEY = 12
        }
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
        sniffer: Sniffer?,
        watcher: Watcher? = null,
        aidl: AidlProbe? = null
    ): String {
        val sb = StringBuilder()
        sb.append(header(c)).append('\n')

        sb.append("=== 0. LIVE VEHICLE SIGNAL ===\n")
        sb.append("(what DWM is reading right now, from the deck's own broadcasts)\n\n")
        sb.append(Vehicle.summary()).append('\n')

        sb.append("\n\n=== 0b. THE CAN SERVICE'S AIDL, NOW SPOKEN PROPERLY ===\n")
        sb.append("(generated from the vendor's own .aidl, which ships inside its APK)\n\n")
        sb.append(CarInfo.report())

        sb.append("\n\n=== 0c. EVERY GETTER, RAW ===\n")
        sb.append("(the ground truth: what each of the 64 methods returns right now)\n\n")
        sb.append(CarInfo.dumpGetters())

        sb.append("\n\n=== 1a. SETTINGS CHANGES, AS THEY HAPPENED ===\n")
        sb.append("(every write while the scan was open — catches values that move and move back)\n\n")
        sb.append(watcher?.report() ?: "Watcher not running.")

        sb.append("\n\n=== 1b. SETTINGS THAT CHANGED WHILE YOU POKED THE CAR ===\n")
        sb.append("(before/after only — blind to anything that returned to its old value)\n\n")
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

        // Name matching missed the CAN service in the package list too, so don't
        // trust it here either — the key names alone are cheap and leak nothing.
        sb.append("\n--- every readable key NAME (no values) ---\n")
        sb.append(after.keys.joinToString(", "))
        sb.append('\n')

        val scan = manifestScan(c)

        sb.append("\n\n=== 3. VEHICLE / CAN APPS ON THE DECK ===\n\n")
        val apps = vehicleApps(c, scan)
        if (apps.isEmpty()) sb.append("Nothing matched on name, component or declared action.\n")
        else sb.append(apps.joinToString("\n\n"))
            .append("\n\nAn [exported] provider with no read= permission can be queried directly.\n")

        sb.append("\n\n=== 4. CAN BROADCASTS SEEN DURING THE SCAN ===\n\n")
        sb.append(sniffer?.report() ?: "Sniffer not running.")

        sb.append("\n\n=== 5. ACTIONS THE DECK'S OWN APPS LISTEN FOR ===\n")
        sb.append("(read out of each APK's manifest — these are real names, not guesses)\n\n")
        sb.append(manifestReport(scan))

        sb.append("\n\n=== 5b. VENDOR PERMISSIONS — CAN WE GET THEM? ===\n")
        sb.append("(decides whether com.tw.carinfoservice's AIDL is reachable at all)\n\n")
        sb.append(permissionLevels(c, scan))

        sb.append("\n\n=== 5c. THE CAN SERVICE'S AIDL — DID THE BIND WORK? ===\n")
        sb.append("(bound at the start of the scan; no transaction codes are guessed)\n\n")
        sb.append(aidl?.report() ?: "AIDL probe not running.")

        sb.append("\n\n=== 6. EXPORTED PROVIDERS, QUERIED ===\n")
        sb.append("(live data if any of these answer)\n\n")
        sb.append(exportedProviders(c))

        sb.append("\n\n=== 7. SERIAL PORTS (CAN arrives over a UART) ===\n")
        sb.append("(stat only — nothing is opened or read)\n\n")
        sb.append(serialPorts())

        sb.append("\n\n=== 7b. CAMERA2 INPUTS ===\n")
        sb.append("(decides whether a feed can be DWM-drawn, and so float over a fullscreen app)\n\n")
        sb.append(cameraInputs(c).joinToString("\n").ifBlank { "No Camera2 devices exposed." })

        sb.append("\n\n=== 7c. VIDEO NODES ===\n")
        sb.append("(stat only — nothing is opened or read)\n\n")
        sb.append(videoNodes())

        sb.append("\n\n=== 8. NON-AOSP PACKAGES, FULL COMPONENT DUMP ===\n\n")
        sb.append(nonAospDump(c))

        sb.append("\n\n=== 9. ALL INSTALLED PACKAGES ===\n\n")
        sb.append(allPackages(c))
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
    /** Which build, which deck. Every report that gets sent to me needs it, so it
     *  lives here rather than being retyped per report. */
    fun header(c: Context): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            append("DWM vehicle / CAN scan\n")
            append(stamp).append('\n')
            append("DWM v").append(Updater.currentVersionName(c)).append('\n')
            append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("  (").append(Build.DEVICE).append(")\n")
            append("android: ").append(Build.VERSION.RELEASE)
                .append(" / API ").append(Build.VERSION.SDK_INT).append('\n')
            append("fingerprint: ").append(Build.FINGERPRINT).append('\n')
        }
    }

    fun saveReport(c: Context, text: String): Pair<Uri, String>? {
        val name = "dwm-vehicle-scan-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        return saveToDownloads(c, name, "text/plain") { it.write(text.toByteArray()) }
    }

    /**
     * Copy a vendor APK out to Downloads so it can be decompiled off the deck.
     *
     * No adb and no cable: every installed APK is world-readable at
     * `applicationInfo.publicSourceDir` — which is already how [manifestScan] reads
     * 180-odd vendor manifests — so DWM can simply hand the file over the same way
     * it hands over the scan report.
     *
     * This matters for correctness, not just convenience. AIDL assigns transaction
     * codes *positionally*, in the order methods are declared, so a copy of this
     * APK from a different ROM build with one extra method inserted shifts every
     * code after it. A downloaded near-match would give us a plausible method list
     * over silently wrong codes — the exact failure [AidlProbe] refuses to risk.
     * The byte-exact copy off this deck is the only version that can be trusted.
     */
    fun saveApk(c: Context, pkg: String): Pair<Uri, String>? {
        val src = runCatching {
            c.packageManager.getApplicationInfo(pkg, 0).publicSourceDir
        }.getOrNull() ?: return null
        val f = File(src)
        if (!f.canRead()) return null
        return saveToDownloads(c, "$pkg.apk", "application/vnd.android.package-archive") { out ->
            f.inputStream().use { it.copyTo(out) }
        }
    }

    /**
     * Write a file where the user can actually get at it. On API 29+ that's the
     * shared Downloads collection via MediaStore (no permission needed under
     * scoped storage, and the returned content:// URI is directly shareable).
     */
    private fun saveToDownloads(
        c: Context,
        name: String,
        mime: String,
        write: (java.io.OutputStream) -> Unit
    ): Pair<Uri, String>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                c.contentResolver.openOutputStream(uri)?.use(write)
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
            f.outputStream().use(write)
            FileProvider.getUriForFile(c, "${c.packageName}.files", f) to f.absolutePath
        }.getOrNull()
    }

    /** Known/likely CAN broadcast actions across the common head-unit platforms. */
    private val CANDIDATE_ACTIONS = listOf(
        // Topway (com.tw.*) — this deck's actual platform. com.tw.carinfoservice
        // is almost certainly the CAN consumer; com.tw.carchoose is the CAN
        // protocol/car-model picker.
        "com.tw.carinfo",
        "com.tw.carinfo.data",
        "com.tw.carinfoservice.data",
        "com.tw.canbus",
        "com.tw.canbus.data",
        "com.tw.aircondition",
        "com.tw.air",
        "com.tw.core.canbus",
        "com.tw.service.canbus",
        "com.tw.action.CAR_INFO",
        "com.tw.action.CANBUS",
        "com.tw.CARINFO",
        "com.tw.reverse",
        "android.intent.action.TW_CARINFO",
        // Dofun — the UI/launcher layer on top (com.dofun.carsetting etc).
        "com.dofun.canbus",
        "com.dofun.canbus.data",
        "com.dofun.carsetting.canbus",
        "com.dofun.aircondition",
        "com.dofun.air.condition",
        "com.dofun.mcu.data",
        "com.dofun.action.CANBUS",
        "com.dofun.action.CAR_INFO",
        "com.dofun.CAR_DATA",
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
