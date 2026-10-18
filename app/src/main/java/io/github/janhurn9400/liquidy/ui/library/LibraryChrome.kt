package io.github.janhurn9400.liquidy.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop
import io.github.janhurn9400.liquidy.glass.glassCapsule
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.ui.home.HomeModel
import io.github.janhurn9400.liquidy.ui.home.MagnifierGlyph
import io.github.janhurn9400.liquidy.ui.search.KeyboardDriver
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import kotlin.math.roundToInt

/**
 * Fixed parts of the App Library above the tiles (outside the glass source):
 *  - Scroll edge at the top: tiles above the search field become blurred (recording2 6.0 s).
 *  - Liquid Glass search field "App Library", stays at the top while scrolling. In the A–Z list it morphs narrow,
 *    "Cancel" appears on the right, the keyboard comes up (M-LIB-LIST).
 */
@Composable
fun LibraryChrome(model: HomeModel, geo: LibraryGeometry, density: Float, pageOffsetX: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = pageOffsetX()
                alpha = model.libCategoryAlpha()
                val sc = model.libCategoryScale()
                scaleX = sc; scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(geo.columnWidth / 2 / size.width, Motion.libCategoryPivotY.value)
            }
    ) {
        ScrollEdge(model, geo)
        LibrarySearchField(model, geo, density)
    }
}

/** Progressive blur at the top edge: blurred copy of the glass source, faded out downwards (DstIn gradient). */
@Composable
private fun ScrollEdge(model: HomeModel, geo: LibraryGeometry) {
    val backdrop = LocalGlassBackdrop.current ?: return
    val endDp = DesignTokens.libEdgeEnd.dp
    Box(
        Modifier
            // Duo: only above the App Library column, fading out softly on the left (otherwise a hard vertical edge to the page next to it)
            .then(if (geo.duo) Modifier.width(Dp(geo.columnWidth / geo.densityValue)) else Modifier.fillMaxWidth())
            .height(endDp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                // only visible once tiles scroll under the field (at rest only wallpaper lies there)
                alpha = (model.libraryScroll.value / EDGE_FADE_IN.dp.toPx()).coerceIn(0f, 1f)
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, EDGE_SOLID to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
                if (geo.duo) drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, (DUO_EDGE_FADE_X * geo.densityValue / size.width) to Color.Black, 1f to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
            }
            .drawPlainBackdrop(backdrop, { RectangleShape }, { blur(DesignTokens.libEdgeBlur.dp.toPx()) })
    )
}

@Composable
private fun LibrarySearchField(model: HomeModel, geo: LibraryGeometry, density: Float) {
    // ── Keyboard (like the home search: app-driven, without a start delay) ───────────
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val kbScope = rememberCoroutineScope()
    val driver = remember(view) { KeyboardDriver(view, kbScope) }
    val wasActive = remember { booleanArrayOf(false) }
    LaunchedEffect(model.libListOpen, model.libListActive) {
        when {
            model.libListActive -> {
                wasActive[0] = true
                focus.requestFocus()
                if (!driver.active) driver.start()
                driver.animateTo(null, Motion.searchKeyboard, finish = true) ?: keyboard?.show()
            }
            model.libListOpen && !wasActive[0] -> {
                // Bind already while pulling down → on release the keyboard comes up without delay
                focus.requestFocus()
                driver.start()
            }
            else -> {
                // "Cancel" / Back: keyboard down, focus gone
                wasActive[0] = false
                if (driver.active) driver.animateTo(0f, Motion.searchClose, finish = false)?.join()
                focusManager.clearFocus(); keyboard?.hide()
            }
        }
    }

    // ── Geometry: full width ↔ list layout ─────────────────────────────────────
    val s = geo.search
    val m = model.libFieldMorph.value
    val fullW = s.width / density
    val listW = (if (geo.duo) geo.duoFieldRight else DesignTokens.libListFieldRight.value) - s.left / density
    val wDp = fullW + (listW - fullW) * m
    val textColor = lerp(DesignTokens.libSearchTextColor, DesignTokens.libListPlaceholder, (model.libListDim.value).coerceIn(0f, 1f))
    val style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.libSearchFont.value.sp, color = Color.White)

    Box(Modifier.offset { IntOffset(s.left.roundToInt(), (s.top + model.libListPull.value * Motion.libListFieldFollow.value * density).roundToInt()) }) {
        LiquidGlass(
            shape = glassCapsule(),
            thickness = DesignTokens.libSearchHeight.value / DesignTokens.dockHeight.value,
            blur = DesignTokens.libSearchBlur.dp,
            // Duo: almost clear glass, just a touch lighter than the surroundings [video 2, 3.5 s]; iPhone: milkier (recording2)
            whiteTint = if (geo.duo) DUO_SEARCH_TINT + (DUO_LIST_FIELD_TINT - DUO_SEARCH_TINT) * model.libListDim.value
                else DesignTokens.libSearchTint.value + (DesignTokens.libListFieldTint.value - DesignTokens.libSearchTint.value) * model.libListDim.value,
            modifier = Modifier.size(Dp(wDp), Dp(s.height / density)),
        ) {
            // Magnifier + text: centred at rest, on the left in the list (inset T-LIB-LIST.fieldInset)
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MagnifierGlyph(DesignTokens.libSearchIcon.dp, textColor)
                        Spacer(Modifier.width(DesignTokens.libSearchIconGap.dp))
                        Box {
                            if (model.libQuery.isEmpty()) {
                                BasicText(stringResource(R.string.app_library), style = style.copy(color = textColor), maxLines = 1)
                            }
                            if (model.libListOpen) {
                                BasicTextField(
                                    value = model.libQuery,
                                    onValueChange = { model.libQuery = it },
                                    singleLine = true,
                                    textStyle = style,
                                    cursorBrush = SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                                    modifier = Modifier.width(Dp(listW - DesignTokens.libListFieldInset.value * 2 - DesignTokens.libSearchIcon.value - DesignTokens.libSearchIconGap.value))
                                        .focusRequester(focus),
                                )
                            }
                        }
                    }
                },
            ) { ms, c ->
                val row = ms[0].measure(Constraints(maxWidth = c.maxWidth))
                layout(c.maxWidth, c.maxHeight) {
                    val placeholderW = row.width.coerceAtMost(c.maxWidth)
                    val centered = (c.maxWidth - groupWidth(placeholderW, model)) / 2f
                    val inset = DesignTokens.libListFieldInset.dp.toPx()
                    val x = centered + (inset - centered) * m
                    row.place(x.roundToInt(), (c.maxHeight - row.height) / 2)
                }
            }
        }
    }

    // Duo: round glass × button to the right of the field instead of "Cancel" [video 2, 17.24–17.30 s]: slides in ≈ 30 pt from
    // the right, first the × glyph, the glass area fills shortly after
    if (geo.duo && m > 0.01f) {
        val d = LIB_DUO_CLOSE_D
        Box(
            Modifier
                .offset {
                    IntOffset(((geo.duoCloseX - d / 2 + (1f - m) * DUO_CLOSE_SLIDE) * density).roundToInt(),
                        (s.top + (s.height - d * density) / 2 + model.libListPull.value * Motion.libListFieldFollow.value * density).roundToInt())
                }
                .size(Dp(d))
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((m - 0.6f) / 0.35f).coerceIn(0f, 1f) }) {
                LiquidGlass(shape = glassCapsule(), modifier = Modifier.fillMaxSize(), whiteTint = DesignTokens.libListFieldTint.value)
            }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = ((m - 0.3f) / 0.3f).coerceIn(0f, 1f) }) {
                val c = center; val a = size.minDimension * 0.17f; val sw = size.minDimension * 0.05f
                drawLine(Color.White, c + androidx.compose.ui.geometry.Offset(-a, -a), c + androidx.compose.ui.geometry.Offset(a, a), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White, c + androidx.compose.ui.geometry.Offset(a, -a), c + androidx.compose.ui.geometry.Offset(-a, a), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
    // "Cancel" to the right of the field — fades in with the morph and follows from the right (recording2 11.4–11.7 s)
    if (!geo.duo && m > 0.01f) {
        Box(
            Modifier
                .offset {
                    IntOffset(
                        ((DesignTokens.libCancelX.value + (1f - m) * CANCEL_SLIDE) * density).roundToInt(),
                        (s.top + (s.height - CANCEL_LINE * density) / 2 + model.libListPull.value * Motion.libListFieldFollow.value * density).roundToInt(),
                    )
                }
                .graphicsLayer { alpha = m.coerceIn(0f, 1f) }
        ) {
            BasicText(stringResource(R.string.cancel), style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.libSearchFont.value.sp, color = Color.White, lineHeight = CANCEL_LINE.sp))
        }
    }
}

/** Width of magnifier + "App Library" (for centring at rest), independent of the wide input field. */
private fun androidx.compose.ui.unit.Density.groupWidth(rowWidth: Int, model: HomeModel): Float {
    // With the input field the row is as wide as the field; only magnifier + placeholder are centred.
    if (!model.libListOpen) return rowWidth.toFloat()
    return (DesignTokens.libSearchIcon.dp + DesignTokens.libSearchIconGap.dp).toPx() + PLACEHOLDER_W.dp.toPx()
}

private const val DUO_SEARCH_TINT = 0.06f
private const val DUO_LIST_FIELD_TINT = 0.10f
private const val DUO_EDGE_FADE_X = 48f   // pt soft fade-out on the left
private const val EDGE_SOLID = 0.6f       // gradient: fully blurred up to 60 % of the edge height [ESTIMATED] recording2 6.0 s
private const val EDGE_FADE_IN = 24f      // pt of scroll travel until the edge is fully there [ESTIMATED]
private const val CANCEL_SLIDE = 24f
private const val DUO_CLOSE_SLIDE = 30f   // pt [MEASURED video 2, 17.24 → 17.30 s]      // pt, "Cancel" follows from the right [ESTIMATED]
private const val CANCEL_LINE = 22f       // line height for vertical centring
private const val PLACEHOLDER_W = 100f    // "App Library" at 17 pt [MEASURED] ≈ 100 pt
