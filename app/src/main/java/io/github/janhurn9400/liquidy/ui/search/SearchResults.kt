package io.github.janhurn9400.liquidy.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.UniversalSearch
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result sections of the global search after iPhone screenshot 10-11 17:10: section title grey semi-bold with a line
 * below, rows with a 28 pt glyph, text 17 pt (typed part white, rest grey), ↗ on the right, indented separators.
 */
@Composable
internal fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = SECTION_TOP.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = SIDE.dp), verticalAlignment = Alignment.Bottom) {
            BasicText(title, Modifier.weight(1f), style = header())
            if (action != null) BasicText(action, Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onAction), style = header())
        }
        Spacer(Modifier.height(8.dp))
        Line(SIDE)
    }
}

@Composable
private fun Line(start: Float) = Box(Modifier.padding(start = start.dp, end = SIDE.dp).fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = LINE_ALPHA)))

/** Web suggestion: browser glyph, typed part white, completion grey, ↗. */
@Composable
internal fun WebRow(query: String, suggestion: String, last: Boolean, onClick: () -> Unit) {
    Row(rowMod(onClick), verticalAlignment = Alignment.CenterVertically) {
        BrowserIcon()
        Spacer(Modifier.width(GAP.dp))
        val q = query.trim()
        val text = buildAnnotatedString {
            if (suggestion.startsWith(q, ignoreCase = true)) {
                withStyle(SpanStyle(color = Color.White)) { append(suggestion.substring(0, q.length)) }
                withStyle(SpanStyle(color = GREY)) { append(suggestion.substring(q.length)) }
            } else withStyle(SpanStyle(color = Color.White)) { append(suggestion) }
        }
        BasicText(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = rowText())
        Arrow()
    }
    if (!last) Line(SIDE + ICON + GAP)
}

@Composable
internal fun ContactRow(c: UniversalSearch.Contact, last: Boolean, onOpen: () -> Unit, onCall: (() -> Unit)?) {
    Row(rowMod(onOpen), verticalAlignment = Alignment.CenterVertically) {
        ContactAvatar(c)
        Spacer(Modifier.width(GAP.dp))
        Column(Modifier.weight(1f)) {
            BasicText(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = rowText())
            if (c.number != null) BasicText(c.number, maxLines = 1, style = rowText().copy(fontSize = 14.sp, color = GREY))
        }
        if (onCall != null) Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onCall), contentAlignment = Alignment.Center,
        ) { PhoneGlyph() }
    }
    if (!last) Line(SIDE + ICON + GAP)
}

/** Hint row (e.g. "Allow contacts in search"). */
@Composable
internal fun HintRow(text: String, onClick: () -> Unit) {
    Row(rowMod(onClick), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text, Modifier.weight(1f), maxLines = 2, style = rowText().copy(color = Color(0xFF64D2FF), fontSize = 15.sp))
    }
}

@Composable
internal fun CalculatorRow(expr: String, result: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = SIDE.dp, vertical = 10.dp)) {
        BasicText(expr.trim(), maxLines = 1, style = rowText().copy(color = GREY, fontSize = 15.sp))
        BasicText("= $result", maxLines = 1, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = Color.White))
    }
}

@Composable
private fun rowMod(onClick: () -> Unit) = Modifier.fillMaxWidth().height(ROW_H.dp)
    .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = SIDE.dp)

@Composable
private fun BrowserIcon() {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            UniversalSearch.browserPackage(context)?.let { p -> runCatching { context.packageManager.getApplicationIcon(p).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
        }
    }
    val i = icon
    if (i != null) Image(i, null, Modifier.size(ICON.dp).clip(RoundedCornerShape((ICON * 0.24f).dp)), contentScale = ContentScale.Crop)
    else Box(Modifier.size(ICON.dp).clip(RoundedCornerShape((ICON * 0.24f).dp)).background(Color(0xFF0A84FF)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((ICON * 0.6f).dp)) { // globe
            val s = size.width; val sw = s * 0.08f
            drawCircle(Color.White, s * 0.45f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            drawLine(Color.White, Offset(s * 0.05f, s / 2), Offset(s * 0.95f, s / 2), sw)
            drawOval(Color.White, Offset(s * 0.3f, s * 0.05f), androidx.compose.ui.geometry.Size(s * 0.4f, s * 0.9f), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        }
    }
}

@Composable
private fun ContactAvatar(c: UniversalSearch.Contact) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, c.photo) {
        value = c.photo?.let { u -> withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } }
    }
    Box(Modifier.size(ICON.dp).clip(CircleShape).background(Color(0xFF8E8E93)), contentAlignment = Alignment.Center) {
        val p = photo
        if (p != null) Image(p, null, Modifier.size(ICON.dp), contentScale = ContentScale.Crop)
        else BasicText(c.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White))
    }
}

@Composable
private fun Arrow() = Canvas(Modifier.size(16.dp)) {
    val s = size.width; val sw = s * 0.1f; val c = Color.White.copy(alpha = 0.85f)
    drawLine(c, Offset(s * 0.1f, s * 0.9f), Offset(s * 0.9f, s * 0.1f), sw, StrokeCap.Round)
    drawLine(c, Offset(s * 0.35f, s * 0.1f), Offset(s * 0.9f, s * 0.1f), sw, StrokeCap.Round)
    drawLine(c, Offset(s * 0.9f, s * 0.1f), Offset(s * 0.9f, s * 0.65f), sw, StrokeCap.Round)
}

@Composable
private fun PhoneGlyph() = Canvas(Modifier.size(16.dp)) {
    val s = size.width
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(s * 0.22f, s * 0.08f); lineTo(s * 0.38f, s * 0.34f); lineTo(s * 0.3f, s * 0.46f)
        quadraticTo(s * 0.4f, s * 0.62f, s * 0.54f, s * 0.7f); lineTo(s * 0.66f, s * 0.62f); lineTo(s * 0.92f, s * 0.78f)
        lineTo(s * 0.8f, s * 0.94f); quadraticTo(s * 0.3f, s * 0.9f, s * 0.06f, s * 0.2f); close()
    }
    drawPath(p, Color.White)
}

private fun header() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White.copy(alpha = 0.62f))
private fun rowText() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Color.White)

private val GREY = Color.White.copy(alpha = 0.55f)
// Dimensions from the screenshot (920 px = 402 pt): margin 16, glyph 26, gap 15, row 56, line 0.6 pt
private const val SIDE = 16f
private const val ICON = 26f
private const val GAP = 15f
private const val ROW_H = 56f
private const val SECTION_TOP = 14f
private const val LINE_ALPHA = 0.16f

/** Photo results as a horizontal row of square thumbnails (iOS "Photos"), tapping opens the photo. */
@Composable
internal fun PhotoStrip(photos: List<io.github.janhurn9400.liquidy.data.DeviceSearch.Photo>, onOpen: (io.github.janhurn9400.liquidy.data.DeviceSearch.Photo) -> Unit) {
    val context = LocalContext.current
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = SIDE.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(PHOTO_GAP.dp),
    ) {
        items(photos.size) { i ->
            val p = photos[i]
            val px = with(androidx.compose.ui.platform.LocalDensity.current) { PHOTO.dp.roundToPx() }
            val bmp by produceState<ImageBitmap?>(null, p.uri) {
                value = withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(p.uri, android.util.Size(px, px), null).asImageBitmap() }.getOrNull() }
            }
            Box(Modifier.size(PHOTO.dp).clip(RoundedCornerShape(PHOTO_CORNER.dp)).background(Color.White.copy(alpha = 0.1f))
                .clickable(remember { MutableInteractionSource() }, null) { onOpen(p) }) {
                bmp?.let { Image(it, null, Modifier.size(PHOTO.dp), contentScale = ContentScale.Crop) }
            }
        }
    }
}

/** File row: coloured type glyph, name, below it folder · size · date. */
@Composable
internal fun FileRow(f: io.github.janhurn9400.liquidy.data.DeviceSearch.FileHit, last: Boolean, onClick: () -> Unit) {
    Row(rowMod(onClick), verticalAlignment = Alignment.CenterVertically) {
        FileIcon(f.mime, f.name)
        Spacer(Modifier.width(GAP.dp))
        Column(Modifier.weight(1f)) {
            BasicText(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = rowText())
            val ctx = LocalContext.current
            val meta = listOfNotNull(f.folder.ifEmpty { null }, android.text.format.Formatter.formatShortFileSize(ctx, f.size),
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(f.modified))).joinToString(" · ")
            BasicText(meta, maxLines = 1, overflow = TextOverflow.Ellipsis, style = rowText().copy(fontSize = 13.sp, color = GREY))
        }
    }
    if (!last) Line(SIDE + ICON + GAP)
}

@Composable
private fun FileIcon(mime: String?, name: String) {
    val ext = name.substringAfterLast('.', "").uppercase().take(4)
    val color = when {
        mime == "application/pdf" -> Color(0xFFFF453A)
        mime?.startsWith("audio") == true -> Color(0xFFFF375F)
        mime?.contains("sheet") == true || ext in listOf("XLS", "XLSX", "CSV") -> Color(0xFF30D158)
        mime?.contains("presentation") == true || ext in listOf("PPT", "PPTX", "KEY") -> Color(0xFFFF9F0A)
        mime?.contains("word") == true || ext in listOf("DOC", "DOCX", "TXT", "RTF", "MD") -> Color(0xFF0A84FF)
        mime?.contains("zip") == true || ext in listOf("ZIP", "RAR", "7Z", "APK") -> Color(0xFF8E8E93)
        else -> Color(0xFF636366)
    }
    Box(Modifier.size(ICON.dp, (ICON * 1.2f).dp).clip(RoundedCornerShape(4.dp)).background(color), contentAlignment = Alignment.BottomCenter) {
        BasicText(ext.ifEmpty { "?" }, Modifier.padding(bottom = 3.dp), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 7.sp, color = Color.White))
    }
}

/** Settings row: grey gear, name, ↗. */
@Composable
internal fun SettingRow(label: String, last: Boolean, onClick: () -> Unit) {
    Row(rowMod(onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(ICON.dp).clip(RoundedCornerShape((ICON * 0.24f).dp)).background(Color(0xFF8E8E93)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size((ICON * 0.66f).dp)) {
                val s = size.width; val sw = s * 0.1f; val c = center
                drawCircle(Color.White, s * 0.2f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                for (k in 0 until 8) { val a = Math.toRadians(k * 45.0); val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                    drawLine(Color.White, c + d * (s * 0.3f), c + d * (s * 0.45f), sw * 1.5f, StrokeCap.Round) }
            }
        }
        Spacer(Modifier.width(GAP.dp))
        BasicText(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = rowText())
        Arrow()
    }
    if (!last) Line(SIDE + ICON + GAP)
}

private const val PHOTO = 84f
private const val PHOTO_GAP = 6f
private const val PHOTO_CORNER = 10f
