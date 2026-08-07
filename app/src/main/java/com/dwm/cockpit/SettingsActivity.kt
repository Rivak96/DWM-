package com.dwm.cockpit

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.dwm.cockpit.ui.DwmTheme
import com.dwm.cockpit.ui.HomeActions
import com.dwm.cockpit.ui.SettingsActions
import com.dwm.cockpit.ui.SettingsScreen
import com.dwm.cockpit.ui.SettingsUi

/**
 * Settings.
 *
 * The view layer is Compose now — `SettingsScreen` — and `activity_settings.xml`
 * with it. That was not a preference: keeping this screen in View/XML meant a second
 * implementation of the rail, the grid, the type scale and the card, restyled at
 * runtime by `Ui.skin()` walking the tree remapping colours it recognised. A section
 * header sitting at a colour that appeared in no palette went unremapped and stayed
 * mid-grey through every theme the app ever had, which is the failure mode that
 * approach produces. Sharing the actual composables with home is the only way two
 * screens genuinely match.
 *
 * **All the logic below is unchanged** — permissions, dialogs, the CAN scan, the
 * camera probe, the updater. Only the wiring moved.
 */
class SettingsActivity : DwmActivity() {

    /**
     * Bumped after every `Prefs` write so [readUi] recomposes. `Prefs` stays the one
     * source of truth; this is a change signal, not a copy of it.
     */
    private val revision = mutableIntStateOf(0)

    private fun bump() {
        revision.intValue++
    }

    /* Text the activity computes and the screen displays. */
    private val camTrimState = mutableStateOf("")
    private val canStatusState = mutableStateOf("")
    private val modeHintState = mutableStateOf("")
    private val updateStatusState = mutableStateOf("")
    private val aboutState = mutableStateOf("")
    private val carplayState = mutableStateOf("")
    private val obdState = mutableStateOf("")
    private val diagnosticsState = mutableStateOf("")

    /** The CAN scan is one button with two steps, so its label is state. */
    private val canScanLabelState = mutableStateOf("Scan vehicle")

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

        refreshCarplayLabel()
        refreshObdLabel()
        refreshUpdateStatus()
        refreshModeHint()
        refreshCamTrimLabel()
        showDiagnostics()
        showAbout()

        // The CAN sniffer has to be armed before the screen can offer to scan.
        // Parsing every APK manifest takes a second or two; do it off the main
        // thread, then listen on the action names it found rather than guesses.
        Thread {
            val discovered = runCatching { VehicleProbe.manifestScan(this).allActions }
                .getOrDefault(emptySet())
            runOnUiThread { if (!isFinishing) sniffer.start(this, discovered) }
        }.start()

        setContent {
            DwmTheme(this) {
                SettingsScreen(
                    ui = readUi(),
                    actions = settingsActions(),
                    home = homeActions(),
                    overlaysOn = OverlayPanelsService.isRunning
                )
            }
        }
    }

    /**
     * The whole screen's state, rebuilt from [Prefs] on every recomposition.
     *
     * Reading [revision] is what makes that happen: every action below calls [bump]
     * after writing, so `Prefs` stays the single source of truth and there is no
     * second copy of it in Compose state to fall out of step. Seventeen individual
     * `mutableStateOf` mirrors would have been the alternative, and mirrors of a
     * preference store are exactly the kind of duplication this rebuild removed
     * everywhere else.
     */
    @Composable
    private fun readUi(): SettingsUi {
        @Suppress("UNUSED_VARIABLE") val rev by revision
        return SettingsUi(
            themeMode = Prefs.theme(this).coerceIn(0, 2),
            uiScale = Prefs.uiScale(this),
            fontScale = Prefs.fontScale(this),
            wallpaper = Prefs.wallpaper(this),
            wallpaperName = when (Prefs.wallpaper(this)) {
                Prefs.WALL_NONE -> "Plain background"
                Prefs.WALL_CUSTOM -> "Your image"
                else -> "Ranger (bundled)"
            },
            wallpaperDim = Prefs.wallpaperDim(this),
            mode = Prefs.mode(this),
            modeHint = modeHintState.value,
            autoLoad = Prefs.autoLoad(this),
            carplay = carplayState.value,
            favGrid = Prefs.showFavGrid(this),
            captionComp = Prefs.captionComp(this),
            pillRunning = Prefs.overlaysOn(this),
            panelsRunning = OverlayPanelsService.isRunning,
            overlayEdit = Prefs.overlayEdit(this),
            muteOverlays = Prefs.muteOverlays(this),
            vehicleStrip = Prefs.vehicleStrip(this),
            demoData = Prefs.demoData(this),
            obd = obdState.value,
            camFit = Prefs.camFit(this),
            camDayNight = Prefs.camDayNight(this),
            camTrim = camTrimState.value,
            canStatus = canStatusState.value,
            canScanLabel = canScanLabelState.value,
            updateStatus = updateStatusState.value,
            autoUpdate = Prefs.autoUpdate(this),
            diagnostics = diagnosticsState.value,
            about = aboutState.value
        )
    }

    private fun settingsActions() = SettingsActions(
        setThemeMode = { applyThemePreset(it) },
        setUiScale = { setUiScale(it) },
        setFontScale = { setScale(it) },
        pickWallpaper = {
            // OPEN_DOCUMENT rather than GET_CONTENT: the result URI can be granted
            // persistably, so the wallpaper survives a reboot. GET_CONTENT hands back
            // a one-shot URI that is dead the next time the launcher starts.
            runCatching {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    REQ_WALL
                )
            }.onFailure {
                Toast.makeText(this, "No image picker on this deck", Toast.LENGTH_LONG).show()
            }
        },
        setWallpaperMode = { Prefs.setWallpaper(this, it); bump() },
        setWallpaperDim = { Prefs.setWallpaperDim(this, it); bump() },
        setMode = { setMode(it) },
        setAutoLoad = { Prefs.setAutoLoad(this, it); bump() },
        pickCarplay = { pickCarplay() },
        setFavGrid = { Prefs.setShowFavGrid(this, it); bump() },
        manageFavourites = { manageFavourites() },
        // The cockpit layout editor. It used to be the "Cockpit" item in the home
        // screen's bottom bar; that bar is now a five-item rail and configuration
        // belongs here.
        editLayout = { startActivity(Intent(this, LayoutEditorActivity::class.java)) },
        setComp = { setComp(it) },
        grantOverlay = {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        },
        startPill = { OverlayService.start(this); bump() },
        stopPill = { OverlayService.stop(this); bump() },
        panelsOn = { OverlayPanelsService.start(this); bump() },
        panelsOff = { OverlayPanelsService.stop(this); bump() },
        setOverlayEdit = { Prefs.setOverlayEdit(this, it); restartOverlayPanels(); bump() },
        raiseWindows = { LaunchEngine.launchLayout(this, Prefs.panels(this)) },
        setMuteOverlays = { Prefs.setMuteOverlays(this, it); restartOverlayPanels(); bump() },
        setVehicleStrip = {
            Prefs.setVehicleStrip(this, it)
            if (it) VehicleStripService.start(this) else VehicleStripService.stop(this)
            bump()
        },
        setDemoData = { Prefs.setDemoData(this, it); bump() },
        pickObd = { pickObd() },
        scanCameras = { scanCameras() },
        setCamFit = { setCamFit(it) },
        setCamDayNight = { setCamDayNight(it) },
        nudgeCamTrim = { nudgeCamTrim(it) },
        canScan = { canScanTapped() },
        canSerial = { serialReadPrompt() },
        canApk = { exportApkPrompt() },
        canDump = { dumpGetters() },
        notifAccess = {
            val was = NotifStore.accessGranted(this)
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .onFailure {
                    Toast.makeText(this, "Open Settings > Notification access", Toast.LENGTH_LONG).show()
                }
            if (!was) Toast.makeText(this, "Turn on DWM in the list", Toast.LENGTH_LONG).show()
        },
        openBluetooth = { open(Settings.ACTION_BLUETOOTH_SETTINGS) },
        openWifi = { open(Settings.ACTION_WIFI_SETTINGS) },
        openDisplay = { open(Settings.ACTION_DISPLAY_SETTINGS) },
        openAllSettings = { open(Settings.ACTION_SETTINGS) },
        defaultLauncher = {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                .onFailure {
                    Toast.makeText(
                        this, "Open Settings > Apps > Default apps > Home", Toast.LENGTH_LONG
                    ).show()
                }
        },
        checkUpdate = { checkForUpdate() },
        editRepo = { editUpdateRepo() },
        setAutoUpdate = { Prefs.setAutoUpdate(this, it); bump() },
        close = { finish() }
    )

    /** The rail is shared with home, so its destinations are too. */
    private fun homeActions() = HomeActions(
        overlayMenu = {
            if (OverlayPanelsService.isRunning) OverlayPanelsService.stop(this)
            else OverlayPanelsService.start(this)
            bump()
        },
        bluetooth = { open(Settings.ACTION_BLUETOOTH_SETTINGS) },
        wifi = { open(Settings.ACTION_WIFI_SETTINGS) },
        apps = { startActivity(Intent(this, AppDrawerActivity::class.java)) },
        settings = {}
    )

    private fun restartOverlayPanels() {
        if (OverlayPanelsService.isRunning) {
            OverlayPanelsService.stop(this)
            OverlayPanelsService.start(this)
        }
    }

    private fun pickCarplay() {
        startActivityForResult(
            Intent(this, AppDrawerActivity::class.java)
                .putExtra(AppDrawerActivity.EXTRA_PICK, true),
            REQ_CARPLAY
        )
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
        camTrimState.value = if (v > 0) "+$v" else "$v"
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
                canScanLabelState.value = "Finish scan & save file"
                canStatusState.value =
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
        canScanLabelState.value = "Scan vehicle"
        canStatusState.value =
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
                    "file is only read. com.tw.carinfoservice is the CAN service; a 360 " +
                    "or panoramic app, if the deck has one, is the other one worth pulling."
            )
            .setPositiveButton("Choose app") { _, _ -> exportApkPicker() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportApkPicker() {
        // The four that earned their place by hand, then anything else on the deck
        // whose name matches a vendor hint — so the 360 app becomes exportable the
        // moment it is identified, without another release.
        val known = listOf(
            "com.tw.carinfoservice",
            "com.dofun.carassistant.car",
            "com.syt.tmps",
            "com.tw.carchoose"
        )
        val pkgs = (known + VehicleProbe.hintedPackages(this)).distinct()
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

    /**
     * One tap, one file: what every CAN getter returns right now. Deliberately
     * not behind the full vehicle scan — this is the question that has come up
     * three releases running, and it should cost one button, not a whole ritual.
     */
    private fun dumpGetters() {
        Toast.makeText(this, "Reading every getter…", Toast.LENGTH_SHORT).show()
        Thread {
            // Binder calls into another process; never on the main thread.
            val text = VehicleProbe.header(this) + "\n" + CarInfo.dumpGetters()
            val saved = VehicleProbe.saveReport(this, text)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                canStatusState.value =
                    saved?.let { "Getter dump saved ${it.second}" } ?: "Couldn't save the dump."
                val named = text.lines().count { it.contains("[name]") && !it.contains("-1.0") && !it.trimEnd().endsWith("-1") }
                Ui.dialog(this)
                    .setTitle("CAN getter dump")
                    .setMessage(
                        (saved?.second ?: "Not saved") + "\n\n" +
                            "$named name-resolved signals returned a value. Send me this file " +
                            "and the dashboard gets cut down to exactly what your car provides."
                    )
                    .setPositiveButton("Share") { _, _ ->
                        saved?.let { shareReport(it.first, "text/plain", "DWM CAN getters") }
                    }
                    .setNegativeButton("Done", null)
                    .show()
            }
        }.start()
    }

    private fun exportApk(pkg: String) {
        Toast.makeText(this, "Copying $pkg…", Toast.LENGTH_SHORT).show()
        Thread {
            val saved = VehicleProbe.saveApk(this, pkg)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                canStatusState.value =
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
                canStatusState.value =
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
        modeHintState.value = if (mode == 0)
            "CURRENT: Dashboard — gauges/camera/web panels are drawn on the home screen; apps open in windows over it."
        else
            "CURRENT: Solo + overlays — your FULLSCREEN base app (e.g. CarPlay) opens on start, and every gauge/camera/web panel floats ON TOP of it. Mark the base app 'Open FULLSCREEN' in the layout editor."
    }


    private fun refreshUpdateStatus() {
        val repo = Prefs.updateRepo(this)
        updateStatusState.value =
            "Installed: v${Updater.currentVersionName(this)}\n" +
                "Repo: " + repo.ifBlank { "not set — tap 'Set update repo'" }
    }

    private fun editUpdateRepo() {
        val input = android.widget.EditText(this).apply {
            // The real repo name ends in a hyphen; a hint of "owner/DWM" sent the
            // obvious guess straight to a 404.
            hint = Prefs.DEFAULT_UPDATE_REPO
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
        aboutState.value =
            "DWM Cockpit v$version — your driving window manager.\n" +
                "Panels: apps in freeform windows · AUX camera · web dashboards · " +
                "custom HTML · OBD-II gauges · GPS speed · clock · images."
    }

    private fun refreshCarplayLabel() {
        val cp = Prefs.carplay(this)
        carplayState.value =
            "CarPlay app: " + if (cp != null) Apps.label(this, cp) else "not set"
    }

    private fun refreshObdLabel() {
        val name = Prefs.obdName(this)
        val mac = Prefs.obdMac(this)
        obdState.value =
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
        val rows = VehicleProbe.cameraInputs(this)
        val sb = StringBuilder()
        if (rows.isEmpty()) {
            sb.append("No Camera2 devices exposed.\n\nThe analog input isn't a camera device here — use a 'Camera app (AUX)' panel instead, which launches your AUX app into a window.")
        } else {
            rows.forEach { sb.append(it).append('\n') }
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
        // Both version fields, because this deck disagrees with itself: it reports
        // release "12" while SDK_INT is 29. The API number is the one that decides
        // what actually runs — blur and RenderEffect need 31 and silently do
        // nothing here, which cost a release to work out.
        val dm = resources.displayMetrics
        val wDp = (dm.widthPixels / (dm.densityDpi / 160f)).toInt()
        val hDp = (dm.heightPixels / (dm.densityDpi / 160f)).toInt()
        diagnosticsState.value = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            // Paste this straight into a @Preview device spec to iterate on the
            // home screen without flashing the deck.
            appendLine("Panel: ${dm.widthPixels}x${dm.heightPixels}px @ ${dm.densityDpi}dpi")
            appendLine("Preview: spec:width=${wDp}dp,height=${hDp}dp,dpi=${dm.densityDpi}")
            appendLine("Freeform feature: ${if (freeform) "YES" else "no"}")
            appendLine("enable_freeform_support: $freeformGlobal")
            appendLine("Overlay permission: ${if (canOverlay()) "granted" else "not granted"}")
            // The performance budget, in the one place it can be read without a
            // laptop and a cable. This SoC is a Unisoc SC9863A with a Mali-G51
            // driving 2.3 million pixels; what is left of the RAM decides how much
            // this launcher may keep alive at once, and it was never reported.
            val mi = android.app.ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager)
                .getMemoryInfo(mi)
            appendLine(
                "Memory: ${mi.availMem / 1048576} MB free of ${mi.totalMem / 1048576} MB" +
                    if (mi.lowMemory) " (LOW)" else ""
            )
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
                bump()
                wallToast()
            }
            REQ_FAV -> {
                val pkg = data.getStringExtra(AppDrawerActivity.EXTRA_PKG) ?: return
                val favs = Apps.effectiveFavorites(this).toMutableList()
                if (pkg !in favs) favs.add(pkg)
                while (favs.size > Apps.FAV_SLOTS) favs.removeAt(0)
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
