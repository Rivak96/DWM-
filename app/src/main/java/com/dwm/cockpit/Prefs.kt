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

    /* ------------------------------------------------------------------ panes */

    /**
     * The cockpit panes.
     *
     * A pane is a slot on the home screen holding an ordered list of sources, and a
     * swipe cycles them. Sources are [Panel]s with their bounds ignored — the pane's
     * geometry supplies those at render time — which is deliberate reuse: the layout
     * editor, `LaunchEngine` and the drawn-panel renderer all already speak [Panel],
     * so a pane source needs no second model and no conversion.
     *
     * Stored as an array of arrays. Empty means "not configured yet", and
     * [defaultPanes] supplies something useful on first run rather than two empty
     * boxes.
     */
    fun panes(c: Context): List<List<Panel>> {
        val s = sp(c).getString("panes", null) ?: return defaultPanes(c)
        return runCatching {
            val outer = JSONArray(s)
            (0 until outer.length()).map { i ->
                val inner = outer.getJSONArray(i)
                (0 until inner.length()).map { j -> Panel.fromJson(inner.getJSONObject(j)) }
            }
        }.getOrDefault(defaultPanes(c))
    }

    fun savePanes(c: Context, panes: List<List<Panel>>) {
        val outer = JSONArray()
        panes.forEach { pane ->
            val inner = JSONArray()
            pane.forEach { inner.put(it.toJson()) }
            outer.put(inner)
        }
        sp(c).edit().putString("panes", outer.toString()).apply()
    }

    /**
     * First run: the camera on the left, the app drawer on the right.
     *
     * Both are drawn by DWM, so the cockpit is full of live content the first time it
     * opens without needing an app to be picked, a permission to be granted or a CAN
     * bus to be talking. The previous default was an empty screen that asked the user
     * to go and configure something, which is why it looked unfinished.
     */
    private fun defaultPanes(c: Context): List<List<Panel>> = listOf(
        listOf(
            Panel(PanelType.CAMERA, 0f, 0f, 1f, 1f, label = "Camera"),
            Panel(PanelType.DRAWER, 0f, 0f, 1f, 1f, label = "Apps")
        ),
        listOf(
            Panel(PanelType.DRAWER, 0f, 0f, 1f, 1f, label = "Apps"),
            Panel(PanelType.CLOCK, 0f, 0f, 1f, 1f, label = "Clock")
        )
    )

    /** How many panes the cockpit is split into. */
    fun paneCount(c: Context) = sp(c).getInt("pane_count", 2).coerceIn(1, 3)
    fun setPaneCount(c: Context, v: Int) =
        sp(c).edit().putInt("pane_count", v.coerceIn(1, 3)).apply()

    /**
     * Where the divider sits, as a fraction of the pane region's width.
     *
     * Clamped well away from the edges: a pane narrower than a quarter of the screen
     * cannot show anything useful, and a freeform window that small is not worth
     * launching an app into.
     */
    fun splitFraction(c: Context) = sp(c).getFloat("split_frac", 0.5f).coerceIn(0.25f, 0.75f)
    fun setSplitFraction(c: Context, v: Float) =
        sp(c).edit().putFloat("split_frac", v.coerceIn(0.25f, 0.75f)).apply()

    /** Which source each pane is currently showing. Survives a restart. */
    fun paneIndex(c: Context, pane: Int) = sp(c).getInt("pane_idx_$pane", 0)
    fun setPaneIndex(c: Context, pane: Int, v: Int) =
        sp(c).edit().putInt("pane_idx_$pane", v).apply()

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

    /** Cockpit Blue — see the note on `Ui.ACCENTS` for why it is not Tesla Blue. */
    fun accent(c: Context) = sp(c).getInt("accent", 0xFF4C8DFF.toInt())
    fun setAccent(c: Context, color: Int) = sp(c).edit().putInt("accent", color).apply()

    fun wallpaperUri(c: Context): String? = sp(c).getString("wall_uri", null)

    /**
     * How far the wallpaper is dimmed behind the cockpit, 0 = untouched, 1 = black.
     *
     * Defaults high on purpose. Every contrast figure in this design was measured
     * against a near-black field; a photograph behind the cards is an unmeasured
     * background, and a bright one turns muted text — which clears 4.5:1 on
     * `#0A0C0F` — into something unreadable in daylight. The slider lets the driver
     * trade that off knowingly rather than by accident.
     */
    fun wallpaperDim(c: Context) = sp(c).getFloat("wall_dim", 0.72f).coerceIn(0f, 1f)
    fun setWallpaperDim(c: Context, v: Float) =
        sp(c).edit().putFloat("wall_dim", v.coerceIn(0f, 1f)).apply()
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
     * 0.7 tiny · 0.8 compact · 0.9 cosy · 1.0 stock (default).
     *
     * **The default was 0.8 and that was the single biggest reason the launcher
     * looked cheap.** `Scale.wrap` multiplies the whole display density by this, so
     * the panel's 1600x1000dp canvas was really being laid out at ~2000x1250dp and
     * every size in the token file arrived 20% smaller than it was written: a 20sp
     * body reached the eye at 16sp, the 13sp floor at 10.4sp. Four releases were
     * spent making text bigger inside a system that was quietly shrinking all of it.
     *
     * At 1.0 the numbers in `DwmTokens`/`DwmType` mean what they say, which is the
     * precondition for an 18sp body floor and 72dp touch targets. The setting stays
     * — someone may still want more on screen — but the design is drawn at 1.0 and
     * that is the only value it is judged at.
     */
    fun uiScale(c: Context) = sp(c).getFloat("ui_scale", 1.0f)
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

    /**
     * Theme preset: 0 Tesla charcoal · 1 Midnight · 2 Light · 3 Cockpit.
     *
     * Cockpit is the default. The three older presets separate a card from its
     * background by tone alone, which measures fine and disappears on this deck's
     * panel; Cockpit adds a gradient and a hairline so the layering survives the
     * hardware. The others stay selectable in Settings → Display.
     */
    fun theme(c: Context) = sp(c).getInt("theme", 3)
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

    /**
     * GitHub "owner/repo" hosting version.json + release APKs (auto-update).
     *
     * Defaulted rather than blank. This is a personal build with exactly one
     * upstream, and a blank default meant a fresh install — or any "clear data" —
     * silently could not see updates at all: `Updater.check` bails before it makes
     * a request and the only clue is a line in Settings → About that nobody has a
     * reason to read.
     *
     * Note the trailing hyphen. The repository really is named `DWM-`; without it
     * the raw URL 404s, which is a very easy thing to type wrong by hand.
     */
    const val DEFAULT_UPDATE_REPO = "Rivak96/DWM-"

    fun updateRepo(c: Context): String =
        sp(c).getString("update_repo", DEFAULT_UPDATE_REPO)!!.trim().ifBlank { DEFAULT_UPDATE_REPO }

    fun setUpdateRepo(c: Context, v: String) = sp(c).edit().putString("update_repo", v.trim()).apply()

    /** On by default: it only ever *offers* an update — Android still shows its own
     *  install confirmation — so the cost of checking is a few KB on start. */
    fun autoUpdate(c: Context) = sp(c).getBoolean("auto_update", true)
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
