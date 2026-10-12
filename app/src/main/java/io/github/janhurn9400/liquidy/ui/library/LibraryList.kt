package io.github.janhurn9400.liquidy.ui.library

import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.ui.home.HomeModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val INDEX = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"

private sealed interface Entry {
    data class Header(val letter: Char) : Entry
    data class Row(val app: AppEntry, val last: Boolean) : Entry
}

private fun letterOf(app: AppEntry): Char {
    val c = app.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
    // Umlaute/Akzente unter ihren Grundbuchstaben
    val base = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD).first()
    return if (base in 'A'..'Z') base else '#'
}

/**
 * A–Z-Liste der Mediathek (recording2 12,4 s): Abschnittsköpfe, Zeilen mit 48-pt-Icon + Name, Haarlinien,
 * Index rechts. Liegt über der Gesten-Arena; beim Herunterziehen folgt sie dem Finger 1:1 (M-LIB-LIST).
 * Das Suchfeld + „Cancel“ zeichnet [LibraryChrome]; hier nur deren Trefferflächen.
 */
@Composable
fun LibraryList(model: HomeModel, geo: LibraryGeometry, density: Float, offsetX: () -> Float = { 0f },
                listBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null, onLaunch: (AppEntry, Rect) -> Unit) {
    val apps = model.libListApps()
    val duo = geo.duo
    // Duo [GEMESSEN Video 2, 18 s, echte Displaykanten]: Liste in der Mediathek-Spalte, ohne Buchstaben-Köpfe,
    // Icon 44 pt bei Spalten-x 66, Name ab 130, Trennlinie bis 400, erste Zeile Mitte 168 pt, A–Z in der Dock-Spalte
    val lay = if (duo) ListLayout(side = 66f, icon = 44f, textX = 130f, sepRight = geo.duoSepRight, top = 132f, indexTop = 214f)
        else ListLayout(DesignTokens.libListSide.value, DesignTokens.libListIcon.value, DesignTokens.libListTextX.value,
            DesignTokens.libListSepRight.value, DesignTokens.libListTop.value, DesignTokens.libIndexTop.value)
    val entries = remember(apps, model.libQuery.isEmpty()) {
        buildList {
            if (model.libQuery.isNotEmpty()) apps.forEachIndexed { i, a -> add(Entry.Row(a, i == apps.lastIndex)) }
            else {
                val groups = apps.groupBy(::letterOf).toSortedMap(compareBy { if (it == '#') 'Z' + 1 else it })
                groups.forEach { (letter, list) ->
                    if (!duo) add(Entry.Header(letter))
                    // Duo (ohne Köpfe): Trennlinie unter jeder Zeile außer der allerletzten
                    list.forEachIndexed { i, a -> add(Entry.Row(a, if (duo) a == apps.last() else i == list.lastIndex)) }
                }
            }
        }
    }
    val state = rememberLazyListState()
    val active = model.libListActive
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = model.libList.value
                translationX = offsetX()
                translationY = model.libListPull.value * density
                compositingStrategy = CompositingStrategy.Offscreen
            }
            // Oben unter dem Feld weich ausblenden (Scroll-Kante) — Duo: nicht ausblenden, Zeilen laufen unter dem
            // Feld bis zum Rand weiter und werden dort progressiv unscharf (ListEdge unten) [Video 2, 24,8 s]
            .drawWithContent {
                drawContent()
                if (duo) return@drawWithContent // Duo: kein Ausblenden — das Suchfeld liegt darüber und bricht die Zeilen
                val a = geo.search.bottom
                val b = DesignTokens.libListTop.value * density
                drawRect(Brush.verticalGradient(0f to Color.Transparent, a / size.height to Color.Transparent, b / size.height to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
            }
    ) {
        // Android-Overscroll aus: iOS federt, die Liste klebt sonst mit Stretch-Effekt
        val edgeEnd = geo.search.bottom + LIST_EDGE_BELOW * density
        val edgeSolid = (geo.search.center.y / edgeEnd).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().then(if (listBackdrop == null) Modifier else Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // scharfe Zeilen dort weg, wo die verwischte Kopie (ListEdge) voll deckt → kein Doppelbild/„Glühen“
                val k = edgeAmount(state, density)
                if (k > 0f) drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = k), edgeSolid * edgeEnd / size.height to Color.Black.copy(alpha = k),
                    edgeEnd / size.height to Color.Transparent), blendMode = BlendMode.DstOut)
            })) {
        CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollFactory provides null) {
            LazyColumn(
                state = state,
                userScrollEnabled = active,
                contentPadding = PaddingValues(
                    top = lay.top.dp,
                    bottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding() + LIST_BOTTOM.dp,
                ),
                modifier = Modifier.fillMaxSize().then(if (listBackdrop != null) Modifier.layerBackdrop(listBackdrop) else Modifier),
            ) {
                items(entries.size, key = { i -> when (val e = entries[i]) { is Entry.Header -> "h${e.letter}"; is Entry.Row -> e.app.key } }) { i ->
                    when (val e = entries[i]) {
                        is Entry.Header -> Header(e.letter)
                        is Entry.Row -> AppRow(e.app, !e.last, enabled = active, lay = lay) {
                            onLaunch(e.app, Rect.Zero)
                            model.closeLibList()
                        }
                    }
                }
            }
        }
        }
        if (listBackdrop != null) ListEdge(listBackdrop, state, geo, density)
        if (model.libQuery.isEmpty()) IndexBar(state, entries, enabled = active, lay = lay,
            // Duo: Index in der Dock-Spalte (Bildschirm − 47 pt), relativ zur verschobenen Liste
            x = { if (duo) geo.width / density - DUO_INDEX_FROM_RIGHT - offsetX() / density else DesignTokens.libIndexX.value })
    }

    // Trefferflächen über dem Feld: „Cancel“ schließt, das Feld selbst bleibt fokussiert
    if (active) {
        val noRipple = remember { MutableInteractionSource() }
        // Duo: runder ×-Knopf (Spalten-x 375 ± 22) statt „Cancel“
        val cancelX = if (duo) geo.duoCloseX - LIB_DUO_CLOSE_D / 2 else DesignTokens.libCancelX.value
        val cancelW = if (duo) LIB_DUO_CLOSE_D else geo.width / density - DesignTokens.libCancelX.value
        Box(
            Modifier
                .offset { IntOffset((cancelX * density + offsetX()).roundToInt() - CANCEL_SLOP.dp.roundToPx(), geo.search.top.roundToInt()) }
                .size((cancelW + 2 * CANCEL_SLOP).dp, (geo.search.height / density).dp)
                .clickable(interactionSource = noRipple, indication = null) { model.closeLibList() }
        )
        // Feld selbst: Tippen schluckt (Fokus bleibt), statt eine Zeile darunter zu treffen
        Box(
            Modifier
                .offset { IntOffset((geo.search.left + offsetX()).roundToInt(), geo.search.top.roundToInt()) }
                .size(((if (duo) geo.duoFieldRight else DesignTokens.libListFieldRight.value) - geo.search.left / density).dp, (geo.search.height / density).dp)
                .clickable(interactionSource = noRipple, indication = null) {}
        )
    }
}

@Composable
private fun Header(letter: Char) {
    Layout(
        modifier = Modifier.fillMaxWidth().height(DesignTokens.libListHeader.dp),
        content = {
            BasicText(
                letter.toString(),
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.libListHeaderFont.value.sp, color = Color.White),
            )
        },
    ) { ms, c ->
        val t = ms[0].measure(Constraints())
        layout(c.maxWidth, c.maxHeight) {
            t.place(DesignTokens.libListSide.dp.roundToPx(), DesignTokens.libListHeaderBaseline.dp.roundToPx() - t[FirstBaseline])
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, separator: Boolean, enabled: Boolean, lay: ListLayout, onClick: () -> Unit) {
    val iconShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (lay.icon / DesignTokens.iconSize.value))
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .height(DesignTokens.libListRow.dp)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .drawWithContent {
                drawContent()
                if (separator) {
                    val y = size.height - 0.5f
                    drawLine(DesignTokens.libListSeparator, Offset(lay.textX.dp.toPx(), y), Offset(lay.sepRight.dp.toPx(), y), strokeWidth = 1f)
                }
            },
        content = {
            io.github.janhurn9400.liquidy.ui.home.StyledAppIcon(app, Modifier.size(lay.icon.dp).clip(iconShape))
            BasicText(
                app.label,
                style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.libListFont.value.sp, color = Color.White),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
    ) { ms, c ->
        val icon = ms[0].measure(Constraints())
        val textX = lay.textX.dp.roundToPx()
        val text = ms[1].measure(Constraints(maxWidth = (lay.sepRight.dp.roundToPx() - textX).coerceAtLeast(0)))
        layout(c.maxWidth, c.maxHeight) {
            icon.place(lay.side.dp.roundToPx(), (c.maxHeight - icon.height) / 2)
            text.place(textX, (c.maxHeight - text.height) / 2)
        }
    }
}

/** A–Z/#-Index rechts; Tippen oder Ziehen springt zum Abschnitt (bzw. dem nächsten vorhandenen). */
@Composable
private fun IndexBar(state: LazyListState, entries: List<Entry>, enabled: Boolean, lay: ListLayout, x: () -> Float) {
    val scope = rememberCoroutineScope()
    // Sprungziel je Buchstabe: der Kopf (iPhone) bzw. die erste Zeile mit dem Buchstaben (Duo, ohne Köpfe)
    val headerIndex = remember(entries) {
        val m = LinkedHashMap<Char, Int>()
        entries.forEachIndexed { i, e ->
            val l = when (e) { is Entry.Header -> e.letter; is Entry.Row -> letterOf(e.app) }
            if (l !in m) m[l] = i
        }
        m
    }
    fun jump(y: Float, pitch: Float) {
        val i = (y / pitch).toInt().coerceIn(0, INDEX.lastIndex)
        val target = INDEX.drop(i).firstNotNullOfOrNull { headerIndex[it] } ?: headerIndex.values.maxOrNull() ?: return
        scope.launch { state.scrollToItem(target) }
    }
    val pitchDp = DesignTokens.libIndexPitch.dp
    val widthDp = INDEX_W.dp
    Box(
        Modifier
            .offset { IntOffset((x().dp - widthDp / 2).roundToPx(), lay.indexTop.dp.roundToPx()) }
            .size(widthDp, pitchDp * INDEX.length)
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerInput(headerIndex) { detectTapGestures { jump(it.y, pitchDp.toPx()) } }
                    .pointerInput(headerIndex) { detectVerticalDragGestures { change, _ -> jump(change.position.y, pitchDp.toPx()) } }
            )
    ) {
        INDEX.forEachIndexed { i, ch ->
            BasicText(
                ch.toString(),
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.libIndexFont.value.sp,
                    color = DesignTokens.libIndexColor, textAlign = TextAlign.Center),
                modifier = Modifier.width(widthDp).offset(y = pitchDp * i),
            )
        }
    }
}

private const val INDEX_W = 20f   // pt, Trefferbreite des Index
private const val DUO_INDEX_FROM_RIGHT = 47f // Dock-Spaltenmitte
const val LIB_DUO_CLOSE_D = 44f

/**
 * Duo: progressiver Blur über dem Suchfeld — verwischte Kopie der Liste, bis zur Feldmitte voll, bis zur Feldunterkante
 * auslaufend; erscheint erst, wenn Zeilen hochgescrollt sind.
 */
@Composable
private fun ListEdge(backdrop: com.kyant.backdrop.Backdrop, state: LazyListState, geo: LibraryGeometry, density: Float) {
    val endPx = geo.search.bottom + LIST_EDGE_BELOW * density
    val solid = (geo.search.center.y / endPx).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height((endPx / density).dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = edgeAmount(state, density)
            }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.Black, solid to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }
            .drawPlainBackdrop(backdrop, { androidx.compose.ui.graphics.RectangleShape }, { blur(LIST_EDGE_BLUR.dp.toPx()) })
    )
}

/** 0…1: wie weit Zeilen über das Feld hinaufgescrollt sind (Kante erscheint erst dann). */
private fun edgeAmount(state: LazyListState, density: Float): Float {
    val scrolled = if (state.firstVisibleItemIndex > 0) Float.MAX_VALUE else state.firstVisibleItemScrollOffset.toFloat()
    return (scrolled / (LIST_EDGE_FADE_IN * density)).coerceIn(0f, 1f)
}

private const val LIST_EDGE_BELOW = 14f   // pt unter dem Feld läuft der Blur aus
private const val LIST_EDGE_BLUR = 12f
private const val LIST_EDGE_FADE_IN = 24f

/** Zeilen-Maße (pt): iPhone aus den Token, Duo gemessen. */
private class ListLayout(val side: Float, val icon: Float, val textX: Float, val sepRight: Float, val top: Float, val indexTop: Float)
private const val LIST_BOTTOM = 16f
private const val CANCEL_SLOP = 8f
