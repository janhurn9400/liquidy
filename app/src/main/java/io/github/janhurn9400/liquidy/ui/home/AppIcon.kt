package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.GlassSurface
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Reine Darstellung eines Icons (Gesten laufen zentral in HomeScreen).
 * Zelle = T-LABEL-W breit, Icon oben mittig, Label-Grundlinie bei T-LABEL-BASELINE.
 * Der Aufrufer positioniert so, dass die Icon-Oberkante-links auf dem Raster liegt (siehe [cellOffsetX]).
 */
@Composable
fun AppIconView(
    app: AppEntry,
    showLabel: Boolean,
    wallpaper: ImageBitmap,
    pressAlpha: () -> Float,
    jiggle: () -> Float,
    clockSeconds: () -> Float,
    badgeScale: () -> Float,
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    /** Minus-Badge zeigt, was wirklich dahinter liegt (offener Ordner: Panel über dem Homescreen) statt des Wallpapers. */
    badgeOnGlass: Boolean = false,
    labelAlpha: () -> Float = { 1f },
) {
    val iconSize = DesignTokens.iconSize.dp
    IconCell(
        phaseKey = app.key,
        label = if (showLabel) app.label else null,
        wallpaper = wallpaper,
        pressAlpha = pressAlpha,
        jiggle = jiggle,
        clockSeconds = clockSeconds,
        badgeScale = badgeScale,
        showBadge = showBadge,
        modifier = modifier,
        notifications = io.github.janhurn9400.liquidy.data.NotificationBadges.countFor(app),
        badgeOnGlass = badgeOnGlass,
        labelAlpha = labelAlpha,
    ) {
        StyledIcon(app, wallpaper, Modifier.size(iconSize))
    }
}

/** Aktuelles Wallpaper für Glas-Icon-Stile (Klar/Getönt) an Stellen ohne eigenen Wallpaper-Parameter. */
val LocalWallpaper = androidx.compose.runtime.staticCompositionLocalOf<ImageBitmap?> { null }

/** App-Icon im gewählten Stil überall dort, wo kein Wallpaper durchgereicht wird (Ordner, Mediathek, Suche). */
@Composable
fun StyledAppIcon(app: AppEntry, modifier: Modifier) {
    val wp = LocalWallpaper.current
    if (wp == null) Image(bitmap = app.icon, contentDescription = app.label, modifier = modifier)
    else StyledIcon(app, wp, modifier)
}

/**
 * Icon im gewählten iOS-Stil (IconAppearance, recording7):
 *  Default = Original · Dark = farbiges Motiv auf dunklem Verlauf · Clear = einfarbiges Motiv auf Glas ·
 *  Tinted = eingefärbtes Motiv auf getöntem Grund. Hell/Dunkel-Variante je nach Einstellung.
 */
@Composable
fun StyledIcon(
    app: AppEntry,
    wallpaper: ImageBitmap,
    modifier: Modifier,
    style: io.github.janhurn9400.liquidy.design.IconStyle = io.github.janhurn9400.liquidy.design.IconAppearance.effective,
    dark: Boolean = io.github.janhurn9400.liquidy.design.IconAppearance.dark,
) {
    val layers = io.github.janhurn9400.liquidy.data.IconLayerCache.map[app.key]
    if (layers == null || style == io.github.janhurn9400.liquidy.design.IconStyle.Default) {
        Image(bitmap = app.icon, contentDescription = app.label, modifier = modifier)
        return
    }
    val ia = io.github.janhurn9400.liquidy.design.IconAppearance
    when (style) {
        io.github.janhurn9400.liquidy.design.IconStyle.Dark -> Box(modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(DARK_TOP, DARK_BOTTOM)))) {
            Image(layers.glyph, app.label, Modifier.matchParentSize())
        }
        io.github.janhurn9400.liquidy.design.IconStyle.Clear -> io.github.janhurn9400.liquidy.glass.GlassSurface(
            wallpaper = wallpaper,
            shape = androidx.compose.ui.graphics.RectangleShape, // Zelle clippt bereits auf das Squircle
            modifier = modifier,
            tint = if (dark) CLEAR_DARK_TINT else 0f,
            whiteTint = if (dark) 0f else CLEAR_LIGHT_TINT,
        ) {
            Image(layers.mono, app.label, Modifier.matchParentSize(),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (dark) CLEAR_GLYPH_DARK else CLEAR_GLYPH_LIGHT))
        }
        else -> { // Tinted
            val bg = if (dark) ia.tint(TINT_DARK_BG_V, TINT_DARK_BG_S) else ia.tint(TINT_LIGHT_BG_V, TINT_LIGHT_BG_S)
            val fg = if (dark) ia.tint(1f, TINT_DARK_FG_S) else ia.tint(TINT_LIGHT_FG_V)
            // getöntes Glas: Wallpaper scheint leicht durch (recording7 24,5 s)
            io.github.janhurn9400.liquidy.glass.GlassSurface(
                wallpaper = wallpaper,
                shape = androidx.compose.ui.graphics.RectangleShape,
                modifier = modifier,
                tint = 0f, whiteTint = 0f,
            ) {
                Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg.copy(alpha = TINT_GLASS_TOP), bg.copy(alpha = TINT_GLASS_BOTTOM)))))
                Image(layers.mono, app.label, Modifier.matchParentSize(), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(fg))
            }
        }
    }
}

// Farben/Tönungen [GEMESSEN grob] recording7 15,6 / 17,2 / 22,4 s
private val DARK_TOP = Color(0xFF3A3A3C)
private val DARK_BOTTOM = Color(0xFF1C1C1E)
private const val CLEAR_LIGHT_TINT = 0.22f
private const val CLEAR_DARK_TINT = 0.35f
private val CLEAR_GLYPH_LIGHT = Color(0xF2FFFFFF)
private val CLEAR_GLYPH_DARK = Color(0xD9FFFFFF)
private const val TINT_DARK_BG_V = 0.38f
private const val TINT_DARK_BG_S = 1.15f
private const val TINT_DARK_FG_S = 0.45f
private const val TINT_LIGHT_BG_V = 0.96f
private const val TINT_LIGHT_BG_S = 0.22f
private const val TINT_LIGHT_FG_V = 0.62f
private const val TINT_GLASS_TOP = 0.8f    // oben etwas durchsichtiger (Glanz)
private const val TINT_GLASS_BOTTOM = 0.93f

/**
 * Gemeinsame Zelle für App- und Ordner-Icons: Squircle-Clip, Abdunkeln, Label, Badge, Wackeln.
 * [iconContent] füllt das T-ICON-Quadrat.
 */
@Composable
fun IconCell(
    phaseKey: String,
    label: String?,
    wallpaper: ImageBitmap,
    pressAlpha: () -> Float,
    jiggle: () -> Float,
    clockSeconds: () -> Float,
    badgeScale: () -> Float,
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    clipIcon: Boolean = true,
    /** Ungelesene Mitteilungen (rotes Badge oben rechts), 0 = keins. */
    notifications: Int = 0,
    badgeOnGlass: Boolean = false,
    /** Deckkraft der Beschriftung (Ordner blendet sie beim Ordner-Hinweis aus). */
    labelAlpha: () -> Float = { 1f },
    iconContent: @Composable () -> Unit,
) {
    val iconSize = DesignTokens.iconSize.dp
    val shape = ContinuousRoundedShape(DesignTokens.iconCorner.dp)
    val phases = remember(phaseKey) { jigglePhases(phaseKey) }

    Layout(
        modifier = modifier.graphicsLayer { applyJiggle(jiggle(), clockSeconds(), phases) },
        content = {
            Box(
                Modifier
                    .size(iconSize)
                    .then(if (clipIcon) Modifier.clip(shape) else Modifier)
                    .drawWithContent {
                        drawContent()
                        val a = pressAlpha()
                        if (a > 0f) {
                            val outline = shape.createOutline(size, layoutDirection, this)
                            drawOutline(outline, Color.Black.copy(alpha = a))
                        }
                    }
            ) { iconContent() }
            if (label != null) {
                BasicText(
                    modifier = Modifier.graphicsLayer { alpha = labelAlpha() },
                    text = label,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontSize = DesignTokens.labelFontSize.value.sp,
                        letterSpacing = DesignTokens.labelLetterSpacing.value.sp,
                        color = DesignTokens.labelColor,
                        shadow = DesignTokens.labelShadow,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showBadge) MinusBadge(wallpaper, Modifier.graphicsLayer {
                val s = badgeScale()
                scaleX = s; scaleY = s
            }, onGlass = badgeOnGlass)
            if (notifications > 0) NotificationBadge(notifications)
        },
    ) { measurables, _ ->
        val cellW = DesignTokens.labelWidth.dp.roundToPx()
        val iconPx = iconSize.roundToPx()
        val icon = measurables[0].measure(Constraints.fixed(iconPx, iconPx))
        val labelP = if (label != null) measurables[1].measure(Constraints(maxWidth = cellW)) else null
        val count = if (notifications > 0) measurables.last().measure(Constraints()) else null
        val badge = if (showBadge) measurables[measurables.size - (if (count != null) 2 else 1)].measure(Constraints()) else null
        val baseline = DesignTokens.labelBaseline.dp.roundToPx()
        val height = if (labelP != null) baseline + (labelP.height - labelP[FirstBaseline]) else iconPx
        val iconX = (cellW - iconPx) / 2
        layout(cellW, height) {
            icon.place(iconX, 0)
            labelP?.place((cellW - labelP.width) / 2, baseline - labelP[FirstBaseline])
            badge?.place(
                iconX + DesignTokens.badgeOffsetX.dp.roundToPx() - badge.width / 2,
                DesignTokens.badgeOffsetY.dp.roundToPx() - badge.height / 2,
                zIndex = 1f,
            )
            // rechte Kante ragt über das Icon hinaus, Mitte knapp unter der Oberkante; breitere Zahlen wachsen nach links
            count?.place(
                iconX + iconPx + NOTIF_OVERHANG.dp.roundToPx() - count.width,
                NOTIF_CENTER_Y.dp.roundToPx() - count.height / 2,
                zIndex = 1f,
            )
        }
    }
}

/**
 * Rotes Mitteilungs-Badge wie iOS [GEMESSEN] Bildschirmaufnahme 10-08 02:34, Bild 391 (3×): 25 pt hoch, eine Ziffer = Kreis,
 * mehr Ziffern = Kapsel; Zahl weiß mit Tausendertrennung („17.882“).
 */
@Composable
internal fun NotificationBadge(count: Int, modifier: Modifier = Modifier) {
    val text = remember(count) { java.text.NumberFormat.getIntegerInstance().format(count) }
    Box(
        modifier.height(NOTIF_H.dp).widthIn(min = NOTIF_H.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(NOTIF_RED).padding(horizontal = NOTIF_PAD.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(fontFamily = Inter, fontSize = NOTIF_FONT.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            letterSpacing = (-0.2).sp, color = Color.White))
    }
}

// Milchglas für Minus-Badge und Bearbeiten-Knöpfe [GESCHÄTZT] nach IMG_3124
internal const val FROST_BLUR = 18f
internal const val FROST_WHITE = 0.18f
internal const val FROST_WHITE_DARK = 0.28f

private const val NOTIF_H = 25f          // pt
private const val NOTIF_OVERHANG = 11.3f // rechte Kante über das Icon hinaus (pt)
private const val NOTIF_CENTER_Y = 2.3f  // Mitte unter der Icon-Oberkante (pt)
private const val NOTIF_PAD = 7f
private const val NOTIF_FONT = 16.5f
private val NOTIF_RED = Color(0xFFFF3B30)

/** Zellen-x (px) aus Icon-x (px). */
fun cellOffsetX(iconX: Float, density: Float): Float =
    iconX - (DesignTokens.labelWidth.value - DesignTokens.iconSize.value) * density / 2

/** Glas-Kreis mit schwarzem Minus (T-BADGE). Ohne Blur — der Wallpaper unter dem Badge ist glatt. */
@Composable
internal fun MinusBadge(wallpaper: ImageBitmap, modifier: Modifier, onGlass: Boolean = false) {
    val frostWhite = if (DesignTokens.wallpaperIsDark) FROST_WHITE_DARK else FROST_WHITE
    // Offener Ordner: dasselbe Milchglas, aber aus dem, was wirklich dahinter liegt (Homescreen unter dem Panel, gleich
    // abgedunkelt wie dort) — der Wallpaper-Ausschnitt wirkte vor dem helleren Panel-Hintergrund zu dunkel (Nutzer 10-14)
    if (onGlass && io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop.current != null) {
        io.github.janhurn9400.liquidy.glass.LiquidGlass(
            shape = io.github.janhurn9400.liquidy.glass.glassCapsule(),
            modifier = modifier.size(DesignTokens.badgeSize.dp),
            blur = FROST_BLUR.dp,
            whiteTint = frostWhite,
            blackTint = DesignTokens.folderBgDim.value,
        ) { MinusGlyph() }
        return
    }
    // Milchglas wie IMG_3124 (stark verwischt, hell, keine Brechung) — auch über dunklen Widgets bleibt es hell
    GlassSurface(
        wallpaper = wallpaper,
        shape = CircleShape,
        blur = FROST_BLUR.dp,
        tint = 0f,
        whiteTint = frostWhite,
        modifier = modifier.size(DesignTokens.badgeSize.dp),
    ) { MinusGlyph() }
}

/** Schwarzer Minus-Strich des Badges (T-BADGE). */
@Composable
private fun MinusGlyph() = Canvas(Modifier.fillMaxSize()) {
    val w = DesignTokens.badgeMinusWidth.dp.toPx()
    val h = DesignTokens.badgeMinusHeight.dp.toPx()
    drawRoundRect(
        color = Color.Black,
        topLeft = Offset((size.width - w) / 2, (size.height - h) / 2),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
    )
}

// ── Wackeln (M-JIGGLE) ───────────────────────────────────────────────────────
class JigglePhases(val rot: Float, val x: Float, val y: Float)

internal fun jigglePhases(key: String): JigglePhases {
    val r = Random(key.hashCode())
    val tau = (2 * PI).toFloat()
    return JigglePhases(r.nextFloat() * tau, r.nextFloat() * tau, r.nextFloat() * tau)
}

/** Wackel-Werte (Drehung °, Versatz x/y px) — eine Quelle für das Icon UND fürs Gegensteuern im Glas. */
internal fun jiggleValues(amount: Float, t: Float, p: JigglePhases, density: Float): Triple<Float, Float, Float> {
    if (amount <= 0f) return Triple(0f, 0f, 0f)
    val tau = (2 * PI).toFloat()
    val tr = Motion.jiggleTranslation.value * density
    return Triple(
        amount * Motion.jiggleRotation.value * sin(tau * t / Motion.jiggleRotationPeriod.value + p.rot),
        amount * tr * sin(tau * t / Motion.jigglePeriodX.value + p.x),
        amount * tr * sin(tau * t / Motion.jigglePeriodY.value + p.y),
    )
}

internal fun GraphicsLayerScope.applyJiggle(amount: Float, t: Float, p: JigglePhases) {
    if (amount <= 0f) {
        rotationZ = 0f; translationX = 0f; translationY = 0f
        return
    }
    // Drehpunkt = Icon-Mitte (gemessen am Icon, nicht an der Zelle inkl. Label)
    val iconPx = DesignTokens.iconSize.value * density
    if (size.height > 0f) transformOrigin = TransformOrigin(0.5f, (iconPx / 2f) / size.height)
    val (r, x, y) = jiggleValues(amount, t, p, density)
    rotationZ = r; translationX = x; translationY = y
}
