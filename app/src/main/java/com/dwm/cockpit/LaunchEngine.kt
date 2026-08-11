package com.dwm.cockpit

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import java.lang.reflect.Method

/**
 * Launches apps: fullscreen, or into the home screen's stage rect.
 *
 * ### The freeform path, why it was deleted, and why it is back
 *
 * [launchInBox] starts an app into a given [Rect] using `ActivityOptions.setLaunchBounds`
 * plus a reflectively set `setLaunchWindowingMode(WINDOWING_MODE_FREEFORM)`. That is the
 * only way an unprivileged launcher can put another app inside a rectangle — `TaskView`,
 * a trusted `VirtualDisplay` and programmatic split-screen all need signature
 * permissions, and a `VirtualDisplay` receives no touch input without root.
 *
 * This existed once as `launchWindow`, was the v0.29 home screen's stage, and was deleted
 * in v0.30.0 after producing three faults on the deck:
 *
 * 1. **A caption bar** with drag and close handles, drawn *inside* the requested bounds.
 * 2. **Drag and resize** — the app would not stay where it was put.
 * 3. **Sinking behind the launcher**, on a z-order the system owns.
 *
 * All three were judged unreachable, and `CLAUDE.md` said not to propose freeform again.
 * That was right about the evidence then and wrong about the conclusion. The owner
 * supplied the stock launcher's own APK (`com.dofun.variety` v9.7.2.367) and a photo of it
 * hosting live CarPlay in a panel. Its entire relevant permission set is
 * `SYSTEM_ALERT_WINDOW` + `WRITE_SECURE_SETTINGS` — no `MANAGE_ACTIVITY_STACKS`, no
 * `INTERNAL_SYSTEM_WINDOW`, no `INJECT_EVENTS`. Since nothing on Android puts an arbitrary
 * app in a rectangle except freeform, the vendor launcher is doing exactly this, with two
 * levers v0.29 never pulled:
 *
 * - **`force_resizable_activities`**, which is what lets *any* app be hosted. Without it an
 *   app declaring `resizeableActivity=false` simply refuses freeform, and most do. See
 *   [freeformState].
 * - **Overlay masking.** A freeform window is dragged *by its caption bar*, so a
 *   touch-consuming overlay laid over the caption removes the ugly bar and the grab handle
 *   in one move. `StageService` draws it. v0.29 instead inflated the rect by a guessed
 *   32 dp, which overhung the vehicle bar when the guess ran long and cropped the app when
 *   it ran short — the guess was the bug, not the idea.
 *
 * [launchFullscreen] is unchanged and is still what every ordinary app launch uses.
 */
object LaunchEngine {

    /** `WindowConfiguration.WINDOWING_MODE_FREEFORM` — hidden, and stable since API 28. */
    private const val WINDOWING_MODE_FREEFORM = 5

    /**
     * Why the deck can or cannot host an app in the box.
     *
     * Read, never written. `enable_freeform_support` and `force_resizable_activities` are
     * `Settings.Global`, which needs `WRITE_SECURE_SETTINGS` to change — a permission DWM
     * cannot hold as a sideloaded app. The user flips them in Developer Options, or grants
     * the permission over adb; Settings shows this state so the reason is never a mystery.
     */
    data class Freeform(
        val feature: Boolean,
        val supportEnabled: Boolean,
        val forceResizable: Boolean
    ) {
        /** Freeform will start. Apps that decline multi-window may still open fullscreen
         *  unless [forceResizable] is on, which is why that is reported separately. */
        val usable: Boolean get() = feature || supportEnabled

        val summary: String
            get() = when {
                !usable -> "This deck does not offer freeform windows."
                !forceResizable ->
                    "Ready, but \"Force activities to be resizable\" is off in Developer " +
                        "options — apps that decline multi-window will open fullscreen instead."
                else -> "Ready."
            }
    }

    fun freeformState(c: Context): Freeform {
        fun global(key: String) =
            runCatching { Settings.Global.getInt(c.contentResolver, key, 0) }.getOrDefault(0) == 1
        return Freeform(
            feature = c.packageManager
                .hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
            supportEnabled = global("enable_freeform_support"),
            forceResizable = global("force_resizable_activities")
        )
    }

    fun displaySize(c: Context): Point {
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            Point(dm.widthPixels, dm.heightPixels)
        }
    }

    /** NOTE: never add FLAG_ACTIVITY_MULTIPLE_TASK here — it spawns duplicate copies of
     *  the app on every call, which the low-RAM deck then kills ("apps closing by
     *  themselves"). NEW_TASK alone reuses one task, which is also what makes returning
     *  to a running app instant rather than a cold start. */
    fun launchFullscreen(c: Context, pkg: String) {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(i) }
    }

    /**
     * Start [pkg] as a freeform window filling [bounds], in screen pixels.
     *
     * Returns false if the app has no launch intent or the start threw; a `true` only
     * means the request went out, because whether the ROM honours the bounds is not
     * something the caller can be told synchronously. `adb shell dumpsys window` is how
     * that gets checked.
     *
     * `NEW_TASK` alone, exactly as [launchFullscreen] — with an existing task this *moves*
     * it rather than making a second copy, which is what keeps the app's state across a
     * trip through fullscreen and back. **Never add `FLAG_ACTIVITY_MULTIPLE_TASK`**: it
     * spawns duplicates that the low-RAM deck then kills, which presented as "apps closing
     * by themselves".
     */
    fun launchInBox(c: Context, pkg: String, bounds: Rect): Boolean {
        if (bounds.isEmpty) return false
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        allowHiddenApis()
        val opts = ActivityOptions.makeBasic()
        // Hidden since it was added, and greylisted, hence [allowHiddenApis] above. If it
        // fails we still pass the bounds: on a ROM with freeform already the default for
        // bounded launches that is enough, and a fullscreen app is a better failure than
        // no app.
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(opts, WINDOWING_MODE_FREEFORM)
        }
        opts.launchBounds = bounds

        return runCatching { c.startActivity(i, opts.toBundle()) }.isSuccess
    }

    /**
     * Lift the hidden-API blacklist for this process, once.
     *
     * `setLaunchWindowingMode` is greylisted, and DWM targets SDK 33, so a direct
     * reflective call is refused outright — the call would fail silently and the window
     * would come up fullscreen with no clue why.
     *
     * The escape is double reflection: `Class.getDeclaredMethod` is itself reached
     * reflectively, so the lookup appears to come from the platform rather than from us,
     * and the blacklist does not apply to it. Works on API 28–30; the deck is 29.
     */
    private var hiddenApisAllowed = false

    private fun allowHiddenApis() {
        if (hiddenApisAllowed || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        hiddenApisAllowed = runCatching {
            val forName = Class::class.java
                .getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java
            )
            val vmRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod
                .invoke(vmRuntime, "getRuntime", null) as Method
            val setExemptions = getDeclaredMethod.invoke(
                vmRuntime, "setHiddenApiExemptions", arrayOf<Class<*>>(Array<String>::class.java)
            ) as Method
            // "L" is the prefix of every Java type descriptor, so this exempts the lot.
            setExemptions.invoke(getRuntime.invoke(null), arrayOf("L"))
            true
        }.getOrDefault(false)
    }

    /**
     * Launch the saved layout's base app.
     *
     * Once upon a time this staggered a fullscreen base app and then a set of freeform
     * windows on top of it, 400ms apart, hence the [Handler]. With the windows gone
     * there is at most one app to open, and the delay survives only because the callers
     * (`onStart` autoload, the reload action) fire during layout.
     */
    fun launchLayout(c: Context, panels: List<Panel>) {
        val base = panels.firstOrNull { it.isFullscreenApp() }?.pkg ?: return
        Handler(Looper.getMainLooper()).post { launchFullscreen(c, base) }
    }
}
