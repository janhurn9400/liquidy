package io.github.janhurn9400.liquidy.ui.widgets

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.ui.Inter
import org.json.JSONArray
import org.json.JSONObject

/** The launcher's own reminders list (Android has no shared reminders app). Stored in the app's preferences. */
object RemindersStore {
    data class Item(val id: Long, val text: String, val done: Boolean)

    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    private var loaded = false
    /** List open? (tapping the widget sets it, the home screen then shows [RemindersSheet]). */
    var sheetOpen by mutableStateOf(false)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        items = runCatching {
            val a = JSONArray(c.getSharedPreferences("reminders", Context.MODE_PRIVATE).getString("items", "[]"))
            List(a.length()) { a.getJSONObject(it).let { o -> Item(o.getLong("id"), o.getString("t"), o.optBoolean("d")) } }
        }.getOrDefault(emptyList())
    }

    private fun save(c: Context) {
        val a = JSONArray(); items.forEach { a.put(JSONObject().put("id", it.id).put("t", it.text).put("d", it.done)) }
        c.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().putString("items", a.toString()).apply()
    }

    fun add(c: Context, text: String) {
        if (text.isBlank()) return
        items = items + Item(System.currentTimeMillis(), text.trim(), false); save(c)
    }

    fun toggle(c: Context, id: Long) { items = items.map { if (it.id == id) it.copy(done = !it.done) else it }; save(c) }

    /** Remove completed ones (like "Delete Completed"). */
    fun clearDone(c: Context) { items = items.filter { !it.done }; save(c) }

    val open get() = items.filter { !it.done }
}

/** Widget: orange number of open reminders + title, below it the first open entries with a circle. */
@Composable
internal fun RemindersWidget(cols: Int, rows: Int, modifier: Modifier) {
    val context = LocalContext.current
    remember { RemindersStore.load(context) }
    val open = RemindersStore.open
    BoxWithConstraints(modifier.background(Color(0xFF1C1C1E))) {
        val s = maxHeight.value / (if (rows >= 4) 363f else 163f)
        Column(Modifier.fillMaxSize().padding(horizontal = (16 * s).dp, vertical = (14 * s).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListGlyph((26 * s).dp)
                Spacer(Modifier.weight(1f))
                BasicText("${open.size}", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = (28 * s).sp, color = ORANGE))
            }
            Spacer(Modifier.height((4 * s).dp))
            BasicText(stringResource(R.string.widget_reminders), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = (15 * s).sp, color = ORANGE))
            Spacer(Modifier.height((8 * s).dp))
            val max = when { rows >= 4 -> 8; cols >= 4 -> 3; else -> 2 }
            if (open.isEmpty()) BasicText(stringResource(R.string.reminders_none), style = rowStyle(s).copy(color = GREY))
            Column(verticalArrangement = Arrangement.spacedBy((6 * s).dp)) {
                open.take(max).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Circle((14 * s).dp, false)
                        Spacer(Modifier.width((8 * s).dp))
                        BasicText(r.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = rowStyle(s))
                    }
                }
            }
        }
    }
}

/** List for adding/checking off (opens when tapping the widget). Dark card centred, input field at the bottom. */
@Composable
fun RemindersSheet(amount: () -> Float, onClose: () -> Unit) {
    val context = LocalContext.current
    remember { RemindersStore.load(context) }
    var text by remember { mutableStateOf("") }
    val noRipple = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = amount().coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = 0.35f))
        .clickable(noRipple, null, onClick = onClose).windowInsetsPadding(WindowInsets.ime), contentAlignment = Alignment.Center) {
        Column(Modifier.width(380.dp).height(460.dp)
            .graphicsLayer { val k = 0.92f + 0.08f * amount(); scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(38.dp)).background(Color(0xFF1C1C1E)).clickable(noRipple, null) { }.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(stringResource(R.string.widget_reminders), Modifier.weight(1f),
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = ORANGE))
                if (RemindersStore.items.any { it.done }) BasicText(stringResource(R.string.reminders_clear_done),
                    Modifier.clickable(noRipple, null) { RemindersStore.clearDone(context) },
                    style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = ORANGE))
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RemindersStore.items.forEach { r ->
                    Row(Modifier.fillMaxWidth().height(44.dp).clickable(noRipple, null) { RemindersStore.toggle(context, r.id) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Circle(22.dp, r.done)
                        Spacer(Modifier.width(12.dp))
                        BasicText(r.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Inter, fontSize = 17.sp,
                            color = if (r.done) GREY else Color.White, textDecoration = if (r.done) TextDecoration.LineThrough else null))
                    }
                    Box(Modifier.padding(start = 34.dp).fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.12f)))
                }
            }
            Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(Color(0xFF2C2C2E)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) BasicText(stringResource(R.string.reminders_new), style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = GREY))
                    BasicTextField(text, { text = it }, singleLine = true, cursorBrush = SolidColor(ORANGE),
                        textStyle = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { RemindersStore.add(context, text); text = "" }),
                        modifier = Modifier.fillMaxWidth())
                }
                if (text.isNotBlank()) BasicText("＋", Modifier.clickable(noRipple, null) { RemindersStore.add(context, text); text = "" },
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ORANGE))
            }
        }
    }
}

@Composable
private fun Circle(size: androidx.compose.ui.unit.Dp, done: Boolean) = Canvas(Modifier.size(size)) {
    val r = this.size.minDimension / 2
    if (done) {
        drawCircle(ORANGE, r)
        val w = r * 0.22f
        drawLine(Color.White, Offset(r * 0.55f, r * 1.0f), Offset(r * 0.88f, r * 1.32f), w, StrokeCap.Round)
        drawLine(Color.White, Offset(r * 0.88f, r * 1.32f), Offset(r * 1.45f, r * 0.7f), w, StrokeCap.Round)
    } else drawCircle(Color(0xFF8E8E93), r - r * 0.1f, style = Stroke(r * 0.16f))
}

@Composable
private fun ListGlyph(size: androidx.compose.ui.unit.Dp) = Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(ORANGE),
    contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(size * 0.55f)) {
        val s = this.size.width; val w = s * 0.12f
        for (k in 0..2) {
            val y = s * (0.2f + k * 0.3f)
            drawCircle(Color.White, w * 0.7f, Offset(s * 0.12f, y))
            drawLine(Color.White, Offset(s * 0.35f, y), Offset(s * 0.95f, y), w, StrokeCap.Round)
        }
    }
}

private fun rowStyle(s: Float) = TextStyle(fontFamily = Inter, fontSize = (14 * s).sp, color = Color.White)
private val ORANGE = Color(0xFFFF9F0A)
private val GREY = Color(0xFF8E8E93)
