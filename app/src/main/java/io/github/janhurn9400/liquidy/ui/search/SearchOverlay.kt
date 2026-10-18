package io.github.janhurn9400.liquidy.ui.search

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
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
 * Search (swipe down), layout after IMG_3131: header "Siri Suggestions" + "Show More",
 * dark glass card with 4 suggestions or results, search field capsule directly above the keyboard.
 * The keyboard animation belongs to the system (RESEARCH M-SEARCH-KB); the field hangs on WindowInsets.ime.
 */
@Composable
fun SearchOverlay(model: HomeModel, onLaunch: (AppEntry, Rect) -> Unit,
    /** Duo unfolded: suggestions as a centred column of this width (dp), otherwise full width. */
    columnWidth: androidx.compose.ui.unit.Dp? = null,
    /** Duo folded: column aligned left (the dock column to the right of it stays free). */
    columnStart: Boolean = false) {
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
        // Suggestions block: fades in with p and follows the finger (M-SEARCH-BLOCK), springs back after release
        val blockLayer = Modifier.graphicsLayer {
            alpha = model.searchBlockAlpha()
            translationY = model.searchBlockOffset.value * density.density
            // fades in blurred and sharpens with the pull distance (measured)
            val r = model.searchBlockBlurFraction() * DesignTokens.searchBlockBlur.value * density.density
            renderEffect = if (r > 0.5f) androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal) else null
        }
        val colMod = if (columnWidth != null) Modifier.align(if (columnStart) Alignment.TopStart else Alignment.TopCenter).width(columnWidth) else Modifier
        val showResults = model.query.isNotBlank()
        if (showResults) {
            // Global search: apps, calculator, contacts, web suggestions as scrollable sections above the search field
            UniversalResults(model, colMod, columnWidth == null, ::launch)
            return@Box
        }
        val items = model.suggestions(COLUMNS)

        // Suggestions block as ONE layer; its height covers header + card, so blur/offset don't clip anything
        Box(
            colMod
                .then(if (columnWidth == null) Modifier.fillMaxWidth() else Modifier)
                .height(DesignTokens.searchCardTop.dp + DesignTokens.searchCardHeight.dp * 2 + DesignTokens.searchCardSide.dp)
                .then(blockLayer)
        ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = DesignTokens.searchHeaderBaseline.dp - with(density) { DesignTokens.searchHeaderSize.value.sp.toDp() })
                .padding(horizontal = DesignTokens.searchHeaderX.dp),
        ) {
            BasicText(
                stringResource(if (showResults) R.string.search_apps else R.string.app_suggestions),
                style = headerStyle(),
                modifier = Modifier.weight(1f),
            )
            if (!showResults) BasicText(stringResource(R.string.show_more), style = headerStyle())
        }

        // card
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
            // iOS (IMG_3131): suggestion card without a light edge — only the light area
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

        } // end suggestions block

    }
}

private const val COLUMNS = 4 // IMG_3131: 4 suggestions per row

/** Results view while typing [iPhone 10-11 17:10]: apps, calculator, contacts, settings, photos, files, suggestions (web). */
@Composable
private fun UniversalResults(model: HomeModel, colMod: Modifier, fullWidth: Boolean, launch: (AppEntry, Rect) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val q = model.query
    val apps = model.searchResults(COLUMNS)
    val calc = remember(q) { io.github.janhurn9400.liquidy.data.UniversalSearch.calculate(q) }
    var contactsTick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val contactPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { contactsTick++ }
    val contacts by androidx.compose.runtime.produceState(emptyList<io.github.janhurn9400.liquidy.data.UniversalSearch.Contact>(), q, contactsTick) {
        kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.janhurn9400.liquidy.data.UniversalSearch.contacts(context, q) }
    }
    val web by androidx.compose.runtime.produceState(listOf(q.trim()), q) {
        value = listOf(q.trim())
        kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.janhurn9400.liquidy.data.UniversalSearch.webSuggestions(q) }
    }
    val photos by androidx.compose.runtime.produceState(emptyList<io.github.janhurn9400.liquidy.data.DeviceSearch.Photo>(), q) {
        kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.janhurn9400.liquidy.data.DeviceSearch.photos(context, q) }
    }
    val files by androidx.compose.runtime.produceState(emptyList<io.github.janhurn9400.liquidy.data.DeviceSearch.FileHit>(), q) {
        kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.janhurn9400.liquidy.data.DeviceSearch.files(context, q) }
    }
    val settingHits = remember(q) { io.github.janhurn9400.liquidy.data.DeviceSearch.settings(context, q) }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    val nav = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    val bottom = with(LocalDensity.current) { maxOf(ime, nav).toDp() } + RESULTS_ABOVE_FIELD.dp
    val noRipple = remember { MutableInteractionSource() }
    // Swipe up closes search also with text, like without (the arena handles that case): the results list keeps every
    // vertical drag, so whatever upward drag/fling it can't use itself (short list or already at its end) closes search
    val closeAfterPx = with(LocalDensity.current) { SWIPE_CLOSE_DP.dp.toPx() }
    val closeConn = remember(model) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var leftover = 0f
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
                                      source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    leftover = if (available.y < 0f) leftover - available.y else 0f
                    if (leftover > closeAfterPx && model.searchOpen) { leftover = 0f; model.closeSearch() }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity { leftover = 0f; return androidx.compose.ui.unit.Velocity.Zero }
        }
    }
    Column(
        colMod.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .fillMaxSize()
            .padding(top = RESULTS_TOP.dp, bottom = bottom)
            .nestedScroll(closeConn)
            .androidx_verticalScroll()
            .clickable(interactionSource = noRipple, indication = null) { },
    ) {
        if (apps.isNotEmpty()) {
            SectionHeader(stringResource(R.string.search_apps))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                apps.forEach { app -> SearchAppCell(app) { b -> launch(app, b) } }
                repeat(COLUMNS - apps.size) { Spacer(Modifier.width(DesignTokens.labelWidth.dp)) }
            }
        }
        if (calc != null) {
            SectionHeader(stringResource(R.string.search_calculator))
            CalculatorRow(q, calc)
        }
        if (q.trim().length >= 2) {
            val hasPerm = io.github.janhurn9400.liquidy.data.UniversalSearch.hasContacts(context)
            if (contacts.isNotEmpty() || !hasPerm) SectionHeader(stringResource(R.string.search_contacts))
            if (!hasPerm) HintRow(stringResource(R.string.search_allow_contacts)) { contactPerm.launch(android.Manifest.permission.READ_CONTACTS) }
            contacts.forEachIndexed { i, c ->
                ContactRow(c, i == contacts.lastIndex,
                    onOpen = { io.github.janhurn9400.liquidy.data.UniversalSearch.openContact(context, c); model.closeSearch(animated = false) },
                    onCall = c.number?.let { n -> { io.github.janhurn9400.liquidy.data.UniversalSearch.call(context, n); model.closeSearch(animated = false) } })
            }
        }
        if (settingHits.isNotEmpty()) {
            SectionHeader(stringResource(R.string.search_settings))
            settingHits.forEachIndexed { i, h ->
                SettingRow(h.label, i == settingHits.lastIndex) { io.github.janhurn9400.liquidy.data.DeviceSearch.openSetting(context, h); model.closeSearch(animated = false) }
            }
        }
        if (photos.isNotEmpty()) {
            SectionHeader(stringResource(R.string.search_photos))
            PhotoStrip(photos) { p -> io.github.janhurn9400.liquidy.data.DeviceSearch.openPhoto(context, p); model.closeSearch(animated = false) }
        }
        if (files.isNotEmpty()) {
            SectionHeader(stringResource(R.string.search_files))
            files.forEachIndexed { i, f ->
                FileRow(f, i == files.lastIndex) { io.github.janhurn9400.liquidy.data.DeviceSearch.openFile(context, f); model.closeSearch(animated = false) }
            }
        }
        SectionHeader(stringResource(R.string.search_suggestions))
        // for a calculation only the "search for" row — Google's completions ("12*75") make no sense there
        (if (calc != null) web.take(1) else web).let { w -> w.forEachIndexed { i, sgg ->
            WebRow(q, sgg, i == w.lastIndex) {
                io.github.janhurn9400.liquidy.data.UniversalSearch.searchWeb(context, sgg); model.closeSearch(animated = false)
            }
        } }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Modifier.androidx_verticalScroll(): Modifier = this.verticalScroll(androidx.compose.foundation.rememberScrollState())

private const val SEARCH_DEBOUNCE_MS = 160L
private const val RESULTS_TOP = 40f // below the status bar
private const val RESULTS_ABOVE_FIELD = 72f // space for the search field above the keyboard

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

/** Upward drag the results list can't use itself, after which search closes (like swiping up without text). */
private const val SWIPE_CLOSE_DP = 48f
