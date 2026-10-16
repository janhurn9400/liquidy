# Architecture plan (phase-0 draft — kept for reference; current state in CLAUDE.md)

## Setup
- Kotlin + Jetpack Compose, minSdk 33, targetSdk 36, compileSdk 36, one module `:app` (plus `:hiddenapi` with compile-only stubs for the optional app-open transition).
- Font: **Inter** (OFL) as a bundled TTF. No Apple assets.
- Emulator (original plan): AVD `iOSHome_API36` — system image `android-36;google_apis;arm64-v8a`, **1206 × 2622 px @ 480 dpi = 402 × 874 dp** (= the user's iPhone, measured), `hw.gpu.mode=host`. Now: the Duo-resolution foldable AVD, see CLAUDE.md.
- Launcher integration modelled on Launcher3: `MAIN` + `HOME` + `DEFAULT` intent filter, `launchMode=singleTask`, `stateNotNeeded`, `onNewIntent` (Home button → close folder / go to page 1), app list via `LauncherApps` (+ callback on install/uninstall), edge-to-edge with transparent system bars.

## Package structure
```
app/src/main/java/…/
  motion/     Motion.kt          all springs/durations/curves, each with a reference to a RESEARCH ID (M-…)
              IosSpring.kt       data class IosSpring(response, dampingFraction) → Compose SpringSpec
  design/     DesignTokens.kt    all sizes/spacing/radii/blur/colours (T-…)
              ContinuousShape.kt Apple "continuous corner" shape (Bézier constants)
  tuning/     Tunables.kt        registry: every motion/token value is an observable state
              DebugPanel.kt      3-finger long press → slider panel, reset, export (Kotlin snippet + JSON)
  data/       AppRepository.kt   LauncherApps, loading icons
              LayoutStore.kt     pages/dock/folders as JSON
              IconRenderer.kt    AdaptiveIcon → squircle; legacy icons scaled onto a plate; icon cache
  glass/      LiquidGlass.kt     Kyant backdrop: blur + refraction + specular + dark rim + tint (docs/GLASS.md)
  ui/home/    HomeScreen (one central gesture arena: horizontal = paging, vertical = search,
              long press = menu/edit, 3 fingers = debug; VelocityTracker), own pager (no HorizontalPager),
              AppIcon, dock, page indicator/search pill, folders, edit mode
  ui/library/ ui/search/ ui/widgets/ ui/wallpaper/ ui/welcome/
```

## Key decisions
1. **Own pager instead of `HorizontalPager`.** The page position is an `Animatable<Float>`; dragging writes directly (`snapTo`), release → `animateTo(target, M-PAGE, initialVelocity = fingerVelocity)`. A new touch during the animation stops it in place and takes over — exactly the iOS behaviour. Rubber band via the UIScrollView formula.
2. **Every animated value is an `Animatable`**, never `animate*AsState` with a tween. Retargeting keeps the velocity (Compose default). No duration-based animations except the jiggle.
3. **Motion/tokens are editable live.** `Motion.kt`/`DesignTokens.kt` define the defaults; at runtime the UI reads from `Tunables` (Compose state). The debug panel changes them live; "Export" produces the new default lines, which are then copied into Motion.kt/DesignTokens.kt. The source of truth stays in the code.
4. **Wallpaper:** Android draws its own wallpaper behind the transparent window; glass and blur use an aligned copy (RESEARCH §5.1, glass/WallpaperCropModel).
5. **Liquid Glass:** [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`backdrop`, Apache-2.0) wrapped behind our own `LiquidGlass` API, all parameters as tokens (docs/GLASS.md).
6. **Layout in pt = dp**, all positions computed from tokens (no constraint-layout guessing) → can be compared with iPhone screenshots by overlay.
7. **Verification:** emulator screenshots laid next to/over iPhone screenshots and recordings (tools/measure/).

## Phase 1 plan (historical)
1. Gradle project, create the AVD (system image download ~1.5 GB), launcher manifest, Inter.
2. Motion.kt, DesignTokens.kt, Tunables + debug panel skeleton (early, as requested).
3. Wallpaper + grid + dock (glass) + own pager + page indicator/pill + tap feedback.
4. Take screenshots, check against the measured values, hand over.
