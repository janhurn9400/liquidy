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

/** Statusleiste ist beim Öffnen eines Ordners bis zu diesem Fortschritt ausgeblendet [GESCHÄTZT]. */
private const val FOLDER_STATUS_FADE = 0.5f

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private var model: HomeModel? = null
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Heller Wallpaper → dunkle Statusleisten-Symbole
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Als normale App gestartet (App-Symbol, adb am start -n), obwohl wir Standard-Launcher sind: in den EINEN
        // Home-Task weiterleiten — sonst läuft eine zweite Instanz mit eigenem Zustand und eine leere Karte steht
        // in den letzten Apps.
        if (redirectToHomeTask()) return
        Tunables.init(this)

        // Icons in 1,5-facher Größe vorrendern, damit größere T-ICON-Werte im Debug-Panel scharf bleiben
        val iconPx = (DesignTokens.iconSize.value * resources.displayMetrics.density * 1.5f).roundToInt()
        repository = AppRepository(this, iconPx).also { it.start() }
        io.github.janhurn9400.liquidy.data.Haptics.load(this)
        io.github.janhurn9400.liquidy.data.FoldableStyle.load(this)
        io.github.janhurn9400.liquidy.data.OwnStatusBar.load(this)
        io.github.janhurn9400.liquidy.glass.GlassClarity.load(this)
        io.github.janhurn9400.liquidy.data.UniversalSearch.WebSuggestions.load(this)
        androidWidgets = io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetHost(this) // vor onStart (startListening)
        android.app.WallpaperManager.getInstance(this).addOnColorsChangedListener(colorsListener, android.os.Handler(mainLooper))
        refreshSystemWallpaperId()

        // Launcher: Zurück beendet nur den Bearbeitungsmodus
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
            // Stil-Ebenen (Dark/Clear/Tinted) erst bei Bedarf berechnen
            LaunchedEffect(apps, io.github.janhurn9400.liquidy.design.IconAppearance.style) {
                if (io.github.janhurn9400.liquidy.design.IconAppearance.style != io.github.janhurn9400.liquidy.design.IconStyle.Default) apps?.let { repository.ensureLayers(it) }
            }
            LaunchedEffect(Unit) { snapshotFlow { DebugOptions.demoPages.value }.collect { homeModel.checkDemoPages() } }
            // Home-Taste: Bearbeitungsmodus beenden, sonst zur ersten Seite (wie iOS)
            LaunchedEffect(Unit) {
                homePressed.collect {
                    when {
                        homeModel.searchOpen || homeModel.searchProgress.value > 0f -> homeModel.closeSearch()
                        homeModel.openFolder != null -> homeModel.closeFolder()
                        homeModel.openCategory != null -> homeModel.closeCategory()
                        homeModel.libListActive -> homeModel.closeLibList()
                        homeModel.editing -> homeModel.exitEdit()
                        else -> pager.animateTo(0)
                    }
                }
            }
            // Bearbeitungsmodus [GEMESSEN] IMG_3124 und offener Ordner [GEMESSEN] IMG_3130: Statusleiste weg
            // Eigene Statusleiste (Einstellung): Android-Leiste im Launcher dauerhaft weg (Wischen von oben zeigt sie kurz)
            val ownBar = io.github.janhurn9400.liquidy.data.OwnStatusBar.enabled && !io.github.janhurn9400.liquidy.ui.welcome.WelcomeStore.active
            val hideStatus = ownBar || homeModel.editing || homeModel.wallpaperEditorOpen || homeModel.stackEditKey != null
            // Ordner: Statusleiste blendet an ihrem Platz mit dem Ordner aus/ein (statt nach oben zu fahren)
            val statusFader = remember { io.github.janhurn9400.liquidy.ui.home.StatusBarFader(window.decorView) }
            LaunchedEffect(homeModel.openFolder) {
                val f = homeModel.openFolder ?: return@LaunchedEffect
                if (hideStatus) return@LaunchedEffect // im Bearbeitungsmodus ist sie schon weg
                fun fade() = 1f - (f.progress.value / FOLDER_STATUS_FADE).coerceIn(0f, 1f)
                statusFader.follow(::fade, done = { f.progress.value >= FOLDER_STATUS_FADE || f.progress.targetValue == 0f }, showAtEnd = { f.progress.targetValue == 0f }) // früh wieder zu → sichtbar lassen
                // warten, bis der Ordner zugeht, dann mit ihm wieder einblenden
                snapshotFlow { f.progress.targetValue }.first { it == 0f }
                statusFader.follow(::fade, done = { f.progress.value <= 0.02f }, showAtEnd = { true })
            }
            LaunchedEffect(hideStatus) {
                applyStatusBar(hideStatus)
            }
            // Suche: dunkler Hintergrund → helle Statusleisten-Symbole [GEMESSEN] IMG_3131
            val welcomeActive = io.github.janhurn9400.liquidy.ui.welcome.WelcomeStore.active
            LaunchedEffect(homeModel.searchOpen, DesignTokens.wallpaperIsDark, welcomeActive, homeModel.settingsOpen) {
                // Einstellungen: heller Grund → dunkle Symbole
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                    welcomeActive || homeModel.settingsOpen || (!homeModel.searchOpen && !DesignTokens.wallpaperIsDark)
            }
            // Nach dem Bearbeiten können Seiten wegfallen → Pager in gültigen Bereich holen
            LaunchedEffect(homeModel.pages.size) {
                if (pager.currentPage > homeModel.pages.lastIndex) pager.animateTo(homeModel.pages.lastIndex.coerceAtLeast(0))
            }
            // Duo-Stil auf schmaleren Foldables: Oberfläche gleichmäßig auf Duo-Breite verkleinern (design/UiScale)
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            val win = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            androidx.compose.runtime.SideEffect { io.github.janhurn9400.liquidy.data.FoldableStyle.updateWindow(this@MainActivity, win.width, win.height) }
            androidx.compose.runtime.LaunchedEffect(win.width, win.height) {
                io.github.janhurn9400.liquidy.glass.WallpaperCropModel.rememberDisplay(this@MainActivity)
                // Auf-/Zugeklappt: Android zeigte danach teils noch den alten Zuschnitt, bis etwas neu zeichnete (z. B. Bearbeiten
                // starten). Nur die Lage melden (billiger Einweg-Aufruf, kein Neuberechnen des Bilds) → Android stellt sofort richtig
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
                // Einstellung, zu der [wallpaper] gehört — nach „Done“ wird das fertige Entwurfsbild übernommen, statt neu zu rendern
                var renderedFor by remember { mutableStateOf<io.github.janhurn9400.liquidy.glass.WallpaperSettings?>(null) }
                LaunchedEffect(wpSettings, w, h, colorful) {
                    // auch bei neuer Fenstergröße (Foldable auf-/zugeklappt) neu rendern — sonst passt das Bild fürs Glas nicht
                    if (renderedFor == wpSettings && !colorful && wallpaper.width == w && wallpaper.height == h) return@LaunchedEffect
                    wallpaper = if (colorful && wpSettings.kind == io.github.janhurn9400.liquidy.glass.WallpaperKind.Pair) Wallpaper.generateColorful(w, h)
                    else io.github.janhurn9400.liquidy.glass.WallpaperRenderer.render(this@MainActivity, wpSettings, w, h)
                    renderedFor = wpSettings
                }
                // Im Editor gewähltes Wallpaper auch als System-Wallpaper setzen (das zeigt Android hinter unserem Fenster)
                LaunchedEffect(renderedFor) {
                    val s = renderedFor ?: return@LaunchedEffect
                    if (!colorful) io.github.janhurn9400.liquidy.glass.WallpaperStore.syncSystemWallpaper(this@MainActivity, s, wallpaper)
                    refreshSystemWallpaperId()
                }
                // Welches Bild zeigt Android? Unseres → nichts zeichnen (Fenster durchsichtig). Ein anderes (in den Android-
                // Einstellungen gewählt) → mit Lesezugriff dessen Pixel für Glas/Unschärfe nutzen, sonst eigenes Bild zeichnen.
                val systemOurs = remember(systemWallpaperId, renderedFor) { io.github.janhurn9400.liquidy.glass.WallpaperStore.systemIsOurs(this@MainActivity) }
                var systemPixels by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                // Mit Lesezugriff ist das Android-Bild immer die einzige Quelle (auch für im Picker gewählte — die setzen wir ja als Android-Bild)
                LaunchedEffect(systemWallpaperId, canReadSystemWallpaper, w, h) {
                    systemPixels = if (colorful) null else io.github.janhurn9400.liquidy.glass.SystemWallpaper.read(this@MainActivity, w, h)
                }
                // Android zeichnet sein Wallpaper selbst hinter unserem durchsichtigen Fenster (so stimmt es auch während der
                // Wisch-nach-Hause-Geste). Fürs Glas/die Unschärfe brauchen wir dieselben Pixel: Androids Zuschnitt + Zoom wird
                // nachgerechnet (glass/WallpaperCropModel) und auf unsere Kopie angewandt.
                val shownOurs = remember(wallpaper, w, h) { io.github.janhurn9400.liquidy.glass.WallpaperStore.asShownBySystem(this@MainActivity, wallpaper) }
                val homeWallpaper = when {
                    colorful -> wallpaper
                    // „Pair“ ist bereits Androids gezeigter Ausschnitt → nicht noch einmal zuschneiden
                    systemOurs && wpSettings.kind != io.github.janhurn9400.liquidy.glass.WallpaperKind.Pair -> shownOurs
                    else -> systemPixels ?: wallpaper
                }
                // Selbst zeichnen nur, wenn wir Androids Bild weder kennen noch lesen dürfen (sonst passte Glas nicht dazu)
                val drawOwnWallpaper = colorful || (!systemOurs && systemPixels == null)
                val onLaunch: (io.github.janhurn9400.liquidy.data.AppEntry, androidx.compose.ui.geometry.Rect) -> Unit = { app, bounds ->
                        // ohne Icon-Rahmen (Suche, A–Z-Liste): kein bestimmtes Homescreen-Icon
                        if (bounds.isEmpty) homeModel.launchedIconKey = null
                        homeModel.libraryLaunch = null // Mediathek-Aufrufe setzen es danach neu
                        val r = android.graphics.Rect(
                            bounds.left.roundToInt(), bounds.top.roundToInt(),
                            bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                        )
                        // Öffnen: das Icon-Bild selbst wächst zum App-Fenster (Thumbnail-Scale-Up) — iOS-ähnlichste der für
                        // Drittanbieter-Launcher erlaubten System-Animationen (die Pixel-Remote-Animation braucht eine Systemberechtigung)
                        val thumb = if (r.width() > 0 && r.height() > 0) roundedIconBitmap(app.icon, r.width(), r.height()) else null
                        // Mit Magisk-Modul (System-App): eigene iOS-Animation, Fenster wächst aus dem Icon
                        val remote = io.github.janhurn9400.liquidy.data.AppLaunchTransition.optionsFor(
                            android.graphics.RectF(r), DesignTokens.iconCorner.value * io.github.janhurn9400.liquidy.design.UiScale.density(this@MainActivity),
                            // genau das angetippte Icon (bei mehreren Icons derselben App), sonst die App
                            homeModel.launchedIconKey?.takeIf { io.github.janhurn9400.liquidy.ui.home.appKeyOf(it) == app.key } ?: app.key)
                        val opts = (remote ?: if (thumb != null) ActivityOptions.makeThumbnailScaleUpAnimation(view, thumb, r.left, r.top)
                            else ActivityOptions.makeScaleUpAnimation(view, r.left, r.top, r.width(), r.height())).toBundle()
                        repository.launch(app, r, opts)
                        homeModel.noteLaunch(app.key)
                        io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins.remove(app.component.packageName) // jetzt übers Icon geöffnet
                        homeModel.onAppLaunching()
                }
                // Labels/Pill-Schrift an die Wallpaper-Helligkeit anpassen (Editor-Vorschau nutzt den Entwurf)
                // Helligkeit des TATSÄCHLICH angezeigten Bilds (Android-Wallpaper), nicht des eigenen Launcher-Bilds
                LaunchedEffect(homeWallpaper, homeModel.wallpaperEditorOpen) { // auch nach „Cancel“ (Entwurf hatte umgestellt)
                    DesignTokens.wallpaperIsDark = io.github.janhurn9400.liquidy.glass.WallpaperRenderer.luminance(homeWallpaper) < DesignTokens.WALLPAPER_DARK_LUMA
                }
                // Wallpaper-Editor: legt Rahmen + Bedienung um DENSELBEN Homescreen (kein Neuaufbau → kein Aufblitzen)
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
                            // Bild sofort übernehmen (kein Aufblitzen des alten), Foto dauerhaft in den App-Speicher kopieren
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
                // Willkommen beim ersten Start (über allem)
                // Vorschau-Icon (Customise-Blatt / Willkommen) braucht die Stil-Ebenen schon vor der ersten Stilwahl
                val styleSample = homeModel.display.dock.firstOrNull()?.let { homeModel.apps[it] } ?: homeModel.apps.values.firstOrNull()
                LaunchedEffect(styleSample?.key) { styleSample?.let { repository.ensureLayers(listOf(it)) } }
                var showWelcome by remember { mutableStateOf(!io.github.janhurn9400.liquidy.ui.welcome.WelcomeStore.done(this@MainActivity)) }
                // Abschluss: Setup blendet aus und zoomt leicht auf (kein harter Schnitt auf den Homescreen)
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
            } // Ende LocalAndroidWidgetHost
        }
    }

    private fun redirectToHomeTask(): Boolean {
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) return false
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (defaultHome != packageName) return false
        intent?.extras?.let { home.putExtras(it) } // z. B. --ez debug true
        startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        return true
    }

    /** Kennung des aktuellen Android-Wallpapers (ändert sich, wenn es irgendwo neu gesetzt wird). */
    private var systemWallpaperId by androidx.compose.runtime.mutableIntStateOf(-1)
    private val colorsListener = android.app.WallpaperManager.OnColorsChangedListener { _, _ -> refreshSystemWallpaperId() }

    /** Lesezugriff aufs Android-Wallpaper (nach Rückkehr aus den Einstellungen neu prüfen). */
    private var canReadSystemWallpaper by mutableStateOf(false)

    private fun refreshSystemWallpaperId() {
        canReadSystemWallpaper = io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead()
        systemWallpaperId = android.app.WallpaperManager.getInstance(this).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)
    }

    /** System-Wallpaper unbewegt und bildschirmfüllend zeigen (kein Parallax beim Seitenwischen), wie unser eigenes Bild. */
    private var wallpaperDimsSet = false

    private fun applyWallpaperLayout() {
        val wm = android.app.WallpaperManager.getInstance(this)
        // Größen-Hinweis nur einmal und im Hintergrund: synchroner Binder-Aufruf, kann den Wallpaper-Dienst neu rendern
        // lassen und hat beim Fokuswechsel den Haupt-Thread blockiert („Home reagiert nicht“)
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

    /** Eckenradius des Displays (Ziel der Öffnen-Animation). */
    private fun screenCornerPx(): Float =
        display?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f // unbekannt → eckig annehmen

    /** Magisk-Modul aktiv (System-App mit Remote-Animations-Recht)? */
    val hasRemoteAnimation: Boolean
        get() = checkSelfPermission("android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        io.github.janhurn9400.liquidy.data.AppLaunchTransition.apply {
            enabled = hasRemoteAnimation
            cardCornerPx = DesignTokens.appLaunchCorner.value * io.github.janhurn9400.liquidy.design.UiScale.density(this@MainActivity)
            displayCornerPx = screenCornerPx()
        }
        androidWidgets?.applyLaunchAnimation()
        refreshSystemWallpaperId()
        model?.refreshSuggestions()
    }

    /** Icon in Squircle-Annäherung (Icon-Eckenradius) auf [w]×[h] px, als Software-Bitmap für die Öffnen-Animation. */
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

    @Deprecated("AppWidget-Binden/-Konfigurieren laufen über startActivityForResult (AppWidgetHost-API)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        androidWidgets?.onActivityResult(this, requestCode, resultCode)
    }

    /**
     * Statusleiste im Bearbeitungsmodus/Ordner/Editor ausblenden. Verhalten „transient“: wird sie durch Wischen von oben
     * eingeblendet, verschwindet sie von selbst wieder (Standard wäre: bleibt stehen und kollidiert mit den Edit-Buttons).
     */
    private fun applyStatusBar(hide: Boolean) {
        statusHidden = hide
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) c.hide(WindowInsetsCompat.Type.statusBars()) else c.show(WindowInsetsCompat.Type.statusBars())
    }

    /** Nach Systemdialogen (z. B. Foto-Berechtigung) oder Rückkehr aus einer App erneut anwenden. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Fokus zurück = Rückkehr-Animation des Systems ist fertig (es setzt jetzt den Wallpaper-Zoom zurück) → unser Übergang
        if (hasFocus) { applyStatusBar(statusHidden); applyWallpaperLayout(); model?.onHomeVisible() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home-Taste/-Geste während der Launcher vorne ist → zur ersten Seite (wie iOS)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Schon auf dem Homescreen (Fokus, nicht von außen nach vorne geholt) → Home-Taste: erste Seite.
            // Aus einer App zurück → auf der Seite bleiben, App fliegt in ihr Icon (GestureNavContract), Homescreen wächst ein.
            val alreadyOnHome = hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
            val contract = io.github.janhurn9400.liquidy.data.GestureNavContract.fromIntent(intent)
            if (alreadyOnHome && contract == null) homePressed.tryEmit(Unit)
            else model?.onReturnFromApp(contract)
        }
        handleDebugIntent(intent)
    }

    /** adb shell am start -n io.github.janhurn9400.liquidy/.MainActivity --ez debug true */
    private fun handleDebugIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("debug", false) == true) DebugState.visible = true
        // adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME --ez welcome true
        // Test (nur Debug-Build): adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME --es testwp /sdcard/x.png [--es crop l,t,r,b]
        if (intent?.getBooleanExtra("teststack", false) == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            intent.removeExtra("teststack"); model?.debugMakeStack()
        }
        // Test (nur Debug-Build): --es testwidget ios:reminders,4,2  → Widget auf Seite 1 legen
        intent?.getStringExtra("testwidget")?.takeIf { (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 }?.let { w ->
            intent.removeExtra("testwidget"); val p = w.split(',')
            if (p[0].startsWith("android:")) { // --es testwidget android:pkg/cls,4,2  (vorher: cmd appwidget grantbind --package …)
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
                    android.util.Log.i("IosHome", "Test-Wallpaper gesetzt id=$id ${bmp.width}x${bmp.height} crop=$crop")
                }.onFailure { android.util.Log.w("IosHome", "Test-Wallpaper: $it") }
            }.start()
        }
        if (intent?.getBooleanExtra("welcome", false) == true) {
            getSharedPreferences("welcome", MODE_PRIVATE).edit().putBoolean("done", false).commit()
            intent.removeExtra("welcome"); recreate()
        }
    }

    /** Emulator-Testtasten — nur in Debug-Builds und nur mit Strg, sonst lösen sie beim Tippen in der Suche aus
     *  (ein „W“ setzte das Willkommen zurück). Strg+D Debug-Panel, Strg+W Willkommen, Strg+L Test-Wallpaper. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0 || !event.isCtrlPressed) return super.dispatchKeyEvent(event)
        // Emulator: Taste W zeigt das Willkommen erneut (Test)
        if (event.keyCode == KeyEvent.KEYCODE_W && event.action == KeyEvent.ACTION_UP) {
            getSharedPreferences("welcome", MODE_PRIVATE).edit().putBoolean("done", false).commit(); recreate(); return true
        }
        // Emulator: Taste L setzt ein helles Test-Wallpaper aus /sdcard/Pictures/lightwp.png (Vergleich mit Duo-Aufnahmen)
        if (event.keyCode == KeyEvent.KEYCODE_L && event.action == KeyEvent.ACTION_UP) {
            Thread {
                runCatching {
                    val bmp = android.graphics.BitmapFactory.decodeFile("/sdcard/Pictures/lightwp.png")
                    android.app.WallpaperManager.getInstance(this).setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_SYSTEM)
                }.onFailure { android.util.Log.w("IosHome", "Test-Wallpaper: $it") }
            }.start(); return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_D && event.action == KeyEvent.ACTION_UP) {
            DebugState.toggle(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (::repository.isInitialized) Tunables.save(this) // nicht nach der Weiterleitung (nichts initialisiert)
    }

    override fun onDestroy() {
        if (::repository.isInitialized) {
            repository.stop()
            android.app.WallpaperManager.getInstance(this).removeOnColorsChangedListener(colorsListener)
        }
        super.onDestroy()
    }
}

private const val WELCOME_EXIT_MS = 450
