package com.dwm.cockpit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A slim strip of vehicle state that floats over CarPlay: gear, speed, indicators.
 *
 * Separate from [OverlayPanelsService] on purpose. That one is tied to the Panel
 * model and can only be rebuilt by stopping and restarting the whole service —
 * fine for a layout of cameras and gauges, useless for something whose entire job
 * is to change several times a second.
 *
 * Two decisions worth keeping:
 *
 *  - **`FLAG_NOT_TOUCHABLE`.** The strip is passive, and CarPlay is the thing the
 *    user is actually trying to touch. Without this flag a transparent bar across
 *    the top of the screen silently eats taps meant for the app underneath, which
 *    is the sort of bug that gets blamed on the head unit.
 *  - **Polling, not callbacks.** Same reasoning as the dashboard: [CarInfo]'s
 *    callbacks land on binder threads at the CAN box's rate, and a view can only
 *    be touched on the main thread. A 500ms tick on the main handler is simpler
 *    than marshalling every push and is well inside what the eye needs here.
 */
class VehicleStripService : Service() {

    private var wm: WindowManager? = null
    private var root: View? = null
    private var gearView: TextView? = null
    private var speedView: TextView? = null
    private var leftArrow: TextView? = null
    private var rightArrow: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var blink = true

    override fun onBind(intent: Intent?): IBinder? = null

    /** Match the launcher's interface scale, or this renders at a different size
     *  to everything else DWM draws. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Scale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(NOTIF_ID, buildNotification())
        // If this was started from boot rather than from the launcher, nothing has
        // bound the CAN service yet and every reading would be a dash.
        CarInfo.start(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addStrip()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun addStrip() {
        val pad = Ui.dp(this, 8)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, Ui.dp(this@VehicleStripService, 3), pad, Ui.dp(this@VehicleStripService, 3))
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(this@VehicleStripService, 9).toFloat()
                setColor(Color.argb(170, 0, 0, 0))
            }
        }

        fun label(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        leftArrow = label(13f, Color.argb(40, 255, 255, 255)).apply { text = "◀" }
        gearView = label(14f, Ui.accent(this), bold = true).apply { text = "—" }
        speedView = label(15f, Color.WHITE).apply { text = "—" }
        rightArrow = label(13f, Color.argb(40, 255, 255, 255)).apply { text = "▶" }

        val gap = Ui.dp(this, 7)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = gap }

        row.addView(leftArrow)
        row.addView(gearView, lp)
        row.addView(speedView, LinearLayout.LayoutParams(lp))
        row.addView(rightArrow, LinearLayout.LayoutParams(lp))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = Ui.dp(this@VehicleStripService, 6)
        }

        runCatching { wm?.addView(row, params) }.onSuccess { root = row }
    }

    private val tick = object : Runnable {
        override fun run() {
            blink = !blink
            gearView?.text = CarInfo.gearName(CarInfo.gear)
            val s = CarInfo.speedKmh
            speedView?.text = if (s == null) "—" else "$s km/h"

            val turn = CarInfo.turnSignal ?: 0
            val on = Color.argb(255, 255, 159, 10)
            val off = Color.argb(40, 255, 255, 255)
            leftArrow?.setTextColor(if (blink && (turn == 2 || turn == 3)) on else off)
            rightArrow?.setTextColor(if (blink && (turn == 1 || turn == 3)) on else off)

            handler.postDelayed(this, 500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(tick)
        root?.let { v -> runCatching { wm?.removeView(v) } }
        root = null
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
            this, 3, Intent(this, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("DWM vehicle strip")
            .setContentText("Gear and speed over your apps.")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1003

        /** Process-local liveness — a saved preference goes stale when the system
         *  kills a service, because onDestroy never runs. */
        @Volatile
        var isRunning: Boolean = false

        fun start(ctx: Context) {
            runCatching {
                val i = Intent(ctx, VehicleStripService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, VehicleStripService::class.java)) }
        }
    }
}
