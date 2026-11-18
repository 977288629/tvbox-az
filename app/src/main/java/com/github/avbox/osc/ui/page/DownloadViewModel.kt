package com.github.avbox.osc.ui.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.avbox.osc.data.AppGraph
import com.github.avbox.osc.data.DownloadRepository
import com.github.avbox.osc.data.DownloadState
import com.github.avbox.osc.data.DownloadTask
import com.github.avbox.osc.data.DownloadType
import com.github.avbox.osc.download.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadUiItem(
    val id: Int,
    val name: String,
    val vodName: String,
    val pic: String?,
    val sourceKey: String?,
    val vodId: String?,
    val status: Int,
    val episodeIndex: Int,
    val downloaded: Long,
    val total: Long,
    val filePath: String?,
    val error: String?,
    val isActive: Boolean,
    val isCompleted: Boolean,
    val canPause: Boolean,
    val canResume: Boolean,
    val canRetry: Boolean,
    val isM3u8: Boolean,
    val playablePath: String? = null,
) {
    val canStreamPlay: Boolean get() = playablePath != null
}

data class DownloadGroup(
    val key: String,
    val name: String,
    val pic: String?,
    val episodes: List<DownloadUiItem>,
    val completedCount: Int,
    val activeCount: Int,
    val failedCount: Int,
    val totalBytes: Long,
) {
    val episodeCount: Int get() = episodes.size
    val allCompleted: Boolean get() = completedCount == episodeCount
    val hasActive: Boolean get() = activeCount > 0
    val progress: Float get() = if (episodeCount == 0) 0f else completedCount.toFloat() / episodeCount
}

class DownloadViewModel(
    private val repository: DownloadRepository = AppGraph.downloadRepository,
) : ViewModel() {

    enum class Filter { ALL, ACTIVE, COMPLETED }

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _groups = MutableStateFlow<List<DownloadGroup>>(emptyList())
    val groups: StateFlow<List<DownloadGroup>> = _groups.asStateFlow()

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _speed = MutableStateFlow(0L)
    val speed: StateFlow<Long> = _speed.asStateFlow()

    private val raw = MutableStateFlow<List<DownloadTask>>(emptyList())

    init {
        DownloadManager.start()
        DownloadManager.restorePending()
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeAll().collect { list ->
                raw.value = list
                publish(list)
                _loading.value = false
            }
        }
        viewModelScope.launch {
            DownloadManager.state.collect { state -> _speed.value = state.speedBps }
        }
    }

    private fun publish(list: List<DownloadTask>) {
        val items = list.map { it.toUi() }
        _groups.value = groupItems(items)
    }

    private fun groupItems(items: List<DownloadUiItem>): List<DownloadGroup> {
        val buckets = LinkedHashMap<String, MutableList<DownloadUiItem>>()
        for (item in items) {
            buckets.getOrPut(item.groupKey()) { ArrayList() }.add(item)
        }
        return buckets.map { (key, episodes) ->
            val sorted = episodes.sortedBy { it.episodeIndex }
            val first = sorted.first()
            DownloadGroup(
                key = key,
                name = first.vodName.ifBlank { first.name },
                pic = first.pic,
                episodes = sorted,
                completedCount = sorted.count { it.isCompleted },
                activeCount = sorted.count { it.isActive && !it.isCompleted && it.status != DownloadState.FAILED.ordinal },
                failedCount = sorted.count { it.status == DownloadState.FAILED.ordinal },
                totalBytes = sorted.filter { it.isCompleted }.sumOf { it.total },
            )
        }.sortedWith(
            compareByDescending<DownloadGroup> { it.hasActive }
                .thenByDescending { group -> group.episodes.maxOf { it.id } },
        )
    }

    private fun DownloadUiItem.groupKey(): String =
        (sourceKey.orEmpty()) + "|" + (vodId.orEmpty())

    private fun DownloadTask.toUi(): DownloadUiItem {
        val state = status()
        return DownloadUiItem(
            id = id,
            name = episodeName?.takeIf { it.isNotBlank() } ?: vodName.orEmpty(),
            vodName = vodName.orEmpty(),
            pic = vodPic,
            sourceKey = sourceKey,
            vodId = vodId,
            status = state.ordinal,
            episodeIndex = episodeIndex,
            downloaded = downloadedBytes,
            total = totalBytes,
            filePath = filePath,
            error = errorMessage,
            isActive = isActiveState(state),
            isCompleted = state == DownloadState.COMPLETED,
            canPause = state == DownloadState.DOWNLOADING || state == DownloadState.WAITING || state == DownloadState.PREPARING,
            canResume = state == DownloadState.PAUSED,
            canRetry = state == DownloadState.FAILED,
            isM3u8 = type() == DownloadType.M3U8,
            playablePath = DownloadManager.playableFileOf(this)?.absolutePath,
        )
    }

    private fun isActiveState(state: DownloadState): Boolean = when (state) {
        DownloadState.WAITING, DownloadState.PREPARING, DownloadState.DOWNLOADING,
        DownloadState.MERGING, DownloadState.PAUSED, DownloadState.FAILED -> true
        else -> false
    }

    fun setFilter(value: Filter) {
        _filter.value = value
        publish(raw.value)
    }

    fun visibleGroups(): List<DownloadGroup> = when (_filter.value) {
        Filter.ALL -> _groups.value
        Filter.ACTIVE -> _groups.value.filter { g -> g.episodes.any { it.isActive && !it.isCompleted } }
        Filter.COMPLETED -> _groups.value.filter { it.allCompleted }
    }

    fun isEmpty(): Boolean = _groups.value.isEmpty()

    fun pause(id: Int) = DownloadManager.pause(id)

    fun resume(id: Int) = DownloadManager.resume(id)

    fun retry(id: Int) = DownloadManager.retry(id)

    fun cancel(id: Int) = DownloadManager.cancel(id)

    fun delete(id: Int) = DownloadManager.delete(id)

    fun pauseAll() = DownloadManager.pauseAll()

    fun resumeAll() = DownloadManager.resumeAll()

    fun clearCompleted() = DownloadManager.clearCompleted()

    fun clearAll() = DownloadManager.clearAll()
}
