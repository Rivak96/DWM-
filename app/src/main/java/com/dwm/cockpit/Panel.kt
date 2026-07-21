package com.dwm.cockpit

import org.json.JSONObject

/** The kinds of thing a layout cell can be. */
enum class PanelType { APP, WEB, HTML, IMAGE, CLOCK, SPEED, OBD, CAMERA }

/**
 * One cell in the cockpit. Position is stored as screen fractions (0..1) so the
 * layout survives resolution changes. APP panels are launched as real freeform
 * windows; every other type is drawn by DWM inside its own screen.
 */
data class Panel(
    val type: PanelType,
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    val pkg: String? = null,     // APP target, or CAMERA fallback app
    val label: String? = null,   // display label
    val url: String? = null,     // WEB url / IMAGE uri
    val html: String? = null,    // HTML content
    val metric: String? = null,  // OBD metric key: rpm, coolant, speed, throttle, map, intake
    val camId: String? = null,   // CAMERA2 camera id (null = auto)
    val fullscreen: Boolean = false, // APP panel opens fullscreen (base app, e.g. CarPlay)
    val rotation: Int = 0        // CAMERA preview rotation: 0/90/180/270
) {
    fun withBounds(l: Float, t: Float, r: Float, b: Float) = copy(l = l, t = t, r = r, b = b)

    /** True if this panel is launched as a freeform app window (vs drawn by DWM).
     *  CAMERA panels are ALWAYS drawn now (Camera2 overlay, with the app as a
     *  tap-to-open fallback) so they persist size/position, never grab audio and
     *  never sink behind the fullscreen app. */
    fun isWindowedApp(): Boolean =
        type == PanelType.APP && pkg != null && !fullscreen

    /** True if this APP panel opens as the fullscreen base app. */
    fun isFullscreenApp(): Boolean = type == PanelType.APP && pkg != null && fullscreen

    /** True if DWM draws this panel itself (gauges, web, camera-live, clock…). */
    fun isDrawn(): Boolean = !isWindowedApp() && !isFullscreenApp() && type != PanelType.APP

    fun displayLabel(): String = when (type) {
        PanelType.APP -> (label ?: pkg ?: "App") + if (fullscreen) " · FULL" else ""
        PanelType.WEB -> "Web · ${label ?: url ?: ""}"
        PanelType.HTML -> "HTML · ${label ?: "custom"}"
        PanelType.IMAGE -> "Image"
        PanelType.CLOCK -> "Clock"
        PanelType.SPEED -> "Speed (GPS)"
        PanelType.OBD -> "OBD · ${Obd.metricName(metric)}"
        PanelType.CAMERA -> "Camera · ${camId ?: "auto"}"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("l", l.toDouble()); put("t", t.toDouble())
        put("r", r.toDouble()); put("b", b.toDouble())
        pkg?.let { put("pkg", it) }
        label?.let { put("label", it) }
        url?.let { put("url", it) }
        html?.let { put("html", it) }
        metric?.let { put("metric", it) }
        camId?.let { put("camId", it) }
        if (fullscreen) put("fs", true)
        if (rotation != 0) put("rot", rotation)
    }

    companion object {
        fun fromJson(o: JSONObject): Panel = Panel(
            type = runCatching { PanelType.valueOf(o.getString("type")) }.getOrDefault(PanelType.APP),
            l = o.getDouble("l").toFloat(),
            t = o.getDouble("t").toFloat(),
            r = o.getDouble("r").toFloat(),
            b = o.getDouble("b").toFloat(),
            pkg = o.optStringOrNull("pkg"),
            label = o.optStringOrNull("label"),
            url = o.optStringOrNull("url"),
            html = o.optStringOrNull("html"),
            metric = o.optStringOrNull("metric"),
            camId = o.optStringOrNull("camId"),
            fullscreen = o.optBoolean("fs", false),
            rotation = o.optInt("rot", 0)
        )
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
