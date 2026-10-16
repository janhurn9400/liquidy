package io.github.janhurn9400.liquidy.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.motion.Motion
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Reusable frosted-glass sheet from below (selection lists, widget settings etc.):
 * slides in and out with M-WPE-SHEET (interruptible), dark frosted glass without refraction, header with a round
 * × button and title. The caller reads [GlassSheetState.progress], e.g. to let content behind it recede.
 *
 * ```
 * val sheet = rememberGlassSheetState()
 * …  onClick = { sheet.open() }
 * GlassSheet(sheet, title = "Album") { rows … }   // inside a BoxWithConstraints
 * ```
 */
@Stable
class GlassSheetState internal constructor(private val scope: CoroutineScope) {
    /** 0 = closed, 1 = open. */
    val progress = Animatable(0f)
    /** Is shown (also while sliding out). */
    var visible by mutableStateOf(false)
        private set

    fun open() {
        visible = true
        scope.launch { progress.animateTo(1f, Motion.wpeSheet.spec()) }
    }

    /** Slides out; [then] runs afterwards (e.g. apply a selection without the sheet disappearing abruptly). */
    fun close(then: () -> Unit = {}) {
        scope.launch {
            progress.animateTo(0f, Motion.wpeSheet.spec(visibilityThreshold = 0.001f))
            visible = false
            then()
        }
    }
}

@Composable
fun rememberGlassSheetState(): GlassSheetState {
    val scope = rememberCoroutineScope()
    return remember { GlassSheetState(scope) }
}

/**
 * The sheet itself, bottom centre in the surrounding [BoxWithConstraintsScope]: at most [maxWidthDp] wide, [heightShare] of the
 * height tall. [content] is the area below the header (scrolls itself if needed).
 */
@Composable
fun BoxWithConstraintsScope.GlassSheet(
    state: GlassSheetState,
    title: String,
    maxWidthDp: Float = SHEET_MAX_W,
    heightShare: Float = SHEET_H_SHARE,
    onClose: () -> Unit = { state.close() },
    /** Floats at the bottom above the content (e.g. glass search field): the content runs under it and is refracted by it.
     *  The content should leave [GlassSheetBottomBarSpace] at the end so the last row can be scrolled clear. */
    bottomBar: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!state.visible) return
    val noRipple = remember { MutableInteractionSource() }
    val h = maxHeight.value * heightShare
    Box(
        Modifier.align(Alignment.BottomCenter)
            // reaches around the corner below the screen edge → no round corners visible at the bottom
            .offset(y = SHEET_CORNER.dp).width(minOf(maxWidthDp, maxWidth.value).dp).height((h + SHEET_CORNER).dp)
            .graphicsLayer { translationY = (1f - state.progress.value) * size.height }
            .clickable(noRipple, null) { }, // don't pass taps in the sheet through to the background
    ) {
        // Frosted glass (iOS selection sheets: continuous grey area, no refraction) [user on the iPhone 10-15].
        // Area AND content are the glass source for [bottomBar]: the search field refracts the rows running under it
        val surface = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
        Box(Modifier.matchParentSize().layerBackdrop(surface)) {
            LiquidGlass(shape = glassRect(SHEET_CORNER.dp), modifier = Modifier.matchParentSize(),
                blur = SHEET_BLUR.dp, blackTint = SHEET_DARK, whiteTint = SHEET_GREY, refraction = false)
            Column(Modifier.fillMaxSize().padding(bottom = SHEET_CORNER.dp)) {
                Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp)) {
                    Box(Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).background(SheetRowColor)
                        .clickable(noRipple, null, onClick = onClose), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(16.dp)) {
                            val sw = 2.2.dp.toPx()
                            drawLine(Color.White, Offset.Zero, Offset(size.width, size.height), sw, StrokeCap.Round)
                            drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), sw, StrokeCap.Round)
                        }
                    }
                    BasicText(title, Modifier.align(Alignment.Center),
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White))
                }
                content()
            }
        }
        if (bottomBar != null) androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides surface) {
            Box(Modifier.matchParentSize().padding(bottom = SHEET_CORNER.dp), content = bottomBar)
        }
    }
}

/** Space at the end of the sheet content for a floating [GlassSheet] bottomBar (search field 44 + margins). */
val GlassSheetBottomBarSpace = 76.dp

/** Background of buttons/fields on the glass sheet. */
val SheetRowColor = Color.White.copy(alpha = 0.12f)

private const val SHEET_CORNER = 38f
private const val SHEET_BLUR = 40f   // strongly blurred: only colours shine through
private const val SHEET_DARK = 0.6f   // grey and almost opaque: clearly recognisable as a window [user 10-15]
private const val SHEET_GREY = 0.16f
private const val SHEET_MAX_W = 461f
private const val SHEET_H_SHARE = 0.86f
