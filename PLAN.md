# DWM — Driving Window Manager

A car-deck (Android 12 head unit) app that shows up to 4 other apps on screen at
once — resizable, arranged how you like, saved as a layout, and reopened all
together every time you launch it.

> **DECISION LOCKED (2026-07-18): zero-setup, no root.** The user will not do ADB,
> Shizuku, or root. That rules out **freeform windows entirely**, so tiling 4 full
> apps at once is *not possible* on stock Android 12. Sections 1–6 below describe
> the freeform design and are kept only as the "if you ever enable freeform later"
> path. **The governing plan is Section 8 — the zero-setup Cockpit + Overlay.**

---

## 1. The hard truth about Android (read this first)

Android is not a web browser. There is **no supported way to embed an arbitrary
third-party app's live screen inside your own app** the way an `<iframe>` embeds a
web page. That boundary is a core security feature — one app cannot draw or host
another app's UI. So the literal "iframe of apps" cannot be built as a plain app.

What *is* possible is having your app act as a **window manager / cockpit**: it
tells the Android system to open the real apps as real windows, positioned and
sized where you want, all visible at the same time. This is actually *better* than
an iframe — the apps keep full functionality — but it depends on a windowing
feature called **freeform multi-window**, which is present in Android 12 but
usually switched off by default.

Key facts:

- Standard Android only does **2-app split-screen** out of the box. Not 4.
- **Freeform mode** (desktop-style, resizable, movable windows) is what gives you
  4 windows arranged freely. It exists in Android 12 but must be *enabled*.
- Enabling it needs a **one-time privileged action** — either an ADB command from
  a PC once, the **Shizuku** app (no PC needed after first setup), or **root**.
- Some apps refuse to be resized (they set `resizeableActivity=false`, force
  portrait, or force fullscreen). Those will open fullscreen or misbehave in a
  window. We can't override that per-app without root. CarPlay-dongle apps are the
  most likely to fight this.

---

## 2. Architecture options

### Tier A — Freeform Cockpit  ★ RECOMMENDED
Your app enables/uses freeform windowing and launches each saved app into a saved
rectangle using `ActivityOptions.setLaunchBounds(Rect)` + freeform windowing mode.

- **Matches the request best:** up to 4 real apps, resize freely, save, reopen all.
- **Cost:** one-time enablement (ADB once, Shizuku, or root).
- **Caveats:** system draws a thin title bar/border on each window; uncooperative
  apps may ignore the window and go fullscreen; on a low-RAM deck the system may
  kill a background window (we re-launch it).

### Tier B — VirtualDisplay embedding (advanced / experimental)
Create virtual displays, launch each app onto one, and render each display's
surface into a view inside your app — a true "app inside my app" look.

- **Look:** closest to a literal iframe (no system title bars, fully custom frame).
- **Cost:** needs **system-level privilege** — launching arbitrary apps onto a
  virtual display requires a *trusted* display (`ADD_TRUSTED_DISPLAY`, a
  signature permission) → realistically **root or a persistent Shizuku/privileged
  service**. Much more complex and fragile; some apps detect the virtual display
  and break. Good as a v2 experiment, not the first build.

### Tier C — Zero-setup fallback
No privileges at all: a fast dashboard of large tiles + native **2-app
split-screen** (`FLAG_ACTIVITY_LAUNCH_ADJACENT`). One tap to swap what's shown.

- **Cost:** none. Works on stock Android 12 immediately.
- **Limit:** max 2 apps side by side, layout controlled by the system, no true
  "4 apps saved grid." This is the safety net if freeform can't be enabled on your
  specific ROM.

**Plan of record:** build **Tier A**, and ship **Tier C** behavior as the
automatic fallback when freeform isn't available. Explore Tier B later only if you
have root.

---

## 3. Recommended build — "Freeform Cockpit"

### Screens
1. **Launch / Cockpit** — on open, reads the saved layout and launches every
   configured app into its saved window rectangle. Shows a small floating control
   (re-launch all, edit layout, exit).
2. **Layout Editor** — a scaled preview of the screen with up to 4 draggable,
   resizable placeholder tiles. "Add app" opens an installed-app picker; assign an
   app to a tile; drag/resize; **Save**.
3. **Settings** — enablement status (freeform on/off, Shizuku/root detected),
   auto-relaunch toggle, per-app options (force-resize attempt, launch delay).

### Data model (persisted JSON / DataStore)
```
Layout {
  screenWidth, screenHeight        // reference resolution the rects were made for
  tiles: [ Tile x0..4 ]
}
Tile {
  packageName, activity            // which app
  bounds: { left, top, right, bottom }   // freeform launch rect
  launchOrder, launchDelayMs
}
```

### Launch logic (per tile)
```
val opts = ActivityOptions.makeBasic()
opts.launchBounds = Rect(...)                  // API 24+
// set windowing mode = freeform via opts (reflection on setLaunchWindowingMode
// for WINDOWING_MODE_FREEFORM = 5) when available / via Shizuku when privileged
val intent = packageManager.getLaunchIntentForPackage(pkg)
    .addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_MULTIPLE_TASK)
startActivity(intent, opts.toBundle())
```
Repeat for each tile with a small stagger. Bounds are scaled from the reference
resolution to the live display so a saved layout survives resolution differences.

### Enablement paths (pick what fits the deck)
- **ADB once (PC):** `adb shell settings put global enable_freeform_support 1`
  then reboot. Verify the exact key on your ROM — some builds also expose a
  Developer-Options "Enable freeform windows" toggle. Survives reboots.
- **Shizuku:** user starts Shizuku once (wireless-ADB or root); our app calls the
  privileged shell to set freeform + launch windows. No PC after first pairing,
  but Shizuku must be restarted after a full power cycle unless rooted.
- **Root:** most robust — persistent freeform, cleaner window launches, and opens
  the door to Tier B later.

---

## 4. Known limitations & mitigations
| Limitation | Mitigation |
|---|---|
| App forces fullscreen / not resizable | Detect + warn in editor; offer force-resize via Shizuku/root; else fall back to split-screen for that app |
| System title bar/border on freeform windows | Accept in v1; remove only via root/Tier B |
| Low RAM deck kills a background window | Auto-relaunch on focus/timer; keep tile count and app choice realistic |
| Freeform not enableable on this ROM | Auto-fall back to Tier C (2-app split) |
| Layout drifts across resolutions | Store reference resolution; scale rects on launch |

---

## 5. Tech stack & build
- **Language:** Kotlin. **UI:** Compose (editor/settings) + system window launches.
- **Min SDK:** 29–31 (target the deck's Android 12 / API 31; test on API 31).
- **Privilege bridge:** Shizuku API (optional), root shell (optional).
- **Build:** Gradle → `assembleRelease` → signed APK. Toolchain already present on
  this PC (JDK 23, Android SDK API 31/33/34, build-tools 34/35).
- **Install:** `adb install` or sideload the APK onto the deck.

---

## 6. Milestones
1. Project scaffold + signed debug APK that installs and opens on the deck.
2. Installed-app picker + Layout Editor with drag/resize + save/load.
3. Freeform launch of 1 app into a saved rect; then N apps (up to 4).
4. Enablement detection + Shizuku/root path + Tier C fallback.
5. Auto-relaunch, per-app options, polish, signed release APK.

---

## 7. Open decisions (need answers to lock the architecture)
1. What one-time setup are you willing to do on the deck (ADB once / Shizuku / must
   be zero-setup)?
2. Is the deck rooted (or rootable)?
3. Which specific apps must be visible at the same time? (e.g. tire-pressure/OBD
   app + navigation.) Some won't tolerate resizing — naming them lets me check.

---

## 8. GOVERNING PLAN — Zero-setup Cockpit + Floating Overlay

Constraints: stock Android 12, **no ADB, no Shizuku, no root**. The only privilege
used is the ordinary, user-granted **"Display over other apps"** (SYSTEM_ALERT_
WINDOW) toggle — a single in-app tap to a settings switch, not a PC/root action.

### What is and isn't possible zero-setup
- ❌ Cannot embed or tile 4 full third-party apps at once (needs freeform → privilege).
- ✅ Can float **our own** content (gauges, buttons, hosted widgets) over any app.
- ✅ Can host up to 4 real **home-screen widgets** from apps that publish them.
- ✅ Can one-tap launch / deep-link into other apps.
- ⚠️ Native 2-app split-screen exists but a plain app can't reliably *initiate* it
  programmatically (it needs a Recents gesture); we treat it as manual-only.

### Components
1. **Floating Overlay Service** (`TYPE_APPLICATION_OVERLAY`)
   - A collapsed pill/bar always on top of CarPlay/nav; tap to expand into a
     compact dashboard, tap/drag to move, tap to collapse. Draggable, remembers
     position. Starts on boot / when the cockpit opens.
   - Content = the same tiles as the cockpit (widgets + gauges + shortcuts).
2. **Cockpit (main activity)** — full-screen saved grid of up to 4 tiles; opens on
   launch and restores the saved layout. Also the place to arrange/resize/save.
3. **Tile types**
   - **Widget tile** — hosts an installed app's App Widget via `AppWidgetHost` +
     `AppWidgetHostView` (live tire-pressure/media/weather if the app offers one).
   - **Gauge tile** — our own readout, if we can source the data directly
     (Bluetooth OBD-II/TPMS dongle, or a NotificationListener reading the app's
     notification). Needed when the target app has no widget.
   - **Shortcut tile** — big one-tap launcher into an app.
4. **Layout Editor** — drag/resize tiles on a scaled preview; assign type + app;
   Save. Persist to DataStore/JSON.
5. **Settings** — grant overlay permission (deep-link to the system toggle),
   optional NotificationListener grant, start-on-boot toggle, per-tile options.

### Data source strategy (how a gauge tile gets real data)
Priority order per app: **(a)** host its App Widget → **(b)** read its notification
via NotificationListenerService (normal permission) → **(c)** read the hardware
directly (BT OBD-II/TPMS) → **(d)** fall back to a shortcut tile (launch the app).

### Known limits (be upfront)
- Overlay shows *our* content, never the other app's full live UI.
- Widget tiles only exist for apps that ship widgets; many nav apps don't.
- Some CarPlay-dongle apps may render at a layer that partly covers overlays —
  must be tested on the specific dongle.
- On a low-RAM deck, keep the overlay lightweight.

### Milestones (zero-setup)
1. Scaffold + signed debug APK that installs/opens on the deck.
2. Overlay permission flow + a draggable floating pill that expands/collapses.
3. Cockpit grid + Layout Editor (add/resize/save shortcut tiles).
4. Widget tile hosting via AppWidgetHost.
5. Gauge data sources (NotificationListener; BT OBD-II if in scope).
6. Start-on-boot, polish, signed release APK.

---

## 9. PIVOT — DWM is a LAUNCHER (2026-07-19)

User's deck is a T3-style head unit whose **stock launcher already shows a windowed
app** (screenshot). Two consequences:

1. **Make DWM a HOME launcher**, not a plain app. Registered with
   `category.HOME` → user sets it default → it auto-starts on boot and replaces the
   stock "fluff" launcher. This is a normal app capability — no root/ADB. DONE in
   the manifest.
2. **The stock launcher windowing app is strong evidence the ROM enabled
   multi-window/freeform.** IF it's the common global-setting kind, a third-party
   app can use it too → the real 4-window cockpit becomes possible on THIS deck
   with zero setup. IF it's OEM-locked to their signed launcher, we can't → fall
   back to launcher-drawn gauge/widget panes + 1 app window.

### Decision gate: the in-app capability probe
Milestone-1 APK now ships a **probe** (MainActivity):
- Reports `FEATURE_FREEFORM_WINDOW_MANAGEMENT`, PiP feature, and the
  `enable_freeform_support` / `force_resizable_activities` global flags.
- **"Test app window"** button launches an app with `ActivityOptions.launchBounds`
  (+ best-effort hidden `setLaunchWindowingMode` freeform). If it opens FLOATING →
  full tiling cockpit is buildable. If FULLSCREEN → overlay + widgets path.

**Next step is empirical:** install M1 on the deck, run the probe, read the result.
That single data point selects the architecture for Milestones 2+.

### Build facts (this machine)
- Toolchain: JDK 17 (Studio JBR, pinned in `gradle.properties`), Gradle 8.11.1,
  AGP 8.7.3, Kotlin 2.0.21, compileSdk 34 / minSdk 26 / targetSdk 33.
- Output: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed, sideloadable).
- Build cmd: `gradlew.bat assembleDebug` (JAVA_HOME → Studio JBR).

---

## 10. STATUS — Milestone 2 built (2026-07-19): real launcher

On-device test PASSED: freeform windows + overlay both work with zero setup.
So we built the full multi-window launcher. Framework-only (no androidx/Compose).

Shipped in the current APK:
- **HomeActivity** (HOME launcher): wallpaper, big clock/date, quick chips
  (Bluetooth/Wi-Fi/Settings), primary tiles (CarPlay / Load Cockpit / Edit Layout /
  Apps), favourites dock. Auto-loads saved windows on cold start (toggle in
  Settings). Design tuned to Google Design-for-Driving (76dp targets, dark, glance).
- **LayoutEditorActivity**: add up to 4 apps, drag to move, drag amber corner to
  resize, tap to change/remove, Save. Stored as screen fractions (`Prefs`/JSON).
- **LaunchEngine**: opens each saved tile into a freeform window (launchBounds +
  hidden freeform mode), staggered.
- **AppDrawerActivity**: grid of all apps; tap = fullscreen, long-press = windowed;
  also the app picker (pick mode).
- **SettingsActivity**: auto-load + overlay toggles, overlay grant/start/stop,
  CarPlay app picker, 3 wallpapers, system deep-links, set-default-launcher,
  ROM diagnostics.
- **OverlayService**: floating pill (unchanged; points at Home now).

Still stubbed / next milestones:
- Gauge tiles with real data (OBD-II BT / NotificationListener) — currently a
  placeholder label in the overlay.
- Media now-playing controls (MediaSessionManager, needs notif-listener).
- Wallpaper from gallery (currently 3 bundled gradients).
- Re-position/reuse already-open windows instead of relaunch; close-all.
- Release signing key + versioned release APK.

---

## 11. STATUS — Milestone 3 (2026-07-19): panel system + templates

Generalised every layout cell into a **Panel** with a type. Two families:
- **Windowed** (freeform windows over the canvas): `APP`, and `CAMERA` pointing at
  a camera app (the deck's **AUX** app → opens the wired analog front camera in a
  window; user confirmed AUX shows the front cam).
- **DWM-drawn** (painted on the home canvas): `WEB`/`HTML` (WebView — Home
  Assistant/Grafana/Node-RED/custom HTML), `IMAGE`, `CLOCK`, `SPEED` (GPS),
  `OBD` (ELM327 gauge), `CAMERA` live (Camera2, if the input is exposed).

New pieces:
- `Panel`/`Prefs` panel model + JSON, migrates old app-only tiles.
- `LaunchEngine.launchWindows` opens all windowed panels into their bounds.
- `HomeActivity` = live canvas: renders DWM panels, launches windows, runs clock +
  GPS-speed + OBD feeds, requests runtime permissions.
- `CameraPanel` (Camera2 live), `Obd`/`ObdManager` (Bluetooth ELM327 client, polls
  RPM/coolant/speed/throttle/MAP/intake; soft-fails with no dongle).
- `Templates` + template-driven **LayoutEditor**: pick a preset (Single, Split,
  Big+2, 2x2, **Big+4**, Main+bottom strip, Main+bottom camera), tap a **"+"** slot
  to drop in any panel type, drag/resize to nudge, **Save = default (auto-loads)**.
- Settings: OBD dongle picker (paired devices), "Scan camera inputs" (Camera2 id
  probe for the analog input), plus existing toggles/wallpaper/system links.
- Permissions added: INTERNET, FINE/COARSE_LOCATION, CAMERA, BLUETOOTH_CONNECT.
- Still framework-only — no androidx/Compose. APK ~881 KB.

Camera decision for this deck: **wired analog → use a "Camera app (AUX)" panel**
placed in a bottom slot; it launches AUX into a freeform window there. Live Camera2
embed kept as an option pending the "Scan camera inputs" result.

Next: HTML↔live-data JS bridge, media (MediaSession) panel, AppWidget host panel,
reuse-open-windows, release signing.

---

## 12. STATUS — Milestone 4 (2026-07-19): polish pass — v0.4.0

Theming & feel:
- **Ui.kt theme kit**: 6 accent presets (Teal/Amber/Sky/Lime/Rose/Violet) chosen in
  Settings; accent-bearing backgrounds generated at runtime; `Ui.skin()` restyles
  Buttons/Switches/tagged TextViews everywhere; dark Material dialogs throughout.
- **Wallpapers**: Aurora (tints with accent) / Midnight / Carbon procedural
  gradients + **pick any gallery image** (persisted URI).
- **GaugeView**: instrument-cluster arc gauges (270° track, animated accent arc,
  big value, unit+label) for OBD metrics and GPS speed. All drawn panels sit in
  rounded translucent cards (clipToOutline).
- Fade window transitions; per-panel crash guards (a bad panel can't bootloop the
  launcher); panels only re-render when layout/accent changed (WebViews/camera
  survive Home round-trips).

Launcher basics:
- **Dock**: bottom-centre, collapsible (state persisted), long-press icon →
  open-in-window / move left / right / remove; add apps from the drawer.
- **App drawer**: live search box; long-press → window / add-remove dock / app
  info / uninstall.
- **Empty state**: welcome card with "Choose template" when no layout exists.
- **Editor**: slots show app icons, drag snaps to a grid, Clear-all, dark dialogs.
- **Overlay pill**: position persisted, snaps to nearest edge after drag, expanded
  card shows 5 favourites that open as floating windows over CarPlay.
- **BootReceiver**: starts the pill on boot when enabled.

Size-agnostic (user has a 13" deck, wants it to work anywhere): positions are
screen-fractions, editor preview matches real aspect ratio, gauges scale to their
view, bars scroll — layouts adapt to any resolution/size.

Build: versionCode 4 / versionName 0.4.0. **Release signing added** —
`dwm-release.keystore` + `keystore.properties` (both gitignored; passwords inside;
BACK THE KEYSTORE UP). `assembleRelease` produces a proper signed release APK.
Note: switching an installed debug build → release build requires uninstalling
once (signature change).

---

## 13. STATUS — Milestone 5 (2026-07-19): Tesla design language — v0.5.0

User asked for a Tesla-inspired finish. Shift from "neon cockpit" to Tesla's
flat monochrome language:
- **Palette**: neutral near-black `#0A0A0C`, flat gray surfaces `#1B1B1E/#2A2A2D`,
  white `#F2F2F2` text, gray `#9A9AA0` secondary. No blue-tinted darks, no glows.
- **Buttons**: flat gray rounded-rects (10dp), NO accent outlines, white
  sans-serif-medium text; pressed = slightly lighter. (Tesla button style.)
- **Accents**: colour only on interactive/live elements (gauge arcs, switch
  thumbs, editor "+", pill handle). New accent list: **Tesla Blue #3E6AE1
  (default)**, Tesla Red #E82127, Mono, Teal, Amber, Violet.
- **Typography**: sans-serif-light for big numerals (52sp home clock, gauge
  values, clock panels); gray CAPS letter-spaced section headers and gauge labels.
- **Gauges**: thinner arcs (0.06), lighter track, light-weight value, caps label.
- **Dock → Tesla bottom bar**: full-width flat translucent bar with hairline top
  edge, centred 60dp icons, collapsible chevron above.
- **Cards**: flat `#1B1B1E` @ 90%, 12dp radius, hairline border.
- Wallpapers: default now flat **Black**; Midnight/Carbon/custom image remain.
- Mono launcher icon; dark dialogs everywhere; neutral editor slots + grip.
- versionCode 5 / versionName 0.5.0. User will uninstall old debug and install
  the **release** APK fresh (release signature from here on).

---

## 14. STATUS — Milestone 6 (2026-07-19): density + adjustable text + Tesla settings — v0.6.0

User wants small fonts, adjustable, max room for data, Tesla-style everywhere.

- **Global text scale**: `DwmActivity` base class applies `Configuration.fontScale`
  (Compact 0.85 / Normal 1.0 / Large 1.15, in Settings→Display→Text size) to every
  screen; Home detects scale changes and recreates. All sp text scales.
- **Denser defaults everywhere**: home clock 52→34sp, top bar tighter, chips
  56→42dp min-height with 13sp text, dock icons 52dp, drawer icons 52dp + 10sp
  labels + 96dp columns, editor/drawer/settings titles 17sp, section headers 11sp
  caps, overlay pill compacted, editor slot labels 11sp, clock panels 34sp.
- **Tesla two-pane Settings**: left sidebar (Display · Cockpit · Overlay · Vehicle
  · System · About) with flat selected-state nav items, hairline divider, content
  pane per category. All prior controls preserved + new Text size row.
- Fixed: 3 stray NUL bytes in HomeActivity source (sentinel strings) → cleaned.
- versionCode 6 / versionName 0.6.0.

## 15. STATUS — Milestone 7 (2026-07-19): theme presets — v0.7.0

User: "the app is black, not gray like Tesla — put an actual Tesla theme I can
click." Correct — real Tesla dark mode is charcoal gray, not OLED black.

- **Theme engine** in `Ui.kt`: `Theme` data class (bg/surface/pressed/card/border/
  text/dim/barBg/hairline) + `Ui.th(c)`. Every drawable + skin() + dialogs +
  wallpapers + gauges derive from it.
- **Three presets** (Settings→Display→Theme, one tap, applies app-wide):
  - **Tesla** (DEFAULT): charcoal gray — bg #292B2E, surfaces #3B3E43, exactly the
    real Tesla dark look.
  - **Midnight**: the previous near-black (#0A0A0C) for OLED lovers.
  - **Light**: Tesla day mode — #F4F5F6 bg, #E3E4E7 buttons, #171A20 dark text,
    light dialogs.
- skin() remaps any known palette text colour → active theme (works across
  repeated theme switches without recreate on Home; Home re-renders panels when
  theme changes via the panels signature).
- `Ui.themeWindow(activity)` paints window + root; bottom bar drawn by `Ui.barBg`
  (flat translucent + hairline). GaugeView gained `setPalette(track/value/label)`.
- Editor preview intentionally stays dark in all themes (it depicts the screen).
- Renamed wallpaper "Black"→"Flat" (follows theme bg). NOTE: `SettingsActivity`
  theme handler is `applyThemePreset()` — do NOT name it `setTheme(Int)`, that
  collides with `Activity.setTheme(int)`.
- versionCode 7 / versionName 0.7.0.

## 16. STATUS — Milestone 8 (2026-07-19): on-device feedback fixes — v0.8.0

User tested on the deck (photo): not fullscreen, top bar wastes space, windows
flaky (sometimes fullscreen/vanish/wrong res), slow-feeling, looks basic. Wants
CarPlay FULLSCREEN with camera + TPMS floating on top.

- **Launch bugs root cause**: `FLAG_ACTIVITY_MULTIPLE_TASK` spawned duplicate
  tasks each reload → low-RAM deck killed them ("apps close by themselves") and
  old fullscreen tasks got reused ("sometimes opens fullscreen"). REMOVED — never
  re-add it. `launchLayout` = fullscreen base apps first, windows staggered after.
- **Panel.fullscreen** flag ("· FULL"): APP slot menu toggle "Open FULLSCREEN
  (base app)" — CarPlay as base, windows stack on top.
- **OverlayPanelsService** (the big one): draws every DWM panel (gauges, speed,
  clock, camera-live, web, image) as ALWAYS-ON-TOP overlay cards over any app —
  CarPlay fullscreen + camera + TPMS overlays = user's exact vision. Drag grip
  (⣿, top-left) to move; position persists into the layout. Toggled via home
  layers-icon, pill "Toggle overlays", or Settings→Overlay. Foreground service,
  type=location (GPS speed), notif id 1002.
- **Immersive fullscreen**: `DwmActivity.goImmersive()` hides status+nav bars
  (swipe to peek) on every screen; home canvas is now full-bleed edge-to-edge.
- **Floating top controls**: old opaque bar → floating clock (text-shadowed) +
  translucent icon cluster (vector icons: BT/WiFi/Apps/Edit/Reload/Overlays/
  Settings) that overlays the canvas, steals zero space, and hides via a tiny
  top-center handle (pref `topCollapsed`).
- **Perceived responsiveness**: RippleDrawable feedback on all chips + icon
  buttons + borderless icon ripples.
- ENCODING WARNING (twice bitten): never edit .kt sources via PowerShell
  `-replace`/`Set-Content` — Windows PowerShell 5.1 reads UTF-8 as ANSI and
  double-encodes non-ASCII (⌃⌄°—…). Use the Edit/Write tools only. A NUL-byte +
  mojibake scan is worth running before builds.
- versionCode 8 / versionName 0.8.0.

### v0.8.1 addendum — caption-bar cutoff fix
User reported CarPlay's bottom play/pause row was cut off in its window. Cause:
the system draws a caption/title bar INSIDE freeform bounds → the content area
shrinks → projection apps (fixed-shape video stream) CROP the bottom instead of
scaling. Fixes:
- `LaunchEngine.compensate()` grows every freeform window's bounds by
  `Prefs.captionComp` (default 32dp; off/24/32/44 in Settings→Cockpit→"Window
  title-bar fix"); if it would run off-screen, the window shifts up instead.
- The definitive CarPlay fix remains the v0.8.0 "Open FULLSCREEN (base app)"
  slot mode — no caption, no crop.
- versionCode 9 / versionName 0.8.1.

### v0.9.0 — Cockpit MODE switch (user request)
Settings→Cockpit→"Cockpit mode":
- **Dashboard** (0, default): panels drawn on the home canvas; apps in windows.
- **Solo + overlays** (1): on start, launches the FULLSCREEN base app (CarPlay)
  + windowed apps, then auto-starts OverlayPanelsService ~1.6s later so every
  drawn panel floats ON TOP of the base app. Home canvas intentionally skips
  drawn panels (no duplication). Reload restarts overlays. Switching back to
  Dashboard stops the overlay service. Mode included in the render signature.
- versionCode 10 / versionName 0.9.0.

### v0.9.1 — scenario-driven bug audit (user requested "ensure no bugs")
Walked cold-boot/Solo, layout-edit-while-overlays-run, service-killed, dongle
drop, re-render, drag, recreate() scenarios. Found + fixed 10:
1. Overlay toggle could wedge forever: liveness was a saved pref that goes stale
   when the system kills the service → replaced with process-local
   `OverlayPanelsService.isRunning` (pref writes kept as bookkeeping only).
2. Stale overlays after editing the layout → `HomeActivity.syncOverlayPanels()`
   restarts (Solo) / stops (Dashboard) overlays whenever the render signature
   changes; Reload also goes through it.
3. Overlay drag could push a panel fully off-screen (unrecoverable) → clamped.
4. Overlay drag saved position onto the WRONG panel if layout was edited while
   overlays ran (index shift) → identity check before persisting.
5. WebView leaks: never destroy()ed on re-render/service stop → destroyWebViews
   walker in HomeActivity.renderPanels + OverlayPanelsService.onDestroy.
6. Grip glyph "⣿" may not exist in deck font → "≡".
7. OBD never retried after a failed/dropped connection (gauges dead until app
   restart) → ObdManager reconnect loop, 5s backoff, while running.
8. `recreate()` (text-size change!) reset didAutoLoad → relaunched every app →
   moved to companion (process-wide, once per boot).
9. Starting overlays with no drawable panels left an empty foreground service →
   toast + stopSelf.
10. Editor slots from odd fractions could sit partly outside the preview →
   clamped in createSlot.
Also: pill overlay toggle switched to isRunning. versionCode 11 / 0.9.1.

### v0.9.2 — second verification pass (machine-checked)
- Ran **Android Lint** across the project: 3 errors → all triaged. Two were
  false-positive MissingPermission (call sites ARE permission-checked +
  runCatching-wrapped; annotated @SuppressLint with comments). One was the
  QUERY_ALL_PACKAGES Play-policy note (legit for a launcher, sideloaded;
  tools:ignore). 167 warnings all cosmetic (HardcodedText/SetTextI18n/
  ObsoleteSdkInt/UnusedResources…) — zero correctness categories (no Recycle/
  StaticFieldLeak/WakeLock findings). Lint now passes clean; keep it that way
  (`gradlew lintDebug`).
- Full re-read of HomeActivity (post-encoding-surgery state verified sane) and
  OverlayPanelsService.
- Hardened `OverlayPanelsService.start/stop` with runCatching so a background-
  FGS restriction or racing stop can never crash the launcher.
- versionCode 12 / versionName 0.9.2. Static verification exhausted — remaining
  risk lives on the device (ROM-specific freeform/overlay behaviour, dongle
  hardware, camera exposure), not in reviewable logic.

## 17. STATUS — Milestone 9 (2026-07-20): on-device overlay-mode feedback — v0.9.3
User ran Solo mode (CarPlay full + AUX overlaid). Three complaints, all addressed:
1. Overlay resize/reposition "guess where to drag" → overlay cards now have TWO
   clear grips: blue MOVE (top-left ✥) + blue RESIZE (bottom-right ⤢), via
   `Ui.gripBg`. `makeResizable` live-resizes; `persistBounds` saves position AND
   size back to the layout. (Freeform *window* edges are system chrome we can't
   restyle — the editor + Reload remains the way to size windowed apps.)
2. "Show ~5 apps every time I open DWM" → favourites GRID centred on the home
   canvas (`buildFavGrid`, up to 8, big 76dp icons), Settings→Cockpit "Show
   favourites grid" toggle + "Manage favourites" dialog (add via picker / tap to
   remove); shares the dock's favourites list. `Prefs.showFavGrid`.
3. Overlay "disappears" when tapping CarPlay → that's a windowed app (AUX) sinking
   behind the fullscreen base (true overlay panels never sink). Added
   `LaunchEngine.raiseWindows`; manual "Raise windows" in the pill +
   Settings→Overlay "Raise windows now"; opt-in "Keep app windows on top" auto-
   raise loop (`Prefs.pinWindows`, 6s) hosted in OverlayPanelsService (which now
   stays alive for pinning even with no drawn panels). Honest caveat in UI: auto-
   raise may flicker/steal focus; the clean fix is a Camera2 live-overlay if the
   deck exposes the analog input.
- Lint clean, encoding clean. versionCode 13 / versionName 0.9.3.

## 18. STATUS — Milestone 10 (2026-07-20): GitHub + in-app self-update — v0.9.4
- **In-app updater** (`Updater.kt`, framework-only): reads
  `raw.githubusercontent.com/<repo>/main/version.json` ({versionCode, versionName,
  tag, apk, notes}), compares `longVersionCode`, downloads the release APK (manual
  redirect-following HttpURLConnection) and installs via **PackageInstaller**
  session (no FileProvider/AndroidX). `InstallResultReceiver` launches the system
  confirm UI on STATUS_PENDING_USER_ACTION. Perm: REQUEST_INSTALL_PACKAGES + a
  one-time "allow unknown apps" grant. NOT silent (needs root for that).
- Settings→About: Set update repo (`Prefs.updateRepo` = owner/name), Check for
  updates, auto-check toggle (`Prefs.autoUpdate`). HomeActivity auto-checks once
  per process (3s delay) when enabled.
- APK URL built from repo+tag+asset, so `version.json` needs no hardcoded owner —
  user only sets owner/name once in the app.
- Repo scaffolding: `README.md`, `RELEASING.md`, `release.ps1` (bumps version.json
  from build.gradle + builds), `version.json`, `.gitattributes`.
- **git initialised + first commit** (118175e, 69 files). SECRETS EXCLUDED &
  VERIFIED: `keystore.properties`, `*.keystore`, `local.properties`, `.claude/`
  all gitignored and confirmed absent from tracking. **gh CLI is NOT installed**
  → the user must create the GitHub repo + push + make releases themselves
  (commands provided). Repo must be PUBLIC for no-login auto-update.
- versionCode 14 / versionName 0.9.4. Lint clean, encoding clean.

## 19. STATUS — Milestone 11 (2026-07-20): on-device overlay fixes — v0.9.5
User ran v0.9.4 Solo mode (CarPlay + AUX camera). 4 issues:
1. Overlay interrupts/pauses CarPlay music → `Prefs.muteOverlays` (default ON):
   `Ui.configureWeb` blocks WebView autoplay + mutes media JS on load; Mute button
   in the pill menu + Settings→Overlay toggle. HONEST LIMIT: the AUX *app window*
   grabs audio focus itself — DWM can't stop another app's focus; the real fix is
   the Camera2 overlay path (no audio, no sink).
2. Overlay didn't auto-start on launch → `ensureOverlaysForMode()` starts
   OverlayPanelsService on every Home onStart in Solo mode (no restart if already
   running).
3. Window sinks when CarPlay tapped → inherent to freeform windows; mitigations:
   Raise button + pin. TRUE fix = Camera2 overlay. Improved `CameraPanel`
   auto-detect: prefer EXTERNAL (analog input) → BACK → first camera id.
4. Overlay menu polish → rewrote `overlay_panel.xml`: clock header, hairline
   dividers, CAPS section labels, 2-col themed button grid (Show/hide · Mute ·
   Raise · Cockpit · Collapse · Close), new `OverlayBtn`/`OverlayLabel` styles.
- **STILL BLOCKED ON: the "Scan camera inputs" result.** If the analog cam is a
  Camera2 device, switch AUX-window → Camera2 overlay panel and #1+#3 both vanish.
- Built + committed (ba05710), NOT yet pushed/released (gh not installed; user
  pushes + creates release, or sideloads v0.9.5 to test first).
- versionCode 15 / versionName 0.9.5. Lint + encoding clean.

## 20. STATUS — Milestone 12 (2026-07-20): overlay UX overhaul — v0.9.6
gh CLI now installed + authed (Rivak96) → push + `gh release create` fully
automated from here. On-device feedback (Solo mode, CarPlay + AUX):
1. Audio interrupt → REMOVED the periodic auto-raise (pinTick) that relaunched
   windows every 6s and stopped music. Manual "Raise" button kept.
2. **Camera is now ALWAYS a drawn Camera2 overlay** (`Panel.isWindowedApp` no
   longer includes CAMERA; overlay/home always build `CameraPanel`). It persists
   size/position (persistBounds), never grabs audio, never sinks. The app (AUX)
   is a tap-to-open fallback shown only if the deck doesn't expose the input to
   Camera2. This is the real fix for the user's #1/#2/#3 re: the camera —
   contingent on the deck exposing the analog input (CameraPanel auto-detects
   EXTERNAL→BACK→first id).
3. Top-edge reach → overlay windows got FLAG_LAYOUT_IN_SCREEN + relaxed move clamp.
4. Auto-start overlays in Solo mode via `ensureOverlaysForMode()` (already added
   0.9.5; camera-as-drawn means the service now has panels and won't self-stop).
5. Home overlap bug fixed: `renderPanels` counts canvas panels and shows the
   favourites grid ONLY when the canvas is free; grid styled as a rounded tray
   (clusterBg). Welcome card only when zero favourites.
- Removed the "keep windows on top" auto-raise switch (kept manual Raise).
- Camera permission now requested for ALL camera panels (not just camId!=null).
- versionCode 16 / 0.9.6. Lint + encoding clean. Pushed + released (a937f9a).

### On "external tools/libraries" (user asked)
Declined by design: Tesla's UI is custom-drawn; no library provides that look, and
a UI framework (Compose/Material) would slow the old deck. Polish = design work
(spacing/color/layout/decluttering), done directly. Kept framework-only.

### STILL the key unknown: does the deck expose the analog cam to Camera2?
The camera panel now just TRIES it automatically. If the user sees live video →
solved. If "Camera not exposed to apps / tap to open" → the analog input is
firmware-only and the AUX-app fallback (with its audio/sink limits) is the ceiling.

## 21. STATUS — Milestone 13 (2026-07-20): card-dashboard home — v0.10.0/0.10.1
User showed a reference dashboard mock (light, card-based: state tiles, hero
action card, favourites) and wanted the home to look/navigate/feel like it.
Full HomeActivity + activity_home.xml rewrite:
- 3-column card dashboard: LEFT 2×3 coloured action **tiles** (CarPlay=accent,
  Overlays=green when active, Bluetooth/WiFi/Apps/Settings neutral) · CENTRE
  **hero card** with live `LayoutPreview` (new custom View drawing the saved
  layout as mini blocks) + Launch/Edit buttons · RIGHT **favourites card** (2×N)
  + **status card** (Overlays On/Off, Floating pill Show, Version→check updates).
- Colour used as STATE not decoration (matches reference + Tesla restraint).
- New `Ui.tileBg/primaryBtnBg`, `Ui.GREEN`, `ic_play`, `LayoutPreview.kt`.
- Dashboard hides when Dashboard-mode canvas panels are present (no overlap).
- **CRITICAL preservation**: `OverlayService` (floating pill) + `OverlayPanelsService`
  UNCHANGED — user explicitly asked not to lose the pill. v0.10.1 added a
  "Floating pill · Show" status row (`startPill()`) for one-tap access from home.
- Reference mock is LIGHT-themed → tell user Settings>Display>Light for that look.
- Removed old top icon-cluster + bottom dock (replaced by tiles/favourites).
- versionCode 18 / 0.10.1. Lint + encoding clean. Pushed + released via gh.
- NOTE: raw.githubusercontent version.json CDN-caches ~5min after push; verify
  real value via `gh api .../contents/version.json`.

## 22. STATUS — Milestone 14 (2026-07-21): camera rotate — v0.10.2
Camera2 previews (esp. analog inputs) can come in rotated with no auto-fix.
- `Panel.rotation` (0/90/180/270, persisted as "rot").
- `CameraPanel.setRotationDeg` applies a TextureView matrix transform (rotate about
  centre + cover-scale for 90/270) so the panel rect stays put.
- Live **⟳ rotate button** top-right of the camera overlay card → cycles 90°,
  applies live, `persistRotation` saves it (identity-checked).
- Editor camera tile menu also has "Rotate 90°".
- versionCode 19 / 0.10.2. Lint + encoding clean. Released via gh.

## 23. STATUS — Milestone 15 (2026-07-21): app-notification overlay — v0.10.3/0.11.0
- v0.10.3: camera aspect fix — pick the camera's real output size for the buffer,
  contain-fit + centre transform (fixes square-panel shift/stretch), + rotation.
- v0.11.0: **App-notification overlay panel** — user reported app WINDOWS (their
  TPMS app) sink behind fullscreen CarPlay while the Camera2 overlay stays. Root
  cause is the Android z-order rule (can't keep another app's window above a
  fullscreen app). Fix: mirror the app's NOTIFICATION as a DWM-drawn overlay
  (stays on top like the camera).
  - `NotifStore` (latest notif per pkg) + `DwmNotificationListener`
    (NotificationListenerService, BIND_NOTIFICATION_LISTENER_SERVICE, manifest).
  - `NotifPanel` (drawn, subscribes to store, live). New `PanelType.NOTIF`.
  - Editor "App notification (TPMS, etc.)" type; `pickApp` refactored Boolean
    asCamera → `PanelType kind`. Settings→Vehicle "Allow DWM notification access"
    (deep-links ACTION_NOTIFICATION_LISTENER_SETTINGS; `NotifStore.accessGranted`).
  - CAVEAT: only works if the TPMS app posts the pressures in a notification. If
    not → need BLE-TPMS decode or OBD TPMS (unknown sensor format) — pending.
- versionCode 21 / 0.11.0. Lint + encoding clean. Released via gh.

## 24. STATUS — Milestone 16 (2026-07-21): Compose UI modernization — v0.12.0
User: "complete UI modernization" — Compose, Material 3, glassmorphism, Lottie,
Material Symbols, Room, JSON theme; target Jaecoo/Tesla OEM feel; native Compose
(no RN/Flutter — user confirmed). Framework-only rule dropped for the home.
- STAGE 1 (validated toolchain): Compose BOM + material3 + icons-extended +
  lottie-compose + kotlin compose plugin all build on the JDK17/AGP8.7.3/K2.0.21
  setup.
- STAGE 2 (shipped): HOME rebuilt in Compose Material 3. `DwmActivity` →
  ComponentActivity; `ui/DwmTheme`, `ui/Glass` (GlassCard + drawableToImageBitmap),
  `ui/DwmHome` (clock + icon chips, action tiles with state colour, hero card with
  live LayoutPreview via AndroidView, favourites + status, entrance animation).
  Glass = pre-blurred wallpaper + translucent cards (NOT live BlurView — perf).
  Dashboard-mode drawn panels still View-based via AndroidView(panelHost).
- Engineering calls made & told to user: (a) glass via pre-blur not live BlurView
  (smoothness); (b) MotionLayout→Compose animations (MotionLayout is a Views API);
  both deliver the intent. APK ~11MB (icons-extended). CANNOT runtime-test without
  the deck — shipped with clear recovery note (prior release APKs remain).
- versionCode 23 / 0.12.0. Lint + build clean. Released via gh.

### Remaining modernization stages (after user confirms Compose runs on the deck)
- Lottie **startup animation** (dep in place; needs a boot splash + a dwm_boot.json).
- **Room** for overlay position/size (currently Prefs JSON — functionally fine).
- **JSON theme engine** (currently M3 theme derives from Ui.th presets).
- Optional: R8/minify or drop icons-extended to shrink the ~11MB APK.

### Candidate next features (user asked "what else"; not yet built)
1. Media now-playing panel + controls (MediaSession; needs notification access).
2. Host real home-screen **widgets** as panels (AppWidgetHost).
3. JS data bridge: feed speed/OBD/time into Custom-HTML panels (custom gauges).
4. Multiple saved layout **profiles** (Day/Night/Highway) + quick switcher.
5. Auto day/night: dim wallpaper/brightness by sunset or headlights.
6. Trip computer panel (distance/avg speed from GPS).
7. Weather panel (needs a free API key).
8. Notification glance strip (needs notification access).


## 25. STATUS — Milestone 17 (2026-07-27): density + J7 language + camera — v0.13.0
User: drop Lottie (perf worry); "everything is so big… old man that cant see kinda
vybs" on a 13" high-res deck — make it smaller, fit more; take inspiration from the
**Jaecoo J7** infotainment ("actually examine it, don't just build"); front camera
"stretches very funny" and is "very bright", wants day/night adaptation.

### J7 research (Chrome automation — DDG images + Pan Motoring walkthrough video)
Portrait 13.2"/14.8" QNX (Qualcomm 8155). Observed design language:
- Thin top status strip, tiny type, edge-aligned; no big clock block.
- ~50% of the panel is plain wallpaper; content lives in **frosted light cards**
  (heavy rounding, translucent, tiny dim all-caps label + larger value).
- Cards sit in a horizontally swipeable row (Navigation / Local radio / …).
- **Persistent bottom bar** always on screen (even over the keyboard): split-screen,
  power, `‹ 18° ›` driver temp, fan, `‹ 18° ›` passenger temp, car, home.
- Thin monochrome **line icons**, small relative to the glass. App grid = 4 cols of
  squircles with small labels.
Takeaway applied: density comes from *small controls + card hierarchy*, not from
cramming; one persistent dock instead of duplicated action tiles.

### Shipped
- **Lottie removed** (`lottie-compose` dep dropped; nothing referenced it).
- **`Scale`** — global interface scale via `Configuration.densityDpi`, shrinking
  every dp *and* sp at once. `screenWidthDp/HeightDp` divided by the same factor so
  `widthPixels` is preserved → overlay pixel geometry stays valid. Applied in
  `DwmActivity.attachBaseContext` **and** both overlay services' `attachBaseContext`
  so floating panels match. Default **0.8**; Settings→Display "Interface scale"
  (Tiny 0.7 / Compact 0.8 / Cosy 0.9 / Stock 1.0). `recreateIfScaleChanged()`
  replaces the old fontScale-only check (now covers scale+font+theme).
- **Home rebuilt in the J7 language** (`ui/DwmHome`): slim status strip (23sp clock,
  tappable Overlays/BT/Wi-Fi pills, reload chip) → 3 glass cards (Cockpit hero /
  Favourites / Status) → **persistent 8-icon dock** (CarPlay, Apps, Overlays, Pill,
  Bluetooth, Wi-Fi, Edit, Settings). Action tiles deleted — the dock replaces them,
  removing the tile/top-icon duplication. Favourites **8 → 12** (4×3, 34dp icons).
  Long-press on a favourite now works (`combinedClickable` → `actions.favLong`,
  which was previously wired but unreachable). Radii 22→16, padding 14→11.
- **CameraPanel rewritten**:
  - *Stretch fix* — was `setDefaultBufferSize(view w,h)` + a fill-the-panel matrix,
    so the driver substituted its nearest mode and the frame got squashed. Now picks
    a real supported size from `SCALER_STREAM_CONFIGURATION_MAP` closest in aspect to
    the panel, then maps it with a matrix that undoes TextureView's stretch → rotate
    → fit. Modes: **Fill** (uniform, crops; default) / **Fit** (letterbox) /
    **Stretch** (old behaviour), Settings→Vehicle.
  - *Brightness* — day/night tone profiles (day 0.90×/1.06 contrast, night 0.60×/
    1.15). **Dimming = a black overlay View's alpha** (guaranteed to composite over
    a TextureView); `RenderEffect.createColorFilterEffect` on API 31+ only *adds
    contrast back* (brightness left at 1.0), so if RenderEffect turns out not to
    apply to a TextureView the picture is still correctly dimmed, never doubly.
    Plus `CONTROL_AE_EXPOSURE_COMPENSATION` (day −0.7 EV, night −0.3 EV) — analog/
    USB bridges often ignore AE, hence the view-side work. Auto day/night from
    `Sensor.TYPE_LIGHT` (<15 lux) with a clock fallback (19:00–06:00), re-evaluated
    every 30 s. Manual **brightness trim** −4..+4 (≈8%/step) in Settings.
- **`material-icons-extended` dropped** — the 9 Compose icons now come from local
  vector drawables (`ic_apps/ic_bt/ic_edit/ic_layers/ic_reload/ic_settings/ic_wifi`
  already existed; added `ic_car` + `ic_pill`) via `painterResource` + `Icon(tint=)`.
  **Release APK 11 MB → 6.31 MB.** R8/minify deliberately NOT enabled — can't
  runtime-test it, and a silently-broken sideload is expensive to recover from.
- **Favourites cap bug fixed** — bumping the home grid to 12 left
  `AppDrawerActivity` still evicting at 8, so the 9th–12th slots were unreachable.
  `FAV_SLOTS` is now the single source (drawer + Settings both honour it) and
  `Apps.DEFAULT_FAVS` pre-fills 10 instead of 6.
- **App drawer densified** — GridView columnWidth 96→82dp, spacing 8→4dp, item
  padding 10→6dp. `dimens.xml` trimmed (touch_min 76→72, gap 18→14, dock/drawer
  icon 52→46, tile_height 104→88); at the 0.8 interface scale touch_min still lands
  ~58dp effective, above the 48dp minimum.
- versionCode 24 / 0.13.0. Build + lint clean. NOT runtime-tested (no deck).

### Open / needs the deck to verify
- Whether 0.8 is the right default scale, or the deck wants 0.7.
- Whether `setRenderEffect` tints a TextureView (only affects the contrast lift —
  dimming works either way). If not, a GLSurfaceView shader is the escape hatch.
- J7 ideas not built: swipeable card carousel, swipe-down shortcut panel, 4-col
  squircle app drawer. (Climate strip is impossible — no HVAC access.)

## 26. STATUS — Milestone 18 (2026-07-27): CAN-bus discovery + overlay lock — v0.14.0
User: "the deck has the canbus talking directly with it — if i change ac controls it
shows up on the deck. cant we use all of that?" Wants AC temps, fan speed, headlights
etc. shown "small and neat, very polish" — e.g. an animated front-seats graphic with
the AC in front. Also: "instead of having an overlay box, thats just wasted space, if
i want to edit the overlay i can click the button i dont need a big empty box."

### Research — can a normal app read the deck's CAN data?
**There is no standard API.** These are aftermarket units running plain Android, not
Android Automotive, so `android.car`/`CarPropertyManager` does not exist. XDA's
consensus on third-party CAN access is "no API, only decompilation".
BUT the reverse-engineered Microntek CAN service (github TheUnknown12/AHUCanBus,
`android/microntek/canbus/CanBusServer.java`) shows it publishes state two ways a
normal app CAN read, both confirmed in source:
1. `Settings.System.putInt(resolver, "com.microntek.hiworld.ari", 0)` — the settings
   provider is world-**readable**; only writing needs WRITE_SETTINGS.
2. `sendBroadcastAsUser(Intent("com.ahucanbus.display").putExtra("text", byte[]))` —
   a plain implicit broadcast. Runtime-registered receivers still get implicit
   broadcasts on O+ (only manifest-declared ones were restricted).
The vendor app itself is `sharedUserId=android.uid.system` and reads `/dev/ttyV0`
via `android.microntek.serial` — that path is closed to us, but its *outputs* aren't.
Both channels are per-unit and undocumented → discover empirically, don't guess.

### Shipped
- **`VehicleProbe`** — zero-setup CAN discovery, no root/ADB/Shizuku:
  - `snapshot()` dumps every name/value in Settings System+Global+Secure by querying
    the content URIs directly; `diff()` compares two snapshots and filters clock/
    battery/volume churn so real changes aren't buried.
  - `vehicleApps()` lists packages matching CAN/vendor name hints with their
    providers/receivers/services and **exported + readPermission** flags — an
    exported provider with no read permission is directly queryable
    (`probeProvider()`).
  - `Sniffer` registers a runtime receiver over ~25 known head-unit CAN actions and
    dumps any extras (byte[] rendered as hex).
  - `buildReport()` + `saveReport()` — Settings→Vehicle→**"Scan vehicle"** is ONE
    button running a two-step flow (user asked for a file, not copy-paste):
    tap → instructions dialog → Start (snapshots) → user changes AC/lights/doors →
    tap again → "Finish scan & save file". Writes a `.txt` to shared **Downloads**
    via MediaStore on API 29+ (no permission under scoped storage; the returned
    content:// URI is directly shareable) with a FileProvider fallback below Q,
    then offers **Share**. Report = device info + settings diff + name-matched
    vehicle keys + app scan + broadcast hits.
    NOT a full settings dump on purpose — that store holds device identifiers and
    account/network details and this file gets uploaded, so values are run through
    a `SENSITIVE` redaction list (android_id/serial/imei/mac/account/token/ssid/…)
    and truncated at 400 chars.
- **Overlay edit mode** (`Prefs.overlayEdit`, default OFF) — the move ✥ / resize ⤢
  grips, the rotate ⟳ button and the card frame are now built ONLY while editing.
  Locked = panel is 100% content, corners usable. Toggle in Settings→Overlay→"Panel
  layout"; flipping it restarts OverlayPanelsService since grips are inflate-time.
- versionCode 25 / 0.14.0. Build + lint clean.

### Next — gated on probe output  ✅ RESOLVED, see §27–§30
Written when the vehicle panel was still hypothetical. It was built, and the AC part
of it never could be: there is no AC method among the CAN service's 64, so climate
lives in a different vendor app entirely. The ELM327 fallback was never needed —
the deck's own CAN service turned out to be bindable. `Obd.kt` remains, unused.

---

> **§27 onward were reconstructed on 2026-08-08 from commit messages**, after this
> file was left at v0.14.0 while development ran on to v0.32.0. The commits are the
> primary record and are considerably more detailed; `git log v0.14.0..HEAD` is the
> place to go for the full argument behind any of it.

## 27. STATUS — Milestone 19 (2026-07-27→28): finding the channel — v0.14.1 → v0.16.0

Three releases spent closing in on where this deck actually publishes CAN state.
Each one replaced a guess with a measurement.

- **v0.14.1 — deeper scan.** The v0.14.0 scan came back with every easy path shut:
  nothing changed in the settings store, no key names matched, no CAN broadcasts
  fired. It did name the vendor — `com.dofun.carsetting` — which exposed a blind
  spot: the scan only listed packages whose *own name* contained a hint, so every
  other `com.dofun.*` package was invisible, and the CAN handler is usually a
  sibling under the same prefix. Added a full component dump per vendor prefix,
  serial-port visibility (`stat` only — never opened; CAN reaches Android over a
  UART and reads are destructive, so bytes we take are bytes the deck's own CAN
  service never gets), and a complete installed-package list as the safety net.
- **v0.15.0 — query providers, stop guessing prefixes.** The scan named the real
  platform: **`com.tw.*` (Topway)**, with `com.tw.carinfoservice` the likely CAN
  consumer. §5 had missed it because the prefix list said `com.ts` and
  "carinfoservice" matches no name hint either. Guessing prefixes is the wrong shape
  of solution, so it was inverted: dump every package that isn't stock AOSP/Google.
  `exportedProviders()` finds exported providers with no read permission and
  actually queries them — even a rejection is informative, since "Unknown URI" means
  alive-and-wants-a-path, which is a different answer from a permission denial.
- **v0.16.0 — read the vendor manifests.** The sniffer had shipped ~50 hand-written
  candidate action names and caught nothing, twice. Those names were never guessable
  — but they *are* readable: every installed APK sits world-readable at
  `applicationInfo.publicSourceDir`, with its receivers' intent filters in the binary
  `AndroidManifest.xml`. So a minimal **AXML reader** was written (string pool +
  element tree, enough to attribute `<action android:name>` to its enclosing
  component) and the sniffer now listens on every action this deck's own apps
  declare. `AxmlTest` covers it against our own release APK, because hand-computed
  chunk offsets fail silently rather than throwing and this ships to a deck that
  can't be attached to a debugger.

## 28. STATUS — Milestone 20 (2026-07-29→30): first real signal, then the bind — v0.17.0 → v0.18.1

- **v0.17.0 — reverse gear, confirmed.** Scan 3 caught the first vendor broadcast any
  scan had ever seen land: `com.unisound.intent.action.DO_MUTE` / `DO_UNMUTE` carry
  live `reverse` and `call` extras. That is the deck ducking its own audio and naming
  the reason — reverse is readable with no permission and no AIDL. New `Vehicle`
  object holds live state, started from `HomeActivity` on the **application** context,
  since the launcher process outlives every activity and the signal is only useful if
  we were already listening when the gear engaged. The scan also stopped relying on a
  before/after diff alone — a `ContentObserver` records writes as they happen, because
  reverse had engaged *and* released inside the v0.16 window with both snapshots
  matching.
- **v0.18.0 — bind the AIDL, and get the APK off the deck.** Scan 3 answered the
  blocking question: the permission guarding `com.tw.carinfoservice` is protection
  level `normal`, and `CarService` is exported **unguarded** anyway. `AidlProbe` binds
  it at scan start and reads at scan finish (bind is async, `buildReport` runs inline).
  It deliberately **does not walk transaction codes** — an AIDL stub reads arguments
  off the parcel, and a parcel we never filled yields 0/null rather than throwing, so
  an unknown code is as likely to be `setSomething(0)` on a live vehicle bus as it is
  a getter. Only facts a binder owes by contract are taken: descriptor, liveness, and
  whatever `dump()` volunteers — drained on a second thread, since a dump larger than
  the pipe buffer would deadlock against our own blocking call. **Settings → Export
  CAN app** copies a vendor APK to Downloads with no adb and no cable, which matters
  for correctness rather than convenience: AIDL assigns transaction codes
  positionally, so only the byte-exact APK off *this* deck can be trusted.
  Also removed the v0.17 settings-store reverse watcher — **do not put it back**.
  `system/revserse_status` reads 1 while the car is in DRIVE; it is a reverse-*camera*
  flag, not the gear, and the live watcher recorded zero writes across a scan in which
  reverse was engaged and released.
- **v0.18.1 — the picker that was never drawn.** Reported off the deck: "Export CAN app
  just have a prompt and nowhere to choose a app." A framework `AlertDialog` shows
  either a message or a list, never both — `AlertController.setupContent` only swaps
  the ListView in on the `mMessage == null` branch, so `setMessage()` + `setItems()`
  silently discards the items. The export was unreachable from the moment it shipped.
  Both dialogs now lead with the explanation and end in a button that opens the list.
  Every other `setItems()` dialog in the app is title-only, which is why they work.

## 29. STATUS — Milestone 21 (2026-07-30): real CAN data — v0.19.0

The APK export made the decompile unnecessary: `com.tw.carinfoservice` packages its
interface **source** at `com/tw/carinfoservice/CarServiceAidl.aidl` and
`CarServiceCallBack.aidl` — 64 methods and 66 callbacks, commented in Chinese, naming
every signal. Both are committed under `app/src/main/aidl` exactly as extracted and the
build generates the stubs.

That is a correctness argument, not convenience. AIDL numbers transactions by
declaration order, so a hand-written client must get 64 codes right and would break
silently on a ROM that inserted a method. Generated-from-source cannot disagree. The
mapping was confirmed three independent ways before any of it was trusted: the `.aidl`
declaration order, the service's own compiled proxy recovered via jadx (gear=8,
reverse=24, radar=59 all matched, including the unmistakable 16-int `onRadar`), and the
generated constants — 66 declared callbacks == 66 in the shipped binary.

`CarInfo` binds, registers a callback and holds live state. Two facts read out of the
service's `onTransact`, both documented in the source and both still binding:

- **`getCarReverse()` is a dead stub on this ROM** — its case writes a literal `-1` and
  never touches the CAN layer. Reverse is only available pushed.
- **`extendedInterface(Bundle)` is not a getter.** It unpacks `data0`/`data1` and hands
  them to the CAN *writer* — it sends bytes to the vehicle bus. Never called from DWM.
  This is precisely the hazard v0.18's probe refused to walk blind.

Vendor APKs stay out of the repo (`extracted apps/` gitignored); the `.aidl` definitions
are committed, the binaries are not.

## 30. STATUS — Milestone 22 (2026-07-30): the dashboard, and what this van actually sends — v0.20.0 → v0.21.1

The arc that taught this project its most load-bearing design rule.

- **v0.20.0 — the dashboard shows the car.** Three cards (a cockpit hero, a favourites
  grid, a status card of dock duplicates) were deleted and replaced with three zones fed
  from `CarInfo`: DRIVE (speed, PRNDS strip, rpm, handbrake, lights), VEHICLE (one
  Compose `Canvas` carrying nine signals — doors, belts, indicators, per-wheel TPMS and
  all 16 radar sensors placed where they physically sit), VITALS (coolant, fuel,
  battery, steering). Every reading drawn whether or not the car had ever sent it,
  showing "—" until it arrived: deliberate, because which signals this van carries was
  unknown and a screen of dashes is the map that tells you. A 250 ms ticker samples
  volatile fields into immutable snapshots rather than piping binder callbacks into
  recomposition; `radar` is carried as `List<Int>` and never `IntArray`, or identity
  equality would repaint everything four times a second. Also `VehicleStripService`, a
  passive gear/speed/indicator strip over CarPlay — `FLAG_NOT_TOUCHABLE` is
  load-bearing, since an invisible bar that eats taps meant for CarPlay is the sort of
  bug that gets blamed on the head unit.
- **v0.20.1 — poll the getters.** First real run: CAN dot green, battery 12.7 V,
  headlights lit, every other tile a dash. The bind and the callback were fine — v0.20.0
  only ever *listened*, and this deck volunteers almost nothing, while the same readings
  sat behind 64 getters nothing was calling. Now polled once a second on a
  HandlerThread (binder calls block), merged with pushes, whichever produces a real
  value winning. `-1` is treated as absence throughout because that is what the vendor
  documents it to mean; the cost is that a genuine −1 °C ambient reads as unknown.
- **v0.20.2 — dump every getter.** Second "nothing works", with one decisive detail: two
  red blocks at the rear when reversing. That is live radar, so bind → poll → dashboard
  works end to end and the problem is *which signals exist*. Rather than act on the
  hypothesis, this shipped the measurement: `dumpGetters()` calls all 64 read-only
  methods once and prints each raw return tagged `[name]` or `[idx N]`.
- **v0.21.0 — build for the six readings this van actually sends.** The dump answered it
  with no ambiguity: **all 45 profile-indexed getters return −1** — gear, rpm, coolant,
  fuel, TPMS, doors, belts, ambient, oil, throttle, mileage, maintenance. No code change
  can conjure them. Six are real, every one name-resolved: voltage, headlight, speed,
  `getTrack` (steering, 240 = centre), turn signal, and radar while reversing — plus
  reverse from the audio-duck broadcast.
  *(Later precision: only **five** of those are readable as getters. `getRadar` polls −1
  in `DWM CAN getters.txt`; the 16 sensors arrive **pushed** via the `onRadar` callback
  during reverse. The commit's "six" conflated the poll and push paths. Both facts are
  true and the parking display is genuinely live — see `CLAUDE.md`.)*
  So the dashboard was rebuilt for those. Door
  outlines, boot, belt marks and TPMS corners were deleted: **drawing them was drawing a
  promise the van cannot keep.** Cosmetic deletions only — the AIDL layer is untouched
  and a different profile in the head unit's car-select app would bring the tiles
  straight back.
- **v0.21.1 — make the working parts look like they work.** The second photo read as
  "nothing works" and was mostly a *design* failure. All sixteen radar segments were
  rendering — which only happens when sixteen live values arrive — correctly reporting
  nothing-detected in an empty yard. But dim grey pills with no label are
  indistinguishable from a dead feature. They now say ALL CLEAR / STOP / CLOSE / NO
  SENSOR DATA, those three states having previously been impossible to tell apart.
  Speed and the steering dial size themselves off the card via `BoxWithConstraints`
  instead of fixed dp, and steering became an arc dial with a needle, because a flat
  5 dp bar at rest looks identical to a flat 5 dp bar that is broken.

**Turning the wheel while parked is still the fastest confirmation the CAN link is
alive** — steering is the only live reading on this van that moves with the engine off.

## 31. STATUS — Milestone 23 (2026-07-30 → 08-01): finding a design — v0.22.0 → v0.23.1

- **v0.22.0 — rebuild against the Ford SYNC reference.** User supplied a target screen.
  The CAN dashboard stopped being the main event — six readings never justified a full
  screen. Left column: speed, a Ranger drawn in Canvas, parking arcs. Right: a
  `HorizontalPager` of one-app-per-page. What the reference shows and this deliberately
  did *not* copy: tyre pressures, engine temperature, PRND, outside temperature, the
  climate bar — every one −1 on this van or absent from the interface. `GlassCard` and
  the blurred wallpaper were dropped for flat panels (one less full-screen blur on a
  low-RAM Unisoc). Media reads the real `MediaSession` through the notification listener
  DWM already ships. A Sketchfab glTF Ranger was offered and **declined**: live 3D means
  Filament or GLES on the weakest hardware here, plus an unverifiable licence in a
  public repo.
- **v0.22.1 — use the real render.** The hand-drawn Canvas truck was ugly; the risk had
  been flagged in the plan and shipped anyway, which was recorded as the part worth
  fixing in *how this goes*, not just in the code.
- **v0.22.2 — stop the app pages looking like placeholders.** A swipe page was one icon
  centred in an empty grey card. Each page now takes its identity from the app: the
  icon's dominant colour sampled once and bled across as a radial wash, skipping
  near-white and near-black pixels (most icons carry a lot of both, and averaging them
  drags every app toward the same lifeless grey). App lists moved to a background
  thread — rasterising and colour-sampling every installed icon on the UI thread made
  returning home stutter.
- **v0.23.0 — give it a design system, and drop the truck.** The launcher looked cheap
  and repainting would not have fixed why: there was no system to be consistent with —
  ~200 inline literals, three status colours redeclared in four files, and
  `Color.White.copy(alpha=)` written out about forty times, which is also why Light
  theme produced white-on-white. `ui/theme/` now holds one palette, one type scale, one
  spacing scale, one set of springs. Three things were measurably wrong: cards sat 18
  RGB points off the background (collapses to flat grey on this panel — now separated
  three ways at once, tonal step *and* gradient *and* hairline); labels were written at
  8 sp and then multiplied by `Scale.kt`'s 0.8 global density, rendering near 6.4 sp on
  a 13" screen; and one animation existed in 8,600 lines. The swipe pager was deleted —
  one app per page photographs well and is hostile while driving, since reaching the
  fifth favourite meant five swipes and five glances off the road. The Ranger render was
  deleted too: a rectangular crop of a photograph with road surface and a visible seam,
  upscaled from a 206 px source. Two attempts is enough. Reverse hands the left column
  to `ParkingDisplay` instead.
- **v0.23.1 — make the updater able to find its own updates.** v0.23.0 shipped correctly
  and the deck could not see it. Release live, `version.json` live and BOM-free, APK
  attached, repo public — and none of it mattered, because `Prefs.updateRepo` defaulted
  to an **empty string** and `Updater.check` bails on a blank repo before making a
  request. It now defaults to `Rivak96/DWM-`; the hint had also read "owner/DWM" while
  **the repository is actually named `DWM-` with a trailing hyphen**, so the obvious
  thing to type 404s. `autoUpdate` now defaults on, and a failed check names the repo it
  tried.

## 32. STATUS — Milestone 24 (2026-08-01 → 08-05): render it on a laptop — v0.24.0 / v0.25.0

**The single most important workflow change in the project.**

- **v0.24.0 — Paparazzi.** Every judgement about this UI had been made from a photograph
  taken after a build and a sideload. That was the actual problem. Paparazzi renders the
  composables to PNG on the JVM via LayoutLib, no device involved. The first render
  reproduced the reported photo exactly, which meant the rest of the release was found
  by *looking* rather than guessing: the favourites grid was written assuming twelve
  apps while this head unit exposes three, so `Row(weight(1f))` turned three icons into
  three full-height slabs with a dead fourth slot; sizing on width alone then broke the
  twelve-app case; the media card held a third of the screen to say nothing was playing;
  the CarPlay tile was small text against an acre of empty blue; track titles stopped
  mid-word. Also `MediaPanel` now skips its poll under `LocalInspectionMode` — off-device
  there is no notification listener, so the poll answered NoAccess and painted over the
  state the preview passed in, and the "now playing" snapshot was rendering the
  permissions prompt convincingly enough to nearly be believed.
- **v0.25.0 — one designer, one sitting.** The largest cause was invisible from the
  code: **`Prefs.uiScale` defaulted to 0.8** and `Scale.wrap` multiplies display density
  by it, so the canvas was ~2000×1250 dp rather than 1600×1000 and every token arrived
  20 % smaller than written. Four releases had been spent enlarging text inside a system
  that was shrinking it. Now **1.0**. `DwmTokens.kt` became the single source —
  `DwmPalette` holds ARGB ints both UI stacks adapt from, so a palette change cannot
  reach Compose and miss the XML screens. Four theme presets collapsed to one design in
  Day and Night variants, chosen by `CarInfo.headlight` with a clock fallback.
  `DwmContrastTest` now gates the palette on every build and immediately moved four
  values that looked fine by eye. No foreign icon is drawn anywhere — 25 vectors
  generated from one spec. Every reading got a designed no-signal state: an em dash in
  the mono face at full size and tabular width, so nothing moves when CAN starts
  talking. Paparazzi was corrected to render the real panel — **1920×1200 at 192 dpi
  with `useDeviceResolution = true`**, without which every golden was silently
  downscaled to a 1000 px edge.

## 33. STATUS — Milestone 25 (2026-08-05): Compose everywhere, and the pane cockpit — v0.26.0 / v0.27.0

- **Settings rebuilt in Compose.** It was the last screen that could not match, because
  it could not *share* anything: a second implementation of the rail, the grid, the type
  scale and the card, restyled at runtime by `Ui.skin()` walking the view tree remapping
  colours it recognised. A section header at `#8E8E93` — a colour in no palette — was
  never remapped and stayed mid-grey through every theme the app ever had. That is the
  failure mode the approach produces, so `activity_settings.xml` was deleted. All the
  logic is unchanged; only the wiring moved. `Prefs` stays the single source of truth,
  with a revision counter driving recomposition rather than seventeen `mutableStateOf`
  mirrors. Six controls now cover all sixty settings. The cockpit layout editor moved
  here.
- **Home rebuilt as a live cockpit (panes).** The previous home was a page of buttons —
  every element *about* content rather than being content. The top two thirds became
  panes: a pane is a slot, not an app, holding an ordered list of sources cycled by a
  swipe on its header. Sources reuse `PanelType` so the editor, `Prefs.panels` and
  `launchLayout` keep working. The distinction that drove the design: DWM-drawn sources
  render inside the activity and swap for free, while a live app is a separate task
  floating above it that DWM cannot draw over and never receives touches from — which is
  exactly why a pane has a header strip rather than being edge to edge, since a swipe
  started over a map would be eaten by the map. `WebPanelHost` landed here: exactly one
  WebView runs at a time, the rest held at `onPause` + `pauseTimers`. The repo
  previously had **no WebView lifecycle at all**. Accent moved to ice blue `#5FD3E8`.
- **Navigation moved to a bottom bar; the nav rail deleted.** The rail was the better
  ergonomic argument — RHD truck, right edge is the near edge — and it was not where the
  driver wanted the controls. **Where a control lives is the driver's call, not a
  geometry argument.** `SystemBar` replaces `NavRail` on both screens so home and
  Settings cannot diverge.
- **Open apps inside a pane, and add a wallpaper.** Tapping an app opened it fullscreen:
  `HomeActions.launch` still pointed at `launchFullscreen` from the previous design, so
  the pane rect was never used and the split cockpit could not actually be used to open
  anything — which made the whole pane system look decorative. Wallpaper picking existed
  in prefs since an earlier version but nothing ever dispatched the picker. Cards stay
  **fully opaque** deliberately: every contrast figure in this design was measured
  against a flat near-black field, and letting a photograph show through would make all
  of those numbers fiction. Decoding is downsampled to the panel — a 4000 px phone photo
  costs ~48 MB and the deck reports roughly **600 MB free of 1.8 GB**, measured on
  device for the first time. `OPEN_DOCUMENT` rather than `GET_CONTENT`, so the URI
  permission survives a reboot.
- **Ranger wallpaper bundled as the default** (1920×1200 WebP, 95 KB). The dim default of
  0.72 was wrong and the render showed it immediately: the photo is already nearly black,
  so dimming that hard produced black. Now 0.30. The wallpaper golden had also been a
  lie — layoutlib cannot decode WebP, so `decodeResource` returned null and the snapshot
  was pixel-identical to the plain one.

## 34. STATUS — Milestone 26 (2026-08-06→07): one app, one stage — v0.28.0 / v0.29.0

- **Vehicle scan hunts the 360 camera.** A 12-pin 360 kit needs a stitching module and a
  vendor app to draw the result, so if this deck supports one the app is installed
  *before* the cameras are. `VENDOR_HINTS` gained eight surround-view stems;
  `videoNodes()` stats `/dev/video*` (stat only, never opened); `cameraInputs()` lifts
  the Camera2 enumeration into the saved report, since that list is the one fact
  deciding whether a feed can be DWM-drawn and float over a fullscreen app, or whether
  the vendor app is the ceiling. Running it before the hardware arrives gives a baseline
  to diff against.
- **The pane cockpit was deleted.** Tested on the deck and rejected as extremely buggy —
  a fair verdict on the architecture, not the finish. A live app is a real freeform task,
  and `launchWindow` with `NEW_TASK` alone *moves* an existing task rather than
  duplicating it, so every divider drag, source swipe and split change relaunched a
  running app. Swiping a pane off an app parked its window off-screen using bounds this
  ROM was never proven to honour. And with ~600 MB free while a freeform app costs
  100–200, two apps plus a camera never really fit. So: one app, on a stage that reports
  its rect and never moves, with the freed space going to things DWM draws itself —
  which cost no window, cannot sink behind anything, and cannot be relaunched out from
  under you. Camera boxes take an **aspect ratio rather than a height**, because a box
  that does not match the feed either letterboxes or crops away the sides, and on a
  reversing camera the sides are where the bollard is. Now-playing and the favourites
  band went by request, taking `MediaStrip`, `Media.kt` and `QuickToggles` with them;
  the drawer grid was worth keeping and became `ui/AppGrid.kt`.
- **Only relaunch the stage app when it changes.** Running on an emulator without
  freeform showed the failure: if a window ever comes back fullscreen it covers the
  launcher, and `loadStage()` relaunched on every `onStart`, so returning to DWM threw
  you straight back into the app — and Back is swallowed because this is a home
  launcher, so the loop has no exit. A launcher that cannot be reached is not a failure
  worth leaving one ROM change away.

## 35. STATUS — Milestone 27 (2026-08-07): freeform deleted for good — v0.30.0 / v0.31.0

- **v0.30.0 — delete the freeform stage; the home screen app is a card.** The stage
  hosted the chosen app live via `ActivityOptions.launchBounds` plus a reflectively set
  freeform windowing mode. On the deck that produced three faults and **all three belong
  to SystemUI**: a caption bar with drag and close handles drawn inside the bounds, a
  window the user could drag out of position, and a z-order that let it sink behind the
  launcher. The rect was never right either — the caption bar was hidden by inflating
  bounds by a guessed 32 dp, which overhung the vehicle bar when the guess ran long and
  cropped the app when it ran short. **Freeform is Android's floating-window mode, so
  "stop it floating" and "keep the live window" were the same request pulling opposite
  ways. The window lost.** Every other door into the same mechanism was shut with it —
  Open in window, Raise windows, the editor's windowed-panel toggle, the Window
  title-bar fix setting — so the bugs cannot return through one. `Panel.fullscreen` and
  `isWindowedApp()` went too. A side benefit: the whole home screen is DWM-drawn now, so
  the goldens finally show it; the stage used to render as a black rectangle no renderer
  could see into.
- **v0.31.0 — the app box is a grid.** The box has now been three things: two live panes
  (rejected as extremely buggy), one live freeform app (three SystemUI faults), and a
  card for one chosen app (solid, but static — a tile that opened something rather than
  anything you could use). So it is the app grid, the honest version of what the last
  two were attempting: into an app in one tap. `AppGrid`/`AppTile` came back from
  `46773ee` rather than being rewritten. **Explicitly a stopgap** — the stock launcher
  `com.dofun.variety` hosts a live app in this slot through
  `cn.cardoor.desktop.window.DesktopWindowService`, which the deck's own scan reports as
  exported and unguarded. If that turns out to be bindable from DWM, a live app comes
  back here and the grid moves.

## 36. STATUS — Milestone 28 (2026-08-07): a real day theme — v0.32.0

Day mode never was one. Day was `#0A0C0F` and night `#06080A` — two near-blacks four
points apart — so the app had one appearance pretending to be two, and choosing Day
changed nothing anyone could see. A dark screen in sun is also a mirror showing you your
own dashboard, which is the opposite of what a daylight variant is for.

Day is now a light grey field, near-white cards, pure white raised, near-black text.
Night is untouched and is still the design this app was built around. Every value was
picked against `DwmContrastTest` rather than by eye; the binding constraint is RAISED at
pure white, where a foreground clearing 7:1 clears it everywhere. The support colours all
moved a long way, because a mid-tone that reads on near-black is invisible on near-white
— the ice blue manages 1.4:1 on white, so day uses the same hue at `#0A6A7C` holding
6.2:1.

Two things the old palette had hidden, both found only by making the variants differ:

- `PRESS` was one white-alpha wash for both, on the reasoning that alpha over whatever it
  sits on needs no variant. True only while both were dark. Day darkens now, night
  lightens.
- **`Ui.Theme.light` was hardcoded `false` in *both* branches.** It drives five things,
  including which Material style dialogs get — so every alert in the app was dark-styled
  regardless of theme.

`DwmTheme` now picks `lightColorScheme` vs `darkColorScheme` rather than always dark,
because M3 derives elevation overlays from which builder was used and would otherwise
apply dark-mode tinting to white surfaces. The old test `night is dimmer than day`
compared foregrounds, which only meant something while day was also dark; it was replaced
with the property it was really protecting — night surfaces stay dark — plus the
assertion that would have caught the fake day mode in the first place.

---

## 37. Where things stand (2026-08-08)

**Shipped:** v0.32.0 / versionCode 51. Home and Settings in Compose on `ui/theme/`
tokens; app grid, vehicle diagram, camera and readings row all DWM-drawn; overlay
panels and the floating pill unchanged from the View layer; CAN via the vendor's AIDL;
in-app OTA from `Rivak96/DWM-`.

**Known open:**
- The app box is a stopgap grid. `cn.cardoor.desktop.window.DesktopWindowService` in
  `com.dofun.variety` is exported and unguarded — if it is bindable, a live app returns.
  This is the biggest open lead in the project; `CLAUDE.md` carries the full evidence.
- **CarPlay resolution is wrong** (`com.zjinnova.zlink`), reported and never diagnosed.
  Unknown whether it predates v0.30.0 or is a regression from it.
- 360 camera: scan tooling is in place, hardware not yet fitted. Baseline before, diff
  after.
- Five CAN getters carry values on this van's profile (radar arrives pushed, separately).
  A different profile in the head unit's car-select app would bring the deleted tiles
  back with no code change.
- `Obd.kt` (ELM327) is still present and unused.
- Three overlay services and the dormant legacy panel system are still shipped. The user
  has been asked whether to delete them and has not decided — do not delete unasked.

**Development constraint lifted:** adb can now reach the deck from the dev machine, so
`logcat`, `install -r` and `dumpsys` are available. The *shipped* app must still be
zero-setup — §7's locked decision governs what ships, not how it is developed.
