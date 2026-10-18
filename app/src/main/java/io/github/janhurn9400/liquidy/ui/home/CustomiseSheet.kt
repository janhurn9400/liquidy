package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.design.IconAppearance
import io.github.janhurn9400.liquidy.design.IconStyle
import io.github.janhurn9400.liquidy.design.IconVariant
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlin.math.roundToInt

/**
 * "Customise" sheet (recording7 13–30 s, T-CUST): Liquid Glass sheet at the bottom with four style previews
 * (Default · Dark · Clear · Tinted), below them Always|Auto or Light|Dark|Auto; Tinted additionally hue + saturation.
 * Lives in HomeScreen (glass source = home screen); tapping outside closes.
 */
@Composable
fun CustomiseSheet(model: HomeModel, sample: AppEntry?, wallpaper: ImageBitmap, onClose: () -> Unit) {
    val context = LocalContext.current
    val appear = remember { Animatable(0f) }
    val tall = remember { Animatable(if (IconAppearance.style == IconStyle.Tinted) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.wpeSheet.spec()) }
    LaunchedEffect(IconAppearance.style) { tall.animateTo(if (IconAppearance.style == IconStyle.Tinted) 1f else 0f, Motion.wpeSheet.spec()) }
    LaunchedEffect(model.customiseClosing) {
        if (model.customiseClosing) { appear.animateTo(0f, Motion.wpeSheet.spec()); onClose() }
    }
    fun save() = IconAppearance.save(context)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val screenW = constraints.maxWidth / density; val screenH = constraints.maxHeight / density
        // Tapping outside closes
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { model.closeCustomise() } })
        val side = SHEET_SIDE
        // Duo (unfolded/folded): sheet at most 396 pt wide, horizontally centred, bottom 17 pt above the edge —
        // the iPhone dimensions (bottom 864 pt) don't fit on 672/678 pt tall displays [MEASURED 10:40, 62/66 s]
        val duo = io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle
        val width = if (duo) minOf(screenW - 2 * side, DUO_SHEET_W) else screenW - 2 * side
        val left = if (duo) (screenW - width) / 2 else side
        val contentDx = if (duo) width / 2 - (STYLE_X.first() + STYLE_X.last()) / 2 + side else 0f
        val yShift = if (duo) screenH - DUO_SHEET_BOTTOM_GAP - SHEET_BOTTOM else 0f
        fun top() = SHEET_TOP + (SHEET_TOP_TALL - SHEET_TOP) * tall.value + yShift
        Box(
            Modifier
                .offset { IntOffset((left * density).roundToInt(), ((top() + (1f - appear.value) * (screenH - top())) * density).roundToInt()) }
                .size(Dp(width), Dp(SHEET_BOTTOM + yShift - top()))
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            LiquidGlass(
                shape = glassRect(SHEET_CORNER.dp),
                modifier = Modifier.fillMaxSize(),
                blur = SHEET_BLUR.dp,
                whiteTint = SHEET_TINT,
            )
            // Content, positions relative to the sheet top (measured on the short and the tall sheet)
            fun y(short: Float, tallY: Float) = (short - SHEET_TOP) + ((tallY - SHEET_TOP_TALL) - (short - SHEET_TOP)) * tall.value // relative to the top
            Centered(width, { y(629f, 441f) }) {
                BasicText(stringResource(R.string.customise), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White))
            }
            // Header [MEASURED user iPhone 10-18]: left the sun (dim wallpaper in dark mode), right small/large icons, 27 pt in
            HeaderButton(HEADER_INSET, { y(629f, 441f) }, if (IconAppearance.dimWallpaper) 1f else UNSELECTED_ALPHA,
                onClick = { IconAppearance.dimWallpaper = !IconAppearance.dimWallpaper; save() }) { c -> drawSunGlyph(c) }
            HeaderButton(width - HEADER_INSET, { y(629f, 441f) }, 1f,
                onClick = { IconAppearance.largeIcons = !IconAppearance.largeIcons; save() }) { c -> drawSizeGlyph(c, IconAppearance.largeIcons) }
            // Style previews
            IconStyle.entries.forEachIndexed { i, st ->
                val cx = STYLE_X[i] - side + contentDx
                val selected = IconAppearance.style == st
                Box(
                    Modifier
                        .offset { IntOffset(((cx - PREVIEW / 2) * density).roundToInt(), ((y(712.8f, 524f) - PREVIEW / 2) * density).roundToInt()) }
                        .size(PREVIEW.dp)
                        .clip(ContinuousRoundedShape(DesignTokens.iconCorner.dp * (PREVIEW / DesignTokens.iconSize.value)))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            IconAppearance.style = st
                            if (st == IconStyle.Dark && IconAppearance.variant == IconVariant.Dark) IconAppearance.variant = IconVariant.Light
                            save()
                        }
                ) {
                    if (sample != null) StyledIcon(sample, wallpaper, Modifier.fillMaxSize(), style = st,
                        dark = if (st == IconStyle.Dark) true else IconAppearance.dark)
                }
                val label = stringResource(when (st) { IconStyle.Default -> R.string.style_default; IconStyle.Dark -> R.string.style_dark; IconStyle.Clear -> R.string.style_clear; IconStyle.Tinted -> R.string.style_tinted })
                CenteredAt(cx, { y(770f, 582f) }, selected) { LabelText(label, selected) }
            }
            // Tinted: hue + saturation
            if (tall.value > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = tall.value }) {
                    Slider(width, { y(0f, 643f) }, Brush.horizontalGradient(HUES), IconAppearance.hue / 360f) {
                        IconAppearance.hue = it * 360f; IconAppearance.tintSource = io.github.janhurn9400.liquidy.design.TintSource.Custom; save() }
                    Slider(width, { y(0f, 697f) }, Brush.horizontalGradient(listOf(Color.White, IconAppearance.tint(1f, 1f / IconAppearance.saturation.coerceAtLeast(0.01f)))),
                        IconAppearance.saturation) { IconAppearance.saturation = it; IconAppearance.tintSource = io.github.janhurn9400.liquidy.design.TintSource.Custom; save() }
                    // Colour source [MEASURED 10-18]: phone (system accent) · wallpaper · eyedropper (sliders), 78 pt apart, Ø 47 pt
                    io.github.janhurn9400.liquidy.design.TintSource.entries.forEachIndexed { i, src ->
                        SourceButton(width / 2 + (i - 1) * SOURCE_PITCH, { y(0f, 757.5f) }, src, IconAppearance.tintSource == src) {
                            IconAppearance.applySource(context, src)
                        }
                    }
                }
            }
            // Light/dark: Dark → Always | Auto, otherwise Light | Dark | Auto
            val options = if (IconAppearance.style == IconStyle.Dark) listOf(IconVariant.Light to R.string.variant_always, IconVariant.Auto to R.string.variant_auto)
            else listOf(IconVariant.Light to R.string.variant_light, IconVariant.Dark to R.string.variant_dark, IconVariant.Auto to R.string.variant_auto)
            if (IconAppearance.style != IconStyle.Default) {
                val pitch = SEG_PITCH
                options.forEachIndexed { i, (v, res) ->
                    val cx = width / 2 + (i - (options.size - 1) / 2f) * pitch
                    val selected = IconAppearance.variant == v
                    CenteredAt(cx, { y(836.6f, 823f) }, selected, onClick = { IconAppearance.variant = v; save() }) { LabelText(stringResource(res), selected) }
                }
            }
        }
    }
}

/** Round tappable glyph in the sheet header, centred at ([cx], [y]). */
@Composable
private fun HeaderButton(cx: Float, y: () -> Float, alpha: Float, onClick: () -> Unit, glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    Canvas(
        Modifier
            .offset { IntOffset(((cx - HEADER_HIT / 2) * density).roundToInt(), ((y() - HEADER_HIT / 2) * density).roundToInt()) }
            .size(HEADER_HIT.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) { glyph(Color.White.copy(alpha = alpha)) }
}

/** Brightness glyph: circle, left half filled, eight short rays (≈ 23 pt). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSunGlyph(c: Color) {
    val r = size.minDimension * 0.17f; val ctr = center; val sw = size.minDimension * 0.055f
    drawCircle(c, r, ctr, style = Stroke(sw))
    drawArc(c, 90f, 180f, true, Offset(ctr.x - r, ctr.y - r), Size(2 * r, 2 * r))
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        val p0 = r * 1.55f; val p1 = r * 2.05f
        drawLine(c, ctr + Offset((kotlin.math.cos(a) * p0).toFloat(), (kotlin.math.sin(a) * p0).toFloat()),
            ctr + Offset((kotlin.math.cos(a) * p1).toFloat(), (kotlin.math.sin(a) * p1).toFloat()), sw, StrokeCap.Round)
    }
}

/** Icon size glyph: small square (≈ 8 pt) next to a large one (≈ 16 pt), bottom-aligned; the chosen size is filled. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSizeGlyph(c: Color, large: Boolean) {
    val u = size.minDimension / HEADER_HIT // px per pt
    val big = 16f * u; val small = 8f * u; val gap = 2.5f * u; val sw = 1.6f * u
    val total = small + gap + big
    val x0 = center.x - total / 2; val bottom = center.y + big / 2
    val sTop = Offset(x0, bottom - small); val bTop = Offset(x0 + small + gap, bottom - big)
    if (large) {
        drawRoundRect(c, sTop, Size(small, small), CornerRadius(2.5f * u), style = Stroke(sw))
        drawRoundRect(c, bTop, Size(big, big), CornerRadius(4.5f * u))
    } else {
        drawRoundRect(c, sTop, Size(small, small), CornerRadius(2.5f * u))
        drawRoundRect(c, bTop, Size(big, big), CornerRadius(4.5f * u), style = Stroke(sw))
    }
}

/** Tint colour source: phone (dark glass, white phone), wallpaper (white, grey flower), eyedropper (tint colour). */
@Composable
private fun SourceButton(cx: Float, y: () -> Float, src: io.github.janhurn9400.liquidy.design.TintSource, selected: Boolean, onClick: () -> Unit) {
    val tint = IconAppearance.tint(1f)
    Canvas(
        Modifier
            .offset { IntOffset(((cx - SOURCE_D / 2 - 4f) * density).roundToInt(), ((y() - SOURCE_D / 2 - 4f) * density).roundToInt()) }
            .size((SOURCE_D + 8f).dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        val r = SOURCE_D / 2 * density; val c = center; val u = density
        val fill = when (src) {
            io.github.janhurn9400.liquidy.design.TintSource.Device -> Color.White.copy(alpha = 0.14f)
            io.github.janhurn9400.liquidy.design.TintSource.Wallpaper -> Color(0xFFEDEDED)
            io.github.janhurn9400.liquidy.design.TintSource.Custom -> tint
        }
        drawCircle(fill, r, c)
        if (selected) drawCircle(Color.White, r - 1.6f * u, c, style = Stroke(3.2f * u))
        when (src) {
            io.github.janhurn9400.liquidy.design.TintSource.Device -> // phone: rounded rect with a camera dot
                drawRoundRect(Color.White, Offset(c.x - 5f * u, c.y - 8.5f * u), Size(10f * u, 17f * u), CornerRadius(2.5f * u)).also {
                    drawCircle(Color.Black.copy(alpha = 0.5f), 1.3f * u, Offset(c.x - 1.8f * u, c.y - 5.6f * u))
                }
            io.github.janhurn9400.liquidy.design.TintSource.Wallpaper -> for (k in 0 until 8) { // flower outline
                val a = Math.toRadians(k * 45.0); val d = 5.2f * u
                drawCircle(Color(0xFFB0B0B5), 3.6f * u, c + Offset((kotlin.math.cos(a) * d).toFloat(), (kotlin.math.sin(a) * d).toFloat()), style = Stroke(1.2f * u))
            }
            io.github.janhurn9400.liquidy.design.TintSource.Custom -> { // eyedropper: diagonal stem + bulb
                drawLine(Color.White, c + Offset(-7f * u, 7f * u), c + Offset(3f * u, -3f * u), 2.2f * u, StrokeCap.Round)
                drawLine(Color.White, c + Offset(3.5f * u, -3.5f * u), c + Offset(7f * u, -7f * u), 4.5f * u, StrokeCap.Round)
                drawLine(Color.White, c + Offset(1f * u, -6f * u), c + Offset(6f * u, -1f * u), 2f * u, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun LabelText(text: String, selected: Boolean) {
    BasicText(text, style = TextStyle(fontFamily = Inter, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 16.sp,
        color = if (selected) Color.White else Color.White.copy(alpha = UNSELECTED_ALPHA)))
}

@Composable
private fun Centered(width: Float, y: () -> Float, content: @Composable () -> Unit) {
    Layout(content = content) { ms, c ->
        val p = ms[0].measure(Constraints())
        layout(c.maxWidth, c.maxHeight) { p.place(((width * density - p.width) / 2).roundToInt(), (y() * density - p.height / 2f).roundToInt()) }
    }
}

/** Caption centred at (cx, y); when selected with a light glass capsule behind it (recording7 15.6 s). */
@Composable
private fun CenteredAt(cx: Float, y: () -> Float, selected: Boolean, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Layout(content = {
        Box(
            Modifier
                .height(CAPSULE_H.dp)
                .clip(CircleShape)
                .background(if (selected) CAPSULE_FILL else Color.Transparent)
                .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
                .padding(horizontal = CAPSULE_PAD.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }) { ms, c ->
        val p = ms[0].measure(Constraints())
        layout(c.maxWidth, c.maxHeight) { p.place((cx * density - p.width / 2f).roundToInt(), (y() * density - p.height / 2f).roundToInt()) }
    }
}

@Composable
private fun Slider(width: Float, y: () -> Float, track: Brush, value: Float, onChange: (Float) -> Unit) {
    val left = SLIDER_X - SHEET_SIDE
    val tw = width - 2 * left
    Canvas(
        Modifier
            .offset { IntOffset(0, ((y() - THUMB) * density).roundToInt()) }
            .size(Dp(width), Dp(THUMB * 2))
            .pointerInput(width) { detectTapGestures { onChange(((it.x / density - left) / tw).coerceIn(0f, 1f)) } }
            .pointerInput(width) { detectDragGestures { ch, _ -> onChange(((ch.position.x / density - left) / tw).coerceIn(0f, 1f)) } }
    ) {
        val l = left.dp.toPx(); val r = (left + tw).dp.toPx(); val cy = THUMB.dp.toPx(); val th = TRACK.dp.toPx()
        drawRoundRect(track, Offset(l, cy - th / 2), Size(r - l, th), CornerRadius(th / 2))
        val x = l + (r - l) * value
        drawCircle(IconAppearance.tint(1f), THUMB_R.dp.toPx(), Offset(x, cy))
        drawCircle(Color.White, THUMB_R.dp.toPx() - 1.5.dp.toPx(), Offset(x, cy), style = Stroke(3.dp.toPx()))
    }
}

// Dimensions [MEASURED] recording7 14.7 s (short) / 22.4 s (tall), 0.804 pt/px
private const val SHEET_TOP = 594.67f
private const val SHEET_TOP_TALL = 406f
private const val SHEET_BOTTOM = 864f
private const val SHEET_SIDE = 10f
private const val DUO_SHEET_W = 396f          // [MEASURED 10:40, 58 s: 626 px × 0.634]
private const val DUO_SHEET_BOTTOM_GAP = 17f  // [MEASURED 10:40, 62/66 s]
private const val SHEET_CORNER = 48f   // ≈ display corner [ESTIMATED]
private const val SHEET_BLUR = 14f     // icons behind only colour patches
private const val SHEET_TINT = 0.06f
private val STYLE_X = floatArrayOf(67.5f, 156f, 244.6f, 333f)
private const val PREVIEW = 61f
private const val SEG_PITCH = 72f // [MEASURED user iPhone 10-18: Light 129 · Dark 201 · Auto 273 pt]
private const val CAPSULE_H = 30f
private const val CAPSULE_PAD = 14f
private val CAPSULE_FILL = Color(0x33FFFFFF)
private const val UNSELECTED_ALPHA = 0.7f
private const val SLIDER_X = 38.6f
private const val HEADER_INSET = 27f  // [MEASURED 10-18] sun / size toggle centres from the sheet edges
private const val HEADER_HIT = 30f
private const val SOURCE_PITCH = 78f  // [MEASURED 10-18] colour source buttons, centre to centre
private const val SOURCE_D = 47f
private const val TRACK = 30f
private const val THUMB = 22f
private const val THUMB_R = 15f
private val HUES = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
