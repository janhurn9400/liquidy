package io.github.janhurn9400.liquidy.ui.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.GlassRim
import io.github.janhurn9400.liquidy.glass.WallpaperRenderer
import io.github.janhurn9400.liquidy.glass.WallpaperSettings
import io.github.janhurn9400.liquidy.ui.Inter
import kotlin.math.roundToInt

/**
 * „Choose Colour“-Blatt (recording6 16,8–24,5 s, T-WPE-SHEET): 6×3 Farbkreise, darunter ein Regler (hell ↔ satt).
 * Der Farbkreis-Knopf links oben schaltet den Regler auf ein freies Farbspektrum um.
 */
@Composable
internal fun ColourSheet(backdrop: com.kyant.backdrop.Backdrop, draft: WallpaperSettings, progress: () -> Float, onChange: (WallpaperSettings) -> Unit) {
    var hueMode by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(draft)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val screenH = constraints.maxHeight / density
        val side = DesignTokens.wpeSheetSide.value
        // Duo: Blatt in iPhone-Breite mittig, Höhe wie beim iPhone vom unteren Rand aus (sonst unter dem Display)
        val duo = io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle
        val screenW = constraints.maxWidth / density
        val top = if (duo) screenH - (874f - DesignTokens.wpeSheetTop.value) else DesignTokens.wpeSheetTop.value
        val width = if (duo) minOf(screenW, 402f) - 2 * side else screenW - 2 * side
        val left = (screenW - width) / 2
        Box(
            Modifier
                .offset { IntOffset((left * density).roundToInt(), ((top + (1f - progress()) * (screenH - top)) * density).roundToInt()) }
                .size(Dp(width), Dp(screenH - top + SHEET_OVERHANG))
                // Tippen ins Blatt nicht an die Vorschau durchreichen
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // Liquid Glass: verwischte, abgedunkelte Vorschau scheint durch (recording6 17,5 s)
            androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides backdrop) {
                io.github.janhurn9400.liquidy.glass.LiquidGlass(
                    shape = io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.wpeSheetCorner.dp),
                    modifier = Modifier.fillMaxSize(),
                    blur = SHEET_BLUR.dp,
                    blackTint = SHEET_DARK,
                    whiteTint = 0f,
                )
            }
            // Titel
            Layout(content = {
                BasicText(stringResource(R.string.choose_colour), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                    fontSize = TITLE_SIZE.sp, color = Color.White))
            }) { ms, c ->
                val t = ms[0].measure(Constraints())
                layout(c.maxWidth, c.maxHeight) { t.place((c.maxWidth - t.width) / 2, DesignTokens.wpeSheetTitleY.dp.roundToPx() - t.height / 2) }
            }
            // Farbkreis-Knopf (Spektrum)
            Canvas(
                Modifier
                    .offset { IntOffset(((WHEEL_X - side - WHEEL_D / 2) * density).roundToInt(), ((DesignTokens.wpeSheetTitleY.value - WHEEL_D / 2) * density).roundToInt()) }
                    .size(Dp(WHEEL_D))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { hueMode = !hueMode }
            ) {
                val r = size.minDimension / 2
                drawCircle(Brush.sweepGradient(HUES), r - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
                drawCircle(if (hueMode) Color.White else Color(0xFF5A5A5E), r * 0.62f)
            }
            // 6×3 Farben
            DesignTokens.wallpaperSwatches.forEachIndexed { i, c ->
                val cx = DesignTokens.wpeSwatchX.value + DesignTokens.wpeSwatchPitch.value * (i % 6) - side
                val cy = DesignTokens.wpeSwatchTop.value + DesignTokens.wpeSwatchPitch.value * (i / 6)
                val d = DesignTokens.wpeSwatch.value
                val selected = current.color == c
                Canvas(
                    Modifier
                        .offset { IntOffset(((cx - d / 2) * density).roundToInt(), ((cy - d / 2) * density).roundToInt()) }
                        .size(Dp(d))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            hueMode = false
                            onChange(current.copy(color = c, tone = WallpaperSettings.DEFAULT_TONE))
                        }
                ) {
                    val r = size.minDimension / 2
                    drawCircle(Color(c), r)
                    // Auswahl: weißer Ring innen (recording6 20,3 s)
                    if (selected) drawCircle(if (c == WHITE) Color.Black else Color.White, r * 0.78f, style = Stroke(2.dp.toPx()))
                }
            }
            // Regler: hell ↔ satt bzw. Spektrum
            val tx = DesignTokens.wpeSliderX.value - side
            val tw = width - 2 * tx
            val sy = DesignTokens.wpeSliderY.value
            val trackH = DesignTokens.wpeSliderH.value
            val thumb = DesignTokens.wpeThumb.value
            val fraction = if (hueMode) hueOf(current.color) / 360f else current.tone
            fun set(xPx: Float) {
                val f = ((xPx / density - tx) / tw).coerceIn(0f, 1f)
                onChange(
                    if (hueMode) current.copy(color = android.graphics.Color.HSVToColor(floatArrayOf(f * 360f, SPECTRUM_SAT, 1f)), tone = WallpaperSettings.DEFAULT_TONE)
                    else current.copy(tone = f)
                )
            }
            // nur ein Band um den Regler (sonst schluckt er die Farbkreise)
            Canvas(
                Modifier
                    .offset { IntOffset(0, ((sy - thumb) * density).roundToInt()) }
                    .size(Dp(width), Dp(thumb * 2))
                    .pointerInput(hueMode) {
                        detectTapGestures { p -> set(p.x) }
                    }
                    .pointerInput(hueMode) {
                        detectDragGestures { change, _ -> set(change.position.x) }
                    }
            ) {
                val left = tx.dp.toPx(); val right = (tx + tw).dp.toPx()
                val cy = thumb.dp.toPx(); val th = trackH.dp.toPx()
                val brush = if (hueMode) Brush.horizontalGradient(HUES, left, right)
                else Brush.horizontalGradient(listOf(
                    Color(WallpaperRenderer.toned(current.color, 0f)),
                    Color(WallpaperRenderer.toned(current.color, 0.5f)),
                    Color(WallpaperRenderer.toned(current.color, 1f)),
                ), left, right)
                drawRoundRect(brush, Offset(left, cy - th / 2), Size(right - left, th), CornerRadius(th / 2))
                val x = left + (right - left) * fraction
                val tr = thumb.dp.toPx() / 2
                drawCircle(Color(WallpaperRenderer.toned(current.color, current.tone)), tr, Offset(x, cy))
                drawCircle(Color.White, tr - 1.5.dp.toPx(), Offset(x, cy), style = Stroke(3.dp.toPx()))
            }
        }
    }
}

private fun hueOf(c: Int) = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }[0]

private const val SHEET_BLUR = 24f  // Vorschau hinter dem Blatt nur noch als Farbflecken [GEMESSEN grob]
private const val SHEET_DARK = 0.62f // Blatt ≈ #2A2A2D über Schwarz/Vorschau
private val HUES = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
private const val WHITE = 0xFFFFFFFF.toInt()
private const val TITLE_SIZE = 17f
private const val WHEEL_X = 42.67f   // pt, Mitte des Farbkreis-Knopfs (Bildschirm)
private const val WHEEL_D = 32f
private const val SPECTRUM_SAT = 0.65f
private const val SHEET_OVERHANG = 40f // Blatt reicht unter den Bildschirmrand (Ecken unten unsichtbar)
