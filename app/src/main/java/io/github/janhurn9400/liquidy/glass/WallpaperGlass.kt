package io.github.janhurn9400.liquidy.glass

import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangularShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Fast Liquid Glass for glass that lies directly on the wallpaper (App Library tiles …) — looks the same as [LiquidGlass]
 * with a [WallpaperBackdrop], but without its per-frame cost (docs/GLASS.md › "Fast wallpaper glass").
 *
 * [LiquidGlass] (Kyant drawBackdrop) re-records the backdrop into its own offscreen layer and rebuilds the effect chain
 * (saturation → blur → lens RenderEffect) every frame the element moves, plus an offscreen layer for the clipped
 * content — ~10 tiles = ~20 offscreen passes per frame, measured 31 % janky frames on a Snapdragon 780G (10-18).
 * Here the wallpaper is a texture and ONE AGSL shader per tile does saturation + Kyant's lens (same code) while sampling
 * the wallpaper at the tile's current screen position; per frame only its uniforms change. The (static) shadow and light
 * rim are drawn by separate nodes that are not re-recorded while the tile moves. No offscreen layers at all.
 *
 * The small glass blur (T-GLASS.blur, 0.6 dp) is below what bilinear sampling smooths anyway; larger blur ("Tinted"
 * glass slider) uses a precomputed blurred wallpaper.
 */
@Composable
fun WallpaperGlass(
    wallpaper: ImageBitmap,
    shape: RoundedRectangularShape,
    modifier: Modifier = Modifier,
    thickness: Float = 1f,
    blur: Dp = DesignTokens.glassBlur.dp,
    whiteTint: Float = DesignTokens.glassWhiteTint.value,
    blackTint: Float = DesignTokens.glassTint.value,
    rimAlpha: Float? = null,
    /** false = frosted glass: no lens and no light rims (as in [LiquidGlass]). */
    refraction: Boolean = true,
    /** Blurred wallpaper blended in by [softAmount] (Today view/App Library); default = [LocalWallpaperBlur]. */
    soft: ImageBitmap? = LocalWallpaperBlur.current.blurred,
    softAmount: () -> Float = LocalWallpaperBlur.current.amount,
    content: @Composable BoxScope.() -> Unit = {},
) {
    if (io.github.janhurn9400.liquidy.tuning.DebugOptions.glassOff) {
        LiquidGlass(shape, modifier, content = content) // flat stand-in
        return
    }
    val clarity = GlassClarity.amount
    val tinted = GlassClarity.tintShare(clarity)
    val black = blackTint * (1f - tinted)
    val veil = DesignTokens.glassTintedVeil.value * tinted
    val grey = DesignTokens.glassTintedGrey.value
    val glass = remember { FastGlassState() }

    // Frosted ("Tinted" slider): blurred wallpaper, computed once per radius and shared by all tiles
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val blurPx = maxOf(blur.value, GlassClarity.frostBlurDp(clarity)) * density
    val frostKey = if (blurPx > SHARP_BLUR_PX) (blurPx / FROST_STEP_PX).roundToInt() * FROST_STEP_PX else 0f
    val frosted by produceState(FrostCache.get(wallpaper, frostKey), wallpaper, frostKey) {
        if (frostKey > 0f && value == null) value = FrostCache.compute(wallpaper, frostKey)
    }

    Box(modifier) {
        // 1 · shadow outside the shape: a blurred ROUNDED RECT — Skia draws that analytically on the GPU. A blurred free
        // path was rasterised on the CPU and uploaded again whenever the size changed (folder panel: every frame)
        Spacer(Modifier.matchParentSize().drawBehind { drawGlassShadow(shape, thickness) })
        // 2 · glass fill: the only part that follows the position (reads [FastGlassState.coords] while drawing)
        Spacer(
            Modifier
                .matchParentSize()
                .onGloballyPositioned { glass.coords = it }
                // own render layer: the fill is redrawn every frame the element moves (new wallpaper position) — without
                // this, that invalidation re-recorded the WHOLE parent layer (home page: all icons, labels, widgets) on
                // the main thread every frame, e.g. while pulling down search
                .graphicsLayer()
                .drawBehind {
                    val c = glass.coords ?: return@drawBehind
                    if (!c.isAttached) return@drawBehind
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val shader = glass.shader(wallpaper, frosted ?: wallpaper, soft, softAmount())
                    glass.setUniforms(this, c, shape, thickness, tinted, refraction)
                    // tints and the light rim are computed in the same shader (one draw, no masks, no layers)
                    glass.setSurface(this, black, whiteTint, veil, grey, if (refraction) rimAlpha ?: DesignTokens.glassRimHighlight.value else 0f)
                    drawOutline(outline, ShaderBrush(shader))
                }
        )
        content()
        // 4 · dark side rim (iOS 27, measured) — same as LiquidGlass
        if (refraction) GlassRim(shape, Modifier.matchParentSize(), lightAlpha = 0f)
    }
}

/**
 * Shaders shared by ALL glass elements (compiled once — compiling per tile cost frames when the App Library appeared).
 * Safe because drawing happens on the main thread one after another and a RuntimeShader is snapshotted when it is
 * recorded (setting a uniform discards the native instance).
 */
private object SharedGlassShaders {
    val fill by lazy { RuntimeShader(FILL_SHADER) }
    val highlight by lazy { RuntimeShader(HIGHLIGHT_SHADER) }
    var inputs: Triple<ImageBitmap, ImageBitmap, ImageBitmap?>? = null
}

/** Per-element state + reused objects (nothing is allocated per frame). */
private class FastGlassState {
    var coords: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())
    private val fill get() = SharedGlassShaders.fill
    val highlight get() = SharedGlassShaders.highlight
    private val m = Matrix()
    private val m3 = FloatArray(9)

    fun shader(wallpaper: ImageBitmap, base: ImageBitmap, soft: ImageBitmap?, softAmount: Float): RuntimeShader {
        val key = Triple(wallpaper, base, soft)
        if (SharedGlassShaders.inputs != key) {
            SharedGlassShaders.inputs = key
            fill.setInputShader("wall", bitmapShader(base))
            fill.setInputShader("soft", bitmapShader(soft ?: base))
        }
        fill.setFloatUniform("softMix", if (soft != null) softAmount.coerceIn(0f, 1f) else 0f)
        return fill
    }

    /** Lens values exactly as in [LiquidGlass]; local → screen mapping of the wallpaper as in [WallpaperBackdrop]. */
    fun setUniforms(scope: DrawScope, c: LayoutCoordinates, shape: RoundedRectangularShape, thickness: Float, tinted: Float, refraction: Boolean) = with(scope) {
        m.reset()
        c.findRootCoordinates().transformFrom(c, m) // local → root (= wallpaper pixels)
        val v = m.values
        m3[0] = v[0]; m3[1] = v[1]; m3[2] = v[3]
        m3[3] = v[4]; m3[4] = v[5]; m3[5] = v[7]
        m3[6] = v[12]; m3[7] = v[13]; m3[8] = v[15]
        fill.setFloatUniform("toRoot", m3)
        fill.setFloatUniform("size", size.width, size.height)
        val k = shape.corners(size, layoutDirection, this)
        fill.setFloatUniform("cornerRadii", k.topLeft, k.topRight, k.bottomRight, k.bottomLeft)
        fill.setFloatUniform("saturation", DesignTokens.glassSaturation.value)
        val corner = minOf(k.topLeft, k.topRight, k.bottomLeft, k.bottomRight)
        val height = DesignTokens.glassRefraction.value.dp.toPx().coerceAtMost(corner * DesignTokens.glassRefractionCornerShare.value)
        val amount = (height * DesignTokens.glassRefractionAmount.value * thickness.coerceAtLeast(1f) * (1f - tinted)).coerceAtMost(size.minDimension)
        val on = refraction && height > 0.5f && amount > 0.5f
        fill.setFloatUniform("lensOn", if (on) 1f else 0f)
        fill.setFloatUniform("refractionHeight", height.coerceAtLeast(1f))
        fill.setFloatUniform("refractionAmount", -amount) // Kyant passes it negated (samples inwards)
        fill.setFloatUniform("depthEffect", if (DesignTokens.glassDepth.value > 0.5f) 1f else 0f)
    }

    /**
     * Surface tints (black, white, grey veil — SrcOver in this order, like LiquidGlass.onDrawSurface) and Kyant's
     * Highlight(width = T-GLASS.rim, Default(angle, falloff)) evaluated per pixel: a stroke of 2·ceil(width) centred on the
     * edge, clipped to the inside, blurred by BlurMaskFilter(width / 2) (σ = 0.57735·r + 0.5), × style alpha 0.5, added (Plus).
     */
    fun setSurface(scope: DrawScope, black: Float, white: Float, veil: Float, grey: Float, rim: Float) = with(scope) {
        fill.setFloatUniform("tints", black, white, veil, grey)
        val w = DesignTokens.glassRimWidth.dp.toPx()
        val on = rim > 0f && w > 0f
        fill.setFloatUniform("rimOn", if (on) 1f else 0f)
        fill.setFloatUniform("rimHalf", ceil(w.coerceAtMost(size.minDimension / 2f)))
        fill.setFloatUniform("rimSigma", (BLUR_RADIUS_TO_SIGMA * w / 2f + BLUR_SIGMA_BIAS).coerceAtLeast(0.01f))
        fill.setFloatUniform("rimAlpha", HIGHLIGHT_STYLE_ALPHA * rim)
        fill.setFloatUniform("lightAngle", Math.toRadians(DesignTokens.glassLightAngle.value.toDouble()).toFloat())
        fill.setFloatUniform("lightFalloff", DesignTokens.glassLightFalloff.value)
    }

    private fun bitmapShader(b: ImageBitmap) = BitmapShader(GpuBitmaps.of(b), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
}

/**
 * Kyant Shadow(radius, color) with its default offset (radius / 6 down), only outside the shape. Drawn as a circular
 * rounded rect (radius = the shape's corner): 6 % black blurred by 16 dp looks the same as the continuous shape, and Skia
 * blurs and clips a rounded rect analytically on the GPU (no CPU mask, no upload, no layer).
 */
private fun DrawScope.drawGlassShadow(shape: RoundedRectangularShape, thickness: Float) {
    val alpha = DesignTokens.glassShadowAlpha.value
    val radius = (DesignTokens.glassShadowRadius.dp * thickness).toPx()
    if (alpha <= 0f || radius <= 0f) return
    val k = shape.corners(size, layoutDirection, this)
    val r = minOf(k.topLeft, k.topRight, k.bottomLeft, k.bottomRight)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.copy(alpha = alpha).toArgb()
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        nc.save()
        nc.clipOutPath(android.graphics.Path().apply { addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW) })
        val dy = radius / SHADOW_OFFSET_DIV
        nc.drawRoundRect(0f, dy, size.width, size.height + dy, r, r, paint)
        nc.restore()
    }
}

/**
 * GPU-resident (HARDWARE) copies of the sampled images. A mutable software bitmap in a shader is uploaded to the GPU
 * again on EVERY draw (simpleperf: ≈ 40 % of the RenderThread in glTexSubImage2D/texture allocation while a folder
 * opened) — a hardware bitmap lives on the GPU once. Weak keys: copies go with their source.
 */
private object GpuBitmaps {
    private val map = java.util.WeakHashMap<android.graphics.Bitmap, android.graphics.Bitmap>()
    fun of(img: ImageBitmap): android.graphics.Bitmap {
        val src = img.asAndroidBitmap()
        if (src.config == android.graphics.Bitmap.Config.HARDWARE) return src
        return synchronized(map) {
            map.getOrPut(src) { runCatching { src.copy(android.graphics.Bitmap.Config.HARDWARE, false) }.getOrNull() ?: src }
        }
    }
}

/** Blurred wallpaper copies for frosted glass, shared by all tiles (key: wallpaper + rounded radius). */
private object FrostCache {
    private val map = HashMap<Pair<ImageBitmap, Float>, ImageBitmap>()
    fun get(w: ImageBitmap, r: Float): ImageBitmap? = if (r <= 0f) null else synchronized(map) { map[w to r] }
    suspend fun compute(w: ImageBitmap, r: Float): ImageBitmap {
        get(w, r)?.let { return it }
        val b = WallpaperRenderer.softBlur(w, r)
        synchronized(map) {
            map.keys.removeAll { it.first !== w } // old wallpaper
            map[w to r] = b
        }
        return b
    }
}

private const val SHARP_BLUR_PX = 2.5f   // below that, bilinear sampling ≈ the RenderEffect blur
private const val FROST_STEP_PX = 4f     // slider steps that share one blurred copy
private const val SHADOW_OFFSET_DIV = 6f // Kyant Shadow default offset = radius / 6
private const val HIGHLIGHT_STYLE_ALPHA = 0.5f // Kyant HighlightStyle.Default colour alpha
private const val BLUR_RADIUS_TO_SIGMA = 0.57735f // Skia: BlurMaskFilter radius → Gaussian sigma
private const val BLUR_SIGMA_BIAS = 0.5f

private const val SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

// Kyant's RoundedRectRefractionShader (backdrop 2.0.1), content = saturated wallpaper sampled at the screen position
private const val FILL_SHADER = """
uniform shader wall;
uniform shader soft;
uniform float softMix;
uniform float3x3 toRoot;
uniform float2 size;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float saturation;
uniform float lensOn;
uniform float4 tints;        // black, white, veil alpha, veil grey
uniform float rimOn;
uniform float rimHalf;       // half stroke width (px)
uniform float rimSigma;
uniform float rimAlpha;
uniform float lightAngle;
uniform float lightFalloff;
$SDF
float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
half4 content(float2 p) {
    float3 r = toRoot * float3(p, 1.0);
    float2 q = r.xy / r.z;
    half4 c = wall.eval(q);
    if (softMix > 0.0) c = mix(c, soft.eval(q), half(softMix));
    half l = dot(c.rgb, half3(0.213, 0.715, 0.072));
    c.rgb = mix(half3(l), c.rgb, half(saturation));
    return c;
}
half4 lensed(float2 coord) {
    if (lensOn < 0.5) return content(coord);
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    return content(coord + d * grad);
}
float gaussCdf(float x) { return 1.0 / (1.0 + exp(-1.702 * x)); } // logistic ≈ Gaussian CDF (AGSL has no tanh/erf)
half4 main(float2 coord) {
    half4 c = lensed(coord);
    c.rgb = mix(c.rgb, half3(0.0), half(tints.x));
    c.rgb = mix(c.rgb, half3(1.0), half(tints.y));
    c.rgb = mix(c.rgb, half3(tints.w), half(tints.z));
    if (rimOn > 0.5) {
        float2 halfSize = size * 0.5;
        float2 centeredCoord = coord - halfSize;
        float radius = radiusAt(coord, cornerRadii);
        float inside = -sdRoundedRect(centeredCoord, halfSize, radius);
        float cov = gaussCdf((rimHalf - inside) / rimSigma) - gaussCdf((-rimHalf - inside) / rimSigma);
        float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
        float intensity = pow(abs(dot(grad, float2(cos(lightAngle), sin(lightAngle)))), lightFalloff);
        c.rgb = min(c.rgb + half(rimAlpha * cov * intensity), half3(1.0));
    }
    return c;
}
"""

// Kyant's DefaultHighlightShader (backdrop 2.0.1)
private const val HIGHLIGHT_SHADER = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;
$SDF
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    return color * intensity;
}
"""
