package com.github.avbox.osc.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.components.AVBoxAlertDialog
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.components.CapsuleSegmentedButton
import com.github.avbox.osc.ui.components.DownloadGroupCard
import com.github.avbox.osc.ui.components.LoadState
import com.github.avbox.osc.ui.components.LoadStateBox
import com.github.avbox.osc.ui.components.SegmentOption
import com.github.avbox.osc.ui.components.TopBarActionBox

@Composable
fun DownloadScreen(
    onNavigateBack: () -> Unit,
    onPlay: (DownloadUiItem) -> Unit,
    vm: DownloadViewModel = viewModel(),
) {
    val loading by vm.loading.collectAsState()
    val groups by vm.groups.collectAsState()
    val filter by vm.filter.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val expandedKeys = remember { mutableStateMapOf<String, Boolean>() }

    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.download_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(
                R.drawable.ic_arrow_left,
                stringResource(R.string.common_back),
                onClick = onNavigateBack,
            )
        },
        actions = {
            TopBarActionBox(R.drawable.ic_download_pause, stringResource(R.string.download_action_pause_all), onClick = { vm.pauseAll() })
            TopBarActionBox(R.drawable.player_ic_play, stringResource(R.string.download_action_resume_all), onClick = { vm.resumeAll() })
            TopBarActionBox(R.drawable.ic_download_done, stringResource(R.string.download_action_clear_completed), onClick = { vm.clearCompleted() })
            TopBarActionBox(R.drawable.ic_download_delete, stringResource(R.string.download_action_clear_all), onClick = { confirmClear = true })
        },
    ) { topPad, bottomPad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPad, bottom = bottomPad),
        ) {
            CapsuleSegmentedButton(
                options = listOf(
                    SegmentOption(stringResource(R.string.download_filter_all), DownloadViewModel.Filter.ALL),
                    SegmentOption(stringResource(R.string.download_filter_active), DownloadViewModel.Filter.ACTIVE),
                    SegmentOption(stringResource(R.string.download_filter_completed), DownloadViewModel.Filter.COMPLETED),
                ),
                selectedValue = filter,
                onOptionSelected = { vm.setFilter(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading -> LoadStateBox(
                        state = LoadState.Loading,
                        emptyText = "",
                        errorText = "",
                        retryText = "",
                        modifier = Modifier.fillMaxSize(),
                    )
                    groups.isEmpty() -> LoadStateBox(
                        state = LoadState.Empty,
                        emptyText = stringResource(R.string.download_empty),
                        errorText = "",
                        retryText = "",
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        val visible = vm.visibleGroups()
                        if (visible.isEmpty()) {
                            LoadStateBox(
                                state = LoadState.Empty,
                                emptyText = stringResource(R.string.download_empty),
                                errorText = "",
                                retryText = "",
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                            ) {
                                items(visible, key = { it.key }) { group ->
                                    DownloadGroupCard(
                                        group = group,
                                        expanded = expandedKeys[group.key] == true,
                                        onToggle = { expandedKeys[group.key] = !(expandedKeys[group.key] ?: false) },
                                        onPlay = onPlay,
                                        onPause = { vm.pause(it) },
                                        onResume = { vm.resume(it) },
                                        onRetry = { vm.retry(it) },
                                        onCancel = { vm.cancel(it) },
                                        onDelete = { vm.delete(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AVBoxAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.download_action_clear_all)) },
            text = { Text(stringResource(R.string.download_clear_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.clearAll()
                    confirmClear = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
