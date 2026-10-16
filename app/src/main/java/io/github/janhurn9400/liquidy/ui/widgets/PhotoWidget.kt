package io.github.janhurn9400.liquidy.ui.widgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Photos (small 2×2, large 4×4) after the iPhone screenshots 10-11 16:39 (docs/reference/widgets/08–10): a photo from the
 * gallery borderless in the widget, without a caption inside; changes hourly. Without gallery access a hint.
 */
@Composable
internal fun PhotoWidget(modifier: Modifier, album: String? = null) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.background(widgetBackground(Color(0xFF2C2C2E)))) {
        val px = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).toPx().toInt().coerceIn(64, 1600) }
        // State cached per widget instance: copies (context menu, dragging, flight) start with the finished photo
        // instead of empty — before, the "no access" hint flashed there for one frame (user 10-15)
        val cacheKey = "$album|$px"
        val state by produceState(PhotoCache[cacheKey] ?: PhotoState.Loading, px, album) {
            while (true) {
                val loaded = withContext(Dispatchers.IO) { loadPhoto(context, px, System.currentTimeMillis() / HOUR_MS, album) }
                if (loaded !is PhotoState.Loading) { PhotoCache[cacheKey] = loaded; value = loaded }
                delay(HOUR_MS - System.currentTimeMillis() % HOUR_MS)
            }
        }
        val s = state
        if (s is PhotoState.Photo) Image(s.bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // Loading: only the surface (no text that would otherwise flash briefly); hint only if there's really no access or no photos
        else if (s != PhotoState.Loading) Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.BottomStart) {
            BasicText(stringResource(if (s == PhotoState.NoAccess) R.string.photos_no_access else R.string.photos_none),
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f)))
        }
    }
}

private sealed interface PhotoState {
    data object Loading : PhotoState
    data object NoAccess : PhotoState
    data object Empty : PhotoState
    class Photo(val bitmap: ImageBitmap) : PhotoState
}

/** Last loaded result per album/size (process-wide). */
private val PhotoCache = java.util.concurrent.ConcurrentHashMap<String, PhotoState>()

private fun hasAccess(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

/** A photo from the camera gallery (preferably DCIM), a different one per hour [slot], but stable within the hour. */
private fun loadPhoto(context: Context, px: Int, slot: Long, album: String? = null): PhotoState {
    if (!hasAccess(context)) return PhotoState.NoAccess
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val ids = ArrayList<Long>()
    runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH),
            album?.let { "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?" }, album?.let { arrayOf(it) },
            "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val camera = ArrayList<Long>()
            while (c.moveToNext() && ids.size + camera.size < 500) {
                val path = c.getString(1) ?: ""
                if (path.startsWith("DCIM")) camera += c.getLong(0) else ids += c.getLong(0)
            }
            if (camera.isNotEmpty()) { ids.clear(); ids += camera }
        }
    }
    if (ids.isEmpty()) return PhotoState.Empty
    val id = ids[(Math.floorMod(slot * 2654435761L, ids.size.toLong())).toInt()]
    return runCatching {
        PhotoState.Photo(context.contentResolver.loadThumbnail(ContentUris.withAppendedId(uri, id), android.util.Size(px, px), null).asImageBitmap())
    }.getOrDefault(PhotoState.Empty)
}

private const val HOUR_MS = 3_600_000L

/** Albums (folders) with count, for the widget setting "Album". */
internal fun photoAlbums(context: Context): List<Pair<String, Int>> {
    if (!hasAccess(context)) return emptyList()
    val counts = LinkedHashMap<String, Int>()
    runCatching {
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) { val b = c.getString(0) ?: continue; counts[b] = (counts[b] ?: 0) + 1 }
        }
    }
    return counts.entries.map { it.key to it.value }
}
