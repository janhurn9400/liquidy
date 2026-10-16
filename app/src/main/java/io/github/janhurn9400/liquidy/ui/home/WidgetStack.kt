package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.janhurn9400.liquidy.data.WidgetSpec
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.ui.widgets.AndroidWidget
import io.github.janhurn9400.liquidy.ui.widgets.IosWidget
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Widget stack like iOS (screen recording 10-08 09:56): widgets lie vertically on top of each other and are swiped through.
 * While swiping the content sits shrunk in a Liquid Glass rim (a thick glass edge refracts the wallpaper),
 * the next widget slides in from below; afterwards the rim fades out. To the right vertical dots
 * (white = current widget). Draws only the content — size/clip/label are done by WidgetCell.
 */
@Composable
fun WidgetStackContent(key: String, spec: WidgetSpec, model: HomeModel, wallpaper: ImageBitmap) {
    val pos = model.stackPosition(key)
    val active = model.stackActivity(key)   // swiping: content shrunk
    val rim = model.stackRimAlpha(key)      // glass rim visible
    Box(Modifier.fillMaxSize()) {
        // Glass rim: appears on touch, stays during the regrowth and only fades out afterwards
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = rim.value }) {
            // Source = only the wallpaper (the widget itself lies in the home glass source → recursion otherwise)
            WithWallpaperGlass(wallpaper) {
                LiquidGlass(glassRect(DesignTokens.widgetCorner.dp), Modifier.fillMaxSize(), thickness = 2.5f, whiteTint = 0f, blackTint = 0f)
            }
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // Content shrunk in the rim while swiping (≈ 12 pt margin, frame 880)
                // Distance to the rim: ≈ 24 pt while swiping (incl. shrinking), regrown ≈ 3.3 pt of glass rim stays visible
                val inset = (STACK_INSET_DP * active.value + STACK_RIM_DP * rim.value) * density
                val s = 1f - 2f * inset / size.width
                scaleX = s; scaleY = 1f - 2f * inset / size.height
                clip = true
                shape = glassRect((DesignTokens.widgetCorner.value - STACK_INSET_DP * active.value - STACK_RIM_DP * rim.value).coerceAtLeast(8f).dp)
            }
        ) {
            val p = pos.value
            for (i in floor(p).toInt()..ceil(p).toInt()) {
                val child = spec.stack.getOrNull(i) ?: continue
                key(i) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val d = i - pos.value
                        translationY = d * size.height
                        val sc = 1f - STACK_DEPTH * abs(d); scaleX = sc; scaleY = sc
                    }.clip(glassRect(DesignTokens.widgetCorner.dp))) {
                        if (child.type == "android") AndroidWidget(child, Modifier.fillMaxSize())
                        else WithWallpaperGlass(wallpaper) { IosWidget(child.type, child.cols, child.rows, Modifier.fillMaxSize(), child.config) }
                    }
                }
            }
        }
    }
}

/** Vertical dots to the right of the stack (outside the widget, frame 880). */
@Composable
fun StackDots(key: String, count: Int, model: HomeModel, modifier: Modifier) {
    val pos = model.stackPosition(key)
    val dots = model.stackDotsAlpha(key)
    Canvas(modifier.graphicsLayer { alpha = dots.value }) {
        val r = DOT_D / 2 * density; val pitch = DOT_PITCH * density
        val top = size.height / 2 - (count - 1) * pitch / 2
        for (k in 0 until count) {
            val on = (1f - abs(k - pos.value)).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * on), r, Offset(size.width / 2, top + k * pitch))
        }
    }
}

@Composable
private inline fun key(k: Any, crossinline content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

internal const val STACK_DOTS_GAP = 6f   // pt from the right widget edge
internal const val STACK_DOTS_W = 8f
private const val STACK_RIM_DP = 3.3f   // frame 892: rim outside 626, content 636 px (3×)
private const val STACK_INSET_DP = 14f  // shrunk in the rim, regrows in ≈ 0.4 s after settling (frame 865–889)
private const val STACK_DEPTH = 0.35f    // widgets sliding in/out shrink clearly (frame 861: ≈ 0.65×)
private const val DOT_D = 6f
private const val DOT_PITCH = 11f
