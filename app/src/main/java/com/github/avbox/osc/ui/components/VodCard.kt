package com.github.avbox.osc.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.Movie

enum class VodCardStyle(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    Stacked(R.string.settings_card_style_stacked, R.drawable.ic_card_stacked),
    Overlay(R.string.settings_card_style_overlay, R.drawable.ic_card_overlay),
    Row(R.string.settings_card_style_row, R.drawable.ic_card_row),
    Wide(R.string.settings_card_style_wide, R.drawable.ic_card_wide),
    Grid(R.string.settings_card_style_grid, R.drawable.ic_card_grid);

    val isPortrait: Boolean get() = this == Stacked || this == Overlay

    val isFullSpan: Boolean get() = this == Row || this == Wide

    val isGrid: Boolean get() = this == Grid

    companion object {
        fun of(value: Int): VodCardStyle = entries.getOrElse(value) { Stacked }
    }
}

private val CardShape = RoundedCornerShape(12.dp)
private val PosterShape = RoundedCornerShape(10.dp)
private val BadgeShape = RoundedCornerShape(12.dp)
private val RowCardShape = RoundedCornerShape(16.dp)

@Composable
fun VodCard(
    video: Movie.Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VodCardStyle = VodCardStyle.Stacked,
) {
    when (style) {
        VodCardStyle.Stacked -> StackedCard(video, onClick, onLongClick, modifier)
        VodCardStyle.Overlay -> OverlayCard(video, onClick, onLongClick, modifier)
        VodCardStyle.Row -> RowCard(video, onClick, onLongClick, modifier)
        VodCardStyle.Wide -> WideCard(video, onClick, onLongClick, modifier)
        VodCardStyle.Grid -> GridCard(video, onClick, onLongClick, modifier)
    }
}

@Composable
private fun StackedCard(
    video: Movie.Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    // 对齐 iOS FeaturedCard: 竖版海报 + 底部深色渐变 + 叠加白色剧名/元信息,
    // 剧名永远可见(海报自带艺术字时也不与下方文字争空间)。
    PressableCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = CardShape,
    ) {
        Box(modifier = Modifier.aspectRatio(2f / 3f)) {
            VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.4f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        )
                    ),
            )
            RatingBadge(video, Modifier.align(Alignment.TopEnd))
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = video.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = metaText(video)
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayCard(
    video: Movie.Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    PressableCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = CardShape,
    ) {
        Box(modifier = Modifier.aspectRatio(2f / 3f)) {
            VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f),
                        )
                    ),
            )
            RatingBadge(video, Modifier.align(Alignment.TopEnd))
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(
                    text = video.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = metaText(video)
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowCard(
    video: Movie.Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    PressableCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RowCardShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(148.dp)
                    .clip(PosterShape),
            ) {
                VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
                RatingBadge(video, Modifier.align(Alignment.BottomStart))
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = video.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val score = ratingBadgeText(video.note)
                if (score != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            text = "★",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = score,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                val meta = metaText(video)
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                val summary = video.des?.let {
                    android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY)
                        .toString()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }.orEmpty()
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val area = video.area
                if (!area.isNullOrBlank()) {
                    Text(
                        text = area,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val actor = video.actor
                if (!actor.isNullOrBlank()) {
                    Text(
                        text = actor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val note = video.note
                if (!note.isNullOrBlank() && score == null && note.trim().toFloatOrNull()?.let { it != 0f } != false) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(BadgeShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WideCard(
    video: Movie.Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    // 文字叠加在 5:3 图底部渐变上, 避免首页底部悬浮导航栏遮住下方文字行。
    PressableCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = PosterShape,
    ) {
        Box(modifier = Modifier.aspectRatio(5f / 3f)) {
            VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.5f),
                            1f to Color.Black.copy(alpha = 0.9f),
                        )
                    ),
            )
            Text(
                text = "NEW", // i18n: keep(样式角标沿用源数据通用标记)
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(BadgeShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            RatingBadge(video, Modifier.align(Alignment.TopStart))
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = video.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = metaText(video)
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val note = video.note
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun metaText(video: Movie.Video): String = buildString {
    if (!video.type.isNullOrBlank()) append(video.type)
    if (video.year > 0) {
        if (isNotEmpty()) append(" · ")
        append(video.year)
    }
}

@Composable
private fun RatingBadge(video: Movie.Video, modifier: Modifier = Modifier) {
    val badge = ratingBadgeText(video.note) ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = "★",
            color = Color.White,
            fontSize = 8.sp,
        )
        Text(
            text = badge,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun NoteBadge(note: String?, modifier: Modifier = Modifier) {
    if (note.isNullOrBlank()) return
    Text(
        text = note,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(8.dp)
            .clip(BadgeShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private val RATING_SCORE_REGEX = Regex("评分[:：]?\\s*(\\d+(?:\\.\\d+)?)") // i18n: keep(源备注评分提取)
private val RATING_SCORE_SUFFIX_REGEX = Regex("^(\\d+(?:\\.\\d+)?)\\s*分$") // i18n: keep(源备注评分提取)

internal fun ratingBadgeText(note: String?): String? {
    val scoreText = note?.trim().orEmpty()
    if (scoreText.isEmpty()) return null
    val scoreMatch = RATING_SCORE_REGEX.find(scoreText)
        ?: RATING_SCORE_SUFFIX_REGEX.find(scoreText)
    if (scoreMatch != null) {
        val num = scoreMatch.groupValues[1]
        return if (num.toFloatOrNull() == 0f) null else num
    }
    return scoreText.takeIf { it.toFloatOrNull() != 0f }
}

internal fun isRatingNote(note: String?): Boolean {
    val scoreText = note?.trim().orEmpty()
    if (scoreText.isEmpty()) return false
    if (scoreText.toFloatOrNull() != null) return true
    return ratingBadgeText(scoreText) != scoreText
}

private val GridImageShape = RoundedCornerShape(10.dp)

private val TagGreen = Color(0xFF5DC98A)

private val TagGray = Color(0xFF6E7B8B)

/** 16:9 大图 + 图上角标(集数/独播标签) + 图下剧名与副标题, 双列网格用。 */
@Composable
private fun GridCard(
    video: Movie.Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        PressableCard(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = GridImageShape,
        ) {
            Box(modifier = Modifier.aspectRatio(16f / 9f)) {
                VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                            )
                        ),
                )
                val tag = video.tag?.trim().orEmpty()
                if (tag.isNotEmpty()) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isFreeTag(tag)) TagGreen else TagGray)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                val note = video.note?.trim().orEmpty()
                if (note.isNotEmpty() && !isRatingNote(note)) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Text(
            text = video.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        val subtitle = gridSubtitle(video)
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

private fun isFreeTag(tag: String): Boolean {
    val t = tag.lowercase(java.util.Locale.ROOT)
    return t.contains("免费") || t.contains("独播") || t.contains("限免") || t.contains("free")
}

/** 副标题优先用简介首句, 其次类型/地区等元信息。 */
private fun gridSubtitle(video: Movie.Video): String {
    val des = video.des?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
    if (des.isNotEmpty()) return des
    return metaText(video)
}
