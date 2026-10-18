# Liquidy

An iOS-inspired home screen launcher for Android (Kotlin + Jetpack Compose), built as a faithful recreation —
not a theme, not an icon pack. The goal is that it doesn't just *look* like the original, but *moves* like it.

> Not affiliated with or endorsed by Apple. No Apple assets: the font is [Inter](docs/Inter-OFL.txt) (SIL OFL),
> glyphs, widgets and wallpapers are drawn in code, app icons come from Android or your icon pack.

## What makes it different

**Matched to the original.** Layout, behaviour and animations are matched to the real thing as closely as
Android allows — sizes, spacing, colours, timing and spring feel. Measured values are documented in
[`docs/RESEARCH.md`](docs/RESEARCH.md).

**Motion that behaves.** Everything that moves is an interruptible spring and inherits your finger's velocity:
paging with rubber-band, folders that morph out of and back into their icon, apps that fly into a folder's free spot,
icons that wait before moving out of the way, minus badges that shrink and grow instead of popping.

**Liquid Glass.** Real refraction, rim light and blur on dock, folders, menus and search, fast enough for mid-range
phones (see below). Includes the *Clear ↔ Tinted* setting with a preview, matched to the original.

**Duo style for foldables and tablets.** Two pages side by side with a vertical dock column, single page when folded,
scaled so every device keeps the same proportions.

## Tested on a real mid-range phone

Liquidy is developed on an emulator, but performance is checked on real hardware — deliberately not a flagship:
a **Xiaomi Mi 11 Lite 5G** (Snapdragon 780G, 2021, 1080 × 2400, 90 Hz) running **LineageOS 23.2 (Android 16)**,
release build.

Janky frames (frames that missed the 11.1 ms budget at 90 Hz, measured with `dumpsys gfxinfo`):

| Interaction | Janky frames |
|---|---|
| Swiping between pages, Today view and App Library (full Liquid Glass) | **4 %** |
| Same, early prototype (live glass backdrop for every element, debug build) | 31 % |
| Pulling down search and closing it again (keyboard included) | 20 % |

What made the difference: glass that lies on the wallpaper (dock, search pill, App Library tiles, folders, widgets,
edit buttons) no longer re-draws everything behind it every frame. One shared shader samples the wallpaper directly,
and shadows/rim light are rendered once and reused — it looks the same, at a fraction of the cost. Only glass with
moving content behind it still uses a live backdrop. Details in [`GLASS.md`](docs/GLASS.md).

**Overall:** everyday use — paging, folders, Today view, App Library, edit mode, widgets — feels smooth on this
phone. Opening search with the keyboard is the heaviest moment and still shows some dropped frames. Stock-ROM
specifics (e.g. vendor wallpaper handling, app-open animations) can differ between manufacturers; the optional
Animation Module was not part of this test.

## 🍎 Quirks

The original has rules that can feel restrictive — e.g. 2-row widgets only snap into row pairs, never a single row of
apps above a widget, and the dock holds four apps. Liquidy keeps these **on by default**, because they are part of why
the home screen looks the way it does.

*Launcher Settings › 🍎 Quirks* lets you relax them one by one, each clearly marked as *not like the original*:

- **Widgets in Any Row** — place widgets in any row
- **More Apps in Dock** (Duo style) — as many apps as fit in the dock column

## Features

- 4×6 grid with free placement, dock, page dots, page overview, edit mode with jiggle, drag & drop across pages
- Folders: create by holding an app over another, paging inside, reorder, rename, drag apps out
- Context menus with Android app shortcuts, widget stacks with Smart Rotate
- Today view, App Library with categories and A–Z list
- Widgets: calendar, clock, batteries, weather, photos, reminders — plus any Android widget, fitted to iOS sizes
- Search: apps, calculator, contacts, settings, photos, files, optional web suggestions
- Icon packs (ADW/Nova format), icon styles Default / Dark / Clear / Tinted
- Optional own status bar and haptics

## Install

Requires Android 13 or newer.

1. Download the APK from [Releases](../../releases) and install it.
2. Follow the welcome flow and set it as the default home app.

**Optional – Animation Module (root/Magisk).** Android only lets system apps open apps with their own animation.
The small module in each release (`liquidy-magisk.zip`) adds the installed launcher as a system app — nothing else.
Install it in Magisk › Modules and reboot. Status and steps: *Launcher Settings › Advanced › Animation Module*.

## Build

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Judge performance only with a release build (`./gradlew :app:assembleRelease`) — debug builds run largely
interpreted and stutter on real phones.

Docs: [`ARCHITECTURE.md`](docs/ARCHITECTURE.md), measured values in [`RESEARCH.md`](docs/RESEARCH.md),
glass rendering in [`GLASS.md`](docs/GLASS.md).

## Privacy

Everything runs on the device. Network is only used for weather (Open-Meteo) and, if enabled, web search suggestions.
All permissions are optional and explained in the settings. Bug reports: *Settings › Advanced › Copy Diagnostics*
(technical info only, nothing is sent).

## How it was made

Built by [janhurn9400](https://github.com/janhurn9400) with AI pair programming (Claude).

Early preview (0.x) — expect rough edges.

## License

[GPL-3.0](LICENSE) — forks and improvements are welcome; anything built on this code and distributed must stay open
source under the same license. The Inter font is under the [SIL Open Font License](docs/Inter-OFL.txt).
