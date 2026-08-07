package com.dwm.cockpit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import com.dwm.cockpit.ui.DwmTheme
import com.dwm.cockpit.ui.HomeActions
import com.dwm.cockpit.ui.CockpitHome
import com.dwm.cockpit.ui.HomeApp
import com.dwm.cockpit.ui.theme.DwmIcons

/**
 * Launcher home. UI is Jetpack Compose (Material 3, glass cards). The
 * dashboard-mode drawn-panel canvas stays View-based and is hosted via
 * AndroidView. Services, feeds, launch engine and overlays are unchanged.
 */
class HomeActivity : DwmActivity() {

    private lateinit var panelHost: FrameLayout

    // Compose-observable state
    private val overlaysOnState = mutableStateOf(false)
    private val showCanvasState = mutableStateOf(false)
    private val allAppsState = mutableStateOf<List<HomeApp>>(emptyList())
    private val stageAppState = mutableStateOf<String?>(null)
    private val cameraPanelState = mutableStateOf(Panel(PanelType.CAMERA, 0f, 0f, 1f, 1f, label = "Camera"))
    private val boostState = mutableStateOf<Float?>(null)
    private val wallpaperState = mutableStateOf<Bitmap?>(null)
    private val wallpaperDimState = mutableStateOf(0.30f)

    private val handler = Handler(Looper.getMainLooper())
    private var lastPanelsJson: String? = "_never"

    private val clockPanels = ArrayList<Pair<TextView, TextView>>()
    private val speedGauges = ArrayList<GaugeView>()
    private val obdGauges = ArrayList<Pair<String, GaugeView>>()
    private var locListener: LocationListener? = null
    private var obd: ObdManager? = null

    private lateinit var actions: HomeActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Registered on the application context and never torn down: DWM is the
        // launcher, so its process outlives every activity, and reverse gear is
        // only useful if we were already listening when it engaged.
        Vehicle.start(this)
        // The vendor CAN service, same reasoning: bound for the life of the
        // process, because its data is pushed and a callback registered after the
        // fact has already missed everything.
        CarInfo.start(this)

        panelHost = FrameLayout(this)

        // Home launcher: swallow Back so it stays on the launcher.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* stay on the launcher */ }
        })

        actions = HomeActions(
            carplay = { launchCarplay() },
            overlayMenu = { overlayMenu() },
            bluetooth = { openSetting(Settings.ACTION_BLUETOOTH_SETTINGS) },
            wifi = { openSetting(Settings.ACTION_WIFI_SETTINGS) },
            apps = { startActivity(Intent(this, AppDrawerActivity::class.java)) },
            edit = { startActivity(Intent(this, LayoutEditorActivity::class.java)) },
            settings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            reload = { reloadCockpit() },
            pill = { startPill() },
            // Everything that opens an app puts it on the stage. Long-press an app
            // tile for the fullscreen option.
            launch = { pkg -> openOnStage(pkg) },
            appMenu = { pkg -> appMenu(pkg) },
            grantNotifications = {
                runCatching {
                    startActivity(
                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )

        setContent {
            DwmTheme(this) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (showCanvasState.value) {
                        AndroidView(factory = { panelHost }, modifier = Modifier.fillMaxSize())
                    } else {
                        CockpitHome(
                            stageApp = stageAppState.value,
                            cameraPanel = cameraPanelState.value,
                            allApps = allAppsState.value,
                            overlaysOn = overlaysOnState.value,
                            actions = actions,
                            boost = boostState.value,
                            wallpaper = wallpaperState.value,
                            wallpaperDim = wallpaperDimState.value,
                            onLaunchOnStage = ::openOnStage,
                            onStageBounds = ::onStageBounds,
                            drawnView = ::buildPanelView
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (recreateIfScaleChanged()) return

        overlaysOnState.value = OverlayPanelsService.isRunning
        loadStage()
        loadApps()
        loadWallpaper()
        // Nothing on the home screen hosts a WebView any more — the stage is a
        // freeform window and the side boxes are a diagram and a camera — so the one
        // shared WebView belongs to the overlay service alone while home is up.
        WebPanelHost.pauseAll()

        ensurePermissions()
        refreshPanelsIfChanged()

        if (!didAutoLoad && Prefs.autoLoad(this)) {
            didAutoLoad = true
            handler.postDelayed({ LaunchEngine.launchLayout(this, Prefs.panels(this)) }, 700)
        }
        if (Prefs.overlayOnStart(this) && canOverlay()) OverlayService.start(this)
        if (Prefs.vehicleStrip(this) && canOverlay() && !VehicleStripService.isRunning) {
            VehicleStripService.start(this)
        }
        ensureOverlaysForMode()

        if (!didUpdateCheck && Prefs.autoUpdate(this) && Prefs.updateRepo(this).isNotBlank()) {
            didUpdateCheck = true
            handler.postDelayed({ autoCheckUpdate() }, 3000)
        }
    }

    override fun onStop() {
        super.onStop()
        stopLocation()
        stopObd()
        // Nothing is visible, so nothing should be running. A web panel used to keep
        // its timers, its JavaScript and any video going while the launcher was in
        // the background, on a deck that cannot afford it.
        WebPanelHost.pauseAll()
    }

    // ---- actions ---------------------------------------------------------

    private fun reloadCockpit() {
        lastPanelsJson = "_force"
        refreshPanelsIfChanged()
        LaunchEngine.launchLayout(this, Prefs.panels(this))
    }

    private fun toggleOverlays() {
        if (!canOverlay()) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (OverlayPanelsService.isRunning) OverlayPanelsService.stop(this)
        else OverlayPanelsService.start(this)
        handler.postDelayed({ overlaysOnState.value = OverlayPanelsService.isRunning }, 600)
    }

    private fun startPill() {
        if (!canOverlay()) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        OverlayService.start(this)
        Toast.makeText(this, "Floating pill shown — drag it anywhere", Toast.LENGTH_SHORT).show()
    }

    private fun launchCarplay() {
        val panels = Prefs.panels(this)
        val base = panels.firstOrNull { it.isFullscreenApp() }?.pkg ?: Prefs.carplay(this)
        if (base != null) LaunchEngine.launchFullscreen(this, base)
        else {
            Toast.makeText(this, "Pick your CarPlay app in Settings > Cockpit", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * Overlays are working well and are deliberately untouched — this only opens
     * their controls instead of the dock button silently toggling them, which was
     * easy to hit by accident.
     */
    private fun overlayMenu() {
        val running = OverlayPanelsService.isRunning
        val items = arrayOf(
            if (running) "Stop overlay panels" else "Start overlay panels",
            "Edit overlay layout",
            if (Prefs.overlayEdit(this)) "Lock panels (hide grips)" else "Unlock panels (show grips)",
            "Show floating pill"
        )
        Ui.dialog(this)
            .setTitle("Overlays")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> if (running) OverlayPanelsService.stop(this) else toggleOverlays()
                    1 -> startActivity(Intent(this, LayoutEditorActivity::class.java))
                    2 -> {
                        Prefs.setOverlayEdit(this, !Prefs.overlayEdit(this))
                        if (OverlayPanelsService.isRunning) {
                            OverlayPanelsService.stop(this)
                            handler.postDelayed({ OverlayPanelsService.start(this) }, 400)
                        }
                    }
                    3 -> startPill()
                }
                handler.postDelayed({ overlaysOnState.value = OverlayPanelsService.isRunning }, 600)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /** Long-press on any app tile. Pinning writes to the same favourites list the
     *  pill and the app drawer use, so all three stay in step. */
    private fun appMenu(pkg: String) {
        val pinned = pkg in Apps.effectiveFavorites(this).take(Apps.FAV_SLOTS)
        Ui.dialog(this)
            .setTitle(Apps.label(this, pkg))
            .setItems(
                arrayOf(
                    "Open fullscreen",
                    "Open in window",
                    if (pinned) "Remove from home" else "Add to home"
                )
            ) { _, w ->
                when (w) {
                    0 -> LaunchEngine.launchFullscreen(this, pkg)
                    1 -> {
                        val s = LaunchEngine.displaySize(this)
                        LaunchEngine.launchWindow(
                            this, pkg,
                            android.graphics.Rect(s.x / 6, s.y / 6, s.x * 5 / 6, s.y * 5 / 6)
                        )
                    }
                    2 -> {
                        val cur = Apps.effectiveFavorites(this).toMutableList()
                        if (pinned) cur.remove(pkg) else cur.add(0, pkg)
                        while (cur.size > Apps.FAV_SLOTS) cur.removeAt(cur.size - 1)
                        Prefs.saveFavorites(this, cur)
                        loadApps()
                    }
                }
            }
            .show()
    }

    // ---- data for Compose ------------------------------------------------

    /**
     * Build the favourites list off the main thread.
     *
     * Every icon gets rasterised and colour-sampled; doing that on the main thread
     * made returning to home stutter on this deck. Results are posted back once, so
     * Compose sees a single state change rather than a drip.
     *
     * One list now, not two. The favourites band is gone from the home screen — it
     * was asked to be removed along with the now-playing strip — so the only consumer
     * left is the grid the stage shows before an app has ever been chosen.
     */
    private fun loadApps() {
        Thread {
            val all = Apps.all(this)
                .map { e -> HomeApp(e.pkg, e.label, DwmIcons.forApp(e.pkg, e.label)) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                allAppsState.value = all
            }
        }.start()
    }

    /* ------------------------------------------------------------------ stage */

    /**
     * Put an app on the stage.
     *
     * Persisted immediately, because the stage app *is* the cockpit's layout now —
     * there is nothing else to save and nothing to restore it from if the process
     * dies. [launchStageApp] does the window work once a rect is known.
     *
     * The outgoing app is deliberately **not** parked off-screen. The old pane system
     * moved a departing window to `displaySize + 40`, a manoeuvre that was never
     * verified on this ROM and would silently do nothing if the ROM clamps launch
     * bounds. A new window at the same rect covers the old one, and Android reclaims
     * background tasks under memory pressure, which on a 600 MB deck it will.
     */
    private fun openOnStage(pkg: String) {
        if (stageAppState.value == pkg) return
        stageAppState.value = pkg
        Prefs.setStageApp(this, pkg)
        launchStageApp()
    }

    /**
     * The stage's rect in window coordinates, as last measured by Compose.
     *
     * Null until the first layout pass. [launchStageApp] is called from both here and
     * `onStart`, whichever arrives second, because a window cannot be launched into a
     * rect that has not been measured and an app cannot be restored before it is read.
     */
    private var stageRect: Rect? = null

    private fun onStageBounds(r: Rect) {
        if (r == stageRect) return
        stageRect = r
        launchStageApp()
    }

    /**
     * Launch (or move) the stage app's window into the stage rect.
     *
     * `LaunchEngine.launchWindow` uses `NEW_TASK` without `MULTIPLE_TASK`, so calling
     * this on an already-running app moves and resizes the existing task rather than
     * spawning a second copy. That is what makes it safe to call on every bounds
     * change — but bounds changes are deduped anyway, because a relaunch is visible.
     */
    private fun launchStageApp() {
        val pkg = stageAppState.value ?: return
        val rect = stageRect ?: return
        LaunchEngine.launchWindow(this, pkg, rect)
    }

    /**
     * Decode the wallpaper once per resume, downsampled to the panel.
     *
     * `inSampleSize` matters here rather than being tidiness: a phone photo is
     * commonly 4000px wide and decoding one at full size costs ~48 MB on a deck with
     * around 600 MB free. Sampled to the panel it is nearer 9 MB.
     *
     * Failure is silent and leaves the background plain — a wallpaper whose URI
     * permission did not survive a reboot must not stop the launcher drawing.
     */
    private fun loadWallpaper() {
        wallpaperDimState.value = Prefs.wallpaperDim(this)

        val mode = Prefs.wallpaper(this)
        val uri = Prefs.wallpaperUri(this)
        if (mode == Prefs.WALL_NONE) {
            wallpaperState.value = null
            return
        }
        if (mode != Prefs.WALL_CUSTOM || uri == null) {
            // The bundled image. Already cropped to the panel and stored as a 95 KB
            // WebP, so it decodes straight through with no sampling maths.
            Thread {
                val bmp = runCatching {
                    BitmapFactory.decodeResource(resources, R.drawable.wallpaper_default)
                }.getOrNull()
                runOnUiThread { if (!isFinishing) wallpaperState.value = bmp }
            }.start()
            return
        }
        Thread {
            val bmp = runCatching {
                val target = LaunchEngine.displaySize(this)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > target.x * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
            runOnUiThread { if (!isFinishing) wallpaperState.value = bmp }
        }.start()
    }

    /** Read the stage app and the side camera's settings back from prefs. */
    private fun loadStage() {
        stageAppState.value = Prefs.stageApp(this)
        cameraPanelState.value = Panel(
            PanelType.CAMERA, 0f, 0f, 1f, 1f,
            label = "Camera",
            camId = Prefs.camId(this),
            rotation = Prefs.camRotation(this)
        )
        launchStageApp()
    }

    // The wallpaper loader lived here. The SYNC-style home is flat dark by design,
    // so there is no backdrop to blur any more — which also takes a full-screen
    // blur pass off a low-RAM Unisoc chip.

    // ---- dashboard-mode canvas panels (View-based, hosted via AndroidView) --

    private fun refreshPanelsIfChanged() {
        val sig = (Prefs.panelsRaw(this) ?: "") + "|" + Prefs.accent(this) + "|" +
            Prefs.theme(this) + "|" + Prefs.mode(this)
        if (sig == lastPanelsJson) { startLocation(); startObd(); return }
        lastPanelsJson = sig
        panelHost.post { renderPanels() }
        syncOverlayPanels()
    }

    private fun syncOverlayPanels() {
        if (Prefs.mode(this) == 1 && canOverlay()) {
            OverlayPanelsService.stop(this)
            handler.postDelayed({ OverlayPanelsService.start(this); overlaysOnState.value = true }, 500)
        } else if (OverlayPanelsService.isRunning) {
            OverlayPanelsService.stop(this)
            handler.postDelayed({ overlaysOnState.value = false }, 300)
        }
    }

    private fun ensureOverlaysForMode() {
        if (Prefs.mode(this) == 1 && canOverlay() && !OverlayPanelsService.isRunning) {
            handler.postDelayed({
                if (Prefs.mode(this) == 1 && !OverlayPanelsService.isRunning) OverlayPanelsService.start(this)
                overlaysOnState.value = OverlayPanelsService.isRunning
            }, 1600)
        }
    }

    private fun renderPanels() {
        val w = panelHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = panelHost.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        destroyWebViews(panelHost)
        panelHost.removeAllViews()
        clockPanels.clear(); speedGauges.clear(); obdGauges.clear()
        stopLocation(); stopObd()

        val panels = Prefs.panels(this)
        val overlayMode = Prefs.mode(this) == 1
        var canvasPanels = 0

        for (p in panels) {
            if (p.isWindowedApp() || p.isFullscreenApp()) continue
            if (overlayMode && p.isDrawn()) continue
            runCatching {
                val content = buildPanelView(p) ?: return@runCatching
                val card = FrameLayout(this)
                card.background = Ui.cardBg(this)
                Ui.roundify(card, 18)
                card.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                val lp = FrameLayout.LayoutParams(
                    ((p.r - p.l) * w).toInt().coerceAtLeast(1),
                    ((p.b - p.t) * h).toInt().coerceAtLeast(1)
                )
                lp.leftMargin = (p.l * w).toInt()
                lp.topMargin = (p.t * h).toInt()
                panelHost.addView(card, lp)
                canvasPanels++
            }
        }
        showCanvasState.value = canvasPanels > 0
        startLocation(); startObd()
    }

    private fun destroyWebViews(v: View) {
        if (v is WebView) runCatching { WebPanelHost.forget(v); v.destroy() }
        else if (v is android.view.ViewGroup) for (i in 0 until v.childCount) destroyWebViews(v.getChildAt(i))
    }

    private fun buildPanelView(p: Panel): View? = when (p.type) {
        PanelType.WEB, PanelType.HTML -> buildWeb(p)
        PanelType.IMAGE -> ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            p.url?.let { u -> runCatching { setImageURI(Uri.parse(u)) } }
        }
        PanelType.CLOCK -> buildClockCard()
        PanelType.SPEED -> gaugeFor("gps_speed").also { speedGauges.add(it) }
        PanelType.OBD -> gaugeFor(p.metric).also { obdGauges.add((p.metric ?: "") to it) }
        PanelType.CAMERA -> CameraPanel(this, p.camId, p.pkg, p.rotation)
        PanelType.NOTIF -> p.pkg?.let { NotifPanel(this, it) }
        // An APP is a freeform window floating above this activity, not a View this
        // activity can build.
        PanelType.APP -> null
    }

    private fun buildClockCard(): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val th = Ui.th(this)
        val time = TextView(this).apply {
            textSize = 34f; setTextColor(th.text)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
        val d = TextView(this).apply { textSize = 11f; setTextColor(th.dim) }
        wrap.addView(time); wrap.addView(d)
        clockPanels.add(time to d)
        // tick the canvas clock
        handler.post(object : Runnable {
            override fun run() {
                if (clockPanels.isEmpty()) return
                val now = java.util.Date()
                val t = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(now)
                val dd = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(now)
                clockPanels.forEach { it.first.text = t; it.second.text = dd }
                handler.postDelayed(this, 1000)
            }
        })
        return wrap
    }

    private fun gaugeFor(metric: String?): GaugeView {
        val g = GaugeView(this)
        g.accentColor = Ui.accent(this)
        val th = Ui.th(this)
        g.setPalette(if (th.light) 0x14000000 else 0x1FFFFFFF, th.text, th.dim)
        when (metric) {
            "gps_speed", "speed" -> g.configure("SPEED", "km/h", 0f, 240f)
            "rpm" -> g.configure("RPM", "rpm", 0f, 8000f)
            "coolant" -> g.configure("COOLANT", "°C", 0f, 130f)
            "throttle" -> g.configure("THROTTLE", "%", 0f, 100f)
            "map" -> g.configure("BOOST/MAP", "kPa", 0f, 250f)
            "intake" -> g.configure("INTAKE", "°C", 0f, 80f)
            else -> g.configure(metric ?: "", "", 0f, 100f)
        }
        return g
    }

    private fun buildWeb(p: Panel): WebView {
        val wv = WebView(this)
        Ui.configureWeb(wv, Prefs.muteOverlays(this))
        // Tracked so exactly one WebView is ever running — see [WebPanelHost].
        WebPanelHost.register(wv)
        if (p.type == PanelType.HTML) wv.loadDataWithBaseURL(null, p.html ?: "<h2>DWM</h2>", "text/html", "utf-8", null)
        else wv.loadUrl(p.url ?: "about:blank")
        return wv
    }

    // ---- feeds -----------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (locListener != null || speedGauges.isEmpty()) return
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val l = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val kmh = loc.speed * 3.6f
                speedGauges.forEach { it.setValue(kmh) }
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        locListener = l
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, l) }
    }

    private fun stopLocation() {
        locListener?.let { runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(it) } }
        locListener = null
    }

    private fun startObd() {
        if (obd != null || obdGauges.isEmpty()) return
        val mac = Prefs.obdMac(this) ?: return
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) return
        obd = ObdManager(mac, obdGauges.map { it.first }.distinct()) { key, num, _ ->
            if (!key.startsWith("_") && num != null) runOnUiThread {
                obdGauges.filter { it.first == key }.forEach { it.second.setValue(num) }
            }
        }.also { it.start(this) }
    }

    private fun stopObd() { obd?.stop(); obd = null }

    // ---- permissions + updater -------------------------------------------

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions() {
        val panels = Prefs.panels(this)
        val need = ArrayList<String>()
        if (panels.any { it.type == PanelType.CAMERA } && !granted(Manifest.permission.CAMERA))
            need.add(Manifest.permission.CAMERA)
        if (panels.any { it.type == PanelType.SPEED } && !granted(Manifest.permission.ACCESS_FINE_LOCATION))
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (panels.any { it.type == PanelType.OBD } && Build.VERSION.SDK_INT >= 31 &&
            !granted(Manifest.permission.BLUETOOTH_CONNECT))
            need.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) { lastPanelsJson = "_force"; refreshPanelsIfChanged() }
    }

    private fun autoCheckUpdate() {
        Updater.check(this) { result -> if (result is Updater.Result.Available) autoPromptUpdate(result.info) }
    }

    private fun autoPromptUpdate(info: Updater.Info) {
        Ui.dialog(this)
            .setTitle("Update to v${info.versionName}?")
            .setMessage(if (info.notes.isBlank()) "A new version is available." else info.notes)
            .setPositiveButton("Update") { _, _ -> startUpdate(info) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startUpdate(info: Updater.Info) {
        if (!Updater.canInstall(this)) {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            return
        }
        val dlg = Ui.dialog(this).setTitle("Updating").setMessage("Starting…").setCancelable(false).create()
        dlg.show()
        Updater.downloadAndInstall(
            this, info,
            onProgress = { pct -> dlg.setMessage("Downloading… $pct%") },
            onCommitted = { runCatching { dlg.dismiss() } },
            onError = { msg -> runCatching { dlg.dismiss() }; Toast.makeText(this, "Update failed: $msg", Toast.LENGTH_LONG).show() }
        )
    }

    private fun openSetting(action: String) {
        runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun canOverlay() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    companion object {
        private var didAutoLoad = false
        private var didUpdateCheck = false
        private const val REQ_PERMS = 301

    }
}
