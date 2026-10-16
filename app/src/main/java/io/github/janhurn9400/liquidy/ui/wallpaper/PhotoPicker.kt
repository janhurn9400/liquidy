package io.github.janhurn9400.liquidy.ui.wallpaper

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop
import io.github.janhurn9400.liquidy.glass.WallpaperKind
import io.github.janhurn9400.liquidy.glass.WallpaperSettings
import io.github.janhurn9400.liquidy.glass.glassCapsule
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private data class Photo(val uri: Uri, val bucket: String?, val favourite: Boolean)
private enum class Filter { All, Favourites, Screenshots }

private suspend fun queryPhotos(context: Context): List<Photo> = withContext(Dispatchers.IO) {
    val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.IS_FAVORITE)
    val out = mutableListOf<Photo>()
    // oldest at the top, newest at the bottom (the iOS photo picker starts at the bottom)
    context.contentResolver.query(base, proj, null, null, "${MediaStore.Images.Media.DATE_ADDED} ASC")?.use { c ->
        val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val b = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val f = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
        while (c.moveToNext()) out += Photo(ContentUris.withAppendedId(base, c.getLong(id)), c.getString(b), c.getInt(f) == 1)
    }
    out
}

/**
 * Photo picker in iOS style (recording6 28–36 s): dark sheet, glass buttons at the top (X, "Photos | Collections", filter chips)
 * that refract the grid below; 3 columns, cells in screen format (= wallpaper crop).
 * Android: own gallery via MediaStore (READ_MEDIA_IMAGES); without permission fall back to the system picker.
 */
@Composable
internal fun PhotoPicker(onClose: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val permission = Manifest.permission.READ_MEDIA_IMAGES
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val system = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPick(uri.toString()) else onClose()
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) system.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(Unit) { if (!granted) request.launch(permission) }

    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.wpeSheet.spec()) }
    fun close() = scope.launch { appear.animateTo(0f, Motion.wpeSheet.spec()); onClose() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val screenW = constraints.maxWidth.toFloat(); val screenH = constraints.maxHeight.toFloat()
        val photos by produceState(emptyList<Photo>(), granted) { if (granted) value = queryPhotos(context) }
        var tab by remember { mutableStateOf(0) } // 0 = Photos, 1 = Collections
        var filter by remember { mutableStateOf(Filter.All) }
        var bucket by remember { mutableStateOf<String?>(null) }
        val shown = photos.filter {
            (bucket == null || it.bucket == bucket) && when (filter) {
                Filter.All -> true
                Filter.Favourites -> it.favourite
                Filter.Screenshots -> it.bucket.equals("Screenshots", ignoreCase = true)
            }
        }
        val top = PICKER_TOP
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = 0.4f)))
        Box(
            Modifier
                .offset { IntOffset(0, ((top + (1f - appear.value) * (screenH / density - top)) * density).roundToInt()) }
                .fillMaxWidth()
                .height(Dp(screenH / density - top))
                .clip(RoundedCornerShape(topStart = SHEET_CORNER.dp, topEnd = SHEET_CORNER.dp))
                .background(Color.Black)
        ) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                if (tab == 0) {
                    val grid = rememberLazyGridState()
                    LaunchedEffect(shown.size) { if (shown.isNotEmpty()) grid.scrollToItem(shown.lastIndex) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = grid,
                        contentPadding = PaddingValues(top = GRID_TOP.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(GRID_GAP.dp),
                        verticalArrangement = Arrangement.spacedBy(GRID_GAP.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown.size, key = { shown[it].uri.toString() }) { i ->
                            val p = shown[i]
                            Thumb(p.uri, Modifier.fillMaxWidth().aspectRatio(screenW / screenH)
                                .clickable { onPick(p.uri.toString()) })
                        }
                    }
                    if (granted && shown.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BasicText(stringResource(R.string.no_photos), style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.White.copy(alpha = 0.6f)))
                    }
                } else {
                    val albums = photos.groupBy { it.bucket ?: "?" }.toList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(top = GRID_TOP.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(albums.size) { i ->
                            val (name, list) = albums[i]
                            androidx.compose.foundation.layout.Column(Modifier.clickable { bucket = name; tab = 0; filter = Filter.All }) {
                                Thumb(list.last().uri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                                BasicText(name, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                BasicText(list.size.toString(), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f)))
                            }
                        }
                    }
                }
            }
            // ── Glass header above the grid ──
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                LiquidGlass(
                    shape = glassCapsule(),
                    modifier = Modifier.offset(x = CLOSE_X.dp, y = HEADER_Y.dp - CLOSE_D.dp / 2).size(CLOSE_D.dp),
                    whiteTint = GLASS_TINT,
                ) {
                    Canvas(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }) {
                        val s = size.width; val a = s * 0.34f; val sw = s * 0.055f
                        drawLine(Color.White, Offset(a, a), Offset(s - a, s - a), sw, StrokeCap.Round)
                        drawLine(Color.White, Offset(s - a, a), Offset(a, s - a), sw, StrokeCap.Round)
                    }
                }
                LiquidGlass(
                    shape = glassCapsule(),
                    modifier = Modifier.offset(x = SEG_X.dp, y = HEADER_Y.dp - CLOSE_D.dp / 2).size(SEG_W.dp, CLOSE_D.dp),
                    whiteTint = GLASS_TINT,
                ) {
                    Row(Modifier.fillMaxSize().padding(SEG_INSET.dp)) {
                        listOf(R.string.photos_tab, R.string.collections_tab).forEachIndexed { i, label ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(if (tab == i) SEG_SELECTED else Color.Transparent)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab = i },
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(stringResource(label), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Color.White))
                            }
                        }
                    }
                }
                if (tab == 0) Row(
                    Modifier.offset(y = CHIPS_Y.dp - CHIP_H.dp / 2).padding(start = CHIPS_X.dp),
                    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
                ) {
                    Filter.entries.forEach { f ->
                        val label = stringResource(when (f) { Filter.All -> R.string.filter_all; Filter.Favourites -> R.string.filter_favourites; Filter.Screenshots -> R.string.filter_screenshots })
                        val selected = filter == f && bucket == null
                        val chip = Modifier.height(CHIP_H.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { filter = f; bucket = null }
                        if (selected) Box(chip.clip(CircleShape).background(CHIP_SELECTED).padding(horizontal = CHIP_PAD.dp), contentAlignment = Alignment.Center) {
                            BasicText(label, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Color.White))
                        } else LiquidGlass(shape = glassCapsule(), modifier = chip, whiteTint = GLASS_TINT) {
                            Box(Modifier.fillMaxHeight().padding(horizontal = CHIP_PAD.dp), contentAlignment = Alignment.Center) {
                                BasicText(label, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Color.White))
                            }
                        }
                    }
                    bucket?.let { b ->
                        Box(Modifier.height(CHIP_H.dp).clip(CircleShape).background(CHIP_SELECTED).padding(horizontal = CHIP_PAD.dp), contentAlignment = Alignment.Center) {
                            BasicText(b, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Color.White), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(THUMB_PX, THUMB_PX), null).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier) { // without grey placeholders (they flashed briefly as an empty grid on open)
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/**
 * Crop (recording6 37.1 s): photo screen-filling, zoom/move with two fingers; Cancel/Done at the top.
 * Result: zoom (≥ 1) and the visible image centre (0…1), from which WallpaperRenderer computes the crop.
 */
@Composable
internal fun PhotoCrop(uri: String, initial: WallpaperSettings, onReady: () -> Unit, onCancel: () -> Unit, onDone: (WallpaperSettings) -> Unit) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val image by produceState<ImageBitmap?>(null, uri) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { d, info, _ ->
                        val s = CROP_DECODE * max(w / info.size.width, h / info.size.height)
                        if (s < 1f) d.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
        var zoom by remember { mutableStateOf(initial.photoZoom.coerceAtLeast(1f)) }
        var cx by remember { mutableStateOf(initial.photoX) }
        var cy by remember { mutableStateOf(initial.photoY) }
        // visible only once the photo is decoded — until then the picker below stays
        LaunchedEffect(image) { if (image != null) onReady() }
        if (image != null) Box(Modifier.fillMaxSize().background(Color.Black))
        image?.let { img ->
            val cover = max(w / img.width, h / img.height)
            fun clampCenter() {
                val dw = img.width * cover * zoom; val dh = img.height * cover * zoom
                cx = cx.coerceIn(w / 2 / dw, 1f - w / 2 / dw)
                cy = cy.coerceIn(h / 2 / dh, 1f - h / 2 / dh)
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(img) {
                        detectTransformGestures { _, pan, z, _ ->
                            zoom = (zoom * z).coerceIn(1f, MAX_ZOOM)
                            val dw = img.width * cover * zoom; val dh = img.height * cover * zoom
                            cx -= pan.x / dw; cy -= pan.y / dh
                            clampCenter()
                        }
                    }
            ) {
                val dw = img.width * cover * zoom; val dh = img.height * cover * zoom
                drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset((w / 2 - cx * dw).roundToInt(), (h / 2 - cy * dh).roundToInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(dw.roundToInt(), dh.roundToInt()))
            }
        }
        if (image != null) TopButton(stringResource(R.string.cancel), alignLeft = true, onClick = onCancel)
        if (image != null) TopButton(stringResource(R.string.done), alignLeft = false) {
            onDone(initial.copy(kind = WallpaperKind.Photo, photoUri = uri, photoZoom = zoom, photoX = cx, photoY = cy))
        }
    }
}

// Dimensions [MEASURED roughly] recording6 29.4 s (montage 0.804 pt/px)
private const val PICKER_TOP = 62f
private const val SHEET_CORNER = 38f
private const val HEADER_Y = 38f     // centre of the header row, from the sheet top
private const val CLOSE_X = 16f
private const val CLOSE_D = 44f
private const val SEG_X = 107f
private const val SEG_W = 190f
private const val SEG_INSET = 4f
private const val CHIPS_Y = 90f
private const val CHIPS_X = 16f
private const val CHIP_H = 32f
private const val CHIP_GAP = 8f
private const val CHIP_PAD = 14f
private const val GRID_TOP = 112f
private const val GRID_GAP = 1.33f
private const val THUMB_PX = 360
private const val GLASS_TINT = 0.12f
private const val CROP_DECODE = 2f   // decode sharp enough for zoom up to 2×
private const val MAX_ZOOM = 4f
private val SEG_SELECTED = Color(0x40FFFFFF)
private val CHIP_SELECTED = Color(0xFF1BA6FB) // "Featured" measured
