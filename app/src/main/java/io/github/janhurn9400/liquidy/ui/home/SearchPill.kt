package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassCapsule
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Glas-Kapsel über dem Dock (T-PILL), die zum Suchfeld morpht (M-SEARCH-MORPH, gemessen recording5):
 *  - Ruhe: „Search“; beim Wischen Page-Dots (M-PILL).
 *  - Beim Herunterziehen bleibt sie scharf (liegt NICHT in der verwischten Ebene) und wandert mit ¼ von h mit.
 *  - Ab h ≈ 94 pt morpht sie (noch während des Ziehens) in die Breite zum Suchfeld; unter ≈ 62 pt zurück.
 *  - Offen: das Feld bleibt auf seiner Höhe, bis die Tastatur es erreicht, dann reitet es auf ihr und dockt in voller Breite an.
 */
@Composable
fun SearchPill(
    model: HomeModel,
    pager: PagerState,
    screenWidth: Dp,
    screenHeight: Dp,
    y: Dp,
    forceDots: Boolean = false,
    dotCount: Int = pager.pageCount,
    /**
     * Duo-Stil: in Ruhe ist die Pill der runde Lupenknopf ([duoButton], Bildschirm-px) in der rechten Spalte; beim Öffnen
     * morpht sie in das Feld unten mittig ([duoFieldWidth] breit, Mitte bei [duoFieldCenterX]) [GEMESSEN] 21:28, 42 s.
     */
    duoButton: Rect? = null,
    duoFieldWidth: Dp = 0.dp,
    duoFieldCenterX: Dp = 0.dp,
    /** Duo: Mitte y des Felds ohne Tastatur (unten mittig, gemessen ≈ 636 pt aufgeklappt). */
    duoFieldRestY: Dp = 0.dp,
    onLaunch: (AppEntry, Rect) -> Unit,
) {
    var showDots by remember { mutableStateOf(false) }
    val forceDotsState = androidx.compose.runtime.rememberUpdatedState(forceDots)
    LaunchedEffect(pager) {
        // Bearbeitungsmodus: immer Dots [GEMESSEN] IMG_3124
        snapshotFlow { pager.isScrolling || forceDotsState.value }.collectLatest { scrolling ->
            if (scrolling) showDots = true
            else {
                delay(Motion.pillDotsHold.value.roundToInt().toLong())
                showDots = false
            }
        }
    }
    val dotsAlpha by animateFloatAsState(
        targetValue = if (showDots) 1f else 0f,
        animationSpec = tween(Motion.pillCrossfade.value.roundToInt()),
        label = "pillDots",
    )

    // ── Tastatur / Fokus (früher im SearchOverlay) ─────────────────────────────
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Tastatur wie iOS: lugt beim Morph heraus (App-gesteuert), fährt beim Loslassen ohne Startverzögerung hoch
    val view = androidx.compose.ui.platform.LocalView.current
    val kbScope = androidx.compose.runtime.rememberCoroutineScope()
    val driver = remember(view) { io.github.janhurn9400.liquidy.ui.search.KeyboardDriver(view, kbScope) }
    val densityForKb = LocalDensity.current
    LaunchedEffect(model.searchMorphOn, model.searchOpen) {
        when {
            model.searchOpen -> {
                focus.requestFocus()
                if (driver.active) driver.animateTo(null, Motion.searchKeyboard, finish = true) else keyboard?.show()
            }
            model.searchMorphOn -> {
                focus.requestFocus() // bindet die Tastatur, ohne sie zu zeigen (showKeyboardOnFocus = false)
                driver.start()
                driver.animateTo(with(densityForKb) { Motion.searchKeyboardPeek.value.dp.toPx() }, Motion.searchKeyboard)
            }
            else -> {
                if (driver.active) driver.animateTo(0f, Motion.searchClose, finish = false)?.join()
                focusManager.clearFocus(); keyboard?.hide()
            }
        }
    }

    // ── Geometrie ───────────────────────────────────────────────────────────
    val density = LocalDensity.current
    val m = model.searchMorph.value.coerceIn(0f, 1.05f)
    val h = model.searchProgress.value
    val pages = dotCount // App-Mediathek bekommt keinen Punkt
    val pillWidth = maxOf(DesignTokens.pillWidth.dp, DesignTokens.dotPitch.dp * (pages + 1))
    val pillHeight = DesignTokens.pillHeight.dp
    val fieldHeight = DesignTokens.searchFieldHeight.dp
    // Duo: Ruheposition = knapp unter dem Bildschirm; offen fährt es hoch (bis über die Tastatur bzw. bis zur Feldposition)
    val centerYClosed = if (duoButton != null) lerp(screenHeight + fieldHeight, duoFieldRestY, m.coerceIn(0f, 1f))
        else y + pillHeight / 2 + (Motion.searchPillFollow.value * (if (model.searchOpen) model.searchReleaseH else h)).dp

    // Morph wächst direkt auf die volle Feldbreite (gemessen ≈ 386 pt)
    // Duo (Aufnahme 21:28, 41,1–41,4 s): KEIN Morph — die Lupe ist ein eigener Knopf (DuoSearchButton), das Feld hat von
    // Anfang an Feldgröße, liegt mittig und gleitet von unterhalb des Bildschirms herein. iPhone: Morph aus der Pill.
    val duo = duoButton != null
    val width = if (duo) duoFieldWidth else lerp(pillWidth, screenWidth - DesignTokens.searchFieldSide.dp * 2, m.coerceIn(0f, 1f))
    val height = if (duo) fieldHeight else lerp(pillHeight, fieldHeight, m.coerceIn(0f, 1f))
    val centerX = if (duo) duoFieldCenterX else screenWidth / 2
    val restTop = centerYClosed - height / 2
    val open = model.searchOpen || model.query.isNotEmpty()
    // Offen: Feld bleibt stehen, bis die Tastaturkante es erreicht, und klebt dann direkt über ihr.
    // Die Tastaturhöhe wird erst in der Layout-Phase gelesen (Modifier.offset-Lambda) → gleicher Frame wie die
    // Tastatur, kein Nachhinken (windowSoftInputMode=adjustResize liefert die Insets Frame für Frame).
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val restTopPx = with(density) { restTop.toPx() }
    val lastTopPx = remember { floatArrayOf(restTopPx) }
    fun keyboardTopPx(d: androidx.compose.ui.unit.Density): Float = with(d) {
        screenHeightPx - maxOf(imeInsets.getBottom(this), navInsets.getBottom(this)) -
            (DesignTokens.searchFieldGap.dp + fieldHeight).toPx()
    }
    // Nur beim Schließen gleitet das Feld mit M-SEARCH-CLOSE von seiner letzten Position zurück zur Pill.
    val closeAnim = remember { androidx.compose.animation.core.Animatable(restTopPx) }
    val closing = remember { booleanArrayOf(false) }
    LaunchedEffect(open) {
        if (!open && lastTopPx[0] < restTopPx - 1f) {
            closing[0] = true
            closeAnim.snapTo(lastTopPx[0])
            closeAnim.animateTo(restTopPx, Motion.searchClose.spec())
        }
        closing[0] = false
    }
    val topPx: androidx.compose.ui.unit.Density.() -> Float = {
        val y = when {
            open -> minOf(restTopPx, keyboardTopPx(this))
            closing[0] -> closeAnim.value
            else -> restTopPx
        }
        lastTopPx[0] = y
        y
    }
    val fieldMix = m.coerceIn(0f, 1f)
    val pillContentAlpha = 1f - (fieldMix / Motion.searchMorphPillOut.value).coerceIn(0f, 1f)
    val fieldContentAlpha = ((fieldMix - Motion.searchMorphFieldIn.value) / (1f - Motion.searchMorphFieldIn.value)).coerceIn(0f, 1f)

    if (duoButton != null && fieldMix < 0.001f && !model.searchOpen) return // Duo: Feld nur während der Suche
    LiquidGlass(
        shape = glassCapsule(),
        thickness = DesignTokens.pillHeight.value / DesignTokens.dockHeight.value,
        // Suchfeld: klares Glas (recording5). Die Glas-Quelle ist nicht abgedunkelt, die Pill liegt aber über der
        // Such-Abdunkelung → dieselbe Abdunkelung als Tönung, sonst wirkt das Feld hell statt dunkel-klar.
        whiteTint = DesignTokens.glassWhiteTint.value + (DesignTokens.searchFieldTint.value - DesignTokens.glassWhiteTint.value) * fieldMix,
        blackTint = (DesignTokens.glassTint.value + model.searchDim()).coerceAtMost(1f),
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset((centerX - width / 2).roundToPx(), topPx().roundToInt()) }
            .size(width, height),
    ) {
        // Pill-Inhalt: „Search“ bzw. Dots
        val pillAlpha = if (duoButton != null) 0f else pillContentAlpha
        if (pillAlpha > 0.01f) {
            Row(
                Modifier.fillMaxSize().graphicsLayer { alpha = (1f - dotsAlpha) * pillAlpha },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (duoButton != null) MagnifierGlyph(DUO_LUPE.dp, DesignTokens.pillContentColor) else {
                MagnifierGlyph(DesignTokens.pillIconSize.dp, DesignTokens.pillContentColor)
                Spacer(Modifier.width(DesignTokens.pillIconSize.dp * PILL_ICON_GAP))
                BasicText(
                    text = stringResource(R.string.search),
                    style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.pillFontSize.value.sp, color = DesignTokens.pillContentColor),
                )
                }
            }
            if (duoButton == null) Box(Modifier.fillMaxSize().graphicsLayer { alpha = dotsAlpha * pillAlpha }) {
                Canvas(Modifier.fillMaxSize()) {
                    val pitch = DesignTokens.dotPitch.dp.toPx()
                    val r = DesignTokens.dotSize.dp.toPx() / 2f
                    val first = size.width / 2f - pitch * (pages - 1) / 2f
                    val current = pager.currentPage
                    for (i in 0 until pages) {
                        drawCircle(
                            color = Color.White.copy(alpha = if (i == current) 1f else DesignTokens.dotInactiveAlpha.value),
                            radius = r,
                            center = Offset(first + i * pitch, size.height / 2f),
                        )
                    }
                }
            }
        }
        // Suchfeld-Inhalt: Lupe, Eingabe, Platzhalter (wächst aus der Pill heraus)
        if (fieldMix > 0.01f || model.searchOpen || duoButton != null) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = DesignTokens.searchFieldPadding.dp).graphicsLayer { alpha = if (duoButton != null) 1f else fieldContentAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val glyph = lerp(DesignTokens.pillContentColor, DesignTokens.searchGlyphColor, fieldMix)
                MagnifierGlyph(lerp(DesignTokens.pillIconSize.dp, DesignTokens.searchFieldIcon.dp, fieldMix), glyph)
                Spacer(Modifier.width(DesignTokens.searchFieldIconGap.dp))
                Box(Modifier.weight(1f)) {
                    val style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.searchFieldFontSize.value.sp, color = DesignTokens.searchTextColor)
                    if (model.query.isEmpty()) {
                        BasicText(stringResource(R.string.search), style = style.copy(color = DesignTokens.searchPlaceholderColor), maxLines = 1)
                    }
                    BasicTextField(
                        value = model.query,
                        onValueChange = { model.query = it },
                        singleLine = true,
                        textStyle = style,
                        cursorBrush = SolidColor(Color.White),
                        // Fokus ohne automatisches Öffnen — die Tastatur steuert der KeyboardDriver
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                        keyboardActions = KeyboardActions(onSearch = {
                            model.searchResults(1).firstOrNull()?.let { onLaunch(it, Rect.Zero); model.closeSearch(animated = false) }
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                MicGlyph(DesignTokens.searchMicSize.dp, glyph)
            }
        }
    }
}

private const val DUO_LUPE = 19f // Lupe im runden Duo-Knopf (pt)
private const val PILL_ICON_GAP = 0.5f // Lupe → „Search“ = halbe Lupenbreite [GEMESSEN] IMG_3123 (≈ 5 pt)

/**
 * Mikrofon wie im iOS-Suchfeld (kein SF Symbol), Kontur in einheitlicher Strichstärke:
 * umrandete Kapsel, U-Bügel als ein durchgehender Pfad, Stiel und waagrechter Fuß. Proportionen nach IMG_3131 [GEMESSEN grob].
 */
@Composable
private fun MicGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val h = this.size.height
        val cx = this.size.width / 2
        val stroke = Stroke(width = h * 0.07f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        // Kapsel
        val capW = h * 0.30f
        drawRoundRect(color, topLeft = Offset(cx - capW / 2, h * 0.05f), size = androidx.compose.ui.geometry.Size(capW, h * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(capW / 2), style = stroke)
        // U-Bügel: senkrecht runter, Halbkreis, senkrecht rauf — ein Pfad, keine Stoßkanten
        val r = h * 0.27f
        val armTop = h * 0.36f
        val arcCenterY = h * 0.50f
        val u = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - r, armTop)
            lineTo(cx - r, arcCenterY)
            arcTo(androidx.compose.ui.geometry.Rect(cx - r, arcCenterY - r, cx + r, arcCenterY + r), 180f, -180f, false)
            lineTo(cx + r, armTop)
        }
        drawPath(u, color, style = stroke)
        // Stiel + Fuß
        drawLine(color, Offset(cx, arcCenterY + r), Offset(cx, h * 0.93f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(cx - h * 0.19f, h * 0.93f), Offset(cx + h * 0.19f, h * 0.93f), stroke.width, StrokeCap.Round)
    }
}

/** Lupe als Vektor (kein SF Symbol). Proportionen [GESCHÄTZT] nach IMG_3123. */
@Composable
internal fun MagnifierGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.width * 0.12f // iOS-Lupe ist schlank (IMG_3131)
        val r = this.size.width * 0.33f
        val c = Offset(r + stroke / 2, r + stroke / 2)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        val d = r * 0.7071f
        drawLine(color, Offset(c.x + d, c.y + d), Offset(this.size.width - stroke / 2, this.size.height - stroke / 2), stroke, StrokeCap.Round)
    }
}

