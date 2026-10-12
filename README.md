# Liquidy

A faithful recreation of a modern iOS home screen for Android (Kotlin + Jetpack Compose) — not a theme or an icon pack,
but a rebuild of how it *behaves*: the same grid geometry, the same spring curves and timings, the same Liquid Glass
refraction and rim light, the same way folders open, widgets snap, pages rubber-band and menus grow out of their buttons.
Plus a two-page **Duo style** for foldables and tablets.

Nearly every value in this project was measured, not guessed: screen recordings of real devices were analysed frame by
frame (positions, sizes, colours, motion per frame at 60–240 fps), turned into documented tokens with their source
([`docs/RESEARCH.md`](docs/RESEARCH.md)), and then checked by laying emulator captures side by side with the reference
until they matched — including the in-between frames of every transition, not just the end states.

> Not affiliated with or endorsed by Apple. No Apple assets are used: the UI font is [Inter](docs/Inter-OFL.txt)
> (SIL OFL), all glyphs, widgets and wallpapers are drawn in code, app icons come from Android.

## Features

**Home screen**
- 4×6 icon grid, dock, page dots, rubber-band paging, edit mode with jiggle, drag & drop, folders, page overview
- Context menus with app shortcuts, widget stacks with swipe and *Smart Rotate*
- Liquid Glass (refraction, rim light, blur) on dock, folders, menus and search
- Today view, App Library with categories and A–Z list
- Own status bar (optional), haptics (optional)

**Duo style (foldables & tablets)**
- Two pages side by side with a vertical dock column and round search button on the right — on an unfolded foldable
  or a tablet in landscape
- Folded: single page with the dock column
- The whole layout scales to the screen so every device keeps the same proportions — Galaxy Z Fold, Pixel Fold,
  10–13" tablets — instead of squeezing or stretching icons
- Status symbols avoid the camera cutout automatically
- Tablets in portrait use the regular single-page layout

**Widgets** — calendar, clock, batteries, weather (Open-Meteo, location or fixed place), photos, plus any Android widget

**Search** — apps, calculator, contacts, Android settings, photos (name, album, month/year), files and web suggestions
(Google suggestions can be turned off in Launcher Settings)

**Smoother app animations (optional)** — with root, the included Magisk module installs the launcher as a system app
so apps open straight out of their icon.

## Requirements

- Android 13 (API 33) or newer
- For the Duo style: a foldable or a tablet (smallest width ≥ 600 dp)
- Optional: root + Magisk for the open animation

## Install

1. Download the APK from [Releases](../../releases) and install it.
2. Open it and follow the welcome flow; choose it as the default home app.
3. Optional (root): flash `liquidy-magisk.zip` in Magisk → Modules, reboot.

## Build

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Magisk module: `tools/build-magisk-module.sh` → `build/liquidy-magisk.zip`.

Project notes: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), measurements and motion values with sources in
[`docs/RESEARCH.md`](docs/RESEARCH.md), glass rendering in [`docs/GLASS.md`](docs/GLASS.md).

## Privacy

Everything runs on the device. Network is only used for the weather (Open-Meteo) and — if enabled — search suggestions
(Google). Usage access, contacts, calendar, photos and location are optional and only used for the matching feature.

## How this was made

Built by [janhurn9400](https://github.com/janhurn9400) with AI pair programming (Claude). The workflow for every screen and animation was the same:
record it on the real device → measure it frame by frame → implement it with the measured values → capture the emulator
at the same moments → compare side by side → repeat until there is no visible difference. Things that can't be matched
on Android without system access (e.g. the system-owned app close gesture) are documented as such instead of faked.

## Status

Early preview (0.x). Expect rough edges, especially on real hardware performance.
