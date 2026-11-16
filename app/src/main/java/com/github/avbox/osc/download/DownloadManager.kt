package com.github.avbox.osc.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.github.avbox.osc.data.AppGraph
import com.github.avbox.osc.data.DownloadRepository
import com.github.avbox.osc.data.DownloadState
import com.github.avbox.osc.data.DownloadTask
import com.github.avbox.osc.data.DownloadType
import com.github.avbox.osc.io.FileUtils
import com.github.avbox.osc.net.download.FileDownloader
import com.github.avbox.osc.net.download.M3u8Downloader
import com.github.avbox.osc.util.AppContextHolder
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.KV
import com.github.avbox.osc.util.KVKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

object DownloadManager {

    data class UiState(
        @JvmField val activeCount: Int = 0,
        @JvmField val speedBps: Long = 0L,
        @JvmField val message: String = "",
    )

    private data class Progress(val id: Int, val downloaded: Long, val total: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: DownloadRepository get() = AppGraph.downloadRepository
    private val signals = Channel<Int>(Channel.UNLIMITED)
    private val progressChannel = Channel<Progress>(Channel.UNLIMITED)
    private val running = HashMap<Int, Job>()
    private val cancelTags = HashMap<Int, String>()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val speedSamples = HashMap<Int, Pair<Long, Long>>()
    private var dispatcherStarted = false

    fun start() {
        if (dispatcherStarted) return
        dispatcherStarted = true
        scope.launch {
            for (id in signals) dispatchOne(id)
        }
        scope.launch {
            for (progress in progressChannel) {
                try {
                    repository.updateProgress(progress.id, progress.downloaded, progress.total)
                } catch (ignored: Throwable) {
                    AppLog.d("DownloadManager", "progress write failed")
                }
            }
        }
    }

    fun restorePending() {
        scope.launch { markStalePaused() }
    }

    fun enqueue(tasks: List<DownloadTask>) {
        if (tasks.isEmpty()) return
        scope.launch {
            val accepted = ArrayList<DownloadTask>()
            for (task in tasks) {
                if (repository.findEpisode(task.cid, task.sourceKey, task.vodId, task.playFlag, task.episodeName) != null) continue
                task.createTime = System.currentTimeMillis()
                task.updateTime = task.createTime
                task.state = DownloadState.WAITING.ordinal
                accepted.add(task)
            }
            if (accepted.isEmpty()) return@launch
            val ids = repository.enqueue(accepted)
            ensureService()
            ids.forEach { signals.trySend(it.toInt()) }
        }
    }

    fun pause(id: Int) {
        cancelTags.remove(id)?.let { FileDownloader.cancel(it) }
        running.remove(id)?.cancel()
        scope.launch { repository.updateState(id, DownloadState.PAUSED) }
    }

    fun resume(id: Int) {
        scope.launch {
            repository.updateState(id, DownloadState.WAITING)
            ensureService()
            signals.trySend(id)
        }
    }

    fun retry(id: Int) {
        scope.launch {
            repository.updateState(id, DownloadState.WAITING, null)
            ensureService()
            signals.trySend(id)
        }
    }

    fun cancel(id: Int) {
        cancelTags.remove(id)?.let { FileDownloader.cancel(it) }
        running.remove(id)?.cancel()
        scope.launch {
            repository.getAll().firstOrNull { it.id == id }?.let { task -> deleteFiles(task) }
            repository.delete(id)
        }
    }

    fun delete(id: Int) {
        scope.launch {
            repository.getAll().firstOrNull { it.id == id }?.let { task -> deleteFiles(task) }
            repository.delete(id)
        }
    }

    fun pauseAll() {
        scope.launch {
            repository.getAll()
                .filter { it.status() == DownloadState.DOWNLOADING || it.status() == DownloadState.WAITING }
                .forEach { pause(it.id) }
        }
    }

    fun resumeAll() {
        scope.launch {
            val paused = repository.getAll().filter { it.status() == DownloadState.PAUSED }
            if (paused.isEmpty()) return@launch
            paused.forEach { repository.updateState(it.id, DownloadState.WAITING) }
            ensureService()
            paused.forEach { signals.trySend(it.id) }
        }
    }

    fun clearCompleted() {
        scope.launch {
            repository.getAll().filter { it.status() == DownloadState.COMPLETED }.forEach { deleteFiles(it) }
            repository.clearCompleted()
        }
    }

    fun clearAll() {
        scope.launch {
            repository.getAll().forEach { deleteFiles(it) }
            repository.clearAll()
        }
    }

    fun waitForWifiChanged() {
        scope.launch {
            repository.getAll().filter { it.status() == DownloadState.WAITING }.forEach { signals.trySend(it.id) }
        }
    }

    private suspend fun markStalePaused() {
        for (task in repository.pendingForResume()) {
            if (!running.containsKey(task.id)) repository.updateState(task.id, DownloadState.PAUSED)
        }
    }

    private suspend fun dispatchOne(id: Int) {
        if (running.containsKey(id)) return
        val limit = concurrency().coerceIn(1, 3)
        if (running.size >= limit) return
        val task = repository.getAll().firstOrNull { it.id == id } ?: return
        if (task.status() != DownloadState.WAITING) return
        if (!wifiAllowed()) {
            _state.value = _state.value.copy(message = "waiting_wifi")
            return
        }
        if (_state.value.message.isNotEmpty()) _state.value = _state.value.copy(message = "")
        val tag = "download_${task.id}_${System.currentTimeMillis()}"
        cancelTags[id] = tag
        val job = scope.launch {
            var finalStatus = DownloadState.FAILED
            var finalError: String? = null
            try {
                execute(task, tag)
                finalStatus = DownloadState.COMPLETED
            } catch (throwable: Throwable) {
                AppLog.d("DownloadManager", "task ${task.id} failed: " + throwable.message)
                finalError = throwable.message.orEmpty()
            } finally {
                if (isActive) repository.updateState(id, finalStatus, finalError)
                running.remove(id)
                cancelTags.remove(id)
                speedSamples.remove(id)
                updateActiveCount()
                val next = repository.getAll()
                    .filter { it.status() == DownloadState.WAITING }
                    .minByOrNull { it.id }
                if (next != null) signals.trySend(next.id)
            }
        }
        running[id] = job
        updateActiveCount()
    }

    private suspend fun execute(task: DownloadTask, tag: String) {
        val dest = outputFile(task)
        if (dest == null) throw IllegalStateException("path")
        val onProgress: (Long, Long) -> Unit = { downloaded, total ->
            progressChannel.trySend(Progress(task.id, downloaded, total))
            sampleSpeed(task.id, downloaded)
        }
        val rawUrl = task.episodeUrl.orEmpty()
        val playUrl = if (isDirect(rawUrl)) {
            rawUrl
        } else {
            repository.updateState(task.id, DownloadState.PREPARING)
            val progressKey = task.sourceKey.orEmpty() + task.vodId.orEmpty() + task.playFlag.orEmpty() +
                task.episodeIndex + task.episodeName.orEmpty()
            val resolved = PlayUrlResolverHolder.resolver?.resolve(task.sourceKey, task.playFlag, progressKey, rawUrl)
            if (resolved.isNullOrEmpty()) throw IllegalStateException("resolve failed: no playable url")
            resolved
        }
        val type = if (playUrl.contains(".m3u8", ignoreCase = true)) DownloadType.M3U8 else DownloadType.HTTP
        when (type) {
            DownloadType.HTTP -> {
                repository.updateState(task.id, DownloadState.DOWNLOADING)
                val file = FileDownloader.download(playUrl, dest, tag, onProgress)
                if (!file.exists() || file.length() <= 0L) throw IllegalStateException("empty")
                repository.updateCompleted(task.id, file.absolutePath)
            }
            DownloadType.M3U8 -> {
                repository.updateState(task.id, DownloadState.DOWNLOADING)
                val file = M3u8Downloader.download(playUrl, dest, onProgress)
                if (!file.exists() || file.length() <= 0L) throw IllegalStateException("empty")
                repository.updateCompleted(task.id, file.absolutePath)
            }
        }
    }

    private fun isDirect(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    private fun sampleSpeed(id: Int, downloaded: Long) {
        val now = System.currentTimeMillis()
        val previous = speedSamples[id] ?: run {
            speedSamples[id] = now to downloaded
            return
        }
        val elapsed = now - previous.first
        if (elapsed < 500L) return
        val speed = (downloaded - previous.second) * 1000L / elapsed
        speedSamples[id] = now to downloaded
        _state.value = _state.value.copy(speedBps = speed.coerceAtLeast(0L))
    }

    private fun updateActiveCount() {
        _state.value = _state.value.copy(activeCount = running.size)
    }

    private fun ensureService() {
        val context = AppContextHolder.context() ?: return
        try {
            DownloadService.start(context)
        } catch (throwable: Throwable) {
            AppLog.d("DownloadManager", "service start failed: " + throwable.message)
        }
    }

    private fun wifiOnly(): Boolean = KV.get(KVKeys.DOWNLOAD_WIFI_ONLY, false)

    private fun concurrency(): Int = KV.get(KVKeys.DOWNLOAD_CONCURRENCY, 3)

    private fun wifiAllowed(): Boolean {
        if (!wifiOnly()) return true
        val context = AppContextHolder.context() ?: return true
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun deleteFiles(task: DownloadTask) {
        val dest = outputFile(task) ?: return
        if (dest.exists()) dest.delete()
        File(dest.parentFile, dest.name + ".tmp").delete()
        File(dest.parentFile, dest.name + ".parts").deleteRecursively()
    }

    private fun outputFile(task: DownloadTask): File? {
        val context = AppContextHolder.context() ?: return null
        val root = File(context.getExternalFilesDir(null), "downloads")
        val dir = File(root, safeName(task.sourceKey.orEmpty() + "_" + task.playFlag.orEmpty()))
        dir.mkdirs()
        FileUtils.createNoMediaMarkerIfAbsent(dir)
        val name = safeName(task.episodeName ?: task.vodName.orEmpty())
        val ext = FileDownloader.extOf(task.episodeUrl.orEmpty())
        return File(dir, "$name.$ext")
    }

    private fun safeName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_")
        return if (cleaned.isEmpty()) "video" else cleaned.take(80)
    }
}
