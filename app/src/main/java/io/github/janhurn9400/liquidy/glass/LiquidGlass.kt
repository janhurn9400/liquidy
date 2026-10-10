package io.github.janhurn9400.liquidy.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.janhurn9400.liquidy.design.DesignTokens
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.RoundedRectangularShape

/**
 * Aufgenommener Homescreen (Wallpaper + Icons) als Quelle für alle Glasflächen (Kyant backdrop, docs/GLASS.md).
 * Gesetzt in HomeScreen über Modifier.layerBackdrop.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Kontinuierliche (G2-)Ecken wie iOS — Kyant Shapes, von der Linse unterstützt. */
fun glassRect(cornerRadius: Dp): RoundedRectangularShape = RoundedRectangle(cornerRadius)
fun glassCapsule(): RoundedRectangularShape = Capsule()

/**
 * Liquid Glass (RESEARCH §2.8, docs/GLASS.md): Farbverstärkung → Blur → Linse (SDF-Refraktion am Rand + Dispersion),
 * Highlight-Licht (oben/unten hell, gemessen), dunkler Seitenrand (iOS 27), innerer + äußerer Schatten.
 *
 * @param thickness Apple: größere Elemente wirken wie dickeres Glas (stärkere Brechung, weichere Streuung).
 *                  Skaliert Brechungshöhe/-stärke; 1 = Dock.
 */
@Composable
fun LiquidGlass(
    shape: RoundedRectangularShape,
    modifier: Modifier = Modifier,
    thickness: Float = 1f,
    blur: Dp = DesignTokens.glassBlur.dp,
    whiteTint: Float = DesignTokens.glassWhiteTint.value,
    blackTint: Float = DesignTokens.glassTint.value,
    /** null = globale Einstellung; Duo-Teile setzen eigene Werte, ohne den normalen Modus zu verändern. */
    dispersion: Boolean? = null,
    rimAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) {
        // Fallback ohne aufgenommenen Hintergrund (z. B. Vorschau): einfache Fläche + Rand
        Box(modifier) { content(); GlassRim(shape, Modifier.matchParentSize()) }
        return
    }
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(saturation = DesignTokens.glassSaturation.value)
                val b = blur.toPx()
                if (b > 0.5f) blur(b)
                // Brechungszone: T-GLASS.refraction, höchstens Anteil des Eckenradius (Kyant: Höhe ≤ Radius).
                // NICHT mit der Größe skalieren — sonst haben kleine Knöpfe (Pill 30 pt) praktisch keine Linse.
                // Dicke (Apple: große Elemente = dickeres Glas) erhöht nur die Stärke.
                val corner = shape.corners(size, layoutDirection, this).let { minOf(it.topLeft, it.topRight, it.bottomLeft, it.bottomRight) }
                val height = DesignTokens.glassRefraction.value.dp.toPx().coerceAtMost(corner * DesignTokens.glassRefractionCornerShare.value)
                val amount = height * DesignTokens.glassRefractionAmount.value * thickness.coerceAtLeast(1f)
                if (height > 0.5f) lens(
                    refractionHeight = height,
                    refractionAmount = amount.coerceAtMost(size.minDimension),
                    depthEffect = DesignTokens.glassDepth.value > 0.5f,
                    chromaticAberration = dispersion ?: (DesignTokens.glassDispersion.value > 0.5f),
                )
            },
            highlight = {
                Highlight(
                    width = DesignTokens.glassRimWidth.dp,
                    alpha = rimAlpha ?: DesignTokens.glassRimHighlight.value,
                    // 90° = Licht von oben → Ober- und Unterkante hell [GEMESSEN] Pill/Dock IMG_3123
                    style = HighlightStyle.Default(angle = DesignTokens.glassLightAngle.value, falloff = DesignTokens.glassLightFalloff.value),
                )
            },
            shadow = {
                Shadow(
                    radius = DesignTokens.glassShadowRadius.dp * thickness,
                    color = Color.Black.copy(alpha = DesignTokens.glassShadowAlpha.value),
                )
            },
            innerShadow = {
                InnerShadow(
                    radius = DesignTokens.glassInnerShadowRadius.dp,
                    offset = DpOffset(0.dp, 0.dp),
                    color = Color.Black.copy(alpha = DesignTokens.glassInnerShadowAlpha.value),
                )
            },
            onDrawSurface = {
                if (blackTint > 0f) drawRect(Color.Black.copy(alpha = blackTint))
                if (whiteTint > 0f) drawRect(Color.White.copy(alpha = whiteTint))
            },
        )
    ) {
        content()
        // Dunkler Seitenrand (iOS 27, gemessen) — nur der dunkle Anteil, das helle Licht macht das Highlight
        GlassRim(shape, Modifier.matchParentSize(), lightAlpha = 0f)
    }
}
