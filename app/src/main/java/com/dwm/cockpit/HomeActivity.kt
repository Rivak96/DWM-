package com.dwm.cockpit

import android.Manifest
import android.annotation.SuppressLint
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
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The launcher home: a reference-style card dashboard — action tiles left, a
 * cockpit hero card with live layout preview centre, favourites + status right.
 * Dashboard-mode panels draw full-bleed on the canvas behind it (the dashboard
 * hides itself when panels are present). Runs clock, GPS-speed and OBD feeds.
 */
class HomeActivity : DwmActivity() {

    private lateinit var wallpaper: ImageView
    private lateinit var panelHost: FrameLayout
    private lateinit var dashboard: LinearLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var cardTiles: LinearLayout
    private lateinit var cardHero: LinearLayout
    private lateinit var cardFavs: LinearLayout
    private lateinit var cardStatus: LinearLayout
    private lateinit var heroSub: TextView
    private lateinit var heroPreviewWrap: FrameLayout
    private lateinit var favWrap: LinearLayout
    private var heroPreview: LayoutPreview? = null
    private var tileCamera: Pair<LinearLayout, Pair<ImageView, TextView>>? = null

    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
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
        dashboard = findViewById(R.id.dashboard)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        cardTiles = findViewById(R.id.cardTiles)
        cardHero = findViewById(R.id.cardHero)
        cardFavs = findViewById(R.id.cardFavs)
        cardStatus = findViewById(R.id.cardStatus)
        heroSub = findViewById(R.id.heroSub)
        heroPreviewWrap = findViewById(R.id.heroPreviewWrap)
        favWrap = findViewById(R.id.favWrap)

        findViewById<View>(R.id.topReload).setOnClickListener { reloadCockpit() }
        findViewById<View>(R.id.topEdit).setOnClickListener { startActivity(Intent(this, LayoutEditorActivity::class.java)) }
        findViewById<View>(R.id.topSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btnHeroLaunch).setOnClickListener { reloadCockpit() }
        findViewById<View>(R.id.btnHeroEdit).setOnClickListener { startActivity(Intent(this, LayoutEditorActivity::class.java)) }
    }

    private fun reloadCockpit() {
        lastPanelsJson = "_force"
        refreshPanelsIfChanged()
        LaunchEngine.launchLayout(this, Prefs.panels(this))
    }

    private fun toggleOverlays() {
        if (!canOverlay()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        if (OverlayPanelsService.isRunning) OverlayPanelsService.stop(this)
        else OverlayPanelsService.start(this)
        handler.postDelayed({ refreshTileStates(); buildStatusCard() }, 600)
    }

    override fun onStart() {
        super.onStart()
        if (Prefs.fontScale(this) != appliedFontScale) { recreate(); return }
        Ui.themeWindow(this)
        Ui.skin(this, findViewById(android.R.id.content))
        styleDashboard()
        applyWallpaper()
        buildTiles()
        buildFavCard()
        buildStatusCard()
        updateHero()
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

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tick)
        stopLocation()
        stopObd()
    }

    // ---- dashboard chrome ------------------------------------------------

    private fun styleDashboard() {
        val th = Ui.th(this)
        for (card in listOf(cardTiles, cardHero, cardFavs, cardStatus)) {
            card.background = Ui.cardBg(this)
            Ui.roundify(card, 20)
        }
        clock.setTextColor(th.text)
        date.setTextColor(th.dim)
        findViewById<TextView>(R.id.heroTitle).apply {
            setTextColor(th.text)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        heroSub.setTextColor(th.dim)
        findViewById<TextView>(R.id.btnHeroLaunch).apply {
            background = Ui.primaryBtnBg(this@HomeActivity)
            Ui.roundify(this, 14)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        findViewById<TextView>(R.id.btnHeroEdit).apply {
            background = Ui.chipBg(this@HomeActivity)
            Ui.roundify(this, 14)
            setTextColor(th.text)
        }
        val tint = android.content.res.ColorStateList.valueOf(th.text)
        for (id in listOf(R.id.topReload, R.id.topEdit, R.id.topSettings)) {
            findViewById<ImageButton>(id).apply {
                background = Ui.iconRipple(this@HomeActivity)
                imageTintList = tint
            }
        }
    }

    /** Left card: 2×3 grid of coloured action tiles (reference style). */
    private fun buildTiles() {
        cardTiles.removeAllViews()
        val th = Ui.th(this)
        val accent = Ui.accent(this)

        fun tile(iconRes: Int, label: String, fill: Int?, onClick: () -> Unit): LinearLayout {
            val filled = fill != null
            val t = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = Ui.tileBg(this@HomeActivity, fill ?: th.surface)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            Ui.roundify(t, 18)
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@HomeActivity, 26), Ui.dp(this@HomeActivity, 26))
                setImageResource(iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    if (filled) 0xFFFFFFFF.toInt() else th.text
                )
            }
            val lbl = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(if (filled) 0xFFFFFFFF.toInt() else th.dim)
                setPadding(0, Ui.dp(this@HomeActivity, 6), 0, 0)
            }
            t.addView(icon); t.addView(lbl)
            return t
        }

        val overlaysOn = OverlayPanelsService.isRunning
        val camTile = tile(R.drawable.ic_layers, "Overlays", if (overlaysOn) Ui.GREEN else null) { toggleOverlays() }
        tileCamera = camTile to ((camTile.getChildAt(0) as ImageView) to (camTile.getChildAt(1) as TextView))

        val tiles = listOf(
            tile(R.drawable.ic_play, "CarPlay", accent) { launchCarplay() },
            camTile,
            tile(R.drawable.ic_bt, "Bluetooth", null) { openSetting(Settings.ACTION_BLUETOOTH_SETTINGS) },
            tile(R.drawable.ic_wifi, "Wi-Fi", null) { openSetting(Settings.ACTION_WIFI_SETTINGS) },
            tile(R.drawable.ic_apps, "Apps", null) { startActivity(Intent(this, AppDrawerActivity::class.java)) },
            tile(R.drawable.ic_settings, "Settings", null) { startActivity(Intent(this, SettingsActivity::class.java)) }
        )

        val gap = Ui.dp(this, 10)
        var i = 0
        while (i < tiles.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                ).apply { if (i > 0) topMargin = gap }
            }
            for (j in 0..1) {
                val t = tiles.getOrNull(i + j) ?: break
                t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .apply { if (j > 0) marginStart = gap }
                row.addView(t)
            }
            cardTiles.addView(row)
            i += 2
        }
    }

    private fun refreshTileStates() {
        val (tile, iconLbl) = tileCamera ?: return
        val on = OverlayPanelsService.isRunning
        val th = Ui.th(this)
        tile.background = Ui.tileBg(this, if (on) Ui.GREEN else th.surface)
        Ui.roundify(tile, 18)
        iconLbl.first.imageTintList =
            android.content.res.ColorStateList.valueOf(if (on) 0xFFFFFFFF.toInt() else th.text)
        iconLbl.second.setTextColor(if (on) 0xFFFFFFFF.toInt() else th.dim)
    }

    /** Show the floating ≡ DWM pill (its favourites/overlays/raise/mute menu). */
    private fun startPill() {
        if (!canOverlay()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
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

    /** Centre hero: live miniature of the saved layout + subtitle. */
    private fun updateHero() {
        val panels = Prefs.panels(this)
        if (heroPreview == null) {
            heroPreview = LayoutPreview(this)
            heroPreviewWrap.addView(
                heroPreview,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        heroPreview?.setColors(Ui.accent(this), Ui.th(this))
        heroPreview?.panels = panels
        val modeName = if (Prefs.mode(this) == 1) "Solo + overlays" else "Dashboard"
        heroSub.text = if (panels.isEmpty())
            "No layout yet — tap Edit to build your cockpit"
        else
            "$modeName · ${panels.size} panel${if (panels.size == 1) "" else "s"}"
    }

    /** Right card: favourites grid (2×N, up to 8). */
    private fun buildFavCard() {
        favWrap.removeAllViews()
        val favs = Apps.effectiveFavorites(this).take(8)
        cardFavs.visibility = if (Prefs.showFavGrid(this) && favs.isNotEmpty()) View.VISIBLE else View.GONE
        if (favs.isEmpty()) return
        val th = Ui.th(this)
        val iconPx = Ui.dp(this, 46)
        val gap = Ui.dp(this, 6)
        val perRow = 2
        var i = 0
        while (i < favs.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = gap }
            }
            for (j in 0 until perRow) {
                val pkg = favs.getOrNull(i + j) ?: break
                val d = Apps.icon(this, pkg) ?: continue
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = Ui.iconRipple(this@HomeActivity)
                    setPadding(gap, gap, gap, gap)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { LaunchEngine.launchFullscreen(this@HomeActivity, pkg) }
                    setOnLongClickListener { favItemMenu(pkg); true }
                }
                item.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
                    setImageDrawable(d)
                })
                item.addView(TextView(this).apply {
                    text = Apps.label(this@HomeActivity, pkg)
                    textSize = 10f
                    maxLines = 1
                    gravity = Gravity.CENTER
                    setTextColor(th.dim)
                    setPadding(0, Ui.dp(this@HomeActivity, 4), 0, 0)
                })
                row.addView(item)
            }
            favWrap.addView(row)
            i += perRow
        }
    }

    private fun favItemMenu(pkg: String) {
        val label = Apps.label(this, pkg)
        Ui.dialog(this)
            .setTitle(label)
            .setItems(arrayOf("Open in window", "Move up", "Move down", "Remove from favourites")) { _, w ->
                val cur = Apps.effectiveFavorites(this).toMutableList()
                val i = cur.indexOf(pkg)
                when (w) {
                    0 -> {
                        val s = LaunchEngine.displaySize(this)
                        LaunchEngine.launchWindow(this, pkg, android.graphics.Rect(s.x / 6, s.y / 6, s.x * 5 / 6, s.y * 5 / 6))
                    }
                    1 -> if (i > 0) { cur.removeAt(i); cur.add(i - 1, pkg); Prefs.saveFavorites(this, cur); buildFavCard() }
                    2 -> if (i in 0 until cur.size - 1) { cur.removeAt(i); cur.add(i + 1, pkg); Prefs.saveFavorites(this, cur); buildFavCard() }
                    3 -> if (i >= 0) { cur.removeAt(i); Prefs.saveFavorites(this, cur); buildFavCard() }
                }
            }
            .show()
    }

    /** Right-bottom card: overlays + updates status rows. */
    private fun buildStatusCard() {
        cardStatus.removeAllViews()
        val th = Ui.th(this)

        fun row(label: String, value: String, valueColor: Int, onClick: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(this@HomeActivity, 40)
                background = Ui.iconRipple(this@HomeActivity)
                setOnClickListener { onClick() }
            }
            r.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(th.text)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(TextView(this).apply {
                text = value
                textSize = 12f
                setTextColor(valueColor)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            })
            cardStatus.addView(r)
        }

        val overlaysOn = OverlayPanelsService.isRunning
        row("Overlays", if (overlaysOn) "On" else "Off", if (overlaysOn) Ui.GREEN else th.dim) { toggleOverlays() }
        row("Floating pill", "Show", th.dim) { startPill() }
        row("Version", "v" + Updater.currentVersionName(this), th.dim) {
            Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show()
            Updater.check(this) { result ->
                when (result) {
                    is Updater.Result.UpToDate -> Toast.makeText(this, "You're on the latest", Toast.LENGTH_SHORT).show()
                    is Updater.Result.Error -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    is Updater.Result.Available -> autoPromptUpdate(result.info)
                }
            }
        }
    }

    // ---- updater ---------------------------------------------------------

    private fun autoCheckUpdate() {
        Updater.check(this) { result ->
            if (result is Updater.Result.Available) autoPromptUpdate(result.info)
        }
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
                Toast.makeText(this, "Update failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    // ---- panel rendering -------------------------------------------------

    /** Re-render only when the saved layout (or theme/mode) actually changed, so
     *  WebViews/cameras aren't torn down every time we come back to Home. */
    private fun refreshPanelsIfChanged() {
        val sig = (Prefs.panelsRaw(this) ?: "") + "|" + Prefs.accent(this) + "|" +
            Prefs.theme(this) + "|" + Prefs.mode(this)
        if (sig == lastPanelsJson) { startLocation(); startObd(); return }
        lastPanelsJson = sig
        panelHost.post { renderPanels() }
        updateHero()
        syncOverlayPanels()
    }

    /** Keep the always-on-top overlay panels in sync with the layout/mode:
     *  Solo mode → (re)start with the fresh layout; Dashboard → stop them. */
    private fun syncOverlayPanels() {
        if (Prefs.mode(this) == 1 && canOverlay()) {
            OverlayPanelsService.stop(this)
            handler.postDelayed({ OverlayPanelsService.start(this); refreshTileStates(); buildStatusCard() }, 500)
        } else if (OverlayPanelsService.isRunning) {
            OverlayPanelsService.stop(this)
            handler.postDelayed({ refreshTileStates(); buildStatusCard() }, 300)
        }
    }

    /** Ensure overlays are up in Solo mode on every launch — without a needless
     *  restart if they're already running. */
    private fun ensureOverlaysForMode() {
        if (Prefs.mode(this) == 1 && canOverlay() && !OverlayPanelsService.isRunning) {
            handler.postDelayed({
                if (Prefs.mode(this) == 1 && !OverlayPanelsService.isRunning) OverlayPanelsService.start(this)
                refreshTileStates(); buildStatusCard()
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
        // Solo mode: drawn panels live in the always-on-top overlay service, so
        // the home canvas stays the clean card dashboard.
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

        // Dashboard cards show only when the canvas isn't taken by panels.
        dashboard.visibility = if (canvasPanels == 0) View.VISIBLE else View.GONE

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
        PanelType.CAMERA -> CameraPanel(this, p.camId, p.pkg, p.rotation)
        PanelType.NOTIF -> p.pkg?.let { NotifPanel(this, it) }
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

    // ---- wallpaper -------------------------------------------------------

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
