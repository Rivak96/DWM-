package com.dwm.cockpit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The floating cockpit pill. Always-on-top handle: drag to move (snaps to the
 * nearest screen edge, position persisted), tap to expand a mini dashboard with
 * clock + favourite apps that open as floating windows over CarPlay.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var root: View? = null
    private var expandedView: View? = null
    private var clockView: TextView? = null
    private var favRow: LinearLayout? = null
    private var expanded = false

    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val tick = object : Runnable {
        override fun run() {
            clockView?.text = clockFormat.format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun addOverlay() {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Prefs.pillX(this@OverlayService)
            y = Prefs.pillY(this@OverlayService)
        }

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        root = view
        expandedView = view.findViewById(R.id.expanded)
        clockView = view.findViewById(R.id.clock)
        favRow = view.findViewById(R.id.favRow)

        val accent = Prefs.accent(this)
        view.findViewById<TextView>(R.id.handle).setTextColor(accent)

        setupDragAndTap(view.findViewById(R.id.handle))

        view.findViewById<Button>(R.id.btnOpenCockpit).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            collapse()
        }
        view.findViewById<Button>(R.id.btnCollapse).setOnClickListener { collapse() }
        view.findViewById<Button>(R.id.btnStop).setOnClickListener { stopSelf() }
        view.findViewById<Button>(R.id.btnOverlays).setOnClickListener {
            if (OverlayPanelsService.isRunning) OverlayPanelsService.stop(this)
            else OverlayPanelsService.start(this)
            collapse()
        }
        view.findViewById<Button>(R.id.btnRaise).setOnClickListener {
            LaunchEngine.raiseWindows(this, Prefs.panels(this))
            collapse()
        }
        val btnMute = view.findViewById<Button>(R.id.btnMute)
        btnMute.text = if (Prefs.muteOverlays(this)) "Muted ✓" else "Mute"
        btnMute.setOnClickListener {
            val newVal = !Prefs.muteOverlays(this)
            Prefs.setMuteOverlays(this, newVal)
            btnMute.text = if (newVal) "Muted ✓" else "Mute"
            // reapply to running overlay panels
            if (OverlayPanelsService.isRunning) {
                OverlayPanelsService.stop(this)
                Handler(Looper.getMainLooper()).postDelayed({ OverlayPanelsService.start(this) }, 400)
            }
        }

        Ui.skin(this, view)
        collapse()
        windowManager.addView(view, params)
    }

    private fun expand() {
        expanded = true
        buildFavRow()
        expandedView?.visibility = View.VISIBLE
        relayout()
    }

    private fun collapse() {
        expanded = false
        expandedView?.visibility = View.GONE
        relayout()
    }

    /** Favourite apps as one-tap floating windows — glance without leaving CarPlay. */
    private fun buildFavRow() {
        val row = favRow ?: return
        row.removeAllViews()
        val size = Ui.dp(this, 46)
        val gap = Ui.dp(this, 10)
        for (pkg in Apps.effectiveFavorites(this).take(5)) {
            val icon = Apps.icon(this, pkg) ?: continue
            val iv = ImageView(this)
            iv.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
            iv.setImageDrawable(icon)
            iv.setOnClickListener {
                val s = LaunchEngine.displaySize(this)
                LaunchEngine.launchWindow(this, pkg, Rect(s.x / 5, s.y / 5, s.x * 4 / 5, s.y * 4 / 5))
                collapse()
            }
            row.addView(iv)
        }
    }

    private fun relayout() {
        root?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun setupDragAndTap(handle: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 12 || abs(dy) > 12) dragging = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    relayout()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        if (expanded) collapse() else expand()
                    } else {
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val v = root ?: return
        val dm = resources.displayMetrics
        val margin = Ui.dp(this, 8)
        val w = if (v.width > 0) v.width else Ui.dp(this, 90)
        params.x = if (params.x + w / 2 < dm.widthPixels / 2) margin
        else (dm.widthPixels - w - margin).coerceAtLeast(margin)
        params.y = params.y.coerceIn(0, (dm.heightPixels - Ui.dp(this, 48)).coerceAtLeast(0))
        relayout()
        Prefs.setPillPos(this, params.x, params.y)
    }

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
            this, 0, Intent(this, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("DWM Cockpit running")
            .setContentText("Tap to open. Drag the pill to move it.")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
    }

    companion object {
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            val i = Intent(ctx, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }
}
