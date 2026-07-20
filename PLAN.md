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

### Candidate next features (user asked "what else"; not yet built)
1. Media now-playing panel + controls (MediaSession; needs notification access).
2. Host real home-screen **widgets** as panels (AppWidgetHost).
3. JS data bridge: feed speed/OBD/time into Custom-HTML panels (custom gauges).
4. Multiple saved layout **profiles** (Day/Night/Highway) + quick switcher.
5. Auto day/night: dim wallpaper/brightness by sunset or headlights.
6. Trip computer panel (distance/avg speed from GPS).
7. Weather panel (needs a free API key).
8. Notification glance strip (needs notification access).

