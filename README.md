# DWM Cockpit

A zero-setup **Android car-launcher** for head units (built and tested on a T3-style
Android 12 deck). It turns one screen into a cockpit: run apps in resizable freeform
windows, float gauges/camera/web panels **over** a fullscreen app like CarPlay, and
drive it all from a clean Tesla-style UI.

> Personal project for my own head unit. No warranty. Sideload at your own risk.

## Features
- **Launcher** — set as default; auto-starts on boot.
- **Panels** — every layout cell can be: an app (freeform window or fullscreen base),
  the **AUX/camera** app, a **live Camera2** feed, a **web dashboard** (Home
  Assistant / Grafana / Node-RED), **custom HTML**, an **image**, a **clock**,
  **GPS speed**, or an **OBD-II gauge** (RPM / boost / coolant / …).
- **Two cockpit modes** — *Dashboard* (panels on the home canvas) and *Solo +
  overlays* (one fullscreen app with panels floating on top).
- **Overlay panels** — always-on-top gauges/camera/web with drag + resize grips.
- **Tesla-style theming** — Tesla gray / Midnight / Light, accent colours,
  adjustable text size, favourites grid + dock, floating translucent controls.
- **Freeform title-bar fix**, **keep-windows-on-top**, **floating pill**.
- **In-app self-update** from this repo's releases.

## Install
Download the latest `app-release.apk` from
[Releases](../../releases/latest) and sideload it (allow "install unknown apps").
Then in DWM: **Settings → System → Set DWM as default launcher**.

## Auto-update
**Settings → About → Set update repo** → enter `OWNER/REPO` (this repo).
Then "Check for updates" (or enable auto-check). Android shows a final install
prompt — silent installs require root, which DWM intentionally avoids.

## Build
JDK 17, Android SDK (platform 34). From the project root:
```
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```
Release signing reads `keystore.properties` (gitignored). See `RELEASING.md`.

## Tech
Kotlin, framework-only (no AndroidX/Compose) to stay light on old hardware.
minSdk 26 · targetSdk 33 · compileSdk 34.
