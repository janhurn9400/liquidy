# CLAUDE.md — iOS 27 home screen launcher for Android (Liquidy)

Goal: an Android launcher (Kotlin + Compose, minSdk 33) that feels 1:1 like the iOS 27 home screen (layout, physics, timing). **Reply to the user in German.** Everything in the repository (docs, code comments, commit messages, release notes) is written in **English**.

## Documents
- `docs/RESEARCH.md` — all dimensions/springs with source and status [SOURCED]/[DERIVED]/[ESTIMATED]/[MEASURED], plus "Please check on the iPhone"
- `docs/ARCHITECTURE.md` — architecture & decisions
- `docs/GLASS.md` — Liquid Glass implementation incl. pitfalls
- `docs/FOLD-BRANCHES.md` — where folded and unfolded differ, and why

## Rules
1. All animation values only in `motion/Motion.kt` (with RESEARCH ID `M-…`), all design values only in `design/DesignTokens.kt` (`T-…`). No magic numbers in UI code.
2. Everything animated is an interruptible `Animatable`; gesture velocity is passed as `initialVelocity`.
3. No Apple assets (Inter font, own/free wallpapers, app icons only via squircle mask). No "iPhone"/"Apple" in user-visible text.
4. After every change: build, install in the emulator, quickly check that it starts (one screenshot is enough), then report briefly + what to look for on the iPhone. The user judges for themselves — no long screenshot test series. On feedback, change a few values specifically and name them.
5. Emulator = 60 Hz + mouse → keep in mind when judging stutter/flings.
6. Releases are regular (not pre-release) so `releases/latest/download/liquidy-magisk.zip` keeps working. Versioning: new behaviour/features → minor (0.x.0), fixes/polish → patch.

## Build & test
- Emulator: AVD `Pixel_10_Pro_Fold` in iPhone-Duo resolution (2854×2007 @480 dpi, hinge at x = 1427 px, GPU host).
  Start: `~/Library/Android/sdk/emulator/emulator -avd Pixel_10_Pro_Fold -gpu host -no-boot-anim -writable-system`
  (`-writable-system` ALWAYS: Liquidy is installed there as a system app in `/system/priv-app/Liquidy` + permission/hidden-API XML from
  tools/magisk-module — without Magisk via `adb root && adb remount`. Only then does the own app-open animation run. `adb install -r`
  works as usual afterwards, the update stays privileged.)
  Reading app data: `run-as` doesn't work for system apps → `adb root`, then `adb shell cat /data/data/io.github.janhurn9400.liquidy/shared_prefs/…`.
  Two displays → screenshots only with a display ID: `adb exec-out screencap -p -d 4619827259835644672 > shot.png` (inner, unfolded).
  After every reinstall the emulator hangs at first (shader compilation, ANR dialog possible) — wait briefly, not an app bug.
- `./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
  Set as home: `adb shell cmd package set-home-activity io.github.janhurn9400.liquidy/.MainActivity`
- Debug panel: 3-finger long press (device), key **D** (emulator), or
  `adb shell am start -n io.github.janhurn9400.liquidy/.MainActivity --ez debug true`.
  "Export" → clipboard + logcat tag `Tuning` (`adb logcat -s Tuning`).
- Comparison: screenshots in `shots/` (gitignored), measurement scripts in `tools/measure/`, references in `docs/reference/iphone/` (local only, not in the repo).

## Status (2026-10-16, v0.4.2 released + unreleased work on main; public repo, GPL-3.0)
- Phase 0: done. Phases 1–4 built, fine-tuning ongoing (the user does NOT tune values themselves — derive values from their videos, put emulator captures next to them frame by frame).
- Phase 1: grid/labels/dock/pill ±1 px; pager M-PAGE verified (0.362 s / 0.904).
- Phase 2: edit mode (long press, measured jiggle, badges, edit buttons, dragging + reflow, dock ↔ grid, edge page flip with peek incl. new page, delete dialog, persistent layout `LayoutStore`), context menu with native Android shortcuts.
- Phase 3: folders (hold over an app, name from app category, measured mini grid, open/close M-FOLDER measured, drag out, paging, reorder, rename with cursor; close morphs into the icon without a crossfade).
- Phase 4: search (rev. 3 after frame-by-frame analysis), App Library 1:1 after recording2 (tiles, glass search field with scroll edge, A–Z list by pulling down, panel-less category with group zoom).
- Emulator performance: GPU median 18 ms/frame (~40 fps) regardless of glass/blur (A/B measured) → stutter in the emulator is not an app problem. `adb shell screenrecord` often only yields still frames; for in-between states slow down springs via debug prefs + screencap.
- Keyboard: manifest `adjustResize` (needed for animated IME insets); read IME insets only in layout/draw lambdas (reading in composition = 1 frame delay).
- Search rev. 6: pill → search field morph (SearchPill.kt, outside the blurred layer), tapping the pill opens search.
- Liquid Glass: Kyant backdrop (see docs/GLASS.md incl. pitfalls). Kotlin 2.4.10 via buildscript classpath. No colour fringes (Apple has none). "Clear ↔ Tinted" slider (glass/GlassClarity, subpage with preview in Appearance): tinted = more blur, no refraction, grey veil. `LiquidGlass(refraction = false)` = frosted glass.
- Reusable sheet: glass/GlassSheet (frosted, slides up, dims behind, optional floating Liquid Glass bottomBar e.g. search field; the sheet is the glass source for elements inside it). Used by widget settings (album, calendars, location, clock city).
- Wallpaper editor (Pair/Colour/Gradient/Photo + blur, own photo picker via MediaStore, crop) via the edit menu. Labels adapt to the wallpaper brightness.
- Customise sheet: icon styles Default/Dark/Clear/Tinted (IconAppearance, layers from IconRenderer.layers).
- Widgets: WidgetSpec in the layout (key "widget:…"), GridPlacement (iOS flow, widgets only in column 0/2 and row pairs unless 🍎 Quirks), own widgets (ui/widgets: clock with city/time zone, calendar, batteries, weather via Open-Meteo, photos, reminders), Android widgets via AppWidgetHost, picker + add sheet.
- Today view (pager page −1, TodayPage.kt), resize via handle + size bar in the widget menu.
- Wallpaper: there is ONLY the Android wallpaper (user's wish, don't change!). Window transparent (windowShowWallpaper + windowIsTranslucent). The picker sets its choice as the Android wallpaper. Pixels (only internally for glass/blur) read via MANAGE_EXTERNAL_STORAGE and cropped like Android (glass/WallpaperCropModel, no zoom on a foldable's folded display); without access: fall back to drawing our own.
- GestureNavContract (swipe home → window flies into the icon) with an icon surface like Launcher3's FloatingSurfaceView; test start: `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME`.
- App open/close sharp/blur: code exists (launchBlur/returnBlur in HomeModel), disabled in backgroundBlurPx — the user wants to check it later with an iPhone video.
- Animation Module (optional, root): tools/magisk-module (build: tools/build-magisk-module.sh → build/liquidy-magisk.zip) adds the installed app as a priv-app + CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS + hidden-API allowlist. Then AppLaunchTransition (RemoteTransition, stubs in :hiddenapi, compileOnly) opens apps with its own spring. Settings › Advanced › Animation Module explains it and links the download. Play Store: the app must not load/install the module itself — only a link + "Open Magisk".
- Duo style (foldables/tablets) is the main target: two pages + dock column on the right. Dock glass springs with the app count (M-REFLOW); "More Apps in Dock" under 🍎 Quirks.
- 🍎 Quirks settings category: the original's rules are on by default; options relax them (Widgets in Any Row, More Apps in Dock).
- Settings: iOS-27-style category tiles, own app icon (placeholder, adaptive icon in res/drawable), close with Home/Back; permissions page explains each permission; Advanced › Report a Problem opens a GitHub issue prefilled with diagnostics.
- Icon styles (10-16): Tinted Light = opaque plate + light glyph like iOS 27 (T-TINT, measured on the user's iPhone); glyphs are computed from the icon (plate removal at 4x supersampling, soft edges) — the user preferred this over the icon pack's `_mono` glyphs. Own widgets are tinted too (photo widget excluded). Tinted Dark not yet compared with the iPhone.
- Today view editing matches the user's iPhone video (lift 1.12× = T-DRAG.scale, springs on lift/drop).
- Report a Problem: GitHub issue or email (Diagnostics.REPORT_EMAIL is a PLACEHOLDER — replace before the next release), optional screen recording (BugRecorder, ≤ 1 min, Movies/Liquidy) attached by email or to the GitHub form.
- Next: real email address; Maps-like icons without a plate in Tinted; Tinted Dark + closed folder icons in Tinted (need iPhone screenshots); Android widgets in Tinted; app icon is still a placeholder; test on a real foldable (120 Hz).
- Testing caution: drag tests in the emulator change the user's layout — back up shared_prefs/layout.xml first and restore it afterwards.
- FIXED RULE dock size: items in the Duo dock are smaller (48 instead of 60 pt). Every place that shows a dock item or starts from it (drawing, folder open/close, context menu, app launch/return, dragging, picking up, flying into a folder) uses `HomeGeometry.dockIconScale` or `iconRect(topLeft, inDock)` — never its own values.
- Architecture: one central gesture arena in `ui/home/HomeScreen.kt`; geometry only in `HomeGeometry`/`FolderGeometry`/`LibraryGeometry`; layout state in `HomeModel`.
- Lesson: ALWAYS analyse motion frame by frame against the reference (montage + tracking), not just single values — otherwise it looks "sloppy" (search rev. 1/2).
