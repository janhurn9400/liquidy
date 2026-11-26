package io.github.janhurn9400.liquidy.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.quicksettings.TileService
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android Quick Settings tiles of installed apps (TileService), offered as Control Center controls. Listing them is
 * public; clicking one goes through SystemUI (`cmd statusbar click-tile`, root) since binding a TileService needs a
 * system permission. Their on/off state is not readable from outside, so they show as plain buttons.
 */
object QsTiles {
    data class Tile(val component: ComponentName, val label: String, val appLabel: String, val icon: ImageBitmap?) {
        val id get() = PREFIX + component.flattenToString()
    }

    const val PREFIX = "tile:"
    private var cache: List<Tile>? = null

    fun list(context: Context): List<Tile> = cache ?: load(context).also { cache = it }

    fun find(context: Context, id: String): Tile? = list(context).firstOrNull { it.id == id }

    private fun load(context: Context): List<Tile> {
        val pm = context.packageManager
        val res = runCatching {
            pm.queryIntentServices(Intent(TileService.ACTION_QS_TILE), PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        }.getOrDefault(emptyList())
        return res.mapNotNull { r ->
            val si = r.serviceInfo ?: return@mapNotNull null
            if (si.packageName == context.packageName) return@mapNotNull null
            val label = si.loadLabel(pm).toString()
            val app = runCatching { si.applicationInfo.loadLabel(pm).toString() }.getOrDefault(si.packageName)
            val icon = runCatching { si.loadIcon(pm) }.getOrNull()?.let { d ->
                val s = 96
                val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, s, s); d.draw(Canvas(b)); b.asImageBitmap()
            }
            Tile(ComponentName(si.packageName, si.name), label, app, icon)
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    /** Taps the tile like in the Android panel (root); false when root is unavailable. */
    fun click(tile: Tile): Boolean {
        if (RootShell.available.value == false) return false
        RootShell.run("cmd statusbar click-tile ${tile.component.flattenToString()}")
        return true
    }
}
