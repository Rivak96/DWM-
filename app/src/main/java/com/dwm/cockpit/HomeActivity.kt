package com.dwm.cockpit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import com.dwm.cockpit.ui.DwmHome
import com.dwm.cockpit.ui.DwmTheme
import com.dwm.cockpit.ui.HomeActions

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
    private val wallpaperState = mutableStateOf<ImageBitmap?>(null)

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
            overlays = { toggleOverlays() },
            bluetooth = { openSetting(Settings.ACTION_BLUETOOTH_SETTINGS) },
            wifi = { openSetting(Settings.ACTION_WIFI_SETTINGS) },
            apps = { startActivity(Intent(this, AppDrawerActivity::class.java)) },
            edit = { startActivity(Intent(this, LayoutEditorActivity::class.java)) },
            settings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            reload = { reloadCockpit() },
            pill = { startPill() }
        )

        setContent {
            DwmTheme(this) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (showCanvasState.value) {
                        AndroidView(factory = { panelHost }, modifier = Modifier.fillMaxSize())
                    } else {
                        DwmHome(
                            wallpaper = wallpaperState.value,
                            overlaysOn = overlaysOnState.value,
                            actions = actions
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (recreateIfScaleChanged()) return

        wallpaperState.value = loadWallpaperBitmap()
        overlaysOnState.value = OverlayPanelsService.isRunning

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

    // ---- data for Compose ------------------------------------------------

    private fun loadWallpaperBitmap(): ImageBitmap? {
        val idx = Prefs.wallpaper(this)
        if (idx == 3) {
            val uri = Prefs.wallpaperUri(this)
            if (uri != null) {
                val bmp = runCatching {
                    contentResolver.openInputStream(Uri.parse(uri))?.use {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }.getOrNull()
                if (bmp != null) return bmp.asImageBitmap()
            }
        }
        val d = Ui.wallpaperDrawable(this, idx)
        val bmp = Bitmap.createBitmap(480, 288, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 480, 288)
        d.draw(Canvas(bmp))
        return bmp.asImageBitmap()
    }

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
        if (v is WebView) runCatching { v.destroy() }
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
