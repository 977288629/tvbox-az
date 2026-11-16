package com.github.avbox.osc.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.github.avbox.osc.R
import com.github.avbox.osc.data.AppGraph
import com.github.avbox.osc.data.DownloadState
import com.github.avbox.osc.sourcedata.SourcePlayUrlResolver
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DownloadService : Service() {

    companion object {
        const val ACTION_START = "download.start"
        const val ACTION_PAUSE_ALL = "download.pause_all"
        const val ACTION_RESUME_ALL = "download.resume_all"
        const val ACTION_CLEAR = "download.clear"
        const val ACTION_WIFI_CHANGED = "download.wifi_changed"
        private const val ChannelId = "avbox_download"
        private const val NotificationId = 2026

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun action(context: Context, action: String) {
            val intent = Intent(context, DownloadService::class.java).setAction(action)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: kotlinx.coroutines.Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    private fun text(resId: Int): String {
        val app: Context? = applicationContext
        return if (app == null) getString(resId) else LanguageManager.localized(app).getString(resId)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        PlayUrlResolverHolder.resolver = SourcePlayUrlResolver()
        DownloadManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        when (intent?.action) {
            ACTION_START, null -> Unit
            ACTION_PAUSE_ALL -> pauseAll()
            ACTION_RESUME_ALL -> resumeAll()
            ACTION_CLEAR -> DownloadManager.clearAll()
            ACTION_WIFI_CHANGED -> DownloadManager.waitForWifiChanged()
        }
        observeIfNeeded()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pauseAll() {
        scope.launch(Dispatchers.IO) {
            AppGraph.downloadRepository.getAll()
                .filter { it.status() == DownloadState.DOWNLOADING || it.status() == DownloadState.WAITING }
                .forEach { DownloadManager.pause(it.id) }
        }
    }

    private fun resumeAll() {
        scope.launch(Dispatchers.IO) {
            AppGraph.downloadRepository.getAll()
                .filter { it.status() == DownloadState.PAUSED }
                .forEach { DownloadManager.resume(it.id) }
        }
    }

    private fun observeIfNeeded() {
        if (observeJob != null) return
        observeJob = scope.launch {
            combine(AppGraph.downloadRepository.observeAll(), DownloadManager.state) { tasks, ui ->
                val pending = tasks.count { isPending(it.status()) }
                Pair(tasks.size, Pair(pending, ui))
            }.collect { (total, rest) ->
                val (pending, ui) = rest
                updateNotification(total, pending, ui.activeCount, ui.speedBps, ui.message)
            }
        }
    }

    private fun startForegroundSafely() {
        try {
            startForeground(NotificationId, buildNotification(0, 0, 0, ""))
        } catch (throwable: Throwable) {
            AppLog.d("DownloadService", "startForeground failed: ${throwable.message}")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ChannelId) != null) return
        val channel = NotificationChannel(ChannelId, text(R.string.download_title), NotificationManager.IMPORTANCE_LOW)
        channel.description = text(R.string.download_title)
        manager.createNotificationChannel(channel)
    }

    private fun isPending(state: DownloadState): Boolean = when (state) {
        DownloadState.WAITING, DownloadState.PREPARING, DownloadState.DOWNLOADING, DownloadState.MERGING -> true
        else -> false
    }

    private fun updateNotification(total: Int, pending: Int, active: Int, speed: Long, message: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (active <= 0 && pending <= 0) {
            manager.cancel(NotificationId)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return
        }
        manager.notify(NotificationId, buildNotification(total, active, speed, message))
    }

    private fun buildNotification(total: Int, active: Int, speed: Long, message: String): Notification {
        val title = text(R.string.download_title)
        val content = if (message == "waiting_wifi") {
            text(R.string.download_waiting_wifi)
        } else {
            val speedText = if (speed > 0) formatSpeed(speed) else ""
            text(R.string.download_notification_summary).format(total, active, speedText)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    private fun formatSpeed(bytes: Long): String {
        val kb = bytes / 1024L
        return if (kb >= 1024L) "${kb / 1024L}MB/s" else "${kb}KB/s"
    }

    override fun onDestroy() {
        observeJob?.cancel()
        super.onDestroy()
    }
}
