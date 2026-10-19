package io.github.janhurn9400.liquidy.glass

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.ui.Inter

/**
 * Manual glass alignment ("Align Glass"): for ROMs whose wallpaper crop/zoom we can't compute (vendor wallpaper services
 * other than AOSP/LineageOS/Pixel and HyperOS). Glass refracts OUR copy of the wallpaper; if the vendor shows it zoomed
 * or shifted differently, the user lines the copy up with the real wallpaper by eye: zoom + move, saved per wallpaper.
 * The correction is applied to the copy when it is read ([SystemWallpaper.read]) — the same transform as the preview.
 */
object GlassAlign {
    /** Zoom around the screen centre, then shift by [dx]/[dy] (share of the screen width/height). */
    data class Correction(val zoom: Float = 1f, val dx: Float = 0f, val dy: Float = 0f) {
        val isIdentity get() = zoom == 1f && dx == 0f && dy == 0f
    }

    private val current = mutableStateOf(Correction())
    private val aligned = mutableStateOf(false)
    /** The user lined the copy up for the current wallpaper (then glass refracts it even on a live wallpaper). */
    val isAligned: Boolean get() = aligned.value
    /** Dock rect on screen (px) — the alignment pane lies exactly there (set by HomeScreen). */
    var dockRect: androidx.compose.ui.geometry.Rect? = null
    /** Correction for the current wallpaper (read by MainActivity when it reads the wallpaper pixels). */
    val correction: Correction get() = current.value

    /** Alignment screen visible (MainActivity hides the home screen and the setup underneath). */
    var open by mutableStateOf(false)

    private const val PREFS = "glass_align"
    private fun key(id: Int) = "c_$id"

    /** Loads the correction saved for wallpaper [id] (none = identity). */
    fun load(c: Context, id: Int) {
        val s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(id), null)
        current.value = s?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 3 }
            ?.let { Correction(it[0], it[1], it[2]) } ?: Correction()
        aligned.value = s != null
    }

    fun save(c: Context, id: Int, corr: Correction) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(id), "${corr.zoom},${corr.dx},${corr.dy}").apply()
        current.value = corr
        aligned.value = true
    }

    private fun hasSaved(c: Context, id: Int) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(key(id))

    /**
     * ROMs whose wallpaper display we compute exactly: AOSP-like (LineageOS, Pixel, emulator — WallpaperCropModel) and
     * HyperOS/MIUI (measured). Everything else (ColorOS, One UI, …) may zoom/shift differently.
     */
    fun cropModelKnown(c: Context): Boolean =
        WallpaperCropModel.isMiuiWallpaper(c) ||
            Build.DISPLAY.contains("lineage", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Google", ignoreCase = true) ||
            Build.FINGERPRINT.contains("generic") || Build.PRODUCT.contains("sdk")

    /** Offer alignment: readable still wallpaper on an unknown ROM, not aligned yet. */
    fun needed(c: Context): Boolean {
        if (!SystemWallpaper.canRead() || cropModelKnown(c)) return false
        val id = runCatching { android.app.WallpaperManager.getInstance(c).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
        return !hasSaved(c, id)
    }

    fun currentWallpaperId(c: Context): Int =
        runCatching { android.app.WallpaperManager.getInstance(c).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)

    const val MIN_ZOOM = 0.8f
    const val MAX_ZOOM = 1.35f
}

/**
 * Alignment screen: a large glass pane in the middle shows the copy glass refracts (unrefracted, so lines are easy to
 * follow); around it the real wallpaper. Pinch/slider zooms, dragging moves, until the lines run on seamlessly at the
 * pane's edge. [base] = the copy WITHOUT any correction (the preview applies the correction itself).
 */
@Composable
fun GlassAlignScreen(base: ImageBitmap, initial: GlassAlign.Correction, onReset: () -> Unit, onDone: (GlassAlign.Correction) -> Unit) {
    var zoom by remember { mutableStateOf(initial.zoom) }
    var dx by remember { mutableStateOf(initial.dx) }
    var dy by remember { mutableStateOf(initial.dy) }
    // moving is locked by default — usually only the zoom differs (user, ColorOS 10-19); unlocked with the checkbox
    var moveUnlocked by remember { mutableStateOf(initial.dx != 0f || initial.dy != 0f) }
    val textShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(GlassAlign.MIN_ZOOM, GlassAlign.MAX_ZOOM)
                    if (moveUnlocked) { dx += pan.x / size.width; dy += pan.y / size.height }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // exactly where the dock is (if it matches there, it matches everywhere); fallback: a pane at the bottom
            val dock = GlassAlign.dockRect
            val pw = dock?.width ?: (size.width * PANE_W); val ph = dock?.height ?: (size.height * PANE_H)
            val tl = dock?.topLeft ?: Offset((size.width - pw) / 2, size.height * PANE_TOP)
            val r = minOf(PANE_CORNER.dp.toPx(), ph / 2)
            val pane = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(tl.x, tl.y, tl.x + pw, tl.y + ph, CornerRadius(r))) }
            clipPath(pane) {
                withTransform({
                    translate(dx * size.width, dy * size.height)
                    scale(zoom, zoom, pivot = Offset(size.width / 2, size.height / 2))
                }) { drawImage(base) }
            }
            // thin light rim like glass (no refraction here — the lines must stay comparable)
            drawRoundRect(Color.White.copy(alpha = RIM_ALPHA), tl, Size(pw, ph), CornerRadius(r), style = Stroke(RIM_W.dp.toPx()))
        }
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(stringResource(R.string.glass_align_title), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, color = Color.White, shadow = textShadow, textAlign = TextAlign.Center))
            Spacer(Modifier.height(6.dp))
            BasicText(stringResource(R.string.glass_align_hint), style = TextStyle(fontFamily = Inter, fontSize = 15.sp,
                color = Color.White, shadow = textShadow, textAlign = TextAlign.Center))
        }
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = PANEL_DARK)).padding(16.dp),
        ) {
            val sliderColors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f))
            val label = TextStyle(fontFamily = Inter, fontSize = 14.sp, color = Color.White)
            val value = label.copy(fontWeight = FontWeight.SemiBold)
            // values visible (to report them so a ROM's zoom can be built in)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(stringResource(R.string.glass_align_zoom), style = label)
                BasicText(String.format(java.util.Locale.ROOT, "%.3f×", zoom), style = value)
            }
            androidx.compose.material3.Slider(value = zoom, onValueChange = { zoom = it }, valueRange = GlassAlign.MIN_ZOOM..GlassAlign.MAX_ZOOM, colors = sliderColors)
            Row(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) {
                moveUnlocked = !moveUnlocked
                if (!moveUnlocked) { dx = 0f; dy = 0f }
            }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = moveUnlocked, onCheckedChange = null,
                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black,
                        uncheckedColor = Color.White.copy(alpha = 0.7f)))
                Spacer(Modifier.width(8.dp))
                BasicText(stringResource(R.string.glass_align_move), style = label)
            }
            if (moveUnlocked) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(stringResource(R.string.glass_align_horizontal), style = label)
                    BasicText(String.format(java.util.Locale.ROOT, "%+.1f %%", dx * 100), style = value)
                }
                androidx.compose.material3.Slider(value = dx, onValueChange = { dx = it }, valueRange = -MAX_SHIFT..MAX_SHIFT, colors = sliderColors)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(stringResource(R.string.glass_align_vertical), style = label)
                    BasicText(String.format(java.util.Locale.ROOT, "%+.1f %%", dy * 100), style = value)
                }
                androidx.compose.material3.Slider(value = dy, onValueChange = { dy = it }, valueRange = -MAX_SHIFT..MAX_SHIFT, colors = sliderColors)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PillButton(stringResource(R.string.glass_align_reset), filled = false) { zoom = 1f; dx = 0f; dy = 0f; onReset() }
                PillButton(stringResource(R.string.glass_align_done), filled = true) { onDone(GlassAlign.Correction(zoom, dx, dy)) }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        BasicText(label, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            color = if (filled) Color.Black else Color.White))
    }
}

/** Largest shift via the sliders (share of the screen width/height). */
private const val MAX_SHIFT = 0.15f
private const val PANE_W = 0.86f
private const val PANE_H = 0.36f
private const val PANE_TOP = 0.24f
private const val PANE_CORNER = 32f
private const val RIM_W = 1.5f
private const val RIM_ALPHA = 0.55f
private const val PANEL_DARK = 0.45f
