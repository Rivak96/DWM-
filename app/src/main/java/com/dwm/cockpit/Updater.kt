package com.dwm.cockpit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updater. Reads a small `version.json` from the configured GitHub repo,
 * compares versionCode, and (on request) downloads the release APK and hands it
 * to the system PackageInstaller. Framework-only — no libraries, no FileProvider.
 *
 * Not fully silent: because DWM isn't a privileged installer, the system shows a
 * final "update?" confirmation. The user must also allow "install unknown apps"
 * for DWM once. Update APKs must be signed with the same release key.
 */
object Updater {

    data class Info(val versionCode: Long, val versionName: String, val notes: String, val apkUrl: String)

    sealed class Result {
        object UpToDate : Result()
        data class Available(val info: Info) : Result()
        data class Error(val message: String) : Result()
    }

    private val main = Handler(Looper.getMainLooper())

    fun currentVersionCode(c: Context): Long {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun currentVersionName(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "?"

    /** Fetch version.json and compare. Callback runs on the main thread. */
    fun check(c: Context, cb: (Result) -> Unit) {
        val repo = Prefs.updateRepo(c)
        if (repo.isBlank() || !repo.contains("/")) {
            cb(Result.Error("Set your update repo (owner/name) in Settings > About")); return
        }
        val url = "https://raw.githubusercontent.com/$repo/main/version.json"
        Thread {
            val result = runCatching {
                val json = httpGetText(url)
                val o = JSONObject(json)
                // apkUrl explicit, else built from repo + release tag + asset name
                val apkUrl = o.optString("apkUrl", "").ifBlank {
                    val tag = o.getString("tag")
                    val apk = o.optString("apk", "app-release.apk")
                    "https://github.com/$repo/releases/download/$tag/$apk"
                }
                val info = Info(
                    versionCode = o.getLong("versionCode"),
                    versionName = o.optString("versionName", "?"),
                    notes = o.optString("notes", ""),
                    apkUrl = apkUrl
                )
                if (info.versionCode > currentVersionCode(c)) Result.Available(info) else Result.UpToDate
            }.getOrElse { Result.Error(it.message ?: "check failed") }
            main.post { cb(result) }
        }.start()
    }

    /** True once the user has allowed DWM to install packages. */
    fun canInstall(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || c.packageManager.canRequestPackageInstalls()

    /** Download the APK and commit an install session. Callbacks on main thread. */
    fun downloadAndInstall(
        c: Context,
        info: Info,
        onProgress: (Int) -> Unit,
        onCommitted: () -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val apk = File(c.cacheDir, "dwm-update.apk")
                httpDownload(info.apkUrl, apk) { pct -> main.post { onProgress(pct) } }

                val installer = c.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(c.packageName)
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("dwm", 0, apk.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val intent = Intent(c, InstallResultReceiver::class.java)
                        .setAction(InstallResultReceiver.ACTION)
                    var flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        flags = flags or android.app.PendingIntent.FLAG_MUTABLE
                    val pending = android.app.PendingIntent.getBroadcast(c, sessionId, intent, flags)
                    session.commit(pending.intentSender)
                }
                main.post { onCommitted() }
            } catch (e: Exception) {
                main.post { onError(e.message ?: "install failed") }
            }
        }.start()
    }

    // ---- tiny HTTP helpers (follow redirects manually) ---------------------

    private fun open(urlStr: String): HttpURLConnection {
        var current = urlStr
        var redirects = 0
        while (true) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "DWM-Updater")
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw Exception("redirect without location")
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                redirects++
                continue
            }
            if (code !in 200..299) { conn.disconnect(); throw Exception("HTTP $code") }
            return conn
        }
    }

    private fun httpGetText(urlStr: String): String {
        val conn = open(urlStr)
        return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun httpDownload(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = open(urlStr)
        val total = conn.contentLength.toLong()
        conn.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(16 * 1024)
                var read: Int
                var done = 0L
                var lastPct = -1
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                    }
                }
            }
        }
    }
}
