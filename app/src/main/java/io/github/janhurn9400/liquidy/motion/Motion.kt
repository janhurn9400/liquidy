package io.github.janhurn9400.liquidy.motion

import io.github.janhurn9400.liquidy.tuning.tunable

/**
 * All animation parameters. Every value refers to its ID in docs/RESEARCH.md §3.3.
 * No animation value may live in UI code — only here.
 */
object Motion {

    // M-PAGE — page settle. [MEASURED] 4 swipes: response 0.32–0.38 s, ζ 0.89–0.92
    val page = iosSpring("M-PAGE", "Page settle", response = 0.36f, dampingFraction = 0.90f)

    // M-PAGE-THRESH — page change from this finger velocity or this offset. [ESTIMATED]
    // 300 → 120 pt/s: on the iPhone a short little flick already changes the page (user 10-18, real phone vs. iPhone)
    val pageFlingVelocity = tunable("M-PAGE-THRESH.velocity", "Page change from velocity", "Motion", 120f, 50f, 1500f, "pt/s")
    val pageDistanceFraction = tunable("M-PAGE-THRESH.distance", "Page change from offset", "Motion", 0.5f, 0.2f, 0.8f, "×width")

    // M-RUBBER — rubber band at the first/last page edge: (x·d·c)/(d + c·x). [SOURCED] UIScrollView c = 0.55
    val rubberBandC = tunable("M-RUBBER.c", "Rubber band constant", "Motion", 0.55f, 0.1f, 1.0f)

    // M-PRESS — icon dimming on touch down: immediate (≤ 1 frame). [MEASURED]
    val pressInDelay = tunable("M-PRESS.delay", "Dimming – delay", "Motion", 0f, 0f, 200f, "ms")
    // M-PRESS-CANCEL — fade back without launching the app. [ESTIMATED]
    val pressOut = iosSpring("M-PRESS-CANCEL", "Dimming back", response = 0.25f, dampingFraction = 1.0f)

    // M-PILL — "Search" ↔ page dots. [MEASURED] crossfade ≈ 0.15 s, dots stay ≈ 0.5 s after settling
    val pillCrossfade = tunable("M-PILL.fade", "Pill crossfade", "Motion", 150f, 0f, 600f, "ms")
    val pillDotsHold = tunable("M-PILL.hold", "Keep dots", "Motion", 500f, 0f, 2000f, "ms")

    // ── Phase 2: edit mode ──────────────────────────────────────────────────
    // M-LONGPRESS — hold time until edit mode. [ESTIMATED] UIKit context menu ≈ 0.5 s
    val longPress = tunable("M-LONGPRESS", "Long press duration", "Editing", 500f, 200f, 1200f, "ms")

    // M-JIGGLE — [MEASURED] 4 icons, sine, random phase per icon, starts immediately at full amplitude
    val jiggleRotation = tunable("M-JIGGLE.rot", "Jiggle – rotation", "Editing", 1.70f, 0f, 5f, "°")
    val jiggleRotationPeriod = tunable("M-JIGGLE.rotP", "Jiggle – rotation period", "Editing", 0.256f, 0.1f, 0.6f, "s")
    val jiggleTranslation = tunable("M-JIGGLE.tr", "Jiggle – offset", "Editing", 0.39f, 0f, 2f, "pt")
    val jigglePeriodX = tunable("M-JIGGLE.xP", "Jiggle – period x", "Editing", 0.268f, 0.1f, 0.6f, "s")
    val jigglePeriodY = tunable("M-JIGGLE.yP", "Jiggle – period y", "Editing", 0.284f, 0.1f, 0.6f, "s")
    // M-JIGGLE-STOP — settling when leaving. [ESTIMATED]
    val jiggleStop = iosSpring("M-JIGGLE-STOP", "Jiggle fade out", response = 0.2f, dampingFraction = 1.0f)

    // M-BADGE — minus badges / edit buttons appear. [ESTIMATED]
    val badge = iosSpring("M-BADGE", "Badge appear", response = 0.3f, dampingFraction = 0.8f)
    // M-BADGE-DRAG — minus badge of a single icon: pick-up → shrinks away (the original fades out as a ghost meanwhile),
    // drop → grows from the centre as soon as the icon has landed; ≈ 0.1 s each without overshoot [MEASURED user iPhone 10-14,
    // 43.32–43.42 s / 44.89–44.99 s]
    val badgeDrag = iosSpring("M-BADGE-DRAG", "Badge on pick-up/drop", response = 0.15f, dampingFraction = 1f)

    // M-DRAG-LIFT — icon grows while dragging. [ESTIMATED]
    val dragLift = iosSpring("M-DRAG-LIFT", "Lift icon", response = 0.3f, dampingFraction = 0.75f)
    // M-REFLOW — other icons make way. [ESTIMATED] close to the measured system springs (0.34 s, critical)
    val reflow = iosSpring("M-REFLOW", "Reflow", response = 0.4f, dampingFraction = 0.9f)
    /**
     * Unlock: the whole home content (icons, widgets, dock) zooms in from far away around the screen centre once the
     * lock screen is gone. Spring on the "distance" 1/scale [MEASURED user iPhone 10-18 15-17-40, 2 unlocks, 60 fps:
     * first visible frame ≈ 4.4×, 1.0 after ≈ 12 frames, fit ±0.05 → ω 16.8 rad/s, ζ 0.8, start 1/14 2.5 frames earlier].
     */
    val unlock = iosSpring("M-UNLOCK", "Unlock zoom", response = 0.374f, dampingFraction = 0.8f)
    val unlockStartDistance = tunable("M-UNLOCK.start", "Unlock zoom – start distance (1/scale)", "Motion", 0.07f, 0.02f, 1f)
    /** Today view: widgets make way springily when resizing/moving (iOS: slight overshoot) [ESTIMATED]. */
    /** Page overview open/close: page shrinks into its card in about 0.25 s (recording 10-08 09:55, frame 357–370) [MEASURED roughly]. */
    /** Widget stack settle: measured ≈ 0.27 s to rest, no overshoot (recording 10-08 09:56, frame 858–874). */
    /** Widget flies from the picker to its slot: ≈ 0.25 s (47.70 → 47.95 s) [MEASURED roughly]. */
    val widgetFly = iosSpring("M-WIDGET-FLY", "Widget – flight from the picker", response = 0.55f, dampingFraction = 0.9f)
    /** After landing: 1.12× → 1.0, content sharp (47.95 → 48.2 s). */
    val widgetSettle = iosSpring("M-WIDGET-SETTLE", "Widget – settle", response = 0.45f, dampingFraction = 0.72f)
    val stackSnap = iosSpring("M-STACK", "Widget stack – settle", response = 0.32f, dampingFraction = 1f)
    /** Fade the stack's glass rim/dots in/out (frame 858 on, ≈ 896 gone) [ESTIMATED]. */
    /** After settling the content regrows to full size inside the glass rim: ≈ 0.4 s (frame 865–889) [MEASURED roughly]. */
    val stackGrow = iosSpring("M-STACK.grow", "Widget stack – regrow", response = 0.47f, dampingFraction = 1f)
    val stackFrame = iosSpring("M-STACK.frame", "Widget stack – rim", response = 0.25f, dampingFraction = 1f)
    /** "Edit Stack" open/close [ESTIMATED] after recording 10-08 10:21. */
    /** Panel grows from the stack to the centre: ≈ 0.3 s, slightly overshooting (recording 10-08 10:21, frame 288–302). */
    val stackEditor = iosSpring("M-STACK.edit", "Edit stack", response = 0.36f, dampingFraction = 0.82f)
    val pagesOverview = iosSpring("M-PAGES", "Page overview", response = 0.38f, dampingFraction = 1f)
    val todayReflow = iosSpring("M-TODAY-REFLOW", "Today – reflow", response = 0.42f, dampingFraction = 0.78f)
    // User 10-14: icons made way too quickly (before the folder intent) → longer hold next to a slot [ESTIMATED]
    val reflowDelay = tunable("M-REFLOW.delay", "Reflow – delay", "Editing", 300f, 0f, 800f, "ms")
    // M-DROP — icon falls into its slot. [ESTIMATED]
    val drop = iosSpring("M-DROP", "Drop", response = 0.4f, dampingFraction = 0.85f)
    // M-EDGE — page change when the icon is held at the edge [MEASURED] user iPhone 10-14 (26.8–27.9 s): immediately ≈ 5 pt
    // jolt, then the page creeps steadily to ≈ 40 pt ("peeks over"); after ≈ 0.85 s haptics + change with M-PAGE
    val edgeFlipDelay = tunable("M-EDGE.delay", "Hold at edge until page change", "Editing", 850f, 100f, 1500f, "ms")
    val edgePeekNudge = tunable("M-EDGE.nudge", "Edge: first jolt", "Editing", 5f, 0f, 30f, "pt")
    val edgePeekMax = tunable("M-EDGE.peek", "Edge: peek until the change", "Editing", 40f, 0f, 120f, "pt")
    val edgeNudge = iosSpring("M-EDGE.nudge", "Edge: jolt", response = 0.1f, dampingFraction = 1f)
    val edgeFlipRepeat = tunable("M-EDGE.repeat", "Next page change after", "Editing", 900f, 200f, 2000f, "ms")

    // M-ALERT — delete dialog open/close. [ESTIMATED]
    val alert = iosSpring("M-ALERT", "Dialog", response = 0.35f, dampingFraction = 0.85f)

    // ── Phase 3: folders ─────────────────────────────────────────────────────────────
    // M-FOLDER — opening AND closing. [MEASURED] response 0.34 s, ζ 1.0, out of the folder icon
    val folder = iosSpring("M-FOLDER", "Folder open/close", response = 0.34f, dampingFraction = 1.0f)
    // M-FOLDER-HANDOFF — crossfade closed icon ↔ panel in the smallest phase (progress 0…x) [ESTIMATED]
    val folderHandoff = tunable("M-FOLDER.handoff", "Icon↔panel crossfade until progress", "Folders", 0.12f, 0.01f, 0.5f)
    // M-MERGE — folder intent + glass hint [MEASURED] user iPhone 10-14 (10.6 s): reacts as soon as the icon STANDS STILL
    // over the target (hint ≈ 20 ms after stopping); glass grows in ≈ 0.1 s without overshoot (edge 36/76/92 % after 1/3/5 frames)
    val mergeDelay = tunable("M-MERGE.delay", "Standstill over icon until folder intent", "Folders", 60f, 0f, 1200f, "ms")
    val mergeHint = iosSpring("M-MERGE", "Folder hint", response = 0.11f, dampingFraction = 1.0f)
    // M-MERGE-DROP — released icon flies into the folder's free mini slot and shrinks meanwhile [MEASURED] 11.40–11.78 s,
    // fit response 0.46 s, ζ 1.0 (RMS 1.2 %)
    val mergeDrop = iosSpring("M-MERGE-DROP", "Icon flies into the folder", response = 0.46f, dampingFraction = 1.0f)
    // M-FOLDER-OUT — dragging an app out of the open folder: the folder closes only once the icon centre has been outside the
    // panel this long [MEASURED] 23.27 → 23.42 s
    val folderOutDelay = tunable("M-FOLDER-OUT.delay", "Outside the folder until close", "Folders", 150f, 0f, 800f, "ms")

    // ── Phase 4: search (swipe down) — rev. 4, [MEASURED] recording3 (8 drags, frame by frame) ──────
    // The control variable is the home offset h (pt). Finger → h with UIScrollView rubber band (M-RUBBER, d = screen height) [DERIVED].
    // NOTHING opens while dragging (not even the keyboard); only on release h ≥ commit decides.
    val searchCommit = tunable("M-SEARCH.commit", "Search opens on release from h", "Search", 60f, 20f, 150f, "pt") // 40 pt cancels, 90 pt opens
    val searchBlurRamp = tunable("M-SEARCH.blurRamp", "Full blur from h", "Search", 12f, 1f, 60f, "pt") // blur starts almost immediately
    // Block fades in AND sharpens meanwhile; visual comparison: at h ≈ 52 half visible + strongly blurred, at h ≈ 90 sharp (same back and forth)
    val searchBlockFadeStart = tunable("M-SEARCH-BLOCK.fadeStart", "Suggestions fade in from h", "Search", 15f, 0f, 150f, "pt")
    val searchBlockFadeEnd = tunable("M-SEARCH-BLOCK.fadeEnd", "Suggestions fully visible from h", "Search", 65f, 10f, 200f, "pt")
    val searchBlockSharpStart = tunable("M-SEARCH-BLOCK.sharpStart", "Suggestions sharpen from h", "Search", 45f, 0f, 150f, "pt")
    val searchBlockSharpEnd = tunable("M-SEARCH-BLOCK.sharpEnd", "Suggestions fully sharp from h", "Search", 95f, 20f, 250f, "pt")
    // M-SEARCH-MORPH — "Search" pill morphs into the search field, already while dragging [MEASURED] recording5:
    // pill stays sharp, moves along with ¼ of h; morph starts at h ≈ 94 pt, grows in ≈ 0.3 s to FULL field width (≈ 386 pt); back below ≈ 62 pt (recording3)
    val searchPillFollow = tunable("M-SEARCH-MORPH.follow", "Pill follows (× h)", "Search", 0.25f, 0f, 1f, "×")
    val searchMorphOn = tunable("M-SEARCH-MORPH.on", "Pill → search field from h", "Search", 94f, 20f, 200f, "pt")
    val searchMorphOff = tunable("M-SEARCH-MORPH.off", "Search field → pill below h", "Search", 62f, 0f, 200f, "pt")
    val searchMorph = iosSpring("M-SEARCH-MORPH", "Pill ↔ search field", response = 0.3f, dampingFraction = 0.9f)
    // M-SEARCH-KB — keyboard (app-driven): peeks out on morph, comes up with the iOS keyboard spring on release.
    // [MEASURED] spring 0.44 s / ζ 0.85 (recording1); peek ≈ 34 pt, 0.1 s after the morph (recording5)
    val searchKeyboard = iosSpring("M-SEARCH-KB", "Keyboard", response = 0.44f, dampingFraction = 0.85f)
    val searchKeyboardPeek = tunable("M-SEARCH-KB.peek", "Keyboard peeks out on morph", "Search", 34f, 0f, 120f, "pt")
    // Crossfade the content staggered so "Search" (pill) and placeholder (field) never show twice [ESTIMATED]
    val searchMorphPillOut = tunable("M-SEARCH-MORPH.pillOut", "Pill content gone until morph", "Search", 0.35f, 0.05f, 1f)
    val searchMorphFieldIn = tunable("M-SEARCH-MORPH.fieldIn", "Field content from morph", "Search", 0.45f, 0f, 0.95f)
    // Block runs 1:1 with the home screen: offset = h + base (fit: 1.07·h − 31)
    val searchBlockBase = tunable("M-SEARCH-BLOCK.base", "Suggestions offset = h + …", "Search", -31f, -120f, 0f, "pt")
    // M-SEARCH — open: block AND home screen spring back to 0 pt (home screen stays shrunk + dimmed). [MEASURED] 0.46 s / ζ 0.81 (recording1); home return ≈ 0.3 s (recording3)
    val search = iosSpring("M-SEARCH", "Search settle", response = 0.46f, dampingFraction = 0.81f)
    // M-SEARCH-CLOSE — close/cancel. [MEASURED] 0.28 / 0.30 s, ζ 1.0 / 0.97 (recording3)
    val searchClose = iosSpring("M-SEARCH-CLOSE", "Search close", response = 0.29f, dampingFraction = 1.0f)

    // ── Phase 4: App Library ───────────────────────────────────────────────────────
    // M-SCROLL — decelerate like UIScrollView.DecelerationRate.normal = 0.998/ms [SOURCED].
    // v(t) = v0·0.998^(t/ms) = v0·e^(−2.002·t/s). Compose exponentialDecay: v0·e^(−4.2·f·t) → f = 2.002/4.2 = 0.4767 [DERIVED]
    val scrollDecayFriction = tunable("M-SCROLL.friction", "Scroll deceleration (frictionMultiplier)", "App Library", 0.4767f, 0.1f, 2f)
    // M-SCROLL-BOUNCE — spring back after overscrolling. [ESTIMATED] critically damped like the measured system springs
    val scrollBounce = iosSpring("M-SCROLL-BOUNCE", "Spring back", response = 0.4f, dampingFraction = 1.0f)

    // M-LIB-LIST — App Library → A–Z list by pulling down [MEASURED] recording2 10.1–11.9 s / 12.97–13.55 s (frame by frame):
    // As soon as you pull, the tiles blur in ≈ 0.12 s; the list fades in from ≈ 0.3 s, dimming over ≈ 0.75 s.
    // Close ("Cancel"): list gone in ≈ 0.17 s, tiles sharpen again from ≈ 0.19 s (≈ 0.38 s), total ≈ 0.57 s.
    val libTilesOut = tunable("M-LIB-LIST.tilesOut", "Tiles blurred", "App Library", 120f, 0f, 600f, "ms")
    val libListInDelay = tunable("M-LIB-LIST.listDelay", "List fade in from", "App Library", 300f, 0f, 800f, "ms")
    val libListIn = tunable("M-LIB-LIST.listIn", "List fade in", "App Library", 450f, 0f, 1000f, "ms")
    val libListDimIn = tunable("M-LIB-LIST.dimIn", "Dimming (open)", "App Library", 750f, 0f, 1500f, "ms")
    val libListOut = tunable("M-LIB-LIST.listOut", "List fade out", "App Library", 170f, 0f, 600f, "ms")
    val libTilesInDelay = tunable("M-LIB-LIST.tilesDelay", "Tiles sharp from", "App Library", 190f, 0f, 800f, "ms")
    val libTilesIn = tunable("M-LIB-LIST.tilesIn", "Tiles sharp", "App Library", 380f, 0f, 1000f, "ms")
    val libListDimOut = tunable("M-LIB-LIST.dimOut", "Dimming (close)", "App Library", 570f, 0f, 1500f, "ms")
    // While dragging the list follows the finger 1:1, the search field only by ⅓ (ratio constant 3.0 over 0.9 s)
    val libListFieldFollow = tunable("M-LIB-LIST.follow", "Search field follows (× finger)", "App Library", 0.333f, 0f, 1f, "×")
    // Release: list + field spring back, initial velocity 0. Fit: response ≈ 0.5 s, critically damped
    val libListSettle = iosSpring("M-LIB-LIST-SETTLE", "List settle", response = 0.5f, dampingFraction = 1.0f)
    // M-LIB-CAT — expand category [MEASURED] recording2 17.50–18.10 s, frame by frame (multi-scale template match):
    // grid + title zoom as ONE group out of the mini cluster (scale 0 → 1); fit response 0.54 s, ζ 0.9, rms 0.005.
    // The 4 cluster icons fly from their mini slots into their grid slots; the cluster in the tile is empty immediately.
    val libCategory = iosSpring("M-LIB-CAT", "Category open", response = 0.54f, dampingFraction = 0.9f)
    // Collapsing is much faster: group back in the cluster in ≈ 0.2 s (18.60–18.81 s) [MEASURED roughly]
    val libCategoryClose = iosSpring("M-LIB-CAT-CLOSE", "Category close", response = 0.32f, dampingFraction = 1.0f)
    // App Library behind: shrinks around (centre, 0.48·height) to 0.88 (fit over 4 anchors) and is gone at progress ≈ 0.55 (blurred + faded out)
    val libCategoryScale = tunable("M-LIB-CAT.scale", "App Library shrinks to", "App Library", 0.88f, 0.7f, 1f, "×")
    val libCategoryScaleEnd = tunable("M-LIB-CAT.scaleEnd", "… reached at progress", "App Library", 0.5f, 0.1f, 1f)
    val libCategoryPivotY = tunable("M-LIB-CAT.pivotY", "Shrink centre (× height)", "App Library", 0.48f, 0f, 1f, "×")
    val libCategoryFadeEnd = tunable("M-LIB-CAT.fadeEnd", "App Library gone at progress", "App Library", 0.55f, 0.1f, 1f)
    val libCategoryTitleFrom = tunable("M-LIB-CAT.titleFrom", "Title fades in from progress", "App Library", 0.5f, 0f, 1f)

    // M-CTXMENU — long press on an icon → context menu [MEASURED] recording4 (3 long presses, frame by frame):
    // touch down immediately dark (M-PRESS); from +0.20 s the icon slowly grows to 1.23 (until +0.73 s); from +0.47 s the dark fades out (0.2 s).
    // At +0.82 s the menu opens: icon in ≈ 0.08 s to 1.15 and 10 pt up; menu grows out of the icon (≈ 0.15 s, ≈ 1 % overshoot,
    // text scales along); background to 48 % in ≈ 0.2 s. Close: menu gone in ≈ 0.1 s, background ≈ 0.22 s.
    val ctxDelay = tunable("M-CTXMENU.delay", "Menu opens after", "Context menu", 820f, 300f, 1500f, "ms")
    val ctxGrowDelay = tunable("M-CTXMENU.growDelay", "Icon grows from", "Context menu", 200f, 0f, 800f, "ms")
    val ctxGrowDuration = tunable("M-CTXMENU.grow", "Icon grows over", "Context menu", 530f, 50f, 1200f, "ms")
    val ctxPressScale = tunable("M-CTXMENU.pressScale", "Icon grows to", "Context menu", 1.23f, 1f, 1.5f, "×")
    val ctxPressFadeDelay = tunable("M-CTXMENU.fadeDelay", "Dark fades out from", "Context menu", 470f, 0f, 1000f, "ms")
    val ctxPressFade = tunable("M-CTXMENU.fade", "Dark fades out over", "Context menu", 200f, 0f, 600f, "ms")
    val ctxLiftScale = tunable("M-CTXMENU.liftScale", "Icon with open menu", "Context menu", 1.15f, 1f, 1.5f, "×")
    val ctxLiftY = tunable("M-CTXMENU.liftY", "Icon lifts by", "Context menu", 10f, 0f, 30f, "pt")
    val ctxLift = iosSpring("M-CTXMENU-LIFT", "Lift icon", response = 0.15f, dampingFraction = 1.0f)
    val ctxMenu = iosSpring("M-CTXMENU-OPEN", "Menu open", response = 0.3f, dampingFraction = 0.8f)
    val ctxMenuFrom = tunable("M-CTXMENU.from", "Menu grows from scale", "Context menu", 0.3f, 0f, 1f, "×")
    val ctxMenuClose = iosSpring("M-CTXMENU-CLOSE", "Menu close", response = 0.2f, dampingFraction = 1.0f)
    val ctxDimIn = tunable("M-CTXMENU.dimIn", "Dimming (open)", "Context menu", 200f, 0f, 600f, "ms")
    val ctxDimOut = tunable("M-CTXMENU.dimOut", "Dimming (close)", "Context menu", 220f, 0f, 600f, "ms")

    // M-WPE — wallpaper editor: home screen shrinks to the preview / sheets come up [ESTIMATED] after recording6 (visual comparison)
    val wpeAppear = iosSpring("M-WPE", "Editor open/close", response = 0.45f, dampingFraction = 1.0f)
    val wpeSheet = iosSpring("M-WPE-SHEET", "Sheet open/close", response = 0.4f, dampingFraction = 1.0f)

    // M-WIDGET-RESIZE — handle bottom right in edit mode (recording8 39.5–48 s, frame-by-frame review):
    // the rect follows the finger continuously, icons make way live; blur follows the distance to the nearest size
    // (exactly sharp at small/medium/large — user observation on the iPhone). Release → snaps in, remaining blur fades out.
    val widgetResize = iosSpring("M-WIDGET-RESIZE", "Widget settle", response = 0.35f, dampingFraction = 0.86f)
    val widgetResizeBlurOut = tunable("M-WIDGET-RESIZE.blurOut", "Content sharp again", "Widgets", 220f, 0f, 800f, "ms")

    // M-HOME-RETURN — app close: home screen (Android wallpaper + icons) appears blurred and sharpens,
    // nothing moves (iOS, user observation). Strength like the open search, spring [ESTIMATED]
    val homeReturn = iosSpring("M-HOME-RETURN", "App close: sharpen", response = 0.45f, dampingFraction = 1.0f)
    // M-APP-OPEN — app open: home screen (Android wallpaper + icons) blurs while the app grows out of the icon [ESTIMATED]
    val appOpen = iosSpring("M-APP-OPEN", "App open: blur", response = 0.35f, dampingFraction = 1.0f)
    val homeReturnBlur = tunable("M-HOME-RETURN.blur", "App close: initial blur", "Motion", 20f, 0f, 60f, "pt")

    // M-APP-LAUNCH — app open (only with the Animation Module) [MEASURED] recording9 2.77 s (Phone in the dock, 60 fps, frame by frame):
    // card opaque from the start, app scaled to card width, anchored at the top, cut off at the bottom. Three critically damped
    // springs: vertical centre fast (0.15 s, rms 0.011), width + horizontal centre 0.26 s (rms 0.007), height 0.30 s (rms 0.015)
    // → card first rises above the dock, then full screen. (iOS's slight trapezoid distortion isn't possible with SurfaceControl.)
    val appLaunch = iosSpring("M-APP-LAUNCH", "App open: width/horizontal", response = 0.26f, dampingFraction = 1.0f)
    val appLaunchY = iosSpring("M-APP-LAUNCH-Y", "App open: vertical centre", response = 0.15f, dampingFraction = 1.0f)
    val appLaunchH = iosSpring("M-APP-LAUNCH-H", "App open: height", response = 0.30f, dampingFraction = 1.0f)

    /** Makes sure the object is initialised and all tunables are registered. */
    fun touch() = Unit
}
