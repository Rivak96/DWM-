# DWM Cockpit

A replacement home launcher for a Chinese Android car head unit, installed in the owner's
van. Registers `CATEGORY_HOME`, auto-starts on boot, replaces the vendor launcher. Personal
project for one specific vehicle — no users but the owner.

Repo `Rivak96/DWM-` (**trailing hyphen**) · package `com.dwm.cockpit` · branch `main`.

`PLAN.md` is the full milestone history — read it for the *why* behind a past decision. The
`.kt` header comments are unusually detailed and are the current source of truth for any
individual file. This file is what you need before touching anything.

## Things git does not bring with it

All gitignored, all present on this machine, all must be hand-copied to any new one:

| File | Why it matters |
|---|---|
| `dwm-release.keystore` | **The critical one.** Android refuses an update signed with a different key. Losing it means uninstall + reinstall on the deck, losing every setting. Back it up somewhere that is not this machine. |
| `keystore.properties` | Passwords/alias for the above. Without it `assembleRelease` silently produces an *unsigned* APK that will not install. |
| `local.properties` | Android SDK path. Regenerate by opening the project in Android Studio. |
| `extracted apps/` | Vendor APKs pulled off the deck. Re-pullable, but they are evidence. |

`gradle.properties` hardcodes `org.gradle.java.home=C:/Program Files/Android/Android
Studio/jbr`. **That path is machine-specific — fix it on a new machine or every Gradle
command fails.** The pin exists because AGP 8.7.3 wants JDK 17 and the JDK on PATH was 23.

## The deck — hardware truths that constrain everything

- **Reports Android 12, is actually API 29.** `Build.VERSION.RELEASE` says `"12"` but
  `SDK_INT` is 29. Anything gated on API 30/31 silently never runs: `RenderEffect`,
  `Modifier.blur`, glassmorphism. Several dead-looking code paths exist for this reason.
- **Unisoc SC9863A, Mali-G51, ~600 MB free of 1.8 GB.** One app in a window costs 100–200 MB.
  Two live apps plus a camera does not fit — tested and rejected.
- **1920×1200 px at 192 dpi → a 1600×1000 dp canvas.** Landscape, `sensorLandscape`.
- **Glossy IPS read through a windscreen in Trinidad daylight.** It lifts blacks badly and
  washes out at the top. Hairlines and contrast are pushed harder than looks right on a desk
  monitor, deliberately.

**No root, and the shipped app must stay zero-setup for normal use.** The only elevated
thing DWM uses is the user-granted "Display over other apps" toggle. `PLAN.md:7` says the
user will not do ADB — that is now obsolete *for development* (see below) but still governs
what ships.

## Build, test, release

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest      # unit + Paparazzi golden verification
.\gradlew.bat recordPaparazziDebug   # re-record goldens — the main tool, see below
.\gradlew.bat lintDebug
```

**`JAVA_HOME` must be set in the shell** even though `gradle.properties` pins
`org.gradle.java.home`. Those solve different problems: the pin chooses the JDK Gradle
*builds* with, but `gradlew.bat` needs a JVM to boot before it can read that file, and there
is no `java` on PATH. The symptom is `ERROR: JAVA_HOME is not set`, which looks like a
missing JDK and is not one.

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.11.1 · Compose BOM 2024.12.01 · Paparazzi 1.3.5 · JDK 17.
compileSdk 34 · minSdk 26 · targetSdk 33. `isMinifyEnabled = false` on purpose — R8 cannot be
runtime-tested here and a silently broken sideload is expensive to recover from.

**Paparazzi is pinned at 1.3.5**: the last line targeting Kotlin 2.0.21 on JDK 17. 2.x needs
JDK 21+. Do not bump it casually.

Releasing is `RELEASING.md`, followed exactly. `version.json`'s `versionCode` must match the
built APK's, and `raw.githubusercontent.com` CDN-caches it ~5 minutes after a push — verify
the live value with `gh api`, not a browser. `release.ps1` writes `version.json` as UTF-8
**without BOM** deliberately: the updater does `JSONObject(bytes.toString(UTF_8))` and a
leading U+FEFF makes org.json throw.

## How UI work gets verified — use the goldens

Paparazzi renders the composables to PNG on the JVM via LayoutLib at the real panel geometry
(**1920×1200 @ 192 dpi, `useDeviceResolution = true`** — without that flag every golden is
silently downscaled to a 1000 px edge). Goldens live in `app/src/test/snapshots/`.

This exists because of one repeated failure: every visual change used to be judged from a
photograph taken after a build, a sideload and a walk out to the van, and the guesses in
between were wrong often enough to cost several releases. **Record the goldens and actually
open the PNGs before shipping any visual change.** Most visual defects since have been found
by looking, not reasoning — tiles collapsing at three apps, album art squeezing a title to
zero width, a "wallpaper" golden that was pixel-identical to the plain one because layoutlib
cannot decode WebP.

Polling composables must skip under `LocalInspectionMode`; off-device there is no
notification listener and the poll will paint over the state a preview passed in.

## Architecture

Two UI stacks, both deriving colour from `ui/theme/DwmTokens.kt` so a palette change cannot
reach one and miss the other:

- **Compose** — home screen and Settings (`ui/`, `ui/theme/`).
- **Framework Views** — app drawer, layout editor, overlay services. Restyled at runtime by
  `Ui.skin()`.

| File | Role |
|---|---|
| `HomeActivity.kt` | The launcher home. State, feeds, permissions, legacy drawn-panel canvas, updater. Swallows Back (it is a home launcher). |
| `ui/CockpitHome.kt` | Home layout. Read its header comment first — it carries the layout history. |
| `ui/AppGrid.kt` | The app grid filling the big left box. |
| `LaunchEngine.kt` | **The only thing that launches apps.** Fullscreen only, by design. |
| `ui/theme/DwmTokens.kt` | The whole design system. Read before any visual change. |
| `CarInfo.kt` | Live vehicle data over the vendor CAN AIDL. |
| `VehicleProbe.kt` | ~1400 lines of rootless CAN/package/APK discovery tooling: `saveApk()`, manifest scanner, AIDL prober. |
| `Prefs.kt` | All persisted state. |
| `Overlay*Service.kt`, `VehicleStripService.kt` | Three floating-window services (`SYSTEM_ALERT_WINDOW`). Still present and running. |
| `LayoutEditorActivity.kt`, `Panel.kt`, `Templates.kt` | Legacy panel/canvas system. Largely vestigial, kept dormant. |

**Do not delete the overlay services or the legacy panel system without asking.** The user
has been asked and has not decided.

## Design system — rules that must not be broken

`DwmTokens.kt` states these itself:

- **Every colour, size, radius and duration comes from the token files.** No call-site
  literals, ever. If a value is missing, add it there with a rationale comment.
- **One accent, on one element per screen.** `ACCENT` is the bottom nav bar's travelling bar
  and nothing else. Never for buttons, never for emphasis.
- **`OK`/`WARN`/`CRITICAL` are semantic and belong to the vehicle.** An orange pixel means
  something is wrong with the van. Never decoration.
- **Two variants, day and night, chosen by fact not preference** — `Ui.night()` reads the
  headlights, falling back to a clock.
- **Depth is a tonal step plus a hairline.** No shadows (invisible on this panel), no blur
  (API 31). No card inside a card.
- **`DwmContrastTest` enforces the palette on every build** — 7:1 text, 4.5:1 support, 1.45:1
  hairlines, both variants. If it fails, the palette is wrong, not the test.
- **`res/values/colors.xml` duplicates the day palette by hand** and must be kept in step with
  `DwmTokens.kt`. It has silently drifted before, sitting on an abandoned preset for two
  releases.

## CAN data — five getters work, and that is all

Evidence: `DWM CAN getters.txt`, a real on-deck dump calling all 64 read-only getters once.

**Carry values (all name-resolved):** `getElectricVoltage` · `getHeadlight` ·
`getInstantaneous_Speed` · `getTrack` (steering, 0–480, centre 240) · `getTurn_Signal`.

**Everything else is `-1`** — gear, rpm, coolant, ambient, fuel, all four TPMS
pressures/temps/warnings, doors, belts, throttle, MAP, engine load, mileage, maintenance.
Every profile-indexed getter. `getHandbrake` is name-resolved but dead on this van. Air
conditioning is not in the interface at all; climate lives in another vendor app.

Two nuances that have caused confusion:

- **`getRadar` polls `-1`, but radar is real.** The 16 sensors arrive *pushed* via the
  `onRadar` callback during reverse only. Poll and push are separate paths — do not conclude
  from the getter dump that the parking display is dead.
- **Reverse comes from a broadcast, not the gear.** `com.unisound.intent.action.DO_MUTE`
  carries a live `reverse` extra — the deck ducking its own audio and naming the reason.
  `getCarReverse()` is a stub that always returns `-1` on this ROM.

**Consequence: do not add vehicle gauges.** The vehicle bar already shows readings that are
permanent em dashes. More CAN tiles means more dashes. A tile that can never fill is worse
than no tile — four releases were spent learning this.

**Never call `extendedInterface(Bundle)`** — it is not a getter, it *writes bytes to the
vehicle bus*. Same for `updateApk()`.

Working alternatives with no vendor dependency: GPS speed (`LocationManager`), or OBD-II over
an ELM327 dongle (`Obd.kt` — written, never verified, no dongle yet).

## The big open lead — the stock launcher

The vendor launcher **hosts a live, interactive app on its home screen with no bugs** — a
live camera feed and Waze side by side, over a widget dock carrying speed, media and TPMS.
Reference frames sit in the working copy as `ex 1.png` / `ex 2.png` but are **gitignored and
will not be in a clone**: they are frames from a third party's demo video, and one has a
saved home address on the Waze panel. Ask the owner for them if you need to look.

It is **`com.dofun.variety`** (DoFun/CarDoor "Variety Theme"; the user calls it "Themes") and
it does **not** use AOSP freeform. From the deck's own scan:

```
com.dofun.variety
    service  cn.cardoor.desktop.window.DesktopWindowService            [exported] [UNGUARDED]
    service  cn.cardoor.desktop.floating.app.window.FloatingAppService  [exported] [UNGUARDED]
    activity cn.cardoor.desktop.window.DesktopWindowAppSetting
             action cn.cardoor.desktop.window.intent.action.WINDOW_APP_SETTING
```

Exported and unguarded means bindable by any app with no permission. It also declares 19
private provider slots (`com.dofun.variety.loader.p.*`) — a VirtualApp/RePlugin-style plugin
container, likely how it hosts arbitrary APKs. The *client* half of the protocol is embedded
in `com.tw.video` and `com.dofun.recorder` as `cn.cardoor.desktop.window.floating.e/f`.

Next steps, cheapest signal first: bind it with `VehicleProbe.AidlProbe` and read the Binder
descriptor (names the interface without decompiling); fire `WINDOW_APP_SETTING` to see
whether the vendor's window picker still opens with DWM as home; then pull the three APKs
(`adb pull`, or DWM's own Settings → Vehicle → Export APK) and decompile with **jadx, which
is not installed — install it**.

**Honest risk:** the service probably renders on behalf of the DoFun launcher specifically
and may do nothing useful when DWM is home. Unknown until tested. If it works, a live app
returns to the box and the grid goes.

## The app box saga — read before proposing a live app

The big left box has been four things, and this has burned several releases. Full history in
`PLAN.md` §33–§35. The short version: two live panes (rejected on the deck as extremely
buggy) → one live app in a freeform window (v0.29) → a static card (v0.30) → **an app grid
(v0.31, current, explicitly a stopgap)**.

**Do not propose freeform again.** Its three failures — a caption bar drawn inside the
bounds, a window the user can drag out of position, a z-order that lets it sink behind the
launcher — all belong to SystemUI and are unreachable from DWM. Freeform *is* Android's
floating-window mode; "make it not float" and "keep the live window" are the same request
pulling opposite ways. Every other door into windowed launching was deliberately shut so the
bugs cannot return through one.

The alternatives that would give a genuinely docked pane — `TaskView`, a trusted
`VirtualDisplay`, programmatic split-screen — all need system signature permissions, and a
`VirtualDisplay` receives **no touch input** without root. The stock-launcher lead above is
the only live route.

## adb is available for development

The deck can be reached over USB from this machine (`adb` is at
`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`). This lifts a constraint that shaped
years of design. **The shipped app must still be zero-setup**, but development is no longer
blind:

- **`adb logcat`** — DWM has never had real crash output; everything was diagnosed from
  screenshots and reasoning. The single biggest win.
- **`adb install -r`** — iterate without the bump/build/push/release round trip. Sign with the
  same keystore; a debug-signed build will not install over the release-signed one.
- **`adb pull`** the vendor APKs for the lead above, in seconds.
- **`adb shell dumpsys activity activities` / `window`** — observe how `com.dofun.variety`
  actually hosts its window. May answer the lead outright.

## Open issues

- **CarPlay resolution is wrong.** Reported as "the resolution of think5 carplay is so off"
  and never diagnosed. The app is `com.zjinnova.zlink`. Unknown whether it predates v0.30.0
  (where windowed launching became fullscreen) or is a regression from it. With adb, check
  `dumpsys display` and `logcat` while launching zlink, and ask whether it looks right from
  the stock launcher — if yes, it is DWM's launch path or `Scale.kt`'s density wrapper.
- The app grid is a stopgap pending the stock-launcher lead.
- 360 camera: scan tooling is in place, hardware not yet fitted.

## Pitfalls that have already bitten

- **Never edit `.kt` sources through PowerShell.** PS 5.1 misreads UTF-8 as ANSI and
  rewriting a file through `-replace`/`Set-Content`/`Get-Content -Raw` produces mojibake.
  This has corrupted source files twice. Use the Edit/Write tools only.
- **PowerShell here-strings passed to `git commit -m` get mangled.** Write the message to a
  file and use `git commit -F`.
- **Never add `FLAG_ACTIVITY_MULTIPLE_TASK`.** It spawns duplicate app copies that the
  low-RAM deck then kills — this presented as "apps closing by themselves". `NEW_TASK` alone
  reuses one task.
- **Do not name a `SettingsActivity` theme handler `setTheme(Int)`** — it collides with
  `Activity.setTheme(int)`. The existing one is `applyThemePreset()`.
- **A variant flag that never varies hides for months.** `Ui.Theme.light` was hardcoded
  `false` in *both* branches and drove five things including dialog styling; Day mode was
  `#0A0C0F` against Night's `#06080A`. Both fixed in v0.32.0. When you add a variant, add the
  test that proves the variants differ.

## Working practice

Bias toward small, releasable increments — the user ships often and tests in the van. Verify
before claiming, and say plainly what was tested on the JVM versus what still needs the deck.
When a term could mean two things that imply opposite work, ask briefly rather than guessing;
ambiguous words have cost real time here ("overlay" has meant both the app box and the
overlay services). Commit messages in this repo are long and explain why, including what was
rejected and what is still unverified — match that.
