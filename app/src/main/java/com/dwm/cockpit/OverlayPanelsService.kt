package com.dwm.cockpit

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Always-on-top overlay panels: renders every DWM-drawn panel of the saved
 * layout (camera, gauges, speed, clock, web, image) as floating cards that sit
 * OVER any app — including fullscreen CarPlay. Each card has a small grip:
 * drag it to move; the new position is saved back into the layout.
 */
class OverlayPanelsService : Service() {

    private lateinit var wm: WindowManager
    private val cards = ArrayList<Pair<View, WindowManager.LayoutParams>>()

    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    private val clockViews = ArrayList<Pair<TextView, TextView>>()
    private val speedGauges = ArrayList<GaugeView>()
    private val obdGauges = ArrayList<Pair<String, GaugeView>>()
    private var obd: ObdManager? = null
    private var locListener: LocationListener? = null

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val t = timeFmt.format(now)
            val d = dateFmt.format(now)
            clockViews.forEach { it.first.text = t; it.second.text = d }
            handler.postDelayed(this, 1000)
        }
    }

    /** Optional: re-raise windowed apps (e.g. AUX camera) above the fullscreen
     *  base app, which they sink behind when it is tapped. Opt-in (may flicker). */
    private val pinTick = object : Runnable {
        override fun run() {
            if (Prefs.pinWindows(this@OverlayPanelsService)) {
                LaunchEngine.raiseWindows(this@OverlayPanelsService, Prefs.panels(this@OverlayPanelsService))
            }
            handler.postDelayed(this, 6000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(NOTIF_ID, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlays()
        // Keep running if there are panels to show OR if we're only here to pin
        // windows on top; otherwise there's nothing to do.
        if (cards.isEmpty() && !Prefs.pinWindows(this)) {
            android.widget.Toast.makeText(
                this, "No overlay panels in the layout — add gauges/camera/web in Edit",
                android.widget.Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }
        if (cards.isNotEmpty()) { handler.post(tick); startFeeds() }
        handler.postDelayed(pinTick, 6000)
        Prefs.setOverlaysOn(this, true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun buildOverlays() {
        val size = LaunchEngine.displaySize(this)
        val panels = Prefs.panels(this)
        panels.forEachIndexed { index, p ->
            if (!p.isDrawn()) return@forEachIndexed
            runCatching {
                val content = buildContent(p) ?: return@runCatching
                val card = FrameLayout(this)
                card.background = Ui.cardBg(this)
                Ui.roundify(card, 12)
                card.addView(
                    content,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                val accent = Ui.accent(this)
                val gripPx = Ui.dp(this, 34)

                // MOVE grip, top-left (clear ✥ target)
                val moveGrip = TextView(this).apply {
                    text = "✥"
                    textSize = 15f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    background = Ui.gripBg(this@OverlayPanelsService, accent, topLeft = true)
                }
                card.addView(
                    moveGrip,
                    FrameLayout.LayoutParams(gripPx, gripPx).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }
                )

                // RESIZE grip, bottom-right (clear ⤢ target)
                val resizeGrip = TextView(this).apply {
                    text = "⤢"
                    textSize = 15f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    background = Ui.gripBg(this@OverlayPanelsService, accent, topLeft = false)
                }
                card.addView(
                    resizeGrip,
                    FrameLayout.LayoutParams(gripPx, gripPx).apply {
                        gravity = Gravity.BOTTOM or Gravity.END
                    }
                )

                val w = ((p.r - p.l) * size.x).toInt().coerceAtLeast(Ui.dp(this, 80))
                val h = ((p.b - p.t) * size.y).toInt().coerceAtLeast(Ui.dp(this, 80))
                val lp = WindowManager.LayoutParams(
                    w, h,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = (p.l * size.x).toInt()
                    y = (p.t * size.y).toInt()
                }

                makeDraggable(moveGrip, card, lp, index, p)
                makeResizable(resizeGrip, card, lp, index, p)
                wm.addView(card, lp)
                cards.add(card to lp)
            }
        }
    }

    private fun buildContent(p: Panel): View? = when (p.type) {
        PanelType.CLOCK -> {
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val th = Ui.th(this)
            val time = TextView(this).apply {
                textSize = 30f
                setTextColor(th.text)
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            }
            val d = TextView(this).apply { textSize = 10f; setTextColor(th.dim) }
            wrap.addView(time); wrap.addView(d)
            clockViews.add(time to d)
            wrap
        }
        PanelType.SPEED -> gauge("gps_speed").also { speedGauges.add(it) }
        PanelType.OBD -> gauge(p.metric).also { obdGauges.add((p.metric ?: "") to it) }
        PanelType.CAMERA -> if (p.camId != null) CameraPanel(this, p.camId, p.pkg) else null
        PanelType.WEB, PanelType.HTML -> {
            val wv = WebView(this)
            Ui.configureWeb(wv, Prefs.muteOverlays(this))
            if (p.type == PanelType.HTML)
                wv.loadDataWithBaseURL(null, p.html ?: "", "text/html", "utf-8", null)
            else wv.loadUrl(p.url ?: "about:blank")
            wv
        }
        PanelType.IMAGE -> ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            p.url?.let { u -> runCatching { setImageURI(Uri.parse(u)) } }
        }
        PanelType.APP -> null
    }

    private fun gauge(metric: String?): GaugeView {
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

    /** Drag by the grip (clamped on-screen); persist back into the layout. */
    private fun makeDraggable(
        grip: View,
        card: View,
        lp: WindowManager.LayoutParams,
        panelIndex: Int,
        expected: Panel
    ) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val size = LaunchEngine.displaySize(this)
        val keep = Ui.dp(this, 48) // never let the grip corner leave the screen
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    lp.x = (startX + dx).coerceIn(0, (size.x - keep).coerceAtLeast(0))
                    lp.y = (startY + dy).coerceIn(0, (size.y - keep).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(card, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) persistBounds(panelIndex, expected, lp)
                    true
                }
                else -> false
            }
        }
    }

    /** Resize by the bottom-right grip (min size + kept on-screen); persist. */
    private fun makeResizable(
        grip: View,
        card: View,
        lp: WindowManager.LayoutParams,
        panelIndex: Int,
        expected: Panel
    ) {
        var downX = 0f; var downY = 0f; var startW = 0; var startH = 0; var changed = false
        val size = LaunchEngine.displaySize(this)
        val minPx = Ui.dp(this, 80)
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startW = lp.width; startH = lp.height
                    changed = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) changed = true
                    lp.width = (startW + dx).coerceIn(minPx, size.x - lp.x)
                    lp.height = (startH + dy).coerceIn(minPx, size.y - lp.y)
                    runCatching { wm.updateViewLayout(card, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (changed) persistBounds(panelIndex, expected, lp)
                    true
                }
                else -> false
            }
        }
    }

    /** Saves the current window position AND size back into the layout — but only
     *  if the panel at [index] is still the same panel (the layout may have been
     *  edited while overlays ran). */
    private fun persistBounds(index: Int, expected: Panel, lp: WindowManager.LayoutParams) {
        val size = LaunchEngine.displaySize(this)
        val panels = Prefs.panels(this).toMutableList()
        val p = panels.getOrNull(index) ?: return
        val same = p.type == expected.type && p.pkg == expected.pkg &&
            p.metric == expected.metric && p.url == expected.url && p.camId == expected.camId
        if (!same) return
        val l = (lp.x.toFloat() / size.x).coerceIn(0f, 1f)
        val t = (lp.y.toFloat() / size.y).coerceIn(0f, 1f)
        val r = ((lp.x + lp.width).toFloat() / size.x).coerceIn(l, 1f)
        val b = ((lp.y + lp.height).toFloat() / size.y).coerceIn(t, 1f)
        panels[index] = p.withBounds(l, t, r, b)
        Prefs.savePanels(this, panels)
    }

    // ---- data feeds --------------------------------------------------------

    // Guarded by the granted() checks below and runCatching; lint can't see that.
    @SuppressLint("MissingPermission")
    private fun startFeeds() {
        if (speedGauges.isNotEmpty() && granted(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
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
        val mac = Prefs.obdMac(this)
        if (obdGauges.isNotEmpty() && mac != null &&
            (Build.VERSION.SDK_INT < 31 || granted(android.Manifest.permission.BLUETOOTH_CONNECT))
        ) {
            obd = ObdManager(mac, obdGauges.map { it.first }.distinct()) { key, num, _ ->
                if (!key.startsWith("_") && num != null) {
                    handler.post {
                        obdGauges.filter { it.first == key }.forEach { it.second.setValue(num) }
                    }
                }
            }.also { it.start(this) }
        }
    }

    private fun granted(p: String) =
        checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @SuppressLint("LaunchActivityFromNotification")
    private fun buildNotification(): Notification {
        val channelId = "dwm_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "DWM Cockpit", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("DWM overlay panels active")
            .setContentText("Floating over your apps. Drag the grip to move a panel.")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(pinTick)
        locListener?.let {
            runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(it) }
        }
        obd?.stop()
        cards.forEach { (v, _) ->
            destroyWebViews(v)
            runCatching { wm.removeView(v) }
        }
        cards.clear()
        Prefs.setOverlaysOn(this, false)
    }

    /** WebViews leak unless destroy() is called — walk and release them. */
    private fun destroyWebViews(v: View) {
        if (v is WebView) {
            runCatching { v.destroy() }
        } else if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) destroyWebViews(v.getChildAt(i))
        }
    }

    companion object {
        private const val NOTIF_ID = 1002

        /** Process-local liveness. A saved preference can go stale when the
         *  system kills the service (onDestroy never runs) — this can't. */
        @Volatile
        var isRunning: Boolean = false

        // runCatching: a background-start restriction or racing stop must never
        // crash the launcher (SAW holders are exempt, but belt-and-braces).
        fun start(ctx: Context) {
            runCatching {
                val i = Intent(ctx, OverlayPanelsService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, OverlayPanelsService::class.java)) }
        }
    }
}
