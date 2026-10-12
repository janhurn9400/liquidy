package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassCapsule
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlin.math.roundToInt

/** Zwei Glas-Kapseln oben (T-EDIT-BTN): links Pinsel („Anpassen“), rechts Häkchen („Fertig“). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditButtons(geo: HomeGeometry, wallpaper: ImageBitmap, density: Float, showLeft: Boolean = true, leftPlus: Boolean = false, scale: () -> Float) {
    // Die Buttons sitzen dort, wo sonst die Statusleiste ist: erst einblenden, wenn sie wirklich weg ist
    // (folgt der System-Animation; gelesen in der Zeichenphase → kein Neuaufbau je Frame)
    val statusBar = WindowInsets.statusBars
    val statusBarFull = WindowInsets.statusBarsIgnoringVisibility
    for (left in listOf(true, false)) {
        if (left && !showLeft) continue
        val r = geo.editButtonRect(left)
        LiquidGlass(
            shape = glassCapsule(),
            thickness = DesignTokens.editButtonHeight.value / DesignTokens.dockHeight.value,
            // Milchglas wie IMG_3124: stark verwischt und hell, damit das dunkle Symbol lesbar bleibt
            // Duo [Video 4, 1 s]: fast klares Glas (Wallpaper-Farbe scheint durch), heller Rand, weiße Symbole
            blur = if (geo.duo) DUO_BTN_BLUR.dp else FROST_BLUR.dp,
            whiteTint = if (geo.duo) DUO_BTN_WHITE else if (DesignTokens.wallpaperIsDark) FROST_WHITE_DARK else FROST_WHITE,
            blackTint = 0f,
            dispersion = if (geo.duo) false else null,
            rimAlpha = if (geo.duo) DUO_BTN_RIM else null,
            modifier = Modifier
                .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                .size(Dp(r.width / density), Dp(r.height / density))
                .graphicsLayer {
                    val s = scale(); scaleX = s; scaleY = s
                    val full = statusBarFull.getTop(this).coerceAtLeast(1)
                    val gone = 1f - statusBar.getTop(this).toFloat() / full
                    alpha = s.coerceIn(0f, 1f) * gone.coerceIn(0f, 1f)
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val gs = if (geo.duo) DUO_GLYPH.dp else DesignTokens.editGlyphSize.dp
                val gc = if (geo.duo) Color.White else DesignTokens.glyphColor
                val sw = if (geo.duo) DUO_GLYPH_STROKE else 0.14f
                if (left) { if (leftPlus) PlusGlyph(gs, gc) else BrushGlyph(gs, gc, sw) } else CheckGlyph(gs, gc, sw)
            }
        }
    }
}

/** Häkchen als Vektor (kein SF Symbol); Proportionen nach IMG_3124 [GESCHÄTZT]. */
@Composable
private fun CheckGlyph(size: Dp, color: Color = DesignTokens.glyphColor, stroke: Float = 0.14f) = Canvas(Modifier.size(size)) {
    val s = this.size.width
    val path = Path().apply {
        moveTo(0.08f * s, 0.55f * s)
        lineTo(0.38f * s, 0.88f * s)
        lineTo(0.92f * s, 0.12f * s)
    }
    drawPath(path, color, style = Stroke(width = stroke * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Plus (Stapel bearbeiten: Widget hinzufügen). */
@Composable
private fun PlusGlyph(size: Dp, color: Color = DesignTokens.glyphColor) = Canvas(Modifier.size(size)) {
    val s = this.size.width; val w = 0.12f * s
    drawLine(color, Offset(s / 2, 0.08f * s), Offset(s / 2, 0.92f * s), w, StrokeCap.Round)
    drawLine(color, Offset(0.08f * s, s / 2), Offset(0.92f * s, s / 2), w, StrokeCap.Round)
}

/** Pinsel als Vektor (kein SF Symbol) [GESCHÄTZT]. */
@Composable
internal fun BrushGlyph(size: Dp, c: Color = DesignTokens.glyphColor, stroke: Float = 0.14f) = Canvas(Modifier.size(size)) {
    val s = this.size.width
    drawLine(c, Offset(0.85f * s, 0.1f * s), Offset(0.45f * s, 0.55f * s), stroke * s, StrokeCap.Round)
    val tip = Path().apply {
        moveTo(0.42f * s, 0.5f * s)
        cubicTo(0.2f * s, 0.5f * s, 0.25f * s, 0.8f * s, 0.08f * s, 0.92f * s)
        cubicTo(0.35f * s, 0.95f * s, 0.55f * s, 0.85f * s, 0.52f * s, 0.6f * s)
        close()
    }
    drawPath(tip, c)
}

/**
 * iOS-Dialog „„App“ entfernen?“ (T-ALERT, M-ALERT) mit gestapelten Kapsel-Buttons im iOS-26/27-Stil.
 * „App löschen“ öffnet den System-Deinstallationsdialog (Android verlangt die Bestätigung des Systems).
 */
@Composable
fun RemoveAppAlert(
    appName: String,
    canDelete: Boolean,
    wallpaper: ImageBitmap,
    onDelete: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onCancel: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.alert.spec()) }
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = DesignTokens.alertScrim.value))
            .clickable(interactionSource = noRipple, indication = null) { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlass(
            shape = glassRect(DesignTokens.alertCorner.dp),
            thickness = DesignTokens.glassThicknessLarge.value,
            blur = DesignTokens.alertBlur.dp,
            whiteTint = DesignTokens.alertTint.value,
            blackTint = 0f,
            modifier = Modifier
                .width(DesignTokens.alertWidth.dp)
                .graphicsLayer {
                    // iOS-Alerts zoomen leicht von größer herein [GESCHÄTZT]
                    val s = 1f + (1f - appear.value) * ALERT_START_SCALE_DELTA
                    scaleX = s; scaleY = s
                }
                .clickable(interactionSource = noRipple, indication = null) { },
        ) {
            Column(
                Modifier.padding(DesignTokens.alertPadding.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.alertButtonGap.dp),
            ) {
                BasicText(
                    stringResource(R.string.remove_title, appName),
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.alertTitleSize.value.sp,
                        color = DesignTokens.alertText, textAlign = TextAlign.Center),
                )
                BasicText(
                    stringResource(R.string.remove_message),
                    style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.alertBodySize.value.sp,
                        color = DesignTokens.alertSecondaryText, textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(DesignTokens.alertButtonGap.dp))
                if (canDelete) AlertButton(stringResource(R.string.delete_app), DesignTokens.destructive, false, onDelete)
                AlertButton(stringResource(R.string.remove_from_home), DesignTokens.alertText, false, onRemoveFromHome)
                AlertButton(stringResource(R.string.cancel), DesignTokens.alertText, true, onCancel)
            }
        }
    }
}

private const val ALERT_START_SCALE_DELTA = 0.1f // [GESCHÄTZT]

/** iOS-Abfrage mit zwei Kapseln nebeneinander (Abbrechen | Entfernen, rot) — Aufnahme 10-08 10:21, Bild 2010. */
@Composable
fun TwoButtonAlert(title: String, message: String, confirm: String, cancel: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.alert.spec()) }
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = DesignTokens.alertScrim.value))
            .clickable(interactionSource = noRipple, indication = null) { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlass(
            shape = glassRect(DesignTokens.alertCorner.dp), thickness = DesignTokens.glassThicknessLarge.value,
            blur = DesignTokens.alertBlur.dp, whiteTint = DesignTokens.alertTint.value, blackTint = 0f,
            modifier = Modifier.width(DesignTokens.alertWidth.dp)
                .graphicsLayer { val sc = 1f + (1f - appear.value) * ALERT_START_SCALE_DELTA; scaleX = sc; scaleY = sc }
                .clickable(interactionSource = noRipple, indication = null) { },
        ) {
            Column(Modifier.padding(DesignTokens.alertPadding.dp), verticalArrangement = Arrangement.spacedBy(DesignTokens.alertButtonGap.dp)) {
                BasicText(title, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.alertTitleSize.value.sp, color = DesignTokens.alertText))
                BasicText(message, style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.alertBodySize.value.sp, color = DesignTokens.alertSecondaryText))
                Spacer(Modifier.height(DesignTokens.alertButtonGap.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.alertButtonGap.dp)) {
                    Box(Modifier.weight(1f)) { AlertButton(cancel, DesignTokens.alertText, false, onCancel) }
                    Box(Modifier.weight(1f)) { AlertButton(confirm, DesignTokens.destructive, false, onConfirm) }
                }
            }
        }
    }
}

@Composable
private fun AlertButton(text: String, color: Color, bold: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DesignTokens.alertButtonHeight.dp)
            .background(DesignTokens.alertButtonFill, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.alertTitleSize.value.sp, color = color,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
        )
    }
}

// Duo-Bearbeiten-Knöpfe [GEMESSEN Video 4, 1 s]: Häkchen ≈ 17 × 13 pt, Strich ≈ 1,5 pt, weiß
internal const val DUO_GLYPH = 18f
internal const val DUO_GLYPH_STROKE = 0.085f
internal const val DUO_BTN_BLUR = 6f
internal const val DUO_BTN_WHITE = 0.05f
internal const val DUO_BTN_RIM = 0.65f
