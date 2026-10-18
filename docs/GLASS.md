# Recreating Liquid Glass — research (2026-10-06)

## What Apple itself describes (WWDC25 "Meet Liquid Glass") [SOURCED]
- The material **refracts, shapes and concentrates light** in real time (lensing) instead of only scattering it (blur).
- **Larger elements = "thicker" glass:** deeper shadows, stronger lens/refraction effects, softer scattering.
- **Specular highlights** follow the geometry, move around the silhouette on interaction, partly with device motion.
- **Touch:** the element "glows from within" from the finger position, the glow spreads to neighbouring glass elements; gel-like give.
- **Adaptive:** shadow opacity, tint and dynamic range adapt to the content below; small elements (tab/nav bars) switch light ↔ dark, large ones (menus) don't.
- **Appearing:** not fading via opacity, but "materialising" by ramping up the refraction.
- Variants **Regular** (adaptive) and **Clear** (more transparent, needs dimming).
- iOS 27: darker rim, brighter specular edges, "Clear ↔ Tinted" slider (see RESEARCH §1).
Source: https://developer.apple.com/videos/play/wwdc2025/219/

## How recreations work technically [SOURCED from several implementations]
1. **Shape:** squircle/rounded rect as an SDF (signed distance field).
2. **Thickness/height profile:** flat centre, "bevel" towards the edge (smoothstep over the SDF) → the surface normal tilts outwards at the edge.
3. **Refraction:** the background UV is shifted along the SDF gradient (Snell or an approximation) — full strength at the edge, 0 in the centre.
4. **Dispersion:** R/G/B with different offsets (blue strongest) → colour fringes at the edge.
5. **Blur + vibrancy:** background blurred, saturation raised (Kyant: ×1.5).
6. **Specular/Fresnel:** normal · light direction, power function; Fresnel (Schlick) → brighter edge.
7. **Shadow:** outer drop shadow + inner shadow (→ the iOS 27 "darker rim").
Sources: https://charlesgrassi.dev/blog/apple-liquid-glass/ · https://1ar.io/updates/how-liquid-glass-works · https://zenn.dev/orectic/articles/liquid-glass-webgl-refraction?locale=en · https://github.com/Oliverrr2424/webgl-apple-liquid-glass

## Existing Android/Compose projects
| Project | License/state | What it does |
|---|---|---|
| **Kyant0/AndroidLiquidGlass → `io.github.kyant0:backdrop` 2.0.1** | Apache-2.0, ~4.1k stars, Compose Multiplatform | `layerBackdrop` records any content (not only the wallpaper) into a texture; `drawBackdrop` with `vibrancy()`, `blur()`, `lens()` (AGSL, API 33+, SDF refraction + dispersion), highlight rim, inner shadow, drop shadow; sample components (button, toggle, slider, bottom tabs). De-facto standard, many ports. https://github.com/Kyant0/AndroidLiquidGlass · docs: https://kyant.gitbook.io/backdrop |
| shayann07/liquidglass | Compose + desktop | refraction, dispersion, rim in one shader https://github.com/shayann07/liquidglass |
| styropyr0/PrismalAGSL | Compose | backdrop sampling, blur, edge refraction, specular, degradation on old versions https://github.com/styropyr0/PrismalAGSL |
| QWEA0/Liquid-Glass-Android | View system, API 24+ | SDF refraction, dispersion, sensor-driven specular highlights https://github.com/QWEA0/Liquid-Glass-Android |
| jtt0001/liquid-glass-demo | demo | local copy of Kyant + control-centre/bottom-bar demos https://github.com/jtt0001/liquid-glass-demo |

## Our gap at the time
`glass/GlassSurface.kt` only sampled the **wallpaper** (blur + tint + edge gradient). No refraction, no dispersion, and glass
over icons (folder panel, context menu, pill over content) didn't see the icons → not like iOS.

## Recommendation
1. **Integrate Kyant `backdrop` 2.0.1** (Apache-2.0) and wrap it behind our own API so that all parameters stay in
   DesignTokens/the debug panel. Record the whole home-screen layer via `layerBackdrop` → dock, pill, badges, folders, menu
   refract real content.
2. Calibrate parameters per element from our references (iOS 27: dark inner rim = inner shadow, light edges = highlight;
   larger elements stronger lens — Apple's statement).
3. Interaction: "glow from within" + gel-like give on touch (phase-2 polish).
Risk: Kotlin/Compose version compatibility (backdrop 2.x may be built with a newer Kotlin) → check when integrating,
raise Kotlin if needed. Fallback: own AGSL following the recipe above.

## Implementation (2026-10-06)
- `io.github.kyant0:backdrop:2.0.1` + `io.github.kyant0:shapes:1.2.1` (G2 corners, supported by the lens). Kotlin raised to 2.4.10 for it
  (backdrop is built with Kotlin 2.4; the AGP-9 built-in Kotlin is overridden via `buildscript { classpath(kotlin-gradle-plugin) }`, per the AGP 9 release notes).
- `glass/LiquidGlass.kt`: colorControls(saturation) → blur → lens(height ≤ corner radius, depth effect; without dispersion, see pitfall 4) + highlight (light at 90° = top/bottom bright)
  + inner/outer shadow + dark side rim (GlassRim, dark part only). All values `T-GLASS.*` in DesignTokens. `refraction = false` = frosted glass (blur + tint only), used by option sheets.
- Glass source: wallpaper + page icons via `Modifier.layerBackdrop` (HomeScreen), provided through `LocalGlassBackdrop`. Sheets (glass/GlassSheet) are their own source for glass elements inside them.
- Real glass: dock, pill, edit buttons, folder panel, delete dialog. **Still old glass** (it lies in the recorded layer itself → it would record itself):
  minus badges, folder icons, folder hint, App Library tiles → solved later via `exportedBackdrop`/separate layers where needed.
- Debug: `DBG.wallpaper = 1` shows a colourful test wallpaper on which refraction is visible (the reference wallpaper is plain grey).

## Pitfalls
1. **Don't animate the shape at constant size:** Kyant's layer only picks up the outline when the size changes. A corner radius changed every frame (folder opening)
   gave an outdated clip (large starting corners) ≠ light edge → "shape within the shape". Solution: change the radius only together with the size (folder morph), otherwise animate via scaling.
2. The lens needs `RoundedRectangularShape` (Kyant Shapes) or `AbsoluteRoundedCornerShape`; our own ContinuousRoundedShape doesn't work there.
3. Too large a refraction zone (thickness 2 → 24 pt) looks like a double rim; the iOS folder has a narrow zone → `T-GLASS.thickLarge = 1`.
4. **No colour fringes (dispersion)** (2026-10-14): Apple's Liquid Glass has no rainbow corners (checked by the user on a real iPhone).
   Kyant's `lens(chromaticAberration = true)` gave strong fringes → `chromaticAberration = false` everywhere, token `T-GLASS.dispersion` removed.

## Further sources
- QWEA0/Liquid-Glass-Android (MIT, View system): ideas for later — refraction falloff `∝ (1 + x/k)^-p`, motion-sensor light reflection,
  brightness measurement behind the glass for adaptive tint. https://github.com/QWEA0/Liquid-Glass-Android
- Void Launcher: closed source, license forbids reverse engineering → not usable. https://github.com/Navoto01/VoidLauncher

## Fast wallpaper glass (performance on real phones)

Kyant's `drawBackdrop` re-records its backdrop into an own offscreen layer every frame the element moves (for the main
backdrop that is the whole home screen) and composites the clipped content offscreen too. With ~10 elements on screen
that cost 31 % janky frames on a Snapdragon 780G — the lens/blur/saturation shaders themselves are nearly free.

`glass/WallpaperGlass.kt` is used for every glass element that only has the wallpaper behind it
(`LiquidGlass(onWallpaper = true)`, or automatically when the backdrop is a `WallpaperBackdrop`):
- ONE shared AGSL shader (Kyant's lens + highlight code 1:1, saturation in-shader) samples the wallpaper texture at the
  element's screen position (local → root matrix as uniform); per frame only uniforms change, no offscreen layers.
- Shadow and light rim (BlurMaskFilter) are recorded once into cached offscreen `GraphicsLayer`s — re-rasterising
  them every frame for every element was the last big cost (30 % → 9 % janky at 90 Hz).
- Frosted glass (Tinted slider, sheets) samples a pre-blurred wallpaper copy (`FrostCache`).
Only glass with moving content behind it (e.g. menus over icons) still uses the live Kyant backdrop.
Measure performance only with release builds (debug = interpreted).
