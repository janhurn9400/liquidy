# Handoff: glass performance on a real phone (2026-10-18)

For the next session (another Claude account). Reply to the user in German; repo text in English (see CLAUDE.md).

## Context
- Real test phone now: **Xiaomi Mi 11 Lite 5G "renoir"** (Snapdragon 780G, 1080×2400), bootloader unlocked, **LineageOS 23.2**
  (Android 16, build 20261017) + MindTheGapps. adb serial `71c9b089`, always use `adb -d` (the emulator is also connected).
- Liquidy debug APK installed there and set as home (normal app, NOT priv-app → `run-as` works).
- Display runs at **60 Hz** (LineageOS default) — the user should switch to 90 Hz in Settings › Display.
- Magisk + Liquidy Animation Module are **not installed yet** (plan: patch `boot.img` of build 20261017 in the Magisk app,
  `fastboot flash boot magisk_patched.img`, then install `build/liquidy-magisk.zip` via Magisk › Modules).
- A colourful test wallpaper was pushed to `/sdcard/Pictures/liquidy-test-wallpaper.jpg`; the user has since chosen their own.

## Problem
Swiping into the App Library (and generally) stutters on the phone. User feedback: looks great, but not smooth.

## Measured (10 swipes left/right, `dumpsys gfxinfo`, 60 Hz)
| config | janky frames |
|---|---|
| everything on (before this session's work) | 31 % |
| `DBG.noGlass` = 1 (all Liquid Glass flat grey) | 6–7 % |
| after fast tile glass + library kept composed | 24 % |
| `DBG.noBlur` = 1 (no full-screen background blur) | 21 % (blur is minor) |
| `T-GLASS.amount` = 0 (no lens) / + blur 0 / + saturation 1 | 23–24 % (effects math is NOT the cost) |

simpleperf: RenderThread 61 %, main thread 29 %. Framestats: steady frames ~6–8 ms draw recording + 5–10 ms issue;
spikes of 60–110 ms at swipe start.

**Conclusion:** the cost is Kyant's `drawBackdrop` mechanism itself, per glass element and per frame: it re-records
the backdrop (for the main backdrop = the WHOLE home content) into its own offscreen layer, plus an offscreen
compositing layer for the clipped content. Lens/blur/saturation shaders are nearly free.

⚠️ Measuring pitfall: tunables are in `shared_prefs/tunables.xml`. Writing it with nested `adb shell "run-as … printf …"`
quoting produced `\"` → invalid XML → silently ignored (gave a wrong "glass off is slow too" result). Write the file
locally, `adb push` to /data/local/tmp, then `run-as <pkg> sh -c 'cat /data/local/tmp/t.xml > shared_prefs/tunables.xml'`,
force-stop, restart, and `cat` it to verify. Delete it afterwards (user prefs = defaults).

## What was built this session
1. `DebugOptions.noGlass` / `noBlur` (debug panel › Debug) — commit a50eef8.
2. `glass/WallpaperGlass.kt` — fast glass for anything lying only on the wallpaper: ONE shared AGSL shader (Kyant's lens
   + highlight shader code copied 1:1, saturation in-shader), wallpaper sampled as a texture at the element's screen
   position (local→root matrix as uniform, trigger via onGloballyPositioned like GlassSurface), shadow/highlight drawn
   without layers, frosted (Tinted slider) via a cached `softBlur` copy. Library tiles use it; the library stays
   composed while off screen — commit 863ae1e.
3. **Committed, runs on the phone, NOT yet compared visually:** `LiquidGlass(onWallpaper = true)` and automatic
   delegation when the backdrop is a `WallpaperBackdrop` → WallpaperGlass. Applied to: dock (HomeScreen), search pill
   (only while search is not visible), Duo magnifier (DuoChrome), tinted widget glass + battery widget glass
   (IosWidgets), and automatically folder icons / merge hint (Folder.kt uses WallpaperBackdrop).
   `WallpaperGlass` got `rimAlpha`, `refraction`, `soft`/`softAmount` params; `WallpaperBackdrop` fields are now public.

Note: the user then switched the phone to **90 Hz** (FPS overlay visible top-left) → frame budget 11.1 ms, so jank %
is no longer comparable with the 60 Hz numbers above (first 90 Hz run with step 3: 28 % janky, median 24 ms). Re-measure
the baselines (glass on/off) at 90 Hz before judging.

**Breakthrough (90 Hz):** glass on 30 % janky / glass off 6 % / shadow+rim off 8 % → the BlurMaskFilter of shadow and
highlight was re-rasterised every frame for every element. Now both are recorded once into cached offscreen
GraphicsLayers (drawWithCache, CompositingStrategy.Offscreen, highlight layer blended with Plus) → **8.8 % janky with
full glass**, visually identical (screenshot comparison of the App Library).

## Next steps
1. Install on the phone, measure (same method), and compare screenshots before/after (dock, pill, folder icons,
   tinted widgets, library) — the user insists it must look identical. Then commit.
2. If still janky: remaining live-backdrop glass on screen = App Library search field (LibraryChrome, needs the tiles
   behind it when scrolled) — could use WallpaperGlass while the list is not scrolled. Find others with
   `grep -rn "LiquidGlass(" ui`.
3. User wants a real **setting** (not only debug): Glass quality Full / Light (translucent + light rim, no refraction) /
   Off — for weaker phones.
4. Then: status bar on the phone "looks a bit odd" (user will send a screenshot); Magisk + module setup.
5. Update CLAUDE.md status + docs/GLASS.md ("Fast wallpaper glass" section) when done.

## Answer the user still wanted (why iOS is fast)
On iOS the system compositor (Core Animation render server) samples the already-rendered pixels behind a glass layer
directly on the GPU — one pass, no app-side work. Android apps cannot read what is behind them; libraries like Kyant must
re-draw everything behind each glass element again into an offscreen buffer every frame (CPU work on main + RenderThread).
Games are fast because they render their whole scene in one GPU pipeline they fully control. Fix = avoid re-drawing:
sample a wallpaper texture in one shader (what WallpaperGlass does).
