package com.dwm.cockpit

import android.content.Context
import android.content.res.Configuration

/**
 * Global interface scale. The deck reports a phone-ish density for a 13" panel,
 * so stock dp values render physically huge and waste most of the glass. Wrapping
 * a Context here rescales `densityDpi`, which shrinks every dp *and* sp at once —
 * one lever for the whole UI instead of re-tuning hundreds of dimensions.
 *
 * Activities wrap in `attachBaseContext`; the overlay services wrap the context
 * they inflate their windows from, so floating panels match the launcher.
 */
object Scale {

    /** Context whose resources are scaled by the user's UI scale + text scale. */
    fun wrap(base: Context): Context {
        val ui = Prefs.uiScale(base)
        val font = Prefs.fontScale(base)
        if (ui == 1.0f && font == 1.0f) return base

        val cfg = Configuration(base.resources.configuration)
        if (ui != 1.0f) {
            cfg.densityDpi = (cfg.densityDpi * ui).toInt().coerceAtLeast(80)
            // screenWidthDp/HeightDp are in dp, so a density change grows them.
            cfg.screenWidthDp = (cfg.screenWidthDp / ui).toInt()
            cfg.screenHeightDp = (cfg.screenHeightDp / ui).toInt()
            cfg.smallestScreenWidthDp = (cfg.smallestScreenWidthDp / ui).toInt()
        }
        // fontScale is applied on top of the density change, so "Large text" still
        // enlarges type at any interface scale.
        cfg.fontScale = cfg.fontScale * font
        return base.createConfigurationContext(cfg)
    }

    /** Signature of everything that requires a screen rebuild when changed. */
    fun signature(c: Context) = "${Prefs.uiScale(c)}|${Prefs.fontScale(c)}|${Prefs.theme(c)}"
}
