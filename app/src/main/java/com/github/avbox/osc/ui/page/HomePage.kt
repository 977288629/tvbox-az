@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.github.avbox.osc.ui.page

import com.github.avbox.osc.api.SearchSourcesState
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.bean.MovieSort
import com.github.avbox.osc.net.SiteSearch
import com.github.avbox.osc.ui.ConfigManageActivity
import com.github.avbox.osc.ui.SearchActivity
import com.github.avbox.osc.ui.WindowSize
import com.github.avbox.osc.ui.components.AVBoxBottomSheet
import com.github.avbox.osc.ui.components.AVBoxOptionMenuAction
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.components.HeroSpotlight
import com.github.avbox.osc.ui.components.HomeBackdrop
import com.github.avbox.osc.ui.components.HomeHeroBackdrop
import com.github.avbox.osc.ui.components.LoadState
import com.github.avbox.osc.ui.components.ProvideTmdbPosterLookup
import com.github.avbox.osc.ui.components.homeHeroBackdropSupported
import com.github.avbox.osc.ui.components.LoadStateBox
import com.github.avbox.osc.ui.components.LocalSheetDismiss
import com.github.avbox.osc.ui.components.SearchField
import com.github.avbox.osc.ui.components.SearchSettingsSheet
import com.github.avbox.osc.ui.components.SettingsCard
import com.github.avbox.osc.ui.components.SettingsCardPosition
import com.github.avbox.osc.ui.components.SettingsGroup
import com.github.avbox.osc.ui.components.SettingsOptionRow
import com.github.avbox.osc.ui.components.SettingsRow
import com.github.avbox.osc.ui.components.SkeletonBox
import com.github.avbox.osc.ui.components.VodCard
import com.github.avbox.osc.ui.components.VodCardMenu
import com.github.avbox.osc.ui.components.clearFocusOnDrag
import com.github.avbox.osc.ui.settings.UiSettingsState
import com.github.avbox.osc.ui.components.glassTopBarSurface
import com.github.avbox.osc.ui.components.heroSpotlightHeight
import com.github.avbox.osc.ui.components.rememberVodCardMenuState
import com.github.avbox.osc.ui.page.jumpToSearch
import com.github.avbox.osc.ui.theme.AppThemeState
import com.github.avbox.osc.ui.theme.cardContainer
import com.github.avbox.osc.util.HomeSettings
import com.kyant.capsule.ContinuousCapsule
import kotlin.math.roundToInt

private val HomeSourceCapsuleMaxWidth = 240.dp

private const val CapsuleLogoZoom = 1.5f

private val HomeTopBarControlSpacing = 8.dp

@Composable
fun HomePage(
    vm: HomeViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val navStart = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    val navBottom = contentPadding.calculateBottomPadding()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val context = LocalContext.current
    val currentSource by vm.currentSource.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val rec by vm.rec.collectAsStateWithLifecycle()
    val partitions by vm.partitions.collectAsStateWithLifecycle()
    val sorts by vm.sorts.collectAsStateWithLifecycle()
    val vodMenu = rememberVodCardMenuState(
        lookupCollected = vm::isVideoCollected,
        onCollectToggle = vm::toggleVideoCollect,
    )

    val listState = rememberLazyListState()

    val pullState = rememberPullToRefreshState()

    val pageLoading by vm.pageLoading.collectAsStateWithLifecycle()
    val sortLoadFailed by vm.sortLoadFailed.collectAsStateWithLifecycle()
    val homeLayout by HomeSettings.layoutFlow.collectAsStateWithLifecycle()
    LaunchedEffect(homeLayout) { vm.onLayoutChanged() }
    LaunchedEffect(vm) {
        vm.pageErrorEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(vm) {
        vm.actionMessages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.refreshPartitions()
        }
    }

    var showSourceSheet by remember { mutableStateOf(false) }
    var showSearchSettings by remember { mutableStateOf(false) }
    var selectedCategoryId by remember(currentSource?.key) { mutableStateOf<String?>(null) }
    var categoriesExpanded by remember(currentSource?.key) { mutableStateOf(false) }
    var backdropSeed by remember { mutableStateOf<Int?>(null) }
    var backdropPic by remember { mutableStateOf("") }
    val heroBackdropEnabled = homeLayout == HomeSettings.HomeLayout.Horizontal &&
        homeHeroBackdropSupported
    val darkTheme = AppThemeState.isDark(isSystemInDarkTheme())
    val backdropContainer by animateColorAsState(
        targetValue = backdropSeed
            ?.takeIf { homeLayout == HomeSettings.HomeLayout.Horizontal }
            ?.let { Color(HomeBackdrop.surfaceOf(it, darkTheme)) }
            ?: MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(200),
        label = "homeBackdrop",
    )

    AppTopBarScaffold(
        collapseEnabled = homeLayout == HomeSettings.HomeLayout.Horizontal,
        topBarStartInset = navStart,
        topScrimEnabled = homeLayout != HomeSettings.HomeLayout.Horizontal,
        containerColor = backdropContainer,
        titleContent = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .widthIn(
                            max = minOf(
                                HomeSourceCapsuleMaxWidth,
                                maxWidth - HomeTopBarControlSpacing,
                            ),
                        )
                        .glassTopBarSurface(ContinuousCapsule, MaterialTheme.colorScheme.cardContainer)
                        .heightIn(min = 40.dp)
                        .clickable {
                            vm.refreshSubscribes()
                            showSourceSheet = true
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val capsuleLogo = remember { ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground) }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .drawBehind {
                                capsuleLogo?.let { drawable ->
                                    val w = size.width
                                    val h = size.height
                                    drawable.setBounds(
                                        (w / 2 - w * CapsuleLogoZoom / 2).roundToInt(),
                                        (h / 2 - h * CapsuleLogoZoom / 2).roundToInt(),
                                        (w / 2 + w * CapsuleLogoZoom / 2).roundToInt(),
                                        (h / 2 + h * CapsuleLogoZoom / 2).roundToInt(),
                                    )
                                    drawable.draw(drawContext.canvas.nativeCanvas)
                                }
                            },
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentSource?.name?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.home_subscription_source),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        actions = {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .glassTopBarSurface(ContinuousCapsule, MaterialTheme.colorScheme.surfaceBright),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { showSearchSettings = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.search_settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            context.startActivity(Intent(context, SearchActivity::class.java))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.common_search),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { topPad, _ ->
        if (heroBackdropEnabled && backdropPic.isNotEmpty()) {
            HomeHeroBackdrop(pic = backdropPic, scrimColor = backdropContainer)
        }
        when {
            pageLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator(Modifier.size(64.dp))
                }
            }
            sources.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPad, bottom = navBottom),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.ic_empty_record),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.home_no_source),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { ConfigManageActivity.start(context) }) {
                            Text(stringResource(R.string.config_add_subscribe))
                        }
                    }
                }
            }
            else -> {
            if (homeLayout == HomeSettings.HomeLayout.Vertical) {
                ProvideTmdbPosterLookup(vm) {
                    HomeGridLayout(
                        vm = vm,
                        topPadding = topPad,
                        contentPadding = contentPadding,
                        pullState = pullState,
                        onCardClick = { video -> handleCardClick(vm, video, context) },
                        onCardLongClick = { video -> vodMenu.show(video) },
                    )
                }
            } else LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pullToRefresh(
                        isRefreshing = false,
                        state = pullState,
                        onRefresh = { vm.reload() },
                    ),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    top = 0.dp,
                    bottom = 88.dp + navBottom,
                ),
            ) {
                item(key = "hero") {
                    when {
                        rec.state == HomeViewModel.PartitionState.Ready && rec.videos.isNotEmpty() -> {
                            ProvideTmdbPosterLookup(vm) {
                                HeroSpotlight(
                                    videos = rec.videos.take(HomeViewModel.HERO_VIDEO_COUNT),
                                    backdropColor = backdropContainer,
                                    onBackdropSeed = { argb -> backdropSeed = argb },
                                    onPosterPic = { pic -> backdropPic = pic },
                                    onCardClick = { video -> handleCardClick(vm, video, context) },
                                    fadeToBackdrop = heroBackdropEnabled,
                                )
                            }
                        }
                        rec.state == HomeViewModel.PartitionState.Loading -> {
                            SkeletonBox(
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .height(heroSpotlightHeight(screenHeightDp)),
                            )
                        }
                        rec.state == HomeViewModel.PartitionState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .height(heroSpotlightHeight(screenHeightDp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadStateBox(
                                    state = LoadState.Error(),
                                    emptyText = "",
                                    errorText = stringResource(R.string.home_load_timeout),
                                    retryText = stringResource(R.string.common_retry),
                                    onRetry = { vm.loadHome() },
                                )
                            }
                        }
                        else -> {
                            Spacer(modifier = Modifier.height(topPad + 8.dp))
                        }
                    }
                }
                stickyHeader(key = "cats") {
                    val pinned by remember {
                        androidx.compose.runtime.derivedStateOf {
                            listState.layoutInfo.visibleItemsInfo.none { it.key == "hero" }
                        }
                    }
                    HomeCategoryChips(
                        sorts = sorts,
                        selectedId = selectedCategoryId,
                        onSelect = { id -> selectedCategoryId = id },
                        expanded = categoriesExpanded,
                        onExpandedChange = { categoriesExpanded = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (pinned) {
                                    Modifier
                                        .background(backdropContainer)
                                        .windowInsetsPadding(
                                            androidx.compose.foundation.layout.WindowInsets.statusBars
                                        )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(start = navStart),
                    )
                }
                if (sortLoadFailed) {
                    item(key = "sort_error") {
                        Row(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(start = navStart)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.common_load_failed_network),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.retrySort() }) {
                                Text(text = stringResource(R.string.common_retry))
                            }
                        }
                    }
                }
                val activePartition = partitions.firstOrNull { it.sort.id == selectedCategoryId }
                if (selectedCategoryId != null && activePartition != null) {
                    homeCategoryGrid(
                        vm = vm,
                        partition = activePartition,
                        navStart = navStart,
                        columns = WindowSize.gridColumns(
                            availableWidthDp = screenWidthDp - 32 - navStart.value.toInt(),
                            minColumns = 3,
                        ),
                        fullSpan = UiSettingsState.config.vodCardStyle.isFullSpan,
                        onCardClick = { video -> handleCardClick(vm, video, context) },
                        onCardLongClick = { video -> vodMenu.show(video) },
                        onLoadMore = { vm.loadMorePartition(activePartition) },
                        onRetry = { vm.retryPartition(activePartition) },
                        onApplyFilter = { selection -> vm.applyFilter(activePartition, selection) },
                    )
                } else if (selectedCategoryId == null) {
                    if (rec.state != HomeViewModel.PartitionState.Empty &&
                        (rec.state == HomeViewModel.PartitionState.Loading ||
                            rec.videos.size > HomeViewModel.HERO_VIDEO_COUNT)
                    ) {
                        item(key = "rec") {
                            ProvideTmdbPosterLookup(vm) {
                                PartitionSection(
                                    title = stringResource(R.string.home_recommend),
                                    state = rec.state,
                                    videos = rec.videos.drop(HomeViewModel.HERO_VIDEO_COUNT),
                                    onLoadMore = {},
                                    onCardClick = { video -> handleCardClick(vm, video, context) },
                                    onCardLongClick = { video -> vodMenu.show(video) },
                                    modifier = Modifier.padding(start = navStart),
                                )
                            }
                        }
                    }
                    items(partitions, key = { it.sort.id.orEmpty() }) { p ->
                        ProvideTmdbPosterLookup(vm) {
                            PartitionSection(
                                title = p.sort.name ?: "",
                                state = p.state,
                                videos = p.videos,
                                onLoadMore = { vm.loadMorePartition(p) },
                                onCardClick = { video -> handleCardClick(vm, video, context) },
                                onCardLongClick = { video -> vodMenu.show(video) },
                                onRetry = { vm.retryPartition(p) },
                                modifier = Modifier.padding(start = navStart),
                            )
                        }
                    }
                }
            }
        }
        }

        if (sources.isNotEmpty() && homeLayout == HomeSettings.HomeLayout.Horizontal) {
            HomePullRefreshIndicator(
                state = pullState,
                isRefreshing = false,
                topPadding = topPad,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (showSourceSheet) {
        AVBoxBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            modifier = Modifier.heightIn(max = (screenHeightDp * 0.9f).dp),
            title = stringResource(R.string.home_subscription_source),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            isScrollable = false,
        ) {
            val dismissAnimated = LocalSheetDismiss.current
            val keyboard = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val subscribes by vm.subscribeItems.collectAsStateWithLifecycle()
            val activeSubscribeIndex by vm.activeSubscribeIndex.collectAsStateWithLifecycle()
            var query by remember { mutableStateOf("") }
            val filtered = remember(sources, query) { SiteSearch.filter(sources, query) }
            val subscribeOptions = remember(subscribes) { subscribes.map { it.name.ifEmpty { it.url } } }
            val listState = rememberLazyListState()
            val selectedIndex = filtered.indexOfFirst { it.key == currentSource?.key }
            LaunchedEffect(query.isEmpty()) {
                if (query.isEmpty() && selectedIndex > 0) listState.scrollToItem(selectedIndex)
            }
            fun switchSubscribe(item: SubscribeSource) {
                if (vm.isSubscribeDisabled(item)) {
                    Toast.makeText(context, R.string.toast_source_auto_disabled, Toast.LENGTH_LONG).show()
                } else {
                    dismissAnimated()
                    vm.switchSubscribe(item)
                }
            }
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { keyboard?.hide() },
                hint = stringResource(R.string.home_site_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                trailing = if (subscribeOptions.isEmpty()) {
                    null
                } else {
                    {
                        AVBoxOptionMenuAction(
                            options = subscribeOptions,
                            selectedIndex = activeSubscribeIndex,
                            onSelect = { index -> subscribes.getOrNull(index)?.let(::switchSubscribe) },
                            contentDescription = stringResource(R.string.home_subscription_source),
                        )
                    }
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clearFocusOnDrag(focusManager)
                    .pointerInput(focusManager) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        LoadStateBox(
                            state = LoadState.Empty,
                            emptyText = stringResource(
                                if (query.isEmpty()) R.string.config_empty_subscribe else R.string.home_site_search_empty,
                            ),
                            errorText = "",
                            retryText = "",
                            emptyIconRes = R.drawable.ic_empty_record,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        )
                    }
                }
                itemsIndexed(filtered) { index, bean ->
                    val selected = bean.key == currentSource?.key
                    SettingsCard(
                        position = when {
                            filtered.size == 1 -> SettingsCardPosition.SINGLE
                            index == 0 -> SettingsCardPosition.FIRST
                            index == filtered.size - 1 -> SettingsCardPosition.LAST
                            else -> SettingsCardPosition.MIDDLE
                        },
                        modifier = Modifier.padding(bottom = if (index == filtered.lastIndex) 0.dp else 2.dp),
                        color = MaterialTheme.colorScheme.surfaceBright,
                    ) {
                        SettingsOptionRow(
                            title = bean.name ?: bean.key.orEmpty(),
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    vm.switchSource(bean)
                                }
                                dismissAnimated()
                            },
                        )
                    }
                }
                item {
                    SettingsGroup(title = null, modifier = Modifier.padding(top = 20.dp)) {
                        SettingsCard(
                            position = SettingsCardPosition.SINGLE,
                            color = MaterialTheme.colorScheme.surfaceBright,
                        ) {
                            SettingsRow(
                                title = stringResource(R.string.settings_config_manage),
                                onClick = {
                                    dismissAnimated()
                                    ConfigManageActivity.start(context)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSearchSettings) {
        val searchSources by SearchSourcesState.state.collectAsStateWithLifecycle()
        SearchSettingsSheet(
            sources = searchSources.sources,
            selected = searchSources.pickedKeys,
            onApplySelection = { SearchSourcesState.applySelection(it) },
            onDismiss = { showSearchSettings = false },
        )
    }

    val menuContext = LocalContext.current
    VodCardMenu(vodMenu) { menuContext.jumpToSearch(it) }
}

@Composable
fun HomePullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier
            .offset(y = topPadding)
            .size(48.dp),
        shape = RectangleShape,
        containerColor = Color.Transparent,
        elevation = 0.dp,
    ) {
        Crossfade(targetState = isRefreshing) { refreshing ->
            if (refreshing) {
                ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
            } else {
                ContainedLoadingIndicator(
                    progress = { state.distanceFraction },
                    modifier = Modifier
                        .size(48.dp)
                        .drawWithContent {
                            val progress = state.distanceFraction
                            if (progress > 1f) {
                                rotate(degrees = -(progress - 1f) * 180f) {
                                    this@drawWithContent.drawContent()
                                }
                            } else {
                                drawContent()
                            }
                        },
                )
            }
        }
    }
}

private fun handleCardClick(vm: HomeViewModel, video: Movie.Video, context: android.content.Context) {
    context.dispatchVodCardClick(video, onAction = { vm.handleAction(it) })
}

@Composable
private fun PartitionSection(
    title: String,
    state: HomeViewModel.PartitionState,
    videos: List<Movie.Video>,
    onLoadMore: () -> Unit,
    onCardClick: (Movie.Video) -> Unit,
    onCardLongClick: (Movie.Video) -> Unit,
    onRetry: (() -> Unit)? = null,
    cardWidth: Dp = 128.dp,
    modifier: Modifier = Modifier,
) {
    val cardStyle = UiSettingsState.config.vodCardStyle
    val itemWidth = if (cardStyle.isPortrait) cardWidth else 280.dp
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        when (state) {
            HomeViewModel.PartitionState.Idle -> Unit

            HomeViewModel.PartitionState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(3) {
                        SkeletonBox(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }
            }

            HomeViewModel.PartitionState.Empty -> Text(
                text = stringResource(R.string.common_empty_content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            HomeViewModel.PartitionState.Error -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.common_load_failed_network),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onRetry?.invoke() }) {
                        Text(text = stringResource(R.string.common_retry))
                    }
                }
            }

            HomeViewModel.PartitionState.Ready -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(videos) { index, video ->
                    VodCard(
                        video = video,
                        onClick = { onCardClick(video) },
                        onLongClick = { onCardLongClick(video) },
                        modifier = Modifier.width(itemWidth),
                        style = cardStyle,
                    )
                }
                item(key = "more_$title") {
                    LaunchedEffect(videos.size) { onLoadMore() }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeCategoryChips(
    sorts: List<MovieSort.SortData>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val rowState = rememberLazyListState()
    val selectedIndex = if (selectedId == null) 0 else sorts.indexOfFirst { it.id == selectedId } + 1
    val endInset = (LocalConfiguration.current.screenWidthDp / 2).dp
    LaunchedEffect(selectedId, sorts) {
        if (selectedId == null) {
            rowState.scrollToItem(0)
        } else if (selectedIndex in 1..sorts.size) {
            rowState.animateScrollToItem(selectedIndex)
            val viewport = rowState.layoutInfo.viewportEndOffset - rowState.layoutInfo.viewportStartOffset
            val itemSize = rowState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == selectedIndex }?.size ?: 0
            if (itemSize > 0) {
                rowState.animateScrollToItem(
                    selectedIndex,
                    scrollOffset = -(viewport - itemSize) / 2,
                )
            }
        }
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                state = rowState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = endInset),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "recommend") {
                    HomeCategoryChip(
                        text = stringResource(R.string.home_recommend),
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(sorts) { sort ->
                    HomeCategoryChip(
                        text = sort.name ?: "",
                        selected = selectedId == sort.id,
                        onClick = { onSelect(sort.id) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onExpandedChange(!expanded) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.ic_expand_less else R.drawable.ic_category_grid
                    ),
                    contentDescription = stringResource(R.string.home_all_categories),
                    tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(180)) +
                fadeOut(animationSpec = tween(140)),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeCategoryGridChip(
                    text = stringResource(R.string.home_recommend),
                    selected = selectedId == null,
                    onClick = {
                        onSelect(null)
                        onExpandedChange(false)
                    },
                )
                sorts.forEach { sort ->
                    HomeCategoryGridChip(
                        text = sort.name ?: "",
                        selected = selectedId == sort.id,
                        onClick = {
                            onSelect(sort.id)
                            onExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCategoryGridChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun HomeCategoryFilters(
    sort: MovieSort.SortData,
    navStart: Dp,
    onApplyFilter: (Map<String, String>) -> Unit,
) {
    var expanded by remember(sort.id) { mutableStateOf(false) }
    val selectedLabels = sort.filters.mapNotNull { filter ->
        val key = filter.key.orEmpty()
        val valueKey = sort.filterSelect[key] ?: return@mapNotNull null
        filter.values.orEmpty().entries.firstOrNull { it.key == valueKey }?.value
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = navStart, top = 4.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedLabels.isEmpty()) {
                    stringResource(R.string.common_filter)
                } else {
                    selectedLabels.joinToString(" · ")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedLabels.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.detail_collapse else R.string.detail_expand
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.ic_expand_less else R.drawable.ic_arrow_down
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sort.filters.forEach { filter ->
                    val key = filter.key.orEmpty()
                    val selectedKey = sort.filterSelect[key]
                    Column {
                        Text(
                            text = filter.name.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filter.values.orEmpty().entries.toList(), key = { it.key }) { entry ->
                                val selected = selectedKey == entry.key
                                Text(
                                    text = entry.value,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .clickable {
                                            val next = HashMap(sort.filterSelect)
                                            if (selected) next.remove(key) else next[key] = entry.key
                                            onApplyFilter(next)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 20.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

private fun LazyListScope.homeCategoryGrid(
    vm: HomeViewModel,
    partition: HomeViewModel.Partition,
    navStart: Dp,
    columns: Int,
    fullSpan: Boolean,
    onCardClick: (Movie.Video) -> Unit,
    onCardLongClick: (Movie.Video) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onApplyFilter: (Map<String, String>) -> Unit,
) {
    if (partition.sort.filters.isNotEmpty()) {
        item(key = "category_filters") {
            HomeCategoryFilters(
                sort = partition.sort,
                navStart = navStart,
                onApplyFilter = onApplyFilter,
            )
        }
    }
    when (partition.state) {
        HomeViewModel.PartitionState.Idle, HomeViewModel.PartitionState.Loading -> item(key = "category_loading") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp + navStart, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(3) {
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        }
        HomeViewModel.PartitionState.Empty -> item(key = "category_empty") {
            Text(
                text = stringResource(R.string.common_empty_content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp + navStart, end = 16.dp),
            )
        }
        HomeViewModel.PartitionState.Error -> item(key = "category_error") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp + navStart, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.common_load_failed_network),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.common_retry))
                }
            }
        }
        HomeViewModel.PartitionState.Ready -> {
            val videos = partition.videos
            val cardStyle = UiSettingsState.config.vodCardStyle
            val rowColumns = if (fullSpan) 1 else columns
            val rows = videos.chunked(rowColumns)
            itemsIndexed(rows, key = { i, _ -> "category_row_$i" }) { _, row ->
                ProvideTmdbPosterLookup(vm) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp + navStart, end = 16.dp)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { video ->
                            VodCard(
                                video = video,
                                onClick = { onCardClick(video) },
                                onLongClick = { onCardLongClick(video) },
                                modifier = Modifier.weight(1f),
                                style = cardStyle,
                            )
                        }
                        repeat(rowColumns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (partition.hasMore) {
                item(key = "category_load_more") {
                    LaunchedEffect(videos.size) { onLoadMore() }
                }
            }
        }
    }
}

