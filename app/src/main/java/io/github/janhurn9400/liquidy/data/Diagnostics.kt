package io.github.janhurn9400.liquidy.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Diagnose für Bug-Reports (Launcher-Einstellungen › Erweitert): Gerät, Android, Displays, Wallpaper-Modell, Zustände.
 * Nur Technik — keine Apps, Kontakte oder sonstige Nutzerdaten. Wird nur in die Zwischenablage kopiert, nie verschickt.
 */
object Diagnostics {
    fun report(context: Context): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val m = context.resources.displayMetrics
        val wm = context.getSystemService(android.view.WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds
        val blur = runCatching { wm.isCrossWindowBlurEnabled }.getOrNull()
        val module = context.checkSelfPermission("android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val wpInfo = android.app.WallpaperManager.getInstance(context).wallpaperInfo // null = statisches Bild
        return buildString {
            appendLine("Liquidy ${info?.versionName} (${info?.longVersionCode})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
            appendLine("Window: ${bounds.width()}x${bounds.height()} px · density ${m.density}")
            appendLine("Foldable: ${FoldableStyle.isFoldable(context)} · Duo style: ${FoldableStyle.duoStyle}")
            appendLine(io.github.janhurn9400.liquidy.glass.WallpaperCropModel.describe(context, bounds.width(), bounds.height()))
            appendLine("Wallpaper: ${if (wpInfo == null) "static" else "live (${wpInfo.packageName})"} · file access: ${io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead()}")
            appendLine("System blur available: $blur · system module: $module")
            append("Glass: ${io.github.janhurn9400.liquidy.glass.GlassClarity.amount} · quirks: freeWidgetRows=${Quirks.freeWidgetRows}, moreDock=${FoldableStyle.moreDockApps}")
        }
    }

    /**
     * Neues GitHub-Issue mit Vorlage + Diagnose im Textfeld (GitHub füllt title/body aus der Adresse vor). Der Nutzer sieht
     * alles vor dem Absenden und kann es ändern; gesendet wird erst, wenn er auf GitHub „Submit“ drückt.
     */
    fun issueUrl(context: Context, intro: String): String {
        val body = "$intro\n\n```\n${report(context)}\n```"
        return ISSUES_NEW + "?labels=bug&body=" + java.net.URLEncoder.encode(body, "UTF-8").replace("+", "%20")
    }

    private const val ISSUES_NEW = "https://github.com/janhurn9400/liquidy/issues/new"

    fun copy(context: Context) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Liquidy diagnostics", report(context)))
    }
}
