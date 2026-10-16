package io.github.janhurn9400.liquidy.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator

/** App shortcut from Android (LauncherApps) — in the iOS menu as a monochrome glyph + text. */
class AppShortcut(val label: String, val glyph: ImageBitmap?, val info: android.content.pm.ShortcutInfo)

data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** ApplicationInfo.category (for folder names + App Library). */
    val category: Int = android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED,
    val installTime: Long = 0L,
)

/**
 * Layers for Dark/Clear/Tinted — computed only when needed (≈ 2 bitmaps per app) and stored as Compose state,
 * so the icons redraw as soon as their layers are done.
 */
object IconLayerCache {
    val map = androidx.compose.runtime.mutableStateMapOf<String, IconLayers>()
}

/**
 * App list via LauncherApps (like Launcher3), incl. a callback on install/uninstall.
 * The layout (pages/dock/folders) is managed by HomeModel + LayoutStore.
 */
class AppRepository(val context: Context, private val iconSizePx: Int) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    /** null = not loaded yet. Order: alphabetical. */
    val apps: StateFlow<List<AppEntry>?> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    fun start() {
        IconPacks.load(context)
        IconPacks.onChanged = { IconLayerCache.map.clear(); reload() }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun stop() = launcherApps.unregisterCallback(callback)

    fun reload() {
        scope.launch {
            val own = context.packageName
            val apps = userManager.userProfiles.flatMap { user ->
                launcherApps.getActivityList(null, user)
                    .filter { it.componentName.packageName != own }
                    .map { info ->
                        AppEntry(
                            key = info.componentName.flattenToShortString() + "#" + userManager.getSerialNumberForUser(user),
                            label = info.label.toString(),
                            component = info.componentName,
                            user = user,
                            icon = info.getIcon(0).let { orig ->
                                val pack = runCatching { IconPacks.icon(context, info.componentName, orig, iconSizePx) }.getOrNull()
                                IconRenderer.render(pack ?: orig, iconSizePx, fullBleed = pack != null)
                            },
                            category = info.applicationInfo.category,
                            installTime = runCatching { context.packageManager.getPackageInfo(info.componentName.packageName, 0).firstInstallTime }.getOrDefault(0L),
                        )
                    }
            }
            val collator = Collator.getInstance()
            _apps.value = apps.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }

    /** Package name heuristic for an iOS-like default dock: phone, messages, browser, music/camera. */
    fun defaultDock(apps: List<AppEntry>): List<AppEntry> {
        val dock = mutableListOf<AppEntry>()
        listOf("dialer", "messaging", "chrome", "music", "camera").forEach { hint ->
            if (dock.size < 4) apps.firstOrNull { it.component.packageName.contains(hint) && it !in dock }?.let { dock += it }
        }
        return dock
    }

    /**
     * Folder name like on iOS from the app category (iOS: App Store category, Android: ApplicationInfo.category).
     * Both apps of the same category → its name, otherwise that of the target icon; without a category "Folder".
     */
    fun folderName(target: AppEntry?, dragged: AppEntry?): String {
        val undefined = android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED
        val c = target?.category?.takeIf { it != undefined } ?: dragged?.category ?: undefined
        return categoryName(c)
    }

    fun categoryName(c: Int): String {
        val res = when (c) {
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> io.github.janhurn9400.liquidy.R.string.folder_games
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> io.github.janhurn9400.liquidy.R.string.folder_music
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> io.github.janhurn9400.liquidy.R.string.folder_entertainment
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> io.github.janhurn9400.liquidy.R.string.folder_photo
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> io.github.janhurn9400.liquidy.R.string.folder_social
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> io.github.janhurn9400.liquidy.R.string.folder_news
            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> io.github.janhurn9400.liquidy.R.string.folder_navigation
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> io.github.janhurn9400.liquidy.R.string.folder_productivity
            android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY -> io.github.janhurn9400.liquidy.R.string.folder_utilities
            else -> io.github.janhurn9400.liquidy.R.string.folder_default
        }
        return context.getString(res)
    }

    fun uninstall(app: AppEntry) {
        val intent = android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.fromParts("package", app.component.packageName, null))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isSystemApp(app: AppEntry): Boolean = runCatching {
        val ai = context.packageManager.getApplicationInfo(app.component.packageName, 0)
        (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(true)

    /**
     * Computes missing style layers in the background (at display size, not at the 1.5× icon resolution)
     * and stores them piece by piece in the cache. Called only when a style other than Default is active.
     */
    suspend fun ensureLayers(apps: Collection<AppEntry>) = kotlinx.coroutines.withContext(Dispatchers.Default) {
        val px = (iconSizePx / ICON_OVERSAMPLE).toInt()
        for (app in apps) {
            if (IconLayerCache.map.containsKey(app.key)) continue
            val info = runCatching {
                launcherApps.resolveActivity(android.content.Intent().setComponent(app.component), app.user)
            }.getOrNull() ?: continue
            val layers = runCatching {
                val orig = info.getIcon(0)
                val pack = IconPacks.icon(context, info.componentName, orig, px)
                // monochrome glyph: the pack's drawn one, else the app's own themed icon (Android 13), else computed
                val designed = IconPacks.mono(context, info.componentName)
                    ?: (orig as? android.graphics.drawable.AdaptiveIconDrawable)?.takeIf { it.monochrome != null }
                IconRenderer.layers(pack ?: orig, px, fullBleed = pack != null, designedMono = designed)
            }.getOrNull() ?: continue
            kotlinx.coroutines.withContext(Dispatchers.Main) { IconLayerCache.map[app.key] = layers }
        }
    }

    /**
     * The native app shortcuts (manifest + dynamic), as Launcher3 shows them on long press: by rank, max. [MAX_SHORTCUTS].
     * Only available if we're the default launcher (hasShortcutHostPermission).
     */
    fun shortcuts(app: AppEntry, glyphPx: Int): List<AppShortcut> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.component.packageName)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        val list = runCatching { launcherApps.getShortcuts(q, app.user) }.getOrNull().orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
            .take(MAX_SHORTCUTS)
        return list.map { s ->
            val label = (s.shortLabel ?: s.longLabel ?: "").toString()
            val glyph = runCatching { launcherApps.getShortcutIconDrawable(s, context.resources.displayMetrics.densityDpi) }
                .getOrNull()?.let { IconRenderer.glyph(it, glyphPx) }
            AppShortcut(label, glyph, s)
        }
    }

    fun startShortcut(s: AppShortcut, sourceBounds: Rect) {
        runCatching { launcherApps.startShortcut(s.info, sourceBounds, null) }
    }

    fun showAppInfo(app: AppEntry, sourceBounds: Rect) {
        launcherApps.startAppDetailsActivity(app.component, app.user, sourceBounds, null)
    }

    fun launch(app: AppEntry, sourceBounds: Rect, options: Bundle?) {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, options)
    }

    companion object {
        const val MAX_SHORTCUTS = 4
        /** MainActivity renders icons at 1.5× size (for enlarging); style layers are fine at display size. */
        const val ICON_OVERSAMPLE = 1.5f
    } // Launcher3: at most 4 shortcuts in the popup
}
