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
