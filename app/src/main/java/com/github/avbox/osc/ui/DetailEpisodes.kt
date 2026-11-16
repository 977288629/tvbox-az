package com.github.avbox.osc.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.TmdbEpisode
import com.github.avbox.osc.bean.VodInfo
import com.github.avbox.osc.data.EpisodeMatcher
import com.github.avbox.osc.ui.components.AVBoxBottomSheet
import com.github.avbox.osc.ui.components.LocalSheetDismiss
import com.github.avbox.osc.ui.theme.filterChipColors
import com.github.avbox.osc.util.TmdbImages
import kotlinx.coroutines.launch

private const val EpisodeSheetHeightFraction = 0.6f

@Composable
internal fun EpisodeRow(
    vm: DetailViewModel,
    info: VodInfo,
    episodes: List<VodInfo.VodSeries>,
    playIndex: Int,
    currentFlag: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitleIcon(painterResource(R.drawable.ic_detail_episodes))
            Text(
                text = stringResource(R.string.detail_episodes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            PillAction(
                iconRes = if (info.reverseSort) {
                    R.drawable.ic_episode_order_asc
                } else {
                    R.drawable.ic_episode_reverse
                },
                text = stringResource(if (info.reverseSort) R.string.detail_order_asc else R.string.detail_order_desc),
                onClick = { vm.toggleReverse() },
            )
            Spacer(Modifier.width(2.dp))
            PillAction(
                iconRes = R.drawable.ic_episode_grid_all,
                text = stringResource(R.string.common_all),
                onClick = { vm.showEpisodeSheet() },
            )
        }
        val seasonHint = remember(info.name, currentFlag) {
            vm.seasonHintFor(info.name, currentFlag)
        }
        val episodeNumbers = remember(episodes, info.reverseSort) {
            episodes.map { EpisodeMatcher.extractEpisodeNumber(it.name) }
        }
        val episodeMeta = rememberEpisodeMeta(
            vm,
            info.name,
            info.en,
            info.year,
            episodes.size,
            seasonHint,
            episodeNumbers,
            info.reverseSort,
        )
        val thumbnails = !episodeMeta.isNullOrEmpty()
        val columns = 3
        val downloadStates by vm.downloadState.collectAsState()
        val rows = episodes.chunked(columns)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEachIndexed { colIndex, ep ->
                        val index = rowIndex * columns + colIndex
                        val state = downloadStates[ep.name.orEmpty()]
                        Box(modifier = Modifier.weight(1f)) {
                            if (thumbnails) {
                                EpisodeCard(
                                    name = ep.name ?: (index + 1).toString(),
                                    episodeNumber = episodeNumbers.getOrNull(index)?.takeIf { it > 0 } ?: (index + 1),
                                    episode = episodeMeta?.getOrNull(index),
                                    selected = index == playIndex,
                                    onClick = { vm.onEpisodeClick(index) },
                                    onDownloadClick = { vm.onDownloadClick(index) },
                                    downloadState = state,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                EpisodeNameCard(
                                    name = ep.name ?: (index + 1).toString(),
                                    selected = index == playIndex,
                                    onClick = { vm.onEpisodeClick(index) },
                                    onDownloadClick = { vm.onDownloadClick(index) },
                                    downloadState = state,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberEpisodeMeta(
    vm: DetailViewModel,
    name: String?,
    altName: String?,
    year: Int,
    episodeCount: Int,
    seasonHint: Int?,
    episodeNumbers: List<Int>,
    reversed: Boolean,
): List<TmdbEpisode>? {
    val enabled = !name.isNullOrBlank() && episodeCount > 0
    val sourceOrder = remember(episodeNumbers, reversed) {
        if (reversed) episodeNumbers.reversed() else episodeNumbers
    }
    val epoch by TmdbImages.configEpoch.collectAsState()
    LaunchedEffect(vm, name, altName, year, episodeCount, seasonHint, sourceOrder, epoch) {
        vm.ensureTmdbEpisodeMeta(name, altName, year, episodeCount, seasonHint, sourceOrder)
    }
    val meta by vm.tmdbEpisodeMeta.collectAsState()
    if (!enabled) return null
    return if (reversed) meta?.asReversed() else meta
}

@Composable
private fun EpisodeCard(
    name: String,
    episodeNumber: Int,
    episode: TmdbEpisode?,
    selected: Boolean,
    onClick: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
    downloadState: Int? = null,
    modifier: Modifier = Modifier,
) {
    val stillPath = episode?.stillPath.orEmpty()
    val title = episode?.name?.takeIf { it.isNotBlank() } ?: name
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(detailCardColor())
                .then(
                    if (selected) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (stillPath.isNotEmpty()) {
                AsyncImage(
                    model = TmdbImages.stillUrl(stillPath),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = episodeNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
            if (downloadState != null && onDownloadClick != null) {
                DownloadBadge(
                    state = downloadState,
                    onClick = onDownloadClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun EpisodeNameCard(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
    downloadState: Int? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .detailGlass(shape)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = Int.MAX_VALUE,
            softWrap = true,
            textAlign = TextAlign.Center,
        )
        if (downloadState != null && onDownloadClick != null) {
            DownloadBadge(
                state = downloadState,
                onClick = onDownloadClick,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun DownloadBadge(
    state: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (state) {
        2, 1, 4 -> R.drawable.ic_download_pause
        5 -> R.drawable.ic_download_done
        else -> R.drawable.ic_download
    }
    val tint = when (state) {
        5 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(R.string.download_title),
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun PillAction(iconRes: Int, text: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeSheet(vm: DetailViewModel, slideFromEnd: Boolean, showQuality: Boolean) {
    val vodUi by vm.vodUi.collectAsState()
    val show by vm.episodeSheet.collectAsState()
    if (!show) return
    val info = vodUi?.info ?: return
    val currentFlag = info.playFlag
    val episodes = info.seriesMap?.get(currentFlag).orEmpty()
    val playIndex = info.playIndex
    val qualityOptions by vm.qualityOptions.collectAsState()
    val qualitySelected by vm.qualitySelected.collectAsState()

    val groupCount = when {
        episodes.size > 400 -> 120
        episodes.size > 100 -> 60
        else -> 20
    }
    val groups = if (episodes.size > groupCount) {
        val result = ArrayList<String>()
        var i = 0
        while (i < episodes.size) {
            val end = minOf(i + groupCount, episodes.size)
            result.add("${i + 1} - $end")
            i += groupCount
        }
        result
    } else {
        emptyList()
    }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val gridScope = rememberCoroutineScope()
    var selectedGroup by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(show, currentFlag, playIndex) {
        if (show && playIndex >= 0) {
            selectedGroup = playIndex / groupCount
            if (playIndex in episodes.indices) gridState.scrollToItem(playIndex)
        }
    }

    AVBoxBottomSheet(
        onDismissRequest = { vm.dismissEpisodeSheet() },
        title = if (info.name.isNullOrEmpty()) {
                stringResource(R.string.detail_episodes)
            } else {
                stringResource(R.string.detail_episodes_of, info.name.orEmpty())
            },
        isScrollable = false,
        slideFromEnd = slideFromEnd,
        modifier = if (slideFromEnd) {
            Modifier
        } else {
            Modifier.fillMaxHeight(EpisodeSheetHeightFraction)
        },
    ) {
        val dismissAnimated = LocalSheetDismiss.current
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showQuality && qualityOptions.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(qualityOptions) { index, option ->
                        FilterChip(
                            selected = index == qualitySelected,
                            onClick = { vm.switchQuality(index) },
                            label = { Text(option) },
                            shape = RoundedCornerShape(20.dp),
                            colors = MaterialTheme.colorScheme.filterChipColors(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (groups.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(groups) { index, label ->
                        FilterChip(
                            selected = index == selectedGroup,
                            onClick = {
                                selectedGroup = index
                                gridScope.launch { gridState.scrollToItem(index * groupCount) }
                            },
                            label = { Text(label) },
                            shape = RoundedCornerShape(20.dp),
                            colors = MaterialTheme.colorScheme.filterChipColors(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            val maxNameLength = episodes.maxOfOrNull { it.name?.length ?: 0 } ?: 0
            val gridColumnCount = when {
                maxNameLength <= 4 -> if (slideFromEnd) 2 else 4
                maxNameLength <= 12 -> 2
                else -> 1
            }
            val gridModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                state = gridState,
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(gridColumnCount),
                modifier = gridModifier,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItemsIndexed(episodes) { index, ep ->
                    FilterChip(
                        selected = index == playIndex,
                        onClick = {
                            vm.onEpisodeClick(index)
                            dismissAnimated()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        label = {
                            Text(
                                text = ep.name ?: (index + 1).toString(),
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = MaterialTheme.colorScheme.filterChipColors(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchDownloadSheet(
    vm: DetailViewModel,
    info: VodInfo,
    episodes: List<VodInfo.VodSeries>,
    onDismiss: () -> Unit,
) {
    val selected by vm.selectedEpisodes.collectAsState()
    val downloadStates by vm.downloadState.collectAsState()
    val completed = episodes.map { downloadStates[it.name.orEmpty()] == 5 }
    val selectable = episodes.indices.filter { !completed[it] }
    ModalBottomSheet(
        onDismissRequest = {
            vm.clearEpisodeSelection()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.download_batch),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.detail_batch_selected, selected.size, episodes.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(episodes.size) { index ->
                    SelectableEpisodeCard(
                        name = episodes[index].name ?: (index + 1).toString(),
                        selected = index in selected,
                        completed = completed[index],
                        onToggle = { vm.toggleEpisodeSelect(index) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { vm.selectAllEpisodes(selectable) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_all))
                }
                TextButton(
                    onClick = {
                        vm.clearEpisodeSelection()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = {
                        vm.downloadSelectedEpisodes()
                        onDismiss()
                    },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(2f),
                ) {
                    Text(
                        text = stringResource(R.string.download_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableEpisodeCard(
    name: String,
    selected: Boolean,
    completed: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .detailGlass(RoundedCornerShape(16.dp))
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = !completed, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = if (completed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(20.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected || completed) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_done),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
