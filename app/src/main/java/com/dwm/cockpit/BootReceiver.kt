package com.dwm.cockpit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Starts the floating pill after boot when the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val canOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
        if (Prefs.overlayOnStart(context) && canOverlay) {
            runCatching { OverlayService.start(context) }
        }
    }
}
