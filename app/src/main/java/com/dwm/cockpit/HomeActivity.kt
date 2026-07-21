package com.dwm.cockpit

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The launcher home / cockpit. Draws DWM-owned panels (web, gauges, image, live
 * camera) as rounded cards on its canvas; real apps float above in freeform
 * windows. Runs the clock, GPS-speed and OBD feeds, and hosts the editable dock.
 */
class HomeActivity : DwmActivity() {

    private lateinit var wallpaper: ImageView
    private lateinit var panelHost: FrameLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var dock: LinearLayout
    private lateinit var dockScroll: HorizontalScrollView
    private lateinit var dockToggle: Button
    private lateinit var topBar: LinearLayout
    private lateinit var topToggle: Button
    private lateinit var cluster: LinearLayout
    private lateinit var favGrid: LinearLayout
    private lateinit var favGridScroll: View

    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    private var lastPanelsJson: String? = "_never"
    private var appliedFontScale = 1.0f

    // live panel registries, rebuilt on each render
    private val clockPanels = ArrayList<Pair<TextView, TextView>>()
    private val speedGauges = ArrayList<GaugeView>()
    private val obdGauges = ArrayList<Pair<String, GaugeView>>()

    private var locListener: LocationListener? = null
    private var obd: ObdManager? = null

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val t = timeFmt.format(now)
            val d = dateFmt.format(now)
            clock.text = t
            date.text = d
            clockPanels.forEach { it.first.text = t; it.second.text = d }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedFontScale = Prefs.fontScale(this)
        setContentView(R.layout.activity_home)

        wallpaper = findViewById(R.id.wallpaper)
        panelHost = findViewById(R.id.panelHost)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        dock = findViewById(R.id.dock)
        dockScroll = findViewById(R.id.dockScroll)
        dockToggle = findViewById(R.id.dockToggle)
        topBar = findViewById(R.id.topBar)
        topToggle = findViewById(R.id.topToggle)
        cluster = findViewById(R.id.cluster)
        favGrid = findViewById(R.id.favGrid)
        favGridScroll = findViewById(R.id.favGridScroll)

        // legible over any panel behind it
        clock.setShadowLayer(10f, 0f, 2f, 0x99000000.toInt())
        date.setShadowLayer(8f, 0f, 1f, 0x99000000.toInt())

        findViewById<View>(R.id.btnApps).setOnClickListener { startActivity(Intent(this, AppDrawerActivity::class.java)) }
        findViewById<View>(R.id.btnLayout).setOnClickListener { startActivity(Intent(this, LayoutEditorActivity::class.java)) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btnReload).setOnClickListener {
            lastPanelsJson = "_force"
            refreshPanelsIfChanged()
            LaunchEngine.launchLayout(this, Prefs.panels(this))
        }
        findViewById<View>(R.id.btnBluetooth).setOnClickListener { openSetting(Settings.ACTION_BLUETOOTH_SETTINGS) }
        findViewById<View>(R.id.btnWifi).setOnClickListener { openSetting(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<View>(R.id.btnOverlays).setOnClickListener { toggleOverlays() }
        dockToggle.setOnClickListener {
            Prefs.setDockCollapsed(this, !Prefs.dockCollapsed(this))
            applyDockCollapsed()
        }
        topToggle.setOnClickListener {
            Prefs.setTopCollapsed(this, !Prefs.topCollapsed(this))
            applyTopCollapsed()
        }
    }

    private fun styleTopIcons() {
        val tint = android.content.res.ColorStateList.valueOf(Ui.th(this).text)
        for (i in 0 until cluster.childCount) {
            val v = cluster.getChildAt(i)
            if (v is android.widget.ImageButton) {
                v.background = Ui.iconRipple(this)
                v.imageTintList = tint
            }
        }
    }

    private fun toggleOverlays() {
        if (!canOverlay()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        if (OverlayPanelsService.isRunning) OverlayPanelsService.stop(this)
        else OverlayPanelsService.start(this)
    }

    private fun applyTopCollapsed() {
        val collapsed = Prefs.topCollapsed(this)
        topBar.visibility = if (collapsed) View.GONE else View.VISIBLE
        topToggle.text = if (collapsed) "⌄" else "⌃"
    }

    override fun onStart() {
        super.onStart()
        if (Prefs.fontScale(this) != appliedFontScale) { recreate(); return }
        Ui.themeWindow(this)
        Ui.skin(this, findViewById(android.R.id.content))
        dockScroll.background = Ui.barBg(this)
        cluster.background = Ui.clusterBg(this)
        favGrid.background = Ui.clusterBg(this)
        favGrid.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8))
        Ui.roundify(favGrid, 24)
        styleTopIcons()
        applyWallpaper()
        buildDock()
        buildFavGrid()
        applyDockCollapsed()
        applyTopCollapsed()
        handler.post(tick)
        ensurePermissions()
        refreshPanelsIfChanged()

        // Process-wide guard: auto-launch once per boot/process, NOT again after
        // recreate() (e.g. a text-size change must not relaunch all the apps).
        if (!didAutoLoad && Prefs.autoLoad(this)) {
            didAutoLoad = true
            handler.postDelayed({ LaunchEngine.launchLayout(this, Prefs.panels(this)) }, 700)
        }
        if (Prefs.overlayOnStart(this) && canOverlay()) OverlayService.start(this)
        ensureOverlaysForMode()

        if (!didUpdateCheck && Prefs.autoUpdate(this) && Prefs.updateRepo(this).isNotBlank()) {
            didUpdateCheck = true
            handler.postDelayed({ autoCheckUpdate() }, 3000)
        }
    }

    private fun autoCheckUpdate() {
        Updater.check(this) { result ->
            if (result is Updater.Result.Available) {
                Ui.dialog(this)
                    .setTitle("Update to v${result.info.versionName}?")
                    .setMessage(if (result.info.notes.isBlank()) "A new version is available." else result.info.notes)
                    .setPositiveButton("Update") { _, _ -> startUpdate(result.info) }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun startUpdate(info: Updater.Info) {
        if (!Updater.canInstall(this)) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            }
            return
        }
        val dlg = Ui.dialog(this).setTitle("Updating").setMessage("Starting…").setCancelable(false).create()
        dlg.show()
        Updater.downloadAndInstall(
            this, info,
            onProgress = { pct -> dlg.setMessage("Downloading… $pct%") },
            onCommitted = { runCatching { dlg.dismiss() } },
            onError = { msg ->
                runCatching { dlg.dismiss() }
                android.widget.Toast.makeText(this, "Update failed: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tick)
        stopLocation()
        stopObd()
    }

    // ---- panel rendering -------------------------------------------------

    /** Re-render only when the saved layout (or accent) actually changed, so
     *  WebViews/cameras aren't torn down every time we come back to Home. */
    private fun refreshPanelsIfChanged() {
        val sig = (Prefs.panelsRaw(this) ?: "") + "|" + Prefs.accent(this) + "|" +
            Prefs.theme(this) + "|" + Prefs.mode(this)
        if (sig == lastPanelsJson) { startLocation(); startObd(); return }
        lastPanelsJson = sig
        panelHost.post { renderPanels() }
        syncOverlayPanels()
    }

    /** Keep the always-on-top overlay panels in sync with the layout/mode:
     *  Solo mode → (re)start with the fresh layout; Dashboard → stop them.
     *  Called when the layout actually changed, so a restart is warranted. */
    private fun syncOverlayPanels() {
        if (Prefs.mode(this) == 1 && canOverlay()) {
            OverlayPanelsService.stop(this)
            handler.postDelayed({ OverlayPanelsService.start(this) }, 500)
        } else if (OverlayPanelsService.isRunning) {
            OverlayPanelsService.stop(this)
        }
    }

    /** Ensure overlays are up in Solo mode on every launch — without a needless
     *  restart if they're already running. */
    private fun ensureOverlaysForMode() {
        if (Prefs.mode(this) == 1 && canOverlay() && !OverlayPanelsService.isRunning) {
            handler.postDelayed({
                if (Prefs.mode(this) == 1 && !OverlayPanelsService.isRunning) OverlayPanelsService.start(this)
            }, 1600)
        }
    }

    private fun renderPanels() {
        val w = panelHost.width
        val h = panelHost.height
        if (w == 0 || h == 0) { panelHost.post { renderPanels() }; return }

        destroyWebViews(panelHost) // WebViews leak unless destroy() is called
        panelHost.removeAllViews()
        clockPanels.clear(); speedGauges.clear(); obdGauges.clear()
        stopLocation(); stopObd()

        val panels = Prefs.panels(this)
        // Solo mode: drawn panels live in the always-on-top overlay service, so the
        // home canvas stays a clean launcher (favourites grid).
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
                card.addView(
                    content,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
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

        // Favourites grid shows only when the canvas isn't busy with dashboard
        // panels — this is the fix for the welcome-card / grid overlap.
        val favs = Apps.effectiveFavorites(this)
        val showGrid = Prefs.showFavGrid(this) && favs.isNotEmpty() && canvasPanels == 0
        favGridScroll.visibility = if (showGrid) View.VISIBLE else View.GONE
        if (canvasPanels == 0 && favs.isEmpty()) showEmptyState()

        startLocation()
        startObd()
    }

    private fun destroyWebViews(v: View) {
        if (v is WebView) {
            runCatching { v.destroy() }
        } else if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) destroyWebViews(v.getChildAt(i))
        }
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
        PanelType.CAMERA -> CameraPanel(this, p.camId, p.pkg)
        PanelType.APP -> null
    }

    private fun buildClockCard(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val th = Ui.th(this)
        val time = TextView(this).apply {
            textSize = 34f
            setTextColor(th.text)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
        val d = TextView(this).apply {
            textSize = 11f
            setTextColor(th.dim)
        }
        wrap.addView(time)
        wrap.addView(d)
        clockPanels.add(time to d)
        return wrap
    }

    private fun gaugeFor(metric: String?): GaugeView {
        val g = GaugeView(this)
        g.accentColor = Ui.accent(this)
        val th = Ui.th(this)
        g.setPalette(
            track = if (th.light) 0x14000000 else 0x1FFFFFFF,
            value = th.text,
            label = th.dim
        )
        when (metric) {
            "gps_speed" -> g.configure("SPEED", "km/h", 0f, 240f)
            "rpm" -> g.configure("RPM", "rpm", 0f, 8000f)
            "speed" -> g.configure("SPEED", "km/h", 0f, 240f)
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
        if (p.type == PanelType.HTML) {
            wv.loadDataWithBaseURL(null, p.html ?: DEFAULT_HTML, "text/html", "utf-8", null)
        } else {
            wv.loadUrl(p.url ?: "about:blank")
        }
        return wv
    }

    private fun showEmptyState() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.cardBg(this@HomeActivity)
            setPadding(Ui.dp(this@HomeActivity, 28), Ui.dp(this@HomeActivity, 24), Ui.dp(this@HomeActivity, 28), Ui.dp(this@HomeActivity, 24))
        }
        Ui.roundify(card, 18)

        val th = Ui.th(this)
        card.addView(TextView(this).apply {
            text = "Welcome to DWM"
            textSize = 20f
            setTextColor(th.text)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })
        card.addView(TextView(this).apply {
            text = "Build your cockpit: pick a layout template, tap the + slots to add CarPlay, your camera, gauges or dashboards — then Save. It auto-loads every start."
            textSize = 12f
            setTextColor(th.dim)
            setPadding(0, Ui.dp(this@HomeActivity, 6), 0, Ui.dp(this@HomeActivity, 14))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Choose template"
            setOnClickListener { startActivity(Intent(this@HomeActivity, LayoutEditorActivity::class.java)) }
        })
        row.addView(Button(this).apply {
            text = "Settings"
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = Ui.dp(this@HomeActivity, 10)
            layoutParams = lp
            setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
        })
        card.addView(row)
        Ui.skin(this, card)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.CENTER
        panelHost.addView(card, lp)
    }

    // ---- live data feeds -------------------------------------------------

    // Guarded by the granted() check below and runCatching; lint can't see that.
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
        locListener?.let {
            runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(it) }
        }
        locListener = null
    }

    private fun startObd() {
        if (obd != null || obdGauges.isEmpty()) return
        val mac = Prefs.obdMac(this) ?: return
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) return
        val metrics = obdGauges.map { it.first }.distinct()
        obd = ObdManager(mac, metrics) { key, num, _ ->
            if (!key.startsWith("_") && num != null) {
                runOnUiThread {
                    obdGauges.filter { it.first == key }.forEach { it.second.setValue(num) }
                }
            }
        }.also { it.start(this) }
    }

    private fun stopObd() {
        obd?.stop(); obd = null
    }

    // ---- permissions -----------------------------------------------------

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            lastPanelsJson = "_force"
            refreshPanelsIfChanged()
        }
    }

    // ---- wallpaper / dock ------------------------------------------------

    private fun applyWallpaper() {
        val idx = Prefs.wallpaper(this)
        if (idx == 3) {
            val uri = Prefs.wallpaperUri(this)
            if (uri != null) {
                val ok = runCatching { wallpaper.setImageURI(Uri.parse(uri)) }.isSuccess
                if (ok && wallpaper.drawable != null) { wallpaper.background = null; return }
            }
        }
        wallpaper.setImageDrawable(null)
        wallpaper.background = Ui.wallpaperDrawable(this, idx)
    }

    private fun buildDock() {
        dock.removeAllViews()
        val favs = Apps.effectiveFavorites(this)
        val size = resources.getDimensionPixelSize(R.dimen.dock_icon)
        val gap = resources.getDimensionPixelSize(R.dimen.gap)
        for (pkg in favs) {
            val icon = Apps.icon(this, pkg) ?: continue
            val iv = ImageView(this)
            iv.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
            iv.setImageDrawable(icon)
            iv.setOnClickListener { LaunchEngine.launchFullscreen(this, pkg) }
            iv.setOnLongClickListener { dockItemMenu(pkg); true }
            dock.addView(iv)
        }
    }

    /** Big centred grid of favourite apps — the "5 apps I always see" the user
     *  wanted. Shares the favourites list with the dock. */
    /** Populates the favourites grid. Visibility is decided by renderPanels so it
     *  never overlaps dashboard panels. */
    private fun buildFavGrid() {
        favGrid.removeAllViews()
        val favs = Apps.effectiveFavorites(this)
        val th = Ui.th(this)
        val iconPx = Ui.dp(this, 68)
        val padH = Ui.dp(this, 18)
        val padV = Ui.dp(this, 14)
        for (pkg in favs.take(8)) {
            val d = Apps.icon(this, pkg) ?: continue
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(padH, padV, padH, padV)
                background = Ui.iconRipple(this@HomeActivity)
            }
            item.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
                setImageDrawable(d)
            })
            item.addView(TextView(this).apply {
                text = Apps.label(this@HomeActivity, pkg)
                setTextColor(th.text)
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, Ui.dp(this@HomeActivity, 8), 0, 0)
                setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
            })
            item.setOnClickListener { LaunchEngine.launchFullscreen(this, pkg) }
            item.setOnLongClickListener { dockItemMenu(pkg); true }
            favGrid.addView(item)
        }
    }

    private fun dockItemMenu(pkg: String) {
        val label = Apps.label(this, pkg)
        Ui.dialog(this)
            .setTitle(label)
            .setItems(arrayOf("Open in window", "Move left", "Move right", "Remove from dock")) { _, w ->
                val cur = Apps.effectiveFavorites(this).toMutableList()
                val i = cur.indexOf(pkg)
                when (w) {
                    0 -> {
                        val s = LaunchEngine.displaySize(this)
                        LaunchEngine.launchWindow(this, pkg, android.graphics.Rect(s.x / 6, s.y / 6, s.x * 5 / 6, s.y * 5 / 6))
                    }
                    1 -> if (i > 0) { cur.removeAt(i); cur.add(i - 1, pkg); Prefs.saveFavorites(this, cur); buildDock(); buildFavGrid() }
                    2 -> if (i in 0 until cur.size - 1) { cur.removeAt(i); cur.add(i + 1, pkg); Prefs.saveFavorites(this, cur); buildDock(); buildFavGrid() }
                    3 -> if (i >= 0) { cur.removeAt(i); Prefs.saveFavorites(this, cur); buildDock(); buildFavGrid() }
                }
            }
            .show()
    }

    private fun applyDockCollapsed() {
        val collapsed = Prefs.dockCollapsed(this)
        dockScroll.visibility = if (collapsed) View.GONE else View.VISIBLE
        dockToggle.text = if (collapsed) "⌃" else "⌄"
    }

    private fun openSetting(action: String) {
        runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun canOverlay() =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* stay on the launcher */ }

    companion object {
        private var didAutoLoad = false
        private var didUpdateCheck = false
        private const val REQ_PERMS = 301
        private const val DEFAULT_HTML =
            "<html><body style='background:transparent;color:#F2F2F2;font-family:sans-serif;" +
                "display:flex;align-items:center;justify-content:center;height:100%;margin:0'>" +
                "<h2>Custom HTML panel</h2></body></html>"
    }
}
