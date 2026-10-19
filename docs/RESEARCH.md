# RESEARCH.md — iOS 27 home screen → Android (Compose)

As of: 2026-10-06 · Phase 0 (rev. 2: with measurements from the user's screenshots + screen recording), extended since

**Legend**
- **[MEASURED]** — pixel-exact from the native iPhone references in `docs/reference/iphone/` (iOS 27, 1206 × 2622 px @3x = 402 × 874 pt); animations by frame tracking from `recording1.mp4` (60 fps) + spring fit
- **[SOURCED]** — directly from Apple docs/WWDC or a clearly citable source
- **[DERIVED]** — calculated from sourced/measured values (calculation given)
- **[ESTIMATED]** — reasoned assumption → "please check on the iPhone"

Every number that ends up in `Motion.kt`/`DesignTokens.kt` has an ID (`M-…`, `T-…`) that the code refers to. All lengths in **pt = dp**.

### Reference material (from the user, 2026-10-05)
| File | Content |
|---|---|
| IMG_3123 | Home screen page 1 (2 small widgets, 14 icons, "Search" pill, dock) — light grey wallpaper |
| IMG_3124 | Edit mode (minus badges, buttons at the top, page dots in the pill) |
| IMG_3125 | Edit mode + long-press menu (Add Widget / Customise / Edit Wallpaper / Edit Pages) |
| IMG_3126 | Widget gallery (sheet) |
| IMG_3127 | "Customise" panel (Default / Dark / Clear / Tinted) |
| IMG_3128 | Wallpaper editor |
| IMG_3129 | Edit pages |
| IMG_3130 | Open folder "Utilities" |
| IMG_3131 | Search (swipe down) with keyboard |
| IMG_3132 | Today view (left of page 1) |
| recording1.mp4 | 26.5 s: paging, Today view, search ×2, folder close/open/close, edit mode + menu, opening an app (Photos), Control Centre |

Measurement scripts: see `tools/measure/`.

---

## 1. What's new in iOS 27 (relevant to the home screen)

| Topic | Finding | Status | Source |
|---|---|---|---|
| Release | announced 8 June 2026, released 14 Sept 2026 | [SOURCED] | [Macworld](https://www.macworld.com/article/2986799/ios-27-new-iphone-features-release-date-beta-compatiblity-apple-intelligence-siri.html) |
| Liquid Glass | diffuses the background more, **darker rim**, **brighter specular edges** | [SOURCED] + [MEASURED]: pill & dock have a light rim at the top/bottom and a dark edge left/right | [MacRumors](https://www.macrumors.com/2026/06/10/how-liquid-glass-is-changing-in-ios-27/) |
| Glass slider | Settings › Appearance › Liquid Glass: "Clearer ↔ More Tinted" | [SOURCED] | [Six Colors](https://sixcolors.com/post/2026/09/ios-27-review-little-things-mean-a-lot/) |
| Glass slider measurement | User's iPhone 10-14, dock/search field at clear/middle/tinted: blur σ ≈ 0.4 / 1.3 / ≥ 4 pt (tinted completely smooth, refraction invisible), dock colour almost unchanged; tinted without refraction (App Library field: edge only softened) and a neutral grey veil (dock 204→196, search field 44→54, blue 252→224 → grey ≈ 140, ≈ 15 %). Implemented: glass/GlassClarity, T-GLASS.frostMid/frostTinted/tintedVeil/tintedGrey | [MEASURED] | User screenshots |
| App icons | several glass layers, sharper; **tilt sparkle removed**; light rim at the top | [SOURCED] + [MEASURED] rim | [Design Compass](https://designcompass.org/en/2026/09/16/ios-27-liquid-glass-icons/) |
| Icon styles | Default / Dark / Clear / Tinted ("Customise" panel, IMG_3127) | [SOURCED] + screenshot | Design Compass |
| Widgets | new: full-screen widget (XL) | [SOURCED] | [TechCrunch](https://techcrunch.com/2026/06/09/ios-27-features-we-didnt-see-on-stage/) |
| Search | swipe down → search with "Siri Suggestions" at the top, search field above the keyboard at the bottom (IMG_3131) | [SOURCED] + screenshot | [Macworld](https://www.macworld.com/article/3236198/ios-27-is-driving-my-fingers-insane.html) |
| Edit menu | Add Widget / Customise / Edit Wallpaper / Edit Pages — **no undo/redo** in the shipped version | [MEASURED] IMG_3125 | — |

Basic structure (4×6, dock of 4, pages, folders, App Library) unchanged. We do **not** build tilt parallax.

---

## 2. Dimensions & shapes — all [MEASURED] from IMG_3123/3124/3130 unless marked otherwise

### 2.1 Device & units
- Reference device: **402 × 874 pt @3x** (iPhone 17 Pro or 16 Pro — same dimensions). Display corner radius 62 pt ([ScreenCorners](https://github.com/kylebshr/ScreenCorners)) [SOURCED].
- 1 pt = 1 dp (error ~2 %) [DERIVED]. Original emulator AVD: **1206 × 2622 px @ 480 dpi = 402 × 874 dp** → screenshots comparable pixel for pixel. (The project now uses the Duo-resolution foldable AVD, see CLAUDE.md.)

### 2.2 Home screen grid

| ID | Value | Pixels (@3x) |
|---|---|---|
| T-ICON | **64 pt** icon (not 60!) | 192 px |
| T-ICON-R | corner radius **16.33 pt** (25.5 % of the edge), *continuous corner* — fit error 0.37 px; a circular arc fits worse (0.64 px). → iOS 27 is rounder than the old 22.5 % | 49 px |
| T-GRID-SIDE | side margin to the icon edge **30 pt** (left = right) | 90 px |
| T-GRID-COLPITCH | column pitch centre to centre **92.67 pt** (gap between icons 28.67 pt) | 278 px |
| T-GRID-TOP | top of row 1 **90 pt** | 270 px |
| T-GRID-ROWPITCH | row pitch **100.33 pt** | 301 px |
| T-GRID-COLS/ROWS | 4 × 6 | — |
| T-LABEL-CAP | label cap height ≈ **9 pt**, cap top 8 pt below the icon, baseline at icon top + **80.33 pt** | 27 px / 24 px / 241 px |
| T-LABEL-W | reference width "FaceTime" = **53 pt** → choose the Inter font size so that width & cap height match (≈ 12–12.5 sp) | 159 px |
| T-LABEL-COLOR | on a light wallpaper **dark, warm tinted** ≈ `#493A36` (iOS tints labels to match the wallpaper) | — |
| T-WIDGET-S | small widget **164.33 × 164.33 pt**, r = **27.75 pt** continuous (fit 0.37 px); vertically exactly 2 rows (icon top row 1 to icon bottom row 2), horizontally **4 pt** wider than the icons on each side; x = 26 / 211.33 pt | 493 px |
| T-WIDGET-LABEL | widget label cap top 8.67 pt below the widget | 26 px |
| T-WIDGET-GRID | S 2×2, M 4×2, L 4×4, XL 4×6 (full screen, iOS 27) | [SOURCED] layout gist / TechCrunch |

**Layout formula:** `iconX(col) = 30 + col·92.67`, `iconY(row) = 90 + row·100.33` (pt). Row 6 ends at 655.67 pt + label.

### 2.3 Search pill & page dots

| ID | Value |
|---|---|
| T-PILL | capsule **78.67 × 30 pt**, horizontally centred, top **703.67 pt** (pill → dock gap 20 pt) |
| T-PILL-RIM | light rim at the top/bottom (1–2 px, almost white), dark 1-px edge left/right |
| T-PILL-CONTENT | magnifier (≈ 9.3 pt wide) + "Search", colour ≈ `#6B605D` |
| T-DOTS | in the same pill: dot Ø **6.33 pt**, pitch centre to centre **14 pt**; active white 100 %, inactive white ≈ 45 % |

### 2.4 Dock

| ID | Value |
|---|---|
| T-DOCK-INSET | **16.67 pt** left/right, **17 pt** bottom |
| T-DOCK | glass plate **368.67 × 103.33 pt**, top 753.67 pt |
| T-DOCK-R | corner radius **38.33 pt** (fit 0.5 px). *Not* concentric with the display (that would be 45.3 pt) — refutes the phase-0 estimate |
| T-DOCK-ICONS | 4 × 64 pt, vertically centred (19.67 pt top/bottom), 20 pt inner margin left/right, pitch centre to centre **88.22 pt** (x = 36.67 / 125 / 213.33 / 301.33 pt) |
| T-DOCK-RIM | like the pill: light rim top/bottom, dark edge at the sides; fill slightly darker than the wallpaper (≈ −8 % luminance) |

### 2.5 Folder (open)

| ID | Value |
|---|---|
| T-FOLDER-PANEL | **306.33 × 352.67 pt**, r = **38.5 pt** continuous (fit 0.7 px), **exactly centred on screen** |
| T-FOLDER-GRID | 3 columns, icons 64 pt; column pitch **92.67 pt** (= home screen), row pitch **104.33 pt**; inner margin left 29 pt, top 28.67 pt |
| T-FOLDER-TITLE | "Utilities", bold, ≈ 34 pt (large-title size), white, left-aligned above the panel (≈ 73 pt from the left), [ESTIMATED] size ±2 pt |
| T-FOLDER-BG | home screen behind strongly blurred (icons only colour blobs), slightly darkened |

### 2.6 Edit mode

| ID | Value |
|---|---|
| T-BADGE | minus badge Ø **26 pt**, light grey glass circle, black bar **12 × 2 pt**; centre at icon top-left corner + (2.3 / 3.2) pt |
| T-EDIT-BTN | two glass capsules at the top **66.67 × 27.33 pt**, y = 19 pt, 35.67 pt from the edge; left brush (Customise), right checkmark (Done). Status bar is hidden. Touch zone [ESTIMATED] ≥ 48 pt tall up to the screen top (capsule alone was missed by fingers on the Mi 11 Lite); over a punch-hole camera the capsule moves beside it with a 6 pt gap |
| T-EDIT-PILL | pill shows page dots instead of "Search" |
| T-WIDGET-RESIZE | widgets get a resize handle at the bottom right (circular arc) |

### 2.7 Squircle shape [SOURCED]
"Continuous corner" rectangle made of Bézier curves; the corner starts 1.52866 × r from the edge ([Liam Rosenfeld](https://liamrosenfeld.com/posts/apple_icon_quest/)). Our measurements (icons, widgets, folders) fit this shape much better than circular arcs → `ContinuousRoundedShape(r)` for everything.

### 2.8 Liquid Glass material
Building blocks (AGSL): blur + saturation, edge refraction, light rim top/bottom, dark side edge, tint ("Clear ↔ Tinted"). Rim/edge behaviour [MEASURED] qualitatively; blur radius & refraction strength [ESTIMATED] → debug panel. Reference: [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

---

## 3. Springs: Apple → Compose

### 3.1 Apple parameters [SOURCED]
- `Spring(duration: 0.5, bounce: 0.0)` = default; duration = response.
- `.smooth` bounce 0 · `.snappy` 0.15 · `.bouncy` 0.3 (each 0.5 s); `.spring(response: 0.5, dampingFraction: 0.825)`; `.interactiveSpring(0.15, 0.86, blend 0.25)`; `CASpringAnimation` mass 1 / stiffness 100 / damping 10.
- Retargeting keeps the current velocity.
Sources: [Apple – Spring](https://developer.apple.com/documentation/swiftui/spring), [snappy](https://developer.apple.com/documentation/swiftui/animation/snappy(duration:extrabounce:)), [bouncy](https://developer.apple.com/documentation/swiftui/animation/bouncy(duration:extrabounce:)), [spring(response:…)](https://developer.apple.com/documentation/swiftui/animation/spring(response:dampingfraction:blendduration:)), [interactiveSpring](https://developer.apple.com/documentation/swiftui/animation/interactivespring(response:dampingfraction:blendduration:)), [WWDC23](https://developer.apple.com/videos/play/wwdc2023/10158/), [WWDC18](https://developer.apple.com/videos/play/wwdc2018/803/)

### 3.2 Conversion [DERIVED]
```
ω₀ = 2π / response
stiffness    = ω₀² = (2π / response)²          (m = 1, as in Compose)
dampingRatio = dampingFraction ;  from bounce: ζ = 1 − bounce (≥0) or 1/(1+bounce) (<0)
Compose: spring(dampingRatio = ζ, stiffness = (2π/response)²)
```
| response (s) | 0.15 | 0.2 | 0.25 | 0.3 | 0.34 | 0.36 | 0.4 | 0.45 | 0.5 |
|---|---|---|---|---|---|---|---|---|---|
| stiffness | 1754.6 | 987.0 | 631.7 | 438.6 | 341.5 | 304.6 | 246.7 | 195.0 | 157.9 |

Compose `Animatable.animateTo()` starts with `initialVelocity = velocity` → retargeting without a jump. Gesture velocity (VelocityTracker, px/s) goes in 1:1 as `initialVelocity`.

**Fit method for [MEASURED] springs:** position per frame via template matching/cross-correlation, then a least-squares fit of the damped-oscillator solution (free parameters ω₀, ζ, v₀) from the first frame after release. The RMS error is given with each.

### 3.3 Springs & timings per interaction

| ID | Interaction | Value | Status |
|---|---|---|---|
| **M-PAGE** | page snaps after a swipe | **response 0.36 s, ζ 0.90** (4 swipes: 0.32–0.38 s / 0.89–0.92; RMS 0.4–3 pt); flick velocity at release ≈ 2000 pt/s | [MEASURED] |
| M-PAGE-DRAG | finger phase | content follows 1:1 | [MEASURED] |
| M-RUBBER | rubber band at the edge | `(x·d·0.55)/(d + 0.55·x)` | [SOURCED] UIScrollView |
| M-PAGE-THRESH | page-change threshold | > ~300 pt/s or > 50 % offset | [ESTIMATED] |
| **M-PILL** | "Search" pill ↔ dots | at the start of a swipe, crossfade to dots in ≈ 0.15 s; after snapping the dots stay ≈ **0.5 s**, then ≈ 0.15 s back to "Search" | [MEASURED] |
| **M-PRESS** | tapping an icon | touch down: **immediately** (≤ 1 frame) to **57 % brightness** (≙ black overlay α 0.43), **no scaling**; on release back immediately (+ app launch) | [MEASURED] |
| M-PRESS-CANCEL | dragging the finger away without launching | soft fade back? | [ESTIMATED] → check |
| **M-FOLDER** | opening **and** closing a folder | **response 0.34 s, ζ 1.0** (critical, no overshoot; open/close symmetric, RMS < 1 pt). Panel scales from the icon rectangle (scale 64/306.33 = 0.209, centre = folder icon) to the screen centre | [MEASURED] |
| M-MERGE | holding an icon over an app/folder → folder intent | reacts as soon as the icon **comes to rest** over the target (hint ≈ 20 ms after stopping); glass grows to **1.22×** (edges 93→72 / 187→162 px) in ≈ 0.1 s without overshoot (spring ≈ 0.11 s, ζ 1); folder name fades out; triggered at ≈ 26 pt distance between centres | [MEASURED] user's iPhone 10-14, 10.6 s |
| M-MERGE-DROP | releasing over the folder | icon shrinks to mini size and flies into the free mini slot: **response 0.46 s, ζ 1.0** (RMS 1.2 %); folder glass back in ≈ 0.1 s | [MEASURED] 11.40–11.78 s |
| M-FOLDER-PAGE | paging in an open folder | page = panel width, icons clipped at the panel edge, follows the finger, snaps with **M-PAGE** (RMS 1.1 px); editing adds an empty page | [MEASURED] 16.5–18.9 s |
| M-FOLDER-OUT | dragging an app out of an open folder | folder stays open, app hangs on the finger; it closes only **≈ 0.15 s** after the app's centre has left the panel (normal M-FOLDER close) | [MEASURED] 23.27 → 23.42 s |
| M-EDGE | holding an icon at the page edge | immediate ≈ 5 pt nudge, then the page creeps evenly to ≈ 40 pt; after **≈ 0.85 s** haptic + page change (M-PAGE) | [MEASURED] 26.8–27.9 s |
| M-BADGE-DRAG | minus badge when picking up/dropping | pick up: the original at its spot fades out as a ghost, its badge shrinks away in ≈ 0.1 s; drop: the badge grows from its centre as soon as the icon has landed, ≈ 0.1 s without overshoot | [MEASURED] 43.32–43.42 / 44.89–44.99 s |
| T-MERGE.dark | folder hint darkens the folder glass | glass inside 75 → 53 with surroundings at 96 ≈ +30 % black; the folder glass itself grows (no extra light glass patch) | [MEASURED] 10.4 / 10.8 s |
| T-WIDGET-ROWS | widget grid | 2-row widgets only in row pairs (rows 1, 3, 5) — never a single row of apps above a widget; "Widgets in Any Row" under 🍎 Quirks | [SOURCED by user] |
| **M-JIGGLE** | jiggle | **rotation ±1.70°, period 0.256 s**; offset **x ±0.39 pt @ 0.268 s**, **y ±0.39 pt @ 0.284 s** (three slightly different periods → organic); all sine; **random phase per icon**; starts immediately at full amplitude (no fade-in). Applies to grid, dock and widgets | [MEASURED] (4 icons, RMS 0.05° / 0.01 pt) |
| M-EDIT-ENTER | badges/buttons/dots on entering | appear within ~0.1 s; fine curve not measured yet | [ESTIMATED] |
| M-SEARCH-PULL | swipe down (rev. 3, both searches in the recording frame by frame) | blur grows with the finger and is full after ≈ ¼ of the way (icons stay recognisable as colour shapes → radius ≈ 12 pt); home screen moves along (≈ 66 pt at release, open ≈ 90 pt), shrinks (0.94 → 0.89) and darkens (−37 % → −58 %) | [MEASURED] |
| M-SEARCH-BLOCK | "Siri Suggestions" + card | fade in from ≈ ⅓ of the way, come from above (≈ −40 pt) and follow the finger **beyond** the resting position (+80…88 pt); after release spring back **response 0.46 s, ζ 0.81** (RMS 0.3 pt) | [MEASURED] |
| M-SEARCH-KB | keyboard + search field | keyboard starts **already while dragging** (≈ 75 % of the way), spring 0.44 s / 0.85 = same system spring as the block; search field rides on the keyboard. On Android the system drives the IME animation → field attached to `WindowInsets.ime` | [MEASURED] |
| M-SCROLL | App Library scrolling | UIScrollView 0.998/ms → Compose `exponentialDecay(frictionMultiplier = 0.4767)`; spring back [ESTIMATED] | [SOURCED]/[DERIVED] |
| T-LIB | App Library layout (recording2, at rest 9.9 s) | field 26/90, 350×47.33, font 17; tiles 165.33 □, side 25.67, gap 20, rows 200.67, top 168.67; inner padding 12.67, large icons 64 (gap 12), mini 25.67 (gap 6); label baseline +16.67 below the tile | [MEASURED] |
| T-LIB-LIST | A–Z list (12.4 s) | field right 302.33, "Cancel" x 323 (17 pt); header baseline 182, rows 72 pt from 189.67, icon 48 at x 26.33, name x 91 (17 pt white), hairline to 371; index x 394.33, 183–550, 27 characters | [MEASURED] |
| T-LIB-CAT | category expanded (18.2 s) | no panel; title bold 34.5 at x 30, baseline 325.67; grid = home screen grid (x 30, 92.67/100.33), top 371 | [MEASURED] |
| M-LIB-LIST | pull down → list (10.1–11.9 / 12.97–13.55 s) | immediately while dragging: tiles blurred in 0.12 s, list from 0.3 s (0.45 s), darkening 0.75 s (brightness 171 → 113); list follows the finger 1:1, field ⅓; release: spring 0.5/1.0, keyboard up; "Cancel": list gone in 0.17 s, tiles sharp from 0.19 s (0.38 s) | [MEASURED] |
| M-LIB-CAT | category open/close (17.50–18.10 / 18.60–18.81 s) | title + grid zoom as a group out of the mini cluster: spring 0.54/0.9 (rms 0.005); the 4 cluster icons fly individually out of their mini slots; App Library shrinks around (centre, 0.48 H) to 0.88 and is gone at progress ≈ 0.55; closing ≈ 0.32/1.0 | [MEASURED] |
| M-SEARCH-MORPH | "Search" pill → search field (recording5) | the pill stays **sharp** while dragging (not in the blurred layer), moves along with ≈ ¼ of h (23 pt at h = 94). At h ≈ **94 pt** (still while dragging) it expands symmetrically in ≈ 0.3 s to **full field width (≈ 386 pt) × 47 pt** (earlier 338 pt = mis-measurement of an intermediate state); field = clear glass, magnifier + placeholder on the left, microphone on the right; stays at its height until the keyboard reaches it, then rides along and docks at full width. Back below ≈ 62 pt. On iOS the keyboard already peeks out during the morph (Android: not controllable). The field sits directly above the keyboard: `windowSoftInputMode=adjustResize` (+ edge-to-edge) delivers the IME insets frame by frame (with adjustNothing they came as a jump); insets are read in the layout phase → no frame delay. Measured: gap constant at 9–10 pt during the whole keyboard animation | [MEASURED] |
| M-FOLDER-HANDOFF | folder: transition icon ↔ panel | an emulator recording showed a lingering leftover panel (0.25 s) + a hard-vanishing icon → crossfade in the smallest phase (up to progress 0.12) + fine end threshold; closing since 10-15: no crossfade, glass and icons morph fully into the folder icon | [DERIVED] |
| M-CTXMENU | long press on an icon → context menu (recording4) | touch down: immediately 57 % dark; from +0.20 s the icon slowly grows 1.0 → 1.23 (until +0.73 s), from +0.47 s the darkening fades out (until +0.67 s, icon then ~6 % brighter); at **+0.82 s** the menu opens: icon in ~0.08 s to 1.15 and 10 pt up, background in ~0.15 s to −50 %; menu grows out of the icon. Closing ~0.2 s. Entries (Phone): Remove App (red), Require Face ID, Edit Home Screen, app actions | [MEASURED] |
| T-CTX | context menu layout (recording4 2.3 s) | width 250.67, left-aligned with the lifted icon (right-aligned in the right half), 9 pt gap; row 42 pt (+19.33 per extra text line), inner padding 10; symbol centre x 40.67 (16.33 pt), text x 64.67, 17 pt; red #FC7075; background −52 %, not blurred; menu glass strongly blurred. Entries on Android: Remove App, Edit Home Screen, App Info + native app shortcuts (LauncherApps, max. 4, monochrome symbols) | [MEASURED] |
| T-FOLDER-PANEL (rev.) | folder panel is clear glass | IMG_3130: inside ≈ outside (132 vs. 136) → lightening 0.03, blur = background blur 14, darkening as tint | [MEASURED] |
| M-LAUNCH | opening an app | window grows from the icon to full screen in ≈ **0.23 s**, home screen behind blurs | [MEASURED] roughly (phase 6) |
| M-DRAG-LIFT / M-REFLOW / M-DROP / M-BADGE / M-MENU | phase 2 | not in the material yet | [ESTIMATED] (phase-0 values remain the starting point) |
| M-UNLOCK | unlock → home | EVERY item (icon, widget, dock glass, pill) flies in on its own towards the screen centre — same spring, response × (0.79 + 0.82·r/W) (r = item distance from the centre): centre first, edges and dock last [MEASURED 6 icons tracked, fit < 0.01]; home content zooms in around the screen centre after the lock screen is gone; spring on distance 1/scale, response 0.374 s, damping 0.8, start 1/14 (first visible frame ≈ 4.4×, rest after ≈ 12 frames, no visible overshoot; lock screen fade is the system's) | [MEASURED] user iPhone 10-18 15-17-40, 2 unlocks at 60 fps, badge/icon tracking, fit ±0.05 |

### 3.4 Scroll physics [SOURCED]
Deceleration `UIScrollView.DecelerationRate.normal` = 0.998/ms, `.fast` = 0.99/ms ([Apple](https://developer.apple.com/documentation/uikit/uiscrollview/decelerationrate-swift.struct/normal)) — for App Library/Today view.

---

## 4. Behaviour details

| Behaviour | Status |
|---|---|
| Tap: immediate darkening, no scaling, launch on release | [MEASURED] |
| Long press on an empty area in edit mode → glass menu (4 entries, icon + text, lies over the widget, blurs what's below) | [MEASURED] IMG_3125 |
| Edit mode: status bar gone, 2 glass buttons at the top, badges on all icons + widgets + dock | [MEASURED] |
| Swipe down → search; swipe right from page 1 → Today view (widget list, IMG_3132) | [MEASURED] |
| Folder: background blurs strongly, large title above the panel | [MEASURED] |
| Icon tilt/sparkle | removed in iOS 27 [SOURCED] |

---

## 5. Android limits
1. **System wallpaper pixels** are not readable by normal apps (Android 13+, `READ_WALLPAPER_INTERNAL` is privileged) ([docs](https://developer.android.com/reference/android/app/WallpaperManager), [cvzi](https://github.com/cvzi/getwallpaperdrawable)). Current solution: Android draws its own wallpaper behind the transparent window; for glass and blur we read the pixels with all-files access (MANAGE_EXTERNAL_STORAGE) and reproduce Android's crop (glass/WallpaperCropModel), otherwise we draw our own wallpaper.
2. Blur/shaders: `RenderEffect` (31) + AGSL (33) → minSdk 33.
3. Opening apps (phase 6): no remote transition for third parties (`CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS` = signature permission); available: `ActivityOptions.makeScaleUpAnimation`/`makeClipRevealAnimation`. With the optional Animation Module (system app) the own transition works.
4. The keyboard animation belongs to the system (see M-SEARCH-KB).
5. Emulator: 60 Hz + mouse.

---

## 6. Please check on the iPhone

Done by measurement: model/dimensions ✔ · pill exists in iOS 27 ✔ · tap feedback ✔ · icon corner rounder ✔ · no undo/redo ✔ · default icon size ✔

Still open:
- [ ] iPhone **17 Pro** or **16 Pro**? (dimensions identical — documentation only)
- [ ] Tap an icon and **drag the finger away** (without launching the app): does the darkening fade back softly or jump?
- [ ] How long do you have to press an icon / empty area for edit mode? (feel: ~0.5 s or ~1 s?)
- [ ] Phase 2: a second recording with **dragging an icon** (others move aside), dragging to the edge (page change), deleting an icon, creating a folder by dragging onto another icon. *(Done 10-14.)*
- [ ] Phase 1: a recording with **slow** swiping (hold the finger, release without momentum, also let it snap back) and swiping past the edge of the last page (rubber band).

## Addendum recording6/7 (wallpaper, Customise, glass calibration)

| ID | What | Value | Status |
|---|---|---|---|
| T-WPE | wallpaper editor (recording6 7.7 s) | preview = real home screen 0.699× at (60.33 / 131.33), corner ≈ 38; Cancel/Done capsules 27.33 high, centre y 32; circles Ø 40 at y 797, x 69 + 60.5·i, divider x 291, blur x 332; labels 12 pt, baseline 833 | [MEASURED] |
| T-WPE-SHEET | "Choose Colour" (17.5 s) | glass sheet from y 539.67, sides 6; 6×3 colour circles Ø 33, grid 57.5 from (57.9 / +95.33); slider at +270.33; 18 colours sampled (DesignTokens.wallpaperSwatches) | [MEASURED] |
| T-WPE-PHOTO | photo picker (29.4 s) | sheet from y 62; glass header (X Ø 44, "Photos | Collections", chips 32 high, selected #1BA6FB); grid 3 columns, cells in screen aspect ratio | [MEASURED roughly] |
| T-CUST | Customise sheet (recording7 14.7 / 22.4 s) | glass sheet sides 10, bottom 864; top 594.67 (Tinted: 406); style previews 61 pt at x 67.5/156/244.6/333; labels y 770 (582); light/dark y 836.6 (823); tinted sliders at 643 / 697 | [MEASURED] |
| T-GLASS (rev.) | glass on a photo wallpaper (recording7 2.1 s, cat) | almost sharp in the dock (detail inside ≈ ½ outside) → blur 0.6 dp; saturation inside ≈ 1.28× outside → 1.2 (previously 1.5 = too orange) | [MEASURED] |
| T-CTX.editTint | edit-menu glass | over a light wallpaper 145 instead of 188 → tint 0.24 | [MEASURED] |
| T-LABEL (adaptive) | labels on a dark/colourful wallpaper | white with a slight shadow as soon as the mean brightness < 0.6 | [ESTIMATED] |

## Addendum recording8 (Today view, widgets, resize)

| ID | What | Value | Status |
|---|---|---|---|
| T-WIDGET | widget sizes | small 164 × 164, wide 349.33, large 349.33 × 364.67; extends 3.67 pt beyond the icon edges; corner radius ≈ 22 (continuous); snap only into column 0/2 | [MEASURED] |
| T-TODAY | Today view (12.5 s) | widgets without labels, top 91.67, margin 26.33, gap 21.33; wallpaper strongly blurred; Batteries widget = dark tinted glass (inside ≈ 35 % of the background brightness) | [MEASURED] |
| T-WIDGET-HANDLE | size handle (39.55 s) | curved glass bean ≈ 13 pt thick, centre line 3 pt inside the edge, legs ≈ 26 pt | [MEASURED] |
| M-WIDGET-RESIZE | resize via the handle (39.5–48 s) | frame follows the finger, icons reflow live (always from the starting layout), blur = distance to the next size (user observation), snap spring 0.35/0.86 [ESTIMATED] | [MEASURED]/[ESTIMATED] |
| T-WIDGET-MENU | widget context menu (15.2 s) | size bar at the top (small · medium · large · extra large greyed out), current one with a grey background; menu centred below the widget | [MEASURED] |
| T-PICKER | widget picker (26.5 s) | sheet from 123 pt, search field white 11 % (field 57 vs. sheet 32 brightness), widgets at their real size, title bold + grey subtitle | [MEASURED] |
