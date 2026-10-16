package io.github.janhurn9400.liquidy.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Diagnostics for bug reports (Launcher settings › Advanced): device, Android, displays, wallpaper model, states.
 * Technical only — no apps, contacts or other user data. Only copied to the clipboard, never sent.
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
        val wpInfo = android.app.WallpaperManager.getInstance(context).wallpaperInfo // null = static image
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
     * New GitHub issue with template + diagnostics in the text field (GitHub prefills title/body from the URL). The user sees
     * everything before submitting and can change it; it's only sent when they press "Submit" on GitHub.
     */
    fun issueUrl(context: Context, intro: String): String {
        val body = "$intro\n\n```\n${report(context)}\n```"
        return ISSUES_NEW + "?labels=bug&body=" + java.net.URLEncoder.encode(body, "UTF-8").replace("+", "%20")
    }

    private const val ISSUES_NEW = "https://github.com/janhurn9400/liquidy/issues/new"

    /** Where email bug reports go. TODO: placeholder — replace with the project's own address once it exists. */
    const val REPORT_EMAIL = "liquidy.reports@example.com"

    /**
     * Bug report by email for people who don't use GitHub: only mail apps are offered (the apps that handle mailto:,
     * each targeted with the attachment-capable ACTION_SEND — the chooser ignores a mailto selector), recipient, subject
     * and the diagnostics are prefilled and a recorded [video] is attached directly. Sent only when the user taps Send.
     */
    fun emailIntent(context: Context, intro: String, video: android.net.Uri? = null): android.content.Intent {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(io.github.janhurn9400.liquidy.R.string.report_email_subject))
            putExtra(android.content.Intent.EXTRA_TEXT, intro + "\n\n" + report(context))
            if (video != null) {
                type = "video/mp4"
                putExtra(android.content.Intent.EXTRA_STREAM, video)
                clipData = android.content.ClipData.newRawUri("video", video)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else type = "text/plain"
        }
        val mailto = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + REPORT_EMAIL))
        val apps = context.packageManager.queryIntentActivities(mailto, 0).map { it.activityInfo.packageName }.distinct()
        if (apps.isEmpty()) return mailto // no mail app found: let the system decide (text only)
        val perApp = apps.map { android.content.Intent(send).setPackage(it) }
        if (perApp.size == 1) return perApp[0]
        return android.content.Intent.createChooser(perApp[0], context.getString(io.github.janhurn9400.liquidy.R.string.report_email_chooser))
            .putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS, perApp.drop(1).toTypedArray())
    }

    fun copy(context: Context) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Liquidy diagnostics", report(context)))
    }
}
