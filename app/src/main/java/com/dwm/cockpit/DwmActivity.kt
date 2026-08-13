package com.dwm.cockpit

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity

/** Base activity: ComponentActivity (so screens can use Jetpack Compose
 *  setContent), plus the user's global interface + text scale and immersive
 *  fullscreen (hidden status/navigation bars — swipe from the edge to peek). */
abstract class DwmActivity : ComponentActivity() {

    /** Scale in force when this screen was built; used to detect a stale UI. */
    private var builtWith: String? = null

    private val immersiveHandler = Handler(Looper.getMainLooper())

    /**
     * Re-hide the system bars while a DWM screen is on screen, focused or not.
     *
     * Focus-gain used to be the only trigger, and that leaves a hole exactly where the bug
     * was reported: with a live app in the box, touching that app moves window focus to the
     * freeform task and **DWM never gets focus back**. `HomeActivity.onStop` already records
     * the same fact ("on API 29 multi-window only the focused activity is resumed, so
     * touching the stage app pauses this activity"). So a bar summoned while the owner is
     * working in the box stayed up until they tapped a DWM button — which is precisely the
     * clear condition reported from the van.
     *
     * Bracketed by `onStart`/`onStop` rather than `onResume`/`onPause` for the same reason:
     * the stage app pauses this activity, and pausing is when this is most needed.
     */
    private val reassertBars = object : Runnable {
        override fun run() {
            goImmersive()
            immersiveHandler.postDelayed(this, REASSERT_MS)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        builtWith = Scale.signature(newBase)
        super.attachBaseContext(Scale.wrap(newBase))
    }

    /** Rebuild if the user changed interface scale, text size or theme while a
     *  screen was in the back stack. Returns true when a recreate was kicked off. */
    protected fun recreateIfScaleChanged(): Boolean {
        val now = Scale.signature(this)
        if (builtWith != null && builtWith != now) { recreate(); return true }
        return false
    }

    /*
     * There is deliberately no stage handling here any more.
     *
     * A freeform window floats **above** fullscreen activities, so a DWM screen starting
     * does not cover it. v0.35.0 answered that with a fullscreen overlay "curtain" raised
     * from every DWM screen's `onStart`. `TYPE_APPLICATION_OVERLAY` is above *every*
     * activity window, DWM's own included, so it covered the app drawer instead of the
     * live app and — being touchable — ate the drawer's touches too. That is the blank
     * screen reported from the van.
     *
     * What replaced it is not a lifecycle callback at all: whoever *starts* a DWM screen
     * evicts the stage first, so the freeform task is out of the way before the screen
     * exists. See `HomeActivity.openDwmScreen` and [LaunchEngine.evictStage].
     */

    override fun onStart() {
        super.onStart()
        immersiveHandler.removeCallbacks(reassertBars)
        immersiveHandler.postDelayed(reassertBars, REASSERT_MS)
    }

    override fun onStop() {
        super.onStop()
        immersiveHandler.removeCallbacks(reassertBars)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    @Suppress("DEPRECATION")
    protected fun goImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { ic ->
                ic.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ic.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            record(window.decorView, 0, 0, changed = false)
            return
        }

        // The deck reports Android 12 and is API 29, so this is the branch that runs.
        val decor = window.decorView
        val want = immersiveFlags(Ui.night(this))
        val have = decor.systemUiVisibility
        val changed = have != want
        if (changed) {
            decor.systemUiVisibility = want
            Log.i(
                TAG,
                "re-hid the system bars: had 0x${Integer.toHexString(have)}," +
                    " set 0x${Integer.toHexString(want)}"
            )
        }
        record(decor, want, have, changed)
    }

    companion object {

        /** `adb logcat -s DwmImmersive` — and it lands in the dump's own logcat section. */
        private const val TAG = "DwmImmersive"

        /**
         * How often a visible DWM screen re-hides the system bars.
         *
         * Long enough that a deliberate swipe-peek at the system nav is still usable, short
         * enough that a bar left painted over the cockpit clears before it reads as broken.
         * Each tick is one integer compare in the common case: [goImmersive] only assigns
         * `systemUiVisibility` when the ROM has actually changed it.
         */
        private const val REASSERT_MS = 2500L

        /**
         * The immersive flag set, as a pure function of day/night.
         *
         * Split out and public so `DwmActivityTest` can prove the two variants differ.
         * `Ui.Theme.light` was hardcoded `false` in both branches for months and drove five
         * things; a variant flag that never varies hides indefinitely.
         *
         * The `LIGHT_*` bits matter now that the bar backgrounds are transparent (see
         * `styles.xml`): a bar the ROM insists on drawing puts its icons straight onto DWM's
         * content, and the day palette's background is `#E4E9EF`. Without these, day mode
         * gets white icons on near-white.
         */
        @Suppress("DEPRECATION")
        fun immersiveFlags(night: Boolean): Int {
            val base = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (night) return base
            return base or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        @Volatile private var applies = 0
        @Volatile private var changes = 0
        @Volatile private var lastWant = 0
        @Volatile private var lastHave = 0
        @Volatile private var lastAt = 0L
        @Volatile private var lastInsets = "(not sampled)"
        @Volatile private var lastFrame = "(not sampled)"

        /**
         * Remember what the last re-assert saw, for [snapshot].
         *
         * Never throws: a dump missing one line is worth more than a launcher that crashed
         * collecting it, and this runs on a timer behind every screen.
         */
        @Suppress("DEPRECATION")
        private fun record(decor: View, want: Int, have: Int, changed: Boolean) {
            applies++
            if (changed) changes++
            lastWant = want
            lastHave = have
            lastAt = System.currentTimeMillis()
            runCatching {
                val ins = decor.rootWindowInsets
                lastInsets = if (ins == null) "(null)" else
                    "l=${ins.systemWindowInsetLeft} t=${ins.systemWindowInsetTop}" +
                        " r=${ins.systemWindowInsetRight} b=${ins.systemWindowInsetBottom}"
                val r = Rect()
                decor.getWindowVisibleDisplayFrame(r)
                lastFrame = "${r.toShortString()} in a ${decor.width}x${decor.height} decor"
            }
        }

        /**
         * What the ROM is doing with DWM's system bars — the `---- window ----` section of a
         * dump.
         *
         * The black bar reported in v0.47.0 has two candidate causes that are identical on
         * the glass, and this separates them: **wanted != found** means the ROM cleared
         * DWM's immersive flags and is showing a bar for real, while **a non-zero top inset
         * with the flags intact** means the ROM believes a bar is up regardless of what DWM
         * asked for. Neither is visible from a photograph, and neither needs a cable.
         */
        fun snapshot(): String = buildString {
            if (applies == 0) {
                append("immersive    : never applied (no DWM screen has been on screen)\n")
                return@buildString
            }
            append("bars applied : ").append(applies).append(" times; ").append(changes)
                .append(" of those actually changed something\n")
            append("last apply   : ").append((System.currentTimeMillis() - lastAt) / 1000)
                .append("s ago\n")
            append("ui flags     : wanted 0x").append(Integer.toHexString(lastWant))
                .append(", found 0x").append(Integer.toHexString(lastHave))
                .append(if (lastWant == lastHave) "  (the ROM left them alone)"
                        else "  <-- the ROM had cleared them")
                .append('\n')
            append("insets       : ").append(lastInsets)
                .append("   (a non-zero top means the ROM thinks a bar is up)\n")
            append("visible frame: ").append(lastFrame).append('\n')
        }
    }
}
