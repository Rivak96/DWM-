package com.dwm.cockpit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/** Receives PackageInstaller session status. On PENDING_USER_ACTION it launches
 *  the system's install-confirmation screen (we're not a privileged installer). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "DWM updated — reopen if needed", Toast.LENGTH_LONG).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (msg != null && !msg.contains("Session was abandoned", true)) {
                    Toast.makeText(context, "Update failed: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val ACTION = "com.dwm.cockpit.INSTALL_RESULT"
    }
}
