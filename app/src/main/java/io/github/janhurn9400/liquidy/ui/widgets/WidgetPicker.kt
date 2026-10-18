package io.github.janhurn9400.liquidy.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import io.github.janhurn9400.liquidy.data.WidgetSpec
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassCapsule
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Entry in the picker: own iOS widget or Android provider. */
internal class PickerEntry(
    val title: String,      // app/widget name (bold)
    val subtitle: String,   // description (grey)
    val cols: Int, val rows: Int,
    val iosType: String? = null,
    val provider: AppWidgetProviderInfo? = null,
)

/**
 * Widget picker (recording8 26–29 s): dark glass sheet from y ≈ 123 pt, grabber, "Search Widgets" glass capsule,
 * widgets at original size (small 2 side by side, wide one row), below them the app name bold + widget name grey.
 * Tap → add sheet (30 s) with a large preview and a blue "Add Widget".
 */
@Composable
fun WidgetPicker(onAdd: (WidgetSpec) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val host = LocalAndroidWidgetHost.current
    val scope = rememberCoroutineScope()
    val appear = remember { Animatable(0f) }
    var detail by remember { mutableStateOf<PickerEntry?>(null) }
    var detailOut by remember { mutableStateOf(false) } // the detail sheet slides out with the picker
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.wpeSheet.spec()) }
    fun close() = scope.launch { appear.animateTo(0f, Motion.wpeSheet.spec()); onClose() }

    val entries by produceState(emptyList<PickerEntry>(), host) {
        val ios = listOf(
            PickerEntry(context.getString(R.string.widget_calendar), context.getString(R.string.widget_calendar_desc), 2, 2, IosWidgetTypes.CALENDAR),
            PickerEntry(context.getString(R.string.widget_clock), context.getString(R.string.widget_clock_desc), 2, 2, IosWidgetTypes.CLOCK),
            PickerEntry(context.getString(R.string.widget_calendar), context.getString(R.string.widget_calendar_desc), 4, 2, IosWidgetTypes.CALENDAR),
            PickerEntry(context.getString(R.string.widget_batteries), context.getString(R.string.widget_batteries_desc), 2, 2, IosWidgetTypes.BATTERY),
            PickerEntry(context.getString(R.string.widget_weather), context.getString(R.string.widget_weather_desc), 2, 2, IosWidgetTypes.WEATHER),
            PickerEntry(context.getString(R.string.widget_weather), context.getString(R.string.widget_weather_desc), 4, 2, IosWidgetTypes.WEATHER),
            PickerEntry(context.getString(R.string.widget_weather), context.getString(R.string.widget_weather_desc), 4, 4, IosWidgetTypes.WEATHER),
            PickerEntry(context.getString(R.string.widget_photos), context.getString(R.string.widget_photos_desc), 2, 2, IosWidgetTypes.PHOTOS),
            PickerEntry(context.getString(R.string.widget_photos), context.getString(R.string.widget_photos_desc), 4, 4, IosWidgetTypes.PHOTOS),
            PickerEntry(context.getString(R.string.widget_reminders), context.getString(R.string.widget_reminders_desc), 2, 2, IosWidgetTypes.REMINDERS),
            PickerEntry(context.getString(R.string.widget_reminders), context.getString(R.string.widget_reminders_desc), 4, 2, IosWidgetTypes.REMINDERS),
            PickerEntry(context.getString(R.string.widget_reminders), context.getString(R.string.widget_reminders_desc), 4, 4, IosWidgetTypes.REMINDERS),
        )
        val android = withContext(Dispatchers.IO) {
            host?.providers().orEmpty().map { p ->
                val app = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(p.provider.packageName, 0)).toString() }.getOrDefault("")
                host!!.iosSizes(p).map { (c, r) -> PickerEntry(app, p.loadLabel(context.packageManager), c, r, provider = p) }
            }.flatten()
        }
        value = ios + android
    }
    val q = query.trim().lowercase()
    val shown = entries.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q) }
    // Rows: two small ones side by side, wide ones alone
    val rows = buildList {
        var pendingSmall: PickerEntry? = null
        shown.forEach { e ->
            if (e.cols >= 4) add(listOf(e))
            else if (pendingSmall == null) pendingSmall = e
            else { add(listOf(pendingSmall!!, e)); pendingSmall = null }
        }
        pendingSmall?.let { add(listOf(it)) }
    }

    // Duo style (unfolded and folded): light sheet with app list + detail sheet with size carousel [recording 10:40, 31–46 s]
    // One gallery for every layout: the newer sheet (size carousel, light sheet, glass only on the search field — the old
    // full-screen glass sheet re-recorded the whole home screen every frame and lagged on real phones)
    run {
        DuoWidgetPicker(
            // separated by app: don't mix own iOS widgets and an Android app of the same name (e.g. "Clock")
            groups = shown.groupBy { it.title to it.provider?.provider?.packageName }.entries.map { it.key.first to it.value }.sortedBy { it.first.lowercase() },
            query = query, onQuery = { query = it }, appear = { appear.value }, close = { close() },
            onAdd = { e, finished ->
                val p = e.provider
                if (p == null) { onAdd(WidgetSpec(e.iosType!!, e.cols, e.rows)); scope.launch { kotlinx.coroutines.delay(250); finished(); close() } }
                else (context as? Activity)?.let { act -> host?.add(act, p, e.cols to e.rows) { spec -> if (spec != null) { onAdd(spec); finished(); close() } } }
            },
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val screenH = constraints.maxHeight / density
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = DIM))
            .pointerInput(Unit) { detectTapGestures { close() } })
        // Wide display (Duo unfolded): sheet centred at iPhone width instead of screen-wide [ESTIMATED — no recording]
        val screenW = constraints.maxWidth / density
        val sheetW = if (screenW > screenH && io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle) WIDE_SHEET_W.coerceAtMost(screenW) else screenW
        Box(
            Modifier
                .offset { IntOffset(((screenW - sheetW) / 2 * density).roundToInt(), ((SHEET_TOP + (1f - appear.value) * (screenH - SHEET_TOP)) * density).roundToInt()) }
                .width(Dp(sheetW))
                .height(Dp(screenH - SHEET_TOP + SHEET_CORNER))
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            LiquidGlass(shape = glassRect(SHEET_CORNER.dp), modifier = Modifier.fillMaxSize(), blur = SHEET_BLUR.dp, blackTint = SHEET_DARK, whiteTint = SHEET_LIFT)
            LazyColumn(
                contentPadding = PaddingValues(top = LIST_TOP.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(rows.size) { i ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = SIDE.dp), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                        rows[i].forEach { e -> PickerCard(e) { detail = e } }
                    }
                }
            }
            // Grabber + search field (glass, stays at the top)
            Box(Modifier.offset(y = HANDLE_Y.dp).align(Alignment.TopCenter).size(HANDLE_W.dp, 5.dp).clip(CircleShape).background(Color(0x66FFFFFF)))
            // Search field: semi-transparent light grey on the sheet (iOS: field 57 vs. sheet 32 brightness), no glass rim
            Box(
                modifier = Modifier.offset(y = SEARCH_Y.dp).padding(horizontal = SIDE.dp).fillMaxWidth().height(SEARCH_H.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = SEARCH_FILL)),
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    io.github.janhurn9400.liquidy.ui.home.MagnifierGlyph(18.dp, Color(0x73FFFFFF))
                    Spacer(Modifier.width(8.dp))
                    Box {
                        if (query.isEmpty()) BasicText(stringResource(R.string.search_widgets), style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color(0x73FFFFFF)))
                        BasicTextField(query, { query = it }, singleLine = true, cursorBrush = SolidColor(Color.White),
                            textStyle = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.White), modifier = Modifier.width(180.dp))
                    }
                }
            }
        }
        detail?.let { e ->
            AddWidgetSheet(e, out = detailOut, onClose = { detail = null }) {
                val p = e.provider
                if (p == null) { onAdd(WidgetSpec(e.iosType!!, e.cols, e.rows)); scope.launch { kotlinx.coroutines.delay(250); detailOut = true; close() } } // the sheet stays until the widget has lifted off
                else (context as? Activity)?.let { act ->
                    host?.add(act, p, e.cols to e.rows) { spec -> if (spec != null) { onAdd(spec); close() } }
                }
            }
        }
    }
}

@Composable
private fun PickerCard(e: PickerEntry, onClick: () -> Unit) {
    val size = if (e.cols >= 4) DesignTokens.widgetWide.value else DesignTokens.widgetSmall.value
    Column(
        Modifier.width(size.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetPreview(e, Modifier.size(size.dp, DesignTokens.widgetSmall.dp))
        Spacer(Modifier.height(CAPTION_GAP.dp))
        BasicText(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center))
        BasicText(e.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = Color(0x8CFFFFFF), textAlign = TextAlign.Center))
    }
}

/** Preview: iOS widgets live, Android: the provider's preview image or the app icon on a dark area. */
@Composable
private fun WidgetPreview(e: PickerEntry, modifier: Modifier) = WidgetPreviewBox(e, modifier)

@Composable
internal fun WidgetPreviewBox(e: PickerEntry, modifier: Modifier) {
    val shape = glassRect(DesignTokens.widgetCorner.dp)
    Box(modifier.clip(shape).background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
        if (e.iosType != null) IosWidget(e.iosType, e.cols, e.rows, Modifier.fillMaxSize())
        else {
            val context = LocalContext.current
            val img by produceState<ImageBitmap?>(null, e.provider) {
                value = withContext(Dispatchers.IO) {
                    val p = e.provider ?: return@withContext null
                    val d = runCatching { p.loadPreviewImage(context, 0) ?: p.loadIcon(context, 0) }.getOrNull()
                    d?.let { runCatching { it.toBitmap(it.intrinsicWidth.coerceIn(1, 600), it.intrinsicHeight.coerceIn(1, 600)).asImageBitmap() }.getOrNull() }
                }
            }
            img?.let { Image(it, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
        }
    }
}

/** Add sheet (recording8 30 s): app header, title, description, preview at original size, blue "Add Widget". */
@Composable
private fun AddWidgetSheet(e: PickerEntry, out: Boolean = false, onClose: () -> Unit, onAdd: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) { PickerLaunch.added = false; onDispose { } }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(out) { appear.animateTo(if (out) 0f else 1f, Motion.wpeSheet.spec()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val screenH = constraints.maxHeight / density
        // Wide display (Duo unfolded): centred at iPhone width; the sheet tall enough that the button fits on screen
        val screenW = constraints.maxWidth / density
        val wide = screenW > screenH && io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle
        val sheetW = if (wide) WIDE_SHEET_W.coerceAtMost(screenW) else screenW
        val addTop = if (wide) ADD_TOP_WIDE else ADD_TOP
        val buttonY = if (wide) screenH - addTop - ADD_BOTTOM_ROOM - 48f else ADD_BUTTON_Y - ADD_TOP
        Box(
            Modifier
                .offset { IntOffset(((screenW - sheetW) / 2 * density).roundToInt(), ((addTop + (1f - appear.value) * (screenH - addTop)) * density).roundToInt()) }
                .width(Dp(sheetW)).height(Dp(screenH - addTop + SHEET_CORNER))
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            LiquidGlass(shape = glassRect(SHEET_CORNER.dp), modifier = Modifier.fillMaxSize(), blur = SHEET_BLUR.dp, blackTint = ADD_DARK, whiteTint = 0f)
            Column(Modifier.fillMaxWidth().padding(horizontal = SIDE.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(e.title, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White), modifier = Modifier.weight(1f))
                    androidx.compose.foundation.Canvas(Modifier.size(28.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
                        val a = size.width * 0.25f; val sw = 2.dp.toPx()
                        drawLine(Color.White, Offset(a, a), Offset(size.width - a, size.height - a), sw, StrokeCap.Round)
                        drawLine(Color.White, Offset(size.width - a, a), Offset(a, size.height - a), sw, StrokeCap.Round)
                    }
                }
                Spacer(Modifier.height(48.dp))
                BasicText(e.subtitle, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White, textAlign = TextAlign.Center))
                BasicText(e.title, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = Color(0x8CFFFFFF)))
                Spacer(Modifier.height(140.dp))
                val w = if (e.cols >= 4) DesignTokens.widgetWide.value else DesignTokens.widgetSmall.value
                // Remember the position (departure point of the flying copy); after "Add" the preview is gone — the copy lifts off from it
                WidgetPreview(e, Modifier.size(w.dp, DesignTokens.widgetSmall.dp)
                    .onGloballyPositioned { c -> PickerLaunch.previewCenter = c.boundsInRoot().center }
                    .graphicsLayer { alpha = if (PickerLaunch.added) 0f else 1f })
            }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = buttonY.dp).padding(horizontal = SIDE.dp)
                    .fillMaxWidth().height(48.dp).clip(CircleShape).background(BLUE)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { PickerLaunch.added = true; onAdd() },
                contentAlignment = Alignment.Center,
            ) {
                BasicText("⊕  " + stringResource(R.string.add_widget), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White))
            }
        }
    }
}

// Dimensions [MEASURED roughly] recording8 26.5 / 30.5 s
private const val SHEET_TOP = 123f
private const val SHEET_CORNER = 38f
private const val SHEET_BLUR = 24f
private const val SHEET_DARK = 0.62f
private const val SHEET_LIFT = 0.12f  // the sheet never blacker than ≈ 32 (iOS), even over black widgets
private const val SEARCH_FILL = 0.11f // field ≈ 57 on sheet ≈ 32 [MEASURED] recording8 26.5 s
private const val ADD_DARK = 0.7f
private const val DIM = 0.25f
private const val HANDLE_Y = 9f
private const val HANDLE_W = 36f
private const val SEARCH_Y = 32f
private const val SEARCH_H = 45f
private const val LIST_TOP = 98f
private const val SIDE = 26.33f
private const val GAP = 21.33f
private const val ROW_GAP = 18f
private const val CAPTION_GAP = 10f
private const val ADD_TOP = 134f
private const val ADD_BUTTON_Y = 767f
private val BLUE = Color(0xFF0A84FF)

private const val WIDE_SHEET_W = 470f // pt, ≈ iPhone width + margin
private const val ADD_BOTTOM_ROOM = 34f
private const val ADD_TOP_WIDE = 40f // space below the button (gesture bar)

/** Departure point of the widget flight: centre of the preview in the detail sheet (root coordinates = screen). */
object PickerLaunch {
    var previewCenter: Offset? = null
    var added by androidx.compose.runtime.mutableStateOf(false)
}
