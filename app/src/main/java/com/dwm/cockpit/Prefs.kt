package com.dwm.cockpit

import android.content.Context
import org.json.JSONArray

/** All persisted launcher state (SharedPreferences + small JSON blobs). */
object Prefs {
    private const val NAME = "dwm"
    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Everything stored, for [Diagnostics]. Redacted at the point of use, never here —
     *  the store itself must stay the plain truth. */
    fun all(c: Context): Map<String, *> = sp(c).all

    /* ----------------------------------------------------------------- github */

    /**
     * OAuth token for posting diagnostics as a gist, and the account it belongs to.
     *
     * A real credential, and it lives in the same store [Diagnostics] dumps — which is safe
     * only because `VehicleProbe.SENSITIVE` already matches "token", so the key that posts
     * the dump is redacted out of the dump. If this key is ever renamed, it must keep the
     * word `token` in it.
     */
    fun githubToken(c: Context): String? = sp(c).getString("github_token", null)
    fun githubLogin(c: Context): String? = sp(c).getString("github_login", null)

    fun setGithub(c: Context, token: String?, login: String?) =
        sp(c).edit().putString("github_token", token).putString("github_login", login).apply()

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

    /* ------------------------------------------------------------------ stage */

    /**
     * The one app on the stage.
     *
     * Null means no app has ever been chosen, and only then — the stage draws the app
     * grid in that state, which is safe precisely because there is no window in the way
     * yet. Once this is set it stays set, so the deck comes back on a cold boot showing
     * whatever was last on screen rather than a chooser.
     *
     * This replaced the panes store: an array-of-arrays of sources per pane, plus a
     * split fraction, plus a per-pane index. All of it existed to answer "which of
     * several things is each slot showing", a question with one slot no longer has.
     */

    /**
     * Which Camera2 device the side camera opens, or null to auto-detect.
     *
     * Auto-detect prefers EXTERNAL (a wired analog input reports as external on this
     * deck), then BACK, then the first id. That is right often enough that this is
     * only worth setting when a second feed appears — which is what the 360 kit will
     * do. Settings → Vehicle → Cameras lists the ids.
     */
    fun camId(c: Context): String? = sp(c).getString("cam_id", null)
    fun setCamId(c: Context, v: String?) = sp(c).edit().putString("cam_id", v).apply()

    // ---- the 360 quad, in the right rail's reserved top slot ------------------

    /**
     * Show the 360 kit's four cameras on the dashboard.
     *
     * The four AHD fisheyes are multiplexed into **one** CSI input, so this is a single
     * ordinary Camera2 feed carrying a 2x2 quad — not four opens, and not the vendor's
     * stitched bird's-eye, which lives in `cn.cardoor.zt360`'s native libraries with
     * per-vehicle calibration and is not something DWM can reproduce. What lands here is
     * the four raw fisheye views as the hardware tiles them.
     *
     * **Off by default**, and deliberately: at the time of writing the deck decodes
     * `MODE_720P_25FPS` against fixed 1080P cameras, so the feed may be the documented
     * stripe pattern. A dashboard that comes up broken on first install is worse than one
     * that comes up as it always did.
     */
    fun cam360On(c: Context) = sp(c).getBoolean("cam360_on", false)
    fun setCam360On(c: Context, v: Boolean) =
        sp(c).edit().putBoolean("cam360_on", v).apply()

    /**
     * Which Camera2 device carries the quad. Defaults to `"0"`.
     *
     * That default is not a guess: the vendor app's own `TopwayCamera.getSupportCameraId()`
     * returns 0 for TS18 (this deck, matched on `Build.MODEL == "s9863a1h10_Natv"`), and
     * `FourCSICamera.openCamera()` opens index 0 at 1280x720 with four channels enabled.
     * It is still only the *legacy* API's index, which is not guaranteed to equal a Camera2
     * string id — hence a picker, and hence the sizes now printed by
     * [VehicleProbe.cameraInputs]. A configured id that does not exist falls through to
     * [CameraIds.resolve]'s auto-pick, same as [camId].
     */
    fun cam360Id(c: Context): String? = sp(c).getString("cam360_id", "0")
    fun setCam360Id(c: Context, v: String?) = sp(c).edit().putString("cam360_id", v).apply()

    /** Preview rotation for the quad, 0/90/180/270. See [camRotation]. */
    fun cam360Rotation(c: Context) = sp(c).getInt("cam360_rot", 0)
    fun setCam360Rotation(c: Context, v: Int) =
        sp(c).edit().putInt("cam360_rot", ((v % 360) + 360) % 360).apply()

    // ---- the stage: one live app in the home screen's box --------------------

    /** Package hosted in the box, or null for the app grid. See [LaunchEngine.launchInBox]. */
    fun stagePkg(c: Context): String? = sp(c).getString("stage_pkg", null)
    fun setStagePkg(c: Context, v: String?) = sp(c).edit().putString("stage_pkg", v).apply()

    /**
     * Height of the system caption bar to mask, in dp.
     *
     * **Nudged, never guessed.** v0.29 hid the caption by inflating the launch rect by a
     * hardcoded 32 dp; it overhung the vehicle bar when the guess ran long and cropped the
     * app when it ran short, and there was no way to correct it without a release. 32 is
     * only where the control starts — AOSP's own `decor_caption_title_height` — and
     * Settings → Cockpit can walk it to whatever this ROM actually draws, against the real
     * window, in the van. Same shape as [camTrim], for the same reason.
     */
    fun captionDp(c: Context) = sp(c).getInt("caption_dp", 32)
    fun setCaptionDp(c: Context, v: Int) =
        sp(c).edit().putInt("caption_dp", v.coerceIn(0, 96)).apply()

    fun captionPx(c: Context) = Ui.dp(c, captionDp(c))

    /**
     * The shape the box asks for, as width ÷ height.
     *
     * The box used to be "whatever is left after the right-hand column", and the app got
     * whatever shape that produced. CarPlay is laid out for a screen and does not adapt
     * gracefully to an arbitrary rectangle — the owner's word for the result was that it
     * renders "crappy", squeezed and clipped. So the box takes the app's shape and DWM
     * works around it, which is the way round the stock launcher does it.
     *
     * Nudgeable for the same reason as [captionDp] and `camTrim`: what shape this
     * particular app wants on this particular ROM is not knowable from here. 16:9 is where
     * the control starts, not an answer.
     */
    fun stageAspect(c: Context) = sp(c).getFloat("stage_aspect", 16f / 9f)
    fun setStageAspect(c: Context, v: Float) =
        sp(c).edit().putFloat("stage_aspect", v.coerceIn(1.0f, 2.5f)).apply()

    /**
     * Draw the rect DWM *asked* for, on top of the live window.
     *
     * A ruler, not a feature. Android gives an unprivileged app no way to read another
     * app's window frame — that needs `android.permission.DUMP` — so when the owner reports
     * that the window "overlaps the right column" there has been no way to find out by how
     * much, or even whether the ROM honoured the request at all. Seven releases have aimed
     * fixes at a frame nobody has measured.
     *
     * With this on, one photograph contains both the requested rect and the real window,
     * and every offset can be read off it. Off by default; it is diagnostic scaffolding.
     */
    fun stageOutline(c: Context) = sp(c).getBoolean("stage_outline", false)
    fun setStageOutline(c: Context, v: Boolean) =
        sp(c).edit().putBoolean("stage_outline", v).apply()

    /** Preview rotation, 0/90/180/270. Analog inputs often arrive on their side. */
    fun camRotation(c: Context) = sp(c).getInt("cam_rot", 0)
    fun setCamRotation(c: Context, v: Int) =
        sp(c).edit().putInt("cam_rot", ((v % 360) + 360) % 360).apply()

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

    /**
     * Which wallpaper: [WALL_DEFAULT] the bundled image, [WALL_NONE] a plain field,
     * [WALL_CUSTOM] the picked [wallpaperUri].
     *
     * Defaults to the bundled one so the cockpit has a backdrop out of the box
     * without anyone configuring anything.
     */
    fun wallpaper(c: Context) = sp(c).getInt("wall", WALL_DEFAULT)
    fun setWallpaper(c: Context, i: Int) = sp(c).edit().putInt("wall", i).apply()

    const val WALL_DEFAULT = 0
    const val WALL_NONE = 1
    const val WALL_CUSTOM = 3

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
     * Measured rather than guessed. The bundled photo has a mean luminance of 0.010
     * against the design background's 0.0036 — three times brighter, but still very
     * dark in absolute terms — and full-contrast text clears **9.2:1** over its
     * brightest areas with no dimming at all.
     *
     * The only thing that ever struggled was *muted* text, at 3.2:1 undimmed. The
     * answer to that was not to dim the picture into invisibility, which is what a
     * 0.72 default did: it was to stop putting muted text on a photograph. Labels in
     * a see-through pane use the full text colour.
     *
     * A picked image is an unknown quantity, so the control stays — a bright photo
     * needs a heavy hand and the driver can give it one.
     */
    fun wallpaperDim(c: Context) = sp(c).getFloat("wall_dim", 0.30f).coerceIn(0f, 1f)
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

    /**
     * Move a stored `stretch` off stretch, once.
     *
     * The *default* has been fill for a long time, but a deck that was set to stretch
     * before that keeps it forever — a stored value is not a default, and this one was
     * still distorting every camera feed on the van, which the v0.48.0 dump is what
     * finally showed (`cam_fit = 2`). Stretch is never the right answer for a camera: it
     * is non-uniform scaling, so the picture is simply the wrong shape.
     *
     * Guarded by its own flag rather than by the value, so choosing stretch deliberately
     * after this has run is respected and never silently undone.
     */
    fun migrateCamFit(c: Context) {
        val p = sp(c)
        if (p.getBoolean("cam_fit_migrated", false)) return
        val e = p.edit().putBoolean("cam_fit_migrated", true)
        if (p.getInt("cam_fit", 0) == 2) e.putInt("cam_fit", 0)
        e.apply()
    }

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

    /** Cockpit mode: 0 = Dashboard (panels drawn on the home canvas) ·
     *  1 = Overlay (one fullscreen base app + panels floating on top). */
    fun mode(c: Context) = sp(c).getInt("mode", 0)
    fun setMode(c: Context, v: Int) = sp(c).edit().putInt("mode", v).apply()

    /** Show the big favourites grid on the home canvas. */
    fun showFavGrid(c: Context) = sp(c).getBoolean("fav_grid", true)
    fun setShowFavGrid(c: Context, v: Boolean) = sp(c).edit().putBoolean("fav_grid", v).apply()

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
