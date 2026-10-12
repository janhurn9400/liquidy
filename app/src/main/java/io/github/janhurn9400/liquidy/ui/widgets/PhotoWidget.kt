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
 * Fotos (klein 2×2, groß 4×4) nach den iPhone-Screenshots 10-11 16:39 (docs/reference/widgets/08–10): ein Foto aus der
 * Galerie randlos im Widget, ohne Beschriftung darin; wechselt stündlich. Ohne Galerie-Zugriff ein Hinweis.
 */
@Composable
internal fun PhotoWidget(modifier: Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.background(Color(0xFF2C2C2E))) {
        val px = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).toPx().toInt().coerceIn(64, 1600) }
        val photo by produceState<ImageBitmap?>(null, px) {
            while (true) {
                value = withContext(Dispatchers.IO) { loadPhoto(context, px, System.currentTimeMillis() / HOUR_MS) } ?: value
                delay(HOUR_MS - System.currentTimeMillis() % HOUR_MS)
            }
        }
        val p = photo
        if (p != null) Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.BottomStart) {
            BasicText(stringResource(R.string.photos_no_access),
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f)))
        }
    }
}

private fun hasAccess(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

/** Ein Foto aus der Kamera-Galerie (bevorzugt DCIM), je Stunde [slot] ein anderes, aber stabil innerhalb der Stunde. */
private fun loadPhoto(context: Context, px: Int, slot: Long): ImageBitmap? {
    if (!hasAccess(context)) return null
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val ids = ArrayList<Long>()
    runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH), null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val camera = ArrayList<Long>()
            while (c.moveToNext() && ids.size + camera.size < 500) {
                val path = c.getString(1) ?: ""
                if (path.startsWith("DCIM")) camera += c.getLong(0) else ids += c.getLong(0)
            }
            if (camera.isNotEmpty()) { ids.clear(); ids += camera }
        }
    }
    if (ids.isEmpty()) return null
    val id = ids[(Math.floorMod(slot * 2654435761L, ids.size.toLong())).toInt()]
    return runCatching {
        context.contentResolver.loadThumbnail(ContentUris.withAppendedId(uri, id), android.util.Size(px, px), null).asImageBitmap()
    }.getOrNull()
}

private const val HOUR_MS = 3_600_000L
