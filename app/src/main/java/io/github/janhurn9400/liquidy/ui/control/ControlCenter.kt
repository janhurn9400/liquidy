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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.RootShell
import io.github.janhurn9400.liquidy.data.SystemControls
import io.github.janhurn9400.liquidy.design.CcLayout
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.drawObjectRim
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Modules that open big on a long press. */
enum class CcExpand { Connectivity, Media, Volume, Brightness }

/**
 * Control Center state (M-CC). [progress] 0…1 follows the finger while pulling down from the top right and settles with
 * a spring; [over] is the rubber-banded pull beyond full (pt). [expand] 0…1 grows the [expanded] module into its big
 * panel (M-CC-EXPAND). Everything interruptible, release velocity passed on.
 */
class ControlCenterModel(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    val over = Animatable(0f)
    var open by mutableStateOf(false)
        private set
    val visible: Boolean get() = open || progress.value > 0.001f

    var expanded by mutableStateOf<CcExpand?>(null)
        private set
    val expand = Animatable(0f)

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
        scope.launch {
            progress.animateTo(0f, Motion.cc.spec(), initialVelocity = velocity / Motion.ccPull.value)
            if (!open) { expanded = null; expand.snapTo(0f) } // closed while a module was big → next time compact again
        }
        scope.launch { over.animateTo(0f, Motion.cc.spec()) }
    }

    /** Back/home: a big module closes first, then the Control Center. */
    fun back() { if (expanded != null && expand.targetValue == 1f) collapse() else hide() }

    fun expandModule(m: CcExpand) {
        if (expanded == m && expand.targetValue == 1f) return
        expanded = m
        io.github.janhurn9400.liquidy.data.PerfBoost.kick()
        scope.launch { expand.animateTo(1f, Motion.ccExpand.spec()) }
    }

    fun collapse() {
        if (expanded == null) return
        scope.launch {
            expand.animateTo(0f, Motion.ccExpand.spec())
            if (expand.targetValue == 0f) expanded = null
        }
    }

    /** Dragging the open Control Center up (negative [dyPt]) closes it along with the finger. */
    fun dragOpen(dyPt: Float, screenPt: Float) {
        if (dyPt >= 0f) { scope.launch { progress.snapTo(1f) }; scope.launch { over.snapTo(Motion.rubberBand(dyPt, screenPt)) } }
        else { scope.launch { progress.snapTo((1f + dyPt / Motion.ccPull.value).coerceIn(0f, 1f)) }; scope.launch { over.snapTo(0f) } }
    }
}

/** Rectangle in pt. */
private data class PtRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun lerp(o: PtRect, t: Float) = PtRect(x + (o.x - x) * t, y + (o.y - y) * t, w + (o.w - w) * t, h + (o.h - h) * t)
}

/** Grid geometry of the compact page (T-CC) for a screen [screenWpt] wide. */
private class CcGrid(screenWpt: Float) {
    val cell = DesignTokens.ccCell.value
    val gx = DesignTokens.ccGapX.value
    val gy = DesignTokens.ccGapY.value
    val width = 4 * cell + 3 * gx
    val left = (screenWpt - width) / 2
    val top = DesignTokens.ccTop.value
    fun slot(col: Int, row: Int, cols: Int = 1, rows: Int = 1) =
        PtRect(left + col * (cell + gx), top + row * (cell + gy), cols * cell + (cols - 1) * gx, rows * cell + (rows - 1) * gy)
    val connectivity get() = slot(0, 0, 2, 2)
    val media get() = slot(2, 0, 2, 2)
    val volume get() = slot(2, 2, 1, 2)
    val brightness get() = slot(3, 2, 1, 2)
    fun from(m: CcExpand) = when (m) { CcExpand.Connectivity -> connectivity; CcExpand.Media -> media; CcExpand.Volume -> volume; CcExpand.Brightness -> brightness }
    fun to(m: CcExpand, screenWpt: Float): PtRect = when (m) {
        CcExpand.Connectivity -> PtRect(left, CcLayout.EXP_TOP, width,
            3 * CcLayout.EXP_ROW + 2 * CcLayout.EXP_TILE + 5 * CcLayout.EXP_GAP + CcLayout.EXP_ROW)
        CcExpand.Media -> PtRect((screenWpt - CcLayout.MEDIA_W) / 2, CcLayout.MEDIA_Y, CcLayout.MEDIA_W, CcLayout.MEDIA_H)
        CcExpand.Volume, CcExpand.Brightness -> PtRect((screenWpt - CcLayout.BIG_SLIDER_W) / 2, CcLayout.BIG_SLIDER_Y, CcLayout.BIG_SLIDER_W, CcLayout.BIG_SLIDER_H)
    }
}

/** Places a composable at a rect in pt (relayout per frame only for the morphing module). */
private fun Modifier.at(density: Float, r: () -> PtRect) = this
    .offset { val q = r(); IntOffset((q.x * density).roundToInt(), (q.y * density).roundToInt()) }
    .layout { m, _ ->
        val q = r(); val w = (q.w * density).roundToInt().coerceAtLeast(1); val h = (q.h * density).roundToInt().coerceAtLeast(1)
        val p = m.measure(Constraints.fixed(w, h)); layout(w, h) { p.place(0, 0) }
    }

private fun Modifier.at(r: PtRect) = this
    .offset(r.x.dp, r.y.dp)
    .requiredSize(r.w.dp, r.h.dp)

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
    val e = { model.expand.value }
    val screenPt = screenH / density
    val wPt = screenW / density
    val grid = CcGrid(wPt)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p() }
            .background(Color.Black.copy(alpha = DesignTokens.ccDim.value))
            // empty area: tap closes (or shrinks a big module), a drag follows the finger (up closes)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(c.uptimeMillis, c.position)
                        val dy = c.position.y - down.position.y
                        if (!moved && model.expanded == null && abs(dy) > viewConfiguration.touchSlop) moved = true
                        if (moved) { model.dragOpen(dy / density, screenPt); c.consume() }
                        if (!c.pressed) break
                    }
                    if (!moved) { if (model.expanded != null) model.collapse() else model.hide() }
                    else {
                        val v = tracker.calculateVelocity().y / density
                        if (v < -Motion.ccFlick.value || model.progress.value < 1f - Motion.ccCommit.value) model.hide(v) else model.show(v)
                    }
                }
            }
    )
    // everything slides in from above, blurred, and follows the pull (M-CC)
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
        // compact page: fades and blurs away while a module is big (M-CC-EXPAND)
        Box(Modifier.fillMaxSize().graphicsLayer {
            val x = e().coerceIn(0f, 1f)
            alpha = 1f - x
            val b = x * Motion.ccExpandBlur.value * density
            renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
        }) {
            CompactPage(context, model, grid)
        }
        model.expanded?.let { m -> Expanded(context, model, m, grid, density, wPt) }
    }
}

// ── compact page ─────────────────────────────────────────────────────────────────────

@Composable
private fun CompactPage(context: Context, model: ControlCenterModel, g: CcGrid) {
    val c = SystemControls
    val hidden = model.expanded
    val bs = DesignTokens.ccButton.value
    val by = DesignTokens.ccButtonY.value - bs / 2
    val fill = Color.White.copy(alpha = DesignTokens.ccFill.value)
    Glyph(Modifier.at(PtRect(g.left - CcLayout.BUTTON_OUT, by, bs, bs)).ccGlass(CircleShape, fill), Icons.plus, Color.White, 0.55f)
    Glyph(Modifier.at(PtRect(g.left + g.width - bs + CcLayout.BUTTON_OUT, by, bs, bs)).ccGlass(CircleShape, fill)
        .ccPress(onTap = { RootShell.run("input keyevent --longpress 26") }), Icons.power, Color.White, 0.6f)
    StatusRow(Modifier.at(PtRect(g.left + 4f, DesignTokens.ccStatusY.value - 10f, g.width - 8f, 20f)))

    val view = LocalView.current
    fun big(m: CcExpand) { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); model.expandModule(m) }
    fun vis(m: CcExpand) = if (hidden == m) Modifier.graphicsLayer { alpha = 0f } else Modifier

    Box(Modifier.at(g.connectivity).then(vis(CcExpand.Connectivity))) { ConnectivityCompact(context) { big(CcExpand.Connectivity) } }
    Box(Modifier.at(g.media).then(vis(CcExpand.Media)).ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp))
        .ccPress(onTap = {}, onLong = { big(CcExpand.Media) })) { MediaCompact() }
    RoundToggle(Modifier.at(g.slot(0, 2)), Icons.rotationLock, c.rotationLock.value, activeGlyph = RED) { c.toggleRotationLock(context) }
    RoundToggle(Modifier.at(g.slot(1, 2)), if (c.silent.value) Icons.bellOff else Icons.bell, c.silent.value, activeGlyph = RED) { c.toggleSilent(context) }
    FocusModule(Modifier.at(g.slot(0, 3, 2, 1)), c.dnd.value) { c.toggleDnd(context) }
    Slider(Modifier.at(g.volume).then(vis(CcExpand.Volume)), c.volume.floatValue, Icons.speaker, BLUE,
        onLong = { big(CcExpand.Volume) }) { v -> c.setVolume(context, v) }
    Slider(Modifier.at(g.brightness).then(vis(CcExpand.Brightness)), c.brightness.floatValue, Icons.sun, YELLOW,
        onLong = { big(CcExpand.Brightness) }) { v -> c.setBrightness(context, v) }
    RoundToggle(Modifier.at(g.slot(0, 4)), Icons.torch, c.torch.value, activeGlyph = BLUE) { c.toggleTorch(context) }
    RoundToggle(Modifier.at(g.slot(1, 4)), Icons.mirror, false) { launch(context, Intent(android.provider.Settings.ACTION_CAST_SETTINGS)) }
    RoundToggle(Modifier.at(g.slot(2, 4)), Icons.contrast, c.darkMode.value, activeGlyph = Color.Black) { c.toggleDarkMode(context) }
    RoundToggle(Modifier.at(g.slot(3, 4)), Icons.camera, false) { launch(context, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
    RoundToggle(Modifier.at(g.slot(0, 5)), Icons.battery, c.batterySaver.value, activeGlyph = ORANGE) { c.toggleBatterySaver(context) }
    RoundToggle(Modifier.at(g.slot(1, 5)), Icons.timer, false) { launch(context, Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS)) }
    RoundToggle(Modifier.at(g.slot(2, 5)), Icons.calculator, false) {
        launch(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)) }
    RoundToggle(Modifier.at(g.slot(3, 5)), Icons.alarm, false) { launch(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
}

/** Carrier with signal bars on the left, battery on the right (like the original's own status row). */
@Composable
private fun StatusRow(m: Modifier) {
    val c = SystemControls
    val info by io.github.janhurn9400.liquidy.data.rememberStatusInfo()
    val style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
    Box(m) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            if (info.cell >= 0) {
                Canvas(Modifier.size(18.dp, 12.dp)) {
                    val bw = size.width / 4 * 0.7f
                    for (i in 0 until 4) {
                        val h = size.height * (0.4f + 0.2f * i)
                        drawRoundRect(Color.White.copy(alpha = if (i < info.cell.coerceAtMost(4)) 1f else 0.35f),
                            Offset(i * size.width / 4, size.height - h), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 3))
                    }
                }
                Spacer(Modifier.width(5.dp))
            }
            BasicText(c.carrier.value, style = style)
            if (c.wifiConnected.value) { Spacer(Modifier.width(6.dp)); Glyph(Modifier.size(16.dp), Icons.wifi, Color.White, 1f) }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            BasicText("${c.batteryPercent.value} %", style = style)
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.size(25.dp, 12.dp)) {
                val body = Size(size.width - 2.5f * density, size.height)
                val r = androidx.compose.ui.geometry.CornerRadius(3.5f * density)
                drawRoundRect(Color.White.copy(alpha = 0.4f), size = body, cornerRadius = r, style = Stroke(1f * density))
                val inset = 2f * density
                val fillW = (body.width - 2 * inset) * (c.batteryPercent.value / 100f)
                drawRoundRect(if (c.charging.value) GREEN else Color.White, Offset(inset, inset), Size(fillW, body.height - 2 * inset),
                    androidx.compose.ui.geometry.CornerRadius(2f * density))
                drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(body.width + 0.8f * density, size.height * 0.33f), Size(1.6f * density, size.height * 0.34f),
                    androidx.compose.ui.geometry.CornerRadius(1f * density))
            }
        }
    }
}

/**
 * Compact connectivity platter like the original: big circles airplane (top left), location (top right, where the
 * original has its blue AirDrop) and Wi-Fi (bottom left); bottom right four small ones: mobile data, Bluetooth, hotspot, NFC.
 */
@Composable
private fun ConnectivityCompact(context: Context, onLong: () -> Unit) {
    val c = SystemControls
    Box(Modifier.fillMaxSize().ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp)).ccPress(onTap = {}, onLong = onLong)) {
        val d = DesignTokens.ccInner.value
        val inset = DesignTokens.ccInnerInset.value
        val span = 2 * DesignTokens.ccCell.value + DesignTokens.ccGapY.value
        val far = span - inset - d
        @Composable
        fun Circle(x: Float, y: Float, size: Float, icon: Path, on: Boolean, color: Color, share: Float, tap: () -> Unit) = Glyph(
            Modifier.offset(x.dp, y.dp).size(size.dp).clip(CircleShape)
                .background(if (on) color else Color.White.copy(alpha = DesignTokens.ccFill.value))
                .ccPress(onTap = tap, onLong = onLong),
            icon, Color.White, share)
        Circle(inset, inset, d, Icons.airplane, c.airplane.value, ORANGE, 0.5f) { c.toggleAirplane(context) }
        Circle(far, inset, d, Icons.location, c.location.value, BLUE, 0.46f) { c.toggleLocation(context) }
        Circle(inset, far, d, Icons.wifi, c.wifi.value, BLUE, 0.5f) { c.toggleWifi(context) }
        val cx = far + d / 2; val o = CcLayout.QUAD_OFFSET; val q = CcLayout.QUAD_D
        Circle(cx - o - q / 2, cx - o - q / 2, q, Icons.cellular, c.mobileData.value, GREEN, 0.55f) { c.toggleMobileData(context) }
        Circle(cx + o - q / 2, cx - o - q / 2, q, Icons.bluetooth, c.bluetooth.value, BLUE, 0.6f) { c.toggleBluetooth(context) }
        Circle(cx - o - q / 2, cx + o - q / 2, q, Icons.link, c.hotspot.value, GREEN, 0.6f) { launch(context, Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) }
        Circle(cx + o - q / 2, cx + o - q / 2, q, Icons.nfc, c.nfc.value, BLUE, 0.55f) { c.toggleNfc(context) }
    }
}

@Composable
private fun MediaCompact() {
    val media = SystemControls.media.value
    val inset = DesignTokens.ccInnerInset.value
    Box(Modifier.fillMaxSize()) {
        // output button top right like the original
        Glyph(Modifier.align(Alignment.TopEnd).padding(top = inset.dp, end = inset.dp).size(34.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = DesignTokens.ccFill.value)), Icons.output, Color.White, 0.5f)
        Column(Modifier.fillMaxSize().padding(inset.dp)) {
            Artwork(media, DesignTokens.ccArt.value)
            Spacer(Modifier.weight(1f))
            val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            BasicText(media?.title?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.cc_not_playing), style = title, maxLines = 1, softWrap = false)
            BasicText(media?.artist.orEmpty(), style = title.copy(fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.55f)), maxLines = 1, softWrap = false)
            Spacer(Modifier.size(6.dp))
            TransportRow(media, 26f)
        }
    }
}

@Composable
private fun Artwork(media: SystemControls.Media?, sizePt: Float) {
    val bmp = media?.art
    val shape = RoundedCornerShape((sizePt * ART_CORNER).dp)
    if (bmp != null) Image(remember(bmp) { bmp.asImageBitmap() }, null, Modifier.size(sizePt.dp).clip(shape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(sizePt.dp).clip(shape).background(Color.White.copy(alpha = DesignTokens.ccFill.value)))
}

@Composable
private fun TransportRow(media: SystemControls.Media?, glyphPt: Float, playPt: Float = glyphPt) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val t = media?.controller?.transportControls
        Glyph(Modifier.size(glyphPt.dp).ccPress(onTap = { t?.skipToPrevious() }), Icons.previous, Color.White, 0.9f)
        Glyph(Modifier.size(playPt.dp).ccPress(onTap = { if (media?.playing == true) t?.pause() else t?.play() }),
            if (media?.playing == true) Icons.pause else Icons.play, Color.White, 0.9f)
        Glyph(Modifier.size(glyphPt.dp).ccPress(onTap = { t?.skipToNext() }), Icons.next, Color.White, 0.9f)
    }
}

@Composable
private fun RoundToggle(m: Modifier, icon: Path, on: Boolean, activeGlyph: Color = Color.Black, tap: () -> Unit) {
    Glyph(m.ccGlass(CircleShape, if (on) Color.White else Color.White.copy(alpha = DesignTokens.ccFill.value)).ccPress(onTap = tap),
        icon, if (on) activeGlyph else Color.White, 0.42f)
}

@Composable
private fun FocusModule(m: Modifier, on: Boolean, tap: () -> Unit) {
    Box(m.ccGlass(CircleShape, if (on) Color.White else Color.White.copy(alpha = DesignTokens.ccFill.value)).ccPress(onTap = tap)) {
        Row(Modifier.fillMaxSize().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Modifier.size(38.dp).clip(CircleShape).background(if (on) PURPLE else Color.White.copy(alpha = 0.9f)), Icons.moon, if (on) Color.White else PURPLE, 0.6f)
            Spacer(Modifier.size(10.dp))
            BasicText(stringResource(R.string.cc_focus), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (on) Color.Black else Color.White))
            Spacer(Modifier.size(5.dp))
            ChevronUpDown(Modifier.size(8.dp, 12.dp), if (on) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.6f))
        }
    }
}

/**
 * Slider (1×2 compact or the big one): white level from the bottom, glyph near the bottom. Dragging moves the level
 * relatively (like the original); holding still for M-CC-PRESS.long opens it big ([onLong]).
 */
@Composable
private fun Slider(m: Modifier, value: Float, icon: Path, glyph: Color, glyphPt: Float = 28f, onLong: (() -> Unit)? = null, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(value)
    val longNow by rememberUpdatedState(onLong)
    val change by rememberUpdatedState(onChange)
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(1f) }
    Box(m.graphicsLayer { scaleX = press.value; scaleY = press.value }
        .ccGlass(CircleShape)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                scope.launch { press.animateTo(Motion.ccPressScale.value, Motion.ccPress.spec()) }
                var v = current
                var dragging = false
                var expanded = false
                val longJob = longNow?.let { l -> scope.launch { delay(Motion.ccLongPress.value.toLong()); if (!dragging) { expanded = true; l() } } }
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    if (expanded) { c.consume(); continue }
                    if (!dragging && abs(c.position.y - down.position.y) > viewConfiguration.touchSlop) { dragging = true; longJob?.cancel() }
                    if (dragging) { v = (v - (c.position.y - c.previousPosition.y) / size.height).coerceIn(0f, 1f); change(v); c.consume() }
                }
                longJob?.cancel()
                scope.launch { press.animateTo(1f, Motion.ccPress.spec()) }
            }
        }
        .drawBehind {
            val h = size.height * value.coerceIn(0f, 1f)
            drawRect(Color.White.copy(alpha = SLIDER_FILL), Offset(0f, size.height - h), Size(size.width, h))
        }) {
        Glyph(Modifier.align(Alignment.BottomCenter).offset(y = (-(CcLayout.SLIDER_GLYPH_FROM_BOTTOM - glyphPt / 2)).dp).size(glyphPt.dp), icon, glyph, 0.95f)
    }
}

// ── expanded modules ─────────────────────────────────────────────────────────────────

@Composable
private fun Expanded(context: Context, model: ControlCenterModel, m: CcExpand, g: CcGrid, density: Float, wPt: Float) {
    val from = g.from(m)
    val to = g.to(m, wPt)
    val e = { model.expand.value }
    val rect = { from.lerp(to, e()) }
    val c = SystemControls
    when (m) {
        CcExpand.Volume, CcExpand.Brightness -> {
            val bright = m == CcExpand.Brightness
            // glyph above the big slider and (brightness) three round buttons below fade in with it
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((e() - 0.2f) / 0.6f).coerceIn(0f, 1f) }) {
                Glyph(Modifier.at(PtRect((wPt - CcLayout.BIG_GLYPH) / 2, CcLayout.BIG_GLYPH_Y - CcLayout.BIG_GLYPH / 2, CcLayout.BIG_GLYPH, CcLayout.BIG_GLYPH)),
                    if (bright) Icons.sun else Icons.speaker, Color.White, 0.9f)
                if (bright) {
                    val items = listOf(
                        Triple(Icons.contrast, R.string.cc_dark_mode, c.darkMode.value) to { c.toggleDarkMode(context) },
                        Triple(Icons.moon, R.string.cc_night_light, c.nightLight.value) to { c.toggleNightLight(context) },
                        Triple(Icons.brightnessAuto, R.string.cc_auto_brightness, c.autoBrightness.value) to { c.toggleAutoBrightness(context) },
                    )
                    items.forEachIndexed { i, (t, tap) ->
                        val cx = wPt / 2 + (i - 1) * CcLayout.BIG_BUTTON_PITCH
                        val d = CcLayout.BIG_BUTTON_D
                        RoundToggle(Modifier.at(PtRect(cx - d / 2, CcLayout.BIG_BUTTON_Y - d / 2, d, d)), t.first, t.third) { tap() }
                        Label(Modifier.at(PtRect(cx - 50f, CcLayout.BIG_BUTTON_Y + d / 2 + 6f, 100f, 34f)), stringResource(t.second),
                            stringResource(if (t.third) R.string.cc_on else R.string.cc_off), TextAlign.Center, 12.sp)
                    }
                }
            }
            // the slider itself morphs from its slot to the big capsule (no crossfade — it's the same object)
            Slider(Modifier.at(density, rect), if (bright) c.brightness.floatValue else c.volume.floatValue,
                if (bright) Icons.sun else Icons.speaker, if (bright) YELLOW else BLUE, glyphPt = 28f + 14f * e().coerceIn(0f, 1f)) { v ->
                if (bright) c.setBrightness(context, v) else c.setVolume(context, v)
            }
        }
        CcExpand.Media -> {
            Box(Modifier.at(density, rect).ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp))) {
                // compact content fades out in place, the big one fades in grown from the slot (scaled with the panel)
                Box(Modifier.requiredSize(from.w.dp, from.h.dp).align(Alignment.TopStart).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val s = rect().w / from.w; scaleX = s; scaleY = s
                    alpha = (1f - e() / 0.35f).coerceIn(0f, 1f)
                }) { MediaCompact() }
                Box(Modifier.requiredSize(to.w.dp, to.h.dp).align(Alignment.TopStart).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val s = rect().w / to.w; scaleX = s; scaleY = s
                    alpha = ((e() - 0.25f) / 0.5f).coerceIn(0f, 1f)
                }) { MediaBig(context) }
            }
        }
        CcExpand.Connectivity -> {
            // the platter grows and dissolves into the separate big modules, which grow with it
            Box(Modifier.at(density, rect).graphicsLayer { alpha = (1f - e() / 0.6f).coerceIn(0f, 1f) }
                .ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp))) {
                Box(Modifier.requiredSize(from.w.dp, from.h.dp).align(Alignment.TopStart).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val s = rect().w / from.w; scaleX = s; scaleY = s
                    alpha = (1f - e() / 0.3f).coerceIn(0f, 1f)
                }) { ConnectivityCompact(context) {} }
            }
            Box(Modifier.at(density, rect)) {
                Box(Modifier.requiredSize(to.w.dp, to.h.dp).align(Alignment.TopStart).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val r = rect(); scaleX = r.w / to.w; scaleY = r.h / to.h
                    alpha = ((e() - 0.15f) / 0.5f).coerceIn(0f, 1f)
                }) { ConnectivityBig(context, to.w) }
            }
        }
    }
}

@Composable
private fun ConnectivityBig(context: Context, w: Float) {
    val c = SystemControls
    val gap = CcLayout.EXP_GAP
    val row = CcLayout.EXP_ROW
    val tile = CcLayout.EXP_TILE
    val tw = (w - gap) / 2
    fun s(on: Boolean) = if (on) R.string.cc_on else R.string.cc_off
    var y = 0f
    Capsule(PtRect(0f, y, w, row), Icons.airplane, ORANGE, c.airplane.value, stringResource(R.string.cc_airplane), stringResource(s(c.airplane.value))) { c.toggleAirplane(context) }
    y += row + gap
    Tile(PtRect(0f, y, tw, tile), Icons.wifi, BLUE, c.wifi.value, stringResource(R.string.cc_wifi),
        stringResource(if (!c.wifi.value) R.string.cc_off else if (c.wifiConnected.value) R.string.cc_connected else R.string.cc_not_connected)) { c.toggleWifi(context) }
    Tile(PtRect(tw + gap, y, tw, tile), Icons.location, BLUE, c.location.value, stringResource(R.string.cc_location), stringResource(s(c.location.value))) { c.toggleLocation(context) }
    y += tile + gap
    Tile(PtRect(0f, y, tw, tile), Icons.cellular, GREEN, c.mobileData.value, stringResource(R.string.cc_mobile_data),
        c.carrier.value.takeIf { it.isNotEmpty() && c.mobileData.value } ?: stringResource(s(c.mobileData.value))) { c.toggleMobileData(context) }
    Tile(PtRect(tw + gap, y, tw, tile), Icons.bluetooth, BLUE, c.bluetooth.value, stringResource(R.string.cc_bluetooth), stringResource(s(c.bluetooth.value))) { c.toggleBluetooth(context) }
    y += tile + gap
    Capsule(PtRect(0f, y, w, row), Icons.link, GREEN, c.hotspot.value, stringResource(R.string.cc_hotspot), stringResource(s(c.hotspot.value))) {
        launch(context, Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) }
    y += row + gap
    Capsule(PtRect(0f, y, w, row), Icons.nfc, BLUE, c.nfc.value, stringResource(R.string.cc_nfc),
        stringResource(if (!c.hasNfc.value) R.string.cc_not_available else s(c.nfc.value))) { c.toggleNfc(context) }
    y += row + gap
    Capsule(PtRect(0f, y, w, row), Icons.dataSaver, ORANGE, c.dataSaver.value, stringResource(R.string.cc_data_saver), stringResource(s(c.dataSaver.value))) { c.toggleDataSaver(context) }
}

@Composable
private fun Capsule(r: PtRect, icon: Path, color: Color, on: Boolean, title: String, subtitle: String, tap: () -> Unit) {
    Box(Modifier.at(r).ccGlass(CircleShape).ccPress(onTap = tap)) {
        val d = CcLayout.EXP_ICON
        val inset = (r.h - d) / 2
        IconCircle(Modifier.offset(inset.dp, inset.dp), d, icon, color, on)
        Label(Modifier.padding(start = CcLayout.EXP_TEXT_X.dp).align(Alignment.CenterStart), title, subtitle)
    }
}

@Composable
private fun Tile(r: PtRect, icon: Path, color: Color, on: Boolean, title: String, subtitle: String, tap: () -> Unit) {
    Box(Modifier.at(r).ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp)).ccPress(onTap = tap)) {
        IconCircle(Modifier.offset(CcLayout.EXP_ICON_INSET.dp, CcLayout.EXP_ICON_INSET.dp), CcLayout.EXP_ICON, icon, color, on)
        Label(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 22.dp), title, subtitle)
    }
}

@Composable
private fun IconCircle(m: Modifier, d: Float, icon: Path, color: Color, on: Boolean) =
    Glyph(m.size(d.dp).clip(CircleShape).background(if (on) color else Color.White.copy(alpha = DesignTokens.ccFill.value * 1.6f)), icon, Color.White, 0.52f)

@Composable
private fun Label(m: Modifier, title: String, subtitle: String, align: TextAlign = TextAlign.Start, size: TextUnit = 16.sp) {
    Column(m, horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        BasicText(title, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = size, color = Color.White, textAlign = align), maxLines = 1)
        BasicText(subtitle, style = TextStyle(fontFamily = Inter, fontSize = size * 0.92f, color = Color.White.copy(alpha = 0.6f), textAlign = align), maxLines = 1)
    }
}

/** Big media panel (the original's music page): artwork, title, progress, transport, volume, output. */
@Composable
private fun MediaBig(context: Context) {
    val c = SystemControls
    val media = c.media.value
    val inset = CcLayout.MEDIA_INSET
    // position ticks while playing
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(media?.playing) { while (media?.playing == true) { now = android.os.SystemClock.elapsedRealtime(); delay(500) } }
    Column(Modifier.fillMaxSize().padding(start = inset.dp, end = inset.dp, top = (inset + 1f).dp)) {
        Artwork(media, CcLayout.MEDIA_ART)
        Spacer(Modifier.height(30.dp))
        val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
        BasicText(media?.title?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.cc_not_playing), style = title, maxLines = 1, softWrap = false)
        BasicText(media?.artist.orEmpty(), style = title.copy(fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.55f)), maxLines = 1, softWrap = false)
        Spacer(Modifier.height(18.dp))
        val dur = media?.duration ?: 0L
        val pos = media?.let { m -> if (m.playing) m.position + ((now - m.positionAt) * m.speed).toLong() else m.position }?.coerceIn(0L, dur.coerceAtLeast(0L)) ?: 0L
        Bar(Modifier.fillMaxWidth().height(4.dp), if (dur > 0) pos.toFloat() / dur else 0f)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth()) {
            val ts = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
            BasicText(time(pos), style = ts, modifier = Modifier.align(Alignment.CenterStart))
            BasicText("−" + time((dur - pos).coerceAtLeast(0L)), style = ts, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Spacer(Modifier.height(18.dp))
        TransportRow(media, 34f, 40f)
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Modifier.size(14.dp), Icons.speakerLow, Color.White.copy(alpha = 0.6f), 1f)
            Spacer(Modifier.width(10.dp))
            Bar(Modifier.weight(1f).height(6.dp), c.volume.floatValue)
            Spacer(Modifier.width(10.dp))
            Glyph(Modifier.size(18.dp), Icons.speaker, Color.White.copy(alpha = 0.6f), 1f)
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.align(Alignment.CenterHorizontally).height(32.dp).clip(CircleShape).background(Color.White.copy(alpha = DesignTokens.ccFill.value))
            .ccPress(onTap = { launch(context, Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Glyph(Modifier.size(16.dp), Icons.output, Color.White, 1f)
            Spacer(Modifier.width(6.dp))
            BasicText(stringResource(R.string.cc_output), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = Color.White))
        }
    }
}

@Composable
private fun Bar(m: Modifier, v: Float) = Canvas(m) {
    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
    drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = r)
    drawRoundRect(Color.White.copy(alpha = 0.9f), size = Size(size.width * v.coerceIn(0f, 1f), size.height), cornerRadius = r)
}

private fun time(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun ChevronUpDown(m: Modifier, color: Color) = Canvas(m) {
    val w = size.width; val h = size.height; val sw = 1.4f * density
    drawLine(color, Offset(0f, h * 0.38f), Offset(w / 2, h * 0.12f), sw, StrokeCap.Round)
    drawLine(color, Offset(w / 2, h * 0.12f), Offset(w, h * 0.38f), sw, StrokeCap.Round)
    drawLine(color, Offset(0f, h * 0.62f), Offset(w / 2, h * 0.88f), sw, StrokeCap.Round)
    drawLine(color, Offset(w / 2, h * 0.88f), Offset(w, h * 0.62f), sw, StrokeCap.Round)
}

private fun launch(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Translucent module glass: light veil, hairline and the object light rim (the blurred home screen shows through). */
private fun Modifier.ccGlass(shape: Shape, fill: Color = Color.White.copy(alpha = DesignTokens.ccFill.value)) =
    this.clip(shape).drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, fill)
        drawOutline(outline, Color.White.copy(alpha = HAIRLINE), style = Stroke(1.2f * density))
        drawObjectRim(this, shape)
    }

/** M-CC-PRESS: grows a little under the finger and springs back; tap and optional long press. */
@Composable
private fun Modifier.ccPress(onTap: () -> Unit, onLong: (() -> Unit)? = null): Modifier {
    val s = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLong)
    return this
        .graphicsLayer { scaleX = s.value; scaleY = s.value }
        .pointerInput(onLong != null) {
            detectTapGestures(
                onPress = {
                    scope.launch { s.animateTo(Motion.ccPressScale.value, Motion.ccPress.spec()) }
                    tryAwaitRelease()
                    scope.launch { s.animateTo(1f, Motion.ccPress.spec()) }
                },
                onTap = { tap() },
                onLongPress = if (onLong != null) { _ -> long?.invoke() } else null,
            )
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
    val location = p("M21,3L3,10.53v0.98l6.84,2.65L12.48,21h0.98L21,3z")
    val nfc = p("M20,2H4c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,20H4V4h16v16zM18,6h-5c-1.1,0 -2,0.9 -2,2v2.28c-0.6,0.35 -1,0.98 -1,1.72 0,1.1 0.9,2 2,2s2,-0.9 2,-2c0,-0.74 -0.4,-1.38 -1,-1.72V8h3v8H8V8h2V6H6v12h12V6z")
    val link = p("M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z")
    val dataSaver = p("M13,2.05v3.03c3.39,0.49 6,3.39 6,6.92 0,0.9 -0.18,1.75 -0.48,2.54l2.6,1.53c0.56,-1.24 0.88,-2.62 0.88,-4.07 0,-5.18 -3.95,-9.45 -9,-9.95zM12,19c-3.87,0 -7,-3.13 -7,-7 0,-3.53 2.61,-6.43 6,-6.92V2.05c-5.06,0.5 -9,4.76 -9,9.95 0,5.52 4.47,10 9.99,10 3.31,0 6.24,-1.61 8.06,-4.09l-2.6,-1.53C16.17,17.98 14.21,19 12,19z")
    val brightnessAuto = p("M10.85,12.65h2.3L12,9l-1.15,3.65zM20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69L12,23.31 15.31,20H20v-4.69L23.31,12 20,8.69zM14.3,16l-0.7,-2h-3.2l-0.7,2H7.8L11,7h2l3.2,9h-1.9z")
    val output = p("M6,22h12l-6,-6zM21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h4v-2H3V5h18v12h-4v2h4c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z")
    val bell = p("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z")
    val bellOff = p("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,11c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68c-0.15,0.03 -0.29,0.08 -0.42,0.12L18,13.68V11zM5.41,3.35L4,4.76l2.81,2.81C6.29,8.57 6,9.73 6,11v5l-2,2v1h14.24l1.74,1.74 1.41,-1.41L5.41,3.35z")
    val speaker = p("M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.850 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z")
    val speakerLow = p("M7,9v6h4l5,5V4l-5,5H7z")
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
    val rotationLock = p("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10c4.75,0 8.73,-3.31 9.75,-7.75h-2.07C18.7,17.6 15.6,20 12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8c2.21,0 4.2,0.9 5.65,2.35L15,9h7V2l-2.95,2.95C17.26,3.13 14.76,2 12,2zM14.5,11v-1c0,-1.38 -1.12,-2.5 -2.5,-2.5S9.5,8.62 9.5,10v1C8.950,11 8.5,11.45 8.5,12v3.5c0,0.55 0.45,1 1,1h5c0.55,0 1,-0.45 1,-1V12c0,-0.55 -0.45,-1 -1,-1zM13.3,11h-2.6v-1c0,-0.72 0.58,-1.3 1.3,-1.3s1.3,0.58 1.3,1.3v1z")
    val mirror = p("M2,5c0,-1.1 0.9,-2 2,-2h11c1.1,0 2,0.9 2,2v2h-2V5H4v8h2v2H4c-1.1,0 -2,-0.9 -2,-2V5zM9,11c0,-1.1 0.9,-2 2,-2h9c1.1,0 2,0.9 2,2v8c0,1.1 -0.9,2 -2,2h-9c-1.1,0 -2,-0.9 -2,-2v-8zM11,11v8h9v-8h-9z")
    val contrast = p("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20V4c4.41,0 8,3.59 8,8s-3.59,8 -8,8z")
    val battery = p("M4,7h14c1.1,0 2,0.9 2,2v6c0,1.1 -0.9,2 -2,2H4c-1.1,0 -2,-0.9 -2,-2V9c0,-1.1 0.9,-2 2,-2zM4,8.8c-0.11,0 -0.2,0.09 -0.2,0.2v6c0,0.11 0.09,0.2 0.2,0.2h14c0.11,0 0.2,-0.09 0.2,-0.2V9c0,-0.11 -0.09,-0.2 -0.2,-0.2H4zM5,10h5v4H5zM21,10h1v4h-1z")
    val timer = p("M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9h-2c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7 3.13,-7 7,-7V3zM12,3v6h-0.01l-0.99,-0.01V3zM11.3,12.7l-3.6,-4.9 0.8,-0.6 4.9,3.6c0.5,0.4 0.6,1.1 0.2,1.6 -0.4,0.5 -1.1,0.6 -1.6,0.2 -0.3,-0.2 -0.5,-0.5 -0.7,-0.9z")
    val calculator = p("M6,2h12c1.1,0 2,0.9 2,2v16c0,1.1 -0.9,2 -2,2H6c-1.1,0 -2,-0.9 -2,-2V4c0,-1.1 0.9,-2 2,-2zM7,5v4h10V5H7zM7,11v2h2v-2H7zM11,11v2h2v-2h-2zM15,11v2h2v-2h-2zM7,15v2h2v-2H7zM11,15v2h2v-2h-2zM15,15v4h2v-4h-2z")
    val alarm = p("M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z")
}

private val ORANGE = Color(0xFFFF9F0A)
private val GREEN = Color(0xFF30D158)
private val BLUE = Color(0xFF0A84FF)
private val RED = Color(0xFFFF453A)
private val YELLOW = Color(0xFFFFCC00)
private val PURPLE = Color(0xFF5E5CE6)
private const val SLIDER_FILL = 0.95f
private const val HAIRLINE = 0.16f
private const val ART_CORNER = 0.22f
private const val REFRESH_MS = 1000L
