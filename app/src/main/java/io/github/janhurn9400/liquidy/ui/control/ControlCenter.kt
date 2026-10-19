package io.github.janhurn9400.liquidy.ui.control

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.RootShell
import io.github.janhurn9400.liquidy.data.SystemControls
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.drawObjectRim
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Control Center state (M-CC). [progress] 0…1 follows the finger while pulling down from the top right and settles with
 * a spring; [over] is the rubber-banded pull beyond full (pt). Everything interruptible, release velocity passed on.
 */
class ControlCenterModel(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    val over = Animatable(0f)
    var open by mutableStateOf(false)
        private set
    val visible: Boolean get() = open || progress.value > 0.001f

    fun pull(pt: Float, screenPt: Float) {
        val full = Motion.ccPull.value
        val p = (pt / full).coerceIn(0f, 1f)
        val extra = if (pt > full) Motion.rubberBand(pt - full, screenPt) else 0f
        scope.launch { progress.snapTo(p) }
        scope.launch { over.snapTo(extra) }
    }

    /** [velocity] in pt/s, positive = down. */
    fun release(velocity: Float) {
        val opens = velocity > Motion.ccFlick.value || (velocity > -Motion.ccFlick.value && progress.value >= Motion.ccCommit.value)
        if (opens) show(velocity) else hide(velocity)
    }

    fun show(velocity: Float = 0f) {
        open = true
        io.github.janhurn9400.liquidy.data.PerfBoost.kick()
        scope.launch { progress.animateTo(1f, Motion.cc.spec(), initialVelocity = velocity / Motion.ccPull.value) }
        scope.launch { over.animateTo(0f, Motion.cc.spec()) }
    }

    fun hide(velocity: Float = 0f) {
        open = false
        scope.launch { progress.animateTo(0f, Motion.cc.spec(), initialVelocity = velocity / Motion.ccPull.value) }
        scope.launch { over.animateTo(0f, Motion.cc.spec()) }
    }

    /** Dragging the open Control Center up (negative [dyPt]) closes it along with the finger. */
    fun dragOpen(dyPt: Float, screenPt: Float) {
        if (dyPt >= 0f) { scope.launch { progress.snapTo(1f) }; scope.launch { over.snapTo(Motion.rubberBand(dyPt, screenPt)) } }
        else { scope.launch { progress.snapTo((1f + dyPt / Motion.ccPull.value).coerceIn(0f, 1f)) }; scope.launch { over.snapTo(0f) } }
    }
}

/** Full-screen Control Center over the (blurred) home screen. */
@Composable
fun ControlCenter(model: ControlCenterModel, density: Float, screenW: Float, screenH: Float) {
    if (!model.visible) return
    val context = LocalContext.current
    // keep the shown state fresh while open (cheap reads); root is asked for once, the first time it opens
    LaunchedEffect(model.open) {
        if (!model.open) return@LaunchedEffect
        RootShell.probe { ok -> if (ok) SystemControls.grantWithRoot(context) }
        while (true) { SystemControls.refresh(context); delay(REFRESH_MS) }
    }
    val p = { model.progress.value }
    val screenPt = screenH / density
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p() }
            .background(Color.Black.copy(alpha = DesignTokens.ccDim.value))
            // empty area: tap closes, a drag follows the finger (up closes)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(c.uptimeMillis, c.position)
                        val dy = c.position.y - down.position.y
                        if (!moved && kotlin.math.abs(dy) > viewConfiguration.touchSlop) moved = true
                        if (moved) { model.dragOpen(dy / density, screenPt); c.consume() }
                        if (!c.pressed) break
                    }
                    if (!moved) model.hide()
                    else {
                        val v = tracker.calculateVelocity().y / density
                        if (v < -Motion.ccFlick.value || model.progress.value < 1f - Motion.ccCommit.value) model.hide(v) else model.show(v)
                    }
                }
            }
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val q = p()
                alpha = q
                translationY = (-(1f - q) * Motion.ccEnterY.value + model.over.value) * density
                val b = (1f - q) * Motion.ccEnterBlur.value * density
                renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
            }
    ) {
        CcContent(context, density, screenW)
    }
}

@Composable
private fun CcContent(context: Context, density: Float, screenW: Float) {
    val cell = DesignTokens.ccCell.value
    val gx = DesignTokens.ccGapX.value
    val gy = DesignTokens.ccGapY.value
    val gridW = 4 * cell + 3 * gx
    val left = (screenW / density - gridW) / 2
    val top = DesignTokens.ccTop.value
    fun x(col: Int) = left + col * (cell + gx)
    fun y(row: Int) = top + row * (cell + gy)
    fun span(n: Int, gap: Float) = n * cell + (n - 1) * gap
    @Composable
    fun At(col: Int, row: Int, cols: Int, rows: Int, content: @Composable (Modifier) -> Unit) =
        content(Modifier.offset { IntOffset((x(col) * density).roundToInt(), (y(row) * density).roundToInt()) }
            .size(span(cols, gx).dp, span(rows, gy).dp))

    val c = SystemControls
    // top: + and power, status row
    val bs = DesignTokens.ccButton.value
    Glyph(Modifier.offset { IntOffset(((left - BUTTON_OUT) * density).roundToInt(), ((DesignTokens.ccButtonY.value - bs / 2) * density).roundToInt()) }
        .size(bs.dp).clip(CircleShape).background(Color.White.copy(alpha = DesignTokens.ccFill.value)), Icons.plus, Color.White, 0.55f)
    Glyph(Modifier.offset { IntOffset(((left + gridW - bs + BUTTON_OUT) * density).roundToInt(), ((DesignTokens.ccButtonY.value - bs / 2) * density).roundToInt()) }
        .size(bs.dp).clip(CircleShape).background(Color.White.copy(alpha = DesignTokens.ccFill.value))
        .pointerInput(Unit) { detectTapGestures { RootShell.run("input keyevent --longpress 26") } }, Icons.power, Color.White, 0.6f)
    val status = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
    Box(Modifier.offset { IntOffset(((left + 4f) * density).roundToInt(), ((DesignTokens.ccStatusY.value - 10f) * density).roundToInt()) }.width((gridW - 8f).dp)) {
        BasicText(c.carrier.value, style = status, modifier = Modifier.align(Alignment.CenterStart))
        BasicText("${c.batteryPercent.value} %", style = status, modifier = Modifier.align(Alignment.CenterEnd))
    }

    // connectivity platter (2×2) and media (2×2)
    At(0, 0, 2, 2) { m -> Connectivity(m, context) }
    At(2, 0, 2, 2) { m -> MediaModule(m) }
    // row 2–3: rotation lock, silent, focus; volume + brightness sliders
    At(0, 2, 1, 1) { m -> RoundToggle(m, Icons.rotationLock, c.rotationLock.value, activeGlyph = RED) { c.toggleRotationLock(context) } }
    At(1, 2, 1, 1) { m -> RoundToggle(m, if (c.silent.value) Icons.bellOff else Icons.bell, c.silent.value, activeGlyph = RED) { c.toggleSilent(context) } }
    At(0, 3, 2, 1) { m -> FocusModule(m, c.dnd.value) { c.toggleDnd(context) } }
    At(2, 2, 1, 2) { m -> Slider(m, c.volume.floatValue, Icons.speaker, BLUE) { v -> c.setVolume(context, v) } }
    At(3, 2, 1, 2) { m -> Slider(m, c.brightness.floatValue, Icons.sun, YELLOW) { v -> c.setBrightness(context, v) } }
    // row 4–5: switches and app shortcuts
    At(0, 4, 1, 1) { m -> RoundToggle(m, Icons.torch, c.torch.value, activeGlyph = BLUE) { c.toggleTorch(context) } }
    At(1, 4, 1, 1) { m -> RoundToggle(m, Icons.mirror, false) { launch(context, Intent(android.provider.Settings.ACTION_CAST_SETTINGS)) } }
    At(2, 4, 1, 1) { m -> RoundToggle(m, Icons.contrast, c.darkMode.value, activeGlyph = Color.Black) { c.toggleDarkMode(context) } }
    At(3, 4, 1, 1) { m -> RoundToggle(m, Icons.camera, false) { launch(context, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) } }
    At(0, 5, 1, 1) { m -> RoundToggle(m, Icons.battery, c.batterySaver.value, activeGlyph = ORANGE) { c.toggleBatterySaver(context) } }
    At(1, 5, 1, 1) { m -> RoundToggle(m, Icons.timer, false) { launch(context, Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS)) } }
    At(2, 5, 1, 1) { m -> RoundToggle(m, Icons.calculator, false) {
        launch(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)) } }
    At(3, 5, 1, 1) { m -> RoundToggle(m, Icons.alarm, false) { launch(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) } }
}

private fun launch(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Translucent module glass: white veil + the object light rim (the blurred home screen shows through). */
private fun Modifier.ccGlass(shape: Shape, fill: Color = Color.White.copy(alpha = DesignTokens.ccFill.value)) =
    this.clip(shape).drawBehind {
        drawOutline(shape.createOutline(size, layoutDirection, this), fill)
        drawObjectRim(this, shape)
    }

@Composable
private fun Connectivity(m: Modifier, context: Context) {
    val c = SystemControls
    Box(m.ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp))) {
        val d = DesignTokens.ccInner.value
        val inset = DesignTokens.ccInnerInset.value
        val span = 2 * DesignTokens.ccCell.value + DesignTokens.ccGapY.value
        val far = span - inset - d
        @Composable
        fun Circle(x: Float, y: Float, icon: Path, on: Boolean, color: Color, tap: () -> Unit) = Glyph(
            Modifier.offset(x.dp, y.dp).size(d.dp).clip(CircleShape)
                .background(if (on) color else Color.White.copy(alpha = DesignTokens.ccFill.value))
                .pointerInput(Unit) { detectTapGestures { tap() } },
            icon, Color.White, 0.5f)
        Circle(inset, inset, Icons.airplane, c.airplane.value, ORANGE) { c.toggleAirplane(context) }
        Circle(far, inset, Icons.cellular, c.mobileData.value, GREEN) { c.toggleMobileData(context) }
        Circle(inset, far, Icons.wifi, c.wifi.value, BLUE) { c.toggleWifi(context) }
        Circle(far, far, Icons.bluetooth, c.bluetooth.value, BLUE) { c.toggleBluetooth(context) }
    }
}

@Composable
private fun MediaModule(m: Modifier) {
    val media = SystemControls.media.value
    val inset = DesignTokens.ccInnerInset.value
    Box(m.ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp))) {
        Column(Modifier.fillMaxSize().padding(inset.dp)) {
            val art = DesignTokens.ccArt.value
            val bmp = media?.art
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(art.dp).clip(RoundedCornerShape((art * ART_CORNER).dp)), contentScale = ContentScale.Crop)
            else Box(Modifier.size(art.dp).clip(RoundedCornerShape((art * ART_CORNER).dp)).background(Color.White.copy(alpha = DesignTokens.ccFill.value)))
            Spacer(Modifier.weight(1f))
            val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            BasicText(media?.title?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.cc_not_playing), style = title, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
            BasicText(media?.artist.orEmpty(), style = title.copy(fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.55f)), maxLines = 1, softWrap = false)
            Spacer(Modifier.size(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                val t = media?.controller?.transportControls
                Glyph(Modifier.size(28.dp).pointerInput(t) { detectTapGestures { t?.skipToPrevious() } }, Icons.previous, Color.White, 0.8f)
                Glyph(Modifier.size(28.dp).pointerInput(t, media?.playing) { detectTapGestures { if (media?.playing == true) t?.pause() else t?.play() } },
                    if (media?.playing == true) Icons.pause else Icons.play, Color.White, 0.8f)
                Glyph(Modifier.size(28.dp).pointerInput(t) { detectTapGestures { t?.skipToNext() } }, Icons.next, Color.White, 0.8f)
            }
        }
    }
}

@Composable
private fun RoundToggle(m: Modifier, icon: Path, on: Boolean, activeGlyph: Color = Color.Black, tap: () -> Unit) {
    Glyph(m.ccGlass(CircleShape, if (on) Color.White else Color.White.copy(alpha = DesignTokens.ccFill.value))
        .pointerInput(Unit) { detectTapGestures { tap() } }, icon, if (on) activeGlyph else Color.White, 0.42f)
}

@Composable
private fun FocusModule(m: Modifier, on: Boolean, tap: () -> Unit) {
    Box(m.ccGlass(CircleShape, if (on) Color.White else Color.White.copy(alpha = DesignTokens.ccFill.value))
        .pointerInput(Unit) { detectTapGestures { tap() } }) {
        Row(Modifier.fillMaxSize().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Modifier.size(38.dp).clip(CircleShape).background(if (on) PURPLE else Color.White.copy(alpha = 0.9f)), Icons.moon, if (on) Color.White else PURPLE, 0.6f)
            Spacer(Modifier.size(10.dp))
            BasicText(stringResource(R.string.cc_focus), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (on) Color.Black else Color.White))
        }
    }
}

/** Tall slider (1×2): white level from the bottom, glyph near the bottom; dragging moves the level relatively (like iOS). */
@Composable
private fun Slider(m: Modifier, value: Float, icon: Path, glyph: Color, onChange: (Float) -> Unit) {
    val current by androidx.compose.runtime.rememberUpdatedState(value)
    Box(m.ccGlass(CircleShape)
        .pointerInput(Unit) {
            var v = 0f
            detectVerticalDragGestures(onDragStart = { v = current }, onVerticalDrag = { ch, dy ->
                ch.consume(); v = (v - dy / size.height).coerceIn(0f, 1f); onChange(v)
            })
        }
        .drawBehind {
            val h = size.height * value.coerceIn(0f, 1f)
            drawRect(Color.White.copy(alpha = SLIDER_FILL), Offset(0f, size.height - h), Size(size.width, h))
        }) {
        val g = SLIDER_GLYPH_FROM_BOTTOM
        Glyph(Modifier.align(Alignment.BottomCenter).offset(y = (-(g - 14f)).dp).size(28.dp), icon, glyph, 0.95f)
    }
}

/** Draws a 24-unit vector path centred, filling [share] of the box. */
@Composable
private fun Glyph(m: Modifier, path: Path, color: Color, share: Float) = Box(m) {
    Canvas(Modifier.fillMaxSize()) { drawGlyph(path, color, share) }
}

private fun DrawScope.drawGlyph(path: Path, color: Color, share: Float) {
    val s = minOf(size.width, size.height) * share / 24f
    translate((size.width - 24f * s) / 2, (size.height - 24f * s) / 2) {
        scale(s, s, pivot = Offset.Zero) { drawPath(path, color) }
    }
}

/** Glyphs: Material icon paths (Apache 2.0) and simple own shapes — no SF Symbols. */
private object Icons {
    private fun p(d: String) = PathParser().parsePathString(d).toPath()
    val airplane = p("M21,16v-2l-8,-5V3.5C13,2.67 12.33,2 11.5,2S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z")
    val cellular = p("M17,4h3v16h-3zM5,14h3v6H5zM11,9h3v11h-3z")
    val wifi = p("M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z")
    val bluetooth = p("M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z")
    val bell = p("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z")
    val bellOff = p("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,11c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68c-0.15,0.03 -0.29,0.08 -0.42,0.12L18,13.68V11zM5.41,3.35L4,4.76l2.81,2.81C6.29,8.57 6,9.73 6,11v5l-2,2v1h14.24l1.74,1.74 1.41,-1.41L5.41,3.35z")
    val speaker = p("M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z")
    val sun = p("M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM20,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0 0.39,-0.39 0.39,-1.03 0,-1.41l-1.06,-1.06zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06z")
    val torch = p("M6,2h12v3H6V2zM6,7v1l2,3v11h8V11l2,-3V7H6zM12,15.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z")
    val camera = p("M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,15.2c1.77,0 3.2,-1.43 3.2,-3.2S13.77,8.8 12,8.8 8.8,10.23 8.8,12s1.43,3.2 3.2,3.2z")
    val play = p("M7,4.5v15c0,0.8 0.85,1.3 1.55,0.9l12.5,-7.5c0.65,-0.4 0.65,-1.4 0,-1.8L8.55,3.6C7.85,3.2 7,3.7 7,4.5z")
    val pause = p("M6,4h4v16H6zM14,4h4v16h-4z")
    val next = p("M2,6.5v11c0,0.6 0.65,0.95 1.15,0.6L11,12.6v4.9c0,0.6 0.65,0.95 1.15,0.6l8.1,-5.5c0.45,-0.3 0.45,-0.9 0,-1.2l-8.1,-5.5C11.65,5.55 11,5.9 11,6.5v4.9L3.15,5.9C2.65,5.55 2,5.9 2,6.5z")
    val previous = p("M22,6.5v11c0,0.6 -0.65,0.95 -1.15,0.6L13,12.6v4.9c0,0.6 -0.65,0.95 -1.15,0.6l-8.1,-5.5c-0.45,-0.3 -0.45,-0.9 0,-1.2l8.1,-5.5C12.35,5.55 13,5.9 13,6.5v4.9l7.85,-5.5C21.35,5.55 22,5.9 22,6.5z")
    val plus = p("M11,4h2v7h7v2h-7v7h-2v-7H4v-2h7z")
    val power = p("M11,2h2v10h-2zM16.56,5.44l-1.45,1.45C16.84,7.94 18,9.83 18,12c0,3.31 -2.69,6 -6,6s-6,-2.69 -6,-6c0,-2.17 1.16,-4.06 2.88,-5.12L7.44,5.44C5.36,6.88 4,9.28 4,12c0,4.42 3.58,8 8,8s8,-3.58 8,-8c0,-2.72 -1.36,-5.12 -3.44,-6.56z")
    val moon = p("M12.3,2.5c-5.2,0.4 -9.3,4.7 -9.3,10 0,5.5 4.5,10 10,10 5.3,0 9.6,-4.1 10,-9.3 -1.3,1.7 -3.4,2.8 -5.7,2.8 -4,0 -7.2,-3.2 -7.2,-7.2 0,-2.3 1.1,-4.4 2.2,-6.3z")
    val rotationLock = p("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10c4.75,0 8.73,-3.31 9.75,-7.75h-2.07C18.7,17.6 15.6,20 12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8c2.21,0 4.2,0.9 5.65,2.35L15,9h7V2l-2.95,2.95C17.26,3.13 14.76,2 12,2zM14.5,11v-1c0,-1.38 -1.12,-2.5 -2.5,-2.5S9.5,8.62 9.5,10v1C8.95,11 8.5,11.45 8.5,12v3.5c0,0.55 0.45,1 1,1h5c0.55,0 1,-0.45 1,-1V12c0,-0.55 -0.45,-1 -1,-1zM13.3,11h-2.6v-1c0,-0.72 0.58,-1.3 1.3,-1.3s1.3,0.58 1.3,1.3v1z")
    val mirror = p("M2,5c0,-1.1 0.9,-2 2,-2h11c1.1,0 2,0.9 2,2v2h-2V5H4v8h2v2H4c-1.1,0 -2,-0.9 -2,-2V5zM9,11c0,-1.1 0.9,-2 2,-2h9c1.1,0 2,0.9 2,2v8c0,1.1 -0.9,2 -2,2h-9c-1.1,0 -2,-0.9 -2,-2v-8zM11,11v8h9v-8h-9z")
    val contrast = p("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20V4c4.41,0 8,3.59 8,8s-3.59,8 -8,8z")
    val battery = p("M4,7h14c1.1,0 2,0.9 2,2v6c0,1.1 -0.9,2 -2,2H4c-1.1,0 -2,-0.9 -2,-2V9c0,-1.1 0.9,-2 2,-2zM4,8.8c-0.11,0 -0.2,0.09 -0.2,0.2v6c0,0.11 0.09,0.2 0.2,0.2h14c0.11,0 0.2,-0.09 0.2,-0.2V9c0,-0.11 -0.09,-0.2 -0.2,-0.2H4zM5,10h5v4H5zM21,10h1v4h-1z")
    val timer = p("M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9h-2c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7 3.13,-7 7,-7V3zM12,3v6h-0.01l-0.99,-0.01V3zM11.3,12.7l-3.6,-4.9 0.8,-0.6 4.9,3.6c0.5,0.4 0.6,1.1 0.2,1.6 -0.4,0.5 -1.1,0.6 -1.6,0.2 -0.3,-0.2 -0.5,-0.5 -0.7,-0.9z")
    val calculator = p("M6,2h12c1.1,0 2,0.9 2,2v16c0,1.1 -0.9,2 -2,2H6c-1.1,0 -2,-0.9 -2,-2V4c0,-1.1 0.9,-2 2,-2zM7,5v4h10V5H7zM7,11v2h2v-2H7zM11,11v2h2v-2h-2zM15,11v2h2v-2h-2zM7,15v2h2v-2H7zM11,15v2h2v-2h-2zM15,15v4h2v-4h-2zM7,19v0h6v0H7z")
    val alarm = p("M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z")
}

private val ORANGE = Color(0xFFFF9F0A)
private val GREEN = Color(0xFF30D158)
private val BLUE = Color(0xFF0A84FF)
private val RED = Color(0xFFFF453A)
private val YELLOW = Color(0xFFFFCC00)
private val PURPLE = Color(0xFF5E5CE6)
private const val SLIDER_FILL = 0.95f
private const val SLIDER_GLYPH_FROM_BOTTOM = 35f // pt [MEASURED] glyph centre above the slider bottom
private const val BUTTON_OUT = 2.5f // pt [MEASURED] + / power stick out of the grid edge
private const val ART_CORNER = 0.22f
private const val REFRESH_MS = 1000L
