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

/** Insertion target while dragging. */
sealed interface DropTarget {
    data class Page(val page: Int, val index: Int) : DropTarget
    data class Dock(val index: Int) : DropTarget
}

/** Animated position of an icon; [pos] is page-local (Page) or a screen coordinate (Dock). */
class IconAnim(var container: Container, initial: Offset) {
    val pos: Animatable<Offset, AnimationVector2D> = Animatable(initial, Offset.VectorConverter)
    /** Spring for the next motion (e.g. M-DROP instead of M-REFLOW). */
    var nextSpring: IosSpring? = null
    /** Scale after dropping: starts at the lifted size and springs back to 1 (M-DROP) instead of jumping. */
    val scale = Animatable(1f)
}

@Stable
class DragState(
    val key: String,
    /** Finger minus icon top-left, screen px. */
    val grabOffset: Offset,
    pointer: Offset,
    initialHover: DropTarget?,
) {
    var pointer by mutableStateOf(pointer)
    var hover by mutableStateOf<DropTarget?>(initialHover)
    /** Item whose centre is being held over → dropping creates/fills a folder (M-MERGE). */
    var mergeTarget by mutableStateOf<String?>(null)
    val lift = Animatable(1f)
}

/** App lifted in the open folder (not pulled out yet); [grab] = finger minus icon top-left. */
class FolderDrag(val key: String, val grab: Offset, pointer: Offset) {
    var pointer by mutableStateOf(pointer)
    /** Insertion slot in the folder (index without the dragged icon); -1 = still at the origin. The others make way. */
    var hoverIndex by mutableStateOf(-1)
    /** Released: screen top-left of the slot the copy now flies into (M-DROP); null = being dragged. */
    var landingAt by mutableStateOf<Offset?>(null)
}

/** Icon flies into a folder's mini slot after release (M-MERGE-DROP); rects in screen px (icon area). */
class FolderFly(val key: String, val folder: String, val from: androidx.compose.ui.geometry.Rect, val to: androidx.compose.ui.geometry.Rect) {
    val t = Animatable(0f)
}

/** Opened folder; [iconTopLeft] = screen position of the folder icon (start/end of the animation). */
@Stable
/** [iconScale] = visible size of the folder icon relative to the grid icon (Duo dock < 1, HomeGeometry.dockIconScale). */
class OpenFolder(val id: String, val iconTopLeft: Offset, val iconScale: Float = 1f) {
    val progress = Animatable(0f)
    /** Being closed (morphs into the icon without a crossfade). */
    var closing by androidx.compose.runtime.mutableStateOf(false)
}

fun isFolder(id: String) = id.startsWith(FOLDER_PREFIX)

/**
 * Several icons of the same app (iOS: an app can be on several pages): further icons are called
 * "<app key>~i<id>" and point to the same app. [AppMap] resolves such keys, [values]/[keys] stay once per app.
 */
/** App flying back into its App Library icon right now: hide the real icon there (like flyingKey on the home screen). */
object LibraryFlying { var key by androidx.compose.runtime.mutableStateOf<String?>(null) }

const val INSTANCE_SEP = "~i"
fun appKeyOf(key: String) = key.substringBefore(INSTANCE_SEP)

class AppMap(private val base: Map<String, AppEntry>) : Map<String, AppEntry> by base {
    override fun get(key: String): AppEntry? = base[key] ?: if (INSTANCE_SEP in key) base[appKeyOf(key)] else null
    override fun containsKey(key: String) = get(key) != null
}
fun isWidget(id: String) = id.startsWith(WIDGET_PREFIX)
const val WIDGET_PREFIX = "widget:"
fun isLibraryFolder(id: String) = id.startsWith(LIBRARY_PREFIX)
const val LIBRARY_PREFIX = "library"

data class LibraryCategory(val id: String, val name: String, val apps: List<AppEntry>, val isHidden: Boolean = false)

/** Glyph of a menu entry: own vector glyphs (no SF Symbols) or the monochrome shortcut icon. */
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
    data object Settings : MenuGlyph
    data class Bitmap(val image: androidx.compose.ui.graphics.ImageBitmap?) : MenuGlyph
}

class MenuItem(val label: String, val glyph: MenuGlyph, val destructive: Boolean = false, val action: () -> Unit)

/** Open context menu; [iconTopLeft] = rest position of the icon (screen). */
@Stable
class ContextMenuState(
    val key: String,
    val iconTopLeft: Offset,
    val items: List<MenuItem>,
    /** Widget: rect on screen + selectable sizes (size bar at the top of the menu, recording8 15 s). */
    val widgetRect: androidx.compose.ui.geometry.Rect? = null,
    val sizes: List<Pair<Int, Int>> = emptyList(),
    /** Base size of the icon relative to the grid icon (Duo dock: 48/60) — the lifted icon stays as big as its slot. */
    val iconScale: Float = 1f,
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

/** Ongoing widget resize: live rect (px) + content blur 0…1. */
@Stable
class WidgetResize(val key: String, val start: androidx.compose.ui.geometry.Size) {
    val w = Animatable(start.width)
    val h = Animatable(start.height)
    val blur = Animatable(0f)
}

/** iOS sizes: small, medium, large (extra large doesn't exist on the iPhone — greyed out in the menu). */
val WIDGET_SIZES = listOf(2 to 2, 4 to 2, 4 to 4)
const val STACK = "stack"
const val SHOCK_DURATION_S = 0.6f
// Flight from the picker [MEASURED] recording 10:40 (green ring tracked): flight 47.56 → 47.94 s, landing 1.16× →
// 0.87× (48.25 s) → 1.0 (48.50 s) ⇒ 0.94 s total. t runs linearly, HomeScreen computes the phases (WidgetFlight).
const val LANDING_MS = 1000
private val LANDING_EASE = androidx.compose.animation.core.LinearEasing
const val LANDING_ARRIVE = 0.42f   // 47.94 s: arrival/impact
private const val LANDING_SHOCK_AT = LANDING_ARRIVE
private const val DUO_LUPE_FLASH_MS = 70
private const val BRUSH_FLASH_IN_MS = 70
// [MEASURED video 4] droplet → menu ≈ 0.22 s to final size (size 14 % after 41 ms, 57 % after 66 ms, 80 % after 125 ms),
// barely any overshoot (≈ 5 %) → ζ 0.7, ω ≈ 15/s
private const val DROP_DAMPING = 0.7f
private const val DROP_STIFFNESS = 230f
private const val DROP_CLOSE_MS = 340
private val DROP_CLOSE_EASE = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.55f, 0.25f, 1f)
private const val BRUSH_FLASH_HOLD_MS = 60L // droplet detaches ≈ 75 ms after lighting up (video 4: 3.837 → 3.912 s)
private const val BRUSH_FLASH_OUT_MS = 140 // white in ≈ 4 frames (41.11 → 41.15 s)
private const val STACK_MERGE_SHARE = 0.35f // centres closer than 35 % of the widget size → stack
private const val STACK_RUBBER = 0.3f
private const val STACK_FLING = 2.5f        // widget heights per second
private const val STACK_RIM_HOLD_MS = 900L // rim still stays after release (frame 866–892+)
private const val WIDGET_LIFT = 1.08f // recording8 15.3 s: grows to ≈ 1.08× only when the menu opens (width 350 → 379 pt), not while holding
private const val RESIZE_RUBBER = 0.25f // beyond small/large only ¼ of the finger travel [ESTIMATED]

/** Expanded App Library category (without panel, recording2 18.2 s); [tileIndex] = tile the icons fly out of. */
@Stable
class OpenCategory(val category: LibraryCategory, val tileIndex: Int, val scrollAtOpen: Float) {
    val progress = Animatable(0f)
}
const val FOLDER_PREFIX = "folder:"

/**
 * Displayed layout. [pages] = only real items (for display/hits), [gapped] = with placeholders for gaps
 * (free placement, GridPlacement.GAP_PREFIX) — the cells are computed from it.
 */
data class DisplayLayout(val pages: List<List<String>>, val dock: List<String>, val gapped: List<List<String>> = pages)

/**
 * Home screen state: saved layout, edit mode, dragging, tap feedback.
 * While dragging the displayed layout is computed live ([display]): layout without the dragged icon
 * + insertion at the hover target, overflow moves to the next page (like iOS). It's only committed on drop.
 */
@Stable
class HomeModel(
    private val store: LayoutStore,
    val repository: AppRepository,
    private val scope: CoroutineScope,
) {
    var apps by mutableStateOf<Map<String, AppEntry>>(AppMap(emptyMap()))
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

    // ── Today view (left of page 0, recording8 2–20 s) ─────────────────────
    var today by mutableStateOf<List<String>>(emptyList())
        private set
    val todayScroll = Animatable(0f)
    /** Finger is scrolling the Today column right now (Duo: glass surface behind the column + scroll bar). */
    var todayTouching by mutableStateOf(false)

    /** Insert a widget at the top of the Today view (iOS: new widgets land at the top). */
    fun addTodayWidget(spec: WidgetSpec) {
        val id = WIDGET_PREFIX + java.util.UUID.randomUUID().toString()
        widgets = widgets + (id to spec)
        today = listOf(id) + today
        save()
    }

    /** Dragging in the Today view: dragged widget + finger offset (px) + current target position in the list. */
    var todayDrag by mutableStateOf<TodayDrag?>(null)
        private set

    fun beginTodayDrag(key: String) {
        if (key !in today) return
        todayDrag = TodayDrag(key, today.indexOf(key))
        todayLiftKey = key
        scope.launch { todayLift.animateTo(DesignTokens.dragScale.value, Motion.dragLift.spec()) }
    }

    /** Lift of the dragged Today widget: grows when picked up (M-DRAG-LIFT), springs back on drop (M-DROP). */
    val todayLift = Animatable(1f)
    /** Widget [todayLift] applies to — stays set while it springs back after the drop. */
    var todayLiftKey by mutableStateOf<String?>(null)
        private set

    fun updateTodayDrag(offset: Offset, targetIndex: Int) {
        val d = todayDrag ?: return
        d.offset = offset
        d.index = targetIndex.coerceIn(0, today.lastIndex)
    }

    /** Drag a widget from the Today page to the home screen: detach from "Today", insert at the front of page 1, keep dragging. */
    fun moveTodayToHome(key: String, pointer: Offset, cellTopLeftScreen: Offset) {
        if (key !in today) return
        todayDrag = null
        today = today - key
        pages = pages.toMutableList().also { if (it.isEmpty()) it += emptyList<String>(); it[0] = listOf(key) + it[0] }
        anims.remove(key)
        todayLiftKey = null
        startDrag(key, pointer, cellTopLeftScreen)
        val lifted = todayLift.value
        drag?.let { d -> scope.launch { d.lift.snapTo(lifted); todayLift.snapTo(1f) } } // keep the size it already had
        save()
    }

    fun endTodayDrag() {
        val d = todayDrag ?: return
        today = todayOrder()
        todayDrag = null
        scope.launch { todayLift.animateTo(1f, Motion.drop.spec()); if (todayDrag == null) todayLiftKey = null }
        save()
    }

    /** Displayed order (while dragging already with the widget at its target → the others move along). */
    fun todayOrder(): List<String> {
        val d = todayDrag ?: return today
        val rest = today - d.key
        return rest.toMutableList().apply { add(d.index.coerceIn(0, rest.size), d.key) }
    }

    /** Default widgets on first start (like iOS: batteries, calendar, clock). */
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

    /**
     * Test only (debug build, adb intent "testdemo"): tidy example layout — widgets at the top, apps sorted by purpose,
     * folders for odds and ends, dock like iOS. Apps are looked up by package name; whatever is missing is skipped.
     */
    fun debugDemoLayout() {
        if (apps.isEmpty()) return
        fun app(pkg: String) = apps.values.firstOrNull { it.component.packageName == pkg }?.key
        fun widget(type: String, c: Int, r: Int): String {
            val id = WIDGET_PREFIX + java.util.UUID.randomUUID().toString()
            widgets = widgets + (id to WidgetSpec(type, c, r)); return id
        }
        // remove old home widgets (not Today), releasing Android widgets
        val keepToday = today.toSet()
        widgets.filterKeys { it !in keepToday }.forEach { (_, w) ->
            (if (w.type == STACK) w.stack else listOf(w)).filter { it.type == "android" }.forEach { onAndroidWidgetRemoved(it.appWidgetId) }
        }
        widgets = widgets.filterKeys { it in keepToday }
        val T = io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes
        val cols = DesignTokens.GRID_COLUMNS
        fun page(vararg items: Pair<String?, Int>) = GridPlacement.canonical(items.mapNotNull { (k, c) -> k?.let { it to c } }, ::sizeOf)
        // Page 1: weather wide, below it calendar + clock, at the bottom the everyday apps
        val p1 = page(
            widget(T.WEATHER, 4, 2) to 0,
            widget(T.CALENDAR, 2, 2) to 2 * cols, widget(T.CLOCK, 2, 2) to 2 * cols + 2,
            app("com.android.camera2") to 4 * cols, app("com.google.android.apps.photos") to 4 * cols + 1,
            app("com.google.android.calendar") to 4 * cols + 2, app("com.google.android.deskclock") to 4 * cols + 3,
            app("com.google.android.apps.maps") to 5 * cols, app("com.google.android.youtube") to 5 * cols + 1,
            app("com.google.android.gm") to 5 * cols + 2, app("com.android.settings") to 5 * cols + 3,
        )
        // Page 2: photos + reminders, tools, folders, batteries bottom left (free placement)
        val extras = listOfNotNull(app("com.google.android.apps.safetyhub"), app("com.android.stk"), app("com.sysadmindoc.iosicons"))
        val folderId = FOLDER_PREFIX + java.util.UUID.randomUUID().toString()
        folders = if (extras.size >= 2) mapOf(folderId to SavedFolder("Extras", extras)) else emptyMap()
        val p2 = page(
            widget(T.PHOTOS, 2, 2) to 0, widget(T.REMINDERS, 2, 2) to 2,
            app("com.google.android.apps.docs") to 2 * cols, app("com.google.android.documentsui") to 2 * cols + 1,
            app("com.google.android.contacts") to 2 * cols + 2, app("com.google.android.googlequicksearchbox") to 2 * cols + 3,
            (if (folders.isNotEmpty()) folderId else extras.firstOrNull()) to 3 * cols,
            widget(T.BATTERY, 2, 2) to 4 * cols,
        )
        dock = listOfNotNull(app("com.google.android.dialer"), app("com.google.android.apps.messaging"),
            app("com.android.chrome"), app("com.google.android.apps.youtube.music")).take(DOCK_MAX)
        pages = listOf(p1, p2)
        hiddenPages = emptyList()
        // remaining apps stay in the App Library (like iOS "App Library Only")
        val placed = (p1 + p2 + dock + extras).toSet()
        hidden = apps.keys.filter { it !in placed }.toSet()
        save()
    }

    /** Size of an item in grid cells (icon/folder 1×1). */
    fun sizeOf(key: String): Pair<Int, Int> = widgets[key]?.let { it.cols to it.rows } ?: (1 to 1)

    /** Cells per page for the displayed layout (GridPlacement). */
    val displayCells: List<IntArray> by derivedStateOf { display.gapped.map { GridPlacement.placeReal(it, ::sizeOf) } }

    /** Set by MainActivity: remove an Android widget (AppWidgetHost.deleteAppWidgetId). */
    var onAndroidWidgetRemoved: (Int) -> Unit = {}

    /** Insert a widget at the top of the current page (iOS), the rest flows on. */
    /**
     * New widget flies from the picker into its slot and triggers a shock wave (recording 10-09 10:40, 47.6–48.3 s):
     * glass copy rises from the preview, lands slightly enlarged (≈ 1.12×), settles to 1.0, content becomes
     * sharp meanwhile; the neighbour icons are pushed ≈ 6 pt radially away and spring back (wave travels outwards).
     */
    class WidgetLanding(val key: String, val fromCenter: Offset) {
        /** ONE continuous progress 0 → 1: lift-off, flight, overshoot, settle (no stitching). */
        val t = Animatable(0f)
    }
    var widgetLanding by mutableStateOf<WidgetLanding?>(null)
        private set
    /** Shock wave: centre (screen px) + time since trigger (s). */
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

    /** New widget; returns its ID (for the landing animation). */
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

    // ── Widget size via handle (M-WIDGET-RESIZE) ─────────────────────────────────
    var resize by mutableStateOf<WidgetResize?>(null)
        private set

    /** Layout at touch down — every size step is redistributed from here (not cumulatively), so apps come back. */
    private var resizeBasePages: List<List<String>> = emptyList()

    fun beginResize(key: String, start: androidx.compose.ui.geometry.Size) {
        val r = WidgetResize(key, start)
        resizeBasePages = pages
        resize = r
        scope.launch { r.w.snapTo(start.width); r.h.snapTo(start.height) }
    }

    /** Rect follows the finger (rubber-band-like beyond small/large); size step changes live → icons reflow. */
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
        // Blur = distance to the nearest allowed size (iOS: the closer to small/medium/large, the sharper)
        val half = Offset((max.width - min.width) / 2, (max.height - min.height) / 2)
        val sizes = listOf(min.width to min.height, max.width to min.height, max.width to max.height)
        val d = sizes.minOf { (sw, sh) -> maxOf(kotlin.math.abs(w - sw) / half.x, kotlin.math.abs(h - sh) / half.y) }.coerceIn(0f, 1f)
        val blur = d * d * (3f - 2f * d) // soft (smoothstep)
        scope.launch { r.w.snapTo(w); r.h.snapTo(h); r.blur.snapTo(blur) }
        // only real sizes (small 2×2, medium 4×2, large 4×4) — the nearest one; never tall and narrow (2×4)
        val (cols, rows) = listOf(2 to 2, 4 to 2, 4 to 4).minBy { (c, rw) ->
            val sw = if (c == 4) max.width else min.width; val sh = if (rw == 4) max.height else min.height
            maxOf(kotlin.math.abs(w - sw) / half.x, kotlin.math.abs(h - sh) / half.y)
        }
        val spec = widgets[r.key] ?: return
        if (spec.cols != cols || spec.rows != rows) {
            widgets = widgets + (r.key to spec.copy(cols = cols, rows = rows))
            // push on only as much as needed — always from the starting layout
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
                // sharpen at the same time while it snaps into the size
                launch { r.blur.animateTo(0f, androidx.compose.animation.core.tween(Motion.widgetResizeBlurOut.value.roundToInt())) }
            }
            if (resize === r) resize = null
        }
        save()
    }

    /** Size from the size bar: the same morph as with the handle (blurred → snap → sharp). */
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
        pages = pagesWithHole(id)
        pruneEmptyPages()
        today = today - id
        anims.remove(id)
        save()
    }

    // ── Widget stacks (screen recording 10-08 09:56) ───────────────────────────
    /** Position per stack: 0 = first widget, 1 = second …; in between while swiping. */
    private val stackPos = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    /** Glass rim + dots visible (1 while swiping, fade out afterwards). */
    private val stackActive = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    private var stackHideJob: kotlinx.coroutines.Job? = null

    fun stackPosition(key: String) = stackPos.getOrPut(key) { Animatable(0f) }
    fun stackActivity(key: String) = stackActive.getOrPut(key) { Animatable(0f) }
    /** Dots next to the stack: stay a little longer after swiping (longer than the rim). */
    private val stackDots = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    fun stackDotsAlpha(key: String) = stackDots.getOrPut(key) { Animatable(0f) }
    /** Glass rim of the stack (stays longer after swiping than the regrowth). */
    private val stackRim = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    fun stackRimAlpha(key: String) = stackRim.getOrPut(key) { Animatable(0f) }

    /** Currently visible widget of a stack (for opening, label). */
    fun shownWidget(key: String): WidgetSpec? {
        val w = widgets[key] ?: return null
        if (w.type != STACK || w.stack.isEmpty()) return w
        return w.stack[stackPosition(key).value.roundToInt().coerceIn(0, w.stack.lastIndex)]
    }

    private var stackStart = 0f
    /** Last manual paging per stack — afterwards "Smart Rotate" doesn't interfere for a while. */
    private val stackTouched = HashMap<String, Long>()
    private var rotateJob: kotlinx.coroutines.Job? = null

    /**
     * Smart Rotate (iOS): when returning to the home screen every stack with the option enabled pages to the
     * currently most useful widget (data/SmartRotate), visible with briefly shown dots. Not shortly after manual paging.
     */
    fun smartRotateStacks() {
        if (editing) return
        val stacks = widgets.filter { it.value.type == STACK && it.value.smartRotate && it.value.stack.size > 1 }
        if (stacks.isEmpty()) return
        rotateJob?.cancel()
        rotateJob = scope.launch {
            delay(SMART_ROTATE_DELAY_MS)
            val now = System.currentTimeMillis()
            val rankedNow = ranked
            for ((key, spec) in stacks) {
                if (now - (stackTouched[key] ?: 0L) < SMART_ROTATE_QUIET_MS) continue
                val cur = stackPosition(key).value.roundToInt()
                val to = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    io.github.janhurn9400.liquidy.data.SmartRotate.pick(repository.context, spec.stack, cur, rankedNow)
                } ?: continue
                launch {
                    stackDotsAlpha(key).animateTo(1f, Motion.stackFrame.spec())
                    stackPosition(key).animateTo(to.toFloat(), Motion.stackSnap.spec())
                    delay(STACK_RIM_HOLD_MS)
                    stackDotsAlpha(key).animateTo(0f, Motion.stackFrame.spec())
                }
            }
        }
    }

    fun stackDragStart(key: String) {
        stackTouched[key] = System.currentTimeMillis()
        rotateJob?.cancel()
        val p = stackPosition(key); stackStart = p.value
        stackHideJob?.cancel()
        scope.launch { p.stop() }
        scope.launch { stackActivity(key).animateTo(1f, Motion.stackFrame.spec()) }
        scope.launch { stackDotsAlpha(key).animateTo(1f, Motion.stackFrame.spec()) }
        scope.launch { stackRimAlpha(key).animateTo(1f, Motion.stackFrame.spec()) }
    }

    /** [dy] = finger travel since start (px), [h] = widget height. Swipe up → next widget; rubber-band-like at the ends. */
    fun stackDrag(key: String, dy: Float, h: Float) {
        val n = widgets[key]?.stack?.size ?: return
        val raw = stackStart - dy / h
        val v = when { raw < 0f -> raw * STACK_RUBBER; raw > n - 1f -> (n - 1f) + (raw - (n - 1f)) * STACK_RUBBER; else -> raw }
        scope.launch { stackPosition(key).snapTo(v) }
    }

    fun stackDragEnd(key: String, velocityY: Float, h: Float) {
        val n = widgets[key]?.stack?.size ?: return
        val p = stackPosition(key)
        // Momentum: a fast swipe pages exactly one further
        val base = stackStart.roundToInt()
        val target = when {
            velocityY < -STACK_FLING * h -> base + 1
            velocityY > STACK_FLING * h -> base - 1
            else -> p.value.roundToInt()
        }.coerceIn(0, n - 1)
        scope.launch { p.animateTo(target.toFloat(), Motion.stackSnap.spec(), initialVelocity = -velocityY / h) }
        // Frame 862–892: content regrows inside the standing glass rim (≈ 0.47 s), the rim then stays briefly,
        // only then rim + dots fade out
        scope.launch { stackActivity(key).animateTo(0f, Motion.stackGrow.spec()) }
        stackHideJob = scope.launch {
            delay(STACK_RIM_HOLD_MS)
            launch { stackRimAlpha(key).animateTo(0f, Motion.stackFrame.spec()) }
            stackDotsAlpha(key).animateTo(0f, Motion.stackFrame.spec())
        }
    }

    // ── Edit Stack (recording 10-08 10:21) ──────────────────────────────────
    var stackEditKey by mutableStateOf<String?>(null)
        private set
    /** 0 = closed, 1 = editor open. */
    val stackEdit = Animatable(0f)
    /** Minus in the editor: index of the widget the confirmation is open for. */
    var stackRemoveAsk by mutableStateOf<Int?>(null)

    /** Screen rect of the stack on opening — the panel grows from it and shrinks back into it. */
    var stackEditFrom by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        private set

    fun openStackEditor(key: String) {
        if (widgets[key]?.type != STACK) return
        val m = contextMenu?.takeIf { it.key == key }
        // Start rect = lifted menu copy (rest slot × lift); the copy itself disappears immediately — the panel IS it (frame 288)
        stackEditFrom = m?.widgetRect?.let { r ->
            val s = m.lift.value; val c = r.center - Offset(0f, m.liftY.value * io.github.janhurn9400.liquidy.design.UiScale.density(repository.context))
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
            1 -> { widgets = widgets + (key to items[0]); stackPos.remove(key); closeStackEditor() } // one widget → no longer a stack
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

    /** Drag from the editor to the home screen: the widget becomes its own widget right behind the stack. */
    fun stackDetach(key: String, index: Int) {
        val s = widgets[key] ?: return
        val item = s.stack.getOrNull(index) ?: return
        val id = WIDGET_PREFIX + java.util.UUID.randomUUID().toString()
        widgets = widgets + (id to item)
        pages = GridPlacement.flow(pages.map { pg -> pg.flatMap { if (it == key) listOf(key, id) else listOf(it) } }, ::sizeOf)
        updateStack(key, s.stack.filterIndexed { i, _ -> i != index })
        closeStackEditor()
    }

    /** "+" in the editor: new widget goes on top of the stack (stack size). */
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

    /** Tap feedback (M-PRESS) — only one icon at a time. */
    var pressedKey by mutableStateOf<String?>(null)
    val pressAlpha = Animatable(0f)

    /** Icon grows while holding before the context menu (M-CTXMENU, 1 → 1.23). */
    val pressScale = Animatable(1f)
    /** Icon that currently carries [pressScale] (stays set while springing back, even if pressedKey is already gone). */
    var pressScaleKey by mutableStateOf<String?>(null)
    var contextMenu by mutableStateOf<ContextMenuState?>(null)
        private set

    /** 0 = still, 1 = full jiggle amplitude (M-JIGGLE). */
    val jiggleAmount = Animatable(0f)
    /** Scale of the minus badges and edit buttons (M-BADGE). */
    val badgeScale = Animatable(0f)
    /** Duo: ✓/brush top right (own curve, see enterEdit). 0 = gone, 1 = there. */
    val editButtons = Animatable(0f)
    private var editButtonsJob: kotlinx.coroutines.Job? = null

    val anims = HashMap<String, IconAnim>()

    // ── App Library (phase 4) ─────────────────────────────────────────────────
    val libraryScroll = Animatable(0f)
    val library: List<LibraryCategory> by derivedStateOf { computeLibrary() }
    /** Categories as "virtual folders" (tapping the mini cluster opens them like a folder). */
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

    // ── App Library: A–Z list (M-LIB-LIST) ───────────────────────────────────────
    /** List visible (also while fading in/out). */
    var libListOpen by mutableStateOf(false)
        private set
    /** Field in list layout (narrow + "Cancel") and keyboard up — only after release or on tap. */
    var libListActive by mutableStateOf(false)
        private set
    /** 1 = tiles sharp, 0 = blurred behind the list. */
    val libTiles = Animatable(1f)
    /** Opacity of the list. */
    val libList = Animatable(0f)
    /** Dimming 0…1 (× T-LIB-LIST.dim). */
    val libListDim = Animatable(0f)
    /** Finger travel while pulling down (pt): list follows 1:1, field with M-LIB-LIST.follow. */
    val libListPull = Animatable(0f)
    /** 0 = field full width (tiles), 1 = list layout. */
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
            // Duo [MEASURED video 2, 17.16–17.38 s]: tiles fade out in ≈ 0.15 s, the rows fade in AT THE SAME TIME
            // (from ≈ 80 ms, fully there after ≈ 0.2 s) — no empty pause in between
            fade(libTiles, 0f, DUO_LIB_TILES_OUT_MS)
            fade(libList, 1f, DUO_LIB_LIST_IN_MS, DUO_LIB_LIST_DELAY_MS)
            fade(libListDim, 1f, Motion.libListDimIn.value)
        } else {
            fade(libTiles, 0f, Motion.libTilesOut.value)
            fade(libList, 1f, Motion.libListIn.value, Motion.libListInDelay.value)
            fade(libListDim, 1f, Motion.libListDimIn.value)
        }
    }

    /** Pull down at the top end of the App Library: list appears immediately (recording2 10.1 s). */
    fun libListPullBegin() = showLibList()

    fun libListPullTo(pt: Float) { scope.launch { libListPull.snapTo(pt.coerceAtLeast(0f)) } }

    /** Release: list and field spring back, field morphs into the list layout, keyboard comes up. */
    fun libListRelease() {
        libListActive = true
        scope.launch { libListPull.animateTo(0f, Motion.libListSettle.spec()) }
        scope.launch { libFieldMorph.animateTo(1f, Motion.searchMorph.spec()) }
    }

    /** Tapping the search field: straight into the list. */
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

    /** All apps alphabetically (without hidden ones), optionally filtered. */
    fun libListApps(): List<AppEntry> {
        val q = libQuery.trim().lowercase()
        return apps.values.filter { it.key !in hidden && (q.isEmpty() || it.label.lowercase().contains(q)) }
            .sortedWith(compareBy(java.text.Collator.getInstance()) { it.label })
    }

    // ── App Library: expand category (M-LIB-CAT) ────────────────────────────
    var openCategory by mutableStateOf<OpenCategory?>(null)
        private set

    fun openCategory(index: Int) {
        val cat = library.getOrNull(index) ?: return
        if (cat.isHidden) return // iOS: Face ID — not here yet
        val c = OpenCategory(cat, index, libraryScroll.value)
        openCategory = c
        scope.launch { c.progress.animateTo(1f, Motion.libCategory.spec()) }
    }

    /** App Library behind the open category: scale and opacity (M-LIB-CAT). */
    fun libCategoryScale(): Float {
        val p = openCategory?.progress?.value ?: return 1f
        return 1f + (Motion.libCategoryScale.value - 1f) * (p / Motion.libCategoryScaleEnd.value).coerceIn(0f, 1f)
    }
    fun libCategoryAlpha(): Float {
        val p = openCategory?.progress?.value ?: return 1f
        return 1f - (p / Motion.libCategoryFadeEnd.value).coerceIn(0f, 1f)
    }

    /** Close immediately (after pulling out an app — the category was already invisible then). */
    fun closeCategoryNow() { openCategory = null }

    /** When pulling out: undo category effects (blur, shrunk App Library) immediately, the view stays. */
    fun collapseCategoryForDrag() { openCategory?.let { c -> scope.launch { c.progress.snapTo(0f) } } }

    fun closeCategory() {
        val c = openCategory ?: return
        scope.launch {
            c.progress.animateTo(0f, Motion.libCategoryClose.spec(visibilityThreshold = FOLDER_DONE))
            if (openCategory === c) openCategory = null
        }
    }

    // ── Search (phase 4) ───────────────────────────────────────────────────────
    /** Home offset h (pt) of the search: follows the finger while dragging, back to 0 on open and close. */
    val searchProgress = Animatable(0f)
    /** 0 = closed, 1 = open (dimming/blur/scale of the open state, independent of the offset). */
    val searchOpenAmount = Animatable(0f)
    var searchOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var recents by mutableStateOf(store.loadRecents())
        private set

    val display: DisplayLayout by derivedStateOf { computeDisplay() }

    private var demoPagesApplied = -1

    // ── Loading / sync ──────────────────────────────────────────────────────
    fun onAppsLoaded(list: List<AppEntry>) {
        apps = AppMap(list.associateBy { it.key })
        refreshSuggestions()
        val demo = DebugOptions.demoPages.value.roundToInt()
        val saved = if (demoPagesApplied != -1 && demo != demoPagesApplied) null else store.load()
        demoPagesApplied = demo
        if (saved == null) {
            val dockApps = repository.defaultDock(list)
            val rest = list.filter { it !in dockApps }.map { it.key }
            // Emulator test helper: spread the app list over 1 + demo pages
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
            folders = saved.folders.mapValues { (_, f) -> f.copy(apps = f.apps.filter { appKeyOf(it) in valid }) }
                .filterValues { it.apps.isNotEmpty() }
            widgets = saved.widgets
            today = saved.today?.filter { it in saved.widgets } ?: defaultToday()
            fun keep(id: String) = appKeyOf(id) in valid || id in folders || (id in widgets && id !in today) || GridPlacement.isGap(id)
            dock = saved.dock.filter(::keep).take(DUO_DOCK_SLOTS_LIMIT) // "More Apps in Dock": at most dockMax are shown
            hidden = saved.hidden.filter { it in valid }.toSet()
            dock = dock.filterNot(::isWidget)
            val p = saved.pages.map { GridPlacement.trim(it.filter(::keep)) }.filterNot(GridPlacement::isBlank).toMutableList()
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

    /** Grid rule changed (Quirks): reflow the pages so nothing ends up outside the grid. */
    fun reflowPages() {
        pages = GridPlacement.flow(pages, ::sizeOf)
        save()
    }

    private fun save() = store.save(SavedLayout(pages, dock, folders, hidden, widgets, today, hiddenPages))

    // ── Edit mode ──────────────────────────────────────────────────────
    /** Haptic tick (set from HomeScreen, needs a View); checks itself whether haptics are on. */
    var haptic: () -> Unit = {}

    fun enterEdit() {
        if (editing) return
        editing = true
        haptic() // iOS: noticeable when jiggling starts
        scope.launch { jiggleAmount.snapTo(1f) } // [MEASURED] no fade-in
        scope.launch { badgeScale.animateTo(1f, Motion.badge.spec()) }
        // Duo [MEASURED recording 19:07:42, 6.54–6.75 s]: status symbols gone, ≈ 90 ms nothing, then ✓/brush pop up at
        // ≈ 60 % size half transparent and spring to full size with a slight overshoot (≈ 120 ms)
        editButtonsJob?.cancel()
        editButtonsJob = scope.launch {
            editButtons.snapTo(0f)
            delay(EDIT_BUTTONS_DELAY_MS)
            editButtons.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 900f))
        }
    }

    fun exitEdit() {
        if (!editing) return
        drag = null
        editing = false
        scope.launch { jiggleAmount.animateTo(0f, Motion.jiggleStop.spec()) }
        scope.launch { badgeScale.animateTo(0f, Motion.badge.spec()) }
        // Duo [MEASURED 19:06:29, 11.97 → 11.98 s]: buttons disappear in one frame
        editButtonsJob?.cancel(); editButtonsJob = scope.launch { editButtons.snapTo(0f) }
        pages = pages.filterNot(GridPlacement::isBlank).map(GridPlacement::trim).ifEmpty { listOf(emptyList()) }
        save()
    }

    // ── Dragging ────────────────────────────────────────────────────────────────
    /** Own badge factor per icon (pick-up/drop, M-BADGE-DRAG); missing = 1. */
    val badgeOwn = androidx.compose.runtime.mutableStateMapOf<String, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    /** Minus badge size for [key] (edit mode × own factor). */
    fun badgeOf(key: String): Float = badgeScale.value * (badgeOwn[key]?.value ?: 1f)

    /** Original at the origin fades out on pick-up (iOS: ghost + shrinking badge). */
    class PickupGhost(val key: String, val topLeft: Offset, val dock: Boolean) { val fade = Animatable(1f) }
    var pickupGhost by mutableStateOf<PickupGhost?>(null)
        private set

    fun startDrag(key: String, pointer: Offset, iconTopLeftScreen: Offset) {
        badgeOwn[key] = Animatable(0f)
        if (apps[key] != null || isFolder(key)) {
            val g = PickupGhost(key, iconTopLeftScreen, key in dock)
            pickupGhost = g
            scope.launch { g.fade.animateTo(0f, Motion.badgeDrag.spec()); if (pickupGhost === g) pickupGhost = null }
        }
        val origin = when {
            key in dock -> DropTarget.Dock(dock.indexOf(key))
            // from the App Library: app isn't anywhere on the home screen (yet) → insert only when hovering over a page
            pages.none { key in it } -> null
            // Target = cell (free placement); origin cell of the item
            else -> pages.indexOfFirst { key in it }.let { p ->
                DropTarget.Page(p, GridPlacement.place(pages[p], ::sizeOf).getOrElse(pages[p].indexOf(key)) { 0 }.coerceAtLeast(0))
            }
        }
        dragGap = GridPlacement.newGap()
        val d = DragState(key, pointer - iconTopLeftScreen, pointer, origin)
        drag = d
        scope.launch { d.lift.animateTo(DesignTokens.dragScale.value, Motion.dragLift.spec()) }
    }

    private var pendingHover: DropTarget? = null
    private var hoverJob: Job? = null
    /** Placeholder that stays at the origin while dragging (current iOS: the others don't move up). */
    private var dragGap = GridPlacement.newGap()

    /** @param pagerPage currently visible page (rounded) */
    private var pendingMerge: String? = null
    private var mergeJob: Job? = null
    /** Finger position from which the standstill over the target is counted (M-MERGE). */
    private var mergeAnchor = Offset.Zero

    fun updateDrag(pointer: Offset, geo: HomeGeometry, pagerPage: Int, pagerOffsetPx: Float) {
        val d = drag ?: return
        d.pointer = pointer
        // Folder intent: icon centre near the centre of another item → freeze reordering
        val merge = when {
            isFolder(d.key) -> null
            isWidget(d.key) -> findStackCandidate(d, geo, pagerPage, pagerOffsetPx) // equally sized widget → stack
            else -> findMergeCandidate(d, geo, pagerPage, pagerOffsetPx)
        }
        if (merge != null) {
            hoverJob?.cancel(); pendingHover = null
            // iOS: the intent arises as soon as the icon STANDS STILL over the target (not when swiping over) — every larger
            // movement restarts the short wait, as long as the hint isn't showing yet
            val still = DesignTokens.folderMergeStill.value * geo.icon / DesignTokens.iconSize.value
            val restart = merge != pendingMerge || (d.mergeTarget == null && (pointer - mergeAnchor).getDistance() > still)
            if (restart) {
                if (merge != pendingMerge) d.mergeTarget = null
                pendingMerge = merge
                mergeAnchor = pointer
                mergeJob?.cancel()
                mergeJob = scope.launch {
                    delay(Motion.mergeDelay.value.roundToInt().toLong())
                    if (drag === d) d.mergeTarget = merge
                }
            }
            return
        }
        pendingMerge = null; mergeJob?.cancel(); d.mergeTarget = null
        // Over an icon (not quite in the centre yet): DON'T make way — otherwise the target moves away before the folder intent
        // can arise. iOS makes way only when you hold next to it (user 10-14)
        // (not in the dock: there you're always close to a dock icon — inserting into the dock must still work)
        if (!isFolder(d.key) && !isWidget(d.key) && !geo.inDockZone(d.pointer) &&
            findMergeCandidate(d, geo, pagerPage, pagerOffsetPx, DesignTokens.folderMergeNear.value) != null) {
            hoverJob?.cancel(); pendingHover = null
            return
        }
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

    /** Widget dragged over an equally sized widget (centres close together) → stack target (iOS). */
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

    private fun findMergeCandidate(d: DragState, geo: HomeGeometry, page: Int, pagerOffsetPx: Float,
                                   radiusPt: Float = DesignTokens.folderMergeRadius.value): String? {
        val half = Offset(geo.icon / 2, geo.icon / 2)
        val center = d.pointer - d.grabOffset + half
        val radius = radiusPt * geo.icon / DesignTokens.iconSize.value
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
            if (d.key !in dock && baseDock.size >= dockMax) return null
            return DropTarget.Dock(geo.dockIndexAt(center, baseDock.size))
        }
        val local = center - Offset(pagerOffsetPx, 0f)
        // free placement: target is the cell under the icon (widget: top-left cell), no longer a list position
        val targetCell = geo.slotAt(local, Int.MAX_VALUE).coerceIn(0, DesignTokens.GRID_COLUMNS * DesignTokens.GRID_ROWS - 1)
        return DropTarget.Page(page, targetCell)
    }

    /** Drops the dragged icon; screen→container conversion so it falls from the finger into the slot. */
    fun drop(geo: HomeGeometry, pagerPosition: Float) {
        val d = drag ?: return
        hoverJob?.cancel()
        mergeJob?.cancel()
        pendingHover = null
        pendingMerge = null
        badgeOwn.remove(d.key) // doesn't land in a slot (folder/back) → factor no longer needed
        d.mergeTarget?.let { target -> mergeInto(d.key, target); drag = null; pruneEmptyPages(); save(); return }
        val result = computeDisplay()
        // dropped nowhere (e.g. dragged from the App Library but never over a page) → stays where it was
        if (result.pages.none { d.key in it } && d.key !in result.dock) { drag = null; pruneEmptyPages(); return }
        badgeOwn[d.key] = Animatable(0f)
        hidden = hidden - appKeyOf(d.key) // brought back from the App Library to the home screen
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
        val lifted = d.lift.value
        scope.launch { anim.scale.snapTo(lifted); anim.scale.animateTo(1f, Motion.drop.spec()) }
        anim.nextSpring = Motion.drop
        // Badge grows in smoothly immediately on release (user 10-14: waiting for the end of the landing spring felt sluggish)
        badgeOwn[d.key]?.let { a -> scope.launch { a.animateTo(1f, Motion.badgeDrag.spec()); if (badgeOwn[d.key] === a && drag?.key != d.key) badgeOwn.remove(d.key) } }
        pages = result.gapped.map(GridPlacement::trim)
        dock = result.dock
        drag = null
        pruneEmptyPages() // e.g. page created at the front on which nothing was dropped after all
        save()
    }

    // ── Widget settings (tap in edit mode) ───────────────────────
    var widgetConfigKey by mutableStateOf<String?>(null)
        private set
    val widgetConfig = Animatable(0f)

    /** Screen rect of the widget the sheet turns out of (and back into). */
    var widgetConfigFrom by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
        private set

    /**
     * [MEASURED iPhone 10-11 16:40, motion per frame at 60 fps]: open ≈ 0.62 s — the widget turns around the vertical
     * axis (edge-on ≈ 0.16 s), behind it the card appears and grows from the centre; close ≈ 0.55 s in reverse.
     */
    fun openWidgetConfig(key: String, from: androidx.compose.ui.geometry.Rect) {
        widgetConfigFrom = from
        widgetConfigKey = key
        scope.launch {
            widgetConfig.snapTo(0f)
            // [MEASURED motion per frame]: strong from the first frame, 0.29 s main part, long tail until 0.62 s; edge-on at ≈ 0.16 s
            widgetConfig.animateTo(1f, androidx.compose.animation.core.tween(WIDGET_CONFIG_OPEN_MS,
                easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.55f, 0.28f, 1f)))
        }
    }

    fun closeWidgetConfig() {
        scope.launch {
            // [MEASURED]: motion rises in ≈ 80 ms, edge-on at ≈ 0.11 s, settled after ≈ 0.55 s
            widgetConfig.animateTo(0f, androidx.compose.animation.core.tween(WIDGET_CONFIG_CLOSE_MS,
                easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.4f, 0.25f, 1f)))
            widgetConfigKey = null
        }
    }

    fun setWidgetConfig(key: String, config: String?) {
        val w = widgets[key] ?: return
        widgets = widgets + (key to w.copy(config = config))
        save()
    }

    /** Test only (debug build, adb intent "teststack"): the first two small widgets of a page into a stack. */
    fun debugMakeStack() {
        val small = pages.flatten().filter { k -> widgets[k]?.let { it.type != STACK && it.cols == 2 && it.rows == 2 } == true }
        if (small.size < 2) return
        mergeInto(small[0], small[1]); save()
    }

    /** Puts [key] into folder [target] or creates a new folder from app [target] + [key] in its place. */
    private fun mergeInto(key: String, target: String) {
        val basePages = pagesWithHole(key)
        val baseDock = dock - key
        if (isWidget(key)) { // widget on widget → stack; the added one is on top (iOS shows it afterwards)
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

    // ── Edit menu (edit mode, button top left) + wallpaper editor ───────────
    val editMenu = Animatable(0f)
    var editMenuOpen by mutableStateOf(false)
        private set
    var wallpaperEditorOpen by mutableStateOf(false)
    var customiseOpen by mutableStateOf(false)
        private set
    var customiseClosing by mutableStateOf(false)
        private set

    fun openCustomise() { exitEdit(); customiseClosing = false; customiseOpen = true }

    var settingsOpen by mutableStateOf(false)
    var settingsClosing by mutableStateOf(false)
    fun openSettings() { exitEdit(); settingsClosing = false; settingsOpen = true }
    fun closeSettings() { if (settingsOpen) settingsClosing = true }
    fun settingsClosed() { settingsOpen = false; settingsClosing = false }
    fun closeCustomise() { if (customiseOpen) customiseClosing = true }
    fun customiseClosed() { customiseOpen = false; customiseClosing = false }

    var widgetPickerOpen by mutableStateOf(false)

    fun editMenuItems(): List<MenuItem> = listOfNotNull(
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.add_widget), MenuGlyph.AddWidget) { widgetPickerOpen = true },
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.customise), MenuGlyph.Customise) { openCustomise() },
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.edit_wallpaper), MenuGlyph.Wallpaper) {
            exitEdit(); wallpaperEditorOpen = true
        },
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.edit_pages), MenuGlyph.Pages) { pagesOverviewRequested = true },
        // Launcher settings (haptics, Duo style, Android wallpaper …) — own sheet instead of more menu entries
        MenuItem(repository.context.getString(io.github.janhurn9400.liquidy.R.string.launcher_settings), MenuGlyph.Settings) { openSettings() },
    )

    /** Duo: brush button lights up white before the menu (0…1) [MEASURED] 21:28, 53.53–53.71 s. */
    val editBrushFlash = Animatable(0f)
    /** Duo: menu is flowing back into the brush right now (different droplet shape than when opening). */
    var editMenuClosing by mutableStateOf(false)

    fun openEditMenu(duo: Boolean = false) {
        if (!duo) {
            editMenuOpen = true
            scope.launch { editMenu.animateTo(1f, Motion.ctxMenu.spec()) }
            return
        }
        scope.launch {
            // Droplet lies congruently on the button immediately (button itself hidden) → it lights up and flows off
            editMenu.snapTo(0f)
            editMenuOpen = true
            editBrushFlash.animateTo(1f, androidx.compose.animation.core.tween(BRUSH_FLASH_IN_MS))
            kotlinx.coroutines.delay(BRUSH_FLASH_HOLD_MS)
            launch { editBrushFlash.animateTo(0f, androidx.compose.animation.core.tween(BRUSH_FLASH_OUT_MS)) }
            // Droplet flows into the menu shape in ≈ 0.19 s (video 4: 3.91 → 4.10 s), slight spring-back
            editMenu.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = DROP_DAMPING, stiffness = DROP_STIFFNESS))
        }
    }

    fun closeEditMenu() {
        scope.launch {
            // Duo: droplet withdraws into the brush (same path backwards, a bit faster)
            if (io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle) {
                // [MEASURED video 4] closing ≈ 0.22 s into the button: shrinks quickly at first, then drawn in slowly
                editMenuClosing = true
                editMenu.animateTo(0f, androidx.compose.animation.core.tween(DROP_CLOSE_MS, easing = DROP_CLOSE_EASE))
                editMenuClosing = false
            }
            else editMenu.animateTo(0f, Motion.ctxMenuClose.spec(visibilityThreshold = FOLDER_DONE))
            editMenuOpen = false
        }
    }

    // ── Returning from an app (gesture navigation) ───────────────────────────────
    /** Set by HomeScreen: screen rect of an app's icon on the visible page/in the dock/in the folder. */
    var iconLocator: (android.content.ComponentName, android.os.UserHandle) -> androidx.compose.ui.geometry.Rect? = { _, _ -> null }

    /** Last tapped icon (with several icons of the same app: exactly this one for open + return), otherwise null. */
    var launchedIconKey: String? = null

    /** Opened from the App Library (overview or category): app key + screen rect of the icon there, for the return. */
    var libraryLaunch: Pair<String, androidx.compose.ui.geometry.Rect>? = null
    /** Which copy of the app in the App Library was launched (libraryMenuKey slot). */
    var libraryLaunchSlot: String? = null

    /** Set by HomeScreen: App Library (or open category) is visible right now → return there possible. */
    var libraryVisible: () -> Boolean = { false }

    /** Icon an app window is flying into right now: hidden (the system's icon surface stands in its place). */
    var flyingKey by mutableStateOf<String?>(null)

    /** Set by MainActivity: shows the icon surface and reports position + surface to the system. */
    var flyingSurface: (io.github.janhurn9400.liquidy.data.GestureNavContract, android.graphics.RectF, android.graphics.Bitmap?, () -> Unit, Float?) -> Unit =
        { c, r, _, done, _ -> c.sendEndPosition(r); done() }

    fun onReturnFromApp(contract: io.github.janhurn9400.liquidy.data.GestureNavContract?) {
        holdReturnBlur()
        contract?.let { c ->
            val app = apps.values.firstOrNull { it.component == c.component && it.user == c.user }
            // opened from the App Library and it's still visible → back to the same spot (iOS)
            val lib = libraryLaunch?.takeIf { (k, _) -> k == app?.key && libraryVisible() }?.second
            if (lib != null && app != null) {
                flyingKey = null
                // hide the real App Library icon until the system's icon surface is done — otherwise it stood there visible immediately
                val fk = libraryMenuKey(app.key, libraryLaunchSlot ?: CATEGORY_SLOT)
                LibraryFlying.key = fk
                flyingSurface(c, android.graphics.RectF(lib.left, lib.top, lib.right, lib.bottom), app.icon.asAndroidBitmap(),
                    { if (LibraryFlying.key == fk) LibraryFlying.key = null }, null)
                return@let
            }
            val r = iconLocator(c.component, c.user) ?: return@let
            // Target is a widget (bigger than an icon) → no icon surface, the widget itself stays visible
            // Icon target (page: 60 pt, Duo dock: 48 pt) instead of widget: square and at most icon-sized
            val iconPx = DesignTokens.iconSize.value * io.github.janhurn9400.liquidy.design.UiScale.density(repository.context)
            val isIconSized = kotlin.math.abs(r.width - r.height) < 2f && r.width <= iconPx + 2f
            fun visibleIcon(k: String) = dock.contains(k) || pages.any { k in it } ||
                openFolder?.let { of -> folderFor(of.id)?.apps?.contains(k) } == true
            // tapped icon first, otherwise any icon of this app (several icons of the same app possible)
            val target = app?.key?.let { base ->
                launchedIconKey?.takeIf { appKeyOf(it) == base && visibleIcon(it) }
                    ?: (listOf(base) + pages.flatten() + dock).firstOrNull { appKeyOf(it) == base && visibleIcon(it) }
            }?.takeIf { isIconSized }
            flyingKey = target
            // Widget target: image of the widget as surface, with widget corners (like the dock icon, just widget-sized)
            val widgetKey = io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins[c.component.packageName]?.takeIf { !isIconSized }
            val snap = widgetKey?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetSnapshots[it] }
            flyingKey = target ?: widgetKey?.takeIf { snap != null }
            val corner = if (snap != null) DesignTokens.widgetCorner.value * io.github.janhurn9400.liquidy.design.UiScale.density(repository.context) else null
            val fk = flyingKey
            android.util.Log.i("GestureNavContract", "Return ${c.component.packageName} → $r widget=$widgetKey snap=${snap != null} iconSized=$isIconSized")
            flyingSurface(c, android.graphics.RectF(r.left, r.top, r.right, r.bottom),
                snap ?: if (target != null) app.icon.asAndroidBitmap() else null, { if (flyingKey == fk) flyingKey = null }, corner)
        }
    }

    /** Blur when returning from an app (1 = full, 0 = sharp); held until the home screen has focus. */
    val returnBlur = Animatable(0f)
    private var returnPending = false

    /** Blur when opening an app (Android wallpaper + icons), 0…1. */
    val launchBlur = Animatable(0f)
    private var launchedApp = false

    /** Icon tapped → home screen blurs while the app grows out of the icon. */
    fun onAppLaunching() {
        launchedApp = true
        scope.launch { launchBlur.animateTo(1f, Motion.appOpen.spec(visibilityThreshold = 0.002f)) }
    }

    /**
     * Home screen no longer visible (app is in front). Background back to sharp — Android shows it sharp during the
     * back gesture as well —, the icons stay blurred for the return (they sharpen on appearing).
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

    /** From MainActivity.onWindowFocusChanged: the system's return animation is done → sharpen. */
    /**
     * Unlock zoom (M-UNLOCK): one shared clock (s since the spring started; [UNLOCK_IDLE] = at rest, negative = waiting
     * far away). Every item evaluates the spring itself with its own response (staggered by distance from the centre).
     * Interruptible: prepare/play/cancel restart or stop the clock at any time.
     */
    /**
     * App Library list: how much wider the localised "Cancel" is than the English one the layout was measured with (pt) —
     * "Cancel" and the field's right edge move left by this (iOS shrinks the field for longer words, e.g. "Abbrechen").
     */
    var libCancelShift by androidx.compose.runtime.mutableFloatStateOf(0f)
    /** Window size in px (set by HomeScreen) — centre of the unlock zoom. */
    var screenSize = androidx.compose.ui.geometry.Size.Zero
    var unlockT by androidx.compose.runtime.mutableFloatStateOf(UNLOCK_IDLE)
        private set
    private var unlockJob: kotlinx.coroutines.Job? = null
    /** Screen went off with the home screen in front: everything waits far away, so the first frame after unlocking is already zoomed. */
    fun prepareUnlock() { unlockJob?.cancel(); unlockT = UNLOCK_WAITING }
    fun playUnlock() {
        unlockJob?.cancel()
        unlockJob = scope.launch {
            val lead = Motion.unlockLead.value
            val start = androidx.compose.runtime.withFrameNanos { it }
            unlockT = lead
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val t = lead + (now - start) / 1e9f
                if (t > Motion.unlock.response.value * UNLOCK_SETTLE_RESPONSES) { unlockT = UNLOCK_IDLE; break }
                unlockT = t
            }
        }
    }
    /** Unlocked into another app (or no unlock followed): back to rest without animating. */
    fun cancelUnlock() { unlockJob?.cancel(); unlockT = UNLOCK_IDLE }
    /**
     * Scale of an item whose centre is at ([cx], [cy]) on a [w]×[h] screen; it also moves to centre + scale·(item − centre).
     * Read only in layer/draw lambdas.
     */
    fun unlockScale(cx: Float, cy: Float, w: Float, h: Float): Float {
        val t = unlockT
        if (t == UNLOCK_IDLE) return 1f
        val d0 = Motion.unlockStartDistance.value
        if (t < 0f) return 1f / d0
        val r = kotlin.math.hypot(cx - w / 2, cy - h / 2) / w
        val f = Motion.unlockStaggerBase.value + Motion.unlockStaggerSlope.value * r
        val d = 1f - Motion.unlock.displacement(1f - d0, t, f)
        return 1f / d.coerceAtLeast(UNLOCK_MIN_DISTANCE)
    }

    fun onHomeVisible() {
        smartRotateStacks()
        // App not launched after all (or cancelled): don't stay stuck blurred
        if (launchBlur.value > 0f || launchBlur.isRunning) scope.launch { launchBlur.animateTo(0f, Motion.homeReturn.spec()) }
        launchedApp = false
        if (!returnPending) return
        returnPending = false
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            returnBlur.animateTo(0f, Motion.homeReturn.spec(visibilityThreshold = 0.002f))
        }
    }

    // ── Context menu (M-CTXMENU) ──────────────────────────────────────────────────
    /** Load shortcuts in the background already on touch down (LauncherApps + glyphs), so the menu opens without stutter. */
    private var shortcutPrefetch: Pair<String, kotlinx.coroutines.Deferred<List<io.github.janhurn9400.liquidy.data.AppShortcut>>>? = null

    fun prefetchShortcuts(key: String, density: Float) {
        if (shortcutPrefetch?.first == key) return
        val app = apps[key] ?: return
        val px = (DesignTokens.ctxGlyph.value * density).toInt()
        shortcutPrefetch = key to scope.async(kotlinx.coroutines.Dispatchers.Default) { repository.shortcuts(app, px) }
    }

    /** App dragged out of the App Library: App Library stays visible until the pager has swiped to the last page. */
    var leavingLibrary by mutableStateOf(false)

    fun openContextMenu(key: String, iconTopLeft: Offset, density: Float, widgetRect: androidx.compose.ui.geometry.Rect? = null,
                        fromLibrary: Boolean = false, iconScale: Float = 1f) {
        haptic() // iOS: noticeable when the menu opens
        val res = repository.context
        val items = if (fromLibrary) buildList {
            // App Library (iOS): no "Remove from Home Screen"/"Edit Home Screen" — delete app, info, shortcuts
            val app = apps[key]
            if (app != null) {
                add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.delete_app_menu), MenuGlyph.Remove, destructive = true) { deleteApp(key) })
                add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.app_info), MenuGlyph.Info) { repository.showAppInfo(app, android.graphics.Rect()) })
                val pre = shortcutPrefetch?.takeIf { it.first == key }?.second
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                val list = if (pre != null && pre.isCompleted) pre.getCompleted() else emptyList()
                list.forEach { sc -> add(MenuItem(sc.label, MenuGlyph.Bitmap(sc.glyph)) { repository.startShortcut(sc, android.graphics.Rect()) }) }
            }
        } else buildList {
            val app = apps[key]
            // Folder: no "Remove App"/app info (would open a delete dialog without an app)
            if (app != null) add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.remove_app_menu), MenuGlyph.Remove, destructive = true) { pendingRemoval = key })
            val isStack = widgets[key]?.type == STACK
            // Stack (recording 10-08 10:21, frame 262): Edit Stack · Edit Home Screen · Remove Stack (red, bottom)
            // Order like the other system entries: the outermost (red) first, ContextMenu flips depending on position
            if (isStack) add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.remove_stack), MenuGlyph.Remove, destructive = true) { removeWidget(key) })
            if (isWidget(key) && !isStack) add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.remove_widget), MenuGlyph.Remove, destructive = true) { removeWidget(key) })
            add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.edit_home_screen), MenuGlyph.EditHome) { enterEdit() })
            if (isStack) add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.edit_stack), MenuGlyph.Stack) { openStackEditor(key) })
            if (app != null) {
                add(MenuItem(res.getString(io.github.janhurn9400.liquidy.R.string.app_info), MenuGlyph.Info) {
                    repository.showAppInfo(app, android.graphics.Rect())
                })
                // native app shortcuts (Android), in iOS style — preloaded; if they're still missing, the menu opens without them
                val pre = shortcutPrefetch?.takeIf { it.first == key }?.second
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                val list = if (pre != null && pre.isCompleted) pre.getCompleted() else emptyList()
                list.forEach { sc ->
                    add(MenuItem(sc.label, MenuGlyph.Bitmap(sc.glyph)) { repository.startShortcut(sc, android.graphics.Rect()) })
                }
            }
        }
        shortcutPrefetch = null
        val m = ContextMenuState(key, iconTopLeft, items, widgetRect, if (isWidget(key)) WIDGET_SIZES else emptyList(), iconScale)
        contextMenu = m
        scope.launch { pressAlpha.snapTo(0f); pressedKey = null }
        // Widgets barely lift (large area) — icons as measured 1.15× and 10 pt
        val liftScale = if (widgetRect != null) WIDGET_LIFT else Motion.ctxLiftScale.value
        scope.launch { m.lift.snapTo(pressScale.value); pressScale.snapTo(1f); m.lift.animateTo(liftScale, Motion.ctxLift.spec()) }
        if (widgetRect == null) scope.launch { m.liftY.animateTo(Motion.ctxLiftY.value, Motion.ctxLift.spec()) }
        scope.launch { m.menu.animateTo(1f, Motion.ctxMenu.spec()) }
        scope.launch { m.dim.animateTo(1f, androidx.compose.animation.core.tween(Motion.ctxDimIn.value.roundToInt())) }
    }

    /** Close; [then] runs immediately (e.g. action), the animation keeps running. */
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

    // ── Open / close folder (M-FOLDER) ─────────────────────────────────────
    /** Rename folder (edit mode, capsule above the open folder). Empty → default name on close. */
    var folderFly by mutableStateOf<FolderFly?>(null)
        private set

    /** Visible folder page (fractional while paging); 0 on open. */
    val folderPage = Animatable(0f)
    /** App is being dragged in the open folder right now. */
    var folderDrag by mutableStateOf<FolderDrag?>(null)

    /** Folder that [key] is in (null = none). */
    fun folderContaining(key: String): String? = folders.entries.firstOrNull { key in it.value.apps }?.key

    /** Starts the flight into the folder mini grid; the mini slot stays empty until landing (FolderIconView.hiddenApp). */
    fun flyIntoFolder(fly: FolderFly) {
        folderFly = fly
        scope.launch {
            fly.t.animateTo(1f, Motion.mergeDrop.spec())
            if (folderFly === fly) folderFly = null
        }
    }

    /** Reordered in the open folder: [key] at [index] (list without [key]). */
    fun reorderInFolder(id: String, key: String, index: Int) {
        val f = folders[id] ?: return
        val rest = f.apps - key
        folders = folders + (id to f.copy(apps = rest.toMutableList().apply { add(index.coerceIn(0, size), key) }))
        save()
    }

    /** Folder name is being edited right now (text field has focus, keyboard open). */
    var folderRenaming by mutableStateOf(false)
    /** Tap point on the name field (screen px) → cursor there. */
    var folderRenameTap by mutableStateOf<Offset?>(null)

    fun renameFolder(id: String, name: String) {
        val f = folders[id] ?: return
        folders = folders + (id to f.copy(name = name))
        save()
    }

    fun openFolder(id: String, iconTopLeft: Offset, iconScale: Float = 1f) {
        val f = OpenFolder(id, iconTopLeft, iconScale)
        openFolder = f
        folderDrag = null
        scope.launch { folderPage.snapTo(0f) }
        scope.launch { f.progress.animateTo(1f, Motion.folder.spec()) }
    }

    fun closeFolder() {
        val f = openFolder ?: return
        folders[f.id]?.let { if (it.name.isBlank()) renameFolder(f.id, DEFAULT_FOLDER_NAME) }
        // from a later folder page back to the first — only that one sits as a mini grid in the folder icon
        if (folderPage.value != 0f) scope.launch { folderPage.animateTo(0f, Motion.folder.spec()) }
        // The spring creeps at the end (ζ 1) — a crossfade by progress therefore dragged on for a long time when closing
        // (user 10-14). Instead no crossfade: glass (size, corners, blur, tint) and app icons morph
        // fully into the folder icon, at the end they are swapped invisibly.
        f.closing = true
        scope.launch {
            // fine visibility threshold so no leftover panel "hangs around"
            f.progress.animateTo(0f, Motion.folder.spec(visibilityThreshold = FOLDER_DONE))
            if (openFolder === f) openFolder = null
        }
    }

    /** Crossfade in the smallest phase: 0 = only the closed icon, 1 = only the panel (M-FOLDER.handoff). */
    /** Visibility of the panel (0 = only folder icon): opening with a short crossfade at the start; closing without — the panel
     *  morphs all the way into the folder icon and is swapped (congruently) only at the end. */
    fun folderVisible(f: OpenFolder): Float = if (f.closing) 1f else folderHandoff(f.progress.value)

    fun folderHandoff(p: Float): Float {
        val x = (p / Motion.folderHandoff.value).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Long press on an icon in the open folder: take it out of the folder and keep dragging on the home screen. */
    fun dragOutOfFolder(key: String, pointer: Offset, iconTopLeftScreen: Offset) {
        val f = openFolder ?: return
        if (isLibraryFolder(f.id)) return
        val folder = folders[f.id] ?: return
        val remaining = folder.apps - key
        val page = pages.indexOfFirst { f.id in it }
        folders = if (remaining.isEmpty()) folders - f.id else folders + (f.id to folder.copy(apps = remaining))
        if (remaining.isEmpty()) { pages = pages.map { it - f.id }; dock = dock - f.id }
        // first insert the icon right behind the folder, then drag normally
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
        val d = drag ?: return DisplayLayout(pages.map(GridPlacement::stripGaps), dock.take(dockMax), pages)
        // Origin stays as a gap (current iOS) — the other icons don't move up
        val p = pages.map { pg -> pg.map { if (it == d.key) dragGap else it } }.toMutableList()
        var dk = (dock - d.key).take(dockMax)
        when (val h = d.hover) {
            is DropTarget.Page -> {
                while (p.size <= h.page) p += emptyList<String>()
                p[h.page] = GridPlacement.placeAt(p[h.page], d.key, h.index, ::sizeOf)
                // push overflow (by space in the grid, widgets occupy several cells) to the following pages
                val flowed = GridPlacement.flow(p, ::sizeOf)
                p.clear(); p.addAll(flowed)
            }
            is DropTarget.Dock -> dk = dk.toMutableList().apply { add(h.index.coerceIn(0, size), d.key) }
            null -> Unit
        }
        return DisplayLayout(p.map(GridPlacement::stripGaps), dk, p)
    }

    // ── Page overview ("Edit Pages", screen recording 10-08 09:55) ─────
    var hiddenPages by mutableStateOf<List<io.github.janhurn9400.liquidy.data.HiddenPage>>(emptyList())
        private set
    var pagesOverviewOpen by mutableStateOf(false)
        private set
    /** 0 = closed, 1 = open (cards in the grid). */
    val pagesOverview = Animatable(0f)
    /** Card (overview index) that is zoomed out of on open or zoomed into on close. */
    var overviewFocus by mutableStateOf(0)
        private set

    /**
     * Set by the page overview: current position of the focus card and the page area it corresponds to (both px).
     * The real home screen zooms along with it on open/close (like Duo) — so it always lies congruently under the card.
     */
    /**
     * Visible page in the left screen half on open/close (−1 = Today view). Duo unfolded the page to the right of it (+1)
     * also moves into its card — both visible pages zoom at the same time.
     */
    var overviewLeftPage by mutableStateOf(0)

    /** Per visible page: current card position + screen area of the page (px), set by the overview. */
    // State map: the pages' drawing block must rerun as soon as the overview registers its cards
    val overviewZoom = androidx.compose.runtime.mutableStateMapOf<Int, () -> Pair<androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect>>()

    /** Overview index → visible page number (null = hidden page). */
    fun overviewVisibleIndex(overviewIndex: Int): Int? =
        overviewPages().takeIf { it.getOrNull(overviewIndex)?.visible == true }?.let { visibleIndexOf(overviewIndex) }

    /** Requested from the edit menu (HomeScreen knows the current page). */
    var pagesOverviewRequested by mutableStateOf(false)

    data class OverviewPage(val items: List<String>, val visible: Boolean)

    /** Visible and hidden pages in overview order. */
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

    /** Close: zooms into [toIndex] (overview index of a visible page) and reports its page number. */
    fun closePagesOverview(toIndex: Int = overviewFocus, onPage: (Int) -> Unit) {
        if (!pagesOverviewOpen) return
        val ov = overviewPages()
        val target = toIndex.takeIf { ov.getOrNull(it)?.visible == true } ?: ov.indexOfFirst { it.visible }.coerceAtLeast(0)
        overviewFocus = target
        scope.launch {
            pagesOverview.animateTo(0f, Motion.pagesOverview.spec(visibilityThreshold = OVERVIEW_SETTLE)) // settle finely: the default threshold 0.01 jumped at the end
            pagesOverviewOpen = false
        }
        onPage(visibleIndexOf(target))
    }

    /** Checkmark: show/hide page (at least one page stays visible). */
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

    /** Move a card in the overview (visible and hidden pages together). */
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

    /** Minus on a hidden page: remove page (apps stay in the App Library). */
    fun deleteHiddenPage(index: Int) {
        if (hiddenPages.none { it.pos == index }) return
        hiddenPages = hiddenPages.filterNot { it.pos == index }.map { if (it.pos > index) it.copy(pos = it.pos - 1) else it }
        save()
    }

    // ── Search ────────────────────────────────────────────────────────────────
    /** 0 = "Search" pill, 1 = search field (M-SEARCH-MORPH). */
    val searchMorph = Animatable(0f)
    private var morphTarget = 0f
    /** h on release — the field stays at this height until the keyboard reaches it. */
    var searchReleaseH by mutableStateOf(0f)
        private set

    /** true as soon as the pill morphs into the search field (already while dragging) → keyboard peeks out. */
    var searchMorphOn by mutableStateOf(false)
        private set

    private fun morphTo(target: Float, spring: io.github.janhurn9400.liquidy.motion.IosSpring = Motion.searchMorph) {
        if (morphTarget == target) return
        morphTarget = target
        searchMorphOn = target > 0.5f
        scope.launch { searchMorph.animateTo(target, spring.spec()) }
    }

    /** Offset of the suggestions block (pt) — runs 1:1 with h while dragging, springs to 0 on open (M-SEARCH). */
    val searchBlockOffset = Animatable(0f)

    /** While dragging: h follows the finger. Nothing opens (measured). */
    fun searchPull(h: Float) {
        val v = h.coerceAtLeast(0f)
        scope.launch { searchProgress.snapTo(v) }
        scope.launch { searchBlockOffset.snapTo(v + Motion.searchBlockBase.value) }
        if (v >= Motion.searchMorphOn.value) morphTo(1f) else if (v < Motion.searchMorphOff.value) morphTo(0f)
    }

    /** Release: open from M-SEARCH.commit, otherwise back. */
    fun searchRelease() {
        if (searchProgress.value >= Motion.searchCommit.value) openSearch() else closeSearch()
    }

    /** Duo magnifier: lights up on tap (0…1), then search opens. */
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
        // measured: home screen springs back up (0 pt), but stays shrunk + dimmed
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

    /** Visible while dragging or while the open state is still settling. */
    val searchVisible: Boolean get() = searchOpen || searchProgress.value > 0f || searchOpenAmount.value > 0.001f || searchMorph.value > 0.001f

    /** Home screen scale: smaller per pt while dragging, open T-SEARCH.openScale. */
    fun searchContentScale(): Float {
        val o = searchOpenAmount.value.coerceIn(0f, 1f)
        val pull = 1f - DesignTokens.searchScalePerPt.value * searchProgress.value
        return pull * (1f - o) + DesignTokens.searchOpenScale.value * o
    }

    fun searchDim(): Float {
        val pull = DesignTokens.searchDimPerPt.value * searchProgress.value
        val open = DesignTokens.searchDimMax.value * searchOpenAmount.value
        // Opening after release: pull dim falls while open dim rises on the same spring → their max dipped to ~half halfway
        // (background flashed brighter, user 10-18). Blend from the dim at release to full instead — monotonic.
        val handoff = if (searchOpen) {
            val r = (DesignTokens.searchDimPerPt.value * searchReleaseH).coerceAtMost(DesignTokens.searchDimMax.value)
            r + (DesignTokens.searchDimMax.value - r) * searchOpenAmount.value.coerceIn(0f, 1f)
        } else 0f
        return maxOf(pull, open, handoff).coerceIn(0f, DesignTokens.searchDimMax.value)
    }

    private fun smooth(h: Float, a: Float, b: Float): Float {
        val x = ((h - a) / (b - a)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Blur share of the suggestions block 1 → 0 (M-SEARCH-BLOCK.sharp*); sharp when open. */
    fun searchBlockBlurFraction(): Float {
        fun pull(h: Float) = 1f - smooth(h, Motion.searchBlockSharpStart.value, Motion.searchBlockSharpEnd.value)
        // opening after release: sharpen from the blur at release instead of jumping to sharp in one frame (icons flashed)
        return if (searchOpen) pull(searchReleaseH) * (1f - searchOpenAmount.value.coerceIn(0f, 1f)) else pull(searchProgress.value)
    }

    /** Visibility of the suggestions block 0…1 (smoothstep over M-SEARCH-BLOCK.fadeStart…End); always 1 when open. */
    fun searchBlockAlpha(): Float {
        val h = searchProgress.value
        val x = ((h - Motion.searchBlockFadeStart.value) / (Motion.searchBlockFadeEnd.value - Motion.searchBlockFadeStart.value)).coerceIn(0f, 1f)
        val open = searchOpenAmount.value.coerceIn(0f, 1f)
        // opening after release: the falling pull fade and the rising open fade made their max dip halfway (icons flickered)
        val handoff = if (searchOpen) {
            val r = ((searchReleaseH - Motion.searchBlockFadeStart.value) / (Motion.searchBlockFadeEnd.value - Motion.searchBlockFadeStart.value))
                .coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            r + (1f - r) * open
        } else 0f
        return maxOf(x * x * (3f - 2f * x), open, handoff)
    }

    private val suggester = io.github.janhurn9400.liquidy.data.AppSuggestions(repository.context)
    /** App keys by suggestion score (frequency, time of day, recency), computed in the background. */
    private var ranked by mutableStateOf<List<String>>(emptyList())
    private var rankJob: Job? = null

    fun hasUsageAccess() = suggester.hasUsageAccess()

    /** Re-score — when returning to the home screen and after every launch (time of day/usage change). */
    fun refreshSuggestions() {
        val snapshot = apps
        if (snapshot.isEmpty()) return
        rankJob?.cancel()
        rankJob = scope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { suggester.rank(snapshot) }
            ranked = r
        }
    }

    /** App suggestions: scored apps, then recently launched, then filled up alphabetically. */
    fun suggestions(count: Int): List<AppEntry> =
        (ranked.mapNotNull { apps[it] } + recents.mapNotNull { apps[it] } + apps.values.sortedBy { it.label.lowercase() })
            .distinct().take(count)

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
        suggester.record(key)
    }

    // ── Removing ─────────────────────────────────────────────────────────────
    fun removeFromHome(key: String) {
        pages = pagesWithHole(key)
        dock = dock - key
        folders = folders.mapValues { (_, f) -> f.copy(apps = f.apps - key) }
        // "hidden" applies per app — only if no other icon of this app is on the home screen anymore
        if (!onHome(appKeyOf(key))) hidden = hidden + appKeyOf(key)
        pruneEmptyPages()
        save()
    }

    /** Pages without [key]; a gap stays in its place (free placement — the others don't move up). */
    private fun pagesWithHole(key: String): List<List<String>> =
        pages.map { pg -> GridPlacement.trim(pg.map { if (it == key) GridPlacement.newGap() else it }) }

    /**
     * When dragging to the left edge of the first page: new empty page in front (like on the right after the last).
     * Hover targets move along. @return false if there is already an empty page at the front.
     */
    fun insertPageFront(): Boolean {
        val d = drag ?: return false
        if (pages.firstOrNull()?.let(GridPlacement::isBlank) == true) return false
        pages = listOf(emptyList<String>()) + pages
        (d.hover as? DropTarget.Page)?.let { d.hover = it.copy(page = it.page + 1) }
        hoverJob?.cancel(); pendingHover = null
        return true
    }

    /** Is any icon of app [appKey] on the home screen (pages, dock, folders)? */
    fun onHome(appKey: String): Boolean {
        fun hit(k: String) = appKeyOf(k) == appKey
        return pages.any { pg -> pg.any(::hit) } || dock.any(::hit) || folders.values.any { f -> f.apps.any(::hit) }
    }

    /** Key for a new icon of the app from the App Library: if it's already on the home screen → another icon. */
    fun newIconKey(appKey: String): String =
        if (onHome(appKey)) appKey + INSTANCE_SEP + java.util.UUID.randomUUID().toString().take(8) else appKey

    /** Dock slots: 4 like iOS; Duo with "More Apps in Dock" as many as fit into the column — set by HomeScreen.
     *  If there are more apps in the dock (layout changes, e.g. tablet in portrait), they stay saved and are just not shown. */
    var dockMax by mutableStateOf(DOCK_MAX)

    /** "More Apps in Dock" turned off: apps beyond [max] move to the end of the home screen instead of disappearing. */
    fun trimDock(max: Int) {
        if (dock.size <= max) return
        val p = pages.toMutableList()
        dock.drop(max).forEach { key ->
            if (p.isEmpty() || !GridPlacement.fits(p.last() + key, ::sizeOf)) p += listOf(key) else p[p.lastIndex] = p.last() + key
        }
        pages = p; dock = dock.take(max)
        save()
    }

    /** Does the dock accept [key]? (free slot or already in it; never widgets) */
    fun dockAccepts(key: String): Boolean = !isWidget(key) && (key in dock || dock.size < dockMax)

    /** Remove empty pages immediately (not while dragging — then the empty page at the end is the drop target). */
    fun pruneEmptyPages() {
        if (drag != null) return
        if (pages.any(GridPlacement::isBlank)) pages = pages.filterNot(GridPlacement::isBlank).ifEmpty { listOf(emptyList()) }
    }

    fun deleteApp(key: String) {
        apps[key]?.let { repository.uninstall(it) }
    }

    fun isSystemApp(key: String) = apps[key]?.let { repository.isSystemApp(it) } ?: true

    companion object {
        const val PER_PAGE = DesignTokens.GRID_COLUMNS * DesignTokens.GRID_ROWS
        const val DOCK_MAX = DesignTokens.DOCK_SLOTS
        const val RECENTS_MAX = 8
        const val SMART_ROTATE_DELAY_MS = 700L
        const val EDIT_BUTTONS_DELAY_MS = 90L
        const val WIDGET_CONFIG_OPEN_MS = 620
        const val WIDGET_CONFIG_CLOSE_MS = 550
        const val SMART_ROTATE_QUIET_MS = 15 * 60_000L
        const val FOLDER_DONE = 0.001f
        const val DEFAULT_FOLDER_NAME = "Folder"
        const val DUO_LIB_TILES_OUT_MS = 220f
        const val DUO_LIB_LIST_IN_MS = 180f
        const val DUO_LIB_LIST_DELAY_MS = 100f
    }
}

/** Run-out threshold of the page overview (share 0…1): card glides into place down to fractions of a pixel. */
private const val OVERVIEW_SETTLE = 0.0005f

private const val UNLOCK_MIN_DISTANCE = 0.01f
private const val UNLOCK_IDLE = Float.MAX_VALUE
private const val UNLOCK_WAITING = -1f
/** Clock stops after this many base responses (the slowest corner item is ≈ 1.6× slower and at rest by then). */
private const val UNLOCK_SETTLE_RESPONSES = 3f
