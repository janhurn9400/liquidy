package io.github.janhurn9400.liquidy.data

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.janhurn9400.liquidy.R

/**
 * Screen recording for bug reports (Settings › Advanced › Report a Problem): records the screen while the user reproduces
 * the problem (MediaProjection, the system asks for consent each time), at most [MAX_MS] and small enough for GitHub's
 * 10 MB attachment limit. Saved to Movies/Liquidy. Stopping (notification button) opens the prefilled GitHub issue with a
 * note to attach the video — GitHub can't receive files through the issue link, the user picks the file in the form.
 * Nothing is uploaded by the app.
 */
class BugRecorder : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var uri: Uri? = null
    private var name: String? = null
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish()
            else -> {
                val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
                @Suppress("DEPRECATION") val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
                if (data == null || !start(code, data)) { stopSelf(); recording = false }
            }
        }
        return START_NOT_STICKY
    }

    private fun start(code: Int, data: Intent): Boolean = runCatching {
        channel(this)
        // the foreground service must be running before the projection is created (Android 14)
        startForeground(NOTIFICATION_ID, runningNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data) ?: return false
        projection = p
        p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { finish() } }, Handler(Looper.getMainLooper()))

        val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bounds.width(), bounds.height()))
        val w = (bounds.width() * scale).toInt() and 1.inv(); val h = (bounds.height() * scale).toInt() and 1.inv()

        val n = "liquidy-bug-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, n)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val u = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        uri = u; name = n
        val fd = contentResolver.openFileDescriptor(u, "w") ?: return false

        val r = MediaRecorder(this).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(w, h)
            setVideoFrameRate(FPS)
            setVideoEncodingBitRate(BITRATE)
            setMaxDuration(MAX_MS)
            setOutputFile(fd.fileDescriptor)
            setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finish() }
            prepare()
        }
        recorder = r
        display = p.createVirtualDisplay("Liquidy bug report", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
        r.start()
        recording = true
        true
    }.getOrElse { android.util.Log.w(TAG, "start", it); cleanUp(keep = false); false }

    /** Stops and saves; afterwards [onFinished] (if set, e.g. by [BugReportActivity]) or a "tap to report" notification. */
    private fun finish() {
        if (finished) return
        finished = true
        val ok = runCatching { recorder?.stop() }.isSuccess
        cleanUp(keep = ok)
        recording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        val n = name
        if (ok && n != null) {
            lastVideo = n
            lastVideoUri = uri
            val cb = onFinished
            onFinished = null
            if (cb != null) cb(n) else getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, doneNotification())
        }
        stopSelf()
    }

    private fun cleanUp(keep: Boolean) {
        runCatching { recorder?.release() }; recorder = null
        runCatching { display?.release() }; display = null
        runCatching { projection?.stop() }; projection = null
        uri?.let { u ->
            if (keep) contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            else runCatching { contentResolver.delete(u, null, null) }
        }
    }

    private fun runningNotification(): Notification {
        val stop = PendingIntent.getActivity(this, 1, Intent(this, BugReportActivity::class.java).setAction(BugReportActivity.ACTION_STOP_AND_REPORT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.bugrec_running))
            .setContentText(getString(R.string.bugrec_running_text))
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(stop)
            .addAction(Notification.Action.Builder(null, getString(R.string.bugrec_stop), stop).build())
            .build()
    }

    private fun doneNotification(): Notification {
        val open = PendingIntent.getActivity(this, 2, Intent(this, BugReportActivity::class.java).setAction(BugReportActivity.ACTION_REPORT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.bugrec_done))
            .setContentText(getString(R.string.bugrec_done_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        /** True while recording (the report page shows "Recording…" instead of the start row). */
        var recording by mutableStateOf(false)
            private set
        /** File name of the last finished recording (for the issue text). */
        var lastVideo: String? = null
            private set
        /** MediaStore entry of the last finished recording (attached to the email report). */
        var lastVideoUri: Uri? = null
            private set
        /** Called on the main thread with the file name when a recording has been saved. */
        var onFinished: ((String) -> Unit)? = null

        const val FOLDER = "Liquidy"
        private const val TAG = "BugRecorder"
        private const val CHANNEL = "bug_recording"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "io.github.janhurn9400.liquidy.BUG_RECORDER_STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val MAX_MS = 60_000      // one minute is plenty to show a bug …
        private const val BITRATE = 1_200_000  // … and stays below GitHub's 10 MB attachment limit (≈ 9 MB)
        private const val FPS = 30
        private const val MAX_SIDE = 1280      // longer side in px

        fun channel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.bugrec_channel), NotificationManager.IMPORTANCE_LOW))
        }

        /** Starts recording with the consent result from MediaProjectionManager.createScreenCaptureIntent(). */
        fun start(context: Context, code: Int, data: Intent) {
            context.startForegroundService(Intent(context, BugRecorder::class.java).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BugRecorder::class.java).setAction(ACTION_STOP))
        }

        /** Issue intro with a note to attach the video [file], placed before the device details paragraph. */
        fun introWithVideo(context: Context, intro: String, file: String): String {
            val hint = context.getString(R.string.settings_report_video_hint, file)
            val i = intro.lastIndexOf("\n\n**")
            return if (i < 0) intro + "\n\n" + hint else intro.substring(0, i) + "\n\n" + hint + intro.substring(i)
        }
    }
}

/**
 * Invisible step between the "Stop" notification button and the browser: stops the recording, waits until the video is
 * saved and opens the prefilled GitHub issue (an activity may open the browser, a background service may not).
 */
class BugReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            ACTION_STOP_AND_REPORT -> if (BugRecorder.recording) {
                BugRecorder.onFinished = { choose() }
                BugRecorder.stop(this)
            } else choose()
            else -> choose()
        }
    }

    /** Email (video attached, for people without GitHub) or GitHub issue (video to attach in the form). */
    private fun choose() {
        val intro = getString(R.string.settings_report_template)
        if (!Diagnostics.emailEnabled) { // no report address yet → straight to GitHub
            runCatching {
                val text = BugRecorder.lastVideo?.let { BugRecorder.introWithVideo(this, intro, it) } ?: intro
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Diagnostics.issueUrl(this, text))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            finish(); return
        }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(R.string.report_choose_title)
            .setItems(arrayOf(getString(R.string.report_choose_email), getString(R.string.settings_report_github))) { _, which ->
                runCatching {
                    if (which == 0) startActivity(Diagnostics.emailIntent(this, intro, BugRecorder.lastVideoUri))
                    else {
                        val text = BugRecorder.lastVideo?.let { BugRecorder.introWithVideo(this, intro, it) } ?: intro
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Diagnostics.issueUrl(this, text))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    companion object {
        const val ACTION_STOP_AND_REPORT = "io.github.janhurn9400.liquidy.REPORT_STOP"
        const val ACTION_REPORT = "io.github.janhurn9400.liquidy.REPORT_OPEN"
    }
}
