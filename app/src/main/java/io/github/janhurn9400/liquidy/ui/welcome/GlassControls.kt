package io.github.janhurn9400.liquidy.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassCapsule
import io.github.janhurn9400.liquidy.ui.Inter
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

/*
 * Liquid-Glass-Bedienelemente im Stil von iOS 26 (Setup-Assistent): Knopf wächst beim Drücken (Feder),
 * Schalter- und Slider-Knauf verwandeln sich beim Anfassen in eine Glaslinse, die die Spur darunter vergrößert.
 * Quelle der Linse ist jeweils die eigene Spur (eigene LayerBackdrop), damit die Brechung sichtbar ist.
 */

/** Blaue (oder klare) Glas-Kapsel, volle Breite, 52 pt — wie „Weiter“ im iOS-Setup. */
@Composable
fun GlassButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, prominent: Boolean = true) {
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    Box(
        modifier.fillMaxWidth().height(BUTTON_H.dp)
            .graphicsLayer { val s = 1f + PRESS_GROW * press.value; scaleX = s; scaleY = s }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scope.launch { press.animateTo(1f, spring(0.6f, 700f)) }
                    val up = tryAwaitRelease()
                    scope.launch { press.animateTo(0f, spring(0.5f, 300f)) }
                    if (up) click()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlass(glassCapsule(), Modifier.matchParentSize(), whiteTint = if (prominent) 0f else 0.55f, blackTint = 0f) {
            Box(Modifier.matchParentSize().clip(CircleShape).drawBehind {
                if (prominent) drawRect(ACCENT.copy(alpha = 0.94f))
                // beim Drücken aufhellen (das Licht macht die Glaskante, kein aufgemalter Glanz)
                drawRect(Color.White.copy(alpha = 0.16f * press.value))
            })
        }
        BasicText(label, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
            color = if (prominent) Color.White else ACCENT))
    }
}

/** Runder Glasknopf mit Zurück-Pfeil (oben links auf Setup-Seiten). */
@Composable
fun GlassBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    Box(
        modifier.size(44.dp)
            .graphicsLayer { val s = 1f + 0.12f * press.value; scaleX = s; scaleY = s }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scope.launch { press.animateTo(1f, spring(0.6f, 700f)) }
                    val up = tryAwaitRelease()
                    scope.launch { press.animateTo(0f, spring(0.5f, 300f)) }
                    if (up) click()
                })
            },
    ) {
        LiquidGlass(glassCapsule(), Modifier.matchParentSize(), whiteTint = 0.6f, blackTint = 0f)
        Canvas(Modifier.matchParentSize()) {
            val s = size.width; val w = 2.4.dp.toPx()
            drawLine(LABEL, Offset(s * 0.56f, s * 0.32f), Offset(s * 0.4f, s * 0.5f), w, StrokeCap.Round)
            drawLine(LABEL, Offset(s * 0.4f, s * 0.5f), Offset(s * 0.56f, s * 0.68f), w, StrokeCap.Round)
        }
    }
}

/**
 * iOS-26-Schalter: grüne/graue Spur, weißer Knauf wird beim Drücken zur vergrößernden Glaslinse.
 * Tippen schaltet um; Ziehen nimmt den Knauf mit, beim Loslassen rastet er auf der näheren Seite ein.
 */
@Composable
fun GlassToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(checked) { if (!dragging) pos.animateTo(if (checked) 1f else 0f, spring(0.72f, 420f)) }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val change by rememberUpdatedState(onCheckedChange)
    val cur by rememberUpdatedState(checked)
    val track = rememberLayerBackdrop()
    val density = LocalDensity.current
    val travel = with(density) { (TOGGLE_W - THUMB_W - 2 * THUMB_PAD).dp.toPx() }
    Box(
        modifier.size(TOGGLE_W.dp, TOGGLE_H.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                scope.launch { press.animateTo(1f, spring(0.62f, 600f)) }
                val start = pos.value
                var moved = 0f
                drag(down.id) {
                    moved += it.positionChange().x
                    if (!dragging && kotlin.math.abs(moved) > viewConfiguration.touchSlop) dragging = true
                    if (dragging) scope.launch { pos.snapTo((start + moved / travel).coerceIn(0f, 1f)) }
                    it.consume()
                }
                scope.launch { press.animateTo(0f, spring(0.55f, 260f)) }
                val target = if (dragging) pos.value > 0.5f else !cur
                dragging = false
                scope.launch { pos.animateTo(if (target) 1f else 0f, spring(0.72f, 420f)) }
                if (target != cur) change(target)
            }
        },
    ) {
        Box(Modifier.matchParentSize().layerBackdrop(track).clip(CircleShape).drawBehind {
            drawRect(lerp(TRACK_OFF, GREEN, pos.value))
        })
        GlassThumb(
            track, press,
            Modifier.align(Alignment.CenterStart)
                .offset { IntOffset((with(density) { THUMB_PAD.dp.toPx() } + travel * pos.value).toInt(), 0) }
                .size(THUMB_W.dp, THUMB_H.dp),
        )
    }
}

/**
 * iOS-26-Slider: dünne Spur (gefüllt bis zum Wert oder eigener Verlauf), Knauf wird beim Ziehen zur Glaslinse.
 * [value] 0…1.
 */
@Composable
fun GlassSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, trackBrush: Brush? = null) {
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val change by rememberUpdatedState(onValueChange)
    val track = rememberLayerBackdrop()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(SLIDER_H.dp)) {
        val thumbW = with(density) { THUMB_W.dp.toPx() }
        val w = constraints.maxWidth.toFloat()
        fun set(x: Float) = change(((x - thumbW / 2) / (w - thumbW)).coerceIn(0f, 1f))
        Box(
            Modifier.matchParentSize().pointerInput(w) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    scope.launch { press.animateTo(1f, spring(0.62f, 600f)) }
                    set(down.position.x)
                    drag(down.id) { set(it.position.x); it.consume() }
                    scope.launch { press.animateTo(0f, spring(0.55f, 260f)) }
                }
            }
        ) {
            // Spur in voller Höhe aufnehmen (oben/unten transparent) — die Linse zeigt sie vergrößert
            Box(Modifier.matchParentSize().layerBackdrop(track).drawBehind {
                val th = SLIDER_TRACK.dp.toPx(); val y = (size.height - th) / 2; val r = CornerRadius(th / 2)
                if (trackBrush != null) drawRoundRect(trackBrush, Offset(0f, y), Size(size.width, th), r)
                else {
                    drawRoundRect(TRACK_OFF, Offset(0f, y), Size(size.width, th), r)
                    drawRoundRect(ACCENT, Offset(0f, y), Size(thumbW / 2 + value * (size.width - thumbW), th), r)
                }
            })
            GlassThumb(
                track, press,
                Modifier.align(Alignment.CenterStart).offset { IntOffset((value * (w - thumbW)).toInt(), 0) }.size(THUMB_W.dp, THUMB_H.dp),
            )
        }
    }
}

/** Knauf: ruhend weiße Kapsel mit Schatten; beim Anfassen wächst er und wird klar (Linse über der Spur). */
@Composable
private fun GlassThumb(track: com.kyant.backdrop.Backdrop, press: Animatable<Float, *>, modifier: Modifier) {
    Box(
        modifier.drawBackdrop(
            backdrop = track,
            shape = { Capsule() },
            effects = {
                val p = press.value
                if (p > 0.02f) lens(refractionHeight = 10.dp.toPx() * p, refractionAmount = 18.dp.toPx() * p, chromaticAberration = false)
            },
            highlight = {
                Highlight(width = 1.2.dp, alpha = 0.25f + 0.6f * press.value,
                    style = HighlightStyle.Default(angle = DesignTokens.glassLightAngle.value, falloff = DesignTokens.glassLightFalloff.value))
            },
            shadow = { Shadow(radius = (4 + 8 * press.value).dp, color = Color.Black.copy(alpha = 0.16f)) },
            layerBlock = { val s = 1f + THUMB_GROW * press.value; scaleX = s; scaleY = s },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - 0.88f * press.value)) },
        )
    )
}

internal val ACCENT = Color(0xFF007AFF)
internal val LABEL = Color(0xFF000000)
internal val SECONDARY = Color(0x993C3C43) // secondaryLabel hell
private val GREEN = Color(0xFF34C759)
private val TRACK_OFF = Color(0xFFE3E3E8)
private const val BUTTON_H = 50
private const val PRESS_GROW = 0.04f   // Knöpfe wachsen beim Drücken (nicht schrumpfen)
private const val TOGGLE_W = 64f
private const val TOGGLE_H = 28f
private const val THUMB_W = 38f
private const val THUMB_H = 24f
private const val THUMB_PAD = 2f
private const val THUMB_GROW = 0.45f
private const val SLIDER_H = 36
private const val SLIDER_TRACK = 6
