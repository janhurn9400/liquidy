package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.data.IconPacks
import io.github.janhurn9400.liquidy.data.IconRenderer
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.GlassSheet
import io.github.janhurn9400.liquidy.glass.SheetRowColor
import io.github.janhurn9400.liquidy.glass.rememberGlassSheetState
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Change Icon" (context menu): a glass sheet with every icon of the installed icon packs (one pack at a time, tabs on
 * top when there are several), a search field and "Original" to go back. Tapping an icon sets it for this app only.
 */
@Composable
fun IconPickerSheet(app: AppEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberGlassSheetState()
    LaunchedEffect(Unit) { sheet.open() }
    val packs = remember { IconPacks.available(context) }
    var pack by remember { mutableStateOf(IconPacks.overrideOf(app.component)?.substringBefore('|') ?: IconPacks.selected ?: packs.firstOrNull()?.packageName) }
    var query by remember { mutableStateOf("") }
    val names by produceState(emptyList<String>(), pack) { value = pack?.let { p -> withContext(Dispatchers.Default) { IconPacks.allIcons(context, p) } }.orEmpty() }
    val q = query.trim().lowercase().replace(' ', '_')
    val shown = remember(names, q) { if (q.isEmpty()) names else names.filter { it.contains(q) } }
    fun close() = sheet.close(onClose)
    // own picture as icon (when the pack has nothing fitting): Android's photo picker, no permission needed
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && IconPacks.setOverrideFromImage(context, app.component, uri)) close()
    }
    androidx.activity.compose.BackHandler { close() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        GlassSheet(sheet, app.label, onClose = { close() }, bottomBar = {
            // Liquid Glass search field floating over the grid (refracts the icons scrolling under it), like the city
            // picker of the clock widget; rides above the keyboard
            Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.ime).padding(16.dp).fillMaxWidth().height(44.dp)) {
                io.github.janhurn9400.liquidy.glass.LiquidGlass(shape = io.github.janhurn9400.liquidy.glass.glassCapsule(), modifier = Modifier.matchParentSize(),
                    whiteTint = DesignTokens.libListFieldTint.value)
                Row(Modifier.matchParentSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                        val sw = 2.dp.toPx(); val r = size.width * 0.36f
                        drawCircle(SEARCH_GREY, r, androidx.compose.ui.geometry.Offset(r + sw / 2, r + sw / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                        drawLine(SEARCH_GREY, androidx.compose.ui.geometry.Offset(r * 1.7f, r * 1.7f), androidx.compose.ui.geometry.Offset(size.width, size.height), sw,
                            androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) BasicText(stringResource(R.string.change_icon_search),
                            style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = SEARCH_GREY))
                        BasicTextField(query, { query = it }, singleLine = true, cursorBrush = SolidColor(Color(0xFF0A84FF)),
                            textStyle = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.White), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }) {
            val noRipple = remember { MutableInteractionSource() }
            if (packs.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                packs.forEach { p ->
                    BasicText(p.label, Modifier.clip(CircleShape).background(if (p.packageName == pack) Color.White else SheetRowColor)
                        .clickable(noRipple, null) { pack = p.packageName }.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (p.packageName == pack) Color.Black else Color.White))
                }
            }
            val current = IconPacks.overrideOf(app.component)
            LazyVerticalGrid(GridCells.Adaptive(PICKER_CELL.dp), Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 90.dp)) {
                item(key = "original") {
                    PickerCell(app.icon, stringResource(R.string.change_icon_original), current == null) {
                        IconPacks.setOverride(context, app.component, null, null); close()
                    }
                }
                item(key = "gallery") {
                    PickerCell(null, stringResource(R.string.change_icon_photos), current?.startsWith(IconPacks.FILE + "|") == true, glyph = true) {
                        gallery.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                items(shown, key = { it }) { n ->
                    val px = with(LocalDensity.current) { PICKER_ICON.dp.roundToPx() }
                    val img by produceState<ImageBitmap?>(null, pack, n) {
                        value = withContext(Dispatchers.Default) { pack?.let { p -> IconPacks.named(context, p, n)?.let { IconRenderer.render(it, px, fullBleed = true) } } }
                    }
                    PickerCell(img, n.replace('_', ' '), current == "$pack|$n") {
                        IconPacks.setOverride(context, app.component, pack, n); close()
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerCell(img: ImageBitmap?, label: String, selected: Boolean, glyph: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = ContinuousRoundedShape((PICKER_ICON * ICON_SHARE).dp)
        Box(Modifier.size(PICKER_ICON.dp).clip(shape).background(Color.White.copy(alpha = 0.08f))
            .then(if (selected) Modifier.background(Color.White.copy(alpha = 0.25f)) else Modifier)) {
            img?.let { Image(it, null, Modifier.fillMaxSize()) }
            // "From Photos": a picture glyph (mountain + sun in a frame)
            if (glyph) androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val sw = 2.dp.toPx(); val w = size.width
                drawRoundRect(Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(sw), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.15f))
                drawCircle(Color.White, w * 0.09f, androidx.compose.ui.geometry.Offset(w * 0.68f, w * 0.32f))
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.1f, w * 0.85f); lineTo(w * 0.4f, w * 0.5f); lineTo(w * 0.65f, w * 0.78f); lineTo(w * 0.75f, w * 0.66f); lineTo(w * 0.9f, w * 0.85f) }
                drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        BasicText(label, Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp), maxLines = 1,
            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, textAlign = TextAlign.Center,
                color = if (selected) Color(0xFF64B5FF) else Color.White.copy(alpha = 0.8f)))
    }
}

private const val PICKER_CELL = 76f
private val SEARCH_GREY = Color(0x99EBEBF5)
private const val PICKER_ICON = 54f
private val ICON_SHARE = DesignTokens.iconCorner.value / DesignTokens.iconSize.value
