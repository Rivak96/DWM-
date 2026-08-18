package com.dwm.cockpit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import com.dwm.cockpit.ui.DwmTheme
import com.dwm.cockpit.ui.HomeActions
import com.dwm.cockpit.ui.CockpitHome
import com.dwm.cockpit.ui.HomeApp
import com.dwm.cockpit.ui.theme.DwmIcons

/**
 * Launcher home. UI is Jetpack Compose (Material 3, glass cards). The
 * dashboard-mode drawn-panel canvas stays View-based and is hosted via
 * AndroidView. Services, feeds, launch engine and overlays are unchanged.
 */
class HomeActivity : DwmActivity() {

    private lateinit var panelHost: FrameLayout

    // Compose-observable state
    private val overlaysOnState = mutableStateOf(false)
    private val showCanvasState = mutableStateOf(false)
    private val allAppsState = mutableStateOf<List<HomeApp>>(emptyList())

    /** The strip under a live app. Built from the same favourites list the drawer's
     *  long-press menu and the overlay pill write to, so all three stay in step. */
    private val favouritesState = mutableStateOf<List<HomeApp>>(emptyList())
    private val cameraPanelState = mutableStateOf(Panel(PanelType.CAMERA, 0f, 0f, 1f, 1f, label = "Camera"))
    /** The 360 quad, or null when it is switched off. Built in [loadCamera]. */
    private val cam360PanelState = mutableStateOf<Panel?>(null)

    /** Label of the app hosted in the box, or null for the grid. */
    private val stageLabelState = mutableStateOf<String?>(null)

    /** Observable, not read straight from Prefs in the composition: a plain read is
     *  captured once and would not follow the Settings nudge back to this screen. */
    private val stageAspectState = mutableStateOf(16f / 9f)

    /**
     * The window chrome. Built once and handed to [StageHost], which decides when it draws.
     *
     * Relaunch guarding lives in the host now — `fc045f7` ("Only relaunch the stage app when
     * it changes") records why it has to exist at all: `onStart` used to relaunch
     * unconditionally, so if a window ever came back fullscreen it covered the launcher and
     * returning to DWM threw you straight back into the app. Back is swallowed here, because
     * this is a home launcher, so that loop had no exit.
     */
    private val stageChrome by lazy {
        StageChrome(this) {
            // The header's Full screen button. NEW_TASK alone *moves* the running task
            // rather than recreating it, so the app comes forward at native resolution with
            // its state intact.
            val pkg = StageHost.stagePkg ?: return@StageChrome
            StageHost.evict()
            LaunchEngine.launchFullscreen(this, pkg)
        }
    }
    private val boostState = mutableStateOf<Float?>(null)
    private val wallpaperState = mutableStateOf<Bitmap?>(null)
    private val wallpaperDimState = mutableStateOf(0.30f)

    private val handler = Handler(Looper.getMainLooper())
    private var lastPanelsJson: String? = "_never"

    private val clockPanels = ArrayList<Pair<TextView, TextView>>()
    private val speedGauges = ArrayList<GaugeView>()
    private val obdGauges = ArrayList<Pair<String, GaugeView>>()
    private var locListener: LocationListener? = null
    private var obd: ObdManager? = null

    private lateinit var actions: HomeActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // One-shot, and here because this is the first thing the deck starts.
        Prefs.migrateCamFit(this)

        // Registered on the application context and never torn down: DWM is the
        // launcher, so its process outlives every activity, and reverse gear is
        // only useful if we were already listening when it engaged.
        Vehicle.start(this)
        // The vendor CAN service, same reasoning: bound for the life of the
        // process, because its data is pushed and a callback registered after the
        // fact has already missed everything.
        CarInfo.start(this)

        panelHost = FrameLayout(this)

        // Home launcher: swallow Back so it stays on the launcher.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* stay on the launcher */ }
        })

        actions = HomeActions(
            carplay = { launchCarplay() },
            overlayMenu = { overlayMenu() },
            bluetooth = { openSetting(Settings.ACTION_BLUETOOTH_SETTINGS) },
            wifi = { openSetting(Settings.ACTION_WIFI_SETTINGS) },
            apps = { openOverStage(Intent(this, AppDrawerActivity::class.java)) },
            edit = { openOverStage(Intent(this, LayoutEditorActivity::class.java)) },
            settings = { openOverStage(Intent(this, SettingsActivity::class.java)) },
            reload = { reloadCockpit() },
            pill = { startPill() },
            appMenu = { pkg -> appMenu(pkg) },
            sendDump = { DumpFlow.send(this) },
            grantNotifications = {
                openOverStage(
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )

        setContent {
            DwmTheme(this) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (showCanvasState.value) {
                        AndroidView(factory = { panelHost }, modifier = Modifier.fillMaxSize())
                    } else {
                        CockpitHome(
                            cameraPanel = cameraPanelState.value,
                            cam360Panel = cam360PanelState.value,
                            allApps = allAppsState.value,
                            overlaysOn = overlaysOnState.value,
                            actions = actions,
                            boost = boostState.value,
                            wallpaper = wallpaperState.value,
                            wallpaperDim = wallpaperDimState.value,
                            onOpenFullscreen = { pkg -> openOther(pkg) },
                            drawnView = ::buildPanelView,
                            stage = stageLabelState.value,
                            stageAspect = stageAspectState.value,
                            favourites = favouritesState.value,
                            onStageBounds = ::onStageBounds
                        )
                    }
                }
            }
        }
    }

    /**
     * Start anything that has to appear **in front of home**, with the stage taken down.
     *
     * A freeform window floats above fullscreen activities, so the drawer, Settings, the
     * editor and every system-settings screen DWM opens do not cover it merely by starting
     * — they come up with a live app punched through the middle of them. v0.35.0 answered
     * that by covering the window with a fullscreen overlay, which covered these screens
     * too and swallowed their touches; that is the blank screen reported from the van. See
     * [StageHost].
     *
     * Eviction is the same lever [LaunchEngine.launchFullscreen] already pulled for every
     * *other* app. This is the path for the intents that are **not** package launches —
     * DWM's own activities, and the `Settings.ACTION_*` screens, neither of which has a
     * launch intent to hand to `launchFullscreen`. Between the two, nothing gets in front
     * of home without the stage standing down first, which is the property that matters:
     * patching these one at a time is how one gets missed.
     */
    private fun openOverStage(intent: Intent) {
        LaunchEngine.evictStage(this)
        runCatching { startActivity(intent) }
    }

    override fun onStart() {
        super.onStart()
        if (recreateIfScaleChanged()) return

        overlaysOnState.value = OverlayPanelsService.isRunning
        loadCamera()
        loadApps()
        loadWallpaper()
        // Nothing on the home screen hosts a WebView any more — the box is an app grid
        // and the side boxes are a diagram and a camera — so the one shared WebView
        // belongs to the overlay service alone while home is up.
        WebPanelHost.pauseAll()
        // The dashboard is in front, so its camera outranks an overlay pointed at the
        // same device. An overlay on a different camera is left alone.
        CameraHost.setHomeVisible(true)
        StageHost.setHomeVisible(true)

        ensurePermissions()
        refreshPanelsIfChanged()
        loadStage()
        // Ask for the launch here as well as from the layout pass. Coming back to home owes
        // one — the window may have been evicted, or moved fullscreen, or killed for RAM —
        // and `onGloballyPositioned` does not necessarily fire again when an unchanged
        // layout resumes, which left the stage header sitting over an empty card. [StageHost]
        // is process-global, so the box's last rect is still here even after a recreate; on
        // a genuine cold start it is null and the layout pass does the first launch.
        scheduleStageLaunch()

        if (!didAutoLoad && Prefs.autoLoad(this)) {
            didAutoLoad = true
            // ...but never while the box is a live app.
            //
            // The saved layout's base app opens *fullscreen over the launcher*, and getting
            // there goes through `launchFullscreen`, which evicts the stage. So 700ms after
            // every cold start — and installing an APK is a cold start — the box the owner
            // had just configured came up empty and stayed empty, because `evicted` is only
            // cleared by leaving home and returning. That is the "select carplay, click
            // home, it shows Tlink5 as text and the app doesn't open at all" report.
            //
            // The two features are alternatives, not layers: the stage IS this screen's live
            // app. A second one fullscreen on top of it is the legacy dashboard mode, and it
            // only makes sense when the box is the app grid.
            if (StageHost.stagePkg == null) {
                handler.postDelayed({ LaunchEngine.launchLayout(this, Prefs.panels(this)) }, 700)
            } else {
                Log.i(TAG_STAGE, "autoload skipped — the box is a live app")
            }
        }
        if (Prefs.overlayOnStart(this) && canOverlay()) OverlayService.start(this)
        if (Prefs.vehicleStrip(this) && canOverlay() && !VehicleStripService.isRunning) {
            VehicleStripService.start(this)
        }
        ensureOverlaysForMode()

        if (!didUpdateCheck && Prefs.autoUpdate(this) && Prefs.updateRepo(this).isNotBlank()) {
            didUpdateCheck = true
            handler.postDelayed({ autoCheckUpdate() }, 3000)
        }
    }

    override fun onStop() {
        super.onStop()
        stopLocation()
        stopObd()
        // Nothing is visible, so nothing should be running. A web panel used to keep
        // its timers, its JavaScript and any video going while the launcher was in
        // the background, on a deck that cannot afford it.
        WebPanelHost.pauseAll()
        // Hand the camera back. onStop does not detach this activity's views, so
        // without this the backgrounded dashboard would sit on the device and starve
        // the overlay that is now the only thing on screen.
        CameraHost.setHomeVisible(false)
        // onStop, never onPause: on API 29 multi-window only the focused activity is
        // resumed, so *touching the stage app* pauses this activity — reporting home
        // hidden there would strip the window's header the instant you tried to use it.
        StageHost.setHomeVisible(false)
        // A launch waiting on the settle timer must not fire into a screen that has gone.
        handler.removeCallbacks(stageLaunch)
    }

    /**
     * Hand back the stage chrome, so its overlay windows die with this activity.
     *
     * [StageChrome] draws through the *application* WindowManager, so without this its
     * windows outlive the activity that owns them and nothing is left holding a reference
     * to remove them. `stageChrome` is `by lazy` — one per activity instance — so a
     * recreate used to leak four opaque, touch-consuming overlays every time, stacked above
     * everything drawn afterwards including the camera overlay panel.
     *
     * [StageHost.detach] ignores this if a newer screen has already attached: `recreate()`
     * runs `new.onStart` before `old.onDestroy`, so this call routinely arrives from a dead
     * activity after its replacement is live.
     */
    override fun onDestroy() {
        super.onDestroy()
        StageHost.detach(stageChrome)
    }

    // ---- actions ---------------------------------------------------------

    private fun reloadCockpit() {
        lastPanelsJson = "_force"
        refreshPanelsIfChanged()
        LaunchEngine.launchLayout(this, Prefs.panels(this))
    }

    private fun toggleOverlays() {
        if (!canOverlay()) {
            openOverStage(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (OverlayPanelsService.isRunning) OverlayPanelsService.stop(this)
        else OverlayPanelsService.start(this)
        handler.postDelayed({ overlaysOnState.value = OverlayPanelsService.isRunning }, 600)
    }

    private fun startPill() {
        if (!canOverlay()) {
            openOverStage(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
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
            openOverStage(Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * Overlays are working well and are deliberately untouched — this only opens
     * their controls instead of the dock button silently toggling them, which was
     * easy to hit by accident.
     */
    private fun overlayMenu() {
        val running = OverlayPanelsService.isRunning
        val items = arrayOf(
            if (running) "Stop overlay panels" else "Start overlay panels",
            "Edit overlay layout",
            if (Prefs.overlayEdit(this)) "Lock panels (hide grips)" else "Unlock panels (show grips)",
            "Show floating pill"
        )
        Ui.dialog(this)
            .setTitle("Overlays")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> if (running) OverlayPanelsService.stop(this) else toggleOverlays()
                    1 -> openOverStage(Intent(this, LayoutEditorActivity::class.java))
                    2 -> {
                        Prefs.setOverlayEdit(this, !Prefs.overlayEdit(this))
                        if (OverlayPanelsService.isRunning) {
                            OverlayPanelsService.stop(this)
                            handler.postDelayed({ OverlayPanelsService.start(this) }, 400)
                        }
                    }
                    3 -> startPill()
                }
                handler.postDelayed({ overlaysOnState.value = OverlayPanelsService.isRunning }, 600)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ---- data for Compose ------------------------------------------------

    /**
     * Build the favourites list off the main thread.
     *
     * Every icon gets rasterised and colour-sampled; doing that on the main thread
     * made returning to home stutter on this deck. Results are posted back once, so
     * Compose sees a single state change rather than a drip.
     *
     * One list now, not two. The favourites band is gone from the home screen, so the
     * only consumer left is the app grid in the box.
     */
    private fun loadApps() {
        Thread {
            val all = Apps.all(this)
                .map { e -> HomeApp(e.pkg, e.label, DwmIcons.forApp(e.pkg, e.label)) }
                .sortedBy { it.label.lowercase() }
            // Two lists again, and the reason is good this time: with a live app in the box
            // the grid is gone, so the strip below it is the only way to open anything
            // without the full-screen drawer.
            val byPkg = all.associateBy { it.pkg }
            val favs = Apps.effectiveFavorites(this).mapNotNull { byPkg[it] }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                allAppsState.value = all
                favouritesState.value = favs
            }
        }.start()
    }

    /** Long-press on an app tile. Pinning writes to the same favourites list the pill
     *  and the app drawer use, so all three stay in step. */
    private fun appMenu(pkg: String) {
        val pinned = pkg in Apps.effectiveFavorites(this).take(Apps.FAV_SLOTS)
        Ui.dialog(this)
            .setTitle(Apps.label(this, pkg))
            .setItems(
                arrayOf(
                    "Open",
                    if (pinned) "Remove from favourites" else "Add to favourites",
                    "App info"
                )
            ) { _, w ->
                when (w) {
                    0 -> LaunchEngine.launchFullscreen(this, pkg)
                    1 -> {
                        val cur = Apps.effectiveFavorites(this).toMutableList()
                        if (pinned) cur.remove(pkg) else cur.add(0, pkg)
                        while (cur.size > Apps.FAV_SLOTS) cur.removeAt(cur.size - 1)
                        Prefs.saveFavorites(this, cur)
                        loadApps()
                    }
                    2 -> openOverStage(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$pkg")
                        )
                    )
                }
            }
            .show()
    }

    /**
     * Decode the wallpaper once per resume, downsampled to the panel.
     *
     * `inSampleSize` matters here rather than being tidiness: a phone photo is
     * commonly 4000px wide and decoding one at full size costs ~48 MB on a deck with
     * around 600 MB free. Sampled to the panel it is nearer 9 MB.
     *
     * Failure is silent and leaves the background plain — a wallpaper whose URI
     * permission did not survive a reboot must not stop the launcher drawing.
     */
    private fun loadWallpaper() {
        wallpaperDimState.value = Prefs.wallpaperDim(this)

        val mode = Prefs.wallpaper(this)
        val uri = Prefs.wallpaperUri(this)
        if (mode == Prefs.WALL_NONE) {
            wallpaperState.value = null
            return
        }
        if (mode != Prefs.WALL_CUSTOM || uri == null) {
            // The bundled image. Already cropped to the panel and stored as a 95 KB
            // WebP, so it decodes straight through with no sampling maths.
            Thread {
                val bmp = runCatching {
                    BitmapFactory.decodeResource(resources, R.drawable.wallpaper_default)
                }.getOrNull()
                runOnUiThread { if (!isFinishing) wallpaperState.value = bmp }
            }.start()
            return
        }
        Thread {
            val bmp = runCatching {
                val target = LaunchEngine.displaySize(this)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > target.x * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
            runOnUiThread { if (!isFinishing) wallpaperState.value = bmp }
        }.start()
    }

    /**
     * Read the side camera's settings back from prefs.
     *
     * This used to restore a chosen "stage app" too, and at one point relaunch its
     * window. The box is an app grid now — there is no single chosen app to remember —
     * so all that is left here is the camera.
     */
    // ---- the stage: one live app in the box ------------------------------

    /**
     * Decide whether the box is a stage or the app grid, and put the label up.
     *
     * A stage needs freeform to actually be available, so this degrades rather than
     * pretending: with no freeform support the box stays the grid and Settings says why
     * (see [LaunchEngine.freeformState]). A configured package that has since been
     * uninstalled falls back the same way.
     */
    private fun loadStage() {
        val pkg = Prefs.stagePkg(this)
        val label = pkg?.let { p ->
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
            }.getOrNull()
        }
        // Degrade rather than pretend: with no freeform support, or a configured app that
        // has since been uninstalled, the box goes back to being the app grid and Settings
        // says why. Overlay permission is required too — without it there is no mask, and
        // an unmasked freeform window is the v0.29 bug.
        val usable = label != null && LaunchEngine.freeformState(this).usable && canOverlay()
        stageLabelState.value = if (usable) label else null
        stageAspectState.value = Prefs.stageAspect(this)
        if (usable) StageHost.attach(stageChrome)
        StageHost.setStage(if (usable) pkg else null, label ?: "")
    }

    /**
     * The box reported where it is. Fires on every layout pass, so it must be cheap.
     *
     * The rect is handed straight to [StageHost], but the *launch* is deferred — see
     * [scheduleStageLaunch]. Launching from here directly is what produced both stage bugs
     * the owner has reported: launching on every pass relaunched the window repeatedly
     * (the flicker), and launching on the first pass only pinned the window to a rect the
     * screen had not settled into yet.
     */
    private fun onStageBounds(bounds: Rect) {
        StageHost.setBounds(bounds)
        scheduleStageLaunch()
    }

    /**
     * Launch the stage once the box has stopped moving.
     *
     * The screen reports its layout several times while it settles — the first pass runs
     * before `goImmersive()` has taken the system bars out of the window, so the box's rect
     * changes underneath us. Every report restarts this timer, so a burst of passes results
     * in exactly one launch, at the rect that was still true when the dust cleared.
     *
     * That is the honest place for this. [StageHost] answers "is a launch owed", which is a
     * question about state; "has the screen finished moving" is a question about time, and
     * belongs to the activity that owns the layout.
     */
    private fun scheduleStageLaunch() {
        handler.removeCallbacks(stageLaunch)
        handler.postDelayed(stageLaunch, STAGE_SETTLE_MS)
    }

    private val stageLaunch = Runnable {
        val (pkg, box) = StageHost.launchNeeded() ?: return@Runnable
        // The box is where the app's *content* should land; the window has to be asked for
        // bigger than that, because the system caption is drawn inside it. See
        // [StageHost.launchRectFor] — this was the missing half of Prefs.captionDp.
        val rect = StageHost.launchRectFor(box, Prefs.captionPx(this))
        Log.i(TAG_STAGE, "launchInBox $pkg box=$box rect=$rect caption=${Prefs.captionDp(this)}dp")
        if (!LaunchEngine.launchInBox(this, pkg, rect)) {
            // No launch intent, or the start threw. Un-commit, or the box sits empty until
            // the user leaves home and comes back.
            Log.w(TAG_STAGE, "launchInBox refused $pkg — will retry")
            StageHost.launchFailed()
        }
    }

    /** Every fullscreen launch evicts the stage — see [LaunchEngine.launchFullscreen]. */
    private fun openOther(pkg: String) = LaunchEngine.launchFullscreen(this, pkg)

    private fun loadCamera() {
        cameraPanelState.value = Panel(
            PanelType.CAMERA, 0f, 0f, 1f, 1f,
            label = "Camera",
            camId = Prefs.camId(this),
            rotation = Prefs.camRotation(this)
        )
        // A second ordinary camera panel, not a second camera *system*: the 360 kit
        // multiplexes its four fisheyes into one CSI input, so the quad is one feed.
        // CameraHost arbitrates per device id, so this and the panel above coexist as
        // long as they are different ids — which is the whole reason it groups by id.
        cam360PanelState.value = if (!Prefs.cam360On(this)) null else Panel(
            PanelType.CAMERA, 0f, 0f, 1f, 1f,
            label = "360",
            camId = Prefs.cam360Id(this),
            rotation = Prefs.cam360Rotation(this)
        )
    }

    // The wallpaper loader lived here. The SYNC-style home is flat dark by design,
    // so there is no backdrop to blur any more — which also takes a full-screen
    // blur pass off a low-RAM Unisoc chip.

    // ---- dashboard-mode canvas panels (View-based, hosted via AndroidView) --

    private fun refreshPanelsIfChanged() {
        val sig = (Prefs.panelsRaw(this) ?: "") + "|" + Prefs.accent(this) + "|" +
            Prefs.theme(this) + "|" + Prefs.mode(this)
        if (sig == lastPanelsJson) { startLocation(); startObd(); return }
        lastPanelsJson = sig
        panelHost.post { renderPanels() }
        syncOverlayPanels()
    }

    private fun syncOverlayPanels() {
        if (Prefs.mode(this) == 1 && canOverlay()) {
            OverlayPanelsService.stop(this)
            handler.postDelayed({ OverlayPanelsService.start(this); overlaysOnState.value = true }, 500)
        } else if (OverlayPanelsService.isRunning) {
            OverlayPanelsService.stop(this)
            handler.postDelayed({ overlaysOnState.value = false }, 300)
        }
    }

    private fun ensureOverlaysForMode() {
        if (Prefs.mode(this) == 1 && canOverlay() && !OverlayPanelsService.isRunning) {
            handler.postDelayed({
                if (Prefs.mode(this) == 1 && !OverlayPanelsService.isRunning) OverlayPanelsService.start(this)
                overlaysOnState.value = OverlayPanelsService.isRunning
            }, 1600)
        }
    }

    private fun renderPanels() {
        val w = panelHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = panelHost.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        destroyWebViews(panelHost)
        panelHost.removeAllViews()
        clockPanels.clear(); speedGauges.clear(); obdGauges.clear()
        stopLocation(); stopObd()

        val panels = Prefs.panels(this)
        val overlayMode = Prefs.mode(this) == 1
        var canvasPanels = 0

        for (p in panels) {
            if (p.isFullscreenApp()) continue
            if (overlayMode && p.isDrawn()) continue
            runCatching {
                val content = buildPanelView(p) ?: return@runCatching
                val card = FrameLayout(this)
                card.background = Ui.cardBg(this)
                Ui.roundify(card, 18)
                card.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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
        showCanvasState.value = canvasPanels > 0
        startLocation(); startObd()
    }

    private fun destroyWebViews(v: View) {
        if (v is WebView) runCatching { WebPanelHost.forget(v); v.destroy() }
        else if (v is android.view.ViewGroup) for (i in 0 until v.childCount) destroyWebViews(v.getChildAt(i))
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
        // Owner.HOME — both this activity's drawn canvas and the dashboard's CameraBox
        // come through here, and both lose the camera to nobody while home is in front.
        PanelType.CAMERA -> CameraPanel(this, p.camId, p.pkg, p.rotation, CameraHost.Owner.HOME)
        PanelType.NOTIF -> p.pkg?.let { NotifPanel(this, it) }
        // An APP is a freeform window floating above this activity, not a View this
        // activity can build.
        PanelType.APP -> null
    }

    private fun buildClockCard(): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val th = Ui.th(this)
        val time = TextView(this).apply {
            textSize = 34f; setTextColor(th.text)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
        val d = TextView(this).apply { textSize = 11f; setTextColor(th.dim) }
        wrap.addView(time); wrap.addView(d)
        clockPanels.add(time to d)
        // tick the canvas clock
        handler.post(object : Runnable {
            override fun run() {
                if (clockPanels.isEmpty()) return
                val now = java.util.Date()
                val t = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(now)
                val dd = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(now)
                clockPanels.forEach { it.first.text = t; it.second.text = dd }
                handler.postDelayed(this, 1000)
            }
        })
        return wrap
    }

    private fun gaugeFor(metric: String?): GaugeView {
        val g = GaugeView(this)
        g.accentColor = Ui.accent(this)
        val th = Ui.th(this)
        g.setPalette(if (th.light) 0x14000000 else 0x1FFFFFFF, th.text, th.dim)
        when (metric) {
            "gps_speed", "speed" -> g.configure("SPEED", "km/h", 0f, 240f)
            "rpm" -> g.configure("RPM", "rpm", 0f, 8000f)
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
        // Tracked so exactly one WebView is ever running — see [WebPanelHost].
        WebPanelHost.register(wv)
        if (p.type == PanelType.HTML) wv.loadDataWithBaseURL(null, p.html ?: "<h2>DWM</h2>", "text/html", "utf-8", null)
        else wv.loadUrl(p.url ?: "about:blank")
        return wv
    }

    // ---- feeds -----------------------------------------------------------

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
        locListener?.let { runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(it) } }
        locListener = null
    }

    private fun startObd() {
        if (obd != null || obdGauges.isEmpty()) return
        val mac = Prefs.obdMac(this) ?: return
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) return
        obd = ObdManager(mac, obdGauges.map { it.first }.distinct()) { key, num, _ ->
            if (!key.startsWith("_") && num != null) runOnUiThread {
                obdGauges.filter { it.first == key }.forEach { it.second.setValue(num) }
            }
        }.also { it.start(this) }
    }

    private fun stopObd() { obd?.stop(); obd = null }

    // ---- permissions + updater -------------------------------------------

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions() {
        val panels = Prefs.panels(this)
        val need = ArrayList<String>()
        // The 360 quad is a dashboard feed, not a legacy canvas panel, so it is not in
        // `panels` and would otherwise never trigger the request — the grant only ever
        // happened here because this deck also has a camera panel on the old canvas.
        if ((panels.any { it.type == PanelType.CAMERA } || Prefs.cam360On(this)) &&
            !granted(Manifest.permission.CAMERA))
            need.add(Manifest.permission.CAMERA)
        if (panels.any { it.type == PanelType.SPEED } && !granted(Manifest.permission.ACCESS_FINE_LOCATION))
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (panels.any { it.type == PanelType.OBD } && Build.VERSION.SDK_INT >= 31 &&
            !granted(Manifest.permission.BLUETOOTH_CONNECT))
            need.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) { lastPanelsJson = "_force"; refreshPanelsIfChanged() }
    }

    private fun autoCheckUpdate() {
        Updater.check(this) { result -> if (result is Updater.Result.Available) autoPromptUpdate(result.info) }
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
            openOverStage(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val dlg = Ui.dialog(this).setTitle("Updating").setMessage("Starting…").setCancelable(false).create()
        dlg.show()
        Updater.downloadAndInstall(
            this, info,
            onProgress = { pct -> dlg.setMessage("Downloading… $pct%") },
            onCommitted = { runCatching { dlg.dismiss() } },
            onError = { msg -> runCatching { dlg.dismiss() }; Toast.makeText(this, "Update failed: $msg", Toast.LENGTH_LONG).show() }
        )
    }

    private fun openSetting(action: String) {
        openOverStage(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun canOverlay() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    companion object {
        private var didAutoLoad = false
        private var didUpdateCheck = false
        private const val REQ_PERMS = 301

        /**
         * `adb logcat -s DwmStage` — the whole live-app path in one filter.
         *
         * DWM has never had diagnostic output, and the stage is the one part of it that
         * cannot be judged from a screenshot: whether a launch went out at all, and at what
         * rect, is invisible on the glass and has now cost two releases of guessing.
         */
        private const val TAG_STAGE = "DwmStage"

        /**
         * How long the box has to hold still before its rect is treated as final.
         *
         * Long enough to cover the layout passes between `setContent` and `goImmersive()`
         * taking the system bars out of the window; short enough that the app appears in
         * the box without the delay reading as a stall.
         */
        private const val STAGE_SETTLE_MS = 250L
    }
}
