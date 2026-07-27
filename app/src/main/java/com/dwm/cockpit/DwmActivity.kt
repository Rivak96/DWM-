package com.dwm.cockpit

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity

/** Base activity: ComponentActivity (so screens can use Jetpack Compose
 *  setContent), plus the user's global text scale and immersive fullscreen
 *  (hidden status/navigation bars — swipe from the edge to peek them). */
abstract class DwmActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val scale = Prefs.fontScale(newBase)
        if (scale == 1.0f) {
            super.attachBaseContext(newBase)
        } else {
            val cfg = Configuration(newBase.resources.configuration)
            cfg.fontScale = scale
            super.attachBaseContext(newBase.createConfigurationContext(cfg))
        }
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
