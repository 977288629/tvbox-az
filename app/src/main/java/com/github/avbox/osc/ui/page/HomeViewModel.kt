package com.github.avbox.osc.ui.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.avbox.osc.R
import com.github.avbox.osc.api.AppBootstrap
import com.github.avbox.osc.api.ApiConfig
import com.github.avbox.osc.api.SortAdjuster
import com.github.avbox.osc.api.TmdbPoster
import com.github.avbox.osc.bean.AbsSortXml
import com.github.avbox.osc.bean.AbsXml
import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.bean.MovieSort
import com.github.avbox.osc.bean.SourceBean
import com.github.avbox.osc.bean.TmdbPosterCache
import com.github.avbox.osc.sourcedata.SourceRuntimeState
import com.github.avbox.osc.sourcedata.SourceViewModel
import com.github.avbox.osc.boot.BootGuard
import com.github.avbox.osc.util.AppContextHolder
import com.github.avbox.osc.util.KVKeys
import com.github.avbox.osc.util.HistoryHelper
import com.github.avbox.osc.util.TitleSimilarity
import com.github.avbox.osc.util.HomeSettings
import com.github.avbox.osc.util.KV
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.LanguageManager
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.github.avbox.osc.data.AppGraph
import com.github.avbox.osc.ui.components.toVodInfo
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel(), TmdbPosterCache {

    override fun tmdbCachedPoster(name: String?, year: Int, altName: String?): String? =
        TmdbPoster.cachedPoster(name, year, altName)

    private fun str(resId: Int, vararg args: Any): String {
        val context = AppContextHolder.context() ?: return ""
        return LanguageManager.localized(context).getString(resId, *args)
    }

    sealed interface PartitionState {
        data object Idle : PartitionState
        data object Loading : PartitionState
        data object Empty : PartitionState
        data object Ready : PartitionState
        data object Error : PartitionState
    }

    data class Partition(
        val sort: MovieSort.SortData,
        val state: PartitionState,
        val videos: List<Movie.Video>,
        val nextPage: Int,
        val maxPage: Int,
    ) {
        companion object {
            const val FIRST_PAGE = 1
        }

        val hasMore: Boolean get() = !(maxPage > 0 && nextPage > maxPage)
    }

    data class Rec(val state: PartitionState, val videos: List<Movie.Video>)

    companion object {
        const val HERO_VIDEO_COUNT = 5
        /** 下拉刷新转圈最短展示时长：数据通常 200ms 内返回，太短会像没反应。 */
        const val MIN_REFRESH_SHOW_MS = 900L
    }

    private val _currentSource = MutableStateFlow<SourceBean?>(null)
    val currentSource = _currentSource.asStateFlow()
    private val _sources = MutableStateFlow<List<SourceBean>>(emptyList())
    val sources = _sources.asStateFlow()
    private val _subscribeItems = MutableStateFlow<List<SubscribeSource>>(emptyList())
    val subscribeItems = _subscribeItems.asStateFlow()
    private val _activeSubscribeIndex = MutableStateFlow(-1)
    val activeSubscribeIndex = _activeSubscribeIndex.asStateFlow()
    private val _allSorts = MutableStateFlow<List<MovieSort.SortData>>(emptyList())
    val allSorts = _allSorts.asStateFlow()
    private val _sorts = MutableStateFlow<List<MovieSort.SortData>>(emptyList())
    val sorts = _sorts.asStateFlow()
    private val _rec = MutableStateFlow(Rec(PartitionState.Loading, emptyList()))
    val rec = _rec.asStateFlow()
    private val _partitions = MutableStateFlow<List<Partition>>(emptyList())
    val partitions = _partitions.asStateFlow()

    private val _pageLoading = MutableStateFlow(true)
    val pageLoading = _pageLoading.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private var refreshPending = 0
    private var refreshStartedAt = 0L
    private var refreshToken = 0
    private var keepContentOnLoad = false
    private val sortsLoaded = MutableStateFlow(false)
    private val bootReady = MutableStateFlow(false)
    private val _pageErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pageErrorEvents: SharedFlow<String> = _pageErrorEvents.asSharedFlow()

    private val _sortLoadFailed = MutableStateFlow(false)
    val sortLoadFailed = _sortLoadFailed.asStateFlow()
    private var sortRetried = false
    private val listRetried = HashSet<String>()

    private val scope = viewModelScope
    private val sortViewModel = SourceViewModel()
    private val actionViewModel = SourceViewModel()
    private val recViewModel = SourceViewModel()
    private val loaders = HashMap<String, PartitionLoader>()
    private val loadSemaphore = Semaphore(2)
    private var loadGeneration = 0
    private var loadingSourceKey: String? = null
    private var configReloading = false
    private var watchdogJob: Job? = null

    private val _activeSortId = MutableStateFlow<String?>(null)
    val activeSortId: StateFlow<String?> = _activeSortId.asStateFlow()

    var defaultLiveLaunched = false
        private set

    var lastBackTime = 0L
        private set

    fun markDefaultLiveLaunched() {
        defaultLiveLaunched = true
    }

    fun onBackPressedOnce(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastBackTime < 2000) return true
        lastBackTime = now
        return false
    }

    suspend fun isVideoCollected(video: Movie.Video): Boolean = withContext(Dispatchers.IO) {
        AppGraph.collectRepository.isVodCollect(video.sourceKey, video.id)
    }

    fun toggleVideoCollect(video: Movie.Video, toCollect: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (toCollect) {
                AppGraph.collectRepository.insertVodCollect(video.sourceKey, toVodInfo(video))
            } else {
                AppGraph.collectRepository.deleteVodCollect(video.sourceKey, toVodInfo(video))
            }
        }
    }

    private val _actionMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actionMessages: SharedFlow<String> = _actionMessages.asSharedFlow()

    init {
        scope.launch { sortViewModel.sortResult.flow.collect { onSortResult(it) } }
        scope.launch { recViewModel.sortResult.flow.collect { onRecResult(it) } }
        scope.launch {
            actionViewModel.actionResult.flow.collect { json ->
                val msg = json?.optString("msg").orEmpty()
                if (msg.isNotEmpty()) _actionMessages.tryEmit(msg)
            }
        }
        scope.launch {
            AppBootstrap.subscriptionCid.drop(1).collect {
                configReloading = true
                reload()
            }
        }
        _sources.value = ApiConfig.get().switchSourceBeanList()
        _currentSource.value = ApiConfig.get().homeSourceBean()
        refreshSubscribes()
        scope.launch {
            AppBootstrap.state.collect {
                bootReady.value = it is AppBootstrap.Boot.Ready
                if (it is AppBootstrap.Boot.Ready) {
                    configReloading = false
                    loadHome()
                }
            }
        }
        scope.launch {
            combine(bootReady, rec, sortsLoaded) { ready, r, loaded ->
                ready && loaded && r.state != PartitionState.Loading
            }.collect { ready ->
                if (ready && _pageLoading.value) {
                    _pageLoading.value = false
                }
            }
        }
    }

    override fun onCleared() {
        val staleLoaders = ArrayList(loaders.values)
        loaders.clear()
        staleLoaders.forEach { it.release() }
    }

    fun reload() {
        AppLog.i("echo--sort-reload")
        SourceRuntimeState.clearRuntimeCache()
        loadHome()
    }

    /** 下拉刷新：保留当前内容重新拉取，期间只显示 48dp 刷新指示器，不切整页加载态。 */
    fun refresh() {
        if (_refreshing.value || _pageLoading.value) return
        AppLog.i("echo--sort-refresh")
        _refreshing.value = true
        refreshPending = 0
        refreshStartedAt = System.currentTimeMillis()
        refreshToken++
        SourceRuntimeState.clearRuntimeCache()
        loadHome(keepContent = true)
    }

    fun switchSource(bean: SourceBean) {
        AppLog.i("echo--sort-switch: key=${bean.key}")
        ApiConfig.get().setSourceBean(bean)
        _currentSource.value = bean
        loadHome()
    }

    fun isSubscribeDisabled(item: SubscribeSource): Boolean = BootGuard.isDisabledSource(item.url)

    fun switchSubscribe(item: SubscribeSource) {
        AppBootstrap.switchVodSubscription(item.url)
    }

    fun refreshSubscribes() {
        val items = vodSubscribes()
        val active = KV.get(KVKeys.API_URL, "")
        _subscribeItems.value = items
        _activeSubscribeIndex.value = items.indexOfFirst {
            it.url == active || HistoryHelper.isApiLineSourceOf(it.url, active)
        }
    }

    fun loadHome(keepContent: Boolean = false) {
        _sources.value = ApiConfig.get().switchSourceBeanList()
        refreshSubscribes()
        val home = ApiConfig.get().homeSourceBean()
        loadingSourceKey = if (home.key.isNullOrEmpty()) null else home.key
        AppLog.i("echo--sort-loadHome: key=${loadingSourceKey} name=${home.name} srcCount=${_sources.value.size}")
        _currentSource.value = home
        keepContentOnLoad = keepContent
        if (keepContent) {
            _refreshing.value = true
        } else {
            refreshToken++ // 让挂起的“最短展示”延迟失效
            _refreshing.value = false
            refreshPending = 0
            _pageLoading.value = true
        }
        sortsLoaded.value = false
        _sortLoadFailed.value = false
        sortRetried = false
        listRetried.clear()
        if (!keepContent) {
            _rec.value = Rec(PartitionState.Loading, emptyList())
            _partitions.value = emptyList()
        }
        val staleLoaders = ArrayList(loaders.values)
        loaders.clear()
        staleLoaders.forEach { it.release() }
        loadGeneration++
        armWatchdog()
        sortViewModel.getSort(loadingSourceKey, HomeSettings.layout == HomeSettings.HomeLayout.Horizontal)
    }

    private fun endRefresh() {
        if (!_refreshing.value) return
        // 数据常常在 200ms 内就回来了，转圈一闪而过像没反应；
        // 保证刷新态至少展示 MIN_REFRESH_SHOW_MS，让“加载中”明确可见。
        val elapsed = System.currentTimeMillis() - refreshStartedAt
        val remaining = MIN_REFRESH_SHOW_MS - elapsed
        if (remaining <= 0) {
            _refreshing.value = false
            refreshPending = 0
            return
        }
        val token = refreshToken
        scope.launch {
            delay(remaining)
            if (refreshToken == token && _refreshing.value) {
                _refreshing.value = false
                refreshPending = 0
            }
        }
    }

    private fun maybeEndRefresh() {
        if (!_refreshing.value || refreshPending > 0 || !sortsLoaded.value) return
        endRefresh()
    }

    private fun onPartitionRequestDone() {
        if (refreshPending > 0) refreshPending--
        maybeEndRefresh()
    }

    private fun onHomeLoadTimeout() {
        if (_refreshing.value) {
            // 刷新态下保留旧内容：只把仍处于 Loading 的分区标错并结束刷新，不切整页错误态
            _partitions.value = _partitions.value.map { p ->
                if (p.state == PartitionState.Loading) p.copy(state = PartitionState.Error) else p
            }
            endRefresh()
            _pageErrorEvents.tryEmit(str(R.string.home_load_partial_timeout))
            return
        }
        val recLoading = _rec.value.state == PartitionState.Loading
        val partitionLoading = _partitions.value.any { it.state == PartitionState.Loading }
        if (!recLoading && !partitionLoading) return
        if (recLoading) {
            _rec.value = Rec(PartitionState.Error, emptyList())
        }
        if (partitionLoading) {
            _partitions.value = _partitions.value.map { p ->
                if (p.state == PartitionState.Loading) p.copy(state = PartitionState.Error) else p
            }
        }
        _pageErrorEvents.tryEmit(
            if (recLoading) str(R.string.home_load_failed)
            else str(R.string.home_load_partial_timeout)
        )
        _pageLoading.value = false
    }

    fun retryPartition(partition: Partition) {
        if (partition.state != PartitionState.Error) return
        listRetried.remove(partition.sort.id)
        _partitions.value = _partitions.value.map {
            if (it.sort.id == partition.sort.id) it.copy(state = PartitionState.Loading) else it
        }
        requestPartition(partition, Partition.FIRST_PAGE)
    }

    fun retrySort() {
        val key = loadingSourceKey ?: return
        AppLog.i("echo--sort-manual-retry: key=$key")
        _sortLoadFailed.value = false
        sortRetried = true
        keepContentOnLoad = false
        refreshToken++ // 取消可能挂起的“最短展示”延迟，立即退出刷新态
        _refreshing.value = false
        refreshPending = 0
        _rec.value = Rec(PartitionState.Loading, emptyList())
        sortsLoaded.value = false
        _pageLoading.value = true
        armWatchdog()
        sortViewModel.getSort(key, HomeSettings.layout == HomeSettings.HomeLayout.Horizontal)
    }

    fun ensureLoaded(sortId: String) {
        _activeSortId.value = sortId
        val current = _partitions.value.firstOrNull { it.sort.id == sortId } ?: return
        if (current.state != PartitionState.Idle) return
        _partitions.value = _partitions.value.map {
            if (it.sort.id == sortId) it.copy(state = PartitionState.Loading) else it
        }
        requestPartition(current, Partition.FIRST_PAGE)
    }

    fun onLayoutChanged() {
        if (HomeSettings.layout != HomeSettings.HomeLayout.Horizontal) return
        val idle = _partitions.value.filter { it.state == PartitionState.Idle }
        if (idle.isNotEmpty()) {
            _partitions.value = _partitions.value.map {
                if (it.state == PartitionState.Idle) it.copy(state = PartitionState.Loading) else it
            }
            idle.forEach { p -> requestPartition(p, Partition.FIRST_PAGE) }
        }
        val key = loadingSourceKey
        val hasRecSort = _allSorts.value.any { it.id == "my0" }
        if (key != null && hasRecSort && _rec.value.videos.isEmpty() &&
            _rec.value.state != PartitionState.Loading
        ) {
            _rec.value = Rec(PartitionState.Loading, emptyList())
            recViewModel.getSort(key, true)
        }
    }

    private fun onSortResult(absXml: AbsSortXml?) {
        val key = loadingSourceKey
        val keepContent = keepContentOnLoad
        if (key == null) {
            AppLog.i("echo--sort-null-key: srcName=${_currentSource.value?.name} srcCount=${_sources.value.size} absXml=${absXml != null} reloading=$configReloading")
            if (configReloading) return
            if (keepContent) {
                sortsLoaded.value = true
                endRefresh()
                return
            }
            _rec.value = Rec(PartitionState.Empty, emptyList())
            _partitions.value = emptyList()
            _sorts.value = emptyList()
            _allSorts.value = emptyList()
            sortsLoaded.value = true
            return
        }
        if (absXml?.sourceKey != null && absXml.sourceKey != key) {
            AppLog.i("echo--sort-stale-drop: key=$key absKey=${absXml.sourceKey}")
            return
        }

        if (absXml != null && absXml.loadFailed) {
            if (!sortRetried) {
                sortRetried = true
                AppLog.i("echo--sort-retry: src=$key")
                sortViewModel.getSort(key, HomeSettings.layout == HomeSettings.HomeLayout.Horizontal)
                return
            }
            AppLog.i("echo--sort-failed: src=$key")
            if (keepContent) {
                // 刷新失败保留旧内容，只提示并结束刷新态
                _pageErrorEvents.tryEmit(str(R.string.home_load_failed))
                sortsLoaded.value = true
                endRefresh()
                return
            }
            _sortLoadFailed.value = true
            _rec.value = Rec(PartitionState.Empty, emptyList())
            _partitions.value = emptyList()
            _sorts.value = emptyList()
            _allSorts.value = emptyList()
            sortsLoaded.value = true
            return
        }

        AppLog.i("echo--sort-result: src=$key hasClasses=${absXml?.classes?.sortList != null} sortSize=${absXml?.classes?.sortList?.size}")
        val sortList = absXml?.classes?.sortList
        val adjusted = if (sortList != null) {
            SortAdjuster.adjustSort(key, sortList, true)
        } else {
            SortAdjuster.adjustSort(key, ArrayList(), true)
        }
        _allSorts.value = adjusted

        val recSort = adjusted.firstOrNull { it.id == "my0" }
        if (recSort != null) {
            loadRec(absXml)
        } else {
            _rec.value = Rec(PartitionState.Empty, emptyList())
        }

        val visible = adjusted.filter { it.id != "my0" }
        if (visible.isEmpty() && absXml != null && absXml.videoList.isNullOrEmpty()) {
            if (!sortRetried) {
                sortRetried = true
                AppLog.i("echo--sort-empty-retry: src=$key sortSize=${absXml.classes?.sortList?.size}")
                val gen = loadGeneration
                scope.launch {
                    delay(2000)
                    if (loadingSourceKey == key && loadGeneration == gen) {
                        sortViewModel.getSort(key, HomeSettings.layout == HomeSettings.HomeLayout.Horizontal)
                    }
                }
                return
            }
            AppLog.i("echo--sort-empty-final: src=$key sortSize=${absXml.classes?.sortList?.size}")
            if (keepContent) {
                _pageErrorEvents.tryEmit(str(R.string.home_load_failed))
                sortsLoaded.value = true
                endRefresh()
                return
            }
            _sortLoadFailed.value = true
            _rec.value = Rec(PartitionState.Empty, emptyList())
            _partitions.value = emptyList()
            _sorts.value = emptyList()
            _allSorts.value = emptyList()
            sortsLoaded.value = true
            return
        }
        _sorts.value = visible
        val vertical = HomeSettings.layout == HomeSettings.HomeLayout.Vertical
        val active = _activeSortId.value?.takeIf { id -> visible.any { it.id == id } } ?: visible.firstOrNull()?.id
        _activeSortId.value = active
        val previous = if (keepContent) _partitions.value.associateBy { it.sort.id } else emptyMap()
        val newPartitions = visible.map { sort ->
            val old = previous[sort.id]
            when {
                vertical && sort.id != active ->
                    Partition(sort, PartitionState.Idle, emptyList(), Partition.FIRST_PAGE, 0)
                old != null && old.videos.isNotEmpty() ->
                    // 刷新：先留着旧视频，等新结果回来再覆盖，期间不闪骨架屏
                    old.copy(sort = sort)
                else -> Partition(sort, PartitionState.Loading, emptyList(), Partition.FIRST_PAGE, 0)
            }
        }
        _partitions.value = newPartitions
        AppLog.i("echo--sort-partitions: n=${newPartitions.size} keep=$keepContent")
        sortsLoaded.value = true
        newPartitions
            .filter { it.state != PartitionState.Idle }
            .forEach { p -> requestPartition(p, Partition.FIRST_PAGE) }
        maybeEndRefresh()
    }


    private fun excludeHeroDuplicates(videos: List<Movie.Video>): List<Movie.Video> {
        val hero = _rec.value.videos.take(HERO_VIDEO_COUNT)
        if (hero.isEmpty()) return videos
        return videos.filter { video ->
            hero.none { h -> TitleSimilarity.similarity(h.name, video.name) >= TitleSimilarity.AcceptThreshold }
        }
    }

    private fun loadRec(absXml: AbsSortXml?) {
        val videos = TitleSimilarity.dedupe(absXml?.videoList ?: emptyList()) { it.name }
        _rec.value = if (videos.isEmpty()) Rec(PartitionState.Empty, videos) else Rec(PartitionState.Ready, videos)
        if (videos.isNotEmpty()) reconcilePartitionsWithHero()
    }

    private fun reconcilePartitionsWithHero() {
        _partitions.value = _partitions.value.map { p ->
            p.copy(videos = excludeHeroDuplicates(p.videos))
        }
    }

    private fun onRecResult(absXml: AbsSortXml?) {
        val key = loadingSourceKey ?: return
        if (absXml?.sourceKey != null && absXml.sourceKey != key) return
        loadRec(absXml)
    }

    private class LoaderResult(val stale: Boolean, val absXml: AbsXml?)

    private fun armWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(20_000)
            onHomeLoadTimeout()
        }
    }

    private fun requestPartition(current: Partition, page: Int) {
        val generation = loadGeneration
        val sourceKey = loadingSourceKey
        val loader = loaders.getOrPut(current.sort.id.orEmpty()) { PartitionLoader(sourceKey, current.sort) }
        val counted = _refreshing.value && page == Partition.FIRST_PAGE
        if (counted) refreshPending++
        scope.launch {
            loadSemaphore.withPermit {
                if (generation != loadGeneration || loader.released || loader.sourceKey != loadingSourceKey) {
                    if (counted) onPartitionRequestDone()
                    return@withPermit
                }
                armWatchdog()
                val result = suspendCancellableCoroutine<LoaderResult> { cont ->
                    loader.request(page) { r -> if (cont.isActive) cont.resume(r) }
                }
                if (!result.stale) {
                    applyPartitionResult(current.sort.id.orEmpty(), page, result.absXml)
                }
                if (counted) onPartitionRequestDone()
            }
        }
    }

    private fun applyPartitionResult(sortId: String, page: Int, absXml: AbsXml?) {
        if (absXml == null && page == Partition.FIRST_PAGE) {
            if (listRetried.add(sortId)) {
                AppLog.i("echo--list-retry: sort=$sortId pg=$page")
                val current = _partitions.value.firstOrNull { it.sort.id == sortId } ?: return
                requestPartition(current, Partition.FIRST_PAGE)
            } else {
                AppLog.i("echo--list-failed: sort=$sortId pg=$page")
                _partitions.value = _partitions.value.map { p ->
                    if (p.sort.id != sortId) {
                        p
                    } else if (p.videos.isNotEmpty()) {
                        // 已有内容时（下拉刷新失败）保留旧列表，不把整块翻成错误态
                        p.copy(state = PartitionState.Ready)
                    } else {
                        p.copy(state = PartitionState.Error)
                    }
                }
            }
            return
        }
        val rawVideos = absXml?.movie?.videoList ?: emptyList()
        val videos = if (page == Partition.FIRST_PAGE) TitleSimilarity.dedupe(rawVideos) { it.name } else rawVideos
        AppLog.i("echo--list-result: src=${loadingSourceKey} sort=$sortId pg=$page n=${videos.size}")
        val maxPage = absXml?.movie?.pagecount ?: 0
        _partitions.value = _partitions.value.map { p ->
            if (p.sort.id != sortId) {
                p
            } else if (videos.isEmpty() && page == Partition.FIRST_PAGE) {
                Partition(p.sort, PartitionState.Empty, emptyList(), Partition.FIRST_PAGE, maxPage)
            } else {
                val merged = if (page == 0) videos else p.videos + videos
                Partition(p.sort, PartitionState.Ready, excludeHeroDuplicates(merged), page + 1, maxPage)
            }
        }
        fillRecFromPartition()
    }

    private fun fillRecFromPartition() {
        if (_rec.value.videos.isNotEmpty()) return
        val first = _partitions.value.firstOrNull {
            it.state == PartitionState.Ready && it.videos.isNotEmpty()
        } ?: return
        val hero = TitleSimilarity.dedupe(first.videos) { it.name }.take(HERO_VIDEO_COUNT)
        _rec.value = Rec(PartitionState.Ready, hero)
        reconcilePartitionsWithHero()
    }

    fun loadMorePartition(partition: Partition) {
        if (_refreshing.value) return
        if (partition.state != PartitionState.Ready || !partition.hasMore) return
        val loader = loaders[partition.sort.id] ?: return
        if (loader.busy) return
        requestPartition(partition, partition.nextPage)
    }

    fun applyFilter(partition: Partition, filterSelect: Map<String, String>) {
        partition.sort.filterSelect = HashMap(filterSelect)
        _partitions.value = _partitions.value.map {
            if (it.sort.id == partition.sort.id) {
                Partition(it.sort, PartitionState.Loading, emptyList(), Partition.FIRST_PAGE, 0)
            } else {
                it
            }
        }
        requestPartition(partition.copy(sort = partition.sort), Partition.FIRST_PAGE)
    }

    fun handleAction(video: Movie.Video) {
        actionViewModel.action(video.sourceKey, video.action)
    }

    fun refreshPartitions() {
        val vertical = HomeSettings.layout == HomeSettings.HomeLayout.Vertical
        val active = _activeSortId.value
        val targets = if (vertical) {
            _partitions.value.filter { it.sort.id == active }
        } else {
            _partitions.value
        }
        _partitions.value = _partitions.value.map { p ->
            if (targets.any { it.sort.id == p.sort.id }) {
                Partition(p.sort, PartitionState.Loading, emptyList(), Partition.FIRST_PAGE, 0)
            } else {
                p
            }
        }
        targets.forEach { p -> requestPartition(p, Partition.FIRST_PAGE) }
    }

    private inner class PartitionLoader(val sourceKey: String?, val sort: MovieSort.SortData) {
        private val svm = SourceViewModel()
        @Volatile
        private var pending: ((LoaderResult) -> Unit)? = null

        @Volatile
        var busy: Boolean = false
            private set

        @Volatile
        var released: Boolean = false
            private set

        private val observeScope = CoroutineScope(
            SupervisorJob(viewModelScope.coroutineContext[Job]) + Dispatchers.Main.immediate
        )

        init {
            observeScope.launch {
                svm.listResult.flow.collect { abs ->
                    val current = pending
                    pending = null
                    busy = false
                    current?.invoke(LoaderResult(stale = false, absXml = abs))
                }
            }
        }

        fun request(page: Int, onDone: (LoaderResult) -> Unit) {
            pending?.invoke(LoaderResult(stale = true, absXml = null))
            pending = onDone
            busy = true
            svm.getList(sourceKey, sort, page)
        }

        fun release() {
            released = true
            pending?.invoke(LoaderResult(stale = true, absXml = null))
            pending = null
            busy = false
            observeScope.cancel()
        }
    }
}
