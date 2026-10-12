# iOS Home Launcher

An Android launcher (Kotlin + Jetpack Compose) that recreates the look and feel of a modern iOS home screen —
layout, Liquid Glass materials, spring physics and timing — plus a two-page **Duo style** for foldables.

> Not affiliated with or endorsed by Apple. No Apple assets are used: the UI font is [Inter](docs/Inter-OFL.txt)
> (SIL OFL), all glyphs, widgets and wallpapers are drawn in code, app icons come from Android.

## Features

**Home screen**
- 4×6 icon grid, dock, page dots, rubber-band paging, edit mode with jiggle, drag & drop, folders, page overview
- Context menus with app shortcuts, widget stacks with swipe and *Smart Rotate*
- Liquid Glass (refraction, rim light, blur) on dock, folders, menus and search
- Today view, App Library with categories and A–Z list
- Own status bar (optional), haptics (optional)

**Duo style (foldables)**
- Unfolded: two pages side by side, vertical dock column with search button on the right
- Folded: single page with the dock column; layout scales to the device so other foldables
  (Galaxy Z Fold, Pixel Fold, …) keep the same proportions
- Status symbols avoid the camera cutout automatically

**Widgets** — calendar, clock, batteries, weather (Open-Meteo, location or fixed place), photos, plus any Android widget

**Search** — apps, calculator, contacts, Android settings, photos (name, album, month/year), files and web suggestions
(Google suggestions can be turned off in Launcher Settings)

**Smoother app animations (optional)** — with root, the included Magisk module installs the launcher as a system app
so apps open straight out of their icon.

## Requirements

- Android 13 (API 33) or newer
- For the Duo style: a foldable (hinge sensor)
- Optional: root + Magisk for the open animation

## Install

1. Download the APK from [Releases](../../releases) and install it.
2. Open it and follow the welcome flow; choose it as the default home app.
3. Optional (root): flash `ioshome-magisk.zip` in Magisk → Modules, reboot.

## Build

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Magisk module: `tools/build-magisk-module.sh` → `build/ioshome-magisk.zip`.

Project notes: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), measurements and motion values with sources in
[`docs/RESEARCH.md`](docs/RESEARCH.md), glass rendering in [`docs/GLASS.md`](docs/GLASS.md).

## Privacy

Everything runs on the device. Network is only used for the weather (Open-Meteo) and — if enabled — search suggestions
(Google). Usage access, contacts, calendar, photos and location are optional and only used for the matching feature.

## How this was made

Built by janhurn9400 with AI pair programming (Claude). Motion and layout values were measured frame by frame from
screen recordings and compared side by side against the emulator.

## Status

Early preview (0.x). Expect rough edges, especially on real hardware performance.
