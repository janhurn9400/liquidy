package io.github.janhurn9400.liquidy.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.GlassRim
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.ui.home.HomeModel
import io.github.janhurn9400.liquidy.motion.Motion

/**
 * Suche (Swipe nach unten), Layout nach IMG_3131: Überschrift „Siri Suggestions“ + „Show More“,
 * dunkle Glaskarte mit 4 Vorschlägen bzw. Treffern, Suchfeld-Kapsel direkt über der Tastatur.
 * Die Tastatur-Animation gehört dem System (RESEARCH M-SEARCH-KB); das Feld hängt an WindowInsets.ime.
 */
@Composable
fun SearchOverlay(model: HomeModel, onLaunch: (AppEntry, Rect) -> Unit,
    /** Duo aufgeklappt: Vorschläge als mittige Spalte dieser Breite (dp), sonst volle Breite. */
    columnWidth: androidx.compose.ui.unit.Dp? = null) {
    val density = LocalDensity.current
    val noRipple = remember { MutableInteractionSource() }


    fun launch(app: AppEntry, bounds: Rect) {
        onLaunch(app, bounds)
        model.closeSearch(animated = false)
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (model.searchOpen) Modifier.clickable(interactionSource = noRipple, indication = null) { model.closeSearch() } else Modifier)
    ) {
        // Vorschlagsblock: blendet mit p ein und folgt dem Finger (M-SEARCH-BLOCK), federt nach dem Loslassen zurück
        val blockLayer = Modifier.graphicsLayer {
            alpha = model.searchBlockAlpha()
            translationY = model.searchBlockOffset.value * density.density
            // blendet unscharf ein und wird mit dem Zugweg scharf (gemessen)
            val r = model.searchBlockBlurFraction() * DesignTokens.searchBlockBlur.value * density.density
            renderEffect = if (r > 0.5f) androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal) else null
        }
        val colMod = if (columnWidth != null) Modifier.align(Alignment.TopCenter).width(columnWidth) else Modifier
        val results = model.searchResults(COLUMNS * 2)
        val showResults = model.query.isNotBlank()
        val items = if (showResults) results else model.suggestions(COLUMNS)

        // Vorschlagsblock als EINE Ebene; ihre Höhe umfasst Überschrift + Karte, damit Blur/Versatz nichts abschneiden
        Box(
            colMod
                .then(if (columnWidth == null) Modifier.fillMaxWidth() else Modifier)
                .height(DesignTokens.searchCardTop.dp + DesignTokens.searchCardHeight.dp * 2 + DesignTokens.searchCardSide.dp)
                .then(blockLayer)
        ) {
        // Überschrift
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = DesignTokens.searchHeaderBaseline.dp - with(density) { DesignTokens.searchHeaderSize.value.sp.toDp() })
                .padding(horizontal = DesignTokens.searchHeaderX.dp),
        ) {
            BasicText(
                stringResource(if (showResults) R.string.search_apps else R.string.siri_suggestions),
                style = headerStyle(),
                modifier = Modifier.weight(1f),
            )
            if (!showResults) BasicText(stringResource(R.string.show_more), style = headerStyle())
        }

        // Karte
        val cardShape = ContinuousRoundedShape(DesignTokens.searchCardCorner.dp)
        Box(
            Modifier
                .offset(x = DesignTokens.searchCardSide.dp, y = DesignTokens.searchCardTop.dp)
                .fillMaxWidth()
                .padding(end = DesignTokens.searchCardSide.dp * 2)
                .height(DesignTokens.searchCardHeight.dp * if (showResults && items.size > COLUMNS) 2 else 1)
                .clip(cardShape)
                .background(DesignTokens.searchCardFill)
                .clickable(interactionSource = noRipple, indication = null) { },
        ) {
            // iOS (IMG_3131): Vorschlags-Karte ohne Lichtkante — nur die helle Fläche
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                items.chunked(COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { app -> SearchAppCell(app) { b -> launch(app, b) } }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.width(DesignTokens.labelWidth.dp)) }
                    }
                }
                if (showResults && items.isEmpty()) {
                    BasicText(stringResource(R.string.no_results), style = headerStyle().copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }

        } // Ende Vorschlagsblock

    }
}

private const val COLUMNS = 4 // IMG_3131: 4 Vorschläge pro Reihe

@Composable
private fun headerStyle() = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.SemiBold,
    fontSize = DesignTokens.searchHeaderSize.value.sp, color = DesignTokens.searchTextColor,
)

@Composable
private fun fieldStyle() = TextStyle(fontFamily = Inter, fontSize = DesignTokens.searchFieldFontSize.value.sp, color = DesignTokens.searchTextColor)

@Composable
private fun SearchAppCell(app: AppEntry, onClick: (Rect) -> Unit) {
    val bounds = remember { arrayOf(Rect.Zero) }
    Column(
        Modifier
            .width(DesignTokens.labelWidth.dp)
            .clickable { onClick(bounds[0]) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        io.github.janhurn9400.liquidy.ui.home.StyledAppIcon(
            app = app,
            modifier = Modifier
                .size(DesignTokens.iconSize.dp)
                .onGloballyPositioned { bounds[0] = it.boundsInWindow() }
                .clip(ContinuousRoundedShape(DesignTokens.iconCorner.dp)),
        )
        Spacer(Modifier.height(DesignTokens.searchLabelGap.dp))
        BasicText(
            app.label,
            style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.labelFontSize.value.sp, color = Color.White, textAlign = TextAlign.Center),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Magnifier(size: Dp) = Canvas(Modifier.size(size)) {
    val c = DesignTokens.searchPlaceholderColor
    val stroke = this.size.width * 0.13f
    val r = this.size.width * 0.34f
    val center = Offset(r + stroke / 2, r + stroke / 2)
    drawCircle(c, r, center, style = Stroke(stroke))
    val d = r * 0.7071f
    drawLine(c, Offset(center.x + d, center.y + d), Offset(this.size.width - stroke / 2, this.size.height - stroke / 2), stroke, StrokeCap.Round)
}

