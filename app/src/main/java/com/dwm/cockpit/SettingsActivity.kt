package com.dwm.cockpit

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Tesla-style two-pane settings: category sidebar on the left, content on the
 * right. Categories: Display · Cockpit · Overlay · Vehicle · System · About.
 */
class SettingsActivity : DwmActivity() {

    private lateinit var navButtons: List<Button>
    private lateinit var sections: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        navButtons = listOf(
            findViewById(R.id.navDisplay), findViewById(R.id.navCockpit),
            findViewById(R.id.navOverlay), findViewById(R.id.navVehicle),
            findViewById(R.id.navSystem), findViewById(R.id.navAbout)
        )
        sections = listOf(
            findViewById(R.id.secDisplay), findViewById(R.id.secCockpit),
            findViewById(R.id.secOverlay), findViewById(R.id.secVehicle),
            findViewById(R.id.secSystem), findViewById(R.id.secAbout)
        )
        navButtons.forEachIndexed { i, b -> b.setOnClickListener { showSection(i) } }

        findViewById<View>(R.id.close).setOnClickListener { finish() }

        // -- Display ------------------------------------------------------
        findViewById<Button>(R.id.btnThemeTesla).setOnClickListener { applyThemePreset(0) }
        findViewById<Button>(R.id.btnThemeMidnight).setOnClickListener { applyThemePreset(1) }
        findViewById<Button>(R.id.btnThemeLight).setOnClickListener { applyThemePreset(2) }
        findViewById<Button>(R.id.btnWallDefault).setOnClickListener { Prefs.setWallpaper(this, 0); wallToast() }
        findViewById<Button>(R.id.btnWallBlue).setOnClickListener { Prefs.setWallpaper(this, 1); wallToast() }
        findViewById<Button>(R.id.btnWallCarbon).setOnClickListener { Prefs.setWallpaper(this, 2); wallToast() }
        findViewById<Button>(R.id.btnWallCustom).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(i, REQ_WALL)
        }
        findViewById<Button>(R.id.btnTextCompact).setOnClickListener { setScale(0.85f) }
        findViewById<Button>(R.id.btnTextNormal).setOnClickListener { setScale(1.0f) }
        findViewById<Button>(R.id.btnTextLarge).setOnClickListener { setScale(1.15f) }
        buildAccentRow()

        // -- Cockpit ------------------------------------------------------
        findViewById<Button>(R.id.btnModeDash).setOnClickListener { setMode(0) }
        findViewById<Button>(R.id.btnModeOverlay).setOnClickListener { setMode(1) }
        refreshModeHint()
        val swFav = findViewById<Switch>(R.id.swFavGrid)
        swFav.isChecked = Prefs.showFavGrid(this)
        swFav.setOnCheckedChangeListener { _, v -> Prefs.setShowFavGrid(this, v) }
        findViewById<Button>(R.id.btnManageFavs).setOnClickListener { manageFavourites() }
        val swAuto = findViewById<Switch>(R.id.swAutoLoad)
        swAuto.isChecked = Prefs.autoLoad(this)
        swAuto.setOnCheckedChangeListener { _, v -> Prefs.setAutoLoad(this, v) }
        findViewById<Button>(R.id.btnCarplay).setOnClickListener {
            startActivityForResult(
                Intent(this, AppDrawerActivity::class.java)
                    .putExtra(AppDrawerActivity.EXTRA_PICK, true),
                REQ_CARPLAY
            )
        }
        findViewById<Button>(R.id.btnCompOff).setOnClickListener { setComp(0) }
        findViewById<Button>(R.id.btnCompSmall).setOnClickListener { setComp(24) }
        findViewById<Button>(R.id.btnCompMed).setOnClickListener { setComp(32) }
        findViewById<Button>(R.id.btnCompLarge).setOnClickListener { setComp(44) }

        // -- Overlay ------------------------------------------------------
        val swOverlay = findViewById<Switch>(R.id.swOverlay)
        swOverlay.isChecked = Prefs.overlayOnStart(this)
        swOverlay.setOnCheckedChangeListener { _, v -> Prefs.setOverlayOnStart(this, v) }
        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btnStartOverlay).setOnClickListener {
            if (canOverlay()) OverlayService.start(this)
            else Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnStopOverlay).setOnClickListener { OverlayService.stop(this) }
        findViewById<Button>(R.id.btnPanelsOn).setOnClickListener {
            if (canOverlay()) OverlayPanelsService.start(this)
            else Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnPanelsOff).setOnClickListener { OverlayPanelsService.stop(this) }
        findViewById<Button>(R.id.btnRaiseNow).setOnClickListener {
            LaunchEngine.raiseWindows(this, Prefs.panels(this))
        }
        val swMute = findViewById<Switch>(R.id.swMuteOverlays)
        swMute.isChecked = Prefs.muteOverlays(this)
        swMute.setOnCheckedChangeListener { _, v ->
            Prefs.setMuteOverlays(this, v)
            if (OverlayPanelsService.isRunning) {
                OverlayPanelsService.stop(this)
                OverlayPanelsService.start(this)
            }
        }

        // -- Vehicle ------------------------------------------------------
        findViewById<Button>(R.id.btnObdPick).setOnClickListener { pickObd() }
        findViewById<Button>(R.id.btnCamScan).setOnClickListener { scanCameras() }
        findViewById<Button>(R.id.btnNotifAccess).setOnClickListener {
            val granted = NotifStore.accessGranted(this)
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .onFailure { Toast.makeText(this, "Open Settings > Notification access", Toast.LENGTH_LONG).show() }
            if (!granted) Toast.makeText(this, "Turn on DWM in the list", Toast.LENGTH_LONG).show()
        }

        // -- System -------------------------------------------------------
        findViewById<Button>(R.id.btnBt).setOnClickListener { open(Settings.ACTION_BLUETOOTH_SETTINGS) }
        findViewById<Button>(R.id.btnWifi).setOnClickListener { open(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<Button>(R.id.btnDisplay).setOnClickListener { open(Settings.ACTION_DISPLAY_SETTINGS) }
        findViewById<Button>(R.id.btnAllSettings).setOnClickListener { open(Settings.ACTION_SETTINGS) }
        findViewById<Button>(R.id.btnDefaultLauncher).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                .onFailure {
                    Toast.makeText(this, "Open Settings > Apps > Default apps > Home", Toast.LENGTH_LONG).show()
                }
        }

        // -- Updates ------------------------------------------------------
        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener { checkForUpdate() }
        findViewById<Button>(R.id.btnUpdateRepo).setOnClickListener { editUpdateRepo() }
        val swAutoUpd = findViewById<Switch>(R.id.swAutoUpdate)
        swAutoUpd.isChecked = Prefs.autoUpdate(this)
        swAutoUpd.setOnCheckedChangeListener { _, v -> Prefs.setAutoUpdate(this, v) }

        refreshCarplayLabel()
        refreshObdLabel()
        refreshUpdateStatus()
        showDiagnostics()
        showAbout()

        Ui.themeWindow(this)
        Ui.skin(this, findViewById(android.R.id.content))
        showSection(0)
    }

    private fun showSection(idx: Int) {
        val t = Ui.th(this)
        sections.forEachIndexed { i, s -> s.visibility = if (i == idx) View.VISIBLE else View.GONE }
        navButtons.forEachIndexed { i, b ->
            b.background = Ui.navItemBg(this, i == idx)
            b.setTextColor(if (i == idx) t.text else t.dim)
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    private fun setScale(v: Float) {
        Prefs.setFontScale(this, v)
        recreate()
    }

    private fun applyThemePreset(idx: Int) {
        Prefs.setTheme(this, idx)
        recreate()
    }

    private fun setComp(dp: Int) {
        Prefs.setCaptionComp(this, dp)
        Toast.makeText(this, "Title-bar fix: ${if (dp == 0) "off" else "$dp dp"} — reload the cockpit to apply", Toast.LENGTH_SHORT).show()
    }

    private fun setMode(mode: Int) {
        Prefs.setMode(this, mode)
        if (mode == 0) OverlayPanelsService.stop(this)
        refreshModeHint()
        Toast.makeText(
            this,
            if (mode == 0) "Dashboard mode" else "Solo + overlays mode",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun manageFavourites() {
        val favs = Apps.effectiveFavorites(this).toMutableList()
        val items = (favs.map { "✕  " + Apps.label(this, it) } + "＋  Add app…").toTypedArray()
        Ui.dialog(this)
            .setTitle("Favourites — tap to remove")
            .setItems(items) { _, w ->
                if (w < favs.size) {
                    favs.removeAt(w)
                    Prefs.saveFavorites(this, favs)
                    manageFavourites()
                } else {
                    startActivityForResult(
                        Intent(this, AppDrawerActivity::class.java)
                            .putExtra(AppDrawerActivity.EXTRA_PICK, true),
                        REQ_FAV
                    )
                }
            }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun refreshModeHint() {
        val mode = Prefs.mode(this)
        findViewById<TextView>(R.id.modeHint).text = if (mode == 0)
            "CURRENT: Dashboard — gauges/camera/web panels are drawn on the home screen; apps open in windows over it."
        else
            "CURRENT: Solo + overlays — your FULLSCREEN base app (e.g. CarPlay) opens on start, and every gauge/camera/web panel floats ON TOP of it. Mark the base app 'Open FULLSCREEN' in the layout editor."
    }

    /** Tappable accent colour swatches; selected one gets a white ring. */
    private fun buildAccentRow() {
        val row = findViewById<LinearLayout>(R.id.accentRow)
        row.removeAllViews()
        val current = Prefs.accent(this)
        val size = Ui.dp(this, 38)
        val gap = Ui.dp(this, 12)
        for (a in Ui.ACCENTS) {
            val v = View(this)
            val d = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(a.color)
                if (a.color == current) setStroke(Ui.dp(this@SettingsActivity, 3), 0xFFFFFFFF.toInt())
            }
            v.background = d
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = gap }
            v.setOnClickListener {
                Prefs.setAccent(this, a.color)
                recreate()
            }
            row.addView(v)
        }
    }

    private fun refreshUpdateStatus() {
        val repo = Prefs.updateRepo(this)
        findViewById<TextView>(R.id.updateStatus).text =
            "Installed: v${Updater.currentVersionName(this)}\n" +
                "Repo: " + repo.ifBlank { "not set — tap 'Set update repo'" }
    }

    private fun editUpdateRepo() {
        val input = android.widget.EditText(this).apply {
            hint = "owner/DWM"
            setText(Prefs.updateRepo(this@SettingsActivity))
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        Ui.dialog(this)
            .setTitle("GitHub repo (owner/name)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                Prefs.setUpdateRepo(this, input.text.toString())
                refreshUpdateStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkForUpdate() {
        Toast.makeText(this, "Checking…", Toast.LENGTH_SHORT).show()
        Updater.check(this) { result ->
            when (result) {
                is Updater.Result.UpToDate ->
                    Toast.makeText(this, "You're on the latest (v${Updater.currentVersionName(this)})", Toast.LENGTH_LONG).show()
                is Updater.Result.Error ->
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                is Updater.Result.Available -> promptInstall(result.info)
            }
        }
    }

    private fun promptInstall(info: Updater.Info) {
        Ui.dialog(this)
            .setTitle("Update to v${info.versionName}?")
            .setMessage(if (info.notes.isBlank()) "A new version is available." else info.notes)
            .setPositiveButton("Update") { _, _ -> startInstall(info) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startInstall(info: Updater.Info) {
        if (!Updater.canInstall(this)) {
            Ui.dialog(this)
                .setTitle("Allow installs")
                .setMessage("Turn on \"Allow from this source\" for DWM, then tap Update again.")
                .setPositiveButton("Open setting") { _, _ ->
                    runCatching {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val dlg = Ui.dialog(this)
            .setTitle("Updating")
            .setMessage("Starting…")
            .setCancelable(false)
            .create()
        dlg.show()
        Updater.downloadAndInstall(
            this, info,
            onProgress = { pct -> dlg.setMessage("Downloading… $pct%") },
            onCommitted = { runCatching { dlg.dismiss() } },
            onError = { msg -> runCatching { dlg.dismiss() }; Toast.makeText(this, "Update failed: $msg", Toast.LENGTH_LONG).show() }
        )
    }

    private fun showAbout() {
        val version = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("?")
        findViewById<TextView>(R.id.aboutText).text =
            "DWM Cockpit v$version — your driving window manager.\n" +
                "Panels: apps in freeform windows · AUX camera · web dashboards · " +
                "custom HTML · OBD-II gauges · GPS speed · clock · images."
    }

    private fun refreshCarplayLabel() {
        val cp = Prefs.carplay(this)
        findViewById<TextView>(R.id.carplayLabel).text =
            "CarPlay app: " + if (cp != null) Apps.label(this, cp) else "not set"
    }

    private fun refreshObdLabel() {
        val name = Prefs.obdName(this)
        val mac = Prefs.obdMac(this)
        findViewById<TextView>(R.id.obdLabel).text =
            "OBD dongle: " + if (mac != null) "${name ?: "device"} ($mac)" else "not set"
    }

    @SuppressLint("MissingPermission")
    private fun pickObd() {
        if (Build.VERSION.SDK_INT >= 31 && !granted(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), REQ_BT)
            return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Turn on Bluetooth first", Toast.LENGTH_SHORT).show()
            open(Settings.ACTION_BLUETOOTH_SETTINGS)
            return
        }
        val bonded = adapter.bondedDevices.toList()
        if (bonded.isEmpty()) {
            Toast.makeText(this, "No paired devices — pair your ELM327 first", Toast.LENGTH_LONG).show()
            open(Settings.ACTION_BLUETOOTH_SETTINGS)
            return
        }
        val names = bonded.map { "${it.name}\n${it.address}" }.toTypedArray()
        Ui.dialog(this)
            .setTitle("Paired devices")
            .setItems(names) { _, w ->
                val d = bonded[w]
                Prefs.setObd(this, d.address, d.name)
                refreshObdLabel()
            }
            .show()
    }

    private fun scanCameras() {
        if (!granted(android.Manifest.permission.CAMERA)) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_CAM)
            return
        }
        val mgr = getSystemService(CAMERA_SERVICE) as CameraManager
        val ids = runCatching { mgr.cameraIdList }.getOrDefault(emptyArray())
        val sb = StringBuilder()
        if (ids.isEmpty()) {
            sb.append("No Camera2 devices exposed.\n\nThe analog input isn't a camera device here — use a 'Camera app (AUX)' panel instead, which launches your AUX app into a window.")
        } else {
            for (id in ids) {
                val facing = runCatching {
                    mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                }.getOrNull()
                val f = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                    else -> "?"
                }
                sb.append("id $id · $f\n")
            }
            sb.append("\nTry a 'Live camera (Camera2)' panel with one of these ids to see if it's your front feed.")
        }
        Ui.dialog(this)
            .setTitle("Camera2 inputs")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_BT -> pickObd()
            REQ_CAM -> scanCameras()
        }
    }

    private fun wallToast() = Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show()

    private fun open(action: String) {
        runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(this, "Not available on this deck", Toast.LENGTH_SHORT).show() }
    }

    private fun canOverlay() =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun showDiagnostics() {
        val pm = packageManager
        val freeform = pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        val freeformGlobal = runCatching {
            Settings.Global.getInt(contentResolver, "enable_freeform_support", -1)
        }.getOrDefault(-1)
        findViewById<TextView>(R.id.diagnostics).text = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}  (API ${Build.VERSION.SDK_INT})")
            appendLine("Freeform feature: ${if (freeform) "YES" else "no"}")
            appendLine("enable_freeform_support: $freeformGlobal")
            appendLine("Overlay permission: ${if (canOverlay()) "granted" else "not granted"}")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_CARPLAY -> {
                Prefs.setCarplay(this, data.getStringExtra(AppDrawerActivity.EXTRA_PKG))
                refreshCarplayLabel()
            }
            REQ_WALL -> {
                val uri = data.data ?: return
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                Prefs.setWallpaperUri(this, uri.toString())
                Prefs.setWallpaper(this, 3)
                wallToast()
            }
            REQ_FAV -> {
                val pkg = data.getStringExtra(AppDrawerActivity.EXTRA_PKG) ?: return
                val favs = Apps.effectiveFavorites(this).toMutableList()
                if (pkg !in favs) favs.add(pkg)
                Prefs.saveFavorites(this, favs)
                manageFavourites()
            }
        }
    }

    companion object {
        private const val REQ_CARPLAY = 201
        private const val REQ_BT = 202
        private const val REQ_CAM = 203
        private const val REQ_WALL = 204
        private const val REQ_FAV = 205
    }
}
