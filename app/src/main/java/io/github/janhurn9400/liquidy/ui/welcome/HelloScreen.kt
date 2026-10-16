package io.github.janhurn9400.liquidy.ui.welcome

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Greetings like in the iPhone setup (iOS 26: "hello" → "你好" → …, own selection). */
private val HELLOS = listOf("hello", "你好", "hola", "hallo", "bonjour", "こんにちは", "ciao", "안녕하세요", "olá", "привет", "merhaba", "hej")

/**
 * "Hello" like in iOS 26: light background of real Liquid Glass panes over a moving light field; on it
 * the greeting is "written" as a thin Liquid Glass line — the line grows like a pen until "hello" stands there,
 * holds briefly, blurs and the next language appears. "hello" is an own continuous writing line
 * (no font); other languages are thin system font in glass that appears from left to right.
 * Swiping up pulls the content up with the finger; tapping continues as well.
 */
@Composable
fun HelloScreen(onContinue: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val draw = remember { Animatable(0f) }    // writing progress of the current greeting 0 → 1
    val out = remember { Animatable(0f) }     // fade out (blur away) 0 → 1
    val lift = remember { Animatable(0f) }    // swipe up (px, negative)
    var time by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }
    var leaving by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { val t0 = withFrameNanos { it }; while (true) withFrameNanos { time = (it - t0) / 1e9f } }
    LaunchedEffect(Unit) {
        delay(START_DELAY_MS)
        while (true) {
            val hero = HELLOS[index] == "hello"
            draw.animateTo(1f, tween(if (hero) WRITE_HERO_MS else WRITE_MS, easing = WRITE_EASE))
            delay(HOLD_MS)
            out.animateTo(1f, tween(OUT_MS, easing = EASE))
            index = (index + 1) % HELLOS.size
            draw.snapTo(0f); out.snapTo(0f)
        }
    }
    /** Push the card up out of view (initial velocity from the finger), shortly before the end it continues. */
    fun leave(velocity: Float = 0f) {
        if (leaving == 1) return
        leaving = 1
        scope.launch {
            var continued = false
            lift.animateTo(-height * 1.02f, spring(1f, SHEET_STIFFNESS), initialVelocity = velocity.coerceAtMost(0f)) {
                if (!continued && value < -height * CONTINUE_AT) { continued = true; onContinue() }
            }
            if (!continued) onContinue()
        }
    }
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { leave() } }
            .pointerInput(Unit) {
                height = size.height.toFloat()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        // like the notification centre: far or fast enough upwards → out, otherwise spring back
                        val v = tracker.calculateVelocity().y
                        if (lift.value < -size.height * LEAVE_SHARE || v < -FLING_PX_S) leave(v)
                        else scope.launch { lift.animateTo(0f, spring(SNAP_BACK_DAMPING, SNAP_BACK_STIFFNESS), initialVelocity = v) }
                    },
                    onDragCancel = { scope.launch { lift.animateTo(0f, spring(SNAP_BACK_DAMPING, SNAP_BACK_STIFFNESS)) } },
                ) { change, dy ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // upwards 1:1 (like iOS), downwards strongly damped
                    val step = if (dy < 0f || lift.value < 0f) dy else dy * 0.15f
                    scope.launch { lift.snapTo((lift.value + step).coerceAtMost(40f)) }
                }
            }
    ) {
        // Below it (iOS: constantly dimmed, blurred, grey background) — revealed when pushing up
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            val b = UNDER_BLUR_DP * density
            renderEffect = BlurEffect(b, b, TileMode.Clamp)
        }) { drawLightField(time); drawRect(UNDER_DIM) }

        // The card: scene, text and hint move rigidly with the finger; round corners like the screen
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationY = lift.value
            shape = androidx.compose.foundation.shape.RoundedCornerShape(SHEET_CORNER_DP.dp)
            clip = lift.value < -0.5f
            shadowElevation = if (lift.value < -0.5f) 24.dp.toPx() else 0f
        }) {
        // Light field (source) + real Liquid Glass panes above it that refract it (like the glass panes in the iOS 26 setup).
        // Both together are the source for the glass text.
        val field = rememberLayerBackdrop()
        val scene = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().layerBackdrop(scene)) {
            Box(Modifier.fillMaxSize().layerBackdrop(field).drawBehind { drawLightField(time) })
            PANES.forEachIndexed { k, pane -> GlassPane(field, pane, k, { time }, { 0f }) }
        }

        Box(Modifier.fillMaxSize().graphicsLayer {
            val o = out.value
            alpha = 1f - o
            val s = 1f + 0.04f * o; scaleX = s; scaleY = s
            val b = BLUR_DP * o * density
            renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
        }) {
            GlassWriting(HELLOS[index], scene) { draw.value }
        }
        BasicText(
            stringResource(R.string.welcome_swipe_up),
            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = Color.White.copy(alpha = 0.95f),
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x40274C8C), Offset(0f, 1f), 8f)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp),
        )
        } // card
    }
}

/**
 * Greeting as a thin Liquid Glass line that grows like a pen. Material = the glass of the dock (Kyant lens), here as
 * an own shader with a distance field to the line instead of the rounded rectangle: circular profile, refraction outwards,
 * colour fringe, light edge top left; the source is the real scene (light field + glass panes).
 */
@Composable
private fun GlassWriting(word: String, scene: com.kyant.backdrop.Backdrop, progress: () -> Float) {
    val strokes = remember(word) { strokesFor(word) }
    val aspect = remember(strokes) {
        var lo = 0f; var hi = 0f
        strokes.forEach { a -> for (i in 1 until a.size step 2) { lo = minOf(lo, a[i]); hi = maxOf(hi, a[i]) } }
        (hi - lo).coerceAtLeast(0.05f)
    }
    val total = remember(strokes) { strokes.sumOf { strokeLength(it).toDouble() }.toFloat() }
    val shader = remember { android.graphics.RuntimeShader(GLASS_LINE_AGSL) }
    // Mask instead of a point list: the line is drawn once per frame on the CPU (R = sharp area, G = soft height),
    // the shader only reads 5 values per pixel instead of checking up to 768 segments
    // Two masks alternating: never draw into the image the graphics may still be reading (otherwise flicker);
    // redrawn only when the line has changed
    val mask = remember { arrayOfNulls<android.graphics.Bitmap>(2) }
    val maskState = remember { IntArray(1) }        // index of the current mask
    val maskKey = remember { arrayOfNulls<Any>(1) } // (progress, size) of the current mask
    val gMax = remember { floatArrayOf(0.6f) }      // height at the line centre (measured)
    val sharpPaint = remember { android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND; color = 0xFFFF0000.toInt()
        blendMode = android.graphics.BlendMode.PLUS } }
    val softPaint = remember { android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND; color = 0xFF00FF00.toInt()
        blendMode = android.graphics.BlendMode.PLUS } }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val halfW = STROKE_DP / 2f * density
    val margin = MARGIN_DP * density

    Box(
        Modifier.fillMaxSize().layout { m, c ->
            val sw = c.maxWidth.toFloat()
            val u = minOf(c.maxWidth, c.maxHeight).toFloat()          // shorter side (Fold landscape: huge otherwise)
            val s = minOf(u * 0.82f, u * 0.24f / aspect)              // word width in px
            val bw = (s + 2 * margin).toInt(); val bh = (s * aspect + 2 * margin).toInt()
            val pl = m.measure(androidx.compose.ui.unit.Constraints.fixed(bw, bh))
            layout(c.maxWidth, c.maxHeight) { pl.place(((sw - bw) / 2).toInt(), (c.maxHeight * 0.45f - bh / 2f).toInt()) }
        }
    ) {
        // Polyline up to the current progress, in box pixels (point + "pen down" flag)
        fun drawn(bw: Float, bh: Float): Pair<FloatArray, Int> {
            val s = bw - 2 * margin
            val out = FloatArray(MAX_POINTS * 3); var n = 0
            var left = total * s * progress()   // total is normalised (word width 1) → in px
            for (a in strokes) {
                if (left <= 0f || n >= MAX_POINTS - 1) break
                var px = 0f; var py = 0f
                for (i in 0 until a.size / 2) {
                    val x = margin + (a[2 * i] + 0.5f) * s; val y = bh / 2 + a[2 * i + 1] * s
                    if (i > 0) {
                        val seg = kotlin.math.hypot(x - px, y - py)
                        if (seg >= left) { val t = left / seg
                            out[3 * n] = px + (x - px) * t; out[3 * n + 1] = py + (y - py) * t; out[3 * n + 2] = 1f; n++; left = 0f; break }
                        left -= seg
                    }
                    if (n >= MAX_POINTS) break
                    out[3 * n] = x; out[3 * n + 1] = y; out[3 * n + 2] = if (i == 0) 0f else 1f; n++
                    px = x; py = y
                }
            }
            return out to n
        }
        // soft, bluish shadow below the line
        Canvas(Modifier.fillMaxSize()) {
            val (pts, n) = drawn(size.width, size.height)
            if (n < 2) return@Canvas
            val path = smoothPath(pts, n)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = halfW * 2
                strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND; color = 0x30274C8C
                maskFilter = android.graphics.BlurMaskFilter(halfW * 1.6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            drawContext.canvas.nativeCanvas.save(); drawContext.canvas.nativeCanvas.translate(0f, halfW * 1.1f)
            drawContext.canvas.nativeCanvas.drawPath(path, paint); drawContext.canvas.nativeCanvas.restore()
        }
        Box(
            Modifier.fillMaxSize().drawBackdrop(
                backdrop = scene,
                shape = { androidx.compose.ui.graphics.RectangleShape },
                highlight = null, shadow = null, innerShadow = null, // no edge/shadow around the area, only the line
                effects = {
                    val bw = size.width; val bh = size.height
                    val (pts, n) = drawn(bw, bh)
                    val w = bw.toInt().coerceAtLeast(1); val h = bh.toInt().coerceAtLeast(1)
                    val key = Triple(progress(), w, h)
                    val bmp: android.graphics.Bitmap
                    if (key == maskKey[0] && mask[maskState[0]] != null) bmp = mask[maskState[0]]!! else {
                    val next = 1 - maskState[0]
                    bmp = mask[next]?.takeIf { it.width == w && it.height == h }
                        ?: android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { mask[next] = it }
                    maskState[0] = next; maskKey[0] = key
                    val c = android.graphics.Canvas(bmp)
                    c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
                    val path = smoothPath(pts, n)
                    // Height: wide + soft (bright centre, falling off towards the edge) → circular profile/normal; area: sharp at stroke width
                    // Height: narrow core, softly faded → centre = maximum, ≈ 0 at the stroke edge (circular profile exactly as before)
                    softPaint.strokeWidth = halfW * 0.5f
                    softPaint.maskFilter = android.graphics.BlurMaskFilter(halfW * 0.85f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    c.drawPath(path, softPaint)
                    sharpPaint.strokeWidth = halfW * 2f
                    c.drawPath(path, sharpPaint)
                    // Measure the height maximum at a point on the line (centre of a segment) → normalisation in the shader
                    if (n >= 2) {
                        val mx = ((pts[0] + pts[3]) / 2).toInt().coerceIn(0, w - 1); val my = ((pts[1] + pts[4]) / 2).toInt().coerceIn(0, h - 1)
                        gMax[0] = ((bmp.getPixel(mx, my) shr 8) and 0xFF).coerceAtLeast(20) / 255f
                    }
                    }
                    shader.setInputShader("mask", android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP))
                    shader.setFloatUniform("halfW", halfW)
                    shader.setFloatUniform("amount", halfW * REFRACTION)
                    shader.setFloatUniform("gMax", gMax[0])
                    effect(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect())
                },
            )
        )
    }
}

/**
 * Points (x, y, pen down) → smooth line: curves through the midpoints of neighbouring segments (quadratic), so that at
 * large sizes no corners between the sample points become visible.
 */
private fun smoothPath(pts: FloatArray, n: Int): android.graphics.Path {
    val path = android.graphics.Path()
    var i = 0
    while (i < n) {
        // one stroke: from i up to the next "pen up"
        var j = i + 1
        while (j < n && pts[3 * j + 2] >= 0.5f) j++
        path.moveTo(pts[3 * i], pts[3 * i + 1])
        if (j - i == 2) path.lineTo(pts[3 * i + 3], pts[3 * i + 4])
        else {
            for (k in i + 1 until j - 1) {
                val mx = (pts[3 * k] + pts[3 * k + 3]) / 2; val my = (pts[3 * k + 1] + pts[3 * k + 4]) / 2
                path.quadTo(pts[3 * k], pts[3 * k + 1], mx, my)
            }
            if (j - 1 > i) path.lineTo(pts[3 * (j - 1)], pts[3 * (j - 1) + 1])
        }
        i = j
    }
    return path
}

/** Polylines of a greeting, normalised (word width 1, centre 0,0). "hello" is the own writing line. */
private fun strokesFor(word: String): Array<FloatArray> = if (word == "hello") HELLO_NORM else HELLO_STROKES.getValue(word)

private fun strokeLength(a: FloatArray): Float {
    var l = 0f
    for (i in 1 until a.size / 2) l += kotlin.math.hypot(a[2 * i] - a[2 * i - 2], a[2 * i + 1] - a[2 * i - 1])
    return l
}

private val HELLO_NORM: Array<FloatArray> by lazy {
    val pm = android.graphics.PathMeasure(HELLO_PATH, false)
    val n = (pm.length / 5f).toInt()   // finely sampled → smooth tube
    val pos = FloatArray(2); val pts = FloatArray(2 * (n + 1))
    for (i in 0..n) {
        pm.getPosTan(pm.length * i / n, pos, null)
        pts[2 * i] = (pos[0] - HELLO_W / 2) / HELLO_W; pts[2 * i + 1] = (pos[1] - HELLO_MID_Y) / HELLO_W
    }
    arrayOf(pts)
}

/**
 * Glass line (AGSL): distance to the polyline (points as texture, b = pen down) → tube with half width halfW.
 * Inside like Kyant's lens: offset = circleMap(1 − depth) · amount along the normal (outwards), colour fringe
 * via three samples, slightly brightened; light edge: bright top left, weaker bottom right.
 */
private const val GLASS_LINE_AGSL = """
uniform shader content;
uniform shader mask;
uniform float halfW;
uniform float amount;
uniform float gMax;

half4 main(float2 coord) {
    half4 m = mask.eval(coord);
    float cov = m.r;
    if (cov < 0.004) return half4(0.0);
    // Height from the soft mask, normal from its gradient (4 samples)
    float s = max(halfW * 0.45, 1.0);
    float hx = mask.eval(coord + float2(s, 0.0)).g - mask.eval(coord - float2(s, 0.0)).g;
    float hy = mask.eval(coord + float2(0.0, s)).g - mask.eval(coord - float2(0.0, s)).g;
    float2 grad = float2(hx, hy);
    float gl = length(grad);
    float2 n = gl > 0.0001 ? -grad / gl : float2(0.0, -1.0);   // points outwards
    float depth = clamp(m.g / gMax, 0.0, 1.0);                 // centre = 1, stroke edge ≈ 0
    float e = 1.0 - depth;
    float disp = (1.0 - sqrt(max(1.0 - e * e, 0.0))) * amount;
    float2 rc = coord + n * disp;
    float2 ca = n * disp * 0.35;
    half4 col = half4(content.eval(rc + ca).r, content.eval(rc).g, content.eval(rc - ca).b, 1.0);
    col.rgb = mix(col.rgb, half3(1.0), 0.16);
    float rim = pow(e, 2.5);
    float litA = max(dot(n, normalize(float2(-0.6, -0.8))), 0.0);
    float litB = max(dot(n, normalize(float2(0.6, 0.8))), 0.0);
    col.rgb += half3(rim * (0.9 * litA + 0.4 * litB));
    col.rgb = min(col.rgb, half3(1.0));
    return col * cov;
}
"""

/**
 * Own continuous writing line "hello" (single pen stroke, slanted script), coordinates in a
 * 720 × 330 area, baseline at y ≈ 300. Drawn ourselves — no Apple asset.
 */
private val HELLO_PATH: android.graphics.Path by lazy {
    android.graphics.Path().apply {
        moveTo(20f, 298f)
        // h: upstroke into the ascender loop, downstroke, arc
        cubicTo(70f, 270f, 140f, 170f, 152f, 95f)
        cubicTo(160f, 45f, 135f, 22f, 118f, 52f)
        cubicTo(98f, 92f, 92f, 200f, 86f, 300f)
        cubicTo(100f, 235f, 140f, 196f, 170f, 200f)
        cubicTo(200f, 204f, 196f, 250f, 196f, 278f)
        cubicTo(196f, 302f, 214f, 306f, 236f, 290f)
        // e: small loop
        cubicTo(266f, 270f, 312f, 246f, 312f, 220f)
        cubicTo(312f, 198f, 286f, 192f, 272f, 210f)
        cubicTo(254f, 236f, 258f, 302f, 302f, 303f)
        cubicTo(330f, 304f, 352f, 288f, 372f, 264f)
        // l
        cubicTo(406f, 214f, 438f, 134f, 438f, 84f)
        cubicTo(438f, 42f, 414f, 38f, 404f, 72f)
        cubicTo(390f, 132f, 384f, 222f, 390f, 272f)
        cubicTo(394f, 302f, 420f, 306f, 444f, 288f)
        // l
        cubicTo(478f, 242f, 508f, 142f, 508f, 92f)
        cubicTo(508f, 48f, 484f, 44f, 474f, 78f)
        cubicTo(460f, 138f, 454f, 222f, 460f, 272f)
        cubicTo(464f, 302f, 490f, 306f, 514f, 288f)
        // o: arc, closed, swing to the right
        cubicTo(534f, 268f, 550f, 222f, 580f, 206f)
        cubicTo(614f, 190f, 642f, 214f, 634f, 254f)
        cubicTo(626f, 294f, 586f, 312f, 566f, 288f)
        cubicTo(548f, 264f, 566f, 212f, 600f, 206f)
        cubicTo(632f, 202f, 664f, 214f, 700f, 200f)
    }
}
private const val HELLO_W = 720f
private const val HELLO_MID_Y = 175f

/** A glass pane: centre/radius relative to the screen width, tint, parallax when swiping up. */
private class Pane(val cx: Float, val cy: Float, val r: Float, val tint: Color, val tintAlpha: Float, val parallax: Float)

private val PANES = listOf(
    Pane(1.0f, 0.36f, 0.8f, Color(0xFF2F6FE0), 0.30f, 0.30f),
    Pane(0.0f, 1.95f, 0.95f, Color(0xFF1FA8E0), 0.26f, 0.18f),
    Pane(1.08f, 1.6f, 0.55f, Color(0xFF6FA6F0), 0.14f, 0.10f),
)

/**
 * Real Liquid Glass pane (Kyant lens like dock/folder): strongly refracts the light field at the edge (light bands bend),
 * slightly scattered, with a gloss edge and soft shadow; drifts slowly.
 */
@Composable
private fun GlassPane(field: com.kyant.backdrop.Backdrop, p: Pane, k: Int, time: () -> Float, lift: () -> Float) {
    // Panes are larger than the screen → measure a fixed size and place freely (squashed otherwise)
    Box(
        Modifier.fillMaxSize().layout { m, c ->
            // Align the size to the SHORTER side: on the landscape Fold inner display the panes would otherwise be
            // > 5000 px large — the glass records every pane completely in every frame (seconds per frame in the emulator)
            val w = c.maxWidth.toFloat(); val u = minOf(c.maxWidth, c.maxHeight).toFloat()
            val d = (2 * p.r * u).toInt()
            val pl = m.measure(androidx.compose.ui.unit.Constraints.fixed(d, d))
            layout(c.maxWidth, c.maxHeight) {
                val t = time(); val a = t * 2f * PI.toFloat() / (DRIFT_S + k * 7f)
                val x = w * p.cx - p.r * u + u * 0.03f * sin(a + k)
                // cy is given in widths of a portrait phone (height ≈ 2.17 widths) → proportionally to the real height
                val y = c.maxHeight * p.cy / PORTRAIT_ASPECT - p.r * u + u * 0.025f * cos(a * 0.8f + k * 1.7f) + lift() * p.parallax
                pl.place(x.toInt(), y.toInt())
            }
        }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = field,
                    shape = { Capsule() },
                    effects = {
                        blur(2.dp.toPx())
                        val r = size.minDimension / 2
                        lens(refractionHeight = r * 0.42f, refractionAmount = r * 0.5f, depthEffect = true, chromaticAberration = true)
                    },
                    highlight = { Highlight(width = 1.5.dp, alpha = 0.7f, style = HighlightStyle.Default(angle = 45f, falloff = 2f)) },
                    shadow = { Shadow(radius = 30.dp, color = Color(0xFF1B3B7A).copy(alpha = 0.10f)) },
                    onDrawSurface = { drawRect(p.tint.copy(alpha = p.tintAlpha)) },
                )
        )
    }
}

/** Light field behind the glass: light pastel gradient, soft colour areas and slanted light bands that move slowly. */
private fun DrawScope.drawLightField(t: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFD5DDF8), Color(0xFFE3E6FA), Color(0xFFCBE6F7)), endY = h))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF7FA2F2).copy(alpha = 0.75f), Color.Transparent), Offset(w * 0.85f, h * 0.2f), w * 0.9f), w * 0.9f, Offset(w * 0.85f, h * 0.2f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF5CC8F0).copy(alpha = 0.7f), Color.Transparent), Offset(w * 0.15f, h * 0.92f), w * 1.0f), w * 1.0f, Offset(w * 0.15f, h * 0.92f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB7B0F4).copy(alpha = 0.55f), Color.Transparent), Offset(w * 0.1f, h * 0.35f), w * 0.7f), w * 0.7f, Offset(w * 0.1f, h * 0.35f))
    rotate(-28f, Offset(w / 2, h / 2)) {
        val band = w * 0.14f
        val shift = (t * band * 0.15f) % (band * 2.4f)
        var y = -h * 0.4f + shift
        while (y < h * 1.4f) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent), startY = y, endY = y + band),
                Offset(-w, y), androidx.compose.ui.geometry.Size(w * 3f, band))
            y += band * 2.4f
        }
    }
}

private val EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val WRITE_EASE = CubicBezierEasing(0.45f, 0.05f, 0.35f, 1f) // pen: brisk, swinging out at the end
private const val START_DELAY_MS = 500L
private const val WRITE_HERO_MS = 2600
private const val WRITE_MS = 1400
private const val HOLD_MS = 1500L
private const val OUT_MS = 550
private const val LEAVE_SHARE = 0.25f          // released from ¼ height → out
private const val FLING_PX_S = 1200f           // or faster upwards
private const val CONTINUE_AT = 0.85f          // page appears when the card is 85 % out
private val SHEET_STIFFNESS = (2 * PI.toFloat() / 0.35f).let { it * it }   // response ≈ 0.35 s, critically damped
private const val SNAP_BACK_DAMPING = 0.86f
private val SNAP_BACK_STIFFNESS = (2 * PI.toFloat() / 0.4f).let { it * it }
private const val SHEET_CORNER_DP = 50f        // ≈ display corner
private const val UNDER_BLUR_DP = 40f
private val UNDER_DIM = Color(0x5C6E7480)       // dimmed, desaturated (measurement: constant brightness below the card)
private const val BLUR_DP = 10f
private const val DRIFT_S = 24f
private const val PORTRAIT_ASPECT = 2.17f
private const val STROKE_DP = 7f
private const val MARGIN_DP = 24f
private const val REFRACTION = 2.6f
private const val MAX_POINTS = 4096
