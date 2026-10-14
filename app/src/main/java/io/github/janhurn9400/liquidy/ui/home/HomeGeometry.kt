package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.janhurn9400.liquidy.design.DesignTokens
import kotlin.math.roundToInt

/**
 * Alle Pixel-Positionen des Homescreens aus DesignTokens (pt = dp). Einzige Quelle für Zeichnen
 * UND Treffer-Erkennung, damit beides nie auseinanderläuft. Liest Tokens bei jedem Zugriff → live tunebar.
 */
/** [navBarPx] = Höhe der Android-Gestenleiste unten (px); das Dock bleibt mit Abstand darüber. */
class HomeGeometry(
    private val density: Float, val width: Float, val height: Float, private val navBarPx: Float = 0f,
    /** iPhone-Duo-Stil (Foldable): Seiten links, rechts eine senkrechte Spalte mit Dock, Lupe und Bearbeiten-Knöpfen. */
    val duo: Boolean = false,
) {

    private fun px(dp: Float) = dp * density

    /** Aufgeklappt = Querformat → zwei Seiten nebeneinander (Duo). */
    val unfolded: Boolean get() = duo && width > height

    /** Breite einer Homescreen-Seite (Blätter-Schritt): aufgeklappt 448 pt (zwei Seiten sichtbar), sonst Bildschirmbreite. */
    // ── Aufgeklappt: alles aus der Displaygröße abgeleitet (gilt für jedes Foldable) ─────────────────────────────
    // [GEMESSEN Duo, Aufnahme 19:18, echte Displaykanten, 0,729 pt/px]: die beiden Raster liegen symmetrisch um die
    // Mitte der Fläche links vom Dock (434 pt = 48 % der Dock-Spaltenmitte; dort stehen auch die Seitenpunkte), ihre
    // Mitten 397 pt auseinander (41,8 % der Breite). Die Wege aus dem Bild werden aus Breite + Rasterausdehnung
    // berechnet, damit eine Seite auf JEDEM Display ganz verschwindet, bevor sie wegfällt.
    private val wPt get() = width / density
    /** Mitte zwischen den beiden Rastern (pt). */
    private val openCenter get() = (wPt - DUO_COLUMN_FROM_RIGHT) * DUO_CONTENT_CENTER
    /** Abstand der Rastermitten = Verschiebung je Seite (pt); mindestens so groß, dass die Raster sich nicht berühren. */
    private val openStride get() = maxOf(wPt * DUO_STRIDE_SHARE, 3 * DUO_COL_PITCH + DesignTokens.iconSize.value + DUO_MIN_GRID_GAP)
    /**
     * Spaltenabstand aufgeklappt: 88 pt wie am Duo; auf schmalen Displays so weit verringert, dass beide Raster samt
     * Beschriftung zwischen linkem Rand und Dock passen.
     */
    private val openColPitch: Float get() {
        val dockLeft = wPt - DUO_COLUMN_FROM_RIGHT - DUO_DOCK_W / 2
        val byRight = (dockLeft - DUO_OUT_MARGIN * 2 - DUO_LABEL_HALF - (openCenter + openStride / 2)) / 1.5f
        val byLeft = (openCenter - openStride / 2 - DUO_LABEL_HALF - DUO_OUT_MARGIN) / 1.5f
        return minOf(DUO_COL_PITCH, byRight, byLeft).coerceAtLeast(DesignTokens.iconSize.value + 8f)
    }
    /** Halbe Rasterbreite inkl. Beschriftung (pt) — so weit muss eine Seite über den Rand hinaus. */
    private val gridExtent get() = 1.5f * colPitch + DUO_LABEL_HALF
    private val leftGridCenter get() = openCenter - openStride / 2
    private val rightGridCenter get() = openCenter + openStride / 2
    /** Nach links hinaus: rechte Kante des linken Rasters muss hinter x = 0 (Duo ≈ 416 pt). */
    private val outLeft get() = leftGridCenter + gridExtent + DUO_OUT_MARGIN
    /** Nach rechts hinaus: linke Kante des rechten Rasters weit hinter den Rand (Duo ≈ 666 pt, saust hinters Dock). */
    private val outRight get() = (wPt - (rightGridCenter - gridExtent)) * DUO_OUT_RIGHT_FACTOR

    val pageWidth: Float get() = if (unfolded) px(openStride) else width

    /** Wie viele Seiten gleichzeitig sichtbar sind. */
    val pagesVisible: Int get() = if (unfolded) 2 else 1
    /**
     * Waagerechte Verschiebung einer Seite, die [rel] Positionen rechts der aktuellen liegt (px). Aufgeklappt stückweise:
     * links ↔ rechts = Rasterabstand, nach links hinaus [outLeft], nach rechts hinaus [outRight] (schneller).
     * Zugeklappt: Bildschirmbreite je Seite.
     */
    fun pageShift(rel: Float): Float {
        if (!unfolded) return rel * width
        return when {
            rel < 0f -> rel * px(outLeft)
            rel <= 1f -> rel * px(openStride)
            else -> px(openStride) + (rel - 1f) * px(outRight)
        }
    }
    /** Verschiebung je Seite für Heute-Seite und Mediathek (aufgeklappt 47 % der Breite, Duo 448 pt). */
    val sideStride: Float get() = if (unfolded) width * DUO_SIDE_SHARE else width
    fun libraryOffset(pagesFromPos: Float): Float = pagesFromPos * sideStride
    /** Ordner aufgeklappt: mittig über dem Raster der Hälfte, in der das Ordner-Icon liegt (Dock → rechts), px. */
    // Hälfte nach Icon-MITTE und der Mitte zwischen beiden Rastern — nicht width/2 (enthält die Dock-Spalte; die linke Spalte
    // der rechten Seite lag sonst noch „links“)
    fun duoFolderCenterX(iconX: Float): Float =
        px(if (iconX + icon / 2 < px((leftGridCenter + rightGridCenter) / 2)) leftGridCenter else rightGridCenter)
    val duoColPitchPt: Float get() = colPitch
    /** Linke Kante der Dock-Glasleiste (pt). */
    val duoDockLeftPt: Float get() = wPt - DUO_COLUMN_FROM_RIGHT - DUO_DOCK_W / 2
    val duoRowPitchPt: Float get() = rowPitch
    /** Seitenpunkte aufgeklappt: x-Mitte normal bzw. bei offener Heute-Seite (unter dem rechten Raster), px. */
    val duoDotsHomeX: Float get() = px(openCenter)
    val duoDotsTodayX: Float get() = px(rightGridCenter)

    private val colPitch get() = if (unfolded) openColPitch else if (duo) DUO_COL_PITCH else DesignTokens.gridColPitch.value
    private val rowPitch get() = if (duo) DUO_ROW_PITCH else DesignTokens.gridRowPitch.value
    // Höher als das Duo (aufgeklappt bzw. zugeklappt) (z. B. Z Fold nach dem Angleichen der Breite, design/UiScale): Raster senkrecht
    // mittig in den Mehrplatz statt oben kleben
    private val row0 get() = DUO_ROW0 + ((height / density - if (unfolded) DUO_HEIGHT_PT else DUO_FOLDED_HEIGHT_PT) / 2f).coerceAtLeast(0f)
    private val small get() = colPitch + DesignTokens.iconSize.value + 2 * DUO_WIDGET_INSET
    /** Mitte der ersten Spalte / Reihe (pt). */
    private val firstColCenter get() = if (unfolded) leftGridCenter - 1.5f * colPitch else DUO_COL0_FOLDED
    private val duoColumnX get() = width - px(DUO_COLUMN_FROM_RIGHT) // Mitte der rechten Spalte

    val icon: Float get() = px(DesignTokens.iconSize.value)
    val perPage: Int get() = DesignTokens.GRID_COLUMNS * DesignTokens.GRID_ROWS

    /** Icon-Oberkante-links eines Slots, seitenlokal (Seite 0 bei Pager-Position 0). */
    fun slotPosition(slot: Int): Offset {
        val col = slot % DesignTokens.GRID_COLUMNS
        val row = slot / DesignTokens.GRID_COLUMNS
        if (duo) return Offset(px(firstColCenter + colPitch * col) - icon / 2, px(row0 + rowPitch * row) - icon / 2)
        return Offset(
            px(DesignTokens.gridSide.value + DesignTokens.gridColPitch.value * col),
            px(DesignTokens.gridTop.value + DesignTokens.gridRowPitch.value * row),
        )
    }

    /** Nächster Slot zur Icon-Mitte [center] (seitenlokal); begrenzt auf [count] (= hinter dem letzten Icon). */
    fun slotAt(center: Offset, count: Int): Int {
        if (duo) {
            val c = ((center.x - px(firstColCenter)) / px(colPitch)).roundToInt().coerceIn(0, DesignTokens.GRID_COLUMNS - 1)
            val r = ((center.y - px(row0)) / px(rowPitch)).roundToInt().coerceIn(0, DesignTokens.GRID_ROWS - 1)
            return (r * DesignTokens.GRID_COLUMNS + c).coerceAtMost(count)
        }
        val col = ((center.x - px(DesignTokens.gridSide.value) - icon / 2) / px(DesignTokens.gridColPitch.value))
            .roundToInt().coerceIn(0, DesignTokens.GRID_COLUMNS - 1)
        val row = ((center.y - px(DesignTokens.gridTop.value) - icon / 2) / px(DesignTokens.gridRowPitch.value))
            .roundToInt().coerceIn(0, DesignTokens.GRID_ROWS - 1)
        return (row * DesignTokens.GRID_COLUMNS + col).coerceAtMost(count)
    }

    /** Trefferfläche einer Zelle: Icon plus Label-Bereich darunter. */
    fun cellHitRect(iconTopLeft: Offset): Rect {
        val padX = (px(colPitch) - icon) / 2
        val bottom = iconTopLeft.y + px(rowPitch) - padX
        return Rect(iconTopLeft.x - padX, iconTopLeft.y, iconTopLeft.x + icon + padX, bottom)
    }

    // ── Widgets ───────────────────────────────────────────────────────────────
    /** Kantenlänge eines Widgets mit [cols]×[rows] Zellen (klein 164, breit 349,33; Höhe 4 Reihen = 2·164 + Reihenabstand). */
    fun widgetSize(cols: Int, rows: Int): Size {
        if (duo) { // Duo: klein = Abstand + Icon + 2·4,5 pt (gemessen 157 pt), Höhe 4 Reihen = klein + 2 Reihenabstände
            val w = if (cols >= 4) 2 * colPitch + small else small
            val h = if (rows >= 4) small + 2 * rowPitch else small
            return Size(px(w), px(h))
        }
        val w = if (cols >= 4) DesignTokens.widgetWide.value else DesignTokens.widgetSmall.value
        val h = if (rows >= 4) 2 * DesignTokens.widgetSmall.value + (2 * DesignTokens.gridRowPitch.value - DesignTokens.widgetSmall.value)
            else DesignTokens.widgetSmall.value
        return Size(px(w), px(h))
    }

    /** Widget-Oberkante-links relativ zur Icon-Oberkante-links seiner ersten Zelle. */
    val widgetOffset: Offset get() = if (duo) Offset(-(px(small - colPitch) - icon) / 2, -(px(small - rowPitch) - icon) / 2 + px(DUO_WIDGET_DY))
        else Offset(-px(DesignTokens.widgetOverhang.value), px(DesignTokens.widgetTopOffset.value))

    fun widgetRect(cellTopLeft: Offset, cols: Int, rows: Int) = Rect(cellTopLeft + widgetOffset, widgetSize(cols, rows))

    // ── Dock ──────────────────────────────────────────────────────────────────
    /** Anzahl Dock-Icons (für das senkrechte Duo-Dock, dessen Höhe davon abhängt) — von HomeScreen gesetzt. */
    var dockCount: Int = 4

    val dockRect: Rect get() = dockRectFor(dockCount.toFloat())

    /** Dock-Glas für [slots] Plätze — Duo: Kommazahl, damit die Leiste beim Hinzufügen/Entfernen weich wächst/schrumpft. */
    fun dockRectFor(slots: Float): Rect {
        if (duo) { // senkrechte Glasleiste in der rechten Spalte, Mitte bei 56,7 % der Höhe
            // 4 Plätze → 263 pt, 1 Platz → 71 pt [GEMESSEN]: h = pitch·(n−1) + 71
            val n = slots.coerceAtLeast(1f)
            val h = px(DUO_DOCK_PITCH * (n - 1) + DUO_DOCK_SINGLE)
            val w = px(DUO_DOCK_W); val cy = height * DUO_DOCK_CY
            return Rect(duoColumnX - w / 2, cy - h / 2, duoColumnX + w / 2, cy + h / 2)
        }
        val side = px(DesignTokens.dockSideInset.value)
        val h = px(DesignTokens.dockHeight.value)
        // iPhone: 17 pt Abstand (dort ohne Home-Leiste auf dem Homescreen). Android zeigt die Gestenleiste immer →
        // Dock mindestens DOCK_ABOVE_NAV pt über ihr, sonst klebt die Leiste am Dock
        val bottom = height - maxOf(px(DesignTokens.dockBottomInset.value), navBarPx + px(DOCK_ABOVE_NAV))
        return Rect(side, bottom - h, width - side, bottom)
    }

    /** Duo: wie viele Dock-Plätze passen zwischen Pinsel-Knopf (oben) und Suchknopf (unten), mit Abstand [DUO_DOCK_CLEAR]. */
    val duoDockCapacity: Int
        get() {
            val cy = height * DUO_DOCK_CY
            val half = minOf(cy - editButtonRect(left = true).bottom, pillRect.top - cy) - px(DUO_DOCK_CLEAR)
            return (((2 * half - px(DUO_DOCK_SINGLE)) / px(DUO_DOCK_PITCH)).toInt() + 1).coerceIn(DesignTokens.DOCK_SLOTS, DUO_DOCK_SLOTS_LIMIT)
        }

    /**
     * FESTE REGEL: Größe eines Icons/Ordners im Dock relativ zum Raster-Icon — Duo ≈ 48 statt 60 pt [GEMESSEN 21:29, 44 s],
     * sonst gleich groß. Alles, was ein Dock-Item zeigt oder von ihm ausgeht (Zeichnen, Ordner auf/zu, Kontextmenü,
     * App-Start/-Rückflug, Ziehen), nimmt diesen Wert.
     */
    val dockIconScale: Float get() = if (duo) DUO_DOCK_ICON / DesignTokens.iconSize.value else 1f

    /** Sichtbare Icon-Fläche eines Items mit Zellen-Oberkante-links [topLeft]; im Dock um die Icon-Mitte verkleinert. */
    fun iconRect(topLeft: Offset, inDock: Boolean): Rect {
        val s = if (inDock) dockIconScale else 1f
        val c = topLeft + Offset(icon / 2, icon / 2)
        return Rect(c - Offset(icon * s / 2, icon * s / 2), Size(icon * s, icon * s))
    }

    /** Dock-Icons sind als Gruppe zentriert (iOS: 3 Icons → mittig). */
    fun dockPosition(index: Int, count: Int): Offset {
        if (duo) {
            val cy = height * DUO_DOCK_CY + px(DUO_DOCK_PITCH) * (index - (count - 1) / 2f)
            return Offset(duoColumnX - icon / 2, cy - icon / 2)
        }
        val pitch = px(DesignTokens.dockIconPitch.value)
        val cx = width / 2 + pitch * (index - (count - 1) / 2f)
        val d = dockRect
        return Offset(cx - icon / 2, d.top + (d.height - icon) / 2)
    }

    /** Liegt [p] im Dock-Bereich (Ablegen)? iPhone: unterhalb der Dock-Oberkante; Duo: rechte Spalte. */
    fun inDockZone(p: Offset): Boolean = if (duo) dockRect.inflate(px(DUO_DOCK_HIT)).contains(p) else p.y >= dockRect.top

    /** Einfügeposition im Dock zur Icon-Mitte [c]. */
    fun dockIndexAt(c: Offset, count: Int): Int {
        if (!duo) return dockIndexAt(c.x, count)
        val pitch = px(DUO_DOCK_PITCH)
        val first = height * DUO_DOCK_CY - pitch * count / 2f
        return ((c.y - first) / pitch).toInt().coerceIn(0, count)
    }

    fun dockIndexAt(x: Float, count: Int): Int {
        val pitch = px(DesignTokens.dockIconPitch.value)
        val first = width / 2 - pitch * count / 2f // count+1 Slots beim Einfügen
        return ((x - first) / pitch).toInt().coerceIn(0, count)
    }

    // ── Pill ──────────────────────────────────────────────────────────────────
    val pillTop: Float get() = dockRect.top - px(DesignTokens.pillGapToDock.value + DesignTokens.pillHeight.value)
    val pillRect: Rect
        get() {
            if (duo) { // runder Suchknopf unten in der rechten Spalte
                val d = px(DUO_SEARCH_D); val cy = height - px(DUO_SEARCH_FROM_BOTTOM)
                return Rect(Offset(duoColumnX - d / 2, cy - d / 2), Size(d, d))
            }
            val w = px(DesignTokens.pillWidth.value)
            return Rect(Offset((width - w) / 2, pillTop), Size(w, px(DesignTokens.pillHeight.value)))
        }

    // ── Bearbeitungsmodus ──────────────────────────────────────────────────────
    fun badgeCenter(iconTopLeft: Offset): Offset =
        iconTopLeft + Offset(px(DesignTokens.badgeOffsetX.value), px(DesignTokens.badgeOffsetY.value))

    val badgeHitRadius: Float get() = px(DesignTokens.badgeSize.value / 2 + DesignTokens.badgeHitSlop.value)

    /** Duo-Suche geöffnet: Feld/Vorschläge als mittige Spalte (aufgeklappt 428 pt, Mitte = Bildschirmmitte) [GEMESSEN] 42 s. */
    val duoSearchWidth: Float get() = if (unfolded) px(DUO_SEARCH_FIELD_W) else (duoColumnX - px(DUO_DOCK_W) / 2 - px(16f)) - px(16f)
    /** Mitte y des Duo-Suchfelds ohne Tastatur (aufgeklappt 636 pt von 669 = unten mittig). */
    val duoSearchRestY: Float get() = height - px(DUO_SEARCH_FIELD_FROM_BOTTOM)
    val duoSearchCenterX: Float get() = if (unfolded) width / 2 else px(16f) + duoSearchWidth / 2

    /** Duo: Seitenpunkte unter dem Raster (zugeklappt mittig unter der Seite, aufgeklappt unter der rechten Seite). */
    val duoDotsCenter: Offset get() = Offset(if (unfolded) px(rightGridCenter) else px(DUO_COL0_FOLDED + 1.5f * DUO_COL_PITCH), height - maxOf(px(DUO_DOTS_FROM_BOTTOM), navBarPx + px(DUO_DOTS_ABOVE_NAV)))

    fun editButtonRect(left: Boolean): Rect {
        if (duo) { // senkrecht in der rechten Spalte: ✓ oben, Pinsel darunter
            val d = px(DUO_EDIT_D); val cy = px(if (left) DUO_EDIT_BRUSH_Y else DUO_EDIT_DONE_Y)
            return Rect(Offset(duoColumnX - d / 2, cy - d / 2), Size(d, d))
        }
        val w = px(DesignTokens.editButtonWidth.value)
        val h = px(DesignTokens.editButtonHeight.value)
        val top = px(DesignTokens.editButtonTop.value)
        val side = px(DesignTokens.editButtonSide.value)
        val x = if (left) side else width - side - w
        return Rect(Offset(x, top), Size(w, h))
    }

    val edgeZone: Float get() = px(DesignTokens.edgeZone.value)
    /** Rechte Kante der Seitenfläche (px): Duo = links neben der Dock-Spalte (dort wechselt das Ziehen die Seite), sonst Bildschirmrand. */
    val pagesRightEdge: Float get() = if (duo) duoColumnX - px(DUO_DOCK_W) else width
}

private const val DOCK_ABOVE_NAV = 8f // pt Abstand zwischen Dock und Gestenleisten-Bereich [GESCHÄTZT]

// iPhone-Duo-Maße [GEMESSEN] Device-Hub-Aufnahmen 10-08 21:28/21:29 (Simulator iPhone Duo, iOS 27.1; 0,634 pt/px)
private const val DUO_CONTENT_CENTER = 0.4803f // Rastermitte ÷ Dock-Spaltenmitte (434 / 904 pt)
private const val DUO_STRIDE_SHARE = 0.4176f   // Rasterabstand ÷ Breite (397 / 951 pt)
private const val DUO_SIDE_SHARE = 0.4711f     // Heute/Mediathek je Seite ÷ Breite (448 / 951 pt)
private const val DUO_MIN_GRID_GAP = 40f
private const val DUO_LABEL_HALF = 44f         // halbe Beschriftungsbreite über die Spaltenmitte hinaus
private const val DUO_OUT_MARGIN = 4f
private const val DUO_OUT_RIGHT_FACTOR = 1.345f // Duo: 666 pt statt der nötigen 495 pt
private const val DUO_WIDGET_INSET = 4.5f
private const val DUO_COL_PITCH = 88f
private const val DUO_ROW_PITCH = 98.7f
private const val DUO_HEIGHT_PT = 667f // Duo-Innendisplay aufgeklappt
private const val DUO_FOLDED_HEIGHT_PT = 678f // Duo-Außendisplay
private const val DUO_COL0_FOLDED = 57.2f      // Mitte Spalte 1 zugeklappt
private const val DUO_ROW0 = 83.6f             // Mitte Reihe 1
private const val DUO_WIDGET_DY = 0f
private const val DUO_COLUMN_FROM_RIGHT = 47f  // Mitte der rechten Spalte (Dock, Lupe, Knöpfe)
private const val DUO_DOCK_W = 73f
private const val DUO_DOCK_HIT = 14f          // pt Treffer-Rand um das Dock-Glas (Ablegen ins Dock) [GESCHÄTZT]
private const val DUO_DOCK_ICON = 48f        // pt Icon-Größe im Duo-Dock (Raster 60 pt)
private const val DUO_DOCK_SINGLE = 71f       // Höhe der Glasfläche mit einem Icon
private const val DUO_DOCK_PITCH = 64f
private const val DUO_DOCK_CY = 0.567f         // Dock-Mitte als Anteil der Höhe
private const val DUO_DOCK_CLEAR = 16f        // pt Mindestabstand des erweiterten Docks zu Pinsel/Suchknopf [GESCHÄTZT]
/** Obergrenze „Mehr Apps im Dock“ (eigene Erweiterung, am Duo gibt es höchstens 4). */
const val DUO_DOCK_SLOTS_LIMIT = 8
private const val DUO_SEARCH_D = 48f
private const val DUO_SEARCH_FIELD_W = 428f
private const val DUO_SEARCH_FIELD_FROM_BOTTOM = 33f
private const val DUO_SEARCH_FROM_BOTTOM = 49f
private const val DUO_DOTS_FROM_BOTTOM = 26f
private const val DUO_DOTS_ABOVE_NAV = 8f   // Android-Gestenleiste (gibt es am Duo nicht) frei lassen
private const val DUO_EDIT_D = 48f
private const val DUO_EDIT_DONE_Y = 51.7f   // [GEMESSEN] 21:28, 53,3 s (aufgeklappt)
private const val DUO_EDIT_BRUSH_Y = 115.1f
