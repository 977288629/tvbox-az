package com.github.avbox.osc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.page.DownloadGroup
import com.github.avbox.osc.ui.page.DownloadUiItem

@Composable
fun DownloadGroupCard(
    group: DownloadGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: (DownloadUiItem) -> Unit,
    onPause: (Int) -> Unit,
    onResume: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onCancel: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onToggle() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupPoster(group.pic, group.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GroupStatusPill(group)
                    Spacer(Modifier.width(8.dp))
                    if (group.totalBytes > 0L) {
                        Text(
                            text = formatSize(group.totalBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!group.allCompleted) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { group.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Spacer(Modifier.height(4.dp))
            Column {
                group.episodes.forEachIndexed { index, item ->
                    EpisodeRow(
                        item = item,
                        onPlay = { onPlay(item) },
                        onPause = { onPause(item.id) },
                        onResume = { onResume(item.id) },
                        onRetry = { onRetry(item.id) },
                        onCancel = { onCancel(item.id) },
                        onDelete = { onDelete(item.id) },
                    )
                    if (index < group.episodes.lastIndex) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupStatusPill(group: DownloadGroup) {
    val (label, container, content) = when {
        group.failedCount > 0 && !group.hasActive -> Triple(
            stringResource(R.string.download_group_failed, group.failedCount),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        group.hasActive -> Triple(
            stringResource(R.string.download_group_active, group.activeCount, group.episodeCount),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        group.allCompleted -> Triple(
            stringResource(R.string.download_state_completed),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> Triple(
            stringResource(R.string.download_group_total, group.episodeCount),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun GroupPoster(pic: String?, name: String) {
    Box(
        modifier = Modifier
            .size(62.dp, 84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (pic.isNullOrEmpty()) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            coil3.compose.AsyncImage(
                model = pic,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(84.dp),
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    item: DownloadUiItem,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusDot(item.status)),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = episodeStatusText(item),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.status == 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.total > 0 && !item.isCompleted) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (item.downloaded.toFloat() / item.total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                item.isCompleted -> PillIconButton(R.drawable.player_ic_play, MaterialTheme.colorScheme.tertiaryContainer, onPlay)
                item.canResume -> PillIconButton(R.drawable.player_ic_play, MaterialTheme.colorScheme.primaryContainer, onResume)
                item.canPause && item.status == 2 -> PillIconButton(R.drawable.ic_download_pause, MaterialTheme.colorScheme.primaryContainer, onPause)
                item.canRetry -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.download_action_retry), style = MaterialTheme.typography.labelLarge)
                }
            }
            PillIconButton(
                if (item.isCompleted) R.drawable.ic_download_delete else R.drawable.ic_download_cancel,
                MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                if (item.isCompleted) onDelete() else onCancel()
            }
        }
    }
}

@Composable
private fun PillIconButton(iconRes: Int, container: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(container),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun statusDot(status: Int): Color = when (status) {
    5 -> MaterialTheme.colorScheme.tertiaryContainer
    6 -> MaterialTheme.colorScheme.error
    2, 0, 1, 4 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun episodeStatusText(item: DownloadUiItem): String {
    val base = stateLabel(item.status)
    return if (item.total > 0 && !item.isCompleted) {
        base + " " + formatSize(item.downloaded) + "/" + formatSize(item.total)
    } else if (!item.error.isNullOrEmpty() && item.status == 6) {
        base + " · " + item.error
    } else {
        base
    }
}

@Composable
private fun stateLabel(status: Int): String = stringResource(
    when (status) {
        0 -> R.string.download_state_waiting
        1 -> R.string.download_state_preparing
        2 -> R.string.download_state_downloading
        3 -> R.string.download_state_paused
        4 -> R.string.download_state_merging
        5 -> R.string.download_state_completed
        6 -> R.string.download_state_failed
        7 -> R.string.download_state_cancelled
        else -> R.string.download_state_waiting
    },
)

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.ROOT, "%.1fGB", bytes / (1024f * 1024f * 1024f))
    bytes >= 1024L * 1024L -> (bytes / (1024L * 1024L)).toString() + "MB"
    bytes >= 1024L -> (bytes / 1024L).toString() + "KB"
    else -> bytes.toString() + "B"
}
