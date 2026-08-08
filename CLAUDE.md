# DWM Cockpit

A zero-setup Android car-launcher for one specific head unit: a T3-style Topway/Dofun
deck in a right-hand-drive Ford Ranger. Personal project, sideloaded, no Play Store.

`PLAN.md` is the full running history — every milestone, what was tried and what was
rejected. Read it when you need the *why* behind something. This file is the part you
need before touching anything.

## The constraints that decide everything

**No ADB, no Shizuku, no root. Locked 2026-07-18 and never revisited.** Any design that
needs a privileged one-time action is off the table. The only elevated thing DWM uses is
the ordinary user-granted "Display over other apps" toggle.

**The deck lies about its API level.** `Build.VERSION.RELEASE` reports `"12"` but
`SDK_INT` is **29**. Anything gated on API 30/31 — `RenderEffect`, `Modifier.blur` —
compiles fine and then never runs on the actual device. Older notes in `PLAN.md` that say
"target API 31" predate this discovery and are wrong.

**Memory is tight.** ~600 MB free of 1.8 GB, measured on device. This is why Lottie and
`material-icons-extended` were both removed, why there is no 3D renderer, and why the APK
is kept near 6 MB. Adding a dependency needs a real argument.

**You cannot test on the device from here.** There is no deck attached to this machine.
See "How UI work actually gets verified" below — this is the single most important
workflow in the project.

## Build, test, release

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug          # or assembleRelease for a signed APK
.\gradlew.bat testDebugUnitTest      # Paparazzi goldens, contrast, AXML
.\gradlew.bat lintDebug              # keep this clean
```

**`JAVA_HOME` must be set in the shell**, even though `gradle.properties` already pins
`org.gradle.java.home` to Android Studio's bundled JBR (JDK 17). Those solve different
problems: the pin chooses the JDK Gradle *builds* with, but `gradlew.bat` needs a JVM to
boot before it ever reads `gradle.properties`, and there is no `java` on PATH here. The
symptom if you forget is `ERROR: JAVA_HOME is not set and no 'java' command could be
found in your PATH`, which looks like a missing JDK and is not one.

minSdk 26 · targetSdk 33 · compileSdk 34. `isMinifyEnabled = false` on purpose: R8 cannot
be runtime-tested here and a silently broken sideload is expensive to recover from.

Releasing is `RELEASING.md`. Two things that have each broken a release: the repo is
`Rivak96/DWM-` **with a trailing hyphen**, and `raw.githubusercontent.com` CDN-caches
`version.json` for ~5 minutes after a push — verify the live value with `gh api`, not a
browser. `version.json`'s `versionCode` must exactly match the built APK's.

`dwm-release.keystore` must never change or updates stop installing over the app. It and
`keystore.properties` are gitignored. Back them up.

## How UI work actually gets verified

Paparazzi renders the composables to PNG on the JVM via LayoutLib — no device involved.
Goldens live in `app/src/test/snapshots`, rendered at the real panel spec: **1920x1200 at
192dpi with `useDeviceResolution = true`** (without that flag every golden is silently
downscaled to a 1000px edge).

```powershell
.\gradlew.bat verifyPaparazziDebug   # fails the build if a change disturbs a golden
.\gradlew.bat recordPaparazziDebug   # re-record after an intentional visual change
```

**Look at the renders.** This is not ceremony. Before Paparazzi, every judgement about
this UI came from a photograph taken after a build and a sideload, and the guesses were
usually wrong. Since it landed, most visual defects have been found by opening the PNG:
tiles collapsing at three apps, album art squeezing a title to zero width, the vehicle
diagram drawn wider than long, a "wallpaper" golden that was pixel-identical to the plain
one because layoutlib cannot decode WebP. Reason about a layout only after you have seen
it fail.

`MediaPanel`-style polling must be skipped under `LocalInspectionMode`; off-device there
is no notification listener and the poll will paint over the state a preview passed in.

## Architecture

Compose + Material 3 for **home** (`ui/CockpitHome.kt`) and **Settings**
(`ui/SettingsScreen.kt`). Still framework Views: the app drawer, the layout editor, and
both overlay services — `res/layout/` holds only those four XML files.

`ui/theme/DwmTokens.kt` is the single source for colour, type, spacing, shape and motion.
`DwmPalette` holds ARGB ints that *both* UI stacks read, so a palette change cannot reach
Compose and miss the XML screens. One design in Day and Night variants, chosen by
`CarInfo.headlight` with a clock fallback.

`DwmContrastTest` gates the palette on every build. It exists because Day mode was
`#0A0C0F` against Night's `#06080A` for months — two near-blacks pretending to be two
themes. Pick colour values against the test, not by eye.

Accent is ice blue: `#4FB8CE` at night, `#0A6A7C` in day (the night value manages 1.4:1 on
white and cannot survive the inversion). The system bar owns the only accent-coloured
element on any screen — green, amber and red belong to the vehicle.

## The vehicle / CAN layer

`app/src/main/aidl/com/tw/carinfoservice/` holds the vendor's own `.aidl` files, extracted
byte-for-byte from `com.tw.carinfoservice.apk` off this deck. `buildFeatures.aidl = true`
is load-bearing: **AIDL numbers transactions by declaration order**, so generated stubs
cannot disagree with the running service the way hand-written codes would.

**This van reports six signals.** All 45 profile-indexed getters return `-1` permanently —
gear, rpm, coolant, fuel, TPMS, doors, belts, ambient, mileage. What is real: voltage,
headlight, speed, steering (`getTrack`, 240 = centre), turn signal, and radar while
reversing. Reverse itself comes from the `com.unisound.intent.action.DO_MUTE` audio-duck
broadcast, not the gear. Air conditioning is not in the interface at all.

Rules learned the hard way, all documented in `CarInfo.kt`:

- `-1` means absent everywhere. Never render a tile that can only ever show a dash — four
  releases were spent learning that an unfillable slot is worse than no slot.
- **`extendedInterface(Bundle)` is not a getter — it writes to the vehicle CAN bus.**
  Never call it. Same for `updateApk()`.
- `getCarReverse()` is a dead stub on this ROM; it writes a literal `-1`.
- Do not watch `system/revserse_status` for gear. It is a reverse-*camera* flag and reads
  1 while in DRIVE. This was tried and removed in v0.18.0.
- Poll on a HandlerThread. Binder calls block.

`VehicleProbe` is the discovery tooling (Settings → Vehicle): settings-store diff, manifest
scanning via a hand-rolled AXML reader, AIDL binding, getter dumps. Scan reports and getter
dumps are gitignored — they carry the deck's build fingerprint and this repo is public.

## Do not bring these back

- **Freeform windows.** Deleted in v0.30.0 after three separate attempts. The caption bar,
  the draggable window and the z-order that lets it sink all belong to SystemUI and are
  unreachable from DWM. Freeform *is* Android's floating-window mode, so "keep the live
  window but stop it floating" is self-contradictory. Every door into it was closed
  deliberately — Open in window, Raise windows, the windowed-panel toggle, the title-bar
  fix setting. The home app slot is `ui/AppGrid.kt` now.
- **`FLAG_ACTIVITY_MULTIPLE_TASK`.** Spawned duplicate tasks that the low-RAM deck then
  killed, which read as "apps close by themselves".
- **The two-pane cockpit.** Rejected on the deck as extremely buggy; two freeform apps plus
  a camera never fit in the memory budget.

## Editing rules

**Never edit `.kt` sources with PowerShell `-replace` or `Set-Content`.** Windows
PowerShell 5.1 reads UTF-8 as ANSI and double-encodes non-ASCII, which has corrupted this
codebase twice. Use the Edit/Write tools only.

Do not name a `SettingsActivity` theme handler `setTheme(Int)` — it collides with
`Activity.setTheme(int)`. The existing one is `applyThemePreset()`.

Commit messages in this repo are long and explain *why*, including what was rejected and
what is still unverified. `PLAN.md` is written in the same voice. Match it.
