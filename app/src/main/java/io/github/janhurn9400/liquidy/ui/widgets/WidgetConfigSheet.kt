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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Hat dieses Widget ein Einstellungsblatt (Antippen im Bearbeiten-Modus)? */
fun hasWidgetConfig(spec: WidgetSpec?) = spec?.type == IosWidgetTypes.WEATHER

/**
 * Einstellungsblatt eines Widgets (iPhone-Aufnahme 10-11 16:40, docs/reference/widgets/widget_config_menu.mp4):
 * schwarze Karte mittig über dem verwischten Homescreen — App-Symbol, Name, Beschreibung, darunter die Optionen als
 * graue Kapseln mit blauem Wert. „Ort“ öffnet die Ortsliste (dunkles Blatt von unten: × und Titel, „Deine Orte“,
 * Suchfeld unten). [amount] 0…1 blendet ein.
 */
@Composable
fun WidgetConfigSheet(spec: WidgetSpec, from: androidx.compose.ui.geometry.Rect, amount: () -> Float, onChange: (String?) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var listOpen by remember { mutableStateOf(false) }
    val place = WeatherRepository.Place.decode(spec.config)
    val noRipple = remember { MutableInteractionSource() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize()
        .drawBehind { drawRect(Color.Black.copy(alpha = DIM * amount().coerceIn(0f, 1f))) }
        .clickable(noRipple, null) { if (listOpen) listOpen = false else onClose() }) {
        val cardW = minOf(CARD_W, maxWidth.value - 32f)
        val cardRect = androidx.compose.ui.geometry.Rect(
            androidx.compose.ui.geometry.Offset((constraints.maxWidth - cardW * density) / 2f, (constraints.maxHeight - CARD_H * density) / 2f),
            androidx.compose.ui.geometry.Size(cardW * density, CARD_H * density))
        // Wo steht die drehende Fläche gerade? Von Widget-Rahmen zur Karte; Fortschritt darf leicht überschwingen (Feder)
        fun current(): androidx.compose.ui.geometry.Rect {
            val t = amount()
            return androidx.compose.ui.geometry.Rect(
                from.left + (cardRect.left - from.left) * t, from.top + (cardRect.top - from.top) * t,
                from.right + (cardRect.right - from.right) * t, from.bottom + (cardRect.bottom - from.bottom) * t)
        }
        // Drehung um die senkrechte Achse: 0 → 180°; bis 90° sieht man das Widget, danach die Rückseite = Karte
        fun angle() = 180f * amount().coerceIn(0f, 1f)
        val showFront = angle() < 90f
        // Vorderseite: Kopie des Widgets
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
        // Rückseite: Karte (um 180° gegengedreht, damit sie nicht gespiegelt ist)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2E7DD7), Color(0xFF7FB8EE)))), contentAlignment = Alignment.Center) {
                    WeatherIcon(2, true, null, 26.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    BasicText(stringResource(R.string.widget_weather_desc), style = text(16f, FontWeight.SemiBold, Color.White))
                    BasicText(stringResource(R.string.widget_weather), style = text(13f, FontWeight.Normal, GREY))
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicText(stringResource(R.string.widget_weather_long), style = text(14f, FontWeight.Normal, GREY))
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(ROW)
                    .clickable(noRipple, null) { listOpen = true }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(stringResource(R.string.wcfg_location), Modifier.weight(1f), style = text(17f, FontWeight.Normal, GREY))
                BasicText(place?.name ?: stringResource(R.string.wcfg_my_location), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = text(17f, FontWeight.Normal, BLUE))
            }
        }
        if (listOpen) LocationList(
            Modifier.align(Alignment.BottomCenter).width(minOf(LIST_W, maxWidth.value).dp).height((maxHeight.value * LIST_H_SHARE).dp),
            onPick = { p ->
                if (p != null) WeatherRepository.rememberPlace(context, p)
                onChange(p?.encode()); listOpen = false
            },
            onClose = { listOpen = false },
        )
    }
}

@Composable
private fun LocationList(modifier: Modifier, onPick: (WeatherRepository.Place?) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val noRipple = remember { MutableInteractionSource() }
    var query by remember { mutableStateOf("") }
    val saved = remember { WeatherRepository.savedPlaces(context) }
    val results by produceState(emptyList<WeatherRepository.Place>(), query) {
        delay(250)
        value = withContext(Dispatchers.IO) { WeatherRepository.searchPlaces(query) }
    }
    Column(modifier.clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)).background(SHEET).clickable(noRipple, null) { }) {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp)) {
            Box(Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).background(ROW)
                .clickable(noRipple, null, onClick = onClose), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(16.dp)) {
                    val sw = 2.2.dp.toPx()
                    drawLine(Color.White, Offset.Zero, Offset(size.width, size.height), sw, StrokeCap.Round)
                    drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), sw, StrokeCap.Round)
                }
            }
            BasicText(stringResource(R.string.wcfg_location), Modifier.align(Alignment.Center), style = text(17f, FontWeight.SemiBold, Color.White))
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
        }
        // Suchfeld unten (wie iOS), über der Tastatur
        Row(
            Modifier.windowInsetsPadding(WindowInsets.ime).padding(16.dp).fillMaxWidth().height(44.dp)
                .clip(RoundedCornerShape(22.dp)).background(ROW).padding(horizontal = 14.dp),
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
                BasicTextField(query, { query = it }, singleLine = true, textStyle = text(17f, FontWeight.Normal, Color.White),
                    cursorBrush = SolidColor(BLUE), modifier = Modifier.fillMaxWidth())
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

// Farben/Maße aus der Aufnahme (402 pt breit): Karte 350 × 345 pt, Ecken 38, Zeile 52 hoch
private val CARD = Color(0xFF050505)
private val ROW = Color(0xFF2C2C2E)
private val SHEET = Color(0xFF1C1C1E)
private val GREY = Color(0xFF8E8E93)
private val BLUE = Color(0xFF0A84FF)
private const val DIM = 0.35f
private const val CAMERA = 14f // Perspektive der Drehung (wie iOS: deutlich, nicht übertrieben)
private const val CARD_W = 350f
private const val CARD_H = 345f
private const val CARD_CORNER = 38f
private const val LIST_W = 461f
private const val LIST_H_SHARE = 0.86f
