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
 * „Customise“-Blatt (recording7 13–30 s, T-CUST): Liquid-Glass-Blatt unten mit vier Stil-Vorschauen
 * (Default · Dark · Clear · Tinted), darunter Always|Auto bzw. Light|Dark|Auto; Tinted zusätzlich Farbton + Sättigung.
 * Liegt in HomeScreen (Glas-Quelle = Homescreen); Tippen außerhalb schließt.
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
        // Tippen außerhalb schließt
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { model.closeCustomise() } })
        val side = SHEET_SIDE
        // Duo (auf-/zugeklappt): Blatt höchstens 396 pt breit, waagerecht mittig, Unterkante 17 pt über dem Rand —
        // die iPhone-Maße (Unterkante 864 pt) passen nicht auf 672/678 pt hohe Displays [GEMESSEN 10:40, 62/66 s]
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
            // Inhalt, Positionen relativ zur Blatt-Oberkante (gemessen am kurzen bzw. hohen Blatt)
            fun y(short: Float, tallY: Float) = (short - SHEET_TOP) + ((tallY - SHEET_TOP_TALL) - (short - SHEET_TOP)) * tall.value // relativ zur Oberkante
            Centered(width, { y(629f, 441f) }) {
                BasicText(stringResource(R.string.customise), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White))
            }
            // Stil-Vorschauen
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
            // Tinted: Farbton + Sättigung
            if (tall.value > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = tall.value }) {
                    Slider(width, { y(0f, 643f) }, Brush.horizontalGradient(HUES), IconAppearance.hue / 360f) { IconAppearance.hue = it * 360f; save() }
                    Slider(width, { y(0f, 697f) }, Brush.horizontalGradient(listOf(Color.White, IconAppearance.tint(1f, 1f / IconAppearance.saturation.coerceAtLeast(0.01f)))),
                        IconAppearance.saturation) { IconAppearance.saturation = it; save() }
                }
            }
            // Hell/Dunkel: Dark → Always | Auto, sonst Light | Dark | Auto
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

/** Beschriftung zentriert bei (cx, y); ausgewählt mit heller Glas-Kapsel dahinter (recording7 15,6 s). */
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
            .pointerInput(Unit) { detectTapGestures { onChange(((it.x / density - left) / tw).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { ch, _ -> onChange(((ch.position.x / density - left) / tw).coerceIn(0f, 1f)) } }
    ) {
        val l = left.dp.toPx(); val r = (left + tw).dp.toPx(); val cy = THUMB.dp.toPx(); val th = TRACK.dp.toPx()
        drawRoundRect(track, Offset(l, cy - th / 2), Size(r - l, th), CornerRadius(th / 2))
        val x = l + (r - l) * value
        drawCircle(IconAppearance.tint(1f), THUMB_R.dp.toPx(), Offset(x, cy))
        drawCircle(Color.White, THUMB_R.dp.toPx() - 1.5.dp.toPx(), Offset(x, cy), style = Stroke(3.dp.toPx()))
    }
}

// Maße [GEMESSEN] recording7 14,7 s (kurz) / 22,4 s (hoch), 0,804 pt/px
private const val SHEET_TOP = 594.67f
private const val SHEET_TOP_TALL = 406f
private const val SHEET_BOTTOM = 864f
private const val SHEET_SIDE = 10f
private const val DUO_SHEET_W = 396f          // [GEMESSEN 10:40, 58 s: 626 px × 0,634]
private const val DUO_SHEET_BOTTOM_GAP = 17f  // [GEMESSEN 10:40, 62/66 s]
private const val SHEET_CORNER = 48f   // ≈ Display-Ecke [GESCHÄTZT]
private const val SHEET_BLUR = 14f     // Icons dahinter nur noch Farbflecken
private const val SHEET_TINT = 0.06f
private val STYLE_X = floatArrayOf(67.5f, 156f, 244.6f, 333f)
private const val PREVIEW = 61f
private const val SEG_PITCH = 90f
private const val CAPSULE_H = 30f
private const val CAPSULE_PAD = 14f
private val CAPSULE_FILL = Color(0x33FFFFFF)
private const val UNSELECTED_ALPHA = 0.7f
private const val SLIDER_X = 38.6f
private const val TRACK = 30f
private const val THUMB = 22f
private const val THUMB_R = 15f
private val HUES = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
