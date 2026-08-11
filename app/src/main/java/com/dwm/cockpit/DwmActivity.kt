package com.dwm.cockpit

import android.content.Context
import android.os.Build
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

    /**
     * Whether this screen must hide the home screen's live stage window.
     *
     * A freeform window floats **above** fullscreen activities, so a DWM screen starting
     * does not cover it — the app drawer would otherwise come up with a live app punched
     * through the middle of it. [StageChrome.curtain] is a fullscreen overlay that does
     * cover it, and every DWM screen raises it except the home screen that owns the stage.
     *
     * Driven from `onStart`/`onStop` and deliberately **not** from `onPause`: on API 29
     * multi-window only the focused activity is resumed, so merely touching the stage app
     * pauses `HomeActivity` — a pause-driven curtain would black the app out the moment you
     * tried to use it.
     */
    protected open val coversStage: Boolean get() = true

    override fun onStart() {
        super.onStart()
        if (coversStage) StageHost.setDwmScreenInFront(true)
    }

    override fun onStop() {
        super.onStop()
        if (coversStage) StageHost.setDwmScreenInFront(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    protected fun goImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { ic ->
                ic.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ic.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }
}
