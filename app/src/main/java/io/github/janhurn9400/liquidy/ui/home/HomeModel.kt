package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.data.AppRepository
import io.github.janhurn9400.liquidy.data.LayoutStore
import io.github.janhurn9400.liquidy.data.SavedFolder
import io.github.janhurn9400.liquidy.data.SavedLayout
import io.github.janhurn9400.liquidy.data.WidgetSpec
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.motion.IosSpring
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.tuning.DebugOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.ceil
import kotlin.math.roundToInt

sealed interface Container {
    data class Page(val index: Int) : Container
    data object Dock : Container
}

/** Einfügeziel beim Ziehen. */
sealed interface DropTarget {
    data class Page(val page: Int, val index: Int) : DropTarget
    data class Dock(val index: Int) : DropTarget
}

/** Animierte Position eines Icons; [pos] ist seitenlokal (Page) bzw. Bildschirmkoordinate (Dock). */
class IconAnim(var container: Container, initial: Offset) {
    val pos: Animatable<Offset, AnimationVector2D> = Animatable(initial, Offset.VectorConverter)
    /** Feder für die nächste Bewegung (z. B. M-DROP statt M-REFLOW). */
    var nextSpring: IosSpring? = null
}

@Stable
class DragState(
    val key: String,
    /** Finger minus Icon-Oberkante-links, Bildschirm-px. */
    val grabOffset: Offset,
    pointer: Offset,
    initialHover: DropTarget,
) {
    var pointer by mutableStateOf(pointer)
    var hover by mutableStateOf<DropTarget?>(initialHover)
    /** Item, über dessen Mitte gehalten wird → Ablegen erzeugt/füllt einen Ordner (M-MERGE). */
    var mergeTarget by mutableStateOf<String?>(null)
    val lift = Animatable(1f)
}

/** Geöffneter Ordner; [iconTopLeft] = Bildschirmposition des Ordner-Icons (Start/Ende der Animation). */
@Stable
class OpenFolder(val id: String, val iconTopLeft: Offset) {
    val progress = Animatable(0f)
}

fun isFolder(id: String) = id.startsWith(FOLDER_PREFIX)
fun isWidget(id: String) = id.startsWith(WIDGET_PREFIX)
const val WIDGET_PREFIX = "widget:"
fun isLibraryFolder(id: String) = id.startsWith(LIBRARY_PREFIX)
const val LIBRARY_PREFIX = "library"

data class LibraryCategory(val id: String, val name: String, val apps: List<AppEntry>, val isHidden: Boolean = false)

/** Glyphe eines Menüeintrags: eigene Vektor-Glyphen (keine SF Symbols) oder das einfarbige Shortcut-Symbol. */
sealed interface MenuGlyph {
    data object Remove : MenuGlyph
    data object EditHome : MenuGlyph
    data object Info : MenuGlyph
    data object Wallpaper : MenuGlyph
    data object Customise : MenuGlyph
    data object AddWidget : MenuGlyph
    data object Haptics : MenuGlyph
    data object Pages : MenuGlyph
    data object Stack : MenuGlyph
    data class Bitmap(val image: androidx.compose.ui.graphics.ImageBitmap?) : MenuGlyph
}

class MenuItem(val label: String, val glyph: MenuGlyph, val destructive: Boolean = false, val action: () -> Unit)

/** Offenes Kontextmenü; [iconTopLeft] = Ruheposition des Icons (Bildschirm). */
@Stable
class ContextMenuState(
    val key: String,
    val iconTopLeft: Offset,
    val items: List<MenuItem>,
    /** Widget: Rahmen auf dem Bildschirm + wählbare Größen (Größenleiste oben im Menü, recording8 15 s). */
    val widgetRect: androidx.compose.ui.geometry.Rect? = null,
    val sizes: List<Pair<Int, Int>> = emptyList(),
) {
    val menu = Animatable(0f)
    val dim = Animatable(0f)
    val lift = Animatable(1f)
    val liftY = Animatable(0f)
    var closing by mutableStateOf(false)
}

@Stable
class TodayDrag(val key: String, startIndex: Int) {
    var offset by mutableStateOf(Offset.Zero)
    var index by mutableStateOf(startIndex)
}

/** Laufende Größenänderung eines Widgets: Live-Rahmen (px) + Unschärfe des Inhalts 0…1. */
@Stable
class WidgetResize(val key: String, val start: androidx.compose.ui.geometry.Size) {
    val w = Animatable(start.width)
    val h = Animatable(start.height)
    val blur = Animatable(0f)
}

/** iOS-Größen: klein, mittel, groß (extragroß gibt es auf dem iPhone nicht — im Menü ausgegraut). */
val WIDGET_SIZES = listOf(2 to 2, 4 to 2, 4 to 4)
const val STACK = "stack"
const val SHOCK_DURATION_S = 0.6f
// Flug aus dem Picker [GEMESSEN] Aufnahme 10:40 (grüner Ring verfolgt): Flug 47,56 → 47,94 s, Landung 1,16× →
// 0,87× (48,25 s) → 1,0 (48,50 s) ⇒ 0,94 s gesamt. t läuft linear, die Phasen rechnet HomeScreen (WidgetFlight).
const val LANDING_MS = 1000
private val LANDING_EASE = androidx.compose.animation.core.LinearEasing
const val LANDING_ARRIVE = 0.42f   // 47,94 s: Ankunft/Aufprall
private const val LANDING_SHOCK_AT = LANDING_ARRIVE
private const val DUO_LUPE_FLASH_MS = 70
private const val BRUSH_FLASH_IN_MS = 70
private const val BRUSH_FLASH_HOLD_MS = 110L
private const val BRUSH_FLASH_OUT_MS = 120 // weiß auf in ≈ 4 Bildern (41,11 → 41,15 s)
private const val STACK_MERGE_SHARE = 0.35f // Mitten näher als 35 % der Widgetgröße → Stapel
private const val STACK_RUBBER = 0.3f
private const val STACK_FLING = 2.5f        // Widgethöhen pro Sekunde
private const val STACK_RIM_HOLD_MS = 900L // Rand steht nach dem Loslassen noch (Bild 866–892+)
private const val WIDGET_LIFT = 1.08f // recording8 15,3 s: wächst erst beim Öffnen des Menüs auf ≈ 1,08× (Breite 350 → 379 pt), nicht beim Halten
private const val RESIZE_RUBBER = 0.25f // über klein/groß hinaus nur noch ¼ des Fingerwegs [GESCHÄTZT]

/** Aufgeklappte Mediathek-Kategorie (ohne Panel, recording2 18,2 s); [tileIndex] = Kachel, aus der die Icons fliegen. */
@Stable
class OpenCategory(val category: LibraryCategory, val tileIndex: Int, val scrollAtOpen: Float) {
    val progress = Animatable(0f)
}
const val FOLDER_PREFIX = "folder:"

data class DisplayLayout(val pages: List<List<String>>, val dock: List<String>)

/**
 * Zustand des Homescreens: gespeichertes Layout, Bearbeitungsmodus, Ziehen, Antipp-Feedback.
 * Während des Ziehens wird das angezeigte Layout live berechnet ([display]): Layout ohne das gezogene Icon
 * + Einfügen am Hover-Ziel, Überlauf rutscht auf die nächste Seite (wie iOS). Erst beim Ablegen wird es übernommen.
 */
@Stable
class HomeModel(
    private val store: LayoutStore,
    val repository: AppRepository,
    private val scope: CoroutineScope,
) {
    var apps by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set
    var pages by mutableStateOf<List<List<String>>>(listOf(emptyList()))
        private set
    var dock by mutableStateOf<List<String>>(emptyList())
        private set
    var folders by mutableStateOf<Map<String, SavedFolder>>(emptyMap())
        private set
    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set
    var widgets by mutableStateOf<Map<String, WidgetSpec>>(emptyMap())
        private set

    // ── Heute-Ansicht (links von Seite 0, recording8 2–20 s) ─────────────────────
    var today by mutableStateOf<List<String>>(emptyList())
        private set
    val todayScroll = Animatable(0f)

    /** Widget oben in die Heute-Ansicht einfügen (iOS: neue Widgets landen oben). */
    fun addTodayWidget(spec: WidgetSpec) {
        val id = WIDGET_PREFIX + java.util.UUID.randomUUID().toString()
        widgets = widgets + (id to spec)
        today = listOf(id) + today
        save()
    }

    /** Ziehen in der Heute-Ansicht: gezogenes Widget + Fingerversatz (px) + aktuelle Zielposition in der Liste. */
    var todayDrag by mutableStateOf<TodayDrag?>(null)
        private set

    fun beginTodayDrag(key: String) {
        if (key !in today) return
        todayDrag = TodayDrag(key, today.indexOf(key))
    }

    fun updateTodayDrag(offset: Offset, targetIndex: Int) {
        val d = todayDrag ?: return
        d.offset = offset
        d.index = targetIndex.coerceIn(0, today.lastIndex)
    }

    fun endTodayDrag() {
        val d = todayDrag ?: return
        today = todayOrder()
        todayDrag = null
        save()
    }

    /** Angezeigte Reihenfolge (während des Ziehens schon mit dem Widget am Zielplatz → die anderen rücken nach). */
    fun todayOrder(): List<String> {
        val d = todayDrag ?: return today
        val rest = today - d.key
        return rest.toMutableList().apply { add(d.index.coerceIn(0, rest.size), d.key) }
    }

    /** Standard-Widgets beim ersten Start (wie iOS: Batterien, Kalender, Uhr). */
    private fun defaultToday(): List<String> {
        val specs = listOf(
            WidgetSpec(io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes.BATTERY, 2, 2),
            WidgetSpec(io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes.CALENDAR, 2, 2),
            WidgetSpec(io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes.CLOCK, 2, 2),
            WidgetSpec(io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes.CALENDAR, 4, 2),
        )
        val ids = specs.map { WIDGET_PREFIX + java.util.UUID.randomUUID().toString() }
        widgets = widgets + ids.zip(specs)
        return ids
    }

    /** Größe eines Items in Rasterzellen (Icon/Ordner 1×1). */
    fun sizeOf(key: String): Pair<Int, Int> = widgets[key]?.let { it.cols to it.rows } ?: (1 to 1)

    /** Zellen je Seite für das angezeigte Layout (GridPlacement). */
    val displayCells: List<IntArray> by derivedStateOf { display.pages.map { GridPlacement.place(it, ::sizeOf) } }

    /** Wird von MainActivity gesetzt: Android-Widget entfernen (AppWidgetHost.deleteAppWidgetId). */
    var onAndroidWidgetRemoved: (Int) -> Unit = {}

    /** Widget oben auf der aktuellen Seite einfügen (iOS), Rest fließt weiter. */
    /**
     * Neues Widget fliegt aus dem Picker in seinen Platz und löst eine Schockwelle aus (Aufnahme 10-09 10:40, 47,6–48,3 s):
     * gläserne Kopie hebt sich aus der Vorschau, landet leicht vergrößert (≈ 1,12×), schwingt auf 1,0 ein, Inhalt wird
     * dabei scharf; die Nachbar-Icons werden ≈ 6 pt radial weggedrückt und federn zurück (Welle läuft nach außen).
     */
    class WidgetLanding(val key: String, val fromCenter: Offset) {
        /** EIN durchgehender Fortschritt 0 → 1: Abheben, Flug, Überschwingen, Einschwingen (kein Stückeln). */
        val t = Animatable(0f)
    }
    var widgetLanding by mutableStateOf<WidgetLanding?>(null)
        private set
    /** Schockwelle: Mittelpunkt (Bildschirm-px) + Zeit seit Auslösung (s). */
    class Shockwave(val center: Offset) { val time = Animatable(0f) }
    var shockwave by mutableStateOf<Shockwave?>(null)
        private set

    fun startWidgetLanding(key: String, fromCenter: Offset, landingCenter: () -> Offset?) {
        val l = WidgetLanding(key, fromCenter)
        widgetLanding = l
        scope.launch {
            var shocked = false
            l.t.animateTo(1f, androidx.compose.animation.core.tween(LANDING_MS, easing = LANDING_EASE)) {
                if (!shocked && value > LANDING_SHOCK_AT) { shocked = true; fireShock(landingCenter()) }
            }
            if (widgetLanding === l) widgetLanding = null
        }
    }

    private fun fireShock(c: Offset?) {
        c ?: return
        val sw = Shockwave(c); shockwave = sw
        scope.launch {
            sw.time.animateTo(SHOCK_DURATION_S, androidx.compose.animation.core.tween((SHOCK_DURATION_S * 1000).toInt(), easing = androidx.compose.animation.core.LinearEasing))
            if (shockwave === sw) shockwave = null
        }
    }

    /** Neues Widget; gibt seine ID zurück (für die Lande-Animation). */
    fun addWidget(spec: WidgetSpec, page: Int): String {
        val id = WIDGET_PREFIX + java.util.UUID.randomUUID().toString()
        widgets = widgets + (id to spec)
        val p = pages.toMutableList()
        val target = page.coerceIn(0, p.lastIndex.coerceAtLeast(0))
        if (p.isEmpty()) p += emptyList<String>()
        p[target] = listOf(id) + p[target]
        pages = GridPlacement.flow(p, ::sizeOf)
        save()
        return id
    }

    // ── Widget-Größe per Griff (M-WIDGET-RESIZE) ─────────────────────────────────
    var resize by mutableStateOf<WidgetResize?>(null)
        private set

    /** Layout beim Anfassen — jede Größenstufe wird von hier aus neu verteilt (nicht kumulativ), damit Apps zurückkommen. */
    private var resizeBasePages: List<List<String>> = emptyList()

    fun beginResize(key: String, start: androidx.compose.ui.geometry.Size) {
        val r = WidgetResize(key, start)
        resizeBasePages = pages
        resize = r
        scope.launch { r.w.snapTo(start.width); r.h.snapTo(start.height) }
    }

    /** Rahmen folgt dem Finger (gummibandartig über klein/groß hinaus); Größenstufe wechselt live → Icons fließen um. */
    fun updateResize(delta: Offset, geo: HomeGeometry) {
        val r = resize ?: return
        val min = geo.widgetSize(2, 2); val max = geo.widgetSize(4, 4)
        fun rb(v: Float, lo: Float, hi: Float): Float = when {
            v < lo -> lo - (lo - v) * RESIZE_RUBBER
            v > hi -> hi + (v - hi) * RESIZE_RUBBER
            else -> v
        }
        val w = rb(r.start.width + delta.x, min.width, max.width)
        val h = rb(r.start.height + delta.y, min.height, max.height)
        // Unschärfe = Abstand zur nächsten erlaubten Größe (iOS: je näher an klein/mittel/groß, desto schärfer)
        val half = Offset((max.width - min.width) / 2, (max.height - min.height) / 2)
        val sizes = listOf(min.width to min.height, max.width to min.height, max.width to max.height)
        val d = sizes.minOf { (sw, sh) -> maxOf(kotlin.math.abs(w - sw) / half.x, kotlin.math.abs(h - sh) / half.y) }.coerceIn(0f, 1f)
        val blur = d * d * (3f - 2f * d) // weich (smoothstep)
        scope.launch { r.w.snapTo(w); r.h.snapTo(h); r.blur.snapTo(blur) }
        val cols = if (w > (min.width + max.width) / 2) 4 else 2
        val rows = if (h > (min.height + max.height) / 2) 4 else 2
        val spec = widgets[r.key] ?: return
        if (spec.cols != cols || spec.rows != rows) {
            widgets = widgets + (r.key to spec.copy(cols = cols, rows = rows))
            // nur so viel weiterschieben wie nötig — immer vom Ausgangslayout aus
            pages = GridPlacement.flow(resizeBasePages, ::sizeOf).filterIndexed { i, pg -> pg.isNotEmpty() || i == 0 }
        }
    }

    fun endResize(geo: HomeGeometry) {
        val r = resize ?: return
        val spec = widgets[r.key] ?: run { resize = null; return }
        val target = geo.widgetSize(spec.cols, spec.rows)
        scope.launch {
            kotlinx.coroutines.coroutineScope {
                launch { r.w.animateTo(target.width, Motion.widgetResize.spec()) }
                launch { r.h.animateTo(target.height, Motion.widgetResize.spec()) }
                // gleichzeitig scharf werden, während es in die Größe einrastet
                launch { r.blur.animateTo(0f, androidx.compose.animation.core.tween(Motion.widgetResizeBlurOut.value.roundToInt())) }
            }
            if (resize === r) resize = null
        }
        save()
    }

    /** Größe aus der Größenleiste: derselbe Morph wie beim Griff (unscharf → einrasten → scharf). */
    fun setWidgetSize(key: String, cols: Int, rows: Int, geo: HomeGeometry) {
        val spec = widgets[key] ?: return
        if (spec.cols == cols && spec.rows == rows) return
        val r = WidgetResize(key, geo.widgetSize(spec.cols, spec.rows))
        resize = r
        scope.launch { r.blur.snapTo(1f) }
        widgets = widgets + (key to spec.copy(cols = cols, rows = rows))
        pages = GridPlacement.flow(pages, ::sizeOf)
        endResize(geo)
    }

    fun removeWidget(id: String) {
        val spec = widgets[id] ?: return
        if (spec.appWidgetId >= 0) onAndroidWidgetRemoved(spec.appWidgetId)
        spec.stack.forEach { if (it.appWidgetId >= 0) onAndroidWidgetRemoved(it.appWidgetId) }
        widgets = widgets - id
        pages = pages.map { it - id }
        today = today - id
        anims.remove(id)
        save()
    }

    // ── Widget-Stapel (Bildschirmaufnahme 10-08 09:56) ───────────────────────────
    /** Position je Stapel: 0 = erstes Widget, 1 = zweites …; dazwischen während des Wischens. */
    private val stackPos = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    /** Glasrahmen + Punkte sichtbar (beim Wischen 1, danach ausblenden). */
    private val stackActive = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    private var stackHideJob: kotlinx.coroutines.Job? = null

    fun stackPosition(key: String) = stackPos.getOrPut(key) { Animatable(0f) }
    fun stackActivity(key: String) = stackActive.getOrPut(key) { Animatable(0f) }
    /** Punkte neben dem Stapel: bleiben nach dem Wischen noch kurz stehen (länger als der Rahmen). */
    private val stackDots = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    fun stackDotsAlpha(key: String) = stackDots.getOrPut(key) { Animatable(0f) }
    /** Glasrand des Stapels (bleibt nach dem Wischen länger als das Aufwachsen). */
    private val stackRim = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    fun stackRimAlpha(key: String) = stackRim.getOrPut(key) { Animatable(0f) }

    /** Gerade sichtbares Widget eines Stapels (für Öffnen, Beschriftung). */
    fun shownWidget(key: String): WidgetSpec? {
        val w = widgets[key] ?: return null
        if (w.type != STACK || w.stack.isEmpty()) return w
        return w.stack[stackPosition(key).value.roundToInt().coerceIn(0, w.stack.lastIndex)]
    }

    private var stackStart = 0f
    fun stackDragStart(key: String) {
        val p = stackPosition(key); stackStart = p.value
        stackHideJob?.cancel()
        scope.launch { p.stop() }
        scope.launch { stackActivity(key).animateTo(1f, Motion.stackFrame.spec()) }
        scope.launch { stackDotsAlpha(key).animateTo(1f, Motion.stackFrame.spec()) }
        scope.launch { stackRimAlpha(key).animateTo(1f, Motion.stackFrame.spec()) }
    }

    /** [dy] = Fingerweg seit Beginn (px), [h] = Widgethöhe. Hochwischen → nächstes Widget; an den Enden gummibandartig. */
    fun stackDrag(key: String, dy: Float, h: Float) {
        val n = widgets[key]?.stack?.size ?: return
        val raw = stackStart - dy / h
        val v = when { raw < 0f -> raw * STACK_RUBBER; raw > n - 1f -> (n - 1f) + (raw - (n - 1f)) * STACK_RUBBER; else -> raw }
        scope.launch { stackPosition(key).snapTo(v) }
    }

    fun stackDragEnd(key: String, velocityY: Float, h: Float) {
        val n = widgets[key]?.stack?.size ?: return
        val p = stackPosition(key)
        // Schwung: schneller Wisch blättert genau eins weiter
        val base = stackStart.roundToInt()
        val target = when {
            velocityY < -STACK_FLING * h -> base + 1
            velocityY > STACK_FLING * h -> base - 1
            else -> p.value.roundToInt()
        }.coerceIn(0, n - 1)
        scope.launch { p.animateTo(target.toFloat(), Motion.stackSnap.spec(), initialVelocity = -velocityY / h) }
        // Bild 862–892: Inhalt wächst im stehenden Glasrand wieder auf (≈ 0,47 s), der Rand bleibt danach noch kurz,
        // erst dann blenden Rand + Punkte aus
        scope.launch { stackActivity(key).animateTo(0f, Motion.stackGrow.spec()) }
        stackHideJob = scope.launch {
            delay(STACK_RIM_HOLD_MS)
            launch { stackRimAlpha(key).animateTo(0f, Motion.stackFrame.spec()) }
            stackDotsAlpha(key).animateTo(0f, Motion.stackFrame.spec())
        }
    }

    // ── Stapel bearbeiten (Aufnahme 10-08 10:21) ──────────────────────────────────
    var stackEditKey by mutableStateOf<String?>(null)
        private set
    /** 0 = zu, 1 = Editor offen. */
    val stackEdit = Animatable(0f)
    /** Minus im Editor: Index des Widgets, für das die Abfrage offen ist. */
    var stackRemoveAsk by mutableStateOf<Int?>(null)

    /** Bildschirmrahmen des Stapels beim Öffnen — daraus wächst das Panel, dorthin schrumpft es zurück. */
    var stackEditFrom by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        private set

    fun openStackEditor(key: String) {
        if (widgets[key]?.type != STACK) return
        val m = contextMenu?.takeIf { it.key == key }
        // Startrahmen = angehobene Menü-Kopie (Ruheplatz × Hebung); die Kopie selbst verschwindet sofort — das Panel IST sie (Bild 288)
        stackEditFrom = m?.widgetRect?.let { r ->
            val s = m.lift.value; val c = r.center - Offset(0f, m.liftY.value * repository.context.resources.displayMetrics.density)
            androidx.compose.ui.geometry.Rect(c - Offset(r.width * s / 2, r.height * s / 2), androidx.compose.ui.geometry.Size(r.width * s, r.height * s))
        } ?: stackEditFrom
        if (m != null) contextMenu = null
        stackEditKey = key
        scope.launch { stackEdit.animateTo(1f, Motion.stackEditor.spec()) }
    }

    fun closeStackEditor() {
        val k = stackEditKey ?: return
        stackRemoveAsk = null
        scope.launch {
            stackEdit.animateTo(0f, Motion.stackEditor.spec())
            if (stackEditKey == k) stackEditKey = null
        }
    }

    private fun updateStack(key: String, items: List<WidgetSpec>) {
        val s = widgets[key] ?: return
        when (items.size) {
            0 -> { removeWidget(key); closeStackEditor(); return }
            1 -> { widgets = widgets + (key to items[0]); stackPos.remove(key); closeStackEditor() } // ein Widget → kein Stapel mehr
            else -> {
                widgets = widgets + (key to s.copy(stack = items))
                scope.launch { stackPosition(key).let { p -> p.snapTo(p.value.coerceIn(0f, items.lastIndex.toFloat())) } }
            }
        }
        save()
    }

    fun stackRemoveAt(key: String, index: Int) {
        val s = widgets[key] ?: return
        s.stack.getOrNull(index)?.let { if (it.appWidgetId >= 0) onAndroidWidgetRemoved(it.appWidgetId) }
        updateStack(key, s.stack.filterIndexed { i, _ -> i != index })
    }

    fun stackMove(key: String, from: Int, to: Int) {
        val s = widgets[key] ?: return
        if (from == to || from !in s.stack.indices) return
        val items = s.stack.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) }
        widgets = widgets + (key to s.copy(stack = items))
        scope.launch { stackPosition(key).snapTo(to.toFloat()) }
        save()
    }

    /** Aus dem Editor auf den Homescreen ziehen: Widget kommt als eigenes Widget direkt hinter den Stapel. */
    fun stackDetach(key: String, index: Int) {
        val s = widgets[key] ?: return
        val item = s.stack.getOrNull(index) ?: return
        val id = WIDGET_PREFIX + java.util.UUID.randomUUID().toString()
        widgets = widgets + (id to item)
        pages = GridPlacement.flow(pages.map { pg -> pg.flatMap { if (it == key) listOf(key, id) else listOf(it) } }, ::sizeOf)
        updateStack(key, s.stack.filterIndexed { i, _ -> i != index })
        closeStackEditor()
    }

    /** „+“ im Editor: neues Widget kommt oben in den Stapel (Größe des Stapels). */
    var addingToStack by mutableStateOf(false)
    fun addToStack(spec: WidgetSpec) {
        val k = stackEditKey ?: return
        val s = widgets[k] ?: return
        updateStack(k, listOf(spec.copy(cols = s.cols, rows = s.rows)) + s.stack)
        scope.launch { stackPosition(k).snapTo(0f) }
    }

    fun toggleStackOption(rotate: Boolean) {
        val k = stackEditKey ?: return
        val s = widgets[k] ?: return
        widgets = widgets + (k to if (rotate) s.copy(smartRotate = !s.smartRotate) else s.copy(suggestions = !s.suggestions))
        save()
    }

    var editing by mutableStateOf(false)
        private set
    var drag by mutableStateOf<DragState?>(null)
        private set
    var pendingRemoval by mutableStateOf<String?>(null)
    var openFolder by mutableStateOf<OpenFolder?>(null)
        private set

    /** Antipp-Feedback (M-PRESS) — immer nur ein Icon gleichzeitig. */
    var pressedKey by mutableStateOf<String?>(null)
    val pressAlpha = Animatable(0f)

    /** Icon wächst beim Halten vor dem Kontextmenü (M-CTXMENU, 1 → 1,23). */
    val pressScale = Animatable(1f)
    /** Icon, das [pressScale] gerade trägt (bleibt beim Zurückfedern gesetzt, auch wenn pressedKey schon weg ist). */
    var pressScaleKey by mutableStateOf<String?>(null)
    var contextMenu by mutableStateOf<ContextMenuState?>(null)
        private set

    /** 0 = ruhig, 1 = volle Wackel-Amplitude (M-JIGGLE). */
    val jiggleAmount = Animatable(0f)
    /** Skalierung der Minus-Badges und Edit-Buttons (M-BADGE). */
    val badgeScale = Animatable(0f)

    val anims = HashMap<String, IconAnim>()

    // ── App-Mediathek (Phase 4) ─────────────────────────────────────────────────
    val libraryScroll = Animatable(0f)
    val library: List<LibraryCategory> by derivedStateOf { computeLibrary() }
    /** Kategorien als „virtuelle Ordner“ (Tippen auf den Mini-Cluster öffnet sie wie einen Ordner). */
    fun folderFor(id: String): SavedFolder? = folders[id] ?: library.firstOrNull { it.id == id }?.let { c ->
        SavedFolder(c.name, c.apps.map { it.key })
    }

    private fun computeLibrary(): List<LibraryCategory> {
        val all = apps.values.toList()
        if (all.isEmpty()) return emptyList()
        val res = mutableListOf<LibraryCategory>()
        res += LibraryCategory("$LIBRARY_PREFIX:suggestions", repository.context.getString(io.github.janhurn9400.liquidy.R.string.library_suggestions), suggestions(8))
        res += LibraryCategory("$LIBRARY_PREFIX:recent", repository.context.getString(io.github.janhurn9400.liquidy.R.string.recently_added),
            all.sortedByDescending { it.installTime }.take(8))
        all.groupBy { it.category }.toList()
            .sortedByDescending { it.second.size }
            .forEach { (cat, list) ->
                val name = if (cat == android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED)
                    repository.context.getString(io.github.janhurn9400.liquidy.R.string.library_other) else repository.categoryName(cat)
                res += LibraryCategory("$LIBRARY_PREFIX:$cat", name, list.sortedBy { it.label.lowercase() })
            }
        res += LibraryCategory("$LIBRARY_PREFIX:hidden", repository.context.getString(io.github.janhurn9400.liquidy.R.string.library_hidden),
            emptyList(), isHidden = true)
        return res
    }

    // ── Mediathek: A–Z-Liste (M-LIB-LIST) ───────────────────────────────────────
    /** Liste sichtbar (auch während des Ein-/Ausblendens). */
    var libListOpen by mutableStateOf(false)
        private set
    /** Feld im Listen-Layout (schmal + „Cancel“) und Tastatur oben — erst nach dem Loslassen bzw. beim Antippen. */
    var libListActive by mutableStateOf(false)
        private set
    /** 1 = Kacheln scharf, 0 = unscharf hinter der Liste. */
    val libTiles = Animatable(1f)
    /** Deckkraft der Liste. */
    val libList = Animatable(0f)
    /** Abdunkeln 0…1 (× T-LIB-LIST.dim). */
    val libListDim = Animatable(0f)
    /** Fingerweg beim Herunterziehen (pt): Liste folgt 1:1, Feld mit M-LIB-LIST.follow. */
    val libListPull = Animatable(0f)
    /** 0 = Feld volle Breite (Kacheln), 1 = Listen-Layout. */
    val libFieldMorph = Animatable(0f)
    var libQuery by mutableStateOf("")

    private fun fade(a: Animatable<Float, *>, target: Float, ms: Float, delayMs: Float = 0f) = scope.launch {
        a.animateTo(target, androidx.compose.animation.core.tween(ms.roundToInt(), delayMs.roundToInt(), androidx.compose.animation.core.FastOutSlowInEasing))
    }

    private fun showLibList() {
        if (libListOpen && libList.targetValue == 1f) return
        libListOpen = true
        scope.launch { libraryScroll.stop() }
        if (io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle) {
            // Duo [GEMESSEN Video 2, 17,16–17,38 s]: Kacheln blenden in ≈ 0,15 s aus, die Zeilen GLEICHZEITIG ein
            // (ab ≈ 80 ms, nach ≈ 0,2 s ganz da) — keine leere Pause dazwischen
            fade(libTiles, 0f, DUO_LIB_TILES_OUT_MS)
            fade(libList, 1f, DUO_LIB_LIST_IN_MS, DUO_LIB_LIST_DELAY_MS)
            fade(libListDim, 1f, Motion.libListDimIn.value)
        } else {
            fade(libTiles, 0f, Motion.libTilesOut.value)
            fade(libList, 1f, Motion.libListIn.value, Motion.libListInDelay.value)
            fade(libListDim, 1f, Motion.libListDimIn.value)
        }
    }

    /** Herunterziehen am oberen Ende der Mediathek: Liste erscheint sofort (recording2 10,1 s). */
    fun libListPullBegin() = showLibList()

    fun libListPullTo(pt: Float) { scope.launch { libListPull.snapTo(pt.coerceAtLeast(0f)) } }

    /** Loslassen: Liste und Feld federn zurück, Feld morpht ins Listen-Layout, Tastatur fährt hoch. */
    fun libListRelease() {
        libListActive = true
        scope.launch { libListPull.animateTo(0f, Motion.libListSettle.spec()) }
        scope.launch { libFieldMorph.animateTo(1f, Motion.searchMorph.spec()) }
    }

    /** Antippen des Suchfelds: direkt in die Liste. */
    fun openLibList() {
        showLibList()
        libListRelease()
    }

    fun closeLibList() {
        if (!libListOpen) return
        libListActive = false
        libQuery = ""
        scope.launch { libListPull.animateTo(0f, Motion.libListSettle.spec()) }
        scope.launch { libFieldMorph.animateTo(0f, Motion.searchMorph.spec()) }
        fade(libList, 0f, Motion.libListOut.value)
        fade(libListDim, 0f, Motion.libListDimOut.value)
        scope.launch {
            libTiles.animateTo(1f, androidx.compose.animation.core.tween(Motion.libTilesIn.value.roundToInt(), Motion.libTilesInDelay.value.roundToInt(),
                androidx.compose.animation.core.FastOutSlowInEasing))
            if (!libListActive && libList.value == 0f) libListOpen = false
        }
    }

    /** Alle Apps alphabetisch (ohne versteckte), optional gefiltert. */
    fun libListApps(): List<AppEntry> {
        val q = libQuery.trim().lowercase()
        return apps.values.filter { it.key !in hidden && (q.isEmpty() || it.label.lowercase().contains(q)) }
            .sortedWith(compareBy(java.text.Collator.getInstance()) { it.label })
    }

    // ── Mediathek: Kategorie aufklappen (M-LIB-CAT) ────────────────────────────
    var openCategory by mutableStateOf<OpenCategory?>(null)
        private set

    fun openCategory(index: Int) {
        val cat = library.getOrNull(index) ?: return
        if (cat.isHidden) return // iOS: Face ID — hier noch nicht
        val c = OpenCategory(cat, index, libraryScroll.value)
        openCategory = c
        scope.launch { c.progress.animateTo(1f, Motion.libCategory.spec()) }
    }

    /** Mediathek hinter der offenen Kategorie: Skala und Deckkraft (M-LIB-CAT). */
    fun libCategoryScale(): Float {
        val p = openCategory?.progress?.value ?: return 1f
        return 1f + (Motion.libCategoryScale.value - 1f) * (p / Motion.libCategoryScaleEnd.value).coerceIn(0f, 1f)
    }
    fun libCategoryAlpha(): Float {
        val p = openCategory?.progress?.value ?: return 1f
        return 1f - (p / Motion.libCategoryFadeEnd.value).coerceIn(0f, 1f)
    }

    fun closeCategory() {
        val c = openCategory ?: return
        scope.launch {
            c.progress.animateTo(0f, Motion.libCategoryClose.spec(visibilityThreshold = FOLDER_DONE))
            if (openCategory === c) openCategory = null
        }
    }

    // ── Suche (Phase 4) ───────────────────────────────────────────────────────
    /** Homescreen-Versatz h (pt) der Suche: folgt beim Ziehen dem Finger, beim Öffnen und Schließen zurück auf 0. */
    val searchProgress = Animatable(0f)
    /** 0 = zu, 1 = offen (Abdunkeln/Blur/Skalierung des offenen Zustands, unabhängig vom Versatz). */
    val searchOpenAmount = Animatable(0f)
    var searchOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var recents by mutableStateOf(store.loadRecents())
        private set

    val display: DisplayLayout by derivedStateOf { computeDisplay() }

    private var demoPagesApplied = -1

    // ── Laden / Abgleich ──────────────────────────────────────────────────────
    fun onAppsLoaded(list: List<AppEntry>) {
        apps = list.associateBy { it.key }
        val demo = DebugOptions.demoPages.value.roundToInt()
        val saved = if (demoPagesApplied != -1 && demo != demoPagesApplied) null else store.load()
        demoPagesApplied = demo
        if (saved == null) {
            val dockApps = repository.defaultDock(list)
            val rest = list.filter { it !in dockApps }.map { it.key }
            // Emulator-Testhilfe: App-Liste auf 1 + demo Seiten verteilen
            val chunk = ceil(rest.size / (1f + demo)).toInt().coerceIn(1, PER_PAGE)
            pages = if (rest.isEmpty()) listOf(emptyList()) else rest.chunked(chunk)
            dock = dockApps.map { it.key }
            folders = emptyMap()
            hidden = emptySet()
            hiddenPages = emptyList()
            widgets = emptyMap()
            today = defaultToday()
        } else {
            val valid = apps.keys
            folders = saved.folders.mapValues { (_, f) -> f.copy(apps = f.apps.filter { it in valid }) }
                .filterValues { it.apps.isNotEmpty() }
            widgets = saved.widgets
            today = saved.today?.filter { it in saved.widgets } ?: defaultToday()
            fun keep(id: String) = id in valid || id in folders || (id in widgets && id !in today)
            dock = saved.dock.filter(::keep).take(DOCK_MAX)
            hidden = saved.hidden.filter { it in valid }.toSet()
            dock = dock.filterNot(::isWidget)
            val p = saved.pages.map { it.filter(::keep) }.filter { it.isNotEmpty() }.toMutableList()
            hiddenPages = saved.hiddenPages.map { it.copy(items = it.items.filter(::keep)) }.filter { it.items.isNotEmpty() }
            val known = p.flatten().toSet() + dock + folders.values.flatMap { it.apps } + hidden + hiddenPages.flatMap { it.items }
            val newApps = list.map { it.key }.filter { it !in known }
            newApps.forEach { key ->
                if (p.isEmpty() || !GridPlacement.fits(p.last() + key, ::sizeOf)) p += listOf(key) else p[p.lastIndex] = p.last() + key
            }
            pages = GridPlacement.flow(p.ifEmpty { listOf(emptyList()) }, ::sizeOf)
        }
        save()
    }

    fun checkDemoPages() {
        if (demoPagesApplied != -1 && DebugOptions.demoPages.value.roundToInt() != demoPagesApplied) {
            apps.values.toList().takeIf { it.isNotEmpty() }?.let { onAppsLoaded(it) }
        }
    }

    private fun save() = store.save(SavedLayout(pages, dock, folders, hidden, widgets, today, hiddenPages))

    // ── Bearbeitungsmodus ──────────────────────────────────────────────────────
    /** Haptik-Tick (aus HomeScreen gesetzt, braucht eine View); prüft selbst, ob Haptik an ist. */
    var haptic: () -> Unit = {}

    fun enterEdit() {
        if (editing) return
        editing = true
        haptic() // iOS: spürbar beim Beginn des Wackelns
        scope.launch { jiggleAmount.snapTo(1f) } // [GEMESSEN] kein Einblenden
        scope.launch { badgeScale.animateTo(1f, Motion.badge.spec()) }
    }

    fun exitEdit() {
        if (!editing) return
        drag = null
        editing = false
        scope.launch { jiggleAmount.animateTo(0f, Motion.jiggleStop.spec()) }
        scope.launch { badgeScale.animateTo(0f, Motion.badge.spec()) }
        pages = pages.filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) }
        save()
    }

    // ── Ziehen ────────────────────────────────────────────────────────────────
    fun startDrag(key: String, pointer: Offset, iconTopLeftScreen: Offset) {
        val origin = when {
            key in dock -> DropTarget.Dock(dock.indexOf(key))
            else -> pages.indexOfFirst { key in it }.let { p -> DropTarget.Page(p, pages[p].indexOf(key)) }
        }
        val d = DragState(key, pointer - iconTopLeftScreen, pointer, origin)
        drag = d
        scope.launch { d.lift.animateTo(DesignTokens.dragScale.value, Motion.dragLift.spec()) }
    }

    private var pendingHover: DropTarget? = null
    private var hoverJob: Job? = null

    /** @param pagerPage aktuell sichtbare Seite (gerundet) */
    private var pendingMerge: String? = null
    private var mergeJob: Job? = null

    fun updateDrag(pointer: Offset, geo: HomeGeometry, pagerPage: Int, pagerOffsetPx: Float) {
        val d = drag ?: return
        d.pointer = pointer
        // Ordner-Absicht: Icon-Mitte nahe der Mitte eines anderen Items → Umsortieren einfrieren
        val merge = when {
            isFolder(d.key) -> null
            isWidget(d.key) -> findStackCandidate(d, geo, pagerPage, pagerOffsetPx) // gleich großes Widget → Stapel
            else -> findMergeCandidate(d, geo, pagerPage, pagerOffsetPx)
        }
        if (merge != null) {
            hoverJob?.cancel(); pendingHover = null
            if (merge != pendingMerge) {
                pendingMerge = merge
                mergeJob?.cancel()
                mergeJob = scope.launch {
                    delay(Motion.mergeDelay.value.roundToInt().toLong())
                    if (drag === d) d.mergeTarget = merge
                }
            }
            return
        }
        pendingMerge = null; mergeJob?.cancel(); d.mergeTarget = null
        val candidate = computeTarget(d, geo, pagerPage, pagerOffsetPx)
        if (candidate != pendingHover) {
            pendingHover = candidate
            hoverJob?.cancel()
            hoverJob = scope.launch {
                delay(Motion.reflowDelay.value.roundToInt().toLong())
                if (drag === d) d.hover = candidate
            }
        }
    }

    /** Widget über ein gleich großes Widget gezogen (Mitten nahe beieinander) → Stapel-Ziel (iOS). */
    private fun findStackCandidate(d: DragState, geo: HomeGeometry, page: Int, pagerOffsetPx: Float): String? {
        val spec = widgets[d.key] ?: return null
        val mine = geo.widgetRect(d.pointer - d.grabOffset, spec.cols, spec.rows)
        val cells = displayCells.getOrNull(page)
        display.pages.getOrNull(page)?.forEachIndexed { slot, id ->
            val other = widgets[id] ?: return@forEachIndexed
            val cell = cells?.getOrNull(slot) ?: -1
            if (id == d.key || cell < 0 || other.cols != spec.cols || other.rows != spec.rows) return@forEachIndexed
            val r = geo.widgetRect(geo.slotPosition(cell) + Offset(pagerOffsetPx, 0f), other.cols, other.rows)
            if ((r.center - mine.center).getDistance() < minOf(r.width, r.height) * STACK_MERGE_SHARE) return id
        }
        return null
    }

    private fun findMergeCandidate(d: DragState, geo: HomeGeometry, page: Int, pagerOffsetPx: Float): String? {
        val half = Offset(geo.icon / 2, geo.icon / 2)
        val center = d.pointer - d.grabOffset + half
        val radius = DesignTokens.folderMergeRadius.value * geo.icon / DesignTokens.iconSize.value
        val shown = display
        val cells = displayCells.getOrNull(page)
        shown.pages.getOrNull(page)?.forEachIndexed { slot, id ->
            val cell = cells?.getOrNull(slot) ?: -1
            if (id != d.key && !isWidget(id) && cell >= 0 && (geo.slotPosition(cell) + Offset(pagerOffsetPx, 0f) + half - center).getDistance() < radius) return id
        }
        shown.dock.forEachIndexed { i, id ->
            if (id != d.key && (geo.dockPosition(i, shown.dock.size) + half - center).getDistance() < radius) return id
        }
        return null
    }

    private fun computeTarget(d: DragState, geo: HomeGeometry, page: Int, pagerOffsetPx: Float): DropTarget? {
        val iconTopLeft = d.pointer - d.grabOffset
        val center = iconTopLeft + Offset(geo.icon / 2, geo.icon / 2)
        val baseDock = dock - d.key
        if (geo.inDockZone(d.pointer) && !isWidget(d.key)) {
            if (d.key !in dock && baseDock.size >= DOCK_MAX) return null
            return DropTarget.Dock(geo.dockIndexAt(center, baseDock.size))
        }
        val basePage = pages.getOrNull(page)?.minus(d.key) ?: emptyList()
        val local = center - Offset(pagerOffsetPx, 0f)
        // Zielzelle → Einfügeposition in der Liste: vor das erste Item, das an/nach dieser Zelle liegt
        val targetCell = geo.slotAt(local, Int.MAX_VALUE)
        val cells = GridPlacement.place(basePage, ::sizeOf)
        val index = cells.indexOfFirst { it < 0 || it >= targetCell }.let { if (it < 0) basePage.size else it }
        return DropTarget.Page(page, index)
    }

    /** Legt das gezogene Icon ab; Bildschirm→Container-Umrechnung, damit es vom Finger aus in den Platz fällt. */
    fun drop(geo: HomeGeometry, pagerPosition: Float) {
        val d = drag ?: return
        hoverJob?.cancel()
        mergeJob?.cancel()
        pendingHover = null
        pendingMerge = null
        d.mergeTarget?.let { target -> mergeInto(d.key, target); drag = null; save(); return }
        val result = computeDisplay()
        val screenTopLeft = d.pointer - d.grabOffset
        val container = when {
            d.key in result.dock -> Container.Dock
            else -> Container.Page(result.pages.indexOfFirst { d.key in it })
        }
        val local = when (container) {
            Container.Dock -> screenTopLeft
            is Container.Page -> screenTopLeft - Offset(geo.pageShift(container.index - pagerPosition), 0f)
        }
        val anim = anims.getOrPut(d.key) { IconAnim(container, local) }
        anim.container = container
        scope.launch { anim.pos.snapTo(local) }
        anim.nextSpring = Motion.drop
        pages = result.pages
        dock = result.dock
        drag = null
        save()
    }

    /** Legt [key] in den Ordner [target] bzw. erzeugt aus App [target] + [key] einen neuen Ordner an dessen Platz. */
    private fun mergeInto(key: String, target: String) {
        val basePages = pages.map { it - key }
        val baseDock = dock - key
        if (isWidget(key)) { // Widget auf Widget → Stapel; das hinzugefügte liegt oben (iOS zeigt es danach)
            val t = widgets.getValue(target); val k = widgets.getValue(key)
            fun parts(w: WidgetSpec) = if (w.type == STACK) w.stack else listOf(w)
            val items = parts(k) + parts(t)
            widgets = widgets - key + (target to WidgetSpec(STACK, t.cols, t.rows, stack = items))
            stackPos.remove(target)
            pages = basePages
            return
        }
        if (isFolder(target)) {
            val f = folders.getValue(target)
            folders = folders + (target to f.copy(apps = f.apps + key))
            pages = basePages; dock = baseDock
            return
        }
        val id = FOLDER_PREFIX + java.util.UUID.randomUUID().toString()
        folders = folders + (id to SavedFolder(repository.folderName(apps[target], apps[key]), listOf(target, key)))
        anims[target]?.let { anims[id] = IconAnim(it.container, it.pos.value) }
        pages = basePages.map { pg -> pg.map { if (it == target) id else it } }
        dock = baseDock.map { if (it == target) id else it }
    }

    // ── Edit-Menü (Bearbeitungsmodus, Knopf oben links) + Wallpaper-Editor ───────────
    val editMenu = Animatable(0f)
    var editMenuOpen by mutableStateOf(false)
        private set
    var wallpaperEditorOpen by mutableStateOf(false)
    var customiseOpen by mutableStateOf(false)
        private set
    var customiseClosing by mutableStateOf(false)
        private set

    fun openCustomise() { exitEdit(); customiseClosing = false; customiseOpen = true }
    fun closeCustomise() { if (customiseOpen) customiseClosing = true }
    fun customiseClosed() { customiseOpen = false; customiseClosing = false }

    var widgetPickerOpen by mutableStateOf(false)

    fun editMenuItems(): List<MenuItem> = listOfNotNull(
        // Einmalig: Lesezugriff aufs Android-Wallpaper („Alle Dateien verwalten“) → danach immer das Android-Bild als einzige Quelle
        if (io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead()) null
        else MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.use_android_wallpaper), MenuGlyph.Wallpaper) {
            exitEdit()
            val ctx = repository.context
            runCatching {
                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.fromParts("package", ctx.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.add_widget), MenuGlyph.AddWidget) { widgetPickerOpen = true },
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.customise), MenuGlyph.Customise) { openCustomise() },
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.edit_wallpaper), MenuGlyph.Wallpaper) {
            exitEdit(); wallpaperEditorOpen = true
        },
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.edit_pages), MenuGlyph.Pages) { pagesOverviewRequested = true },
        // Haptik ein/aus (Beschriftung = was der Tipp bewirkt)
        MenuItem(repository.context.getString(if (io.github.janhurn9400.liquidy.data.Haptics.enabled) io.github.janhurn9400.liquidy.R.string.haptics_off
            else io.github.janhurn9400.liquidy.R.string.haptics_on), MenuGlyph.Haptics) {
            io.github.janhurn9400.liquidy.data.Haptics.set(repository.context, !io.github.janhurn9400.liquidy.data.Haptics.enabled)
        },
    )

    /** Duo: Pinsel-Knopf leuchtet vor dem Menü weiß auf (0…1) [GEMESSEN] 21:28, 53,53–53,71 s. */
    val editBrushFlash = Animatable(0f)

    fun openEditMenu(duo: Boolean = false) {
        if (!duo) {
            editMenuOpen = true
            scope.launch { editMenu.animateTo(1f, Motion.ctxMenu.spec()) }
            return
        }
        scope.launch {
            editBrushFlash.animateTo(1f, androidx.compose.animation.core.tween(BRUSH_FLASH_IN_MS))
            kotlinx.coroutines.delay(BRUSH_FLASH_HOLD_MS)
            editMenuOpen = true
            launch { editBrushFlash.animateTo(0f, androidx.compose.animation.core.tween(BRUSH_FLASH_OUT_MS)) }
            editMenu.animateTo(1f, Motion.ctxMenu.spec())
        }
    }

    fun closeEditMenu() {
        scope.launch {
            editMenu.animateTo(0f, Motion.ctxMenuClose.spec(visibilityThreshold = FOLDER_DONE))
            editMenuOpen = false
        }
    }

    // ── Rückkehr aus einer App (Gesten-Navigation) ───────────────────────────────
    /** Von HomeScreen gesetzt: Bildschirmrechteck des Icons einer App auf der sichtbaren Seite/im Dock/im Ordner. */
    var iconLocator: (android.content.ComponentName, android.os.UserHandle) -> androidx.compose.ui.geometry.Rect? = { _, _ -> null }

    /** Icon, in das gerade ein App-Fenster fliegt: wird ausgeblendet (die Icon-Fläche des Systems steht an seiner Stelle). */
    var flyingKey by mutableStateOf<String?>(null)

    /** Von MainActivity gesetzt: zeigt die Icon-Fläche und meldet Position + Fläche ans System. */
    var flyingSurface: (io.github.janhurn9400.liquidy.data.GestureNavContract, android.graphics.RectF, android.graphics.Bitmap?, () -> Unit, Float?) -> Unit =
        { c, r, _, done, _ -> c.sendEndPosition(r); done() }

    fun onReturnFromApp(contract: io.github.janhurn9400.liquidy.data.GestureNavContract?) {
        holdReturnBlur()
        contract?.let { c ->
            val r = iconLocator(c.component, c.user) ?: return@let
            val app = apps.values.firstOrNull { it.component == c.component && it.user == c.user }
            // Ziel ist ein Widget (größer als ein Icon) → keine Icon-Fläche, das Widget selbst bleibt sichtbar
            val isIconSized = kotlin.math.abs(r.width - DesignTokens.iconSize.value * repository.context.resources.displayMetrics.density) < 2f
            val inOpenFolder = openFolder?.let { of -> app?.key?.let { k -> folderFor(of.id)?.apps?.contains(k) } } == true
            val target = app?.key?.takeIf { k -> isIconSized && (dock.contains(k) || pages.any { k in it } || inOpenFolder) }
            flyingKey = target
            // Widget-Ziel: Bild des Widgets als Fläche, mit Widget-Ecken (wie beim Dock-Icon, nur in Widget-Größe)
            val widgetKey = io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins[c.component.packageName]?.takeIf { !isIconSized }
            val snap = widgetKey?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetSnapshots[it] }
            flyingKey = target ?: widgetKey?.takeIf { snap != null }
            val corner = if (snap != null) DesignTokens.widgetCorner.value * repository.context.resources.displayMetrics.density else null
            val fk = flyingKey
            android.util.Log.i("GestureNavContract", "Rückkehr ${c.component.packageName} → $r widget=$widgetKey snap=${snap != null} iconSized=$isIconSized")
            flyingSurface(c, android.graphics.RectF(r.left, r.top, r.right, r.bottom),
                snap ?: if (target != null) app.icon.asAndroidBitmap() else null, { if (flyingKey == fk) flyingKey = null }, corner)
        }
    }

    /** Unschärfe beim Zurückkehren aus einer App (1 = voll, 0 = scharf); gehalten, bis der Homescreen den Fokus hat. */
    val returnBlur = Animatable(0f)
    private var returnPending = false

    /** Unschärfe beim Öffnen einer App (Android-Wallpaper + Icons), 0…1. */
    val launchBlur = Animatable(0f)
    private var launchedApp = false

    /** Icon angetippt → Homescreen wird unscharf, während die App aus dem Icon wächst. */
    fun onAppLaunching() {
        launchedApp = true
        scope.launch { launchBlur.animateTo(1f, Motion.appOpen.spec(visibilityThreshold = 0.002f)) }
    }

    /**
     * Homescreen nicht mehr sichtbar (App liegt davor). Hintergrund zurück auf scharf — Android zeigt ihn während der
     * Zurück-Geste ebenfalls scharf —, die Icons bleiben für die Rückkehr unscharf (werden beim Erscheinen scharf).
     */
    fun onHomeHidden() {
        scope.launch { launchBlur.snapTo(0f) }
        if (launchedApp) holdReturnBlur()
        launchedApp = false
    }

    private fun holdReturnBlur() {
        scope.launch { returnBlur.snapTo(1f) }
        returnPending = true
    }

    /** Von MainActivity.onWindowFocusChanged: Rückkehr-Animation des Systems ist fertig → scharf werden. */
    fun onHomeVisible() {
        // App doch nicht gestartet (oder abgebrochen): nicht unscharf hängen bleiben
        if (launchBlur.value > 0f || launchBlur.isRunning) scope.launch { launchBlur.animateTo(0f, Motion.homeReturn.spec()) }
        launchedApp = false
        if (!returnPending) return
        returnPending = false
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            returnBlur.animateTo(0f, Motion.homeReturn.spec(visibilityThreshold = 0.002f))
        }
    }

    // ── Kontextmenü (M-CTXMENU) ──────────────────────────────────────────────────
    /** Shortcuts schon beim Aufsetzen im Hintergrund laden (LauncherApps + Glyphen), damit das Menü ohne Ruckler öffnet. */
    private var shortcutPrefetch: Pair<String, kotlinx.coroutines.Deferred<List<io.github.janhurn9400.liquidy.data.AppShortcut>>>? = null

    fun prefetchShortcuts(key: String, density: Float) {
        if (shortcutPrefetch?.first == key) return
        val app = apps[key] ?: return
        val px = (DesignTokens.ctxGlyph.value * density).toInt()
        shortcutPrefetch = key to scope.async(kotlinx.coroutines.Dispatchers.Default) { repository.shortcuts(app, px) }
    }

    fun openContextMenu(key: String, iconTopLeft: Offset, density: Float, widgetRect: androidx.compose.ui.geometry.Rect? = null) {
        haptic() // iOS: spürbar, wenn das Menü aufgeht
        val res = repository.context
        val items = buildList {
            val app = apps[key]
            // Ordner: kein „App entfernen“/App-Info (würde einen Lösch-Dialog ohne App öffnen)
            if (app != null) add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.remove_app_menu), MenuGlyph.Remove, destructive = true) { pendingRemoval = key })
            val isStack = widgets[key]?.type == STACK
            // Stapel (Aufnahme 10-08 10:21, Bild 262): Stapel bearbeiten · Home-Bildschirm bearbeiten · Stapel entfernen (rot, unten)
            // Reihenfolge wie die übrigen Systemeinträge: zuerst der äußerste (rot), ContextMenu dreht je nach Lage um
            if (isStack) add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.remove_stack), MenuGlyph.Remove, destructive = true) { removeWidget(key) })
            if (isWidget(key) && !isStack) add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.remove_widget), MenuGlyph.Remove, destructive = true) { removeWidget(key) })
            add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.edit_home_screen), MenuGlyph.EditHome) { enterEdit() })
            if (isStack) add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.edit_stack), MenuGlyph.Stack) { openStackEditor(key) })
            if (app != null) {
                add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.app_info), MenuGlyph.Info) {
                    repository.showAppInfo(app, android.graphics.Rect())
                })
                // native App-Shortcuts (Android), im iOS-Stil — vorab geladen; fehlen sie noch, öffnet das Menü ohne sie
                val pre = shortcutPrefetch?.takeIf { it.first == key }?.second
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                val list = if (pre != null && pre.isCompleted) pre.getCompleted() else emptyList()
                list.forEach { sc ->
                    add(MenuItem(sc.label, MenuGlyph.Bitmap(sc.glyph)) { repository.startShortcut(sc, android.graphics.Rect()) })
                }
            }
        }
        shortcutPrefetch = null
        val m = ContextMenuState(key, iconTopLeft, items, widgetRect, if (isWidget(key)) WIDGET_SIZES else emptyList())
        contextMenu = m
        scope.launch { pressAlpha.snapTo(0f); pressedKey = null }
        // Widgets heben sich kaum (große Fläche) — Icons wie gemessen 1,15× und 10 pt
        val liftScale = if (widgetRect != null) WIDGET_LIFT else Motion.ctxLiftScale.value
        scope.launch { m.lift.snapTo(pressScale.value); pressScale.snapTo(1f); m.lift.animateTo(liftScale, Motion.ctxLift.spec()) }
        if (widgetRect == null) scope.launch { m.liftY.animateTo(Motion.ctxLiftY.value, Motion.ctxLift.spec()) }
        scope.launch { m.menu.animateTo(1f, Motion.ctxMenu.spec()) }
        scope.launch { m.dim.animateTo(1f, androidx.compose.animation.core.tween(Motion.ctxDimIn.value.roundToInt())) }
    }

    /** Schließen; [then] läuft sofort (z. B. Aktion), die Animation läuft weiter. */
    fun closeContextMenu(animated: Boolean = true) {
        val m = contextMenu ?: return
        if (m.closing) return
        m.closing = true
        if (!animated) { contextMenu = null; return }
        scope.launch { m.menu.animateTo(0f, Motion.ctxMenuClose.spec(visibilityThreshold = FOLDER_DONE)) }
        scope.launch { m.lift.animateTo(1f, Motion.ctxMenuClose.spec()) }
        scope.launch { m.liftY.animateTo(0f, Motion.ctxMenuClose.spec()) }
        scope.launch {
            m.dim.animateTo(0f, androidx.compose.animation.core.tween(Motion.ctxDimOut.value.roundToInt()))
            if (contextMenu === m) contextMenu = null
        }
    }

    // ── Ordner öffnen / schließen (M-FOLDER) ─────────────────────────────────────
    /** Ordner umbenennen (Bearbeiten-Modus, Kapsel über dem offenen Ordner). Leer → beim Schließen Standardname. */
    fun renameFolder(id: String, name: String) {
        val f = folders[id] ?: return
        folders = folders + (id to f.copy(name = name))
        save()
    }

    fun openFolder(id: String, iconTopLeft: Offset) {
        val f = OpenFolder(id, iconTopLeft)
        openFolder = f
        scope.launch { f.progress.animateTo(1f, Motion.folder.spec()) }
    }

    fun closeFolder() {
        val f = openFolder ?: return
        folders[f.id]?.let { if (it.name.isBlank()) renameFolder(f.id, DEFAULT_FOLDER_NAME) }
        scope.launch {
            // feine Sichtbarkeitsschwelle, damit kein Rest-Panel „hängen bleibt“
            f.progress.animateTo(0f, Motion.folder.spec(visibilityThreshold = FOLDER_DONE))
            if (openFolder === f) openFolder = null
        }
    }

    /** Überblendung in der kleinsten Phase: 0 = nur geschlossenes Icon, 1 = nur Panel (M-FOLDER.handoff). */
    fun folderHandoff(p: Float): Float {
        val x = (p / Motion.folderHandoff.value).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Long-Press auf ein Icon im offenen Ordner: aus dem Ordner nehmen und auf dem Homescreen weiterziehen. */
    fun dragOutOfFolder(key: String, pointer: Offset, iconTopLeftScreen: Offset) {
        val f = openFolder ?: return
        if (isLibraryFolder(f.id)) return
        val folder = folders[f.id] ?: return
        val remaining = folder.apps - key
        val page = pages.indexOfFirst { f.id in it }
        folders = if (remaining.isEmpty()) folders - f.id else folders + (f.id to folder.copy(apps = remaining))
        if (remaining.isEmpty()) { pages = pages.map { it - f.id }; dock = dock - f.id }
        // Icon zunächst direkt hinter dem Ordner einsortieren, dann normal ziehen
        if (page >= 0) {
            val idx = pages[page].indexOf(f.id)
            pages = pages.toMutableList().also { it[page] = it[page].toMutableList().apply { add((idx + 1).coerceIn(0, size), key) } }
        } else {
            pages = pages.toMutableList().also { it[0] = it[0] + key }
        }
        anims.remove(key)
        enterEdit()
        startDrag(key, pointer, iconTopLeftScreen)
        closeFolder()
    }

    private fun computeDisplay(): DisplayLayout {
        val d = drag ?: return DisplayLayout(pages, dock)
        val p = pages.map { it - d.key }.toMutableList()
        var dk = dock - d.key
        when (val h = d.hover) {
            is DropTarget.Page -> {
                while (p.size <= h.page) p += emptyList<String>()
                p[h.page] = p[h.page].toMutableList().apply { add(h.index.coerceIn(0, size), d.key) }
                // Überlauf (nach Platz im Raster, Widgets belegen mehrere Zellen) auf die Folgeseiten schieben
                val flowed = GridPlacement.flow(p, ::sizeOf)
                p.clear(); p.addAll(flowed)
            }
            is DropTarget.Dock -> dk = dk.toMutableList().apply { add(h.index.coerceIn(0, size), d.key) }
            null -> Unit
        }
        return DisplayLayout(p, dk)
    }

    // ── Seitenübersicht („Seiten bearbeiten“, Bildschirmaufnahme 10-08 09:55) ─────
    var hiddenPages by mutableStateOf<List<io.github.janhurn9400.liquidy.data.HiddenPage>>(emptyList())
        private set
    var pagesOverviewOpen by mutableStateOf(false)
        private set
    /** 0 = zu, 1 = offen (Karten im Raster). */
    val pagesOverview = Animatable(0f)
    /** Karte (Übersichts-Index), aus der beim Öffnen herausgezoomt bzw. in die beim Schließen hineingezoomt wird. */
    var overviewFocus by mutableStateOf(0)
        private set

    /** Aus dem Bearbeiten-Menü angefordert (HomeScreen kennt die aktuelle Seite). */
    var pagesOverviewRequested by mutableStateOf(false)

    data class OverviewPage(val items: List<String>, val visible: Boolean)

    /** Sichtbare und ausgeblendete Seiten in Übersichts-Reihenfolge. */
    fun overviewPages(): List<OverviewPage> {
        val hid = hiddenPages.sortedBy { it.pos }.toMutableList()
        val vis = pages.toMutableList()
        val out = mutableListOf<OverviewPage>()
        while (vis.isNotEmpty() || hid.isNotEmpty()) {
            if (hid.isNotEmpty() && (hid.first().pos <= out.size || vis.isEmpty())) out += OverviewPage(hid.removeAt(0).items, false)
            else out += OverviewPage(vis.removeAt(0), true)
        }
        return out
    }

    private fun visibleIndexOf(overviewIndex: Int) = overviewPages().take(overviewIndex).count { it.visible }

    fun openPagesOverview(currentPage: Int) {
        if (pagesOverviewOpen) return
        val ov = overviewPages()
        var seen = -1
        overviewFocus = ov.indexOfFirst { if (it.visible) seen++; it.visible && seen == currentPage }.coerceAtLeast(0)
        pagesOverviewOpen = true
        scope.launch { pagesOverview.animateTo(1f, Motion.pagesOverview.spec()) }
    }

    /** Schließen: zoomt in [toIndex] (Übersichts-Index einer sichtbaren Seite) und meldet deren Seitennummer. */
    fun closePagesOverview(toIndex: Int = overviewFocus, onPage: (Int) -> Unit) {
        if (!pagesOverviewOpen) return
        val ov = overviewPages()
        val target = toIndex.takeIf { ov.getOrNull(it)?.visible == true } ?: ov.indexOfFirst { it.visible }.coerceAtLeast(0)
        overviewFocus = target
        scope.launch {
            pagesOverview.animateTo(0f, Motion.pagesOverview.spec())
            pagesOverviewOpen = false
        }
        onPage(visibleIndexOf(target))
    }

    /** Haken: Seite ein-/ausblenden (mindestens eine Seite bleibt sichtbar). */
    fun togglePage(index: Int) {
        val ov = overviewPages()
        val page = ov.getOrNull(index) ?: return
        if (page.visible) {
            if (ov.count { it.visible } <= 1) return
            val v = visibleIndexOf(index)
            pages = pages.toMutableList().apply { removeAt(v) }
            hiddenPages = hiddenPages + io.github.janhurn9400.liquidy.data.HiddenPage(index, page.items)
        } else {
            val v = visibleIndexOf(index)
            hiddenPages = hiddenPages.filterNot { it.pos == index }
            pages = pages.toMutableList().apply { add(v.coerceIn(0, size), page.items) }
        }
        save()
    }

    /** Karte in der Übersicht verschieben (sichtbare und ausgeblendete Seiten gemeinsam). */
    fun movePage(from: Int, to: Int) {
        val ov = overviewPages().toMutableList()
        if (from !in ov.indices || to !in ov.indices || from == to) return
        ov.add(to, ov.removeAt(from))
        pages = ov.filter { it.visible }.map { it.items }.ifEmpty { listOf(emptyList()) }
        hiddenPages = ov.mapIndexedNotNull { i, pg -> if (pg.visible) null else io.github.janhurn9400.liquidy.data.HiddenPage(i, pg.items) }
        overviewFocus = when {
            overviewFocus == from -> to
            from < overviewFocus && to >= overviewFocus -> overviewFocus - 1
            from > overviewFocus && to <= overviewFocus -> overviewFocus + 1
            else -> overviewFocus
        }
        save()
    }

    /** Minus an einer ausgeblendeten Seite: Seite entfernen (Apps bleiben in der App-Mediathek). */
    fun deleteHiddenPage(index: Int) {
        if (hiddenPages.none { it.pos == index }) return
        hiddenPages = hiddenPages.filterNot { it.pos == index }.map { if (it.pos > index) it.copy(pos = it.pos - 1) else it }
        save()
    }

    // ── Suche ────────────────────────────────────────────────────────────────
    /** 0 = „Search“-Pill, 1 = Suchfeld (M-SEARCH-MORPH). */
    val searchMorph = Animatable(0f)
    private var morphTarget = 0f
    /** h beim Loslassen — das Feld bleibt auf dieser Höhe, bis die Tastatur es erreicht. */
    var searchReleaseH by mutableStateOf(0f)
        private set

    /** true, sobald die Pill zum Suchfeld morpht (auch schon während des Ziehens) → Tastatur lugt heraus. */
    var searchMorphOn by mutableStateOf(false)
        private set

    private fun morphTo(target: Float, spring: io.github.janhurn9400.liquidy.motion.IosSpring = Motion.searchMorph) {
        if (morphTarget == target) return
        morphTarget = target
        searchMorphOn = target > 0.5f
        scope.launch { searchMorph.animateTo(target, spring.spec()) }
    }

    /** Versatz des Vorschlagsblocks (pt) — läuft beim Ziehen 1:1 mit h, federt beim Öffnen auf 0 (M-SEARCH). */
    val searchBlockOffset = Animatable(0f)

    /** Während des Ziehens: h folgt dem Finger. Es öffnet nichts (gemessen). */
    fun searchPull(h: Float) {
        val v = h.coerceAtLeast(0f)
        scope.launch { searchProgress.snapTo(v) }
        scope.launch { searchBlockOffset.snapTo(v + Motion.searchBlockBase.value) }
        if (v >= Motion.searchMorphOn.value) morphTo(1f) else if (v < Motion.searchMorphOff.value) morphTo(0f)
    }

    /** Loslassen: ab M-SEARCH.commit öffnen, sonst zurück. */
    fun searchRelease() {
        if (searchProgress.value >= Motion.searchCommit.value) openSearch() else closeSearch()
    }

    /** Duo-Lupe: Aufleuchten beim Antippen (0…1), danach öffnet die Suche. */
    val duoLupePress = Animatable(0f)

    fun openSearchFromDuoLupe() {
        scope.launch {
            duoLupePress.animateTo(1f, androidx.compose.animation.core.tween(DUO_LUPE_FLASH_MS))
            openSearch()
            duoLupePress.snapTo(0f)
        }
    }

    fun openSearch() {
        searchReleaseH = searchProgress.value
        morphTo(1f)
        searchOpen = true
        // gemessen: Homescreen federt zurück nach oben (0 pt), bleibt aber verkleinert + abgedunkelt
        scope.launch { searchProgress.animateTo(0f, Motion.search.spec()) }
        scope.launch { searchOpenAmount.animateTo(1f, Motion.search.spec()) }
        scope.launch { searchBlockOffset.animateTo(0f, Motion.search.spec()) }
    }

    fun closeSearch(animated: Boolean = true) {
        morphTarget = -1f
        morphTo(0f, Motion.searchClose)
        if (!animated) scope.launch { searchMorph.snapTo(0f) }
        searchOpen = false
        query = ""
        scope.launch { if (animated) searchProgress.animateTo(0f, Motion.searchClose.spec()) else searchProgress.snapTo(0f) }
        scope.launch { if (animated) searchOpenAmount.animateTo(0f, Motion.searchClose.spec()) else searchOpenAmount.snapTo(0f) }
    }

    /** Sichtbar, solange gezogen wird oder der offene Zustand noch ausklingt. */
    val searchVisible: Boolean get() = searchOpen || searchProgress.value > 0f || searchOpenAmount.value > 0.001f || searchMorph.value > 0.001f

    /** Homescreen-Skalierung: beim Ziehen je pt kleiner, offen T-SEARCH.openScale. */
    fun searchContentScale(): Float {
        val o = searchOpenAmount.value.coerceIn(0f, 1f)
        val pull = 1f - DesignTokens.searchScalePerPt.value * searchProgress.value
        return pull * (1f - o) + DesignTokens.searchOpenScale.value * o
    }

    fun searchDim(): Float = maxOf(DesignTokens.searchDimPerPt.value * searchProgress.value, DesignTokens.searchDimMax.value * searchOpenAmount.value)
        .coerceIn(0f, DesignTokens.searchDimMax.value)

    private fun smooth(h: Float, a: Float, b: Float): Float {
        val x = ((h - a) / (b - a)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Blur-Anteil des Vorschlagsblocks 1 → 0 (M-SEARCH-BLOCK.sharp*); offen scharf. */
    fun searchBlockBlurFraction(): Float =
        if (searchOpen) 0f else 1f - smooth(searchProgress.value, Motion.searchBlockSharpStart.value, Motion.searchBlockSharpEnd.value)

    /** Sichtbarkeit des Vorschlagsblocks 0…1 (smoothstep über M-SEARCH-BLOCK.fadeStart…End); offen immer 1. */
    fun searchBlockAlpha(): Float {
        val h = searchProgress.value
        val x = ((h - Motion.searchBlockFadeStart.value) / (Motion.searchBlockFadeEnd.value - Motion.searchBlockFadeStart.value)).coerceIn(0f, 1f)
        return maxOf(x * x * (3f - 2f * x), searchOpenAmount.value.coerceIn(0f, 1f))
    }

    /** Siri-Vorschläge: zuletzt gestartete Apps, aufgefüllt mit den ersten Apps der Liste. */
    fun suggestions(count: Int): List<AppEntry> =
        (recents.mapNotNull { apps[it] } + apps.values).distinct().take(count)

    fun searchResults(count: Int): List<AppEntry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return apps.values.filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { if (it.label.startsWith(q, ignoreCase = true)) 0 else 1 }
            .take(count)
    }

    fun noteLaunch(key: String) {
        recents = (listOf(key) + recents.filter { it != key }).take(RECENTS_MAX)
        store.saveRecents(recents)
    }

    // ── Entfernen ─────────────────────────────────────────────────────────────
    fun removeFromHome(key: String) {
        pages = pages.map { it - key }
        dock = dock - key
        folders = folders.mapValues { (_, f) -> f.copy(apps = f.apps - key) }
        hidden = hidden + key
        save()
    }

    fun deleteApp(key: String) {
        apps[key]?.let { repository.uninstall(it) }
    }

    fun isSystemApp(key: String) = apps[key]?.let { repository.isSystemApp(it) } ?: true

    companion object {
        const val PER_PAGE = DesignTokens.GRID_COLUMNS * DesignTokens.GRID_ROWS
        const val DOCK_MAX = DesignTokens.DOCK_SLOTS
        const val RECENTS_MAX = 8
        const val FOLDER_DONE = 0.001f
        const val DEFAULT_FOLDER_NAME = "Folder"
        const val DUO_LIB_TILES_OUT_MS = 220f
        const val DUO_LIB_LIST_IN_MS = 180f
        const val DUO_LIB_LIST_DELAY_MS = 100f
    }
}
