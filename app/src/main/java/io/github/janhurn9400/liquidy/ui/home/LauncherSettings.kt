package io.github.janhurn9400.liquidy.ui.home

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
private enum class Category { General, Appearance, HomeScreen, Permissions, Advanced, About }

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
                Box(btnModifier) {
                    if (back) GlassBackButton(onClick = { selected = null })
                    else CloseButton { model.closeSettings() }
                }
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
        Canvas(Modifier.size(58.dp)) { // Launcher-Symbol: 3×3-Raster aus Kacheln
            drawRoundRect(Color(0xFF1C1C1E), cornerRadius = CornerRadius(size.width * 0.22f))
            val cell = size.width / 4.2f; val gap = (size.width - 3 * cell) / 4
            for (r in 0..2) for (c in 0..2) drawRoundRect(TILE_COLORS[(r * 3 + c) % TILE_COLORS.size],
                Offset(gap + c * (cell + gap), gap + r * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.25f))
        }
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
            Category.Permissions -> {
                val cal = remember(tick) { context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED }
                val badges = remember(tick) { io.github.janhurn9400.liquidy.data.NotificationBadges.hasAccess(context) }
                val files = remember(tick) { io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead() }
                val usage = remember(tick) { model.hasUsageAccess() }
                Section(stringResource(R.string.settings_sec_access), footer = stringResource(R.string.settings_perm_footer)) {
                    LinkRow(stringResource(R.string.welcome_perm_calendar), allowed(cal)) { openAppSettings(context) }
                    Divider()
                    LinkRow(stringResource(R.string.welcome_perm_badges), allowed(badges)) { io.github.janhurn9400.liquidy.data.NotificationBadges.requestAccess(context) }
                    Divider()
                    LinkRow(stringResource(R.string.settings_usage_access), allowed(usage)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)))
                    }
                    Divider()
                    LinkRow(stringResource(R.string.use_android_wallpaper), allowed(files)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.fromParts("package", context.packageName, null)))
                    }
                }
            }
            Category.Advanced -> {
                val module = remember(tick) { context.checkSelfPermission("android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED }
                Section(stringResource(R.string.settings_sec_module), footer = stringResource(R.string.settings_module_footer)) {
                    ValueRow(stringResource(R.string.settings_module), stringResource(if (module) R.string.settings_active else R.string.settings_inactive))
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
    Category.Permissions -> R.string.settings_cat_permissions
    Category.Advanced -> R.string.settings_cat_advanced
    Category.About -> R.string.settings_section_about
})

/** Farbiges Bereichs-Symbol (29 pt, abgerundet) mit eigener Vektor-Glyphe (keine SF Symbols). */
@Composable
private fun CategoryIcon(c: Category, sizePt: Float = ICON) {
    val bg = when (c) {
        Category.General -> Color(0xFF8E8E93); Category.Appearance -> Color(0xFF1C1C1E); Category.HomeScreen -> Color(0xFF0A84FF)
        Category.Permissions -> Color(0xFF34C759); Category.Advanced -> Color(0xFFAF52DE); Category.About -> Color(0xFF8E8E93)
    }
    Canvas(Modifier.size(sizePt.dp).clip(RoundedCornerShape((sizePt * 0.24f).dp)).background(bg)) {
        val s = size.width; val sw = s * 0.08f; val cc = center
        when (c) {
            Category.General -> { // Zahnrad
                drawCircle(Color.White, s * 0.16f, cc, style = Stroke(sw))
                for (k in 0 until 8) { val a = Math.toRadians(k * 45.0); val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                    drawLine(Color.White, cc + d * (s * 0.24f), cc + d * (s * 0.33f), sw * 1.4f, StrokeCap.Round) }
            }
            Category.Appearance -> { // Kreis halb gefüllt
                drawCircle(Color.White, s * 0.28f, cc, style = Stroke(sw))
                drawArc(Color.White, 90f, 180f, true, Offset(cc.x - s * 0.28f, cc.y - s * 0.28f), Size(s * 0.56f, s * 0.56f))
            }
            Category.HomeScreen -> { // 2×2-Raster
                val t = s * 0.18f; val g = s * 0.08f; val o = (s - 2 * t - g) / 2
                for (r in 0..1) for (k in 0..1) drawRoundRect(Color.White, Offset(o + k * (t + g), o + r * (t + g)), Size(t, t), CornerRadius(t * 0.3f))
            }
            Category.Permissions -> shield(s, sw)
            Category.Advanced -> { // Funken
                for (k in 0 until 4) { val a = Math.toRadians(45.0 + k * 90.0); val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                    drawLine(Color.White, cc + d * (s * 0.08f), cc + d * (s * 0.3f), sw * 1.3f, StrokeCap.Round) }
                drawCircle(Color.White, s * 0.07f, cc)
            }
            Category.About -> { // „i“
                drawCircle(Color.White, s * 0.06f, Offset(cc.x, s * 0.3f))
                drawLine(Color.White, Offset(cc.x, s * 0.44f), Offset(cc.x, s * 0.72f), sw * 1.5f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.shield(s: Float, sw: Float) {
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(s * 0.5f, s * 0.2f); lineTo(s * 0.74f, s * 0.3f); lineTo(s * 0.72f, s * 0.55f)
        quadraticTo(s * 0.68f, s * 0.72f, s * 0.5f, s * 0.82f); quadraticTo(s * 0.32f, s * 0.72f, s * 0.28f, s * 0.55f)
        lineTo(s * 0.26f, s * 0.3f); close()
    }
    drawPath(p, Color.White, style = Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
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

@Composable
private fun Chevron() = Canvas(Modifier.size(8.dp, 14.dp)) {
    val sw = 2.dp.toPx()
    drawLine(CHEVRON, Offset(0f, 0f), Offset(size.width, size.height / 2), sw, StrokeCap.Round)
    drawLine(CHEVRON, Offset(size.width, size.height / 2), Offset(0f, size.height), sw, StrokeCap.Round)
}

/** Runder Glas-Knopf „×“ (gleiches Glas wie der Zurück-Knopf). */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)) {
        io.github.janhurn9400.liquidy.glass.LiquidGlass(io.github.janhurn9400.liquidy.glass.glassCapsule(), Modifier.matchParentSize(), whiteTint = 0.6f, blackTint = 0f)
        Canvas(Modifier.matchParentSize()) {
            val s = size.width; val a = s * 0.18f; val w = 2.2.dp.toPx(); val c = center
            drawLine(Color(0xFF1C1C1E), c + Offset(-a, -a), c + Offset(a, a), w, StrokeCap.Round)
            drawLine(Color(0xFF1C1C1E), c + Offset(a, -a), c + Offset(-a, a), w, StrokeCap.Round)
        }
    }
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
private const val ROW_H = 52f
private const val ICON = 29f
private const val GROUP_CORNER = 24f
private const val SECTION_GAP = 26f
private val SIDEBAR_BG = Color.White
private val DETAIL_BG = Color(0xFFF2F2F7)
private val SELECTED = Color(0xFFE5E5EA)
private val GREY = Color(0xFF8E8E93)
private val SEPARATOR = Color(0xFFD1D1D6)
private val CHEVRON = Color(0xFFC4C4C7)
private val LABEL = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.Black)
private val TILE_COLORS = listOf(Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFFFF375F), Color(0xFFBF5AF2), Color(0xFF64D2FF))
