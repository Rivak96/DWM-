# DWM Cockpit

A zero-setup **Android car-launcher** for head units (built and tested on a T3-style
Unisoc deck that reports Android 12 but is really API 29). It turns one screen into a
cockpit: live vehicle instrumentation and a camera drawn on the home screen, gauges and
web panels floating **over** a fullscreen app like CarPlay, in one flat day/night UI.

> Personal project for my own head unit. No warranty. Sideload at your own risk.

## Features
- **Launcher** — set as default; auto-starts on boot.
- **Cockpit home** — an app grid, a live vehicle diagram, a camera feed and a row of
  vehicle readings, all drawn by DWM so nothing can sink behind another window.
- **Vehicle readings** — speed, voltage, steering, headlights, indicators and 16 parking
  sensors, read over the deck's own CAN service via the vendor's AIDL. Reverse promotes a
  proximity display with a steering-predicted guide path.
- **Overlay panels** — always-on-top camera / web / gauge / clock / notification cards
  that float over a fullscreen app like CarPlay, with an optional drag + resize mode.
- **Floating pill** — collapsible always-on-top launcher for favourites.
- **Theming** — Day / Night / Auto (Auto follows the headlights, falling back to the
  clock), plus wallpaper and interface scale.
- **In-app self-update** from this repo's releases.

## Install
Download the latest `app-release.apk` from
[Releases](../../releases/latest) and sideload it (allow "install unknown apps").
Then in DWM: **Settings → System → Set DWM as default launcher**.

## Auto-update
On by default and already pointed at this repo — **Settings → About → Check for
updates**, or leave auto-check on and it looks on start. Point it elsewhere with
**Set update repo** (`OWNER/REPO`). Android shows a final install prompt; silent
installs require root, which DWM intentionally avoids.

## Build
JDK 17, Android SDK (platform 34). `JAVA_HOME` must point at a JDK 17 — on Windows,
Android Studio's bundled JBR (`C:\Program Files\Android\Android Studio\jbr`). From the
project root:
```
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest # Paparazzi goldens, contrast and AXML tests
```
Release signing reads `keystore.properties` (gitignored). See `RELEASING.md`, and
`CLAUDE.md` for the constraints and gotchas behind the design.

## Tech
Kotlin. Home and Settings are Jetpack Compose + Material 3 on a small hand-rolled token
layer (`ui/theme/`); the app drawer, the layout editor and the overlay services are
framework Views, restyled at runtime from the same palette. UI changes are verified with
Paparazzi renders on the JVM rather than on the deck. Dependencies are kept deliberately
thin for a low-RAM Unisoc chip.
minSdk 26 · targetSdk 33 · compileSdk 34.

Note the deck reports `Build.VERSION.RELEASE` as "12" while `SDK_INT` is **29**, so
anything gated on API 30/31 — `RenderEffect`, `Modifier.blur` — never runs on it.
