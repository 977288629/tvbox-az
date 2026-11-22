@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.github.avbox.osc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R

@Composable
internal fun SectionTitleIcon(painter: Painter) {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
internal fun SectionTitleIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp),
    )
}

internal const val DetailCardAlpha = 0.5f

@Composable
internal fun detailCardColor(): Color =
    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = DetailCardAlpha)

@Composable
internal fun detailChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = detailCardColor(),
    selectedContainerColor = detailCardColor(),
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
internal fun detailChipBorder(selected: Boolean) = if (selected) {
    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
} else {
    BorderStroke(0.dp, Color.Transparent)
}

internal val DetailItemHeight = 70.dp
internal val DetailItemMinWidth = 160.dp
internal val DetailItemWidth = 160.dp

private const val DetailGlassGlossAlpha = 0.06f
private const val DetailGlassHighlightTopAlpha = 0.35f
private const val DetailGlassHighlightBottomAlpha = 0.06f

@Composable
internal fun Modifier.detailGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(detailCardColor())
    .background(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = DetailGlassGlossAlpha),
            0.45f to Color.Transparent,
        ),
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = DetailGlassHighlightTopAlpha),
                Color.White.copy(alpha = DetailGlassHighlightBottomAlpha),
            ),
        ),
        shape = shape,
    )

@Composable
internal fun DetailItemCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: androidx.compose.ui.unit.Dp = DetailItemMinWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .height(DetailItemHeight)
            .defaultMinSize(minWidth = minWidth)
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
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun SourceCard(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailItemCard(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        minWidth = 0.dp,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SourceSection(vm: DetailViewModel, currentSourceName: String?) {
    val sourceChips by vm.sourceChips.collectAsState()
    val sourcesSearching by vm.sourcesSearching.collectAsState()
    if (!sourcesSearching && sourceChips.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitleIcon(painterResource(R.drawable.ic_detail_switch_source))
            Text(
                text = stringResource(R.string.detail_switch_source),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            if (sourcesSearching) {
                Text(
                    text = stringResource(R.string.detail_finding_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 一行两列网格：不再左右横滑，所有候选站点平铺展示
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2,
        ) {
            if (currentSourceName != null) {
                SourceCard(
                    name = currentSourceName,
                    selected = true,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
            sourceChips.forEach { chip ->
                SourceCard(
                    name = chip.name,
                    selected = false,
                    onClick = { vm.candidateForKey(chip.key)?.let { vm.switchSource(it) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val CR_LINK_REGEX = Regex("\\[a=cr:(?:\\{.*?\\}|\\[.*?\\])/](.*?)\\[/a]")
private val WHITESPACE_REGEX = Regex("\\s")

internal fun removeHtmlTag(info: String?): String {
    if (info.isNullOrEmpty()) return ""
    var text = info.replace(CR_LINK_REGEX, "$1")
    text = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    return text.replace(WHITESPACE_REGEX, "")
}
