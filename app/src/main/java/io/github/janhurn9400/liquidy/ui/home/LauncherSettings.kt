package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.ui.text.withLink
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.FoldableStyle
import io.github.janhurn9400.liquidy.data.Haptics
import io.github.janhurn9400.liquidy.data.OwnStatusBar
import io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.ui.welcome.GlassBackButton
import io.github.janhurn9400.liquidy.ui.welcome.GlassToggle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Bereiche der Einstellungen — jede Einstellung genau dort, wo man sie sucht. */
private enum class Category { General, Appearance, HomeScreen, Quirks, Permissions, Advanced, About }

/**
 * Launcher-Einstellungen im Stil der iOS-Einstellungs-App [Aufnahme 09:39, Duo]:
 * breit (aufgeklappt/Tablet): links Seitenleiste (≈ ⅓, max 320 pt) mit Kopfkarte + Bereichen (Icon 29 pt, Zeile 52 pt,
 * Auswahl als graue Kapsel), rechts der gewählte Bereich mit kleinem Titel oben und runden Gruppen (Radius 24).
 * Schmal (zugeklappt/Handy): Liste mit großem Titel, Antippen schiebt den Bereich herein (Zurück-Knopf).
 * Duo: rechte Spalte bleibt frei für Statussymbole + runden Zurück/Schließen-Knopf (wie im Original).
 */
@Composable
fun LauncherSettingsSheet(model: HomeModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.wpeSheet.spec()) }
    LaunchedEffect(model.settingsClosing) { if (model.settingsClosing) { appear.animateTo(0f, Motion.wpeSheet.spec()); onClose() } }
    // Berechtigungen/Standard-App können sich in Android-Einstellungen ändern → Status regelmäßig neu lesen
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val backdrop = rememberLayerBackdrop()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val screenW = constraints.maxWidth / density; val screenH = constraints.maxHeight / density
        val duo = FoldableStyle.duoStyle
        val wide = screenW >= WIDE_MIN
        // Duo: rechte Spalte (Dock-Spalte) frei lassen
        val rightRoom = if (duo) DUO_RIGHT_ROOM else 0f
        var selected by remember { mutableStateOf(if (wide) Category.General else null) }
        androidx.activity.compose.BackHandler(enabled = !wide && selected != null) { selected = null }
        // Zurück auf der obersten Ebene schließt die Einstellungen (wie eine App verlassen)
        androidx.activity.compose.BackHandler(enabled = wide || selected == null) { model.closeSettings() }

        Box(
            Modifier.fillMaxSize()
                .offset { IntOffset(0, ((1f - appear.value) * screenH * density).roundToInt()) }
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(DETAIL_BG)) {
                if (wide) {
                    val sideW = minOf(SIDEBAR_MAX, screenW * SIDEBAR_SHARE)
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(Dp(sideW)).fillMaxHeight().background(SIDEBAR_BG)) {
                            Sidebar(selected, tick, onSelect = { selected = it })
                        }
                        Box(Modifier.fillMaxHeight().width(Dp(screenW - sideW - rightRoom))) {
                            AnimatedContent(selected, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "cat") { c ->
                                Detail(c ?: Category.General, model, tick, inlineTitle = true)
                            }
                        }
                    }
                } else {
                    AnimatedContent(selected, transitionSpec = {
                        if (targetState != null) slideInHorizontally(spring(1f, 380f)) { it } togetherWith slideOutHorizontally(spring(1f, 380f)) { -it / 3 }
                        else slideInHorizontally(spring(1f, 380f)) { -it / 3 } togetherWith slideOutHorizontally(spring(1f, 380f)) { it }
                    }, label = "nav", modifier = Modifier.width(Dp(screenW - rightRoom))) { c ->
                        if (c == null) Box(Modifier.fillMaxSize().background(DETAIL_BG)) { CategoryList(tick, largeTitle = true) { selected = it } }
                        else Box(Modifier.fillMaxSize().background(DETAIL_BG)) { Detail(c, model, tick, inlineTitle = true) }
                    }
                }
            }
            // Duo: Statussymbole + runder Glas-Knopf in der rechten Spalte; sonst Knopf oben rechts
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                if (duo && OwnStatusBar.enabled) {
                    val info by io.github.janhurn9400.liquidy.data.rememberStatusInfo()
                    DuoStatusSymbols(info, (screenW - DUO_COLUMN_FROM_RIGHT) * density, density, Color.Black)
                }
                val back = !wide && selected != null
                val btnModifier = if (duo) Modifier.offset { IntOffset(((screenW - DUO_COLUMN_FROM_RIGHT - 22f) * density).roundToInt(), ((DUO_BUTTON_Y - 22f) * density).roundToInt()) }
                    else Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 16.dp)
                // Kein ×: die Einstellungen verlässt man wie eine App mit der Home-Geste (oder Zurück) — Nutzer 10-15
                if (back) Box(btnModifier) { GlassBackButton(onClick = { selected = null }) }
            }
        }
    }
}

// ── Seitenleiste / Liste ─────────────────────────────────────────────────────────────

@Composable
private fun Sidebar(selected: Category?, tick: Int, onSelect: (Category) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(SIDEBAR_TOP.dp))
        HeaderCard(compact = true)
        Spacer(Modifier.height(18.dp))
        Category.entries.forEach { c ->
            if (!visible(c)) return@forEach
            val sel = c == selected
            Row(
                // schmale Seitenleiste (andere Foldables): langer Titel bricht wie bei iOS in zwei Zeilen um, Zeile wird höher
                Modifier.fillMaxWidth().heightIn(min = ROW_H.dp).clip(RoundedCornerShape((ROW_H / 2).dp)).background(if (sel) SELECTED else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(c) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(c)
                Spacer(Modifier.width(12.dp))
                BasicText(categoryTitle(c), style = LABEL.copy(fontSize = 16.sp, lineHeight = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun CategoryList(tick: Int, largeTitle: Boolean, onSelect: (Category) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(30.dp))
        if (largeTitle) BasicText(stringResource(R.string.launcher_settings), Modifier.padding(bottom = 18.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.Black))
        HeaderCard(compact = false)
        Spacer(Modifier.height(SECTION_GAP.dp))
        val cats = Category.entries.filter { visible(it) }
        Group {
            cats.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth().height(ROW_H.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(c) }
                        .padding(start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(c); Spacer(Modifier.width(14.dp))
                    BasicText(categoryTitle(c), Modifier.weight(1f), style = LABEL)
                    Chevron()
                }
                if (i < cats.lastIndex) Divider(start = 59f)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Kopfkarte wie „Apple Account“: Launcher-Symbol, Name, Version. */
@Composable
private fun HeaderCard(compact: Boolean) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(if (compact) Color.Transparent else Color.White)
            .padding(horizontal = if (compact) 4.dp else 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidyAppIcon(58f)
        Spacer(Modifier.width(14.dp))
        Column {
            BasicText(stringResource(R.string.settings_app_name), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Color.Black))
            BasicText(stringResource(R.string.settings_header_sub, version), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
        }
    }
}

// ── Bereiche ────────────────────────────────────────────────────────────────────────

@Composable
private fun Detail(c: Category, model: HomeModel, tick: Int, inlineTitle: Boolean) {
    val context = LocalContext.current
    var subPage by remember { mutableStateOf(SubPage.None) }
    androidx.activity.compose.BackHandler(enabled = subPage != SubPage.None) { subPage = SubPage.None }
    AnimatedContent(subPage, transitionSpec = {
        if (targetState != SubPage.None) slideInHorizontally(spring(1f, 380f)) { it } togetherWith slideOutHorizontally(spring(1f, 380f)) { -it / 3 }
        else slideInHorizontally(spring(1f, 380f)) { -it / 3 } togetherWith slideOutHorizontally(spring(1f, 380f)) { it }
    }, label = "sub") { sub ->
        Box(Modifier.fillMaxSize().background(DETAIL_BG)) {
            when (sub) {
                SubPage.IconPacks -> IconPackPage(categoryTitle(c)) { subPage = SubPage.None }
                SubPage.Glass -> GlassPage(categoryTitle(c)) { subPage = SubPage.None }
                SubPage.Module -> ModulePage(categoryTitle(c), tick) { subPage = SubPage.None }
                SubPage.Report -> ReportPage(categoryTitle(c)) { subPage = SubPage.None }
                SubPage.None -> DetailBody(c, model, tick, inlineTitle) { subPage = it }
            }
        }
    }
}

@Composable
private fun DetailBody(c: Category, model: HomeModel, tick: Int, inlineTitle: Boolean, openSub: (SubPage) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        // „Allgemein“ mit großer Kopfkarte wie iOS (Symbol, Titel, Beschreibung), sonst kleiner Titel oben
        if (c == Category.General) {
            Spacer(Modifier.height(HERO_TOP.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(Color.White).padding(20.dp)) {
                Box(Modifier.size(60.dp)) { CategoryIcon(c, 60f) }
                Spacer(Modifier.height(14.dp))
                BasicText(categoryTitle(c), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.Black))
                Spacer(Modifier.height(6.dp))
                BasicText(stringResource(R.string.settings_general_desc), style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = GREY))
            }
            Spacer(Modifier.height(SECTION_GAP.dp))
        } else if (inlineTitle) BasicText(categoryTitle(c), Modifier.padding(start = 0.dp, bottom = 18.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.Black))
        when (c) {
            Category.General -> {
                Section(stringResource(R.string.settings_sec_feedback), footer = stringResource(R.string.settings_haptics_footer)) {
                    ToggleRow(stringResource(R.string.welcome_haptics), Haptics.enabled) { Haptics.set(context, it) }
                }
                val isDefault = remember(tick) { isDefaultHome(context) }
                Section(stringResource(R.string.settings_sec_default), footer = stringResource(R.string.settings_default_footer)) {
                    LinkRow(stringResource(R.string.settings_default_home), stringResource(if (isDefault) R.string.settings_yes else R.string.settings_no)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
                    }
                }
            }
            Category.Appearance -> {
                Section(stringResource(R.string.settings_sec_icons_wallpaper)) {
                    LinkRow(stringResource(R.string.settings_icons)) { model.closeSettings(); model.openCustomise() }
                    Divider()
                    LinkRow(stringResource(R.string.edit_wallpaper)) { model.closeSettings(); model.wallpaperEditorOpen = true }
                    Divider()
                    // Icon-Pakete: eigene Unterseite mit Vorschau je Paket
                    val chosen = io.github.janhurn9400.liquidy.data.IconPacks.selected
                    val label = remember(chosen) { chosen?.let { io.github.janhurn9400.liquidy.data.IconPacks.available(context).firstOrNull { p -> p.packageName == it }?.label } }
                    LinkRow(stringResource(R.string.settings_sec_iconpack), label ?: stringResource(R.string.settings_iconpack_system)) { openSub(SubPage.IconPacks) }
                }
                // wie iOS: Einstellungen › Darstellung › Liquid Glass (eigene Seite mit Vorschau + Regler klar ↔ getönt)
                Section(null) {
                    val t = io.github.janhurn9400.liquidy.glass.GlassClarity.amount
                    LinkRow(stringResource(R.string.settings_sec_glass), stringResource(when {
                        t < 0.25f -> R.string.settings_glass_clear
                        t > 0.75f -> R.string.settings_glass_tinted
                        else -> R.string.settings_glass_between
                    })) { openSub(SubPage.Glass) }
                }
                Section(stringResource(R.string.settings_sec_statusbar),
                    footer = stringResource(if (FoldableStyle.duoStyle) R.string.settings_statusbar_footer_duo else R.string.settings_statusbar_footer)) {
                    ToggleRow(stringResource(R.string.settings_statusbar), OwnStatusBar.enabled) { OwnStatusBar.set(context, it) }
                }
            }
            Category.HomeScreen -> {
                if (FoldableStyle.supportsDuo(context)) Section(stringResource(R.string.settings_sec_layout), footer = stringResource(R.string.settings_duo_footer)) {
                    ToggleRow(stringResource(R.string.settings_duo), FoldableStyle.duoStyle) { FoldableStyle.set(context, it) }
                }
                Section(stringResource(R.string.settings_sec_pages)) {
                    LinkRow(stringResource(R.string.edit_pages)) { model.closeSettings(); model.pagesOverviewRequested = true }
                }
                Section(stringResource(R.string.settings_sec_search), footer = stringResource(R.string.settings_web_suggest_footer)) {
                    ToggleRow(stringResource(R.string.settings_web_suggest), io.github.janhurn9400.liquidy.data.UniversalSearch.WebSuggestions.enabled) {
                        io.github.janhurn9400.liquidy.data.UniversalSearch.WebSuggestions.set(context, it)
                    }
                }
            }
            Category.Quirks -> {
                BasicText(stringResource(R.string.settings_quirks_intro), Modifier.padding(start = 20.dp, end = 20.dp, bottom = SECTION_GAP.dp),
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
                Section(stringResource(R.string.settings_sec_grid), footer = stringResource(R.string.settings_free_widget_rows_footer)) {
                    ToggleRow(stringResource(R.string.settings_free_widget_rows), io.github.janhurn9400.liquidy.data.Quirks.freeWidgetRows) {
                        io.github.janhurn9400.liquidy.data.Quirks.setFreeWidgetRows(context, it)
                        model.reflowPages()
                    }
                }
                // eigene Erweiterung: senkrechtes Duo-Dock mit mehr als 4 Apps; beim Ausschalten wandern überzählige auf den Homescreen
                if (FoldableStyle.duoStyle) Section(stringResource(R.string.settings_sec_dock), footer = stringResource(R.string.settings_more_dock_footer)) {
                    ToggleRow(stringResource(R.string.settings_more_dock), FoldableStyle.moreDockApps) {
                        FoldableStyle.setMoreDockApps(context, it)
                        if (!it) model.trimDock(io.github.janhurn9400.liquidy.design.DesignTokens.DOCK_SLOTS)
                    }
                }
            }
            Category.Permissions -> {
                fun granted(p: String) = context.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val cal = remember(tick) { granted(android.Manifest.permission.READ_CALENDAR) }
                // Fotos: auch „nur ausgewählte Fotos“ (Android 14) zählt als erlaubt
                val photos = remember(tick) { granted(android.Manifest.permission.READ_MEDIA_IMAGES) || granted(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) }
                val contacts = remember(tick) { granted(android.Manifest.permission.READ_CONTACTS) }
                val location = remember(tick) { granted(android.Manifest.permission.ACCESS_COARSE_LOCATION) }
                val badges = remember(tick) { io.github.janhurn9400.liquidy.data.NotificationBadges.hasAccess(context) }
                val files = remember(tick) { io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead() }
                val usage = remember(tick) { model.hasUsageAccess() }
                // Jede Zeile sagt, wofür die Erlaubnis gebraucht wird — niemand soll blind etwas freigeben
                Section(stringResource(R.string.settings_sec_access), footer = stringResource(R.string.settings_perm_footer)) {
                    PermissionRow(stringResource(R.string.welcome_perm_calendar), cal, stringResource(R.string.settings_perm_calendar_why)) { openAppSettings(context) }
                    Divider()
                    PermissionRow(stringResource(R.string.welcome_perm_photos), photos, stringResource(R.string.settings_perm_photos_why)) { openAppSettings(context) }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_perm_contacts), contacts, stringResource(R.string.settings_perm_contacts_why)) { openAppSettings(context) }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_perm_location), location, stringResource(R.string.settings_perm_location_why)) { openAppSettings(context) }
                    Divider()
                    PermissionRow(stringResource(R.string.welcome_perm_badges), badges, stringResource(R.string.settings_perm_badges_why)) {
                        io.github.janhurn9400.liquidy.data.NotificationBadges.requestAccess(context)
                    }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_usage_access), usage, stringResource(R.string.settings_perm_usage_why)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)))
                    }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_perm_files), files, stringResource(R.string.settings_perm_files_why)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.fromParts("package", context.packageName, null)))
                    }
                }
            }
            Category.Advanced -> {
                val module = remember(tick) { context.checkSelfPermission("android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED }
                // Animationsmodul: eigene Unterseite (Erklärung, Status, Anleitung) — hier nur Zeile mit Status
                Section(null) {
                    LinkRow(stringResource(R.string.settings_module), stringResource(if (module) R.string.settings_active else R.string.settings_module_missing)) {
                        openSub(SubPage.Module)
                    }
                }
                // Fehler melden: eigene Unterseite (Erklärung, Diagnose ansehen, Issue auf GitHub mit Diagnose vorausgefüllt)
                Section(null) {
                    LinkRow(stringResource(R.string.settings_report)) { openSub(SubPage.Report) }
                }
            }
            Category.About -> {
                val info = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
                Section(null) {
                    ValueRow(stringResource(R.string.settings_version), info?.versionName ?: "–")
                    Divider()
                    ValueRow(stringResource(R.string.settings_build), (info?.longVersionCode ?: 0L).toString())
                    Divider()
                    ValueRow("Android", android.os.Build.VERSION.RELEASE)
                    Divider()
                    ValueRow(stringResource(R.string.settings_device), android.os.Build.MODEL)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun allowed(on: Boolean) = stringResource(if (on) R.string.settings_allowed else R.string.settings_not_allowed)

private fun visible(c: Category) = true

@Composable
private fun categoryTitle(c: Category) = stringResource(when (c) {
    Category.General -> R.string.settings_section_general
    Category.Appearance -> R.string.settings_cat_appearance
    Category.HomeScreen -> R.string.settings_cat_home
    Category.Quirks -> R.string.settings_cat_quirks
    Category.Permissions -> R.string.settings_cat_permissions
    Category.Advanced -> R.string.settings_cat_advanced
    Category.About -> R.string.settings_section_about
})

/** Das App-Icon (res/mipmap ic_launcher) als Kachel: Hintergrund + Vordergrund wie das adaptive Icon (sichtbar 72 von 108). */
@Composable
private fun LiquidyAppIcon(sizePt: Float) {
    Box(Modifier.size(sizePt.dp).clip(io.github.janhurn9400.liquidy.design.ContinuousRoundedShape((sizePt * TILE_CORNER).dp)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_background), null,
            Modifier.requiredSize((sizePt * ADAPTIVE_SCALE).dp))
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
            Modifier.requiredSize((sizePt * ADAPTIVE_SCALE).dp))
    }
}

/**
 * Kategorie-Kachel wie in den iOS-27-Einstellungen [GEMESSEN Nutzer-Screenshot 10-15]: kontinuierliche Ecken (≈ 23 % der
 * Kante), leichter Verlauf nach unten (≈ 6 % dunkler) und ein feiner heller Lichtrand oben/unten; weiße Glyphe.
 * Zahnrad und Darstellung selbst gezeichnet im iOS-Stil, übrige Glyphen Material Symbols Rounded (Apache-2.0) —
 * keine SF Symbols. Farben aus dem Screenshot (Blau 2/140/252, Grau 155→141, Schwarz 9→0), Orange/Indigo [GESCHÄTZT].
 */
@Composable
private fun CategoryIcon(c: Category, sizePt: Float = ICON) {
    val bg = when (c) {
        Category.General, Category.About -> Color(0xFF99989E)
        Category.Appearance -> Color(0xFF0A0A0A)
        Category.HomeScreen, Category.Permissions -> Color(0xFF028CFC)
        Category.Quirks -> Color(0xFFFF9500)
        Category.Advanced -> Color(0xFF636366)
    }
    val bottom = Color(bg.red * TILE_SHADE, bg.green * TILE_SHADE, bg.blue * TILE_SHADE)
    val shape = io.github.janhurn9400.liquidy.design.ContinuousRoundedShape((sizePt * TILE_CORNER).dp)
    Box(
        Modifier.size(sizePt.dp).clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg, bottom)))
            .drawWithContent {
                drawContent()
                // Lichtrand: oben und unten heller, seitlich kaum (Liquid Glass)
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.White.copy(alpha = TILE_RIM), 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.White.copy(alpha = TILE_RIM * 0.6f)),
                    style = Stroke(TILE_RIM_W.dp.toPx() * 2))
            },
        contentAlignment = Alignment.Center,
    ) {
        when (c) {
            Category.General -> Canvas(Modifier.size((sizePt * GEAR_SIZE).dp)) { iosGear() }
            Category.Appearance -> Canvas(Modifier.size((sizePt * GEAR_SIZE).dp)) { contrastGlyph() }
            else -> androidx.compose.foundation.Image(
                androidx.compose.ui.graphics.vector.rememberVectorPainter(when (c) {
                    Category.HomeScreen -> SettingsGlyphs.GridView
                    Category.Quirks -> SettingsGlyphs.Tune
                    Category.Permissions -> SettingsGlyphs.PanTool
                    Category.Advanced -> SettingsGlyphs.Build
                    else -> SettingsGlyphs.Info
                }), contentDescription = null,
                modifier = Modifier.size((sizePt * TILE_GLYPH).dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
            )
        }
    }
}

/** Zahnrad wie die iOS-Einstellungen [Screenshot 10-15]: ≈ 26 feine Zähne, Innenring, drei Speichen, Nabe. */
private fun DrawScope.iosGear() {
    val s = size.minDimension; val c = center
    val teeth = GEAR_TEETH
    val ro = s * 0.5f; val rt = s * 0.43f // Zahnspitze / Zahnfuß
    val ring = androidx.compose.ui.graphics.Path().apply {
        for (k in 0 until teeth) {
            val a0 = (k.toFloat() / teeth) * 2 * Math.PI.toFloat()
            val step = 2 * Math.PI.toFloat() / teeth
            fun pt(r: Float, a: Float) = Offset(c.x + r * kotlin.math.cos(a), c.y + r * kotlin.math.sin(a))
            val p0 = pt(rt, a0); val p1 = pt(ro, a0 + step * 0.18f); val p2 = pt(ro, a0 + step * 0.48f); val p3 = pt(rt, a0 + step * 0.66f)
            if (k == 0) moveTo(p0.x, p0.y) else lineTo(p0.x, p0.y)
            lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y)
        }
        close()
        // Loch: Innenring bleibt als schmaler Kranz stehen
        addOval(androidx.compose.ui.geometry.Rect(c, s * 0.355f))
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    drawPath(ring, Color.White)
    drawCircle(Color.White, s * 0.30f, c, style = Stroke(s * 0.045f))
    // drei Speichen (rechts oben, links oben, unten) von der Nabe zum Innenring
    for (a in listOf(-30f, -150f, 90f)) {
        val r = Math.toRadians(a.toDouble()); val d = Offset(kotlin.math.cos(r).toFloat(), kotlin.math.sin(r).toFloat())
        drawLine(Color.White, c, c + d * (s * 0.30f), s * 0.05f, StrokeCap.Round)
    }
    drawCircle(Color.White, s * 0.06f, c)
}

/** Darstellung wie iOS [Screenshot 10-15]: Kreis links weiß gefüllt, rechts nur Rand; kleiner Innenkreis umgekehrt. */
private fun DrawScope.contrastGlyph() {
    val s = size.minDimension; val c = center; val r = s * 0.44f; val w = s * 0.085f
    drawCircle(Color.White, r - w / 2, c, style = Stroke(w))
    drawArc(Color.White, 90f, 180f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
    val ri = r * 0.38f
    drawArc(Color.Black, 90f, 180f, true, Offset(c.x - ri, c.y - ri), Size(2 * ri, 2 * ri))
    drawArc(Color.White, -90f, 180f, true, Offset(c.x - ri, c.y - ri), Size(2 * ri, 2 * ri))
}

// ── Bausteine ───────────────────────────────────────────────────────────────────────

@Composable
private fun Section(title: String?, footer: String? = null, content: @Composable () -> Unit) {
    if (title != null) BasicText(title, Modifier.padding(start = 20.dp, bottom = 8.dp),
        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = GREY))
    Group(content)
    if (footer != null) BasicText(footer, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
    Spacer(Modifier.height(SECTION_GAP.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(Color.White)) { Column(Modifier.fillMaxWidth()) { content() } }
}

@Composable
private fun Divider(start: Float = 20f) = Box(Modifier.padding(start = start.dp, end = 20.dp).fillMaxWidth().height(0.5.dp).background(SEPARATOR))

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(ROW_H.dp).padding(start = 20.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = LABEL)
        GlassToggle(checked, onChange)
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(ROW_H.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = LABEL)
        BasicText(value, style = LABEL.copy(color = GREY))
    }
}

/**
 * Unterseite „Icon-Paket“: je Paket eine Karte mit Name, Häkchen und einer Vorschau-Reihe aus vier Standard-Apps
 * (Telefon, Nachrichten, Fotos, Kalender), so wie sie mit diesem Paket aussehen. Oben „‹ Zurück“ wie iOS.
 */
@Composable
private fun IconPackPage(backLabel: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val packs = remember { io.github.janhurn9400.liquidy.data.IconPacks.available(context) }
    val sample = remember { previewApps(context) }
    val chosen = io.github.janhurn9400.liquidy.data.IconPacks.selected
    val noRipple = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        Row(Modifier.clickable(noRipple, null, onClick = onBack).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp, 17.dp)) {
                val sw = 2.4.dp.toPx(); val c = Color(0xFF007AFF)
                drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.05f), androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.5f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.5f), androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.95f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
            BasicText(backLabel, style = LABEL.copy(color = Color(0xFF007AFF)))
        }
        BasicText(stringResource(R.string.settings_sec_iconpack), Modifier.padding(bottom = 18.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.Black))
        (listOf<io.github.janhurn9400.liquidy.data.IconPacks.Pack?>(null) + packs).forEach { pack ->
            val selected = chosen == pack?.packageName
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(GROUP_CORNER.dp)).background(Color.White)
                .clickable(noRipple, null) { io.github.janhurn9400.liquidy.data.IconPacks.select(context, pack?.packageName) }
                .padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(pack?.label ?: stringResource(R.string.settings_iconpack_system), Modifier.weight(1f), maxLines = 1,
                        style = LABEL.copy(fontWeight = FontWeight.SemiBold))
                    if (selected) Canvas(Modifier.size(16.dp, 14.dp)) {
                        val sw = 2.4.dp.toPx(); val c = Color(0xFF007AFF)
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.55f), androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.92f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.92f), androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.08f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                    sample.forEach { (cn, label) -> PreviewIcon(pack?.packageName, cn, label) }
                }
            }
        }
        BasicText(stringResource(if (packs.isEmpty()) R.string.settings_iconpack_none else R.string.settings_iconpack_footer),
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
    }
}

@Composable
private fun PreviewIcon(pack: String?, cn: android.content.ComponentName, label: String) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val px = (PREVIEW_ICON * density).toInt()
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pack, cn) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val orig = context.packageManager.getActivityIcon(cn)
                val themed = pack?.let { io.github.janhurn9400.liquidy.data.IconPacks.iconFrom(context, it, cn, orig, px) }
                io.github.janhurn9400.liquidy.data.IconRenderer.render(themed ?: orig, px, fullBleed = themed != null)
            }.getOrNull()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width((PREVIEW_ICON + 18f).dp)) {
        Box(Modifier.size(PREVIEW_ICON.dp).clip(io.github.janhurn9400.liquidy.design.ContinuousRoundedShape((PREVIEW_ICON * ICON_CORNER_SHARE).dp))
            .background(Color(0xFFE5E5EA))) {
            img?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize()) }
            // feiner Rand: weiße Symbole sonst unsichtbar auf der weißen Karte
            Box(Modifier.fillMaxSize().border(0.5.dp, Color.Black.copy(alpha = 0.12f), io.github.janhurn9400.liquidy.design.ContinuousRoundedShape((PREVIEW_ICON * ICON_CORNER_SHARE).dp)))
        }
        Spacer(Modifier.height(5.dp))
        BasicText(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = Color.Black))
    }
}

/** Vier Standard-Apps für die Vorschau (Telefon, Nachrichten, Fotos, Kalender) — jeweils die Launcher-Activity. */
private fun previewApps(c: android.content.Context): List<Pair<android.content.ComponentName, String>> {
    val pm = c.packageManager
    fun launcherOf(i: android.content.Intent): Pair<android.content.ComponentName, String>? {
        val pkg = runCatching { pm.resolveActivity(i, 0)?.activityInfo?.packageName }.getOrNull()?.takeIf { it != "android" } ?: return null
        val cn = pm.getLaunchIntentForPackage(pkg)?.component ?: return null
        val label = runCatching { pm.getActivityInfo(cn, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
        return cn to label
    }
    val main = android.content.Intent.ACTION_MAIN
    return listOfNotNull(
        launcherOf(android.content.Intent(android.content.Intent.ACTION_DIAL)),
        launcherOf(android.content.Intent.makeMainSelectorActivity(main, android.content.Intent.CATEGORY_APP_MESSAGING)),
        launcherOf(android.content.Intent.makeMainSelectorActivity(main, android.content.Intent.CATEGORY_APP_GALLERY)),
        launcherOf(android.content.Intent.makeMainSelectorActivity(main, android.content.Intent.CATEGORY_APP_CALENDAR)),
    ).distinctBy { it.first }.take(4)
}

@Composable
private fun LinkRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ROW_H.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 20.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = LABEL)
        if (value != null) { BasicText(value, style = LABEL.copy(color = GREY)); Spacer(Modifier.width(8.dp)) }
        Chevron()
    }
}

private enum class SubPage { None, IconPacks, Glass, Module, Report }

/**
 * Unterseite „Liquid Glass“ wie iOS (Einstellungen › Darstellung › Liquid Glass, Nutzer-Screenshot 10-14): Vorschaukarte
 * (Bild oben, dunkler Textteil, darüber runder Glasknopf + Glas-Suchfeld, die den Text darunter live brechen), darunter der
 * Regler klar ↔ getönt und die Erklärung. Bild = eigenes Wallpaper (keine Apple-Bilder), sonst farbiger Ersatz.
 */
@Composable
private fun GlassPage(backLabel: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val noRipple = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        Row(Modifier.clickable(noRipple, null, onClick = onBack).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp, 17.dp)) {
                val sw = 2.4.dp.toPx(); val c = Color(0xFF007AFF)
                drawLine(c, Offset(size.width * 0.9f, size.height * 0.05f), Offset(size.width * 0.1f, size.height * 0.5f), sw, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.9f, size.height * 0.95f), sw, StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
            BasicText(backLabel, style = LABEL.copy(color = Color(0xFF007AFF)))
        }
        BasicText(stringResource(R.string.settings_sec_glass), Modifier.padding(bottom = 18.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.Black))
        GlassPreviewCard(Modifier.widthIn(max = PREVIEW_MAX_W.dp).fillMaxWidth().align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        Group {
            Row(Modifier.fillMaxWidth().height(ROW_H.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassStyleGlyph(filled = false)
                io.github.janhurn9400.liquidy.ui.welcome.GlassSlider(
                    io.github.janhurn9400.liquidy.glass.GlassClarity.amount,
                    { io.github.janhurn9400.liquidy.glass.GlassClarity.set(context, it) },
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                GlassStyleGlyph(filled = true)
            }
        }
        BasicText(stringResource(R.string.settings_glass_footer), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 30.dp),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
    }
}

/** Vorschaukarte: Bild + Text bilden die Glasquelle, Knopf und Suchfeld darüber sind echtes LiquidGlass (folgt dem Regler). */
@Composable
private fun GlassPreviewCard(modifier: Modifier) {
    val wallpaper = LocalWallpaper.current
    val fallback = remember { if (wallpaper == null) io.github.janhurn9400.liquidy.glass.Wallpaper.generateColorful(800, 800) else null }
    val image = wallpaper ?: fallback!!
    val source = rememberLayerBackdrop()
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(PREVIEW_CORNER.dp)).background(PREVIEW_DARK)) {
        // Glasquelle: Bild (oben ≈ 55 %) + dunkler Textteil
        Column(Modifier.matchParentSize().layerBackdrop(source)) {
            androidx.compose.foundation.Image(image, null, Modifier.fillMaxWidth().weight(PREVIEW_IMAGE_SHARE),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Column(Modifier.fillMaxWidth().weight(1f - PREVIEW_IMAGE_SHARE).background(PREVIEW_DARK).padding(horizontal = 18.dp, vertical = 14.dp)) {
                BasicText(stringResource(R.string.settings_glass_preview_title), maxLines = 1,
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White))
                BasicText(stringResource(R.string.settings_glass_preview_sub), maxLines = 1,
                    style = TextStyle(fontFamily = Inter, fontSize = 22.sp, color = Color.White.copy(alpha = 0.55f)))
                Spacer(Modifier.height(8.dp))
                BasicText(stringResource(R.string.settings_glass_preview_body),
                    style = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 22.sp, color = Color.White))
            }
        }
        // Glas über dem Text: runder Knopf (Filter) + Suchfeld, wie in der iOS-Vorschau
        CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides source) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = PREVIEW_CONTROLS_BOTTOM.dp),
                verticalAlignment = Alignment.CenterVertically) {
                io.github.janhurn9400.liquidy.glass.LiquidGlass(io.github.janhurn9400.liquidy.glass.glassCapsule(), Modifier.size(PREVIEW_CONTROL_H.dp),
                    whiteTint = PREVIEW_GLASS_WHITE, blackTint = 0f) {
                    Canvas(Modifier.matchParentSize()) { // Filter-Symbol: drei kürzer werdende Linien
                        val sw = 2.dp.toPx(); val cx = size.width / 2; val cy = size.height / 2
                        listOf(16f, 11f, 6f).forEachIndexed { i, len ->
                            val y = cy + (i - 1) * 6.dp.toPx(); val half = len.dp.toPx() / 2
                            drawLine(Color.White, Offset(cx - half, y), Offset(cx + half, y), sw, StrokeCap.Round)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                io.github.janhurn9400.liquidy.glass.LiquidGlass(io.github.janhurn9400.liquidy.glass.glassCapsule(), Modifier.weight(1f).height(PREVIEW_CONTROL_H.dp),
                    whiteTint = PREVIEW_GLASS_WHITE, blackTint = 0f) {
                    Row(Modifier.matchParentSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(18.dp)) { // Lupe
                            val sw = 2.dp.toPx(); val r = size.width * 0.33f; val c = Offset(size.width * 0.42f, size.height * 0.42f)
                            drawCircle(Color.White, r, c, style = Stroke(sw))
                            drawLine(Color.White, c + Offset(r * 0.72f, r * 0.72f), Offset(size.width * 0.92f, size.height * 0.92f), sw, StrokeCap.Round)
                        }
                        Spacer(Modifier.width(8.dp))
                        BasicText(stringResource(R.string.search_placeholder_glass), style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f)))
                    }
                }
            }
        }
    }
}

/**
 * Unterseite „Animationsmodul“ wie ein iOS-Untermenü (Nutzer-Screenshot WLAN 10-15): oben eine Karte mit großem Symbol,
 * fettem Titel, Erklärung + Link, darunter in derselben Karte die Zeile mit dem Status (statt Schalter). Darunter immer die
 * Installationsschritte, Download (fester Link aufs neueste Release) und „Magisk öffnen“.
 */
@Composable
private fun ModulePage(backLabel: String, tick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val noRipple = remember { MutableInteractionSource() }
    val active = remember(tick) { context.checkSelfPermission("android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED }
    fun view(url: String) = open(context, android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        Row(Modifier.clickable(noRipple, null, onClick = onBack).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp, 17.dp)) {
                val sw = 2.4.dp.toPx(); val c = Color(0xFF007AFF)
                drawLine(c, Offset(size.width * 0.9f, size.height * 0.05f), Offset(size.width * 0.1f, size.height * 0.5f), sw, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.9f, size.height * 0.95f), sw, StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
            BasicText(backLabel, style = LABEL.copy(color = Color(0xFF007AFF)))
        }
        // Karte: Symbol, Titel, Erklärung + Link, Status
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(Color.White)) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp)) {
                ModuleTile(MODULE_HERO)
                Spacer(Modifier.height(14.dp))
                BasicText(stringResource(R.string.settings_module), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.Black))
                Spacer(Modifier.height(6.dp))
                val more = stringResource(R.string.settings_module_github)
                val body = stringResource(R.string.settings_module_about)
                BasicText(androidx.compose.ui.text.buildAnnotatedString {
                    append(body); append(" ")
                    withLink(androidx.compose.ui.text.LinkAnnotation.Url(GITHUB_URL,
                        androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = Color(0xFF007AFF))))) { append(more) }
                }, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 21.sp, color = GREY))
            }
            Divider()
            Row(Modifier.fillMaxWidth().height(ROW_H.dp).padding(start = 20.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(stringResource(R.string.settings_module), Modifier.weight(1f), style = LABEL)
                BasicText(stringResource(if (active) R.string.settings_active else R.string.settings_module_missing),
                    style = LABEL.copy(color = if (active) Color(0xFF34C759) else GREY, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal))
            }
        }
        BasicText(stringResource(R.string.settings_module_footer), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
        Spacer(Modifier.height(SECTION_GAP.dp))
        // Anleitung (immer sichtbar)
        BasicText(stringResource(R.string.settings_module_install), Modifier.padding(start = 20.dp, bottom = 8.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = GREY))
        // Willkommen hat eigene Kartenfarbe → hier wie die übrigen Gruppen weiß
        io.github.janhurn9400.liquidy.ui.welcome.RootSteps(active = false, cardColor = Color.White, cardCorner = GROUP_CORNER)
        Spacer(Modifier.height(10.dp))
        Group {
            LinkRow(stringResource(R.string.welcome_root_download)) { view(io.github.janhurn9400.liquidy.ui.welcome.MODULE_URL) }
            val magisk = remember { context.packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk") }
            if (magisk != null) {
                Divider()
                LinkRow(stringResource(R.string.welcome_root_open_magisk)) { open(context, magisk) }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Unterseite „Fehler melden“ (Aufbau wie das Animationsmodul): Karte mit Symbol, Titel, Erklärung; darunter „Auf GitHub
 * melden“ (öffnet ein neues Issue, Vorlage + Diagnose schon im Textfeld) und „Diagnose kopieren“, dann die Diagnose selbst
 * zum Nachlesen — nichts wird ohne den Nutzer gesendet.
 */
@Composable
private fun ReportPage(backLabel: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val noRipple = remember { MutableInteractionSource() }
    val report = remember { io.github.janhurn9400.liquidy.data.Diagnostics.report(context) }
    val intro = stringResource(R.string.settings_report_template)
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        Row(Modifier.clickable(noRipple, null, onClick = onBack).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp, 17.dp)) {
                val sw = 2.4.dp.toPx(); val c = Color(0xFF007AFF)
                drawLine(c, Offset(size.width * 0.9f, size.height * 0.05f), Offset(size.width * 0.1f, size.height * 0.5f), sw, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.9f, size.height * 0.95f), sw, StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
            BasicText(backLabel, style = LABEL.copy(color = Color(0xFF007AFF)))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(Color.White).padding(20.dp)) {
            HeroTile(MODULE_HERO, Color(0xFFFF3B30), SettingsGlyphs.BugReport)
            Spacer(Modifier.height(14.dp))
            BasicText(stringResource(R.string.settings_report), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.Black))
            Spacer(Modifier.height(6.dp))
            BasicText(stringResource(R.string.settings_report_about), style = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 21.sp, color = GREY))
        }
        Spacer(Modifier.height(SECTION_GAP.dp))
        Group {
            LinkRow(stringResource(R.string.settings_report_github)) {
                open(context, android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(io.github.janhurn9400.liquidy.data.Diagnostics.issueUrl(context, intro))))
            }
            Divider()
            LinkRow(stringResource(R.string.settings_diagnostics_copy), if (copied) stringResource(R.string.settings_diagnostics_copied) else null) {
                io.github.janhurn9400.liquidy.data.Diagnostics.copy(context); copied = true
            }
        }
        BasicText(stringResource(R.string.settings_diagnostics_footer), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
        Spacer(Modifier.height(SECTION_GAP.dp))
        BasicText(stringResource(R.string.settings_report_included), Modifier.padding(start = 20.dp, bottom = 8.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = GREY))
        BasicText(report, Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(Color.White).padding(16.dp),
            style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF3A3A3C)))
        Spacer(Modifier.height(40.dp))
    }
}

/** Große Kachel oben auf Unterseiten (Kategorie-Stil: Verlauf, kontinuierliche Ecken, weiße Glyphe). */
@Composable
private fun HeroTile(sizePt: Float, bg: Color, glyph: androidx.compose.ui.graphics.vector.ImageVector) {
    val shape = io.github.janhurn9400.liquidy.design.ContinuousRoundedShape((sizePt * TILE_CORNER).dp)
    Box(Modifier.size(sizePt.dp).clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(bg, Color(bg.red * TILE_SHADE, bg.green * TILE_SHADE, bg.blue * TILE_SHADE)))), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.graphics.vector.rememberVectorPainter(glyph), null,
            Modifier.size((sizePt * TILE_GLYPH).dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
    }
}

/** Kachel des Animationsmoduls (Funkeln auf Indigo) im Stil der Kategorie-Kacheln. */
@Composable
private fun ModuleTile(sizePt: Float) {
    val bg = Color(0xFF5856D6)
    val shape = io.github.janhurn9400.liquidy.design.ContinuousRoundedShape((sizePt * TILE_CORNER).dp)
    Box(Modifier.size(sizePt.dp).clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(bg, Color(bg.red * TILE_SHADE, bg.green * TILE_SHADE, bg.blue * TILE_SHADE)))), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.graphics.vector.rememberVectorPainter(SettingsGlyphs.AutoAwesome), null,
            Modifier.size((sizePt * TILE_GLYPH).dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
    }
}

/** Symbol am Glas-Regler (iOS): zwei versetzte abgerundete Flächen — links nur Umriss (klar), rechts gefüllt (getönt). */
@Composable
private fun GlassStyleGlyph(filled: Boolean) = Canvas(Modifier.size(26.dp, 22.dp)) {
    val w = 18.dp.toPx(); val h = 13.dp.toPx(); val r = CornerRadius(4.dp.toPx()); val sw = 1.6.dp.toPx()
    val back = Offset(size.width - w, 0f); val front = Offset(0f, size.height - h)
    if (filled) {
        drawRoundRect(GREY.copy(alpha = 0.55f), back, Size(w, h), r)
        drawRoundRect(GREY, front, Size(w, h), r)
    } else {
        drawRoundRect(GREY, back, Size(w, h), r, style = Stroke(sw))
        drawRoundRect(Color.White, front, Size(w, h), r)
        drawRoundRect(GREY, front, Size(w, h), r, style = Stroke(sw))
    }
}

/** Berechtigung: Name + Status, darunter in Grau, wofür sie gebraucht wird. */
@Composable
private fun PermissionRow(label: String, granted: Boolean, why: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_H.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 20.dp, end = 18.dp, top = 11.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = LABEL)
            BasicText(why, Modifier.padding(top = 3.dp, end = 12.dp), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 17.sp, color = GREY))
        }
        BasicText(allowed(granted), style = LABEL.copy(color = GREY)); Spacer(Modifier.width(8.dp))
        Chevron()
    }
}

@Composable
private fun Chevron() = Canvas(Modifier.size(8.dp, 14.dp)) {
    val sw = 2.dp.toPx()
    drawLine(CHEVRON, Offset(0f, 0f), Offset(size.width, size.height / 2), sw, StrokeCap.Round)
    drawLine(CHEVRON, Offset(size.width, size.height / 2), Offset(0f, size.height), sw, StrokeCap.Round)
}

private fun isDefaultHome(c: android.content.Context): Boolean {
    val h = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
    return c.packageManager.resolveActivity(h, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == c.packageName
}

private fun open(c: android.content.Context, i: android.content.Intent) { runCatching { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
private fun openAppSettings(c: android.content.Context) = open(c, android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    android.net.Uri.fromParts("package", c.packageName, null)))

// Maße [GEMESSEN Aufnahme 09:39, 0,729 pt/px] bzw. iOS-Standard
private const val WIDE_MIN = 700f
private const val SIDEBAR_MAX = 320f
private const val SIDEBAR_SHARE = 0.34f
private const val DUO_RIGHT_ROOM = 85f        // Inhalt endet 85 pt vor dem rechten Rand (866 von 951)
private const val DUO_COLUMN_FROM_RIGHT = 47f
private const val DUO_BUTTON_Y = 190f
private const val TOP_PAD = 34f
private const val SIDEBAR_TOP = 86f   // Kopfkarte ≈ 100 pt [GEMESSEN]
private const val HERO_TOP = 46f
// Liquid-Glass-Vorschau [GEMESSEN] Nutzer-Screenshot 10-14 (920 px = 402 pt): Karte ≈ 370 × 370 pt, Ecken ≈ 26 pt, Bild ≈ 55 %,
// Knopf/Suchfeld 46 pt hoch, Unterkante ≈ 44 pt über dem Kartenrand
private const val PREVIEW_MAX_W = 420f
private const val PREVIEW_CORNER = 26f
private const val PREVIEW_IMAGE_SHARE = 0.55f
private const val PREVIEW_CONTROL_H = 46f
private const val PREVIEW_CONTROLS_BOTTOM = 44f
private const val PREVIEW_GLASS_WHITE = 0.1f
private val PREVIEW_DARK = Color(0xFF252321)
private const val ROW_H = 52f
private const val ICON = 29f
private const val TILE_CORNER = 0.225f // Ecken der Kategorie-Kachel relativ zur Kante (iOS-Einstellungen)
private const val TILE_GLYPH = 0.62f   // Glyphe relativ zur Kachel
private const val TILE_SHADE = 0.94f   // Kachel unten ≈ 6 % dunkler [GEMESSEN] iOS-27-Einstellungen
private const val TILE_RIM = 0.22f     // Lichtrand oben (unten 60 %) [GEMESSEN qualitativ]
private const val TILE_RIM_W = 0.6f    // dp Breite des Lichtrands
private const val GEAR_SIZE = 0.72f    // Zahnrad/Darstellung relativ zur Kachel [GEMESSEN]
private const val GEAR_TEETH = 26
private const val MODULE_HERO = 60f    // pt Symbol oben auf der Modul-Seite (iOS-Untermenü-Kopf)
private const val GITHUB_URL = "https://github.com/janhurn9400/liquidy"
private const val ADAPTIVE_SCALE = 1.5f // adaptives Icon: 108 dp Ebene, davon 72 sichtbar
private const val GROUP_CORNER = 24f
private const val SECTION_GAP = 26f
private val SIDEBAR_BG = Color.White
private val DETAIL_BG = Color(0xFFF2F2F7)
private val SELECTED = Color(0xFFE5E5EA)
private val GREY = Color(0xFF8E8E93)
private val SEPARATOR = Color(0xFFD1D1D6)
private val CHEVRON = Color(0xFFC4C4C7)
private const val PREVIEW_ICON = 54f
/** T-ICON-R / Icon-Größe (16,33 / 60). */
private const val ICON_CORNER_SHARE = 16.33f / 60f
private val LABEL = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.Black)
