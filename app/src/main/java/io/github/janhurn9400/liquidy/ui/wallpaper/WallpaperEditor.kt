package io.github.janhurn9400.liquidy.ui.wallpaper

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
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
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.GlassRim
import io.github.janhurn9400.liquidy.glass.Wallpaper
import io.github.janhurn9400.liquidy.glass.WallpaperKind
import io.github.janhurn9400.liquidy.glass.WallpaperRenderer
import io.github.janhurn9400.liquidy.glass.WallpaperSettings
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onSizeChanged

/**
 * Zustand des Wallpaper-Editors (recording6 7,7–40 s). Der Editor legt nur Rahmen + Bedienelemente um den EINEN
 * bestehenden Homescreen ([WallpaperEditorFrame]) — kein zweiter Homescreen, also kein Neuaufbau und kein Aufblitzen.
 */
@Stable
class WallpaperEditorState(initial: WallpaperSettings, initialBitmap: ImageBitmap) {
    var draft by mutableStateOf(initial)
    /** Bild zum Entwurf; startet mit dem aktuellen Wallpaper (nichts muss erst gerendert werden). */
    var draftBitmap by mutableStateOf(initialBitmap)
    internal var renderedFor by mutableStateOf<WallpaperSettings?>(initial)
    val appear = Animatable(0f)
    val sheetAnim = Animatable(0f)
    var sheet by mutableStateOf<WallpaperKind?>(null)
    var picker by mutableStateOf(false)
    var crop by mutableStateOf<String?>(null)
    internal var finishing = false
    internal var backdrop: com.kyant.backdrop.Backdrop? = null
    /** Entwurf ist fertig gerendert (Zuschnitt bleibt bis dahin stehen → kein Zwischenbild). */
    val ready: Boolean get() = renderedFor == draft
}

/**
 * Schwarzer Grund + Vorschau-Transformation (Homescreen schrumpft um die linke obere Ecke auf 0,7) um [content].
 * Ohne Editor ([state] = null) unverändert. Nimmt im Editor Schwarz + Vorschau als Glas-Quelle fürs Farbblatt auf.
 */
@Composable
fun WallpaperEditorFrame(state: WallpaperEditorState?, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    state?.backdrop = backdrop
    // Größe ohne Subcomposition merken (BoxWithConstraints würde den Homescreen neu aufbauen)
    var sizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(Modifier.fillMaxSize().onSizeChanged { sizePx = it }.then(if (state != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
        // immer komponiert (nur Deckkraft ändert sich) — sonst würde der Homescreen beim Öffnen neu aufgebaut
        // sofort voll schwarz (nicht einblenden): der Homescreen deckt anfangs alles ab, beim Schrumpfen erscheint nur Schwarz —
        // eine Überblendung ließ den hellen Fenstergrund als grauen Rahmen aufblitzen
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (state != null) 1f else 0f }.background(Color.Black))
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val a = state?.appear?.value ?: 0f
                    if (a <= 0f) return@graphicsLayer
                    val l = wpeLayout(sizePx.width / density, sizePx.height / density)
                    val s = 1f + (l.previewScale - 1f) * a
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = l.previewX * density * a
                    translationY = l.previewY * density * a
                    clip = true
                    shape = RoundedCornerShape(DesignTokens.wpePreviewCorner.value * density * a / s)
                }
        ) {
            content()
            if (state != null) {
                // Vorschau nicht bedienbar; Tippen schließt nur das Farbblatt. Bei offenem Blatt abgedunkelt.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = state.sheetAnim.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = SHEET_PREVIEW_DIM)))
                Box(Modifier.fillMaxSize().pointerInput(state) { detectTapGestures { state.sheet = null } })
            }
        }
    }
}

/**
 * Geometrie des Editors in pt. iPhone: Design-Token (recording6). Duo aufgeklappt [GEMESSEN 10:40, 84,5 s]: Vorschau
 * 0,70 mittig (x 143, y 100), Done links / Cancel rechts (Mitte 37 pt vom Rand, y 35,5), Leiste mittig bei y 606 mit
 * 46,5-pt-Kreisen im Abstand 67,5. Duo zugeklappt: iPhone-Maße mittig und am unteren Rand verankert.
 */
internal class WpeLayout(
    val previewScale: Float, val previewX: Float, val previewY: Float,
    val topY: Float, val topSide: Float, val doneLeft: Boolean,
    val barY: Float, val barFirstX: Float, val barPitch: Float, val dividerX: Float, val blurX: Float,
    val circle: Float, val labelY: Float,
)

private const val PHONE_W = 402f
private const val PHONE_H = 874f

internal fun wpeLayout(screenW: Float, screenH: Float): WpeLayout {
    val duo = io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle
    if (duo && screenW > screenH) {
        val s = DUO_PREVIEW_SCALE
        return WpeLayout(s, (screenW - s * screenW) / 2, DUO_PREVIEW_Y,
            DUO_TOP_Y, DUO_TOP_CENTER - DesignTokens.wpeButtonW.value / 2, doneLeft = true,
            DUO_BAR_Y, screenW / 2 - DUO_BAR_HALF_SPAN, DUO_BAR_PITCH,
            screenW / 2 - DUO_BAR_HALF_SPAN + 3 * DUO_BAR_PITCH + DUO_BAR_GAP, screenW / 2 - DUO_BAR_HALF_SPAN + 3 * DUO_BAR_PITCH + 2 * DUO_BAR_GAP,
            DUO_CIRCLE, DUO_BAR_Y + DUO_LABEL_BELOW)
    }
    if (duo) {
        val dx = (screenW - PHONE_W) / 2; val dy = screenH - PHONE_H
        val s = DesignTokens.wpePreviewScale.value
        val barY = DesignTokens.wpeBarY.value + dy
        val top = DesignTokens.wpeTopY.value + 20f
        return WpeLayout(s, (screenW - s * screenW) / 2, (top + (barY - 36f) - s * screenH) / 2,
            DesignTokens.wpeTopY.value, DesignTokens.wpeTopSide.value, doneLeft = false,
            barY, DesignTokens.wpeBarFirstX.value + dx, DesignTokens.wpeBarPitch.value,
            DesignTokens.wpeBarDividerX.value + dx, DesignTokens.wpeBarBlurX.value + dx,
            DesignTokens.wpeCircle.value, DesignTokens.wpeLabelY.value + dy)
    }
    return WpeLayout(DesignTokens.wpePreviewScale.value, DesignTokens.wpePreviewX.value, DesignTokens.wpePreviewY.value,
        DesignTokens.wpeTopY.value, DesignTokens.wpeTopSide.value, doneLeft = false,
        DesignTokens.wpeBarY.value, DesignTokens.wpeBarFirstX.value, DesignTokens.wpeBarPitch.value,
        DesignTokens.wpeBarDividerX.value, DesignTokens.wpeBarBlurX.value, DesignTokens.wpeCircle.value, DesignTokens.wpeLabelY.value)
}

/** Bedienelemente erst in der zweiten Hälfte sichtbar — beim Zurückwachsen nicht über dem Homescreen stehen lassen. */
private fun chromeAlpha(appear: Float) = ((appear - CHROME_FROM) / (1f - CHROME_FROM)).coerceIn(0f, 1f)
private const val CHROME_FROM = 0.45f

/** Bedienelemente des Editors: Cancel/Done, untere Leiste, Farbblatt, Fotos-Picker, Zuschnitt. */
@Composable
fun WallpaperEditorChrome(
    state: WallpaperEditorState,
    onDone: (WallpaperSettings, ImageBitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.appear.animateTo(1f, Motion.wpeAppear.spec()) }
    LaunchedEffect(state.sheet) { state.sheetAnim.animateTo(if (state.sheet != null) 1f else 0f, Motion.wpeSheet.spec()) }

    fun finish(apply: Boolean) {
        if (state.finishing) return // Doppeltippen auf Done/Cancel
        state.finishing = true
        scope.launch {
            state.sheet = null
            state.appear.animateTo(0f, Motion.wpeAppear.spec())
            if (apply) onDone(state.draft, state.draftBitmap) else onCancel()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        // Entwurf rendern; das bisherige Bild bleibt stehen, bis das neue fertig ist
        LaunchedEffect(state.draft, w, h) {
            val d = state.draft
            if (state.renderedFor == d) return@LaunchedEffect
            val bmp = WallpaperRenderer.render(context, d, w, h)
            state.draftBitmap = bmp
            state.renderedFor = d
        }
        val draft = state.draft
        val lay = wpeLayout(w / LocalDensity.current.density, h / LocalDensity.current.density)

        // ── Cancel / Done ──
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha(state.appear.value) * (1f - state.sheetAnim.value * SHEET_CHROME_FADE) }) {
            // Duo aufgeklappt: Done links, Cancel rechts
            TopButton(stringResource(R.string.cancel), alignLeft = !lay.doneLeft, layout = lay) { finish(false) }
            TopButton(stringResource(R.string.done), alignLeft = lay.doneLeft, layout = lay) { finish(true) }
        }

        // ── Untere Leiste ──
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha(state.appear.value) }) {
            BottomBar(
                lay = lay,
                draft = draft,
                photoThumb = if (draft.kind == WallpaperKind.Photo) state.draftBitmap else null,
                onPair = { state.draft = draft.copy(kind = WallpaperKind.Pair, blur = false); state.sheet = null },
                onColour = { state.draft = draft.copy(kind = WallpaperKind.Colour); state.sheet = WallpaperKind.Colour },
                onGradient = { state.draft = draft.copy(kind = WallpaperKind.Gradient); state.sheet = WallpaperKind.Gradient },
                onPhoto = { state.sheet = null; state.picker = true },
                onBlur = { if (draft.blurAvailable) state.draft = draft.copy(blur = !draft.blur) },
            )
        }

        // ── Farbblatt ──
        val backdrop = state.backdrop
        if (backdrop != null && (state.sheet != null || state.sheetAnim.value > 0.01f)) {
            ColourSheet(backdrop = backdrop, draft = draft, progress = { state.sheetAnim.value }, onChange = { state.draft = it })
        }

        // Fotos-Picker bleibt stehen, bis der Zuschnitt sein Bild hat (kein schwarzes Zwischenbild)
        if (state.picker) {
            PhotoPicker(onClose = { state.picker = false }, onPick = { uri -> state.crop = uri })
        }
        state.crop?.let { uri ->
            var applied by remember(uri) { mutableStateOf(false) }
            // nach „Done“ im Zuschnitt: stehen bleiben, bis der Entwurf gerendert ist
            LaunchedEffect(applied, state.ready) { if (applied && state.ready) state.crop = null }
            PhotoCrop(
                uri = uri,
                initial = if (draft.photoUri == uri) draft else WallpaperSettings(kind = WallpaperKind.Photo, photoUri = uri),
                onReady = { state.picker = false },
                onCancel = { state.crop = null; state.picker = true },
                onDone = { s ->
                    state.draft = s.copy(kind = WallpaperKind.Photo, blur = draft.blur && draft.kind == WallpaperKind.Photo)
                    applied = true
                },
            )
        }
    }
}

/**
 * Cancel/Done-Kapsel wie iOS (recording6 7,7 s): beide gleich breit (67,33 pt) und gleich stark, dunkel gefüllt,
 * heller Rand außen an der Kapsel.
 */
@Composable
internal fun TopButton(text: String, alignLeft: Boolean, layout: WpeLayout? = null, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Layout(
            content = {
                Box(
                    Modifier
                        .size(DesignTokens.wpeButtonW.dp, DesignTokens.wpeButtonH.dp)
                        .clip(CircleShape)
                        .background(EDITOR_BUTTON_FILL)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(text, style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.wpeButtonFont.value.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
                    GlassRim(CircleShape, Modifier.matchParentSize())
                }
            },
        ) { ms, c ->
            val p = ms[0].measure(Constraints())
            val side = (layout?.topSide ?: DesignTokens.wpeTopSide.value).dp.roundToPx()
            val x = if (alignLeft) side else c.maxWidth - side - p.width
            layout(c.maxWidth, c.maxHeight) { p.place(x, (layout?.topY ?: DesignTokens.wpeTopY.value).dp.roundToPx() - p.height / 2) }
        }
    }
}

@Composable
private fun BottomBar(
    lay: WpeLayout,
    draft: WallpaperSettings,
    photoThumb: ImageBitmap?,
    onPair: () -> Unit,
    onColour: () -> Unit,
    onGradient: () -> Unit,
    onPhoto: () -> Unit,
    onBlur: () -> Unit,
) {
    val colour = Color(WallpaperRenderer.toned(draft.color, draft.tone))
    val items = listOf(
        Triple(R.string.wp_pair, WallpaperKind.Pair, onPair),
        Triple(R.string.wp_colour, WallpaperKind.Colour, onColour),
        Triple(R.string.wp_gradient, WallpaperKind.Gradient, onGradient),
        Triple(R.string.wp_photo, WallpaperKind.Photo, onPhoto),
    )
    items.forEachIndexed { i, (label, kind, action) ->
        BarButton(
            lay = lay,
            centerX = lay.barFirstX + lay.barPitch * i,
            label = stringResource(label),
            selected = draft.kind == kind,
            onClick = action,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                when (kind) {
                    WallpaperKind.Pair -> drawCircle(Brush.linearGradient(listOf(Color(0xFFE4E2E0), Color(0xFFA9A7A6)),
                        Offset.Zero, Offset(size.width, size.height)), r)
                    WallpaperKind.Colour -> drawCircle(if (draft.kind == WallpaperKind.Colour) colour else INACTIVE_FILL, r)
                    WallpaperKind.Gradient -> drawCircle(
                        if (draft.kind == WallpaperKind.Gradient) Brush.verticalGradient(listOf(colour.copy(alpha = 0.7f), colour))
                        else Brush.verticalGradient(listOf(INACTIVE_FILL, INACTIVE_FILL.copy(alpha = 0.7f))), r)
                    WallpaperKind.Photo -> {
                        drawCircle(PHOTO_FILL, r)
                        if (photoThumb == null) photoGlyph(this)
                    }
                }
            }
            if (kind == WallpaperKind.Photo && photoThumb != null) {
                androidx.compose.foundation.Image(photoThumb, null, Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
    }
    // Trenner + Blur
    Canvas(Modifier.fillMaxSize()) {
        val x = lay.dividerX.dp.toPx()
        val cy = lay.barY.dp.toPx()
        val half = lay.circle.dp.toPx() * DIVIDER_HALF
        drawLine(DIVIDER_COLOR, Offset(x, cy - half), Offset(x, cy + half), DIVIDER_W.dp.toPx(), StrokeCap.Round)
    }
    val on = draft.blur && draft.blurAvailable
    BarButton(
        lay = lay,
        centerX = lay.blurX,
        label = stringResource(R.string.wp_blur),
        selected = false,
        dimmed = !draft.blurAvailable,
        onClick = onBlur,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(if (on) Color.White else INACTIVE_DARK, size.minDimension / 2)
            dropGlyph(this, if (on) Color.Black else Color.White.copy(alpha = if (draft.blurAvailable) 0.9f else 0.35f))
        }
    }
}

/** Kreis + Beschriftung der unteren Leiste; ausgewählt = weißer Ring mit Abstand (recording6 25,2 s). */
@Composable
private fun BarButton(lay: WpeLayout, centerX: Float, label: String, selected: Boolean, dimmed: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val d = lay.circle.dp
    Box(
        Modifier
            .offset { IntOffset((centerX.dp - d / 2).roundToPx(), (lay.barY.dp - d / 2).roundToPx()) }
            .size(d)
            .graphicsLayer { alpha = if (dimmed) DIMMED_ALPHA else 1f }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize().padding(if (selected) RING_GAP.dp else 0.dp).clip(CircleShape)) { content() }
        if (selected) Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White, size.minDimension / 2 - RING_W.dp.toPx() / 2, style = Stroke(RING_W.dp.toPx()))
        }
    }
    Layout(content = {
        BasicText(label, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.wpeLabelFont.value.sp,
            color = Color.White.copy(alpha = if (dimmed) DIMMED_ALPHA else 1f)))
    }) { ms, c ->
        val t = ms[0].measure(Constraints())
        layout(c.maxWidth, c.maxHeight) {
            t.place((centerX.dp.toPx() - t.width / 2).roundToInt(), lay.labelY.dp.roundToPx() - t[FirstBaseline])
        }
    }
}

private fun photoGlyph(scope: androidx.compose.ui.graphics.drawscope.DrawScope) = with(scope) {
    val s = size.width
    val sw = s * 0.045f
    // zwei versetzte Bilderrahmen
    drawRoundRect(Color.White, Offset(s * 0.30f, s * 0.28f), Size(s * 0.44f, s * 0.36f), CornerRadius(s * 0.06f), style = Stroke(sw))
    drawRoundRect(Color.White, Offset(s * 0.24f, s * 0.36f), Size(s * 0.44f, s * 0.36f), CornerRadius(s * 0.06f), style = Stroke(sw))
    drawCircle(Color.White, s * 0.035f, Offset(s * 0.36f, s * 0.47f))
}

private fun dropGlyph(scope: androidx.compose.ui.graphics.drawscope.DrawScope, color: Color) = with(scope) {
    val s = size.width
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(s * 0.5f, s * 0.27f)
        cubicTo(s * 0.58f, s * 0.40f, s * 0.66f, s * 0.48f, s * 0.66f, s * 0.58f)
        cubicTo(s * 0.66f, s * 0.68f, s * 0.58f, s * 0.75f, s * 0.5f, s * 0.75f)
        cubicTo(s * 0.42f, s * 0.75f, s * 0.34f, s * 0.68f, s * 0.34f, s * 0.58f)
        cubicTo(s * 0.34f, s * 0.48f, s * 0.42f, s * 0.40f, s * 0.5f, s * 0.27f)
        close()
    }
    drawPath(path, color)
}

private const val DUO_PREVIEW_SCALE = 0.70f
private const val DUO_PREVIEW_Y = 100f
private const val DUO_TOP_Y = 35.5f
private const val DUO_TOP_CENTER = 37f      // Kapsel-Mitte vom Rand
private const val DUO_BAR_Y = 606f
private const val DUO_BAR_PITCH = 67.5f
private const val DUO_BAR_GAP = 45f         // Photo → Trenner → Blur
private const val DUO_BAR_HALF_SPAN = 146f  // Bildschirmmitte − erster Kreis (329,5 bei 951 pt)
private const val DUO_CIRCLE = 46.5f
private const val DUO_LABEL_BELOW = 38f     // Grundlinie unter Kreis-Mitte
private val EDITOR_BUTTON_FILL = Color(0xFF161616)   // Cancel/Done auf Schwarz [GEMESSEN] recording6 7,7 s
private val INACTIVE_FILL = Color(0xFFBDBDBD)        // inaktive Farb-/Verlaufskreise (hellgrau)
private val INACTIVE_DARK = Color(0xFF2C2C2E)
private val PHOTO_FILL = Color(0xFF2C2C2E)
private val DIVIDER_COLOR = Color(0x66FFFFFF)
private const val DIVIDER_HALF = 0.6f
private const val DIVIDER_W = 1.5f
private const val RING_W = 2f
private const val RING_GAP = 4f
private const val DIMMED_ALPHA = 0.35f
private const val SHEET_PREVIEW_DIM = 0.45f // Vorschau bei offenem Farbblatt abgedunkelt (recording6 16,8 s)
private const val SHEET_CHROME_FADE = 0.6f  // Cancel/Done blassen bei offenem Blatt ab
