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

**No root, no adb, and the shipped app must stay zero-setup.** The only elevated thing DWM
uses is the user-granted "Display over other apps" toggle. `PLAN.md:7` says the user will
not do ADB; that is not just a preference any more — **the deck has no USB device port and
API 29 rules out wireless debugging, so there is no adb to do.** See the adb section below
before planning anything that assumes a shell. The one permission still worth reaching for
is `WRITE_SETTINGS` ("Modify system settings"), which is user-grantable like the overlay
toggle and would let DWM write `Settings.System` — `Settings.Secure`/`Global` need
`WRITE_SECURE_SETTINGS` and are closed.

## Build, test, release

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest      # unit tests only — see the warning below
.\gradlew.bat verifyPaparazziDebug   # unit tests AND the goldens. This is the real one.
.\gradlew.bat recordPaparazziDebug   # re-record goldens — the main tool, see below
.\gradlew.bat lintDebug
```

**`testDebugUnitTest` does not check the goldens**, despite running every snapshot test and
printing a Paparazzi report link. It renders them and compares nothing. Only
`verifyPaparazziDebug` compares, and it does work — proven by corrupting a golden and
watching it fail that one test by name. This file claimed otherwise for several releases,
and three commit messages went out saying "goldens verify" on the strength of a green
`testDebugUnitTest`. **Use `verifyPaparazziDebug` before shipping a visual change.**

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
| `ui/AppGrid.kt` | The app grid — what the box falls back to with no live app chosen. |
| `StageHost.kt` | **What the box's live app should be doing, decided in one place.** Pure logic, no `WindowManager` — hence `StageHostTest`. Read this before touching the stage. |
| `StageChrome.kt` | The overlay windows that mask the freeform caption and resize handles. Drawing only; policy lives in `StageHost`. Must never go fullscreen. |
| `LaunchEngine.kt` | **The only thing that launches apps.** Fullscreen, or into the box's rect as a freeform window. Also owns eviction. |
| `ui/theme/DwmTokens.kt` | The whole design system. Read before any visual change. |
| `CarInfo.kt` | Live vehicle data over the vendor CAN AIDL. |
| `VehicleProbe.kt` | ~1400 lines of rootless CAN/package/APK discovery tooling: `saveApk()`, manifest scanner, AIDL prober. |
| `Diagnostics.kt`, `DumpFlow.kt`, `GitHub.kt` | The dump button — collect, then gist or paste. **Reach for this before reasoning about a bug from a photograph.** |
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

## The stock launcher — settled, and it was freeform all along

The vendor launcher hosts a live, interactive app on its home screen. Reference frames sit
in the working copy as `ex 1.png` / `ex 2.png` but are **gitignored and will not be in a
clone**: they are frames from a third party's demo video, and one has a saved home address
on the Waze panel. Ask the owner for them if you need to look.

It is **`com.dofun.variety`** v9.7.2.367, and the owner's `lnfinite_car_launcher.apk` turns
out to *be* it. Read with `aapt dump xmltree`, its entire relevant permission set is
`SYSTEM_ALERT_WINDOW` + `WRITE_SECURE_SETTINGS`. No `MANAGE_ACTIVITY_STACKS`, no
`INTERNAL_SYSTEM_WINDOW`, no `INJECT_EVENTS`. **Nothing on Android puts an arbitrary app
inside a rectangle except freeform**, so the vendor launcher is doing exactly what DWM now
does. See the section below.

Two things not to re-investigate a fourth time: the 19 `com.dofun.variety.loader.p.*`
provider slots are RePlugin (`com.qihoo360.replugin`) loading **theme** plugins, confirmed
in `assets/plugins/kp.jar` — not an arbitrary-APK container. And
`cn.cardoor.desktop.window.floating.e/f` in `com.tw.video`/`com.dofun.recorder` is a
separate vendor-only *cooperative* SDK: those apps opt in, arbitrary apps cannot.

## The app box — one live app, in a freeform window

The big left box has been five things and this has burned several releases. Full history in
`PLAN.md` §33–§35 and in the v0.35.0/v0.36.0/v0.37.0 commit messages. Two live panes
(rejected on the deck as extremely buggy) → one live app in freeform (v0.29) → a static card
(v0.30) → an app grid (v0.31) → **one live app in freeform again, and working (v0.35+)**.

CLAUDE.md used to say "do not propose freeform again". That was right about the evidence and
wrong about the conclusion. v0.29 failed for want of **two levers it never pulled**:

- **`force_resizable_activities`**, a plain Developer-options toggle. Without it any app
  declaring `resizeableActivity=false` simply refuses freeform, and most do. This is the
  whole "the stock launcher hosts anything" difference. `Settings → Cockpit` reports the
  state; DWM cannot set it (`WRITE_SECURE_SETTINGS`).
- **Overlay masking.** A freeform window is dragged *by its caption*, so a touch-consuming
  `TYPE_APPLICATION_OVERLAY` over the caption removes the ugly bar and the grab handle in
  one move. `StageChrome` draws that, plus a frame over the resize outset. v0.29 instead
  inflated the launch rect by a guessed 32dp — the guess was the bug, not the idea.

The box still falls back to the app grid when no app is chosen, when freeform is
unavailable, or without the overlay permission. `Prefs.stagePkg` chooses.

**The one thing that must not come back is a fullscreen overlay.** v0.35.0 covered the live
window with a full-screen `TYPE_APPLICATION_OVERLAY` "curtain" whenever another DWM screen
came forward. That window type is above *every* activity window, DWM's own included, so it
covered the app drawer it was protecting and ate its touches — a dead light-grey panel with
the hardware Home key as the only exit. There is no z-order that fixes it. v0.37.0 replaced
it with **eviction**: relaunch the stage app *without* bounds so its task leaves the freeform
stack, then start the screen on top. `HomeActivity.openOverStage` and
`LaunchEngine.launchFullscreen` are the only two ways anything gets in front of home, and
both evict.

The alternatives that would give a genuinely *docked* pane — `TaskView`, a trusted
`VirtualDisplay`, programmatic split-screen — still need system signature permissions, and a
`VirtualDisplay` receives **no touch input** without root. Freeform plus masking is the only
unprivileged route, and it is the one the vendor took.

## The dump button — ask for this before reasoning

**When something on the deck is wrong, ask the owner to send a dump. Do not reason from a
photograph.** That is not a style preference: v0.37.0 and v0.39.0 each shipped a confident,
wrong answer to a real bug, derived by reading code against a photo, and each cost a
release. Both were four lines of log away from obvious.

Owner-side it is one tap — **Settings → About → Send a dump**, or a **long-press on the CAN
dot** on the home screen. The long-press is the one that matters when DWM itself is broken:
the v0.36.0 white screen made Settings and the drawer untouchable, which is exactly the
state a dump is wanted from.

How it gets to you, in order (`DumpFlow.kt`):

1. Always written to Downloads first, through `VehicleProbe.saveDump`. The van is on a phone
   hotspot as often as not, and a dump lost to a failed upload is worse than none.
2. A **secret gist** on the owner's account if signed in — `gh api /gists --jq '.[0]'` finds
   it and **the owner has to tell you nothing**.
3. Otherwise `dpaste.com`, and the owner reads you the short URL. Works with no setup at all.

**`GitHub.CLIENT_ID` is filled in as of v0.45.0** (`Ov23li9H6NMRyQH4lbA9`, on the owner's
account, `gist` scope only), so the gist path is live. It was blank for eleven releases,
which also made Settings → About's "Sign in" button dead UI that could only ever print "No
GitHub client ID is built into this version yet" — the owner read that as the dump button
being broken, which it never was.

**The deck must be signed in as `Rivak96`** for `gh api /gists --jq '.[0]'` to find the
dump. Signing the deck into any other account silently costs you the whole benefit: the
upload still works and you still have to be told the URL.

A client ID is public by design; **a client secret must never be added** — this repo and
every release APK are public, and `GitHubTest` fails the build if a field with "secret" in
its name appears. **"Enable Device Flow" must stay ticked** on the OAuth app; it is off by
default and its absence only bites at the last step, where the code is shown and then never
accepted. `curl -X POST https://github.com/login/device/code -H "Accept: application/json"
-d "client_id=...&scope=gist"` returns a real `user_code` when it is on — check that before
debugging anything else.

What a dump contains: `VehicleProbe.header`, the stage section (configured package, the
three freeform settings, overlay permission, caption height, and `StageHost.snapshot()`),
display geometry, redacted prefs, and DWM's own logcat. `StageHost.snapshot()` is the part
to read first — "the box shows its placeholder" has five causes that are identical on the
glass, and it names which one.

**What it cannot see**: anything belonging to the system. It answers *what did DWM do*,
never *what did the ROM do with the window*. `dumpsys window` and the full system log need
`DUMP` and `READ_LOGS` — see below.

### The upgrade that is not actually available

`DUMP` and `READ_LOGS` both carry Android's **`development`** protection flag, so
`adb shell pm grant com.dwm.cockpit android.permission.DUMP` can hand them to an ordinary
app, and this deck is API 29 — before Google tightened it. Declare them in the manifest,
grant them once, and the dump button thereafter carries the full system log and
`dumpsys window` / `activity` / `display` for good.

**It needs a shell, and this deck cannot give one** — see the section below. This was
written up as "one minute's work on a cable", offered to the owner and declined; the
declining turned out not to matter, because the cable was never there. Treat `READ_LOGS`
as closed unless a route to a shell appears. The practical consequence is permanent: the
dump answers *what did DWM do*, never *what did the ROM do*, and no amount of asking
changes that.

## adb is NOT available — the deck has no USB device port

**This section previously claimed the opposite and was wrong.** It said "the deck can be
reached over USB from this machine", and every session since has been steering toward a
diagnostic route that does not exist. Corrected 2026-08-16 against the evidence:

- **Windows has never enumerated an Android device on this machine.** All 28 USB vendor IDs
  in `HKLM\SYSTEM\CurrentControlSet\Enum\USB` are keyboards, controllers, hubs, a Bluetooth
  dongle and an audio device. No Google (`18D1`), no Unisoc/Spreadtrum, no `9BB5`.
- **`~/.android/adb_usb.ini` has `0x9BB5` hand-added to it** — exactly what someone does when
  trying to force an unrecognised head unit to enumerate. It never did. The only adb
  artefacts on the machine are emulator ones (`modem-nv-ram-5554`).
- The deck's USB ports are **host-only**. There is no device port to plug into.
- This is consistent with Open issues below, which have said for several releases that the
  cable session "has never happened".

**The network routes are closed too, and for a reason that will not change.** LADB and
`adb pair` both need Android 11+ Wireless Debugging, and **this deck is API 29** — it reports
`RELEASE == "12"` but `SDK_INT` is 29, the same fact that kills `RenderEffect` and
`Modifier.blur`. There is no pairing UI to use. `adb connect <ip>:5555` would need
`service.adb.tcp.port` already set in the ROM, and setting it needs the shell we are trying
to get.

**So the dump button is not the convenient route, it is the only route.** Everything below
that wants a shell has to be answered another way or not at all:

- `adb logcat -s DwmStage` and `-s DwmImmersive` still write their lines, and they still go
  into the dump — `Diagnostics` collects DWM's own logcat with no permission at all. What is
  lost is *other* processes' logs: the 360 app's crash is unreadable without `READ_LOGS`.
- `adb shell dumpsys activity activities` / `window` — **unavailable.** The open freeform
  questions below cannot be settled this way, and pretending otherwise has already cost
  time. `DwmActivity.snapshot()` and `StageHost.snapshot()` are what there is.
- `adb install -r` — unavailable. Releases go through the updater, as they always have.
- `adb pull` the vendor APKs — unnecessary: `VehicleProbe.saveApk()` copies any installed
  APK to Downloads from `publicSourceDir`, which is how the manifest scan already works.
- `adb shell pm grant … DUMP` / `READ_LOGS` — **unreachable**, so the "one minute's work on
  a cable" upgrade described above under the dump button is not one minute's work. It needs
  a cable that does not exist.

If a shell ever genuinely becomes worth it, the remaining options are a ROM that ships
`service.adb.tcp.port=5555`, or a factory/engineering screen that toggles network adb —
`Settings → Vehicle → 360 input format → Vendor screens` lists every vendor screen on the
deck and opens the ones Android permits, which is the cheapest place to look.

## Open issues

- **The stuck black bar is fixed blind in v0.48.0 and not confirmed.** Reported in v0.47.0:
  with a live app in the box, a full-width strip the height of a system bar appears across
  the top, over the DWM nav row and the vehicle card, and *stays after the system nav
  minimises*. The buttons under it still work — the pixels are stale, the window is not.
  Two causes were addressed at once because the owner asked to skip the diagnostic round:
  `styles.xml` painted the bars `#000000` (so a stuck strip is an opaque slab on the
  `#E4E9EF` day background — now transparent, which helps whichever window owns the bar),
  and `goImmersive()` had exactly one trigger, focus-gain, which the freeform case never
  fires because touching the stage app moves focus away and it never comes back
  (`DwmActivity` now re-asserts on a 2.5s tick bracketed by `onStart`/`onStop`). **The
  re-assert may do nothing**: bar visibility is governed by the *focused* window. If the
  strip is still there, the dump's new `---- window ----` section
  (`DwmActivity.snapshot()`) and `adb logcat -s DwmImmersive` separate the two — wanted ≠
  found means the ROM cleared DWM's flags; a non-zero top inset with the flags intact means
  the ROM believes a bar is up regardless. Do not re-derive this from a photograph.
- **The ROM remembers freeform bounds per task, and that outlives DWM's stage setting.**
  The owner set zlink as the stage, hand-resized the window while fighting it, then cleared
  the stage — and zlink still opens in the hand-drawn rect, with `stage_pkg` unset and the
  box showing the app grid behind it. So "the app is in the box" does **not** imply DWM put
  it there, and a photo of a live app in the box proves nothing about the stage. Check
  `Prefs.stagePkg` in a dump before assuming. It also means a hand-resized rect can be
  *smaller* than the box, which shows as an unexplained band of empty card above the app —
  reported as "a huge margin at the top". `Settings → Cockpit → Show box outline` settles
  which rect is which in one photo.
- **The stage has never been checked against `dumpsys`, and now cannot be.** Everything
  about how this ROM treats a freeform launch is still inferred. Four questions: does it
  honour the launch bounds; is the caption drawn *inside* the requested rect or outside it
  (`Prefs.captionDp` starts at 32 and the band above the app measured nearer 55dp in the
  owner's photo, so probably not); does a bounds-less relaunch really move the task out of
  the freeform stack, which is what all of v0.37.0 rests on; and does
  `force_resizable_activities` make zlink relaunch on a configuration change, which would be
  the remaining half of the reported flicker. **This used to say "answerable in one sitting
  on the cable" — there is no cable.** They are answerable only by what DWM can observe
  itself: the box's own rect, `StageHost.snapshot()`, and `Settings → Cockpit → Show box
  outline`. Do not plan around `dumpsys`.
- **CarPlay resolution is wrong.** Reported as "the resolution of think5 carplay is so off"
  and never diagnosed. The app is `com.zjinnova.zlink`. Unknown whether it predates v0.30.0
  (where windowed launching became fullscreen) or is a regression from it. `dumpsys display`
  is not available; the cheap discriminator that is, and that has never been asked for, is
  **whether it looks right when launched from the stock launcher** — if yes, it is DWM's
  launch path or `Scale.kt`'s density wrapper, and if no, it is the app or the ROM.
- **The vehicle diagram card is a reserved slot, not dead space — stop offering to delete
  it.** It is the emptiest card on the screen and it looks exactly like the "a tile that can
  never fill" this file warns about: doors and all four TPMS values are permanent em dashes
  and the status word is a hardcoded ALL CLEAR. It has been offered as a deletion twice and
  refused twice. The reason, given plainly the second time: **the owner is holding that
  region for the live 360 bird's-eye feed** once the hardware is fitted. Asking a third time
  is not diligence, it is not having read this.
- **360 camera: hardware is fitted, and the input format is wrong.** Four AHD fisheye
  cameras on the 12-pin, no stitching box — the deck's own 360 module dewarps and stitches.
  The module, decoder and app all work: the vehicle model, guidelines and view switching
  render correctly. But the cameras are **fixed AHD 1080P** and the deck decodes
  **`MODE_720P_25FPS`** (About → Hardware information), so the video layer is a dense
  horizontal stripe pattern — the textbook AHD timing mismatch, and the one the kit's own
  manual documents. This firmware build ships **no format selector anywhere**; the
  "Reversing system" menu the manual and the seller both show does not exist here. Factory
  (123456) has the `360panorama` source toggle and nothing else relevant. Corroborated on
  XDA by TS18 owners with the identical symptom.

  Replacement switchable 720P/1080P cameras are on the way, so this is time-boxed. **A clean
  negative is a useful result.** The `---- camera ----` dump section (shipped in v0.49.0) is what
  settles it: it reads the property store (`getprop`, which DWM had never looked at), all
  three settings tables, and **every vendor activity including non-exported and disabled ones** —
  `manifestScan` only sees components with an `<intent-filter>`, so a factory screen reached
  by explicit `ComponentName` was invisible to everything this app had. The section states
  its own verdict, because only two answers are actionable without a shell: an **exported,
  enabled, unguarded** vendor screen (DWM starts it, the vendor's own code does the write —
  `Settings → Vehicle → 360 input format → Vendor screens`), or a **`Settings.System`** key
  (writable under a granted `WRITE_SETTINGS`). A property, a `Secure`/`Global` key, a
  non-exported activity, or the MCU all mean no.

  Two things not to re-derive: **the seller must confirm the replacements are 25fps** — a
  720P **30**FPS camera reproduces the identical stripes — and the plain reverse camera is a
  separate signal path that already handles higher formats, so one existing 1080P camera
  works as an ordinary rear camera today. **Do not take an OTA as a fix attempt**: OTAs are
  known to remove the 360 option on TS18 with no clean downgrade.

## Pitfalls that have already bitten

- **Never edit `.kt` sources through PowerShell.** PS 5.1 misreads UTF-8 as ANSI and
  rewriting a file through `-replace`/`Set-Content`/`Get-Content -Raw` produces mojibake.
  This has corrupted source files twice. Use the Edit/Write tools only.
- **PowerShell here-strings passed to `git commit -m` get mangled.** Write the message to a
  file and use `git commit -F`.
- **Never add `FLAG_ACTIVITY_MULTIPLE_TASK`.** It spawns duplicate app copies that the
  low-RAM deck then kills — this presented as "apps closing by themselves". `NEW_TASK` alone
  reuses one task.
- **Anything that reaches `launchFullscreen` takes the stage down with it**, because that is
  where `evictStage` lives. That is correct for a user opening an app and wrong for anything
  automatic. The startup autoload (`Prefs.autoLoad`, **defaults true**, guarded by a
  per-process `didAutoLoad`) did exactly this 700ms after every cold start, and since
  installing an APK *is* a cold start, the box came up empty on the owner's first run of two
  releases running. It is skipped when a stage app is set. Check the same thing before
  wiring any other automatic launch — `CameraPanel`'s fallback app is the other one.
- **The box's rect is not final on the first layout pass.** It is reported again after
  `goImmersive()` takes the system bars out of the window. Anything that acts on it must
  wait for it to settle (`HomeActivity.scheduleStageLaunch`) rather than committing to the
  first value — v0.37.0 committed, and the window was placed against a box that had moved.
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

**On a bug report from the van, ask for a dump first.** The tempting move is to read the
code against the photo and produce an explanation, because that has usually worked here and
because it feels like the fast path. It is the slow path: it produced two wrong releases in
a row, and the owner has said plainly that shuttling files by hand is not acceptable, which
is why the button exists. One tap, and then you are reading facts. A guess that is right
still teaches the next session to guess.
