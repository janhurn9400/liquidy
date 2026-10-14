package io.github.janhurn9400.liquidy.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
 * Wiederverwendbares Milchglas-Blatt von unten (Auswahllisten, Einstellungen von Widgets usw.):
 * gleitet mit M-WPE-SHEET herein und hinaus (unterbrechbar), dunkles Milchglas ohne Brechung, Kopf mit runder
 * ×-Taste und Titel. Der Aufrufer liest [GlassSheetState.progress], um z. B. Inhalte dahinter zurückweichen zu lassen.
 *
 * ```
 * val sheet = rememberGlassSheetState()
 * …  onClick = { sheet.open() }
 * GlassSheet(sheet, title = "Album") { Zeilen … }   // in einem BoxWithConstraints
 * ```
 */
@Stable
class GlassSheetState internal constructor(private val scope: CoroutineScope) {
    /** 0 = zu, 1 = offen. */
    val progress = Animatable(0f)
    /** Wird gezeigt (auch während des Hinausgleitens). */
    var visible by mutableStateOf(false)
        private set

    fun open() {
        visible = true
        scope.launch { progress.animateTo(1f, Motion.wpeSheet.spec()) }
    }

    /** Gleitet hinaus; [then] läuft danach (z. B. Auswahl übernehmen, ohne dass das Blatt hart verschwindet). */
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
 * Das Blatt selbst, unten mittig im umgebenden [BoxWithConstraintsScope]: höchstens [maxWidthDp] breit, [heightShare] der
 * Höhe hoch. [content] ist der Bereich unter dem Kopf (scrollt selbst, falls nötig).
 */
@Composable
fun BoxWithConstraintsScope.GlassSheet(
    state: GlassSheetState,
    title: String,
    maxWidthDp: Float = SHEET_MAX_W,
    heightShare: Float = SHEET_H_SHARE,
    onClose: () -> Unit = { state.close() },
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!state.visible) return
    val noRipple = remember { MutableInteractionSource() }
    val h = maxHeight.value * heightShare
    Box(
        Modifier.align(Alignment.BottomCenter)
            // reicht um die Ecke unter den Bildschirmrand → unten keine runden Ecken sichtbar
            .offset(y = SHEET_CORNER.dp).width(minOf(maxWidthDp, maxWidth.value).dp).height((h + SHEET_CORNER).dp)
            .graphicsLayer { translationY = (1f - state.progress.value) * size.height }
            .clickable(noRipple, null) { }, // Tipps im Blatt nicht an den Hintergrund durchreichen
    ) {
        // Milchglas (iOS-Auswahlblätter: durchgehende graue Fläche, keine Brechung) [Nutzer am iPhone 10-15]; die Fläche ist
        // selbst Glasquelle für Glas-Elemente im Blatt (z. B. Suchfeld) — sie brechen das Blatt, nicht den Homescreen dahinter
        val surface = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
        Box(Modifier.matchParentSize().then(Modifier.layerBackdrop(surface))) {
            LiquidGlass(shape = glassRect(SHEET_CORNER.dp), modifier = Modifier.matchParentSize(),
                blur = SHEET_BLUR.dp, blackTint = SHEET_DARK, whiteTint = SHEET_GREY, refraction = false)
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides surface) {
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
    }
}

/** Hintergrund von Knöpfen/Feldern auf dem Glasblatt. */
val SheetRowColor = Color.White.copy(alpha = 0.12f)

private const val SHEET_CORNER = 38f
private const val SHEET_BLUR = 40f   // kräftig verwischt: nur Farben scheinen durch
private const val SHEET_DARK = 0.6f   // grau und fast deckend: klar als Fenster erkennbar [Nutzer 10-15]
private const val SHEET_GREY = 0.16f
private const val SHEET_MAX_W = 461f
private const val SHEET_H_SHARE = 0.86f
