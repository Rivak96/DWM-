package com.dwm.cockpit

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Mirrors a chosen app's latest notification (e.g. TPMS pressures) as a drawn
 * overlay that stays above CarPlay. Updates live from [NotifStore].
 */
class NotifPanel(context: Context, private val pkg: String) : FrameLayout(context) {

    private val titleView = TextView(context)
    private val bodyView = TextView(context)
    private val listener: () -> Unit = { post { refresh() } }

    init {
        val th = Ui.th(context)
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 14), Ui.dp(context, 10))
        }
        titleView.apply {
            textSize = 11f
            setTextColor(th.dim)
            maxLines = 1
        }
        bodyView.apply {
            textSize = 18f
            setTextColor(th.text)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            maxLines = 4
        }
        wrap.addView(titleView)
        wrap.addView(bodyView)
        addView(wrap, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        setOnClickListener {
            if (!NotifStore.accessGranted(context)) openAccessSettings()
            else LaunchEngine.launchFullscreen(context, pkg)
        }
    }

    private fun openAccessSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun refresh() {
        val appLabel = Apps.label(context, pkg)
        if (!NotifStore.accessGranted(context)) {
            titleView.text = appLabel
            bodyView.text = "Tap: allow DWM notification access"
            return
        }
        val item = NotifStore.get(pkg)
        if (item == null) {
            titleView.text = appLabel
            bodyView.text = "Waiting for a notification…"
        } else {
            titleView.text = if (item.title.isNotBlank()) item.title else appLabel
            bodyView.text = if (item.text.isNotBlank()) item.text else item.title
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotifStore.addListener(listener)
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        NotifStore.removeListener(listener)
    }
}
