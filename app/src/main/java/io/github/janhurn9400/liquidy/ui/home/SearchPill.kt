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
 * Glass capsule above the dock (T-PILL) that morphs into the search field (M-SEARCH-MORPH, measured recording5):
 *  - Rest: "Search"; page dots while swiping (M-PILL).
 *  - While pulling down it stays sharp (it does NOT lie in the blurred layer) and moves along with ¼ of h.
 *  - From h ≈ 94 pt it morphs (still while dragging) in width into the search field; back below ≈ 62 pt.
 *  - Open: the field stays at its height until the keyboard reaches it, then rides on it and docks at full width.
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
     * Duo style: at rest the pill is the round magnifier button ([duoButton], screen px) in the right column; on open
     * it morphs into the field at the bottom centre ([duoFieldWidth] wide, centre at [duoFieldCenterX]) [MEASURED] 21:28, 42 s.
     */
    duoButton: Rect? = null,
    duoFieldWidth: Dp = 0.dp,
    duoFieldCenterX: Dp = 0.dp,
    /** Duo: centre y of the field without keyboard (bottom centre, measured ≈ 636 pt unfolded). */
    duoFieldRestY: Dp = 0.dp,
    onLaunch: (AppEntry, Rect) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDots by remember { mutableStateOf(false) }
    val forceDotsState = androidx.compose.runtime.rememberUpdatedState(forceDots)
    LaunchedEffect(pager) {
        // Edit mode: always dots [MEASURED] IMG_3124
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

    // ── Keyboard / focus (formerly in SearchOverlay) ─────────────────────────────
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Keyboard like iOS: peeks out on morph (app-driven), comes up on release without a start delay
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
                focus.requestFocus() // binds the keyboard without showing it (showKeyboardOnFocus = false)
                driver.start()
                driver.animateTo(with(densityForKb) { Motion.searchKeyboardPeek.value.dp.toPx() }, Motion.searchKeyboard)
            }
            else -> {
                if (driver.active) driver.animateTo(0f, Motion.searchClose, finish = false)?.join()
                focusManager.clearFocus(); keyboard?.hide()
            }
        }
    }

    // ── Geometry ───────────────────────────────────────────────────────────
    val density = LocalDensity.current
    val m = model.searchMorph.value.coerceIn(0f, 1.05f)
    val h = model.searchProgress.value
    val pages = dotCount // the App Library gets no dot
    val pillWidth = maxOf(DesignTokens.pillWidth.dp, DesignTokens.dotPitch.dp * (pages + 1))
    val pillHeight = DesignTokens.pillHeight.dp
    val fieldHeight = DesignTokens.searchFieldHeight.dp
    // Duo: rest position = just below the screen; open it moves up (above the keyboard or to the field position)
    val centerYClosed = if (duoButton != null) lerp(screenHeight + fieldHeight, duoFieldRestY, m.coerceIn(0f, 1f))
        else y + pillHeight / 2 + (Motion.searchPillFollow.value * (if (model.searchOpen) model.searchReleaseH else h)).dp

    // The morph grows directly to the full field width (measured ≈ 386 pt)
    // Duo (recording 21:28, 41.1–41.4 s): NO morph — the magnifier is its own button (DuoSearchButton), the field has field
    // size from the start, is centred and slides in from below the screen. iPhone: morph from the pill.
    val duo = duoButton != null
    val width = if (duo) duoFieldWidth else lerp(pillWidth, screenWidth - DesignTokens.searchFieldSide.dp * 2, m.coerceIn(0f, 1f))
    val height = if (duo) fieldHeight else lerp(pillHeight, fieldHeight, m.coerceIn(0f, 1f))
    val centerX = if (duo) duoFieldCenterX else screenWidth / 2
    val restTop = centerYClosed - height / 2
    val open = model.searchOpen || model.query.isNotEmpty()
    // Open: the field stays until the keyboard edge reaches it, and then sticks directly above it.
    // The keyboard height is read only in the layout phase (Modifier.offset lambda) → same frame as the
    // keyboard, no lag (windowSoftInputMode=adjustResize delivers the insets frame by frame).
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val restTopPx = with(density) { restTop.toPx() }
    val lastTopPx = remember { floatArrayOf(restTopPx) }
    fun keyboardTopPx(d: androidx.compose.ui.unit.Density): Float = with(d) {
        screenHeightPx - maxOf(imeInsets.getBottom(this), navInsets.getBottom(this)) -
            (DesignTokens.searchFieldGap.dp + fieldHeight).toPx()
    }
    // Only when closing does the field glide back from its last position to the pill with M-SEARCH-CLOSE.
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

    if (duoButton != null && fieldMix < 0.001f && !model.searchOpen) return // Duo: field only during search
    LiquidGlass(
        shape = glassCapsule(),
        thickness = DesignTokens.pillHeight.value / DesignTokens.dockHeight.value,
        // Search field: clear glass (recording5). The glass source isn't dimmed, but the pill lies above the
        // search dimming → the same dimming as tint, otherwise the field looks light instead of dark-clear.
        whiteTint = DesignTokens.glassWhiteTint.value + (DesignTokens.searchFieldTint.value - DesignTokens.glassWhiteTint.value) * fieldMix,
        blackTint = (DesignTokens.glassTint.value + model.searchDim()).coerceAtMost(1f),
        // at rest only the wallpaper lies behind the pill; during search the (blurred) home content does
        onWallpaper = !model.searchVisible,
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset((centerX - width / 2).roundToPx(), topPx().roundToInt()) }
            .size(width, height),
    ) {
        // Pill content: "Search" or dots
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
        // Search field content: magnifier, input, placeholder (grows out of the pill)
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
                        // Focus without opening automatically — the KeyboardDriver controls the keyboard
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                        keyboardActions = KeyboardActions(onSearch = {
                            // iOS: input opens the best match — an app whose name starts like that, otherwise the web search
                            val app = model.searchResults(1).firstOrNull()?.takeIf { it.label.startsWith(model.query.trim(), ignoreCase = true) }
                            if (app != null) onLaunch(app, Rect.Zero)
                            else if (model.query.isNotBlank()) io.github.janhurn9400.liquidy.data.UniversalSearch.searchWeb(context, model.query)
                            model.closeSearch(animated = false)
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                MicGlyph(DesignTokens.searchMicSize.dp, glyph)
            }
        }
    }
}

private const val DUO_LUPE = 19f // magnifier in the round Duo button (pt)
private const val PILL_ICON_GAP = 0.5f // magnifier → "Search" = half the magnifier width [MEASURED] IMG_3123 (≈ 5 pt)

/**
 * Microphone like in the iOS search field (no SF Symbol), outline with uniform stroke width:
 * outlined capsule, U-bracket as one continuous path, stem and horizontal foot. Proportions after IMG_3131 [MEASURED roughly].
 */
@Composable
private fun MicGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val h = this.size.height
        val cx = this.size.width / 2
        val stroke = Stroke(width = h * 0.07f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        // capsule
        val capW = h * 0.30f
        drawRoundRect(color, topLeft = Offset(cx - capW / 2, h * 0.05f), size = androidx.compose.ui.geometry.Size(capW, h * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(capW / 2), style = stroke)
        // U-bracket: straight down, semicircle, straight up — one path, no seams
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
        // Stem + foot
        drawLine(color, Offset(cx, arcCenterY + r), Offset(cx, h * 0.93f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(cx - h * 0.19f, h * 0.93f), Offset(cx + h * 0.19f, h * 0.93f), stroke.width, StrokeCap.Round)
    }
}

/** Magnifier as a vector (no SF Symbol). Proportions [ESTIMATED] after IMG_3123. */
@Composable
internal fun MagnifierGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.width * 0.12f // the iOS magnifier is slim (IMG_3131)
        val r = this.size.width * 0.33f
        val c = Offset(r + stroke / 2, r + stroke / 2)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        val d = r * 0.7071f
        drawLine(color, Offset(c.x + d, c.y + d), Offset(this.size.width - stroke / 2, this.size.height - stroke / 2), stroke, StrokeCap.Round)
    }
}

