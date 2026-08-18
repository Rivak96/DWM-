package com.dwm.cockpit

import android.content.Context
import android.os.Process
import android.provider.Settings
import java.io.BufferedReader

/**
 * Everything DWM can say about itself, in one text file.
 *
 * ### Why this exists
 *
 * Every bug in this project has been diagnosed from a photograph of the panel and a chain
 * of reasoning about the code, because that was the only evidence there was. That works
 * until it doesn't: v0.37.0 and v0.39.0 each shipped a wrong answer to a real bug, and both
 * would have been settled in seconds by four lines of the log this collects. `adb` answers
 * it too, but only with the deck on a cable and the owner shuttling files, which is the
 * thing this replaces.
 *
 * ### What it can see, and what it cannot
 *
 * **An unprivileged app can read its own logcat with no permission at all.** That has been
 * true since Android 4.1: `logcat` run by an ordinary app returns only entries logged by
 * its own UID, and *that restriction is itself the reason no permission is required*. ACRA
 * has relied on it for a decade. So [logcat] below needs nothing declared, nothing granted
 * and no adb — which is what keeps the shipped app zero-setup.
 *
 * What it cannot see is anything belonging to the system: `dumpsys window` and the full
 * system log need `DUMP` and `READ_LOGS`. Both carry Android's `development` protection
 * flag, so `adb shell pm grant` *can* hand them to an ordinary app and this deck is API 29,
 * before Google tightened that — but taking it would make the app need a cable once, and
 * the owner chose not to. The consequence is honest and worth knowing: this file answers
 * "what did DWM do", never "what did the ROM do with the window". Questions of the second
 * kind still want the cable.
 */
object Diagnostics {

    /**
     * Ceiling for a log section.
     *
     * A gist is generous but not infinite, the deck is on a phone hotspot as often as not,
     * and a busy logcat is megabytes. The **tail** is what gets kept — see [cap] — because
     * the interesting event is always the last thing that happened.
     */
    const val MAX_LOG_BYTES = 96 * 1024

    fun build(c: Context): String = buildString {
        append("DWM DIAGNOSTICS\n")
        append(VehicleProbe.header(c))

        append("\n---- stage ----\n")
        append(stage(c))

        append("\n---- display ----\n")
        append(display(c))

        append("\n---- window ----\n")
        append(DwmActivity.snapshot())

        append("\n---- settings ----\n")
        append(prefs(c))

        append("\n---- camera ----\n")
        append(camera(c))

        append("\n---- logcat: this app, main ----\n")
        append(cap(logcat("-d", "-v", "time", "--pid=${Process.myPid()}"), MAX_LOG_BYTES))

        append("\n---- logcat: this app, crash ----\n")
        append(cap(logcat("-d", "-v", "time", "-b", "crash", "--pid=${Process.myPid()}"), MAX_LOG_BYTES))
    }

    /**
     * The 360 camera's input format: which store, if any, owns it.
     *
     * The deck reports `MODE_720P_25FPS` while the fitted cameras are fixed AHD 1080P,
     * and this firmware build ships no UI that selects the format. Whether that is
     * fixable at all comes down to which store holds the value, and every candidate
     * store is readable without a permission — so the answer is collectable by one tap
     * on the dump button, which matters because **this deck has no USB device port**
     * and the adb route CLAUDE.md used to describe does not exist.
     *
     * [VehicleProbe.cameraReport] carries the reasoning and states the verdict; the
     * property dump underneath it is the safety net for a key whose name we did not
     * think to match, and is capped because it is the only part that can grow without
     * bound.
     */
    private fun camera(c: Context): String = buildString {
        append(runCatching { VehicleProbe.cameraReport(c) }
            .getOrElse { "camera report failed: $it\n" })
        append("\n-- all readable properties --\n")
        append(cap(runCatching { VehicleProbe.propDump() }.getOrElse { "prop dump failed: $it" },
            MAX_LOG_BYTES))
    }

    /**
     * The live-app box: what is configured, what the platform allows, and what [StageHost]
     * currently believes. This is the section the last two bugs were about.
     */
    private fun stage(c: Context): String {
        val pkg = Prefs.stagePkg(c)
        val label = pkg?.let {
            runCatching {
                c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(it, 0))
                    .toString()
            }.getOrNull()
        }
        val ff = LaunchEngine.freeformState(c)
        return buildString {
            append("configured   : ").append(pkg ?: "(none)")
            append(if (pkg != null && label == null) "  <-- NOT INSTALLED" else "  ($label)").append('\n')
            append("freeform     : feature=").append(ff.feature)
            append(" enable_freeform_support=").append(ff.supportEnabled)
            append(" force_resizable_activities=").append(ff.forceResizable).append('\n')
            append("             : ").append(ff.summary).append('\n')
            append("overlay perm : ").append(Settings.canDrawOverlays(c)).append('\n')
            append("caption mask : ").append(Prefs.captionDp(c)).append("dp\n")
            append(StageHost.snapshot())
        }
    }

    /**
     * Geometry, because a freeform launch is a rectangle and every question about it is a
     * question about which pixels. [Scale.signature] is here too: it is what decides whether
     * a screen gets recreated, and a mismatch between it and the launched rect would explain
     * a window landing in the wrong place.
     */
    private fun display(c: Context): String {
        val size = LaunchEngine.displaySize(c)
        val dm = c.resources.displayMetrics
        return buildString {
            append("panel        : ").append(size.x).append('x').append(size.y).append(" px\n")
            append("density      : ").append(dm.density).append("  (").append(dm.densityDpi)
                .append(" dpi)\n")
            append("canvas       : ").append((size.x / dm.density).toInt()).append('x')
                .append((size.y / dm.density).toInt()).append(" dp\n")
            append("scale sig    : ").append(Scale.signature(c)).append('\n')
            append("api          : ").append(android.os.Build.VERSION.SDK_INT).append('\n')
        }
    }

    /**
     * Every stored preference, through [VehicleProbe.redact].
     *
     * One redaction list for the whole app, deliberately: this dump can be posted to a gist,
     * and `SENSITIVE` already catches `token`, which is exactly the GitHub credential that
     * posts it. A second list here would be a second thing to keep in step, and this file
     * has been burned by duplicated constants before.
     */
    private fun prefs(c: Context): String {
        val all = runCatching { Prefs.all(c) }.getOrNull() ?: return "(unreadable)\n"
        if (all.isEmpty()) return "(nothing stored)\n"
        return all.entries
            .sortedBy { it.key }
            .joinToString("\n") { (k, v) -> "$k = ${VehicleProbe.redact(k, v?.toString())}" } + "\n"
    }

    /**
     * Run logcat and read it back. Never throws — a dump that fails because one section
     * could not be collected is worth less than a dump with a note in it.
     */
    private fun logcat(vararg args: String): String = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("logcat") + args)
        val out = p.inputStream.bufferedReader().use(BufferedReader::readText)
        p.waitFor()
        out.ifBlank { "(empty)\n" }
    }.getOrElse { "(logcat unavailable: ${it.message})\n" }

    /**
     * Keep the **last** [max] bytes, whole lines only.
     *
     * Tail rather than head: the thing being diagnosed is always the most recent thing that
     * happened, and a log truncated from the end is a log of the boot sequence. Says how
     * much it dropped, so a suspiciously short section is never mistaken for a quiet one.
     */
    fun cap(text: String, max: Int): String {
        val bytes = text.toByteArray()
        if (bytes.size <= max) return text
        val tail = String(bytes, bytes.size - max, max)
        val firstBreak = tail.indexOf('\n')
        val clean = if (firstBreak >= 0) tail.substring(firstBreak + 1) else tail
        return "… ${bytes.size - max} earlier bytes dropped …\n$clean"
    }
}
