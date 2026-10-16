package io.github.janhurn9400.liquidy.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.WeatherRepository
import io.github.janhurn9400.liquidy.data.WidgetSpec
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.glass.GlassSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Does this widget have a settings sheet (tap in edit mode)? */
fun hasWidgetConfig(spec: WidgetSpec?) = spec?.type in setOf(IosWidgetTypes.WEATHER, IosWidgetTypes.PHOTOS, IosWidgetTypes.CALENDAR, IosWidgetTypes.CLOCK)

/**
 * Settings sheet of a widget (iPhone recording 10-11 16:40, docs/reference/widgets/widget_config_menu.mp4):
 * black card centred above the blurred home screen — app icon, name, description, below it the options as
 * grey capsules with a blue value. "Location" opens the location list (dark sheet from below: × and title, "Your Locations",
 * search field at the bottom). [amount] 0…1 fades in.
 */
@Composable
fun WidgetConfigSheet(spec: WidgetSpec, from: androidx.compose.ui.geometry.Rect, amount: () -> Float, onChange: (String?) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    // Selection list (album/calendar/location): Liquid Glass sheet, slides in from below (M-WPE-SHEET), pushes the card
    // slightly back; close → slides out, only then does it disappear (before: black sheet with a hard cut)
    val list = io.github.janhurn9400.liquidy.glass.rememberGlassSheetState()
    var query by remember { mutableStateOf("") } // search text (location/city) — the field floats above the list
    val sheet = list.progress
    val listOpen = list.visible
    fun openList() = list.open()
    fun closeList() = list.close()
    val place = WeatherRepository.Place.decode(spec.config)
    val noRipple = remember { MutableInteractionSource() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize()
        .drawBehind { drawRect(Color.Black.copy(alpha = DIM * amount().coerceIn(0f, 1f))) }
        .clickable(noRipple, null) { if (listOpen) closeList() else onClose() }) {
        val cardW = minOf(CARD_W, maxWidth.value - 32f)
        val cardRect = androidx.compose.ui.geometry.Rect(
            androidx.compose.ui.geometry.Offset((constraints.maxWidth - cardW * density) / 2f, (constraints.maxHeight - CARD_H * density) / 2f),
            androidx.compose.ui.geometry.Size(cardW * density, CARD_H * density))
        // Where is the turning surface right now? From the widget rect to the card; progress may overshoot slightly (spring)
        fun current(): androidx.compose.ui.geometry.Rect {
            val t = amount()
            return androidx.compose.ui.geometry.Rect(
                from.left + (cardRect.left - from.left) * t, from.top + (cardRect.top - from.top) * t,
                from.right + (cardRect.right - from.right) * t, from.bottom + (cardRect.bottom - from.bottom) * t)
        }
        // Rotation around the vertical axis: 0 → 180°; up to 90° you see the widget, afterwards the back = card
        fun angle() = 180f * amount().coerceIn(0f, 1f)
        val showFront = angle() < 90f
        // Front: copy of the widget
        if (showFront) Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(from.left.toInt(), from.top.toInt()) }
                .size((from.width / density).dp, (from.height / density).dp)
                .graphicsLayer {
                    val c = current()
                    translationX = c.center.x - from.center.x; translationY = c.center.y - from.center.y
                    scaleX = c.width / from.width; scaleY = c.height / from.height
                    rotationY = angle(); cameraDistance = CAMERA * density
                    shape = RoundedCornerShape(io.github.janhurn9400.liquidy.design.DesignTokens.widgetCorner.dp); clip = true
                },
        ) { IosWidget(spec.type, spec.cols, spec.rows, Modifier.fillMaxSize(), spec.config) }
        // Back: card (counter-rotated by 180° so it isn't mirrored)
        if (!showFront) Column(
            Modifier.align(Alignment.Center).width(cardW.dp).height(CARD_H.dp)
                .graphicsLayer {
                    val c = current()
                    translationX = c.center.x - cardRect.center.x; translationY = c.center.y - cardRect.center.y
                    scaleX = c.width / cardRect.width; scaleY = c.height / cardRect.height
                    rotationY = angle() - 180f; cameraDistance = CAMERA * density
                }
                .clip(RoundedCornerShape(CARD_CORNER.dp)).background(CARD)
                .clickable(noRipple, null) { }
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            // Header + one option per widget kind (same look as the weather sheet from the recording)
            val (title, sub, desc) = when (spec.type) {
                IosWidgetTypes.PHOTOS -> Triple(stringResource(R.string.wcfg_photos_title), stringResource(R.string.widget_photos), stringResource(R.string.widget_photos_long))
                IosWidgetTypes.CALENDAR -> Triple(stringResource(R.string.wcfg_calendar_title), stringResource(R.string.widget_calendar), stringResource(R.string.widget_calendar_long))
                IosWidgetTypes.CLOCK -> Triple(stringResource(R.string.widget_clock_desc), stringResource(R.string.widget_clock), stringResource(R.string.widget_clock_long))
                else -> Triple(stringResource(R.string.widget_weather_desc), stringResource(R.string.widget_weather), stringResource(R.string.widget_weather_long))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(when (spec.type) {
                    IosWidgetTypes.PHOTOS -> Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF375F), Color(0xFF5E5CE6)))
                    IosWidgetTypes.CALENDAR -> Brush.verticalGradient(listOf(Color.White, Color.White))
                    IosWidgetTypes.CLOCK -> Brush.verticalGradient(listOf(Color(0xFF1C1C1E), Color.Black))
                    else -> Brush.verticalGradient(listOf(Color(0xFF2E7DD7), Color(0xFF7FB8EE)))
                }), contentAlignment = Alignment.Center) {
                    when (spec.type) {
                        IosWidgetTypes.CALENDAR -> BasicText("${java.time.LocalDate.now().dayOfMonth}", style = text(18f, FontWeight.SemiBold, Color.Black))
                        IosWidgetTypes.PHOTOS -> {}
                        IosWidgetTypes.CLOCK -> Canvas(Modifier.size(24.dp)) {
                            val r = size.minDimension / 2; drawCircle(Color.White, r)
                            drawLine(Color.Black, center, center + Offset(0f, -r * 0.6f), r * 0.14f, StrokeCap.Round)
                            drawLine(Color.Black, center, center + Offset(r * 0.45f, 0f), r * 0.14f, StrokeCap.Round)
                        }
                        else -> WeatherIcon(2, true, null, 26.dp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    BasicText(title, style = text(16f, FontWeight.SemiBold, Color.White))
                    BasicText(sub, style = text(13f, FontWeight.Normal, GREY))
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicText(desc, style = text(14f, FontWeight.Normal, GREY))
            Spacer(Modifier.height(14.dp))
            val (label, value) = when (spec.type) {
                IosWidgetTypes.PHOTOS -> stringResource(R.string.wcfg_album) to (spec.config ?: stringResource(R.string.wcfg_all_photos))
                IosWidgetTypes.CLOCK -> stringResource(R.string.wcfg_city) to
                    (io.github.janhurn9400.liquidy.data.WorldClock.City.decode(spec.config)?.name ?: stringResource(R.string.wcfg_local_time))
                IosWidgetTypes.CALENDAR -> stringResource(R.string.wcfg_calendars) to (spec.config?.split(',')?.filter { it.isNotBlank() }?.size
                    ?.let { stringResource(R.string.wcfg_n_selected, it) } ?: stringResource(R.string.wcfg_all_calendars))
                else -> stringResource(R.string.wcfg_location) to (place?.name ?: stringResource(R.string.wcfg_my_location))
            }
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(ROW)
                    .clickable(noRipple, null) { openList() }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(label, Modifier.weight(1f), style = text(17f, FontWeight.Normal, GREY))
                BasicText(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = text(17f, FontWeight.Normal, BLUE))
            }
        }
        // Sheet open: the card stays, everything behind it dims (like on the iPhone, user 10-15)
        if (listOpen) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = SHEET_DIM * sheet.value)) }
        when (spec.type) {
            IosWidgetTypes.PHOTOS -> GlassSheet(list, stringResource(R.string.wcfg_album)) {
                val albums = remember { photoAlbums(context) }
                OptionRows(listOf(null to stringResource(R.string.wcfg_all_photos)) + albums.map { it.first to "${it.first}  ·  ${it.second}" },
                    selected = { it == spec.config }, onPick = { k -> list.close { onChange(k) } })
            }
            IosWidgetTypes.CALENDAR -> GlassSheet(list, stringResource(R.string.wcfg_calendars)) {
                val cals = remember { deviceCalendars(context) }
                val chosen = spec.config?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet()
                OptionRows(cals.map { it.first.toString() to it.second },
                    selected = { id -> chosen == null || id?.toLong() in chosen },
                    onPick = { id ->
                        val all = cals.map { it.first }.toSet(); val cur = chosen ?: all
                        val next = id?.toLong()?.let { if (it in cur) cur - it else cur + it } ?: cur
                        onChange(if (next == all || next.isEmpty()) null else next.joinToString(","))
                    }, dots = cals.associate { it.first.toString() to Color(it.third) })
            }
            IosWidgetTypes.CLOCK -> GlassSheet(list, stringResource(R.string.wcfg_city), bottomBar = { Box(Modifier.align(Alignment.BottomCenter)) { SearchField(query) { query = it } } }) {
                CityRows(query, onPick = { c ->
                    if (c != null) io.github.janhurn9400.liquidy.data.WorldClock.remember(context, c)
                    list.close { onChange(c?.encode()) }
                })
            }
            else -> GlassSheet(list, stringResource(R.string.wcfg_location), bottomBar = { Box(Modifier.align(Alignment.BottomCenter)) { SearchField(query) { query = it } } }) {
                LocationRows(query, onPick = { p ->
                    if (p != null) WeatherRepository.rememberPlace(context, p)
                    list.close { onChange(p?.encode()) }
                })
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LocationRows(query: String, onPick: (WeatherRepository.Place?) -> Unit) {
    val context = LocalContext.current
    val saved = remember { WeatherRepository.savedPlaces(context) }
    val results by produceState(emptyList<WeatherRepository.Place>(), query) {
        delay(250)
        value = withContext(Dispatchers.IO) { WeatherRepository.searchPlaces(query) }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (query.isBlank()) {
                Spacer(Modifier.height(14.dp))
                BasicText(stringResource(R.string.wcfg_your_locations), style = text(17f, FontWeight.SemiBold, GREY))
                Spacer(Modifier.height(4.dp))
                PlaceRow(stringResource(R.string.wcfg_my_location), null) { onPick(null) }
                saved.forEach { p -> PlaceRow(p.name, null) { onPick(p) } }
            } else {
                Spacer(Modifier.height(8.dp))
                results.forEach { p -> PlaceRow(p.name, p.region.ifEmpty { null }) { onPick(p) } }
            }
            Spacer(Modifier.height(io.github.janhurn9400.liquidy.glass.GlassSheetBottomBarSpace)) // rows run under the search field
    }
    }

/** Cities for the clock widget: local time, recently chosen, then search (offline time zones immediately, online any city). */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CityRows(query: String, onPick: (io.github.janhurn9400.liquidy.data.WorldClock.City?) -> Unit) {
    val context = LocalContext.current
    val saved = remember { io.github.janhurn9400.liquidy.data.WorldClock.saved(context) }
    val online by produceState(emptyList<io.github.janhurn9400.liquidy.data.WorldClock.City>(), query) {
        value = emptyList(); delay(300)
        value = withContext(Dispatchers.IO) { io.github.janhurn9400.liquidy.data.WorldClock.searchOnline(query) }
    }
    val offline = remember(query) { io.github.janhurn9400.liquidy.data.WorldClock.searchOffline(query) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (query.isBlank()) {
            Spacer(Modifier.height(14.dp))
            BasicText(stringResource(R.string.wcfg_your_cities), style = text(17f, FontWeight.SemiBold, GREY))
            Spacer(Modifier.height(4.dp))
            PlaceRow(stringResource(R.string.wcfg_local_time), null) { onPick(null) }
            saved.forEach { c -> PlaceRow(c.name, zoneLabel(c)) { onPick(c) } }
        } else {
            Spacer(Modifier.height(8.dp))
            // online first (more precise, e.g. small places), then offline without duplicates
            val all = (online + offline).distinctBy { it.name.lowercase() + it.zone }
            if (all.isEmpty()) BasicText(stringResource(R.string.wcfg_nothing), Modifier.fillMaxWidth().padding(top = 24.dp),
                style = text(15f, FontWeight.Normal, GREY).copy(textAlign = TextAlign.Center))
            all.forEach { c -> PlaceRow(c.name, listOfNotNull(c.region.ifEmpty { null }, zoneLabel(c)).joinToString(" · ")) { onPick(c) } }
        }
        Spacer(Modifier.height(io.github.janhurn9400.liquidy.glass.GlassSheetBottomBarSpace)) // rows run under the search field
    }
}

/** Time difference to the device, e.g. "+6 hrs" / "Same time". */
@Composable
private fun zoneLabel(c: io.github.janhurn9400.liquidy.data.WorldClock.City): String {
    val z = c.zoneId ?: return ""
    val now = java.time.Instant.now()
    val diffMin = (z.rules.getOffset(now).totalSeconds - java.time.ZoneId.systemDefault().rules.getOffset(now).totalSeconds) / 60
    if (diffMin == 0) return stringResource(R.string.wcfg_same_time)
    val h = diffMin / 60; val m = kotlin.math.abs(diffMin % 60)
    val s = (if (diffMin > 0) "+" else "−") + kotlin.math.abs(h) + (if (m != 0) ":%02d".format(m) else "")
    return stringResource(R.string.wcfg_hours_offset, s)
}

/** Search field at the bottom of the sheet (like iOS), above the keyboard — Liquid Glass like the App Library search field. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    // Search field at the bottom (like iOS), above the keyboard — Liquid Glass like the App Library search field (refracts the sheet behind)
    Box(Modifier.windowInsetsPadding(WindowInsets.ime).padding(16.dp).fillMaxWidth().height(44.dp)) {
    io.github.janhurn9400.liquidy.glass.LiquidGlass(shape = io.github.janhurn9400.liquidy.glass.glassCapsule(), modifier = Modifier.matchParentSize(),
        whiteTint = io.github.janhurn9400.liquidy.design.DesignTokens.libListFieldTint.value)
    Row(
        Modifier.matchParentSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val sw = 2.dp.toPx(); val r = size.width * 0.36f
            drawCircle(GREY, r, Offset(r + sw / 2, r + sw / 2), style = Stroke(sw))
            drawLine(GREY, Offset(r * 1.7f, r * 1.7f), Offset(size.width, size.height), sw, StrokeCap.Round)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) BasicText(stringResource(R.string.wcfg_search), style = text(17f, FontWeight.Normal, GREY))
            BasicTextField(query, onChange, singleLine = true, textStyle = text(17f, FontWeight.Normal, Color.White),
                cursorBrush = SolidColor(BLUE), modifier = Modifier.fillMaxWidth())
        }
    }
    }
}

/** Selection rows for a [io.github.janhurn9400.liquidy.glass.GlassSheet]: checkmark on selected entries, optional colour dot. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.OptionRows(options: List<Pair<String?, String>>, selected: (String?) -> Boolean,
                                                                      onPick: (String?) -> Unit, dots: Map<String, Color> = emptyMap()) {
    val noRipple = remember { MutableInteractionSource() }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (options.isEmpty()) BasicText(stringResource(R.string.wcfg_nothing_found), Modifier.fillMaxWidth().padding(top = 24.dp),
            style = text(15f, FontWeight.Normal, GREY).copy(textAlign = TextAlign.Center))
        options.forEach { (key, label) ->
            Column(Modifier.fillMaxWidth().clickable(noRipple, null) { onPick(key) }) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    dots[key]?.let { c -> Box(Modifier.size(12.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(10.dp)) }
                    BasicText(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = text(17f, FontWeight.Normal, Color.White))
                    if (selected(key)) Canvas(Modifier.size(16.dp)) {
                        val sw = 2.4.dp.toPx()
                        drawLine(BLUE, Offset(size.width * 0.1f, size.height * 0.55f), Offset(size.width * 0.4f, size.height * 0.85f), sw, StrokeCap.Round)
                        drawLine(BLUE, Offset(size.width * 0.4f, size.height * 0.85f), Offset(size.width * 0.95f, size.height * 0.15f), sw, StrokeCap.Round)
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.14f)))
            }
        }
    }
}

@Composable
private fun PlaceRow(name: String, region: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, onClick = onClick)) {
        Column(Modifier.fillMaxWidth().height(if (region != null) 58.dp else 45.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = text(17f, FontWeight.Normal, Color.White))
            if (region != null) BasicText(region, maxLines = 1, overflow = TextOverflow.Ellipsis, style = text(14f, FontWeight.Normal, GREY))
        }
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.14f)))
    }
}

private fun text(size: Float, weight: FontWeight, color: Color) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.25f).sp, color = color, textAlign = TextAlign.Start)

// Colours/dimensions from the recording (402 pt wide): card 350 × 345 pt, corners 38, row 52 tall
private val CARD = Color(0xFF050505)
private val ROW = Color(0xFF2C2C2E)
private const val SHEET_DIM = 0.35f // dimming behind the selection sheet [ESTIMATED]
private val GREY = Color(0xFF8E8E93)
private val BLUE = Color(0xFF0A84FF)
private const val DIM = 0.35f
private const val CAMERA = 14f // perspective of the rotation (like iOS: clear, not exaggerated)
private const val CARD_W = 350f
private const val CARD_H = 345f
private const val CARD_CORNER = 38f
