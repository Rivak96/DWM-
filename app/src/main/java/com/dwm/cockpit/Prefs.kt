package com.dwm.cockpit

import android.content.Context
import org.json.JSONArray

/** All persisted launcher state (SharedPreferences + small JSON blobs). */
object Prefs {
    private const val NAME = "dwm"
    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun panels(c: Context): List<Panel> {
        val s = sp(c).getString("panels", null) ?: return migrateOldTiles(c)
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { Panel.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun savePanels(c: Context, panels: List<Panel>) {
        val a = JSONArray()
        panels.forEach { a.put(it.toJson()) }
        sp(c).edit().putString("panels", a.toString()).apply()
    }

    /** Old versions stored app-only "tiles"; convert them to APP panels once. */
    private fun migrateOldTiles(c: Context): List<Panel> {
        val s = sp(c).getString("tiles", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Panel(
                    PanelType.APP,
                    o.getDouble("l").toFloat(), o.getDouble("t").toFloat(),
                    o.getDouble("r").toFloat(), o.getDouble("b").toFloat(),
                    pkg = o.getString("pkg"),
                    label = o.optString("label", o.getString("pkg"))
                )
            }
        }.getOrDefault(emptyList())
    }

    fun favorites(c: Context): List<String> {
        val s = sp(c).getString("favs", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun saveFavorites(c: Context, list: List<String>) {
        sp(c).edit().putString("favs", JSONArray(list).toString()).apply()
    }

    fun autoLoad(c: Context) = sp(c).getBoolean("autoload", true)
    fun setAutoLoad(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoload", v).apply()

    fun overlayOnStart(c: Context) = sp(c).getBoolean("overlay_start", false)
    fun setOverlayOnStart(c: Context, v: Boolean) = sp(c).edit().putBoolean("overlay_start", v).apply()

    fun carplay(c: Context): String? = sp(c).getString("carplay", null)
    fun setCarplay(c: Context, pkg: String?) = sp(c).edit().putString("carplay", pkg).apply()

    fun wallpaper(c: Context) = sp(c).getInt("wall", 0)
    fun setWallpaper(c: Context, i: Int) = sp(c).edit().putInt("wall", i).apply()

    fun obdMac(c: Context): String? = sp(c).getString("obd_mac", null)
    fun obdName(c: Context): String? = sp(c).getString("obd_name", null)
    fun setObd(c: Context, mac: String?, name: String?) {
        sp(c).edit().putString("obd_mac", mac).putString("obd_name", name).apply()
    }

    /** Raw panels JSON — used to detect layout changes cheaply. */
    fun panelsRaw(c: Context): String? = sp(c).getString("panels", null)

    fun accent(c: Context) = sp(c).getInt("accent", 0xFF3E6AE1.toInt())
    fun setAccent(c: Context, color: Int) = sp(c).edit().putInt("accent", color).apply()

    fun wallpaperUri(c: Context): String? = sp(c).getString("wall_uri", null)
    fun setWallpaperUri(c: Context, uri: String?) = sp(c).edit().putString("wall_uri", uri).apply()

    fun pillX(c: Context) = sp(c).getInt("pill_x", 24)
    fun pillY(c: Context) = sp(c).getInt("pill_y", 160)
    fun setPillPos(c: Context, x: Int, y: Int) =
        sp(c).edit().putInt("pill_x", x).putInt("pill_y", y).apply()

    fun dockCollapsed(c: Context) = sp(c).getBoolean("dock_collapsed", false)
    fun setDockCollapsed(c: Context, v: Boolean) =
        sp(c).edit().putBoolean("dock_collapsed", v).apply()

    /** Global text scale: 0.85 compact · 1.0 normal · 1.15 large. */
    fun fontScale(c: Context) = sp(c).getFloat("font_scale", 1.0f)
    fun setFontScale(c: Context, v: Float) = sp(c).edit().putFloat("font_scale", v).apply()

    /**
     * Global interface scale — a density multiplier applied to every DWM screen.
     * The deck reports a density meant for a phone-sized panel, so at 1.0 every
     * control eats far more of the 13" glass than it needs to. Below 1.0 shrinks
     * dp *and* sp together, so the whole UI gets smaller and more fits on screen.
     * 0.7 tiny · 0.8 compact (default) · 0.9 cosy · 1.0 stock.
     */
    fun uiScale(c: Context) = sp(c).getFloat("ui_scale", 0.8f)
    fun setUiScale(c: Context, v: Float) = sp(c).edit().putFloat("ui_scale", v).apply()

    /** Camera picture fit: 0 = fill (crop to panel) · 1 = fit (letterbox) ·
     *  2 = stretch (old behaviour — distorts). */
    fun camFit(c: Context) = sp(c).getInt("cam_fit", 0)
    fun setCamFit(c: Context, v: Int) = sp(c).edit().putInt("cam_fit", v).apply()

    /** Camera day/night tone: 0 = auto · 1 = force day · 2 = force night. */
    fun camDayNight(c: Context) = sp(c).getInt("cam_dn", 0)
    fun setCamDayNight(c: Context, v: Int) = sp(c).edit().putInt("cam_dn", v).apply()

    /** Manual exposure trim on top of the day/night profile, in steps of ~8%.
     *  Range -4..+4, 0 = profile default. */
    fun camTrim(c: Context) = sp(c).getInt("cam_trim", 0)
    fun setCamTrim(c: Context, v: Int) = sp(c).edit().putInt("cam_trim", v.coerceIn(-4, 4)).apply()

    /** Theme preset: 0 = Tesla (gray, default) · 1 = Midnight (black) · 2 = Light. */
    /** 0 Tesla charcoal · 1 Midnight · 2 Light. Defaults to Midnight since the
     *  SYNC-style home is built against a near-black reference; Settings → Display
     *  switches it back to the charcoal Tesla preset. */
    fun theme(c: Context) = sp(c).getInt("theme", 1)
    fun setTheme(c: Context, v: Int) = sp(c).edit().putInt("theme", v).apply()

    fun topCollapsed(c: Context) = sp(c).getBoolean("top_collapsed", false)
    fun setTopCollapsed(c: Context, v: Boolean) =
        sp(c).edit().putBoolean("top_collapsed", v).apply()

    /** Whether the always-on-top overlay panels are active. */
    fun overlaysOn(c: Context) = sp(c).getBoolean("overlays_on", false)
    fun setOverlaysOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("overlays_on", v).apply()

    /**
     * Overlay edit mode. Off (default) = panels are pure content: no move/resize
     * grips, no rotate button, no card frame eating the corners. Turn it on only
     * while rearranging, then turn it back off.
     */
    fun overlayEdit(c: Context) = sp(c).getBoolean("overlay_edit", false)
    fun setOverlayEdit(c: Context, v: Boolean) = sp(c).edit().putBoolean("overlay_edit", v).apply()

    /** Freeform caption-bar compensation in dp: the system draws a title bar
     *  inside each freeform window, cropping app content (e.g. CarPlay's bottom
     *  controls). We grow window bounds by this much to compensate. */
    fun captionComp(c: Context) = sp(c).getInt("caption_comp", 32)
    fun setCaptionComp(c: Context, dp: Int) = sp(c).edit().putInt("caption_comp", dp).apply()

    /** Cockpit mode: 0 = Dashboard (panels drawn on the home canvas) ·
     *  1 = Overlay (one fullscreen base app + panels floating on top). */
    fun mode(c: Context) = sp(c).getInt("mode", 0)
    fun setMode(c: Context, v: Int) = sp(c).edit().putInt("mode", v).apply()

    /** Show the big favourites grid on the home canvas. */
    fun showFavGrid(c: Context) = sp(c).getBoolean("fav_grid", true)
    fun setShowFavGrid(c: Context, v: Boolean) = sp(c).edit().putBoolean("fav_grid", v).apply()

    /** Re-raise app windows above the fullscreen base app every few seconds
     *  (freeform windows sink when the fullscreen app is tapped). */
    fun pinWindows(c: Context) = sp(c).getBoolean("pin_windows", false)
    fun setPinWindows(c: Context, v: Boolean) = sp(c).edit().putBoolean("pin_windows", v).apply()

    /** GitHub "owner/repo" hosting version.json + release APKs (auto-update). */
    fun updateRepo(c: Context): String = sp(c).getString("update_repo", "")!!.trim()
    fun setUpdateRepo(c: Context, v: String) = sp(c).edit().putString("update_repo", v.trim()).apply()

    fun autoUpdate(c: Context) = sp(c).getBoolean("auto_update", false)
    fun setAutoUpdate(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_update", v).apply()

    /** Mute audio from DWM's own overlay panels (web/media) so they never
     *  interrupt CarPlay music. Default on. */
    fun muteOverlays(c: Context) = sp(c).getBoolean("mute_overlays", true)
    fun setMuteOverlays(c: Context, v: Boolean) = sp(c).edit().putBoolean("mute_overlays", v).apply()

    /** The slim vehicle strip that floats over CarPlay: gear, speed, indicators. */
    fun vehicleStrip(c: Context) = sp(c).getBoolean("vehicle_strip", false)
    fun setVehicleStrip(c: Context, v: Boolean) = sp(c).edit().putBoolean("vehicle_strip", v).apply()

    /**
     * Feed the dashboard invented vehicle data. Purely a bench aid: the deck can't
     * be driven from a desk, and with no CAN data every tile reads "—", so there is
     * otherwise no way to see whether the layout is right before a drive. Never
     * touches the CAN service or the real [CarInfo] fields — it is read at the
     * snapshot boundary only. Off by default, and it says so loudly on screen.
     */
    fun demoData(c: Context) = sp(c).getBoolean("demo_data", false)
    fun setDemoData(c: Context, v: Boolean) = sp(c).edit().putBoolean("demo_data", v).apply()
}
