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

    /** CAN discovery state (see [VehicleProbe]). */
    private var canBefore: Map<String, String>? = null
    private val sniffer = VehicleProbe.Sniffer()
    private val watcher = VehicleProbe.Watcher()
    private val aidl = VehicleProbe.AidlProbe()

    override fun onDestroy() {
        sniffer.stop(this)
        watcher.stop(this)
        aidl.stop(this)
        super.onDestroy()
    }

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
        findViewById<Button>(R.id.btnUiTiny).setOnClickListener { setUiScale(0.7f) }
        findViewById<Button>(R.id.btnUiCompact).setOnClickListener { setUiScale(0.8f) }
        findViewById<Button>(R.id.btnUiCosy).setOnClickListener { setUiScale(0.9f) }
        findViewById<Button>(R.id.btnUiStock).setOnClickListener { setUiScale(1.0f) }
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
        val swEdit = findViewById<Switch>(R.id.swOverlayEdit)
        swEdit.isChecked = Prefs.overlayEdit(this)
        swEdit.setOnCheckedChangeListener { _, v ->
            Prefs.setOverlayEdit(this, v)
            // Grips are built at inflate time, so the panels have to be rebuilt.
            if (OverlayPanelsService.isRunning) {
                OverlayPanelsService.stop(this)
                OverlayPanelsService.start(this)
            }
            Toast.makeText(
                this,
                if (v) "Grips shown — drag ✥ to move, ⤢ to resize" else "Panels locked — full content, no chrome",
                Toast.LENGTH_SHORT
            ).show()
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
        findViewById<Button>(R.id.btnCamFill).setOnClickListener { setCamFit(CameraPanel.FILL) }
        findViewById<Button>(R.id.btnCamFit).setOnClickListener { setCamFit(CameraPanel.FIT) }
        findViewById<Button>(R.id.btnCamStretch).setOnClickListener { setCamFit(CameraPanel.STRETCH) }
        findViewById<Button>(R.id.btnCamAuto).setOnClickListener { setCamDayNight(CameraPanel.AUTO) }
        findViewById<Button>(R.id.btnCamDay).setOnClickListener { setCamDayNight(CameraPanel.FORCE_DAY) }
        findViewById<Button>(R.id.btnCamNight).setOnClickListener { setCamDayNight(CameraPanel.FORCE_NIGHT) }
        findViewById<Button>(R.id.btnCamDarker).setOnClickListener { nudgeCamTrim(-1) }
        findViewById<Button>(R.id.btnCamBrighter).setOnClickListener { nudgeCamTrim(+1) }
        refreshCamTrimLabel()
        findViewById<Button>(R.id.btnCanScan).setOnClickListener { canScanTapped() }
        findViewById<Button>(R.id.btnCanSerial).setOnClickListener { serialReadPrompt() }
        findViewById<Button>(R.id.btnCanApk).setOnClickListener { exportApkPrompt() }
        // Parsing every APK manifest takes a second or two; do it off the main
        // thread, then listen on the action names it found rather than guesses.
        Thread {
            val discovered = runCatching { VehicleProbe.manifestScan(this).allActions }
                .getOrDefault(emptySet())
            runOnUiThread { if (!isFinishing) sniffer.start(this, discovered) }
        }.start()
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

    /** Interface scale rescales density, so every open screen has to be rebuilt —
     *  HomeActivity picks this up via recreateIfScaleChanged() on its next start. */
    private fun setUiScale(v: Float) {
        Prefs.setUiScale(this, v)
        if (OverlayPanelsService.isRunning) {
            OverlayPanelsService.stop(this)
            OverlayPanelsService.start(this)
        }
        recreate()
    }

    private fun setCamFit(mode: Int) {
        Prefs.setCamFit(this, mode)
        Toast.makeText(
            this,
            when (mode) {
                CameraPanel.FIT -> "Camera: fit whole frame"
                CameraPanel.STRETCH -> "Camera: stretch to panel"
                else -> "Camera: fill panel, keep proportions"
            },
            Toast.LENGTH_SHORT
        ).show()
        restartCameraPanels()
    }

    private fun setCamDayNight(mode: Int) {
        Prefs.setCamDayNight(this, mode)
        Toast.makeText(
            this,
            when (mode) {
                CameraPanel.FORCE_DAY -> "Camera: day picture"
                CameraPanel.FORCE_NIGHT -> "Camera: night picture"
                else -> "Camera: auto day/night"
            },
            Toast.LENGTH_SHORT
        ).show()
        restartCameraPanels()
    }

    private fun nudgeCamTrim(delta: Int) {
        Prefs.setCamTrim(this, Prefs.camTrim(this) + delta)
        refreshCamTrimLabel()
        restartCameraPanels()
    }

    private fun refreshCamTrimLabel() {
        val v = Prefs.camTrim(this)
        findViewById<TextView>(R.id.camTrimLabel).text = if (v > 0) "+$v" else "$v"
    }

    // ---- CAN / vehicle-data discovery ------------------------------------

    /** One button, two steps: arm the scan, let the user poke the car, then write
     *  the report out as a file they can upload. */
    private fun canScanTapped() {
        if (canBefore == null) canScanStart() else canScanFinish()
    }

    private fun canScanStart() {
        Ui.dialog(this)
            .setTitle("Scan vehicle — step 1 of 2")
            .setMessage(
                "This looks for the CAN data your deck already receives (reverse, AC, fan, " +
                    "lights, doors).\n\n" +
                    "When you tap Start:\n\n" +
                    "1.  Leave DWM open and go change things on the car — shift into reverse and " +
                    "back out, AC temperature up and down, fan speed, A/C on and off, headlights " +
                    "on and off, open and close a door. Hold each one for a couple of seconds. " +
                    "The more you change, the more I can identify.\n\n" +
                    "2.  Come back here and tap \"Finish scan\". It saves a file to Downloads that " +
                    "you can upload to me.\n\n" +
                    "New this time: DWM also binds the deck's own CAN service " +
                    "(com.tw.carinfoservice) and reports which AIDL interface it hands back. " +
                    "It only reads what a binder is obliged to tell us — it does not call any " +
                    "vehicle function, so nothing on the car can be changed by this.\n\n" +
                    "Nothing is sent anywhere by itself, and identifiers are stripped out of the file."
            )
            .setPositiveButton("Start") { _, _ ->
                canBefore = VehicleProbe.snapshot(this)
                watcher.start(this)
                // Bound now so the connections have the whole scan window to
                // arrive — bindService is async and the report is built inline.
                aidl.start(this)
                findViewById<Button>(R.id.btnCanScan).text = "Finish scan & save file"
                findViewById<TextView>(R.id.canStatus).text =
                    "Scanning — ${canBefore!!.size} keys recorded, ${sniffer.watching} broadcast actions watched, ${aidl.bound} vendor service(s) binding, live settings watcher on. Go change the AC, lights and doors, then come back and tap Finish."
                Toast.makeText(this, "Scanning — now go change the AC and lights", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun canScanFinish() {
        val before = canBefore
        val after = VehicleProbe.snapshot(this)
        val changes = if (before == null) emptyList() else VehicleProbe.diff(before, after)
        val live = watcher.count
        val report = VehicleProbe.buildReport(this, before, after, sniffer, watcher, aidl)
        val saved = VehicleProbe.saveReport(this, report)

        watcher.stop(this)
        // Unbound only after the report has read the binders.
        aidl.stop(this)
        canBefore = null
        findViewById<Button>(R.id.btnCanScan).text = "Scan vehicle"
        findViewById<TextView>(R.id.canStatus).text =
            if (saved == null) "Could not write the file."
            else "Saved ${saved.second} · $live live change(s), ${changes.size} net."

        if (saved == null) {
            Ui.dialog(this)
                .setTitle("Couldn't save")
                .setMessage("The report couldn't be written to storage. Tell me and I'll add a fallback.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        Ui.dialog(this)
            .setTitle("Scan saved")
            .setMessage(
                "Saved to ${saved.second}\n\n" +
                    "${changes.size} setting(s) changed while you were poking the car" +
                    (if (changes.isEmpty())
                        " — so the deck keeps CAN state inside its own app. The file also lists every " +
                            "broadcast action its apps declare, read straight out of their manifests, " +
                            "which is what I need next."
                    else ". That's very likely your live vehicle data.") +
                    "\n\nUpload the file to me and I'll build the panel around it."
            )
            .setPositiveButton("Share") { _, _ -> shareReport(saved.first) }
            .setNegativeButton("Done", null)
            .show()
    }

    /**
     * Copy one of the deck's vehicle APKs to Downloads so it can be decompiled.
     *
     * Read-only, and needs no adb: installed APKs sit world-readable at
     * `publicSourceDir`, which is already how the manifest scan works. The point is
     * to get the *exact* build off this deck — see [VehicleProbe.saveApk] for why a
     * copy downloaded from anywhere else can't be trusted.
     *
     * Two dialogs, and it has to be two: a framework AlertDialog shows *either* a
     * message *or* a list, never both. `AlertController.setupContent` only swaps the
     * ListView into the scroll parent on the `mMessage == null` branch, so setting
     * both silently drops the list — which is exactly what v0.18.0 shipped, an
     * explanation with nothing to pick. Don't merge these back together.
     */
    private fun exportApkPrompt() {
        Ui.dialog(this)
            .setTitle("Export a vehicle app's APK")
            .setMessage(
                "Copies the app's own APK to Downloads so it can be pulled apart off the " +
                    "deck — that's how we learn the real method names behind the CAN " +
                    "service's AIDL.\n\nNothing is installed, changed or sent anywhere; the " +
                    "file is only read. com.tw.carinfoservice is the one that matters."
            )
            .setPositiveButton("Choose app") { _, _ -> exportApkPicker() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportApkPicker() {
        val pkgs = listOf(
            "com.tw.carinfoservice",
            "com.dofun.carassistant.car",
            "com.syt.tmps",
            "com.tw.carchoose"
        )
        val labels = pkgs.map { pkg ->
            val mb = runCatching {
                val p = packageManager.getApplicationInfo(pkg, 0).publicSourceDir
                java.io.File(p).length() / (1024.0 * 1024.0)
            }.getOrNull()
            if (mb == null) "$pkg  (not installed)" else "%s  (%.1f MB)".format(pkg, mb)
        }
        Ui.dialog(this)
            .setTitle("Which app? (the size proves it was read)")
            .setItems(labels.toTypedArray()) { _, which -> exportApk(pkgs[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportApk(pkg: String) {
        Toast.makeText(this, "Copying $pkg…", Toast.LENGTH_SHORT).show()
        Thread {
            val saved = VehicleProbe.saveApk(this, pkg)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                findViewById<TextView>(R.id.canStatus).text =
                    saved?.let { "APK saved ${it.second}" } ?: "Couldn't read $pkg's APK."
                if (saved == null) {
                    Ui.dialog(this)
                        .setTitle("Couldn't export")
                        .setMessage("$pkg's APK wasn't readable. Tell me and I'll find another route.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    Ui.dialog(this)
                        .setTitle("Saved")
                        .setMessage("${saved.second}\n\nSend me this file and I'll decompile it.")
                        .setPositiveButton("Share") { _, _ ->
                            shareReport(saved.first, "application/octet-stream", "$pkg.apk")
                        }
                        .setNegativeButton("Done", null)
                        .show()
                }
            }
        }.start()
    }

    /**
     * Reading the CAN UART steals bytes from the deck's own service, so this is
     * gated behind an explicit warning and never runs as part of the normal scan.
     *
     * Split in two for the same reason as [exportApkPrompt] — a message and a list
     * can't share one AlertDialog, and here the warning is the whole point of the
     * first dialog, so it earns the extra tap.
     */
    private fun serialReadPrompt() {
        Ui.dialog(this)
            .setTitle("Read serial port — heads up")
            .setMessage(
                "CAN data reaches Android over one of these UARTs. Reading one CONSUMES the " +
                    "bytes, so for the ~2.5 seconds this runs, the deck's own CAN service " +
                    "doesn't get them — its AC or climate display may glitch or freeze.\n\n" +
                    "It recovers on its own. Do it parked, not mid-drive."
            )
            .setPositiveButton("Pick a port") { _, _ -> serialPortPicker() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun serialPortPicker() {
        val ports = listOf("/dev/ttyS0", "/dev/ttyS1", "/dev/ttyS2", "/dev/ttyS3", "/dev/ttyS4")
        Ui.dialog(this)
            .setTitle("Sample which port?")
            .setItems(ports.toTypedArray()) { _, which -> serialRead(ports[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun serialRead(path: String) {
        Toast.makeText(this, "Reading $path for 2.5s…", Toast.LENGTH_SHORT).show()
        Thread {
            val out = VehicleProbe.readSerial(path)
            runOnUiThread {
                val saved = VehicleProbe.saveReport(this, "DWM serial read\n$path\n\n$out\n")
                Ui.dialog(this)
                    .setTitle("Serial $path")
                    .setMessage(out.take(2000))
                    .setPositiveButton(if (saved != null) "Share" else "OK") { _, _ ->
                        saved?.let { shareReport(it.first) }
                    }
                    .setNegativeButton("Close", null)
                    .show()
                findViewById<TextView>(R.id.canStatus).text =
                    saved?.let { "Serial read saved ${it.second}" } ?: "Serial read done."
            }
        }.start()
    }

    /** [mime] matters: a chooser filtered to text/plain hides every target that
     *  could carry an APK. */
    private fun shareReport(
        uri: android.net.Uri,
        mime: String = "text/plain",
        subject: String = "DWM vehicle scan"
    ) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(send, "Send file")) }
            .onFailure { Toast.makeText(this, "Nothing on the deck can share files — grab it from Downloads over USB", Toast.LENGTH_LONG).show() }
    }

    /** Camera panels read their tuning when they attach, so bounce the overlay
     *  service to make a change visible without a full cockpit reload. */
    private fun restartCameraPanels() {
        if (OverlayPanelsService.isRunning) {
            OverlayPanelsService.stop(this)
            OverlayPanelsService.start(this)
        }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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
            appendLine("Vehicle: ${Vehicle.summary()}")
            appendLine(CarInfo.summary())
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
                while (favs.size > com.dwm.cockpit.ui.FAV_SLOTS) favs.removeAt(0)
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
