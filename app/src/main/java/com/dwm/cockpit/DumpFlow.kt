package com.dwm.cockpit

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * The "send diagnostics" flow: collect, save, confirm, upload, report.
 *
 * A shared object rather than a method on `SettingsActivity`, because **the home screen
 * fires it too**. A diagnostics button reachable only from Settings is no use in the case
 * it exists for: the v0.36.0 white screen made Settings and the drawer untouchable, which
 * is precisely when someone wants a dump. Long-pressing the CAN dot on the home screen is
 * the other way in.
 *
 * Collection is [Diagnostics], transport is [GitHub]; this is only the sequence and the
 * dialogs.
 */
object DumpFlow {

    /**
     * Save first, then ask, then upload.
     *
     * The order is the point. The van runs off a phone hotspot as often as not, and a dump
     * that was collected and then lost to a failed upload is worse than one never taken —
     * so it is on disk via [VehicleProbe.saveDump] before the confirm dialog is even shown.
     *
     * The dialog names the destination and the size **before** anything leaves the deck. A
     * secret gist is unlisted and not indexed, but it is still readable by anyone holding
     * the URL, and that is the owner's call to make each time rather than a setting they
     * flipped once.
     */
    fun send(a: Activity) {
        val dlg = Ui.dialog(a)
            .setTitle("Collecting")
            .setMessage("Reading logs…")
            .setCancelable(false)
            .create()
        dlg.show()

        // Off the main thread, and not as tidiness: this forks `logcat` and writes a file
        // through MediaStore, on a deck with ~600 MB free and a Mali-G51. Five seconds of
        // that on the UI thread is an ANR, and an ANR in the diagnostics button is a joke
        // that writes itself.
        Thread {
            val text = Diagnostics.build(a)
            val saved = VehicleProbe.saveDump(a, text)
            val size = "${text.toByteArray().size / 1024} KB"
            val where = Prefs.githubLogin(a)
                ?.let { "a secret gist on GitHub ($it)" }
                ?: "dpaste.com, as an unlisted paste"

            a.runOnUiThread {
                runCatching { dlg.dismiss() }
                Ui.dialog(a)
                    .setTitle("Send diagnostics?")
                    .setMessage(
                        "$size of logs, stage state and settings. Passwords, tokens and " +
                            "device ids are stripped out.\n\n" +
                            (saved?.let { "Saved to ${it.second}\n\n" }
                                ?: "Could not write to Downloads.\n\n") +
                            "Sending to $where — anyone with the link can read it."
                    )
                    .setPositiveButton("Send") { _, _ -> upload(a, text) }
                    .setNegativeButton("Just save it", null)
                    .show()
            }
        }.start()
    }

    /**
     * Gist if signed in, paste otherwise — **and paste if the gist fails**, which is what a
     * revoked or expired token looks like from here. A failed upload never loses anything:
     * the file is already in Downloads and the dialog says so.
     */
    private fun upload(a: Activity, text: String) {
        val dlg = Ui.dialog(a)
            .setTitle("Sending")
            .setMessage("Uploading…")
            .setCancelable(false)
            .create()
        dlg.show()

        Thread {
            val name = VehicleProbe.dumpName()
            val hadToken = Prefs.githubToken(a) != null
            val result = runCatching {
                if (hadToken) GitHub.postGist(a, name, "DWM $name", text)
                else GitHub.postPaste(text)
            }.recoverCatching { first ->
                if (!hadToken) throw first
                GitHub.postPaste(text)
            }

            a.runOnUiThread {
                runCatching { dlg.dismiss() }
                result
                    .onSuccess { url ->
                        copyToClipboard(a, url)
                        Ui.dialog(a)
                            .setTitle("Sent")
                            .setMessage("$url\n\nCopied to the clipboard.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    .onFailure {
                        Ui.dialog(a)
                            .setTitle("Upload failed")
                            .setMessage("${it.message}\n\nThe file is still in Downloads.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
            }
        }.start()
    }

    private fun copyToClipboard(c: Context, text: String) {
        runCatching {
            val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("DWM diagnostics", text))
        }
    }
}
