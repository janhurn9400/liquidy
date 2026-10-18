package io.github.janhurn9400.liquidy.glass

import androidx.compose.foundation.background
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
 * Recorded home screen (wallpaper + icons) as the source for all glass surfaces (Kyant backdrop, docs/GLASS.md).
 * Set in HomeScreen via Modifier.layerBackdrop.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Continuous (G2) corners like iOS — Kyant Shapes, supported by the lens. */
fun glassRect(cornerRadius: Dp): RoundedRectangularShape = RoundedRectangle(cornerRadius)
fun glassCapsule(): RoundedRectangularShape = Capsule()

/**
 * Liquid Glass (RESEARCH §2.8, docs/GLASS.md): saturation boost → blur → lens (SDF refraction at the edge, without colour fringes),
 * highlight light (top/bottom bright, measured), dark side rim (iOS 27), inner + outer shadow.
 *
 * @param thickness Apple: larger elements look like thicker glass (stronger refraction, softer scattering).
 *                  Scales refraction height/amount; 1 = dock.
 */
@Composable
fun LiquidGlass(
    shape: RoundedRectangularShape,
    modifier: Modifier = Modifier,
    thickness: Float = 1f,
    blur: Dp = DesignTokens.glassBlur.dp,
    whiteTint: Float = DesignTokens.glassWhiteTint.value,
    blackTint: Float = DesignTokens.glassTint.value,
    rimAlpha: Float? = null,
    /** false = frosted glass: only blur + tint, no refraction and no light rim (e.g. selection sheets). */
    refraction: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val backdrop = LocalGlassBackdrop.current
    // "Clear ↔ Tinted" slider (launcher settings): more blur, dark glass becomes milky instead of dark
    val clarity = GlassClarity.amount
    val frost = GlassClarity.frostBlurDp(clarity)
    val tinted = GlassClarity.tintShare(clarity)
    val black = blackTint * (1f - tinted)
    val veil = DesignTokens.glassTintedVeil.value * tinted
    val grey = DesignTokens.glassTintedGrey.value
    if (io.github.janhurn9400.liquidy.tuning.DebugOptions.glassOff) {
        Box(modifier.background(io.github.janhurn9400.liquidy.tuning.DebugOptions.GLASS_OFF_FILL, shape), content = content)
        return
    }
    if (backdrop == null) {
        // Fallback without a recorded background (e.g. preview): simple area + rim
        Box(modifier) { content(); GlassRim(shape, Modifier.matchParentSize()) }
        return
    }
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(saturation = DesignTokens.glassSaturation.value)
                val b = maxOf(blur.value, frost).dp.toPx()
                if (b > 0.5f) blur(b)
                // Refraction zone: T-GLASS.refraction, at most a share of the corner radius (Kyant: height ≤ radius).
                // DON'T scale with size — otherwise small buttons (pill 30 pt) have practically no lens.
                // Thickness (Apple: large elements = thicker glass) only increases the amount.
                val corner = shape.corners(size, layoutDirection, this).let { minOf(it.topLeft, it.topRight, it.bottomLeft, it.bottomRight) }
                val height = DesignTokens.glassRefraction.value.dp.toPx().coerceAtMost(corner * DesignTokens.glassRefractionCornerShare.value)
                // tinted: no visible refraction anymore (user iPhone 10-14, App Library field) → fades out from the middle
                val amount = height * DesignTokens.glassRefractionAmount.value * thickness.coerceAtLeast(1f) * (1f - tinted)
                // no colour fringes (dispersion): Apple's Liquid Glass has none [user 10-14, real iPhone]
                if (refraction && height > 0.5f && amount > 0.5f) lens(
                    refractionHeight = height,
                    refractionAmount = amount.coerceAtMost(size.minDimension),
                    depthEffect = DesignTokens.glassDepth.value > 0.5f,
                    chromaticAberration = false,
                )
            },
            highlight = if (!refraction) null else ({
                Highlight(
                    width = DesignTokens.glassRimWidth.dp,
                    alpha = rimAlpha ?: DesignTokens.glassRimHighlight.value,
                    // 90° = light from the top → top and bottom edges bright [MEASURED] pill/dock IMG_3123
                    style = HighlightStyle.Default(angle = DesignTokens.glassLightAngle.value, falloff = DesignTokens.glassLightFalloff.value),
                )
            }),
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
                if (black > 0f) drawRect(Color.Black.copy(alpha = black))
                if (whiteTint > 0f) drawRect(Color.White.copy(alpha = whiteTint))
                if (veil > 0f) drawRect(Color(grey, grey, grey, veil))
            },
        )
    ) {
        content()
        // Dark side rim (iOS 27, measured) — only the dark part, the bright light is done by the highlight
        if (refraction) GlassRim(shape, Modifier.matchParentSize(), lightAlpha = 0f)
    }
}
