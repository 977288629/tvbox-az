package com.github.avbox.osc.util

import coil3.SingletonImageLoader
import coil3.request.ImageRequest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HomeImagePrefetch {

    @Synchronized fun reset() { lastKey = null }

    private const val MAX_PREFETCH = 12
    private const val PREFETCH_BATCH = 3
    private const val PREFETCH_INTERVAL_MS = 60L
    @Volatile private var lastKey: String? = null

    suspend fun prefetch(images: List<String>?, key: String) = withContext(Dispatchers.IO) {
        val safeImages = images ?: return@withContext
        val shouldRun = synchronized(this) {
            if (safeImages.isEmpty() || lastKey != null) false else { lastKey = key; true }
        }
        if (!shouldRun) return@withContext
        val context = AppContextHolder.context() ?: return@withContext
        val loader = SingletonImageLoader.get(context)
        // 错峰预热：一次 enqueue 太多会与 UI 首屏请求争抢解码线程，反而造成瞬时卡顿。
        // 用 suspend 逐个入队并让出调度，把预热压力摊到多帧。
        val targets = safeImages.take(MAX_PREFETCH)
            .filter { it.isNotBlank() && !it.startsWith("clan://") }
        targets.forEachIndexed { index, url ->
            try {
                loader.enqueue(ImageRequest.Builder(context).data(url).build())
            } catch (ignored: Throwable) {
            }
            if (index % PREFETCH_BATCH == PREFETCH_BATCH - 1) {
                kotlinx.coroutines.delay(PREFETCH_INTERVAL_MS)
            }
        }
    }
}
