package io.github.janhurn9400.liquidy.ui.widgets

import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.draggable
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Widget picker in iPhone Duo style [MEASURED recording 10:40, 33.6 s / 40.3 s, 0.634 pt/px]:
 * light sheet 461 pt wide, centred, top 92 pt; grabber; grey search field (29 pt margin, 43 pt tall, top +33 pt);
 * app list (icon 48 pt, row pitch 72 pt, name bold 17 pt, separator from the name).
 * Tap → detail sheet 20 pt lower on top: app header, title, description, size carousel with dots, green button.
 * All positions limited by the display size (other foldables).
 */
@Composable
internal fun DuoWidgetPicker(
    groups: List<Pair<String, List<PickerEntry>>>,
    query: String,
    onQuery: (String) -> Unit,
    appear: () -> Float,
    close: () -> Unit,
    onAdd: (PickerEntry, finished: () -> Unit) -> Unit,
) {
    var detail by remember { mutableStateOf<List<PickerEntry>?>(null) }
    var detailOut by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) } // px the sheet is pulled down by the finger (grabber / list at the top)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val screenW = constraints.maxWidth / density
        val screenH = constraints.maxHeight / density
        val sheetW = minOf(SHEET_W, screenW - 2 * MIN_SIDE)
        val left = (screenW - sheetW) / 2
        val top = minOf(SHEET_TOP, screenH * 0.14f)
        // Dimming + tapping beside it closes
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear() }.background(Color.Black.copy(alpha = DIM))
            .pointerInput(Unit) { detectTapGestures { close() } })
        Box(
            Modifier
                .offset { IntOffset((left * density).roundToInt(), ((top + (1f - appear()) * (screenH - top)) * density + drag.value).roundToInt()) }
                .width(Dp(sheetW)).height(Dp(screenH - top + CORNER))
                .sheetDismiss(drag, close)
                .shadow(16.dp, RoundedCornerShape(CORNER.dp), clip = false)
                .clip(RoundedCornerShape(CORNER.dp))
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // Sheet + list are the glass source of the search field: rows visibly run under it while scrolling
            val sheetBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().layerBackdrop(sheetBackdrop).background(SHEET_BG)) {
                LazyColumn(
                    contentPadding = PaddingValues(top = LIST_TOP.dp, bottom = (CORNER + 24f).dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(groups, key = { it.first + "|" + (it.second.first().provider?.provider?.packageName ?: "") }) { (name, entries) ->
                        AppRow(name, entries.first(), last = entries === groups.last().second) { detailOut = false; detail = entries }
                    }
                }
                // Scroll edge: above the field the list fades into the sheet colour, below the field it fades out softly
                Box(Modifier.fillMaxWidth().height((SEARCH_Y + SEARCH_H + EDGE_BELOW).dp).background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to SHEET_BG, ((SEARCH_Y + SEARCH_H / 2) / (SEARCH_Y + SEARCH_H + EDGE_BELOW)) to SHEET_BG, 1f to SHEET_BG.copy(alpha = 0f))))
            }
            Grabber()
            androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides sheetBackdrop) {
                SearchField(query, onQuery)
            }
            // Detail sheet on top → the list sheet dims (brightness 240 → 209) [40.3 s]
            if (detail != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = BACK_DIM)))
        }
        detail?.let { entries ->
            DetailSheet(entries, left, sheetW, top + DETAIL_DY, screenH, out = detailOut,
                onClose = { detail = null },
                onAdd = { e -> PickerLaunch.added = true; onAdd(e) { detailOut = true } })
        }
    }
}

/**
 * iOS sheet drag-to-dismiss: the sheet follows the finger downwards (upwards only with strong resistance), released far
 * or fast enough it closes, otherwise it springs back. Works from anywhere on the sheet; a list inside hands the pull
 * over once it is scrolled to the top (nested scroll).
 */
@Composable
private fun Modifier.sheetDismiss(drag: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, onDismiss: () -> Unit): Modifier {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = LocalDensity.current.density
    val dismissPx = DISMISS_DISTANCE * density
    fun moveBy(d: Float) = scope.launch {
        val next = drag.value + d
        drag.snapTo(if (next >= 0f) next else drag.value + d * DISMISS_UP_RESIST) // above the rest position: strong resistance
    }
    fun release(v: Float) = scope.launch {
        if (drag.value > dismissPx || (v > DISMISS_VELOCITY * density && drag.value > 0f)) onDismiss()
        else drag.animateTo(0f, Motion.wpeSheet.spec(), initialVelocity = v)
    }
    val nested = remember(drag) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            // sheet pulled down: an upward scroll first pushes the sheet back up
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset =
                if (drag.value > 0f && available.y < 0f) { val d = maxOf(available.y, -drag.value); moveBy(d); Offset(0f, d) } else Offset.Zero
            // list at the top and still pulling down → the sheet follows
            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset =
                if (available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) { moveBy(available.y); Offset(0f, available.y) } else Offset.Zero
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity =
                if (drag.value > 0f) { release(available.y); available } else androidx.compose.ui.unit.Velocity.Zero
        }
    }
    return this.nestedScroll(nested).draggable(
        androidx.compose.foundation.gestures.rememberDraggableState { moveBy(it) },
        androidx.compose.foundation.gestures.Orientation.Vertical,
        onDragStopped = { v -> release(v) },
    )
}

@Composable
private fun Grabber() = Box(Modifier.fillMaxWidth().padding(top = GRAB_Y.dp), contentAlignment = Alignment.TopCenter) {
    Box(Modifier.size(GRAB_W.dp, 5.dp).clip(CircleShape).background(Color(0x33000000)))
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    // Liquid Glass capsule on the light sheet [33.6 s: field 246 on sheet 242, light rim at the top, fine edge]
    io.github.janhurn9400.liquidy.glass.LiquidGlass(
        shape = io.github.janhurn9400.liquidy.glass.glassCapsule(),
        modifier = Modifier.padding(start = SIDE.dp, end = SIDE.dp, top = SEARCH_Y.dp).fillMaxWidth().height(SEARCH_H.dp),
        blur = FIELD_BLUR.dp, whiteTint = FIELD_WHITE, blackTint = 0f, rimAlpha = FIELD_RIM,
    ) {
        // fine grey edge like in the original (otherwise barely any contour on white)
        Box(Modifier.matchParentSize().border(0.75.dp, FIELD_OUTLINE, CircleShape))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            io.github.janhurn9400.liquidy.ui.home.MagnifierGlyph(20.dp, GREY_TEXT)
            Spacer(Modifier.width(8.dp))
            Box {
                if (query.isEmpty()) BasicText(stringResource(R.string.search_widgets), style = TextStyle(fontFamily = Inter, fontSize = 19.sp, color = GREY_TEXT))
                BasicTextField(query, onQuery, singleLine = true, cursorBrush = SolidColor(Color(0xFF007AFF)),
                    textStyle = TextStyle(fontFamily = Inter, fontSize = 19.sp, color = Color.Black), modifier = Modifier.width(170.dp))
            }
        }
    }
}

@Composable
private fun AppRow(name: String, e: PickerEntry, last: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(ROW.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Row(Modifier.fillMaxSize().padding(start = SIDE.dp), verticalAlignment = Alignment.CenterVertically) {
            PickerAppIcon(e, ICON)
            Spacer(Modifier.width((TEXT_X - SIDE - ICON).dp))
            BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.Black))
        }
        if (!last) Box(Modifier.align(Alignment.BottomStart).padding(start = TEXT_X.dp).fillMaxWidth().height(1.dp).background(SEPARATOR))
    }
}

/** App icon in the picker: Android provider → icon of its app; own widgets → a plain own glyph (no Apple icons). */
@Composable
internal fun PickerAppIcon(e: PickerEntry, sizePt: Float) {
    val shape = RoundedCornerShape((sizePt * 0.225f).dp)
    val context = LocalContext.current
    if (e.provider != null) {
        val img by produceState<ImageBitmap?>(null, e.provider) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.packageManager.getApplicationIcon(e.provider.provider.packageName).toBitmap(144, 144).asImageBitmap() }.getOrNull()
            }
        }
        Box(Modifier.size(sizePt.dp).clip(shape).background(Color(0xFFE5E5EA))) { img?.let { Image(it, null, Modifier.fillMaxSize()) } }
        return
    }
    if (e.iosType == IosWidgetTypes.WEATHER) {
        // own weather glyph: sky gradient + sun/cloud
        Box(Modifier.size(sizePt.dp).clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2E6FB7), Color(0xFF7FB2E5)))),
            contentAlignment = androidx.compose.ui.Alignment.Center) { WeatherIcon(2, true, null, (sizePt * 0.7f).dp) }
        return
    }
    val bg = when (e.iosType) { IosWidgetTypes.BATTERY -> Color(0xFF34C759); IosWidgetTypes.CLOCK -> Color(0xFF1C1C1E); else -> Color.White }
    Box(Modifier.size(sizePt.dp).clip(shape).background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width
            when (e.iosType) {
                IosWidgetTypes.BATTERY -> {
                    drawRoundRect(Color.White, Offset(s * 0.2f, s * 0.36f), Size(s * 0.54f, s * 0.28f), androidx.compose.ui.geometry.CornerRadius(s * 0.07f), style = Stroke(s * 0.05f))
                    drawRoundRect(Color.White, Offset(s * 0.25f, s * 0.41f), Size(s * 0.44f, s * 0.18f), androidx.compose.ui.geometry.CornerRadius(s * 0.04f))
                    drawRoundRect(Color.White, Offset(s * 0.76f, s * 0.45f), Size(s * 0.04f, s * 0.1f), androidx.compose.ui.geometry.CornerRadius(s * 0.02f))
                }
                IosWidgetTypes.CLOCK -> {
                    drawCircle(Color.White, s * 0.36f, center)
                    drawLine(Color.Black, center, center + Offset(0f, -s * 0.22f), s * 0.05f, StrokeCap.Round)
                    drawLine(Color.Black, center, center + Offset(s * 0.15f, s * 0.06f), s * 0.05f, StrokeCap.Round)
                }
                IosWidgetTypes.PHOTOS -> { // own Photos glyph: landscape (sun + mountains) in a frame
                    drawRoundRect(Color(0xFF8E8E93), Offset(s * 0.2f, s * 0.26f), Size(s * 0.6f, s * 0.48f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(s * 0.045f))
                    drawCircle(Color(0xFFFFB300), s * 0.06f, Offset(s * 0.36f, s * 0.4f))
                    val m = androidx.compose.ui.graphics.Path().apply { moveTo(s * 0.24f, s * 0.7f); lineTo(s * 0.44f, s * 0.48f); lineTo(s * 0.56f, s * 0.6f); lineTo(s * 0.64f, s * 0.52f); lineTo(s * 0.76f, s * 0.7f); close() }
                    drawPath(m, Color(0xFF34C759))
                }
                else -> { // calendar: red header bar + day number
                    drawRect(Color(0xFFFF3B30), Offset.Zero, Size(s, s * 0.28f))
                    drawCircle(Color(0xFF1C1C1E), s * 0.05f, Offset(s * 0.35f, s * 0.6f))
                    drawCircle(Color(0xFF1C1C1E), s * 0.05f, Offset(s * 0.5f, s * 0.6f))
                    drawCircle(Color(0xFF1C1C1E), s * 0.05f, Offset(s * 0.65f, s * 0.6f))
                }
            }
        }
    }
}

@Composable
private fun DetailSheet(
    entries: List<PickerEntry>, left: Float, sheetW: Float, top: Float, screenH: Float, out: Boolean,
    onClose: () -> Unit, onAdd: (PickerEntry) -> Unit,
) {
    androidx.compose.runtime.DisposableEffect(Unit) { PickerLaunch.added = false; onDispose { } }
    val appear = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    // closing slides the sheet down like opening (it used to vanish with a hard cut), then removes it
    LaunchedEffect(out, closing) {
        appear.animateTo(if (out || closing) 0f else 1f, Motion.wpeSheet.spec())
        if (closing) onClose()
    }
    val close = { closing = true }
    val drag = remember { Animatable(0f) }
    val pager = rememberPagerState { entries.size }
    val e = entries[pager.currentPage.coerceIn(0, entries.lastIndex)]
    val density = LocalDensity.current.density
    // Button: 448 pt below the sheet edge (Duo), kept above the bottom edge on short displays
    val buttonY = minOf(BUTTON_Y, screenH - top - BUTTON_H - BUTTON_BOTTOM)
    Box(
        Modifier
            .offset { IntOffset((left * density).roundToInt(), ((top + (1f - appear.value) * (screenH - top)) * density + drag.value).roundToInt()) }
            .width(Dp(sheetW)).height(Dp(screenH - top + CORNER))
            .sheetDismiss(drag, close)
            .shadow(20.dp, RoundedCornerShape(CORNER.dp), clip = false)
            .clip(RoundedCornerShape(CORNER.dp)).background(SHEET_BG)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Grabber()
        // Header: app icon + name left, × right
        Row(Modifier.padding(start = HEAD_X.dp, top = (HEAD_Y - HEAD_ICON / 2).dp), verticalAlignment = Alignment.CenterVertically) {
            PickerAppIcon(e, HEAD_ICON)
            Spacer(Modifier.width(10.dp))
            BasicText(e.title, maxLines = 1, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.Black))
        }
        Canvas(
            Modifier.align(Alignment.TopEnd).padding(top = (HEAD_Y - 14f).dp, end = (CLOSE_RIGHT - 14f).dp).size(28.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
        ) {
            val a = size.width * 0.3f; val sw = 2.dp.toPx()
            drawLine(Color.Black, Offset(a, a), Offset(size.width - a, size.height - a), sw, StrokeCap.Round)
            drawLine(Color.Black, Offset(size.width - a, a), Offset(a, size.height - a), sw, StrokeCap.Round)
        }
        Column(Modifier.fillMaxWidth().padding(top = TITLE_Y.dp, start = SIDE.dp, end = SIDE.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(e.subtitle, maxLines = 1, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.Black, textAlign = TextAlign.Center))
            Spacer(Modifier.height(6.dp))
            BasicText(widgetDescription(e), maxLines = 2, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = GREY_TEXT, textAlign = TextAlign.Center))
        }
        // Size carousel: current size centred, neighbours peek in at the edge
        val small = DesignTokens.widgetSmall.value
        // With a large size in the carousel it gets taller; the large widget is shown shrunk at its real aspect ratio
        val hasLarge = entries.any { it.rows >= 4 }
        val pagerH = if (hasLarge) small * LARGE_PAGER_GROW else small
        HorizontalPager(
            state = pager,
            pageSize = PageSize.Fill,
            contentPadding = PaddingValues(horizontal = PAGER_PEEK.dp),
            modifier = Modifier.fillMaxWidth().padding(top = (PREVIEW_CY - pagerH / 2).dp).height(pagerH.dp),
        ) { i ->
            val pe = entries[i]
            val wide = DesignTokens.widgetWide.value
            val w = if (pe.cols >= 4) wide else small
            val h = if (pe.rows >= 4) wide * LARGE_ASPECT else small
            // fits in height and width (neighbours peek in further)
            val fit = minOf(1f, pagerH / h, (sheetW - 2 * PAGER_PEEK) / w)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WidgetPreviewBox(pe, Modifier.requiredSize(w.dp, h.dp).graphicsLayer { scaleX = fit; scaleY = fit }
                    .then(if (i == pager.currentPage) Modifier.onGloballyPositioned { c -> PickerLaunch.previewCenter = c.boundsInRoot().center } else Modifier)
                    .graphicsLayer { alpha = if (PickerLaunch.added && i == pager.currentPage) 0f else 1f })
            }
        }
        if (entries.size > 1) Row(Modifier.align(Alignment.TopCenter).padding(top = DOTS_Y.dp), horizontalArrangement = Arrangement.spacedBy((DOT_PITCH - DOT_D).dp)) {
            entries.indices.forEach { k ->
                Box(Modifier.size(DOT_D.dp).clip(CircleShape).background(if (k == pager.currentPage) Color.Black else Color(0x4D000000)))
            }
        }
        Box(
            Modifier.padding(top = buttonY.dp, start = BUTTON_SIDE.dp, end = BUTTON_SIDE.dp).fillMaxWidth().height(BUTTON_H.dp)
                .clip(CircleShape).background(GREEN)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAdd(e) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp)) {
                    drawCircle(Color.White, size.width / 2)
                    val a = size.width * 0.27f; val sw = size.width * 0.12f
                    drawLine(GREEN, center + Offset(-a, 0f), center + Offset(a, 0f), sw, StrokeCap.Round)
                    drawLine(GREEN, center + Offset(0f, -a), center + Offset(0f, a), sw, StrokeCap.Round)
                }
                Spacer(Modifier.width(8.dp))
                BasicText(stringResource(R.string.add_widget), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Color.White))
            }
        }
    }
}

@Composable
private fun widgetDescription(e: PickerEntry): String = when (e.iosType) {
    IosWidgetTypes.BATTERY -> stringResource(R.string.widget_batteries_long)
    IosWidgetTypes.CLOCK -> stringResource(R.string.widget_clock_long)
    IosWidgetTypes.CALENDAR -> stringResource(R.string.widget_calendar_long)
    IosWidgetTypes.WEATHER -> stringResource(R.string.widget_weather_long)
    IosWidgetTypes.PHOTOS -> stringResource(R.string.widget_photos_long)
    IosWidgetTypes.REMINDERS -> stringResource(R.string.widget_reminders_long)
    else -> e.subtitle
}

// Dimensions [MEASURED] recording 10:40, 0.634 pt/px (display edges x 90–1590, y 108–1160 px)
private const val SHEET_W = 461f
private const val DISMISS_DISTANCE = 140f // pt pulled down → closes on release [ESTIMATED]
private const val DISMISS_VELOCITY = 900f // pt/s downwards → closes even if pulled less far [ESTIMATED]
private const val DISMISS_UP_RESIST = 0.15f
private const val MIN_SIDE = 4f
private const val SHEET_TOP = 92f
private const val CORNER = 30f
private const val DETAIL_DY = 20f
private const val GRAB_Y = 9f
private const val GRAB_W = 40f
private const val SEARCH_Y = 33f
private const val SEARCH_H = 43f
private const val SIDE = 29f
private const val LIST_TOP = 113f     // first row centre 137 pt below the edge − half a row
private const val ROW = 72f
private const val ICON = 48f
private const val TEXT_X = 94f
private const val HEAD_X = 20f
private const val HEAD_Y = 35f
private const val HEAD_ICON = 29f
private const val CLOSE_RIGHT = 32f
private const val TITLE_Y = 92f
private const val PREVIEW_CY = 295f
private const val LARGE_PAGER_GROW = 1.45f
private const val LARGE_ASPECT = 830f / 796f // iPhone large [MEASURED docs/reference/widgets/03]
private const val PAGER_PEEK = 70f
private const val DOTS_Y = 426f
private const val DOT_D = 7f
private const val DOT_PITCH = 18f
private const val BUTTON_Y = 448f
private const val BUTTON_H = 49f
private const val BUTTON_SIDE = 24f
private const val BUTTON_BOTTOM = 24f
private const val DIM = 0.12f
private val SHEET_BG = Color(0xFFF2F2F2)
private const val FIELD_BLUR = 8f
private const val FIELD_WHITE = 0.35f
private const val FIELD_RIM = 0.5f
private const val EDGE_BELOW = 12f
private val FIELD_OUTLINE = Color(0x1F000000)
private const val BACK_DIM = 0.13f
private val GREY_TEXT = Color(0xFF8E8E93)
private val SEPARATOR = Color(0xFFDCDCE0)
private val GREEN = Color(0xFF34C759)
