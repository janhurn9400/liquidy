package io.github.janhurn9400.liquidy

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import io.github.janhurn9400.liquidy.data.AppRepository
import io.github.janhurn9400.liquidy.data.LayoutStore
import io.github.janhurn9400.liquidy.tuning.DebugOptions
import io.github.janhurn9400.liquidy.ui.home.HomeModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.Wallpaper
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import io.github.janhurn9400.liquidy.tuning.Tunables
import io.github.janhurn9400.liquidy.ui.debug.DebugPanel
import io.github.janhurn9400.liquidy.ui.debug.DebugState
import io.github.janhurn9400.liquidy.ui.debug.threeFingerLongPress
import io.github.janhurn9400.liquidy.ui.home.HomeScreen
import io.github.janhurn9400.liquidy.ui.home.PagerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.roundToInt

/** The status bar is hidden when opening a folder up to this progress [ESTIMATED]. */
private const val FOLDER_STATUS_FADE = 0.5f

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private var model: HomeModel? = null
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light wallpaper → dark status bar icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        registerReceiver(screenReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        })
        // Started as a normal app (app icon, adb am start -n) although we're the default launcher: forward into the ONE
        // home task — otherwise a second instance runs with its own state and an empty card shows
        // in recent apps.
        if (redirectToHomeTask()) return
        Tunables.init(this)

        // Pre-render icons at 1.5× size so larger T-ICON values in the debug panel stay sharp
        val iconPx = (DesignTokens.iconSize.value * resources.displayMetrics.density * 1.5f).roundToInt()
        repository = AppRepository(this, iconPx).also { it.start() }
        io.github.janhurn9400.liquidy.data.Haptics.load(this)
        io.github.janhurn9400.liquidy.data.FoldableStyle.load(this)
        io.github.janhurn9400.liquidy.data.OwnStatusBar.load(this)
        io.github.janhurn9400.liquidy.glass.GlassClarity.load(this)
        io.github.janhurn9400.liquidy.data.Quirks.load(this)
        io.github.janhurn9400.liquidy.data.UniversalSearch.WebSuggestions.load(this)
        androidWidgets = io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetHost(this) // before onStart (startListening)
        android.app.WallpaperManager.getInstance(this).addOnColorsChangedListener(colorsListener, android.os.Handler(mainLooper))
        refreshSystemWallpaperId()

        // Launcher: Back only ends edit mode
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val m = model ?: return
                when {
                    m.wallpaperEditorOpen -> m.wallpaperEditorOpen = false
                    m.customiseOpen -> m.closeCustomise()
                    m.settingsOpen -> m.closeSettings()
                    m.widgetPickerOpen -> { m.widgetPickerOpen = false; m.addingToStack = false }
                    m.searchOpen -> m.closeSearch()
                    m.openCategory != null -> m.closeCategory()
                    m.libListActive -> m.closeLibList()
                    m.openFolder != null -> m.closeFolder()
                    else -> m.exitEdit()
                }
            }
        })
        handleDebugIntent(intent)

        setContent {
            val apps by repository.apps.collectAsState()
            val scope = rememberCoroutineScope()
            remember { io.github.janhurn9400.liquidy.design.IconAppearance.load(this@MainActivity) }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect { io.github.janhurn9400.liquidy.design.IconAppearance.systemDark = systemDark }
            val widgetHost = remember { androidWidgets!! }
            val homeModel = remember { HomeModel(LayoutStore(this), repository, scope).also { model = it } }
            val pager = remember { PagerState() }
            val view = LocalView.current
            LaunchedEffect(apps) { apps?.let { homeModel.onAppsLoaded(it) } }
            LaunchedEffect(Unit) {
                homeModel.onAndroidWidgetRemoved = { widgetHost.delete(it) }
                widgetHost.widgetKeyForId = { id -> homeModel.widgets.entries.firstOrNull { it.value.appWidgetId == id }?.key }
            }
            LaunchedEffect(Unit) {
                homeModel.flyingSurface = { c, r, icon, done, corner -> io.github.janhurn9400.liquidy.ui.home.FlyingIconSurface.show(this@MainActivity, c, r, icon, done, corner) }
            }
            // Compute the style layers (Dark/Clear/Tinted) only when needed
            LaunchedEffect(apps, io.github.janhurn9400.liquidy.design.IconAppearance.style) {
                if (io.github.janhurn9400.liquidy.design.IconAppearance.style != io.github.janhurn9400.liquidy.design.IconStyle.Default) apps?.let { repository.ensureLayers(it) }
            }
            LaunchedEffect(Unit) { snapshotFlow { DebugOptions.demoPages.value }.collect { homeModel.checkDemoPages() } }
            // Home button: end edit mode, otherwise go to the first page (like iOS)
            LaunchedEffect(Unit) {
                homePressed.collect {
                    when {
                        // Launcher settings behave like an app → the home gesture/button closes them (instead of an ×)
                        homeModel.settingsOpen -> homeModel.closeSettings()
                        homeModel.searchOpen || homeModel.searchProgress.value > 0f -> homeModel.closeSearch()
                        homeModel.openFolder != null -> homeModel.closeFolder()
                        homeModel.openCategory != null -> homeModel.closeCategory()
                        homeModel.libListActive -> homeModel.closeLibList()
                        homeModel.editing -> homeModel.exitEdit()
                        else -> pager.animateTo(0)
                    }
                }
            }
            // Edit mode [MEASURED] IMG_3124 and open folder [MEASURED] IMG_3130: status bar gone
            // Own status bar (setting): Android bar permanently gone in the launcher (swiping from the top shows it briefly)
            val ownBar = io.github.janhurn9400.liquidy.data.OwnStatusBar.enabled && !io.github.janhurn9400.liquidy.ui.welcome.WelcomeStore.active
            val hideStatus = ownBar || homeModel.editing || homeModel.wallpaperEditorOpen || homeModel.stackEditKey != null
            // Folder: the status bar fades out/in in place with the folder (instead of sliding up)
            val statusFader = remember { io.github.janhurn9400.liquidy.ui.home.StatusBarFader(window.decorView) }
            LaunchedEffect(homeModel.openFolder) {
                val f = homeModel.openFolder ?: return@LaunchedEffect
                if (hideStatus) return@LaunchedEffect // already gone in edit mode
                fun fade() = 1f - (f.progress.value / FOLDER_STATUS_FADE).coerceIn(0f, 1f)
                statusFader.follow(::fade, done = { f.progress.value >= FOLDER_STATUS_FADE || f.progress.targetValue == 0f }, showAtEnd = { f.progress.targetValue == 0f }) // closed again early → leave visible
                // wait until the folder closes, then fade back in with it
                snapshotFlow { f.progress.targetValue }.first { it == 0f }
                statusFader.follow(::fade, done = { f.progress.value <= 0.02f }, showAtEnd = { true })
            }
            LaunchedEffect(hideStatus) {
                applyStatusBar(hideStatus)
            }
            // Search: dark background → light status bar icons [MEASURED] IMG_3131
            val welcomeActive = io.github.janhurn9400.liquidy.ui.welcome.WelcomeStore.active
            LaunchedEffect(homeModel.searchOpen, DesignTokens.wallpaperIsDark, welcomeActive, homeModel.settingsOpen) {
                // Settings: light background → dark icons
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                    welcomeActive || homeModel.settingsOpen || (!homeModel.searchOpen && !DesignTokens.wallpaperIsDark)
            }
            // Pages may disappear after editing → bring the pager back into a valid range
            LaunchedEffect(homeModel.pages.size) {
                if (pager.currentPage > homeModel.pages.lastIndex) pager.animateTo(homeModel.pages.lastIndex.coerceAtLeast(0))
            }
            // Duo style on narrower foldables: scale the UI down uniformly to the Duo width (design/UiScale)
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            val win = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            androidx.compose.runtime.SideEffect { io.github.janhurn9400.liquidy.data.FoldableStyle.updateWindow(this@MainActivity, win.width, win.height) }
            androidx.compose.runtime.LaunchedEffect(win.width, win.height) {
                io.github.janhurn9400.liquidy.glass.WallpaperCropModel.rememberDisplay(this@MainActivity)
                // Unfolded/folded: Android sometimes still showed the old crop afterwards until something redrew (e.g. starting
                // edit mode). Only report the position (cheap one-way call, no recomputing of the image) → Android corrects immediately
                applyWallpaperLayout()
            }
            val uiScale = io.github.janhurn9400.liquidy.design.UiScale.compute(
                io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle && io.github.janhurn9400.liquidy.data.FoldableStyle.supportsDuo(this@MainActivity),
                win.width, win.height, baseDensity.density)
            androidx.compose.runtime.SideEffect { io.github.janhurn9400.liquidy.design.UiScale.update(uiScale) }
            androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.ui.widgets.LocalAndroidWidgetHost provides widgetHost,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density * uiScale, baseDensity.fontScale)) {
            BoxWithConstraints(Modifier.fillMaxSize().threeFingerLongPress { DebugState.toggle() }) {
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val colorful = DebugOptions.wallpaperStyle.value >= 0.5f
                var wpSettings by remember { mutableStateOf(io.github.janhurn9400.liquidy.glass.WallpaperStore.load(this@MainActivity)) }
                val fallback = remember(w, h) { Wallpaper.generate(w, h) }
                var wallpaper by remember { mutableStateOf(fallback) }
                // Setting that [wallpaper] belongs to — after "Done" the finished draft image is taken over instead of re-rendering
                var renderedFor by remember { mutableStateOf<io.github.janhurn9400.liquidy.glass.WallpaperSettings?>(null) }
                LaunchedEffect(wpSettings, w, h, colorful) {
                    // also re-render on a new window size (foldable unfolded/folded) — otherwise the image for the glass doesn't match
                    if (renderedFor == wpSettings && !colorful && wallpaper.width == w && wallpaper.height == h) return@LaunchedEffect
                    wallpaper = if (colorful && wpSettings.kind == io.github.janhurn9400.liquidy.glass.WallpaperKind.Pair) Wallpaper.generateColorful(w, h)
                    else io.github.janhurn9400.liquidy.glass.WallpaperRenderer.render(this@MainActivity, wpSettings, w, h)
                    renderedFor = wpSettings
                }
                // Also set the wallpaper chosen in the editor as the system wallpaper (Android shows that behind our window)
                LaunchedEffect(renderedFor) {
                    val s = renderedFor ?: return@LaunchedEffect
                    if (!colorful) io.github.janhurn9400.liquidy.glass.WallpaperStore.syncSystemWallpaper(this@MainActivity, s, wallpaper)
                    refreshSystemWallpaperId()
                }
                // Which image does Android show? Ours → draw nothing (window transparent). Another one (chosen in Android's
                // settings) → with read access use its pixels for glass/blur, otherwise draw our own image.
                val systemOurs = remember(systemWallpaperId, renderedFor) { io.github.janhurn9400.liquidy.glass.WallpaperStore.systemIsOurs(this@MainActivity) }
                var systemPixels by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                // With read access the Android image is always the only source (also for ones chosen in the picker — we set those as the Android image)
                LaunchedEffect(systemWallpaperId, canReadSystemWallpaper, w, h) {
                    systemPixels = if (colorful) null else io.github.janhurn9400.liquidy.glass.SystemWallpaper.read(this@MainActivity, w, h)
                }
                // Android draws its wallpaper itself behind our transparent window (so it's also right during the
                // swipe-home gesture). For the glass/blur we need the same pixels: Android's crop + zoom is
                // recomputed (glass/WallpaperCropModel) and applied to our copy.
                val shownOurs = remember(wallpaper, w, h) { io.github.janhurn9400.liquidy.glass.WallpaperStore.asShownBySystem(this@MainActivity, wallpaper) }
                val homeWallpaper = when {
                    colorful -> wallpaper
                    // "Pair" already is Android's displayed crop → don't crop again
                    systemOurs && wpSettings.kind != io.github.janhurn9400.liquidy.glass.WallpaperKind.Pair -> shownOurs
                    else -> systemPixels ?: wallpaper
                }
                // Draw ourselves only if we neither know nor may read Android's image (otherwise the glass wouldn't match)
                val drawOwnWallpaper = colorful || (!systemOurs && systemPixels == null)
                val launchNow: (io.github.janhurn9400.liquidy.data.AppEntry, androidx.compose.ui.geometry.Rect) -> Unit = { app, bounds ->
                        // without an icon rect (search, A–Z list): no specific home icon
                        if (bounds.isEmpty) homeModel.launchedIconKey = null
                        homeModel.libraryLaunch = null // App Library launches set it again afterwards
                        val r = android.graphics.Rect(
                            bounds.left.roundToInt(), bounds.top.roundToInt(),
                            bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                        )
                        // Open: the icon image itself grows into the app window (thumbnail scale-up) — the most iOS-like of the
                        // system animations allowed for third-party launchers (the Pixel remote animation needs a system permission)
                        val thumb = if (r.width() > 0 && r.height() > 0) roundedIconBitmap(app.icon, r.width(), r.height()) else null
                        // With the Animation Module (system app): own iOS animation, the window grows out of the icon
                        val remote = io.github.janhurn9400.liquidy.data.AppLaunchTransition.optionsFor(
                            android.graphics.RectF(r), DesignTokens.iconCorner.value * io.github.janhurn9400.liquidy.design.UiScale.density(this@MainActivity),
                            // exactly the tapped icon (with several icons of the same app), otherwise the app
                            homeModel.launchedIconKey?.takeIf { io.github.janhurn9400.liquidy.ui.home.appKeyOf(it) == app.key } ?: app.key)
                        val opts = (remote ?: if (thumb != null) ActivityOptions.makeThumbnailScaleUpAnimation(view, thumb, r.left, r.top)
                            else ActivityOptions.makeScaleUpAnimation(view, r.left, r.top, r.width(), r.height())).toBundle()
                        repository.launch(app, r, opts)
                        homeModel.noteLaunch(app.key)
                        io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins.remove(app.component.packageName) // now opened via the icon
                        homeModel.onAppLaunching()
                }
                // Tapped while the system's close animation (swipe home) is still running: Shell would MERGE our open transition
                // into the playing recents transition, so our remote animation never ran (logcat "Transition was merged").
                // Hold the launch until that animation is done (window focus back), at most RETURN_WAIT_MS.
                val onLaunch: (io.github.janhurn9400.liquidy.data.AppEntry, androidx.compose.ui.geometry.Rect) -> Unit = { app, bounds ->
                    if (returnAnimating && io.github.janhurn9400.liquidy.data.AppLaunchTransition.enabled) {
                        pendingLaunch = { launchNow(app, bounds) }
                        window.decorView.postDelayed({ runPendingLaunch() }, RETURN_WAIT_MS)
                    } else launchNow(app, bounds)
                }
                // Adapt labels/pill text to the wallpaper brightness (the editor preview uses the draft)
                // Brightness of the ACTUALLY displayed image (Android wallpaper), not of our own launcher image
                LaunchedEffect(homeWallpaper, homeModel.wallpaperEditorOpen) { // also after "Cancel" (the draft had switched)
                    DesignTokens.wallpaperIsDark = io.github.janhurn9400.liquidy.glass.WallpaperRenderer.luminance(homeWallpaper) < DesignTokens.WALLPAPER_DARK_LUMA
                }
                // Wallpaper editor: puts frame + controls around the SAME home screen (no rebuild → no flash)
                val editor = remember(homeModel.wallpaperEditorOpen) {
                    if (homeModel.wallpaperEditorOpen) io.github.janhurn9400.liquidy.ui.wallpaper.WallpaperEditorState(wpSettings, wallpaper) else null
                }
                editor?.let { e ->
                    LaunchedEffect(e.draftBitmap) {
                        DesignTokens.wallpaperIsDark = io.github.janhurn9400.liquidy.glass.WallpaperRenderer.luminance(e.draftBitmap) < DesignTokens.WALLPAPER_DARK_LUMA
                    }
                }
                io.github.janhurn9400.liquidy.ui.wallpaper.WallpaperEditorFrame(editor) {
                    HomeScreen(model = homeModel, pager = pager, wallpaper = editor?.draftBitmap ?: homeWallpaper, onLaunch = onLaunch,
                        drawWallpaper = editor != null || drawOwnWallpaper, wallpaperBlur = { editor?.blurAnim?.value ?: 0f })
                }
                editor?.let { e ->
                    io.github.janhurn9400.liquidy.ui.wallpaper.WallpaperEditorChrome(
                        state = e,
                        onDone = { chosen, bitmap ->
                            val s = chosen.copy(userChosen = true)
                            // Take over the image immediately (no flash of the old one), copy the photo permanently into app storage
                            wallpaper = bitmap
                            renderedFor = s
                            homeModel.wallpaperEditorOpen = false
                            scope.launch {
                                val kept = io.github.janhurn9400.liquidy.glass.WallpaperStore.keepPhoto(this@MainActivity, s)
                                renderedFor = kept
                                wpSettings = kept
                                io.github.janhurn9400.liquidy.glass.WallpaperStore.save(this@MainActivity, kept)
                            }
                        },
                        onCancel = { homeModel.wallpaperEditorOpen = false },
                    )
                }
                // Welcome on first start (above everything)
                // The preview icon (Customise sheet / welcome) needs the style layers before the first style choice
                val styleSample = homeModel.display.dock.firstOrNull()?.let { homeModel.apps[it] } ?: homeModel.apps.values.firstOrNull()
                LaunchedEffect(styleSample?.key) { styleSample?.let { repository.ensureLayers(listOf(it)) } }
                var showWelcome by remember { mutableStateOf(!io.github.janhurn9400.liquidy.ui.welcome.WelcomeStore.done(this@MainActivity)) }
                // Finish: setup fades out and zooms in slightly (no hard cut to the home screen)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showWelcome,
                    enter = androidx.compose.animation.EnterTransition.None,
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(WELCOME_EXIT_MS)) +
                        androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(WELCOME_EXIT_MS), targetScale = 1.08f),
                ) { io.github.janhurn9400.liquidy.ui.welcome.WelcomeFlow(
                    isDefaultHome = {
                        val h = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        packageManager.resolveActivity(h, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
                    },
                    hasRemoteAnimation = hasRemoteAnimation,
                    sample = styleSample,
                    wallpaper = homeWallpaper,
                    onFinish = { io.github.janhurn9400.liquidy.ui.welcome.WelcomeStore.markDone(this@MainActivity); showWelcome = false },
                ) }
                if (DebugState.visible) DebugPanel()
            }
            } // end LocalAndroidWidgetHost
        }
    }

    private fun redirectToHomeTask(): Boolean {
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) return false
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (defaultHome != packageName) return false
        intent?.extras?.let { home.putExtras(it) } // e.g. --ez debug true
        startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        return true
    }

    /** Identifier of the current Android wallpaper (changes when it's set anywhere). */
    private var systemWallpaperId by androidx.compose.runtime.mutableIntStateOf(-1)
    private val colorsListener = android.app.WallpaperManager.OnColorsChangedListener { _, _ -> refreshSystemWallpaperId() }

    /** Read access to the Android wallpaper (check again after returning from settings). */
    private var canReadSystemWallpaper by mutableStateOf(false)

    private fun refreshSystemWallpaperId() {
        canReadSystemWallpaper = io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead()
        systemWallpaperId = android.app.WallpaperManager.getInstance(this).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)
    }

    /** Show the system wallpaper still and screen-filling (no parallax when swiping pages), like our own image. */
    private var wallpaperDimsSet = false

    private fun applyWallpaperLayout() {
        val wm = android.app.WallpaperManager.getInstance(this)
        // Size hint only once and in the background: synchronous binder call, can make the wallpaper service re-render
        // and blocked the main thread on focus change ("Home isn't responding")
        if (!wallpaperDimsSet) {
            wallpaperDimsSet = true
            val m = resources.displayMetrics
            Thread { runCatching { wm.suggestDesiredDimensions(m.widthPixels, m.heightPixels) } }.start()
        }
        runCatching {
            wm.setWallpaperOffsetSteps(0f, 0f)
            window.decorView.windowToken?.let { wm.setWallpaperOffsets(it, 0.5f, 0.5f) }
        }
    }

    /** Corner radius of the display (target of the open animation). */
    private fun screenCornerPx(): Float =
        display?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f // unknown → assume square

    /** Animation Module active (system app with remote animation permission)? */
    val hasRemoteAnimation: Boolean
        get() = checkSelfPermission("android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        homeOnTop = true
        resumedAt = android.os.SystemClock.uptimeMillis()
        log("resume armed=$unlockArmed locked=${keyguard.isKeyguardLocked}")
        startUnlockPoll()
        io.github.janhurn9400.liquidy.data.AppLaunchTransition.apply {
            enabled = hasRemoteAnimation
            cardCornerPx = DesignTokens.appLaunchCorner.value * io.github.janhurn9400.liquidy.design.UiScale.density(this@MainActivity)
            displayCornerPx = screenCornerPx()
        }
        androidWidgets?.applyLaunchAnimation()
        refreshSystemWallpaperId()
        model?.refreshSuggestions()
    }

    /** Icon as a squircle approximation (icon corner radius) at [w]×[h] px, as a software bitmap for the open animation. */
    private fun roundedIconBitmap(icon: androidx.compose.ui.graphics.ImageBitmap, w: Int, h: Int): android.graphics.Bitmap {
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val r = DesignTokens.iconCorner.value / DesignTokens.iconSize.value * w
        val rect = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.clipPath(android.graphics.Path().apply { addRoundRect(rect, r, r, android.graphics.Path.Direction.CW) })
        c.drawBitmap(icon.asAndroidBitmap(), null, rect, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private var statusHidden = false
    private var androidWidgets: io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetHost? = null

    override fun onStart() { super.onStart(); runCatching { androidWidgets?.host?.startListening() } }
    override fun onStop() {
        super.onStop()
        runCatching { androidWidgets?.host?.stopListening() }
        model?.onHomeHidden()
    }

    @Deprecated("AppWidget binding/configuration runs via startActivityForResult (AppWidgetHost API)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        androidWidgets?.onActivityResult(this, requestCode, resultCode)
    }

    /**
     * Hide the status bar in edit mode/folder/editor. Behaviour "transient": if it's shown by swiping from the top,
     * it disappears again by itself (the default would be: it stays and collides with the edit buttons).
     */
    private fun applyStatusBar(hide: Boolean) {
        statusHidden = hide
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) c.hide(WindowInsetsCompat.Type.statusBars()) else c.show(WindowInsetsCompat.Type.statusBars())
    }

    /** Apply again after system dialogs (e.g. photo permission) or returning from an app. */
    /** System close animation (GestureNavContract) still playing — from the home intent until our window has focus again. */
    private var returnAnimating = false
    private var pendingLaunch: (() -> Unit)? = null
    private fun runPendingLaunch() { pendingLaunch?.let { pendingLaunch = null; returnAnimating = false; it() } }

    /**
     * Unlock animation (M-UNLOCK). The home screen waits far away once the screen goes off while it is the top activity
     * (also if the screen goes off again from the lock screen). It flies in as soon as our window is revealed: resumed,
     * keyguard gone, display on — checked every frame from onResume, because USER_PRESENT only arrives ≈ 0.4 s after
     * the lock screen has faded (measured on LineageOS 23.2) and fingerprint "wake and unlock" can resume us while the
     * display is still off. Another app revealed instead → reset without animating.
     */
    private var unlockArmed = false
    /** The home screen is the top activity (stays true while only the screen/keyguard paused us). */
    private var homeOnTop = true
    private var resumedAt = 0L
    private val unlockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val keyguard by lazy { getSystemService(android.app.KeyguardManager::class.java) }
    private val power by lazy { getSystemService(android.os.PowerManager::class.java) }
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> if (homeOnTop && !unlockArmed) { log("screen off → armed"); unlockArmed = true; model?.prepareUnlock() }
                Intent.ACTION_USER_PRESENT -> {
                    log("user present resumed=${isResumed()} armed=$unlockArmed")
                    // unlocked, but not to us (an app was on top) → reset, so returning home later doesn't play it
                    if (unlockArmed && !isResumed()) { unlockArmed = false; model?.cancelUnlock() } else startUnlockPoll()
                }
            }
        }
    }
    private fun isResumed() = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    private var unlockPollStart = 0L
    private val unlockPoll = object : Runnable {
        override fun run() {
            playUnlockIfReady()
            if (unlockArmed && android.os.SystemClock.uptimeMillis() - unlockPollStart < UNLOCK_POLL_MAX_MS) unlockHandler.postDelayed(this, UNLOCK_POLL_MS)
        }
    }
    private fun startUnlockPoll() {
        if (!unlockArmed) return
        unlockHandler.removeCallbacks(unlockPoll); unlockPollStart = android.os.SystemClock.uptimeMillis(); unlockHandler.post(unlockPoll)
    }
    private fun playUnlockIfReady() {
        if (!unlockArmed || !isResumed() || keyguard.isKeyguardLocked || !power.isInteractive) return
        if (display?.state != android.view.Display.STATE_ON) return
        if (android.os.SystemClock.uptimeMillis() - resumedAt < io.github.janhurn9400.liquidy.motion.Motion.unlockRevealDelay.value * 1000) return
        log("play"); unlockArmed = false; unlockHandler.removeCallbacks(unlockPoll); model?.playUnlock()
    }
    private fun log(msg: String) = android.util.Log.d("Unlock", msg)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { log("focus"); startUnlockPoll() }
        // Focus back = the system's return animation is done (it now resets the wallpaper zoom) → our transition
        if (hasFocus) { returnAnimating = false; runPendingLaunch(); applyStatusBar(statusHidden); applyWallpaperLayout(); model?.onHomeVisible() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button/gesture while the launcher is in front → go to the first page (like iOS)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Already on the home screen (focused, not brought to the front from outside) → home button: first page.
            // Back from an app → stay on the page, the app flies into its icon (GestureNavContract), home screen grows in.
            val alreadyOnHome = hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
            val contract = io.github.janhurn9400.liquidy.data.GestureNavContract.fromIntent(intent)
            if (alreadyOnHome && contract == null) homePressed.tryEmit(Unit)
            else { if (contract != null) returnAnimating = true; model?.onReturnFromApp(contract) }
        }
        handleDebugIntent(intent)
    }

    /** adb shell am start -n io.github.janhurn9400.liquidy/.MainActivity --ez debug true */
    private fun handleDebugIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("debug", false) == true) DebugState.visible = true
        // adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME --ez welcome true
        // Test (debug build only): adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME --es testwp /sdcard/x.png [--es crop l,t,r,b]
        if (intent?.getBooleanExtra("teststack", false) == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            intent.removeExtra("teststack"); model?.debugMakeStack()
        }
        // Test (debug build only): --es testwidget ios:reminders,4,2  → put a widget on page 1
        intent?.getStringExtra("testwidget")?.takeIf { (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 }?.let { w ->
            intent.removeExtra("testwidget"); val p = w.split(',')
            if (p[0].startsWith("android:")) { // --es testwidget android:pkg/cls,4,2  (before: cmd appwidget grantbind --package …)
                val cn = android.content.ComponentName.unflattenFromString(p[0].removePrefix("android:"))
                androidWidgets?.providers()?.firstOrNull { it.provider == cn }?.let { info ->
                    androidWidgets?.add(this, info, p[1].toInt() to p[2].toInt()) { spec -> if (spec != null) model?.addWidget(spec, 0) }
                }
            } else model?.addWidget(io.github.janhurn9400.liquidy.data.WidgetSpec(p[0], p[1].toInt(), p[2].toInt()), 0)
        }
        if (intent?.getBooleanExtra("testdemo", false) == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            intent.removeExtra("testdemo"); model?.debugDemoLayout()
        }
        val testWp = intent?.getStringExtra("testwp")
        if (testWp != null && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val crop = intent.getStringExtra("crop")?.split(',')?.map { it.trim().toInt() }?.let { android.graphics.Rect(it[0], it[1], it[2], it[3]) }
            intent.removeExtra("testwp"); intent.removeExtra("crop")
            io.github.janhurn9400.liquidy.glass.WallpaperStore.testSuppressSync = testWp != "clear"
            Thread {
                runCatching {
                    if (testWp == "clear") { android.app.WallpaperManager.getInstance(this).clear(android.app.WallpaperManager.FLAG_SYSTEM); return@runCatching }
                    val bmp = android.graphics.BitmapFactory.decodeFile(testWp)
                    val id = android.app.WallpaperManager.getInstance(this).setBitmap(bmp, crop, true, android.app.WallpaperManager.FLAG_SYSTEM)
                    android.util.Log.i("IosHome", "Test wallpaper set id=$id ${bmp.width}x${bmp.height} crop=$crop")
                }.onFailure { android.util.Log.w("IosHome", "Test wallpaper: $it") }
            }.start()
        }
        if (intent?.getBooleanExtra("welcome", false) == true) {
            getSharedPreferences("welcome", MODE_PRIVATE).edit().putBoolean("done", false).commit()
            intent.removeExtra("welcome"); recreate()
        }
    }

    /** Emulator test keys — only in debug builds and only with Ctrl, otherwise they trigger while typing in search
     *  (a "W" reset the welcome). Ctrl+D debug panel, Ctrl+W welcome, Ctrl+L test wallpaper. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0 || !event.isCtrlPressed) return super.dispatchKeyEvent(event)
        // Emulator: key W shows the welcome again (test)
        if (event.keyCode == KeyEvent.KEYCODE_W && event.action == KeyEvent.ACTION_UP) {
            getSharedPreferences("welcome", MODE_PRIVATE).edit().putBoolean("done", false).commit(); recreate(); return true
        }
        // Emulator: key L sets a light test wallpaper from /sdcard/Pictures/lightwp.png (comparison with Duo recordings)
        if (event.keyCode == KeyEvent.KEYCODE_L && event.action == KeyEvent.ACTION_UP) {
            Thread {
                runCatching {
                    val bmp = android.graphics.BitmapFactory.decodeFile("/sdcard/Pictures/lightwp.png")
                    android.app.WallpaperManager.getInstance(this).setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_SYSTEM)
                }.onFailure { android.util.Log.w("IosHome", "Test wallpaper: $it") }
            }.start(); return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_D && event.action == KeyEvent.ACTION_UP) {
            DebugState.toggle(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        // paused by another app (not by the screen going off or the keyguard)
        if (power.isInteractive && !keyguard.isKeyguardLocked) homeOnTop = false
        if (::repository.isInitialized) Tunables.save(this) // not after forwarding (nothing initialised)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        unlockHandler.removeCallbacksAndMessages(null)
        if (::repository.isInitialized) {
            repository.stop()
            android.app.WallpaperManager.getInstance(this).removeOnColorsChangedListener(colorsListener)
        }
        super.onDestroy()
    }
}

private const val WELCOME_EXIT_MS = 450

/** Longest wait for the system close animation before an app launch goes ahead anyway (ms). */
private const val RETURN_WAIT_MS = 700L

/** How long after resume/focus we keep checking whether the keyguard is gone and the display is on. */
private const val UNLOCK_POLL_MAX_MS = 3000L
private const val UNLOCK_POLL_MS = 8L
