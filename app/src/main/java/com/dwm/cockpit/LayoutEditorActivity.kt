package com.dwm.cockpit

import android.app.Activity
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max

/**
 * Template-driven layout editor. Pick a preset template, tap a "+" slot to drop
 * in an app/panel, drag to nudge (snaps to a grid), amber corner to resize,
 * Save — that becomes the default layout that auto-loads on start.
 */
class LayoutEditorActivity : DwmActivity() {

    private class Slot(
        var panel: Panel?,          // null = empty ("+")
        val view: FrameLayout,
        val iconView: ImageView,
        val labelView: TextView
    )

    private lateinit var inner: FrameLayout
    private val slots = ArrayList<Slot>()
    private var pendingTarget: Slot? = null
    private var pendingIsNewFree = false
    private var pendingKind: PanelType = PanelType.APP
    private var minTile = 0
    private var slop = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_layout_editor)

        inner = findViewById(R.id.previewInner)
        minTile = dp(56)
        slop = dp(6)

        findViewById<View>(R.id.close).setOnClickListener { finish() }
        findViewById<View>(R.id.templates).setOnClickListener { chooseTemplate() }
        findViewById<View>(R.id.addApp).setOnClickListener { chooseType(null, newFree = true) }
        findViewById<View>(R.id.clearAll).setOnClickListener { confirmClear() }
        findViewById<View>(R.id.save).setOnClickListener { save() }

        findViewById<View>(R.id.previewOuter).post { sizePreviewToScreen(); loadInitial() }
        Ui.themeWindow(this)
        Ui.skin(this, findViewById(android.R.id.content))
    }

    private fun sizePreviewToScreen() {
        val outer = findViewById<View>(R.id.previewOuter)
        val s = LaunchEngine.displaySize(this)
        val ar = s.x.toFloat() / s.y
        var w = outer.width
        var h = (w / ar).toInt()
        if (h > outer.height) { h = outer.height; w = (h * ar).toInt() }
        inner.layoutParams = inner.layoutParams.apply { width = w; height = h }
        inner.requestLayout()
    }

    private fun loadInitial() {
        inner.post {
            val saved = Prefs.panels(this)
            if (saved.isNotEmpty()) {
                for (p in saved) createSlot(RectF(p.l, p.t, p.r, p.b), p)
            } else {
                applyTemplate(Templates.ALL[3]) // 2x2 as a friendly starting point
            }
        }
    }

    // ---- templates -------------------------------------------------------

    private fun chooseTemplate() {
        val names = Templates.ALL.map { it.name }.toTypedArray()
        Ui.dialog(this)
            .setTitle("Choose a layout")
            .setItems(names) { _, which -> applyTemplate(Templates.ALL[which]) }
            .show()
    }

    private fun applyTemplate(t: Templates.Template) {
        val existing = slots.mapNotNull { it.panel } // keep assigned panels by order
        slots.toList().forEach { inner.removeView(it.view) }
        slots.clear()
        t.slots.forEachIndexed { i, rect -> createSlot(rect, existing.getOrNull(i)) }
    }

    private fun confirmClear() {
        Ui.dialog(this)
            .setTitle("Clear layout?")
            .setMessage("Removes every slot from the editor. Nothing is saved until you press Save.")
            .setPositiveButton("Clear") { _, _ ->
                slots.toList().forEach { inner.removeView(it.view) }
                slots.clear()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- add / configure -------------------------------------------------

    private fun chooseType(target: Slot?, newFree: Boolean) {
        val items = arrayOf(
            "Android app", "Camera app (e.g. AUX)", "Web dashboard", "Custom HTML",
            "Image", "Clock", "Speed (GPS)", "OBD gauge", "Live camera (Camera2)",
            "App notification (TPMS, etc.)"
        )
        Ui.dialog(this)
            .setTitle("Add to slot")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickApp(target, newFree, PanelType.APP)
                    1 -> pickApp(target, newFree, PanelType.CAMERA)
                    2 -> configWeb(target, newFree)
                    3 -> configHtml(target, newFree)
                    4 -> pickImage(target, newFree)
                    5 -> assign(target, newFree, Panel(PanelType.CLOCK, 0f, 0f, 0f, 0f))
                    6 -> assign(target, newFree, Panel(PanelType.SPEED, 0f, 0f, 0f, 0f))
                    7 -> configObd(target, newFree)
                    8 -> configCameraLive(target, newFree)
                    9 -> pickApp(target, newFree, PanelType.NOTIF)
                }
            }
            .show()
    }

    private fun pickApp(target: Slot?, newFree: Boolean, kind: PanelType) {
        pendingTarget = target; pendingIsNewFree = newFree; pendingKind = kind
        startActivityForResult(
            Intent(this, AppDrawerActivity::class.java).putExtra(AppDrawerActivity.EXTRA_PICK, true),
            REQ_APP
        )
    }

    private fun pickImage(target: Slot?, newFree: Boolean) {
        pendingTarget = target; pendingIsNewFree = newFree
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(i, REQ_IMAGE)
    }

    private fun configWeb(target: Slot?, newFree: Boolean) {
        val input = EditText(this).apply {
            hint = "https://homeassistant.local:8123"
            setText(target?.panel?.url ?: "")
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        Ui.dialog(this).setTitle("Web dashboard URL").setView(input)
            .setPositiveButton("OK") { _, _ ->
                val url = normalizeUrl(input.text.toString().trim())
                if (url.isNotEmpty())
                    assign(target, newFree, Panel(PanelType.WEB, 0f, 0f, 0f, 0f, url = url, label = hostOf(url)))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun configHtml(target: Slot?, newFree: Boolean) {
        val input = EditText(this).apply {
            setText(target?.panel?.html ?: "<h2 style='color:#F2F2F2'>Hello DWM</h2>")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
        }
        Ui.dialog(this).setTitle("Custom HTML").setView(input)
            .setPositiveButton("OK") { _, _ ->
                assign(target, newFree, Panel(PanelType.HTML, 0f, 0f, 0f, 0f, html = input.text.toString(), label = "custom"))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun configObd(target: Slot?, newFree: Boolean) {
        val names = Obd.METRICS.map { it.third }.toTypedArray()
        Ui.dialog(this).setTitle("OBD metric")
            .setItems(names) { _, which ->
                assign(target, newFree, Panel(PanelType.OBD, 0f, 0f, 0f, 0f, metric = Obd.METRICS[which].first))
            }
            .show()
    }

    private fun configCameraLive(target: Slot?, newFree: Boolean) {
        val input = EditText(this).apply {
            hint = "camera id (0, 1, 2…)"
            setText(target?.panel?.camId ?: "0")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        Ui.dialog(this).setTitle("Live camera (Camera2) id").setView(input)
            .setPositiveButton("OK") { _, _ ->
                val id = input.text.toString().trim().ifBlank { "0" }
                assign(target, newFree, Panel(PanelType.CAMERA, 0f, 0f, 0f, 0f, camId = id))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun assign(target: Slot?, newFree: Boolean, panel: Panel) {
        if (target != null) {
            target.panel = panel
            styleSlot(target)
        } else if (newFree) {
            val idx = slots.size
            val off = 0.06f * idx
            createSlot(RectF(0.1f + off, 0.1f + off, 0.55f + off, 0.6f + off), panel)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) { pendingTarget = null; return }
        when (requestCode) {
            REQ_APP -> {
                val pkg = data.getStringExtra(AppDrawerActivity.EXTRA_PKG) ?: return
                val label = Apps.label(this, pkg)
                val panel = when (pendingKind) {
                    PanelType.CAMERA -> Panel(PanelType.CAMERA, 0f, 0f, 0f, 0f, pkg = pkg, label = label)
                    PanelType.NOTIF -> Panel(PanelType.NOTIF, 0f, 0f, 0f, 0f, pkg = pkg, label = label)
                    else -> Panel(PanelType.APP, 0f, 0f, 0f, 0f, pkg = pkg, label = label)
                }
                assign(pendingTarget, pendingIsNewFree, panel)
            }
            REQ_IMAGE -> {
                val uri = data.data ?: return
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                assign(pendingTarget, pendingIsNewFree, Panel(PanelType.IMAGE, 0f, 0f, 0f, 0f, url = uri.toString()))
            }
        }
        pendingTarget = null; pendingIsNewFree = false
    }

    // ---- slot views ------------------------------------------------------

    private fun createSlot(frac: RectF, panel: Panel?) {
        val cw = inner.width; val ch = inner.height
        val w = ((frac.right - frac.left) * cw).toInt().coerceIn(minTile, cw.coerceAtLeast(minTile))
        val h = ((frac.bottom - frac.top) * ch).toInt().coerceIn(minTile, ch.coerceAtLeast(minTile))
        val x = (frac.left * cw).toInt().coerceIn(0, (cw - w).coerceAtLeast(0))
        val y = (frac.top * ch).toInt().coerceIn(0, (ch - h).coerceAtLeast(0))

        val tile = FrameLayout(this).apply { background = Ui.slotBg(this@LayoutEditorActivity) }
        Ui.roundify(tile, 12)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            visibility = View.GONE
        }
        val labelView = TextView(this).apply { gravity = Gravity.CENTER }
        content.addView(iconView)
        content.addView(labelView)
        tile.addView(
            content,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val handle = View(this).apply { setBackgroundResource(R.drawable.handle_bg) }
        tile.addView(handle, FrameLayout.LayoutParams(dp(30), dp(30)).apply { gravity = Gravity.BOTTOM or Gravity.END })

        val lp = FrameLayout.LayoutParams(w, h).apply { leftMargin = x; topMargin = y }
        inner.addView(tile, lp)

        val slot = Slot(panel, tile, iconView, labelView)
        styleSlot(slot)
        makeMovable(tile, slot)
        makeResizable(handle, tile)
        slots.add(slot)
    }

    private fun styleSlot(slot: Slot) {
        val p = slot.panel
        if (p == null) {
            slot.iconView.visibility = View.GONE
            slot.labelView.text = "＋"
            slot.labelView.textSize = 28f
            slot.labelView.setTextColor(Ui.accent(this))
        } else {
            val icon = p.pkg?.let { Apps.icon(this, it) }
            if (icon != null) {
                slot.iconView.setImageDrawable(icon)
                slot.iconView.visibility = View.VISIBLE
            } else {
                slot.iconView.visibility = View.GONE
            }
            slot.labelView.text = p.displayLabel()
            slot.labelView.textSize = 11f
            // the editor preview is always dark, so slot labels stay light
            slot.labelView.setTextColor(0xFFF2F2F2.toInt())
        }
    }

    private fun onTileTapped(slot: Slot) {
        val p = slot.panel
        if (p == null) {
            chooseType(slot, newFree = false)
            return
        }
        val items = when {
            p.type == PanelType.APP -> arrayOf(
                "Reconfigure",
                if (p.fullscreen) "Open as window instead" else "Open FULLSCREEN (base app)",
                "Clear slot", "Remove slot"
            )
            p.type == PanelType.CAMERA -> arrayOf(
                "Reconfigure", "Rotate 90° (now ${p.rotation}°)", "Clear slot", "Remove slot"
            )
            else -> arrayOf("Reconfigure", "Clear slot", "Remove slot")
        }

        Ui.dialog(this)
            .setTitle(p.displayLabel())
            .setItems(items) { _, which ->
                when {
                    p.type == PanelType.APP -> when (which) {
                        0 -> reconfigure(slot)
                        1 -> { slot.panel = p.copy(fullscreen = !p.fullscreen); styleSlot(slot) }
                        2 -> { slot.panel = null; styleSlot(slot) }
                        3 -> { inner.removeView(slot.view); slots.remove(slot) }
                    }
                    p.type == PanelType.CAMERA -> when (which) {
                        0 -> reconfigure(slot)
                        1 -> { slot.panel = p.copy(rotation = (p.rotation + 90) % 360); styleSlot(slot); Toast.makeText(this, "Rotation ${(p.rotation + 90) % 360}° — Save to apply", Toast.LENGTH_SHORT).show() }
                        2 -> { slot.panel = null; styleSlot(slot) }
                        3 -> { inner.removeView(slot.view); slots.remove(slot) }
                    }
                    else -> when (which) {
                        0 -> reconfigure(slot)
                        1 -> { slot.panel = null; styleSlot(slot) }
                        2 -> { inner.removeView(slot.view); slots.remove(slot) }
                    }
                }
            }
            .show()
    }

    private fun reconfigure(slot: Slot) {
        when (slot.panel?.type) {
            PanelType.APP -> pickApp(slot, false, PanelType.APP)
            PanelType.NOTIF -> pickApp(slot, false, PanelType.NOTIF)
            PanelType.CAMERA -> if (slot.panel?.camId != null) configCameraLive(slot, false) else pickApp(slot, false, PanelType.CAMERA)
            PanelType.WEB -> configWeb(slot, false)
            PanelType.HTML -> configHtml(slot, false)
            PanelType.IMAGE -> pickImage(slot, false)
            PanelType.OBD -> configObd(slot, false)
            else -> Toast.makeText(this, "Nothing to configure", Toast.LENGTH_SHORT).show()
        }
    }

    private fun gridStep() = (inner.width / 48).coerceAtLeast(8)

    private fun makeMovable(tile: FrameLayout, slot: Slot) {
        var downX = 0f; var downY = 0f; var startL = 0; var startT = 0
        var moved = false; var downAt = 0L
        tile.setOnTouchListener { _, e ->
            val lp = tile.layoutParams as FrameLayout.LayoutParams
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startL = lp.leftMargin; startT = lp.topMargin
                    moved = false; downAt = System.currentTimeMillis(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    lp.leftMargin = (startL + dx).coerceIn(0, max(0, inner.width - lp.width))
                    lp.topMargin = (startT + dy).coerceIn(0, max(0, inner.height - lp.height))
                    tile.layoutParams = lp; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && System.currentTimeMillis() - downAt < 300) {
                        onTileTapped(slot)
                    } else if (moved) {
                        val g = gridStep()
                        lp.leftMargin = ((lp.leftMargin + g / 2) / g * g).coerceIn(0, max(0, inner.width - lp.width))
                        lp.topMargin = ((lp.topMargin + g / 2) / g * g).coerceIn(0, max(0, inner.height - lp.height))
                        tile.layoutParams = lp
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun makeResizable(handle: View, tile: FrameLayout) {
        var downX = 0f; var downY = 0f; var startW = 0; var startH = 0
        handle.setOnTouchListener { _, e ->
            val lp = tile.layoutParams as FrameLayout.LayoutParams
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startW = lp.width; startH = lp.height; true }
                MotionEvent.ACTION_MOVE -> {
                    val nw = (startW + (e.rawX - downX)).toInt()
                    val nh = (startH + (e.rawY - downY)).toInt()
                    lp.width = nw.coerceIn(minTile, inner.width - lp.leftMargin)
                    lp.height = nh.coerceIn(minTile, inner.height - lp.topMargin)
                    tile.layoutParams = lp; true
                }
                MotionEvent.ACTION_UP -> {
                    val g = gridStep()
                    lp.width = ((lp.width + g / 2) / g * g).coerceIn(minTile, inner.width - lp.leftMargin)
                    lp.height = ((lp.height + g / 2) / g * g).coerceIn(minTile, inner.height - lp.topMargin)
                    tile.layoutParams = lp
                    true
                }
                else -> true
            }
        }
    }

    private fun save() {
        val cw = inner.width.toFloat(); val ch = inner.height.toFloat()
        if (cw <= 0 || ch <= 0) return
        val out = slots.filter { it.panel != null }.map { s ->
            val lp = s.view.layoutParams as FrameLayout.LayoutParams
            s.panel!!.withBounds(
                lp.leftMargin / cw, lp.topMargin / ch,
                (lp.leftMargin + lp.width) / cw, (lp.topMargin + lp.height) / ch
            )
        }
        Prefs.savePanels(this, out)
        Toast.makeText(this, "Saved as default — auto-loads on start (${out.size} panels)", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun normalizeUrl(u: String): String =
        if (u.isEmpty() || u.contains("://")) u else "http://$u"

    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_APP = 101
        private const val REQ_IMAGE = 102
    }
}
